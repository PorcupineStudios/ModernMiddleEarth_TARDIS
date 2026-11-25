package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.ring.RingPowers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import top.theillusivec4.curios.api.CuriosApi;

/** Ring done-when: the One Ring in a Curios ring slot hides its wearer (no potion), mobs lose them, taking it off ends it. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RingTests {
    private RingTests() {}

    @GameTest(template = "empty")
    public static void ringPowerDataLoads(GameTestHelper helper) {
        RingPowers.Power p = RingPowers.powerOf(RingPowers.ONE_RING.get());
        helper.assertTrue(p != null, "no ring_power names lotr_port:one_ring: " + RingPowers.powers().keySet());
        helper.assertTrue(p.invisible() && p.mobVisibility() == 0, "the One Ring does not hide its wearer");
        helper.assertTrue(CuriosApi.getSlot("ring", false).isPresent(), "Curios has no ring slot");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void theOneRingHidesItsWearerWithoutAPotion(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 1, 8)));
        var inv = CuriosApi.getCuriosInventory(player).orElse(null);
        helper.assertTrue(inv != null, "the player has no Curios inventory");
        helper.assertTrue(inv.getStacksHandler("ring").isPresent(), "the player has no ring slot: " + inv.getCurios().keySet());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setTarget(player);
        inv.setEquippedCurio("ring", 0, new ItemStack(RingPowers.ONE_RING.get()));
        // a mock player isn't ticked by a connection, so run its ring tick here (PlayerTickEvent does it in game)
        helper.onEachTick(() -> RingPowers.update(player));
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(player.isInvisible(), "the wearer is not invisible");
            helper.assertTrue(player.getActiveEffects().isEmpty(), "the ring gave an effect (particles): "
                    + player.getActiveEffects());
            helper.assertTrue(player.getVisibilityPercent(zombie) < 0.001, "a zombie still sees the wearer: "
                    + player.getVisibilityPercent(zombie));
            helper.assertTrue(zombie.getTarget() != player, "the zombie kept the wearer as its target");
            inv.setEquippedCurio("ring", 0, ItemStack.EMPTY);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(!player.isInvisible(), "taking the ring off did not end the invisibility");
                helper.assertTrue(player.getVisibilityPercent(zombie) > 0.5, "the zombie still can't see the player");
                player.discard();
                helper.succeed();
            });
        });
    }
}
