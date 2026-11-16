package com.porcupinestudios.lotrport.colony.market;

import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.building.Building;
import com.porcupinestudios.lotrport.colony.building.Buildings;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Delivery hatches: barrels in a colony's market building. Every {@link #EVERY} ticks what is in them is sold to the
 * colony, within today's cap per item and what the treasury can pay; what's over waits in the barrel (so a hopper line
 * backs up). The coins go to the account of whoever placed the barrel (else the founder), collected at the market.
 */
public final class Hatches {
    static final int EVERY = 200;

    private Hatches() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(Hatches::onTick);
        NeoForge.EVENT_BUS.addListener(Hatches::onPlace);
    }

    private static void onTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % EVERY == 120) {
            for (Colony c : new ArrayList<>(Colonies.get(e.getServer()).all())) {
                if (!c.ai()) {
                    run(e.getServer(), c);
                }
            }
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !(e.getLevel() instanceof Level level)) {
            return;
        }
        BlockPos pos = e.getPos();
        sp.server.execute(() -> StoreLock.at(sp.server, level, pos).ifPresent(p -> {
            if (StoreLock.hatch(p.building(), level.getBlockEntity(pos))) {
                Market.State st = Market.state(sp.server, p.colony());
                Map<String, UUID> h = new LinkedHashMap<>(st.hatches());
                h.put(Long.toString(pos.asLong()), sp.getUUID());
                Market.save(sp.server, p.colony(), new Market.State(st.level(), st.limits(), st.taken(), st.day(),
                        st.accounts(), h));
            }
        }));
    }

    /** The colony's hatches that are loaded. */
    public static List<BlockPos> hatches(ServerLevel level, Colony c) {
        List<BlockPos> out = new ArrayList<>();
        for (Building b : Buildings.of(c).ofType(StoreLock.MARKET)) {
            if (!level.isLoaded(b.door())) {
                continue;
            }
            for (long l : b.blocks()) {
                BlockPos p = BlockPos.of(l);
                if (level.isLoaded(p) && level.getBlockEntity(p) instanceof BarrelBlockEntity) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Sells what is in the colony's hatches; returns how many items were taken. */
    public static int run(MinecraftServer server, Colony c) {
        ServerLevel level = server.getLevel(c.dimension());
        if (level == null) {
            return 0;
        }
        int total = 0;
        for (BlockPos p : hatches(level, c)) {
            if (!(level.getBlockEntity(p) instanceof BarrelBlockEntity barrel)) {
                continue;
            }
            UUID who = Optional.ofNullable(Market.state(server, c).hatches().get(Long.toString(p.asLong())))
                    .orElse(c.founder());
            for (int i = 0; i < barrel.getContainerSize(); i++) {
                ItemStack s = barrel.getItem(i);
                if (s.isEmpty() || !s.getComponentsPatch().isEmpty()) {
                    continue;
                }
                var item = BuiltInRegistries.ITEM.getKey(s.getItem());
                int took = Market.intake(server, c, item, s.getCount(), coins -> Market.credit(server, c, who,
                        coins));
                if (took > 0) {
                    s.shrink(took);
                    barrel.setChanged();
                    total += took;
                }
            }
        }
        return total;
    }
}
