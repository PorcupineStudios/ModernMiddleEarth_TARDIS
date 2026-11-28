# LOTR tables from the reflection dump

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

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
