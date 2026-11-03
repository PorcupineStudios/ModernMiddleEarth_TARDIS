# TARDIS: the console model and its controls
Read when: the console block/renderer, console controls (data `tardis/control/`), clicking controls, the course.

- Aaron 2026-11-03: a console like the original mod's, a custom model with buttons, levers and dials that don't follow
  block shapes; controls appear on it only once repaired. The block is just the central column
  (`block/ConsoleBlock`, render shape ENTITYBLOCK_ANIMATED); `client/ConsoleRenderer` draws the plinth, six sloped
  panels, a bobbing rotor and each control (ModelPart layer `tardis_rb:console`, texture `textures/entity/console.png`,
  placeholder colours for Aaron's art). Clicking the column opens the flight screen.
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
