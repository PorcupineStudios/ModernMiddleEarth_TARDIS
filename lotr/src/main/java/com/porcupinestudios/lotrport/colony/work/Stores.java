package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.building.RoomScan;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * A colony's goods for one pass: the chests of its loaded buildings (only blocks in {@code #lotr_port:colony/chests},
 * each half of a double chest on its own, so nothing counts twice) and the virtual {@link WorkComponents.Stock}. Goods
 * go into the workplace, then storehouses, then the stock; they are taken from the same places in that order. Call
 * {@link #save} at the end of the pass.
 */
public final class Stores {
    public static final ResourceLocation STOREHOUSE = LotrPort.id("storehouse");

    private final net.minecraft.server.MinecraftServer server;
    private final ServerLevel level;
    private final Colony colony;
    private final Map<ResourceLocation, Integer> stock;
    private final Map<UUID, List<IItemHandler>> chests = new HashMap<>();
    private final List<Building> storehouses;
    private boolean dirty;

    public Stores(net.minecraft.server.MinecraftServer server, ServerLevel level, Colony colony) {
        this.server = server;
        this.level = level;
        this.colony = colony;
        this.stock = new LinkedHashMap<>(colony.get(WorkComponents.STOCK).orElse(WorkComponents.Stock.EMPTY).items());
        this.storehouses = Buildings.of(colony).ofType(STOREHOUSE);
    }

    /** The chests of a building, if it is loaded. */
    public List<IItemHandler> chests(Building b) {
        return chests.computeIfAbsent(b.id(), id -> {
            List<IItemHandler> out = new ArrayList<>();
            if (level == null || !level.isLoaded(b.door())) {
                return out;
            }
            var tag = RoomScan.tag("chests");
            for (long l : b.blocks()) {
                BlockPos p = BlockPos.of(l);
                if (!level.isLoaded(p) || !level.getBlockState(p).is(tag)
                        || com.porcupinestudios.lotrport.colony.market.StoreLock.fed(level, p)) {
                    // a store a hopper or dropper touches isn't the colony's: goods can't be piped in or out
                    continue;
                }
                BlockEntity be = level.getBlockEntity(p);
                if (com.porcupinestudios.lotrport.colony.market.StoreLock.hatch(b, be)) {
                    continue; // a market's delivery hatch: the player's goods until sold
                }
                if (be instanceof Container c) {
                    out.add(new InvWrapper(c));
                }
            }
            return out;
        });
    }

    private List<IItemHandler> places(Building workplace) {
        List<IItemHandler> out = new ArrayList<>();
        if (workplace != null) {
            out.addAll(chests(workplace));
        }
        for (Building s : storehouses) {
            if (workplace == null || !s.id().equals(workplace.id())) {
                out.addAll(chests(s));
            }
        }
        return out;
    }

    public int count(ResourceLocation item, Building workplace) {
        Item it = BuiltInRegistries.ITEM.get(item);
        int n = stock.getOrDefault(item, 0);
        for (IItemHandler h : places(workplace)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.is(it) && s.getComponentsPatch().isEmpty()) {
                    n += s.getCount();
                }
            }
        }
        return n;
    }

    /** Every plain item in the stores and the stock, with how many (the workplace's chests too, if given). */
    public Map<ResourceLocation, Integer> items(Building workplace) {
        Map<ResourceLocation, Integer> out = new LinkedHashMap<>(stock);
        for (IItemHandler h : places(workplace)) {
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty() && s.getComponentsPatch().isEmpty()) {
                    out.merge(BuiltInRegistries.ITEM.getKey(s.getItem()), s.getCount(), Integer::sum);
                }
            }
        }
        out.values().removeIf(n -> n <= 0);
        return out;
    }

    /** Takes up to n plain items; returns how many. */
    public int take(ResourceLocation item, int n, Building workplace) {
        Item it = BuiltInRegistries.ITEM.get(item);
        int left = n;
        for (IItemHandler h : places(workplace)) {
            for (int i = 0; i < h.getSlots() && left > 0; i++) {
                ItemStack s = h.getStackInSlot(i);
                if (s.is(it) && s.getComponentsPatch().isEmpty()) {
                    left -= h.extractItem(i, Math.min(left, s.getCount()), false).getCount();
                }
            }
        }
        int fromStock = Math.min(left, stock.getOrDefault(item, 0));
        if (fromStock > 0) {
            stock.merge(item, -fromStock, Integer::sum);
            stock.remove(item, 0);
            left -= fromStock;
            dirty = true;
        }
        return n - left;
    }

    /** Puts n items away; what fits nowhere goes to the stock. */
    public void put(ResourceLocation item, int n, Building workplace) {
        Item it = BuiltInRegistries.ITEM.get(item);
        int left = n;
        for (IItemHandler h : places(workplace)) {
            while (left > 0) {
                ItemStack part = new ItemStack(it, Math.min(left, it.getDefaultMaxStackSize()));
                int offered = part.getCount();
                ItemStack rest = ItemHandlerHelper.insertItemStacked(h, part, false);
                left -= offered - rest.getCount();
                if (!rest.isEmpty()) {
                    break;
                }
            }
        }
        if (left > 0) {
            stock.merge(item, left, Integer::sum);
            dirty = true;
        }
    }

    /**
     * Carries up to n items from workplace chests (every building but storehouses) into storehouse chests; returns how
     * many moved (RingRealms' couriers).
     */
    public int carry(int n, Buildings buildings) {
        int left = n;
        List<IItemHandler> to = new ArrayList<>();
        storehouses.forEach(b -> to.addAll(chests(b)));
        if (to.isEmpty()) {
            return 0;
        }
        for (Building b : buildings.list()) {
            // only workplaces that make goods: never a house's or a market's chests
            boolean makes = com.porcupinestudios.lotrport.colony.building.BuildingType.get(b.type())
                    .flatMap(com.porcupinestudios.lotrport.colony.building.BuildingType::job).flatMap(JobDef::get)
                    .map(d -> !d.produces().isEmpty()).orElse(false);
            if (b.type().equals(STOREHOUSE) || !makes) {
                continue;
            }
            for (IItemHandler from : chests(b)) {
                for (int i = 0; i < from.getSlots() && left > 0; i++) {
                    ItemStack s = from.extractItem(i, Math.min(left, 64), true);
                    if (s.isEmpty()) {
                        continue;
                    }
                    int moved = 0;
                    ItemStack rest = s;
                    for (IItemHandler h : to) {
                        rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
                        if (rest.isEmpty()) {
                            break;
                        }
                    }
                    moved = s.getCount() - rest.getCount();
                    if (moved > 0) {
                        from.extractItem(i, moved, false);
                        left -= moved;
                    }
                }
            }
        }
        return n - left;
    }

    /** Eats one food item from the storehouses (then the stock); returns its nutrition, 0 if there is none. */
    public int eatOne() {
        for (Building b : storehouses) {
            for (IItemHandler h : chests(b)) {
                for (int i = 0; i < h.getSlots(); i++) {
                    FoodProperties f = h.getStackInSlot(i).get(DataComponents.FOOD);
                    if (f != null && f.nutrition() > 0 && !h.extractItem(i, 1, false).isEmpty()) {
                        return f.nutrition();
                    }
                }
            }
        }
        for (Map.Entry<ResourceLocation, Integer> e : stock.entrySet()) {
            FoodProperties f = BuiltInRegistries.ITEM.get(e.getKey()).components().get(DataComponents.FOOD);
            if (f != null && f.nutrition() > 0 && e.getValue() > 0) {
                ResourceLocation id = e.getKey();
                stock.merge(id, -1, Integer::sum);
                stock.remove(id, 0);
                dirty = true;
                return f.nutrition();
            }
        }
        return 0;
    }

    /** Moves the stock into loaded storehouses (goods made while unloaded). */
    public void flush() {
        if (stock.isEmpty() || storehouses.stream().allMatch(b -> chests(b).isEmpty())) {
            return;
        }
        Map<ResourceLocation, Integer> all = new LinkedHashMap<>(stock);
        stock.clear();
        dirty = true;
        all.forEach((item, n) -> put(item, n, null));
    }

    public void save() {
        if (dirty) {
            Colonies.get(server).set(colony, WorkComponents.STOCK,
                    new WorkComponents.Stock(stock));
        }
    }

    public Map<ResourceLocation, Integer> stock() {
        return stock;
    }
}
