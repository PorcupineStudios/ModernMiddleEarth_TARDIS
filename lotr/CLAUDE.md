# LOTR Port (Minecraft 1.21.1 NeoForge)

Future-port of The Lord of the Rings Mod (1.7.10, Update 36.15) to Minecraft 1.21.1, part of the TARDIS: Ring Bearer
modpack project. Mod id `lotr_port`, main class `LotrPort`, package
`com.porcupinestudios.lotrport`.

**This repo is public** (2026-11-11): the LOTR mod's creator gave Aaron permission to use LOTR's assets. Three things
must never be committed (all in `.gitignore`): the film music (private repo `PorcupineStudios/LOTR_music`,
`lotr/docs/music.md`), the LOTR mod's jar itself (private copy `libs/LOTRMod v36.15.jar` in PorcupineStudios/RingRealms;
copy it to `converter/libs/` to run the converter), and other mods' jars. Its class map:
https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu. Rebuild features in modern code; never paste decompiled code.

## Approach (agreed in the project thread)
- Split the old `LOTRMod` god class into one registry per area.
- Player/world state (old `LOTRPlayerData`, `LOTRLevelData`) as saved data and data attachments with Codecs.
- Factions and alignment first (most things depend on them, and RingRealms and the TARDIS mod read them).
- NPCs: two data-driven bases, people (humanoids with records, jobs, speech) and creatures (animals, monsters), not one
  class per race. NPCs stay light and get a saved person record only when they matter (hired, named, recruited...).
- World gen as data: biomes, structures (`.nbt` templates), loot tables. A converter that runs the old mod and exports
  structures, recipes and models as data is the planned big time-saver.
- RingRealms (colonies and realms, PorcupineStudios/RingRealms on 1.7.10) is part of this mod, in `colony` and `realm`
  packages: colonists build on the NPC base, realms register as factions. Its `docs/` folder is the feature list; its
  code is ours, so its logic can be carried over, but the LOTR-internals and reflection layer goes away.
- Middle-earth is a start dimension for TARDIS: Ring Bearer: offer it through the shared API's crash-site provider,
  and register `lotr_port:alignment`-style condition types there. Never depend on `tardis/`.

## Decisions (Aaron, 2026-11-03)
- Reuse LOTR's textures, sounds and map image (with the creator's permission, 2026-11-11).
- Middle-earth is a world type, as in the original; in a normal world it is a dimension players travel to.
- Many LOTR systems get redesigned around RingRealms, Create and the TARDIS rather than copied.
- Prefer writing scripts that do the repetitive work (export, generate, rename) over porting class by class by hand.

## World generation (phase 3)
- `world/map/MiddleEarthMap` reads the generated map; `MapBiomeSource` (`lotr_port:middle_earth`) gives one biome
  per map pixel with wobbled borders; density function `lotr_port:map_terrain` gives ground height or hilliness,
  and the noise settings JSON shapes terrain from them. Tune heights in `converter/world.py` (or the JSON), not Java.
- Terrain shape (2026-11-04, from LOTR's LOTRChunkProvider): density = map height - y (4x steeper below it, LOTR's
  offset) + hilliness x (2 x 1.7's blended 3D noise, `old_blended_noise` xz factor 400; hilliness capped at 8 for
  the 3D part so mountains don't float islands) + hilliness x a 2D hill noise. Hilliness fades to 1/4 within 32
  blocks of a road (LOTR's roadNear). Stronger 3D noise broke `terrainIsSmoothAcrossBiomeBorders` (overhangs at rivers).
- Heights measured from LOTR (2026-11-05): the exporter's shot runner (`converter/.../ShotRunner.java`,
  `-Dlotrexport.shots=<script>`: `goto`, `wait`, `shot`, `survey`) generates LOTR's real terrain every 48 chunks over
  the whole map (~20 min) into `converter/export/terrain_survey.json`: per biome mean/std ground, and each LOTRMountains
  peak's ground. `world.py` uses the mean as the biome height (minus `SURVEY_OFFSET`) and std as hilliness, except in
  biomes holding a peak (their mean includes the mountain); peaks are cones up to the measured peak over LOTR's range
  (`map.json` `mountains`, `MiddleEarthMap.heightAt`). Mount Doom's crater and LOTR's generateMountainTerrain aren't ported.
- Ground rules (2026-11-05, `world.py` surface rule + `converter/grounds.py`): LOTR's podzol patches (35% podzol, 15%
  coarse "barren" dirt; moss in wet forests), rock and gravel breaking through above y 90, sandstone under sand, each
  biome's own patches (the 31 generateBiomeTerrain overrides as a table: Brown Lands coarse dirt, Harad dirt/sand,
  Gorgoroth soils...), marsh pools at sea level in the swamp-variant biomes, quagmire + mud disks where LOTR had
  quagmires. LOTR's thresholds are scaled by `NOISE_SCALE` for 1.18 noise. Biome grass/foliage/sky/fog colours are LOTR's.
  Underground: deepslate under y 0 (ores get deepslate targets), granite/diorite/andesite/tuff blobs, pointed
  dripstone hung from cave roofs in place of LOTR's stalactites.
