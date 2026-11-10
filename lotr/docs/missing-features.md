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

## 1.7.10 → 1.21.1: where the game itself changed under LOTR

LOTR was built for a game without the 1.9 combat update, data components, the flattening or data-driven
enchantments. Copying its numbers one-to-one gives wrong results in these places. **Done** means handled on this
branch, and **Decision** means it needs Aaron to choose.

### Combat

| 1.7.10 LOTR | 1.21.1 | What we do | Status |
|---|---|---|---|
| No attack cooldown. LOTR timed melee itself (`LOTRWeaponStats`): a player swings every 15 ticks ÷ class speed factor. Dagger 1.5 → 10 t, spear 0.833 → 18 t, polearm and hammer 0.667 → 22 t, battleaxe 0.75 → 20 t, long polearm, pike and lance 0.5 → 30 t | Attack speed attribute (attacks per second), and a hit before the cooldown ends is weaker. Vanilla swords are 1.6 | `attack_speed = 20 / LOTR ticks`: sword 1.33, dagger 2.0, spear 1.11, polearm and hammer 0.91, battleaxe 1.0, pike and lance 0.67. Speeds are read from `LOTRWeaponStats` by `generate.py` and from the export once CI reruns it (`Exporter.weaponStats`) | Done |
| Weapon damage (`generic.attackDamage` modifier) used fractions: daggers 3.5, hammers 8.5 | Same base +1 hand damage. 1.9 lowered vanilla swords by 1 (iron sword 7 → 6) | Keep LOTR's damage as is, because LOTR's NPC health is balanced around it. **Bug fixed:** `SwordItem.createAttributes(tier, (int) damage, …)` dropped every .5, so 3.5 became 3. Floats are kept now | Done |
| Reach = 3 blocks × factor (dagger 0.75, spear and polearm 1.5, pike, long polearm and lance 2.0) | `player.entity_interaction_range` attribute (base 3) | Mainhand modifier `+3 × (factor − 1)`: dagger −0.75, spear +1.5, pike +3 | Done |
| Extra knockback (hammer, lance +1) | `attack_knockback` attribute | +1 modifier | Done |
| No sweep attack | Swords sweep (1.9) | Swords sweep. Battleaxes sweep harder (+0.5 `sweeping_damage_ratio`, Aaron 2026-11-04). Daggers, spears, polearms and hammers hit one target | Done |
| NPCs swing every 20 ticks ÷ speed factor | `MeleeAttackGoal` always waits 20 ticks | `LotrNpc.WeaponMeleeGoal` uses the held weapon's `mob_ticks` | Done |
| NPC reach scaled with the weapon | Mob melee range comes from the hitbox (`isWithinMeleeAttackRange`), not an attribute | Pike NPCs still reach like sword NPCs | Todo |
| Crits whenever falling | Crits need a full-charge hit (1.9) | Nothing to port. Fast daggers crit easily, slow pikes rarely | Note |
| No off-hand, no shield | Off-hand and vanilla shields (1.9) block LOTR NPCs' melee and arrows | Kept: vanilla shields block LOTR NPCs (Aaron 2026-11-04). LOTR's "shields" are cosmetic back-banners | Done |
| Armor: flat 4% per point, capped at 80% | Reduction falls as hits get bigger, offset by toughness (1.9) | LOTR armor keeps its defense points with toughness 0, so trolls and 9–11 damage hammers pierce more than in 1.7.10. `armor.toughness` / `knockback_resistance` are data fields. Mithril gets toughness 3 (like netherite), dwarven and blue dwarven 2 (like diamond) (Aaron 2026-11-04, `ARMOR_TOUGHNESS` in `generate.py`) | Done |
| Bows: `LOTRItemBow` draw time (`bowPullTime`) and `arrowDamageFactor` | `BowItem` is fixed at 20 ticks | `LotrBowItem` scales held ticks to the bow's draw time and multiplies arrow damage (`customArrow`). Pull frames use LOTR's large `_pull_0..2` icons | Done (real numbers arrive with the next export; until then 20 t and ×1.0, see `report.json ranged_stats_defaulted`) |
| Crossbow = a bow subclass that fires bolts | Vanilla crossbow (load, then fire, 1.14) | `LotrCrossbowItem` extends `CrossbowItem`, takes only `#lotr_port:crossbow_bolts`, and scales bolt damage. Load time stays 1.25 s because `getChargeDuration` is static in 1.21.1. Bolts fly as vanilla arrows. Multishot and Piercing would now apply | Done (bolt entity look: Todo) |
| Poisoned daggers, bolts and darts: Poison I for n…2n−1 s, n = 1 + 2 × difficulty | Tipped arrows carry a fixed potion | Daggers apply LOTR's formula on hit (`LotrWeaponItem.applyPoison`). Poisoned arrows and bolts carry a fixed 7 s Poison (the normal-difficulty middle) | Done |
| LOTR trident = melee polearm | Vanilla trident is thrown (1.13) | Kept as melee polearm | Note |
| Spears: melee and thrown | No vanilla spear in 1.21.1 (it arrives in 1.21.11) | Melee done. Throwing needs a projectile entity (task 28) | Todo |
| LOTR enchantments ("modifiers") replace vanilla's | Enchantments are data (1.21) | Task 18 | Todo |

