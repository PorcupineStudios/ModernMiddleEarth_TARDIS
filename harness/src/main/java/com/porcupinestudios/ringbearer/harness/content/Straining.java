package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimal;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * A hitched animal working hard slows down: a movement speed modifier that grows smoothly with its strain (pull against
 * its limit, or stretch against its slack). Attributes sync to clients, so a ridden horse slows on the rider's side
 * too, instead of being snapped back by the server.
 */
public final class Straining {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(HarnessMod.MOD_ID, "straining");
    /** strain under this costs nothing */
    private static final double FREE = 0.4;

    private Straining() {}

    /**
     * The speed multiplier at {@code strain} (0..1): 1 up to 0.4, then down in a straight line to 1 - load_slowing at
     * full strain (a straight line: a steep middle made the slowing itself swing).
     */
    public static double factor(DraughtAnimal d, double strain) {
        double u = Mth.clamp((strain - FREE) / (1 - FREE), 0, 1);
        return 1 - d.loadSlowing() * u;
    }

    static void apply(Entity e, DraughtAnimal d, double strain) {
        if (!(e instanceof LivingEntity living)) {
            return;
        }
        AttributeInstance speed = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        double amount = factor(d, strain) - 1;
        AttributeModifier current = speed.getModifier(ID);
        if (amount > -1e-3) {
            if (current != null) {
                speed.removeModifier(ID);
            }
        } else if (current == null || Math.abs(current.amount() - amount) > 1e-3) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(ID, amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    static void clear(Entity e) {
        if (e instanceof LivingEntity living && living.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            living.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ID);
        }
    }
}
