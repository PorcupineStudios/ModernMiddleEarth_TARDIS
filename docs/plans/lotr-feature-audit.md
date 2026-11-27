# LOTR 1.7.10 feature audit: every class against the port (2026-11-26)

Every class in The Lord of the Rings Mod 1.7.10 (Update 36.15, `LOTRMod v36.15.jar`) is sorted into a feature, and
each feature is marked by what the port (`lotr/`, 1.21.1 NeoForge) has for it. This is the full inventory.
`lotr/docs/missing-features.md` stays the short task list, and this file is where its items come from (see
"Reconciled" below). `lotr/docs/behavior-audit.md` is the block-by-block list of missing hooks.

## Statuses

- **Ported**: the port does what LOTR did, possibly in modern form (data instead of Java, NeoForge instead of Forge).
  Small missing pieces are named in the row.
- **Replaced**: a different system does the same job (colony membership instead of fellowships, borders instead
  of the conquest grid, a world type instead of the ring portal).
- **Partial**: part is there. The row says what is missing.
- **Not ported**: nothing does this job yet.
- **Dropped**: not needed on 1.21.1 (coremods, patches for 1.7.10 mods, help for converting old saves).

## How it is made (and how to rerun it)

- `converter/LotrFeatureAudit.java` (JDK 21, run from the repo root after a converter export):
  `java converter/LotrFeatureAudit.java "<path to LOTRMod v36.15.jar>"`. It reads the jar's class list (no code), so
  nothing from the jar is committed. Only class names appear here.
- `lotr-feature-audit/rules.tsv` sorts classes into features: a regex per line, first match wins, inner classes
  count with their outer class. `lotr-feature-audit/features.tsv` gives each feature its status and the port
  evidence. The tool fails if any class has no feature, so a new LOTR class or a rule typo shows up straight away.
- What the tool checks against the port by itself (column "LOTR classes" and `classes.tsv`):
  - Blocks and items: of LOTR's ids made by that class (export `blocks.json`/`items.json`, every LOTR class in
    the id's chain), how many `converter/idtable.json` maps to a port id.
  - NPCs and animals: an NPC type or mount breed under LOTR's registered entity name (export `npcs.json`).
    Classes LOTR never registered (bases such as `LOTREntityMan`, and unused Sauron and skeletal wraith) are
    left out of the count.
  - Biomes: each LOTR biome the class makes (export `world.json`) has a port biome of that name.
  - Structures: exported by `StructureExporter`, placed as a port structure, and whether LOTR's biome
    decorators placed it.
  - Mentions: port and converter files (java, py, md) that name the class (`classes.tsv`, last two columns).
- What was judged by hand: the status and the evidence column. These come from reading the port's code, data and
  docs (lotr/CLAUDE.md, `lotr/docs/`), checked on 2026-11-26 against branch `claude/legacy-jar-assets`. Statuses
  are about whether the code exists. Most systems have GameTests, but few were looked at in game (see lotr/CLAUDE.md).

## Biggest gaps

By how much of LOTR's play they cover, largest first:

1. **Utumno and the portals** (`dim.utumno`, `dim.faction_portals`; 36 classes): the whole dimension, its levels,
   keys and return portal, plus the Elven and Morgul portals. Utumno's NPC types already exist.
2. **21 of LOTR's structures were never exported** (`struct.structures`, automatic gaps below): the old
   `lotr/common/world/structure` generators that LOTR's biomes still placed. These are the Angmar shrine and tower,
   the Blue Mountains stronghold, the Dunlending campfire, Gondor ruins, the Harad obelisk, the High Elven and
   Rivendell halls, the hobbit picnic bench, the Mordor tower, the Nurn wheat farm, the orc slaver tower, the Rohan
   barrow, the ruined Dúnedain, Gondor, High Elven, Rohan and Wood-elf towers, the stone ruin and the Wood-elf
   tower. `StructureExporter` only builds `structure2` generators. Adding the old base class
   (`LOTRWorldGenStructureBase`) to it should close most of this.
3. **Fixed places, mines and pyramids** (`world.fixed`, `struct.mines`, `struct.pyramid`): Isengard's walls,
   the Rammas Echor, the settlements at waypoints, LOTR's small dwarven mines and the Taurëdain pyramids.
   Villages also have only one layout per seed (`struct.villages`).
