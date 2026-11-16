# Space: the solar system zone
Read when touching `space/StarSystem`, `Systems`, `progression/SystemFile`, `client/SystemRenderer`, or travel between planets.

- Aaron 2026-11-04 ("slice 2"): one 3D zone where every planet is a ball and every body pulls, with a sun. Data:
  `data/<ns>/tardis/system/<id>.json` (`SystemFile`): `dimension` (a `tardis_rb:space` type level,
  `tardis_rb:solar_system`), `sun` {x,y,z, radius, gravity, color, burn}, `bodies` [{planet, x,y,z, radius, gravity}].
  Each planet is in at most one system (first by id wins). Built in: `tardis_rb:sol`; the LOTR port's `lotr_port:arda`
  (Middle-earth, the TARDIS overworld, vanilla overworld).
- Balls are small (radius 128..400) on purpose: the zone is a map, the layer is the full-size planet. Entry sphere =
  radius * (1 + orbit `maxAltitude`), so the ball's pull at the entry sphere equals `Orbits.gravity` at the layer top
  (`SpaceTests.solarSystemGravity`): no jump in pull when you cross.
- Ways: above an orbit layer's top -> `SpaceTravel.toSystem`, placed just outside the entry sphere at the lon/lat of
  your layer (x, z) (equirectangular), velocity turned from east/north/up into world axes. Inside an entry sphere ->
  `toLayer`, the reverse, arriving just under the layer top. The player's exact frame is carried (`Body.layerToSystem`,
  the velocity mapping as a rotation), set before the gravity direction so nothing turns (`SpaceTravel.arrive`,
  `SkyNet.FrameOf`). The cloud bank also sits under the layer top and just outside each entry sphere, so the swap
  happens in cloud (`GlobeRenderer.cloudiness`).
- Pull (Aaron 2026-11-15, performance): only the **focus** pulls, G * tuning.gravity * body.gravity * (r/d)^2. Focus
  (`StarSystem.focus`) = nearest ball whose sphere of influence (`space.motion.focus_range` x entry radius, 6) holds
  you, else the sun. Gravity radius (Aaron 2026-11-15, Outer Wilds style): `gravity_radius` on a body or the sun (0 =
  unset) overrides that sphere; outside every radius nothing pulls (an unset sun radius pulls everywhere, the old
  fallback), and each pull fades to 0 over the outer `space.motion.gravity_edge` share (0.1, smoothstep)
  (`StarSystem.fade`, `SpaceTests.gravityRadiusFades`); ships obey the same. With the align key held up points away
  from the focus, so you turn as you orbit (`SpaceTests.orbitTurnsWithTheFocus`).
  Aeronautics ships (space stations) turn the same way, and players on their decks with them (`docs/sable.md`). Feet
  turn to the nearest of the six directions of the total pull (`GravityDirection`, 1000 ms turn), checked every check
  tick while not on the ground. Players step on the client (`ClientSpacePhysics`), others on the server
  (`SpacePhysics`), both through `Orbits.stepSystem`.
- The sun: inside its radius you burn (fire + `inFire` hurt, `burn` per hit) and get pushed out. Zone edges: build
  height +-8 is clamped.
- Drawing (`SystemRenderer`, AFTER_SKY, no depth): balls far to near, shrunk to the far plane; planets are painted like
  their own globe (`GlobeSurface`: map at true size, radius = longer side / 2 pi, unwrapped round the spot facing the
  camera, which is where you'd land; Aaron 2026-11-04: a lat/long wrap squashed Middle-earth 2.5x north-south), with a
  shade turned to the sun; the sun has 3 additive glow shells.
- Planets cache (`Planets`) is keyed also by the level count: during startup it cached an empty list and the world
  check said "the Overworld is no planet".
- Tests: `SpaceTests.solarSystemRoundTrip`, `solarSystemGravity`; world check goes layer top -> system -> back.
  Headless client (2026-11-04): the sun with its glow and a planet ball lit on the sun side both draw. Not yet
  felt in play: the gravity turns while flying.

## Free tilt (slice 3, `gravity/Tilt`)
- Floating in a system, players lean to the true pull at any angle; the hitbox stays six-way (Minecraft boxes can't
  turn). On blocks (`onGround`) the lean returns to the six-way frame. Data: `space.motion.free_tilt` (on),
  `tilt_speed` (share of the way per tick, 0.12). Players only; outside a system there is no lean.
- Kept as the world orientation of the player's own frame (`Tilt.now/prev`, on `GravityHolder`), ticked the same on
  the server (aiming: `calculateViewVector` in `EntityMixin`, projectiles) and the client (`ClientSpacePhysics`, every
  player: view in `CameraMixin.setRotation`, bodies in `EntityRenderDispatcherMixin` turned round the eyes so camera,
  aim and body agree). Jump/sneak thrust pushes along the leaned up (`Orbits.apply`).
- A gravity turn under a leaning player (`GravityDirection.apply`) skips the turning animation; `Tilt.regravitate`
  keeps the lean and the world look and moves the difference into yaw/pitch.
- Known: the eye still jumps when the six-way direction flips mid-flight (the box turns instantly) (WASD now push along the frame, see Free frame).
- Tests: `SpaceTests.freeTiltLeansToThePull` (frames match `RotationUtil`, lean, aim, gravity turn keeps the look, on
  blocks, setting off). Headless client: looking straight down (local) centres the planet ball under the crosshair.

## Free frame (Aaron 2026-11-15, Outer Wilds style)
- In space (a system or an orbit layer) a floating player's frame is their own: the mouse turns it with no clamp
  (`Tilt.spin` folds pitch into the frame every client tick and leaves pitch 0), roll keys Z / C (`TARDIS: Space`
  controls, rebindable; not Q/E, Q drops items) turn about the look at `roll_speed` degrees a tick. Jump / sneak push
  along the frame's up, WASD along its forward / right (`air_thrust`; vanilla walking input is taken off while
  floating, `ClientSpacePhysics.onInput`). Sprint + W/S still boost / brake.
- No lean by itself while floating. The frame eases upright (`tilt_speed`) only on blocks (six-way frame), on a ship's
  deck (the deck), while holding align (Left Alt, away from the focus / layer down), or within `auto_align_altitude`
  (24) of a surface (the focus ball's radius in a system, the blocks below in a layer).
- The client owns its own frame: it sends it (`SkyNet.FrameUp`) when it changes; the server keeps it for aiming and
  passes it to watchers (`FrameOf`). Tests: `SpaceTests.freeFrameTurns` (stays put, loop past vertical, roll, align
  converges, layer -> system turn matches the velocity mapping).

## Truly seamless (no layer): options for later
The swap now keeps frame, speed and look and happens in cloud, but the world still changes under you. To drop it:
1. **One dimension**: build each planet's full-size surface inside the system dimension as a wrapped region and let
   the ball's pull hand over to flat "down" near the surface. Simplest to reason about; full-size planets don't fit
   the build height and coordinate range together, so this needs smaller planets.
2. **Immersive Portals sky window**: keep the layer, but put an IP portal at the layer top showing the system
   (and the reverse at each entry sphere), so you see the far side before you cross; crossing is then just an IP
   portal pass, which IP already makes seamless. Cost: portal rendering per player near a top.
3. **Render the other side**: draw the system's balls and sun from the layer (and the layer's planet surface from
   the system) with the existing `SystemRenderer` / globe code, so both views match at the swap. Cheapest; the swap
   stays a teleport.
