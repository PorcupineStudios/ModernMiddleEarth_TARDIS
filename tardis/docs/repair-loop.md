# TARDIS: repair loop (Repair Station, research, looks, damage)
Read when: the Repair Station and repair tree, timed research, exterior looks, world damage.

- **Repair Station** (`block/RepairStationBlock`): opens the tree for crew (as does the repair core below)
  (`WorkshopNet.act` -> `Repairs.attempt`, paid from the player). The basement room and its ladder were removed
  (Aaron 2026-11-04): the lower deck will do this job differently, so for now the station is placed by hand
  (creative tab "TARDIS"). An old `part_pos` key in repair data is ignored.
- **Repair core** (Aaron 2026-11-10: under the console in the basement, "an extension of the column ... the control
  panel that will eventually handle repairs and upgrades", with "plenty of girth" for visual cues, a blank slate for
  now). Built into the console room by `tools/capaldi.py` (CORE, CORE_CELLS): `repair_core` (`block/RepairCoreBlock`,
  block entity `RepairCoreBlockEntity`, light 10) at 0,-3,0 from the plot origin and invisible `core_casing` in the
  other 17 cells of 3 x 3 x 2 on the lower deck (floor top y -3, up to the platform at y -1), replacing the pedestal
  post. Clicking any cell opens the same tree as the Repair Station (`RepairStationBlock.open`); the station stays,
  placed by hand. `client/RepairCoreRenderer` draws a hexagonal column on the console's rotor axis (0.5, 0.25 in the
  block; 2.4 flat to flat, 2 tall): plinth, body, collar, a top seen through the grating, and six panels with ribs.
  The block outline is hidden on the core (`RenderHighlightEvent`).
  - **Slots** come from `assets/tardis_rb/core/layout.json` (reread on F3+T): per face (0 faces the main door, +z,
    then anticlockwise seen from above, like the console's sides) a name and slots `{id, kind, x: [from, to],
    y: [from, to]}` in 0..1 of the panel. Faces now: 0 status (hull, oxygen, charge bars), 1 upgrades (6 lamps),
    2 research (screen + progress bar), 3 subsystems (9 lamps), 4 power (screen + 2 bars), 5 access (screen + lamp).
    All blank: drawn as an inset by kind (bar, lamp, screen).
  - **Adding a visual cue later**: put the state in `RepairCoreBlockEntity` (save it and sync it with
    `getUpdateTag`/`getUpdatePacket`, set from the server when the record changes, e.g. on `RepairEvent.Post` or the
    health change), then in `RepairCoreRenderer.slot` pick the colour by the slot's `id` (a lit lamp for an installed
    upgrade); for a bar's fill, draw a second `rect` over the slot from y0 up to the fraction. Full-bright parts:
    pass `LightTexture.FULL_BRIGHT` instead of the block's light. New slots or faces need only the layout file.
  - Test: `RoomTests.theBasementHasTheRepairCoreUnderAGrating`. Seen in the dev client (screenshots 2026-11-10).
- **Floor grating** (Aaron 2026-11-10: the mesh between the console room and the basement "more transparent and not
  go all the way to the edges, make a 2-3 block gap"): `grille_plate` is now a see-through grating (a 3 px plate at
  the block's top, 4 x 4 px open squares, cutout, `TransparentBlock`, no light of its own), only in the 5 x 5 round
  the console (`tools/capaldi.py` GRATING), leaving 2 blocks of solid floor plate to the platform's edges; the old
  dotted grilles elsewhere (and on the lower deck's floor) are floor plate now. The platform didn't need widening.
- **Old interiors** keep their old layout: `/tardis rebuild_interior` moves a TARDIS into a fresh plot built on the
  current one (`Interiors.rebuild`).
- **Upgrades (research)** (reworked 2026-11-19, Aaron: no research time, no rooms menu, show exactly what buying does,
  multi-level nodes with level/max): `research/Researching.start` buys the next level at once (paid by the player:
  items, and the TARDIS: charge). A node is one level (`cost`, `stats`) or several (`levels: [{cost, stats}]`, stats add
  up; `Research.statsUpTo`). The record keeps `research` (level 1 done, read by `tardis_rb:research` conditions) plus
  `research_levels` for levels above 1 (`TardisRecord.researchLevel` / `advanceResearch`; old saves read as level 1).
  Jobs from older saves still finish on their old timer, and colonist researchers only hurry those. Research `stats`
  (e.g. `range: 16`) add to `Unlocks.stat`.
- **Tree screen** (`net/WorkshopNet`, `client/WorkshopScreen`): tabs Repairs (one node per subsystem; clicking does
  its next repair) and Upgrades (columns by depth, lines from parents). Each node shows `level/max` in its bottom right;
  the tooltip gives the next level's cost and each named number it changes, now -> after (`WorkshopNet.Change`, names
  from lang `tardis_rb.stat.<key>`, node names `tardis_rb.node.<id>`). Untried in a client.
- **Upgrade parts** (2026-11-19, `tools/upgrade_parts.py`, items `TardisBlocks.PARTS`): artron battery (bronze or
  brass, gold, a gem), hull plate, temporal circuit, chronon lens, stabilizer coil; placeholder pictures until Aaron's
  art. Every repair costs its subsystem's part x its level (+ its old charge); the upgrade nodes cost parts too. The
  script also writes the upgrade tree and the console controls' requirements.
- **Console controls** (Aaron 2026-11-19: only flight unlocks for now): the upgrades `materialised_flight` (needs
  navigation 1: thrusters, flight yoke, phase lever and key, door control, facing compass; features piloting,
  thrusters, exterior phase) and `dematerialised_flight` (after it: demat lever, handbrake, land switch, X/Y/Z
  levers and wheels, dimension dial, relative switch, randomiser, fast return, save switch, memory bank, scanner
  screen; feature short hop). Time controls, room controls, the sonic holder and telepathic circuits can't be
  unlocked yet (`not always`). Tests unlock flight with `gametest/FlightReady`.
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
