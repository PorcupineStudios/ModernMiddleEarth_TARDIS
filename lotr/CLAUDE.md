# LOTR Port (Minecraft 1.21.1 NeoForge)

Future-port of The Lord of the Rings Mod (1.7.10, Update 36.15) to Minecraft 1.21.1, part of the TARDIS: Ring Bearer
modpack project. Mod id `lotr_port`, main class `LotrPort`, package
`com.porcupinestudios.lotrport`.

**Keep this repo private** (it holds both mods). LOTR's code belongs to Mevans and has no public source or license to
redistribute. The reference is the compiled jar `libs/LOTRMod v36.15.jar` in PorcupineStudios/RingRealms; its class map:
https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu. Rebuild features in modern code; never paste decompiled code.

## Approach (agreed in the project thread)
- Split the old `LOTRMod` god class into one registry per area.
- Player/world state (old `LOTRPlayerData`, `LOTRLevelData`) as saved data and data attachments with Codecs.
- Factions and alignment first (most things depend on them, and RingRealms and the TARDIS mod read them).
- NPCs: two data-driven bases, people (humanoids with records, jobs, speech) and creatures (animals, monsters), not one
  class per race. NPCs stay light and get a saved person record only when they matter (hired, named, recruited...).
- World gen as data: biomes, structures (`.nbt` templates), loot tables. A converter that runs the old mod and exports
  structures, recipes and models as data is the planned big time-saver.
- RingRealms (colonies and realms, PorcupineStudios/RingRealms on 1.7.10) is part of this mod, in `colony` and `realm`
  packages: colonists build on the NPC base, realms register as factions. Its `docs/` folder is the feature list; its
  code is ours, so its logic can be carried over, but the LOTR-internals and reflection layer goes away.
- Middle-earth is a start dimension for TARDIS: Ring Bearer: offer it through the shared API's crash-site provider,
  and register `lotr_port:alignment`-style condition types there. Never depend on `tardis/`.

## Decisions (Aaron, 2026-11-03)
- Reuse LOTR's textures, sounds and map image (private play).
- Middle-earth is a world type, as in the original; in a normal world it is a dimension players travel to.
- Many LOTR systems get redesigned around RingRealms, Create and the TARDIS rather than copied.
- Prefer writing scripts that do the repetitive work (export, generate, rename) over porting class by class by hand.

## World generation (phase 3)
- `world/map/MiddleEarthMap` reads the generated map; `MapBiomeSource` (`lotr_port:middle_earth`) gives one biome
  per map pixel with wobbled borders; density function `lotr_port:map_terrain` gives ground height or hilliness,
  and the noise settings JSON shapes terrain from them. Tune heights in `converter/world.py` (or the JSON), not Java.
- World type "Middle-earth" (`lotr_port:middle_earth` preset): the overworld is generated from the map, so spawn and
  respawn are vanilla. Any other world gets the `lotr_port:middle_earth` dimension; `/lotr travel` goes there and
  back. `LotrDimensions.middleEarth(server)` returns whichever level is Middle-earth.
- Rivers: `MiddleEarthMap.Rivers` (noise, cut into ground under `max_height`); roads: `map/Roads` +
  `world/feature/RoadFeature` (no bridges yet). Trees and ores per biome are generated data (`converter/trees.py`,
  `world.py`); tree shapes are vanilla stand-ins for LOTR's hand-made ones.
