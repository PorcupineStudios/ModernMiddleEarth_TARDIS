package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Wearing a harness: it goes on in place of a saddle (a saddled horse's saddle comes off; a harnessed horse can't be
 * saddled, see {@code AbstractHorseSaddleMixin}), only harnessed animals can be hitched, shears take it off, and it
 * drops when the animal dies. Worn is the synced attachment {@code harness_worn}, so clients draw it.
 */
public final class Harnessing {
    /** Horses' saddle slot, as {@link Entity#getSlot} numbers it. */
    private static final int SADDLE_SLOT = 400;

    private Harnessing() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Harnessing::onInteract);
        NeoForge.EVENT_BUS.addListener(Harnessing::onDrops);
    }

    public static boolean wears(Entity e) {
        return e.hasData(HarnessMod.HARNESS_WORN) && e.getData(HarnessMod.HARNESS_WORN);
    }

    /** Whether this animal can wear a harness: it can pull (draught data), and a horse must be tame and grown. */
    public static boolean canWear(Entity e) {
        if (!(e instanceof LivingEntity living) || living.isBaby() || DraughtAnimals.of(e) == null) {
            return false;
        }
        return !(e instanceof AbstractHorse horse) || horse.isTamed();
    }

    /**
     * Puts a harness on: the saddle, if any, comes off (to {@code player}'s inventory, else dropped). Server side.
     * Returns false if it can't wear one or already does.
     */
    public static boolean putOn(Entity e, Player player) {
        if (wears(e) || !canWear(e)) {
            return false;
        }
        SlotAccess saddle = e.getSlot(SADDLE_SLOT);
        if (saddle != SlotAccess.NULL && saddle.get().is(Items.SADDLE)) {
            ItemStack off = saddle.get().copy();
            saddle.set(ItemStack.EMPTY);
            if (player == null || !player.getInventory().add(off)) {
                e.spawnAtLocation(off);
            }
        }
        e.setData(HarnessMod.HARNESS_WORN, true);
        e.level().playSound(null, e, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.6f, 0.9f);
        return true;
    }

    /** Takes the harness off (unhitching first) and drops it. Server side. */
    public static boolean takeOff(Entity e) {
        if (!wears(e)) {
            return false;
        }
        HitchLink.of(e).ifPresent(link -> {
            if (e.level().getBlockEntity(link.harness()) instanceof HitchBlockEntity hitch) {
                hitch.unhitch(e);
            } else {
                HitchLink.clear(e);
            }
        });
        e.setData(HarnessMod.HARNESS_WORN, false);
        e.spawnAtLocation(new ItemStack(HarnessMod.HARNESS.get()));
        e.level().playSound(null, e, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 0.8f, 1.0f);
        return true;
    }

    /** Shears take a harness off; a saddle won't go on a harnessed horse (the mixin also keeps the slot shut). */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!wears(target)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (held.is(Items.SHEARS)) {
            if (!event.getLevel().isClientSide && takeOff(target)) {
                held.hurtAndBreak(1, event.getEntity(), LivingEntity.getSlotForHand(event.getHand()));
            }
            event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            event.setCanceled(true);
        } else if (held.is(Items.SADDLE)) {
            if (!event.getLevel().isClientSide) {
                event.getEntity().displayClientMessage(
                        Component.translatable("message.ringbearer_harness.harnessed_no_saddle"), true);
            }
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
        }
    }

    private static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (wears(e)) {
            e.setData(HarnessMod.HARNESS_WORN, false);
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(e.level(), e.getX(), e.getY(), e.getZ(),
                    new ItemStack(HarnessMod.HARNESS.get())));
        }
    }
}
