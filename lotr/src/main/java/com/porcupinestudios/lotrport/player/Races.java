package com.porcupinestudios.lotrport.player;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Cell;
import com.porcupinestudios.lotrport.ui.Column;
import com.porcupinestudios.lotrport.ui.TableBuilder;
import com.porcupinestudios.lotrport.ui.View;
import com.porcupinestudios.lotrport.ui.ViewProvider;
import com.porcupinestudios.lotrport.ui.Views;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Player races: a picker the first time a player is in Middle-earth, then the race's size and attributes on the
 * player and its skin on every client. Races are data, {@code data/<ns>/player_race/<id>.json} ({@link Def}), and
 * reload with /reload. Size uses {@code minecraft:generic.scale}, which also scales the hitbox, eye height and camera.
 */
public final class Races {
    public static final ResourceLocation VIEW = LotrPort.id("race_picker");
    public static final ResourceLocation ADMIN_VIEW = LotrPort.id("race_admin");
    /** Op level for the admin screen and its key (server admins). */
    public static final int ADMIN_LEVEL = 4;
    static final ResourceLocation BLOCK_REACH = ResourceLocation.withDefaultNamespace("player.block_interaction_range");
    static final ResourceLocation ENTITY_REACH = ResourceLocation.withDefaultNamespace("player.entity_interaction_range");
    /** Modifier id on every attribute a race changes, so a new race replaces the old one cleanly. */
    private static final ResourceLocation MODIFIER = LotrPort.id("race");

