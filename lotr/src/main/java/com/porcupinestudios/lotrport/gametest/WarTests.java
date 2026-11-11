package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.work.WorkComponents;
import com.porcupinestudios.lotrport.land.Protection;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.war.War;
import com.porcupinestudios.lotrport.war.WarSettings;
import com.porcupinestudios.lotrport.war.Wars;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Colony wars, the rework's step 7 "done when": a war goes from declaration through the warning to a siege that
 * takes the colony, and a second war (a raid on an offline colony) ends in a peace with tribute capped by the score.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WarTests {
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");

    private WarTests() {}

    @GameTest(template = "empty", batch = "wars", timeoutTicks = 900)
    @SuppressWarnings("deprecation")
    public static void aWarGoesFromDeclarationToATakenColonyAndAPeace(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        helper.assertTrue(WarSettings.loadProblems().isEmpty(), "war data: " + WarSettings.loadProblems());
        WarSettings d = WarSettings.DEFAULT;
        WarSettings.Armies a = d.armies();
        // short times so the test runs in seconds: warning 40 ticks, marches 20, hold the Hall 300
        WarSettings.override(new WarSettings(new WarSettings.Armies(40, 20, 0, 20, a.musterCost(), a.raidCap(), 0,
                a.lootShare(), a.raidLootCap(), a.raidItemCap(), a.raidLosses(), 300, a.siegeZone(), 4, 6000, 6000),
                d.peace()));
        ServerPlayer lord = helper.makeMockServerPlayerInLevel();
        ServerPlayer keeper = helper.makeMockServerPlayerInLevel();
        UUID absent = UUID.randomUUID(); // owns a colony and is never online
        Colonies colonies = Colonies.get(server);
        Persons persons = Persons.get(server);
        Wars wars = Wars.get(server);
        int x = 6_000_016 + level.random.nextInt(10_000) * 16 * 64;
        // on the ground of the flat test world, so the Hall's zone holds the soldiers standing by it
        BlockPos home = new BlockPos(x, helper.absolutePos(BlockPos.ZERO).getY() + 1, 6_000_016);
        BlockPos keep = home.offset(600, 0, 0);
        BlockPos farm = home.offset(-600, 0, 0);
        Colony attacker = colony(server, colonies, lord.getUUID(), "War Test Home", home);
        Colony defender = colony(server, colonies, keeper.getUUID(), "War Test Keep", keep);
        Colony offline = colony(server, colonies, absent, "War Test Farm", farm);
        List<ChunkPos> forced = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                ChunkPos c = new ChunkPos((keep.getX() >> 4) + dx, (keep.getZ() >> 4) + dz);
                level.setChunkForced(c.x, c.z, true);
                forced.add(c);
            }
        }
        List<UUID> soldiers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Person p = persons.migrated(UUID.randomUUID(), LotrPort.id("gondor_soldier"), "Soldier " + i,
                    level.getGameTime(), attacker.asOwner());
            persons.set(server, p, CoreComponents.UNIT, CoreComponents.Unit.hired(level.getGameTime()));
            soldiers.add(p.id());
        }
        colonies.set(attacker, CoreComponents.TREASURY, new CoreComponents.Treasury(100));
        colonies.set(offline, CoreComponents.TREASURY, new CoreComponents.Treasury(500));
        colonies.set(offline, WorkComponents.STOCK, new WorkComponents.Stock(Map.of(BREAD, 100)));
        int history = HistoryLog.get(server).size();

        Runnable cleanup = () -> {
            WarSettings.override(null);
            wars.all().stream().filter(w -> w.involves(attacker.asOwner())).forEach(wars::forget);
            for (UUID id : soldiers) {
                if (level.getEntity(id) != null) {
                    level.getEntity(id).discard();
                }
                persons.remove(server, id);
            }
            colonies.disband(server, attacker, "test");
            colonies.disband(server, defender, "test");
            colonies.disband(server, offline, "test");
            forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
        };
        try {
            // declaring: a reason is needed, the war waits out its warning
            helper.assertTrue(!wars.declare(server, lord.getUUID(), attacker, defender, War.Goal.TAKE_COLONY, " ",
                    Optional.empty()).ok(), "a war without a reason was declared");
            helper.assertTrue(!wars.declare(server, keeper.getUUID(), attacker, defender, War.Goal.TAKE_COLONY,
                    "spite", Optional.empty()).ok(), "someone without the war permission declared");
            War siegeWar = wars.declare(server, lord.getUUID(), attacker, defender, War.Goal.TAKE_COLONY,
                    "border claim", Optional.empty()).war().orElseThrow();
            War raidWar = wars.declare(server, lord.getUUID(), attacker, offline, War.Goal.TRIBUTE, "insult",
                    Optional.empty()).war().orElseThrow();
            helper.assertTrue(siegeWar.state() == War.State.WARNING, "no warning period");
            helper.assertTrue(!wars.declare(server, lord.getUUID(), attacker, defender, War.Goal.VASSAL, "again",
                    Optional.empty()).ok(), "two wars between the same colonies");
            helper.assertTrue(!wars.muster(server, lord.getUUID(), siegeWar, War.Army.Kind.SIEGE, 2).ok(),
                    "an army mustered during the warning");
            helper.assertTrue(Protection.blocks(lord, level, keep.offset(1, 0, 1), Perm.BUILD),
                    "the keep's land is open before any siege");
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }

        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    wars.tick(server);
                    War siegeWar = war(server, attacker, defender);
                    War raidWar = war(server, attacker, offline);
                    helper.assertTrue(siegeWar.state() == War.State.ACTIVE, "the warning never ended");
                    // sieges need both sides online, raids need the defender away
                    helper.assertTrue(!wars.muster(server, lord.getUUID(), raidWar, War.Army.Kind.SIEGE, 2).ok(),
                            "a siege on a colony whose leaders are offline");
                    helper.assertTrue(!wars.muster(server, lord.getUUID(), siegeWar, War.Army.Kind.RAID, 2).ok(),
                            "a raid on a colony whose leaders are online");
                    helper.assertTrue(wars.muster(server, lord.getUUID(), raidWar, War.Army.Kind.RAID, 2).ok(),
                            "no raid");
                    Wars.Result siege = wars.muster(server, lord.getUUID(), siegeWar, War.Army.Kind.SIEGE, 10);
                    helper.assertTrue(siege.ok(), "no siege: " + siege.message().getString());
                    helper.assertTrue(siegeWar.armies().get(0).soldiers().size() == 2,
                            "the siege took soldiers already raiding");
                    helper.assertTrue(attacker.get(CoreComponents.TREASURY).orElseThrow().coins()
                            == 100 - 4L * WarSettings.get().armies().musterCost(), "mustering cost nothing");
                })
                .thenExecuteAfter(60, () -> {
                    wars.tick(server);
                    War raidWar = war(server, attacker, offline);
                    // the raid: fought in the abstract against no guards, a capped share taken, no blocks touched
                    helper.assertTrue(raidWar.tally().scoreA() == WarSettings.get().peace().raidScore(),
                            "the raid was not won: " + raidWar.reports());
                    long coins = offline.get(CoreComponents.TREASURY).orElseThrow().coins();
                    helper.assertTrue(coins == 450, "the raid took " + (500 - coins) + " coins, not 50");
                    helper.assertTrue(offline.get(WorkComponents.STOCK).orElseThrow().count(BREAD) == 90
                            && attacker.get(WorkComponents.STOCK).orElseThrow().count(BREAD) == 10,
                            "the raid took the wrong goods");
                    helper.assertTrue(!wars.muster(server, lord.getUUID(), raidWar, War.Army.Kind.RAID, 9).ok()
                            || WarSettings.get().armies().raidCooldown() == 0, "raided again in the cooldown");
                    // the siege: bodies by the Hall, its land open to the attackers only
                    War siegeWar = war(server, attacker, defender);
                    War.Army army = siegeWar.armies().get(0);
                    helper.assertTrue(army.stage() == War.Army.Stage.SIEGE && army.landed(),
                            "the siege army did not land: " + army.stage());
                    helper.assertTrue(army.soldiers().stream().allMatch(id -> level.getEntity(id) != null),
                            "no soldier bodies at the keep");
                    helper.assertTrue(!Protection.blocks(lord, level, keep.offset(1, 0, 1), Perm.BUILD),
                            "the keep's land stayed shut to the besiegers");
                })
                .thenWaitUntil(() -> {
                    wars.tick(server);
                    helper.assertTrue(war(server, attacker, offline) != null && colonies.get(defender.id())
                            .map(c -> c.founder().equals(lord.getUUID())).orElse(false), "the keep is not taken yet");
                })
                .thenExecute(() -> {
                    // taken: the war ended, the colony is the lord's, its old members are gone, bodies went home
                    War done = wars.all().stream().filter(w -> w.defender().equals(defender.asOwner()))
                            .findFirst().orElseThrow();
                    helper.assertTrue(done.state() == War.State.ENDED && done.result().equals("taken"),
                            "the war did not end with the colony taken: " + done.result());
                    helper.assertTrue(done.tally().scoreA() >= WarSettings.get().peace().siegeScore(),
                            "no score for the siege");
                    helper.assertTrue(done.armies().stream().allMatch(x2 -> x2.stage() == War.Army.Stage.RETURNING),
                            "the army did not turn for home");
                    helper.assertTrue(Protection.blocks(keeper, level, keep.offset(1, 0, 1), Perm.BUILD),
                            "the old owner still builds in the taken keep");
                    helper.assertTrue(level.getEntity(done.armies().get(0).soldiers().get(0)) == null,
                            "the siege bodies stayed");

                    // peace for the other war: terms are capped by the score
                    War raidWar = war(server, attacker, offline);
                    int cap = Wars.tributeCap(raidWar, true);
                    helper.assertTrue(cap == 50, "tribute cap " + cap);
                    helper.assertTrue(!wars.propose(server, lord.getUUID(), raidWar, cap + 1).ok(),
                            "tribute above the score was offered");
                    helper.assertTrue(wars.propose(server, lord.getUUID(), raidWar, cap).ok(), "no offer");
                    helper.assertTrue(!wars.accept(server, lord.getUUID(), raidWar).ok(),
                            "the side that offered accepted its own offer");
                    helper.assertTrue(wars.accept(server, absent, raidWar).ok(), "the offer was not accepted");
                    helper.assertTrue(raidWar.state() == War.State.ENDED && offline.get(CoreComponents.TREASURY)
                            .orElseThrow().coins() == 400, "no tribute paid");
                    long types = HistoryLog.get(server).entries().stream().skip(history)
                            .map(e -> e.type()).filter(t -> t.getNamespace().equals(LotrPort.MOD_ID))
                            .filter(t -> t.getPath().startsWith("war_") || t.getPath().equals("siege")
                                    || t.getPath().equals("raid") || t.getPath().equals("army_mustered")
                                    || t.getPath().equals("colony_transferred"))
                            .distinct().count();
                    helper.assertTrue(types >= 7, "history is missing war entries (" + types + " kinds)");
                    cleanup.run();
                })
                .thenSucceed();
    }

    private static War war(MinecraftServer server, Colony a, Colony b) {
        return Wars.get(server).all().stream().filter(w -> w.attacker().equals(a.asOwner())
                && w.defender().equals(b.asOwner())).findFirst().orElse(null);
    }

    private static Colony colony(MinecraftServer server, Colonies colonies, UUID owner, String name, BlockPos hall) {
        Colony c = colonies.foundAi(server, UUID.randomUUID(), owner, name, Level.OVERWORLD, hall,
                LotrPort.id("gondor"), 0).orElseThrow();
        colonies.transfer(server, c, owner, c.faction(), false, "test");
        return c;
    }
}
