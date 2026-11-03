package com.porcupinestudios.lotrport.net;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.quest.ActiveQuest;
import com.porcupinestudios.lotrport.quest.QuestLog;
import com.porcupinestudios.lotrport.quest.Quests;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The Red Book's packets. An NPC with a quest for the player sends an {@link Offer} (the client shows LOTR's quest
 * offer page) and the player answers with {@link Answer}. Using the Red Book sends the player's quests as a
 * {@link Book}; {@link Abandon} drops one and sends the book again. Client handlers are set by client setup, so common
 * code never names a screen.
 */
public final class QuestNet {
    /** Shows a quest offer; set by the client. Does nothing on a dedicated server. */
    public static Consumer<Offer> offerHandler = offer -> { };
    /** Opens or refreshes the Red Book; set by the client. */
    public static Consumer<Book> bookHandler = book -> { };

    private QuestNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(QuestNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(Offer.TYPE, Offer.CODEC, (p, ctx) -> ctx.enqueueWork(() -> offerHandler.accept(p)));
        r.playToClient(Book.TYPE, Book.CODEC, (p, ctx) -> ctx.enqueueWork(() -> bookHandler.accept(p)));
        r.playToServer(Answer.TYPE, Answer.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Quests.answer(sp, p.accept());
            }
        }));
        r.playToServer(Abandon.TYPE, Abandon.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Quests.abandon(sp, p.index());
                sendBook(sp, false);
            }
        }));
    }

    /** Sends the player's quests; {@code open} opens the book, otherwise only an open book refreshes. */
    public static void sendBook(ServerPlayer player, boolean open) {
        QuestLog log = Quests.log(player);
        List<Entry> entries = new ArrayList<>();
        for (ActiveQuest q : log.active()) {
            entries.add(new Entry(q.giverName(), Quests.factionName(q.faction()), Quests.describe(q),
                    Quests.progress(player, q), q.count(), q.coins(), q.alignment()));
        }
        PacketDistributor.sendToPlayer(player, new Book(entries, log.completed(), open));
    }

    /** One open quest as the book shows it. */
    public record Entry(String giver, Component faction, Component task, int progress, int count, int coins,
            float alignment) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::giver,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::faction,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::task,
                ByteBufCodecs.VAR_INT, Entry::progress,
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.VAR_INT, Entry::coins,
                ByteBufCodecs.FLOAT, Entry::alignment,
                Entry::new);
    }

    /** The player's open quests and how many they have finished. */
    public record Book(List<Entry> quests, int completed, boolean open) implements CustomPacketPayload {
        public static final Type<Book> TYPE = new Type<>(LotrPort.id("red_book"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Book> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), Book::quests,
                ByteBufCodecs.VAR_INT, Book::completed,
                ByteBufCodecs.BOOL, Book::open,
                Book::new);

        @Override
        public Type<Book> type() {
            return TYPE;
        }
    }

    /** A quest an NPC asks the player to take; {@code npc} is the giver's entity id, drawn on the page. */
    public record Offer(int npc, String giver, Component faction, Component task, int coins, float alignment)
            implements CustomPacketPayload {
        public static final Type<Offer> TYPE = new Type<>(LotrPort.id("quest_offer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Offer::npc,
                ByteBufCodecs.STRING_UTF8, Offer::giver,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Offer::faction,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Offer::task,
                ByteBufCodecs.VAR_INT, Offer::coins,
                ByteBufCodecs.FLOAT, Offer::alignment,
                Offer::new);

        @Override
        public Type<Offer> type() {
            return TYPE;
        }
    }

    /** The player takes or turns down the offered quest. */
    public record Answer(boolean accept) implements CustomPacketPayload {
        public static final Type<Answer> TYPE = new Type<>(LotrPort.id("quest_answer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Answer> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Answer::accept, Answer::new);

        @Override
        public Type<Answer> type() {
            return TYPE;
        }
    }

    /** The player drops an open quest from the book. */
    public record Abandon(int index) implements CustomPacketPayload {
        public static final Type<Abandon> TYPE = new Type<>(LotrPort.id("quest_abandon"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Abandon> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Abandon::index, Abandon::new);

        @Override
        public Type<Abandon> type() {
            return TYPE;
        }
    }
}
