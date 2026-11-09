# TARDIS: the time vortex
Read when: dematerialising, the vortex dimension, its tunnel sky, debris, `vortex/Vortex`, `client/VortexRenderer`.

Aaron 2026-11-09: instead of the 1.7.10 button-pressing minigame, dematerialising flies the box through a time vortex
dimension, dodging obstacles, "to" the destination.

- **When**: `ConsoleControls.takeOff` calls `Vortex.depart` when a crew member pulled the lever, `vortex.enabled` is on
  and `tardis_rb:time_vortex` exists. With no player (tests, automation) or the vortex off, hops and jumps land at once
  as before. Fast return still lands at once.
- **Depart** checks what the landing will check (hop unlock/range/charge, or the jump's destination and charge), shuts
  the doors, lifts the block (or docks a loose body) and spawns a `TardisBody` at the start of its lane: x =
  (plot + 1) x 4096 + 0.5, y 128, z 0.5, facing south (+z). `setLane` marks it; the puller is seated with
  `Piloting.seat` (no piloting unlock needed). The record's location is the vortex while it flies.
- **Trip** is module `tardis_rb:vortex` on the record: from (dimension, pos, facing), destination (jumps only), target,
  facing (-1 = as it left), lane x, length, progress, hits, next obstacle z, obstacles placed.
- **Flying** (`TardisBody.vortexPhysics`): no gravity, drifts +z at `speed` (W x1.5, S x0.5, sprint x boost, jump
  brakes); A/D and Q/E steer across; it can't leave `radius` of the axis. It moves with `setPos`, so debris (which has
  no collision) never stops it. Unflown (the pilot pressed Shift, or not loaded), the server autopilot moves it on.
- **Debris**: `vortex_debris` clumps (up to `obstacle_size`, a quarter of cells left out) placed up to 96 blocks ahead
  every `obstacle_gap` / 2 .. 1.5 x gap, seeded per TARDIS and z. Touching one (box inflated 0.3) shatters the clump,
  costs `hit_charge` and adds a hit; each hit moves the landing up to `hit_drift` blocks.
- **Arrive** at `length` (`min_length + length_per_block x distance` for hops, `jump_length` for jumps, capped by
  `max_length`): the body docks (the pilot goes back to the console), placed debris is cleared, then the normal
  `Flight.hop`/`jump` lands it and pays. If that fails, the block goes back where it left.
- **Refused while in the vortex**: `Flight.hop`/`jump`, the lever again, phase, thrusters off. The exterior side of
  the monitor shows "In the vortex: N%, H hits".
- **Solid tube** (Aaron 2026-11-09: "make the tube have collision"): the box can reach `radius` (11) from the axis and
  bounces off the wall there with a crackle (`TardisBody.vortexPhysics`: the speed into the wall is turned back at
  0.6, a lightning-impact sound on the pilot's client). Anything else loose in the vortex (a crew member stepping out
  of the doors, items, mobs) is kept within `radius` + 1 by `Vortex.onEntityTick` on both sides (`keepInside`):
  pushed back off the wall, its speed into the wall dropped, and on the tube's bottom it can stand. Checked in the
  dev client: dropped 20 blocks below the axis, a player stood on the bottom at 11.1.
- **Cloud wall** (Aaron 2026-11-09: edges "puffy like clouds and charged with lightning", then "have the clouds
  cover the skybox completely" with "the same texture/pattern"): the whole wall is cloud. `VortexRenderer.cloudWall`
  draws the tube (96 x 320 quads, radius 16 at the folds) pushed in up to `BILLOW` (4) at round puffs (`puffs`:
  big ones about 4 blocks across with small ones heaped on, one per jittered cell, fixed along the tunnel so they
  rush past, slowly turning) and paints it with the vortex texture as before. Puff tops are bright and the folds
  dark. `bolts` strikes jagged forked lightning over it (up to 4 at once, a new chance 3 times a second each,
  crackling and fading); the cloud near a strike lights up blue-white. The glow streaks are a fainter tube inside.
- **Twisting tunnel** (Aaron 2026-11-09: "make the time vortex twist and turn"): the axis winds. `Vortex.bend(z)`
  gives the sideways and up/down offset at distance z along the lane (two sine waves each way, up to about 11 off
  the straight line, never steeper than 0.5 blocks per block; straight for the first 40 so departure is clean),
  scaled by tuning `vortex.bend` (0 = straight). Everything follows it: the renderer bends each ring, the glow and
  bolts; `keepInside` and the wall bounce measure from the bent axis; debris is placed on it; the autopilot steers
  toward the axis 3 blocks ahead. `VortexTests.theTunnelBendsGently` checks the limits.
- **Look**: dimension type `effects` "tardis_rb:time_vortex" -> `VortexRenderer.Effects` (no sky, no clouds, dark
  blue fog). `VortexRenderer` draws at AFTER_SKY, depth off, a tube of radius 16 round the lane axis, ±160 blocks:
  `textures/environment/time_vortex.png` (rings) scrolling along and twisting, plus `time_vortex_glow.png` added on
  top turning the other way. Ends fade to black.
- **Textures** come from `tools/vortex_texture.py` (seamless periodic noise, fixed seed); the image sites were blocked
  from the cloud sandbox, so nothing was downloaded. Any seamless 1024 x 1024 image can replace them (wrapping both
  ways: u round the tube, v along it).
- **Loading**: nobody may be in the lane (the pilot pressed Shift, or no pilot), so each tick of a trip adds a
  short `tardis_rb_vortex` region ticket (entity-ticking) round the box, or round where it should be. The pilot's seat
  waits (trip `pilot`) until the box can be found, then `Piloting.seat` runs. Vanilla still stops ticking entities in
  a dimension 300 ticks after its last player leaves (`emptyTime`); the autopilot moves the box with `setPos` anyway.
- Test: `gametest/VortexTests` (depart without a pilot or forced chunk, the box's chunk is still entity-ticking 100
  ticks in, debris hit, arrive near the target). Run locally 2026-11-09: passes; fails with the ticket switched off.
- Not done yet: the pilot's own view of progress (a HUD bar), sounds, other obstacle kinds, moving debris.
