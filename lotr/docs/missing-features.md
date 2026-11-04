# LOTR 1.7.10 → port: what is missing (task list)

Written 2026-11-04 against `claude/happy-heisenberg-4yebld` (commit 8eed5eb7). Source of truth for "what LOTR has" is
the class list of `converter/libs/LOTRMod v36.15.jar` (3,469 classes) plus `converter/export/*.json`; "what we have"
is `lotr/src/main/java`, `converter/*.py` and `lotr/CLAUDE.md`.

## How to use this list (for the agent doing the work)

- Read `CLAUDE.md`, `lotr/CLAUDE.md` and `converter/README.md` first. Never edit `lotr/src/generated/`; change a
  generator and rerun it. Never paste decompiled LOTR code; read LOTR's *values* (fields, constants, lists) from the
  running game (exporter) or from class constants (`converter/classfile.py`), and write our own code.
- Tasks are ordered cheapest-and-most-automatable first. Each says **how** it gets done:
  - **GEN**: data is already in `converter/export/`; only a Python generator (and maybe a small Java loader) is needed.
  - **EXP**: add a section to the 1.7.10 exporter (`converter/src/.../lotrexport/`, reflection on public game state,
    run by the "Convert LOTR" workflow), then a generator.
  - **JAVA**: the data exists (or is trivial); the work is behaviour code in `lotr/`.
  - **DESIGN**: needs a decision from Aaron or a redesign around RingRealms/Create/TARDIS. Don't start these alone.
- One task = one commit. Add a line to `converter/report.json` for anything skipped, and a GameTest for anything with a
  done-when. The sandbox can't reach maven.neoforged.net: only CI verifies builds and the exporter.
- Every number goes in data files (reload listeners), never Java constants.

## Biggest gap in one sentence

Content was registered by *shape*, not by *function*: `content/items.json` classes everything as `item`, `sword`,
`armor` or a tool, so 29 spears, 63 daggers, 17 bows, 4 crossbows, 5 throwing axes, 43 mugs/drinks, 13 pikes,
20 hammers, 20 battleaxes, pipes, pouches, banners, horns, coins, beds, seeds and ~40 functional blocks (forges,
ovens, barrels, millstone, kegs, plates, mugs, chests, crops) exist but do nothing special. The export already
records each item's and block's LOTR class (`classes[0]`), so tagging them by function is pure **GEN** work.

---

## Tier 1 — generator only (data already exported)

1. **GEN — item kinds from LOTR class.** In `generate.py`, map `classes[0]` to a new `kind` in `content/items.json`:
   `LOTRItemSpear→spear`, `LOTRItemDagger→dagger`, `LOTRItemBow→bow`, `LOTRItemCrossbow→crossbow`,
   `LOTRItemThrowingAxe→throwing_axe`, `LOTRItemPike/Polearm/PolearmLong/Lance/Trident→polearm` (keep a `reach`
   field), `LOTRItemHammer→hammer`, `LOTRItemBattleaxe→battleaxe`, `LOTRItemMug/Vessel→drink`,
   `LOTRItemFood/Stew/Berry/Kebab→food`, `LOTRItemSeeds/GrapeSeeds/ItemSeedFood→seeds` (with the crop block),
   `LOTRItemPouch→pouch`, `LOTRItemBanner→banner`, `LOTRItemCoin→coin`, `LOTRItemBed→bed`, `LOTRItemMountArmor→
   horse_armor`, `LOTRItemDart/Blowgun/Sling/Pebble/Conker/Plate/FirePot→projectile`. Until the JAVA task for a kind
   lands, `LotrItems` must fall back to today's behaviour for unknown kinds. Done-when: counts in `report.json`
   match the class counts above.
