# Airships backport plan (Create Aeronautics for 1.7.10)

Oct 29, 2026 · @Aaron

## Summary

We rebuild Create Aeronautics (with its Simulated and Offroad halves) inside our kinetics system, block for block, with our own names, models and textures. Aeronautics' assets are not ours to copy, so every block gets a new name and a new model.

Our moving structures cannot carry it as they are. `ContraptionEntity` and `VehicleEntity` turn about one axis only, snap to quarter turns, have no mass and no side collision. Aeronautics needs ships that pitch, roll and yaw freely, weigh what they carry, and bump into the world. So phase 1 replaces the core with a real rigid-body ship (Aaron's call: rewrite it if it doesn't line up). Bearings, pistons and gantries keep the old single-axis entity, since they move on rails anyway.

The new system is called **Skyworks** (package `kinetics/sky`). Ships are server-simulated; clients only draw and predict, in line with the server-heavy rule.

## Feature inventory

Every block named in the 1.0 release (April 2026), our name for it, and the phase that builds it. Sources: [120fps overview](https://120fps.ru/articles/create-aeronautics-polnyj-obzor-stroj-realistichnye-samolyoty-dirizhabli-i-vnedorozhniki-v-minecraft), [video guide summary](https://videohighlight.com/v/mJDw9ROFa1o). The official wikis would not load, so numbers below are our own tuning unless a source gave one.

| Aeronautics / Simulated / Offroad | Ours | What it does | Phase |
| --- | --- | --- | --- |
| Sable physics (mass, inertia, collision) | Skyworks core | Rigid-body ships, any angle | 1 |
| Physics Assembler | Hull Assembler | Lever turns the build into a ship; hold to put it down | 1 |
| Super glue / slime / Honey Glue | Ship Resin | Joins blocks; diagonal joins | 1 |
| Engineer's / Aviator's Goggles | Flight Goggles | Ship mass, lift, block weight | 1 |
| Contraption Diagram | Hull Chart | Mass, centre of mass, force arrows | 1 |
| Creative Physics Staff | Mover's Rod | Creative: drag and turn ships | 1 |
| Levitite + casting | Aerolite | Floating block, cast from clay | 2 |
| Hot Air Burner | Balloon Burner | Heats the envelope above it | 2 |
| Hot Air Envelope | Balloon Canvas | Enclosed volume gives lift, less with height | 2 |
| Steam Vent | Lift Vent | Steam-fed balloon, up to 5000 m3 | 2 |
| Propeller Bearing (wooden / andesite) | Rotor Mount + blades | Thrust from RPM | 2 |
| Smart Propeller | Trim Rotor | Thrust set by redstone | 2 |
| Gyroscopic Propeller Bearing | Gimbal Rotor | Stays upright (helicopters) | 2 |
| Symmetric Sail / sails | Fin, Sail | Drag per face, keeps the nose forward | 2 |
| Steering Wheel (angle out) | Helm | Rotation to a set angle | 2 |
| Throttle Lever | Throttle | Drag-set redstone 0-15 | 2 |
| Fan + Nozzle | Encased Fan + Thrust Nozzle | Fan thrust; nozzle hover lift | 2 |
| Altitude / Velocity / Gimbal sensors | same names | Redstone from height, speed, tilt | 3 |
| Navigation Table | Chart Table | Points at a map/compass target | 3 |
| Optical Sensor | Range Eye | Block at range | 3 |
| Laser Pointer + Sensor | Beam Lamp + Beam Eye | Coloured beams | 3 |
| Redstone Accumulator / Inductor | same idea, Charge Cell / Signal Coil | Signal build-up, fall-off | 3 |
| Modulating / Directional linked receivers | Range / Bearing Receiver | Link strength by distance or aim | 3 |
| Linked Typewriter | Link Keyboard | Keys to link frequencies | 3 |
| Nameplate | Hull Plate | Ship name | 3 |
| Swivel Bearing | Swivel Joint | Joins two ships on a hinge | 4 |
| Rope / Rope Connector | Tether | Rope between ships or ground | 4 |
| Spring / Torsion Spring | Coil Spring / Twist Spring | Spring joint; angle-limited bearing | 4 |
| Handle | Grab Handle | Pull yourself or the ship | 4 |
| Redstone Magnet | Lodestone Coil | Attract/repel when powered | 4 |
| Docking Connector | Dock Clamp | Locks ships, moves items/fluids | 4 |
| Plunger Launcher | Grapple Launcher | Pulls two ships together | 4 |
| Portable Engine | Field Engine | 2048 SU, coal; blaze cake doubles | 5 |
| Directional Gearshift | Twin Gearshift | Side picks direction | 5 |
| Analog Transmission | Step Transmission | Speed by signal | 5 |
| Auger Shaft / Auger Cogwheel | Screw Shaft / Screw Cog | Lift items without fans | 5 |
| Mounted Potato Cannon | Deck Cannon | Fires on redstone | 5 |
| Wheel Mount + 4 wheel sizes | Axle Mount + tyres | Driven, steerable wheels, suspension | 6 |
| Rock Cutting Wheel / Bore Head Bearing | Rock Cutter / Bore Mount | 3x3 mining on vehicles | 6 |

Already built and reused: Steering Wheel driving (becomes a ship control), Linked Controller, Redstone Link, drills, carried machines, Vehicle Bay.

## New physics core

Ships keep their blocks as **real blocks in a far-off shipyard** of the same dimension, the way Aeronautics' physics engine does. Everything on a ship then works exactly as on the ground: kinetic networks, chests, hoppers, redstone, AE2, furnaces. The ship entity only holds the pose and draws the yard blocks where the ship is.

- **Shipyard:** each ship gets a 256 x 256 plot from x/z 20,000,000 in its own dimension. Plot chunks are generated empty (no terrain, no ores) and kept loaded while the ship is loaded.
- **Rigid body:** position, rotation (quaternion, any angle), velocity and spin. Mass, centre of mass and inertia come from the blocks (block weight table, config overrides) and update when blocks are added or removed.
- **Forces:** gravity, water buoyancy per block, air drag, and lift/thrust parts (Aerolite now, balloons and rotors in phase 2) applied at their own block, so a ship with lift up top and weight below rights itself.
- **Collision:** the corners of the ship's outer blocks are tested against world blocks and other ships, with bounce, friction and push-out, four substeps a tick. Ships rest and stop simulating when still.
- **Players and mobs:** ship blocks are added to the normal collision list, so you can walk, jump and stand on a moving ship. Riders are carried with it. Your own client does your player, the server does mobs and items.
- **Building on ships:** looking at a ship block points the game at its yard block, so breaking, placing, opening chests and using machines all go through vanilla code. Drops, sounds and particles are moved back to where the ship is.
- **Coremod hooks (5):** collision list, mouse-over, distance checks (containers), block digging distance, empty yard chunks.

The old one-axis entity stays for bearings, pistons and gantries. Steering-wheel cars move onto the new core with Offroad in phase 6.

## Phases

Each phase ends with a dev-server test and its own PR on top of the current stack.

1. **Core** (this thread, now): shipyard, rigid body, world and ship collision, walking on ships, building on ships, Hull Assembler, Aerolite, water floating, Flight Goggles readout.
2. **Lift and thrust:** Balloon Canvas + Burner (enclosed-volume lift, heat, falls off with height), Lift Vent, Rotor Mount + blade blocks (thrust = RPM, reversed by direction), Trim and Gimbal Rotors, Fins and Sails (drag per face), Thrust Nozzle, Helm (angle out), Throttle. Linked Controller and a desk-style flight console drive it.
3. **Instruments:** altitude, speed, tilt sensors, Chart Table, Range Eye, Beam Lamp/Eye, Charge Cell, Signal Coil, Range/Bearing Receivers, Link Keyboard, Hull Plate, Hull Chart.
4. **Joints:** Swivel Joint, Tether, Coil/Twist Springs, Grab Handle, Lodestone Coil, Dock Clamp, Grapple Launcher, Ship Resin.
5. **Power and extras:** Field Engine, Twin Gearshift, Step Transmission, Screw Shaft/Cog, Deck Cannon.
6. **Offroad:** Axle Mount with four tyre sizes and suspension, Rock Cutter, Bore Mount; steering-wheel cars moved onto the new core.
7. **Our extras:** TARDIS Vehicle Bay takes ships; LOTR wind and Middle-earth ceilings; NEI pages.

## Limits and open questions

- Blocks on ships get no random ticks (crops won't grow, fire won't spread) unless we add them; redstone, machines and tile entities all run.
- Ships render with the yard's lighting, so a ship in a cave looks daylit.
- The server trusts your client for your own collision with ships, to avoid rubber-banding on moving decks.
- Fluids can spill off a ship's edge inside the yard; Aeronautics stops this, we will too in phase 2.
- Numbers (lift per Aerolite, block weights, ceiling height) are our tuning, since the source wikis wouldn't load. Tell me if a known value should be matched.
- Open: should ships cross into Middle-earth via the TARDIS only, or never change dimension? Default: never.
