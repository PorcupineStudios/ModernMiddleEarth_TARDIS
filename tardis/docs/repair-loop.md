# TARDIS: repair loop (basement repair room, research, looks, damage)
Read when: the Repair Station and repair tree, timed research, exterior looks, world damage.

- **Basement repair room** (Aaron 2026-11-03, replaced the phase 3 part blocks): `Interiors.ensure` places the
  `interior.basement` template (`tools/rooms.py repair_basement`) at `basement_offset` (0,-5,0) under the console room;
  a ladder in the console room floor at (7,0,3) leads down. The Repair Station (`block/RepairStationBlock`, at basement
  (4,1,4)) opens the tree for crew. Every repair is done there (`WorkshopNet.act` -> `Repairs.attempt`, paid from the
  player). Interiors built before this have no basement (dev worlds only). An old `part_pos` key in repair data is ignored.
- **Research** (`research/Researching`): one job at a time in `TardisRecord.researching` (node + finish game time).
  `start` fires `ResearchEvent.Pre` (listeners may change cost/time or cancel), pays, then stores the job; time 0 finishes
  at once. A server tick every 20 ticks finishes due jobs, also when the TARDIS's chunks are unloaded.
  Research `stats` (e.g. `range: 16`) add to `Unlocks.stat`, so hop range = subsystem stat + research stats.
- **Tree screen** (`net/WorkshopNet`, `client/WorkshopScreen`): opened only at the Repair Station. The server sends every
  repair, research node and room (state DONE 0, RUNNING 1, AVAILABLE 2, LOCKED 3; `Node` = subsystem row + level
  column, or research parents) and resends after each action. Tabs Repairs / Research / Rooms; research columns are
  depth by parents with lines; click a bright node; drag/scroll to pan. Untried in a client.
- **Looks** (`look/Looks`, data `look/exterior.json`): the exterior's LOOK (0-3) is the highest rule whose requirements
  pass, after `LookEvent`. 32 blockstate variants (facing × half × look), now used only for break particles: the box is drawn by
  `client/ExteriorRenderer` from Aaron's Blockbench model (see `docs/door.md`). Look 3 is his original paint; looks 0-2
  are the same texture with paint chipped off to bare wood (55/32/12%), made by `tools/police_box.py`.
- **Damage** (`repair/WorldDamage`): an explosion within `tuning.damage.explosion_radius` of the box lowers
  `explosion_subsystem` by `explosion_levels`.
- Tests: `gametest/RepairLoopTests` (fix a part + research a node → range up and look changes; a blast lowers the hull).
  Nothing here has been tried in a game client yet.
- **Colonist researchers**: `Researching.tick` asks the shared API (`Providers.researchers`) how many researchers the
  owner and crew have; each one (up to `tuning.research.max_researchers`) adds `speed_per_researcher` x normal speed to
  the running node by pulling its finish time earlier (`TardisRecord.hurryResearch`). The LOTR port answers from
  colonies (every rare citizen, plus one per `citizens_per_researcher` others). Not shown on the screen yet.
