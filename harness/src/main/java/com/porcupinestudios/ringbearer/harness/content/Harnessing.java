package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.draught.DraughtAnimals;
import com.porcupinestudios.ringbearer.harness.mixin.HorseInventoryAccess;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

/**
 * Wearing a harness. It has a slot of its own, Curios slot {@code harness} (given to every animal that can pull by
 * {@code data/ringbearer_harness/curios/entities/}), so an animal wears a saddle and a harness together (Aaron
 * 2026-11-25): ridden and pulling at once. Use a harness on the animal to put it on; a horse-family animal also shows
 * the slot in its inventory, under the armour (put it in, take it out there); animals with no inventory (hired wargs,
 * spiders) take it off when you sneak and use them with an empty hand. Only harnessed animals can be hitched, taking it
 * off unhitches, and Curios drops it when the animal dies. Worn is also the synced attachment {@code harness_worn}
 * (renderers and the hitch read it), kept to the slot by {@link CurioChangeEvent}, when an animal joins a level, and
 * by {@link #putOn}/{@link #takeOff}.
 */
public final class Harnessing {
    public static final String SLOT = "harness";
    /** On the client, whether a harness fits this animal (the server's draught data isn't there): set by the client. */
    public static volatile Predicate<Entity> clientFits = e -> false;

    private Harnessing() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Harnessing::onInteract);
        NeoForge.EVENT_BUS.addListener(Harnessing::onDrops);
        NeoForge.EVENT_BUS.addListener(Harnessing::onCurioChange);
        NeoForge.EVENT_BUS.addListener(Harnessing::onJoin);
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

    /** The animal's harness slot (Curios), if it has one. */
    public static Optional<IItemHandlerModifiable> slot(Entity e) {
        if (!(e instanceof LivingEntity living)) {
            return Optional.empty();
        }
        return CuriosApi.getCuriosInventory(living).flatMap(inv -> inv.getStacksHandler(SLOT))
                .map(ICurioStacksHandler::getStacks).filter(h -> h.getSlots() > 0).map(h -> h);
    }

    /**
     * Puts a harness on, in its slot (or, for an animal Curios gave no slot, as the attachment alone). Server side.
     * False if it can't wear one or already does.
     */
    public static boolean putOn(Entity e, Player player) {
        if (wears(e) || !canWear(e)) {
            return false;
        }
        slot(e).ifPresent(s -> s.setStackInSlot(0, new ItemStack(HarnessMod.HARNESS.get())));
        e.setData(HarnessMod.HARNESS_WORN, true);
        e.level().playSound(null, e, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.6f, 0.9f);
        return true;
    }

    /** Takes the harness off (unhitching first), to {@code player}'s inventory (null: dropped). Server side. */
    public static boolean takeOff(Entity e, Player player) {
        if (!wears(e)) {
            return false;
        }
        unhitch(e);
        slot(e).ifPresent(s -> s.setStackInSlot(0, ItemStack.EMPTY));
        e.setData(HarnessMod.HARNESS_WORN, false);
        give(player, e, new ItemStack(HarnessMod.HARNESS.get()));
        e.level().playSound(null, e, SoundEvents.HORSE_SADDLE, SoundSource.NEUTRAL, 0.6f, 1.2f);
        return true;
    }

    /** The slot's contents changed (Curios, or the inventory screen's slot): worn follows, and taking it out unhitches. */
    public static void slotChanged(Entity e, boolean harnessInSlot) {
        if (e.level().isClientSide) {
            return;
        }
        if (wears(e) && !harnessInSlot) {
            unhitch(e);
        }
        if (wears(e) != harnessInSlot) {
            e.setData(HarnessMod.HARNESS_WORN, harnessInSlot);
        }
    }

    static void unhitch(Entity e) {
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

    private static void onCurioChange(CurioChangeEvent event) {
        if (SLOT.equals(event.getIdentifier())) {
            slotChanged(event.getEntity(), event.getTo().is(HarnessMod.HARNESS.get()));
        }
    }

    /**
     * Keeps worn and the slot together as an animal is loaded, and moves harnesses from before the slot (2026-11-25):
     * worn in a horse's saddle slot (2026-11-24 builds), or worn as the attachment alone (the first build).
     */
    private static void onJoin(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !(e instanceof LivingEntity)) {
            return;
        }
        Optional<IItemHandlerModifiable> slot = slot(e);
        if (slot.isEmpty()) {
            return;
        }
        IItemHandlerModifiable s = slot.get();
        if (e instanceof AbstractHorse horse) {
            var inventory = ((HorseInventoryAccess) horse).ringbearer_harness$inventory();
            if (inventory.getItem(0).is(HarnessMod.HARNESS.get())) {
                ItemStack moved = inventory.removeItemNoUpdate(0);
                if (s.getStackInSlot(0).isEmpty()) {
                    s.setStackInSlot(0, moved);
                } else {
                    e.spawnAtLocation(moved);
                }
                inventory.setChanged();
            }
        }
        boolean inSlot = s.getStackInSlot(0).is(HarnessMod.HARNESS.get());
        if (wears(e) && !inSlot && s.getStackInSlot(0).isEmpty()) {
            s.setStackInSlot(0, new ItemStack(HarnessMod.HARNESS.get()));
            inSlot = true;
        }
        if (wears(e) != inSlot) {
            e.setData(HarnessMod.HARNESS_WORN, inSlot);
        }
    }

    /**
     * A harness goes on an animal that can wear one (before the animal's own right-click: a hired NPC's menu would
     * take the click); sneaking with an empty hand takes it off an animal with no inventory to take it out of.
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
        }
    }

    private static void cancel(PlayerInteractEvent.EntityInteract event, InteractionResult result) {
        event.setCancellationResult(result);
        event.setCanceled(true);
    }

    /** Curios drops what's in the slot; an animal with no slot drops the harness it wore. */
    private static void onDrops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (wears(e) && slot(e).isEmpty()) {
            e.setData(HarnessMod.HARNESS_WORN, false);
            event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(e.level(), e.getX(), e.getY(), e.getZ(),
                    new ItemStack(HarnessMod.HARNESS.get())));
        }
    }
}