- Trees (2026-11-04): the exporter grows each of LOTR's 111 tree types with its own generator, up to six shapes each
  (`TreeExporter`, `trees.json.gz`, run in CI). `converter/trees.py` saves shapes as `structure/tree/<type>/<n>.nbt`
  (leaves get their real distance to a log so they don't decay) and the type's feature as `lotr_port:template_tree`
  (`world/feature/TemplateTreeFeature`: random shape and turn; `trunk` = where LOTR's generator started the shape,
  set on the origin; saplings pass their square (origin = corner + (size-1)/2, as LOTR) and turned trees are shifted
  back onto it; soil a sapling could take, sand for desert trees). Export: 108 types, 648 shapes; the giant mallorn
  (9x9 on elven grass) and the water willow grew nothing in the exporter and stay stand-ins. 
- Saplings: kind `sapling` (`content/LotrSaplingBlock`: random ticks in light, 2 stages, bone meal). What grows is
  `data/<ns>/sapling/<block>.json` (`SaplingRules`), read by `converter/saplings.py` from the constants in LOTR's
  sapling classes' growTree: rules tried in order, `square` N (2x2, 3x3 party trees, 4x4/5x5 redwood and
  dragonblood, the mallorn's 9x9 on elven grass), `cross` (mallorn boughs), `chance` (1 in N large trees), else a lone
  sapling. The arrangement's saplings become the trunk. GameTest `SaplingTests`.
- Whole trees for felling (`tree/`, hooks for RingRealms woodcutters and orchards, 2026-11-04): every tree a
  `template_tree` grows (worldgen and saplings) is recorded per chunk (`TreeRecords`, SavedData `lotr_port_trees`:
  feature id, trunk corner + width, box, planted), and sapling-grown stand-ins too. `Trees.find(level, pos)` gives the
  tree a log or leaf belongs to (its logs from the trunk, diagonals for branches, inside its box, never into another
  recorded trunk, and its leaves); unrecorded trees fall back to a search from the block. `Trees.harvest(level, pos,
  harvester, leaves)` fells it all, returns the drops instead of dropping them and the sapling to replant
  (`Trees.saplingFor`); `Trees.near` lists recorded trees by trunk. Events `Trees.Grown` (saplings, main thread) and
  `Trees.Harvested`. Colonists aren't wired to it yet. GameTest `SaplingTests` grows a 3x3 and fells it whole.
- The crash-site provider answers the overworld in a Middle-earth world (the wreck lands at the Shire spawn).
- CI "Middle-earth world" step makes a new Middle-earth world on a dedicated server (`WorldCheck`): spawn, crash
  site, one chunk in every biome on the map, road blocks on a road.

## Factions and realms (phase 4)
- Great powers are data: `data/<ns>/faction/*.json` (`FactionDef`, reloads), generated from LOTR by
  `converter/factions.py`; names and ranks are lang keys `faction.<ns>.<id>[.rank.<rank>]`.
- `Factions` merges them with realms (`Realms` SavedData, ids `lotr_port:realm/<n>`); a realm without its own
  relations uses its culture's. Relations: `Diplomacy` SavedData overrides the data defaults; vassals live there too.
  Every change goes into the history log.
- Kills: `FactionKills` reads the `lotr_port:faction` entity attachment; the faction and its allies lose its
  `kill_value`, its enemies gain it. Pledging (`Pledges`) needs the pledge rank and makes the player's realm a vassal.
- Conditions `lotr_port:alignment`, `lotr_port:pledged`, `lotr_port:relation`. Commands under `/lotr faction`,
  `/lotr pledge`, `/lotr realm found`. Faction data isn't synced to clients yet (no screens need it).

## NPCs (phase 6)
- One entity, `lotr_port:npc` (`npc/LotrNpc`), plays every NPC type from data: `data/<ns>/npc/<type>.json`
  (`NpcType`: faction, skins + `skin_height` 32/64, scale, health, speed, attack, gear, name bank, pools,
  `trade_alignment`). Pools: `npc_trades/` (`buy` = what the trader sells, `sell` = what it buys; coins), `npc_units/`
  (captains), names `npc_names/`. All generated by `converter/npcs.py`; everything reloads with /reload.
- Clients get only what they draw (synced type id, skin, 64x64 flag); size comes from the `generic.scale` attribute.
  Renderer `client/NpcRenderer` (classic humanoid mesh, two layer sizes); client code only in `client/`.
- Trading uses the vanilla trading screen (`NpcTrading`, prices split into silver coin stacks by `Coins`). Hiring:
  right-click a captain (sneak if it also trades) lists units in chat with [Hire] links running `/lotr_hire`;
  `Hiring.hire` checks alignment, pledge and coins. Hired units: sneak + right-click switches follow/guard; they defend
  the owner. `People` (SavedData) keeps a record only for hired units (history: `npc_hired`, `npc_died`).
- Speech: `speech/<bank>/<mood>.json` (`{"lines": [...]}`, `#` = player name, `@` = place, avoided), from LOTR's speech
  banks; the type's `speech` names the bank. Right-click (not trade/hire) says a line above the head (synced `SPEECH`,
  drawn by `NpcRenderer`): `hired` for the owner, `hostile` below 0 alignment, else `friendly`, then `neutral`.
  Model + speech share the `NpcType.Persona` codec (a codec group holds at most 16 fields).