2. **GEN — block kinds from LOTR class.** Same for `content/blocks.json`: `LOTRBlock*Crop`, `Grapevine*`, `Corn`,
   `Banana`, `Date`, `BerryBush`, `FruitLeaves`→`crop`/`bush`; `LOTRBlockHobbitOven/AlloyForge/DwarvenForge/
   ElvenForge/OrcForge`→`furnace`; `LOTRBlockBarrel`→`barrel`; `LOTRBlockMillstone`→`millstone`;
   `LOTRBlockUnsmeltery`→`unsmeltery`; `LOTRBlockChest/SpawnerChest`→`chest`; `LOTRBlockMug/ClayMug/Goblet*/
   AleHorn/SkullCup/WineGlass/GlassBottle`→`mug`; `LOTRBlockPlate`→`plate`; `LOTRBlockPlaceableFood`→`cake`;
   `LOTRBlockBed`→`bed`; `LOTRBlockOre*`→`ore` (with xp + drop); `LOTRBlockFallenLeaves`, `LOTRBlockVine`, `Rope`,
   `HithlainRope`, `OrcChain`→`climbable`; `LOTRBlockQuagmire`→`quagmire`; `LOTRBlockMud/MudFarmland`→`farmland`;
   `LOTRBlockTreasurePile`→`layered`; `LOTRBlockChandelier/DoubleTorch/MallornTorch/ElvenTorch/MorgulTorch`→
   `torch`; `LOTRBlockStalactite`→`stalactite`; `LOTRBlockWeaponRack/ArmorStand`→`display`.
3. **GEN — loot for blocks only placed by an item** (known gap): crops, beds, plates, mugs, portals drop nothing.
   Generate loot tables from the item↔block pairs (`itemClass`, `blocks_named_from_id` in the report).
4. **GEN — leaf and grass tint.** Export has `blockColor`/`renderPass` per block and biome `biomeColors`; emit
   `tintindex` in models and a `block_colors.json` (biome grass/foliage colour or fixed) for a tiny client colour
   handler. Fixes the "leaves and grass are untinted" gap.
5. **GEN — biome decoration counts.** `world.json` decorator already has `grassPerChunk`, `doubleGrassPerChunk`,
   `doubleFlowersPerChunk`, `mushroomsPerChunk`, `canePerChunk`, `cactiPerChunk`, `waterlilyPerChunk`,
   `deadBushPerChunk`, `cornPerChunk`, `melonPerChunk`, `logsPerChunk`, `vinesPerChunk`, `quagmirePerChunk`,
   `sandPerChunk`, `clayPerChunk`, `generateAthelas`, `generateCobwebs`, `enableFern`, `whiteSand`,
   `treeClusterSize/Chance`. `world.py` only uses trees, flowers and reeds. Emit vanilla `placed_feature`s per biome
   for the rest. Done-when: CI world check counts grass in a Shire chunk.
6. **GEN — biome fog/water/sky colours.** `biomeColors` (foggy, custom water) and `fields.waterColorMultiplier`
   → biome `effects` in `world.py`.
7. **GEN — sounds.** The jar has `assets/lotr/sounds.json` + 229 `.ogg` files; none are copied. Copy them in
   `assets.py`, translate `sounds.json` to 1.21 format (`lotr:` → `lotr_port:`), and write the sound event id list so
   a `LotrSounds` registry can register them from data. Then point blocks' `sound` at LOTR sound types where LOTR used
   custom ones.
8. **GEN — music.** `assets/lotr/music/` (track list + region pools, read by `LOTRMusic`/`LOTRRegionTrackPool`) →
   a `music/<region>.json` table keyed by biome tag; biomes get `music` in `effects`. (LOTR ships the track index,
   not all audio; copy what is there.)
9. **GEN — lore books.** 115 files in `assets/lotr/lore/` (`LOTRLore`): copy into `data/lotr_port/lore/` as JSON
   (title, author, pages, categories) and add written-book loot entries to structure chests by category. The loot
   hook is the only JAVA part.
10. **GEN — NPC name banks are done; titles are not.** `LOTRTitle` values are lang keys + alignment/achievement
    requirements: read with `classfile.py` or a 20-line **EXP** section → `data/lotr_port/title/*.json`.
11. **GEN — mounted units.** `npc_mounted_units_skipped` (46 entries, e.g. `ROHIRRIM_MARSHAL: Rohirrim_Horse`) is
    already in the export; write the unit pool entry with a `mount` field now so it's ready when mounts land (task 27).
12. **GEN — unmapped trades/quest targets/structure blocks.** Fix the mapping tables behind `npc_trades_unmapped` (43,
    all `minecraft:coal` meta 32767 → `#minecraft:coals`), `structure_blocks_unmapped` (`stone_slab#8/#11` = double
    smooth stone / top slabs) and `quest_targets_unmapped` (36: map kill targets to entity type tags so they work once
    creatures exist).
