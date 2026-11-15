package com.porcupinestudios.lotrport.colony.building;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.sim.ColonySystem;
import com.porcupinestudios.lotrport.society.sim.ColonySystems;
import com.porcupinestudios.lotrport.society.sim.SystemContext;
import com.porcupinestudios.lotrport.society.sim.Tier;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/** Wires the buildings feature: data, the component, the writ's door bypass and the re-measure system. */
public final class BuildingSystems {
    /** Measures buildings again, a few per pulse, oldest first, while the colony is in the world. */
    public static final ResourceLocation RECHECK = LotrPort.id("building_recheck");
    /** Count: buildings measured this pass. */
    public static final ResourceLocation RECHECKED = LotrPort.id("buildings_rechecked");
    /** Count: buildings lost this pass. */
    public static final ResourceLocation LOST = LotrPort.id("buildings_lost");

    private BuildingSystems() {}

    public static void register() {
        Buildings.init();
        BuildingSettings.register();
        BuildingType.register();
        NeoForge.EVENT_BUS.addListener(WritItem::onRightClick);
        ColonySystems.register(ColonySystem.of(RECHECK, ColonySystem.Cadence.PULSE, Tier.IN_WORLD, Set.of(),
                BuildingSystems::recheck));
    }

    static void recheck(SystemContext ctx) {
        if (ctx.level() == null || ctx.colony().ai()) {
            return;
        }
        BuildingSettings s = BuildingSettings.get();
        List<Building> due = Buildings.of(ctx.colony()).list().stream()
                .filter(b -> ctx.now() - b.checked() >= s.recheckTicks() && ctx.level().isLoaded(b.door()))
                .sorted(Comparator.comparingLong(Building::checked)).limit(Math.max(0, s.recheckPerPulse())).toList();
        for (Building b : due) {
            ctx.count(RECHECKED, 1);
            if (!ColonyBuildings.recheck(ctx.level(), ctx.colony(), b)) {
                ctx.count(LOST, 1);
            }
        }
    }
}
