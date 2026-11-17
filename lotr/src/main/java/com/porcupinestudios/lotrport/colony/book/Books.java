package com.porcupinestudios.lotrport.colony.book;

import com.porcupinestudios.lotrport.LotrPort;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The colony books: providers by kind, and three payloads. {@link Show} carries a whole {@link Book} to the client;
 * {@link Open} asks for a book (a tab change, a link); {@link Click} sends a click, after which the server answers with
 * the book built afresh (or another one) and the click's message.
 */
public final class Books {
    private static final Map<String, BookProvider> PROVIDERS = new HashMap<>();
    /** Set by the client: shows a book. */
    public static Consumer<Book> showHandler = b -> { };

    private Books() {}

    public static void register(String kind, BookProvider p) {
        PROVIDERS.put(kind, p);
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(Books::payloads);
        PlaqueBook.register();
        HallLedgerBook.register();
        TreasuryBook.register();
        CustomsBook.register();
        ColonyBlocks.register(modBus);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Show.TYPE, Show.CODEC, (p, ctx) -> ctx.enqueueWork(() -> showHandler.accept(p.book())));
        r.playToServer(Open.TYPE, Open.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                open(sp, p.kind(), p.context(), p.tab(), "");
            }
        }));
        r.playToServer(Click.TYPE, Click.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                click(sp, p);
            }
        }));
    }

    /** Builds and sends a book; false when the player may not see it. */
    public static boolean open(ServerPlayer player, String kind, String context, String tab, String message) {
        BookProvider p = PROVIDERS.get(kind);
        if (p == null) {
            return false;
        }
        Optional<Book> book = p.build(player, context, tab);
        book.ifPresent(b -> PacketDistributor.sendToPlayer(player, new Show(b.withMessage(message))));
        return book.isPresent();
    }

    static void click(ServerPlayer player, Click c) {
        BookProvider p = PROVIDERS.get(c.kind());
        if (p == null) {
            return;
        }
        BookProvider.Result r = p.act(player, c.context(), c.tab(), c.action(), c.arg(), c.num());
        if (r.go().isPresent()) {
            BookProvider.Target t = r.go().get();
            open(player, t.kind(), t.context(), t.tab(), r.message());
        } else {
            open(player, c.kind(), c.context(), c.tab(), r.message());
        }
    }

    /** Server to client: the book to show. */
    public record Show(Book book) implements CustomPacketPayload {
        public static final Type<Show> TYPE = new Type<>(LotrPort.id("book_show"));
        public static final StreamCodec<ByteBuf, Show> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(
                t -> new Show(Book.load(t)), s -> s.book().save());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: show me this book (and tab). */
    public record Open(String kind, String context, String tab) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(LotrPort.id("book_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Open::kind, ByteBufCodecs.STRING_UTF8, Open::context,
                ByteBufCodecs.STRING_UTF8, Open::tab, Open::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a click in a book. */
    public record Click(String kind, String context, String tab, String action, String arg, int num)
            implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(LotrPort.id("book_click"));
        public static final StreamCodec<ByteBuf, Click> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Click::kind, ByteBufCodecs.STRING_UTF8, Click::context,
                ByteBufCodecs.STRING_UTF8, Click::tab, ByteBufCodecs.STRING_UTF8, Click::action,
                ByteBufCodecs.STRING_UTF8, Click::arg, ByteBufCodecs.VAR_INT, Click::num, Click::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
