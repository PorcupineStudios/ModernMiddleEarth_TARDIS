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
  facing (-1 = as it left), lane x, length, progress, hits, health, inaccuracy, crash (the fraction it broke down at) and crash ticks while
  crashing, at_wall, next obstacle z, obstacles placed.
- **Flying** (`TardisBody.vortexPhysics`): no gravity, drifts +z at `speed` (W x1.5, S x0.5, sprint x boost, jump
  brakes); A/D and Q/E steer across; it can't leave `radius` of the axis. It moves with `setPos`, so debris (which has
  no collision) never stops it. Unflown (the pilot pressed Shift, or not loaded), the server autopilot moves it on.
- **Debris**: `vortex_debris` clumps (up to `obstacle_size`, a quarter of cells left out) placed up to 96 blocks ahead
  every `obstacle_gap` / 2 .. 1.5 x gap, seeded per TARDIS and z. Touching one (box inflated 0.3) shatters the clump,
  costs `hit_charge`, adds a hit and costs `damage.debris` health.
- **Arrive** at `length` (`min_length + length_per_block x distance` for hops, `jump_length` for jumps, capped by
  `max_length`): the body docks (the pilot goes back to the console), placed debris is cleared, then the normal
  `Flight.hop`/`jump` lands it and pays. If that fails, the block goes back where it left.
- **Health, inaccuracy, crash landing** (Aaron 2026-11-09, instead of 1.7.10's "land part way along the plotted line
  when it breaks down"): the TARDIS's health lasts between trips (`progression/Health`, module `tardis_rb:health`;
  none saved = full). The most it can have is `vortex.damage.health` (50) plus the `health` stat: the hull's levels in
  `subsystem/hull.json` (0, 25, 50, 100) and any research bonus. Repairing the hull fills it up (RepairEvent.Post);
  `/tardis health` shows it, `set <n>`, `repair [amount]` (no amount = full) and `damage <amount>` change it (op, on
  your own TARDIS, like the other /tardis commands). Debris costs `debris`, a bump onto
  the tunnel wall `wall` (spotted by the server, `at_wall` edge, and it shocks the box for everyone). Each point lost
  adds `drift_per_damage` blocks of inaccuracy: arriving at the end lands within that many blocks of the target
  (`offCourse`, a random point in the disc), then the usual landing search. At 0 health it crashes
  (`Vortex.damage`): the pilot is put back at the console, the box's thrusters die (`TardisBody.crashing`, it tumbles
  and crackles) and it sinks out of the tunnel through the clouds; 22 blocks under the axis (or after 3 s)
  `Vortex.crashOut` takes it into the world over the point its course had reached: `Vortex.course` runs a line from
  where it left to the target (for a jump in the target's coordinates, using each dimension's coordinate scale); under
  halfway it comes down in the dimension it left, else the target's. That point is pushed off course by the
  inaccuracy; the TARDIS is left with `after_crash` health (10: enough to fly, not to take a debris hit), and the box appears up to `crash_height` blocks over the ground there (under a roof, the first floor
  below the course), thrusters off, flying down the course at `crash_speed`. A crash ticket keeps the spot loaded.
  Hitting the ground (`TardisBody.impact`) sets off an explosion of power `crater` (0 = none; it goes through
  ExplosionEvent, so claims and explosion settings apply, and `WorldDamage` knocks the hull down as for any explosion
  next to the box), a last shock, and it lies there as a loose box. No charge is paid for a crash. The monitor shows
  how far through and off course it is, and the exterior side always shows Health H/M. At 0 health
  in the vortex for any reason (a hit, the command) it goes down at once. Tests: `outOfHealthItCrashLandsPartWay`, `theCourseCrossesDimensions`, `hullRepairsAndTheHealthCommand`;
  `departHitDebrisAndArrive` checks the damage lasts into the next trip.
