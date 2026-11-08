# TARDIS: the console model and its controls
Read when: the console block/renderer, console controls (data `tardis/control/`), clicking controls, the course.

- **2026-11-08: blank slate + themed sides** (Aaron: "a plain texture ... a blank slate for us to add the controls on
  top ... hit boxes that are WAILA compatible"; "each side of the console should have a theme and the monitor should
  say different things depending on what console side its at"; then the sides: "1. X coords (see tardis mod 1.7.10
  for the XYZ coord controls) 2. Z coords 3. Dimension + Time controls 4. Exterior controls (facing direction,
  dematerialization, entity, physics object) 5. Y + save/load coord controls 6. Room controls", plus "additional
  controls in each side that are the exception to the rules"; "Feel free to make new models").
  - `tools/capaldi.py` leaves the hand-built model's controls out (all but the monitor `ctl_screen_1`) and gives the
    body `textures/entity/capaldi/console_plain.png` (one colour). It also writes each panel's frame (`panels`:
    origin = middle of the outer edge on the top, right, normal, down-slope; the top rises 21.6 degrees from the
    outer edge at 1.31 blocks from the rotor axis) and the monitor's stand (`monitor`: tilt, lift, inward).
  - `tools/console_layout.py` places the small models (`docs/flight-controls.md`, 39 now) on the sides and writes
    the control data files (old ones deleted), lang (`tardis_rb.control.<id>`, `action/click/side.*`) and the review
    pictures `tools/console_layout/side_<k>.png` + `overview.png`. Rows stack up from the operator's edge; a side
    that doesn't fit shrinks (not below 0.6). Sides: 0 X (door side; exception: relative switch), 1 Z (exception:
    randomiser), 2 Dimension & time, 3 Exterior, 4 Y & memory, 5 Rooms (exceptions: door control, twin gauge).
  - Coordinates (after the 1.7.10 console, tuning `console.coordinates`): X and Z have 7 slots each: levers 1-4
    (-6..6; lever 1 x 4^(wheel 1 + 1), lever 2 x 2^(wheel 2 + 1), lever 3 x 6, lever 4 x 1), power wheels 1-2
    (0..7, go round) and lever 5 (x 71); Y has 4 base-4 digits (x64, x16, x4, x1) + `y_offset` -64. Relative switch
    (default on: X/Z count from where the TARDIS stands), land-on-ground switch (default on: Y = the surface).
    Sneak-click turns a slot down. Memory bank: 20 slots, save switch on = a button saves, off = loads.
  - Control data (`progression/Control`): `model`, `panel`, `u`, `v`, `turn`, `scale`, `actions` (clickable part ->
    action; slot actions `x0`..`x6`, `z0`.., `y0`..), `shows` (gauges), `requirements`. `model: monitor` = the console's
    own monitor. Controls show from navigation 1 (dimension dial from milestone restored_core).
  - Actions (`ConsoleControls`): coordinate slots, relative, land, save, memory, facing (doors on landing, 0-3 =
    S W N E), destination (dimension, sneak back), random (the small levers), jump (= take off: hop in this dimension
    or jump to the picked one, `takeOff`), handbrake, door, screen, none. Old course actions still load.
  - State: `ConsoleBlockEntity.state` holds every setting by role (+ the value before, for animating); a part bound to
    a role is drawn at its value (`ControlModels.pose`), others by their click count. Memory slots in the block entity.
    Block entity data version 2. Uses/last use per part keyed `<control id>|<part>`.
  - `Flight.hop/jump` take an optional landing facing.
  - Client: `ControlModels` (load + pose), `ConsoleModel.placement` / `monitorMatrix`, `ConsoleAim` (the one hit test:
    each clickable part's posed box in the control's own space against the view ray turned into that space), used
    by `ConsoleInput` (sends `ConsoleNet.Press(pos, "<id>|<part>")`) and Jade. The client reads the coordinate rules
    from its own tuning copy (the defaults on a dedicated server), only for Jade's totals.
  - **Monitor per side**: `ConsoleControls.readout` picks the side from the monitor angle (`ConsoleLayout.side`,
    home = side 1) and shows that theme's lines. The monitor always stands raised.
  - Aaron 2026-11-08 20:10: the monitor stays level as it turns, high enough to clear the controls: upright glass
    (`MONITOR` tilt 68.4 in capaldi.py) lifted 0.65, so it spans y 1.41-1.91 over the console block on every side
    (tallest control top 1.31, exterior side). Readouts lead with the target: "Target X n", "Target Z n",
    "Target Y n" / "Target Y: the ground", "Target <name>[<dimension id>]" and "Target time: ...".
  - **Target time** (side 2, tuning `console.time`): time drums `time0..time2` (digits 0-9, first highest), time
    unit dial `time_unit` (minutes 1200 ticks, hours, days 24000, years; the model has 4 notches), time lever
    `time_from` (off: "12 minutes ago", on: "12 minutes from the beginning of time"). `ConsoleControls.targetTick`
    gives the tick (clamped 0..now). Stored, shown and saved in memory slots only: time travel isn't built yet.
  - **Jade** (`compat/jade`, compile only `maven.modrinth:jade`, CI puts its jar in the test-build release): a ray
    trace callback hands Jade the console block when `ConsoleAim` finds a control nearer than any block; the tooltip
    names the control (and part), its setting (with the axis total), what a click does and its side.
  - **Fast return** (side 2, `fast_return`): arms going back; `takeOff` then flies to where the console last took
    off from (`ConsoleControls.lastSpot`, saved in the record's module data `console`: `last_dimension`, `last_pos`),
    a hop or a jump through an open destination in that dimension, and disarms. **Schema chooser** (side 5):
    `schema_prev`/`schema_next` page the room plans (id order, role `schema`), `add_room` builds it (`Rooms.add`,
    paid like the repair screen), `schema_info` says why it can't be added. Still planned: room deleter, sonic holder,
    telepathic circuits, and the exterior side's phase key/lever, thrusters, flight yoke (box as entity/physics).
  - Test: `ConsoleTests` (X levers = +10, monitor over side 0 shows the target X, target time ago/from the beginning, fast return, schema paging, memory save/load, handbrake, the
    dematerialisation lever flies there). Rendering, clicking and Jade are untried in a client, and none of this has
    compiled yet (CI blocked by GitHub billing, 2026-11-08).
- Older notes below describe the hand-built controls (`part`, `ctl_*` click boxes): replaced by the above.

- **2026-11-04: the Capaldi console room** (hand-built in another thread, sources in `tools/capaldi/`,
  `python3 tardis/tools/capaldi.py` writes everything). Aaron: what looks like full blocks is real blocks (structure
  `interior/console_room_capaldi`, blocks floor_plate, wall_panel, frame, roundel_wall(_lit), light_strip,
  grille_plate, frame/floor_plate stairs, frame_slab; glowing ones have a full-bright overlay element via
  `neoforge_data`), only non-cubes stay models: console + pedestal down to the lower deck, time rotor, arches, all
  drawn by `ConsoleRenderer` from `assets/tardis_rb/console/capaldi.json` (`client/ConsoleModel`, quads in blocks from
  the console block's corner; OBJ faces mapped to bbmodel elements in order to get the `ctl_*` groups). Invisible
  `collision_post` (arch pillars), `collision_pillar` (pedestal) and `console_hull[part]` (8 cells round the console,
  hexagon apothem 1.12, 19 px tall; `ConsoleHullBlock`) give collision with no outline, so they never block clicks.
- Controls name their model part (`part`; 12 working ones mapped in `tools/capaldi.py` CONTROL_PARTS, the other 16
  are `decor_*` with action `none`, shown from navigation 1). Click boxes come from the model file; the server checks
  reach from the console's centre (`ConsoleLayout.REACH + RADIUS`). Used controls sink, levers/switches lean 35
  degrees while flipped (odd use count), rotor column bobs, ceiling rings spin. Untried in a client.
- **No console screen** (Aaron 2026-11-04: 100% physical controls like the 1.7.10 mod). `ConsoleNet` has only
  `Press`. The monitor (`screen_1`, action `screen`: says where the TARDIS is) shows `ConsoleControls.readout` (name,
  position, dimension, course, picked destination, charge), worked out on the server every 20 ticks and synced in the
  block entity; `ConsoleRenderer` writes it on the glass (`ConsoleModel.Glass`, the highest big face of the part).
- **Monitor faces you**: clicking the time rotor's column (client ray test against a 0.4-radius box round the pivot,
  sent as the pseudo-control `column`) or the console block sets `monitor_angle` (`ConsoleControls.faceMonitor`,
  atan2 from `ConsoleLayout.PIVOT_X/Z`). The renderer eases toward it and turns `screen_1` round the pivot, raised
  `ConsoleModel.MONITOR_LIFT` (0.5) once off its panel so it clears the other panels' controls; its click box turns
  the same way (`ConsoleModel.monitorBox`). Untried in a client.
- Old notes (the placeholder console, 2026-11-03):
- Aaron 2026-11-03: a console like the original mod's, a custom model with buttons, levers and dials that don't follow
  block shapes; controls appear on it only once repaired. The block is just the central column
  (`block/ConsoleBlock`, render shape ENTITYBLOCK_ANIMATED); `client/ConsoleRenderer` draws the plinth, six sloped
  panels, a bobbing rotor and each control (ModelPart layer `tardis_rb:console`, texture `textures/entity/console.png`,
  placeholder colours for Aaron's art). (The flight screen it opened is gone.)
- Shape math shared by renderer, click test and server reach check: `console/ConsoleLayout` (panel 0 faces +z, each
  next one +60 degrees; `point(panel, u, v)` is a control's centre from the block's corner).
- Controls are data: `tardis/control/<id>.json` = kind (button/lever/dial/monitor), action, panel 0-5, u, v,
  requirements. Actions in `console/ConsoleControls.ACTIONS`: screen, x/y/z_up/down, step, reset, hop, destination,
  jump. Step sizes: tuning `console.steps`. The wreck shows only the monitor; navigation 1 brings the course buttons,
  step dial and hop lever; milestone restored_core brings the destination dial and jump lever.
- `ConsoleBlockEntity` refreshes its shown list every 20 ticks on the server (`ConsoleControls.shown`) and syncs it with
  the course (dx/dy/dz, step, destination) and per-control last use/use count (button sink, lever flip, dial turn).
- Clicks: `client/ConsoleInput` catches right-click, ray-tests each shown control's box (`HIT` half size, `REACH`) on
  loaded consoles, and sends `ConsoleNet.Press(pos, id)`; a block in front wins. The server checks crew, that the
  control is shown, and reach, then `ConsoleControls.use`.
- Test: `gametest/ConsoleTests` (controls appear with the navigation repair; dial + button set a course; lever hops).
  Rendering and clicking are untried in a client.
