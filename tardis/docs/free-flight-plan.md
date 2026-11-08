# Plan: flying the TARDIS materialized (free flight)
Read when: building free flight (the TARDIS flying as a real object without dematerialising), its controls, camera,
or how it crosses into space. Status: slices 0-4 built 2026-11-08 (untried in a client, not compiled yet: CI blocked by
GitHub billing); costs (slice 5) and space (slice 6) next.

## Built (slices 2-3, 2026-11-08)
- `entity/TardisBody` (`tardis_rb:tardis_body`, 1 x 2, drawn by `client/TardisBodyRenderer` with the block's model and
  looks): moves on the server only; thrusters off = gravity, drag, ground friction; on = motion damped to a hover.
  Can't be hurt. Every second it calls `FreeFlight.track` (record location follows it; a stale body discards itself).
- `flight/FreeFlight`: `phase` (block -> body: doors close, `Exteriors.lift`, spawn; body -> block: `Landing.find`
  within tuning `flight.landing_radius/height`, place, `setLocation`), `setThrusters`, `dock` (Flight.land takes a
  loose body away and lands as a block), `height`. Record module data `free_flight`: `body` (UUID), `thrusters`.
- Features `exterior_phase`, `thrusters` (navigation 1 for now; raise in data). Tuning `flight` in main.json.
- Console (Exterior side): `phase_key` (toggle), `phase` (needs the key), `thrusters`, `pilot` (placeholder).
  `Doors.open` refuses while loose. Monitor: landed / loose / hovering N up, key and thrusters.
- Not yet: chunk tickets (the body unloads with its chunk; the record keeps its last spot), damage from hard hits,
  charge drain, a body lost while unloaded is set down at the record's spot by `phase`.
- Test: `FreeFlightTests` (loose, doors stay shut, hovers 3 up, falls with thrusters off, sets down as the block).

## Built (slice 4, piloting, 2026-11-08)
- Console `pilot` (flight yoke) -> `flight/Piloting.start`: needs feature `piloting`, a loose box, thrusters on. Saves
  where the player stood (player persistent data `tardis_rb_pilot`), makes them invisible and unhurt, moves them to
  the box (`PortalAPI.teleportEntity` across dimensions, `teleportTo` within one) and seats them (retried for 40 ticks).
- Getting off (vanilla Shift, thrusters off, phase back to a block, a jump: `ejectPassengers`) -> `EntityMountEvent`
  -> put back at the console next tick. Login while flagged and not riding -> put back.
- `TardisBody` steers like a boat: `isControlledByLocalInstance` runs `physics()` on the pilot's client from
  `flight/PilotInput` (written by `client/PilotClient`: W A S D along the pilot's view, Q / E key mappings
  `key.tardis_rb.fly_up/down`, sprint boost, jump brake); vanilla sends the vehicle moves. Other clients lerp.
  PilotClient takes drop/inventory clicks while flying and switches to third person (restored after).
- The model spins `flight.piloting.spin` degrees per tick while flown. Tuning `flight.piloting`: acceleration,
  max_speed, vertical_speed, boost, spin.
- Not yet: the stand-in at the console, camera distance, charge drain, carrying the pilot through the space ceiling.
- Test: `FreeFlightTests.theFlightYokeSeatsThePilotAndShiftPutsThemBack` (refusals, seated and invisible, put back).

## What Aaron asked for
- 2026-11-03: besides jumps (dematerialise here, appear there), the TARDIS takes off and flies as a real object through
  the sky and through space. While flying, the pilot sees it from a third-person camera, spinning as it flies.
- Port plan phase 6 "done when": the repaired TARDIS flies itself, without dematerialising, from a planet to space
  and on to another planet with no loading screen.
