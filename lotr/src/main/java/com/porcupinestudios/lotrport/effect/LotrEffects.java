package com.porcupinestudios.lotrport.effect;

import com.porcupinestudios.lotrport.LotrPort;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The port's own status effects. Their numbers per use (duration, strength, chance) are data: {@link RegionEffects}.
 * <ul>
 * <li>{@code drowsy}: the heavy air of Enchanted Mirkwood (The Hobbit: the dwarves grow drowsy and lose their way):
 * 15% slower and 20% slower digging per level.</li>
 * <li>{@code enchanted_sleep}: the sleep of the Enchanted River (Bombur fell in and slept for days, dreaming of
 * feasts): no walking, jumping or digging while it lasts. Data pairs it with blindness and, on waking, a muddled
 * moment and a turn about (the dwarves woke not knowing where they were).</li>
 * </ul>
 */
public final class LotrEffects {
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT,
            LotrPort.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> DROWSY = EFFECTS.register("drowsy",
            () -> new Plain(MobEffectCategory.HARMFUL, 0x5D6B4A)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LotrPort.id("effect.drowsy"), -0.15,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, LotrPort.id("effect.drowsy"), -0.2,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    public static final DeferredHolder<MobEffect, MobEffect> ENCHANTED_SLEEP = EFFECTS.register("enchanted_sleep",
            () -> new Plain(MobEffectCategory.HARMFUL, 0x101810)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, LotrPort.id("effect.enchanted_sleep"), -1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, LotrPort.id("effect.enchanted_sleep"), -1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.BLOCK_BREAK_SPEED, LotrPort.id("effect.enchanted_sleep"), -1.0,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private LotrEffects() {}

    public static void register(IEventBus modBus) {
        EFFECTS.register(modBus);
    }

    /** An effect that only carries its attribute changes (MobEffect's constructor is protected). */
    private static final class Plain extends MobEffect {
        Plain(MobEffectCategory category, int color) {
            super(category, color);
        }
    }
}
