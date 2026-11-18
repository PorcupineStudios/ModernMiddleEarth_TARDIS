package com.porcupinestudios.lotrport.border;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Borders to the client's map: the original map as a table (biome index -> holder, the client has the biome map
 * itself) plus the changed pixels, never the whole grid. Sent on login and after every change. The client handler is
 * set by client setup, so common code never names a client class.
 */
public final class BorderNet {
    /** Set by the client (client.BorderLayer). */
    public static Consumer<Snapshot> clientHandler = s -> { };

    private BorderNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent e) -> e.registrar("1").playToClient(Snapshot.TYPE,
                Snapshot.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p))));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                send(sp, snapshot(sp.server));
            }
        });
    }

    public static void sendToAll(MinecraftServer server) {
        if (server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        Snapshot s = snapshot(server);
        server.getPlayerList().getPlayers().forEach(p -> send(p, s));
    }

    private static void send(ServerPlayer to, Snapshot s) {
        if (to.connection != null && to.connection.hasChannel(s)) { // mock test players have no channel
            PacketDistributor.sendToPlayer(to, s);
        }
    }

    /** One holder on the map: its id, name and map colour. */
    public record Holder(ResourceLocation id, Component name, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Holder> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Holder::id,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Holder::name,
                ByteBufCodecs.INT, Holder::color,
                Holder::new);
    }

    /** A kingdom's name on the map, at its capital (map pixels). */
    public record Label(String name, float x, float z, int color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Label> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Label::name,
                ByteBufCodecs.FLOAT, Label::x,
                ByteBufCodecs.FLOAT, Label::z,
                ByteBufCodecs.INT, Label::color,
                Label::new);
    }

    /**
     * @param holders palette; the lists below hold indices into it, -1 = nobody
     * @param base per biome index of the map, its original holder
     * @param pixels changed pixels ({@code pz * width + px})
     * @param owners the holder of each changed pixel
     */
    public record Snapshot(List<Holder> holders, List<Integer> base, List<Integer> pixels, List<Integer> owners,
            List<Label> labels) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(LotrPort.id("borders"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.composite(
                Holder.CODEC.apply(ByteBufCodecs.list()), Snapshot::holders,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Snapshot::base,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Snapshot::pixels,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Snapshot::owners,
                Label.CODEC.apply(ByteBufCodecs.list()), Snapshot::labels,
                Snapshot::new);

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    public static Snapshot snapshot(MinecraftServer server) {
        MiddleEarthMap map = MiddleEarthMap.get();
        List<ResourceLocation> ids = new ArrayList<>();
        List<Integer> base = new ArrayList<>();
        for (int b = 0; b < map.biomes().size(); b++) {
            base.add(index(ids, Borders.baseOfBiome(b)));
        }
        List<Integer> pixels = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        for (Map.Entry<Integer, Optional<ResourceLocation>> e : Borders.get(server).changes().entrySet()) {
            pixels.add(e.getKey());
            owners.add(index(ids, e.getValue()));
        }
        List<Holder> holders = new ArrayList<>();
        for (ResourceLocation id : ids) {
            Optional<Faction> f = Factions.get(server, id);
            holders.add(new Holder(id, f.map(Faction::name).orElse(Component.literal(id.toString())),
                    f.map(Faction::color).orElse(0xFFFFFF)));
        }
        List<Label> labels = new ArrayList<>();
        Borders.capitals(server).forEach((realm, colony) -> {
            Optional<Faction> f = Factions.get(server, realm);
            labels.add(new Label(f.map(x -> x.name().getString()).orElse(realm.toString()),
                    (float) map.pixelX(colony.hall().getX()), (float) map.pixelZ(colony.hall().getZ()),
                    f.map(Faction::color).orElse(0xFFFFFF)));
        });
        return new Snapshot(holders, base, pixels, owners, labels);
    }

    private static int index(List<ResourceLocation> ids, Optional<ResourceLocation> id) {
        if (id.isEmpty()) {
            return -1;
        }
        int i = ids.indexOf(id.get());
        if (i < 0) {
            ids.add(id.get());
            i = ids.size() - 1;
        }
        return i;
    }

}
