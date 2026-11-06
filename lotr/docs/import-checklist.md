# LOTR import checklist

What of LOTR 36.15 (1.7.10) still has to be imported into the port, and what is done. Cross items off (`~~text~~`
and `[x]`) as they land, with the commit. Details and the 1.7.10 → 1.21.1 mismatches are in
`lotr/docs/missing-features.md`. Converter counts come from `converter/report.json`.

## Done so far

- [x] ~~Blocks (1,441), items (897), textures, 22 languages, creative tabs~~
- [x] ~~Crafting, smelting and faction-table recipes (2,591)~~
- [x] ~~Machine recipes: barrel 29, millstone 35, Ent jar 7, forge alloys and smelting~~ (`519cb0af`)
- [x] ~~Poisoned-weapon recipes (31)~~ (`519cb0af`)
- [x] ~~Drops for item-placed blocks: crops, beds, plates (26)~~ (`519cb0af`)
- [x] ~~Create cross-mod recipes (134) and ore/nugget tags~~ (`222dc5a1`)
- [x] ~~Weapon stats on 1.21 combat, bows, crossbows, large in-hand icons~~ (`4cf0759f`)
- [x] ~~Special armor models (34)~~ (`4cf0759f`)
- [x] ~~JEI plugin~~ (`519cb0af`)
- [x] ~~World: biomes (169), map, roads, ores, trees, saplings, 82 structures~~
- [x] ~~Factions (29), NPC types (350), trades, units, speech, waypoints (274), mini-quest pools~~

## ~~1. Sounds~~ (done)

- [x] ~~Copy the sound files (190 .ogg) and convert `sounds.json` (101 events) to 1.21 format, with subtitles~~
- [x] ~~Register the sound events from data (`sound/LotrSounds`)~~
- [x] ~~NPC voices: orcs, elves, dwarves (say/hurt/death/attack/kill) on their NPC types~~
- [x] ~~Blocks: gates (wood and stone), plates, treasure piles~~
- [x] ~~Items: horns, smoking pipe, filling mugs and vessels, crossbow shoot/load~~
- [x] ~~Events: fast travel, pledge, unpledge, trade~~
- [x] ~~Ambience: wight moans in haunted biomes, wind high up, sea waves on coasts, LOTR's rain and thunder in
  Middle-earth~~
- [ ] Creature sounds (60): registered; they play once the creatures exist (sections 9, 11; table in
  `converter/sounds.py` CREATURE_SOUNDS)
- [ ] Ability sounds: Sauron's mace, Balrog whip, Gandalf's fireball, darts, orc bombs, structure spawner,
  wood-elf teleport, mallorn-ent; they play once those items and NPC abilities are ported
- [ ] Barrel and Ent jar filling; wraith rising from spawner chests; the elves' jazz easter egg (needs the
  machines, spawner chests and elf behaviour)

## 2. Small data fixes

- [ ] 43 unmapped trade entries (mostly `minecraft:coal` meta 32767 → `#minecraft:coals`)
- [ ] 13 unmapped structure blocks (`stone_slab#8/#11`)
- [x] ~~Skins for Gandalf, Saruman, Harad slave, scrap trader~~
- [ ] 2 town-wall generators that failed to export (Gondor, Easterling)
- [ ] 4 untranslated factions

## 3. Recipes still missing

- [ ] Unsmeltery: compute from the crafting recipes (the export came back empty)
- [ ] Hobbit oven and kebab stand
- [ ] 19 dyeing recipes (armor, hats, feathers, pouches, Harad robes); need dye support on those items

## 4. Enchantments ("modifiers")

- [ ] Export LOTR's 24 enchantment types, weights, costs and incompatibilities
- [ ] Generate 1.21 data-driven enchantments
- [ ] Anvil reforge and engrave costs

## 5. Achievements and titles

- [ ] Export LOTR's achievements (categories, icons, required factions)
- [ ] Generate advancements
- [ ] Titles and their unlock rules

## 6. Lore books

- [ ] Convert the 115 lore texts to data
- [ ] Add them to structure chest loot by category

## 7. Music

- [ ] Track list and region pools → music per biome

## 8. Shields and capes

- [ ] Export unlock rules; copy textures

## 9. Spawning: animals, creatures, invasions

- [ ] Export animal and creature spawn lists per biome
- [ ] Export invasion lists and conquest spawns
- [ ] Creature stats (wargs, trolls, spiders, ents, animals); creature models are separate (34)

## 10. Structures: villages and big builds

- [ ] Templates for the 207 village and fortress pieces
- [ ] Convert the 76 structure scans (`strscan`)
- [ ] Fixed-position builds (Isengard walls, Rammas Echor)

## 11. NPC data still skipped

- [x] ~~Non-person NPCs (39 types): half-trolls, trolls (incl. mountain, olog-hai, snow, Mirk, Utumno), wargs (11),
  spiders (3), ents, huorns, the Balrog, barrow-wight, skeleton wraiths, marsh-wraith, Gollum, tormented elf;
  creature bodies from LOTR's models (`converter/entity_models.py`, `client/CreatureModel`), LOTR's hitboxes,
  skins and voices~~
- [x] ~~Their animations: LOTR's setRotationAngles for each model, converted (`converter/model_anim.py`,
  `client/CreatureAnim`)~~
- [x] ~~14 non-person units in captains' pools~~ (now hired like the rest)
- [x] ~~20 non-person structure spawners~~ (now place their creatures)
- [ ] 47 mounted units (need mounts: wargs and horses to ride)
- [ ] Creature behaviour LOTR had: trolls turning to stone in daylight, spiders climbing and webbing, ents and
  huorns rooting, the Balrog's fire, wights' drain, Gollum, warg riders and bombardiers
- [ ] Animals (23 models: deer, bears, horses, birds...) are not NPCs; they come with section 9

## 12. Quests

- [ ] 32 bounty, retrieve and pickpocket quests
- [ ] 11 kill targets that point at animals (25 creature targets now resolve)
- [ ] Per-quest speech lines

## 13. Drinks

- [ ] Export drink strength, effects and vessels
- [ ] Unmerge the 138 drink variants into one item + strength/vessel components

## 14. Item-placed blocks that drop themselves

- [ ] Block items or block entities for the 26 mugs, chests and pots LOTR dropped as themselves

## 15. Calendar

- [ ] Shire Reckoning months, day lengths and holidays → `calendar.json`
