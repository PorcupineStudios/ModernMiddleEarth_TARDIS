package com.porcupinestudios.lotrport.border;

import com.mojang.serialization.Codec;
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
 * {@code {"type": "lotr_port:territory", "holder": "lotr_port:gondor", "taken": true}}: the player stands in land
 * held by {@code holder} (any holder if left out; "lotr_port:nobody" = nobody's land); with {@code taken}, only if the
 * land did (true) or did not (false) change hands since the original map. For quests, unlocks, story and research.
 */
public final class BorderConditions {
    private static final DeferredRegister<ConditionType<?>> TYPES =
            DeferredRegister.create(Conditions.REGISTRY_KEY, LotrPort.MOD_ID);

    public static final DeferredHolder<ConditionType<?>, ConditionType<InTerritory>> TERRITORY =
            TYPES.register("territory", () -> new ConditionType<>(InTerritory.CODEC));

    private BorderConditions() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public record InTerritory(Optional<ResourceLocation> holder, Optional<Boolean> taken) implements Condition {
        public static final MapCodec<InTerritory> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("holder").forGetter(InTerritory::holder),
                Codec.BOOL.optionalFieldOf("taken").forGetter(InTerritory::taken))
                .apply(i, InTerritory::new));

        @Override
        public ConditionType<?> type() {
            return TERRITORY.get();
        }

        @Override
        public boolean test(ConditionContext context) {
            return context.player().map(p -> {
                Optional<ResourceLocation> now = Borders.ownerAt(p.serverLevel(), p.blockPosition());
                Optional<ResourceLocation> was = Borders.baseOwnerAt(p.serverLevel(), p.blockPosition());
                boolean holderOk = holder.isEmpty() ? now.isPresent()
                        : holder.get().equals(Borders.NOBODY) ? now.isEmpty() : now.equals(holder);
                boolean takenOk = taken.isEmpty() || taken.get() == !now.equals(was);
                return holderOk && takenOk;
            }).orElse(false);
        }
    }
}