- 2026-11-08 (Aaron, replaces the first draft's ideas):
  - **Three separate controls**: one turns the exterior into an entity, one switches the **thrusters** on (without them
    it has no anti-grav and is just a physics object), and one enters the **third-person view to pilot**.
  - Turning into an entity and dematerialising are **different button sequences** (one goes through
    dematerialisation, one only turns the box into an entity).
  - The TARDIS **hovers as a block entity** when nobody is piloting.
  - **Q / E fly up / down.** Shift only stops piloting; it no longer flies down.
  - About 16 small physical controls, each with an off/on state or a full animation when clicked.
- It must fit the repair progression: free flight unlocks by repair stage like everything else.

## What already exists to build on
- The exterior is a block (`block/ExteriorBlock`, two halves) drawn by `client/ExteriorRenderer` from Aaron's police
  box model (`PoliceBoxModel`), with an Immersive Portals portal pair for the open door (`door/Doors`).
- `TardisRecord.location()` is where the box stands. `flight/Flight.hop/jump` move it; `flight/Landing.find` finds a
  safe spot.
- Space: `space/SpaceTravel` swaps entities between a planet and its orbit layer above the ceiling with
  `PortalAPI.teleportEntity` (no loading screen). `space/Orbits` + `SpacePhysics` run gravity and orbits. The
  `gravity/gc` port keeps an entity's motion in its own frame while gravity isn't down.
- The console's controls are data (`tardis/control/*.json`, actions in `console/ConsoleControls`). The 16 new control
  models are in `assets/tardis_rb/console/controls/` (`docs/flight-controls.md`).

## The three states of the exterior
| State | What it is | How you get there |
|---|---|---|
| **Landed** | The exterior block, as today. | Landing, or a jump's materialisation. |
| **Loose** | An entity (`tardis_rb:tardis_body`, 1 x 2 x 1, the same police box model). With thrusters off it is a **physics object**: it falls, slides, can be pushed and knocked about, and takes damage from hard hits. | Control **Exterior Phase** (key turned, then the **Phase Lever** pulled) while landed. |
| **Hovering** | The same entity with **thrusters on**: anti-grav holds it still in the air. Nobody piloting means it hovers in place. | Control **Thrusters** (rocker under its guard) on. |

- **Piloting** is a mode on top of Hovering: control **Flight Yoke** puts the pressing crew member into the
  third-person view and gives them the flight keys. Yoke again, or Shift, stops piloting: the box hovers where it is.
- **Back to a block**: Exterior Phase again while hovering low or resting on the ground. `Landing.find` under the
  entity, then the block pair returns, and `TardisRecord.setLocation` fires the usual events and history
  (`tardis_rb:flight`: start, end, distance).
- **Dematerialising stays a separate sequence**: the **Dematerialisation Lever** (with the Handbrake off) starts a
  jump, as today. Pulling it while Loose or Hovering dematerialises the entity and lands as a block at the destination.
- The record gets an exterior state (`LANDED`, `LOOSE`, `HOVERING`) and the entity id. The monitor shows the state.
- The entity keeps its own chunk and the interior plot loaded with a NeoForge ticket. If a record says Loose or
  Hovering but no entity exists on load, the box lands where the entity was last saved (crash-safe).

## Piloting and the camera
- **The pilot rides the entity** as its controlling passenger in the outside dimension, moved there by
  `PortalAPI.teleportEntity` (no loading screen); invisible and unhurt while piloting. A stand-in entity stays at the
  console, so the crew inside see who is flying. Stopping returns the pilot to the console.
- Why ride outside: the camera has to be in the box's level on the client, and moving the pilot there through IP is
  the one way that needs no second client world.
- **Camera**: third-person orbit round the box (distance and height from data, via `CalculateDetachedCameraDistance`
  and `ViewportEvent.ComputeCameraAngles`); the mouse looks around. First person is off while piloting.
- **Spin**: the model turns all the time (`spin` degrees per tick, faster with speed) and leans into its motion. That
  is for show only: the collision box doesn't turn.

## Keys and movement
| Key | While piloting |
|---|---|
| W A S D | Thrust along the camera's facing |
| **Q / E** | **Up / down** |
| Sprint | Boost |
| Space | Brake (hold position) |
| **Shift** | **Stop piloting** (the box keeps hovering) |

- Q and E are our own key mappings (`KeyMapping`, rebindable). They only act while piloting, so they don't clash with
  drop item (Q) or the inventory (E) at other times: the mod consumes them only in that mode.
- Movement uses vanilla vehicle sync: the pilot's client moves the entity (as with boats), and the server checks it
  (speed cap, "moved wrongly").
- Thrusters off mid-flight: the box falls as a physics object (and the pilot is put back at the console). Running out
  of charge does the same.
- Cost: charge per block flown plus a small drain while hovering, from `TardisRecord.charge`.

## Physics while Loose
- Gravity, ground friction, bounce on hard hits. Players and explosions push it (heavy: mass from data).
- Damage from a fall or hit over a speed threshold goes through the existing damage code.
- In space, Orbits pulls it like any other entity, so a box with its thrusters off drifts in orbit.
- It opts out of the gravity-direction port (its "down" is the world's or the planet's).

## Progression (all data)
- Feature files `tardis/feature/` with requirements: `exterior_phase` (become an entity), `thrusters` with a
  `hover` / `sky` / `space` ceiling, `piloting`. Each control is shown only once its feature is unlocked.
- Tuning `flight` in `tuning/main.json`: max speed, acceleration, vertical speed, boost, spin rate, lean, camera
  distance and height, charge per block, hover drain, mass, friction, bounce, damage speed, height cap per step.

## Space
- Above the ceiling, `SpaceTravel` must move the box **with its pilot**. IP's `teleportEntity` on a vehicle is the
  first thing to prototype; if it drops passengers, teleport both and remount on the same tick.
- In the orbit layer the thrusters replace the player push; then layer to solar system and on to another planet
  (`docs/solar-system.md`). The cloud bank hides the swap; the camera has to be inside the bank too when it happens.

## The door while flying
- First: the doors close and lock when the box becomes an entity (`Doors.close`; `DoorLock` refuses until it lands).
- Later: an open door in flight (the portal pair moved with the entity each tick, plus wind).

## Inside while flying
- The rotor animates faster, and the room shakes a little with acceleration. The monitor shows the exterior state,
  altitude, speed and charge.

## Slices (each ends green in CI with a GameTest)
0. **Control models** (done 2026-11-08): 16 small models with on/off or animations (`docs/flight-controls.md`).
1. **Controls in game**: place them on the console, click boxes and animations driven by the model files.
2. **Loose**: Exterior Phase turns the block into the entity and back; physics, damage, chunk tickets, crash-safe load.
   - Test: phase, the block is gone and the entity falls to the ground; phase again, the block is back with the
     record's location set.
3. **Hovering**: Thrusters hold it still; thrusters off makes it fall.
4. **Piloting**: Yoke, ride with the IP move, stand-in, camera, spin, WASD/Q/E/sprint/space, Shift to stop.
   - Test: a mock pilot's Q input raises the entity; Shift returns them to the console and the box hovers.
5. **Costs and progression**: charge drain, the feature steps, out of charge means falling.
6. **Space**: box and pilot through the ceiling and on to another planet with no loading screen (the phase 6
   done-when, as a world check).
7. **Open door in flight**, then polish (shake, monitor lines, sounds, shimmer).

## Risks and checks first
- IP teleport of a vehicle with a passenger (slices 4 and 6): prototype before building on it.
- Client-moved vehicles trust the pilot's client; keep the server's speed and "moved wrongly" checks on outside space.
- A moving portal pair for the open door (slice 7) may cost too much per tick; measure first.

## Open questions for Aaron
- Exact button sequences: the plan uses Exterior Phase = turn the key, then pull the Phase Lever; Dematerialise =
  Handbrake off, then the Dematerialisation Lever. Change freely; they are data.
- Can other crew ride on the outside, or only the pilot?
