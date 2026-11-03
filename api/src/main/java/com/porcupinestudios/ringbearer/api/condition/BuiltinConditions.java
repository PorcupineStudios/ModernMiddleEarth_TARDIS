package com.porcupinestudios.ringbearer.api.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;

/** The condition types every mod can use: always, all, any, not. */
public final class BuiltinConditions {
    private BuiltinConditions() {}

    /** Always true. Useful as a default and in tests. */
    public record Always() implements Condition {
        public static final MapCodec<Always> CODEC = MapCodec.unit(Always::new);

        @Override
        public ConditionType<?> type() {
            return Conditions.ALWAYS.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return true;
        }
    }

    /** True when every listed condition is true (and when the list is empty). */
    public record All(List<Condition> conditions) implements Condition {
        public static final MapCodec<All> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Conditions.CODEC.listOf().fieldOf("conditions").forGetter(All::conditions))
                .apply(i, All::new));

        @Override
        public ConditionType<?> type() {
            return Conditions.ALL.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            for (Condition c : conditions) {
                if (!c.test(context)) {
                    return false;
                }
            }
            return true;
        }
    }

    /** True when at least one listed condition is true. */
    public record Any(List<Condition> conditions) implements Condition {
        public static final MapCodec<Any> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Conditions.CODEC.listOf().fieldOf("conditions").forGetter(Any::conditions))
                .apply(i, Any::new));

        @Override
        public ConditionType<?> type() {
            return Conditions.ANY.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            for (Condition c : conditions) {
                if (c.test(context)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** True when the inner condition is false. */
    public record Not(Condition condition) implements Condition {
        public static final MapCodec<Not> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Conditions.CODEC.fieldOf("condition").forGetter(Not::condition))
                .apply(i, Not::new));

        @Override
        public ConditionType<?> type() {
            return Conditions.NOT.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return !condition.test(context);
        }
    }
}
