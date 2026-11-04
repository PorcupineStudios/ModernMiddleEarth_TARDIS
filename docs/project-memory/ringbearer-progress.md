---
name: ringbearer-progress
description: Build status of the 1.21.1 TARDIS-RingBearer repo (which roadmap phases are done) and Aaron's keep-going instruction
metadata:
  type: project
  modified: 2026-11-03T12:14:38.370Z
---

Repo PorcupineStudios/TARDIS-RingBearer (private), branch claude/project-thread-6vl44n, PR #1 (not merged as of 2026-11-03).
Order follows the Shared Roadmap artifact (https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ).

Status 2026-11-03: LOTR port phases 1-9 done and green in CI (Stages 1-3 complete).
- Phase 5: full map world, rivers, roads, trees, ores, crash site at the Shire spawn (CI world check).
- Phase 6: one data-driven NPC entity, 350 LOTR people types, vanilla trading screen with silver coins,
  captains' [Hire] chat links, follow/guard commands, People records only for hired units.
- Phase 7: 82 LOTR structures (3 seeded layouts each, jigsaw) with their NPCs, biome NPC spawn tables + spawner;
  CI world check finds a Gondor and a Mordor structure with their NPCs. Gaps: villages/town walls, invasions,
  respawner blocks, non-person creatures.
- Phase 8: 274 waypoints, region unlocks, fast travel with cooldown, map screen (key M, untried in a client),
  37 mini-quest pools (collect/kill) given by 287 NPC types. Gaps: bounty/retrieve quests, fellowships, titles.
- Phase 9: RingRealms colonies in lotr_port `colony/`: Charter founding, growth by beds + faction migration, rare
  races by multi-stage recruit quests (Rangers, Wizard). Gaps: buildings/validator, jobs, needs, Ledger, realms.
- TARDIS phase 1 (Foundations, pushed 2026-11-03): record + SavedData, 7 data folders with validator, Unlocks,
  condition types, repairs/damage, stages/milestones, /tardis admin commands; notes in tardis/docs/progression.md.
- TARDIS phase 2 (green 2026-11-03): police box, interior dimension + plots, crash site at start spawn, salvage
  wrecks, short hops + console screen, Create winder; CI "TARDIS world" dedicated-server check. Create comes from
  Modrinth (maven.modrinth:create) because maven.createmod.net's certificate expired. Notes: tardis/docs/wreck-flight.md.
- TARDIS phase 3 (green 2026-11-03): part blocks fixed by hand, timed research with stat bonuses, Repairs & research
  screen, 4 exterior looks, explosion damage. Notes: tardis/docs/repair-loop.md.
- TARDIS phase 4 (green 2026-11-03): rooms added behind the console room (data tardis/room/), room_cap stat.
  Notes: tardis/docs/rooms.md.
- TARDIS phase 5 (green 2026-11-03): destinations (data tardis/destination/), Overworld opens with milestone
  restored_core, Nether/End stay locked, Flight.jump, console picker. In a Middle-earth world the "other" dimension
  is lotr_port:vanilla_overworld (lotr ships the dimension + destination data). tardis/docs/destinations.md.
- Stage 4 colonist research (green 2026-11-03): shared API ResearcherProvider; lotr colonies count rare citizens + one
  per citizens_per_researcher; TARDIS research runs faster per researcher. STAGE 4 COMPLETE.
- LOTR phase 10 (green 2026-11-03): AI rulers in lotr `ai/` (6 rulers in data, seeded daily
  step, sparse AI colonies, wars, sieges, Colonies.transfer, AiReplay from history). Notes in lotr/CLAUDE.md.
- Vision rework (2026-11-03 evening, Aaron said "start on next phase"): AI rulers wait for the first player colony
  (`wait_for_player`); selective migration (`colony/Migrants`, accept/deny at the Hall); basement repair room +
  Repair Station + tree screen (part blocks removed; CI world check finds it); custom console model with data-driven
  controls (`tardis/control/`, `console/`, docs/console.md). Rendering/clicking untried in a client.
- Stage 5 = LOTR phases 10-11 (AI rulers, colony wars, story engine, strategy map) + TARDIS phase 6 (space).
- (done) Stage 4 "The wreck" = TARDIS phases 1-5 (tardis/ subproject), done-when: the wreck lands at the start
  dimension's spawn and is repaired to dimension travel; colonists can research TARDIS tech.

**Why:** Aaron first asked to keep working through the phases until usage runs out; on 2026-11-03 19:07 UTC he said
"Finish your tasks then wait for further instructions" (stopped after phase 10).
Aaron 19:46 UTC: "Start on next phase when done" -> did the vision rework. **How to apply:** after the rework, the next
phase is stage 5 (LOTR phase 11 / TARDIS phase 6 space); ask Aaron before starting it unless he said to keep going; CI is the only compiler
(sandbox can't reach maven). See [[future-port-scope]].
