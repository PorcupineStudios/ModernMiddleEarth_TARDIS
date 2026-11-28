# Modern Middle-earth

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

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
