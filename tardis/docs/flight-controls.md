# TARDIS: flight console controls (16 small models)
Read when: the new console controls' models, their animations, or wiring them into the console renderer and clicks.

- Aaron 2026-11-08: about 16 small controls based on real TARDIS controls, each with an off/on state or a full
  animation for visual feedback on click; three of them drive free flight (`docs/free-flight-plan.md`): Exterior
  Phase Key + Phase Lever (block <-> entity), Thrusters (anti-grav on/off), Flight Yoke (third-person piloting).
- **Made by `tools/flight_controls.py`** (never edit the outputs). Each control is boxes in pixels round its base centre
  (x right, y up, operator at +z), about 4-7 px across and up to 7.6 px tall. Outputs:
  - `assets/tardis_rb/console/controls/<id>.json`: parts (`pivot` + quads `v` = x y z u v in blocks, normal `n`, the
    same quad format as `console/capaldi.json`) and `actions`: `click` (the part clicked), `type`, `ticks`, `moves`,
    `lit` (lamp part -> `on` or `active`), `count` for steps.
  - `tools/flight_controls/<id>.bbmodel` for Blockbench (generic model, texture embedded), with animations `on`/`off`
    (toggles), `steps`, `press`, `spin`; `<click>_on` etc. when a control has several clickable parts.
  - Texture `textures/entity/controls/controls.png` (32x32 palette, 4x4 swatches) + `controls_glow.png` (only the
    glow swatches, for the full-bright pass). `tools/flight_controls/preview.png` draws every control off and on.
- **Action types**: `toggle` (off/on: rotation `axis` deg `off`->`on` about the part's pivot, or `move_on`), `steps`
  (`count` notches, each `step` deg or `move_step`), `press` (in by `move` and back), `spin` (`turn` deg about `axis`,
  which may be a vector). Rotations are right-handed about the axis through the part's pivot. Glow parts with no
  `lit` entry are always lit.
- Not in game yet (slice 1 of the flight plan): place them on the console panels, draw from these files, click boxes
  per clickable part, state per control in `ConsoleBlockEntity`.

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