13. **GEN — village and town-wall parts already exported?** `structures_in_no_biome` lists 207 generators (village
    houses, fortress pieces). They're in `structures.json.gz`; generate their templates now under
    `structure/village/<culture>/<piece>/` so the village task (29) only needs the layout. Fix the 2
    `structure_export_errors` (`LOTRWorldGenGondorTownWall`/`EasterlingTownWall` have no `(boolean)` constructor:
    try other constructors in `StructureExporter`).
14. **GEN — structure scans.** 76 `assets/lotr/strscan/*` files (`LOTRStructureScan`, used by big fixed builds) can
    be parsed straight into `.nbt` by a Python script with no game needed. Check which already arrive via the
    structure export and convert the rest. Also `LOTRFixedStructures`, `LOTRWorldGenIsengardWalls`,
    `LOTRWorldGenRammasEchor` and `LOTRMountains` (fixed-position builds and mountains placed at map coordinates).

## Tier 2 — extend the exporter (one reflection section each, then a generator)

Each is "read the LOTR registry/list from the running game, dump JSON". Pattern: copy `QuestExporter`.

15. **EXP — station recipes.** `recipes.json` has crafting tables and smelting only. Add: `LOTRBrewingRecipes`
    (barrel, ingredients → drink, brew time), `LOTRMillstoneRecipes`, alloy-forge recipes (each forge TE class has
    them as methods: call `getSmeltingResult`/`getAlloySmeltingResult` over every item in the registry and record
    hits), `LOTREntJarRecipes`, unsmeltery (`uncraftableUnsmeltingRecipes` is listed as a station already; export the
    full reverse table), kebab stand, hobbit oven (food only). Generate 1.21 recipe types for each.
16. **EXP — drink stats.** For every `LOTRItemMug`: heal/food amount, alcoholicity, potion effects per strength,
    allowed vessels (`LOTRItemMug.Vessel` enum: mug, wood cup, goblets, skull, glass, bottle, horns...). Replaces the
    138 `merged_item_metas` mug variants with one item + `strength`/`vessel` components.
17. **EXP — weapon stats.** Per weapon item: LOTR's melee reach factor, attack speed factor, knockback, spear/throwing
    damage, bow draw speed and damage factor, crossbow reload, dagger poison flag. (`LOTRWeaponStats` keeps these in
    maps keyed by item/class; read them through its public static getters for every item.)
18. **EXP — LOTR enchantments/modifiers.** `LOTREnchantment` registry (24 classes: keen, sharp, swift, mithril
    protection, ranged...), what applies to what, weights, cost, incompatibilities, and the anvil reforge/engrave
    costs. → 1.21 data-driven enchantments (`data/lotr_port/enchantment/*.json`). Most map onto vanilla enchantment
    effect components with zero Java.
19. **EXP — achievements.** `LOTRAchievement` (categories per region, icon item, required faction, title unlocks) →
    1.21 advancements, generated. Triggers: biome visits (`location`), kills (`player_killed_entity`), crafting
    (`inventory_changed`), alignment ranks (custom trigger, small JAVA).
20. **EXP — shields and capes.** `LOTRShields`/`LOTRCapes` enums: texture, faction, alignment/achievement needed.
    Textures already in `assets/lotr/shield|cape`. Output data + copy textures; JAVA renders them later.
