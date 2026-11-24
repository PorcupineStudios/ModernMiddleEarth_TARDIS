package com.porcupinestudios.ringbearer.harness.gametest;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import com.porcupinestudios.ringbearer.harness.content.Harnessing;
import com.porcupinestudios.ringbearer.harness.content.HitchBlock;
import com.porcupinestudios.ringbearer.harness.content.HitchBlockEntity;
import com.porcupinestudios.ringbearer.harness.content.HitchLink;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Wearing a harness: it replaces the saddle, keeps saddles off, is needed to hitch, and comes off with shears. */
@GameTestHolder(HarnessMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WearingTests {
    private WearingTests() {}

    private static Horse tameHorse(GameTestHelper h, int x) {
        Horse horse = h.spawn(EntityType.HORSE, new BlockPos(x, 1, 1));
        horse.setTamed(true);
        horse.setNoAi(true);
        return horse;
    }

    private static Player playerHolding(GameTestHelper h, ItemStack stack) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return p;
    }

    @GameTest(template = "arena")
    public static void harnessReplacesTheSaddle(GameTestHelper h) {
        Horse horse = tameHorse(h, 1);
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
        h.assertTrue(horse.isSaddled(), "the horse wasn't saddled to start with");
        Player p = playerHolding(h, new ItemStack(HarnessMod.HARNESS.get()));
        p.interactOn(horse, InteractionHand.MAIN_HAND);
        h.assertTrue(Harnessing.wears(horse), "using a harness on a tamed horse didn't put it on");
        h.assertFalse(horse.isSaddled(), "the saddle stayed on under the harness");
        h.assertTrue(p.getInventory().countItem(Items.SADDLE) == 1, "the saddle didn't come back to the player");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the harness wasn't used up");
        h.assertFalse(horse.isSaddleable(), "a harnessed horse still takes a saddle (its inventory's saddle slot)");

        // a saddle won't go on now
        Player q = playerHolding(h, new ItemStack(Items.SADDLE));
        q.interactOn(horse, InteractionHand.MAIN_HAND);
        h.assertFalse(horse.isSaddled(), "a saddle went on over the harness");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void onlyTameGrownHorsesWearOne(GameTestHelper h) {
        Horse wild = h.spawn(EntityType.HORSE, new BlockPos(1, 1, 1));
        wild.setNoAi(true);
        h.assertFalse(Harnessing.putOn(wild, null), "an untamed horse took a harness");
        Horse foal = tameHorse(h, 2);
        foal.setAge(-24000);
        h.assertFalse(Harnessing.putOn(foal, null), "a foal took a harness");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void hitchingNeedsAHarness(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 1, 0);
        h.setBlock(at, HarnessMod.HITCH.get().defaultBlockState().setValue(HitchBlock.FACING, Direction.SOUTH));
        HitchBlockEntity hitch = (HitchBlockEntity) h.getBlockEntity(at);
        Horse bare = tameHorse(h, 0);
        Horse harnessed = tameHorse(h, 2);
        Harnessing.putOn(harnessed, null);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 1, 1.5)));
        bare.setLeashedTo(p, true);
        harnessed.setLeashedTo(p, true);
        int[] result = HitchBlock.hitchLed(hitch, p);
        h.assertTrue(result[0] == 1 && result[1] == 1, "hitched " + result[0] + ", bare " + result[1]
                + " (expected 1 and 1)");
        h.assertTrue(HitchLink.of(harnessed).isPresent(), "the harnessed horse isn't linked to the hitch");
        h.assertTrue(HitchLink.of(bare).isEmpty(), "the bare horse got hitched");
        h.assertTrue(bare.getLeashHolder() == p, "the bare horse lost its lead");
        h.assertTrue(harnessed.getLeashHolder() == null, "the hitched horse kept its lead");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void shearsTakeItOffAndUnhitch(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 1, 0);
        h.setBlock(at, HarnessMod.HITCH.get().defaultBlockState().setValue(HitchBlock.FACING, Direction.SOUTH));
        HitchBlockEntity hitch = (HitchBlockEntity) h.getBlockEntity(at);
        Horse horse = tameHorse(h, 1);
        Harnessing.putOn(horse, null);
        h.assertTrue(hitch.hitch(horse), "couldn't hitch a harnessed horse");
        Player p = playerHolding(h, new ItemStack(Items.SHEARS));
        p.interactOn(horse, InteractionHand.MAIN_HAND);
        h.assertFalse(Harnessing.wears(horse), "shears didn't take the harness off");
        h.assertTrue(HitchLink.of(horse).isEmpty(), "taking the harness off didn't unhitch it");
        h.assertFalse(hitch.hasHitches(), "the hitch still holds the horse");
        AABB around = horse.getBoundingBox().inflate(3);
        h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                i -> i.getItem().is(HarnessMod.HARNESS.get())).size() == 1, "the harness wasn't dropped");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void harnessDropsWhenTheHorseDies(GameTestHelper h) {
        Horse horse = tameHorse(h, 1);
        Harnessing.putOn(horse, null);
        horse.kill();
        h.runAfterDelay(2, () -> {
            AABB around = new AABB(h.absolutePos(BlockPos.ZERO)).inflate(6);
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, around,
                    i -> i.getItem().is(HarnessMod.HARNESS.get())).size() == 1, "no harness dropped");
            h.succeed();
        });
    }
}
