package com.porcupinestudios.lotrport.social;

import com.mojang.authlib.GameProfile;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Map icons and the "who sees me" screen. The open map asks for {@link Icons} every two seconds; the server sends
 * only the players and colonies this viewer may see ({@link Social#canSee}, {@link MapPresence}). The visibility
 * screen gets {@link View} and sends {@link Change}s.
 */
public final class SocialNet {
    public static Consumer<Icons> iconsHandler = i -> { };
    public static Consumer<View> viewHandler = v -> { };

    /** What a {@link Change} does. */
    public enum Kind { EVERYONE, COLONIES, GROUPS, ADD_PLAYER, REMOVE_PLAYER, COLONY_PRESENCE, GROUP_CREATE,
        GROUP_ADD, GROUP_REMOVE, GROUP_LEAVE }

    private SocialNet() {}

    public static void register(IEventBus modBus) {
        MapPresence.init();
        modBus.addListener(SocialNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Icons.TYPE, Icons.CODEC, (p, ctx) -> ctx.enqueueWork(() -> iconsHandler.accept(p)));
        r.playToClient(View.TYPE, View.CODEC, (p, ctx) -> ctx.enqueueWork(() -> viewHandler.accept(p)));
        r.playToServer(IconsRequest.TYPE, IconsRequest.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, icons(sp));
            }
        }));
        r.playToServer(OpenView.TYPE, OpenView.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                sendView(sp, true);
            }
        }));
        r.playToServer(Change.TYPE, Change.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                apply(sp, p);
                sendView(sp, false);
            }
        }));
    }

    /** The icons {@code viewer} may see: other players in Middle-earth and colonies there. */
    public static Icons icons(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        Social social = Social.get(server);
        MiddleEarthMap map = MiddleEarthMap.get();
        ServerLevel me = LotrDimensions.middleEarth(server);
        List<PlayerIcon> players = new ArrayList<>();
        if (me != null) {
            for (ServerPlayer p : me.players()) {
                if (p != viewer && !p.isSpectator() && social.canSee(server, viewer.getUUID(), p.getUUID())) {
                    players.add(new PlayerIcon(p.getUUID(), p.getGameProfile().getName(),
                            (float) map.pixelX(p.getX()), (float) map.pixelZ(p.getZ())));
                }
            }
        }
        List<ColonyIcon> colonies = new ArrayList<>();
        for (Colony c : Colonies.get(server).all()) {
            if (me == null || !c.dimension().equals(me.dimension()) || !MapPresence.visible(c, viewer.getUUID())) {
                continue;
            }
            boolean member = Permissions.isMember(viewer.getUUID(), c);
            colonies.add(new ColonyIcon(c.name(), (float) map.pixelX(c.hall().getX()),
                    (float) map.pixelZ(c.hall().getZ()), member, MapPresence.of(c) != MapPresence.PUBLIC, c.ai()));
        }
        return new Icons(players, colonies);
    }

    static void apply(ServerPlayer sp, Change c) {
        MinecraftServer server = sp.server;
        Social social = Social.get(server);
        UUID me = sp.getUUID();
        Social.Prefs prefs = social.prefs(me);
        Kind kind = c.kind() >= 0 && c.kind() < Kind.values().length ? Kind.values()[c.kind()] : null;
        if (kind == null) {
            return;
        }
        switch (kind) {
            case EVERYONE -> social.setPrefs(me, prefs.withEveryone(!prefs.everyone()));
            case COLONIES -> social.setPrefs(me, prefs.withColonies(!prefs.colonies()));
            case GROUPS -> social.setPrefs(me, prefs.withGroups(!prefs.groups()));
            case ADD_PLAYER, REMOVE_PLAYER -> resolve(server, c.a()).ifPresent(id -> social.setPrefs(me,
                    prefs.withPlayer(id, kind == Kind.ADD_PLAYER)));
            case COLONY_PRESENCE -> {
                try {
                    Colonies colonies = Colonies.get(server);
                    colonies.get(UUID.fromString(c.a())).filter(col -> Permissions.can(me, col, Perm.MAP))
                            .ifPresent(col -> colonies.set(col, MapPresence.TYPE, MapPresence.of(col).next()));
                } catch (IllegalArgumentException e) {
                    // bad id from the client
                }
            }
            case GROUP_CREATE -> social.createGroup(me, c.a());
            case GROUP_ADD, GROUP_REMOVE -> resolve(server, c.b()).ifPresent(id -> social.setMember(me, c.a(), id,
                    kind == Kind.GROUP_ADD));
            case GROUP_LEAVE -> {
                try {
                    social.leave(me, UUID.fromString(c.a()));
                } catch (IllegalArgumentException e) {
                    // bad id from the client
                }
            }
            default -> { }
        }
    }

    static Optional<UUID> resolve(MinecraftServer server, String who) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(who.strip());
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(who.strip()))
                .map(GameProfile::getId);
    }

    static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(id)).map(GameProfile::getName)
                .orElse(id.toString().substring(0, 8));
    }

    public static void sendView(ServerPlayer sp, boolean open) {
        MinecraftServer server = sp.server;
        Social social = Social.get(server);
        Social.Prefs p = social.prefs(sp.getUUID());
        List<String> players = p.players().stream().map(id -> name(server, id)).toList();
        List<GroupView> groups = new ArrayList<>();
        for (Social.Group g : social.groupsOf(sp.getUUID())) {
            List<String> names = new ArrayList<>();
            names.add(name(server, g.leader()));
            g.members().forEach(m -> names.add(name(server, m)));
            groups.add(new GroupView(g.id(), g.name(), g.leader().equals(sp.getUUID()), String.join(", ", names)));
        }
        List<ColonyView> colonies = new ArrayList<>();
        for (Colony c : Colonies.get(server).all()) {
            if (Permissions.isMember(sp.getUUID(), c)) {
                colonies.add(new ColonyView(c.id(), c.name(), MapPresence.of(c).ordinal(),
                        Permissions.can(sp.getUUID(), c, Perm.MAP)));
            }
        }
        PacketDistributor.sendToPlayer(sp, new View(p.everyone(), p.colonies(), p.groups(), players, groups,
                colonies, open));
    }

    public record PlayerIcon(UUID id, String name, float x, float z) {
        public static final StreamCodec<ByteBuf, PlayerIcon> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, PlayerIcon::id, ByteBufCodecs.STRING_UTF8, PlayerIcon::name,
                ByteBufCodecs.FLOAT, PlayerIcon::x, ByteBufCodecs.FLOAT, PlayerIcon::z, PlayerIcon::new);
    }

    /** {@code limited}: not public (members only or hidden); {@code ai}: an AI ruler's. */
    public record ColonyIcon(String name, float x, float z, boolean member, boolean limited, boolean ai) {
        public static final StreamCodec<ByteBuf, ColonyIcon> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ColonyIcon::name, ByteBufCodecs.FLOAT, ColonyIcon::x,
                ByteBufCodecs.FLOAT, ColonyIcon::z, ByteBufCodecs.BOOL, ColonyIcon::member,
                ByteBufCodecs.BOOL, ColonyIcon::limited, ByteBufCodecs.BOOL, ColonyIcon::ai, ColonyIcon::new);
    }

    public record Icons(List<PlayerIcon> players, List<ColonyIcon> colonies) implements CustomPacketPayload {
        public static final Type<Icons> TYPE = new Type<>(LotrPort.id("map_icons"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Icons> CODEC = StreamCodec.composite(
                PlayerIcon.CODEC.apply(ByteBufCodecs.list()), Icons::players,
                ColonyIcon.CODEC.apply(ByteBufCodecs.list()), Icons::colonies, Icons::new);

        @Override
        public Type<Icons> type() {
            return TYPE;
        }
    }

    public record IconsRequest() implements CustomPacketPayload {
        public static final Type<IconsRequest> TYPE = new Type<>(LotrPort.id("map_icons_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, IconsRequest> CODEC =
                StreamCodec.unit(new IconsRequest());

        @Override
        public Type<IconsRequest> type() {
            return TYPE;
        }
    }

    public record OpenView() implements CustomPacketPayload {
        public static final Type<OpenView> TYPE = new Type<>(LotrPort.id("visibility_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenView> CODEC = StreamCodec.unit(new OpenView());

        @Override
        public Type<OpenView> type() {
            return TYPE;
        }
    }

    public record GroupView(UUID id, String name, boolean leader, String members) {
        public static final StreamCodec<ByteBuf, GroupView> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, GroupView::id, ByteBufCodecs.STRING_UTF8, GroupView::name,
                ByteBufCodecs.BOOL, GroupView::leader, ByteBufCodecs.STRING_UTF8, GroupView::members,
                GroupView::new);
    }

    public record ColonyView(UUID id, String name, int presence, boolean canEdit) {
        public static final StreamCodec<ByteBuf, ColonyView> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, ColonyView::id, ByteBufCodecs.STRING_UTF8, ColonyView::name,
                ByteBufCodecs.VAR_INT, ColonyView::presence, ByteBufCodecs.BOOL, ColonyView::canEdit,
                ColonyView::new);
    }

    /** The visibility screen's state. */
    public record View(boolean everyone, boolean colonies, boolean groups, List<String> players,
            List<GroupView> groupList, List<ColonyView> colonyList, boolean open) implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(LotrPort.id("visibility"));
        private static final StreamCodec<ByteBuf, List<String>> NAMES = ByteBufCodecs.STRING_UTF8
                .apply(ByteBufCodecs.list());
        private static final StreamCodec<ByteBuf, List<GroupView>> GROUPS = GroupView.CODEC.apply(ByteBufCodecs.list());
        private static final StreamCodec<ByteBuf, List<ColonyView>> COLONIES = ColonyView.CODEC
                .apply(ByteBufCodecs.list());
        public static final StreamCodec<RegistryFriendlyByteBuf, View> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeBoolean(v.everyone());
            buf.writeBoolean(v.colonies());
            buf.writeBoolean(v.groups());
            NAMES.encode(buf, v.players());
            GROUPS.encode(buf, v.groupList());
            COLONIES.encode(buf, v.colonyList());
            buf.writeBoolean(v.open());
        }, buf -> new View(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), NAMES.decode(buf),
                GROUPS.decode(buf), COLONIES.decode(buf), buf.readBoolean()));

        @Override
        public Type<View> type() {
            return TYPE;
        }
    }

    /** A change: {@code kind} a {@link Kind} ordinal; {@code a}, {@code b} its arguments (names, ids). */
    public record Change(int kind, String a, String b) implements CustomPacketPayload {
        public static final Type<Change> TYPE = new Type<>(LotrPort.id("visibility_change"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Change::kind, ByteBufCodecs.STRING_UTF8, Change::a,
                ByteBufCodecs.STRING_UTF8, Change::b, Change::new);

        @Override
        public Type<Change> type() {
            return TYPE;
        }
    }
}
