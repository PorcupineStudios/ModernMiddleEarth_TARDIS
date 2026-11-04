# Gravity and orbits
Read when: touching `space/Orbits`, `space/SpacePhysics`, `client/ClientSpacePhysics`, `gravity/`, tuning
`space.motion`, or the `/gravity` command. Planets and orbit layers: `docs/space.md`.

- Aaron (2026-11-04): Gravity Changer (MIT, Fabric; port https://github.com/NightEpiphany/gravity-changer-1.21.1) is
  the reference. Its shape: gravity direction per entity (six directions) + an animated visual quaternion; mixins on
  Entity (bounding box, eye position, collide, move, moveRelative, applyGravity), LivingEntity (travel, jump), Camera
  (`setRotation` multiplies the gravity quaternion in, eye moved along local up), renderers. Planned for the tilt
  slice: six-way like GC when standing on blocks, free tilt only while floating (the hitbox can't rotate).
- **Slice 1 (built)**: in a planet's orbit layer down is the planet. `Orbits.gravity` = 0.08 x `space.gravity` x
  (R / (R + altitude))^2; sideways speed cancels it (`orbit_speed` at the floor, x sqrt(R / (R + a)) higher up), so
  orbits hold. Vanilla gravity is off in space (GRAVITY modifier -1) and vanilla air drag is undone after each tick
  (`drag_free`); speed capped at `max_speed` (under the server's ~10 blocks/tick rubber-band). Jump / sneak in the air
  push up / down (`thrust`). Players run it on their client (`ClientSpacePhysics`, tuning comes in `SkyNet.Planets`),
  everything else on the server (`SpacePhysics`, EntityTickEvent.Post). Creative flyers, spectators, riders skip it.
- Players' sky / floor / edge checks run every tick (others every `check_ticks`), so fast movers can't overshoot far.
- **`/gravity`** (`gravity/GravityScale`, op 2): `get [targets]`, `set <targets> <scale>`, `reset <targets>`,
  `dimension <dim> [<scale>|reset]` (saved, `tardis_rb_gravity`). Both are GRAVITY attribute multipliers
  (`tardis_rb:gravity_scale`, `tardis_rb:dimension_gravity`), so they reach the client and `Orbits` multiplies by them.
- Next: slice 2, the solar system zone (3D, every body pulls, a sun; leaving a layer's top); slice 3, tilting.
- Tests: `SpaceTests.gravityFadesAndOrbitsHold`, `gravityCommandsScaleGravity`. Not tried in a client.
