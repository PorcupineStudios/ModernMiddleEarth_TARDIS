package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.flight.Destinations;
import com.porcupinestudios.tardisrb.flight.Flight;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The console screen's packets: the server sends what the console shows ({@link ConsoleView}); the client asks for a
 * hop ({@link Hop}) or a jump to a destination ({@link Jump}), or clicks a control on the console model ({@link Press}). The screen is opened through {@link #clientHandler}, set by client setup.
 */
public final class ConsoleNet {
    public static BiConsumer<ConsoleView, IPayloadContext> clientHandler = (view, ctx) -> { };

    private ConsoleNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(ConsoleNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(ConsoleView.TYPE, ConsoleView.CODEC,
                (p, ctx) -> ctx.enqueueWork(() -> clientHandler.accept(p, ctx)));
        r.playToServer(Hop.TYPE, Hop.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                hop(sp, new BlockPos(p.x(), p.y(), p.z()));
            }
        }));
        r.playToServer(Press.TYPE, Press.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                ConsoleControls.press(sp, p.pos(), p.control()).filter(c -> !c.getString().isEmpty())
                        .ifPresent(c -> sp.displayClientMessage(c, true));
            }
        }));
        r.playToServer(Jump.TYPE, Jump.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                jump(sp, p.destination(), new BlockPos(p.x(), p.y(), p.z()));
            }
        }));
    }

    public static void open(ServerPlayer player, TardisRecord tardis) {
        PacketDistributor.sendToPlayer(player, view(player, tardis, ""));
    }

    /** A hop asked for from the console the player stands at. */
    static void hop(ServerPlayer player, BlockPos target) {
        Optional<TardisRecord> tardis = Interiors.at(player.serverLevel(), player.blockPosition());
        if (tardis.isEmpty() || !tardis.get().isCrew(player.getUUID())) {
            return;
        }
        reply(player, tardis.get(), Flight.hop(player.server, tardis.get(), target, Optional.of(player)));
    }

    /** A jump to a destination asked for from the console. */
    static void jump(ServerPlayer player, ResourceLocation destination, BlockPos target) {
        Optional<TardisRecord> tardis = Interiors.at(player.serverLevel(), player.blockPosition());
        if (tardis.isEmpty() || !tardis.get().isCrew(player.getUUID())) {
            return;
        }
        reply(player, tardis.get(), Flight.jump(player.server, tardis.get(), destination, Optional.of(target),
                Optional.of(player)));
    }

    private static void reply(ServerPlayer player, TardisRecord tardis, Flight.Outcome out) {
        String message = out.done()
                ? Component.translatableWithFallback("tardis_rb.hop.done", "The TARDIS lands at %s %s %s",
                        out.landed().get().pos().getX(), out.landed().get().pos().getY(),
                        out.landed().get().pos().getZ()).getString()
                : out.problem().get().getString();
        PacketDistributor.sendToPlayer(player, view(player, tardis, message));
    }

    static ConsoleView view(ServerPlayer player, TardisRecord t, String message) {
        BlockPos at = t.location().map(g -> g.pos()).orElse(BlockPos.ZERO);
        String dim = t.location().map(g -> g.dimension().location().toString()).orElse("-");
        boolean canHop = Unlocks.check(player.server, t, Flight.SHORT_HOP, Optional.of(player)).allowed();
        return new ConsoleView(t.name(), dim, at, t.charge(), Unlocks.chargeCap(t),
                (int) Unlocks.stat(t, "range"), canHop, message,
                Destinations.open(player.server, t, Optional.of(player)));
    }

    /** What the console shows: where the TARDIS is, its charge and hop range, the last result, open destinations. */
    public record ConsoleView(String name, String dimension, BlockPos pos, long charge, long chargeCap, int range,
            boolean canHop, String message, List<ResourceLocation> destinations) implements CustomPacketPayload {
        public static final Type<ConsoleView> TYPE = new Type<>(TardisRB.id("console_view"));
        public static final StreamCodec<ByteBuf, ConsoleView> CODEC = StreamCodec.of(ConsoleView::write,
                ConsoleView::read);

        private static void write(ByteBuf buf, ConsoleView v) {
            ByteBufCodecs.STRING_UTF8.encode(buf, v.name);
            ByteBufCodecs.STRING_UTF8.encode(buf, v.dimension);
            BlockPos.STREAM_CODEC.encode(buf, v.pos);
            ByteBufCodecs.VAR_LONG.encode(buf, v.charge);
            ByteBufCodecs.VAR_LONG.encode(buf, v.chargeCap);
            ByteBufCodecs.VAR_INT.encode(buf, v.range);
            ByteBufCodecs.BOOL.encode(buf, v.canHop);
            ByteBufCodecs.STRING_UTF8.encode(buf, v.message);
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, v.destinations);
        }

        private static ConsoleView read(ByteBuf buf) {
            return new ConsoleView(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    BlockPos.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                    ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf));
        }

        @Override
        public Type<ConsoleView> type() {
            return TYPE;
        }
    }

    /** The client asks for a jump to a destination, landing near these coordinates. */
    public record Jump(ResourceLocation destination, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<Jump> TYPE = new Type<>(TardisRB.id("jump"));
        public static final StreamCodec<ByteBuf, Jump> CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Jump::destination, ByteBufCodecs.VAR_INT, Jump::x,
                ByteBufCodecs.VAR_INT, Jump::y, ByteBufCodecs.VAR_INT, Jump::z, Jump::new);

        @Override
        public Type<Jump> type() {
            return TYPE;
        }
    }

    /** The client clicked a control on the console at {@code pos}. */
    public record Press(BlockPos pos, String control) implements CustomPacketPayload {
        public static final Type<Press> TYPE = new Type<>(TardisRB.id("console_press"));
        public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Press::pos, ByteBufCodecs.STRING_UTF8, Press::control, Press::new);

        @Override
        public Type<Press> type() {
            return TYPE;
        }
    }

    /** The client asks for a short hop to these coordinates. */
    public record Hop(int x, int y, int z) implements CustomPacketPayload {
        public static final Type<Hop> TYPE = new Type<>(TardisRB.id("hop"));
        public static final StreamCodec<ByteBuf, Hop> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hop::x, ByteBufCodecs.VAR_INT, Hop::y, ByteBufCodecs.VAR_INT, Hop::z, Hop::new);

        @Override
        public Type<Hop> type() {
            return TYPE;
        }
    }
}
