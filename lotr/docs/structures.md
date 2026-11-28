# Structures and spawning

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

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
    the footprint can be deep; 24 left a floating building in Eriador (2026-11-19). The fill (and the grass levelling
    of open ground-layer spots) never enters the template's own air: before 2026-11-27 it filled the Hobbit inn's
    cellar through its stairwell (#91; test `foundationsLeaveCellarsEmpty`).
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
