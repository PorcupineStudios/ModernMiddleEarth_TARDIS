# Harness: animals pulling Create: Aeronautics vehicles

Read before touching `harness/`. Proof of concept agreed with Aaron 2026-11-24: horses first, other animals are
calibrated later from the same data.

## How it fits
- Separate mod `ringbearer_harness` (package `com.porcupinestudios.ringbearer.harness`). It requires Create,
  Create: Aeronautics 1.3.2+ (with Simulated and Offroad inside) and Sable 2.0.6+, and depends on neither `lotr` nor
  `tardis`: which animals pull is data, by entity type id (`lotr_port:horse` is just a name in a file).
- The vehicle is an ordinary Aeronautics contraption: blocks assembled into a Sable sub-level (Simulated's assembler in
  game, `SubLevelAssemblyHelper.assembleBlocks` in tests), rolling on Offroad wheel mounts with tires. The hitch is
  one more block on it.
- `HitchBlockEntity` is a Sable `BlockEntitySubLevelActor`, like Offroad's `WheelMountBlockEntity`: Sable calls
  `sable$physicsTick` every physics substep (2 a tick by default) and `sable$tick` every tick. The pull goes in through
  a `ForceTotal` (local frame, impulse at the hitch point) and `RigidBodyHandle.applyForcesAndReset`, as the wheels do.