- Mountains (2026-11-05): LOTR's generateMountainTerrain per biome (`grounds.MOUNTAINS`: snow block tops above snow
  height - rockDepth, the biome's stone above stone height; foothills none; Mordor's stone all Mordor rock), rock
  breaking through keeps rising above y 150 (LOTR's threshold keeps falling), 3D noise up to `MAX_3D` 20 and crags
  (vanilla's jagged noise) for hilliness over `JAGGED_FROM` 12. Peaks flatten hills by 1 + LOTR's height boost; Mount
  Doom has a flat crater floor and lava to y 198 (`CraterLavaFeature`, `MiddleEarthMap.lavaAt`). Peak biomes with
  1000+ surveyed columns (Misty Mountains) keep the survey.
- Roughness measured against LOTR (2026-11-05): GameTest `terrainRoughness` and the exporter's `rough X Z R` step log
  mean, spread, mean 4-block step and share of 45-degree steps over the same squares. Tuned: `HILL_2D` 2.5 (Shire
  spread 4.0 vs LOTR 4.8), `HILL_BIAS` 0.4 (the 4x-steeper ground below lifted means), crags `JAGGED` 2 and ridges
  `RIDGES` 1.2 over hilliness 10. Mountains now match LOTR's slopes (step ~3.8, 30% steep vs 3.1-3.7, 28-38%) and
  means, but their spread over 384 blocks is ~23 vs LOTR's 32-40 (wider ridges only shifted whole areas).
- Reeds (2026-11-14): kind `reed` (`content/ReedBlock`): waterlogged stem from the bed of still water, LOTR's
  lower/mid/upper icons by `part`, live reeds grow to 3 above the water; worldgen `world/feature/ReedsFeature`
  (LOTRWorldGenReeds: still water at most 4 deep, never dry land). Lakes and Shire (2026-11-14): the 2D hill noise's
  valleys are a quarter deep (`VALLEYS`, fewer sea-level ponds). The Shire's hills are its own "downs"
  (`MiddleEarthMap.downs`, `DOWNS` in converter/world.py): round seedless hills that only rise,
  with LOTR's noise kept only as roughness.
- Biome variants (2026-11-14, LOTR's LOTRBiomeVariant; Aaron: "many biomes within the Shire"): `map/Variants`, set in
  `converter/world.py VARIANTS` -> map.json `variants`. Patches of a jittered 176-block grid (seeded by the world
  since 2026-11-25, so each world's patches lie differently) inside a biome (Shire:
  meadows, light forest, forest, hills, wooded hills, birch wood, aspen wood, farmland; 60% of cells) that
  scale its downs and hilliness (blended smoothly between patches) and get their own plants: placed features
  `<biome>_variant/*` gated by placement filter `lotr_port:in_variant` (looked up up to the biome's `feather`, 20 by
  default, away, so edges feather). `lotr_port:orchard_grid` is unused since orchards became enclosures.
  Not separate biomes, as in LOTR: NPCs, structures, roads,
  music and waypoint regions keyed to the biome are unchanged. `MiddleEarthMap.variantAt` names a column's patch.
- Enclosures (2026-11-25, Aaron: Shire orchards "flatter and a lot smaller ... enclosed in fences", and Farmer
  Maggot's fields with 2-block crops sized so farmland makes sense): `map/Enclosures`, set in `converter/world.py
  ENCLOSURES` -> map.json `enclosures`. A 128-block grid (a chunk is always inside one cell); each cell holds at most one
  plot (seeded by the world: Aaron 2026-11-25, "randomness on new worlds") of a kind rolled by `chance` (x `boost`
  in a variant patch), sized in the kind's ranges, kept only if the biome is
  the same over it and its fade, its patch is in `variants`, no road within `road_gap` and the map's ground varies by at
  most `max_relief`. `heightAt` flattens it to one level (fading over `fade` blocks), `variationAt` keeps `roughness` of
  the noise. Feature `lotr_port:enclosure` (`world/feature/EnclosureFeature`, configured `shire_enclosure/<kind>`): fence
  or (by chance) hedge, gates with dirt paths, orchard tree rows, corn 2 high on farmland. Trees stay out (placement
  filter `lotr_port:outside_enclosures` on `trees/<biome>`, and `in_variant`), LOTR structures too (`LotrStructure`).
  Shire: orchards 18-34 x 14-26 (apple/pear, plum; trees 7 apart: at 6 their branches met and chunk order decided
  which grew), corn fields 40-72 x 28-48 (a third hedged, the Marish's hedgerows); flat to 4 blocks past the fence
  (`PLATEAU`: terrain samples the map's height every 4 blocks, so a slope at the fence leaned the plot's edge), then
  20 blocks of fade; the old 176-block orchard patches are the `farmland` variant, where most plots gather. Sizes
  reasoned in world.py (real fields ~200 m, Middle-earth ~11x shorter
  than Tolkien's). GameTests `LandscapeTests`; world check builds the nearest plot of each kind.
- Large variants (2026-11-25, Aaron: Eriador is "primarily just travelled through ... large scale ... but there still
  being variation"): Eriador (1100 km2 as one biome on LOTR's map), Minhiriath and Enedwaith (the Lone-lands had them
  until the wild-land rules below replaced them) have variant
  patches of 640-768 blocks with `warp` (edges meander by noise, share of a cell) and `feather` (plants thin out over ~56
  blocks at an edge). Kinds: meadows, heath, copses, old woods, pinewood, tors (boulders, standing stones), old fields
  (Arnor's tumbled dry-stone walls, `lotr_port:old_walls`, and feral fruit trees), thickets, stump-lands and regrowth
  (Minhiriath's felled forests), wetland. Extras are `configured_feature/variant/*` (world.py `VARIANT_FEATURES`).
- Wild lands (2026-11-25, Aaron, with four reference photos of the Lone-lands: "gritty wilderness ... not overly
  foresty (patches of forests, seeded and following rules that make it feel life like) ... a rocky feel ... custom
  blocks that match the pallet ... Variation without it being jarring ... boulder formations, moss growth, dried up
  creek beds, ravines"): post-step `converter/wilds.py` (after regions.py) for `lone_lands` and `lone_lands_hills`
  (Weather Hills). Blocks (`content/WildBlocks`): weathered_stone, lichen_stone, scree (doesn't fall), moorland_soil,
  stony_earth, heath_moss (the last three in `minecraft:dirt`), tussock and bracken (dry, untinted). Textures are
  vanilla 1.21.1's (andesite, gravel, rooted dirt, coarse dirt, moss, short grass, fern) gradient-mapped onto 5-6 tone
  ramps from the references (cool shadows, warm lights, neighbouring values close); vanilla's moss and coarse dirt
  were too green and too orange beside the tawny grass (grass 0xC2AC66, foliage 0x6B7342). `wilds.py --preview
  <png>` tiles the set. Feature `lotr_port:ground_cover` (`world/feature/GroundCoverFeature`, first in the vegetal
  step, per column on the real ground): dry creek beds cut 1-2 deep (seeded noise zero lines in a sparse mask, never
  by water or on slopes), rock down steep faces and rubble a step less steep, gorge floors rubble, wet hollows moorland
  soil and moss, exposed ground stony earth / scree / flush rock by a patch noise plus eastward stoniness (block x
  17024 -> 48512, Tolkien's barer east towards the Hoarwell); then bracken and heather in patches, tussock elsewhere.
  `lotr_port:boulders` (`BoulderFeature`): clusters of 2-6 half-sunk boulders and tors, lichen tops, moss on the north
  side, more eastwards. Trees only through placement filter `lotr_port:grove` (`Grove`): dense clumps where a seeded
  patch noise (+ hollows 40 blocks round, - east) passes `above`, scrub in a band just under it, a few lone stunted
  trees; the old `trees/lone_lands`, variant patches, old walls and green grass patches are dropped from these biomes.
  Gorges are terrain (`map/Wilds`, map.json `wilds.gorges`, `MiddleEarthMap.gorgeAt`): winding seeded cuts, flat
  floor, steep walls, up to 16 deep, never under sea + 3, faded at roads, rivers and the biome border; terrain is
  sampled every 4 blocks, so narrow creeks are the feature's job. Numbers in wilds.py `SETTINGS`/`GORGES` -> the
  configured feature JSONs. GameTests `WildLandTests`.
  Eriador (Aaron 2026-11-25: "slightly tune the 7 and then add milder versions on top"): its seven variant kinds keep
  their plants and get a light ground-cover pass each (`ground_cover` and `boulders` take `variants`; wilds.py
  `ERIADOR`): stony earth spots in meadows, stony patches and bracken on the heath, mossy hollows in the woods, rock,
  scree and boulders round the tors, a few dry creeks; four milder Lone-lands kinds join map.json's list (`ERIADOR_NEW`,
  a third of the weight): stony_heath, moorland (wet, creeks, pine clumps), rocky_dells (boulders, groves in hollows),
  brushland (scrub patches, lone trees). No gorges, and Eriador keeps its green grass, so it turns tawnier past Bree.
- Seeding (2026-11-25, Aaron: "ensure that this stays seeded so there's randomness on new worlds"): variant patches
  (and their edges), enclosures, lairs and old walls all mix `MiddleEarthMap.seed()` (the world seed, set as the server
  starts; 0 on a client, which never knows it) into their hashes; the planners' per-thread caches reset when it changes.
  The Shire's downs and the region layer's areas stay fixed: they follow LOTR's map, like the biomes.
- Region layer (2026-11-25, Aaron: an "allowed biomes" map over the geographical one, "backend only", for the enchanted
  forest and later Moria and Goblin-town): post-step `converter/regions.py` draws named regions in map pixels into
  `map/regions.json` (`map/Regions`: polygons, rough circles, cut to another region); map.json `overlays` paint a
  sub-biome over chosen biomes inside a region as the map loads (`MiddleEarthMap.paint`), so everything reading the map
  sees it. Sub-biomes are their base biome copied fresh each run plus their own looks, plants and spawns, and join every
  tag and table the base is in. `MiddleEarthMap.inRegion`/`regionsAt`, placement filter `lotr_port:in_region`. Moria
  and Goblin-town are drawn (reserved) but nothing generates there yet: an underground phase would answer
  `MapBiomeSource.getNoiseBiome` by y inside those regions.
- Lairs (2026-11-25, Aaron: nests "relatively small only about a 200 sq/blocks of actual spider nests, and with
  spiders spawning within a larger radius around it (with spawning being affected by destroying nests)"): `map/Lairs`,
  set in `converter/regions.py LAIRS` -> map.json `lairs`. Seeded points on a per-kind grid (spider nests: a 512-block
  cell in three of the enchanted forest's, ~100 a world), each a core (radius 100, Aaron: "200 by 200"; feature
  `lotr_port:spider_nest`: webs, 4 egg sacs, silk bundles) and infested ground (radius 160, ragged) that
  `biomeIndexAt` answers as the lair's biome (`landIndexAt` is the map without lairs). `lair/LairLife`: an egg sac
  (`lair/EggSacBlock`, `spider_egg_sac`) gone hatches spiders and counts against its lair (SavedData
  `lotr_port_lairs`); a spawn on its ground keeps its own table with chance = strength (1 - broken / `dead_at`), else
  the `fallback_biome`'s (data `lair/<kind>.json`, reloads). World check builds the nest nearest the Forest Gate.
- Enchanted Mirkwood (2026-11-25, The Hobbit films): northern Mirkwood from the Forest Gate along the Elf-path to the
  Enchanted River, between the Forest River and the Mountains of Mirkwood, is `mirkwood_enchanted` (dark fog, black
  water, drifting spores, light webs, spiders at night and Wood-elves); its spider nests are lairs (above), their
  ground `mirkwood_spider_nests` (webs, dead trees, spiders by day; `wilderness` so borders.py leaves it nobody's
  land); the map's painted river from the Mountains of Mirkwood to above the Forest River is `enchanted_river`.
  Effects are data
  (`region_effect/*.json`, `effect/RegionEffects`, effects `lotr_port:drowsy` and `enchanted_sleep` in
  `effect/LotrEffects`): drowsiness and now and then nausea in `#lotr_port:enchanted_forest`; in water of
  `#lotr_port:cursed_water` an enchanted sleep (no moving, blind, can't drown) and on waking a turn about; bottles filled
  there are Black Water. Atmosphere files `mirkwood_enchanted.json`, `mirkwood_spider_nests.json`. Not looked at in game
  yet.
- Rivers (2026-11-14): `RIVERS` scale 1700 (about half as dense as LOTR-like 900), width 0.01324 (noise units: same
  width in blocks), banks 5 widths. `Rivers.carve`: only the channel (half a width) is cut to the bed; the banks come
  down to a block over sea level at a width, then up to the land (cutting the whole valley towards the bed flooded low
  ground 60-80 blocks wide). A mask that faded part of the network out squeezed banks into cliffs and was dropped
  (`mask_scale` still read, 0 = off).
- Shire downs (2026-11-14, Aaron: steep round hills that stay grassy): 30 blocks (moors 20, woodlands 18) on a
  112-block grid, steepest ~0.7 blocks per block (0.9 in "hills" patches), so steps are grass edges; no rock breaking
  through in biomes with downs (`ground_style`).
- Mountain ranges (2026-11-15, Aaron: overhangs and floating rock; then "gigantic mountain ranges with basically no
  passes ... change depending on seed ... huge variety", no needle spires): `map/Ranges`, set in `converter/world.py
  RANGES` -> map.json `ranges` (range biome -> height scale; Misty 1). The range biomes lose their noise hilliness
  (`variationAt` x (1 - range share): no 3D noise, so no overhangs) and get a seeded height field: depth in from the
  range's edge on the map (exact distance transform per 64-pixel tile), valley floors climbing with depth (the wall:
  no low passes; roads cross over the top), ridges in seeded 2600-block regions of four kinds (sawtooth aretes, spurs
  down the depth's slope, glacial U-valleys, giant summits), cliff bands on steep ground, summits eased under y 610.
  `MiddleEarthMap.setSeed` (ServerAboutToStartEvent) salts all its noise. Prototype: numpy + a voxel-space perspective
  renderer; `Ranges` reproduces it exactly. Misty Mountains: valleys ~200-300, ridges 400-600, lowest crossing of any
  6 km stretch y 340-490.
- Build limit (2026-11-15): Middle-earth goes up to y 640 (`world.py TOP_Y`; its own dimension type, also for the
  world preset's overworld). Worlds made before keep vanilla's 320, and their mountains get cut flat at the top.
- Cheap terrain (2026-11-15, Aaron: LOTR's fast travel needs fast chunk loading): the hills' noise only within a
  band of the ground (`range_choice` on (level - y) / (hilliness + 2), `world.py NOISE_BAND_*`, map_terrain value
  `band`); mixin `NoiseGeneratorMixin` cuts Middle-earth's noise height to the band's top per chunk and per column
  (`map/NoiseTop`, never under the sea) for filling, the noise chunk and ground scans; `MapBiomeSource` caches the
  last 16 quart columns per thread (biomes don't vary with height; a chunk asks all 44 sections). GameTests
  `NoiseBandTests`: the band covers the sampled noise for every hilliness (needs 8.4 above, 4 below), and 3000
  columns with the cuts match the full formula exactly. Benchmark (one runner, 512x512 squares, seed 12345):
  Shire 44 s before -> 31 s, the same as the world at y 320; Misty Mountains 84 s (y 320 flattens them: 57 s).
  The world check's leaf failure on seed 7866868388345729068 (Gondor woodlands, 64928 74 59930) predates the cuts.
- Mountain ores, caves, Heart of the Mountain (2026-11-17, Aaron: mountains had lost ores and caves): LOTR's ores sit
  under y 48 and vanilla caves under 180, so the 640-high ranges were bare stone. Biome modifiers
  (`data/lotr_port/neoforge/biome_modifier/`, biome tag `lotr_port:middle_earth`) add `world/feature/MountainOresFeature`
  (the biome's own `ore/*` features re-run once per 112 blocks of ground over y 48, lifted into the rock keeping depth
  rank) and carvers `mountain_cave` (y 100-560) / `mountain_canyon`. `HeartOfTheMountainFeature`: one
  `heart_of_the_mountain` block (`content/HeartOfTheMountain`, hand-registered; its tags/lang are patched into the
  generated files by `generate.py write_tags` and `colonies.py UI_EN`) per mountain = per 640-block seeded cell whose
  highest sampled ground reaches 200, plus one per LOTR single peak (not volcanoes); jittered, 25-60% up from y 62.
  Numbers in the configured feature JSONs. Not checked in game (new chunks only).
- `Roads` registers each segment in every 256-block cell within `Roads.FLATTEN` (32): the terrain flattens hills by
  road distance, and a segment missing from a cell made it jump at the cell edge (straight 10+ block cliffs).
- Features placed in the decoration steps must read the final heightmaps (`WORLD_SURFACE`, `MOTION_BLOCKING`,
  `OCEAN_FLOOR`): from the carvers step on, 1.21 keeps only those up to date, and the `_WG` ones stay at the bare
  ground under every tree (2026-11-25: the spider nests' canopy webs found no crowns and 9 in 10 tries failed).
- Terrain preview (dev tool): `-Dlotr_port.terrainpreview=<x>,<z>,<size>` on a Middle-earth server writes
  `run/terrain_preview/` (relief.png, variants.png, overhangs.png, blocks.png: each column's top block in its map
  colour; enclosures.txt: each enclosure's top blocks and each lair's core as letters; stats.txt: water share,
  slopes, overhangs, columns
  with webs, what the ground is made of) and stops
  (`.save=true`: saves the world for a client, e.g. DevScript aerial shots; `-Dlotr_port.landmarks=true` logs
  `[landmark]` spots of the seed: plots, Eriador patches, a spider nest, the Enchanted River; DevScript `floor x z yaw
  pitch [up]` stands on the ground under any canopy, `noscreen` closes the race picker; first-person script
  `lotr/devscripts/biome_shots.txt`; don't set `chatVisibility` hidden in
  options.txt, it blocks the script's commands).
  Roads are 6-7 wide (`ROAD_HALF_WIDTH` 3.1, Aaron 2026-11-25; was 2.1, LOTR's were narrower); bridges
  `RoadFeature.BRIDGE_EXTRA` 1 wider each side so 6-7 blocks of deck lie between the fences.
- No plants on roads (2026-11-25, Aaron: no grass or flowers on roads, sparse overgrowth on run-down ones): feature
  `lotr_port:road_verge` (`world/feature/RoadVergeFeature`, first in the top-layer step) clears plants off road
  columns in the chunk and 8 blocks round it after all vegetation. Roads with LOTR repair 1 stay bare; worn roads keep
  or sow a few plants (chance (1 - repair) x a smooth per-24-block stretch factor x edge bias, more on gaps than on
  laid blocks; numbers and plant list in `world.py ROAD_VERGE` -> its configured feature JSON). Hash-based per column,
  so neighbouring chunks' passes agree. GameTest `RoadPlantTests`; the world check's `keptRoadsAreBare`.
- LOTR's ground plants (2026-11-25, Aaron: thistles "and ALL LOTR weeds and flowers" with LOTR's spawning rules;
  run on LOTR 36.15 in the sandbox with the jar from RingRealms): the exporter writes each biome's rules to
  `world.json` `plants` (sampled getRandomGrass, getRandomWorldGenForDoubleGrass/DoubleFlower, the weighted `flowers`
  list) and `-Dlotrexport.plants=true` runs `PlantCensus`: every plant on 15264 populated LOTR chunks (6 patches of
  4x4 chunks per biome, seed 1, ~8 min) counted per biome with its grounds -> `export/plants_census.json`.
  `world.py lotr_plants`: per kind (grass, double grass, flowers, double flowers) the census's plants per chunk shared
  out by the rules, plus census-only plants of each biome's own decorate (Mordor scrub, thorns and moss, Morgul
  shrooms, athelas, berry bushes, Fangorn plants, Rhun flowers...) at their measured rate and patch size; crops,
  fruit, vines, reeds, fallen leaves and the vanilla patches world.py already places are skipped (`CENSUS_SKIP_PREFIX`).
  Features `plant_<kind>/<plant>_c<n>|r<n>` (rarities on ~25% `RARITY_STEPS`) on shared `plant_patch/<plant>_<tries>`;
  `PLANT_YIELD` 0.5 (plants per try) is an estimate, not measured in the port. The vanilla grass/flower stand-ins stay
  only for an export without rules. LOTR's flowers are sparse because its decorator drops each flower patch at a
  random y 0-127 (`nextInt(128)`); the census measures that, so the port keeps it. Soils: `content/PlantBlock` also
  grows on its tag `lotr_port:grows_on/<plant>`, the grounds the census found (arid grass on sand, Mordor plants on
  Mordor dirt/gravel/rock...; `SOIL_MIN_SHARE`). GameTest `RoadPlantTests`. Running the 1.7.10 exporter in the cloud
  sandbox: JDK 8 + 25 from Adoptium, xvfb, keep the sandbox's `JAVA_TOOL_OPTIONS` (proxy truststore) and append to it,
  and a Gradle init script pointing Maven Central at Google's mirror (Central answers 429 there).
  The full `generate.py` no longer reproduces every committed generated file byte for byte (Pillow re-encodes PNGs,
  hand additions to lang/tags/npc_animals): commit only the parts a change is about.
- Vanilla flowers (2026-11-25, Aaron: "tone down the vanilla flowers but keep them in sparse patches", the Shire's
  flower forest keeps lots of flowers, LOTR's and vanilla's mixed): `converter/flowers.py` mixes (one random_patch,
  weighted plant per try). Foothills get `vanilla_flowers_sparse` (1 in 6 chunks, 12 tries) instead of vanilla's
  flower_meadow; the Shire's meadows variant (LOTR's FLOWERS, the flower forest) gets `shire_flower_forest` (5 a chunk,
  48 tries: marigold, bluebell, lavender, Shire heather, flax + vanilla's meadow and flower-forest flowers) and
  `shire_flower_forest_tall` (lilac, rose bush, peony). No biome uses vanilla's flower_default/meadow/flower_forest.
- Plant tuning (2026-11-25): dev tool `-Dlotr_port.plantcensus=<patches>` (`world/PortPlantCensus`, ~1 h for 3 patches
  a biome here) counts the port's plants as the exporter's census counts LOTR's; `converter/plant_compare.py` sets them
  side by side (`--calibrate` updates `converter/plant_calibration.json`). Run 1 (guessed yield 0.5): 0.34 of LOTR's
  plants; run 2 (per-plant rates and LOTR's patch sizes, yield 0.17): 0.90, median biome 0.92; then yield 0.153 and
  per-plant factors; run 3 (final flower mixes): 0.97, median biome 0.98, then recalibrated. Shire by variant (`.biomes=shire`, `.variants=true`, 40 patches, 5 min): meadows ~40 flowers a chunk (16 LOTR, 24 vanilla), the rest ~3 (LOTR's ~2.5). Mountain biomes stay ~0.1: the port's ranges are bare rock where LOTR's had grass (terrain).
  Why a literal port of LOTR's rules would grow ~8x LOTR's flowers: 1.7.10's WorldGenFlowers starts at the decorator's
  random y (`nextInt(128)`, tries only +-3) and never looks for the ground, while WorldGenTallGrass walks down to it;
  in LOTR's census a flower patch leaves 0.35 flowers, a grass patch 5.8. Modern patches start at the heightmap.
- Shaders (2026-11-25, Aaron: shaders must know to wave LOTR's leaves and plants): most packs (Complementary, BSL,
  Sildur's, Noble) wave only vanilla block names from their block.properties; Photon also reads tags (`%leaves`,
  `%small_flowers`, `%saplings`, `%crops`). `converter/shaders.py` gives each LOTR leaf, sapling, flower, grass, vine,
  bush, reed and crop a vanilla counterpart (`assets/lotr_port/shaders/block_aliases.json`; chandeliers excluded) and
  adds the single-block irises/hibiscus to `minecraft:small_flowers`; optional mixin config `lotr_port.iris.mixins.json`
  (`mixin/iris/IrisBlockIdMixin`, @Pseudo on Iris 1.8's `BlockMaterialMapping.createBlockStateIdMap`) fills ids the
  pack left empty from the counterpart (`client/ShaderAliases`, matching shared properties such as crop age).
  GameTest `everyLotrPlantHasAShaderAlias`. Not tried with Iris in game yet.
- Plants: LOTR's reed patches (`reedPerChunk`, dried with `dryReedChance`) and the Dead Marshes' dead plants
  (`world.py marsh_plants`). Marsh lights left out: the block draws as a white cube, not LOTR's particles.
- Big trees (Fangorn oaks reach 25+ blocks from the trunk) overflow what a feature may write: `tree/TreeOverflow`
  keeps those blocks per chunk and places them when that chunk is loaded.
- Tree placement follows LOTR's rules (2026-11-05, `TemplateTreeFeature.fits`): logs go into air, plants, leaves
  and soil; leaves only into air and plants; a tree whose logs would hit another tree's logs, or whose trunk would go
  through another crown, isn't grown (~5% of forest trees). Before this, later trees' leaves cut holes in earlier
  trunks and branches (300+ logs in the check's patches). Odd sapling squares grow round their middle (a trunk log
  on it). The world check's `TreeCheck` generates 7 forests (252 chunks) and fails on any missing tree block, two
  trees sharing a log, or decaying leaves. Run it locally like the CI "Middle-earth world" step (back up
  `lotr/run/server.properties` first: the GameTest server uses it too). Screenshot script: `lotr/run/trees.txt`.
- Forest floor (`ForestFloorFeature`, LOTRBiomeDecorator): fallen leaves (trees / 2 a chunk), leaf bushes (trees / 3),
  rotten logs (`logsPerChunk`); ferns and tall grass where LOTR had them. Foggy biomes (tag `lotr_port:foggy`, LOTR's
  biomeColors foggy) get a client fog (entry `atmosphere/foggy.json`). Region looks (2026-11-16, `client/Atmosphere`): fog distance, fog colour and a screen filter per biome from `assets/lotr_port/atmosphere/*.json` (biome ids, `#tags`, `prefix*`; highest `priority` wins; F3+T reloads); client config `atmosphere.*` toggles and scales them. Screenshot scripts: `lotr/run/mt.txt`,
  `converter/run/client/shots3.txt` (the shot runner gives night vision; `goto ... floor` = ground under trees).
- Colour grading (2026-11-16, `client/ColorGrade`, shader `shaders/core/grade.*`): each atmosphere file's `grade`
  (offsets: saturation, contrast, warmth, brightness, vignette, bloom) runs as a post pass after the world, eased with
  the region look; `atmosphere/middle_earth.json` (priority -100) is the base look for every lotr_port biome. Config
  `atmosphere.grading`. Dark-land effects (grain, colour fringes, heat shimmer, edge blur), the One Ring's
  wraith-world and drawing over Iris shader packs (2026-11-25): **read `lotr/docs/ring-and-looks.md`**.
- Sky (2026-11-16, `client/MiddleEarthSky`, dimension type effects `lotr_port:middle_earth`): LOTR's sun (half-size 10),
  8-phase moon, painted night sky (3x2 cube maps `textures/environment/middle_earth/night_<n>`, one per night) and
  Earendil near the sun at dusk and dawn; vanilla sky colour, sunrise glow and clouds. Config `sky.enabled`.
  Night-box face orientation is a guess from LOTR's renderer, not checked in game.
- World type "Middle-earth" (`lotr_port:middle_earth` preset): the overworld is generated from the map, so spawn and
  respawn are vanilla. Any other world gets the `lotr_port:middle_earth` dimension; `/lotr travel` goes there and
  back. `LotrDimensions.middleEarth(server)` returns whichever level is Middle-earth.
- Rivers and peaks (2026-11-16, Aaron: rivers too wide, green banks in Mordor, Erebor a round cone): small rivers
  narrowed (width 0.013, river biome only 0.7 widths out); `keep_biome` biomes (Mordor, deserts, wastes) keep their own
  ground instead of the river biome. Peaks use `map.json mountain_shape` (`MiddleEarthMap.MountainShape`: warped
  distance, concave flanks, ridged crests/gullies, hills flattened by only 0.25 x boost; volcanoes untouched).
  JAGGED 3, RIDGES 2 for steeper, less rounded mountains. Not yet checked in game.
- Rivers: `MiddleEarthMap.Rivers` (noise, cut into ground under `max_height`); roads: `map/Roads` +
  `world/feature/RoadFeature`. Road blocks are LOTR's per biome (2026-11-04, `converter/roads.py`, a table read from the
  decompiled LOTRRoadType and biome getRoadBlock/getBridgeBlock; 22 road types, weights = LOTR's random branches,
  repair = share of blocks laid on old roads), written into `roads.json` (`types`, `bridges`, `biomes`). Slabs where a
  road column is a block below the next (LOTR's exact slab rule not copied). Bridges are LOTR's LOTRRoadGenerator rules:
  the deck rises half a block per block of water depth under it (cap 5 above the water, slabs on half steps), so it
  arches over the deep middle; edge columns (a non-road block among 8 neighbours) are beam + fence with a beam below
  and a hanging fence brace; every 8th diagonal edge column is a pillar from the bed to 4 above the deck. Planks/slab/
  beam/fence per biome (oak, Mirkwood dark oak, Mordor charred). Trees and ores per biome are generated data (`converter/trees.py`,
  `world.py`); tree shapes are vanilla stand-ins for LOTR's hand-made ones.
- Trees (2026-11-04): the exporter grows each of LOTR's 111 tree types with its own generator, up to six shapes each
  (`TreeExporter`, `trees.json.gz`, run in CI). `converter/trees.py` saves shapes as `structure/tree/<type>/<n>.nbt`
  (leaves get their real distance to a log so they don't decay) and the type's feature as `lotr_port:template_tree`
  (`world/feature/TemplateTreeFeature`: random shape and turn, trunk corner + `trunk_width` kept on the origin square,
  soil a sapling could take, sand for desert trees). Types without exported shapes keep vanilla-shaped stand-ins.
- Saplings: kind `sapling` (`content/LotrSaplingBlock`: random ticks in light, 2 stages, bone meal). What grows is
  `data/<ns>/sapling/<block>.json` (`SaplingRules`), read by `converter/saplings.py` from the constants in LOTR's
  sapling classes' growTree: rules tried in order, `square` N (2x2, 3x3 party trees, 4x4/5x5 redwood and
  dragonblood, the mallorn's 9x9 on elven grass), `cross` (mallorn boughs), `chance` (1 in N large trees), else a lone
  sapling. The arrangement's saplings become the trunk. GameTest `SaplingTests`.
- Whole trees for felling (`tree/`, hooks for RingRealms woodcutters and orchards, 2026-11-04): every tree a
  `template_tree` grows (worldgen and saplings) is recorded per chunk (`TreeRecords`, SavedData `lotr_port_trees`:
  feature id, trunk corner + width, box, planted), and sapling-grown stand-ins too. `Trees.find(level, pos)` gives the
  tree a log or leaf belongs to (its logs from the trunk, diagonals for branches, inside its box, never into another
  recorded trunk, and its leaves); unrecorded trees fall back to a search from the block. `Trees.harvest(level, pos,
  harvester, leaves)` fells it all, returns the drops instead of dropping them and the sapling to replant
  (`Trees.saplingFor`); `Trees.near` lists recorded trees by trunk. Events `Trees.Grown` (saplings, main thread) and
  `Trees.Harvested`. Colonists aren't wired to it yet. GameTest `SaplingTests` grows a 3x3 and fells it whole.
- The crash-site provider answers the overworld in a Middle-earth world (the wreck lands at the Shire spawn).
- CI "Middle-earth world" step makes a new Middle-earth world on a dedicated server (`WorldCheck`): spawn, crash
  site, one chunk in every biome on the map, road blocks on a road.

## Delves: Moria (2026-11-25, `world/delve/`)
- Aaron: procedural caves, tunnels, great halls, mines, forges, stairs and bridges for Moria (later Goblin Town);
  Moria at full map scale from the West-gate (Eregion) to Dimrill Dale, a grand Third Age ruin, coded set pieces plus
  .nbt rooms, a hidden route through a maze. Feature `lotr_port:delve`; Moria's whole spec is
  `worldgen/configured_feature/moria.json`. A plan per world seed (`DelvePlanner`: grid of 48-block cells, cover rule
  under the mountain, set pieces, hidden route, Kruskal maze, prune, loops), built per chunk from a cell index.
  Planner and builders are plain Java (`Ground` = `MiddleEarthMap.heightAt`). `/lotr_delve info|goto|preview`.
  GameTests `DelveTests`; world check `WorldCheck.moriaIsBuilt`. **Read `lotr/docs/delves.md`** (tuning, template
  room contract with jigsaw doorways, numbers). Inhabitants by depth: `delve_spawns/moria.json` (`DelveSpawns`;
  orcs above, trolls below, the Balrog very rare under y -30, one at a time). Not checked in game yet.
- Goblin Town (2026-11-26, layout `goblin`: `GoblinPlanner`, `GoblinPieces`, `RiftShape`; blocks and the trap in
  `content/goblin/`, drawn by `converter/goblin_blocks.py`): THE town (`goblin_town.json`), generic copies
  (`goblin_towns.json`) and decoy porches (`goblin_porches.json`) scattered per region over the northern mountains.
  The Front Porch's floor swings open (`GoblinTraps`, block displays; armed = open drop under it, no block state, so
  decoys are indistinguishable; redstone springs it). Spawns by zone (Great Goblin at the throne, Gollum by his lake).
  GameTests `GoblinTests`; world check `goblinTownIsBuilt`; seen in the screenshot workflow's client
  (`devscripts/goblin_town.txt`, whose world step needs 10 GB like build.yml's), not played yet. Read the Goblin
  Town section of `lotr/docs/delves.md`.

## Goblins (2026-11-26, Aaron: a new faction with its own model, which players can be too)
- Faction `lotr_port:goblins` (`faction/goblins.json`, hand-written; lang keys in `converter/colonies.py UI_EN`):
  the Misty Mountains' goblins, friends of Gundabad, Angmar, Mordor and Dol Guldur, at war with the free peoples
  (Gundabad's list); control zones down the range from Goblin Town; pledge at Cutthroat (100). Counted in
  `FactionTests.HAND_MADE`.
- Body `goblin` (`client/ShapedModel` shape `GOBLIN`, an `NpcModels` kind): the humanoid boxes and 64x64 skin layout
  drawn at goblin proportions (head x1.2, narrow hunched torso, long thin arms x1.15, short legs x0.75; part scales
  and posture offsets added after vanilla's animation, so walk, swing, crouch and ride stay vanilla's; held items keep
  their shape), plus bat ears and a hooked nose in the rows under y 32 (layout in `NpcModels`). Vanilla's `setupAnim`
  never resets `head.z`/`body.z`: set them, don't add to them.
- NPCs `goblin`, `goblin_archer`, `great_goblin` (hand-written, `NpcTests.HAND_MADE`): skins painted by
  `converter/goblin_skins.py` (`textures/entity/goblin/`: 8 warriors, 4 hooded archers, the crowned Great Goblin;
  colours matched to Goblin Town's blocks), orc name bank and speech. Goblin Town's spawns and the Front Porch's
  ambush are goblins. `converter/body_preview.py` draws a skin on the goblin body (or the humanoid) outside the game.
- Player race `goblin` (`player_race/goblin.json`: scale 0.8, a little faster, 2 less health, home Goblin Town,
  `own_skin`): the player's own skin is converted to the goblin body (`RaceSkinRemap` goblin rules: head, body, limbs
  as they are, flattened second layers, the vanilla hat kept, ears and nose painted in the face's median-brightness
  colour with a darker ear hollow). `/lotr_race set <player> <race>` (op 2) sets a race without the picker.
- GameTests `GoblinFactionTests`; JUnit `RaceSkinRemapTest`; screenshot script `lotr/devscripts/goblins.txt`
  (`view front|back` turns the camera). Not yet: goblin traders, captains or quests; surface spawns (goblins live in
  the goblin towns); a goblin colony race.

## Factions and realms (phase 4)
- Great powers are data: `data/<ns>/faction/*.json` (`FactionDef`, reloads), generated from LOTR by
  `converter/factions.py`; names and ranks are lang keys `faction.<ns>.<id>[.rank.<rank>]`.
- `Factions` merges them with realms (`Realms` SavedData, ids `lotr_port:realm/<n>`); a realm without its own
  relations uses its culture's. Relations: `Diplomacy` SavedData overrides the data defaults; vassals live there too.
  Every change goes into the history log.
- Kills: `FactionKills` reads the `lotr_port:faction` entity attachment; the faction and its allies lose its
  `kill_value`, its enemies gain it. Pledging (`Pledges`) needs the pledge rank and makes the player's realm a vassal.
- Conditions `lotr_port:alignment`, `lotr_port:pledged`, `lotr_port:relation`. Commands under `/lotr faction`,
  `/lotr pledge`, `/lotr realm found`. Faction data isn't synced to clients yet (no screens need it).

## NPCs (phase 6)
- One entity, `lotr_port:npc` (`npc/LotrNpc`), plays every NPC type from data: `data/<ns>/npc/<type>.json`
  (`NpcType`: faction, skins + `skin_height` 32/64, scale, health, speed, attack, gear, name bank, pools,
  `trade_alignment`). Pools: `npc_trades/` (`buy` = what the trader sells, `sell` = what it buys; coins), `npc_units/`
  (captains), names `npc_names/`. All generated by `converter/npcs.py`; everything reloads with /reload.
- Clients get only what they draw (synced type id, skin, 64x64 flag); size comes from the `generic.scale` attribute.
  Renderer `client/NpcRenderer` (classic humanoid mesh, two layer sizes); client code only in `client/`.
- Trading is LOTR's own screens (2026-11-17, `npc/Traders`): right-click shows Talk / Trade / Exchange Coins
  (`client/TraderInteractScreen`); `TraderMenu` (`client/TradeScreen`, LOTR's trade.png: Buy row = click to pay and take,
  Sell row prices, Sell offers + coin button) and `CoinExchangeMenu` (`client/CoinExchangeScreen`). Deals from
  `NpcTrading` (max 9 per row, uses in memory only, not saved). Hiring:
  right-click a captain (sneak if it also trades) opens its hire screen (view `lotr_port:hire`, `Hiring.HireView`; no
  chat links anywhere, Aaron 2026-11-17);
  `Hiring.hire` checks alignment, pledge and coins. Hired units: sneak + right-click switches follow/guard; they defend
  the owner. `People` (SavedData) keeps a record only for hired units (history: `npc_hired`, `npc_died`).
- Speech: `speech/<bank>/<mood>.json` (`{"lines": [...]}`, `#` = player name, `@` = place, avoided), from LOTR's speech
  banks; the type's `speech` names the bank. Right-click (not trade/hire) says a line above the head (synced `SPEECH`,
  drawn by `NpcRenderer`): `hired` for the owner, `hostile` below 0 alignment, else `friendly`, then `neutral`.
  Model + speech share the `NpcType.Persona` codec (a codec group holds at most 16 fields).
- Creature NPCs (2026-11-06): a type whose `model` is an id (`lotr_port:warg`) is drawn on one of LOTR's creature
  models, `assets/<ns>/npc_model/<id>.json`, which `converter/entity_models.py` builds by replaying the LOTRModel*
  constructor. It's drawn with `client/CreatureModel` and `CreatureRenderer` at `render_scale`, animated by LOTR's
  own setRotationAngles: `converter/model_anim.py` runs it symbolically into expressions (branches merged as
  if-then-else, shared subtrees in `defs`), which `client/CreatureAnim` compiles and runs each frame. Entity state
  LOTR read (tail droop, troll kind, hurt time) is answered in `CreatureAnim.entity`; behaviour state not ported
  yet (rock throwing, sniffing, Gollum sitting...) reads 0. Its hitbox is `width` x `height` (synced; `LotrNpc.getDefaultDimensions`). Huorns are trees of
  real blocks with a face (`client/HuornRenderer`). Skins LOTR picked in code (warg colours per kind, ents,
  spiders, char/) come from `npcs.py CODE_SKINS` and `warg_type`.
- Not yet: female NPCs, LOTR's trader outfits. `/lotr npc spawn|types|people` for testing.
- Big creatures on procedural rigs (2026-11-16: IK legs on the real ground, body tilt, hitbox parts; spiders first):
  **read `lotr/docs/rig.md`** before touching `rig/`, `RiggedNpc` or `data/<ns>/rig/`.

## Player races
- Races are `data/<ns>/player_race/<id>.json` (`player/Races.Def`: scale, attributes, `model` = an `NpcModels` body,
  `skin`, `own_skin`...), picked on first entering Middle-earth; admins tune them (`RaceTunes`). `client/RacePlayers`
  draws race players on the LOTR body. With `own_skin` (default true; every race since 2026-11-26) the player's own
  skin is converted to that body by `client/RaceSkins` (read back from the GPU, cached per skin and body) using
  `client/RaceSkinRemap` (pure Java, unit tested): trouser/jacket/sleeve layers flattened, hair from the hat (hangs
  down the sides and back), hobbit feet from the leg bottom, elf ears from the head side, goblin ears and nose in the
  face's colour (see Goblins). Per-body rules:
  `assets/<ns>/race_skin/<kind>.json` (`hang`, `ear_y`, `ears`, `orcish`). The race `skin` is the fallback (skin not
  loaded, not 64x64), so it must be in the body's 64x64 layout.
- Orcs and Uruks (2026-11-26, Aaron: a skin converter that still makes a player look orcish): bodies `orc` (a stoop,
  arms a little long, legs a little short) and `uruk` (broad, thick-limbed, upright) are `client/ShapedModel` shapes
  with pointed ears swept back (the elves' box at (0, 0)). Their `race_skin` `orcish` block: every pixel coloured like
  the face (chromaticity within `tolerance`, half to 1.7 times as bright) takes the `skin` tone [h, s, v] at its own
  relative brightness, so shading, mouth and features stay; clothes and hair get `grime`; eye whites (nearly
  colourless and brighter than the face) glow in `eyes` with the iris beside them turned to a dark pupil; lower
  `tusks` at the mouth corners; `scars`; Uruks carry the White Hand (`hand`). Orcs: LOTR's grey-green, red eyes.
  Uruks: dark brown, amber eyes, the hand. Pixels are ABGR (NativeImage's order). Fallback skins: LOTR's orc and Uruk
  skins padded to 64x64 by `converter/orc_player_skins.py` (`textures/entity/orc/player/`). `converter/body_preview.py`
  draws any of these bodies; JUnit `RaceSkinRemapTest`, GameTest `RaceTests.ownSkinIsSyncedPerRace`.
  Armour: feet hide under boots and ears under helmets (`NpcModels.fitArmor`, NPCs too). Client config
  `race.ownSkins` turns the conversion off.

## Animals and mounts
- Animals are creature NPCs (above); `converter/world.py GRAZERS` adds vanilla spawners on grass biomes. NPCs with
  `spawnsInDarkness` spawn only in the dark unless the biome has `hostiles_in_day` (`npc_spawns`, `NpcSpawner`).
  LOTR mutton and the three vanilla horse armours are vanilla items (`VANILLA_ITEMS`).
- LOTR's animal NPCs (deer, aurochs, bears, kine araw...: `npc_animals/<biome>`, from LOTR's creature lists by
  `extra.py ANIMAL_SPAWNING`) spawn as 1.7.10's creatures did (2026-11-18, Aaron: aurochs were rare and vanished):
  with the land (`generation_chance` 0.1 per new chunk, mixin `AnimalGenerationMixin` after vanilla's creatures,
  `NpcSpawner.populate`), then every 400 ticks up to `cap` 10 within `radius` 128 counting only animals
  (`count_only_animals`: villages don't keep them away), and they don't despawn (`LotrNpc.removeWhenFarAway`,
  `NpcSpawns.isAnimal`). LOTR's horse family (boars...) is on the biomes' vanilla spawn lists. GameTest
  `lotrAnimalsSpawnAndStayLikeAnimals`.
- Animal density (2026-11-19, Aaron: "a LOT of animals"): 1.7.10 had one 10% roll per new chunk over one creature
  list (vanilla's and LOTR's animals together). Post-step `converter/animal_balance.py` splits that roll by weight
  between vanilla's pass (biome `creature_spawn_probability`) and LOTR's (`generation_chance`); before, each rolled
  10%. The ongoing animal cap counts vanilla animals (and LOTR's horses) too, not only LOTR's animal NPCs.
- Mounts (2026-11-10, `mount/`, `converter/mounts.py`): LOTR's horse family is one entity class, `LotrHorse` (a
  vanilla `AbstractChestedHorse`, so taming, saddle, inventory screen, jumping and breeding are vanilla's), with one
  entity type per breed: `horse`, `shire_pony`, `zebra`, `wild_boar`, `camel`, `elk`, `rhino`, `giraffe`. Looks and
  sizes are `content/mounts.json` (startup: model, textures, markings, saddle, carpet, scale, `seat`); numbers are
  `data/<ns>/mount/<breed>.json` and reload: `food` (items or `#tags`), `armour` (which mount armour fits), `attack`,
  `fights_back` (speed; boars, elk, rhinos), `spawn` (ops on the vanilla roll: `mul`, `mul_rand`, `min`, `max`),
  `boosts` (biome tag: Rohan and Dor-en-Ernil horses), `clamp_*` (foals: mean of parents and a fresh roll, clamped),
  `chest`, `milk`, `sounds`. `/summon` skips the rolls (vanilla does too).
- `MountArmorItem` (kind `mount_armor`: mount, protection, texture) goes in the body slot; vanilla horse armour fits
  breeds with `armour: horse` (horses, ponies, zebras, as in LOTR), wool carpets fit camels. Warg armour items exist
  but wargs don't wear them yet. Giraffe and camel armour (iron, golden, diamond) is the port's own (Aaron
  2026-11-10; LOTR's Mount enum had both but no items): `mounts.py NEW_ARMOUR_*` paints plates onto the model's
  texture layout (`paint_armour`), stamps vanilla's icon, and writes a recipe (vanilla horse armour + 2 leather).
- Drawing (`client/MountModel`, `MountRenderer`): horses, ponies and zebras use Minecraft 1.7.10's horse model,
  because LOTR's zebra and horse armour textures are painted for its 128x128 layout. The converter reads it from
  Mojang's 1.7.10 client jar (downloaded to `converter/cache/`, gitignored) into `npc_model/old_horse.json`; its
  animation is written in `MountModel` from 1.7.10's numbers. The others use LOTR's models through `CreatureModel`
  (`CreatureAnim.Ctx.state` answers what they ask of a horse), with `<id>_saddle` (inflated) for saddle, armour and
  carpet passes. Foals of the 1.7.10 models are the adult at half size.
- NPC riders: `npcs.py` reads LOTR's `spawnRidingHorse` chance and `createMountToRide` (mount and its armour) into
  gear `mount` / `mount_armor` and `ride_chance`; `LotrNpc.maybeRide` (wild and invasion spawns) and hired units go
  through `Hiring.mount`. An NPC's mount is tame, saddled and `NpcOwned` (despawns like its rider; players can't use
  it while the NPC rides). The NPC entity has a riding offset so riders sit in the saddle. The rider's feet are at
  half the mount's height (LOTR), which is `seat`.
- Rhosgobel rabbits (2026-11-24, Aaron: Radagast's sleigh rabbits, from a film still): the port's own breed
  `rhosgobel_rabbit` (`converter/mounts.py OWN_BREEDS`), pony-sized shaggy hares, 1.2 x 1.3, carrots. Own model, hop
  and three coats from `converter/rhosgobel_rabbit.py` (writes `src/main/resources/.../npc_model/rhosgobel_rabbit.json`
  and `textures/entity/rhosgobel_rabbit/`): crouched long body and big rump, thick neck, block-long ears swept back,
  long hind feet; the hop is a bound driven by limb swing (body lifts and pitches, hind legs push together, forelegs
  reach together, ears flatten in flight; idle ear twitch and nose wiggle). They live round Rhosgobel (waypoint
  68288, 4160, in southern Mirkwood at its western eaves): biome modifier `rhosgobel_rabbits.json` puts them on
  `mirkwood_corrupted` and `anduin_vale` (weight 8, 2-4), and the breed's `spawn_near` (`Mounts.nearHome`, in the
  spawn placement rule) keeps them within 2048 blocks of the waypoint. GameTest `rhosgobelRabbitsLiveRoundRhosgobel`.
  Saddled, they carry a rider (2026-11-25): the same script writes `rhosgobel_rabbit_saddle.json` (the same parts, so
  it moves with them, plus pommel, cantle and stirrups) and `textures/entity/rhosgobel_rabbit/saddle.png`; seat 1.4.
  With the harness mod they also pull, saddle and harness worn together. The generated entries (mounts.json, `mount/rhosgobel_rabbit.json`, en_us
  name) were written from `OWN_BREEDS` without a full converter run (the LOTR jar isn't in the sandbox).
- For the harness mod (optional, no dependency either way): `LotrNpc` is an `OwnableEntity` (its hirer), and
  `LotrPort` sends IMC `draught_key` naming NPCs by NPC type, so harness data can list `lotr_port:mordor_warg` etc.
  `client/PartPoses` records where each creature model's parts were drawn (the first `renderToBuffer` per entity
  after `RenderLivingEvent.Pre`; old horses too) and `LotrClient` offers it as IMC `part_pose`, so the harness sits on
  the neck and body and follows the animation. `NpcRenderer` posts `RenderLivingEvent.Post` for rigged NPCs too (their rig skips `LivingEntityRenderer.render`),
  so other mods' overlays draw on them.
- GameTests `MountTests`. Earlier batches leave blocks outside the 1-block `empty` template: clear the space first.
  Screenshot script: `lotr/run/mount_shots.txt`.

## Structures and spawning (phase 7)
- 82 LOTR structures (`converter/structures.py`): `structure/<id>/<0-2>.nbt` (3 seeded layouts), placed by our own
  structure type `lotr_port:lotr` (`world/structure/LotrStructure` + `LotrStructurePiece`, 2026-11-04 rework):
  - Rolls are LOTR's, bit for bit (2026-11-04, read from `LOTRBiomeDecorator`): LOTR seeds one `java.util.Random`
    per chunk from the chunk's block corner alone (no world seed: `x*1879267 ^ z*67209689L`, squared and mixed), takes
    the biome at the chunk's +16, +16, and rolls that biome's list in order, 1 in each entry's chance. One structure +
    set per (structure, chance): `worldgen/structure/<id>[_<chance>]`, biome tag `has_structure/<id>[_<chance>]`, set
    `random_spread` spacing 1 (every chunk asks). The structure's `rolls` holds per biome the list's chances up to
    and including its own, so it replays the rolls before it (`LotrStructure.lotrRoll`); an entry LOTR lists twice in
    a biome (Udun's camps and warg pits) rolls twice. It overrides `findValidGenerationPoint` because LOTR picks by the
    chunk's biome, not the building's. LOTR's chunk rules too: nothing with a block corner within `axis_margin` (32)
    of the x or z axis, nothing in a chunk with a road within `chunk_road_distance` (16) of its +8, +8 spot.
  - Rates match LOTR by measuring LOTR (2026-11-04): LOTR's generators turned many rolls down with their own ground
    tests (117 classes, ~20 shapes) on LOTR's terrain. The exporter's rates mode (`StructureRateExporter`,
    `-Dlotrexport.rates=true`, or the "Convert LOTR" workflow's `rates` input) opens a throwaway world, puts a probe in
    place of every biome decorator's random structure with chance 1, and populates 4x4-chunk patches (9 per biome)
    of LOTR's real Middle-earth: at every chunk the decorator offers every structure at its own spot, the probe builds
    it with Forge's block snapshots captured, counts whether it stood and puts every block back (and removes its
    NPCs), so each sample is on untouched land. `converter/export/structure_rates.json` holds per biome the chunks
    LOTR rolls in (off the axes, no road) and per structure the probes and how many stood; orchard and vineyard
    chunks count but get no probe (their variant turns structures off). `structures.py` writes stood per probe times
    the share of the biome's chunks that probe at all as `lotr_success` per biome (under 20 probes: the structure's
    share per probe over all biomes), and the structure keeps LOTR's roll with that chance (a hash of seed, chunk and
    salt). Run 2026-11-04 on this PC: all 1148 patches in 9 minutes, 13599 chunks, 51052 probes, 65% stood; a first
    run (before two fixes) agreed on stood per probe within sampling noise (mean difference 0.004). The fixes: the
    world's scheduled block ticks are dropped each tick (1.7.10 keeps those of unloaded chunks; they slowed the first
    run 3x), and the decorator's chunk is put back after each probe (LOTR's decorator isn't re-entrant: a neighbour
    populated inside a probe took the rest of the list). Only biomes LOTR doesn't paint on its map (beaches, glacier,
    Near Harad riverbank, Tauredain clearing) take the all-biome share. Locally: `cd converter`, JDK 25 as JAVA_HOME,
    `JAVA_TOOL_OPTIONS="-Dlotrexport.run=true -Dlotrexport.out=../../export -Dlotrexport.rates=true" ./gradlew
    runClient` (no spaces in the out path: Java 8 can't quote them); opens a game window and quits by itself.
  - Then, so this port's terrain doesn't turn the roll down a second time, the structure tries up to 8 spots in the
    chunk (`TRIES`) for ground that fits: no water at the surface, at most `max_slope` (6) blocks between highest and
    lowest ground under the footprint, no road within `road_distance` (16); none fits, nothing is built. LOTR's spot
    (+8..+23 into the chunk), layout and turn come from a random of the structure's own (`salt`): sharing vanilla's
    per-chunk random put two structures of one chunk on the same spot.
  - No overlaps (Aaron 2026-11-04: buildings colliding): LOTR's generators refused anything but natural ground (its
    `isSurfaceStatic`: the biome's top/filler, grass, dirt, gravel, paths, mud, sand), so a later one never stood in an
    earlier one. Here every plan is a pure function of seed and chunk, so each structure replans the LOTR structures
    of nearby chunks and gives way to one that comes first (a fixed hash order of chunks, then LOTR's list order) and
    whose own roll, share and ground pass, within `gap` (2) blocks. A structure's spot is its first that fits and never
    moves for another, which keeps this exact. GameTest `StructureTests` plans 1024 Shire chunks, fails on any
    overlap and logs per structure how many stood against LOTR's expected count (`LotrStructure.expected`: 1 in
    chance times `lotr_success`, summed over the chunks); the port falls short only where no spot fits or another
    structure is in the way (2026-11-04: 27 placed against LOTR's 29.7).
  - Air: templates hold real air only where LOTR cleared space (dug cellars from the export; above ground, a
    column's open space under its highest block, i.e. rooms and under roofs). Everywhere else is structure void, so
    natural terrain stays. `terrain_adaptation` is `none` (beard_box carved the air ditches round every building);
    the piece fills under the ground layer down to solid ground instead (dirt under dirt/grass, else the same block,
    up to `max_foundation` 96, dirt for 4 blocks then stone). Ground scans don't see caves, so a cave mouth under
    the footprint can be deep; 24 left a floating building in Eriador (2026-11-19).
  - Doors: the lower half takes the upper half's hinge (1.7.10 kept it only on top; split doors). Gates
    (`content/GateBlock`, LOTR's multi-block gates) rotate and mirror with the structure and open as one gate: the
    touching blocks of the same kind in the same plane.
  - Biome variants that disable structures (LOTR's vineyards and orchards) are folded into `lotr_success`, not placed.
- Template NPCs are `{id:lotr_port:npc, NpcType, Fresh:1b, PersistenceRequired:1b}`; `LotrNpc` rolls skin, gear and
  name on load when `Fresh` is set. LOTR's spawner blocks become up to 3 placed NPCs (no respawning yet).
- Natural spawning: `npc_spawns/<biome ns>/<biome path>.json` (entries npc/weight/min/max, cap, chance, interval),
  run by `npc/NpcSpawner` per player on `LevelTickEvent`. Conquest and invasion lists are not ported yet.
- CI world check (`world/StructureCheck`): nearest gondor and mordor structure from the faction's map home must hold
  NPCs of that faction.
- Templates are placed without block updates, so `structures.py` sets pane/fence/wall sides from the neighbours
  (lone pane posts showed as dark squares) and fills pots with a vanilla potted flower picked by position (1.7.10 kept
  the plant in a tile entity the export lacks). Foundations, not terrain adaptation, keep houses on slopes off the ground
  (beard_thin left them floating, beard_box dug ditches; Aaron 2026-11-04).
- Armor whose LOTR texture is per item (gambesons, jacket, hats) gets a material per texture (`LotrItems.key`); the
  hats are drawn on LOTR's own hat model, so `generate.py hat_layer` paints a cap onto the helmet layer instead
  (the Shirriff's missing hat texture was the purple head).
- Not yet: villages and town walls (LOTR's village generators), invasions, respawner blocks, non-person creatures.

## Map, waypoints and mini-quests (phase 8)
- Waypoints: `data/<ns>/waypoint/<id>.json` (x, z, map pixel, region, faction, hidden); 274 from LOTR. Entering a biome
  tagged `waypoint_region/<r>` unlocks region r (`travel/FastTravel`, player attachment `travel`). Travel needs the
  region, alignment >= 0 with the waypoint's faction, no mob attacking you, and the wait over
  (`fast_travel/settings.json`: 180-1800 s by distance). `/lotr_travel`, `/lotr_waypoints`; admin
  `/lotr waypoint unlock|ready|info`.
- Map screen (rebuilt 2026-11-04 to match LOTR's `LOTRGuiMap`): key M opens it (`client/MapScreen`). Fullscreen by
  default, widget for a framed window (30 px border); zoom powers -3..4 eased over 6 ticks; drag or WASD/arrows to
  pan; LOTR's 105 map labels (`assets/lotr_port/map/labels.json`, read by `converter/classfile.py` from the constants
  in LOTR's label class, lang `map_label.lotr_port.<id>` or the biome name) fading over their zoom range; widgets for
  waypoints, labels, zoom, fullscreen and sepia (icons from LOTR's map sheet). Hovering shows the biome and block
  x, z; an operator or creative player in Middle-earth presses Map Teleport (T, `MapNet.Teleport`, checked again on
  the server) to go there. Click a waypoint to select it, Fast Travel (F) travels; tooltips give distance and why
  travel is blocked. Drawn like LOTR 1.7.10's (2026-11-14, from the decompiled LOTRGuiMap): the full map image with
  nearest filtering at every zoom (`client/MapTextures`, plain and sepia), red-to-black frame, 1 px road dots every
  400 blocks / zoom, 4 px waypoint dots placed from their block x/z (the data's `map_x` is the pixel corner, half a
  pixel off the roads), names above them from zoom -1, LOTR's dark red-edged waypoint box. Zoom is eased by the clock
  (`System.nanoTime`) and panning lerps with `DeltaTracker.getGameTimeDeltaPartialTick`: in 1.21 a screen's render
  gets the frame delta, not the partial tick, which made both jitter. Waypoint reach shrinks from 5 px to 2 px zoomed
  out; a click selects the dot hovered in the last frame. The parchment overlay is one stretch over the window at 20% (as LOTR), the sea beyond the edge the map's ocean
  colour. Packets in `net/MapNet` (the client handler is set in client setup, so common code never names the screen).
- Mini-quests: `quest_pool/<group>.json` (collect / kill_faction / kill_npc, min-max, weight, reward factor; coins
  and alignment per target), named by an NPC type's `quests`. Right-click a giver (not traders/captains) to get LOTR's
  offer page (`client/QuestOfferScreen`, `QuestNet.Offer`; the roll waits server-side in `Quests.PENDING` until
  Accept/Decline). Right-click again: progress or thanks above its head. Using the Red Book (item kind `red_book`)
  opens `client/RedBookScreen` (list, details, abandon). Textures: LOTR's `gui/quest/*` copied by `quests.py`.
  Player attachment `quests` (`quest/QuestLog`); history `quest_completed`. `/lotr_quests [abandon <n>]`.
  LOTR's per-quest lines (`speech/miniquest/<group>/<quest>_start|progress|complete`) need the exporter to record each
  quest's speech key first.
- Offers (2026-11-04, LOTR's `LOTREntityQuestInfo`): a giver offers a quest only now and then. Each tick a giver not
  offering starts with chance 1 in the pool's `offer_chance` (20000, about once in 17 min; LOTR's renegades and
  shieldmaidens 4000) and the offer lasts `offer_ticks` (24000, a day). Traders, captains and hired units never offer.
  The synced `QUEST_OFFER` colour (faction colour, -1 = none) draws LOTR's quest mark (`textures/gui/quest/
  quest_offer.png`) above the head within 16 blocks (`NpcRenderer.renderQuestMark`). Right-clicking an unmarked giver
  just talks; taking the quest ends the offer (`Quests.accept`). `/lotr npc offer [radius]` starts offers for testing.
  Not yet: hiding the mark from players below the alignment (clients don't have faction data yet).
- Companions (2026-11-19, `travel/Companions`): fast travel and the map's teleport take the mount you ride (you stay
  on), animals on your lead, tamed pets not sitting and your hired units on follow (within 24 blocks).
- Not yet: bounty, retrieve and pickpocket quests, fellowships, titles, custom waypoints, LOTR's quest
  speech.

## Colonies (phase 9, RingRealms built in: `colony/`)
- Founding: a Colony Charter (`colony_charter`, tab "Colonies") used on a block makes the Hall there, for the
  founder's pledged faction; renamed on an anvil = colony name. One colony per player, except kingdom rulers (any
  number, Aaron 2026-11-18; commands act on the owned colony nearest the player); Halls `hall_gap` apart.
  Saved in `Colonies` (SavedData `lotr_port_colonies`, codecs); history `colony_founded`, `citizen_arrive`,
  `citizen_die`, `recruit_started`. Citizens carry the `lotr_port:colony` attachment and never despawn.
- Growth is immigration by approval, all in the GUI (Aaron 2026-11-12, `colony/Immigration`, colony view tab
  Immigration): a board of groups (alone, family or friends) redrawn every `refresh_ticks` (30 min); Welcome brings
  them to the Hall as citizens with names, traits, skills and ties (needs free beds, bed heads within `bed_radius`;
  `first_settlers` need none), Turn away leaves the slot empty. Slots, group size and quality come from `levels` in
  `colony_immigration/settings.json` (shared conditions: population, `lotr_port:colony_upgrade`). The old migration
  (migrants waiting at the Hall, chat accept/deny) is removed; old saves' waiting migrants are dropped. Who comes:
  `colony_migration/<faction>.json`
  (generated by `converter/colonies.py`: the faction's people minus traders, captains, leaders, rare races).
- Rare races (`recruit/<id>.json`, hand-written in `src/main/resources`): never migrate. The founder sneak +
  right-clicks an envoy (an NPC of that type) to start; gates are faction alignment, population and a per-colony
  limit. Stages of objectives: deliver (sneak + right-click the envoy again), kill_faction, kill_npc, visit (a
  waypoint), population. After the last stage the envoy walks to the Hall as a rare citizen. Shipped: Rangers of the
  North, a Wizard (Gandalf's type).
- TARDIS researchers (shared API `ResearcherProvider`, in `shared/SharedApiHooks`): a founder's colony counts every rare
  citizen plus one per `citizens_per_researcher` others; the TARDIS runs its research faster for each.
- `/lotr_colony` (info, citizens, open recruitments); admin `/lotr colony list|day [n]`.
- The rest (2026-11-11 rework): society core (Persons + components, `Society` facade), colony systems scheduler,
  hired units, Town Hall roles and land protection, map visibility, buildings (registered by hanging a Building
  Plaque; the writ is retired), homes, jobs,
  work, wages, needs, the Ledger, and colony wars: **read `lotr/docs/colonies.md`** before touching `society/`,
  `colony/`, `unit/`, `land/`, `social/`, `war/` or `ai/`.
- Not yet: envoy spawning (rare NPCs come from normal spawning or commands for now).
- Road works (2026-11-20, `road/`): roads that change after world generation, a tier ladder in data (`road_tier/`:
  trails and dirt paths worn in by traffic; gravel, cobble and paved roads only built by waymakers), laid on natural
  ground of loaded chunks, seen by routes. **Read `lotr/docs/roads.md`**; plan (caravans, Waymaker station, tunnels):
  `docs/plans/roads.md`.

## Borders and kingdoms (2026-11-18, `border/`)
- Who holds Middle-earth's land per map pixel, read top down: marks (conquest, operators), colonies' claims
  (settling: unclaimed or own people's land only, radius by population, terrain cost; `TerritoryMath`), the original map (`border_base`: biome -> faction, from
  `converter/borders.py`). Saved layers hold only changes from the layer below (`Borders`, history `border_changed`),
  so the original map is kept and changes forwarded onto it. World generation never reads borders.
- Kingdoms are realms: `/lotr_kingdom found` (own faction, alignment, colour; the ruler's colony is the capital), or
  pledge (player kingdoms are pledgeable, rank `subject`). Colonies are founded for the ruler's kingdom else the pledge;
  wars move land with the colonies they take. Map layer `client/BorderLayer` (key B), spawning hook
  `TerritorySpawns` + `SpawnEvent`, condition `lotr_port:territory`. Conquest (`Conquest`): pressure (kills of the
  holder's people, assaults; x colony research perks `conquest` near your colonies) against defence (holder x terrain
  from `conquest_terrain`, minus assault damage); assaults cost injuries and deaths (family, race and colony mood).
  Through time: layer deltas `border_layer` in the history log (`BorderHistory`); the map shows a time traveller the
  borders, kingdoms and colonies of their tick and a world rewind restores them (shared API time hooks).
  **Read `lotr/docs/borders.md`.**

## Diplomacy (2026-11-18, `diplomacy/`)
- Diplomacy Table block (`DiplomacyTable`) and `/lotr_kingdom diplomacy` open `lotr_port:diplomacy` (`DiplomacyViews`):
  realms, Incoming (answer offers here, never in chat), New offer (coins, land squares and colonies each way, trade,
  truce, alliance, vassalage, ending the war), Sent (waiting and answered), treaties, wars. `Treaties` (SavedData): offers wait for a kingdom's ruler, great powers and AI realms
  answer at once by worth (`diplomacy/settings.json`); acceptance carries out all terms or none; trade agreements let
  caravans trade; war between kingdoms is a realm `war/` war. **Read `lotr/docs/diplomacy.md`.**

## UI core (2026-11-11, `ui/`, `client/ui/`, `colony/ui/`)
- Management screens are views the server builds (tabs + a table or stats page + actions) and one client screen
  (`client/ui/UiScreen`) draws with the Kenney sprites; a new screen is a `ViewProvider` registered in `ui/Views`, no
  client code. Four payloads (`ui/UiNet`). Search, filters and sort run on the client (`ui/TableQuery`, JUnit
  `TableQueryTest`). Colony views (`colony/ui/ColonyViews`): one colony (Overview, Buildings, People, Stock, Members)
  and all your colonies; the Town Hall's Manage button, `/lotr_colony manage`, `/lotr_colony colonies`.
- **Read `lotr/docs/ui-core.md`** before adding or changing a management screen. Builds and its unit test passes in CI
  (2026-11-11); the screen has not been looked at in game yet.

## Colonists: traits, mood, kin (2026-11-11, `colonist/`)
- Person components `traits`, `mood`, `kin` on the society core; mood adds to `Life`'s happiness target, traits scale
  work and food, `Assign` houses households together (`HouseRules`), families mind sharing (`KinRules.homeThought`).
  Data `colonist_trait/`, `colonist_thought/`, `colonist_race/`, `colonist_ration/`, `colonist/settings.json`.
  Citizen page (`colony/ui/CitizenViews`, 2026-11-11): rename, portrait, mood parts, needs, orders (`orders` part:
  workplace, rest, home, ration, schedule; permission `citizens`). Sleep (energy by the hour), ONI schedules per
  colony with race rhythms, and a data-driven task queue (`colonist_task/`) that loaded bodies follow. **Read
  `lotr/docs/colonists.md`.** Marriage, births and family arrivals are not built yet. Green in CI (2026-11-11,
  unit tests and all GameTests); the daily passes are tuned for `SocietyTests` 50 x 150 tick budget (0 over, 1.8 ms
  worst): keep new per-citizen work out of collections and streams, and see the doc's Performance section.

## AI rulers (phase 10: `ai/`)
- Rulers from data (`ai_ruler/<id>.json`: name, faction, home waypoint, aggression, expansion, max_colonies, colony
  names); numbers in `ai/settings.json`. Each gets an AI realm (`Realms`). One `AiRulers.step` per colony day, rulers in
  id order, each with a random source from (world seed, day, ruler) so the same world replays the same.
  `wait_for_player` (default on, Aaron 2026-11-03): the AI starts the day after the first player colony exists, and
  keeps going after that (`AiRulers.waits`). `/lotr ai day` still runs days by hand.
- A day: AI colonies grow (counted population, no bodies: `Colony.ai`, `virtual_population`), strength grows, maybe found
  a colony near home (`Colonies.foundAi`, id from ruler/day/count), maybe declare war on a hostile ruler (faction
  relation is bad), march on the nearest enemy AI colony only when strength >= march_ratio x its defense, peace after
  min_war_days. A won siege is `Colonies.transfer` (history `colony_transferred`, also for handing colonies to players).
- History: `colony_founded` (with `ai`), `ai_relation`, `ai_siege`, `colony_transferred`; `AiReplay` rebuilds owners
  and wars from the log alone. Admin: `/lotr ai list|day [n]`. Their wars and sieges are also `war/` records on
  realm parties; raiding player colonies is behind `ai_raids_players` (off). Not yet: real soldiers on AI marches,
  trade and treaties beyond war/peace, refugees.

## Gates and connected textures (2026-11-04)
- LOTR drew many blocks with connected textures (gates, portcullises, dwarven bricks): a base plus side, corner and
  inside-corner pieces by neighbour. `converter/assets.py sheet` bakes each into `textures/block/connected/<tex>.png`:
  5 tiles x 2 rows (tile per quarter: 0 nothing connected, 1 beside only, 2 above/below only, 3 both but not the
  diagonal, 4 all; row 0 shut, row 1 open = no base, only the frame). Gates without LOTR pieces (iron/bronze
  portcullis, cross-braced wooden gate) get a frame from their own solid bars (thickest full-width line top and
  bottom, thickest full-height line down the sides); LOTR cut their outer 3/16, which for bars is a row of dots.
  Blocks use model loader `lotr_port:connected` (`client/ConnectedModel`, shapes `cube`,
  `gate`, `full_gate`): it reads the neighbours in `getModelData` (NeoForge calls it per block in chunk builds) and
  draws each face a quarter at a time. The rule is LOTR's (`GateBlock.connects`): same block; a gate only in its
  plane, same axis and open state; an open upright gate also to the whole row under it (diagonals too, or the bottom
  joints get inner corners).
- Checked in game 2026-11-04 with `client/DevScript` (dev only): `JAVA_TOOL_OPTIONS=-Dlotr_port.devscript=<file in
  run/>` plus `./gradlew :lotr:runClient -PquickPlay=<save>` runs a script of commands, waits, right-clicks and
  screenshots (`run/screenshots/`), then quits; steps in its javadoc. The save needs `allowCommands` and
  `confirmedExperimentalSettings`, and `run/options.txt` `onboardAccessibility:false`, or a screen blocks quick play.
- Gates (`content/GateBlock`): use or redstone opens/shuts the whole gate (same block, same axis, in its plane, max 64);
  with a bow in hand the click shoots (LOTR); with a block it works the gate, sneak to place (Aaron 2026-11-16). Open gates have no collision and draw
  their frame (they used to render as air: "right-clicking deletes it"). Dwarven doors (`full`) are whole blocks while
  shut; they have no LOTR texture of their own to frame, so they keep a plain panel (`gates_without_frame`). LOTR's
  gate sounds aren't ported: vanilla wooden/iron door sounds.
- NPCs open gates (Aaron 2026-11-04, unlike LOTR, for colonists): a shut upright gate is `DOOR_WOOD_CLOSED` to the
  pathfinder, `LotrNpc` navigation opens doors, `npc/OpenGateGoal` opens a gate on its path and shuts it once through
  (not while someone stands in it); vanilla `OpenDoorGoal` for doors. GameTest `KegGateTests.gatesOpenAsOne`.

## Drinks and kegs (2026-11-04, merged onto the cloud drinks 2026-11-09, `drink/`)
- Drinks are the cloud's `LotrDrinkItem` (stats in `content/drinks.json`, components `LotrComponents.DRINK_STRENGTH`
  and `VESSEL`, a vessel id like `goblet_gold`, none = mug). Brewing recipes are `MachineRecipe` BREWING
  (`recipe/brewing/*.json`). `converter/drinks.py` adds `drinking/settings.json` (`DrinkData`: strength factors,
  serving 250 mB, keg 16 servings, 3000 mB water, `brew_ticks` 12000), `content/vessels.json`,
  `content/drink_fluids.json`, each drink's picture per vessel (`item/drink/<item>/<vessel>`, model predicate
  `lotr_port:vessel`) and the keg screen textures (`gui/keg*.png`).
- LOTR's barrel is the Keg (`keg`): `KegBlock`/`KegBlockEntity`/`KegMenu`, client `KegScreen`. 6 ingredients + 3
  water buckets (or piped water), Start Brewing, one strength per recipe `time` (else `brew_ticks`) to potent, Stop
  Brewing after the first step. Right-click with an empty vessel pours a serving. Structures' barrels load as full
  kegs, and their chests' drinks keep strength and vessel.
- Create: no dependency. The keg exposes `Capabilities.FluidHandler.BLOCK` (tank 0 the drink, tank 1 water) and
  `ItemHandler.BLOCK`; drinks and vessels expose `FluidHandler.ITEM` (one serving). Each drink is a fluid
  (`Drinks.DrinkFluid`, tinted water) carrying its strength. GameTests `KegGateTests`.
- Set-down vessels (2026-11-17, `drink/Vessels`, `VesselBlock`): sneak + use a vessel or drink on a block top sets it
  down. Use a full one: you drink it where it stands (`LotrDrinkItem.drink`), it stays, empty. Use an empty one with a
  drink in that kind of vessel: pours it in; otherwise nothing (it used to pick the block up, and structure vessels,
  holding nothing, vanished). Break it to take it. Models: `converter/vessels.py` (no two boxes overlap: overlapping
  floor and walls z-fought; a real glass bottle). Water drawn into a vessel stays in that vessel.
- Drink-in-vessel item models are `item/drink/<drink>/<vessel>` (2026-11-17): `<drink>_<vessel>` clashed with real
  items (the empty ale horn drew full ale; ale in a golden horn drew the set-down block).
- Poisoned drinks (2026-11-17, `drink/PoisonDrinkRecipe`, recipe `own/poison_drink`): any drink + the bottle of
  poison in a crafting grid = the drink with component `lotr_port:poisoned` (the crafter's UUID, set in
  `LotrDrinkItem.onCraftedBy`; only they see "Poisoned!"); the bottle comes back empty. Drinking it (hand or set-down
  vessel) gives `drinking/settings.json` `poison` (stand-in for LOTR's killing poison: Wither II 30 s + nausea).
- Jade (optional, `compat/jade`, compile only like JEI): a set-down drink shows as the drink with its own tooltip lines
  (strength, "Poisoned!" for the poisoner, or for everyone with COMMON config `drinks.poisonShownToAll`), or "Empty".
- Not yet: alcohol tolerance, drunken speech, NPCs drinking (LOTR's poisoned drinks also killed NPCs given them).

## Modifiers (2026-11-17, `modifier/`)
- LOTR's modifiers are data enchantments (extra.py); `Modifiers` hooks them in: crafted gear rolls random ones
  (`mixin/CraftedModifierMixin` on `Item.onCraftedBy`, so shift-click too), traders' goods roll skilful ones and cost
  more by their `value` (`NpcTrading`), Smith's Scrolls (`SmithScrollItem`, component `lotr_port:modifier`; NPCs drop
  blank ones that get a random modifier on landing) apply on a vanilla anvil, two alike make the `upgrade`
  ("combineSmithScrolls"), undamaged gear + its repair material reforges (keeps `persists_reforge` and vanilla
  enchantments; "reforge"), looting/silk touch count as vanilla's (`acts_as`, `GetEnchantmentLevelEvent`),
  headhunting drops heads, tooltips green/red (`#lotr_port:modifier/negative`). Ancient items (`AncientItem`, data
  `ancient_item/<item>.json`) turn into an old weapon or armour piece with skilful modifiers when used.
- Data: `modifier/<enchantment>.json` (generated, `extra.py write_modifier_data`), `modifier_settings/settings.json`
  (roll chances, anvil XP costs, heads). The roll chances and the ancient lists are stand-ins, not LOTR's numbers.
  GameTests `ModifierVesselTests`.

## Modern Middle-earth (2026-11-17, Aaron: "modernize" 1.7.10 -> 1.21.1; list in lotr/docs/modernize.md)
- Converter post-steps that run on the generated content (each also alone, `python <step>.py`; generate.py runs them
  in this order after everything else; each is idempotent; `waterlog.py` after `tags.py`, `noisecaves.py` before
  `caves.py`): `woodsets.py` (bark blocks `<wood>_bark` "<Wood> Bark" with bark
  on all six sides = vanilla's oak wood, named so because LOTR's log is already "<Wood> Wood"; stripped logs, beams
  and bark, signs, hanging
  signs, buttons, pressure plates, boats and chest boats for all 39 LOTR woods; textures are vanilla 1.21.1's oak
  ones recoloured to the LOTR planks' colours, the client jar is downloaded once to converter/cache/), `tags.py`,
  `stonecutting.py`, `waters.py`, `mountains.py`, `land.py`, `caves.py`, `regions.py` (before `biomeconfig.py` and
  `borders.py`, which then see its sub-biomes).
- Wood sets (`content/woodset/`): one `WoodType` + `BlockSetType` per wood (`lotr_port:<wood>`, made when
  `WoodSets` loads, so vanilla builds their sign models and sheets), own sign/hanging-sign block entity types
  (`LotrSignBlocks`, the hanging one reports our type from vanilla's hanging-sign class so its edit screen opens),
  stripping by `BlockToolModificationEvent`, boats `LotrBoat`/`LotrChestBoat` (synced `Wood`, drawn on vanilla's oak
  model with the wood's picture: `client/WoodSetRenderers`). GameTests `ModernTests`.
- Tags (`tags.py`): each block and item gets a vanilla analogue (rules on port kind, LOTR class, LOTR material and id)
  and copies its functional vanilla tags (ALLOWED_* lists; never identity tags) from vanilla's own tag files, plus
  c: tags (metals, gems, foods, drinks, tools...). "<vanilla>|<tool>" = the analogue's tags with its mining tool
  swapped. Blocks no tool helps (glass, beds, cakes) go in `lotr_port:no_mining_tool`; GameTest
  `everyBlockHasAMiningTool` fails on any other block without a mining tag, hand-registered ones included (their
  tags: `tags.py HAND`). What it added is remembered in `content/tag_sources.json`; report `converter/tags_report.json`.
- Stonecutter (`stonecutting.py`, `recipe/own/stonecutting/`): from LOTR's own single-ingredient block recipes
  (4 rock -> 4 bricks...): a full block cuts into every block below it and their slabs (2), stairs and walls; no
  mossy or cracked forms. These skip faction tables (LOTR's building recipes need one). LOTR's chisel is something
  else: it carves text into a stone block (`signCarved`, glowing ithildin with the moon-chisel); not ported yet.
- No vanilla monsters in Middle-earth (`world/NoMonsters`: natural, chunk, structure, patrol, reinforcement spawns of
  non-LOTR MONSTER mobs are cancelled; exceptions: entity tag `lotr_port:allowed_in_middle_earth`).
- Seas (`waters.py`): the map's ocean becomes five sea biomes by latitude (map.json `seas`, `MiddleEarthMap.seaAt`,
  `MapBiomeSource`): frozen_ocean (z < -45000), cold_ocean, ocean (to 60000), lukewarm_ocean (to 110000),
  warm_ocean; each takes fish, dolphins, seagrass, kelp, coral, icebergs and water colours from vanilla's matching
  ocean. Rivers and lakes: salmon, squid, river seagrass; marshes: lily pads; warm beaches: turtles; everywhere: bats
  and glow squid. Seas are listed wherever `lotr_port:ocean` is.
- Mountains (`mountains.py`): LOTR's mountain snow line gets vanilla frozen-peaks patches (powder snow, packed ice,
  ice; `snow_top`, also used by world.py); goats on the ranges, foxes/rabbits/polar bears in the north, ice spikes
  in Forodwaith, meadow flowers on foothills, amethyst geodes everywhere; biome tags for snow foxes, white and gold
  rabbits, is_mountain.
- Land (`land.py`): wild bee nests on LOTR trees (feature `lotr_port:bee_nest`), frogs and lily pads in the wetlands,
  foxes, rabbits, parrots, ocelots, armadillos, bamboo, melons, mushrooms, pumpkins. `MODERN_ORDER` +
  `normalize_order`: every added feature in one global order after LOTR's own (Minecraft rejects order cycles).
- Waterlogging (`waterlog.py`, `content/Waterlog`): blocks.json `waterlog` on LOTR blocks whose vanilla kin holds water
  (chests, gates and portcullises, chandeliers, bird cages, orc chains, stalactites, vessels, plates; not the
  full dwarven doors). `LotrBlocks` builds them inside `Waterlog.building`, so `ShapedBlock`, `FacingBlock` and
  `GateBlock` add `waterlogged` only to them (SimpleWaterloggedBlock, guarded for blocks without it). Vanilla-class
  blocks (slabs, stairs, walls, fences, panes, trapdoors, ladders, leaves, signs) already waterlog.
- Noise caves and aquifers (`noisecaves.py`, 2026-11-18): the terrain density becomes the named, once-cached
  function `lotr_port:middle_earth/terrain`, and the final density wraps it in vanilla's overworld cave formula
  (cheese caves with pillars, spaghetti, entrances, noodles; vanilla's `minecraft:overworld/caves/*`) below
  `CAVE_TOP` 200 and only where the terrain is rock; aquifers on with vanilla's noises (off, the game flooded every
  air pocket under sea level). Ground scans (`iterateNoiseColumn`: structures, spawning, getBaseHeight) skip both:
  `lotr_port:caves_on` (`world/map/CavesOn`) reads 0 during a scan, and the scan's noise chunk is made without
  aquifers (mixin `NoiseGeneratorMixin`). Cost (CI, 2026-11-18): a ground scan 0.56 ms against 0.25 ms before, the
  Shire structure plan 5.0 s against 3.7 s (19 s with caves and aquifers in scans); full chunk generation pays
  vanilla-like cave and aquifer costs underground and hasn't been benchmarked. GameTest `noiseCavesUnderTheShire`.
- Caves (`caves.py`): vanilla's lush and dripstone decorations only inside scattered pockets (placement filter
  `lotr_port:cave_pocket`, `world/feature/CavePocket`: per 112 x 64 x 112 cell, chance 0.25 per kind, radius 14-30,
  10+ blocks under the ground): lush under green wet lands (axolotls there), dripstone under dry, rocky and mountain
  lands, both where both; glow lichen everywhere. Not looked at in game yet (new chunks only).
- Biome cave config (`biomeconfig.py`, `world/BiomeCaveConfig`, 2026-11-18, Aaron: "tone down the cave entrances and
  ESPECIALLY the ravines", same caves below, "a biome config that has every biome split up into its country's
  regions"): `config/lotr_port-biomes.toml` (COMMON, Mods > Config), one section per biome
  `[land.region.biome]` (regions = LOTR's waypoint regions, lands = `LANDS` in biomeconfig.py), built at mod load
  from `data/lotr_port/biome_config.json` (the biome list, settings with their ranges, defaults). Settings:
  `caveEntrances` 0-1 (holds back vanilla's entrance pits and tunnel mouths in the top ~9 blocks of rock, through
  density function `lotr_port:cave_setting`; 0 shuts them), `ravines` and `carverCaves` 0-1 (share kept of the
  canyon / cave carvers that start in the biome), `surfaceCover` 0-32 (carvers stop that far under the ground),
  `cheeseCaves` / `spaghettiCaves` / `noodleCaves` switches, `lushCaves` / `dripstoneCaves` 0-4 (pocket chance, only
  in biomes with those pockets). Carvers: biomes use `lotr_port:cave`, `cave_extra_underground`, `canyon` (vanilla's
  configs, types `lotr_port:cave` / `lotr_port:canyon`, `world/feature/BiomeCarvers`), the mountain caves and
  canyons too. Defaults: lowlands entrances 0.15, ravines 0.2, cover 8; hills 0.3 / 0.3 / 4; `#is_mountain` 0.5 /
  0.6 / 0; waters entrances 0. Changes apply to new chunks. GameTests `biomeConfigHasEveryBiome`,
  `caveSettingsFollowTheBiomeConfig`.

## Rings of power and Curios (2026-11-25, `ring/`)
- Curios is required (worn-item slots: ring now, cloak and backpack later, all as data). The One Ring
  (`one_ring`, `ring_power/one_ring.json`) worn in a ring slot hides its wearer without the potion's particles, and
  mobs lose them, except the Unseen. GameTest mock players need `gametest/MockPlayerChannels` for Curios' sync.
  **Read `lotr/docs/ring-and-looks.md`.**

## Combat and armor models (2026-11-04)
- 1.7.10 had no attack cooldown. LOTR's melee timing (`LOTRWeaponStats`: 15 ticks for a player's sword ÷ the
  class's speed factor, reach 3 × factor, extra knockback) is generated onto each melee weapon's `weapon` block in
  `content/items.json`. `content/LotrWeaponItem` turns that into attack speed (20 / ticks), entity interaction
  range, attack knockback, sweep only for plain swords, and LOTR's poison for poisoned daggers. NPCs swing at the
  weapon's `mob_ticks` (`LotrNpc.WeaponMeleeGoal`). Damage is LOTR's own, with fractions kept.
- Bows (`LotrBowItem`: draw time, arrow damage factor), crossbows (`LotrCrossbowItem`: vanilla crossbow, bolts only,
  `#lotr_port:crossbow_bolts`), and bolts and poisoned arrows (`ArrowItem` + a fixed Poison effect). Pull and charged
  predicates are in `LotrClient.rangedProperties`.
- LOTR's large in-hand icons: `separate_transforms` item models on `item/large_2x` / `large_3x`, with
  `"gui_light": "front"` (a model without a parent defaults to block lighting: spears and pikes drew dark).
- Modifiers don't glint (`mixin/ModifierGlintMixin` on `Item.isFoil`): only lotr_port enchantments = no glint.
- Special armor models: `converter/armor_models.py` → `lotr_port/content/armor_models.json` → `client/ArmorModels`
  (`RegisterClientExtensionsEvent`). Animated parts keep their resting pose.
- Per-item armor textures (2026-11-16): LOTR's `new LOTRItemArmor(mat, slot, "extra")` pieces (special helmets,
  winged/Harnedor/Gulf bodies, trimmed dwarven sets) draw `armor/<mat>_<extra>.png`; `generate.py armor_extras` reads
  them from LOTRMod's bytecode. With the material texture their models showed broken. Check: `lotr/run/armor_script.txt`.
- Every 1.7.10 → 1.21.1 mismatch and what was decided: `lotr/docs/missing-features.md`.

## Recipes and JEI (2026-11-06)
- Machine recipes are `crafting/MachineRecipe`: one recipe type and serializer per machine (`alloy_forge`,
  `millstone`, `brewing`, `ent_jar`, `unsmelting`). Inputs match in any order, plus optional `station`, `chance`
  and `time`. They load and show in JEI and the machine blocks run them (see Machines).
- JEI is optional (`compileOnly` API, `jei_version` in `lotr/gradle.properties`, optional dependency in
  mods.toml). `compat/jei/LotrJeiPlugin` adds one category for faction tables, which shows the table each recipe
  needs, and one category per machine, with the machine blocks as catalysts.

## Machines and display blocks (2026-11-10, `machine/`)
- Which block is which machine is `machine` in `blocks.json` (`converter/machines.py`, by LOTR class); `LotrBlocks`
  picks the block class from it. LOTR's numbers are `machine/settings.json` (`Machines.Settings`).
- `MachineBlock` + `MachineBlockEntity`: faction forges (4 columns of alloy + input; alloy recipes for this
  `station`, else single-ingredient ones, else a furnace result for `#lotr_port:forge_smeltable`), hobbit oven (9
  food slots), millstone (runs while redstone-powered, recipe `chance`), unsmeltery (yield from
  `content/unsmelting.json` × durability left). `lit` shows LOTR's active texture. One `client/MachineScreen` draws
  every `MachineMenu.Layout` (slot spots read from LOTR's containers). Hoppers through `SidedInvWrapper`.
- `DisplayBlock` (plate: food stack, eat with empty hand; weapon rack; armour stand: 4-slot screen), `KebabStandBlock`
  (fuel from the container below, LOTR's takeFuelFromBelow), `EntJarBlock` (biome tag `ent_jar_brewing`),
  `LotrChestBlock` (27 slots, 1.7.10 chest model in LOTR's texture). Renderers in `client/MachineRenderers` on
  LOTR's models (`npc_model/`, static); the armour stand's armour is drawn on an invisible vanilla armour stand.
  `WeaponRackBlock`: floor (faces the placer's look) or `wall` (faces the block it hangs on, breaks without it);
  the model takes LOTR's onWall pose. Racks and stands in structures: meta & 3 = north/east/south/west, & 4 = wall.
  The rack's weapon is drawn with `ItemDisplayContext.NONE` moved to 1.7.10's flat-icon frame (checked in game).
- Items that place another block (`places`): beds stay `BedItem`, the rest are `ItemNameBlockItem`.
- GameTests `MachineTests` (own batch: the 1-block `empty` template lets other tests land on long-running ones).

## Farming, ores and pouches (2026-11-10, `content/farm/`, `content/pouch/`)
- `converter/farming.py`: crops (`crop`, vanilla CropBlock, 8 ages on LOTR's 4 icons, age>>1), berry bushes (ripe and
  bare are two blocks that swap), corn (`corn`: stalks up to `corn_height`, cobs on non-bottom stalks, a guess),
  grapevines (`grapevine` on a post; seeds plant on a post with farmland within 3 blocks below; picking leaves the
  post). Numbers in `farming/settings.json` (`Farming.Settings`).
- `converter/ores.py`: 15 item-dropping ores (`ore` = DropExperienceBlock, LOTR's xp), loot with silk touch, fortune.
- Raw ores (2026-11-19, as vanilla's cave update): post-step `converter/raw_ores.py`. The metal ores that
  dropped themselves (copper, tin, silver, mithril, Morgul iron + its Mordor ore) drop `raw_<metal>` (silk touch: the
  ore; fortune: vanilla `ore_drops`; counts in the loot tables, `DROPS`); `raw_<metal>_block` packs 9. Every recipe
  that took the ore takes the raw ore too (smelting + a blasting twin, alloy forges, Create mixing). Create: tags
  `c:raw_materials/<metal>` / `c:storage_blocks/raw_<metal>` (tags.py) let Create's own recipes crush raw tin,
  silver and copper into its crushed ores (create_compat.py smelts and washes those into LOTR's ingots/nuggets;
  copper stays Create's, i.e. vanilla copper); raw mithril and Morgul iron are crushed like Create's raw iron into
  what their ore crushes into. Mithril and Morgul iron still smelt only in LOTR's forges. GameTests `RawOreTests`.
- LOTR's amethyst is **tanzanite** (2026-11-19: 1.21 has its own amethyst): post-step `converter/renames.py`
  (`RENAMES`, names per language in `LANG`) renames ids, files, lang, tags, templates after generation. Keep LOTR's
  old ids out of new code: always `tanzanite`, `tanzanite_vein`, `tanzanite_block`.
- LOTR's copper is vanilla's (2026-11-19, post-step `converter/vanilla_swaps.py`, `SWAPS`): copper ingot, ore
  (deepslate form in deepslate) and block are removed and every reference uses vanilla's; recipes that only repeated
  vanilla's are dropped. Copper goblets and treasure piles stay.
- `converter/create_extra.py` (last post-step): Create saw cutting for every LOTR wood (log/beam/bark -> stripped ->
  6 planks), smoker + campfire recipes for every LOTR furnace food (Create fan smoking), keg drinks as Create mixing
  into the drink fluid (6 ingredients + 1000 mB water -> 1000 mB; spouts fill vessels, pipes fill/drain kegs).
- Leaves (`converter/leaf_drops.py`, 2026-11-19): vanilla oak leaves' loot (shears/silk touch: the leaves; else
  sapling, sticks), and the 14 fruit trees (`FRUIT`) drop their fruit (`FRUIT_CHANCES`, 4x vanilla's apple rate).
- Saddles: also 3 leather + 2 iron (`src/main/resources/.../recipe/own/crafting/`); pouches: leather + string
  (small only, `LSL / L`; no silk item); bigger pouches only by combining (`content/pouch/PouchCombineRecipe`,
  `own/pouch_combine`: pouches whose slots add up to a bigger one, contents kept; 2 small = medium, small + medium
  or 3 small = large).
- `PouchItem` keeps items in `DataComponents.CONTAINER`; `PouchMenu`/`client/PouchScreen` on LOTR's layout with the
  name field (`Pouches.Rename` payload). Pick-ups go into pouches when the inventory is full.
- GameTests `FarmTests`. `GameTestHelper.destroyBlock` drops nothing: use `level.destroyBlock(pos, true)` to test loot.

## Thrown weapons and NPC archers (2026-11-10, `content/thrown/`)
- `converter/thrown.py` marks items by LOTR class (spear `throw`, `throwing_axe`, `blowgun`, `dart`, `sling`,
  `throwable` pebble/conker/fire_pot/mystery_web/plate) and orc bomb blocks (`bomb` strength, fire); LOTR's numbers
  go to `thrown/settings.json` (`Thrown.num`). Spears, axes and darts fly as `ThrownWeapon` (an arrow carrying its
  item, damage = speed x multiplier, comes back one point worn); the rest as `ThrownMissile`; `OrcBomb` is a TNT-like
  entity (fuse 40 + 20 x strength). Dispensers for all of them (`Thrown.dispensers`). Renderers in
  `client/ThrownRenderers`.
- NPC archers: `npcs.py` gives types with a ranged weapon behaviour `archer` and LOTR's AI numbers (`behaviours.py
  archer`, read from the constructor's LOTREntityAIRangedAttack). Pure archers hold the bow; swap types (elves...)
  keep it as `ranged` gear and draw it outside melee range. Pike reach: `LotrNpc.getAttackBoundingBox`.
- GameTests `ThrownTests` force-load the chunks they shoot across (the 1-block template keeps only its own loaded).

## Create compat (2026-11-06)
- `converter/create_compat.py` writes 134 cross-mod recipes under `recipe/create/` (conditional on
  `neoforge:mod_loaded` create) and `c:ores/*` / `c:nuggets/*` tags; every match and why is in
  `converter/create/crossref.json`. Re-extract Create's recipes when the pack's Create version changes. The recipe
  count GameTest skips `create/`, because the test server runs without Create.
- Faction mechanical crafters (2026-11-19, Aaron: "literally duplicate the autocrafters, one for each faction ...
  tinted that faction's color"): `compat/create` (registered only when Create is loaded; lotr compiles against Create,
  `create_version` in lotr/gradle.properties, dev/GameTest runs load it with `-PwithCreate`). One
  `<table>_mechanical_crafter` per faction table (27, `converter/create_crafters.py` `TABLES` table -> faction ->
  `content/create_crafters.json` with the tint = faction colour lightened `LIGHTEN`). `FactionCrafterBlock` extends
  Create's `MechanicalCrafterBlock` with our block entity type (`FactionCrafterBlockEntity`); Create's renderer,
  cogwheel visual, stress impact 2, item handler and unpacking handler are registered for it. Mixins
  (`lotr_port.create.mixins.json`, applied only with Create via `CreateMixinPlugin`): `BlockEntryMixin` makes
  `AllBlocks.MECHANICAL_CRAFTER.has(state)` true for them (every Create crafter check: grids, connected inputs, arms);
  `CrafterRecipeMixin` wraps `RecipeGridHandler.tryToApplyRecipe` in the crafter's tick: when Create finds nothing, a
  grid with faction crafters (any in the chain) makes their tables' recipes (`FactionCrafting`; never ones with an
  `alignment` need). Models: Create's crafter models, every quad given tint index 0 (`FactionCraftersClient`,
  ModifyBakingResult); no connected textures. Made at their own table with Create's recipe (3 per craft) and
  `FactionRecipe.alignment` (faction, `above` 0), checked by `FactionCraftingMenu`. GameTests `FactionCrafterTests`.
  Read Create's code by decompiling the jar locally (allowed by Aaron 2026-11-19); never commit decompiled code.

## Sounds (2026-11-06)
- `converter/sounds.py` copies LOTR's 190 sound files and 101 events. Ids are LOTR's, snake_cased per part:
  `half_troll.say`. `sound/LotrSounds` registers them from `content/sounds.json`.
- NPC types carry `sounds` (ambient/hurt/death/attack/kill), set from the LOTR class (`sounds.NPC_SOUNDS`) and
  played by `LotrNpc`. Blocks: `LotrBlocks.sound` (treasure, plate) and `GateBlock`. Items: `content/LotrUseItem`
  (horn, pipe, vessel). Crossbows: `sound/SoundHooks` swaps vanilla's crossbow sounds. Events: FastTravel, Pledges,
  `LotrNpc.notifyTrade`.
- `client/LotrAmbience` plays LOTR's ambience from `assets/lotr_port/ambience.json` (wight moans, wind, sea,
  rain and thunder). "In Middle-earth" = standing in a lotr_port biome.

## Build
From the repo root: `./gradlew :lotr:build`, `:lotr:runClient`, `:lotr:runServer`, `:lotr:runGameTestServer`,
`:lotr:runData`. Shared rules are in the root `CLAUDE.md`.
- GameTests (2026-11-18): ~136 in `gametest/`; CI's "Middle-earth world" step (`WorldCheck`, real chunks across the
  biomes) runs only when all pass. Most use template `lotr_port:empty`, which is 16x4x16 so the runner spaces tests
  apart (at 1x1x1 neighbouring creature tests' animals ran into each other). Tests that need entities to tick wrap
  their body in `ThrownTests.inLoadedArea` (forced chunks). Timing tests (`fiftyColoniesRunTenDaysWithinTheBudget`)
  can miss by a hair on a slow runner; re-run the failed job rather than loosening them. `rabbitEatsCrop` failed now
  and then with its field a block above the rabbit; since 2026-11-18 the field is level with the ground (watch it).

## LOTR tables from the reflection dump (2026-11-06)
- The exporter's `ReflectionDump` writes `export/extra.json.gz` (static fields of LOTR's tables, every drink item,
  every biome's fields incl. music, achievement, invasions, animal lists, villages); `converter/extra.py` turns it
  into: modifiers as data enchantments (+ enchantable tags for all LOTR gear), achievements as advancements
  (`progress/Achievements.grant` by LOTR name), titles (`/lotr_title`), shields and capes (`/lotr_shield`,
  `/lotr_cape`; not drawn yet), drinks (`content/LotrDrinkItem`, components `drink_strength` and `vessel`),
  calendar (`/lotr_date`), music regions (biome music events a pack fills), invasions (`npc/InvasionSpawner`),
  animals (creature NPCs + `npc_animals/`, `AnimalBehaviour`).
- Recipes: unsmeltery computed from crafting recipes, hobbit oven and kebab stand from furnace foods. Lore books
  (`converter/lore.py`) go into structure chests. Quests: pickpocket kind, LOTR's per-quest lines
  (`speech/miniquest/...`). Mounted units ride (`Hiring.mount`).
- What is still missing: `lotr/docs/missing-features.md` ("Still open").
- Faction influence (2026-11-07, `faction/FactionInfluence`): a kill changes alignment only with factions whose
  influence reaches the player: inside one of its control zones (LOTR's, map pixels, Middle-earth only) or within
  `npc_radius` (24, LOTR's) of one of its NPCs in any dimension (`data/lotr_port/alignment/settings.json`). The bar
  draws LOTR's icons either side (`gui/alignment.png`, 14x14, faction-tinted layer then gold frame): arrows (v 74/60)
  in a zone, circles (v 102/88) near its people, else arrows faded by distance to a zone edge (1 at the edge, 0 at 50
  map px; none for isolationists). Shows outside Middle-earth while a faction NPC is near. Payload
  `HudNet.AlignmentBar.flags` (bits 4-11 the fade). Up/down cycles regions (west, east, south, realms), left/right
  factions in it; factions with fixed alignment, not playable or region `none` (Utumno, Hostile...) are hidden.

## Server pregeneration (2026-11-16, `world/Pregen`)
- `/lotr_pregen start <all|roads|waypoints|spawn|players> [radius]`, `pause|resume|cancel|status`, `speed <n>`,
  `follow on [radius]|off` (op level 3). Default radii (chunks): spawn 32, waypoints 12, roads 4, players 24. Queue is
  SavedData `lotr_port_pregen` (resumes after restart); a region ticket per chunk, FULL status, fewer at a time over
  35/45 mspt. Distant Horizons (3.3.3) is installed on the server too, so it sends LODs to clients.
