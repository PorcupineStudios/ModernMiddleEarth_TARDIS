package com.porcupinestudios.lotrport.colony.members;

import com.mojang.authlib.GameProfile;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Society;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Town Hall screen's packets. Right-click the Hall's block with an empty hand (or {@code /lotr_colony hall}):
 * the server sends the colony and its members ({@link Hall}); members with {@link Perm#MEMBERS} send
 * {@link SetRole} to add, promote, demote or remove a player. Every change is checked by {@link Permissions}.
 */
public final class HallNet {
    public static Consumer<Hall> hallHandler = h -> { };

    private HallNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(HallNet::payloads);
        NeoForge.EVENT_BUS.addListener(HallNet::onUse);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Hall.TYPE, Hall.CODEC, (p, ctx) -> ctx.enqueueWork(() -> hallHandler.accept(p)));
        r.playToServer(SetRole.TYPE, SetRole.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                setRole(sp, p);
            }
        }));
    }

    /** An empty-handed right-click on the Hall's block (or the Hall spot) opens the Town Hall. */
    private static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !sp.getMainHandItem().isEmpty()
                || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) {
            return;
        }
        BlockPos pos = event.getPos();
        for (Colony c : Colonies.get(sp.server).all()) {
            if (c.dimension().equals(sp.level().dimension()) && (c.hall().equals(pos) || c.hall().below().equals(pos))) {
                send(sp, c, true);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
        }
    }

    static void setRole(ServerPlayer sp, SetRole p) {
        Colony colony = Colonies.get(sp.server).get(p.colony()).orElse(null);
        if (colony == null) {
            return;
        }
        Optional<UUID> target = resolve(sp.server, p.player());
        if (target.isEmpty()) {
            sp.displayClientMessage(Component.translatableWithFallback("colony.lotr_port.members.no_player",
                    "No player called %s has been here", p.player()), false);
        } else {
            Optional<ResourceLocation> role = p.role().isEmpty() ? Optional.empty()
                    : Optional.ofNullable(ResourceLocation.tryParse(p.role()));
            Permissions.Change ch = Permissions.setRole(sp.server, sp.getUUID(), colony, target.get(), role);
            sp.displayClientMessage(ch.message(), false);
        }
        send(sp, colony, false);
    }

    /** A player by name (online, or known to the server) or by UUID text. */
    static Optional<UUID> resolve(MinecraftServer server, String who) {
        try {
            return Optional.of(UUID.fromString(who));
        } catch (IllegalArgumentException e) {
            // a name
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(who);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(who)).map(GameProfile::getId);
    }

    static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        return Optional.ofNullable(server.getProfileCache()).flatMap(pc -> pc.get(id)).map(GameProfile::getName)
                .orElse(id.toString().substring(0, 8));
    }

    /** Sends the Town Hall of {@code colony}; {@code open} opens the screen, else only an open one refreshes. */
    public static void send(ServerPlayer sp, Colony colony, boolean open) {
        MinecraftServer server = sp.server;
        List<Member> members = new ArrayList<>();
        members.add(new Member(colony.founder(), name(server, colony.founder()), Permissions.RULER.toString()));
        for (Map.Entry<UUID, ResourceLocation> e : colony.get(Members.TYPE).orElse(Members.NONE).roles().entrySet()) {
            members.add(new Member(e.getKey(), name(server, e.getKey()), e.getValue().toString()));
        }
        List<Role> roles = new ArrayList<>();
        Permissions.roles().forEach((id, def) -> roles.add(new Role(id.toString(), def.name(), def.rank(),
                String.join(", ", def.permissions()))));
        roles.sort((a, b) -> Integer.compare(b.rank(), a.rank()));
        String mine = Permissions.roleId(sp.getUUID(), colony).map(ResourceLocation::toString).orElse("");
        long coins = colony.get(CoreComponents.TREASURY).map(CoreComponents.Treasury::coins).orElse(0L);
        PacketDistributor.sendToPlayer(sp, new Hall(colony.id(), colony.name(), Society.citizenCount(server, colony),
                Society.soldiers(server, colony).size(), (int) Math.min(Integer.MAX_VALUE, coins), mine,
                Permissions.can(sp.getUUID(), colony, Perm.MEMBERS), members, roles, open));
    }

    public record Member(UUID id, String name, String role) {
        public static final StreamCodec<ByteBuf, Member> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id, ByteBufCodecs.STRING_UTF8, Member::name,
                ByteBufCodecs.STRING_UTF8, Member::role, Member::new);
    }

    public record Role(String id, String name, int rank, String permissions) {
        public static final StreamCodec<ByteBuf, Role> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Role::id, ByteBufCodecs.STRING_UTF8, Role::name,
                ByteBufCodecs.VAR_INT, Role::rank, ByteBufCodecs.STRING_UTF8, Role::permissions, Role::new);
    }

    /** The colony as the Town Hall shows it. {@code yourRole} empty = not a member. */
    public record Hall(UUID colony, String name, int citizens, int soldiers, int coins, String yourRole,
            boolean canEdit, List<Member> members, List<Role> roles, boolean open) implements CustomPacketPayload {
        public static final Type<Hall> TYPE = new Type<>(LotrPort.id("town_hall"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hall> CODEC = StreamCodec.of((buf, h) -> {
            UUIDUtil.STREAM_CODEC.encode(buf, h.colony());
            ByteBufCodecs.STRING_UTF8.encode(buf, h.name());
            buf.writeVarInt(h.citizens());
            buf.writeVarInt(h.soldiers());
            buf.writeVarInt(h.coins());
            ByteBufCodecs.STRING_UTF8.encode(buf, h.yourRole());
            buf.writeBoolean(h.canEdit());
            Member.CODEC.apply(ByteBufCodecs.list()).encode(buf, h.members());
            Role.CODEC.apply(ByteBufCodecs.list()).encode(buf, h.roles());
            buf.writeBoolean(h.open());
        }, buf -> new Hall(UUIDUtil.STREAM_CODEC.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), ByteBufCodecs.STRING_UTF8.decode(buf),
                buf.readBoolean(), Member.CODEC.apply(ByteBufCodecs.list()).decode(buf),
                Role.CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readBoolean()));

        @Override
        public Type<Hall> type() {
            return TYPE;
        }
    }

    /** Give {@code player} (a name or UUID) {@code role} ("" = remove from the colony). */
    public record SetRole(UUID colony, String player, String role) implements CustomPacketPayload {
        public static final Type<SetRole> TYPE = new Type<>(LotrPort.id("hall_role"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetRole> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, SetRole::colony, ByteBufCodecs.STRING_UTF8, SetRole::player,
                ByteBufCodecs.STRING_UTF8, SetRole::role, SetRole::new);

        @Override
        public Type<SetRole> type() {
            return TYPE;
        }
    }
}
