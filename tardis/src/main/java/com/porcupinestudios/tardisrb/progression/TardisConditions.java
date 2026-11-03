package com.porcupinestudios.tardisrb.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.condition.Condition;
import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.ringbearer.api.condition.ConditionType;
import com.porcupinestudios.ringbearer.api.condition.Conditions;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Condition types about a TARDIS, usable in any data file that takes conditions: {@code tardis_rb:stage},
 * {@code milestone}, {@code subsystem_level}, {@code repair}, {@code research} and {@code mod_loaded}. The TARDIS comes
 * from the context's extra map under the mod id ({@link Unlocks#context}); without one they are false.
 */
public final class TardisConditions {
    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(Conditions.REGISTRY_KEY, TardisRB.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<StageReached>> STAGE =
            TYPES.register("stage", () -> new ConditionType<>(StageReached.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<MilestonePassed>> MILESTONE =
            TYPES.register("milestone", () -> new ConditionType<>(MilestonePassed.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<SubsystemLevel>> SUBSYSTEM_LEVEL =
            TYPES.register("subsystem_level", () -> new ConditionType<>(SubsystemLevel.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<RepairDone>> REPAIR =
            TYPES.register("repair", () -> new ConditionType<>(RepairDone.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<ResearchDone>> RESEARCH =
            TYPES.register("research", () -> new ConditionType<>(ResearchDone.CODEC));
    public static final DeferredHolder<ConditionType<?>, ConditionType<ModLoaded>> MOD_LOADED =
            TYPES.register("mod_loaded", () -> new ConditionType<>(ModLoaded.CODEC));

    private TardisConditions() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static Optional<TardisRecord> tardis(ConditionContext context) {
        return context.extra().get(TardisRB.MOD_ID) instanceof TardisRecord r ? Optional.of(r) : Optional.empty();
    }

    /**
     * {@code {"type": "tardis_rb:stage", "stage": "tardis_rb:patched", "finished": false}}: the TARDIS is in that stage
     * or a later one; with {@code finished}, that stage's repairs are also all done.
     */
    public record StageReached(ResourceLocation stage, boolean finished) implements Condition {
        public static final MapCodec<StageReached> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("stage").forGetter(StageReached::stage),
                Codec.BOOL.optionalFieldOf("finished", false).forGetter(StageReached::finished))
                .apply(i, StageReached::new));

        @Override
        public ConditionType<?> type() {
            return STAGE.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return tardis(context).map(t -> {
                TardisContent.Snapshot data = TardisContent.get();
                Stage wanted = data.stages().get(stage);
                Stage now = t.stage().map(data.stages()::get).orElse(null);
                if (wanted == null || now == null || now.order() < wanted.order()) {
                    return false;
                }
                return !finished || now.order() > wanted.order() || Progression.finished(t, wanted);
            }).orElse(false);
        }
    }

    /** {@code {"type": "tardis_rb:milestone", "milestone": "..."}}. */
    public record MilestonePassed(ResourceLocation milestone) implements Condition {
        public static final MapCodec<MilestonePassed> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("milestone").forGetter(MilestonePassed::milestone))
                .apply(i, MilestonePassed::new));

        @Override
        public ConditionType<?> type() {
            return MILESTONE.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return tardis(context).map(t -> t.milestones().contains(milestone)).orElse(false);
        }
    }

    /** {@code {"type": "tardis_rb:subsystem_level", "subsystem": "tardis_rb:navigation", "min": 2}}. */
    public record SubsystemLevel(ResourceLocation subsystem, int min) implements Condition {
        public static final MapCodec<SubsystemLevel> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("subsystem").forGetter(SubsystemLevel::subsystem),
                Codec.INT.fieldOf("min").forGetter(SubsystemLevel::min))
                .apply(i, SubsystemLevel::new));

        @Override
        public ConditionType<?> type() {
            return SUBSYSTEM_LEVEL.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return tardis(context).map(t -> Unlocks.level(t, subsystem) >= min).orElse(false);
        }
    }

    /** {@code {"type": "tardis_rb:repair", "repair": "..."}}: done and not undone by damage. */
    public record RepairDone(ResourceLocation repair) implements Condition {
        public static final MapCodec<RepairDone> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("repair").forGetter(RepairDone::repair))
                .apply(i, RepairDone::new));

        @Override
        public ConditionType<?> type() {
            return REPAIR.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            Repair r = TardisContent.get().repairs().get(repair);
            return r != null && tardis(context).map(t -> Unlocks.level(t, r.subsystem()) >= r.level()).orElse(false);
        }
    }

    /** {@code {"type": "tardis_rb:research", "research": "..."}}. */
    public record ResearchDone(ResourceLocation research) implements Condition {
        public static final MapCodec<ResearchDone> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.fieldOf("research").forGetter(ResearchDone::research))
                .apply(i, ResearchDone::new));

        @Override
        public ConditionType<?> type() {
            return RESEARCH.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return tardis(context).map(t -> t.research().contains(research)).orElse(false);
        }
    }

    /** {@code {"type": "tardis_rb:mod_loaded", "mod": "create"}}. */
    public record ModLoaded(String mod) implements Condition {
        public static final MapCodec<ModLoaded> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("mod").forGetter(ModLoaded::mod))
                .apply(i, ModLoaded::new));

        @Override
        public ConditionType<?> type() {
            return MOD_LOADED.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return ModList.get().isLoaded(mod);
        }
    }
}
