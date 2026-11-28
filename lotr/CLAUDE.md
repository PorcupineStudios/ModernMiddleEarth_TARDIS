# LOTR Port (Minecraft 1.21.1 NeoForge)

Future-port of The Lord of the Rings Mod (1.7.10, Update 36.15) to Minecraft 1.21.1, part of the TARDIS: Ring Bearer
modpack project. Mod id `lotr_port`, main class `LotrPort`, package
`com.porcupinestudios.lotrport`.

**This repo is public** (2026-11-11). **We have no rights to the LOTR mod's files**: its creator's permission to use
LOTR's assets was revoked, with a grace period to adjust (Aaron, 2026-11-27). LOTR's own files (textures, sounds, text,
the map) are never shipped or committed: the port reads them from the player's LOTR jar (plan:
`docs/plans/legacy-jar-references.md`). Also never committed (all in `.gitignore`): the film music (private repo
`PorcupineStudios/LOTR_music`, `lotr/docs/music.md`), the converter export (private repo
`PorcupineStudios/LOTR_export`, cloned into `converter/export/`), the LOTR mod's jar itself (private copy
`libs/LOTRMod v36.15.jar` in PorcupineStudios/RingRealms; copy it to `converter/libs/` to run the converter), and other
mods' jars. Its class map:
https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu. Rebuild features in modern code; never paste decompiled code.

## LOTR's own files come from the player's LOTR jar
LOTR's unchanged textures and sounds (2,996) and 609 textures built from them are read from the LOTR jar in `mods/`
(`client/LegacyAssets`, `client/LegacyComposer`), never shipped or committed: the lists
`src/main/resources/lotr_port_legacy_*.tsv` and the generated `src/generated/resources/.gitignore` keep them out, and
CI's "No LOTR files in git" step fails on one. How it works: `lotr/docs/legacy-jar.md`.

## Where to read (index)
This file stays short and is read on every task: open only the notes for what you're working on. New notes go into
the topic file (or a new file listed here), never into this one. All in `lotr/docs/`:

| Working on | Read |
|---|---|
| LOTR's files from the player's jar: the lists, composites, the load warning | `legacy-jar.md` |
| Terrain, the map and heights, mountains and ranges, rivers, roads on the map, build limit, cheap terrain, world type, crash site, CI world check, terrain preview, pregeneration | `worldgen-terrain.md` |
| Biome variants, Shire enclosures, Eriador, wild lands (Lone-lands), seeding, region layer, lairs and spider nests, Enchanted Mirkwood, Regions Unexplored / Biomes O' Plenty | `worldgen-regions.md` |
| Plants, flowers, reeds, plant census and tuning, shaders, trees and tree placement, saplings, tree records and felling, decoration heightmaps | `worldgen-plants.md` |
| Forest floor, fog and region looks, colour grading, sky | `sky-and-atmosphere.md`; dark-land effects and the Ring's look: `ring-and-looks.md` |
| Seas, mountains' snow and life, noise caves and aquifers, cave pockets, biome cave config, wood sets, tags, stonecutter, no vanilla monsters, waterlogging | `modern-middle-earth.md` (the to-do list: `modernize.md`) |
| Moria and Goblin Town (delves), the goblins faction, body and race | `delves-and-goblins.md`, then `delves.md` |
| Factions, realms, pledging, kill alignment; NPCs (types, trading, hiring, speech, creature models); player races and skin conversion | `factions-npcs-races.md`; big creature rigs: `rig.md` |
| Animals and their spawning, mounts and NPC riders, Rhosgobel rabbits, the harness mod's hooks | `animals-mounts.md` |
| LOTR structures: rolls, rates, overlaps, foundations, doors, template NPCs; natural NPC spawning | `structures.md` |
| Map screen, waypoints, fast travel and companions, mini-quests, the Red Book, the title screen map | `map-quests.md` |
| Colonies, immigration, rare races, TARDIS researchers, road works, borders and kingdoms, diplomacy, management screens, colonists, AI rulers | `colonies-and-kingdoms.md`, then `colonies.md`, `colonists.md`, `borders.md`, `diplomacy.md`, `ui-core.md`, `roads.md` |
| Gates and connected textures, drinks, kegs and vessels, recipes and JEI, machines and display blocks, double torches, farming, ores and raw ores, pouches, Create compat and faction crafters | `blocks-machines.md` |
| Modifiers, the One Ring and Curios, combat and weapons, armour models, thrown weapons and NPC archers, sounds | `items-combat.md`; the Ring: `ring-and-looks.md` |
| LOTR's tables from the reflection dump (achievements, titles, calendar, music regions, invasions), faction influence and the alignment bar | `lotr-tables.md` |
| What the port still lacks against 1.7.10 | `missing-features.md` |
| Music | `music.md`, `music-pack.md` |

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
- ~~Reuse LOTR's textures, sounds and map image~~ (permission given 2026-11-11, revoked 2026-11-27: they are read
  from the player's jar instead, see the top).
- Middle-earth is a world type, as in the original; in a normal world it is a dimension players travel to.
- Many LOTR systems get redesigned around RingRealms, Create and the TARDIS rather than copied.
- Prefer writing scripts that do the repetitive work (export, generate, rename) over porting class by class by hand.

## Build
From the repo root: `./gradlew :lotr:build`, `:lotr:runClient`, `:lotr:runServer`, `:lotr:runGameTestServer`,
`:lotr:runData`. Shared rules are in the root `CLAUDE.md`.
- GameTests (2026-11-18): ~136 in `gametest/`; CI's "Middle-earth world" step (`WorldCheck`, real chunks across the
  biomes) runs only when all pass. Most use template `lotr_port:empty`, which is 16x4x16 so the runner spaces tests
  apart (at 1x1x1 neighbouring creature tests' animals ran into each other). Tests that need entities to tick wrap
  their body in `ThrownTests.inLoadedArea` (forced chunks). Timing tests (`fiftyColoniesRunTenDaysWithinTheBudget`,
  median of 10 days, each against a reference job timed beside it) can still miss on a slow runner; re-run the job. An entity that walks out of
  the forced chunks freezes there (no player keeps it ticking): `rabbitEatsCrop` failed about half the runs that way
  until 2026-11-27, when the rabbit was put back before the edge.
