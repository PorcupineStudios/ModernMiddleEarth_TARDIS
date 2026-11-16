package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.farm.BerryBushBlock;
import com.porcupinestudios.lotrport.content.farm.CornBlock;
import com.porcupinestudios.lotrport.content.farm.GrapevineBlock;
import com.porcupinestudios.lotrport.content.pouch.PouchItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's farming and small items: crops plant from their seed and drop their produce when ripe, berry bushes and
 * corn are picked by hand, grape seeds plant a vine on a post over farmland and picking it leaves the post,
 * item-dropping ores give experience, pouches hold 9/18/27 stacks.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmTests {
    private FarmTests() {}

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(LotrPort.id(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        helper.getLevel().getBlockState(abs).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    @GameTest(template = "empty", batch = "farming")
    public static void cropsGrowAndDrop(GameTestHelper helper) {
        Block leek = block("leek_block");
        helper.assertTrue(leek instanceof CropBlock, "leek_block isn't a crop");
        helper.assertTrue(item("leek") instanceof net.minecraft.world.item.BlockItem bi && bi.getBlock() == leek,
                "a leek doesn't plant leeks");
        helper.assertTrue(((CropBlock) block("pipeweed")).getCloneItemStack(helper.getLevel(), BlockPos.ZERO,
                block("pipeweed").defaultBlockState()).is(item("pipe_weed_seeds")), "pipe-weed's seed is wrong");
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, ((CropBlock) block("flax_block")).getStateForAge(7));
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        helper.assertItemEntityPresent(item("flax"), pos, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "farming")
    public static void berriesAndCornPicked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos bush = new BlockPos(1, 2, 1);
        helper.setBlock(bush, block("blueberry_bush"));
        helper.assertTrue(((BerryBushBlock) block("blueberry_bush")).hasBerries(), "the ripe bush has no berries");
        use(helper, player, bush);
        helper.assertBlockPresent(block("bare_blueberry_bush"), bush);
        helper.assertItemEntityPresent(item("blueberries"), bush, 2);
        BlockPos corn = new BlockPos(3, 2, 1);
        helper.setBlock(corn.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(corn, block("corn_stalks"));
        helper.setBlock(corn.above(), block("corn_stalks").defaultBlockState().setValue(CornBlock.CORN, true));
        use(helper, player, corn.above());
        helper.assertItemEntityPresent(item("corn"), corn.above(), 2);
        helper.assertFalse(helper.getBlockState(corn.above()).getValue(CornBlock.CORN), "the cob is still on");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "farming")
    public static void grapesOnPosts(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos post = new BlockPos(1, 3, 1);
        helper.setBlock(post.below(), Blocks.FARMLAND);
        helper.setBlock(post, block("grapevine_post"));
        ItemStack seeds = new ItemStack(item("red_grape_seeds"));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, seeds);
        BlockPos abs = helper.absolutePos(post);
        seeds.useOn(new UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false)));
        helper.assertBlockPresent(block("grapevine_red"), post);
        helper.setBlock(post, block("grapevine_red").defaultBlockState().setValue(GrapevineBlock.AGE, 7));
        use(helper, player, post);
        helper.assertBlockPresent(block("grapevine_post"), post);
        helper.assertItemEntityPresent(item("red_grapes"), post, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "farming")
    public static void oresAndPouches(GameTestHelper helper) {
        helper.assertTrue(block("sulfur_ore") instanceof DropExperienceBlock, "sulfur ore gives no experience");
        helper.assertTrue(item("small_pouch") instanceof PouchItem p && p.slots() == 9, "the small pouch isn't 9");
        helper.assertTrue(item("large_pouch") instanceof PouchItem p && p.slots() == 27, "the large pouch isn't 27");
        ItemStack pouch = new ItemStack(item("medium_pouch"));
        var items = PouchItem.contents(pouch);
        helper.assertTrue(items.size() == 18, "the medium pouch isn't 18");
        items.set(17, new ItemStack(Items.DIAMOND, 5));
        PouchItem.store(pouch, items);
        helper.assertTrue(PouchItem.contents(pouch).get(17).getCount() == 5, "the pouch lost its diamonds");
        helper.succeed();
    }
}
