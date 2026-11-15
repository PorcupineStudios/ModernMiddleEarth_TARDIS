package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.machine.DisplayBlock;
import com.porcupinestudios.lotrport.machine.DisplayBlockEntity;
import com.porcupinestudios.lotrport.machine.EntJarBlockEntity;
import com.porcupinestudios.lotrport.machine.ForgeBlockEntity;
import com.porcupinestudios.lotrport.machine.KebabStandBlockEntity;
import com.porcupinestudios.lotrport.machine.LotrChestBlockEntity;
import com.porcupinestudios.lotrport.machine.MillstoneBlockEntity;
import com.porcupinestudios.lotrport.machine.WeaponRackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's machines run: a Dwarven forge makes Dwarven steel from iron and coal, the millstone grinds while powered,
 * the kebab stand cooks on fuel from the chest under it, the Ent jar holds draughts, and the display blocks and
 * chests have their block entities and items that place them. Their own batch: the 1-block "empty" template lets
 * other tests' areas land on these machines while they run.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineTests {
    private MachineTests() {}

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(LotrPort.id(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    @GameTest(template = "empty", batch = "machines", timeoutTicks = 400)
    public static void forgeMakesSteel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, block("dwarven_forge"));
        ForgeBlockEntity forge = helper.getBlockEntity(pos);
        forge.setItem(0, new ItemStack(Items.COAL)); // alloy, first column
        forge.setItem(4, new ItemStack(Items.RAW_IRON)); // input, first column
        forge.setItem(12, new ItemStack(Items.COAL, 4)); // fuel
        helper.succeedWhen(() -> helper.assertTrue(forge.getItem(8).is(item("dwarven_steel_ingot")),
                "no Dwarven steel in the first output: " + forge.getItem(8)));
    }

    @GameTest(template = "empty", batch = "machines", timeoutTicks = 400)
    public static void millstoneGrindsWhilePowered(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, block("millstone"));
        MillstoneBlockEntity mill = helper.getBlockEntity(pos);
        mill.setItem(0, new ItemStack(Items.STONE));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(mill.getItem(1).isEmpty(), "the millstone ground without power");
            helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(helper.getLevel().hasNeighborSignal(helper.absolutePos(pos)), "the millstone isn't powered");
            helper.assertTrue(helper.getBlockState(pos).getValue(com.porcupinestudios.lotrport.machine.MachineBlock.LIT),
                    "the powered millstone isn't turning: " + mill.getItem(0));
        });
        helper.succeedWhen(() -> helper.assertTrue(mill.getItem(1).is(Items.COBBLESTONE),
                "no cobblestone from the powered millstone: " + mill.getItem(1) + " in " + mill.getItem(0)));
    }

    @GameTest(template = "empty", batch = "machines", timeoutTicks = 400)
    public static void kebabStandCooks(GameTestHelper helper) {
        BlockPos below = new BlockPos(1, 2, 1);
        helper.setBlock(below, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(below);
        chest.setItem(0, new ItemStack(Items.COAL));
        BlockPos pos = below.above();
        helper.setBlock(pos, block("kebab_stand"));
        KebabStandBlockEntity stand = helper.getBlockEntity(pos);
        helper.assertTrue(stand.addMeat(new ItemStack(Items.BEEF)), "the stand didn't take raw beef");
        helper.assertFalse(stand.addMeat(new ItemStack(Items.DIRT)), "the stand took dirt");
        helper.succeedWhen(() -> {
            helper.assertTrue(stand.cooked(), "the meat isn't cooked");
            helper.assertTrue(chest.getItem(0).isEmpty(), "the stand didn't burn the coal below");
            helper.assertTrue(stand.take().is(item("kebab")), "no kebab came off the stand");
        });
    }

    @GameTest(template = "empty", batch = "machines")
    public static void entJarHoldsDraughts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, block("ent_jar"));
        EntJarBlockEntity jar = helper.getBlockEntity(pos);
        ItemStack draught = new ItemStack(item("green_ent_draught"));
        helper.assertTrue(jar.pourIn(draught) && jar.pourIn(draught), "the jar didn't take two draughts");
        helper.assertFalse(jar.pourIn(new ItemStack(item("blue_ent_draught"))), "the jar mixed two draughts");
        helper.assertTrue(jar.takeDraught().is(draught.getItem()) && jar.amount() == 1, "no draught came out");
        jar.empty();
        jar.fillWithWater();
        helper.assertTrue(jar.isWater() && jar.amount() == jar.capacity(), "the jar isn't full of water");
        helper.assertTrue(jar.brew(new ItemStack(item("riverweed"))).is(item("blue_ent_draught")),
                "riverweed doesn't brew the blue draught");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "machines")
    public static void displayBlocksAndChests(GameTestHelper helper) {
        helper.assertTrue(item("wooden_plate") instanceof BlockItem bi && bi.getBlock() == block("wood_plate"),
                "the wooden plate item doesn't place the plate");
        helper.assertTrue(item("armor_stand") instanceof BlockItem, "the armour stand item doesn't place it");
        helper.assertTrue(DisplayBlock.plateFood(new ItemStack(Items.BREAD)), "bread can't go on a plate");
        helper.assertFalse(DisplayBlock.plateFood(new ItemStack(Items.MUSHROOM_STEW)), "stew can go on a plate");
        helper.assertTrue(DisplayBlock.rackWeapon(new ItemStack(Items.IRON_SWORD)), "a sword can't hang on a rack");
        BlockPos plate = new BlockPos(1, 2, 1);
        helper.setBlock(plate, block("plate"));
        DisplayBlockEntity be = helper.getBlockEntity(plate);
        helper.assertTrue("plate".equals(be.kind()) && be.getContainerSize() == 1, "the plate holds the wrong amount");
        BlockPos stand = new BlockPos(2, 2, 1);
        helper.setBlock(stand, block("armor_stand_block"));
        DisplayBlockEntity standBe = helper.getBlockEntity(stand);
        helper.assertTrue(standBe.getContainerSize() == 4, "the armour stand doesn't hold four pieces");
        BlockPos chest = new BlockPos(3, 2, 1);
        helper.setBlock(chest, block("stone_chest"));
        LotrChestBlockEntity chestBe = helper.getBlockEntity(chest);
        chestBe.setItem(26, new ItemStack(Items.DIAMOND));
        helper.destroyBlock(chest);
        helper.assertItemEntityPresent(Items.DIAMOND, chest, 2);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "machines")
    public static void rackHangsOnWalls(GameTestHelper helper) {
        BlockPos wall = new BlockPos(1, 2, 1);
        BlockPos rack = wall.west();
        helper.setBlock(wall, Blocks.STONE_BRICKS);
        helper.setBlock(rack, block("weapon_rack").defaultBlockState().setValue(WeaponRackBlock.WALL, true)
                .setValue(WeaponRackBlock.FACING, Direction.EAST));
        helper.assertBlockPresent(block("weapon_rack"), rack);
        helper.setBlock(wall, Blocks.AIR);
        helper.assertBlockNotPresent(block("weapon_rack"), rack);
        helper.succeed();
    }
}
