package com.porcupinestudios.tardisrb.repair;

import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.Progression;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/** The world hurting the TARDIS: explosions near the police box lower a subsystem (tuning file, {@code damage}). */
public final class WorldDamage {
    private WorldDamage() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ExplosionEvent.Detonate e) -> {
            if (e.getLevel() instanceof ServerLevel level) {
                explosion(level, e.getExplosion().center());
            }
        });
    }

    public static void explosion(ServerLevel level, Vec3 center) {
        Tuning.Damage t = TardisContent.tuning().damage();
        if (t.explosionLevels() <= 0) {
            return;
        }
        for (TardisRecord tardis : TardisManager.get(level.getServer()).all()) {
            boolean near = tardis.location().map(g -> g.dimension().equals(level.dimension())
                    && g.pos().getCenter().closerThan(center, t.explosionRadius())).orElse(false);
            if (near && tardis.damage(t.explosionSubsystem(), t.explosionLevels()) > 0) {
                Progression.update(level.getServer(), tardis);
            }
        }
    }
}