### Rendering and items

| 1.7.10 LOTR | 1.21.1 | What we do | Status |
|---|---|---|---|
| `LOTRRenderLargeItem`: 179 `large/` icons drawn 2× and 25 `large2/` icons 3× in the hand, scaled about the centre | Item models with display transforms | `separate_transforms` model: small icon in GUI, ground and frames, large icon on `item/large_2x` / `large_3x` (vanilla handheld with x and y scaled). 140 items | Done (in-hand offsets are untested in a client) |
| `LOTRArmorModels`: 34 armor pieces on their own ModelBiped (crested and winged helmets, Morgul spikes, lion head, turbans, robes, swan and Gulf chestplates, hats) | `IClientItemExtensions.getHumanoidArmorModel` | `converter/armor_models.py` replays each model's constructor bytecode into `armor_models.json`. `client/ArmorModels` builds a `HumanoidModel` from it. All 34 convert, and 31 distinct models result | Done |
| Animated armor parts (swan wings, robes following the legs) and items drawn on armor (leather hat feather) | `setupModelAnimations` hook | Resting pose only, so the swan chestplate's wings stay spread | Todo |
| Dyed hats via `getColorFromItemStack` | `dyed_color` component | Hats tinted their undyed LOTR colour. Dyeing is task 36 | Todo |
| Item damage values (metadata) for variants | Flattening (1.13), data components (1.20.5) | Separate ids. 138 drink metas merged (task 16) | Partly |
| Horse armor in metadata, many mount types | Body slot / `AnimalArmorItem` | Task 27 | Todo |
| `IItemRenderer` for bows (`LOTRRenderBow`), crossbows, elven blades glowing near orcs, skull staff, horns | Item model overrides and properties | Bows and crossbows done (pull, charged). Glowing blades: Todo | Partly |

### World and data (already handled by earlier phases, listed so nobody redoes them)

- World height is 0–256 in 1.7.10 and −64–320 in 1.21. Middle-earth heights are tuned in `converter/world.py`.
- LOTR's `WorldProvider` dimension is now a datapack dimension and world preset. Its biome IDs are now one data biome
  per LOTR biome (`MapBiomeSource`).
- TileEntities became BlockEntities with menus. GUIs are task 31.
- Enchantments, recipes, loot and tags are data.
- Sounds need a registry of sound events (task 7).
- 1.7.10 saves are not converted (repo rule).

---

## Tier 1 — generator only (data already exported)

1. **GEN — item kinds from LOTR class.** *Partly done:* melee weapons carry a `weapon` block (type, speed, reach,
   knockback, sweep), and `bow`, `crossbow`, `bolt` and `arrow` are kinds. The rest below is still to do. In `generate.py`, map `classes[0]` to a new `kind` in `content/items.json`:
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
3. **Done (2026-11-06)** for crops, beds, plates and the like: the exporter records LOTR's drops and 26 blocks
   drop what LOTR's did. 11 drop nothing in LOTR either (placed pies are eaten in place, portals). Left: the 26
   vessels, chests and pots that LOTR dropped as themselves (their contents came from the tile entity); they need
   their block items or block entities. Original task: **GEN — loot for blocks only placed by an item** (known gap): crops, beds, plates, mugs, portals drop nothing.
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

15. **Mostly done (2026-11-06):** barrel 29, millstone 35, Ent jar 7, and the four forges' alloys and smelting
    are now `lotr_port:<machine>` recipes (`MachineRecipe`), shown in JEI (`compat/jei`). The barrel's run in the keg (`drink/KegBlockEntity`, PC work merged 2026-11-09). Poisoning a weapon is a
    crafting recipe. Left: the unsmeltery export came back empty (its result needs a world, so compute it from the
    crafting recipes in the generator instead), the hobbit oven and kebab stand, LOTR's dye recipes (task 36), and
    the machines' own blocks (task 31). Original task: **EXP — station recipes.** `recipes.json` has crafting tables and smelting only. Add: `LOTRBrewingRecipes`
    (barrel, ingredients → drink, brew time), `LOTRMillstoneRecipes`, alloy-forge recipes (each forge TE class has
    them as methods: call `getSmeltingResult`/`getAlloySmeltingResult` over every item in the registry and record
    hits), `LOTREntJarRecipes`, unsmeltery (`uncraftableUnsmeltingRecipes` is listed as a station already; export the
    full reverse table), kebab stand, hobbit oven (food only). Generate 1.21 recipe types for each.
