package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.travel.Companions;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 2026-11-19 fixes: fruit trees drop fruit and saplings (converter/leaf_drops.py), LOTR modifiers don't glint
 * (ModifierGlintMixin), saddles from leather, small pouches from leather and string, bigger ones by combining, companions teleport with the player
 * (travel/Companions).
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FixesTests {
    private FixesTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    @GameTest(template = "empty")
    public static void fruitLeavesDropFruitAndSaplings(GameTestHelper helper) {
        Block leaves = BuiltInRegistries.BLOCK.get(LotrPort.id("pear_leaves"));
        ItemStack tool = new ItemStack(Items.DIAMOND_SWORD);
        tool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.FORTUNE), 3);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        boolean pear = false;
        boolean sapling = false;
        for (int n = 0; n < 400 && !(pear && sapling); n++) {
            for (ItemStack s : Block.getDrops(leaves.defaultBlockState(), helper.getLevel(), pos, null, null, tool)) {
                pear |= s.is(item("pear"));
                sapling |= s.is(item("pear_sapling"));
            }
        }
        helper.assertTrue(pear, "pear leaves dropped no pears in 400 breaks with fortune III");
        helper.assertTrue(sapling, "pear leaves dropped no saplings in 400 breaks");
        List<ItemStack> sheared = Block.getDrops(leaves.defaultBlockState(), helper.getLevel(), pos, null, null,
                new ItemStack(Items.SHEARS));
        helper.assertTrue(sheared.size() == 1 && sheared.get(0).is(leaves.asItem()), "shears don't give the leaves");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void modifiersDontGlint(GameTestHelper helper) {
        var reg = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(reg.getHolderOrThrow(ResourceKey.create(Registries.ENCHANTMENT, LotrPort.id("bane_orc"))), 1);
        helper.assertFalse(sword.hasFoil(), "a LOTR modifier makes the sword glint");
        sword.enchant(reg.getHolderOrThrow(Enchantments.SHARPNESS), 1);
        helper.assertTrue(sword.hasFoil(), "a vanilla enchantment no longer glints");
        helper.succeed();
    }

    private static ItemStack craft(GameTestHelper helper, int w, int h, ItemStack... grid) {
        var level = helper.getLevel();
        CraftingInput input = CraftingInput.of(w, h, List.of(grid));
        return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(r -> r.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    @GameTest(template = "empty")
    public static void saddleAndPouchRecipes(GameTestHelper helper) {
        ItemStack l = new ItemStack(Items.LEATHER);
        ItemStack i = new ItemStack(Items.IRON_INGOT);
        ItemStack s = new ItemStack(Items.STRING);
        ItemStack e = ItemStack.EMPTY;
        helper.assertTrue(craft(helper, 3, 2, l, l, l, i, e, i).is(Items.SADDLE), "3 leather + 2 iron isn't a saddle");
        helper.assertTrue(craft(helper, 3, 2, l, s, l, e, l, e).is(item("small_pouch")), "no small pouch");
        helper.assertTrue(craft(helper, 3, 3, l, s, l, l, e, l, l, l, l).isEmpty(), "medium pouches are craftable");
        // bigger pouches come from combining, contents kept
        ItemStack a = new ItemStack(item("small_pouch"));
        com.porcupinestudios.lotrport.content.pouch.PouchItem.store(a, List.of(new ItemStack(Items.DIAMOND, 3)));
        ItemStack b = new ItemStack(item("small_pouch"));
        com.porcupinestudios.lotrport.content.pouch.PouchItem.store(b, List.of(new ItemStack(Items.EMERALD, 5)));
        ItemStack medium = craft(helper, 2, 1, a, b);
        helper.assertTrue(medium.is(item("medium_pouch")), "two small pouches don't make a medium one");
        var held = com.porcupinestudios.lotrport.content.pouch.PouchItem.contents(medium);
        helper.assertTrue(held.stream().anyMatch(x -> x.is(Items.DIAMOND) && x.getCount() == 3)
                && held.stream().anyMatch(x -> x.is(Items.EMERALD) && x.getCount() == 5), "the pouches' items were lost");
        helper.assertTrue(craft(helper, 2, 1, new ItemStack(item("small_pouch")), medium).is(item("large_pouch")),
                "small + medium isn't a large pouch");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void companionsTeleportWithThePlayer(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 10, 10, () -> {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setPos(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
            Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(3, 2, 3));
            wolf.tame(player);
            Pig pig = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 4));
            pig.setLeashedTo(player, true);
            Pig stranger = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 5));
            var target = helper.absolutePos(new BlockPos(8, 2, 8));
            Companions.teleport(player, target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
            helper.assertTrue(wolf.distanceToSqr(player) < 16, "the tamed wolf stayed behind");
            helper.assertTrue(pig.distanceToSqr(player) < 16, "the leashed pig stayed behind");
            helper.assertTrue(stranger.distanceToSqr(player) > 16, "a stranger's pig came along");
            helper.succeed();
        });
    }

    /** #96: a head under quagmire suffocates (it has no collision, so vanilla's in-wall check missed it). */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void quagmireSuffocates(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 2, 2, () -> {
            Block mud = com.porcupinestudios.lotrport.content.LotrBlocks.BY_ID.get("quagmire").get();
            BlockPos at = new BlockPos(1, 1, 1);
            helper.setBlock(at, mud);
            helper.setBlock(at.above(), mud);
            helper.setBlock(at.below(), net.minecraft.world.level.block.Blocks.STONE); // it can't sink out of it
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, at);
            float full = pig.getHealth();
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(pig.getHealth() < full, "a pig under quagmire wasn't hurt (" + pig.getHealth() + ")");
                pig.discard();
                helper.succeed();
            });
        });
    }

    /** #98: a party tree's soil base on a pillar over a pit is filled down to the ground, not left as a ledge. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bigTreesLeaveNoDirtLedge(GameTestHelper helper) {
        net.minecraft.server.level.ServerLevel level = helper.getLevel();
        // a grass pillar on a stone floor two blocks down, away from the test grid and over the flat world's ground
        BlockPos soil = helper.absolutePos(BlockPos.ZERO).offset(0, 30, -500);
        int r = 8;
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                level.setBlock(soil.offset(dx, -2, dz), net.minecraft.world.level.block.Blocks.STONE
                        .defaultBlockState(), 2);
                level.setBlock(soil.offset(dx, -1, dz), air, 2);
                level.setBlock(soil.offset(dx, 0, dz), air, 2);
            }
        }
        level.setBlock(soil.below(), net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 2);
        level.setBlock(soil, net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        var config = (com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature.Config) level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .get(LotrPort.id("tree/oak_party")).config();
        helper.assertTrue(com.porcupinestudios.lotrport.world.feature.TemplateTreeFeature.grow(level, level.random,
                soil.above(), config, 0, false, null), "the party tree didn't grow on " + level.getBlockState(soil));
        int dirt = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockState(soil.offset(dx, 0, dz)).is(
                        net.minecraft.tags.BlockTags.DIRT)) {
                    dirt++;
                    helper.assertTrue(!level.getBlockState(soil.offset(dx, -1, dz)).isAir(),
                            "the tree's soil hangs over air at " + dx + ", " + dz);
                }
            }
        }
        helper.assertTrue(dirt > 0, "the party tree has no soil base to test");
        // round the base the ground slopes down a block per block: next to it, the bank is one block lower
        int banks = 0;
        for (int dx = -r + 1; dx < r; dx++) {
            for (int dz = -r + 1; dz < r; dz++) {
                BlockPos c = soil.offset(dx, 0, dz);
                // the tree's own soil, not the test's grass pillar under the origin
                java.util.function.Predicate<BlockPos> base = q -> !q.equals(soil)
                        && level.getBlockState(q).is(net.minecraft.world.level.block.Blocks.DIRT);
                boolean nextToBase = !base.test(c)
                        && (base.test(c.east()) || base.test(c.west()) || base.test(c.north()) || base.test(c.south()));
                if (nextToBase && level.getBlockState(c).isAir()) {
                    banks++;
                    helper.assertTrue(level.getBlockState(c.below()).is(net.minecraft.world.level.block.Blocks
                            .GRASS_BLOCK), "no grass bank beside the base at " + dx + ", " + dz + ": "
                            + level.getBlockState(c.below()));
                }
            }
        }
        helper.assertTrue(banks > 0, "no open ground beside the base to check a bank on");
        helper.succeed();
    }
}
