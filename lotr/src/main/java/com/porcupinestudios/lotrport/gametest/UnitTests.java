package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.LotrEntities;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.CoreComponents.Order;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.sim.ColonyScheduler;
import com.porcupinestudios.lotrport.unit.UnitSettings;
import com.porcupinestudios.lotrport.unit.Units;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hired units on the society core: orders and squads live on the Person and reach the body; a unit stationed in a
 * colony becomes its soldier, paid from the treasury, and deserts when unpaid; a body that loads takes the orders
 * its record got while it was away.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UnitTests {
    private UnitTests() {}

    private static LotrNpc hired(GameTestHelper helper, ServerPlayer player, double x) {
        Vec3 at = helper.absoluteVec(new Vec3(x, 2, 1.5));
        LotrNpc npc = LotrNpc.spawn(helper.getLevel(), LotrPort.id("gondor_soldier"), at.x, at.y, at.z)
                .orElseThrow();
        npc.hireTo(player);
        Society.hired(helper.getLevel().getServer(), npc, player.getUUID());
        return npc;
    }

    @GameTest(template = "empty")
    @SuppressWarnings("deprecation")
    public static void ordersAndSquadsReachTheBodyAndStationedSoldiersArePaidOrDesert(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Persons persons = Persons.get(server);
        LotrNpc a = hired(helper, player, 0.5);
        LotrNpc b = hired(helper, player, 2.5);
        Person pa = persons.get(a.getUUID()).orElseThrow();
        Person pb = persons.get(b.getUUID()).orElseThrow();
        helper.assertTrue(Units.commandable(server, player.getUUID()).size() == 2, "the player commands 2 units");

        // orders: the record changes and the body follows it
        Units.order(server, pa, Order.GUARD, Optional.empty());
        helper.assertTrue(a.command() == Order.GUARD && pa.get(CoreComponents.UNIT).orElseThrow().order()
                == Order.GUARD, "guard did not reach the body");
        // squads: one order moves the whole squad
        Units.setSquad(server, pa, "  Rangers  ");
        Units.setSquad(server, pb, "Rangers");
        helper.assertTrue(pa.get(CoreComponents.UNIT).flatMap(CoreComponents.Unit::squad).equals(Optional.of(
                "Rangers")), "squad name not trimmed");
        helper.assertTrue(Units.orderSquad(server, pa, Order.FOLLOW, Optional.empty()) == 2
                && a.command() == Order.FOLLOW && b.command() == Order.FOLLOW, "the squad did not follow");

        // stationing: the colony owns the unit, the body guards for the colony and stops following the player
        UUID colonyId = UUID.randomUUID();
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.foundAi(server, colonyId, player.getUUID(), "Unit Test Hold", Level.OVERWORLD,
                new BlockPos(4_000_000, 70, 4_000_000), LotrPort.id("gondor"), 0).orElseThrow();
        try {
            helper.assertTrue(Units.station(server, player.getUUID(), pa, colony).ok(), "could not station");
            helper.assertTrue(pa.owner().equals(colony.asOwner()) && a.owner().isEmpty()
                    && a.garrison().equals(Optional.of(colonyId)) && a.command() == Order.GUARD,
                    "the stationed body: owner " + a.owner() + " garrison " + a.garrison() + " " + a.command());
            helper.assertTrue(Society.soldiers(server, colony).size() == 1
                    && Units.commandable(server, player.getUUID()).size() == 2,
                    "the manager still commands the stationed soldier");
            helper.assertTrue(!a.isEnemy(player), "a soldier turned on its manager");
            // a soldier can't be told to follow: there is no one to follow
            Units.order(server, pa, Order.FOLLOW, Optional.empty());
            helper.assertTrue(a.command() == Order.GUARD, "a colony soldier follows");

            // pay: with coins it is paid; without, it counts unpaid days and deserts after desert_days
            colonies.set(colony, CoreComponents.TREASURY, new CoreComponents.Treasury(5));
            ColonyScheduler scheduler = ColonyScheduler.get(server);
            scheduler.runDay(server, colony, 1);
            helper.assertTrue(colony.get(CoreComponents.TREASURY).orElseThrow().coins() == 5
                    - UnitSettings.get().wage(), "the soldier was not paid");
            int desert = UnitSettings.get().desertDays();
            for (int i = 0; i <= desert + 4 && pa.has(CoreComponents.UNIT); i++) {
                scheduler.runDay(server, colony, 1);
            }
            helper.assertTrue(!pa.has(CoreComponents.UNIT) && pa.owner().equals(Owner.NONE)
                    && a.garrison().isEmpty() && a.command() == Order.NONE, "the unpaid soldier did not desert");

            // a body that loads takes orders given while it was away
            Units.order(server, pb, Order.GUARD, Optional.empty());
            CompoundTag saved = b.saveWithoutId(new CompoundTag());
            b.discard();
            Units.order(server, pb, Order.NONE, Optional.empty());
            LotrNpc back = LotrEntities.NPC.get().create(level);
            back.load(saved);
            level.addFreshEntity(back);
            helper.assertTrue(back.command() == Order.NONE && back.isOwnedBy(player),
                    "the loaded body kept old orders: " + back.command());
            back.discard();
        } finally {
            colonies.disband(server, colony, "test");
            a.discard();
        }
        helper.succeed();
    }
}
