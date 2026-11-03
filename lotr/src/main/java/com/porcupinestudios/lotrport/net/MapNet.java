package com.porcupinestudios.lotrport.net;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The map screen's packets: the client asks for the map ({@link OpenMap}), the server answers with the waypoints and
 * what the player may do ({@link MapData}), and a click on a waypoint asks to travel ({@link Travel}). The client
 * side handler is set by client setup ({@link #clientHandler}), so common code never names a screen.
 */
public final class MapNet {
    /** Opens the map screen; set by the client. Does nothing on a dedicated server. */
    public static BiConsumer<MapData, IPayloadContext> clientHandler = (data, ctx) -> { };

    private MapNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MapNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(OpenMap.TYPE, OpenMap.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ctx.reply(data(sp));
            }
        }));
        r.playToServer(Travel.TYPE, Travel.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                FastTravel.travel(sp, p.waypoint());
            }
        }));
        r.playToClient(MapData.TYPE, MapData.CODEC, (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p, ctx)));
    }

    /** What the map screen shows this player. */
    public static MapData data(ServerPlayer player) {
        List<Mark> marks = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Waypoint> e : FastTravel.waypoints().entrySet()) {
            Waypoint w = e.getValue();
            boolean unlocked = FastTravel.data(player).regions().contains(w.region());
            if (w.hidden() && !unlocked) {
                continue;
            }
            Optional<Component> problem = unlocked ? FastTravel.problem(player, e.getKey()) : Optional.empty();
            int state = !unlocked ? Mark.LOCKED : problem.isPresent() ? Mark.BLOCKED : Mark.READY;
            int distance = (int) Math.round(Math.hypot(w.x() - player.getX(), w.z() - player.getZ()));
            marks.add(new Mark(e.getKey(), (float) w.mapX(), (float) w.mapZ(), state, distance,
                    problem.orElse(Component.empty())));
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        return new MapData(marks, (float) map.pixelX(player.getX()), (float) map.pixelZ(player.getZ()),
                (int) FastTravel.waitTicks(player));
    }

    /** One waypoint on the map: where, whether the player may travel there, how far it is, and why not. */
    public record Mark(ResourceLocation id, float mapX, float mapZ, int state, int distance, Component problem) {
        public static final int LOCKED = 0;
        public static final int BLOCKED = 1;
        public static final int READY = 2;

        public static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Mark::id,
                ByteBufCodecs.FLOAT, Mark::mapX,
                ByteBufCodecs.FLOAT, Mark::mapZ,
                ByteBufCodecs.VAR_INT, Mark::state,
                ByteBufCodecs.VAR_INT, Mark::distance,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Mark::problem,
                Mark::new);
    }

    /** Client asks for the map. */
    public record OpenMap() implements CustomPacketPayload {
        public static final Type<OpenMap> TYPE = new Type<>(LotrPort.id("open_map"));
        public static final StreamCodec<ByteBuf, OpenMap> CODEC = StreamCodec.unit(new OpenMap());

        @Override
        public Type<OpenMap> type() {
            return TYPE;
        }
    }

    /** Client asks to travel to a waypoint. */
    public record Travel(ResourceLocation waypoint) implements CustomPacketPayload {
        public static final Type<Travel> TYPE = new Type<>(LotrPort.id("travel"));
        public static final StreamCodec<ByteBuf, Travel> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Travel::waypoint, Travel::new);

        @Override
        public Type<Travel> type() {
            return TYPE;
        }
    }

    /** The map for one player: waypoints, where they are (map pixels), and how long until they may travel. */
    public record MapData(List<Mark> marks, float playerX, float playerZ, int waitTicks)
            implements CustomPacketPayload {
        public static final Type<MapData> TYPE = new Type<>(LotrPort.id("map_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MapData> CODEC = StreamCodec.composite(
                Mark.CODEC.apply(ByteBufCodecs.list()), MapData::marks,
                ByteBufCodecs.FLOAT, MapData::playerX,
                ByteBufCodecs.FLOAT, MapData::playerZ,
                ByteBufCodecs.VAR_INT, MapData::waitTicks,
                MapData::new);

        @Override
        public Type<MapData> type() {
            return TYPE;
        }
    }
}
