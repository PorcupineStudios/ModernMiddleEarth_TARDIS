# Delves: Moria (and later Goblin Town)

A **delve** is a procedural underground complex: a maze of levels, corridors, rooms, stairwells and hand-placed
"set pieces", planned once per world and built chunk by chunk. Moria is the first (2026-11-25). Goblin Town will
be a second data file with its own palette and room kinds (wooden scaffolding, fence-post stilts) on the same engine.

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
- `/lotr_delve goto lotr_port:moria <set piece>`: stand outside a gate or at the west end of a set piece.
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

## Not yet
- The Watcher in the Water, Gollum.
- Windows and light shafts to the mountainside (the 21st Hall's east window), the Dimrill Stair waterfall.
- Goblin Town (same engine, its own spec: natural caverns hung with wooden scaffolding, rope bridges, fence-post
  stilts, the Great Goblin's throne, the back door to Gollum's lake).