- Animals stay ordinary entities on their own legs (their AI, a rider, or a test script moves them). A hitched animal
  carries attachment `hitch_link` (the hitch block's position in the plot; saved and synced to clients); after its
  tick, `restrain` holds it back if it got further than its rest length + `slack` from the hitch.
- **Harness (item, worn)**: use it on a tamed, grown animal with draught data. A horse-family animal (vanilla's, the
  LOTR port's mounts) wears it in its saddle slot (Aaron 2026-11-24): using it on the animal puts it there (a saddle
  there comes back to you), or put it in the slot in the animal's inventory; take it out of the slot to take it off
  (no shears). `HorseInventoryMenuMixin` swaps the saddle slot for `content/HarnessSlot` (a saddle where the animal
  takes one, a harness where it can wear one; shown for either, or while holding one, so Rhosgobel rabbits with no
  saddle get the slot). Vanilla counts anything in that slot as a saddle; `AbstractHorseHarnessMixin` (on
  `syncSaddleToClients`) makes a harness there not count (no saddle drawn, no steering) and sets the synced
  attachment `harness_worn` from the slot, unhitching when it's taken out. A saddle used on a harnessed animal says
  to take the harness out first. Animals without a saddle slot (hired wargs, spiders) wear it as the attachment alone;
  sneak and use them with an empty hand to take it off. Worlds from before the slot (harness worn, slot empty) get it
  put in the slot as the animal loads (only while its save is read). It drops when the animal dies (a horse's with
  its inventory). `content/Harnessing`, `HarnessItem`.
- **Hitch (block)**: the block on the vehicle (called "harness" in the first build; registry aliases keep those).
  Lead harnessed animals to it and use it: it hitches every harnessed animal you lead within 10 blocks and takes their
  leads (an unharnessed one stays on its lead, with a message). Sneak-use with an empty hand unhitches them back onto
  your leads, harnesses on.
- **Two ropes**: from the hooks at the ends of the hitch's bar (`models/block/hitch.json`, `RopeRenderer` HOOK_*) to the
  harness's tug rings on the animal's sides (`assets/<ns>/harness_fit/*.json`: `rope_anchor` [side, up, forward],
  `rope_color`), drawn like leads after the animal (`RenderLivingEvent.Post`) through the vehicle's interpolated render
  pose (`ClientSubLevel.renderPose(partialTick)`). The physics still pulls at one point between them (two parallel
  traces pull the same as one at their middle).
- **Looks**: `client/HarnessModel` is the vanilla horse's part tree (same pivots, so it follows body, neck and head)
  holding only harness boxes: padded collar with brass hames, bridle (noseband, crownpiece, cheek straps, bit rings,
  blinkers), backpad, girth, hip strap, breeching, side straps and brass tug rings. `HarnessLayer` draws it like horse
  armour on every horse-family renderer (horse, donkey, mule, skeleton and zombie horse). Textures from
  `harness/tools/textures.py`. LOTR's own horse renderers aren't covered (lotr can't be referenced): they get the
  ropes but no harness drawn.

## The pull (`physics/`, pure maths, JUnit `CouplingTest`)
1. **Where the traces start** (`tow_point`, turned with the animal's body). Sable steps physics at the start of the
   level tick, before entities move, so substep k of n uses the animal's last two positions interpolated at (k+1)/n.
2. **`AnchorTracker`**: a critically damped follower of that point with the point's own velocity fed forward
   (backward Euler, stable at any step). Feed-forward: no lag at a steady pace (a plain smoother lags speed x time
   constant, which pulled the wagon onto the horse at a canter). It rounds off tick-boundary velocity jumps and
   step-ups. `smoothing_hz`.
3. **The pole hinge**: only the level (world horizontal) gap from hitch to tow point counts, and the pull is level.
   Vertical moves of the animal (steps, jumps, bobbing) and the vehicle pitching over a bump don't change it. Measured
   in the vehicle's own tilted floor instead, every 1-block step-up on a 14-degree slope read as a quarter block of
   stretch and kicked the wagon at the pull limit (2026-11-24).
4. **`TraceCoupling`**: one implicit spring-damper along the traces, set by `stiffness_hz` and `damping_ratio` against
   Sable's effective mass at the hitch along the traces (`MassData.getInverseNormalMass`). Same response for a cart or
   a loaded wagon, unconditionally stable (an explicit stiff spring at 2 substeps a tick is what turns into vibration).
5. **Limits**: at most `max_pull` forward; at most `max_push` back (braking), scaled by how squarely the animal stands
   ahead of the hitch (one beside the wagon can't brake it through its traces).

## Steering, the pole and the animal's side
- **Front axle**: `HarnessSteering` + mixin `WheelMountSteeringMixin` (wraps the two redstone reads in Offroad's
  `WheelMountBlockEntity.getSteeringSignal`): wheel mounts ahead of the vehicle's centre of mass steer toward the
  traces (`steer` x trace angle; Offroad's own limit is 30 degrees = signal 15, 2 degrees a step). Offroad then turns
  the wheels in its physics and syncs them to clients, so they visibly turn. Without it four fixed wheels refused to
  turn and the traces ended up at 100-110 degrees to the wagon.
- **Pole angle**: the animal can't swing round further than `max_angle` (60) from straight ahead of the hitch; in a
  turn tighter than the wagon can follow (30-degree wheels on a 4-block wheelbase: ~7-block radius) it walks a wider
  circle instead of jackknifing.
- **Straining** (`Straining`): a movement-speed modifier from the animal's strain (its pull averaged over each tick
  against `max_pull`, or stretch against `slack`, smoothed over ~1 s): free up to 0.4, then down in a straight line to
  `1 - load_slowing` at full strain. Attributes sync to the client, so a ridden horse slows on the rider's side too. A
  faster or steeper curve made its own 3 Hz pull/push shudder on an overloaded climb.
- **Hold-back** (`restrain`, after the animal's tick): the last resort. Past `rest + slack`, closer than
  `rest - push_slack`, or round past `max_angle`, the animal is moved back by half the overshoot a tick, that correction
  changing by at most 0.02 blocks/tick a tick (a shove builds up over a few ticks; a one-tick snap read as a yank),
  all at once past half a block.

## Data: `data/<ns>/draught/<name>.json` (reload listener, /reload)
`entities`, `tow_point` [right, up, forward], `min_gap`/`max_gap` (rest length = level distance at hitching, clamped),
`max_pull`, `max_push`, `stiffness_hz`, `damping_ratio`, `smoothing_hz`, `slack`, `push_slack`, `break_distance`,
`steer`, `max_angle`, `load_slowing`. Forces are Sable units (its block masses x blocks/s^2): the 3x5 plank test wagon
has mass 14.5 and weighs 160; with 27 iron blocks, 122.5 and 1348. Horse (`horse.json`, minecraft and lotr_port
horses): 1.2 Hz, damping 0.5, pull 400, push 200; chosen from the calibration grid below. Other animals: copy and
calibrate (pull for their size, stiffness for how tight their traces should feel); the LOTR port's are below.

## LOTR port animals (`draught/lotr_*.json`, `harness_fit/lotr_*.json`, GameTests `LotrAnimalTests`)
Every animal ridden in LOTR 36.15, as the port has them, and the port's Rhosgobel rabbit (2026-11-24): its mount breeds
(horse, Shire pony, zebra, wild boar, camel, elk, rhino, giraffe, Rhosgobel rabbit) and the creatures its NPCs ride
(the eleven warg types, the three giant spiders). Same traces as the horse; pull and push scaled by size from the horse
(pony 260, boar 150, camel 450, elk 380, rhino 650, giraffe 320, warg 320, spider 260, rabbit 230); tow points behind
the hind legs. Not yet calibrated one by one beyond the horse's runs (they pass the horse's marks on flat ground).
- Wargs and spiders share one entity type (`lotr_port:npc`, `rigged_npc`), so files can name a creature kind:
  `draught/DraughtKeys` asks the mods that sent an IMC message `draught_key` (a `Function<Entity, ResourceLocation>`)
  first, then uses the entity type id. The LOTR port sends one naming NPCs by their NPC type. Neither mod depends on
  the other.
- Who can wear one: grown, with draught data; horse-family animals tamed; other animals that can have an owner
  (`OwnableEntity`: wolves, the port's hired NPCs, which now report their hirer) must have one. Putting a harness on
  goes ahead of the animal's own right-click (a hired NPC's menu took the click).
- Drawing: vanilla's horse renderer gets `HarnessLayer` (posed with the horse). The LOTR port's animals wear the
  harness on their own model parts: a fit's `pieces` (part, material, box in that part's model pixels) are drawn in
  each part's frame as the animal was just drawn, which the port offers through IMC `part_pose`
  (`client/PartPoses` here, `lotr_port` `client/PartPoses` there: it records each creature model's pose stack as it
  renders and replays LOTR's draw transforms and part poses down to the part asked for). So the collar sits on the
  neck and turns and nods with it, and girth and straps move with the body (2026-11-24, Aaron: the collar was fixed
  to the body and the head went through it; legs went through straps).
  `harness/tools/fits.py` writes the pieces from each LOTR model file: girth in the gap between front and hind legs,
  breeching behind the hind legs over the upper hindquarters, side straps from girth to breeching above the thighs
  where the legs sit beside the body (rhino, elk, wargs), spine strap and backpad, brass tug rings just behind the
  girth (and `rope_anchor` there, from the model at rest), and a padded collar with brass hames round the neck cube
  near the body (the neck part, or the head part's neck cube; padding scaled to the neck). `OPTIONS` (breast collar,
  no belly strap, no breeching, girth position) is for bodies the general layout doesn't suit; none needs it now (LOTR's
  sitting rabbit did, before the Rhosgobel rabbit got its own model). The Rhosgobel rabbit in harness:
  ![Rhosgobel rabbit](images/rhosgobel_rabbit.png) Animals with no part poses (the rigged spiders) get the older fallback: `body`
  (`[width, bottom, top, rear, front]` in blocks) and `collar` boxes turned with the body yaw (`client/BodyHarness`).
- GameTests: `lotr_wears_<kind>` (a harness goes on, a saddle comes off, it has draught data) and `lotr_<kind>_flat`
  (pulls a wagon on wagon wheels at a trot) for one of each kind; the rabbit also pulls a sled up a block on a gentle
  nose (`lotr_rhosgobel_rabbit_sled`) and a wagon up 1-block steps. Generated only when the port is loaded: run with
  `./gradlew :harness:runGameTestServer -PwithLotr` (runs also load lotr and api; JEI left out). 2026-11-24: all pass;
  the rabbit's sled climb measured the same as the horse's (jolt 13, stretch 0.27 with pull 230).
- Screenshots: `-Dringbearer_harness.showcase=lotr` (with `-PwithLotr`) puts the animals in a row, harnessed, and
  `ShowcaseCamera` takes `screenshots/harness_lotr_<n>.png` of each. The save needs `confirmedExperimentalSettings`
  in level.dat once the port is loaded, or a confirmation screen blocks quick play.
  `-Dringbearer_harness.showcaseHead=<yaw>,<pitch>` turns and lowers their heads (bodies follow a turned head a
  little, as vanilla's do). The fits (2026-11-24):
  ![LOTR animals in harness](images/lotr_harnesses.png)
  (row by row: horse, Shire pony, zebra, boar; camel, elk, rhino, giraffe; Rhosgobel rabbit, Mordor warg, Utumno ice
  warg, Mirkwood spider), and with heads lowered (collars stay on the necks):
  ![heads down](images/lotr_harnesses_heads_down.png)
- The port's rigged NPCs (spiders) are drawn without `LivingEntityRenderer.render`, so `NpcRenderer` posts
  `RenderLivingEvent.Post` for them: the harness and ropes draw on them as on any animal.

## Tests and metrics
GameTests `HarnessTests` (`./gradlew :harness:runGameTestServer`; the run task deletes `run/world` first, see
Gotchas). `TowRun` lays a 48x72 course near the world origin (flat; 1-in-4 block staircase slopes uphill, downhill
and to the side; an uphill road of slab half-steps), builds a 3x5 plank wagon on four Offroad wheel mounts with tires
plus the harness, assembles it, hitches a horse and drives the horse from its own entity tick like a rider (speed and
heading each tick, scaled by its speed attribute): settle 1.5 s, speed up 2 s, cruise 2 s, turn (or cruise), slow
down 1.5 s, stand 3 s. Every physics step is recorded; `TowMetrics` measures it and writes
`harness/run/harness-metrics/summary.txt` plus a CSV per run (positions included).
`-Dringbearer_harness.sweeps=true` adds the calibration grid (stiffness 0.8/1.0/1.2 Hz x damping 0.5/0.7/1.0 on
uphill, canter, tight turn, downhill; report only). `-Dringbearer_harness.metricsOnly=true` reports without failing.

| Metric | Means | Too high = |
|---|---|---|
| stretch max / rms | traces longer or shorter than rest (blocks) | too soft and stretchy |
| vibration | force left after a centred 0.3 s average, over its rms | rough pull |
| shake | how often that fast part swings side to side (Hz) | vibration (a bump answered is 1-2 Hz) |
| chatter | pull/push flips per second | rattling |
| wobble rms/peak | fast pitch+roll rate (deg/s, 0.3 s high-pass) | shaking, bumps |
| yaw-shake | fast yaw rate | fishtailing |
| accel peak (horse) | wagon's peak acceleration per tick, and the horse's own | kicks / horse yanked |
| jerk rms | change of acceleration per tick | jerky |
| stop settle / rings | time to settle and swings (>0.05 block) after the horse stops | springy |
| trace angle max | traces against the wagon's forward | jackknifing |
| cost | microseconds of harness code per substep | performance |

Results 2026-11-24 (local GameTest server; 13 required runs passed 3 runs in a row; at 4 blocks/s unless noted):

| Run | Stretch | Vibration | Shake | Wagon accel (horse) | Wobble rms | Stop | Trace angle | Cost us |
|---|---|---|---|---|---|---|---|---|
| flat straight | 0.12 | 0.022 | 0 | 4.3 (4.0) | 0.0 | 0 s | 0 | 1.2 |
| flat canter 8 b/s | 0.25 | 0.025 | 0 | 8.7 (8.0) | 0.0 | 0.05 s | 0 | 1.0 |
| flat gentle turn 20 deg/s | 0.13 | 0.022 | 0 | 4.0 (4.0) | 0.0 | 0 s | 19 | 1.0 |
| flat tight turn 45 deg/s | 0.23 | 0.018 | 0 | 3.1 (3.6) | 0.0 | 0.30 s | 65 (pole) | 0.9 |
| side slope straight (roll 14.5) | 0.12 | 0.022 | 0 | 4.3 (4.0) | 0.1 | 0 s | 4 | 1.0 |
| side slope tight turn | 0.24 | 0.038 | 0 | 6.3 (4.7) | 8.5 | 0.28 s | 65 | 1.1 |
| uphill straight (block steps) | 0.16 | 0.31 | 2.1 | 16.8 (4.0) | 13.5 | 0 s | 0 | 1.0 |
| uphill slab road | 0.17 | 0.31 | 2.0 | 9.6 (4.0) | 13.2 | 0 s | 0 | 1.4 |
| downhill straight | 0.17 | 0.22 | 1.4 | 11.1 (4.0) | 6.9 | 0 s | 1 | 0.9 |
| overloaded uphill (27 iron) | 0.53 | 0.43 | 1.6 | 8.7 (4.3) | 20.6 | 0.98 s | 1 | 0.9 |

Findings:
- Flat ground and side slopes are smooth: the wagon's acceleration matches the horse's, no vibration, stops settle
  without swinging.
- On 1-block staircase slopes the kicks (~16 blocks/s^2) and pitch wobble come from Offroad's wheels climbing each
  step, not the traces: the same from 0.3 to 2.4 Hz (sweeps). Softer, less damped traces pass less of it on (that is
  why damping 0.5). A slab road nearly halves the kicks and pitch (25 -> 16 degrees).
- Stiffer than ~1.5 Hz: more vibration and chatter everywhere; softer than ~1 Hz: the canter stretches half a block.

## Gotchas
- **Far from the origin Sable's positions round off**: at the GameTest server's test positions (~7 million blocks
  out) a wagon could only move in quarter/half-block steps and froze. Tests lay their courses near the origin.
  Vehicles that far out in a real world would suffer the same.
- **Sable saves vehicles with the world**: the GameTest server reuses `run/world`, and wagons from an earlier (crashed)
  run reloaded on the new courses and jammed the new ones (looked like random frozen wagons). The run task deletes it.
- `GameTestHelper.runAtTickTime` from inside a scheduled callback crashes (the timer map is iterated); schedule up
  front.

## Screenshots (dev showcase)
`-Dringbearer_harness.showcase=true`: when a player joins, `dev/Showcase` builds the test wagon (`dev/TestWagon`, the
same as the GameTests') with a harnessed horse hitched, and a second harnessed horse, on the ground under them;
`client/ShowcaseCamera` puts the player in spectator, flies to five viewpoints, saves `screenshots/harness_*.png` and
quits. Headless, from a save made from the GameTest server's flat world (2026-11-24):
```
cp -r harness/run/world harness/run/saves/showcase   # then allowCommands 1 in level.dat (nbtlib), options.txt
JAVA_TOOL_OPTIONS=-Dringbearer_harness.showcase=true LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a \
  -s "-screen 0 1280x800x24 +extension GLX" ./gradlew :harness:runClient -PquickPlay=showcase -PwindowSize=1280x720
```
![wagon](images/wagon.png) ![ropes from above](images/ropes_from_above.png)
![harness from the side](images/harness_side.png) ![ropes from the side](images/ropes_side.png)

## Movies of the tow runs (dev)
`-Dringbearer_harness.movies=true` (or `=run,run`): when a player joins, `dev/Movies` plays the GameTest runs
(`HarnessTests.SCENARIOS`) one by one on courses beside them (`TowRun.Host`; ground in smooth stone, which compresses
far better than grass), with the server's tick rate at 5 (`ringbearer_harness.movieTickRate`; physics steps per tick,
so it's unchanged). `client/MovieRecorder` follows each rig from the side through a critically damped (~1 s) camera,
so the camera can't add or hide jitter, and saves every rendered frame with its game time (`run/movie/<run>/`, ~140
frames per game second at 854x480 under software GL). `harness/tools/movies.py [fps] [width]` picks the frames nearest
to even game-time steps and writes `run/movies/<run>.gif` (default 40 fps, 480 wide, ~16-24 MB each) and a 60 fps
MP4 (~4-7 MB). Same save and command as the screenshots, with `-PquickPlay=<save> -PwindowSize=854x480`
(2026-11-24: all 13 runs, about 15 minutes; 7 slope/wheel/sled runs in 8 minutes).

Found while making them: in singleplayer the test driver also ran for the client's copy of the horse (same entity
id, and `Entity.equals` compares ids), moving the horse twice a tick; the driver now runs server side only.

## Slopes (`content/SlopeBlock`, `SlopeContacts`, `physics/Convex`, `Sat`)
Right-triangle blocks placed like stairs: the low edge faces the player, and clicking the top half of a face (or the
underside of a block) places one upside down. `oak_slope`/`stone_slope` rise 1 block over 1 (45 degrees); the gentle
pairs `*_slope_low` (0 to 0.5) and `*_slope_high` (0.5 to 1) make a 2-block ramp at 27 degrees. Models are OBJ files made
by `tools/slopes.py` (with blockstates, item models, loot tables and recipes).
- Entities (and the vanilla collision shape) see 4 stair slices per block: vanilla step-up tries the first height that
  helps, so 16 fine slices slowed walking animals.
- Sable gets no collider from slopes (`BlockSubLevelCollisionShape` returns empty; its colliders come only from
  boxes). Instead `SlopeContacts`, before each physics substep, tests each vehicle's slope wedges against the world's
  blocks, and the world's slope wedges against each vehicle's blocks, with a separating-axis test. A push only goes
  along a face normal that isn't blocked by the neighbouring geometry (so a wedge never catches on the internal edge
  where it meets a block), and all contacts of a vehicle share one sequential-impulse accumulator (several corners
  touching at once don't add up into a launch). The push is the same soft spring-damper as the traces, with friction;
  stiffness, damping and friction are per block in `data/<ns>/slope_contact/*.json` (now 3 Hz, critical, 0.1).
- On a vehicle, upside-down slopes in front of a sled's deck are a nose that rides it up over a block edge.
- A vehicle carrying slopes (a sled) rides on a cushion (`cushion` in its slopes' `slope_contact` file: 0.1 block):
  the bottom corners of its deck (blocks with nothing solid under them, rescanned every second) are held that far over
  the block tops under them by the same spring-damper, with the same friction. Without it a sled's deck sat on
  Sable's rigid contact, so tipping over a crest slammed its front down (2026-11-24: a 54 blocks/s^2 knock). Corners
  against a step's face (ground above them) are left to Sable; world slopes have their own contacts.

## Wagon wheels (`content/WagonWheelBlock`, `WagonWheelBlockEntity`, `WheelTerrain`)
A spoked wheel block (radius 0.9, model `client/WagonWheelModel`) whose axle points out of the vehicle. Each physics
substep it finds the ground under it by sweeping an ellipse (reach 3, height 1.5) over the true block tops (slopes
analytically) along three lines across the tyre, so a 1-block step is met as a gentle ramp instead of a 45-degree wall;
then a soft spring along the vehicle's up through the contact point, grip across the tyre, rolling drag (capped by the
load) and steering from the hitch (`HarnessSteering.degreesFor`, front wheels only). The client draws the hub at the
suspension height, rolling with distance and turned with the steering (negated in `WagonWheelRenderer`: a positive
angle turns the physics' rolling direction +x toward +z, a positive `Axis.YP` turn +x toward -z; drawn the wrong
way round until 2026-11-24). All numbers are in `data/<ns>/wheel/*.json`.

Results (horse at a trot, wagon 12.5, same course; `harness-metrics/`):

| run | accel peak | jerk rms | wobble rms | vibration |
|---|---|---|---|---|
| Offroad wheels, 1-block steps (`uphill_straight`) | 16.8 | 62 | 13.8 | 0.31 |
| Offroad wheels, steps with 45-degree slopes | 10.9 | 51 | 12.0 | 0.27 |
| wagon wheels, 1-block steps (`wagon_uphill`) | 4.7 | 9.5 | 1.9 | 0.08 |
| wagon wheels, 45-degree slopes | 4.8 | 10.7 | 2.5 | 0.09 |
| wagon wheels, gentle slopes | 4.5 | 8.2 | 2.2 | 0.07 |
| sled on flat | 4.4 | 4.1 | 0.1 | 0.05 |
| sled up one block on a 45-degree ground slope (`sled_ramp`) | 15 (23 before the cushion) | 37 (95) | 3.9 | 0.21 |
| sled up one bare block on a gentle nose (`sled_gentle_step`) | 13 (57) | 31 (180) | 4.8 | 0.18 |
| sled up one bare block on a 45-degree nose (`sled_step`) | 23 (couldn't) | 57 | 4.2 | 0.24 |
| sled up a gentle ramp onto one block (`sled_gentle_ramp_once`) | 18 (40) | 53 (117) | 4.0 | 0.25 |

Sleds: with the cushion, single climbs are required runs. What's left of their jolt is the nose meeting the step (the
path's kink: gentler noses and ramps give less). A sled longer than the steps are apart (a staircase every 4 blocks
under a 6-block sled) spans two steps pitched, and its nose meets the next edge steep: `sled_uphill_steps`,
`sled_gentle_steps`, `sled_slope_ramp` stay report-only (`HarnessTests.HARD`), with `overloaded_uphill` (whether its
heavy stop swings once or 3 times varies by run). The cost mark leaves out the single worst step (a 48 ms GC or
class-load pause once decided a run's mean).

## Status
- 2026-11-24: proof of concept, horses calibrated; numbers above.
- 2026-11-24: wearable harness, hitch block, two ropes; GameTests `WearingTests` (harness replaces the saddle and
  keeps saddles off, only tame grown horses, hitching needs a harness, taking it off unhitches, drops on
  death); looks checked in a headless client (screenshots above).
- 2026-11-24: harness in the saddle slot (no shears); harness drawn on the LOTR models' own parts.
- 2026-11-24: sled cushion; LOTR port animals (all ridden kinds and the Rhosgobel rabbit) harnessed, pulling.
- 2026-11-24: slopes (45-degree and gentle pairs, placed like stairs) with their own contact solver, wagon wheels,
  sleds; 37 required GameTests passed twice in a row in the sandbox, movies checked (above).
- Not yet: teams in formation (several animals on one hitch all pull at the same
  point), ridden horses tried in a client (the slowing syncs; the hold-back is server-side and may rubber-band a rider
  who pushes past it), sleigh runners (anisotropic friction), other animals, the howdah (carry mode).
