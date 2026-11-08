# Plan: flying the TARDIS materialized (free flight)
Read when: building free flight (the TARDIS flying as a real object without dematerialising), its camera, controls,
or how it crosses into space. Status: **plan only, nothing built** (written 2026-11-08).

## What Aaron asked for
- 2026-11-03: besides jumps (dematerialise here, appear there), the TARDIS takes off and flies as a real object through
  the sky and through space. While flying, the pilot sees it from a third-person camera, spinning as it flies.
- Port plan phase 6 "done when": the repaired TARDIS flies itself, without dematerialising, from a planet to space
  and on to another planet with no loading screen.
- It must fit the repair progression: free flight unlocks by repair stage like everything else.

## What already exists to build on
- The exterior is a block (`block/ExteriorBlock`, two halves) drawn by `client/ExteriorRenderer` from Aaron's police
  box model (`PoliceBoxModel`), with an Immersive Portals portal pair for the open door (`door/Doors`).
- `TardisRecord.location()` is where the box stands. `flight/Flight.hop/jump` move it; `flight/Landing.find` finds a
  safe spot.
- Space: `space/SpaceTravel` swaps entities between a planet and its orbit layer above the ceiling, with
  `PortalAPI.teleportEntity` (no loading screen). `space/Orbits` + `SpacePhysics` run gravity and orbits. The
  `gravity/gc` port keeps an entity's motion in its own frame while gravity isn't down.
- The console's controls are data (`tardis/control/*.json`, actions in `console/ConsoleControls`). The Capaldi model
  already has a `handbrake` lever and a `crank` wheel that are decoration today.

## Design

### 1. The flying TARDIS is an entity
- New entity `tardis_rb:tardis_body` (a plain `Entity`, not living): a 1 x 2 x 1 box with the TARDIS id, drawn with the
  same police box model as the block, plus its look and damage.
- **Take-off**: the exterior block pair is removed and a body is spawned in its place. `TardisRecord` gets a flight
  state (`LANDED`, `FLYING`, plus the existing jump flow) and the body's entity id. The location becomes "in flight"
  (the monitor already shows that line).
- **Landing**: `Landing.find` under the body. The body turns back into the exterior block there, and
  `TardisRecord.setLocation` fires the usual events and history (`tardis_rb:flight` with start, end and distance).
- The body keeps its own chunk and the interior plot loaded with a NeoForge ticket while flying.
- A body found on load with no record, or a record "flying" with no body, lands where the body was last saved
  (crash-safe).

### 2. Who flies it and how they see it
- **Pilot seat**: a crew member uses the flight lever (the `handbrake` part gets action `fly`). The server moves them
  into the body as its **controlling passenger**, in the outside dimension, with `PortalAPI.teleportEntity`, so there
  is no loading screen. The pilot is invisible and can't be hurt while seated.
- A stand-in (an armour-stand-like `pilot` entity) stays at the console, so the crew inside see who is flying.
  Sneaking (dismount) or the lever again ends piloting: the pilot goes back to the console, and the body hovers in
  place or lands, per data.
- **Camera**: a third-person orbit camera round the body. Distance and height come from data;
  `CalculateDetachedCameraDistance` and `ViewportEvent.ComputeCameraAngles` set them, and the mouse looks around.
  First person is off while piloting.
- **Spin**: the body's model yaw turns all the time (`spin` degrees per tick, faster with speed) and leans into its
  motion. That is for show only: the collision box doesn't turn.
- Why ride outside instead of steering from inside: the camera has to be in the body's level on the client. Moving the
  pilot there through IP is the one way that needs no second client world. Players inside can still watch through
  the door (slice 5).

### 3. Controls and movement
- WASD thrust along the camera's yaw, jump / sneak for up / down, sprint to boost. A brake key holds position.
- Vanilla vehicle movement does the work: the controlling passenger's client moves the body (as with boats), and the
  server checks it (speed cap, vehicle "moved wrongly"). That gives smooth, prompt flight without writing new netcode.
- Gravity: a powered TARDIS ignores gravity, using `Attributes.GRAVITY` like space already does, and opts out of the
  gravity-direction port. With no charge it falls and crash-lands (damage from the existing damage code).
- Cost: charge per block flown plus a small idle drain, taken from `TardisRecord.charge`.

### 4. Progression (all data)
- Feature files `tardis/feature/free_flight_*.json` with requirements, in steps:
  1. `hover`: low flight only (a few blocks over the ground), short time.
  2. `sky`: flight up to the planet's ceiling.
  3. `space`: up through the ceiling into the orbit layer and on to other planets.
- Tuning `flight` in `tuning/main.json`: max speed, acceleration, vertical speed, boost, spin rate, lean, camera
  distance and height, charge per block, idle drain, hover height and time for stage 1, fall damage.

### 5. Space
- Above the ceiling, `SpaceTravel` must move the body **with its passenger**. IP's `teleportEntity` on a vehicle is
  the first thing to prototype; if it drops passengers, teleport both and remount on the same tick.
- In the orbit layer the body uses the same orbit gravity as players (`Orbits`), but its own thrust replaces the
  player's push. Then layer to solar system and on to another planet, as `docs/solar-system.md` describes.
- The globe, fog and haze already hide the swaps. The camera has to stay outside the cloud bank's haze box, so the
  swap must happen with the camera in the bank too.

### 6. The door while flying
- First: the doors close and lock on take-off (`Doors.close`; `DoorLock` refuses while flying).
- Later: an open door in flight. That needs the door's portal pair to move with the body each tick (IP portals can be
  moved and re-synced), plus wind and a pull towards the doorway for anyone near it.

### 7. Inside while flying
- The rotor animates faster, and the console room shakes a little with acceleration (camera shake for the crew
  inside).
- The monitor shows altitude, speed and charge left (new lines in `ConsoleControls.readout`).

## Slices (each ends green in CI with a GameTest)
1. **Take-off and landing with the body entity.** Lever action `fly`, flight state on the record, block to body and
   back, chunk tickets, crash-safe load.
   - Test: take off, the block is gone and the body is there; land, the block is back with the record's location set.
2. **Pilot and camera.** Ride as controlling passenger with the IP move, stand-in at the console, third-person camera,
   spin and lean, WASD/space/sneak/sprint.
   - Test: a mock pilot's input moves the body; dismount returns them to the console.
3. **Costs and progression.** Charge drain, the hover/sky/space steps as features, out of charge means fall and crash.
   - Test: with only `hover` unlocked, the body can't climb above hover height; with no charge it lands damaged.
4. **Space.** Body and pilot through the ceiling into the orbit layer and back, orbit gravity on the body, on to the
   solar system and another planet.
   - Test (world check): fly up through the ceiling and come down on another planet with no loading screen. This is
     the phase 6 done-when.
5. **Open door in flight.** Moving portal pair, wind.
6. **Polish.** Interior shake, monitor lines, sounds, take-off and landing shimmer.

## Risks and checks first
- IP teleport of a vehicle with a passenger (slices 2 and 4): prototype before building on it.
- The gravity-direction port changes motion frames; the body has to opt out cleanly.
- Client-moved vehicles trust the pilot's client. The server's speed and "moved wrongly" checks must stay on outside
  space (the space-level speed check is already off, `SpaceSpeedMixin`).
- A moving IP portal pair (slice 5) may cost too much to update every tick; measure before committing to it.

## Open questions for Aaron
- Should take-off use the `handbrake` lever, or another control?
- When the pilot lets go mid-air, should the TARDIS hover until someone takes over, or land itself?
- Can other crew ride on the outside, or only the pilot?