    /**
     * One race.
     *
     * @param name shown in the picker (a lang key {@code race.<ns>.<id>} wins when present)
     * @param description one line in the picker
     * @param scale body size, 1 = a Man (hitbox, eye height and camera follow it)
     * @param attributes attribute id -> amount added (reach, health, speed...)
     * @param skin texture drawn on the player in place of their own skin (64x64 layout), if any
     * @param factions peoples of this race, shown in the picker (not enforced)
     * @param order place in the picker, lowest first
     * @param alignment alignment given per faction when the race is first chosen
     * @param spawn waypoint the player is sent to (and respawns at) when the race is first chosen
     * @param colonyRace colonist race (colony_race names) this race counts as; default: this race's id path
     * @param settlerBias settler weight multiplier for that colonist race in colonies the player founded
     * @param crouchHeight extra factor on the crouching hitbox and eyes (hobbit 0.99: fits a 1-block gap)
     * @param model LOTR body the player is drawn on (an NPC model kind: human, hobbit, dwarf, elf, orc; "classic" for
     *     64x32 skins); empty: the vanilla player model
     */
    public record Def(String name, String description, double scale, Map<ResourceLocation, Double> attributes,
            Optional<ResourceLocation> skin, List<ResourceLocation> factions, int order,
            Map<ResourceLocation, Float> alignment, Optional<ResourceLocation> spawn, Optional<String> colonyRace,
            double settlerBias, Optional<String> model, double crouchHeight) {
        public static final Codec<Def> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Def::name),
                Codec.STRING.optionalFieldOf("description", "").forGetter(Def::description),
                Codec.doubleRange(0.0625, 16).optionalFieldOf("scale", 1.0).forGetter(Def::scale),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE).optionalFieldOf("attributes", Map.of())
                        .forGetter(Def::attributes),
                ResourceLocation.CODEC.optionalFieldOf("skin").forGetter(Def::skin),
                ResourceLocation.CODEC.listOf().optionalFieldOf("factions", List.of()).forGetter(Def::factions),
                Codec.INT.optionalFieldOf("order", 0).forGetter(Def::order),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT).optionalFieldOf("alignment", Map.of())
                        .forGetter(Def::alignment),
                ResourceLocation.CODEC.optionalFieldOf("spawn").forGetter(Def::spawn),
                Codec.STRING.optionalFieldOf("colony_race").forGetter(Def::colonyRace),
                Codec.doubleRange(0, 100).optionalFieldOf("settler_bias", 1.15).forGetter(Def::settlerBias),
                Codec.STRING.optionalFieldOf("model").forGetter(Def::model),
                Codec.doubleRange(0.1, 2).optionalFieldOf("crouch_height", 1.0).forGetter(Def::crouchHeight))
                .apply(i, Def::new));
    }

    private static final CodecJsonLoader<Def> LOADER = new CodecJsonLoader<>("player_race", Def.CODEC);

    /** Each known player's race skin texture, on the client (filled by {@link Skin}); read by the skin mixin. */
    public static final Map<UUID, ResourceLocation> CLIENT_SKINS = new ConcurrentHashMap<>();
    /** Each known player's LOTR body (Def.model), on the client; read by client.RacePlayers. */
    public static final Map<UUID, String> CLIENT_MODELS = new ConcurrentHashMap<>();
    /** Each known player's Def.crouchHeight, on the client (hitboxes are worked out on both sides). */
    public static final Map<UUID, Float> CLIENT_CROUCH = new ConcurrentHashMap<>();

    private Races() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> joined(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> joined(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> joined(e.getEntity()));
        NeoForge.EVENT_BUS.addListener(Races::size);
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Skin.TYPE, Skin.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
                    CLIENT_CROUCH.put(p.player(), p.crouch());
                    p.skin().ifPresentOrElse(s -> CLIENT_SKINS.put(p.player(), s), () -> CLIENT_SKINS.remove(p.player()));
                    if (p.model().isEmpty() || p.skin().isEmpty()) {
                        CLIENT_MODELS.remove(p.player());
                    } else {
                        CLIENT_MODELS.put(p.player(), p.model());
                    }
                })));
        Views.register(VIEW, new Picker());
        Views.register(ADMIN_VIEW, new Admin());
    }

    public static Map<ResourceLocation, Def> all() {
        return LOADER.entries();
    }

    /** The player's race, if chosen and still in the data. */
    public static Optional<ResourceLocation> of(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(player.getData(LotrRegistries.RACE));
        return id != null && all().containsKey(id) ? Optional.of(id) : Optional.empty();
    }

    /** Sets (or clears, with null) the race, applies it and tells every client. */
    public static void set(ServerPlayer player, ResourceLocation race) {
        player.setData(LotrRegistries.RACE, race == null ? "" : race.toString());
        apply(player);
        sendToAll(player, skinOf(player));
    }

    /** A first race's starting alignment and home: alignment added per faction, then off to its waypoint. */
    private static void start(ServerPlayer player, Def def) {
        def.alignment().forEach((faction, amount) -> com.porcupinestudios.lotrport.faction.Alignment.add(player,
                faction, amount));
        def.spawn().flatMap(com.porcupinestudios.lotrport.travel.FastTravel::waypoint).ifPresent(w -> {
            ServerLevel me = LotrDimensions.middleEarth(player.server);
            net.minecraft.core.BlockPos pos = LotrDimensions.surface(me, w.x(), w.z());
            player.teleportTo(me, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.setRespawnPosition(me.dimension(), pos, 0f, true, false);
        });
    }

    /**
     * How much more likely a settler of colonist race {@code colonistRace} is in a colony this player founded: the
     * founder race's {@code settler_bias} if it counts as that race, else 1.
     * shortcut: reads the founder only while online (offline founders give 1); store the race on the colony if that matters.
     */
    public static double settlerFactor(java.util.UUID founder, String colonistRace) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(founder);
        if (p == null) {
            return 1.0;
        }
        return of(p).map(id -> {
            Def d = all().get(id);
            return d.colonyRace().orElse(id.getPath()).equals(colonistRace) ? d.settlerBias() : 1.0;
        }).orElse(1.0);
    }

    /** The race's crouch_height on the crouching hitbox (and eyes), server and client alike. */
    private static void size(net.neoforged.neoforge.event.entity.EntityEvent.Size e) {
        if (e.getPose() != net.minecraft.world.entity.Pose.CROUCHING
                || !(e.getEntity() instanceof net.minecraft.world.entity.player.Player p)) {
            return;
        }
        float f = p instanceof ServerPlayer sp
                ? of(sp).map(id -> (float) all().get(id).crouchHeight()).orElse(1f)
                : CLIENT_CROUCH.getOrDefault(p.getUUID(), 1f);
        if (f != 1f) {
            e.setNewSize(e.getNewSize().scale(1f, f));
        }
    }

    private static void joined(net.minecraft.world.entity.player.Player p) {
        if (!(p instanceof ServerPlayer player)) {
            return;
        }
        apply(player);
        // the joiner's skin to everyone, and everyone's to the joiner
        sendToAll(player, skinOf(player));
        for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
            if (other != player) {
                send(player, skinOf(other));
            }
        }
        if (LotrConfig.RACE_PICKER.get() && of(player).isEmpty() && !all().isEmpty()
                && LotrDimensions.isMiddleEarth((ServerLevel) player.level())) {
            Views.open(player, new View.Target(VIEW, "", ""), true);
        }
    }

    /** Puts the race's scale and attribute modifiers on the player (removing an earlier race's). */
    public static void apply(ServerPlayer player) {
        for (var attribute : BuiltInRegistries.ATTRIBUTE.holders().toList()) {
            AttributeInstance inst = player.getAttribute(attribute);
            if (inst != null) {
                inst.removeModifier(MODIFIER);
            }
        }
        Def def = of(player).map(id -> tuned(player.server, id)).orElse(null);
        float size = player.getData(LotrRegistries.RACE_SCALE); // an operator's size, 0 = the race's
        double scale = size > 0 ? size : def == null ? 1.0 : def.scale();
        if (scale != 1.0) {
            AttributeInstance inst = player.getAttribute(Attributes.SCALE);
            if (inst != null) {
                inst.addTransientModifier(new AttributeModifier(MODIFIER, scale - 1.0,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
        if (def == null) {
            player.refreshDimensions();
            return;
        }
        BiConsumer<net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute>, AttributeModifier>
                add = (attr, mod) -> {
                    AttributeInstance inst = player.getAttribute(attr);
                    if (inst != null) {
                        inst.addTransientModifier(mod);
                    }
                };
        def.attributes().forEach((id, amount) -> BuiltInRegistries.ATTRIBUTE.getHolder(id).ifPresentOrElse(
                attr -> add.accept(attr, new AttributeModifier(MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE)),
                () -> LotrPort.LOG.warn("[player_race] unknown attribute {}", id)));
        player.refreshDimensions(); // hitbox and eye height now, not on the next tick
    }

    private static void sendToAll(ServerPlayer from, Skin skin) {
        from.server.getPlayerList().getPlayers().forEach(p -> send(p, skin));
    }

    private static void send(ServerPlayer to, Skin skin) {
        if (to.connection != null && to.connection.hasChannel(skin)) { // test players have no channel
            PacketDistributor.sendToPlayer(to, skin);
        }
    }

    private static Skin skinOf(ServerPlayer player) {
        Optional<Def> def = of(player).map(all()::get);
        return new Skin(player.getUUID(), def.flatMap(Def::skin), def.flatMap(Def::model).orElse(""),
                def.map(d -> (float) d.crouchHeight()).orElse(1f));
    }

    private static boolean mayPick(ServerPlayer player) {
        return of(player).isEmpty() || LotrConfig.RACE_CHANGE.get() || player.hasPermissions(2);
    }

    /** {@code /lotr_race} opens the picker (when you may pick); {@code /lotr_race reset <player>} (ops) clears it. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("lotr_race")
                .executes(c -> {
                    Views.open(c.getSource().getPlayerOrException(), new View.Target(VIEW, "", ""), true);
                    return 1;
                })
                .then(Commands.literal("admin").requires(s -> s.hasPermission(ADMIN_LEVEL)).executes(c -> {
                    Views.open(c.getSource().getPlayerOrException(), new View.Target(ADMIN_VIEW, "", ""), true);
                    return 1;
                }))
                .then(Commands.literal("reset").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                            ServerPlayer target = EntityArgument.getPlayer(c, "player");
                            set(target, null);
                            c.getSource().sendSuccess(() -> Component.literal("Race cleared for ")
                                    .append(target.getDisplayName()), true);
                            return 1;
                        }))));
    }

    private static Component name(ResourceLocation id, Def def) {
        return Component.translatableWithFallback("race." + id.getNamespace() + "." + id.getPath(), def.name());
    }

    /** The picker: one row per race; Choose (or a double-click) picks it. */
    private static final class Picker implements ViewProvider {
        @Override
        public Optional<View> build(ServerPlayer player, String context, String tab) {
            if (!mayPick(player)) {
                return Optional.empty(); // also closes the screen once a race is chosen
            }
            List<Map.Entry<ResourceLocation, Def>> races = all().entrySet().stream()
                    .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, Def> e) -> e.getValue().order())
                            .thenComparing(e -> e.getKey().toString()))
                    .toList();
            var page = TableBuilder.of(races, e -> e.getKey().toString())
                    .column(Column.text("race", label("race", "Race"), 2),
                            e -> Cell.text(name(e.getKey(), e.getValue()).getString()))
                    .column(Column.number("height", label("height", "Height"), 1),
                            e -> Cell.number(e.getValue().scale(), Math.round(e.getValue().scale() * 100) + "%",
                                    com.porcupinestudios.lotrport.ui.Tone.NORMAL))
                    .column(Column.text("about", label("about", "About"), 6), e -> Cell.text(e.getValue().description()))
                    .open("choose")
                    .rowAction(Action.of("choose", label("choose", "Choose")).confirmed())
                    .build();
            return Optional.of(new View(VIEW, "", label("title", "Choose your race"), List.of(), "", page,
                    Optional.empty()));
        }

        @Override
        public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
            ResourceLocation id = ResourceLocation.tryParse(row);
            if (!"choose".equals(action) || id == null || !all().containsKey(id) || !mayPick(player)) {
                return Result.NONE;
            }
            boolean first = of(player).isEmpty();
            set(player, id);
            if (first) {
                start(player, all().get(id));
            }
            return Result.say(Component.translatableWithFallback("ui.lotr_port.race_picker.chosen", "You are now: %s",
                    name(id, all().get(id))));
        }

        private static Component label(String key, String fallback) {
            return Component.translatableWithFallback("ui.lotr_port.race_picker." + key, fallback);
        }
    }

    /**
     * Server admins (op level 4): tab Players lists every online player with their race and size (a player's page,
     * context = their UUID, forces a race and size); tab Races tunes each preset's size and reach (a preset's page,
     * context = {@code race:<id>}), saved in {@link RaceTunes}.
     */
    private static final class Admin implements ViewProvider {
        private static final String RACE_CONTEXT = "race:";
        private static final List<View.Tab> TABS = List.of(new View.Tab("players", label("players", "Players")),
                new View.Tab("races", label("races", "Races")));

        @Override
        public Optional<View> build(ServerPlayer op, String context, String tab) {
            if (!op.hasPermissions(ADMIN_LEVEL)) {
                return Optional.empty();
            }
            if (context.startsWith(RACE_CONTEXT)) {
                return racePage(op, context);
            }
            if (!context.isEmpty()) {
                return playerPage(op, context);
            }
            com.porcupinestudios.lotrport.ui.Page page;
            if ("races".equals(tab)) {
                page = TableBuilder.of(sorted(), e -> RACE_CONTEXT + e.getKey())
                        .column(Column.text("race", label("race", "Race"), 3),
                                e -> Cell.text(name(e.getKey(), e.getValue()).getString()))
                        .column(Column.number("size", label("size", "Size"), 1),
                                e -> percent(tuned(op.server, e.getKey()).scale()))
                        .column(Column.number("block", label("block_reach", "Block reach"), 2),
                                e -> reach(tuned(op.server, e.getKey()).attributes().getOrDefault(BLOCK_REACH, 0.0)))
                        .column(Column.number("entity", label("entity_reach", "Hit reach"), 2),
                                e -> reach(tuned(op.server, e.getKey()).attributes().getOrDefault(ENTITY_REACH, 0.0)))
                        .open("edit")
                        .rowAction(Action.of("edit", label("edit", "Edit")))
                        .build();
            } else {
                tab = "players";
                page = TableBuilder.of(op.server.getPlayerList().getPlayers(), p -> p.getUUID().toString())
                        .column(Column.text("player", label("player", "Player"), 3),
                                p -> Cell.text(p.getGameProfile().getName()))
                        .column(Column.text("race", label("race", "Race"), 2), p -> Cell.text(raceName(p)))
                        .column(Column.number("size", label("size", "Size"), 1), p -> percent(p.getScale()))
                        .open("edit")
                        .rowAction(Action.of("edit", label("edit", "Edit")))
                        .build();
            }
            return Optional.of(new View(ADMIN_VIEW, "", label("title", "Races"), TABS, tab, page, Optional.empty()));
        }

        private Optional<View> playerPage(ServerPlayer op, String context) {
            ServerPlayer p = target(op, context);
            if (p == null) {
                return Optional.empty();
            }
            List<com.porcupinestudios.lotrport.ui.Stat.Choice> choices = new java.util.ArrayList<>();
            choices.add(com.porcupinestudios.lotrport.ui.Stat.Choice.of("", label("none", "None")));
            sorted().forEach(e -> choices.add(com.porcupinestudios.lotrport.ui.Stat.Choice.of(e.getKey().toString(),
                    name(e.getKey(), e.getValue()))));
            float size = p.getData(LotrRegistries.RACE_SCALE);
            var page = new com.porcupinestudios.lotrport.ui.Page.Stats(List.of(
                    new com.porcupinestudios.lotrport.ui.Page.Section(Component.literal(p.getGameProfile().getName()),
                            List.of(
                                    com.porcupinestudios.lotrport.ui.Stat.of(label("race", "Race"), raceName(p))
                                            .withPicker(new com.porcupinestudios.lotrport.ui.Stat.Picker("race",
                                                    p.getData(LotrRegistries.RACE), choices)),
                                    com.porcupinestudios.lotrport.ui.Stat.of(label("size", "Size"),
                                            Math.round(p.getScale() * 100) + "%" + (size > 0 ? "" : " (race)"))
                                            .withStepper(new com.porcupinestudios.lotrport.ui.Stat.Stepper("size",
                                                    Math.round(p.getScale() * 100), 10, 400, 5))))),
                    List.of(Action.of("size_reset", label("size_reset", "Race size"))));
            return Optional.of(new View(ADMIN_VIEW, context, label("title", "Races"), List.of(), "", page,
                    Optional.of(new View.Target(ADMIN_VIEW, "", "players"))));
        }

        private Optional<View> racePage(ServerPlayer op, String context) {
            ResourceLocation id = ResourceLocation.tryParse(context.substring(RACE_CONTEXT.length()));
            if (id == null || !all().containsKey(id)) {
                return Optional.empty();
            }
            Def d = tuned(op.server, id);
            boolean changed = RaceTunes.get(op.server).tune(id).isPresent();
            int block = (int) Math.round(d.attributes().getOrDefault(BLOCK_REACH, 0.0) * 10);
            int entity = (int) Math.round(d.attributes().getOrDefault(ENTITY_REACH, 0.0) * 10);
            var page = new com.porcupinestudios.lotrport.ui.Page.Stats(List.of(
                    new com.porcupinestudios.lotrport.ui.Page.Section(name(id, d), List.of(
                            com.porcupinestudios.lotrport.ui.Stat.of(label("size", "Size"),
                                    Math.round(d.scale() * 100) + "%").withStepper(
                                    new com.porcupinestudios.lotrport.ui.Stat.Stepper("size",
                                            (int) Math.round(d.scale() * 100), 10, 400, 1)),
                            com.porcupinestudios.lotrport.ui.Stat.of(label("block_reach", "Block reach"),
                                    reachText(block / 10.0)).withStepper(
                                    new com.porcupinestudios.lotrport.ui.Stat.Stepper("block", block, -40, 40, 1)),
                            com.porcupinestudios.lotrport.ui.Stat.of(label("entity_reach", "Hit reach"),
                                    reachText(entity / 10.0)).withStepper(
                                    new com.porcupinestudios.lotrport.ui.Stat.Stepper("entity", entity, -40, 40, 1)),
                            com.porcupinestudios.lotrport.ui.Stat.of(label("source", "Numbers from"), changed
                                    ? label("source_tuned", "this world (Reset uses the race file)").getString()
                                    : label("source_file", "the race file").getString())))),
                    List.of(Action.of("reset", label("reset", "Reset")).confirmed()));
            return Optional.of(new View(ADMIN_VIEW, context, label("title", "Races"), List.of(), "", page,
                    Optional.of(new View.Target(ADMIN_VIEW, "", "races"))));
        }

        @Override
        public Result act(ServerPlayer op, String context, String tab, String action, String row, int value) {
            if (!op.hasPermissions(ADMIN_LEVEL)) {
                return Result.NONE;
            }
            String about = context.isEmpty() ? row : context;
            if (about.startsWith(RACE_CONTEXT)) {
                return actRace(op, about, action, value);
            }
            ServerPlayer p = target(op, about);
            if (p == null) {
                return Result.NONE;
            }
            switch (action) {
                case "edit" -> {
                    return Result.go(new View.Target(ADMIN_VIEW, p.getUUID().toString(), ""));
                }
                case "race" -> {
                    ResourceLocation id = row.isEmpty() ? null : ResourceLocation.tryParse(row);
                    if (id == null || all().containsKey(id)) {
                        set(p, id); // no starting alignment or teleport: that is for a player's own first choice
                    }
                }
                case "size" -> {
                    p.setData(LotrRegistries.RACE_SCALE, Math.max(10, Math.min(400, value)) / 100f);
                    apply(p);
                }
                case "size_reset" -> {
                    p.setData(LotrRegistries.RACE_SCALE, 0f);
                    apply(p);
                }
                default -> { }
            }
            return Result.NONE;
        }

        private Result actRace(ServerPlayer op, String context, String action, int value) {
            ResourceLocation id = ResourceLocation.tryParse(context.substring(RACE_CONTEXT.length()));
            if (id == null || !all().containsKey(id)) {
                return Result.NONE;
            }
            if ("edit".equals(action)) {
                return Result.go(new View.Target(ADMIN_VIEW, context, ""));
            }
            Def d = tuned(op.server, id);
            RaceTunes.Tune t = new RaceTunes.Tune(d.scale(), d.attributes().getOrDefault(BLOCK_REACH, 0.0),
                    d.attributes().getOrDefault(ENTITY_REACH, 0.0));
            switch (action) {
                case "size" -> t = new RaceTunes.Tune(Math.max(10, Math.min(400, value)) / 100.0, t.blockReach(),
                        t.entityReach());
                case "block" -> t = new RaceTunes.Tune(t.scale(), Math.max(-40, Math.min(40, value)) / 10.0,
                        t.entityReach());
                case "entity" -> t = new RaceTunes.Tune(t.scale(), t.blockReach(),
                        Math.max(-40, Math.min(40, value)) / 10.0);
                case "reset" -> t = null;
                default -> {
                    return Result.NONE;
                }
            }
            RaceTunes.get(op.server).set(id, t);
            // everyone of that race takes the new numbers now
            op.server.getPlayerList().getPlayers().stream().filter(p -> of(p).filter(id::equals).isPresent())
                    .forEach(Races::apply);
            return Result.NONE;
        }

        private static Cell percent(double scale) {
            return Cell.number(scale, Math.round(scale * 100) + "%", com.porcupinestudios.lotrport.ui.Tone.NORMAL);
        }

        private static Cell reach(double amount) {
            return Cell.number(amount, reachText(amount), amount < 0 ? com.porcupinestudios.lotrport.ui.Tone.WARN
                    : com.porcupinestudios.lotrport.ui.Tone.NORMAL);
        }

        private static String reachText(double amount) {
            return amount == 0 ? "normal" : String.format(java.util.Locale.ROOT, "%+.1f blocks", amount);
        }

        private static ServerPlayer target(ServerPlayer op, String uuid) {
            try {
                return op.server.getPlayerList().getPlayer(UUID.fromString(uuid));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private static String raceName(ServerPlayer p) {
            return of(p).map(id -> name(id, all().get(id)).getString()).orElse("-");
        }

        private static Component label(String key, String fallback) {
            return Component.translatableWithFallback("ui.lotr_port.race_admin." + key, fallback);
        }
    }

    /** The race presets in picker order. */
    private static List<Map.Entry<ResourceLocation, Def>> sorted() {
        return all().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, Def> e) -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .toList();
    }

    /** A preset with this world's admin tune (size, reach) applied over its file. */
    public static Def tuned(net.minecraft.server.MinecraftServer server, ResourceLocation id) {
        Def d = all().get(id);
        return RaceTunes.get(server).tune(id).map(t -> {
            Map<ResourceLocation, Double> attrs = new java.util.HashMap<>(d.attributes());
            attrs.put(BLOCK_REACH, t.blockReach());
            attrs.put(ENTITY_REACH, t.entityReach());
            return new Def(d.name(), d.description(), t.scale(), Map.copyOf(attrs), d.skin(), d.factions(), d.order(),
                    d.alignment(), d.spawn(), d.colonyRace(), d.settlerBias(), d.model(), d.crouchHeight());
        }).orElse(d);
    }

    /** Server -> client: this player's race skin (empty: their own skin). */
    public record Skin(UUID player, Optional<ResourceLocation> skin, String model, float crouch) implements CustomPacketPayload {
        public static final Type<Skin> TYPE = new Type<>(LotrPort.id("race_skin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Skin> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Skin::player,
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Skin::skin,
                ByteBufCodecs.STRING_UTF8, Skin::model,
                ByteBufCodecs.FLOAT, Skin::crouch,
                Skin::new);

        @Override
        public Type<Skin> type() {
            return TYPE;
        }
    }
}
