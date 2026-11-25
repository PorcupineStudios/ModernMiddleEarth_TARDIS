---
name: future-port-scope
description: Which addon modules carry into the 1.21.1 TARDIS port and what replaces the rest (Create, Refined Storage)
metadata:
  type: project
  modified: 2026-11-03T07:34:18.771Z
---

Module scope for the 1.21.1 port (was 1.26.3) (Aaron, 2026-11-03). Design: [[future-port-tardis]].

- **Kinetics: scrapped.** Replaced by Create integration.
- **Airships: only via Create: Aeronautics** integration; nothing from our Kinetics-based airships.
- **AE2: replaced by Refined Storage.**
- Kept: Souls, half-life ores, Dwarvish bank, RFTools, time travel.
- Possible new: a Galacticraft-style space program (space ores feed TARDIS milestones).
- **RingRealms is folded into the LOTR port** (Aaron, 2026-11-03): `colony` + `realm` packages in LOTR-Port, colonists built on the NPC base, realms as factions, phase 10 of the LOTR plan. Colonist research of TARDIS tech stays optional (config), as a ResearchProvider registered with the shared API.
- LOTR is future-ported as part of this project (Aaron, 2026-11-03): rebuild clean using the class map as a feature list, converter for content; private play only.
- No 1.21.1 build of a needed mod (Aeronautics, RS, RFTools): lay out other-version options.
- Port plan doc (Claude Docs): https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ
- TARDIS code architecture doc (Claude Docs): https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE
- **One shared repo** (Aaron, 2026-11-03): **PorcupineStudios/TARDIS-RingBearer** (private) holds `api/` (shared API mod `ringbearer_api`, pkg com.porcupinestudios.ringbearer.api: history log, conditions, providers), `tardis/` (`tardis_rb`) and `lotr/` (`lotr_port`, pkg com.porcupinestudios.lotrport). Mods depend on api, never on each other. LOTR-Port repo history merged in; that repo is obsolete. NeoForge 21.1.259 / ModDevGradle 2; sandbox can't reach maven.neoforged.net, so only CI verifies builds.
- **Build order: LOTR port first**, then TARDIS. Shared Roadmap (Claude Docs): https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ. Plan Review: https://claude.ai/artifact/1BciPgPdz8FbnnamU9E2Rs (recorder/history from day one, tuning data via reload listeners not datapack registries, blocks before worldgen, GameTest per done-when).
- LOTR port plan + architecture doc (Claude Docs): https://claude.ai/artifact/AgjUf8mwNgX4omnWC5zzN4 (11 phases, blocks before worldgen, pitches folded in as 'Living Middle-earth').
- LOTR port decisions (Aaron, 2026-11-03): reuse LOTR textures/sounds/map image (private play, no release); Middle-earth is a world type like the original, and a normal world can travel to it; many LOTR systems get redesigned around RingRealms, Create and TARDIS; **prefer scripts that do the work over agent brute force**.
- LOTR x RingRealms rewrite pitches (Claude Docs https://claude.ai/artifact/NA3cAi3EezqNQETSreQ2qz, Aaron 2026-11-03): every NPC is a person record; factions are realms with live diplomacy; one multi-stage quest engine; many recruitment paths; rare races as data; colony-based wars; living world. **NPC colonies are NOT every village**: a few sparse, lightweight **AI rulers** (like Empire Earth bots) with their own colonies, diplomacy and war; AI colonies can be destroyed for good, AI founds new ones; players can conquer them, and can sell/transfer colonies. Picked big ideas: story engine, strategy map mode, other ages of Middle-earth (done like ancient-past TARDIS trips).
- **Stage 1 done** (2026-11-03): shared API (history log, conditions, providers, JSON loader) + LOTR foundations (player data/alignment attachment, world data, /lotr commands); 3 GameTests pass in CI (`:lotr:runGameTestServer`). On PR #1 of TARDIS-RingBearer. Next: stage 2 of the Shared Roadmap.
- **Work mode (Aaron 2026-11-03): keep working through roadmap phases back to back without waiting for 'next phase'**, a short note per finished phase, only ask when a decision truly needs him. Converter: `converter/` (1.7.10 exporter run in CI + generate.py), see converter/README.md.
- RingRealms changes (Aaron 2026-11-03 evening): **AI rulers start only after the first player colony is founded** (no
  head start). **Selective migration**: migrants arrive at the Hall and offer to settle; player accepts/denies
  (replaces auto move-in). Both written into the LOTR plan and Shared Roadmap ("Changes from Aaron's vision").