16. **EXP — drink stats. Done** (2026-11-04): `Exporter` writes `drinks.json` (stats, strengths, vessels, brewing);
    `converter/drinks.py`; drinks carry `lotr_port:drink_strength` / `lotr_port:vessel` components (see
    `lotr/CLAUDE.md`, "Drinks and kegs").
17. **EXP — weapon stats. Done:** `Exporter.weaponStats` records `LOTRWeaponStats`' getters per item plus bows'
    draw time and damage factor. The generator falls back to the class tables until CI's "Convert LOTR" run commits
    a new export. **Still to do:** rerun the export, and use `rangedDamage` for spears and throwing axes once they
    can be thrown.
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
28. **Ranged weapons.** Bows, crossbows and bolts are done (`LotrBowItem`, `LotrCrossbowItem`). Left:
    `throwing_axe`, `spear` (thrown), `projectile` kinds with entities
    (`LOTREntitySpear`, `ThrowingAxe`, `CrossbowBolt`, `Dart`, `Plate`, `Pebble`, `FirePot`, `MysteryWeb`) and the 14
    dispenser behaviours. NPC archers use them.
29. **Melee reach/speed. Done** (`LotrWeaponItem`, see the table above). Left: the `LOTRRecipePoisonWeapon` special
    recipe (dagger + poison bottle).
30. **Drinks and drunkenness.** Done: `drink`/`vessel` kinds, drinking with LOTR's food, nausea, effects, cures and
    specials. Left: placeable mugs (`mug` block kind, holds the item), alcohol tolerance (LOTR lowered nausea 1% per
    point), `LOTRDrunkenSpeech` scrambling chat (word table from constants), NPCs drinking.
31. **Done (2026-11-10, `machine/`):** faction forges, hobbit oven, millstone, unsmeltery (screens on LOTR's
    textures), kebab stand, plates, armour stand, weapon rack, Ent jar, LOTR chests. Left: `bookshelf_storage`, the
    unsmeltery's rocking. Original task: **Station blocks.** `furnace` kind (faction forges: alloy recipes; hobbit oven: 9-slot food oven), `keg`
    (LOTR's barrel: **done**, `drink/KegBlock`), `millstone`, `unsmeltery`, `kebab_stand`, `chest` (LOTR chests with LOTR textures), `plate`
    (holds food), `bookshelf_storage`, `armor_stand`, `weapon_rack`. Menus can copy vanilla furnace/brewing layouts
    with LOTR's GUI textures from `assets/lotr/gui`.
32. **Done (2026-11-10, `content/farm/`):** leek, lettuce, turnip, yam, flax, pipe-weed crops; berry bushes; corn;
    grapevines on posts. Left: fruit leaves dropping fruit, mud farmland. Original task: **Crops and farming.** `crop` kind growth stages from the block's `variants`; seeds plant; grapevines on
    trellises; fruit leaves drop fruit; mud farmland. Hired farmers later.
33. **Done (2026-11-10, `content/pouch/`):** 9/18/27-slot pouches with LOTR's screen and renaming, pick-ups go in
    when the inventory is full. Left: restock from a chest. Original task: **Pouches.** `pouch` kind (small/medium/large = 9/18/27 slots), dye recipe (`LOTRRecipesPouch`), auto-pick-up and
    restock from a chest (`LOTRContainerChestWithPouch`).
34. **Done (2026-11-10):** every trader's last offers split and merge coins (`NpcTrading.exchange`). Original
    task: **Coin exchange.** `LOTRContainerCoinExchange`: split/merge silver coin denominations (`coin` kind; `Coins`
    already exists).
35. **Done (2026-11-10):** pipe blows a smoke ring of particles (`LotrUseItem.smokeRing`). Left: ring colour by
    dye. Original task: **Pipes.** `smoking_pipe` + pipeweed: smoke ring projectile, colour by dye (`LOTRRecipeHobbitPipe`).
36. **Dye/special recipes.** The 44 `special_recipes`: armor dyes, feather dye, leather hat dye/feather, party hat,
    Harad robe/turban, banner patterns, treasure-pile, poison drinks. Each is a small `CustomRecipe`.
37. **Done (2026-11-10, `converter/ores.py`):** item-dropping ores with LOTR's xp, silk touch, fortune. Left:
    Quendite/Gulduril particles. Original task: **Ores and gems.** `ore` kind: xp, silk touch, fortune; Mithril, Naurite, Quendite, Gulduril, Morgul iron,
    Edhelvir; glowing Quendite/Gulduril (light + particles from `LOTRTileEntityGulduril`).
38. **Quagmire, dead marshes, webs.** Quagmire slows and sinks, marsh lights (`blocks_without_texture`: give them a
    particle-only renderer), marsh faces (`LOTREntityDeadMarshFace`), Ungoliant webs.
39. **Road bridges**: done 2026-11-04 with LOTR's arch, edge, brace and pillar rules.
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
