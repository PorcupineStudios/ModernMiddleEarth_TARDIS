package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The console's one packet: the client clicked a control on the console model, or the time rotor's column
 * ({@link Press}). There is no console screen (Aaron, 2026-11-04): everything is done with the physical controls and
 * the monitor shows what the console knows.
 */
public final class ConsoleNet {
    private ConsoleNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ConsoleNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Press.TYPE, Press.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ConsoleControls.press(sp, p.pos(), p.control()).filter(c -> !c.getString().isEmpty())
                        .ifPresent(c -> sp.displayClientMessage(c, true));
            }
        }));
    }

    /** The client clicked a control (or {@link ConsoleControls#COLUMN}) on the console at {@code pos}. */
    public record Press(BlockPos pos, String control) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(TardisRB.id("console_press"));
        public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Press::pos, ByteBufCodecs.STRING_UTF8, Press::control, Press::new);

        @Override
        public Type<Press> type() {
            return TYPE;
        }
    }
}
