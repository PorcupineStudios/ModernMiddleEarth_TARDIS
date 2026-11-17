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
        var level = helper.getLevel();
        helper.assertTrue(com.porcupinestudios.lotrport.world.LotrDimensions.isMiddleEarth(level),
                "the test world isn't Middle-earth");
        helper.assertTrue(NoMonsters.blocked(level, EntityType.ZOMBIE, MobSpawnType.NATURAL), "zombies spawn");
        helper.assertTrue(NoMonsters.blocked(level, EntityType.PHANTOM, MobSpawnType.NATURAL), "phantoms spawn");
        helper.assertTrue(!NoMonsters.blocked(level, EntityType.ZOMBIE, MobSpawnType.SPAWN_EGG), "eggs blocked");
        helper.assertTrue(!NoMonsters.blocked(level, EntityType.COW, MobSpawnType.NATURAL), "cows blocked");
        helper.succeed();
    }
}
