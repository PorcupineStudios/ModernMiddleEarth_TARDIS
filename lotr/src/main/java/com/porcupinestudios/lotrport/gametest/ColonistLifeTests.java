package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.ColonistData;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colonist.Kin;
import com.porcupinestudios.lotrport.colonist.Opinions;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.Immigration;
import com.porcupinestudios.lotrport.colony.events.ColonyEvents;
import com.porcupinestudios.lotrport.journey.CaravanConditions;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Colonist life (2026-11-12): weddings from opinions, births, and immigration growing with upgrades. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ColonistLifeTests {
    private ColonistLifeTests() {}

    @GameTest(template = "empty")
    public static void friendsMarryHaveAChildAndUpgradesWidenImmigration(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Colonies colonies = Colonies.get(server);
        UUID ruler = UUID.randomUUID();
        BlockPos hall = new BlockPos(900000 + (int) (Math.abs(ruler.getLeastSignificantBits()) % 50000), 70, -900000);
        Colony c = colonies.foundAi(server, UUID.randomUUID(), ruler, "Life Test", helper.getLevel().dimension(),
                hall, LotrPort.id("gondor"), 0).orElseThrow();
        Persons persons = Persons.get(server);
        Person a = persons.migrated(UUID.randomUUID(), LotrPort.id("gondor_man"), "", 0, c.asOwner());
        Person b = persons.migrated(UUID.randomUUID(), LotrPort.id("gondor_man"), "", 0, c.asOwner());
        for (Person p : List.of(a, b)) {
            persons.set(server, p, CoreComponents.CITIZEN, new CoreComponents.Citizen(0, false));
            Colonists.settle(server, p);
        }
        helper.assertTrue(Colonists.adult(a, server.overworld().getGameTime()), "settlers should be grown");
        // close friends: a wedding finds them
        Opinions.set(persons, a, b.id(), 80);
        Opinions.set(persons, b, a.id(), 80);
        helper.assertTrue(ColonyEvents.run(server, c, LotrPort.id("wedding"), new Random(1)), "no wedding");
        Kin ka = a.get(ColonistData.KIN).orElseThrow();
        Kin kb = b.get(ColonistData.KIN).orElseThrow();
        helper.assertTrue(ka.spouse().equals(java.util.Optional.of(b.id())) && kb.spouse().equals(
                java.util.Optional.of(a.id())), "not married: " + ka.spouse() + " " + kb.spouse());
        helper.assertTrue(!ColonyEvents.run(server, c, LotrPort.id("wedding"), new Random(2)),
                "a second wedding with nobody single");
        // a birth: the child is theirs, a citizen, and not grown
        Person child = ColonyEvents.born(server, c, a, b);
        Kin kc = child.get(ColonistData.KIN).orElseThrow();
        helper.assertTrue(kc.parents().containsAll(List.of(a.id(), b.id())) && a.get(ColonistData.KIN).orElseThrow()
                .children().contains(child.id()), "the child's family is wrong");
        helper.assertTrue(!Colonists.adult(child, server.overworld().getGameTime()), "a newborn counts as grown");
        helper.assertTrue(Society.citizens(server, c).stream().anyMatch(p -> p.id().equals(child.id())),
                "the child is not a citizen");
        // immigration: one slot of lone settlers, until an upgrade opens a guest house
        Immigration.Perks before = Immigration.perks(server, c);
        colonies.set(c, CaravanConditions.UPGRADES, List.of("guest_house"));
        Immigration.Perks after = Immigration.perks(server, c);
        helper.assertTrue(before.slots() == 1 && before.maxGroup() == 1, "a new colony's perks: " + before);
        helper.assertTrue(after.slots() == 2 && after.maxGroup() == 2 && after.levels().contains("Guest house"),
                "after the guest house: " + after);
        Immigration.Board board = Immigration.draw(server, c, Immigration.Board.EMPTY, 0);
        helper.assertTrue(board.offers().size() == 2, "offers with two slots: " + board.offers().size());
        for (Person p : Society.citizens(server, c)) {
            persons.remove(server, p.id());
        }
        colonies.disband(server, c, "test");
        helper.succeed();
    }

    /** An immigrant family keeps its ties: married parents, their children, and a tree that shows it before and after. */
    @GameTest(template = "empty")
    public static void anImmigrantFamilyArrivesAsAFamily(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Colonies colonies = Colonies.get(server);
        Colony c = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Family Test",
                helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 1, 1)), LotrPort.id("gondor"), 0)
                .orElseThrow(() -> new AssertionError("no colony at the test spot"));
        Immigration.Offer o = Immigration.addFamily(server, c, 4).orElseThrow();
        helper.assertTrue(o.bond().equals("family") && o.members().size() == 4, "offer: " + o);
        helper.assertTrue(o.members().get(0).spouse() == 1 && o.members().get(1).spouse() == 0
                && o.members().get(2).parents().equals(List.of(0, 1)) && o.members().get(3).parents().equals(List.of(0,
                        1)), "ties in the offer: " + o.members());
        var tree = com.porcupinestudios.lotrport.colony.ui.CandidateViews.treeOf(o, 0);
        helper.assertTrue(tree.nodes().size() == 4 && tree.links().stream().filter(l -> l.kind().equals("spouse"))
                .count() == 1 && tree.links().stream().filter(l -> l.kind().equals("parent")).count() == 4,
                "the candidate tree: " + tree.links());
        List<Person> came = Immigration.welcome(server, helper.getLevel(), c, o);
        helper.assertTrue(came.size() == 4, "came: " + came.size());
        Kin mum = came.get(0).get(ColonistData.KIN).orElseThrow();
        Kin kid = came.get(2).get(ColonistData.KIN).orElseThrow();
        helper.assertTrue(mum.spouse().equals(java.util.Optional.of(came.get(1).id())) && mum.children().containsAll(
                List.of(came.get(2).id(), came.get(3).id())), "the parents' kin: " + mum);
        helper.assertTrue(kid.parents().containsAll(List.of(came.get(0).id(), came.get(1).id())), "a child's kin: "
                + kid);
        helper.assertTrue(!Colonists.adult(came.get(2), server.overworld().getGameTime()), "the children are grown");
        Persons persons = Persons.get(server);
        for (Person p : came) {
            helper.getLevel().getEntity(p.id());
            var e = helper.getLevel().getEntity(p.id());
            if (e != null) {
                e.discard();
            }
            persons.remove(server, p.id());
        }
        colonies.disband(server, c, "test");
        helper.succeed();
    }
}
