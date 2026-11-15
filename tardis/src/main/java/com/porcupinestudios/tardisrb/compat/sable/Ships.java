package com.porcupinestudios.tardisrb.compat.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/** Finding the Sable sub-level (ship) an entity stands on, is inside, or looks at. */
public final class Ships {
    private Ships() {}

    /** The ship the entity stands on (Sable tracks it), else one whose box holds the entity, else null. */
    public static SubLevel under(Entity e) {
        SubLevel s = ((EntityMovementExtension) e).sable$getTrackingSubLevel();
        if (s != null && !s.isRemoved()) {
            return s;
        }
        SubLevelContainer c = SubLevelContainer.getContainer(e.level());
        if (c == null) {
            return null;
        }
        for (SubLevel each : c.queryIntersecting(new BoundingBox3d(e.getBoundingBox()))) {
            if (!each.isRemoved()) {
                return each;
            }
        }
        return null;
    }

    /** The first ship block along the entity's look, within {@code range} blocks, or null. */
    public static SubLevel lookedAt(Entity e, double range) {
        Level level = e.level();
        SubLevelContainer c = SubLevelContainer.getContainer(level);
        if (c == null) {
            return null;
        }
        Vec3 from = e.getEyePosition();
        Vec3 dir = e.getViewVector(1);
        for (double d = 0; d <= range; d += 0.25) {
            Vec3 at = from.add(dir.scale(d));
            for (SubLevel s : c.queryIntersecting(new BoundingBox3d(at, at))) {
                Vector3d local = s.logicalPose().transformPositionInverse(new Vector3d(at.x, at.y, at.z));
                BlockPos pos = BlockPos.containing(local.x, local.y, local.z);
                if (!level.getBlockState(pos).isAir()) {
                    return s;
                }
            }
        }
        return null;
    }

    /** A ship's down in world axes (its orientation applied to 0, -1, 0). */
    public static Vec3 down(SubLevel s) {
        Pose3dc pose = s.logicalPose();
        Vector3d d = pose.orientation().transform(new Vector3d(0, -1, 0));
        return new Vec3(d.x, d.y, d.z);
    }
}
