# LOTR Port (Minecraft 1.21.1 NeoForge)

Future-port of The Lord of the Rings Mod (1.7.10, Update 36.15) to Minecraft 1.21.1, part of the TARDIS: Ring Bearer
modpack project. Mod id `lotr_port`, main class `LotrPort`, package
`com.porcupinestudios.lotrport`.

**Keep this repo private** (it holds both mods). LOTR's code belongs to Mevans and has no public source or license to
redistribute. The reference is the compiled jar `libs/LOTRMod v36.15.jar` in PorcupineStudios/RingRealms; its class map:
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
- Reuse LOTR's textures, sounds and map image (private play).
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
  biomeColors foggy) get a client fog (`LotrClientGame.fog`). Screenshot scripts: `lotr/run/mt.txt`,
  `converter/run/client/shots3.txt` (the shot runner gives night vision; `goto ... floor` = ground under trees).
- World type "Middle-earth" (`lotr_port:middle_earth` preset): the overworld is generated from the map, so spawn and
  respawn are vanilla. Any other world gets the `lotr_port:middle_earth` dimension; `/lotr travel` goes there and
  back. `LotrDimensions.middleEarth(server)` returns whichever level is Middle-earth.
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
- Trading uses the vanilla trading screen (`NpcTrading`, prices split into silver coin stacks by `Coins`). Hiring:
  right-click a captain (sneak if it also trades) lists units in chat with [Hire] links running `/lotr_hire`;
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

## Animals and mounts
- Animals are creature NPCs (above); `converter/world.py GRAZERS` adds vanilla spawners on grass biomes. NPCs with
  `spawnsInDarkness` spawn only in the dark unless the biome has `hostiles_in_day` (`npc_spawns`, `NpcSpawner`).
  LOTR mutton and the three vanilla horse armours are vanilla items (`VANILLA_ITEMS`).
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
    up to `max_foundation` 24).
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
  travel is blocked. `client/MapTextures` keeps the map at full size and halved three times (box filter, plain and
  sepia) and the screen draws the copy that fits the zoom with float positions, so zoomed-out maps don't shimmer;
  the parchment overlay is one stretch over the window at 20% (as LOTR), the sea beyond the edge the map's ocean
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
- Not yet: bounty, retrieve and pickpocket quests, fellowships, titles, custom waypoints, LOTR's quest
  speech.

## Colonies (phase 9, RingRealms built in: `colony/`)
- Founding: a Colony Charter (`colony_charter`, tab "Colonies") used on a block makes the Hall there, for the
  founder's pledged faction; renamed on an anvil = colony name. A player may found several (Aaron 2026-11-10;
  `max_colonies` in `colony/settings.json`, 0 = no limit); Halls `hall_gap` apart. Player actions go to
  `Colonies.nearest(player)` (nearest owned colony in their dimension, else the oldest).
  Saved in `Colonies` (SavedData `lotr_port_colonies`, codecs); history `colony_founded`, `citizen_arrive`,
  `citizen_die`, `recruit_started`. Citizens carry the `lotr_port:colony` attachment and never despawn.
