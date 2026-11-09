package com.porcupinestudios.tardisrb.progression;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.RepairEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The TARDIS's health (Aaron 2026-11-09): it lasts between trips. Vortex hits drain it ({@code vortex/Vortex}); at 0 the
 * box crash lands and is left with {@code vortex.damage.after_crash}. The most it can have is
 * {@code vortex.damage.health} plus the {@code health} stat (the hull's levels in {@code subsystem/hull.json}, and any
 * research bonus). Repairing the hull fills it up; {@code /tardis health} sets, repairs or damages it.
 * Kept in the record's module data {@value #MODULE}; a TARDIS without it (older saves) is at full health.
 */
public final class Health {
    public static final String MODULE = "tardis_rb:health";
    public static final ResourceLocation HULL = TardisRB.id("hull");

    private Health() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((RepairEvent.Post e) -> {
            if (HULL.equals(e.repair().subsystem())) {
                set(e.tardis(), max(e.tardis()));
            }
        });
    }

    /** The most health it can have now. */
    public static double max(TardisRecord tardis) {
        return Math.max(1, TardisContent.tuning().vortex().damage().health() + Unlocks.stat(tardis, "health"));
    }

    public static double get(TardisRecord tardis) {
        CompoundTag tag = tardis.moduleData(MODULE);
        return tag.contains("health") ? Math.min(max(tardis), tag.getDouble("health")) : max(tardis);
    }

    /** Sets it, kept between 0 and the most it can have; gives what it is now. */
    public static double set(TardisRecord tardis, double health) {
        double now = Math.max(0, Math.min(max(tardis), health));
        CompoundTag tag = tardis.moduleData(MODULE);
        tag.putDouble("health", now);
        tardis.setModuleData(MODULE, tag);
        return now;
    }

    /** Adds {@code amount} (negative to damage it); gives what it is now. */
    public static double add(TardisRecord tardis, double amount) {
        return set(tardis, get(tardis) + amount);
    }
}
