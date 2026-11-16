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
     * @param model LOTR body the player is drawn on (an NPC model kind: human, hobbit, dwarf, elf, orc; "classic" for
     *     64x32 skins); empty: the vanilla player model
     */
    public record Def(String name, String description, double scale, Map<ResourceLocation, Double> attributes,
            Optional<ResourceLocation> skin, List<ResourceLocation> factions, int order,
            Map<ResourceLocation, Float> alignment, Optional<ResourceLocation> spawn, Optional<String> colonyRace,
            double settlerBias, Optional<String> model) {
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
                Codec.STRING.optionalFieldOf("model").forGetter(Def::model))
                .apply(i, Def::new));
    }

    private static final CodecJsonLoader<Def> LOADER = new CodecJsonLoader<>("player_race", Def.CODEC);

    /** Each known player's race skin texture, on the client (filled by {@link Skin}); read by the skin mixin. */
    public static final Map<UUID, ResourceLocation> CLIENT_SKINS = new ConcurrentHashMap<>();
    /** Each known player's LOTR body (Def.model), on the client; read by client.RacePlayers. */
    public static final Map<UUID, String> CLIENT_MODELS = new ConcurrentHashMap<>();

    private Races() {}

    public static void register(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> joined(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> joined(e.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> joined(e.getEntity()));
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Skin.TYPE, Skin.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> {
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
        Def def = of(player).map(all()::get).orElse(null);
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
        return new Skin(player.getUUID(), def.flatMap(Def::skin), def.flatMap(Def::model).orElse(""));
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
                .then(Commands.literal("admin").requires(s -> s.hasPermission(2)).executes(c -> {
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
     * Operators only: every online player with their race and size (context ""), and one player's page (context = their
     * UUID) with a race drop-down and a size stepper.
     */
    private static final class Admin implements ViewProvider {
        private static final View.Target LIST = new View.Target(ADMIN_VIEW, "", "");

        @Override
        public Optional<View> build(ServerPlayer op, String context, String tab) {
            if (!op.hasPermissions(2)) {
                return Optional.empty();
            }
            if (context.isEmpty()) {
                var page = TableBuilder.of(op.server.getPlayerList().getPlayers(), p -> p.getUUID().toString())
                        .column(Column.text("player", label("player", "Player"), 3),
                                p -> Cell.text(p.getGameProfile().getName()))
                        .column(Column.text("race", label("race", "Race"), 2), p -> Cell.text(raceName(p)))
                        .column(Column.number("size", label("size", "Size"), 1),
                                p -> Cell.number(p.getScale(), Math.round(p.getScale() * 100) + "%",
                                        com.porcupinestudios.lotrport.ui.Tone.NORMAL))
                        .open("edit")
                        .rowAction(Action.of("edit", label("edit", "Edit")))
                        .build();
                return Optional.of(new View(ADMIN_VIEW, "", label("title", "Player races"), List.of(), "", page,
                        Optional.empty()));
            }
            ServerPlayer p = target(op, context);
            if (p == null) {
                return Optional.empty();
            }
            List<com.porcupinestudios.lotrport.ui.Stat.Choice> choices = new java.util.ArrayList<>();
            choices.add(com.porcupinestudios.lotrport.ui.Stat.Choice.of("", label("none", "None")));
            all().forEach((id, d) -> choices.add(com.porcupinestudios.lotrport.ui.Stat.Choice.of(id.toString(),
                    name(id, d))));
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
            return Optional.of(new View(ADMIN_VIEW, context, label("title", "Player races"), List.of(), "", page,
                    Optional.of(LIST)));
        }

        @Override
        public Result act(ServerPlayer op, String context, String tab, String action, String row, int value) {
            ServerPlayer p = op.hasPermissions(2) ? target(op, context.isEmpty() ? row : context) : null;
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

    /** Server -> client: this player's race skin (empty: their own skin). */
    public record Skin(UUID player, Optional<ResourceLocation> skin, String model) implements CustomPacketPayload {
        public static final Type<Skin> TYPE = new Type<>(LotrPort.id("race_skin"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Skin> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Skin::player,
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), Skin::skin,
                ByteBufCodecs.STRING_UTF8, Skin::model,
                Skin::new);

        @Override
        public Type<Skin> type() {
            return TYPE;
        }
    }
}
