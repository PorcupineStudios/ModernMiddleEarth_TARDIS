# Converter

Turns LOTR 36.15 (1.7.10) content into the 1.21.1 port's generated resources. Two steps:

1. **Export** (`converter/src`, a small 1.7.10 mod built with the GTNH toolchain). CI's "Convert LOTR" workflow
   starts a real LOTR client under a virtual screen, the exporter reads every LOTR block, item, recipe, creative tab
   and ore dictionary entry from the running game, writes `converter/export/*.json` and CI commits them. It runs when
   `converter/src` or the Gradle files change, or by hand from the Actions tab. Reflection on public game state only;
   no LOTR code is copied.
2. **Generate** (`python3 converter/generate.py`, needs Pillow). Reads the export and the LOTR jar
   (`converter/libs/`) and writes `lotr/src/generated/resources/`: the block/item/tab lists the mod registers from,
   blockstates, models, textures, 22 languages, recipes, loot tables and tags. Also writes `converter/idtable.json`
   (old id + metadata -> new id, for structures later) and `converter/report.json` (what was skipped or guessed).

   `converter/world.py` does Middle-earth: one 1.21 biome per LOTR biome (climate, water colour, simple vanilla trees,
   grass and ores for now), LOTR's map image as `lotr_port/map/biomes.bin.gz` (one biome per 128-block pixel, origin
   pixel 810,730 = block 0,0 in the Shire), `map.json` (per-biome ground height and hilliness in blocks), the noise
   settings with a surface rule per top/filler block pair, the dimension, dimension type and world preset.
   Phase 5 adds small rivers (noise lines cut into low ground, settings in `map.json`), LOTR's 225 roads
   (`lotr_port/map/roads.json`, laid by the `lotr_port:roads` feature), each biome's ores and LOTR's weighted tree mix
   (`converter/trees.py`: 95 tree types as vanilla-shaped trees with LOTR's woods and leaves).
   `converter/factions.py` writes LOTR's 29 factions (colour, region, ranks, pledge rank, map area, control zones,
   default relations) as faction data, with their lang keys.

   `converter/npcs.py` (phase 6) writes LOTR's people as NPC types (350: faction, skins, size, stats, gear, names) with
   their trade pools, captains' unit pools and name banks; the exporter builds each LOTR entity in a throwaway client
   world and samples its spawn gear.

Never edit generated files by hand: change the generator and run it again.

Known gaps (report.json): spawn eggs and structure spawners wait for the NPC and structure phases; LOTR's special
recipes (poisoning weapons, dyeing) are code, not data; drinks keep one item per drink (vessel and strength become
data later); a few armor sets use one texture per item; connected textures are baked to the single-block look;
leaves and grass are untinted; blocks only placed by an item (crops, beds, plates, portals) drop nothing yet.
