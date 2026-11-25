package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.SlopeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Slope blocks: placed like stairs, walkable as a fine staircase, and no collider for Sable. */
@GameTestHolder(HarnessMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SlopeTests {
    private SlopeTests() {}

    private static BlockState place(GameTestHelper h, float yaw, Direction face, double hitY) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setYRot(yaw);
        BlockPos pos = h.absolutePos(new BlockPos(1, 1, 1));
        Vec3 hit = new Vec3(pos.getX() + 0.5, pos.getY() + hitY, pos.getZ() + 0.5);
        BlockPlaceContext ctx = new BlockPlaceContext(h.getLevel(), p, InteractionHand.MAIN_HAND,
                new ItemStack(HarnessMod.OAK_SLOPE_ITEM.get()), new BlockHitResult(hit, face, pos, false));
        return HarnessMod.OAK_SLOPE.get().getStateForPlacement(ctx);
    }

    /** The top of the collision shape over a strip along z (x across the whole block). */
    private static double topOver(VoxelShape shape, double z0, double z1) {
        double top = 0;
        for (var box : shape.toAabbs()) {
            if (box.maxZ > z0 && box.minZ < z1) {
                top = Math.max(top, box.maxY);
            }
        }
        return top;
    }

    @GameTest(template = "arena")
    public static void placedLikeStairs(GameTestHelper h) {
        // looking north, on top of a block: right side up, climbing north (its low edge toward the player, south)
        BlockState s = place(h, 180, Direction.UP, 0);
        h.assertTrue(s.getValue(SlopeBlock.FACING) == Direction.NORTH, "facing " + s.getValue(SlopeBlock.FACING));
        h.assertTrue(s.getValue(SlopeBlock.HALF) == Half.BOTTOM, "not right side up from a floor");
        VoxelShape shape = s.getCollisionShape(h.getLevel(), BlockPos.ZERO, CollisionContext.empty());
        h.assertTrue(topOver(shape, 0.94, 1.0) < 0.3, "the near (south) edge isn't low: " + topOver(shape, 0.94, 1));
        h.assertTrue(topOver(shape, 0.0, 0.06) > 0.9, "the far (north) edge isn't high: " + topOver(shape, 0, 0.06));
        h.assertTrue(shape.toAabbs().size() >= 4, "not a staircase: " + shape.toAabbs().size() + " boxes");
        // against the top half of a side, or under a block: upside down
        h.assertTrue(place(h, 180, Direction.SOUTH, 0.8).getValue(SlopeBlock.HALF) == Half.TOP,
                "not upside down from a high click on a side");
        h.assertTrue(place(h, 180, Direction.SOUTH, 0.2).getValue(SlopeBlock.HALF) == Half.BOTTOM,
                "upside down from a low click on a side");
        h.assertTrue(place(h, 90, Direction.DOWN, 1).getValue(SlopeBlock.HALF) == Half.TOP,
                "not upside down under a ceiling");
        h.assertTrue(place(h, 90, Direction.DOWN, 1).getValue(SlopeBlock.FACING) == Direction.WEST, "facing west");
        h.assertTrue(HarnessMod.OAK_SLOPE.get().getSubLevelCollisionShape(h.getLevel(), s).isEmpty(),
                "slopes must give Sable no box collider (sleds catch on them)");
        h.succeed();
    }
}
