package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.BorderData;
import com.porcupinestudios.lotrport.border.Borders;
import com.porcupinestudios.lotrport.border.Kingdoms;
import com.porcupinestudios.lotrport.border.TerritorySpawns;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Pledges;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.npc.NpcData;
import com.porcupinestudios.lotrport.npc.NpcSpawns;
import com.porcupinestudios.lotrport.npc.NpcType;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Borders (phase: kingdoms and borders): conquest goes onto the player map, never onto the original map or terrain. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BorderTests {
    private static final ResourceLocation SHIRE = LotrPort.id("shire");
    private static final ResourceLocation HOBBIT = LotrPort.id("hobbit");
    private static final ResourceLocation MORDOR = LotrPort.id("mordor");

    private BorderTests() {}

    /** A Shire pixel's block centre, picked at random so reruns on a kept world don't meet old colonies. */
    private static BlockPos shireBlock(RandomSource random) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int index = -1;
        for (int i = 0; i < map.biomes().size(); i++) {
            if (map.biomes().get(i).id().equals(SHIRE.toString())) {
                index = i;
            }
        }
        List<int[]> pixels = map.pixelCentres(index, 2000);
        int[] p = pixels.get(random.nextInt(pixels.size()));
        return new BlockPos(p[0], 70, p[1]);
    }

    @GameTest(template = "empty", batch = "borders")
    public static void conquestIsForwardedOntoTheOriginalMap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel me = LotrDimensions.middleEarth(server);
        helper.assertTrue(me != null, "no Middle-earth");
        helper.assertTrue(BorderData.base().size() > 50, "border_base missing: " + BorderData.base().size());
        BlockPos hall = shireBlock(helper.getLevel().getRandom());
        int pixel = Borders.pixelOf(hall.getX(), hall.getZ());
        helper.assertTrue(Borders.baseOwner(pixel).equals(Optional.of(HOBBIT)),
                "the Shire isn't the hobbits' on the original map: " + Borders.baseOwner(pixel));
        Borders borders = Borders.get(server);
        Realms.Realm realm = Realms.get(server).found(server, "Border Test Realm", Optional.of(UUID.randomUUID()),
                Optional.of(MORDOR), 0x663300);
        Colonies colonies = Colonies.get(server);
        Optional<Colony> made = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Border Test Hold",
                me.dimension(), hall, realm.id(), 0);
        helper.assertTrue(made.isPresent(), "colony not founded (too close to another?)");
        Colony colony = made.get();
        try {
            borders.recompute(server);
            helper.assertTrue(borders.owner(pixel).equals(Optional.of(realm.id())), "the realm doesn't hold its Hall's "
                    + "land: " + borders.owner(pixel));
            helper.assertTrue(Borders.baseOwner(pixel).equals(Optional.of(HOBBIT)), "the original map changed");
            helper.assertTrue(me.getBiome(hall).is(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.BIOME, SHIRE)), "the terrain's biome changed");
            helper.assertTrue(borders.changes().containsKey(pixel), "the change isn't in the overlay");

            colonies.transfer(server, colony, colony.founder(), MORDOR, true, "test");
            borders.recompute(server);
            helper.assertTrue(borders.owner(pixel).equals(Optional.of(MORDOR)), "the war's winner doesn't hold it");

            // spawning in taken land: the holder's people with conquered_share 1
            BorderData.Settings s = BorderData.settings();
            BorderData.override(new BorderData.Settings(s.claimRadius(), s.peoplePerPixel(), s.maxRadius(),
                    s.recomputeTicks(), 1f, s.enemyFactor(), s.kingdomPledgeAlignment(), s.rulerAlignment(),
                    s.kingdomNeedsColony()));
            NpcSpawns.Table biome = NpcSpawns.forBiome(SHIRE).orElseThrow();
            NpcSpawns.Table table = TerritorySpawns.adjust(me, hall, biome, RandomSource.create(1));
            helper.assertTrue(!table.entries().isEmpty(), "no spawns in taken land");
            for (NpcSpawns.Entry e : table.entries()) {
                helper.assertTrue(NpcData.type(e.npc()).flatMap(NpcType::faction).equals(Optional.of(MORDOR)),
                        "a non-Mordor NPC in Mordor's taken Shire: " + e.npc());
            }

            int marked = borders.mark(server, hall, 1, Optional.of(HOBBIT), false, "test");
            helper.assertTrue(marked > 0 && borders.owner(pixel).equals(Optional.of(HOBBIT)), "a mark doesn't win");
            borders.mark(server, hall, 1, Optional.empty(), true, "test");
        } finally {
            BorderData.override(null);
            colonies.disband(server, colony, "test");
        }
        borders.recompute(server);
        helper.assertTrue(borders.owner(pixel).equals(Optional.of(HOBBIT)), "land isn't back to the original holder");
        helper.assertTrue(!borders.changes().containsKey(pixel), "the overlay still holds the pixel");
        NpcSpawns.Table back = TerritorySpawns.adjust(me, hall, NpcSpawns.forBiome(SHIRE).orElseThrow(),
                RandomSource.create(1));
        helper.assertTrue(back.equals(NpcSpawns.forBiome(SHIRE).orElseThrow()), "original land's spawns changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "borders")
    public static void aKingdomIsItsOwnPledgeableFaction(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        MinecraftServer server = player.server;
        Kingdoms.Result r = Kingdoms.found(player, "Test Kingdom", Optional.of(0x2255AA));
        helper.assertTrue(r == Kingdoms.Result.FOUNDED, "not founded: " + r);
        Realms.Realm realm = Kingdoms.ruled(server, player).orElseThrow();
        helper.assertTrue(realm.color() == 0x2255AA, "colour " + Integer.toHexString(realm.color()));
        helper.assertTrue(Alignment.get(player, realm.id()) >= BorderData.settings().rulerAlignment(),
                "ruler alignment " + Alignment.get(player, realm.id()));
        helper.assertTrue(Factions.get(server, realm.id()).flatMap(f -> f.pledgeAlignment())
                .equals(Optional.of(BorderData.settings().kingdomPledgeAlignment())), "can't pledge to a kingdom");
        helper.assertTrue(Kingdoms.colonyFaction(player).equals(Optional.of(realm.id())),
                "the ruler's colonies wouldn't be the kingdom's");
        helper.assertTrue(Kingdoms.found(player, "Again", Optional.empty()) == Kingdoms.Result.ALREADY_RULER,
                "a second kingdom");
        helper.assertTrue(Pledges.pledge(player, realm.id()) == Pledges.Result.CANNOT_PLEDGE,
                "the ruler pledged to their own kingdom");

        ServerPlayer subject = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(Pledges.pledge(subject, realm.id()) == Pledges.Result.NOT_ENOUGH_ALIGNMENT,
                "pledged without alignment");
        Alignment.set(subject, realm.id(), BorderData.settings().kingdomPledgeAlignment());
        helper.assertTrue(Pledges.pledge(subject, realm.id()) == Pledges.Result.PLEDGED, "subject can't pledge");
        helper.assertTrue(Kingdoms.colonyFaction(subject).equals(Optional.of(realm.id())),
                "a subject's colony wouldn't be the kingdom's");
        helper.succeed();
    }
}
