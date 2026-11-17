package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.lotrport.content.woodset.LotrBoat;
import com.porcupinestudios.lotrport.content.woodset.LotrBoatItem;
import com.porcupinestudios.lotrport.content.woodset.WoodSets;
import com.porcupinestudios.lotrport.world.NoMonsters;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** What 1.8 - 1.21 added to Middle-earth: full wood sets, no vanilla monsters. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModernTests {
    private ModernTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    @GameTest(template = "empty")
    public static void everyWoodHasAFullSet(GameTestHelper helper) {
        int woods = WoodSets.types().size();
        helper.assertTrue(woods >= 39, "only " + woods + " wood types");
        for (String base : WoodSets.types().keySet()) {
            for (String id : new String[] {"stripped_" + base + "_wood", "stripped_" + base + "_wood_beam",
                    base + "_button", base + "_pressure_plate", base + "_sign", base + "_hanging_sign", base + "_boat",
                    base + "_chest_boat"}) {
                helper.assertTrue(item(id) != Items.AIR, "missing " + id);
            }
            helper.assertTrue(LotrBlocks.BY_ID.containsKey(base + "_wall_sign")
                    && LotrBlocks.BY_ID.containsKey(base + "_wall_hanging_sign"), "missing wall signs of " + base);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void axeStripsLotrLogsAndSignsAndBoatsWork(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        BlockPos at = new BlockPos(0, 2, 0);
        Block log = LotrBlocks.BY_ID.get("mallorn_wood").get();
        helper.setBlock(at, log.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        BlockPos abs = helper.absolutePos(at);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        UseOnContext ctx = new UseOnContext(p, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        BlockState stripped = helper.getLevel().getBlockState(abs)
                .getToolModifiedState(ctx, ItemAbilities.AXE_STRIP, false);
        helper.assertTrue(stripped != null && stripped.is(LotrBlocks.BY_ID.get("stripped_mallorn_wood").get()),
                "axe made " + stripped);
        helper.assertTrue(stripped.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X, "axis lost");

        helper.setBlock(at, LotrBlocks.BY_ID.get("mallorn_sign").get());
        helper.assertTrue(helper.getLevel().getBlockEntity(abs) != null
                && helper.getLevel().getBlockEntity(abs).getType() == WoodSets.SIGN.get(), "sign has no sign entity");

        LotrBoatItem boatItem = (LotrBoatItem) item("mallorn_boat");
        var boat = boatItem.create(helper.getLevel(), Vec3.atCenterOf(abs), 0f);
        helper.assertTrue(boat instanceof LotrBoat b && b.wood().equals("mallorn")
                && boat.getDropItem() == boatItem, "boat drops " + boat.getDropItem());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaMonstersDontSpawnNaturally(GameTestHelper helper) {
        helper.assertTrue(NoMonsters.blocks(EntityType.ZOMBIE, MobSpawnType.NATURAL), "zombies spawn");
        helper.assertTrue(NoMonsters.blocks(EntityType.PHANTOM, MobSpawnType.NATURAL), "phantoms spawn");
        helper.assertTrue(NoMonsters.blocks(EntityType.PILLAGER, MobSpawnType.PATROL), "patrols spawn");
        helper.assertTrue(!NoMonsters.blocks(EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG), "eggs blocked");
        helper.assertTrue(!NoMonsters.blocks(EntityType.COW, MobSpawnType.NATURAL), "cows blocked");
        // only in Middle-earth: the test world's overworld is a normal one
        helper.assertTrue(com.porcupinestudios.lotrport.world.LotrDimensions.isMiddleEarth(helper.getLevel())
                || !NoMonsters.blocked(helper.getLevel(), EntityType.ZOMBIE, MobSpawnType.NATURAL),
                "zombies blocked outside Middle-earth");
        helper.succeed();
    }

    /**
     * Nothing missed by converter/tags.py: every block that takes time to break is in a mineable tag or, like glass
     * and beds, in lotr_port:no_mining_tool; every LOTR log, plank, leaf, slab, stair, wall, fence, door and sign is
     * in its vanilla family tag.
     */
    @GameTest(template = "empty")
    public static void everyBlockHasAMiningTool(GameTestHelper helper) {
        var noTool = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                LotrPort.id("no_mining_tool"));
        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.List<String> family = new java.util.ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            var key = BuiltInRegistries.BLOCK.getKey(b);
            if (!key.getNamespace().equals(LotrPort.MOD_ID)) {
                continue;
            }
            BlockState s = b.defaultBlockState();
            float speed = s.getDestroySpeed(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            boolean mined = s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
                    || s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                    || s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
                    || s.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE);
            if (speed > 0 && !mined && !s.is(noTool)) {
                missing.add(key.getPath());
            }
            String p = key.getPath();
            var expect = b instanceof net.minecraft.world.level.block.LeavesBlock ? net.minecraft.tags.BlockTags.LEAVES
                    : b instanceof net.minecraft.world.level.block.SlabBlock ? net.minecraft.tags.BlockTags.SLABS
                    : b instanceof net.minecraft.world.level.block.StairBlock ? net.minecraft.tags.BlockTags.STAIRS
                    : b instanceof net.minecraft.world.level.block.WallBlock ? net.minecraft.tags.BlockTags.WALLS
                    : b instanceof net.minecraft.world.level.block.FenceBlock ? net.minecraft.tags.BlockTags.FENCES
                    : b instanceof net.minecraft.world.level.block.DoorBlock ? net.minecraft.tags.BlockTags.DOORS
                    : b instanceof net.minecraft.world.level.block.SignBlock ? net.minecraft.tags.BlockTags.ALL_SIGNS
                    : p.endsWith("_planks") ? net.minecraft.tags.BlockTags.PLANKS
                    : WoodSets.strippedOf(p) != null || p.startsWith("stripped_") ? net.minecraft.tags.BlockTags.LOGS
                    : null;
            if (expect != null && !s.is(expect)) {
                family.add(p + " not in " + expect.location());
            }
        }
        helper.assertTrue(missing.isEmpty(), missing.size() + " blocks without a mining tool: "
                + missing.subList(0, Math.min(10, missing.size())));
        helper.assertTrue(family.isEmpty(), family.size() + " blocks outside their family tag: "
                + family.subList(0, Math.min(10, family.size())));
        helper.succeed();
    }

    /** stonecutting.py: Gondor rock cuts into its bricks and their slabs (2 each), as vanilla stone does. */
    @GameTest(template = "empty")
    public static void stonecutterCutsLotrStone(GameTestHelper helper) {
        var input = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(item("gondor_rock")));
        var results = helper.getLevel().getRecipeManager()
                .getRecipesFor(net.minecraft.world.item.crafting.RecipeType.STONECUTTING, input, helper.getLevel());
        boolean bricks = false;
        boolean slabs = false;
        for (var r : results) {
            ItemStack out = r.value().getResultItem(helper.getLevel().registryAccess());
            bricks |= out.is(item("gondor_brick"));
            slabs |= out.is(item("gondor_brick_slab")) && out.getCount() == 2;
        }
        helper.assertTrue(bricks && slabs, "gondor rock cuts into " + results.size() + " things, bricks " + bricks
                + ", 2 slabs " + slabs);
        helper.succeed();
    }

    /** waters.py: the sea is split by latitude, and each sea has its own fish (cod north, tropical fish south). */
    @GameTest(template = "empty")
    public static void seasByLatitudeHaveTheirOwnLife(GameTestHelper helper) {
        var map = com.porcupinestudios.lotrport.world.map.MiddleEarthMap.get();
        helper.assertTrue(map.seaZones().size() == 5, "seas: " + map.seaZones());
        String north = map.seaZones().get(map.seaAt(0, -80000)).biome();
        String home = map.seaZones().get(map.seaAt(0, 20000)).biome();
        String south = map.seaZones().get(map.seaAt(0, 160000)).biome();
        helper.assertTrue(north.endsWith("frozen_ocean") && home.equals("lotr_port:ocean")
                && south.endsWith("warm_ocean"), north + " / " + home + " / " + south);
        var biomes = helper.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var warm = biomes.get(net.minecraft.resources.ResourceLocation.parse(south));
        helper.assertTrue(warm != null && warm.getMobSettings()
                .getMobs(net.minecraft.world.entity.MobCategory.WATER_AMBIENT).unwrap().stream()
                .anyMatch(d -> d.type == EntityType.TROPICAL_FISH), "no tropical fish in the warm sea");
        var river = biomes.get(LotrPort.id("river"));
        helper.assertTrue(river != null && river.getMobSettings()
                .getMobs(net.minecraft.world.entity.MobCategory.WATER_AMBIENT).unwrap().stream()
                .anyMatch(d -> d.type == EntityType.SALMON), "no salmon in rivers");
        helper.succeed();
    }
}
