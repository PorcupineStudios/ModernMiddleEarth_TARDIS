# Progression: record, data, unlocks, repairs (phase 1)
Read when: touching `core/`, `progression/`, `repair/`, `event/`, `/tardis`, or the `data/*/tardis/` folders.

- **Record** `core/TardisRecord`: one per TARDIS, saved in `TardisManager` (SavedData `tardis_rb_tardises` on the
  Overworld) as a codec with `data_version`. Only its methods change state; each fires its event (`event/`) and marks
  the save dirty. `revision` (not saved) bumps on every change for caches.
- **Levels**: `levels` = highest repaired level, `damage` = levels lost. `Unlocks.level` = repaired − damage, capped by
  the best cap of passed milestones (else the subsystem's `start_cap`). Damage keeps repairs and research; the same
  repair fixes the lost level again (Aaron: damage lowers levels only).
- **Stats**: named numbers per subsystem level (`range`, `charge_cap`, `room_cap`, `controls`) summed by
  `Unlocks.stat`. New stats need no code, just a key in the subsystem file and a reader.
- **Data** `progression/TardisContent`: one reload listener reads all seven folders (`subsystem stage milestone repair
  research feature cost` under `data/<ns>/tardis/`) so the validator can check across them. Drops (and logs) repairs
  with unknown subsystem/cost or a level above max, stages with unknown repairs/milestone, research in loops or with
  unknown parents. `build()` is pure for tests. `generation` bumps per reload.
- **Costs**: `{"items": [{"ingredient": {...}, "count": n}], "charge": n}`; repairs/research take a cost id or inline.
  Creative players and no-player calls pay no items; charge is always paid.
- **Features** (`tardis/feature`): exist only in data. `Unlocks.check`: unknown → no; config `forcedOffFeatures` /
  `forcedOnFeatures`; `enabled`; requirements; then `UnlockCheckEvent`. Player-free results cached by revision+generation.
- **Conditions** `tardis_rb:stage {stage, finished}`, `milestone`, `subsystem_level {subsystem, min}`, `repair`,
  `research`, `mod_loaded`; the TARDIS is passed in `ConditionContext.extra["tardis_rb"]` (`Unlocks.context`).
- **Stages**: `Progression.update` (call after changes) passes milestones whose requirements hold (none = only by
  command), and enters the next stage when the current one's repairs are done and its milestone is passed.
- Events live in this mod (`event/`), not the shared API: they carry `TardisRecord`, and the API can't depend on us.
- Create parts in costs come with the Create dependency in phase 2; the default data uses vanilla items until then.
- Tests: `gametest/FoundationTests` (5). The reload test writes a world datapack and calls `reloadResources`; it
  changes only items of `hull_plating`, so other tests (which pay no items) aren't affected.
