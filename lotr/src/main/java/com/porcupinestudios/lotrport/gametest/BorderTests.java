package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.border.BorderData;
import com.porcupinestudios.lotrport.border.Conquest;
import com.porcupinestudios.lotrport.border.ConquestData;
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
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
        // only pixels two deep inside the Shire: borders are wobbled, so an edge pixel's centre can generate as the
        // next biome
        List<int[]> inside = new java.util.ArrayList<>();
        for (int[] p : map.pixelCentres(index, 4000)) {
            int px = (int) Math.floor(map.pixelX(p[0]));
            int pz = (int) Math.floor(map.pixelZ(p[1]));
            boolean deep = true;
            for (int dz = -2; dz <= 2 && deep; dz++) {
                for (int dx = -2; dx <= 2 && deep; dx++) {
                    deep = map.pixelIndex(px + dx, pz + dz) == index;
                }
            }
            if (deep) {
                inside.add(p);
            }
        }
        int[] p = inside.get(random.nextInt(inside.size()));
        return new BlockPos(p[0], 70, p[1]);
    }

    /** A block in a low-lying biome nobody holds on the original map (wilderness), if the map has one. */
    private static Optional<BlockPos> wildBlock() {
        MiddleEarthMap map = MiddleEarthMap.get();
        int[][] samples = map.samplePositions();
        for (int b = 0; b < samples.length; b++) {
            if (samples[b] == null || b == map.oceanIndex()) {
                continue;
            }
            int pixel = Borders.pixelOf(samples[b][0], samples[b][1]);
            String id = map.biomes().get(b).id();
            if (Borders.baseOwner(pixel).isEmpty() && Conquest.terrain(pixel) < 1.5 && !id.contains("river")
                    && !id.contains("lake")) {
                return Optional.of(new BlockPos(samples[b][0], 70, samples[b][1]));
            }
        }
        return Optional.empty();
    }

    private static ConquestData.Settings assaultOdds(float death, float injury) {
        ConquestData.Settings s = ConquestData.settings();
        return new ConquestData.Settings(s.killPressure(), s.dayTicks(), s.pressureDecay(), s.damageRecovery(),
                s.flipMargin(), s.claimTerrainCost(), s.soldierAttack(), s.assaultPressure(), death, 0f, death,
                injury, s.injuryDays(), s.raceLossCount(), s.heavyLossShare(), s.heavyLossMin());
    }

    @GameTest(template = "empty", batch = "borders")
    public static void settlingTakesOnlyUnclaimedLand(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var me = Borders.dimension(server); // the key: the GameTest server skips datapack dimensions
        helper.assertTrue(BorderData.base().size() > 50, "border_base missing: " + BorderData.base().size());
        Optional<BlockPos> wild = wildBlock();
        helper.assertTrue(wild.isPresent(), "no low wilderness on the map");
        BlockPos shire = shireBlock(helper.getLevel().getRandom());
        int wildPixel = Borders.pixelOf(wild.get().getX(), wild.get().getZ());
        int shirePixel = Borders.pixelOf(shire.getX(), shire.getZ());
        Borders borders = Borders.get(server);
        Realms.Realm realm = Realms.get(server).found(server, "Settler Test Realm", Optional.of(UUID.randomUUID()),
                Optional.of(MORDOR), 0x663300);
        Colonies colonies = Colonies.get(server);
        Optional<Colony> inWild = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Wild Test Hold",
                me, wild.get(), realm.id(), 0);
        Optional<Colony> inShire = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Shire Test Hold",
                me, shire, realm.id(), 0);
        helper.assertTrue(inWild.isPresent() && inShire.isPresent(), "colonies not founded (too close to others?)");
        try {
            borders.recompute(server);
            helper.assertTrue(borders.owner(wildPixel).equals(Optional.of(realm.id())), "the wild isn't settled");
            helper.assertTrue(borders.owner(shirePixel).equals(Optional.of(HOBBIT)),
                    "a colony took the hobbits' land by settling: " + borders.owner(shirePixel));
            helper.assertTrue(Conquest.terrain(shirePixel) < 2 && Conquest.terrain(Borders.pixelOf(
                    mistyBlock().getX(), mistyBlock().getZ())) >= 4, "mountains don't defend more than the Shire");
        } finally {
            inWild.ifPresent(c -> colonies.disband(server, c, "test"));
            inShire.ifPresent(c -> colonies.disband(server, c, "test"));
        }
        borders.recompute(server);
        helper.assertTrue(borders.owner(wildPixel).isEmpty(), "the wild isn't nobody's again");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "borders")
    public static void conquestIsForwardedOntoTheOriginalMap(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var me = Borders.dimension(server); // the key: the GameTest server skips datapack dimensions
        BlockPos hall = shireBlock(helper.getLevel().getRandom());
        int pixel = Borders.pixelOf(hall.getX(), hall.getZ());
        helper.assertTrue(Borders.baseOwner(pixel).equals(Optional.of(HOBBIT)),
                "the Shire isn't the hobbits' on the original map: " + Borders.baseOwner(pixel));
        Borders borders = Borders.get(server);
        Conquest conquest = Conquest.get(server);
        Realms.Realm realm = Realms.get(server).found(server, "Border Test Realm", Optional.of(UUID.randomUUID()),
                Optional.of(MORDOR), 0x663300);
        helper.assertTrue(Conquest.hostile(server, realm.id(), HOBBIT), "a Mordor-born realm may not press the Shire");
        helper.assertTrue(!Conquest.hostile(server, HOBBIT, realm.id()),
                "a player kingdom's land can be pressed without a war");
        double defence = conquest.defence(server, pixel);
        helper.assertTrue(defence > 0, "the Shire has no defence");
        conquest.press(server, pixel, realm.id(), (float) (defence / 2), "test");
        helper.assertTrue(borders.owner(pixel).equals(Optional.of(HOBBIT)), "half the defence took the land");
        conquest.press(server, pixel, realm.id(), (float) defence, "test");
        try {
            helper.assertTrue(borders.owner(pixel).equals(Optional.of(realm.id())), "pressure past defence didn't "
                    + "take it: " + borders.owner(pixel));
            helper.assertTrue(Borders.baseOwner(pixel).equals(Optional.of(HOBBIT)), "the original map changed");
            helper.assertTrue(MiddleEarthMap.get().biomes().get(MiddleEarthMap.get().pixelIndex(pixel
                    % MiddleEarthMap.get().width(), pixel / MiddleEarthMap.get().width())).id().equals(SHIRE.toString()),
                    "the map's biome changed");
            helper.assertTrue(borders.changes().containsKey(pixel), "the change isn't in the overlay");

            // spawning in taken land: the holder's (its culture's) people with conquered_share 1
            BorderData.Settings s = BorderData.settings();
            BorderData.override(new BorderData.Settings(s.claimRadius(), s.peoplePerPixel(), s.maxRadius(),
                    s.recomputeTicks(), 1f, s.enemyFactor(), s.kingdomPledgeAlignment(), s.rulerAlignment(),
                    s.kingdomNeedsColony()));
            NpcSpawns.Table table = TerritorySpawns.rules(server, pixel, NpcSpawns.forBiome(SHIRE).orElseThrow(),
                    RandomSource.create(1));
            helper.assertTrue(!table.entries().isEmpty(), "no spawns in taken land");
            for (NpcSpawns.Entry e : table.entries()) {
                helper.assertTrue(NpcData.type(e.npc()).flatMap(NpcType::faction).equals(Optional.of(MORDOR)),
                        "a non-Mordor NPC in the Mordor-born realm's Shire: " + e.npc());
            }
        } finally {
            BorderData.override(null);
            borders.take(server, pixel, HOBBIT, "test"); // retaken: the mark goes, the original map shows again
        }
        helper.assertTrue(borders.owner(pixel).equals(Optional.of(HOBBIT)), "land isn't back to the original holder");
        helper.assertTrue(!borders.changes().containsKey(pixel), "the overlay still holds the pixel");
        NpcSpawns.Table back = TerritorySpawns.rules(server, pixel, NpcSpawns.forBiome(SHIRE).orElseThrow(),
                RandomSource.create(1));
        helper.assertTrue(back.equals(NpcSpawns.forBiome(SHIRE).orElseThrow()), "original land's spawns changed");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "borders")
    public static void assaultsCostSoldiersAndWearDefenceDown(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var me = Borders.dimension(server); // the key: the GameTest server skips datapack dimensions
        BlockPos hall = shireBlock(helper.getLevel().getRandom());
        int pixel = Borders.pixelOf(hall.getX(), hall.getZ());
        Realms.Realm realm = Realms.get(server).found(server, "Assault Test Realm", Optional.of(UUID.randomUUID()),
                Optional.of(MORDOR), 0x660000);
        Colonies colonies = Colonies.get(server);
        Colony colony = colonies.foundAi(server, UUID.randomUUID(), UUID.randomUUID(), "Assault Test Hold",
                me, hall, realm.id(), 0).orElseThrow();
        com.porcupinestudios.lotrport.society.Persons persons =
                com.porcupinestudios.lotrport.society.Persons.get(server);
        long now = server.overworld().getGameTime();
        for (int i = 0; i < 6; i++) {
            var p = persons.migrated(UUID.randomUUID(), LotrPort.id("mordor_orc"), "Orc " + i, now, colony.asOwner());
            persons.set(server, p, com.porcupinestudios.lotrport.society.CoreComponents.UNIT,
                    com.porcupinestudios.lotrport.society.CoreComponents.Unit.hired(now));
        }
        Conquest conquest = Conquest.get(server);
        try {
            helper.assertTrue(Conquest.fit(server, colony).size() == 6, "soldiers not fit");
            ConquestData.override(assaultOdds(0f, 1f)); // nobody dies, everyone is wounded
            double before = conquest.defence(server, pixel);
            Conquest.AssaultResult a = conquest.assault(server, colony, pixel, 3, RandomSource.create(2)).orElseThrow();
            helper.assertTrue(a.sent() == 3 && a.died() == 0 && a.injured() == 3, "wounds: " + a);
            helper.assertTrue(a.taken() || conquest.defence(server, pixel) < before, "defence didn't fall");
            helper.assertTrue(Conquest.fit(server, colony).size() == 3, "the wounded can still fight");
            ConquestData.override(assaultOdds(1f, 0f)); // everyone sent dies
            Conquest.AssaultResult b = conquest.assault(server, colony, pixel, 3, RandomSource.create(3)).orElseThrow();
            helper.assertTrue(b.died() == 3, "deaths: " + b);
            helper.assertTrue(Conquest.fit(server, colony).isEmpty(), "the dead still fight");
            helper.assertTrue(conquest.assault(server, colony, pixel, 1, RandomSource.create(4)).isEmpty(),
                    "an assault with nobody fit");
        } finally {
            ConquestData.override(null);
            Borders.get(server).take(server, pixel, HOBBIT, "test");
            colonies.disband(server, colony, "test");
        }
        helper.succeed();
    }

    private static BlockPos mistyBlock() {
        MiddleEarthMap map = MiddleEarthMap.get();
        for (int i = 0; i < map.biomes().size(); i++) {
            if (map.biomes().get(i).id().equals("lotr_port:misty_mountains")) {
                int[] p = map.pixelCentres(i, 1).get(0);
                return new BlockPos(p[0], 70, p[1]);
            }
        }
        throw new IllegalStateException("no Misty Mountains on the map");
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
