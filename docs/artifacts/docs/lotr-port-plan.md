# LOTR 1.21.1 Port Plan

Nov 3, 2026 · @Aaron

## Summary

We rebuild The Lord of the Rings Mod (1.7.10, Update 36.15) on NeoForge 1.21.1, with RingRealms' colonies and realms built in. It lives in the `lotr/` folder of the private shared repo, [TARDIS-RingBearer](https://github.com/PorcupineStudios/TARDIS-RingBearer), next to the TARDIS mod and the shared API both mods depend on. The LOTR port is built first, so Middle-earth exists before the TARDIS crash-lands in it; the [Shared Roadmap](https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ) gives the order across both mods.

The approach has three parts:

1. **Content becomes data.** About two thirds of the old classes are structures, biomes, NPC variants, blocks and items. A converter runs the old mod and exports them as JSON, NBT structure templates and models, so they're never hand-ported.
2. **Systems are rewritten.** Factions, alignment, NPC behaviour, hiring, trading, waypoints and the map are rebuilt in modern code, using the [class map](https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu) as the feature list. Many are redesigned around RingRealms, Create and the TARDIS rather than copied as they were.
3. **Other mods use our API.** This mod and the TARDIS mod share one small API (history log, conditions, providers) and never call each other's code.
4. **RingRealms is built in.** Colonies and realms become a module of this mod, so they use factions, NPCs and alignment directly instead of being ported on their own.

The repo stays private: LOTR's code and assets belong to Mevans.

## What the old mod is

The 36.15 jar holds 2,498 top-level classes in 17 areas. Most of the bulk is content that the converter can turn into data. The systems that need real rewriting are a few hundred classes.

| Area | Classes | Becomes |
| --- | --- | --- |
| NPCs | 444 | One NPC base plus race data files; behaviour classes rewritten |
| Structures | 386 | `.nbt` structure templates and structure sets, exported |
| Blocks | 311 | Generated blocks with blockstates; special blocks rewritten |
| Render and models | 295 | Exported entity and block models; renderers rewritten |
| Biomes and features | 278 | Biome JSON, placed and configured features |
| Items and recipes | 186 | Generated items; recipes and loot exported as JSON |
| Other entities | 144 | Animals, projectiles and AI goals, rewritten |
| Network packets | 137 | Fewer modern payloads; data attachments and registry sync replace many old packets |
| GUIs | 86 | Rewritten screens, the map screen first |
| Tile entities and containers | 82 | Rewritten block entities and menus |
| Mod core | 45 | Split into registries, config and events |
| Commands | 25 | Rewritten with Brigadier |
| Client core | 22 | Rewritten |
| Factions and players | 19 | Rewritten first: factions, alignment, fellowships, titles |
| Mini-quests | 14 | Quest engine rewritten; quest definitions as data |
| Map and waypoints | 14 | Rewritten; waypoint list as data |
| Spawning and invasions | 10 | Rewritten; spawn lists and invasions as data |

The hubs from the map show what to build first: `LOTRMod` (used by 1,132 classes), `LOTRLevelData`, `LOTRPlayerData`, `LOTRFaction` and `LOTREntityNPC` each sit under 300 or more classes.

## The converter

The converter is a small 1.7.10 mod that loads alongside the old LOTR jar on a headless server and writes its content out as modern data. It runs once per export, and its output feeds the new mod's data generation.

&#91;embedded content: converter pipeline · exported content vs hand-written systems\]

- **Structures.** Each structure generator builds into an empty test world, and the exporter saves the blocks as an `.nbt` template. The ID table turns old block ids and metadata (0 to 15) into new blockstates.
- **Recipes and loot.** Read from the old recipe lists and chest tables (`LOTRRecipes`, `LOTRChestContents`) and written as JSON.
- **NPC data.** Trades, spawn lists and faction values come out of `LOTRTradeEntries`, `LOTRBiomeSpawnList` and the faction classes.
- **Models.** The old Java models are built in memory and their boxes are written as block and entity JSON models.

* **Assets.** Since we reuse LOTR's art, the converter also copies textures into the 1.21.1 folder layout and writes `sounds.json` and the lang files for every language LOTR ships.
* **ID table.** A script reads the old jar's block and item list and writes the metadata-to-blockstate table plus the new registrations, so no block is typed by hand.
* **Random structures.** LOTR structures are random generators, so one export captures one roll. The exporter runs several seeds per structure, turns the varying spots into structure processors and jigsaw pools, and writes chests and NPC spawns as marker blocks instead of baked contents.

