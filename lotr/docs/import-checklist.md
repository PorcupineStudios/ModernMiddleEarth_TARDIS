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
- [x] ~~Ent jar filling (bowls, buckets, rain; Fangorn brewing), 2026-11-10~~
- [ ] Barrel filling sound; wraith rising from spawner chests; the elves' jazz easter egg (needs the
  machines, spawner chests and elf behaviour)

## ~~2. Small data fixes~~ (done)

- [x] ~~43 unmapped trade entries (`minecraft:coal` meta 32767 → coal)~~
- [x] ~~13 unmapped structure blocks (`stone_slab#8/#11`, top-half slabs)~~
- [x] ~~Skins for Gandalf, Saruman, Harad slave, scrap trader~~
- [x] ~~4 untranslated factions~~
- [x] ~~The Gondor and Easterling town walls: they only build as part of a village, and come with the exported
  villages (section 10)~~

## ~~3. Recipes still missing~~ (done)

- [x] ~~Unsmeltery: 436 recipes computed from the crafting recipes (gear with durability → its most used material,
  `recipe/unsmelting/<gear>.json`; LOTR's run-time yield 0.8 × durability left × 0.7–1.0 in
  `content/unsmelting.json`, read from `LOTRTileEntityUnsmeltery` bytecode)~~
- [x] ~~Hobbit oven (21: every furnace food, as `LOTRTileEntityHobbitOven`) and kebab stand (11: furnace meats →
  kebab, as `LOTRTileEntityKebabStand.isMeat`); JEI categories for both~~
- [x] ~~Dyeing: party hats, pouches, dyed feathers, leather hats and Harad robes are in `#minecraft:dyeable`, so
  vanilla's dye recipe colours them (`dyed_color`); item tint and dyeable armour layers~~
- **Skipped:** the machines' blocks still don't run their recipes (forge, barrel, millstone, unsmeltery, oven,
  kebab stand); recipes load and show in JEI. Treasure piles, poisoned drinks, pipe colours, banners and turban
  ornaments (the other special recipes) need those items' own behaviour.

## ~~4. Enchantments ("modifiers")~~ (done)

- [x] ~~Exported LOTR's 59 modifiers by reflection (`ReflectionDump` → `export/extra.json.gz`)~~
- [x] ~~57 data-driven enchantments (`enchantment/<id>.json`): damage, bane (by NPC type), durability, melee speed
  and reach, knockback, tool speed, protection (all, fire, fall, ranged, mithril), ranged damage and knockback, fire,
  chill; exclusive sets per kind; LOTR's weights in `#lotr_port:modifier/random` and `content/modifiers.json`~~
- [x] ~~Every LOTR weapon, tool and armour piece in vanilla's enchantable tags (they had none, so not even vanilla
  enchantments applied)~~
- **Skipped:** silk touch, looting and headhunting (vanilla ties silk touch and looting to its own enchantment ids
  inside loot tables; headhunting needs a loot modifier). LOTR's random modifiers on crafted and forged items and the
  anvil's reforge/engrave costs need the anvil screen; `modifiers.json` has the numbers for it. Fire uses vanilla
  Fire Aspect I's burn (LOTR's is set in code, not exported).

## ~~5. Achievements and titles~~ (done)

- [x] ~~492 achievements as advancements, one tab per LOTR category (`advancement/<category>/<name>`), LOTR's
  icons, names and descriptions in all 22 languages~~
- [x] ~~Granted by code (`progress/Achievements.grant`): entering Middle-earth and each biome's achievement (152)~~
- [x] ~~337 titles with their unlock rules (`content/titles.json`): starter, alignment, achievement; `/lotr_title`
  picks one and it shows before the name~~
- **Skipped:** the other achievements' triggers (kills, crafting, trading, drinking...) are LOTR code in many
  places; they exist as advancements and are granted as those systems get hooks. Rank achievements and
  player-exclusive titles (LOTR gave those to named players; operators only here).

## ~~6. Lore books~~ (done)

- [x] ~~114 lore texts to `data/lotr_port/lore/<id>.json` (`converter/lore.py`)~~
- [x] ~~64 lore books placed where LOTR's structures put them (the export's empty written books), by the
  structure's lore category, names and numbers filled in from LOTR's name banks~~
- **Skipped:** each chest's book is fixed at generation (LOTR rolled it when the structure was built); lore
  rewards for mini-quests.

## ~~7. Music~~ (done)

- [x] ~~LOTR ships no tracks (players add music packs). Each biome's LOTR music region (168 biomes,
  `content/music.json`) is its biome music, `lotr_port:music.<region>`, an empty sound event a resource pack fills~~
- **Skipped:** LOTR's own music-pack format (`music.json` inside a zip) and its "now playing" overlay; packs use
  vanilla's sounds.json instead.

## 8. Shields and capes (partly)

