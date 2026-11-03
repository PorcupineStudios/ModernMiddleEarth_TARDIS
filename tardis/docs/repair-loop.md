# TARDIS: repair loop (parts, research, looks, damage)
Read when: part blocks, timed research, the Repairs & research screen, exterior looks, world damage.

- **Part blocks** (`block/PartBlock`, `repair/Parts`): a repair with `part_pos` (relative to the plot origin) gets a broken
  part block in the console room. Right-clicking it pays the repair's cost from the player (`Repairs.pay`, shared with
  research) and applies it. `Parts.sync` (run by `Progression.update`) flips the FIXED state to match the record, so
  `/tardis repair` and damage keep the blocks honest. Repairs with a `part_pos` show as AT_PART in the screen.
- **Research** (`research/Researching`): one job at a time in `TardisRecord.researching` (node + finish game time).
  `start` fires `ResearchEvent.Pre` (listeners may change cost/time or cancel), pays, then stores the job; time 0 finishes
  at once. A server tick every 20 ticks finishes due jobs, also when the TARDIS's chunks are unloaded.
  Research `stats` (e.g. `range: 16`) add to `Unlocks.stat`, so hop range = subsystem stat + research stats.
- **Screen** (`net/WorkshopNet`, `client/WorkshopScreen`): opened from the console screen. The server builds the whole list
  (state codes DONE 0, RUNNING 1, AVAILABLE 2, LOCKED 3, AT_PART 4) and resends it after each action.
- **Looks** (`look/Looks`, data `look/exterior.json`): the exterior's LOOK (0-3) is the highest rule whose requirements
  pass, after `LookEvent`. 32 blockstate variants (facing × half × look); textures are placeholders for Aaron's art.
- **Damage** (`repair/WorldDamage`): an explosion within `tuning.damage.explosion_radius` of the box lowers
  `explosion_subsystem` by `explosion_levels`.
- Tests: `gametest/RepairLoopTests` (fix a part + research a node → range up and look changes; a blast lowers the hull).
  Nothing here has been tried in a game client yet.
- **Colonist researchers**: `Researching.tick` asks the shared API (`Providers.researchers`) how many researchers the
  owner and crew have; each one (up to `tuning.research.max_researchers`) adds `speed_per_researcher` x normal speed to
  the running node by pulling its finish time earlier (`TardisRecord.hurryResearch`). The LOTR port answers from
  colonies (every rare citizen, plus one per `citizens_per_researcher` others). Not shown on the screen yet.