The converter lives in a `converter/` folder of the shared repo as its own Gradle build on the 1.7.10 toolchain, not part of the main build, so the main build never needs the old jar.

## Code architecture

The mod follows the same rules as TARDIS: Ring Bearer: numbers and content in data, state changed only through owning classes, other mods only through an API and `compat/<modid>`.

| Package | Holds | Replaces |
| --- | --- | --- |
| `shared` | Hooks into the shared API: our conditions, providers and history writes | Nothing; the old addons used LOTR internals |
| `registry` | One deferred register per area: blocks, items, entities, menus, sounds | The `LOTRMod` god class |
| `faction` | Factions, alignment, ranks, titles, live diplomatic states | `fac/`, `LOTRAlignmentValues` |
| `player` | Per-player data as a data attachment | `LOTRPlayerData` |
| `world` | World-wide saved data: fellowships, custom waypoints, invasions | `LOTRLevelData` |
| `npc` | Two bases, people and creatures; races as data; light NPCs promoted to person records when they matter; hiring, trading, families, speech | 444 NPC classes |
| `quest` | One multi-stage quest engine for mini-quests, recruitment and story events, built on the shared conditions | `quest/` |
| `worldgen` | Middle-earth dimension and world type, map-driven biome source, structures | `world/` |
| `map` | Waypoints, fast travel, the map screen's server side, later the strategy map | `world/map/` |
| `content` | Blocks and items that need code (crafting tables, barrels, banners) | Special cases in `block/`, `item/` |
| `colony` | RingRealms colonies: founding, buildings, jobs, production, people, research, colonist pathing and routines | RingRealms `colony/`, `nav/`, `life/` |
| `realm` | Player and AI realms, diplomacy, selling and transferring colonies | RingRealms `realm/` |
| `ai` | AI rulers: seeded daily decisions, campaigns, sieges | LOTR invasions |
| `client` | Renderers, models, screens | `client/` |
| `compat` | Create and other optional mods | The old compatibility package |

**Data.** Things world generation and saved holders need are datapack registries: `faction`, `race` and `waypoint`. Everything a pack maker tunes during play loads through reload listeners, so `/reload` and KubeJS apply it live: trade tables, spawn lists, invasions, quests, NPC speech, colony buildings, jobs and research. Biomes, structures, loot and recipes use vanilla's own data formats. A validator checks every file at load.

**Player data** is a NeoForge data attachment with a Codec: alignment per faction, pledge, titles, unlocked waypoints, fellowships joined, mini-quests in progress. It copies across death and dimension changes.

**The shared API.** The mod registers its conditions (`lotr_port:alignment`, `lotr_port:pledged`, `lotr_port:colony_tier`) and providers (Middle-earth as a start dimension for crash sites, colonists as TARDIS researchers) with the shared API, and writes colony, battle and AI events to the shared history log. It never calls TARDIS code. Its own events (`AlignmentChangeEvent`, `NpcHiredEvent`, `PledgeEvent`) are for KubeJS and addons.

## Middle-earth world generation

The old mod paints Middle-earth from a map image: each pixel's colour picks a biome, so the Shire is always where the map says. We keep that idea and rebuild the machinery on 1.21.1's data-driven world generation.

