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
- **Look**: dimension type `effects` "tardis_rb:time_vortex" -> `VortexRenderer.Effects` (no sky, no clouds, dark
  blue fog). `VortexRenderer` draws at AFTER_SKY, depth off, a tube of radius 16 round the lane axis, ±160 blocks:
  `textures/environment/time_vortex.png` (rings) scrolling along and twisting, plus `time_vortex_glow.png` added on
  top turning the other way. Ends fade to black.
- **Textures** come from `tools/vortex_texture.py` (seamless periodic noise, fixed seed); the image sites were blocked
  from the cloud sandbox, so nothing was downloaded. Any seamless 1024 x 1024 image can replace them (wrapping both
  ways: u round the tube, v along it).
- Test: `gametest/VortexTests` (depart without a pilot, debris hit, arrive near the target). Not run: CI is blocked.
- Not done yet: the pilot's own view of progress (a HUD bar), sounds, other obstacle kinds, moving debris.
