package com.porcupinestudios.lotrport.journey;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.network.chat.Component;

/**
 * The built-in caravan rule outcomes.
 * <ul>
 * <li>{@code lotr_port:send_caravan} {only_extras}: send this route's caravan (its goods, plus lines added by this
 * firing; with only_extras, just those);</li>
 * <li>{@code lotr_port:take} / {@code lotr_port:bring} {item, mode fixed|excess|topup, amount, cap}: add a line;</li>
 * <li>{@code lotr_port:message} {text}: tell the owner;</li>
 * <li>{@code lotr_port:people} {count}: how many colonists go this time;</li>
 * <li>{@code lotr_port:stop}: no later rule of this route runs this time.</li>
 * </ul>
 */
public final class Outcomes {
    private Outcomes() {}

    public static final Outcome.Type<Send> SEND = Outcome.register(LotrPort.id("send_caravan"), Send.CODEC);
    public static final Outcome.Type<Goods> TAKE = Outcome.register(LotrPort.id("take"), Goods.codec(true));
    public static final Outcome.Type<Goods> BRING = Outcome.register(LotrPort.id("bring"), Goods.codec(false));
    public static final Outcome.Type<Message> MESSAGE = Outcome.register(LotrPort.id("message"), Message.CODEC);
    public static final Outcome.Type<People> PEOPLE = Outcome.register(LotrPort.id("people"), People.CODEC);
    public static final Outcome.Type<Stop> STOP = Outcome.register(LotrPort.id("stop"), Stop.CODEC);

    /** Loads the built-ins (call before worlds load). */
    public static void init() {}

    public record Send(boolean onlyExtras) implements Outcome {
        static final MapCodec<Send> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("only_extras", false).forGetter(Send::onlyExtras))
                .apply(i, Send::new));

        @Override
        public Outcome.Type<?> type() {
            return SEND;
        }

        @Override
        public void run(Outcome.Run r) {
            r.send = true;
            r.extrasOnly |= onlyExtras;
        }
    }

    /** A goods line: mode "fixed" (amount), "excess" (over amount, at most cap) or "topup" (to amount, at most cap). */
    public record Goods(boolean take, String item, String mode, int amount, int cap) implements Outcome {
        static MapCodec<Goods> codec(boolean take) {
            return RecordCodecBuilder.mapCodec(i -> i.group(
                    Codec.STRING.optionalFieldOf("item", "minecraft:bread").forGetter(Goods::item),
                    Codec.STRING.optionalFieldOf("mode", "fixed").forGetter(Goods::mode),
                    Codec.INT.optionalFieldOf("amount", 64).forGetter(Goods::amount),
                    Codec.INT.optionalFieldOf("cap", 0).forGetter(Goods::cap))
                    .apply(i, (it, m, a, c) -> new Goods(take, it, m, a, c)));
        }

        public CaravanRules.Amount line() {
            return switch (mode) {
                case "excess" -> new CaravanRules.Amount(item, cap, Math.max(0, amount), -1);
                case "topup" -> new CaravanRules.Amount(item, cap, -1, Math.max(0, amount));
                default -> new CaravanRules.Amount(item, Math.max(0, amount), -1, -1);
            };
        }

        @Override
        public Outcome.Type<?> type() {
            return take ? TAKE : BRING;
        }

        @Override
        public void run(Outcome.Run r) {
            (take ? r.take : r.bring).add(line());
        }
    }

    public record Message(String text) implements Outcome {
        static final MapCodec<Message> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("text", "A caravan rule fired").forGetter(Message::text))
                .apply(i, Message::new));

        @Override
        public Outcome.Type<?> type() {
            return MESSAGE;
        }

        @Override
        public void run(Outcome.Run r) {
            r.messages.add(Component.literal(r.route.title() + ": " + text));
        }
    }

    public record People(int count) implements Outcome {
        static final MapCodec<People> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("count", 2).forGetter(People::count))
                .apply(i, People::new));

        @Override
        public Outcome.Type<?> type() {
            return PEOPLE;
        }

        @Override
        public void run(Outcome.Run r) {
            r.people = java.util.Optional.of(Math.max(1, Math.min(16, count)));
        }
    }

    public record Stop() implements Outcome {
        static final MapCodec<Stop> CODEC = MapCodec.unit(Stop::new);

        @Override
        public Outcome.Type<?> type() {
            return STOP;
        }

        @Override
        public void run(Outcome.Run r) {
            r.stop = true;
        }
    }
}
