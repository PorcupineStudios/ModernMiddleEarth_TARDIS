package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.Kingdoms;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.diplomacy.Terms;
import com.porcupinestudios.lotrport.diplomacy.Treaties;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.lotrport.society.CoreComponents;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The diplomacy table's rules: offers between kingdoms, AI answers, truces, trade, war and peace. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiplomacyTests {
    private static final ResourceLocation GONDOR = LotrPort.id("gondor");
    private static final ResourceLocation MORDOR = LotrPort.id("mordor");

    private DiplomacyTests() {}

    private static Colony capital(GameTestHelper helper, ResourceLocation side, long coins, int offset) {
        MinecraftServer server = helper.getLevel().getServer();
        int far = 200_000 + helper.getLevel().getRandom().nextInt(400_000);
        Colony c = Colonies.get(server).foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Diplomacy Test " + offset,
                helper.getLevel().dimension(), new BlockPos(far + offset * 5000, 64, far), side, 0).orElseThrow();
        Colonies.get(server).set(c, CoreComponents.TREASURY, new CoreComponents.Treasury(coins));
        return c;
    }

    @GameTest(template = "empty", batch = "diplomacy")
    public static void kingdomsTreatWithEachOther(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ra = helper.makeMockServerPlayerInLevel();
        ServerPlayer rb = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(Kingdoms.found(ra, "Treaty Test A", Optional.of(0x335577)) == Kingdoms.Result.FOUNDED, "A");
        helper.assertTrue(Kingdoms.found(rb, "Treaty Test B", Optional.of(0x775533)) == Kingdoms.Result.FOUNDED, "B");
        ResourceLocation a = Treaties.sideOf(ra).orElseThrow();
        ResourceLocation b = Treaties.sideOf(rb).orElseThrow();
        Colony ca = capital(helper, a, 500, 0);
        Colony cb = capital(helper, b, 100, 1);
        Treaties treaties = Treaties.get(server);
        try {
            Terms terms = Terms.NONE.withGiveCoins(200).withTrade(true).withTruceDays(5);
            helper.assertTrue(treaties.check(server, a, b, Terms.NONE.withGiveCoins(900)).isPresent(),
                    "an offer of more coins than the treasury holds passed the check");
            treaties.propose(server, ra.getUUID(), a, b, terms);
            List<Treaties.Offer> waiting = treaties.offers().stream().filter(o -> o.from().equals(a)).toList();
            helper.assertTrue(waiting.size() == 1, "a kingdom's offer isn't waiting for its ruler: " + waiting);
            helper.assertTrue(treaties.accept(server, ra.getUUID(), waiting.get(0).id()).getString().contains("no such"),
                    "the proposer accepted their own offer");
            treaties.accept(server, rb.getUUID(), waiting.get(0).id());
            helper.assertTrue(Treaties.treasury(server, a) == 300 && Treaties.treasury(server, b) == 300,
                    "coins didn't move: " + Treaties.treasury(server, a) + " / " + Treaties.treasury(server, b));
            helper.assertTrue(Treaties.trading(server, a, b), "no trade agreement");
            helper.assertTrue(treaties.whyNoWar(server, a, b).isPresent(), "war could be declared during a truce");
            Treaties.Treaty trade = treaties.of(a).stream().filter(t -> t.kind() == Treaties.Kind.TRADE).findFirst()
                    .orElseThrow();
            treaties.breakTreaty(server, ra.getUUID(), trade.id());
            helper.assertTrue(!Treaties.trading(server, a, b), "a broken trade agreement still holds");
        } finally {
            Colonies.get(server).disband(server, ca, "test");
            Colonies.get(server).disband(server, cb, "test");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "diplomacy")
    public static void greatPowersWeighOffersAndMakePeace(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer ruler = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(Kingdoms.found(ruler, "Treaty Test C", Optional.empty()) == Kingdoms.Result.FOUNDED, "C");
        ResourceLocation me = Treaties.sideOf(ruler).orElseThrow();
        Colony home = capital(helper, me, 1000, 2);
        Treaties treaties = Treaties.get(server);
        try {
            helper.assertTrue(Treaties.ruler(server, GONDOR).isEmpty(), "a great power has a ruler");
            treaties.propose(server, ruler.getUUID(), me, GONDOR, Terms.NONE.withVassal(Terms.Vassal.THEY_SERVE));
            helper.assertTrue(server.overworld() != null && !Factions.get(server, GONDOR).isEmpty()
                    && com.porcupinestudios.lotrport.faction.Diplomacy.get(server).overlord(GONDOR).isEmpty(),
                    "Gondor swore to a kingdom");
            treaties.propose(server, ruler.getUUID(), me, GONDOR, Terms.NONE.withTrade(true));
            helper.assertTrue(Treaties.trading(server, me, GONDOR), "Gondor refused trade at neutral standing");

            helper.assertTrue(treaties.whyNoWar(server, me, MORDOR).isEmpty(), "can't declare war on Mordor");
            treaties.declareWar(server, ruler.getUUID(), me, MORDOR, "test");
            helper.assertTrue(!Treaties.wars(server, me, MORDOR).isEmpty(), "no war with Mordor");
            helper.assertTrue(Factions.relation(server, me, MORDOR) == Relation.ENEMY, "not enemies after war");
            helper.assertTrue(treaties.check(server, me, MORDOR, Terms.NONE.withTrade(true)).isPresent(),
                    "trade without peace passed the check");
            treaties.propose(server, ruler.getUUID(), me, MORDOR, Terms.NONE.withEndWar(true).withTruceDays(10));
            helper.assertTrue(Treaties.wars(server, me, MORDOR).isEmpty(), "Mordor didn't take peace");
            helper.assertTrue(treaties.has(me, MORDOR, Treaties.Kind.TRUCE), "no truce after peace");
        } finally {
            Colonies.get(server).disband(server, home, "test");
        }
        helper.succeed();
    }
}
