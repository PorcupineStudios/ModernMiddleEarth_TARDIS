package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.space.SpaceTravel;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Tells a player's client which planets to draw in the space dimension they are in (none elsewhere). */
public final class SkyNet {
    public static Consumer<Globes> clientHandler = g -> { };

    private SkyNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SkyNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Globes.TYPE, Globes.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p)));
    }

    public static void sendGlobes(ServerPlayer player) {
        List<Globe> globes = SpaceTravel.links().stream().filter(l -> l.space().equals(player.level().dimension()))
                .map(SkyNet::globe).toList();
        PacketDistributor.sendToPlayer(player, new Globes(globes));
    }

    static Globe globe(SkyLink l) {
        return new Globe(l.center().x, l.center().y, l.center().z, l.radius());
    }

    /** A planet seen from space: a ball at (x, y, z). */
    public record Globe(double x, double y, double z, double radius) {
        static final StreamCodec<ByteBuf, Globe> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Globe::x, ByteBufCodecs.DOUBLE, Globe::y, ByteBufCodecs.DOUBLE, Globe::z,
                ByteBufCodecs.DOUBLE, Globe::radius, Globe::new);
    }

    public record Globes(List<Globe> globes) implements CustomPacketPayload {
        public static final Type<Globes> TYPE = new Type<>(TardisRB.id("globes"));
        static final StreamCodec<ByteBuf, Globes> CODEC = Globe.CODEC.apply(ByteBufCodecs.list())
                .map(Globes::new, Globes::globes);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