- Growth (`colony/settings.json`): once a colony day, `arrivals_per_day` migrants come to a loaded Hall while there are
  free beds (bed heads within `bed_radius`); the first settler needs no bed. Selective migration (`colony/Migrants`,
  Aaron 2026-11-03, `selective_migration` on by default): migrants wait at the Hall marked `applicant:<colony>` (not
  citizens) and hold a bed; the owner right-clicks them or uses the chat [Accept]/[Deny] links (`/lotr_colony
  accept|deny <uuid>`). At most `max_applicants` wait; unanswered ones leave after `applicant_days`. History
  `migrant_offer`, `migrant_leave` (why: denied/waited). A turned-away applicant that was unloaded is dropped on load. Who comes: `colony_migration/<faction>.json`
  (generated by `converter/colonies.py`: the faction's people minus traders, captains, leaders, rare races).
- Rare races (`recruit/<id>.json`, hand-written in `src/main/resources`): never migrate. The founder sneak +
  right-clicks an envoy (an NPC of that type) to start; gates are faction alignment, population and a per-colony
  limit. Stages of objectives: deliver (sneak + right-click the envoy again), kill_faction, kill_npc, visit (a
  waypoint), population. After the last stage the envoy walks to the Hall as a rare citizen. Shipped: Rangers of the
  North, a Wizard (Gandalf's type).
- TARDIS researchers (shared API `ResearcherProvider`, in `shared/SharedApiHooks`): a founder's colony counts every rare
  citizen plus one per `citizens_per_researcher` others; the TARDIS runs its research faster for each.
- `/lotr_colony` (info, citizens, open recruitments); admin `/lotr colony list|day [n]`.
- Not yet (RingRealms has them on 1.7.10): buildings and the validator, jobs, needs and happiness, the Ledger and
  other screens, realms on the colony, envoy spawning (rare NPCs come from normal spawning or commands for now).

## Groups: the shared core for colonies and hired units (2026-11-10, `group/`, UNBUILT)
Plan: https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N (colony core, hired units, multiplayer; Aaron's answers are in
it). Step 1 of its build order is written; nothing was compiled against NeoForge (CI is offline, the sandbox has no
Maven). The store's logic was run against stand-in classes only, so treat every line as unbuilt until a PC build.
- `Group` = id, kind, owner (`Owner`: player / AI ruler / realm), members (`Member`: person, role, joined, flags) and
  parts. `Groups` (SavedData `lotr_port_groups`) is the only writer and keeps owner → groups and person → group maps,
  so nothing scans all groups (target: 50 colonies of 100+). A person is in one group at a time; `join` moves them.
- Parts (`PartType`, `Parts.register`): each has its own Codec, data version and migration. A part whose type is not
  registered, fails to parse or comes from a newer build stays as saved and is written back untouched. No part types
  are registered on groups yet; land, access, orders and the rest arrive with their steps in the plan.
- Colonies: each colony has a group with the colony's id (kind `colony`). `Colonies` mirrors owner and citizens into
  it (found, foundAi, transfer, join) and `Colonies.get` back-fills groups for older saves. `Colony` still keeps its
  own citizen list and `founder`; moving those fully into the group is the next step. `ownedBy` reads the owner map.
- Hired units: `Hiring.hire` puts the unit in the player's company (kind `company`, `Groups.company`: one per player
  for now; several is undecided and that method is the only place that decides). `LotrNpc.die` leaves the group.
- People: citizens get a `People` record too (`People.settled`, no owner). Membership is in `Groups`, not `People`.
- Tests (unrun): `GroupTests`; group checks added to `ColonyTests` and `NpcTests`. `ColonyTests` now expects a second
  colony to be allowed.
- Not yet: commands as the one entry point, the layer check, access and land, simulation levels, the scheduler.

## Colonists: traits, needs, mood, kin (2026-11-10, `colonist/`, UNBUILT)
Plan: the "Colonists" section of https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N. Not compiled against NeoForge. The
rules are plain Java (no game classes but `ResourceLocation`) and their JUnit test `ColonistRulesTest` (`:lotr:test`)
passed here against stand-in classes; the Codecs, loaders and `Colonists` have never been compiled.
- Data, reloaded live: `colonist_trait/` (19: kind, scale, weight, heritable, work_speed, need_rates, thoughts,
  on_break, conflicts), `colonist_need/` (5 bars: fall_per_day, start, levels → thought), `colonist_thought/` (24:
  mood, duration_ticks, stack_limit; duration 0 = lasts while its cause does), `colonist_race/` (man, hobbit, dwarf,
  elf, orc: match words in the NPC type path, days_per_year, adult_age, extended_household, need_rates, settler ages),
  `colonist/settings.json`. `ColonistData.loadProblems()` lists dangling ids.
- Person parts (`People.part` / `setPart`, saved in the person's tag under `parts`, via `group/PartBag`, which groups
  use too): `traits`, `needs`, `mood`, `kin`.
- Rules: `TraitRules` (roll: one positive, one negative, more by chance, never two on a scale or in conflict;
  inherit; work speed, need rates, break behaviour), `NeedRules` (a bar is a value at a tick; its value now is worked
  out on read, so nothing ticks per colonist), `MoodRules` (score = base + live memories + standing thoughts;
  states by threshold), `KinRules` (age: Men 1 colony day = 1 year, adult at 18, Aaron 2026-11-10; household id;
  `homeThought`: own household never counts as sharing), `RaceRules`.
- Households: a couple and their children under age; a race with `extended_household` (hobbits) keeps grown
  children, a spouse who married in and grandparents together. Only the living who are in the lookup count.
- Wired so far: `Colonies.join` → `Colonists.settle` gives a citizen their four parts, seeded by the NPC's id (same
  settler, same traits). `/lotr_colony` lists each citizen's traits. The race numbers other than Men's are first guesses.
- Not yet: anything that fills needs or applies mood (work speed, breaks, leaving), houses per household (the port
  only counts beds), marriage, births, children's bodies, coming of age, the family tree and needs screens, job
  interests (no jobs in the port yet).

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
  and wars from the log alone. Admin: `/lotr ai list|day [n]`. Not yet: attacking player colonies (needs RingRealms'
  offline protections), real soldiers on the march, trade and treaties beyond war/peace, refugees.

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
  with a block item or a bow in hand the click places/shoots instead (LOTR). Open gates have no collision and draw
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
- Not yet: alcohol tolerance, drunken speech, placeable mugs, NPCs drinking.

## Combat and armor models (2026-11-04)
- 1.7.10 had no attack cooldown. LOTR's melee timing (`LOTRWeaponStats`: 15 ticks for a player's sword ÷ the
  class's speed factor, reach 3 × factor, extra knockback) is generated onto each melee weapon's `weapon` block in
  `content/items.json`. `content/LotrWeaponItem` turns that into attack speed (20 / ticks), entity interaction
  range, attack knockback, sweep only for plain swords, and LOTR's poison for poisoned daggers. NPCs swing at the
  weapon's `mob_ticks` (`LotrNpc.WeaponMeleeGoal`). Damage is LOTR's own, with fractions kept.
- Bows (`LotrBowItem`: draw time, arrow damage factor), crossbows (`LotrCrossbowItem`: vanilla crossbow, bolts only,
  `#lotr_port:crossbow_bolts`), and bolts and poisoned arrows (`ArrowItem` + a fixed Poison effect). Pull and charged
  predicates are in `LotrClient.rangedProperties`.
- LOTR's large in-hand icons: `separate_transforms` item models on `item/large_2x` / `large_3x`.
- Special armor models: `converter/armor_models.py` → `lotr_port/content/armor_models.json` → `client/ArmorModels`
  (`RegisterClientExtensionsEvent`). Animated parts keep their resting pose.
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
- What was skipped and why: `lotr/docs/import-checklist.md`.
- Faction influence (2026-11-07, `faction/FactionInfluence`): a kill changes alignment only with factions whose
  influence reaches the player: inside one of its control zones (LOTR's, map pixels, Middle-earth only) or within
  `npc_radius` of one of its NPCs in any dimension (`data/lotr_port/alignment/settings.json`). The bar shows LOTR's
  arrows either side (gold: zone, pale: near its people, none: kills don't count), and shows outside Middle-earth
  while a faction NPC is near (auto-watching that NPC's faction there). Payload `HudNet.AlignmentBar.flags`.
