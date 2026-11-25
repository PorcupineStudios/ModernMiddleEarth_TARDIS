package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.effect.LotrEffects;
import com.porcupinestudios.lotrport.effect.RegionEffects;
import com.porcupinestudios.lotrport.lair.LairLife;
import com.porcupinestudios.lotrport.world.map.Enclosures;
import com.porcupinestudios.lotrport.world.map.Lairs;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Done-when for the landscape pass (Aaron 2026-11-25): the Shire's orchards are small fenced plots on flat ground
 * and its corn fields come in sizes that fit; Eriador changes landscape only every few hundred blocks; Enchanted
 * Mirkwood, its spider nests and the Enchanted River lie only where the region layer allows; the enchanted forest's
 * effects load and work.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandscapeTests {
    private static final String EMPTY = "empty";
    /** Cells (128 blocks) searched round the Shire's middle: about 6 km square. */
    private static final int CELLS = 24;

    private LandscapeTests() {}

    private static int biome(MiddleEarthMap map, String id) {
        for (int i = 0; i < map.biomes().size(); i++) {
            if (map.biomes().get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void shireEnclosuresFitTheirKinds(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int shire = biome(map, "lotr_port:shire");
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Long> area = new HashMap<>();
        for (int cx = -CELLS; cx < CELLS; cx++) {
            for (int cz = -CELLS; cz < CELLS; cz++) {
                Enclosures.Enclosure e = map.enclosureInCellOf(cx * 128, cz * 128);
                if (e == null) {
                    continue;
                }
                Enclosures.Kind k = e.kind();
                counts.merge(k.name(), 1, Integer::sum);
                area.merge(k.name(), (long) e.sizeX() * e.sizeZ(), Long::sum);
                int smallest = Math.min(k.minLength(), k.minWidth());
                int largest = Math.max(k.maxLength(), k.maxWidth());
                for (int side : new int[] { e.sizeX(), e.sizeZ() }) {
                    helper.assertTrue(side >= smallest && side <= largest, k.name() + " is " + e.sizeX() + "x"
                            + e.sizeZ() + ", outside " + smallest + "-" + largest);
                }
                helper.assertTrue(e.minX() >= cx * 128 && e.maxX() < cx * 128 + 128 && e.minZ() >= cz * 128
                        && e.maxZ() < cz * 128 + 128, k.name() + " at " + e.minX() + "," + e.minZ()
                        + " leaves its cell");
                int[][] corners = { { e.minX(), e.minZ() }, { e.maxX(), e.minZ() }, { e.minX(), e.maxZ() },
                        { e.maxX(), e.maxZ() } };
                for (int[] c : corners) {
                    helper.assertTrue(map.biomeIndexAt(c[0], c[1]) == e.biome(), k.name() + " corner " + c[0]
                            + "," + c[1] + " is out of its biome");
                }
                helper.assertTrue(Roads.get().distanceWithin((e.minX() + e.maxX()) / 2, (e.minZ() + e.maxZ()) / 2,
                        64) > Math.max(e.sizeX(), e.sizeZ()) / 2.0, k.name() + " at " + e.minX() + "," + e.minZ()
                        + " is on a road");
                // flat inside: the map's ground is the plot's level everywhere inside the fence
                for (int x = e.minX(); x <= e.maxX(); x += 3) {
                    for (int z = e.minZ(); z <= e.maxZ(); z += 3) {
                        helper.assertTrue(Math.abs(map.heightAt(x, z) - e.level()) < 1e-6,
                                k.name() + " ground at " + x + "," + z + " is " + map.heightAt(x, z) + ", not "
                                        + e.level());
                        helper.assertTrue(map.variationAt(x, z) <= 1e-6 + k.roughness() * 40,
                                k.name() + " hilly at " + x + "," + z);
                    }
                }
                // the fade back to the land is gentle: it adds at most a block to any step across it
                int z = (e.minZ() + e.maxZ()) / 2;
                for (int x = e.minX() - 26; x <= e.maxX() + 26; x++) {
                    double step = Math.abs(map.heightAt(x + 1, z) - map.heightAt(x, z));
                    double plain = Math.abs(map.plainHeightAt(x + 1, z) - map.plainHeightAt(x, z));
                    helper.assertTrue(step <= plain + 1.0, k.name() + " fade steps " + step + " (land " + plain
                            + ") at " + x + "," + z);
                }
            }
        }
        LotrPort.LOG.info("[landscape] enclosures in {} cells round the Shire: {}, mean area {}", 4 * CELLS * CELLS,
                counts, area.entrySet().stream().map(en -> en.getKey() + " " + en.getValue() / counts.get(en.getKey()))
                        .toList());
        for (Enclosures.Kind k : map.enclosureKinds(shire)) {
            helper.assertTrue(counts.getOrDefault(k.name(), 0) >= 3, "only " + counts.getOrDefault(k.name(), 0)
                    + " " + k.name() + " round the Shire's middle");
        }
        helper.succeed();
    }

    /** A corn field's real terrain (the generator's ground, noise and all) is level inside the fence. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void cornFieldsAreLevelGround(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        MiddleEarthTests.Terrain terrain = MiddleEarthTests.middleEarth(helper);
        int checked = 0;
        for (int cx = -CELLS; cx < CELLS && checked < 4; cx++) {
            for (int cz = -CELLS; cz < CELLS && checked < 4; cz++) {
                Enclosures.Enclosure e = map.enclosureInCellOf(cx * 128, cz * 128);
                if (e == null || e.kind().roughness() > 0) {
                    continue;
                }
                Set<Integer> grounds = new HashSet<>();
                for (int x = e.minX(); x <= e.maxX(); x += 4) {
                    for (int z = e.minZ(); z <= e.maxZ(); z += 4) {
                        grounds.add(terrain.ground(x, z));
                    }
                }
                LotrPort.LOG.info("[landscape] {} at {},{}: ground {}", e.kind().name(), e.minX(), e.minZ(), grounds);
                helper.assertTrue(grounds.size() == 1, e.kind().name() + " at " + e.minX() + "," + e.minZ()
                        + " isn't level: " + grounds);
                checked++;
            }
        }
        helper.assertTrue(checked > 0, "no flat enclosure round the Shire's middle");
        helper.succeed();
    }

    /** Eriador's patches are large: walking across it, the landscape changes every few hundred blocks, not more. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void eriadorChangesLandscapeOnlyNowAndThen(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int eriador = biome(map, "lotr_port:eriador");
        // walk south along every 40th pixel column of Eriador, a sample every 16 blocks
        Map<Integer, List<Integer>> meridians = new java.util.TreeMap<>();
        for (int[] c : map.pixelCentres(eriador, 100_000)) {
            if (Math.floorMod(c[0], 128 * 40) == 64) {
                meridians.computeIfAbsent(c[0], x -> new java.util.ArrayList<>()).add(c[1]);
            }
        }
        Set<String> seen = new HashSet<>();
        int changes = 0;
        int walked = 0;
        for (Map.Entry<Integer, List<Integer>> m : meridians.entrySet()) {
            int x = m.getKey();
            String last = null;
            for (int z = m.getValue().get(0) - 64; z <= m.getValue().get(m.getValue().size() - 1) + 64; z += 16) {
                if (map.biomeIndexAt(x, z) != eriador) {
                    last = null;
                    continue;
                }
                String v = map.variantAt(x, z);
                v = v == null ? "plain" : v;
                seen.add(v);
                if (last != null && !last.equals(v)) {
                    changes++;
                }
                last = v;
                walked += 16;
            }
        }
        double run = changes == 0 ? walked : (double) walked / changes;
        LotrPort.LOG.info("[landscape] Eriador: {} blocks walked, {} changes (one per {} blocks), kinds {}", walked,
                changes, Math.round(run), seen);
        helper.assertTrue(walked > 5000, "only " + walked + " blocks of Eriador walked");
        helper.assertTrue(seen.size() >= 5, "only " + seen + " in Eriador");
        helper.assertTrue(run >= 250, "Eriador changes landscape every " + Math.round(run) + " blocks");
        helper.succeed();
    }

    /** Each painted sub-biome lies only in its region and over the biomes it may paint; reserved regions exist. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void subBiomesOnlyWhereTheRegionsAllow(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int enchanted = biome(map, "lotr_port:mirkwood_enchanted");
        int nests = biome(map, "lotr_port:mirkwood_spider_nests");
        int river = biome(map, "lotr_port:enchanted_river");
        int north = biome(map, "lotr_port:mirkwood_north");
        int corrupted = biome(map, "lotr_port:mirkwood_corrupted");
        helper.assertTrue(enchanted >= 0 && nests >= 0 && river >= 0, "sub-biomes missing from the biome table");
        int[] count = new int[3];
        for (int pz = 0; pz < map.height(); pz++) {
            for (int px = 0; px < map.width(); px++) {
                int b = map.pixelIndex(px, pz);
                boolean inEnchanted = map.regions().contains("mirkwood_enchanted", px, pz);
                if (b == enchanted) {
                    count[0]++;
                    helper.assertTrue(inEnchanted, "Enchanted Mirkwood outside its region at pixel " + px + "," + pz);
                } else if (b == nests) {
                    count[1]++; // nests are seeded lairs round points, never painted pixels
                } else if (b == river) {
                    count[2]++;
                    helper.assertTrue(map.regions().contains("enchanted_river", px, pz),
                            "Enchanted River outside its course at pixel " + px + "," + pz);
                } else if (inEnchanted) {
                    helper.assertTrue(b != north && b != corrupted, "plain Mirkwood left inside the enchanted forest "
                            + "at pixel " + px + "," + pz);
                }
            }
        }
        LotrPort.LOG.info("[landscape] pixels: enchanted {}, spider nests {}, enchanted river {}; regions {}",
                count[0], count[1], count[2], map.regions().names());
        helper.assertTrue(count[0] > 1000 && count[2] > 10, "too few sub-biome pixels");
        helper.assertTrue(count[1] == 0, count[1] + " map pixels painted as spider nests");
        for (String reserved : List.of("moria", "goblin_town")) {
            helper.assertTrue(map.regions().pixels(reserved) > 100, "no region " + reserved);
        }
        // the Forest Gate (LOTR waypoint 1303,655) opens into the enchanted forest a few pixels in
        var gate = map.blockOfPixel(1310.5, 655.5);
        String at = map.biomes().get(map.biomeIndexAt(gate.getX(), gate.getZ())).id();
        helper.assertTrue(at.equals("lotr_port:mirkwood_enchanted") || at.equals("lotr_port:mirkwood_spider_nests"),
                "inside the Forest Gate is " + at);
        helper.succeed();
    }

    /** The lairs of a seed in the enchanted forest's cells. */
    private static List<Lairs.Lair> nests(MiddleEarthMap map, long seed) {
        Lairs.Kind kind = map.lairKinds().stream().filter(k -> k.name().equals("mirkwood_spider_nests")).findFirst()
                .orElseThrow();
        var west = map.blockOfPixel(1280, 590);
        var east = map.blockOfPixel(1450, 700);
        List<Lairs.Lair> out = new java.util.ArrayList<>();
        for (int cx = Math.floorDiv(west.getX(), kind.cell()); cx <= Math.floorDiv(east.getX(), kind.cell()); cx++) {
            for (int cz = Math.floorDiv(west.getZ(), kind.cell()); cz <= Math.floorDiv(east.getZ(), kind.cell());
                    cz++) {
                Lairs.Lair l = map.planLair(kind, cx, cz, seed);
                if (l != null) {
                    out.add(l);
                }
            }
        }
        return out;
    }

    /**
     * Spider nests (Aaron 2026-11-25): small cores (~200 blocks square) only in the enchanted forest, their biome only
     * on the infested ground round them, and somewhere else in every world.
     */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void spiderNestsAreSmallSeededAndInTheEnchantedForest(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int enchanted = biome(map, "lotr_port:mirkwood_enchanted");
        int nests = biome(map, "lotr_port:mirkwood_spider_nests");
        List<Lairs.Lair> here = nests(map, MiddleEarthMap.seed());
        helper.assertTrue(here.size() >= 20, "only " + here.size() + " spider nests in the enchanted forest");
        for (Lairs.Lair l : here) {
            double area = Math.PI * l.kind().core() * l.kind().core();
            helper.assertTrue(area >= 25000 && area <= 40000, "nest core of " + Math.round(area) + " blocks square");
            helper.assertTrue(map.landIndexAt(l.x(), l.z()) == enchanted, "nest at " + l.x() + "," + l.z()
                    + " outside the enchanted forest");
            helper.assertTrue(map.biomeIndexAt(l.x(), l.z()) == nests, "no nest biome at the nest at " + l.x() + ","
                    + l.z());
            helper.assertTrue(l.sacs().stream().allMatch(s -> l.inCore(s[0], s[1])), "an egg sac outside its core");
            // the infested ground: the nest biome inside the radius (ragged edge aside), the forest well past it
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                int nx = l.x() + (int) Math.round(Math.cos(ang) * l.kind().radius() * 0.7);
                int nz = l.z() + (int) Math.round(Math.sin(ang) * l.kind().radius() * 0.7);
                int fx = l.x() + (int) Math.round(Math.cos(ang) * l.kind().radius() * 1.25);
                int fz = l.z() + (int) Math.round(Math.sin(ang) * l.kind().radius() * 1.25);
                helper.assertTrue(map.landIndexAt(nx, nz) != enchanted || map.biomeIndexAt(nx, nz) == nests,
                        "no nest ground at " + nx + "," + nz);
                helper.assertTrue(map.biomeIndexAt(fx, fz) != nests || map.lairAt(fx, fz) != l,
                        "nest ground far out at " + fx + "," + fz);
            }
        }
        List<Lairs.Lair> other = nests(map, MiddleEarthMap.seed() + 1);
        Set<Long> spots = new HashSet<>();
        here.forEach(l -> spots.add((long) l.x() << 32 | (l.z() & 0xFFFFFFFFL)));
        long same = other.stream().filter(l -> spots.contains((long) l.x() << 32 | (l.z() & 0xFFFFFFFFL))).count();
        LotrPort.LOG.info("[landscape] spider nests: {} this world, {} with the next seed ({} in the same spot)",
                here.size(), other.size(), same);
        helper.assertTrue(same < here.size() / 4, same + " nests in the same spots in another world");
        helper.succeed();
    }

    /** The Shire's plots are seeded too: another world, other plots. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void enclosuresDifferBetweenWorlds(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        int same = 0;
        int both = 0;
        for (int cx = -CELLS; cx < CELLS; cx++) {
            for (int cz = -CELLS; cz < CELLS; cz++) {
                Enclosures.Enclosure a = map.planEnclosure(cx, cz, 1L);
                Enclosures.Enclosure b = map.planEnclosure(cx, cz, 2L);
                if (a != null && b != null) {
                    both++;
                    same += a.minX() == b.minX() && a.minZ() == b.minZ() && a.kind() == b.kind() ? 1 : 0;
                }
            }
        }
        LotrPort.LOG.info("[landscape] enclosures in both of two worlds' cells: {}, the same plot: {}", both, same);
        helper.assertTrue(same <= both / 4, same + " of " + both + " plots alike in two worlds");
        helper.succeed();
    }

    /** Breaking a nest's egg sacs weakens it, and a dead nest's ground spawns as the plain forest. */
    @GameTest(template = EMPTY)
    public static void destroyingANestStopsItsSpiders(GameTestHelper helper) {
        MiddleEarthMap map = MiddleEarthMap.get();
        Lairs.Lair lair = nests(map, MiddleEarthMap.seed()).get(0);
        var server = helper.getLevel().getServer();
        LairLife.Settings settings = LairLife.settings(lair.kind());
        helper.assertTrue(settings.deadAt() > 1 && settings.deadAt() <= lair.kind().sacs(),
                "dead_at " + settings.deadAt() + " for " + lair.kind().sacs() + " sacs");
        LairLife.State state = LairLife.State.get(server);
        state.heal(lair.id());
        helper.assertTrue(LairLife.strength(server, lair) == 1, "an untouched nest is weak");
        state.breakSac(lair.id());
        double weakened = LairLife.strength(server, lair);
        helper.assertTrue(weakened > 0 && weakened < 1, "one broken sac left strength " + weakened);
        for (int i = 1; i < settings.deadAt(); i++) {
            state.breakSac(lair.id());
        }
        helper.assertTrue(LairLife.strength(server, lair) == 0, "the nest lives on");
        var table = com.porcupinestudios.lotrport.npc.NpcSpawns.forBiome(LotrPort.id("mirkwood_spider_nests"))
                .orElseThrow();
        var fallback = LairLife.adjust(table, 0, settings, 0.5);
        helper.assertTrue(fallback != table && !fallback.hostilesInDay(), "a dead nest still spawns its spiders by day");
        helper.assertTrue(LairLife.adjust(table, 1, settings, 0.99) == table, "a whole nest doesn't spawn its own");
        state.heal(lair.id());
        helper.succeed();
    }

    /** An egg sac broken anywhere hatches (8 sacs: the chance none hatches is ~1 in 65000). */
    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void eggSacsHatch(GameTestHelper helper) {
        ThrownTests.inLoadedArea(helper, 12, 8, () -> {
            for (int i = 0; i < 8; i++) {
                net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(1 + i, 1, 4);
                helper.setBlock(p, com.porcupinestudios.lotrport.lair.EggSacBlock.BLOCK.get());
                helper.getLevel().destroyBlock(helper.absolutePos(p), true);
            }
            var box = new net.minecraft.world.phys.AABB(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))
                    .inflate(24);
            int hatched = helper.getLevel().getEntitiesOfClass(com.porcupinestudios.lotrport.npc.LotrNpc.class,
                    box).size();
            helper.assertTrue(hatched > 0, "no spider hatched from 8 broken egg sacs");
            helper.getLevel().getEntitiesOfClass(com.porcupinestudios.lotrport.npc.LotrNpc.class, box)
                    .forEach(e -> e.discard());
            helper.succeed();
        });
    }

    /** The region effects load; drowsiness and the enchanted sleep do what they say; black water sends to sleep. */
    @GameTest(template = EMPTY)
    public static void enchantedForestEffectsWork(GameTestHelper helper) {
        RegionEffects.Def forest = RegionEffects.defs().get(LotrPort.id("enchanted_forest"));
        RegionEffects.Def water = RegionEffects.defs().get(LotrPort.id("cursed_water"));
        helper.assertTrue(forest != null && water != null, "region effects not loaded: " + RegionEffects.defs()
                .keySet());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RegionEffects.Rule drowsy = forest.rules().get(0);
        RegionEffects.Rule sure = new RegionEffects.Rule(drowsy.when(), 1, 1f, 0, drowsy.unless(), drowsy.effects(),
                drowsy.message(), drowsy.wake());
        helper.assertTrue(RegionEffects.apply(player, "test#drowsy", sure), "the drowsy rule didn't fire");
        helper.assertTrue(player.hasEffect(LotrEffects.DROWSY), "not drowsy");
        RegionEffects.Rule sleep = water.rules().get(0);
        helper.assertTrue(!RegionEffects.apply(player, "test#sleep", sleep), "slept without being in the water");
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(LotrEffects.ENCHANTED_SLEEP, 200));
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 1e-6,
                "can still walk in the enchanted sleep: " + player.getAttributeValue(Attributes.MOVEMENT_SPEED));
        helper.assertTrue(!RegionEffects.apply(player, "test#drowsy2", sure), "drowsy while asleep");
        ItemStack bottle = RegionEffects.bottled(water.bottle().orElseThrow());
        PotionContents contents = bottle.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
        helper.assertTrue(contents.customEffects().stream().anyMatch(e -> e.getEffect().is(
                ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "enchanted_sleep"))),
                "black water doesn't send to sleep");
        helper.assertTrue(contents.customColor().equals(Optional.of(water.bottle().get().color())), "colour");
        helper.succeed();
    }
}