- **Wormholes** (Aaron 2026-11-09: openings in the vortex, "opaque portals by default but if you upgrade the scanner
  system they become immersive portals", with "a vacuum effect that pulls the TARDIS" weakened by upgrading the
  stabilizers; exits picked by weight per dimension). `vortex/Wormholes`, entity `entity/Wormhole`, drawn by
  `client/WormholeRenderer`; numbers in tuning `vortex.wormholes`.
  - **Placed** like debris, up to 96 blocks ahead: from `first` (80) every `gap` (150) blocks with `chance` (0.7),
    anywhere across the tunnel. The trip keeps a list `wormholes` (entity id, centre, z, exit dimension, exit aim,
    portal id); ones 12 blocks behind the box go, and `clearTrip` removes the rest. `next_wormhole` is the next z.
  - **Exits** are data: `tardis/wormhole/*.json` = `{dimension, weight, from: {dimension: weight}}`. The weight on a
    trip is `from[the dimension it left]`, else `from[the dimension it aims at]`, else `weight`. Defaults: overworld
    10 (6 from the End), Nether 3 (8 from the Nether), End 1 (2 from the Nether, 6 from the End). Only dimensions in
    this world count. With `respect_locks` (on) a dimension whose destination files are all still locked or
    switched off for that TARDIS is left out; one with no destination file is fine. The exit spot is where the
    course line has reached at that z, in that dimension's coordinates, pushed a random way up to `spread` (400).
  - **Pull**: within `range` (28) the box is pulled across the tunnel toward the middle at up to `pull` (0.1
    blocks/tick², fading to 0 at the range), times 1 - the `pull_resist` stat: stabilizers levels 0/1/2/3 =
    0/0.35/0.6/0.8 (`subsystem/stabilizers.json`; repairs `stabilizers_1..3`, cost `stabilizer_coil`; start cap 1,
    the restored-core milestone raises it to 3). Applied in `TardisBody.vortexPhysics`, so it works on the pilot's
    own client; only that TARDIS's box feels its wormholes. Full steering (0.12) beats the strongest pull, so a
    level-0 box has to steer away early; at level 3 it hardly notices. The autopilot doesn't dodge.
  - **Sucked in**: the box's middle crossing the disc within `radius` (4) of its centre. The pilot is put back at
    the console, the thrusters die, it crackles and spins into the disc for `suck_ticks` (30), then the trip ends:
    a rift opens over the exit spot and the box is thrown out of it tumbling, thrusters off, from up to
    `crash_height` up (`Vortex.dropInto`, shared with the crash landing). It loses `exit_damage` (5) health (never
    below 1) and lands with a crater of `exit_crater` (0 = none). No charge is paid.
  - **Scanner** (`subsystem/scanner.json`, stat `wormhole_sight` 0/1/2; repairs `scanner_1..2`, cost `scanner_lens`):
    at `name_level` (1) the exterior monitor side shows "Wormhole in N: dimension" for the next one ahead; at
    `see_through_level` (2) each new wormhole gets a round Immersive Portals portal (not teleporting, tag
    `tardis_rb:wormhole`) showing its exit from 6 blocks over the ground, and only the disc's rim is drawn. One
    portal per wormhole, removed with it.
  - Ground under a roof (the Nether) is now the first floor below the aim's height, else below the roof; a spot
    with nothing under it (the End's void) moves to the middle of the world.
  - Tests: `wormholeExitsArePickedByWeight`, `stabilizersWeakenTheWormholePull`, `aWormholeSucksTheBoxIntoItsDimension`
    (exits in the End: Nether chunks don't generate in time on the fast-ticking test server),
    `theScannerMakesWormholesSeeThrough`. Other vortex tests switch wormholes off (`noWormholes`).
  - Not done: stabilizers don't soften the debris/wall knock; the autopilot doesn't steer round wormholes.
- **Refused while in the vortex**: `Flight.hop`/`jump`, the lever again, phase, thrusters off. The exterior side of
  the monitor shows "In the vortex: N%, off course D" (or CRASHING!) and Health H/M.
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
- **Shock** (Aaron 2026-11-09: "an electricity damage animation" with "a repelling bounce", playing at any rotation
  or wobble): `TardisBody.shock(away, strength)` on a debris hit (`Vortex.hitDebris`), a bump onto the wall
  (spotted by the server in `Vortex.step`) and any attack with a source position (`hurt`; it still takes no damage). The server bumps a synced
  counter and direction, so every client plays it; the side that moves the box adds the knock (tuning
  `flight.shock.bounce`). `client/ShockRenderer` draws in the box's own pose, after its turn: arcs that cling to its
  sides and wrap round its edges (points put back onto the box's bounds, a little out; reshaped every 2 ticks),
  a blue glow, bolts jumping off the struck side and falling sparks; the box rocks across the knock, dying away
  (`wobble`). `ticks`, `bounce`, `wobble` and `arcs` are tunable. `FreeFlightTests.aHitShocksTheBoxAndKnocksItAway`.
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
  short `tardis_rb_vortex` region ticket (radius 3: the box's chunk and the ring round it entity-ticking, so the next chunk is ready before it gets there) round the box, or round where it should be. The pilot's seat
  waits (trip `pilot`) until the box can be found, then `Piloting.seat` runs. Vanilla still stops ticking entities in
  a dimension 300 ticks after its last player leaves (`emptyTime`); the autopilot moves the box with `setPos` anyway.
- Test: `gametest/VortexTests` (depart without a pilot or forced chunk, the box's chunk is still entity-ticking 100
  ticks in, debris hit, arrive near the target). Run locally 2026-11-09: passes; fails with the ticket switched off.
- Not done yet: the pilot's own view of progress (a HUD bar), sounds, other obstacle kinds, moving debris.