- [x] ~~64 shields and 27 capes with textures and unlock rules (`content/shields.json`, `capes.json`)~~
- [x] ~~`/lotr_shield` and `/lotr_cape` choose one (alignment shields at +1000 with their faction)~~
- **Skipped:** drawing the shield and cape on the player (needs a synced payload and LOTR's shield model);
  capes on NPCs. Achievable and exclusive shields are operator-only (LOTR gave them out per player).

## ~~9. Spawning: animals, creatures, invasions~~ (done)

- [x] ~~Animals: 18 LOTR animals (deer, bears, birds, rabbits, camels, giraffes, lions, rhinos, elk, aurochs,
  flamingos, swans, butterflies, midges, termites, scorpions, crocodiles, dik-diks, gemsbok, fish) are creature NPCs
  on their own converted models and animations; vanilla animals spawn from LOTR's lists in the biome JSON; LOTR's
  per-biome animal lists in `npc_animals/` (128 biomes), spawned by `NpcSpawner`~~
- [x] ~~Animal behaviour (`AnimalBehaviour`): most panic when hurt; bears, lions, rhinos, aurochs and crocodiles
  fight back~~
- [x] ~~Invasions: 45 invasions (`invasion/<id>.json`, faction and mobs) and 95 biomes' chances per second
  (`invasion_spawns/biomes.json`), run by `npc/InvasionSpawner`~~
- **Skipped:** boars, horses, zebras and Shire ponies are vanilla pigs, horses and donkeys (LOTR drew them on
  vanilla's models, which the converter can't replay). Invasion size (6–12) is the port's, in each invasion file
  (LOTR's invasion banner and waves aren't ported). Conquest spawns (they need LOTR's conquest system).

## ~~10. Structures: villages and big builds~~ (done)

- [x] ~~Villages: the exporter grows each of LOTR's 11 village generators whole, twice (`StructureExporter.villages`:
  Bree, Dúnedain, Gondor towns with their walls, Rohan, Rhûn, Southron, Umbar, Gulf, Harnedor, nomads, Taurëdain),
  placed as `village_<kind>` in their biomes at LOTR's grid rate (1 in gridScale² / spawnChance chunks)~~
- [x] ~~The 75 structure scans (`strscan`): LOTR's generators read them while they build, so the structure export
  already holds them (barns, gatehouses, forts...)~~
- **Skipped:** each village is one fixed layout per seed (LOTR laid them out fresh each time); fixed-position builds (Isengard walls, Rammas Echor, Minas Tirith and the other fixed
  settlements at waypoints): they need placing at map coordinates.

## 11. NPC data still skipped

- [x] ~~Non-person NPCs (39 types): half-trolls, trolls (incl. mountain, olog-hai, snow, Mirk, Utumno), wargs (11),
  spiders (3), ents, huorns, the Balrog, barrow-wight, skeleton wraiths, marsh-wraith, Gollum, tormented elf;
  creature bodies from LOTR's models (`converter/entity_models.py`, `client/CreatureModel`), LOTR's hitboxes,
  skins and voices~~
- [x] ~~Their animations: LOTR's setRotationAngles for each model, converted (`converter/model_anim.py`,
  `client/CreatureAnim`)~~
- [x] ~~14 non-person units in captains' pools~~ (now hired like the rest)
- [x] ~~20 non-person structure spawners~~ (now place their creatures)
- [x] ~~47 mounted units: riders come on their mount (`Hiring.mount`): wargs are hired with them; horses (tamed and
  saddled), boars (pigs) and camels are vanilla stand-ins~~
- [x] ~~Creature behaviour (npc/behaviour, numbers from LOTR's classes): trolls burn and turn to stone in daylight
  (fleeing to shade), sneeze when tickled, throw rocks/snow; spiders' sizes, venoms and climbing; ents blink,
  heal saplings, are weak to axes and fire; huorns sleep as trees; the Balrog's flames, regeneration and charge;
  wights' curses; marsh-wraiths fade, hover and cast; Gollum flees, is tamed by 20 fish, sits and follows; warg
  tails droop; half-trolls' horns and mohawks~~
- [x] ~~Animations 1:1: LOTR's render() and renderers converted too (every draw with its transforms, the
  renderer's size/lean/fade, troll outfits, armour, weapons and held rocks)~~
- [x] ~~Animals~~ (section 9)
- **Skipped:** NPC riders don't steer vanilla horses (vanilla horses only take orders from players; wargs follow
  their rider); warg bombardiers' bombs, spider webs (mystery web), Gollum's fishing and inventory, the troll
  chieftain's boss fight: each needs its own item or boss bar.

## ~~12. Quests~~ (done)

- [x] ~~Pickpocket quests (2): sneak up behind a member of the faction and use them (`Quests.pickpocket`)~~
- [x] ~~Kill targets that are animals: LOTR animals are NPC types now; horses count vanilla horses~~
- [x] ~~LOTR's lines for each quest (start, progress, complete) from `speech/miniquest`, with `#` the player and `$`
  the target~~
- **Skipped:** the 30 bounty quests: LOTR's bounties are on players with enough enemy kills (LOTRFactionBounties),
  a multiplayer system that isn't ported.

## ~~13. Drinks~~ (done)

- [x] ~~Drink stats exported (heal, saturation, alcohol, effects, damage, cures) for 46 drinks, with LOTR's 5
  strengths and 12 vessels (`content/drinks.json`)~~
- [x] ~~One item per drink (`content/LotrDrinkItem`) with `drink_strength` and `vessel` components; drinking feeds,
  applies effects scaled by strength, makes you drunk (nausea), hands back the vessel~~
- **Skipped:** LOTR's drunkenness levels and alcohol tolerance (nausea per alcohol × strength is the port's
  stand-in, tunable in `drinks.json`); barrels ageing drinks into stronger ones (needs the barrel block).

## ~~14. Item-placed blocks that drop themselves~~ (done)

- [x] ~~Placed mugs, goblets, cups, horns, glasses, bottles and pots drop the empty vessel; grapevines their seeds;
  the storage bookshelf a bookshelf (15)~~
- **Skipped (drop nothing, as in survival LOTR):** portals (elven, Morgul, Utumno), Utumno bricks, Rhûn fire,
  mechanised rails, goran/cargoran (11).

## ~~15. Calendar~~ (done)

- [x] ~~Shire Reckoning months, weekdays and the Third Age year in `content/calendar.json`; `/lotr_date`
  (`progress/Calendar`). The port keeps vanilla's day length; LOTR's (48000 ticks) is kept as `lotr_ticks_per_day`~~
- **Skipped:** seasons' grass colours and holidays' events.
