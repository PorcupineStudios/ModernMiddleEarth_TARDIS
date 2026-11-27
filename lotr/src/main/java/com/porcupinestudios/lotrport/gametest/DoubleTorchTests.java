package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.DoubleTorchBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** LOTR's two-block orc and Taurëdain torches (content/DoubleTorchBlock, converter kind double_torch). */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoubleTorchTests {
    private DoubleTorchTests() {}

    @GameTest(template = "empty")
    public static void orcTorchPlacesBothHalvesLitAtTheTop(GameTestHelper helper) {
        Block torch = BuiltInRegistries.BLOCK.get(LotrPort.id("orc_torch_block"));
        Item item = BuiltInRegistries.ITEM.get(LotrPort.id("orc_torch"));
        helper.assertTrue(torch instanceof DoubleTorchBlock, "orc_torch_block isn't a DoubleTorchBlock");
        BlockPos ground = new BlockPos(2, 1, 2);
        helper.setBlock(ground, Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item));
        BlockPos abs = helper.absolutePos(ground);
        item.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0.5, 0), Direction.UP, abs, false)));
        BlockState lower = helper.getBlockState(ground.above());
        BlockState upper = helper.getBlockState(ground.above(2));
        helper.assertTrue(lower.is(torch) && lower.getValue(DoubleTorchBlock.HALF) == DoubleBlockHalf.LOWER,
                "the orc torch item placed no lower half: " + lower);
        helper.assertTrue(upper.is(torch) && upper.getValue(DoubleTorchBlock.HALF) == DoubleBlockHalf.UPPER,
                "the orc torch item placed no upper half: " + upper);
        helper.assertTrue(lower.getLightEmission() == 0 && upper.getLightEmission() == 14,
                "light should be 0 below and 14 at the top, got " + lower.getLightEmission() + " and "
                        + upper.getLightEmission());
        helper.getLevel().destroyBlock(helper.absolutePos(ground.above(2)), true);
        helper.assertBlockNotPresent(torch, ground.above());
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(abs).inflate(3));
        int count = drops.stream().filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(count == 1, "breaking the torch dropped " + count + " torches, want 1");
        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void structureTorchesHaveAnUpperHalf(GameTestHelper helper) {
        Block torch = BuiltInRegistries.BLOCK.get(LotrPort.id("orc_torch_block"));
        StructureTemplate camp = helper.getLevel().getStructureManager().get(LotrPort.id("uruk_camp/0"))
                .orElseThrow();
        List<StructureTemplate.StructureBlockInfo> infos = camp.filterBlocks(BlockPos.ZERO,
                new StructurePlaceSettings(), torch, true);
        helper.assertFalse(infos.isEmpty(), "uruk_camp/0 has no orc torches");
        Set<BlockPos> lowers = new HashSet<>();
        infos.stream().filter(i -> i.state().getValue(DoubleTorchBlock.HALF) == DoubleBlockHalf.LOWER)
                .forEach(i -> lowers.add(i.pos()));
        for (StructureTemplate.StructureBlockInfo i : infos) {
            boolean up = i.state().getValue(DoubleTorchBlock.HALF) == DoubleBlockHalf.UPPER;
            helper.assertTrue(up ? lowers.contains(i.pos().below()) : !lowers.contains(i.pos().below()),
                    "orc torch half at " + i.pos() + " doesn't pair up (" + i.state() + ")");
        }
        helper.assertTrue(lowers.size() * 2 == infos.size(), "uruk_camp/0's torches aren't lower+upper pairs");
        helper.succeed();
    }
}
