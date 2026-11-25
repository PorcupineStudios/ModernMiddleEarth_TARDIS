package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.flight.PilotStatus;
import com.porcupinestudios.tardisrb.flight.Piloting;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Piloting (Aaron 2026-11-19): the server tells the pilot the box's health, charge and speed bonuses every half second
 * ({@link Status}, kept in {@link PilotStatus} for the HUD and the box's physics); the pilot's set-down key
 * ({@link Settle}) turns the box into a block and back while they keep piloting (as a camera while it's a block).
 */
public final class PilotNet {
    private PilotNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(PilotNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Status.TYPE, Status.CODEC, (p, ctx) -> ctx.enqueueWork(() -> PilotStatus.set(p.health(),
                p.maxHealth(), p.charge(), p.chargeCap(), p.flightSpeed(), p.vortexSpeed(), p.camera())));
        r.playToServer(Settle.TYPE, Settle.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Piloting.settle(sp);
            }
        }));
    }

    public record Status(double health, double maxHealth, long charge, long chargeCap, double flightSpeed,
            double vortexSpeed, boolean camera) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(TardisRB.id("pilot_status"));
        // composite() stops at six fields in 1.21.1
        public static final StreamCodec<ByteBuf, Status> CODEC = StreamCodec.of((b, p) -> {
            b.writeDouble(p.health());
            b.writeDouble(p.maxHealth());
            ByteBufCodecs.VAR_LONG.encode(b, p.charge());
            ByteBufCodecs.VAR_LONG.encode(b, p.chargeCap());
            b.writeDouble(p.flightSpeed());
            b.writeDouble(p.vortexSpeed());
            b.writeBoolean(p.camera());
        }, b -> new Status(b.readDouble(), b.readDouble(), ByteBufCodecs.VAR_LONG.decode(b),
                ByteBufCodecs.VAR_LONG.decode(b), b.readDouble(), b.readDouble(), b.readBoolean()));

        @Override
        public Type<Status> type() {
            return TYPE;
        }
    }

    public record Settle() implements CustomPacketPayload {
        public static final Type<Settle> TYPE = new Type<>(TardisRB.id("pilot_settle"));
        public static final StreamCodec<ByteBuf, Settle> CODEC = StreamCodec.unit(new Settle());

        @Override
        public Type<Settle> type() {
            return TYPE;
        }
    }
}