4. **Missing parts of multiplayer and meta**: custom waypoints, bounties, the factions screen with its
   faction map, the Red Book's other tabs, title and shield screens, shields and capes drawn on players, and
   achievement triggers.
5. **NPC life**: travelling and scrap traders, spawn damping, wild NPC families (colonists do marry), Gollum's
   fishing and inventory, the troll chieftain fight and Sauron, Gandalf's fireball, players riding wargs and
   spiders, spear-throwing NPCs and mounted lances.
6. **Blocks that only look right**: spawners and NPC respawners (structures place their NPCs once), dart traps
   and troll totems, carved signs and the chisel, Rhûn fire, the gulduril and edhelvir crystals, treasure piles,
   animal jars, the mechanism and its rails, falling sand and gravel slabs, Dale crackers, LOTR's fishing loot,
   and the rideable barrel.
7. **Polish**: drunken speech and alcohol tolerance, restocking pouches from chests, elven blades glowing near
   orcs and the other named-weapon powers, animated armour parts and dye recipes, the alignment "+n" pop-ups,
   26 particle effects, northern lights, sandstorms and Mordor ash, and LOTR's own day length.

## Reconciled with missing-features.md

Every "Still open" item in `lotr/docs/missing-features.md` is in a feature row here. "Most LOTR commands" has no
row of its own: each of LOTR's 25 command classes is counted under the feature it serves. This audit found these
that the list did not have, and they have been added there: the 21 unexported structures, LOTR's day length
(`LOTRTime`), the alignment pop-ups, Dale crackers, animal jars, LOTR's fishing hook and loot, the rideable barrel,
anvil owner engraving and gem-coloured names, the quest tracker and retrieve/welcome quests, the hired-unit
inventory screen, the command sword's marker, the remaining world features (streams, troll hoards, skull piles,
Ent jars, cave cobwebs, blasted land, obsidian gravel) and particles. One correction: the list said families
were "not built", but colonists marry and have children through colony events. Only LOTR's wild NPC families
and rings are open.

<!-- generated by converter/LotrFeatureAudit.java: begin -->

### Totals (features / LOTR classes)

| Category | Ported | Replaced | Partial | Not ported | Dropped | All |
|---|---|---|---|---|---|---|
| Core and platform |  | 6 / 46 |  | 1 / 4 | 2 / 20 | 9 / 70 |
| Progress and meta | 4 / 25 | 3 / 46 | 4 / 22 | 2 / 17 |  | 13 / 110 |
| Factions, war and diplomacy | 4 / 30 | 2 / 25 | 1 / 8 | 2 / 6 | 1 / 3 | 10 / 72 |
| NPCs and creatures | 5 / 510 |  | 5 / 55 | 2 / 10 |  | 12 / 575 |
| Hiring, trade and quests | 3 / 72 | 2 / 18 | 1 / 23 |  |  | 6 / 113 |
| Items and combat | 8 / 144 | 1 / 10 | 5 / 105 | 5 / 20 |  | 19 / 279 |
| Blocks and machines | 11 / 292 |  | 3 / 62 | 6 / 52 |  | 20 / 406 |
| World generation | 9 / 222 | 2 / 28 | 2 / 33 | 1 / 3 |  | 14 / 286 |
| Structures |  | 1 / 12 | 2 / 368 | 2 / 10 |  | 5 / 390 |
| Dimensions and portals |  | 1 / 7 |  | 2 / 36 |  | 3 / 43 |
| Client rendering, models, sound | 5 / 148 |  | 2 / 22 |  |  | 7 / 170 |
| **All** | **49 / 1443** | **18 / 192** | **25 / 698** | **23 / 158** | **3 / 23** | **118 / 2514** |

Every one of LOTR's 2514 top-level classes (inner classes counted with their outer class) is in exactly one feature; the full list is `lotr-feature-audit/classes.tsv`.

