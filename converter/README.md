# Converter

Turns LOTR 36.15 (1.7.10) content into the 1.21.1 port's generated resources. Two steps:

1. **Export** (`converter/src`, a small 1.7.10 mod built with the GTNH toolchain). CI's "Convert LOTR" workflow
   starts a real LOTR client under a virtual screen, the exporter reads every LOTR block, item, recipe, creative tab
   and ore dictionary entry from the running game, writes `converter/export/*.json` and CI commits them. It runs when
   `converter/src` or the Gradle files change, or by hand from the Actions tab. Reflection on public game state only;
   no LOTR code is copied.
   **Running it locally** (how it's normally run; the GitHub workflow is only a fallback):

   ```bash
   sudo apt-get install -y openjdk-8-jdk xvfb x11-xserver-utils libgl1-mesa-dri libglu1-mesa
   sudo sed -i 's/^assistive_technologies=/#assistive_technologies=/' /etc/java-8-openjdk/accessibility.properties
   cd converter
   JAVA_TOOL_OPTIONS="-Dlotrexport.run=true -Dlotrexport.out=$PWD/export" \
     xvfb-run -a -s "-screen 0 1280x800x24 +extension RANDR +extension GLX" ./gradlew runClient \
     -Porg.gradle.java.installations.paths=/usr/lib/jvm/java-8-openjdk-amd64
   ```

   About 15 minutes; the game closes itself when the export is written. Gotchas: LWJGL 2 needs `xrandr`
   (x11-xserver-utils), or the game fails with `LinuxDisplay.getAvailableDisplayModes`. Ubuntu's Java 8 enables an
   accessibility bridge that isn't installed (`AWTError: Assistive Technology not found`), hence the sed. Where
   Maven Central answers 429 (rate limit), point Gradle at a mirror with an init script in `~/.gradle/init.d`.

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

   `converter/structures.py` (phase 7) turns `structures.json.gz` (every LOTR structure built 3 times with different
   seeds on fresh flat chunks of a throwaway client world) into NBT templates (`nbt.py`), one-element jigsaw pools,
   structures and structure sets (spacing from LOTR's chance per chunk), `has_structure/<id>` biome tags and
   `faction/<f>` structure tags, plus `npc_spawns/` tables from each biome's LOTR spawn lists. Only structures a biome
   places directly are wired (82); village parts and fortress pieces wait for villages (`structures_in_no_biome`).

   `converter/quests.py` (phase 8) writes LOTR's waypoints, waypoint-region biome tags, the fast travel cooldown,
   the map image and the mini-quest pools (collect and kill quests per LOTR quest group) from `quests.json`, and
   names each quest-giving NPC type's pool.

   `converter/colonies.py` (phase 9) writes each faction's colony migration table and the English text of the mod's
   own screens and messages (`UI_EN`).

   `converter/armor_models.py` replays the constructors of LOTR's special armor models (`LOTRArmorModels`: crested
   helmets, robes, hats) on a small bytecode interpreter and writes their boxes as `lotr_port/content/armor_models.json`.
   Melee weapon speed, reach and knockback come from the `LOTRWeaponStats` tables (`classfile.py static_calls`) or,
   once exported, from each item's `weapon` stats in `items.json`.

   Recipes: crafting, smelting, each faction table, and LOTR's machines (`StationExporter`: barrel, millstone, Ent
   jar, each alloy forge's smelting and alloys, the unsmeltery) as `lotr_port:<machine>` recipes; weapon poisoning
   becomes shapeless crafting. Blocks only an item places drop what LOTR's block dropped (exported `drops`).

Never edit generated files by hand: change the generator and run it again.

Known gaps (report.json): spawn eggs and structure spawners wait for the NPC and structure phases; LOTR's special
recipes (poisoning weapons, dyeing) are code, not data; drinks keep one item per drink (vessel and strength become
data later); a few armor sets use one texture per item; connected textures are baked to the single-block look;
leaves and grass are untinted; blocks only placed by an item (crops, beds, plates, portals) drop nothing yet.
