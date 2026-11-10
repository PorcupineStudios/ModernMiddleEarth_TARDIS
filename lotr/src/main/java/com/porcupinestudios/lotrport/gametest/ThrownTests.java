package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrWeaponItem;
import com.porcupinestudios.lotrport.content.thrown.OrcBomb;
import com.porcupinestudios.lotrport.content.thrown.ThrowableItem;
import com.porcupinestudios.lotrport.content.thrown.ThrowingAxeItem;
import com.porcupinestudios.lotrport.content.thrown.ThrownMissile;
import com.porcupinestudios.lotrport.content.thrown.ThrownWeapon;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LOTR's thrown weapons and missiles: a spear and a throwing axe hurt what they hit and come back to be picked up, a
 * pebble falls as an item again, redstone lights an orc bomb, and an NPC archer shoots at its enemy.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ThrownTests {
    private ThrownTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    /**
     * Force-loads the chunks under the test's first {@code dx} x {@code dz} blocks and runs {@code body} once they
     * tick entities: the 1-block template only keeps its own chunk loaded, and a projectile that crosses into an
     * unloaded neighbour stops in mid-air.
     */
    private static void inLoadedArea(GameTestHelper helper, int dx, int dz, Runnable body) {
        BlockPos a = helper.absolutePos(new BlockPos(-2, 0, -2));
        BlockPos b = helper.absolutePos(new BlockPos(dx + 2, 0, dz + 2));
        for (int cx = Math.min(a.getX(), b.getX()) >> 4; cx <= Math.max(a.getX(), b.getX()) >> 4; cx++) {
            for (int cz = Math.min(a.getZ(), b.getZ()) >> 4; cz <= Math.max(a.getZ(), b.getZ()) >> 4; cz++) {
                helper.getLevel().setChunkForced(cx, cz, true);
            }
        }
        helper.runAfterDelay(3, body);
    }

    private static Pig target(GameTestHelper helper, Vec3 at) {
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, at);
        pig.setNoGravity(true);
        return pig;
    }

    @GameTest(template = "empty", batch = "thrown", timeoutTicks = 100)
    public static void spearsAndAxesHitAndDrop(GameTestHelper helper) {
        helper.assertTrue(item("gondorian_spear") instanceof LotrWeaponItem w && w.thrown(),
                "a Gondorian spear can't be thrown");
        helper.assertTrue(item("iron_throwing_axe") instanceof ThrowingAxeItem, "no iron throwing axe");
        inLoadedArea(helper, 2, 8, () -> spear(helper));
    }

    private static void spear(GameTestHelper helper) {
        Pig pig = target(helper, new Vec3(1.5, 3, 6.5));
        float health = pig.getHealth();
        Vec3 from = helper.absoluteVec(new Vec3(1.5, 3.2, 1.5));
        ItemStack spear = new ItemStack(item("gondorian_spear"));
        ThrownWeapon w = new ThrownWeapon(helper.getLevel(), from.x, from.y, from.z, spear, ThrownWeapon.Kind.SPEAR,
                ((LotrWeaponItem) spear.getItem()).throwMultiplier());
        w.pickup = AbstractArrow.Pickup.ALLOWED;
        w.shoot(0, 0, 1, 3f, 0f);
        helper.getLevel().addFreshEntity(w);
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < health, "the spear didn't hurt the pig");
            helper.assertTrue(w.isAlive(), "the spear was used up instead of dropping by its target");
            ItemStack back = w.getPickupItem();
            helper.assertTrue(back.is(spear.getItem()) && back.getDamageValue() == 1,
                    "the spear doesn't come back one point worn: " + back);
        });
    }

    @GameTest(template = "empty", batch = "thrown", timeoutTicks = 100)
    public static void pebblesFallAgain(GameTestHelper helper) {
        helper.assertTrue(item("pebble") instanceof ThrowableItem t && t.kind() == ThrownMissile.Kind.PEBBLE,
                "a pebble isn't a thrown pebble");
        inLoadedArea(helper, 2, 2, () -> pebble(helper));
    }

    private static void pebble(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        Vec3 from = helper.absoluteVec(new Vec3(1.5, 4, 1.5));
        ThrownMissile m = new ThrownMissile(helper.getLevel(), from.x, from.y, from.z, new ItemStack(item("pebble")),
                ThrownMissile.Kind.PEBBLE);
        m.shoot(0, -1, 0, 1f, 0f);
        helper.getLevel().addFreshEntity(m);
        helper.succeedWhen(() -> {
            helper.assertFalse(m.isAlive(), "the pebble is still flying");
            helper.assertItemEntityPresent(item("pebble"), new BlockPos(1, 2, 1), 2);
        });
    }

    @GameTest(template = "empty", batch = "thrown", timeoutTicks = 40)
    public static void redstoneLightsOrcBombs(GameTestHelper helper) {
        Block bomb = BuiltInRegistries.BLOCK.get(LotrPort.id("orc_bomb"));
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, bomb);
        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockNotPresent(bomb, pos);
            BlockPos abs = helper.absolutePos(pos);
            var lit = helper.getLevel().getEntitiesOfClass(OrcBomb.class, new AABB(abs).inflate(1));
            helper.assertTrue(lit.size() == 1, "redstone lit " + lit.size() + " bombs");
            lit.forEach(OrcBomb::discard); // don't blow up the neighbouring tests
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "thrown", timeoutTicks = 300)
    public static void npcArchersShoot(GameTestHelper helper) {
        inLoadedArea(helper, 3, 13, () -> archer(helper));
    }

    private static void archer(GameTestHelper helper) {
        Vec3 a = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        Vec3 b = helper.absoluteVec(new Vec3(1.5, 2, 11.5));
        Optional<LotrNpc> archer = LotrNpc.spawn(helper.getLevel(), LotrPort.id("gondor_archer"), a.x, a.y, a.z);
        Optional<LotrNpc> orc = LotrNpc.spawn(helper.getLevel(), LotrPort.id("mordor_orc"), b.x, b.y, b.z);
        if (archer.isEmpty() || orc.isEmpty()) {
            helper.fail("could not spawn the archer or the orc");
            return;
        }
        LotrNpc shooter = archer.get();
        LotrNpc victim = orc.get();
        for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)),
                helper.absolutePos(new BlockPos(3, 1, 13)))) {
            helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
        }
        shooter.setPersistenceRequired();
        victim.setPersistenceRequired();
        victim.setNoAi(true);
        shooter.setTarget(victim);
        helper.assertTrue(shooter.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem,
                "the Gondor archer holds " + shooter.getMainHandItem() + ", not a bow");
        float health = victim.getHealth();
        helper.succeedWhen(() -> {
            shooter.setTarget(victim);
            helper.assertTrue(victim.getHealth() < health, "the archer hasn't hit the orc (arrows "
                    + helper.getLevel().getEntitiesOfClass(AbstractArrow.class, shooter.getBoundingBox().inflate(20))
                            .size() + ", behaviours " + shooter.behaviours().size() + ", goals "
                    + shooter.goals().getAvailableGoals().stream().filter(g -> g.isRunning())
                            .map(g -> g.getGoal().getClass().getSimpleName()).toList()
                    + ", dist " + shooter.distanceTo(victim) + ", sees " + shooter.hasLineOfSight(victim)
                    + ", target " + shooter.getTarget() + ", hand " + shooter.getMainHandItem() + ")");
        });
    }
}
