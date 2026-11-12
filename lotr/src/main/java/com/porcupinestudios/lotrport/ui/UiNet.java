package com.porcupinestudios.lotrport.ui;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The UI core's four payloads: the client asks for a view ({@link Open}) or acts in one ({@link Act}); the server
 * sends a view ({@link Show}) or says it may no longer be seen ({@link Close}). Every screen uses these four.
 */
public final class UiNet {
    /** Protocol version of these payloads and {@link UiCodecs}. */
    public static final String VERSION = "6";

    /** Set by the client at setup (common code never names the screen). */
    public static Consumer<Show> showHandler = s -> { };
    public static Consumer<Close> closeHandler = c -> { };

    private UiNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(UiNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar(VERSION);
        r.playToClient(Show.TYPE, Show.CODEC, (p, ctx) -> ctx.enqueueWork(() -> showHandler.accept(p)));
        r.playToClient(Close.TYPE, Close.CODEC, (p, ctx) -> ctx.enqueueWork(() -> closeHandler.accept(p)));
        r.playToServer(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Views.open(sp, p.target(), p.open());
            }
        }));
        r.playToServer(Act.TYPE, Act.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Views.act(sp, p);
            }
        }));
    }

    /** Client → server: send me this view ({@code open} = open the screen, else refresh it). */
    public record Open(View.Target target, boolean open) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(LotrPort.id("ui_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                UiCodecs.TARGET, Open::target, ByteBufCodecs.BOOL, Open::open, Open::new);

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Client → server: do this action in this view (with the selected row and a stepper's value). */
    public record Act(View.Target target, String action, String row, int value) implements CustomPacketPayload {
        public static final Type<Act> TYPE = new Type<>(LotrPort.id("ui_act"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Act> CODEC = StreamCodec.composite(
                UiCodecs.TARGET, Act::target, ByteBufCodecs.STRING_UTF8, Act::action, ByteBufCodecs.STRING_UTF8,
                Act::row, ByteBufCodecs.VAR_INT, Act::value, Act::new);

        @Override
        public Type<Act> type() {
            return TYPE;
        }
    }

    /** Server → client: a view to show. */
    public record Show(View view, boolean open) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(LotrPort.id("ui_show"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Show> CODEC = StreamCodec.composite(
                UiCodecs.VIEW, Show::view, ByteBufCodecs.BOOL, Show::open, Show::new);

        @Override
        public Type<Show> type() {
            return TYPE;
        }
    }

    /** Server → client: this view may no longer be seen; a screen showing it closes. */
    public record Close(ResourceLocation view, String context) implements CustomPacketPayload {
        public static final Type<Close> TYPE = new Type<>(LotrPort.id("ui_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Close::view, ByteBufCodecs.STRING_UTF8, Close::context, Close::new);

        @Override
        public Type<Close> type() {
            return TYPE;
        }
    }
}
