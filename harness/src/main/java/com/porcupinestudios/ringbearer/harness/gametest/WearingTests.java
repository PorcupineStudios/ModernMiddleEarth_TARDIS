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

/** Wearing a harness: its own slot beside the saddle, needed to hitch, taken out of the slot to take it off. */
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
    public static void harnessAndSaddleTogether(GameTestHelper h) {
        Horse horse = tameHorse(h, 1);
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
        Player p = playerHolding(h, new ItemStack(HarnessMod.HARNESS.get()));
        p.interactOn(horse, InteractionHand.MAIN_HAND);
        h.assertTrue(Harnessing.wears(horse), "using a harness on a saddled horse didn't put it on");
        h.assertTrue(horse.isSaddled(), "the saddle came off for the harness");
        h.assertTrue(Harnessing.slot(horse).map(s -> s.getStackInSlot(0).is(HarnessMod.HARNESS.get())).orElse(false),
                "the harness isn't in the horse's Curios harness slot");
        h.assertTrue(p.getMainHandItem().isEmpty(), "the harness wasn't used up");
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
    public static void takingItOutOfTheSlotUnhitches(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 1, 0);
        h.setBlock(at, HarnessMod.HITCH.get().defaultBlockState().setValue(HitchBlock.FACING, Direction.SOUTH));
        HitchBlockEntity hitch = (HitchBlockEntity) h.getBlockEntity(at);
        Horse horse = tameHorse(h, 1);
        Harnessing.putOn(horse, null);
        h.assertTrue(hitch.hitch(horse), "couldn't hitch a harnessed horse");
        Harnessing.slot(horse).orElseThrow().setStackInSlot(0, ItemStack.EMPTY);
        // noticed within half a second (Curios' change event doesn't come for every animal)
        h.runAfterDelay(12, () -> {
            h.assertFalse(Harnessing.wears(horse), "taking the harness out of its slot didn't take it off");
            h.assertTrue(HitchLink.of(horse).isEmpty(), "taking the harness off didn't unhitch it");
            h.assertFalse(hitch.hasHitches(), "the hitch still holds the horse");
            h.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void inventoryHasAHarnessSlot(GameTestHelper h) {
        Horse horse = tameHorse(h, 1);
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        // the menu the horse's inventory screen opens (opening it for real sends packets a mock player can't take)
        var menu = new net.minecraft.world.inventory.HorseInventoryMenu(1, p.getInventory(),
                ((com.porcupinestudios.ringbearer.harness.mixin.HorseInventoryAccess) horse)
                        .ringbearer_harness$inventory(), horse, 0);
        net.minecraft.world.inventory.Slot saddle = menu.getSlot(0);
        h.assertTrue(saddle.mayPlace(new ItemStack(Items.SADDLE)), "the saddle slot won't take a saddle");
        h.assertFalse(saddle.mayPlace(new ItemStack(HarnessMod.HARNESS.get())), "the saddle slot takes a harness");
        net.minecraft.world.inventory.Slot harness = menu.slots.get(menu.slots.size() - 1);
        h.assertTrue(harness instanceof com.porcupinestudios.ringbearer.harness.content.HarnessSlot,
                "no harness slot in the horse's inventory");
        h.assertTrue(harness.isActive(), "the harness slot isn't shown");
        h.assertTrue(harness.mayPlace(new ItemStack(HarnessMod.HARNESS.get())), "the harness slot won't take a harness");
        h.assertFalse(harness.mayPlace(new ItemStack(Items.SADDLE)), "the harness slot takes a saddle");
        saddle.set(new ItemStack(Items.SADDLE));
        harness.set(new ItemStack(HarnessMod.HARNESS.get()));
        h.assertTrue(Harnessing.wears(horse) && horse.isSaddled(), "saddle and harness aren't both on");
        harness.set(ItemStack.EMPTY);
        h.assertFalse(Harnessing.wears(horse), "taking it out of the slot left it worn");
        h.assertTrue(Harnessing.slot(horse).map(s -> s.getStackInSlot(0).isEmpty()).orElse(false),
                "the Curios slot still holds the harness");
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