21. **EXP — mini-quest speech keys** (already named in `lotr/CLAUDE.md`): record each quest's `speechBankStart/
    Progress/Complete` so `speech/miniquest/<group>/<quest>_*` can be wired.
22. **EXP — bounty / retrieve / pickpocket / welcome quests.** `quests_not_ported` has 32. Export their factory
    params (reward, alignment cut, target faction, item to retrieve) — data is the same shape as collect/kill.
23. **EXP — creature spawn lists and invasion lists.** `LOTRBiomeSpawnList` animal/creature entries,
    `LOTRBiomeInvasionSpawns` (invasion factions per biome, chance), `LOTRInvasions` (each invasion's units and
    weights) and conquest spawn lists. Today only people spawn.
24. **EXP — creature stats.** `npc_units_not_people` / `structure_spawners_not_people` / `npc_models_not_ported` (34
    models): wargs, spiders, trolls, olog-hai, ents, huorns, barrow-wights, marsh wraiths, balrog, gollum and all 33
    `entity/animal` classes. Export attributes, size, drops, faction, rideable flag, tameable item. (Models are the
    expensive JAVA part; see 26.)
25. **EXP — LOTR date & holidays.** `LOTRDate` (Shire Reckoning months, day lengths, holidays) → `calendar.json`.
    Cheap and used by the map screen and speech.

## Tier 3 — behaviour code on top of generated data (JAVA, mechanical)

Each task picks up one `kind` from Tier 1 and makes it work; stats come from Tier 2.

26. **Creature models.** Port the 34 `LOTRModel*` boxes as `LayerDefinition`s. Mechanical but long: do it with a
    script that reads cube/rotation constants from the model classes' constructors (`classfile.py` can read the
    `addBox` call constants) and writes Java or entity-model JSON. Start with warg, spider, troll, horse.
27. **Mounts.** Horses (LOTR stats per breed), ponies, wargs, elk, boars, camels, rhinos, zebras, giraffes; NPCs
    riding them (task 11 data), horse armour items (`horse_armor` kind).
28. **Ranged weapons.** `bow`, `crossbow`, `throwing_axe`, `spear` (throw + melee), `projectile` kinds with entities
    (`LOTREntitySpear`, `ThrowingAxe`, `CrossbowBolt`, `Dart`, `Plate`, `Pebble`, `FirePot`, `MysteryWeb`) and the 14
    dispenser behaviours. NPC archers use them.
29. **Melee reach/speed.** `polearm`, `dagger`, `hammer`, `battleaxe`: reach and speed via attribute modifiers from
    task 17 (no Java beyond the item kind). Poisoned daggers + `LOTRRecipePoisonWeapon` as a special recipe.
30. **Drinks and drunkenness.** `drink` kind with strength/vessel components (task 16), placeable mugs (`mug` block
    kind, holds the item), nausea at high alcohol, `LOTRDrunkenSpeech` scrambling chat (word table from constants).
31. **Station blocks.** `furnace` kind (faction forges: alloy recipes; hobbit oven: 9-slot food oven), `barrel`
    (brewing, task 15), `millstone`, `unsmeltery`, `kebab_stand`, `chest` (LOTR chests with LOTR textures), `plate`
    (holds food), `bookshelf_storage`, `armor_stand`, `weapon_rack`. Menus can copy vanilla furnace/brewing layouts
    with LOTR's GUI textures from `assets/lotr/gui`.
32. **Crops and farming.** `crop` kind growth stages from the block's `variants`; seeds plant; grapevines on
    trellises; fruit leaves drop fruit; mud farmland. Hired farmers later.
33. **Pouches.** `pouch` kind (small/medium/large = 9/18/27 slots), dye recipe (`LOTRRecipesPouch`), auto-pick-up and
    restock from a chest (`LOTRContainerChestWithPouch`).
34. **Coin exchange.** `LOTRContainerCoinExchange`: split/merge silver coin denominations (`coin` kind; `Coins`
    already exists).
35. **Pipes.** `smoking_pipe` + pipeweed: smoke ring projectile, colour by dye (`LOTRRecipeHobbitPipe`).
36. **Dye/special recipes.** The 44 `special_recipes`: armor dyes, feather dye, leather hat dye/feather, party hat,
    Harad robe/turban, banner patterns, treasure-pile, poison drinks. Each is a small `CustomRecipe`.
37. **Ores and gems.** `ore` kind: xp, silk touch, fortune; Mithril, Naurite, Quendite, Gulduril, Morgul iron,
    Edhelvir; glowing Quendite/Gulduril (light + particles from `LOTRTileEntityGulduril`).
38. **Quagmire, dead marshes, webs.** Quagmire slows and sinks, marsh lights (`blocks_without_texture`: give them a
    particle-only renderer), marsh faces (`LOTREntityDeadMarshFace`), Ungoliant webs.
39. **Road bridges** (known gap in `Roads`/`RoadFeature`): LOTR places bridges where roads cross rivers.
40. **Structure quirks.** No structures within 32 blocks of the x/z axes; biome variants that disable structures;
    respawner blocks (`LOTREntityNPCRespawner`, `LOTRTileEntityMobSpawner`): spawners re-place NPCs over time.
41. **Faction data sync to clients.** Needed for quest-mark hiding, alignment HUD, factions screen.
42. **Alignment HUD + bonus popups.** `LOTRAlignmentTicker`, `LOTREntityAlignmentBonus` (the floating "+5" over kills).
43. **Remaining unique NPCs.** `npcs_without_skins` (Gandalf, Saruman, Harad slave, scrap trader): skins exist under
    other names in `assets/lotr/mob`; map them in `npcs.py`. Plus female NPCs and trader outfits (known gap).

## Tier 4 — bigger systems (JAVA + DESIGN; check with Aaron before starting)

44. **Villages and towns** (`LOTRVillageGen*`, 13 cultures; parts from task 13). LOTR lays them out in code; we need
    our own jigsaw/layout. Overlaps with RingRealms colonies and AI rulers.
45. **Invasions** (`LOTREntityInvasionSpawner`, horns of conquest) and **conquest** (`LOTRConquestGrid`/`Zone`,
    `/conquest`). Probably redesigned around AI rulers' wars.
46. **Banners and land protection** (`LOTRBannerProtection`, whitelist GUI `LOTRGuiBanner`). May become colony
    borders.
47. **Fellowships** (`fellowship/`, 6 classes: invites, shared waypoints, fellowship chat, `/fellowship`).
48. **Hired-unit depth.** Hired-unit screens (`LOTRGuiHiredWarrior/Farmer`, inventories, levelling, XP), squadrons
    (`LOTRSquadrons`), horn of command (`LOTRGuiHornSelect`), command sword marker, mercenaries
    (`LOTRGuiMercenaryHire`), unit trading screen (`LOTRGuiUnitTrade`, today a chat list).
49. **NPC families.** Marriage, rings, children (hobbits, dwarves, men) – `LOTRFamilyInfo`. Likely folds into
    RingRealms person records.
50. **Travelling traders, Gollum, bandits, scrap traders** (`LOTRTravellingTraderSpawner`, `LOTRGollumSpawner`,
    `LOTREntityBandit` steals items, `LOTRContainerGollum`).
51. **Portals and Utumno.** Ring portal into Middle-earth (`LOTREntityPortal`), elven and Morgul portals, Utumno
    (entrance, three layers, its own caves `LOTRMapGenCavesUtumno`, return portal, Utumno key, the Utumno block
    set). Overlaps with "Middle-earth is a world type / dimension" decision.
52. **Underground.** Dwarven mines (`mapgen/dwarvenmine`, 7 classes), Tauredain pyramids (`mapgen/tpyr`), Moria-style
    caves, LOTR's own caves/ravines (`LOTRMapGenCaves`). Can be exported as structures like task 13 (the generators
    take a seeded random): if so, this becomes **EXP**.
53. **Sky and weather.** `LOTRSkyRenderer` (sun/moon/stars), `LOTRRenderNorthernLights`, `LOTRCloudRenderer`,
    `LOTRWeatherRenderer` (sandstorms, ash in Mordor), `LOTRAmbience`. Pure client work, textures in `assets/lotr/sky`
    and `weather`.
54. **Custom waypoints** (`LOTRCustomWaypoint`, sharing via fellowships), map conquest overlay, factions screen with
    faction map (`LOTRGuiFactions`), Red Book menu tabs (achievements, titles, shields, options).
55. **Bosses and legendary items.** Mountain-troll chieftain (`LOTRGuiMobSpawner`/boss trophy), Balrog whip, Sauron's
    mace, Gandalf's staffs, Glamdring/Sting/Orcrist glowing near orcs (`LOTRItemSting`), Andúril/Narsil, Ent draught,
    Morgul draught, Tauredain cure, branding iron, Dale crackers, orc bombs (block + entity + dispenser), troll
    statues and totems, rugs.
56. **Commands parity.** LOTR's 25 commands: `/alignment`, `/alignmentSee`, `/conquest`, `/invasion`, `/date`,
    `/fastTravelClock`, `/waypointCooldown`, `/pledgeCooldown`, `/spawnDamping`, `/strScan`, `/structureTimelapse`,
    `/allowStructures`/`banStructures`, `/enableAlignmentZones`, `/factionRelations`, `/lotrAchievement`,
    `/lotrTime`, `/summon` (LOTR override). Many are a few lines once the system exists; list in `/lotr help`.
57. **Spawn damping & alignment zones** (`LOTRSpawnDamping`, `LOTRControlZone` already exported in factions):
    alignment only gained inside control zones.

## Suggested order for a low-effort agent

1 → 2 → 3 → 12 → 7 → 4 → 5 → 6 → 13 → 14 → 9 → 11 → then Tier 2 in order 15, 16, 17, 21, 22, 18, 19, 23, 24, 25,
20, 10 → then Tier 3 by the kind it unlocks (29 and 28 first: weapons are most visible). Stop before Tier 4.
