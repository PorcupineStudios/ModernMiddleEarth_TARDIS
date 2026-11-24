package com.porcupinestudios.ringbearer.harness.dev;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlock;
import dev.ryanhcode.offroad.content.blocks.wheel_mount.WheelMountBlockEntity;
import dev.ryanhcode.offroad.index.OffroadBlocks;
import dev.ryanhcode.offroad.index.OffroadItems;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The test wagon (GameTests and the showcase): a 3 x 5 plank deck on four Offroad wheel mounts with tires, a hitch at
 * its front facing +z, optional iron cargo, assembled into a Sable sub-level as Simulated's assembler would.
 */
public final class TestWagon {
    public static final int WIDTH = 3, LENGTH = 5;

    /**
     * @param vehicle the assembled wagon
     * @param hitch its hitch (in the vehicle's plot)
     * @param hitchFront where the hitch's front face was before assembly (world): animals stand in front of it
     */
    public record Built(ServerSubLevel vehicle, HitchBlockEntity hitch, Vec3 hitchFront) {}

    private TestWagon() {}

    /** Builds and assembles it with its deck's back-left corner at {@code corner} (the wheels go 1 block outside). */
    @Nullable
    public static Built build(ServerLevel level, BlockPos corner, int cargo) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < LENGTH; z++) {
                BlockPos p = corner.offset(x, 0, z);
                boolean wheel = x != 1 && (z == 0 || z == LENGTH - 1);
                if (wheel) {
                    Direction out = x == 0 ? Direction.WEST : Direction.EAST;
                    level.setBlockAndUpdate(p, OffroadBlocks.WHEEL_MOUNT.getDefaultState()
                            .setValue(WheelMountBlock.HORIZONTAL_FACING, out));
                    if (level.getBlockEntity(p) instanceof WheelMountBlockEntity mount) {
                        mount.getInventory().slot.setStack(new ItemStack(OffroadItems.TIRE.get()));
                        mount.setChanged();
                    }
                } else {
                    level.setBlockAndUpdate(p, Blocks.OAK_PLANKS.defaultBlockState());
                }
                blocks.add(p);
            }
        }
        for (int i = 0; i < cargo; i++) {
            BlockPos p = corner.offset(i % 3, 1 + i / 9, 1 + (i / 3) % 3);
            level.setBlockAndUpdate(p, Blocks.IRON_BLOCK.defaultBlockState());
            blocks.add(p);
        }
        BlockPos hitchAt = corner.offset(1, 0, LENGTH);
        level.setBlockAndUpdate(hitchAt, HarnessMod.HITCH.get().defaultBlockState()
                .setValue(HitchBlock.FACING, Direction.SOUTH));
        blocks.add(hitchAt);
        BlockPos max = hitchAt.offset(1, 3, 0);
        ServerSubLevel vehicle = SubLevelAssemblyHelper.assembleBlocks(level, hitchAt, blocks,
                new BoundingBox3i(corner.getX(), corner.getY(), corner.getZ(), max.getX(), max.getY(), max.getZ()));
        Vec3 local = vehicle.logicalPose().transformPositionInverse(Vec3.atCenterOf(hitchAt));
        if (!(level.getBlockEntity(BlockPos.containing(local)) instanceof HitchBlockEntity hitch)) {
            return null;
        }
        return new Built(vehicle, hitch, Vec3.atCenterOf(hitchAt).add(0, 0, 0.5));
    }
}
