# Delves: Moria and Goblin Town

A **delve** is a procedural underground complex, planned once per world and built chunk by chunk. Two layouts share
the engine (spec `layout`): `maze` (Moria, 2026-11-25: levels, corridors, rooms, stairwells and hand-placed set
pieces) and `goblin` (Goblin Town, 2026-11-26: one great cavern hung with wooden scaffolding; see its section below).

Aaron's decisions (2026-11-25): Moria at **full map scale**, gate to gate (the map's West-gate in Eregion to Dimrill
Dale, ~4.6 km under the Misty Mountains); a **grand Third Age ruin** (intact architecture, dark, cracked, rubble,
orc camps; how ruined is one `decay` number so a restored Moria can reuse the plan); rooms are **coded set pieces
plus .nbt rooms** built in game; the way through is a **hidden route in a maze** (one guaranteed route, unmarked,
winding between levels; side passages loop, dead-end or end in collapses).

## Where things are
- Code: `lotr/src/main/java/com/porcupinestudios/lotrport/world/delve/`. Everything but `DelveFeature` and
  `DelveCommand` is plain Java with no Minecraft classes: the planner and builders can run (and be tested or previewed)
  without the game, given a ground height function.
- Data: `data/lotr_port/worldgen/configured_feature/moria.json` (type `lotr_port:delve`, the whole spec under
  `config.spec`), placed by `worldgen/placed_feature/moria.json` and `neoforge/biome_modifier/delves.json` (every
  `#lotr_port:middle_earth` biome, step `underground_structures`; chunks outside the plan's box return at once).
- Loot: `loot_table/chests/moria_hoard|moria_store|moria_looted.json`.
- Template rooms: `structure/delve/moria/**.nbt` (any namespace; folder is the spec's `templates`).
- Tests: `gametest/DelveTests` (route gate to gate over 3+ levels, rooms stay under the mountain, the plan is the
  same twice, every palette block and loot table exists, the West-gate doors and the Dwarrowdelf's pillars build).
  The CI world check (`WorldCheck.moriaIsBuilt`) generates the real West-gate chunk and checks the doors stand in
  the doorway with the passage open behind. Screenshot script: `lotr/devscripts/moria.txt`.

## How a plan is made (`DelvePlanner`)
1. **Grid**: `cell`-block cells (48) over a band `half_width` (800) either side of the `axis` (gate to gate). The
   ground over each cell is sampled every `ground_step` (16) blocks from `MiddleEarthMap.heightAt` (a pure function
   of seed and map; ~0.6 s for Moria). A cell is **open** on a level if its lowest ground is at least `cover` (14)
   blocks above the level's tallest room. So upper levels only exist under the high core and nothing breaks the
   mountainside except where a set piece means to.
2. **Set pieces** take their cells first, in list order. `place`: `front` (gates: the doorway goes where the ground
   rises at least `rise` within `look` blocks, the lowest such spot near the data's x/z within `foot_search` and
   `row_search` rows; then cells inward until one is covered, `min_cells`..`max_cells`), `summit` (the Endless
   Stair: the cell with the highest ground within `search` cells), else `cover` (fits within `search` cells of its
   spot; `attach` {to, side} puts it beside another set piece). A piece also reserves the cells of every level its
   height (`height`) or depth (`below`) passes through.
3. **Maze nodes**: every other open cell. Candidate ways: corridors between neighbouring cells of a level (a set
   piece only on its `doors` sides; a gate only from its innermost cell), stairwells between the same cell on two
   levels (each with chance `stairs`), the Endless Stair's landings.
4. **Hidden route**: Dijkstra with random weights (`route_wiggle`, stairs cost `stair_cost`) from each set piece
   with a `route` number to the next (gates 0 and 6; other set pieces are avoided). Then Kruskal over the rest
   (route first), cells cut off from the route dropped, `prune_passes` passes removing dead ends with chance `prune`,
   and some dead ends joined back as `loops`.
5. **What each node becomes**: a stair landing if it has a stair; else a room with chance `room_chance`
   (`dead_end_rooms` for dead ends) of a kind from the level's `rooms` weights, else a junction. Rooms get a size and
   height from the kind, a jitter in their cell, a floor offset of up to `floor_jitter` (corridors climb with stairs).
6. **Pieces**, built in order: corridors (0), rooms/stairwells/templates (1), set pieces (2). Indexed per cell, so
   a chunk finds its few pieces at once. Every random choice while building is a hash of the world position, so it
   doesn't matter which chunk is generated first.

## Geometry rules worth knowing
- Room interiors stay `margin` + 2 blocks inside their cell; corridors leave rooms through the middle of a side
  (the lane) and dog-leg along the cell boundary when two lanes differ. That keeps every corridor out of every other
  room. A set piece's doorways are the middle of each of its perimeter cells.
- Level spacing: Moria's upper levels are 24 apart (`level_1` y 74 at the West-gate .. `level_7` y 218), the seven
  deeps 18 apart (`deep_1` y 56 .. `deep_7` y -52). A level's tallest room + floor jitter + 2 must stay under the next
  level's floor - 1.
- Built rooms are fully walled, so caves, aquifers and carvers never leak in; hewn corridors and caverns only seal
  where they meet air, water or lava (`fillOpen`).

## Moria's set pieces
| id | kind | what |
|---|---|---|
| `west_gate` | `west_gate` | The Doors of Durin: ithildin doors (role `door` in palette `west_gate`) in a carved doorway at the foot of a built rock face that slopes back into the mountain, a landing, steps down if the ground falls; inside, broad half-block steps up to `level_2` and a pillared hall. |
| `east_gate` | `east_gate` | The Great Gates, broken; steps down towards Dimrill Dale; inside, the First Hall. |
| `guardroom_well` | `room` | The guardroom with the deep well (route stop 1). |
| `twenty_first_hall` | `great_hall` | The Twenty-first Hall of the North-end on `level_7` (route 2). |
| `mazarbul` | `room` | The Chamber of Mazarbul, Balin's tomb in calcite and quartz (route 3), beside the 21st Hall. |
| `dwarrowdelf` | `great_hall` | The great pillared hall on `level_2`: 7x7 pillars on plinths with flared capitals into a coffered roof 34 high, a clear aisle (route 4). |
| `bridge_of_khazad_dum` | `chasm_bridge` | At the Dwarrowdelf's east end: a chasm 120 blocks deep to lava, a slender railless arch (route 5). |
| `endless_stair` | `endless_stair` | A helix stair from `deep_7` through every level to Durin's Tower under the nearest summit, with a door out on the mountainside. Off the route. |
| `great_cavern`, `mithril_deeps` | `room` | Landmarks in the deeps: a vast natural cavern; mine workings rich in ore and mithril. |

## Tuning (all data, no Java)
- How ruined: `decay` (0..1). Each palette entry's `decay` (-1..1) ties its weight to it (1 = cracked blocks, only
  in ruin; -1 = lit lamps and gilding, only when intact). `ruin` numbers (rubble, rough walls, webs, broken pillars,
  collapsed dead ends) are multiplied by `decay`.
- How much maze: `maze.prune_passes` and each level's `prune` (fewer = denser), `loops` (more = more ways round),
  `stairs` (stairwells per cell), `room_chance`.
- Room kinds (`rooms`): `size`, `height`, and optional parts: `shape` (`built`, `mine`, `cavern`), `pillars`, `band`,
  `tomb`, `well`, `forge`, `lamps`, `chandelier`, `palette`, `roughness`, `veins`; `variants` with `dressing`
  (role, count, at `wall` | `corner` | `floor` | `center` | `ceiling`).
- Styles (`styles`): corridor width (odd), height, rib spacing, `hewn`, roughness, lamp chance, palette.
- Palettes (`palettes`, may `extends` another): roles `wall`, `ceiling`, `floor`, `floor_alt`, `floor_trim`, `trim`,
  `carved`, `pillar`, `capital`, `chamfer`, `stairs`, `slab`, `rail`, `light`, `chandelier`, `chain`, `rough`,
  `rock`, `support`, `vein`, `rubble`, `rubble_top`, `web`, `tomb`, `tomb_top`, `water`, `lava`, `forge`, `anvil`,
  `door`, `chasm_wall`, `bridge`, plus any dressing roles. Blocks are `ns:id[prop=value]`; properties a block doesn't
  have are ignored; `keep` places nothing. A chest or barrel entry may carry `loot`.
- Changes need a new world (or new chunks): the plan is per world seed and fixed while the server runs.

## Template rooms (.nbt) - for Aaron
Build a room in game, save it with a structure block, and drop the `.nbt` in
`data/lotr_port/structure/delve/moria/` (subfolders are fine). It joins the pool for levels whose `rooms` list
`template` (weight in each level's `rooms`).
- Size: at most 37 x 37 footprint (a cell less its margins) and no taller than the level's tallest room + 2 (12-14).
- The template's bottom layer is the floor; its outer layer is the walls (or structure void to keep the rock).
- Mark every doorway with a **jigsaw block** in the doorway's bottom middle, in the wall plane, its front facing out
  of the room, final state `minecraft:air` (the default). The room is used only where it can be turned so it has a
  doorway on every side a corridor arrives from; corridors run to the nearest doorway on each side.
- Doorways should be at least 5 wide and 6 high (corridor size) where the corridor meets them.
- Everything else (chests with loot tables, spawners later) is placed as saved.

## Commands and tools
- `/lotr_delve info lotr_port:moria`: the plan's summary (where each set piece went, node counts).
- `/lotr_delve goto lotr_port:moria <set piece>`: stand on a gate's landing facing its doors, or at the west end of a set piece.
- `/lotr_delve preview lotr_port:moria` (op 3): writes `delve_preview/moria/level_NN_<name>.png` (what each level
  builds at foot height, every 2nd block, over the ground) and `summary.txt`. Takes a few minutes; no world needed.

## Numbers measured (2026-11-25, seed 12345, outside the game)
Plan 1.1-1.4 s; ~49,500 candidate cells, ~20,000 kept, ~39,000 pieces; route ~130 nodes over many levels. Building
every chunk of the plan's box in plain Java (no world writes) took 2.1 ms a chunk on average with the earlier,
sparser maze. Not yet measured in game.

## Who lives there (Aaron 2026-11-25: orcs and trolls by depth, the Balrog very rare in the lowest mines, capped)
`data/lotr_port/delve_spawns/moria.json` (`DelveSpawns`, reloads with /reload; biome spawns stay on the surface).
Every `interval` (200) ticks per player inside a Moria piece, chance `chance` (0.35): up to `tries` spots 16-40
blocks away and 8 up or down, inside the delve, dark, on solid floor with a clear `space` [width, height]; the entry
is picked by weight among those whose `min_y`..`max_y` holds the floor, then rolls its own `chance`; `max_alive`
caps how many of that NPC are loaded in the world; no spawn while `cap` (10) of the table's NPCs are within
`radius` (40).
- Levels (y 64-260): Gundabad orcs, archers, wargs, a few uruks.
- Upper deeps (y 15-63): orcs, uruks and their archers, trolls.
- Lower deeps (below y 15): uruks, trolls, mountain trolls where there is room (3 x 6).
- The Balrog: y -64..-30 only (`deep_6`, `deep_7`), weight 1 of 37 there, its own chance 0.004, `max_alive` 1, room
  3 x 6 (caverns and mines; it can't fit the 5-high tunnels). About one attempt in 9000 in the lowest deeps, roughly
  a 0.3% chance per player-hour spent down there.
- `/lotr_delve spawn lotr_port:moria` forces one attempt near you. GameTest `moriaSpawnsByDepth`.
- The West-gate's doors open like any gate (Aaron 2026-11-25: no password).

## Goblin Town (2026-11-26, `GoblinPlanner`, `GoblinPieces`, `RiftShape`, `content/goblin/`)
Aaron: THE Goblin Town plus smaller generic ones; scrappy wooden scaffolding with new blocks that sit together in
hue and contrast; the Front Porch's floor should literally swing open (Create may drive it); decoy caves that look
exactly like the Front Porch but are safe; the deep falls into Gollum's watery cave; the back door.
- Three configured features, all layout `goblin`, all on the biome modifier `delves.json`:
  - `goblin_town`: THE town, one per world at `anchor` (52544, -4288).
  - `goblin_towns`: generic copies, smaller (`town.length` 70-110, half width 18-26, height 60-80, random axis,
    back door with chance 0.5, no throne or lake). `scatter`: one chance (`chance` 0.35) per `region` (2048) square,
    at a seeded spot `margin` (300) inside it, only if that spot's map biome is in `biomes` (Misty, Grey, Angmar
    mountains) and outside every `exclude` circle (THE town's 2600). `reach` (700): how far a copy can spread from its
    spot (chunks look that far for copies). Copies are planned when a chunk nears them and kept (512 regions).
  - `goblin_porches`: decoys, just the porch (`armed` false, `chute` false), region 768, chance 0.4, foothills too.
  Seed 12345 (measured outside the game): 27 generic towns and 188 decoys between x 44000-60000, z -30000-30000.
- **The cavern** (`town`, `RiftShape`): a tall rift along `axis`, `length` x 2 `half_width`, `height` high, its top
  `cover` under the lowest ground over it (at most `max_top`). Across, it is narrow at the bottom (a pool
  `pool_depth` deep), widest a little under the middle, a dome at the top; ends taper; walls are 3D noise
  (`roughness`). Water falls (`falls`) pour from the walls into the pool.
- **Scaffolding**: `levels` (from 0.4 to 0.86 of the height, `gap` apart). Per level: platforms against both walls
  (`platforms`: spacing, length, depth; plank decks to the rock, rails of lashed posts, stilts of single fence posts
  or diagonal beams to the wall below, braziers and lanterns, junk, lean-to huts of planks and straw roofs), walkways
  along the walls between them (`walkway_*`), bridges across (`bridges`: per level, chance, share of rope bridges;
  they sag by up to half a block per few blocks), ladders between levels (plank column + ladder, a hatch), cages
  hung on chains. The Great Goblin's throne (`throne`) on a rock spire rising from the pool at `at` of the length,
  on `level`: deck, dais, a dark oak throne, skulls, braziers, bridged to both walls.
- **The Front Porch** (`porch`): a shallow dry cave in a cliff (a spot within `search` of `near` where the ground
  rises `rise` within `look`, ground in `ground`), `width` x `height` x `depth`. Its floor from `floor_from` to
  `floor_to` in, `floor_half` either side, is **porch floor** (Dusty Cave Floor). THE town's lies over a chute
  (at least `min_drop` 16; 215 blocks at seed 12345) to the **landing**, a low cave on a town level below with a heap of Filthy Straw
  (`straw`; it takes all fall damage) and a tunnel (`tunnel`: half-block steps, at most `max_slope`) into the town.
  Decoys are built by the same code at their own spot, rock under the floor sealed 6 deep; GameTest
  `goblinTownKeepsItsRules` builds a decoy at THE porch's spot and checks every block from the floor up is the same.
- **The trap** (`GoblinTraps`, numbers in `data/lotr_port/goblin_traps/settings.json`, reloads): a porch floor is
  armed when at least `min_drop` (4) blocks under it are open. There is no block state for it, so nothing on screen
  or in F3 tells a trap from a decoy (only digging or falling does). Stand on an armed floor for `trigger_ticks`
  (100-240) and grit falls (`dust`) with creaks for the last `warn_ticks` (50); then it springs: every porch floor
  block joined to it (up to `max_blocks`) becomes a block display, the floor splits along its long axis and the
  two leaves swing down on their outer edges in `swing_steps` (6) steps of `step_ticks` (3), and the `ambush`
  (2-4 goblins, 0-2 goblin archers) waits within `ambush_radius` at the bottom. After `reset_ticks` (2400) with no
  player within a few blocks it swings shut and the floor is whole again. Open traps are saved
  (`lotr_port_goblin_traps`; a swing cut short by a restart carries on, and a trap waits while its chunks are
  unloaded). A **redstone signal** beside an armed floor
  springs it too: a lever, or Create (a contraption's redstone link or contact) can work one, and players can
  build their own over a pit (the block has a recipe). GameTests `goblinTrapSwingsOpenAndShuts`,
  `goblinTrapSpringsOnRedstone`, `goblinTrapSpringsUnderfoot`.
- **Gollum's lake** (`lake`): past the cavern's far end at the pool's level (the falls drop into the pool),
  reached by a flooded tunnel:
  a round cave `radius` 28, `height` 14, still water `water_depth` deep, an island (`island_radius`), shores, glow
  lichen. **The back door** (`back_door`): a passage `passage` long from the lake (or the cavern) up to a slope
  within `search` of `near` (zigzag stairs when too steep), a dark oak door in a frame, a ledge outside.
- **Who lives there** (`delve_spawns/goblin_town.json`, `goblin_towns.json`; `DelveSpawns` with zones): each piece
  has a zone (`town`, `throne`, `porch`, `landing`, `tunnels`, `gollum_lake`, `back_door`) and an entry spawns only
  in its `zone` (empty = anywhere in the delve). Goblin Town is lit, so its entries have `dark` false. Goblins and
  goblin archers (faction `lotr_port:goblins`, 2026-11-26, see `lotr/CLAUDE.md` Goblins) in the town, at the landing,
  in the tunnels and at the back door; the **Great Goblin** (`npc/great_goblin.json`: the goblin body at scale 1.7,
  crowned skin, health 160, battleaxe or warhammer) only at the throne, chance 0.25, one at a time; **Gollum** only
  by his lake, in the dark, chance 0.2, one at a time. GameTest `goblinSpawnsByZone`.
- **Blocks** (`converter/goblin_blocks.py` draws every texture from one palette of warm greyed browns, rope tan,
  rust and straw, so they sit together and against Middle-earth's stone; it also writes models, blockstates, loot,
  recipes): Goblin Planks (+ slab, stairs), Lashed Post (thin log lashed with rope, any axis), Goblin Scaffold (open
  frame you see through), Rope Bridge (half-height slats, top or bottom half, either axis), Rope Rail (a rope
  handrail that stops you like a fence), Goblin Cage, Goblin Brazier (light 15, burns what steps in), Filthy Straw (no fall
  damage), Dusty Cave Floor (the porch floor). Run `python goblin_blocks.py` in `converter/` after changing them.
- `/lotr_delve goto <delve> front_porch|landing|town|throne|lake|back_door` (scattered delves: the copy nearest the
  player); world check `WorldCheck.goblinTownIsBuilt` (the real porch chunk: porch floor over the chute, straw at
  the landing); screenshot script `lotr/devscripts/goblin_town.txt`.

## Not yet
- The Watcher in the Water.
- Windows and light shafts to the mountainside (the 21st Hall's east window), the Dimrill Stair waterfall.
- Goblin Town was seen only in the screenshot workflow's client (2026-11-26, `devscripts/goblin_town.txt`: the
  porch, the floor swinging open, the town from the throne and a low platform, Gollum's lake, the back door, a
  generic town and a decoy), not played. In those shots the blue past the far scaffolding was chunks the client
  hadn't drawn yet (the roof was 29 blocks under the lowest ground). Create's own blocks aren't used (no
  dependency): a contraption drives the trap through redstone. Goblins don't haul prisoners or work the scaffolding's lifts;
  nothing shuts the back door.
