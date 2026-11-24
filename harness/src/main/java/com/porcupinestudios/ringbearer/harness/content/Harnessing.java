package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import java.util.function.Predicate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.porcupinestudios.ringbearer.harness.mixin.HorseInventoryAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Wearing a harness. A horse-family animal (vanilla's, the LOTR port's mounts) wears it in its saddle slot: use it on
 * the animal (a saddle there comes back to you) or put it in the slot in the animal's inventory, and take it out of
 * the slot to take it off ({@code AbstractHorseHarnessMixin}, {@code HarnessSlot}). Animals without that inventory
 * (hired wargs and spiders) take it off when you sneak and use them with an empty hand. Only harnessed animals can be
 * hitched, taking it off unhitches, and it drops when the animal dies. Worn is the synced attachment
 * {@code harness_worn} (for horses it follows the slot), so clients draw it.
 */
public final class Harnessing {
    /** On the client, whether a harness fits this animal (the server's draught data isn't there): set by the client. */
    public static volatile Predicate<Entity> clientFits = e -> false;

    private Harnessing() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Harnessing::onInteract);
        NeoForge.EVENT_BUS.addListener(Harnessing::onDrops);
    }

    public static boolean wears(Entity e) {
        return e.hasData(HarnessMod.HARNESS_WORN) && e.getData(HarnessMod.HARNESS_WORN);
    }

    /**
     * Whether this animal can wear a harness: it can pull (draught data; on the client, a harness fit) and is grown; a
     * horse-family animal must be tame, and any other animal that can have an owner (wolves, hired wargs in the LOTR
     * port) must have one.
     */
    public static boolean canWear(Entity e) {
        if (!(e instanceof LivingEntity living) || living.isBaby()) {
            return false;
        }
        if (DraughtAnimals.of(e) == null && !(e.level().isClientSide && clientFits.test(e))) {
            return false;
        }
        if (e instanceof AbstractHorse horse) {
            return horse.isTamed();
        }
        return !(e instanceof OwnableEntity owned) || owned.getOwnerUUID() != null;
    }

    /** A horse's saddle slot changed (server): worn follows it, and taking the harness out unhitches. */
    public static void slotChanged(AbstractHorse horse, boolean harnessInSlot) {
        if (wears(horse) && !harnessInSlot) {
            unhitch(horse);
        }
        if (wears(horse) != harnessInSlot) {
            horse.setData(HarnessMod.HARNESS_WORN, harnessInSlot);
        }
    }

    /**
     * Puts a harness on: in a horse's saddle slot (a saddle there comes off, to {@code player}'s inventory, else
     * dropped), or worn by any other animal. Server side. Returns false if it can't wear one or already does.
     */
    public static boolean putOn(Entity e, Player player) {
        if (wears(e) || !canWear(e)) {
            return false;
        }
        ItemStack harness = new ItemStack(HarnessMod.HARNESS.get());
        if (e instanceof AbstractHorse horse) {
            var inventory = ((HorseInventoryAccess) horse).ringbearer_harness$inventory();
            ItemStack off = inventory.getItem(0).copy();
            inventory.setItem(0, harness); // the saddle slot (its SlotAccess takes only saddles)
            if (!off.isEmpty()) {
                give(player, e, off);
            }
            if (!wears(e)) {
                e.setData(HarnessMod.HARNESS_WORN, true); // a horse whose slot isn't reachable this way
            }
        } else {
            e.setData(HarnessMod.HARNESS_WORN, true);
        }
        e.level().playSound(null, e, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.6f, 0.9f);
        return true;
    }

    /** Takes the harness off (unhitching first), to {@code player}'s inventory (null: dropped). Server side. */
    public static boolean takeOff(Entity e, Player player) {
        if (!wears(e)) {
            return false;
        }
        unhitch(e);
        if (e instanceof AbstractHorse horse) {
            var inventory = ((HorseInventoryAccess) horse).ringbearer_harness$inventory();
            if (inventory.getItem(0).is(HarnessMod.HARNESS.get())) {
                inventory.setItem(0, ItemStack.EMPTY);
            }
        }
        e.setData(HarnessMod.HARNESS_WORN, false);
        give(player, e, new ItemStack(HarnessMod.HARNESS.get()));
        e.level().playSound(null, e, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.6f, 1.2f);
        return true;
    }

    private static void unhitch(Entity e) {
        HitchLink.of(e).ifPresent(link -> {
            if (e.level().getBlockEntity(link.harness()) instanceof HitchBlockEntity hitch) {
                hitch.unhitch(e);
            } else {
                HitchLink.clear(e);
            }
        });
    }

    private static void give(Player player, Entity at, ItemStack stack) {
        if (player == null || !player.getInventory().add(stack)) {
            at.spawnAtLocation(stack);
        }
    }

    /**
     * A harness goes on an animal that can wear one (before the animal's own right-click: a hired NPC's menu would
     * take the click); sneaking with an empty hand takes it off an animal with no saddle slot; a saddle won't go on
     * over a harness.
     */
    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        ItemStack held = event.getItemStack();
        Player player = event.getEntity();
        boolean client = event.getLevel().isClientSide;
        if (!wears(target)) {
            if (held.is(HarnessMod.HARNESS.get()) && canWear(target)) {
                if (!client && putOn(target, player)) {
                    held.consume(1, player);
                }
                cancel(event, InteractionResult.sidedSuccess(client));
            }
            return;
        }
        if (held.isEmpty() && player.isSecondaryUseActive() && !(target instanceof AbstractHorse)
                && event.getHand() == InteractionHand.MAIN_HAND) {
            if (!client) {
                takeOff(target, player);
            }
            cancel(event, InteractionResult.sidedSuccess(client));
        } else if (held.is(Items.SADDLE)) {
            if (!client) {
                player.displayClientMessage(Component.translatable("message.ringbearer_harness.harnessed_no_saddle"),
                        true);
            }
            cancel(event, InteractionResult.FAIL);
        }
    }

    private static void cancel(PlayerInteractEvent.EntityInteract event, InteractionResult result) {
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    /** It drops when the animal dies (a horse's drops with its inventory, saddle slot and all). */
    private static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (wears(e) && !(e instanceof AbstractHorse)) {
            e.setData(HarnessMod.HARNESS_WORN, false);
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(e.level(), e.getX(), e.getY(), e.getZ(),
                    new ItemStack(HarnessMod.HARNESS.get())));
        }
    }
}