### Core and platform

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Mod bootstrap: the LOTRMod god class, proxies, event and tick handlers, creative tabs, key bindings `core.bootstrap` | Replaced | 16 | `LotrPort`, `LotrClient`, one registry per area (`registry/LotrRegistries`), content from data (`content/LotrBlocks`, `LotrItems`, `content/*.json`), `content/LotrTabs` |
| Config and its screen `core.config` | Replaced | 3 | `LotrConfig`, `LotrClientConfig`, `client/LotrConfigScreen` (NeoForge `ConfigurationScreen`, COMMON and CLIENT only) |
| Player and world data, login sync, options `core.playerdata` | Replaced | 8 | Attachments and SavedData with codecs: `player/PlayerLotrData`, `world/LotrWorldData`, `travel/TravelData`, `quest/QuestLog`; client options are client config |
| Network handler and generic packets `core.network` | Replaced | 8 | `CustomPacketPayload` + `StreamCodec` per area (`net/HudNet`, `MapNet`, `QuestNet`, `ui/UiNet`, `unit/UnitNet`, `border/BorderNet`, `social/SocialNet`...) |
| Utilities, generic inventory slots, maze generator, version checker `core.util` | Replaced | 10 | Java and NeoForge equivalents; the delve planner has its own maze (`world/delve/DelvePlanner`); no update checker |
| Mod checker `core.compat` | Replaced | 1 | `compat/` (Create, JEI, Jade, Iris), `converter/compat.py` (Regions Unexplored, Biomes O' Plenty) |
| Coremod (ASM patches to vanilla) `core.coremod` | Dropped | 4 | Mixins only where needed (`mixin/`) |
| Bundled Unofficial Compatibility Patch (Thaumcraft, Botania, ScreenshotEnhanced fixes) `core.ucp` | Dropped | 16 | 1.7.10 mod conflicts; nothing to port |
| Player details from LOTR's web API (exclusive groups, e.g. who gets exclusive shields) `core.playerdetails` | Not ported | 4 | No online lookup; exclusive shields have no source in the port |

### Progress and meta

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Achievements and their screen `progress.achievements` | Partial | 8 | 523 advancements (`advancement/`, `progress/Achievements.grant` by LOTR name, `extra.py`); most triggers (kills, crafting, trading, drinking) are not wired; LOTR's screen is vanilla's advancement screen |
| Titles `progress.titles` | Partial | 4 | `progress/Titles`, `/lotr_title`; no title screen (LOTRGuiTitles) |
| Shields and capes (cosmetic) `progress.shields_capes` | Partial | 7 | `progress/Cosmetics`, `/lotr_shield`, `/lotr_cape`; not drawn on players (LOTRRenderShield), no picking screen |
| Shire Reckoning calendar `progress.calendar` | Ported | 3 | `progress/Calendar`, `/lotr_date`; holidays not ported |
| Middle-earth's own day length and clock (LOTRTime, `/lotr time`) `progress.time` | Not ported | 3 | The port uses vanilla's 24000-tick day and `/time` |
| Lore books `progress.lore` | Ported | 1 | `converter/lore.py`: 114 `lore/` books in structure chests |
| Red Book `progress.redbook` | Partial | 3 (1/1 ids mapped) | `quest/RedBookItem`, `client/RedBookScreen` (quests: list, details, abandon); LOTR's other tabs (missing-features: "Red Book tabs") |
| Map screen `progress.map` | Ported | 7 | `client/MapScreen` (LOTRGuiMap rebuilt), `MapTextures`, 105 labels `map/labels.json`, `net/MapNet` (map teleport for operators) |
| Other players on the map, admin hiding `progress.map_players` | Replaced | 2 | `social/Social.canSee`, `client/VisibilityScreen` (who sees you), colony icons `social/MapPresence` |
| Waypoints and fast travel `progress.fasttravel` | Ported | 14 | `travel/FastTravel`, 274 `waypoint/`, `fast_travel/settings.json`, `travel/Companions`, `/lotr_travel`, `/lotr waypoint` |
| Custom waypoints (made by players, shared) `progress.custom_waypoints` | Not ported | 14 | Nothing in the port (missing-features) |
| Fellowships `progress.fellowships` | Replaced | 26 | Colony membership (`colony/members`) and player groups (`social/Social`); no fellowship chat, icons or friendly-fire switch |
| LOTR's menu screens, options, notifications, title screen `progress.menu` | Replaced | 18 | Management screens on the UI core (`client/ui/UiScreen`, `ui/Views`), config screen, `client/MenuMusic`; LOTR's own main menu backdrop is not ported |

### Factions, war and diplomacy

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Factions, ranks, map regions, control zones `fac.factions` | Ported | 8 | `faction/` (`FactionDef`, 29 `faction/` + goblins, `Factions`, `FactionInfluence` zones) |
| Alignment, kill values, alignment bar `fac.alignment` | Ported | 14 | `faction/Alignment`, `FactionKills`, `client/LotrHud`; the floating "+n alignment" pop-ups over kills (LOTREntityAlignmentBonus) are not drawn |
| Faction relations `fac.relations` | Ported | 3 | `faction/Relation`, `Diplomacy` SavedData over data defaults, `/lotr faction` |
| Pledging and broken pledges `fac.pledge` | Ported | 5 | `faction/Pledges`, `/lotr pledge` (the pledge makes the player's realm a vassal) |
| Alignment choices offered after LOTR's faction changes `fac.alignment_choices` | Dropped | 3 | Only for old saves; the port is new worlds only |
| Factions screen (faction pages, faction map) `fac.screen` | Not ported | 4 | missing-features; realms and treaties have `diplomacy/DiplomacyViews` |
| Bounties on players and bounty quests `fac.bounties` | Not ported | 2 | missing-features (30 bounty quests) |
| Conquest grid `fac.conquest` | Replaced | 6 | `border/Conquest` (pressure against defence moves land), `border/Borders`, map layer `client/BorderLayer` |
| Invasions `fac.invasions` | Partial | 8 (1/1 ids mapped) | `npc/InvasionSpawner`, 45 `invasion/`, `invasion_spawns`; the conquest horn (starts an invasion) only plays its sound, no invasion boss bar |
| Banners and banner protection `fac.banners` | Replaced | 19 (1/1 ids mapped) | `land/ClaimBannerItem` (42 faction banners claim land), `land/Claims`, `land/Protection`; the whitelist screen is colony role permissions |

### NPCs and creatures

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| NPC base: one entity, skins, names, speech, inventory `npc.core` | Ported | 22 (0/1 ids mapped) | `npc/LotrNpc` + `NpcType` data (412 `npc/`), `npc_names/`, 1514 `speech/`, `client/NpcRenderer`, `npc/SpawnEggs`, `/lotr npc` |
| NPC types (one class per race and job in LOTR) `npc.types` | Ported | 410 (383/383 found by name) | One NPC type file each, checked by name (see the count); female NPCs and trader outfits not yet; LOTR never registered the skeletal wraith (an unused class), so missing-features' "skeletal wraith" is a new NPC, not a port |
| NPC AI and NPC-thrown attacks `npc.ai` | Ported | 42 | `npc/behaviour/` (36 behaviours: archers, bandits, trolls fleeing the sun, orc bombs, huorns, ents, wights...), `LotrProjectile`, `npc/OpenGateGoal`; spear-throwing NPCs and mounted lances missing (missing-features) |
| Animals and ambient creatures `npc.animals` | Ported | 31 (23/23 found by name) | Creature NPCs on LOTR's models, 157 `npc_animals/`, `AnimalBehaviour`, `AnimalGenerationMixin` |
| Mounts, riding, mount inventories `npc.mounts` | Partial | 21 (23/23 ids mapped, 8/8 found by name) | `mount/LotrHorse` (vanilla chested horse, 8 LOTR breeds), `MountArmorItem`, NPC riders (`LotrNpc.maybeRide`); players riding wargs and spiders, structure NPCs riding missing |
| Gollum `npc.gollum` | Partial | 12 (1/1 found by name) | `GollumBehaviour` (feed fish, follows, sits); his fishing and inventory missing |
| Gandalf, Saruman, the Grey Wanderer `npc.wizards` | Partial | 8 (2/2 found by name) | `GandalfBehaviour` (LOTRGreyWandererTracker); Gandalf's fireball and smoke ship missing |
| Bosses (Balrog, troll chieftain, Sauron) `npc.bosses` | Partial | 7 (2/2 found by name) | `BalrogBehaviour`, `BossJumpBehaviour`; the troll chieftain fight and Sauron missing (missing-features) |
| Marriage, rings, children `npc.families` | Partial | 7 (3/3 ids mapped) | Colonists marry and have children through colony events (`colony/events/ColonyEvents` wedding and birth, `colonist/Kin`, family tree `client/ui/TreePane`); LOTR's wild hobbit, dwarf and man families, marriage rings and children following players are not ported |
| Travelling traders, scrap traders, trader respawn `npc.travelling` | Not ported | 8 (1/1 found by name) | missing-features |
| NPC spawning (lists, conditions, event spawner) `npc.spawning` | Ported | 5 | `npc/NpcSpawner`, 177 `npc_spawns/`, `world/NoMonsters`, `border/TerritorySpawns`; the event spawner's travelling-trader timers belong to `npc.travelling` |
| Spawn damping `npc.spawn_damping` | Not ported | 2 | missing-features |

### Hiring, trade and quests

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Trading and coins `trade.trading` | Ported | 29 (1/1 ids mapped) | `npc/Traders`, `TraderMenu`, `CoinExchangeMenu`, 206 `npc_trades/`, `npc/Coins` |
| Hiring units, mercenaries, hired-unit screens `trade.hiring` | Ported | 39 | `npc/Hiring`, `client/UnitScreen`, 51 `npc_units/`, `unit/Units` (orders, post, wage); hired units' own inventory screen is not ported |
| Hired farmers `trade.farmers` | Replaced | 8 | Colony jobs and production (`colony/work`, `colony_job/`) |
| Squadrons, command sword and command horn `trade.squadrons` | Replaced | 10 (2/2 ids mapped) | Unit squads (`unit/Units` `squad`); the command sword's target marker is not ported |
| Mini-quests `quest.miniquests` | Partial | 23 | `quest/` (collect, kill_faction, kill_npc; 37 pools), `client/QuestOfferScreen`; retrieve and welcome quests, the on-screen tracker and the per-quest speech missing |
| Pickpocketing `quest.pickpocket` | Ported | 4 | Quest kind `pickpocket` |

### Items and combat

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Melee weapons, tools, LOTR's attack timing `item.weapons` | Ported | 23 (479/479 ids mapped) | `content/LotrWeaponItem`, `combat/AttackTiming`, weapon stats per class (missing-features "Combat") |
| Bows, crossbows, thrown weapons, bombs, termites `item.ranged` | Ported | 36 (43/43 ids mapped) | `LotrBowItem`, `LotrCrossbowItem`, `content/thrown/` (`ThrownWeapon`, `ThrownMissile`, `OrcBomb`) |
| Dispenser behaviours `item.dispensers` | Ported | 13 | `Thrown.dispensers` |
| Named and magic weapons `item.special_weapons` | Partial | 13 (9/9 ids mapped) | Items exist as swords; Sting/Glamdring/Orcrist glowing near orcs, the Balrog whip's fire, Sauron's mace and the staffs' powers missing |
| Armour, special models, dyeable hats and robes `item.armor` | Partial | 46 (255/255 ids mapped) | `client/ArmorModels` (34 models), per-item textures, dyeable flag; animated parts (swan wings, robes), dyeing and feather recipes missing |
| Modifiers, smith's scrolls, reforging, ancient items `item.modifiers` | Ported | 28 (7/7 ids mapped) | `modifier/` (data enchantments, `SmithScrollItem`, `AncientItem`) |
| LOTR's anvil (coloured names, reforge, engrave owner) `item.anvil` | Replaced | 10 (11/11 ids mapped) | Vanilla anvil + `modifier/Modifiers` (scrolls, reforging); owner engraving and gem-coloured names missing |
| Foods, placeable foods, Ent-draughts `item.food` | Ported | 11 (88/88 ids mapped) | Foods in `content/items.json`, `content/PieBlock`, `FeastMode`, `EntDraughtItem` |
| Drinks, vessels, poisoned drinks `item.drinks` | Partial | 30 (93/93 ids mapped) | `drink/`, `LotrDrinkItem`, `VesselBlock`, `PoisonDrinkRecipe`; drunken speech, alcohol tolerance, NPCs drinking missing |
| Pouches `item.pouches` | Partial | 11 (1/1 ids mapped) | `content/pouch/`; restocking from chests and the chest-with-pouch screen missing |
| Pipes and smoke rings `item.pipes` | Partial | 5 (1/1 ids mapped) | `content/LotrUseItem` (smoking, smoke ring); smoke colour recipes missing |
| Branding iron `item.branding` | Ported | 3 (1/1 ids mapped) | `content/BrandingIronItem` |
| Rugs, boss trophies, troll statues `item.rugs` | Ported | 25 (10/10 ids mapped) | `content/figure/` (figure entity: statues and rugs) |
| Animal and butterfly jars (catching creatures) `item.jars` | Not ported | 6 (4/4 ids mapped) | `butterfly_jar` is a plain shaped block |
| Treasure piles (stacking, falling) `item.treasure` | Not ported | 5 (3/3 ids mapped) | Plain blocks (behavior-audit) |
| Dale crackers (seal items inside) `item.dalecracker` | Not ported | 5 (1/1 ids mapped) | `dalish_cracker` is a plain item |
| Mechanism and mechanised rails `item.mechanism` | Not ported | 2 (3/3 ids mapped) | Plain item and blocks (behavior-audit) |
| LOTR's fishing hook and loot `item.fishing` | Not ported | 2 | Vanilla fishing; regional loot is open in `modernize.md` |
| Other items (gems, bones, dyes, matches) and potion changes `item.misc` | Ported | 5 (18/18 ids mapped) | Plain items with LOTR's ids; dye on mobs not ported |

### Blocks and machines

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Building blocks (stone, bricks, slabs, stairs, walls, pillars, glass, panes, bars, doors...) `block.building` | Ported | 85 (512/512 ids mapped) | `content/blocks.json` kinds cube, pillar, slab, stairs, wall, pane, door...; stonecutter recipes, waterlogging, tags |
| Woods, planks, beams, leaves, saplings `block.wood` | Ported | 65 (113/113 ids mapped) | Full wood sets (`content/woodset/`), `LotrSaplingBlock` + `sapling/` rules, leaf drops |
| Plants, flowers, crops, grass, reeds `block.plants` | Partial | 49 (80/80 ids mapped) | `PlantBlock`, `content/farm/`, `ReedBlock`, `HazardPlantBlock`; Mordor moss and Morgul shroom spreading, mud farmland, fruit leaves and hanging fruit, goran use missing |
| Blocks that hurt or slow (quagmire, waste, thorns, Morgul flowers, coral) `block.hazards` | Ported | 5 (5/5 ids mapped) | `content/Hazards`, `HazardBlock`, `HazardPlantBlock` |
| Hanging and floor-bound blocks (chandeliers, cages, chains, stalactites, fallen leaves, ropes) `block.supported` | Ported | 10 (17/17 ids mapped) | `content/SupportedBlock`, `behaviors.json` |
| Torches and hearths `block.lights` | Ported | 7 (17/17 ids mapped) | `LotrTorchBlock`, `client/BlockParticles` |
| Ores and storage blocks `block.ores` | Ported | 7 (21/21 ids mapped) | `ore` kind, raw ores (`converter/raw_ores.py`) |
| Sand and gravel slabs that fall `block.falling` | Not ported | 3 (8/8 ids mapped) | Plain slabs (missing-features) |
| Faction crafting tables and recipes `block.tables` | Ported | 31 (54/54 ids mapped) | `crafting/FactionTableBlock`, `FactionRecipe`, 4949 recipes, JEI category |
| Forges, hobbit oven, millstone, unsmeltery, kebab stand, Ent jar `block.machines` | Ported | 40 (16/16 ids mapped) | `machine/` (`MachineBlock`, `MachineMenu`, `client/MachineScreen`); the unsmeltery's rocking animation missing |
| Barrel (brewing) `block.keg` | Ported | 11 (1/1 ids mapped) | `drink/KegBlock` (LOTR's barrel is the keg); the rideable barrel entity (LOTREntityBarrel) and drinks ageing in barrels are not ported |
| Plates, weapon racks, armour stands, chests, bookshelf storage `block.display` | Ported | 25 (12/12 ids mapped) | `machine/DisplayBlock`, `WeaponRackBlock`, `LotrChestBlock`, `BookshelfStorageBlock` |
| Gates, portcullises, dwarven doors `block.gates` | Partial | 8 (28/28 ids mapped) | `content/GateBlock`, `client/ConnectedModel`; ithildin glow and LOTR's gate sounds missing |
| Beacons of Gondor `block.beacon` | Ported | 6 (1/1 ids mapped) | `content/GondorBeaconBlock` (lit beacons light each other) |
| Chisel and carved stone signs (ithildin glow) `block.carved_signs` | Not ported | 10 (4/4 ids mapped) | missing-features; stonecutting is done |
| Mob spawners, spawner chests, NPC respawners `block.spawners` | Not ported | 17 (5/5 ids mapped) | Structures place up to 3 NPCs once (missing-features) |
| Dart traps, troll totems `block.traps` | Not ported | 7 (4/4 ids mapped) | missing-features |
| Rhûn fire and fire jars `block.rhun_fire` | Not ported | 6 (2/2 ids mapped) | missing-features |
| Gulduril and edhelvir crystals, glows, corrupt mallorn `block.crystals` | Not ported | 9 (5/5 ids mapped) | Blocks and items exist without behaviour (missing-features) |
| Beds, bones, remains, goran `block.misc` | Partial | 5 (11/11 ids mapped) | Beds (`LotrBedBlock`) done; goran use and remains' drops missing |

### World generation

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Middle-earth dimension and world type `world.dimension` | Ported | 5 | `world/LotrDimensions`, world preset `lotr_port:middle_earth`, `/lotr travel` |
| Terrain (chunk provider) `world.terrain` | Ported | 1 | Noise settings + `lotr_port:map_terrain` (`world/map/MapTerrain`, `MiddleEarthMap`), tuned by LOTR's terrain survey |
| Biome layers and rivers (GenLayer) `world.genlayer` | Replaced | 26 | `world/map/MapBiomeSource` reads LOTR's map image; rivers `MiddleEarthMap.Rivers` |
| Biomes `world.biomes` | Ported | 166 (163/163 found by name) | 219 biome files (one per LOTR biome plus sub-biomes and seas), checked by name |
| Biome decorator `world.decorator` | Ported | 1 | `converter/world.py` placed features per biome |
| Biome variants `world.variants` | Ported | 12 | `world/map/Variants`, placement filter `lotr_port:in_variant` |
| Trees `world.trees` | Ported | 32 | 111 tree types grown by LOTR's own generators into templates (`TemplateTreeFeature`) |
| Other world features `world.features` | Partial | 29 | Boulders, reeds, logs, fallen leaves, quagmire, crater, webs (`world/feature/`); streams, troll hoards, skull piles, Ent jars, cave cobwebs, blasted land, obsidian gravel, marsh lights not found in the port's features; spider webs dropping mystery webs missing |
| Caves and ravines `world.caves` | Replaced | 2 | Vanilla carvers and noise caves (`world/feature/BiomeCarvers`, `BiomeCaveConfig`, `converter/noisecaves.py`) |
| Roads and bridges `world.roads` | Ported | 3 | `world/map/Roads`, `RoadFeature`, `converter/roads.py` |
| Mountains and peaks `world.mountains` | Ported | 1 | `map.json` mountains, `world/map/Ranges` |
| Fixed builds at map coordinates (Isengard walls, Rammas Echor, waypoint settlements) `world.fixed` | Not ported | 3 | missing-features |
| Chest contents `world.loot` | Ported | 1 | 2254 loot tables |
| Sky, clouds, weather `world.sky` | Partial | 4 | `client/MiddleEarthSky` (sun, moon, stars, Eärendil), `client/Atmosphere`, `LotrAmbience`; northern lights, sandstorms, Mordor ash, LOTR's clouds, seasonal grass colours missing |

### Structures

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Structures (LOTR's generators) `struct.structures` | Partial | 354 (82/103 found by name) | 82 structures placed by `world/structure/LotrStructure` at LOTR's rates; the automatic check lists generators LOTR placed that the port lacks |
| Villages and towns `struct.villages` | Partial | 14 (11/11 found by name) | 11 village kinds (`village_*`, 3 layouts each); one fixed layout per seed, no town walls variety |
| Dwarven mines `struct.mines` | Not ported | 7 | Moria is a delve (`world/delve/`); LOTR's small mines could be small delves |
| Taurëdain pyramids (map-gen) `struct.pyramid` | Not ported | 3 | missing-features |
| Structure scans, timelapse, table of command, ban and allow `struct.tools` | Replaced | 12 (1/2 ids mapped) | The converter exports LOTR's structures (`converter/.../StructureExporter`); no in-game scan tool, no structure ban commands |

### Dimensions and portals

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| Utumno (dimension, levels, portals, keys, its NPCs' home) `dim.utumno` | Not ported | 27 (23/23 ids mapped) | missing-features; Utumno's NPC types exist |
| The ring portal to Middle-earth `dim.ring_portal` | Replaced | 7 (2/2 ids mapped) | World type, `/lotr travel`, TARDIS crash site (`shared/SharedApiHooks`) |
| Elven and Morgul portals `dim.faction_portals` | Not ported | 9 (2/2 ids mapped) | missing-features |

### Client rendering, models, sound

| Feature | Status | LOTR classes | Port counterpart, or what is missing |
|---|---|---|---|
| NPC, creature and player renderers `client.render_npc` | Ported | 92 | `client/NpcRenderer`, `CreatureRenderer`, `RigRenderer`, `RacePlayers`, `HuornRenderer` |
| Creature and block models `client.models` | Ported | 40 | `converter/entity_models.py` replays LOTR's model constructors (`npc_model/`), `client/CreatureModel`, `CreatureAnim` |
| Item renderers `client.render_items` | Partial | 5 | Large icons, bows, crossbows done; the skull staff, horns blown and elven-blade glow missing |
| Block rendering and block entity renderers `client.render_blocks` | Ported | 5 | `client/ConnectedModel`, `MachineRenderers`, `VesselRenderer`, `LegacyAssets` |
| Particles `client.fx` | Partial | 17 | Torch and leaf particles (`client/BlockParticles`), creature trails (`TrailBehaviour`); many of LOTR's 26 effects (marsh faces, river water, waves, music notes...) and ore glow particles missing |
| HUD (compass, alignment bar, attack meter) `client.hud` | Ported | 1 | `client/LotrHud` |
| Music regions and ambience `client.music` | Ported | 10 | `client/LotrMusic`, `LotrAmbience`, music pack (`lotr/docs/music-pack.md`) |

### Automatic gaps

Classes inside a feature whose port counterpart the automatic check did not find (an NPC type, biome or structure of that name; block or item ids not mapped). Some are covered under another name or by design; the feature row says which.

- `npc.core`: LOTRItemSpawnEgg (ids 0/1 mapped)
- `struct.structures`: LOTRWorldGenAngmarShrine (not exported, LOTR placed it), LOTRWorldGenAngmarTower (not exported, LOTR placed it), LOTRWorldGenBlueMountainsStronghold (not exported, LOTR placed it), LOTRWorldGenDunlendingCampfire (not exported, LOTR placed it), LOTRWorldGenGondorRuin (not exported, LOTR placed it), LOTRWorldGenGondorRuins (not exported, LOTR placed it), LOTRWorldGenHaradObelisk (not exported, LOTR placed it), LOTRWorldGenHighElvenHall (not exported, LOTR placed it), LOTRWorldGenHobbitPicnicBench (not exported, LOTR placed it), LOTRWorldGenMordorTower (not exported, LOTR placed it), LOTRWorldGenNurnWheatFarm (not exported, LOTR placed it), LOTRWorldGenOrcSlaverTower (not exported, LOTR placed it), LOTRWorldGenRivendellHall (not exported, LOTR placed it), LOTRWorldGenRohanBarrow (not exported, LOTR placed it), LOTRWorldGenRuinedDunedainTower (not exported, LOTR placed it), LOTRWorldGenRuinedGondorTower (not exported, LOTR placed it), LOTRWorldGenRuinedHighElvenTurret (not exported, LOTR placed it), LOTRWorldGenRuinedRohanWatchtower (not exported, LOTR placed it), LOTRWorldGenRuinedWoodElfTower (not exported, LOTR placed it), LOTRWorldGenWoodElfTower (not exported, LOTR placed it), LOTRWorldGenStoneRuin (not exported, LOTR placed it)
- `struct.tools`: LOTRItemStructureSpawner (ids 0/1 mapped)

<!-- generated by converter/LotrFeatureAudit.java: end -->
