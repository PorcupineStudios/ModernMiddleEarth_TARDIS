# World generation: plants and trees

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Plants and trees (World generation)
- Reeds (2026-11-14): kind `reed` (`content/ReedBlock`): waterlogged stem from the bed of still water, LOTR's
  lower/mid/upper icons by `part`, live reeds grow to 3 above the water; worldgen `world/feature/ReedsFeature`
  (LOTRWorldGenReeds: still water at most 4 deep, never dry land). Lakes and Shire (2026-11-14): the 2D hill noise's
  valleys are a quarter deep (`VALLEYS`, fewer sea-level ponds). The Shire's hills are its own "downs"
  (`MiddleEarthMap.downs`, `DOWNS` in converter/world.py): round seedless hills that only rise,
  with LOTR's noise kept only as roughness.
- Features placed in the decoration steps must read the final heightmaps (`WORLD_SURFACE`, `MOTION_BLOCKING`,
  `OCEAN_FLOOR`): from the carvers step on, 1.21 keeps only those up to date, and the `_WG` ones stay at the bare
  ground under every tree (2026-11-25: the spider nests' canopy webs found no crowns and 9 in 10 tries failed).
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
- Big trees stand on a wide soil layer at the origin's height; `TemplateTreeFeature.settle` fills dirt under it
  down to the ground (`max_foundation`, default 12) so it doesn't hang as a ledge on slopes (#98), and `bank`
  raises the ground round it to a grass slope of a block per block, so it isn't a dirt wall either.
- Big trees (Fangorn oaks reach 25+ blocks from the trunk) overflow what a feature may write: `tree/TreeOverflow`
  keeps those blocks per chunk and places them when that chunk is loaded.
- Tree placement follows LOTR's rules (2026-11-05, `TemplateTreeFeature.fits`): logs go into air, plants, leaves
  and soil; leaves only into air and plants; a tree whose logs would hit another tree's logs, or whose trunk would go
  through another crown, isn't grown (~5% of forest trees). Before this, later trees' leaves cut holes in earlier
  trunks and branches (300+ logs in the check's patches). Odd sapling squares grow round their middle (a trunk log
  on it). The world check's `TreeCheck` generates 7 forests (252 chunks) and fails on any missing tree block, two
  trees sharing a log, or decaying leaves. Run it locally like the CI "Middle-earth world" step (back up
  `lotr/run/server.properties` first: the GameTest server uses it too). Screenshot script: `lotr/run/trees.txt`.
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