- **Dimension.** `lotr_port:middle_earth` is a datapack dimension with its own dimension type and noise settings. As in the original mod, Middle-earth is a world type on the create-world screen and players start there. In a normal world it still exists, and players travel to it.
- **Biome source.** A custom `BiomeSource` reads the biome map image and returns the biome for each position, with edge blending so borders aren't pixel steps. The map image and its colour table are data, so a pack can swap the map.
- **Fast biome lookup.** 1.21.1 asks for a biome every 4 blocks, millions of times while generating. The map is pre-processed into a lookup table at load and cached per region, and phase 3 benchmarks it on the small map.
- **Terrain.** Each biome carries height and roughness values in data. A custom density function blends them across nearby biomes, so mountains rise in the Misty Mountains and plains stay flat in Rohan.
- **Roads and rivers.** Rivers come from the map image in the old mod; we keep that as a map layer. Roads are typed paths in code (\`LOTRRoadType\`); they become road definitions in data, drawn as features.
- **Structures.** Exported `.nbt` templates grouped into structure sets per faction region, with spacing rules in data. Large builds such as Minas Tirith become jigsaw structures.
- **Spawning.** Spawn lists and invasions come from data per biome and faction, replacing `LOTRBiomeSpawnList` and `LOTRInvasions`.

This is the riskiest part of the port. Phase 2 starts with a small test map before we scale up to the full one.

## RingRealms: colonies and realms

RingRealms becomes part of this mod instead of a separate port. On 1.7.10 it reached into LOTR's internals (`LOTRLevelData`, `LOTRPlayerData`, `LOTRFaction`, reflection for private fields). Inside the same mod it calls factions, NPCs and alignment directly, and that whole layer disappears.

- **Size.** 158 classes, about 27,600 lines. Most of it, `colony/` (85 classes, about 16,000 lines), is game rules: the building validator, jobs, the production planner, the treasury, people and research. That is plain Java logic and moves over with small changes, unlike LOTR's rendering and world code.
- **Colonists are NPCs.** Colonists use the NPC base from the NPCs phase with a colonist role, so races, speech, families and hiring are shared, not duplicated.
- **Realms are factions.** A player's realm registers in the faction registry, so alignment, wartime relations and the API events treat it like any other faction.
- **Definitions as data.** Building types, jobs, research nodes and edicts already load from JSON in RingRealms; they become datapack registries like LOTR's.
- **Saves.** Colony state stays event-sourced with world ticks, so any past can be rebuilt for time travel, stored as SavedData with Codecs.
- **Screens.** The book screens built on LOTR's Red Book are rewritten together with this mod's own screens.
- **TARDIS links.** Colony history goes into the shared history log, so time trips rebuild colonies from it; trip events also come through the shared API (`TimeTripEvent`), replacing RingRealms' `compat/timetravel`. Colonists researching TARDIS tech is a `ResearchProvider` registered with the shared API, still behind a config option.

RingRealms' own `docs/` folder is the feature list, the way the class map is for LOTR.

## Living Middle-earth

These come from the [LOTR Rewrite Pitches](https://claude.ai/artifact/NA3cAi3EezqNQETSreQ2qz), with the fixes from the [Plan Review](https://claude.ai/artifact/1BciPgPdz8FbnnamU9E2Rs).

- **People records, promoted when they matter.** NPCs start light. One becomes a saved person (name, race, culture, personal alignment, traits, home, job, memory of the player) when hired, named, recruited, made a quest giver, living in a colony, or dealt with by a player. Random orcs and bandits leave nothing behind.
- **Factions are the great powers.** Gondor, Mordor and the rest keep LOTR's balance, now with live diplomatic states (war, pact, alliance, vassal). Pledging makes a player a vassal in the same system as RingRealms' vassal charters. Alignment also grows from trade, quests and defending a faction's land.
- **AI rulers are small realms.** A few AI rulers, like bots in Empire Earth, each run a realm aligned with a faction, using the realm slots players use. They start only once the first player colony is founded, so they get no head start; then they found sparse, cheap colonies, trade, sign treaties and go to war. Their colonies can be destroyed for good, and they can found new ones. Players can conquer them, and can sell or hand over their own colonies.
- **Deterministic AI.** Every AI decision is seeded per in-game day and written to the shared history log. Pasts rebuild exactly, and a TARDIS future trip can run the AI forward to show a real forecast.
- **One quest engine.** Mini-quests, rare-race recruitment and story events are all multi-stage quests built on the shared conditions.
- **Recruitment paths.** Selective migration (migrants come to the Hall and offer to settle; the player accepts or turns each one away), hiring in LOTR villages and AI colonies, refugees from razed AI colonies, captives and defectors from wars, rare races won over by quests.
- **Rare races as data.** Each race file says whether it migrates, its recruitment quest, envoy spawns and per-colony limit. Goblins, half-trolls and the Taurethrim become ordinary base races.
- **Colony wars.** LOTR invasions become campaigns: real soldiers mustered from colonies, march warnings, sieges, conquest, with RingRealms' offline protections.
- **Story engine and strategy map.** Data-driven world events on the quest engine, and the map screen as a command view for realms.
- **Other ages.** Second Age Númenor and First Age Beleriand as their own maps on the same map-driven world generation, reached by TARDIS like the ancient past. After full Middle-earth works.

## Build phases

Blocks come before world generation, because Middle-earth is built from LOTR blocks. Each phase ends playable, and each done-when is a GameTest.

1. **Foundations.** The shared repo and CI, the shared API (history log, conditions, providers), registries, config, player data, world data, reload listeners and the data validator.
   - Done when: a GameTest sets a player's alignment and it survives a restart, and the history log records a block change.
2. **Converter, blocks and items.** The ID table script, then exports of blocks, items, assets, lang files, recipes and loot; generated blocks and items.
   - Done when: every LOTR block and item exists in creative with its texture, name and recipe.
3. **Middle-earth, small.** The dimension and world type, the map-driven biome source with fast lookup, terrain blending on a small test map.
   - Done when: a new Middle-earth world spawns there, walking between test biomes shows smooth terrain, and biome lookup stays within its time budget.
4. **Factions and realms.** Factions from data as the great powers, alignment, ranks, pledging, live diplomatic states, and the realm structure players and AI rulers will use.
   - Done when: a GameTest kill event raises alignment with the right factions, and two factions change diplomatic state by command.
5. **Full Middle-earth.** The full map, all biomes, rivers, roads and features.
   - Done when: every biome on the map generates, and the start-dimension provider puts a crash site at the Middle-earth spawn.
6. **NPCs.** The people and creature bases, person records with promotion, races (rare ones too) from data, hiring, trading, families and speech.
   - Done when: a player hires, trades with and commands NPCs from two factions; a hired NPC keeps its record across a restart, and a random orc leaves none.
7. **Structures and spawning.** Multi-seed structure exports with processors and jigsaw pools, marker blocks, spawn lists, invasions.
   - Done when: faction structures appear in their regions with varied layouts, filled with their NPCs.
8. **Map, waypoints and quests.** The map screen, fast travel, the multi-stage quest engine, fellowships and titles.
   - Done when: a player fast travels between waypoints and finishes a three-stage quest.
9. **Colonies and realms.** The RingRealms module: founding, buildings, jobs, production, people, research, realms, recruitment paths, selective migration (accept or deny migrants at the Hall), rare-race recruitment, selling and handing over colonies.
   - Done when: a player founds a colony, colonists move in and work, a rare race joins by quest, and the colony is handed to another player.
10. **AI rulers and colony wars.** Seeded AI rulers that wake once the first player colony is founded, diplomacy, campaigns, sieges and conquest.
    - Done when: two AI rulers found colonies and go to war, a colony changes hands, and replaying the history log gives the same result.
11. **Story engine, strategy map and polish.** World events, the strategy map, remaining renderers and screens, Create recipes where they fit.
    - Done when: a story event runs through its stages, and a realm can be managed from the strategy map.

Other ages of Middle-earth come after TARDIS time travel; the [Shared Roadmap](https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ) places them.

## Risks

- **Licensing.** LOTR's code and assets belong to Mevans. The mod is for private play and the repo stays private.
- **World generation is the hard part.** A map-driven biome source with blended terrain is custom work against 1.21.1's density functions. Phase 2 proves it on a small map first.
- **Size.** Even with the converter, NPC behaviour, the map screen and the quest engine are large rewrites. Phases keep each one playable on its own, and scripts do the repetitive work (exporting, generating, renaming) instead of porting class by class by hand.
- **The converter needs a 1.7.10 setup.** It runs inside the old mod, so it lives in its own Gradle subproject on the old toolchain, like the existing addon.
- **Performance of the living world.** Person records, colonies, AI rulers and the history log all cost time and disk. Each system gets a budget, measured by a perf command and a fixed-seed benchmark world in CI.

## Decisions

- **Assets.** We reuse LOTR's textures, sounds and map image.
- **Mod id.** `lotr_port`.
- **Start.** Middle-earth is a world type, as in the original. A normal world still has Middle-earth as a dimension to travel to.
- **Redesign over copying.** Many LOTR systems are rewritten with RingRealms, Create and the TARDIS in mind. Each phase decides which ones change.
- **RingRealms.** Part of this mod (see its section above).
- **Repository.** One private repo with the shared API, the TARDIS mod and this mod; the mods never depend on each other.
- **Build order.** This mod goes first, before the TARDIS mod.
- **Crash site.** The TARDIS lands in whichever dimension the world starts in.
- **Pitches.** Accepted, with the review's fixes (see Living Middle-earth).
- **Old worlds.** New worlds only; 1.7.10 LOTR and RingRealms saves are not converted.
- **AI rulers wait for the player.** No AI colonies exist until the first player colony is founded, so the AI gets no head start (Aaron, 2026-11-03).
- **Selective migration.** Changed from RingRealms: migrants don't just move in. They arrive at the Hall and offer to settle, and the player accepts or turns each one away (Aaron, 2026-11-03).
