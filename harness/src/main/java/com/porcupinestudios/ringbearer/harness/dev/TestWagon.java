package com.porcupinestudios.ringbearer.harness.dev;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.SlopeBlock;
import com.porcupinestudios.ringbearer.harness.content.WagonWheelBlock;
import net.minecraft.world.level.block.state.properties.Half;
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
 * The test wagon (GameTests and the showcase): a 3 x 5 plank deck on four Offroad wheel mounts with tires, on four
 * wagon wheels, or sliding as a sled with a slope nose ({@link Kind}); a hitch at its front facing +z, optional iron
 * cargo; assembled into a Sable sub-level as Simulated's assembler would.
 */
public final class TestWagon {
    public static final int WIDTH = 3, LENGTH = 5;

    /** What it rolls or slides on. */
    public enum Kind {
        /** Offroad wheel mounts with tires at the deck's corners */
        OFFROAD,
        /** wagon wheels on the outside of the deck's corners */
        WAGON_WHEELS,
        /** no wheels: the deck slides, with upside-down 45-degree slopes along its front edge as a nose */
        SLED,
        /** a sled with a 2-block gentle nose (upside-down gentle slope halves: 27 degrees) */
        SLED_GENTLE;

        public boolean sled() {
            return this == SLED || this == SLED_GENTLE;
        }

        /** How far above the ground's top block the deck is built. */
        public int deckHeight() {
            return sled() ? 1 : 2;
        }

        /** How many rows of nose are in front of the deck. */
        public int nose() {
            return this == SLED ? 1 : this == SLED_GENTLE ? 2 : 0;
        }
    }

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
        return build(level, corner, cargo, Kind.OFFROAD);
    }

    @Nullable
    public static Built build(ServerLevel level, BlockPos corner, int cargo, Kind kind) {
        List<BlockPos> blocks = new ArrayList<>();
        if (kind == Kind.WAGON_WHEELS) {
            for (int z : new int[] {0, LENGTH - 1}) {
                for (int x : new int[] {-1, WIDTH}) {
                    BlockPos p = corner.offset(x, 0, z);
                    level.setBlockAndUpdate(p, HarnessMod.WAGON_WHEEL.get().defaultBlockState()
                            .setValue(WagonWheelBlock.FACING, x < 0 ? Direction.WEST : Direction.EAST));
                    blocks.add(p);
                }
            }
        }
        int noseLen = kind.nose();
        if (kind.sled()) {
            // the nose: upside-down slopes in front of the deck, thin edge forward (+z), so a step's edge slides up
            // under them. In front of a full deck: a nose taking the deck's own front rows left too little sled on the
            // ground behind it, and a 2-block nose tipped the sled onto its face (2026-11-24)
            List<SlopeBlock> nose = kind == Kind.SLED ? List.of(HarnessMod.OAK_SLOPE.get())
                    : List.of(HarnessMod.OAK_SLOPE_HIGH.get(), HarnessMod.OAK_SLOPE_LOW.get());
            for (int i = 0; i < nose.size(); i++) {
                for (int x = 0; x < WIDTH; x++) {
                    BlockPos p = corner.offset(x, 0, LENGTH + i);
                    level.setBlockAndUpdate(p, nose.get(i).defaultBlockState()
                            .setValue(SlopeBlock.FACING, Direction.NORTH).setValue(SlopeBlock.HALF, Half.TOP));
                    blocks.add(p);
                }
            }
        }
        for (int x = 0; x < WIDTH; x++) {
            for (int z = 0; z < LENGTH; z++) {
                BlockPos p = corner.offset(x, 0, z);
                boolean wheel = kind == Kind.OFFROAD && x != 1 && (z == 0 || z == LENGTH - 1);
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
        // the hitch in front at deck level: for a sled that's low at its front, where a pull doesn't tip it
        BlockPos hitchAt = corner.offset(1, 0, LENGTH + noseLen);
        level.setBlockAndUpdate(hitchAt, HarnessMod.HITCH.get().defaultBlockState()
                .setValue(HitchBlock.FACING, Direction.SOUTH));
        blocks.add(hitchAt);
        BlockPos max = corner.offset(WIDTH - 1, 3, LENGTH + noseLen);
        ServerSubLevel vehicle = SubLevelAssemblyHelper.assembleBlocks(level, hitchAt, blocks,
                new BoundingBox3i(corner.getX() - 1, corner.getY(), corner.getZ(), max.getX() + 1, max.getY(),
                        max.getZ()));
        Vec3 local = vehicle.logicalPose().transformPositionInverse(Vec3.atCenterOf(hitchAt));
        if (!(level.getBlockEntity(BlockPos.containing(local)) instanceof HitchBlockEntity hitch)) {
            return null;
        }
        return new Built(vehicle, hitch, Vec3.atCenterOf(hitchAt).add(0, 0, 0.5));
    }
}
