package com.porcupinestudios.lotrport.faction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.event.AlignmentChangeEvent;
import com.porcupinestudios.lotrport.net.HudNet;
import com.porcupinestudios.lotrport.player.PlayerLotrData;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Feeds LOTR's alignment bar (LOTRTickHandlerClient.renderAlignment): which faction a player watches and their
 * standing with it, sent to the client whenever it changes. LOTR picked the watched faction in its factions screen;
 * here it's {@code /lotr_view <faction>}, or automatic: the pledged faction, else the playable faction with the
 * most alignment either way, else the Hobbits (LOTR's start in the Shire).
 */
public final class AlignmentHud {
    private static final ResourceLocation DEFAULT = LotrPort.id("hobbit");
    /** How often (ticks) a player's bar is rebuilt and compared; changes from alignment events go out at once. */
    private static final int CHECK_TICKS = 10;
    private static final Map<UUID, HudNet.AlignmentBar> SENT = new ConcurrentHashMap<>();

    private AlignmentHud() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer p && p.tickCount % CHECK_TICKS == 0) {
                update(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((AlignmentChangeEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                update(p);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            SENT.remove(e.getEntity().getUUID());
            LAST_IN_REGION.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> SENT.remove(e.getEntity().getUUID()));
    }

    /** Sends the player's bar if it differs from the last one sent. */
    public static void update(ServerPlayer player) {
        HudNet.AlignmentBar bar = bar(player);
        if (!bar.equals(SENT.get(player.getUUID()))) {
            SENT.put(player.getUUID(), bar);
            if (player.connection != null && player.connection.hasChannel(bar)) { // test players have no channel
                PacketDistributor.sendToPlayer(player, bar);
            }
        }
    }

    public static HudNet.AlignmentBar bar(ServerPlayer player) {
        Optional<Faction> watched = watched(player);
        if (watched.isEmpty()) {
            return HudNet.AlignmentBar.NONE;
        }
        Faction f = watched.get();
        PlayerLotrData data = player.getData(LotrRegistries.PLAYER_DATA);
        float alignment = f.fixedAlignment().orElse(data.alignment(f.id()));
        List<HudNet.Rank> ranks = f.ranks().stream()
                .map(r -> new HudNet.Rank(r.alignment(), f.rankName(r))).toList();
        boolean nearNpc = FactionInfluence.settings().showBarNearNpcs()
                && FactionInfluence.nearestFaction(player).isPresent();
        return new HudNet.AlignmentBar(f.id().toString(), f.name(), f.color(), alignment,
                HudNet.AlignmentBar.flags(data.pledge().filter(f.id()::equals).isPresent(),
                        FactionInfluence.reach(player, f.id()).ordinal(), nearNpc), ranks);
    }

    /** The faction the player watches: their choice, else the pledge, else the strongest standing, else Hobbits. */
    public static Optional<Faction> watched(ServerPlayer player) {
        MinecraftServer server = player.server;
        ResourceLocation chosen = ResourceLocation.tryParse(player.getData(LotrRegistries.VIEW_FACTION));
        if (chosen != null) {
            Optional<Faction> f = Factions.get(server, chosen).filter(AlignmentHud::shown);
            if (f.isPresent()) {
                return f;
            }
        }
        // away from Middle-earth, the people you're standing with are the ones you see
        var me = com.porcupinestudios.lotrport.world.LotrDimensions.middleEarth(server);
        if (player.level() != me) {
            Optional<Faction> near = FactionInfluence.nearestFaction(player).flatMap(id -> Factions.get(server, id))
                    .filter(AlignmentHud::shown);
            if (near.isPresent()) {
                return near;
            }
        }
        PlayerLotrData data = player.getData(LotrRegistries.PLAYER_DATA);
        Optional<Faction> pledged = data.pledge().flatMap(id -> Factions.get(server, id));
        if (pledged.isPresent()) {
            return pledged;
        }
        Optional<Faction> strongest = data.alignment().entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<ResourceLocation, Float> e) -> -Math.abs(e.getValue()))
                        .thenComparing(e -> e.getKey().toString()))
                .map(e -> Factions.get(server, e.getKey()))
                .flatMap(Optional::stream)
                .filter(AlignmentHud::shown)
                .findFirst();
        if (strongest.isPresent()) {
            return strongest;
        }
        return Factions.get(server, DEFAULT)
                .or(() -> Factions.all(server).stream().filter(AlignmentHud::shown).findFirst());
    }

    /** LOTR's bar regions in up/down order (LOTRDimension.DimensionRegion), then founded realms. */
    public static final List<String> REGIONS = List.of("west", "east", "south", REALMS);
    private static final String REALMS = "realms";

    /**
     * Whether the bar can show a faction: LOTR hid Utumno, Hostile, Unaligned and the like (fixed alignment, not
     * playable, or outside the map's regions).
     */
    public static boolean shown(Faction f) {
        return f.playable() && f.fixedAlignment().isEmpty() && REGIONS.contains(region(f));
    }

    /** The bar region of a faction: its data's region, or "realms" for a realm. */
    public static String region(Faction f) {
        if (f.realm()) {
            return REALMS;
        }
        FactionDef def = Factions.definitions().get(f.id());
        return def == null ? "none" : def.region();
    }

    /** Every faction the bar can show, region by region in LOTR's order. */
    public static List<Faction> shown(MinecraftServer server) {
        List<Faction> all = Factions.all(server).stream().filter(AlignmentHud::shown).toList();
        List<Faction> out = new ArrayList<>();
        for (String r : REGIONS) {
            all.stream().filter(f -> region(f).equals(r)).forEach(out::add);
        }
        return out;
    }

    /**
     * The arrow keys (as LOTR's): left/right ({@code step} -1/1) the previous or next faction within the shown one's
     * region, up/down ({@code step} -2/2) the previous or next region that has any, landing on the faction last
     * shown there. Wraps round; the choice sticks as with /lotr_view.
     */
    public static void cycle(ServerPlayer player, int step) {
        List<Faction> all = shown(player.server);
        if (all.isEmpty()) {
            return;
        }
        Faction current = watched(player).filter(all::contains).orElse(all.get(0));
        String region = region(current);
        Faction next;
        if (Math.abs(step) >= HudNet.CycleView.REGION) {
            List<String> regions = REGIONS.stream().filter(r -> all.stream().anyMatch(f -> region(f).equals(r)))
                    .toList();
            String to = regions.get(Math.floorMod(regions.indexOf(region) + Integer.signum(step), regions.size()));
            Map<String, ResourceLocation> last = LAST_IN_REGION.computeIfAbsent(player.getUUID(),
                    k -> new ConcurrentHashMap<>());
            last.put(region, current.id());
            ResourceLocation remembered = last.get(to);
            next = all.stream().filter(f -> region(f).equals(to) && f.id().equals(remembered)).findFirst()
                    .orElseGet(() -> all.stream().filter(f -> region(f).equals(to)).findFirst().orElseThrow());
        } else {
            List<Faction> inRegion = all.stream().filter(f -> region(f).equals(region)).toList();
            int at = inRegion.indexOf(current);
            next = inRegion.get(Math.floorMod(at + (step < 0 ? -1 : 1), inRegion.size()));
        }
        player.setData(LotrRegistries.VIEW_FACTION, next.id().toString());
        update(player);
    }

    /** Per player, the faction last shown in each region, so up/down returns to it (as LOTR's regionLastViewed). */
    private static final Map<UUID, Map<String, ResourceLocation>> LAST_IN_REGION = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ /lotr_view

    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_view")
                .executes(AlignmentHud::show)
                .then(Commands.literal("auto").executes(ctx -> choose(ctx, null)))
                .then(Commands.argument("faction", ResourceLocationArgument.id())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggestResource(
                                Factions.all(ctx.getSource().getServer()).stream()
                                        .filter(AlignmentHud::shown).map(Faction::id), b))
                        .executes(ctx -> choose(ctx, ResourceLocationArgument.getId(ctx, "faction")))));
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Optional<Faction> f = watched(p);
        boolean auto = p.getData(LotrRegistries.VIEW_FACTION).isEmpty();
        ctx.getSource().sendSuccess(() -> Component.literal("Alignment bar: ")
                .append(f.map(Faction::name).orElse(Component.literal("none")))
                .append(auto ? " (automatic)" : ""), false);
        return 1;
    }

    private static int choose(CommandContext<CommandSourceStack> ctx, ResourceLocation id)
            throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (id != null) {
            Optional<Faction> f = Factions.get(p.server, id);
            if (f.isEmpty()) {
                ctx.getSource().sendFailure(Component.literal("Unknown faction " + id));
                return 0;
            }
        }
        p.setData(LotrRegistries.VIEW_FACTION, id == null ? "" : id.toString());
        update(p);
        return show(ctx);
    }
}
