package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.space.Planet;
import com.porcupinestudios.tardisrb.space.PlanetMaps;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Tells clients about the planets (every one, so the client picks by the dimension it is in: its own planet's cloud
 * bank, or the globes of the space it is in) and sends finished globe maps.
 */
public final class SkyNet {
    public static Consumer<Planets> clientHandler = g -> { };
    public static Consumer<PlanetMap> clientMapHandler = g -> { };

    private SkyNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SkyNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("2");
        r.playToClient(Planets.TYPE, Planets.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p)));
        r.playToClient(PlanetMap.TYPE, PlanetMap.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientMapHandler.accept(p)));
    }

    /** Every planet, then every finished globe map. */
    public static void sendPlanets(ServerPlayer player) {
        List<Planet> all = com.porcupinestudios.tardisrb.space.Planets.all(player.server);
        PacketDistributor.sendToPlayer(player, new Planets(all));
        for (Planet p : all) {
            PlanetMaps.map(p.id()).ifPresent(m -> sendMap(player, m));
        }
    }

    public static void sendMap(ServerPlayer player, PlanetMap map) {
        PacketDistributor.sendToPlayer(player, map);
    }

    static final StreamCodec<ByteBuf, Planet> PLANET = StreamCodec.composite(ResourceLocation.STREAM_CODEC,
            Planet::id, ByteBufCodecs.fromCodec(SkyLink.CODEC), Planet::link, Planet::new);

    public record Planets(List<Planet> planets) implements CustomPacketPayload {
        public static final Type<Planets> TYPE = new Type<>(TardisRB.id("planets"));
        static final StreamCodec<ByteBuf, Planets> CODEC = PLANET.apply(ByteBufCodecs.list())
                .map(Planets::new, Planets::planets);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A planet's globe map: width x height RGB bytes, deflated. */
    public record PlanetMap(ResourceLocation planet, int width, int height, byte[] packed)
            implements CustomPacketPayload {
        public static final Type<PlanetMap> TYPE = new Type<>(TardisRB.id("planet_map"));
        static final StreamCodec<ByteBuf, PlanetMap> CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC,
                PlanetMap::planet, ByteBufCodecs.VAR_INT, PlanetMap::width, ByteBufCodecs.VAR_INT,
                PlanetMap::height, ByteBufCodecs.byteArray(4 << 20), PlanetMap::packed, PlanetMap::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
