# Space: planets, orbit layers, the way up and down, wrapping, the globe
Read when: touching `space/`, `progression/SkyLink`, `net/SkyNet`, `client/Globe*`, `data/*/tardis/sky`, or the
shared API's `PlanetProvider`.

- Aaron (2026-11-03): seamless Outer Wilds-style space, any dimension can lead into it; Immersive Portals is required.
  2026-11-04: there was no way back to Middle-earth from space; Middle-earth is a planet and **can't be scaled down**,
  "cheat as long as it's immersive"; a custom planet-scale portal system on IP's foundations; performance is a must.
- **Planets stay full size; space is a scale model.** `tardis/sky/<name>.json` (`SkyLink`): `dimension`, `space`,
  `surface` {min/max x/z} (x once round the globe, z pole to pole, north = min z; make it 2:1 for an undistorted
  globe), `ceiling`, `ground`, `orbit` {x, z, scale, floor, top, max_altitude}, `wrap` (globe/torus/none),
  `seam_portals`, `cloud_band`, `picture` {texture, min/max x/z, fill}, `map_size`. `space/Planet` holds the maths.
- **Orbit layer**: each planet's space is a layer of the space dimension: surface x/z shrunk `orbit.scale` times round
  `orbit.x/z`; height is altitude, `altitude(y) = (ceiling - ground) * e^(k (y - floor))`, reaching `max_altitude`
  planet radii at `top`. Several planets share `tardis_rb:space` at different `orbit.x` (overworld 0; LOTR's
  `middle_earth` / `vanilla_overworld` links at 2,000,000).
- **Up/down** (`SpaceTravel`, every `space.check_ticks`): above the ceiling -> 3 above the layer floor over the same
  spot; below the floor -> 3 under the ceiling (+ slow fall). Players use `PortalAPI.teleportEntity` (no loading
  screen). Both swaps happen inside the cloud bank (`cloud_band` blocks under the ceiling / over the floor), where the
  client thickens fog to white and draws a white haze box, so the swap doesn't show. Space has low gravity
  (`Attributes.GRAVITY` modifier `tardis_rb:space_gravity`).
- **Wrapping**: `Planet.wrap`: x loops; on a globe, crossing a pole folds the edge (`poleX`: x -> 2*centre + W/2 - x)
  and turns you 180 deg. A fold, not a shift, because a portal can turn but not mirror. The server wraps anything
  1.5 blocks past an edge (surface and orbit layer).
- **Seam portals** (`SeamPortals`): IP's own world wrapping keeps a portal along each whole edge; ours puts one-way IP
  portals (full world height, `space.seam_segment` long) only on the bit of edge within `space.seam_view` of a
  player, removed `space.seam_linger` ticks after. Never saved (dropped on load). Each side has its own pieces, so
  they never overlap.
- **Middle-earth** (LOTR port, `SharedApiHooks.middleEarthPlanet` via the API `PlanetProvider`): the map is 3200 x 4000
  px at 128 blocks/px (409,600 x 512,000 blocks); the planet is the map's full height and twice that round (1,024,000),
  centred on the map: Middle-earth on one side, open sea round the back, so the east/west seams are mid-ocean.
  The globe is painted from `lotr_port:textures/gui/map.png`. The provider also covers the overworld of a
  Middle-earth world.
- **Globe** (`client/GlobeRenderer`, `GlobeMesh`, `GlobeTextures`): drawn at AFTER_SKY without depth (behind all
  blocks), under each player from their own spot at full size and full-size altitude, shrunk towards the camera to
  fit in the far plane. Static VertexBuffers: UV sphere at 16..256 steps chosen by screen size, plus a shade sphere
  turned to the sun (vanilla sun angle from the day time) for day/night. Textures: pictures resized off-thread to
  4096 x 2048; planets without one get `PlanetMaps` (server samples biome colours, `map_budget_ms` per tick, saved in
  `<world>/data/tardis_rb_planets/`, sent deflated). Mipmapped, nearest when close (blocky).
- Tests: `SpaceTests` (layer round trip, altitude, wrap + pole fold, seam pieces), world check (up, down, east edge
  wrap, seam portals at the edge leading to the west edge). **Not tried in a game client**: the globe drawing, fog,
  haze, the look of the seams, IP teleport smoothness.
- Next: inter-planet travel (TARDIS / leaving the layer top), atmosphere rim, a sharper local map patch when low,
  terrain relief on the globe.
