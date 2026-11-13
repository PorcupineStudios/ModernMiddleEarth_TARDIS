package com.porcupinestudios.lotrport.colony.market;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.BuildingType;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import com.porcupinestudios.lotrport.colony.work.Stores;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The colony's goods belong to the colony: containers in its workplaces and storehouses (not homes) can't be opened
 * by players (right-clicking one opens the market), hoppers and droppers can't be placed against them (and a store
 * touched by one doesn't count: {@link #fed}), and breaking one moves its contents into the colony's stock. Barrels in
 * a market building are delivery hatches ({@link Hatches}): open to hoppers and players. Operators in creative pass.
 */
public final class StoreLock {
    public static final ResourceLocation MARKET = LotrPort.id("market");

    private StoreLock() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StoreLock::onUse);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StoreLock::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, StoreLock::onPlace);
    }

    /** The colony and building whose own blocks hold this spot. */
    public record Place(Colony colony, Building building) {}

    public static Optional<Place> at(MinecraftServer server, Level level, BlockPos pos) {
        for (Colony c : Colonies.get(server).all()) {
            if (!c.dimension().equals(level.dimension()) || c.ai()) {
                continue;
            }
            Optional<Building> b = Buildings.of(c).at(pos);
            if (b.isPresent()) {
                return Optional.of(new Place(c, b.get()));
            }
        }
        return Optional.empty();
    }

    static boolean home(Building b) {
        return BuildingType.get(b.type()).map(BuildingType::home).orElse(false);
    }

    /** A barrel in a market building: hoppers and players may fill it. */
    public static boolean hatch(Building b, BlockEntity be) {
        return b.type().equals(MARKET) && be instanceof BarrelBlockEntity;
    }

    /** Whether the container at pos is a locked colony store. */
    public static Optional<Place> locked(MinecraftServer server, Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container)) {
            return Optional.empty();
        }
        return at(server, level, pos).filter(p -> !home(p.building()) && !hatch(p.building(), be));
    }

    /** A hopper or dropper touches it: it doesn't count as the colony's store. */
    public static boolean fed(LevelReader level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos n = pos.relative(d);
            if (!level.isLoaded(n)) {
                continue;
            }
            Block b = level.getBlockState(n).getBlock();
            if (b instanceof HopperBlock || b instanceof DropperBlock) {
                return true;
            }
        }
        return false;
    }

    static boolean bypass(ServerPlayer p) {
        return p.hasPermissions(2) && p.isCreative();
    }

    private static void onUse(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || bypass(sp)) {
            return;
        }
        Optional<Place> p = locked(sp.server, e.getLevel(), e.getPos());
        if (p.isEmpty() || (sp.isShiftKeyDown() && !sp.getMainHandItem().isEmpty())) {
            return;
        }
        e.setUseBlock(TriState.FALSE);
        e.setUseItem(TriState.FALSE);
        e.setCanceled(true);
        MarketViews.open(sp, p.get().colony());
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer sp) || !(e.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<Place> p = locked(sp.server, level, e.getPos());
        if (p.isEmpty() || !(level.getBlockEntity(e.getPos()) instanceof Container box)) {
            return;
        }
        // the goods stay the colony's: into its stock, not onto the ground
        Stores stores = new Stores(sp.server, level, p.get().colony());
        for (int i = 0; i < box.getContainerSize(); i++) {
            ItemStack s = box.getItem(i);
            if (!s.isEmpty() && s.getComponentsPatch().isEmpty()) {
                stores.put(BuiltInRegistries.ITEM.getKey(s.getItem()), s.getCount(), null);
                box.setItem(i, ItemStack.EMPTY);
            }
        }
        stores.save();
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof Level level) || bypass(sp)) {
            return;
        }
        Block placed = e.getPlacedBlock().getBlock();
        if (!(placed instanceof HopperBlock) && !(placed instanceof DropperBlock)) {
            return;
        }
        for (Direction d : Direction.values()) {
            if (locked(sp.server, level, e.getPos().relative(d)).isPresent()) {
                e.setCanceled(true);
                sp.displayClientMessage(Component.translatableWithFallback("colony.lotr_port.store_locked",
                        "The colony's stores are its own: deliver through a market hatch (a barrel in a market)"),
                        true);
                return;
            }
        }
    }
}
