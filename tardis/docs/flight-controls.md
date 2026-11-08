# TARDIS: console controls (32 small models)
Read when: the new console controls' models, their animations, or wiring them into the console renderer and clicks.

- Aaron 2026-11-08: about 16 small controls based on real TARDIS controls, each with an off/on state or a full
  animation for visual feedback on click; three of them drive free flight (`docs/free-flight-plan.md`): Exterior
  Phase Key + Phase Lever (block <-> entity), Thrusters (anti-grav on/off), Flight Yoke (third-person piloting).
- Aaron 2026-11-08 (later): 16 more after the 1.7.10 TARDIS Mod's console (group `classic`): its control kinds
  (staple lever swinging 140 deg, valve wheel, take-off special lever, push switch, flight buttons that rise and glow,
  gauges with 180 deg needles, framed screens, schematic chooser, screwdriver holder, lamps, 20-button memory bank,
  the coordinated/drifting flight lever, room deleter). Shapes and motions redrawn in our boxes; nothing copied.
- **Made by `tools/flight_controls.py`** (never edit the outputs). Each control is boxes in pixels round its base centre
  (x right, y up, operator at +z), about 4-7 px across and up to 7.6 px tall. Outputs:
  - `assets/tardis_rb/console/controls/<id>.json`: parts (`pivot` + quads `v` = x y z u v in blocks, normal `n`, the
    same quad format as `console/capaldi.json`) and `actions`: `click` (the part clicked), `type`, `ticks`, `moves`,
    `lit` (lamp part -> `on` or `active`), `count` for steps.
  - `tools/flight_controls/<id>.bbmodel` for Blockbench (generic model, texture embedded), with animations `on`/`off`
    (toggles), `steps`, `press`, `spin`; `<click>_on` etc. when a control has several clickable parts.
  - Texture `textures/entity/controls/controls.png` (32x32 palette, 4x4 swatches) + `controls_glow.png` (only the
    glow swatches, for the full-bright pass). `tools/flight_controls/preview.png` (flight set) and `preview_classic.png` draw every control off and
    on; `animations.gif` plays all 32 animations (a small z-buffered software renderer, no game needed).
- **Action types**: `toggle` (off/on: rotation `axis` deg `off`->`on` about the part's pivot, or `move_on`), `steps`
  (`count` notches, each `step` deg or `move_step`), `press` (in by `move` and back), `spin` (`turn` deg about `axis`,
  which may be a vector), `gauge` (not clicked, `click` null: the needle at `from` + value x (`to` - `from`)).
  A toggle with no `moves` only lights its lamps (screens, lamps). Several clickable parts = several actions. Rotations are right-handed about the axis through the part's pivot. Glow parts with no
  `lit` entry are always lit.
- Not in game yet (slice 1 of the flight plan): place them on the console panels, draw from these files, click boxes
  per clickable part, state per control in `ConsoleBlockEntity`.

## Flight set (designed from named TARDIS controls)
| # | id | Name | Animation | Does (planned) |
|---|---|---|---|---|
| 1 | `demat_lever` | Dematerialisation Lever | toggle | Starts a jump: dematerialise here, land at the destination (with the handbrake off). |
| 2 | `handbrake` | Handbrake | toggle | On: no jump or flight can start. Off: the dematerialisation lever and thrusters work. |
| 3 | `phase_key` | Exterior Phase Key | toggle | Turned on, it frees the exterior: pull the Phase Lever to make the box an entity, or back to a block. |
| 4 | `phase_lever` | Phase Lever | toggle | With the key turned: block -> entity (stays a materialised box) or entity -> block where it rests. |
| 5 | `thrusters` | Thrusters | toggle | On: anti-grav holds the box still in the air (hovering). Off: it is a physics object and falls. |
| 6 | `flight_yoke` | Flight Yoke | toggle | Takes the pressing crew member into third-person piloting; again (or Shift) stops piloting. |
| 7 | `helmic_regulator` | Helmic Regulator | steps | Four settings, one per click: flight speed (slow, cruise, fast, boost). |
| 8 | `fast_return` | Fast Return Switch | press | Pressed: the next jump goes back to the last landing spot. |
| 9 | `vector_tracker` | Vector Tracker | spin | Spins when used: fine-tunes the landing spot by one block in the facing direction. |
| 10 | `coordinate_wheels` | Coordinate Wheels | steps | Each wheel moves the course by the step size along X, Y or Z (8 detents per turn). |
| 11 | `directional_pointer` | Directional Pointer | steps | Each click turns the needle 45 degrees; the box lands facing that way (8 directions). |
| 12 | `stabilisers` | Stabilisers | toggle | Left: steadies flight (less shake, slower turns). Right: steadies landings (no drift on touchdown). |
| 13 | `throttle` | Space-Time Throttle | steps | Four notches: how much charge a jump spends to land closer to the aimed spot. |
| 14 | `telepathic_circuits` | Telepathic Circuits | toggle | Hand on it (click): picks the destination you last looked at on the map; lights while set. |
| 15 | `randomiser` | Randomiser | spin | Spins the die: the next jump goes to a random open destination. |
| 16 | `door_control` | Door Control | toggle | Half a turn opens the doors, half a turn closes them (also locks them while flying). |

## Classic set (after the 1.7.10 TARDIS Mod)
| # | id | Name | Animation | Does (planned) |
|---|---|---|---|---|
| 17 | `coordinate_lever` | Coordinate Lever | toggle | Moves the course along one axis by its own amount (small to huge, set per lever in data). |
| 18 | `valve_wheel` | Valve Wheel | steps | Each click turns it an eighth; eight make one full turn of the setting it drives. |
| 19 | `takeoff_lever` | Flight Take-off Lever | toggle | The classic way to start a coordinated flight (kept beside the dematerialisation lever). |
| 20 | `push_switch` | Push Switch | toggle | On/off for whatever it is set to in data (stabilisers, landing pad, save/load mode). |
| 21 | `flight_button` | Flight Control Button | toggle | During a jump it lights and rises; press it in time or the flight drifts (stabilisers skip this). |
| 22 | `gauge` | Gauge | gauge | Shows one value from 0 to full (charge, rooms used, speed, shields), set in data; not clicked. |
| 23 | `twin_gauge` | Twin Gauge | gauge | Two values side by side (e.g. shields and hull), each needle 0 to full. |
| 24 | `scanner_screen` | Scanner Screen | toggle | Click to switch it on/off; shows the coordinate guess or the scanner view (text drawn by the renderer). |
| 25 | `schema_chooser` | Schematic Chooser | press | Pages through the rooms that can be added (category and room), as the old console did. |
| 26 | `screwdriver_holder` | Sonic Screwdriver Holder | toggle | Click to take or put back the sonic screwdriver; it rises out of the socket when ready. |
| 27 | `console_lamp` | Console Lamp | toggle | Lights for whatever it is tied to in data (in flight, low charge, door open...). |
| 28 | `memory_bank` | Memory Bank | press | Each button is a saved location: press to load it, or save to it in save mode. |
| 29 | `drift_lever` | Coordinated Flight Lever | toggle | Out: the TARDIS drifts (free roaming flight). In: coordinated flight to the set destination. |
| 30 | `room_deleter` | Room Deleter | press | Two-step press (arm, then sneak-press) removes every added room; the frame lights while armed. |
| 31 | `speed_lever` | Speed Lever | steps | Five notches of flight speed; faster flights cost more charge. |
| 32 | `sub_levers` | Sub-Lever Bank | toggle | Three small on/off switches for lesser functions (lights, alarms, shields), set in data. |
