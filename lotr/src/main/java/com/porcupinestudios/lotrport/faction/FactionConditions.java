package com.porcupinestudios.lotrport.faction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.ConditionType;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Condition types for TARDIS unlocks, quests, story events and colony research: {@code lotr_port:alignment},
 * {@code lotr_port:pledged} and {@code lotr_port:relation}.
 */
public final class FactionConditions {
    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(Conditions.REGISTRY_KEY, LotrPort.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<AlignmentAtLeast>> ALIGNMENT =
            TYPES.register("alignment", () -> new ConditionType<>(AlignmentAtLeast.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<Pledged>> PLEDGED =
            TYPES.register("pledged", () -> new ConditionType<>(Pledged.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<RelationIs>> RELATION =
            TYPES.register("relation", () -> new ConditionType<>(RelationIs.CODEC));

    private FactionConditions() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    /** {@code {"type": "lotr_port:alignment", "faction": "lotr_port:gondor", "min": 100}}: the player's alignment. */
    public record AlignmentAtLeast(ResourceLocation faction, float min) implements Condition {
        public static final MapCodec<AlignmentAtLeast> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("faction").forGetter(AlignmentAtLeast::faction),
                com.mojang.serialization.Codec.FLOAT.fieldOf("min").forGetter(AlignmentAtLeast::min))
                .apply(i, AlignmentAtLeast::new));

        @Override
        public ConditionType<?> type() {
            return ALIGNMENT.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return context.player().map(p -> Alignment.get(p, faction) >= min).orElse(false);
        }
    }

    /** {@code {"type": "lotr_port:pledged", "faction": "lotr_port:rohan"}}; without a faction, pledged to any. */
    public record Pledged(Optional<ResourceLocation> faction) implements Condition {
        public static final MapCodec<Pledged> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Pledged::faction))
                .apply(i, Pledged::new));

        @Override
        public ConditionType<?> type() {
            return PLEDGED.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return context.player().flatMap(Pledges::pledge)
                    .map(p -> faction.isEmpty() || faction.get().equals(p)).orElse(false);
        }
    }

    /** {@code {"type": "lotr_port:relation", "a": "lotr_port:gondor", "b": "lotr_port:mordor", "is": "enemy"}}. */
    public record RelationIs(ResourceLocation a, ResourceLocation b, Relation is) implements Condition {
        public static final MapCodec<RelationIs> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("a").forGetter(RelationIs::a),
                ResourceLocation.CODEC.fieldOf("b").forGetter(RelationIs::b),
                Relation.CODEC.fieldOf("is").forGetter(RelationIs::is))
                .apply(i, RelationIs::new));

        @Override
        public ConditionType<?> type() {
            return RELATION.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return Factions.relation(context.level().getServer(), a, b) == is;
        }
    }
}
