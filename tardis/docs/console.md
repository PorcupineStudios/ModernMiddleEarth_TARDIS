# TARDIS: the console model and its controls
Read when: the console block/renderer, console controls (data `tardis/control/`), clicking controls, the course.

- **2026-11-08: blank slate + themed sides** (Aaron: "a plain texture ... a blank slate for us to add the controls on
  top. Then add the controls on top, complete with hit boxes that are WAILA compatible"; "each side of the console
  should have a theme and the monitor should say different things depending on what console side its at").
  - `tools/capaldi.py` leaves the hand-built model's controls out (all but the monitor `ctl_screen_1`) and gives the
    body `textures/entity/capaldi/console_plain.png` (one colour, every face mapped to the whole image). It also writes
    each panel's frame (`panels`: origin = middle of the outer edge on the top, right, normal, down-slope; the top is
    one plane rising 21.6 degrees from the outer edge at 1.31 blocks from the rotor axis) and the monitor's stand
    (`monitor`: tilt, lift, inward).
  - `tools/console_layout.py` places the 32 small models (`docs/flight-controls.md`) on six themed sides and writes
    the control data files (old ones deleted), lang names (`tardis_rb.control/action/click/side.*`) and the review
    pictures `tools/console_layout/side_<k>.png` + `overview.png`. It fails if a control is off its panel or overlaps
    another. Sides: 0 Flight (door side), 1 Navigation, 2 Exterior, 3 Destinations, 4 Systems, 5 Interior & crew.
  - Control data (`progression/Control`): `model`, `panel`, `u` (right of the panel's middle), `v` (up the slope from
    the outer edge), `turn`, `scale` (0.8), `actions` (clickable part -> action), `shows` (gauges: `charge`, `rooms`),
    `requirements`. `model: monitor` is the console's own monitor.
  - Actions (`ConsoleControls`): screen, x/y/z (sneak = back), step, reset, hop, destination, random, jump,
    handbrake (on: hop/jump refused), door (open/shut), none (moves only, planned). The old x_up..z_down still load.
  - State: uses and last use per clickable part, keyed `<control id>|<part>` (`ConsoleLayout.key`); toggles are on
    after an odd number of clicks, steps at uses mod count. `ConsoleBlockEntity` also keeps the handbrake and the
    gauge values (synced every second). Block entity data version 2 (version 1's shown list is dropped and rebuilt).
  - Client: `ControlModels` loads the models and poses every part (`pose`: toggle/steps/press/spin/gauge, eased over
    `ticks`; lamps lit while on or while animating), `ConsoleModel.placement` stands a control on its panel,
    `ConsoleModel.monitorMatrix` stands the monitor up over the side it faces. `ConsoleAim` is the one hit test:
    each clickable part's posed box in the control's own space against the view ray turned into that space; used by
    `ConsoleInput` (sends `ConsoleNet.Press(pos, "<id>|<part>")`) and Jade.
  - **Monitor per side**: `ConsoleControls.readout` picks the side from the monitor angle (`ConsoleLayout.side`,
    home = side 1) and shows that theme's lines (flight state/handbrake/charge; position/course; exterior; open
    destinations; charge + subsystem levels + stage; crew/rooms/doors). The monitor now always stands raised.
  - **Jade** (`compat/jade`, compile only `maven.modrinth:jade`, CI puts its jar in the test-build release): a ray
    trace callback hands Jade the console block when `ConsoleAim` finds a control nearer than any block; the tooltip
    names the control (and part), its state, what a click does and its side. Hull and collision posts are hidden.
  - Test: `ConsoleTests` (wheels + valve wheel set the course, handbrake stops the take-off lever, the monitor over
    side 0 shows FLIGHT). Rendering, clicking and Jade are untried in a client (CI was blocked by billing, 2026-11-08).
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