- Not yet: animals, trolls, wargs, spiders (other models), mounts for mounted units, female NPCs,
  LOTR's trader outfits. `/lotr npc spawn|types|people` for testing.

## Structures and spawning (phase 7)
- 82 LOTR structures (`converter/structures.py`): `structure/<id>/<0-2>.nbt` (3 seeded layouts), placed by our own
  structure type `lotr_port:lotr` (`world/structure/LotrStructure` + `LotrStructurePiece`, 2026-11-04 rework):
  - Rarity is LOTR's: LOTR gives each biome its own chance per chunk, so there is one structure + set per (structure,
    chance): `worldgen/structure/<id>[_<chance>]`, biome tag `has_structure/<id>[_<chance>]`, set `random_spread`
    spacing 1 (every chunk is a candidate) and the structure's own `chance` + `salt` roll 1 in chance per chunk with a
    mixed hash. Not vanilla's `frequency`: it seeds its random linearly from the chunk, and with every chunk rolling
    that put structures in long rows of overlapping neighbours. Before all this, one spacing ~sqrt(lowest chance)
    applied to every biome, so structures were far too common.
  - Then the structure turns down ground the way LOTR's generators did: water at the surface, more than `max_slope`
    (6) blocks between highest and lowest ground under the footprint, a road within `road_distance` (16). Random
    layout and rotation; template layer `ground` sits at the surface.
  - Air: templates hold real air only where LOTR cleared space (dug cellars from the export; above ground, a
    column's open space under its highest block, i.e. rooms and under roofs). Everywhere else is structure void, so
    natural terrain stays. `terrain_adaptation` is `none` (beard_box carved the air ditches round every building);
    the piece fills under the ground layer down to solid ground instead (dirt under dirt/grass, else the same block,
    up to `max_foundation` 24).
  - Doors: the lower half takes the upper half's hinge (1.7.10 kept it only on top; split doors). Gates
    (`content/GateBlock`, LOTR's multi-block gates) rotate and mirror with the structure and open as one gate: the
    touching blocks of the same kind in the same plane.
  - Not yet: LOTR's quirk of no structures within 32 blocks of the x/z axes, biome variants that disable structures.
- Template NPCs are `{id:lotr_port:npc, NpcType, Fresh:1b, PersistenceRequired:1b}`; `LotrNpc` rolls skin, gear and
  name on load when `Fresh` is set. LOTR's spawner blocks become up to 3 placed NPCs (no respawning yet).
- Natural spawning: `npc_spawns/<biome ns>/<biome path>.json` (entries npc/weight/min/max, cap, chance, interval),
  run by `npc/NpcSpawner` per player on `LevelTickEvent`. Conquest and invasion lists are not ported yet.
- CI world check (`world/StructureCheck`): nearest gondor and mordor structure from the faction's map home must hold
  NPCs of that faction.
- Templates are placed without block updates, so `structures.py` sets pane/fence/wall sides from the neighbours
  (lone pane posts showed as dark squares) and fills pots with a vanilla potted flower picked by position (1.7.10 kept
  the plant in a tile entity the export lacks). Foundations, not terrain adaptation, keep houses on slopes off the ground
  (beard_thin left them floating, beard_box dug ditches; Aaron 2026-11-04).
- Armor whose LOTR texture is per item (gambesons, jacket, hats) gets a material per texture (`LotrItems.key`); the
  hats are drawn on LOTR's own hat model, so `generate.py hat_layer` paints a cap onto the helmet layer instead
  (the Shirriff's missing hat texture was the purple head).
- Not yet: villages and town walls (LOTR's village generators), invasions, respawner blocks, non-person creatures.

## Map, waypoints and mini-quests (phase 8)
- Waypoints: `data/<ns>/waypoint/<id>.json` (x, z, map pixel, region, faction, hidden); 274 from LOTR. Entering a biome
  tagged `waypoint_region/<r>` unlocks region r (`travel/FastTravel`, player attachment `travel`). Travel needs the
  region, alignment >= 0 with the waypoint's faction, no mob attacking you, and the wait over
  (`fast_travel/settings.json`: 180-1800 s by distance). `/lotr_travel`, `/lotr_waypoints`; admin
  `/lotr waypoint unlock|ready|info`.
- Map screen (rebuilt 2026-11-04 to match LOTR's `LOTRGuiMap`): key M opens it (`client/MapScreen`). Fullscreen by
  default, widget for a framed window (30 px border); zoom powers -3..4 eased over 6 ticks; drag or WASD/arrows to
  pan; LOTR's 105 map labels (`assets/lotr_port/map/labels.json`, read by `converter/classfile.py` from the constants
  in LOTR's label class, lang `map_label.lotr_port.<id>` or the biome name) fading over their zoom range; widgets for
  waypoints, labels, zoom, fullscreen and sepia (icons from LOTR's map sheet). Hovering shows the biome and block
  x, z; an operator or creative player in Middle-earth presses Map Teleport (T, `MapNet.Teleport`, checked again on
  the server) to go there. Click a waypoint to select it, Fast Travel (F) travels; tooltips give distance and why
  travel is blocked. `client/MapTextures` keeps the map at full size and halved three times (box filter, plain and
  sepia) and the screen draws the copy that fits the zoom with float positions, so zoomed-out maps don't shimmer;
  the parchment overlay is one stretch over the window at 20% (as LOTR), the sea beyond the edge the map's ocean
  colour. Packets in `net/MapNet` (the client handler is set in client setup, so common code never names the screen).
- Mini-quests: `quest_pool/<group>.json` (collect / kill_faction / kill_npc, min-max, weight, reward factor; coins
  and alignment per target), named by an NPC type's `quests`. Right-click a giver (not traders/captains) to get LOTR's
  offer page (`client/QuestOfferScreen`, `QuestNet.Offer`; the roll waits server-side in `Quests.PENDING` until
  Accept/Decline). Right-click again: progress or thanks above its head. Using the Red Book (item kind `red_book`)
  opens `client/RedBookScreen` (list, details, abandon). Textures: LOTR's `gui/quest/*` copied by `quests.py`.
  Player attachment `quests` (`quest/QuestLog`); history `quest_completed`. `/lotr_quests [abandon <n>]`.
  LOTR's per-quest lines (`speech/miniquest/<group>/<quest>_start|progress|complete`) need the exporter to record each
  quest's speech key first.
- Offers (2026-11-04, LOTR's `LOTREntityQuestInfo`): a giver offers a quest only now and then. Each tick a giver not
  offering starts with chance 1 in the pool's `offer_chance` (20000, about once in 17 min; LOTR's renegades and
  shieldmaidens 4000) and the offer lasts `offer_ticks` (24000, a day). Traders, captains and hired units never offer.
  The synced `QUEST_OFFER` colour (faction colour, -1 = none) draws LOTR's quest mark (`textures/gui/quest/
  quest_offer.png`) above the head within 16 blocks (`NpcRenderer.renderQuestMark`). Right-clicking an unmarked giver
  just talks; taking the quest ends the offer (`Quests.accept`). `/lotr npc offer [radius]` starts offers for testing.
  Not yet: hiding the mark from players below the alignment (clients don't have faction data yet).
- Not yet: bounty, retrieve and pickpocket quests, fellowships, titles, custom waypoints, LOTR's quest
  speech.

## Colonies (phase 9, RingRealms built in: `colony/`)
- Founding: a Colony Charter (`colony_charter`, tab "Colonies") used on a block makes the Hall there, for the
  founder's pledged faction; renamed on an anvil = colony name. One colony per player; Halls `hall_gap` apart.
  Saved in `Colonies` (SavedData `lotr_port_colonies`, codecs); history `colony_founded`, `citizen_arrive`,
  `citizen_die`, `recruit_started`. Citizens carry the `lotr_port:colony` attachment and never despawn.
- Growth (`colony/settings.json`): once a colony day, `arrivals_per_day` migrants come to a loaded Hall while there are
  free beds (bed heads within `bed_radius`); the first settler needs no bed. Selective migration (`colony/Migrants`,
  Aaron 2026-11-03, `selective_migration` on by default): migrants wait at the Hall marked `applicant:<colony>` (not
  citizens) and hold a bed; the owner right-clicks them or uses the chat [Accept]/[Deny] links (`/lotr_colony
  accept|deny <uuid>`). At most `max_applicants` wait; unanswered ones leave after `applicant_days`. History
  `migrant_offer`, `migrant_leave` (why: denied/waited). A turned-away applicant that was unloaded is dropped on load. Who comes: `colony_migration/<faction>.json`
  (generated by `converter/colonies.py`: the faction's people minus traders, captains, leaders, rare races).
- Rare races (`recruit/<id>.json`, hand-written in `src/main/resources`): never migrate. The founder sneak +
  right-clicks an envoy (an NPC of that type) to start; gates are faction alignment, population and a per-colony
  limit. Stages of objectives: deliver (sneak + right-click the envoy again), kill_faction, kill_npc, visit (a
  waypoint), population. After the last stage the envoy walks to the Hall as a rare citizen. Shipped: Rangers of the
  North, a Wizard (Gandalf's type).
- TARDIS researchers (shared API `ResearcherProvider`, in `shared/SharedApiHooks`): a founder's colony counts every rare
  citizen plus one per `citizens_per_researcher` others; the TARDIS runs its research faster for each.
- `/lotr_colony` (info, citizens, open recruitments); admin `/lotr colony list|day [n]`.
- Not yet (RingRealms has them on 1.7.10): buildings and the validator, jobs, needs and happiness, the Ledger and
  other screens, realms on the colony, envoy spawning (rare NPCs come from normal spawning or commands for now).

## AI rulers (phase 10: `ai/`)
- Rulers from data (`ai_ruler/<id>.json`: name, faction, home waypoint, aggression, expansion, max_colonies, colony
  names); numbers in `ai/settings.json`. Each gets an AI realm (`Realms`). One `AiRulers.step` per colony day, rulers in
  id order, each with a random source from (world seed, day, ruler) so the same world replays the same.
  `wait_for_player` (default on, Aaron 2026-11-03): the AI starts the day after the first player colony exists, and
  keeps going after that (`AiRulers.waits`). `/lotr ai day` still runs days by hand.
- A day: AI colonies grow (counted population, no bodies: `Colony.ai`, `virtual_population`), strength grows, maybe found
  a colony near home (`Colonies.foundAi`, id from ruler/day/count), maybe declare war on a hostile ruler (faction
  relation is bad), march on the nearest enemy AI colony only when strength >= march_ratio x its defense, peace after
  min_war_days. A won siege is `Colonies.transfer` (history `colony_transferred`, also for handing colonies to players).
- History: `colony_founded` (with `ai`), `ai_relation`, `ai_siege`, `colony_transferred`; `AiReplay` rebuilds owners
  and wars from the log alone. Admin: `/lotr ai list|day [n]`. Not yet: attacking player colonies (needs RingRealms'
  offline protections), real soldiers on the march, trade and treaties beyond war/peace, refugees.

## Build
From the repo root: `./gradlew :lotr:build`, `:lotr:runClient`, `:lotr:runServer`, `:lotr:runGameTestServer`,
`:lotr:runData`. Shared rules are in the root `CLAUDE.md`.
