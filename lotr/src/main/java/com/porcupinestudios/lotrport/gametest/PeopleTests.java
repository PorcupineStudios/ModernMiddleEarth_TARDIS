package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.behaviour.BanditBehaviour;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** LOTR's people and their ways (npc/behaviour): thieves, ruffians, orcs in daylight, panics and fleeing. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PeopleTests {
    private static final String EMPTY = "empty";

    private PeopleTests() {}

    static LotrNpc spawn(GameTestHelper helper, String type, double x, double z) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true); // peaceful removes NPCs at once
        Vec3 pos = helper.absoluteVec(new Vec3(x, 0, z));
        LotrNpc npc = LotrNpc.spawn(helper.getLevel(), LotrPort.id(type), pos.x, pos.y, pos.z)
                .orElseThrow(() -> new IllegalStateException("could not spawn " + type));
        npc.setPersistenceRequired();
        return npc;
    }

    static ServerPlayer player(GameTestHelper helper, double x, double z) {
        // makeMockServerPlayerInLevel's player always says it is creative, which thieves and the fearful ignore
        var level = helper.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-survivor"), false);
        ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        p.setGameMode(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(new Vec3(x, 0, z));
        p.moveTo(pos.x, pos.y, pos.z);
        return p;
    }

    private static Item coin() {
        return BuiltInRegistries.ITEM.get(LotrPort.id("silver_coin"));
    }

    /** Its own batch: a bandit fights the other tests' ruffians and orcs. */
    @GameTest(template = EMPTY, batch = "people_bandit", timeoutTicks = 200)
    public static void banditStealsCoins(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 4, 4, () -> {
            ServerPlayer p = player(helper, 2.5, 1.5);
            p.getInventory().setItem(5, new ItemStack(coin(), 20)); // not the held slot
            LotrNpc bandit = spawn(helper, "bandit", 1.5, 1.5);
            helper.succeedWhen(() -> {
                bandit.setTarget(null); // other batches' NPCs run alongside; a hostile bandit would fight them
                helper.assertTrue(p.getInventory().countItem(coin()) < 20, "the bandit stole no coins; running " + bandit.goals().getAvailableGoals().stream()
                        .filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList()
                        + " alive " + bandit.isAlive() + " at " + bandit.position() + " player " + p.position()
                        + " target " + bandit.getTarget() + " behaviours " + bandit.behaviours() + " goals "
                        + bandit.goals().getAvailableGoals().stream().map(g -> g.getGoal().getClass().getSimpleName()).toList()
                        + " creative " + p.isCreative() + " spect " + p.isSpectator() + " inv " + p.getInventory().items.get(5));
                helper.assertTrue(!BanditBehaviour.loot(bandit).isEmpty(), "the bandit isn't carrying the coins");
            });
        });
    }

    @GameTest(template = EMPTY, batch = "people")
    public static void banditDropsLootOnDeath(GameTestHelper helper) {
        LotrNpc bandit = spawn(helper, "bandit", 1.5, 1.5);
        BanditBehaviour.loot(bandit).add(new ItemStack(coin(), 7).save(bandit.registryAccess()));
        bandit.kill();
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, bandit.getBoundingBox().inflate(3),
                i -> i.getItem().is(coin()) && i.getItem().getCount() == 7).size() == 1, "the stolen coins weren't dropped");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = "people")
    public static void ruffiansGangUp(GameTestHelper helper) {
        ServerPlayer p = player(helper, 0.5, 0.5);
        LotrNpc hit = spawn(helper, "ruffian_brute", 1.5, 1.5);
        LotrNpc friend = spawn(helper, "ruffian_brute", 3.5, 1.5);
        hit.hurt(helper.getLevel().damageSources().playerAttack(p), 1);
        helper.assertTrue(friend.getTarget() == p, "the other ruffian didn't turn on the attacker");
        helper.succeed();
    }

    /** The orc checks the daylight once a second, so its chunk must tick: forced loaded, as the creature tests do. */
    @GameTest(template = EMPTY, batch = "people_day", timeoutTicks = 200)
    public static void weakOrcSlowInDaylight(GameTestHelper helper) {
        helper.getLevel().setDayTime(6000);
        ThrownTests.inLoadedArea(helper, 2, 2, () -> {
            LotrNpc orc = spawn(helper, "mordor_orc", 1.5, 1.5);
            helper.assertTrue(helper.getLevel().canSeeSky(orc.blockPosition()), "the test spot has no sky");
            helper.succeedWhen(() -> {
                helper.getLevel().setDayTime(6000); // other tests change the time
                helper.assertTrue(orc.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)
                    && orc.hasEffect(MobEffects.WEAKNESS), "a weak orc in sunlight isn't weakened: day " + helper.getLevel().isDay() + " alive " + orc.isAlive() + " ticks " + orc.tickCount + " sky " + helper.getLevel().canSeeSky(orc.blockPosition()) + " biome " + helper.getLevel().getBiome(orc.blockPosition()).unwrapKey());
            });
        });
    }

    @GameTest(template = EMPTY, batch = "people", timeoutTicks = 200)
    public static void burningNpcPanics(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 8, 8, () -> {
            LotrNpc man = spawn(helper, "gondor_soldier", 4.5, 4.5);
            Vec3 start = man.position();
            man.igniteForSeconds(8);
            helper.succeedWhen(() -> helper.assertTrue(man.position().distanceTo(start) > 1.5, "the burning man stood still"));
        });
    }

    @GameTest(template = EMPTY, batch = "people", timeoutTicks = 200)
    public static void hobbitFleesEvilPlayer(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 8, 8, () -> {
            ServerPlayer p = player(helper, 4.5, 4.5);
            Alignment.set(p, LotrPort.id("hobbit"), -200);
            LotrNpc hobbit = spawn(helper, "hobbit", 6.5, 4.5);
            helper.succeedWhen(() -> helper.assertTrue(hobbit.distanceTo(p) > 5, "the hobbit didn't run"));
        });
    }
}
