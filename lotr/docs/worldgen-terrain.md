# World generation: terrain, map, ranges, rivers and roads

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Terrain, map, ranges, rivers and roads (World generation, phase 3)
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
- No floating terrain (2026-11-26, Aaron: floating islands in playtests; "stable through set rules rather than pure 3d
  randomization"): LOTR's blended noise is sampled at y 0 only (`flat_cache` round `old_blended_noise`, world.py), so
  hills are a 2D height offset and every column's density falls with y and crosses zero once (the 4x-steeper ground,
  the band and the interpolation all keep that). No overhangs either. Noodle caves only in deep rock (terrain density
  >= 1.5625, as cheese and spaghetti; noisecaves.py): near the ground they left thin crusts. Cave entrances and the
  ranges' carvers still open the ground. GameTest `terrainHasNoFloatingRock` (ground scans, caves off, round the
  roughness spots).
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
- The crash-site provider answers the overworld in a Middle-earth world (the wreck lands at the Shire spawn).
- CI "Middle-earth world" step makes a new Middle-earth world on a dedicated server (`WorldCheck`): spawn, crash
  site, one chunk in every biome on the map, road blocks on a road.

## Server pregeneration (2026-11-16, `world/Pregen`)
- `/lotr_pregen start <all|roads|waypoints|spawn|players> [radius]`, `pause|resume|cancel|status`, `speed <n>`,
  `follow on [radius]|off` (op level 3). Default radii (chunks): spawn 32, waypoints 12, roads 4, players 24. Queue is
  SavedData `lotr_port_pregen` (resumes after restart); a region ticket per chunk, FULL status, fewer at a time over
  35/45 mspt. Distant Horizons (3.3.3) is installed on the server too, so it sends LODs to clients.
