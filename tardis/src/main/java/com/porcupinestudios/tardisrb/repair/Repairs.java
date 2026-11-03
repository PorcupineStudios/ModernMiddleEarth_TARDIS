package com.porcupinestudios.tardisrb.repair;

import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.RepairEvent;
import com.porcupinestudios.tardisrb.progression.Cost;
import com.porcupinestudios.tardisrb.progression.Progression;
import com.porcupinestudios.tardisrb.progression.Repair;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Doing a repair: checks it is the next level of its subsystem, under the milestone cap, and that its requirements
 * hold; fires {@link RepairEvent.Pre} (which may cancel or change the cost); takes the items from the player and the
 * charge from the TARDIS; applies it; then moves the TARDIS along its stages.
 */
public final class Repairs {
    /** {@code problem} is empty when the repair was done. */
    public record Outcome(Optional<Component> problem) {
        public static final Outcome OK = new Outcome(Optional.empty());

        public boolean done() {
            return problem.isEmpty();
        }

        static Outcome fail(String key, String fallback, Object... args) {
            return new Outcome(Optional.of(Component.translatableWithFallback(key, fallback, args)));
        }
    }

    private Repairs() {}

    /** Why this repair can't be done now (ignoring its cost), or empty if it can. */
    public static Optional<Component> blocked(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Optional<Repair> found = TardisContent.repair(id);
        if (found.isEmpty()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.unknown",
                    "There is no repair %s", id.toString()));
        }
        Repair r = found.get();
        int level = Unlocks.level(tardis, r.subsystem());
        if (level >= r.level()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.done", "Already repaired"));
        }
        if (level != r.level() - 1) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.order",
                    "Repair %s to level %s first", r.subsystem().toString(), r.level() - 1));
        }
        if (r.level() > Unlocks.cap(tardis, r.subsystem())) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.capped",
                    "The TARDIS needs a major repair milestone before this"));
        }
        if (r.requirements().isPresent() && !r.requirements().get().test(Unlocks.context(server, tardis, player))) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.requirements",
                    "Something else has to come first"));
        }
        return Optional.empty();
    }

    /**
     * Tries a repair, paid by {@code player} (items) and the TARDIS (charge). With no player, or a creative one, items
     * are free; charge is always paid.
     */
    public static Outcome attempt(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Optional<Component> blocked = blocked(server, tardis, id, player);
        if (blocked.isPresent()) {
            return new Outcome(blocked);
        }
        Repair repair = TardisContent.repair(id).orElseThrow();
        RepairEvent.Pre pre = NeoForge.EVENT_BUS.post(
                new RepairEvent.Pre(tardis, id, repair, TardisContent.get().cost(repair.cost())));
        if (pre.isCanceled()) {
            return Outcome.fail("tardis_rb.repair.cancelled", "The repair was stopped");
        }
        Optional<Component> unpaid = pay(tardis, pre.cost(), player);
        if (unpaid.isPresent()) {
            return new Outcome(unpaid);
        }
        tardis.applyRepair(id, repair);
        Progression.update(server, tardis);
        return new Outcome(Optional.empty());
    }

    /**
     * Pays a cost: charge from the TARDIS, items from the player (none for a creative player or no player). Takes
     * nothing and says why when anything is short.
     */
    public static Optional<Component> pay(TardisRecord tardis, Cost cost, Optional<ServerPlayer> player) {
        if (tardis.charge() < cost.charge()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.repair.charge",
                    "Needs %s charge (the TARDIS has %s)", cost.charge(), tardis.charge()));
        }
        boolean payItems = player.isPresent() && !player.get().getAbilities().instabuild;
        if (payItems) {
            Optional<Cost.ItemCost> missing = missing(player.get().getInventory(), cost);
            if (missing.isPresent()) {
                ItemStack[] shown = missing.get().ingredient().getItems();
                Component what = shown.length > 0 ? shown[0].getHoverName() : Component.literal("?");
                return Optional.of(Component.translatableWithFallback("tardis_rb.repair.items", "Needs %s x %s",
                        missing.get().count(), what));
            }
            take(player.get().getInventory(), cost);
        }
        tardis.useCharge(cost.charge());
        return Optional.empty();
    }

    /** The first item cost the inventory can't cover, counting each stack once across all costs. */
    public static Optional<Cost.ItemCost> missing(Inventory inv, Cost cost) {
        List<ItemStack> left = new ArrayList<>();
        for (ItemStack s : inv.items) {
            left.add(s.copy());
        }
        for (Cost.ItemCost c : cost.items()) {
            int need = c.count();
            for (ItemStack s : left) {
                if (need <= 0) {
                    break;
                }
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    int used = Math.min(need, s.getCount());
                    s.shrink(used);
                    need -= used;
                }
            }
            if (need > 0) {
                return Optional.of(c);
            }
        }
        return Optional.empty();
    }

    private static void take(Inventory inv, Cost cost) {
        for (Cost.ItemCost c : cost.items()) {
            int need = c.count();
            for (ItemStack s : inv.items) {
                if (need <= 0) {
                    break;
                }
                if (!s.isEmpty() && c.ingredient().test(s)) {
                    int used = Math.min(need, s.getCount());
                    s.shrink(used);
                    need -= used;
                }
            }
        }
        inv.setChanged();
    }
}
