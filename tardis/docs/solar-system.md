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
  `toLayer`, the reverse, arriving just under the layer top. Gravity direction is set with no turn on arrival.
- Pull: sum of G * tuning.gravity * body.gravity * (r/d)^2 over the sun and all balls (`StarSystem.gravity`). Feet
  turn to the nearest of the six directions of the total pull (`GravityDirection`, 1000 ms turn), checked every check
  tick while not on the ground. Players step on the client (`ClientSpacePhysics`), others on the server
  (`SpacePhysics`), both through `Orbits.stepSystem`.
- The sun: inside its radius you burn (fire + `inFire` hurt, `burn` per hit) and get pushed out. Zone edges: build
  height +-8 is clamped.
- Drawing (`SystemRenderer`, AFTER_SKY, no depth): balls far to near, shrunk to the far plane; planets use their globe
  textures (`GlobeTextures`) on a lat/long ball with a shade turned to the sun; the sun has 3 additive glow shells.
- Planets cache (`Planets`) is keyed also by the level count: during startup it cached an empty list and the world
  check said "the Overworld is no planet".
- Tests: `SpaceTests.solarSystemRoundTrip`, `solarSystemGravity`; world check goes layer top -> system -> back.
  Headless client (2026-11-04): the sun with its glow and a planet ball lit on the sun side both draw. Not yet
  felt in play: the gravity turns while flying.
