# Space: sky links, the space dimension, low gravity
Read when: touching `space/`, `progression/SkyLink`, `net/SkyNet`, `client/GlobeRenderer` or `data/tardis_rb/tardis/sky`.

- Aaron (2026-11-03): seamless Outer Wilds-style space, any dimension can lead into it; Immersive Portals is required.
  Planets: a looping flat world below (IP world wrapping, later slice), our own rendered globe seen from space.
- `tardis/sky/<name>.json` (`SkyLink`): `dimension`, `space`, `center`, `radius`, `ceiling`, `half_size`,
  `arrive_below`. The surface square -half..+half maps onto the ball: x is longitude, z latitude. Validator drops a
  link whose dimension is its own space. Ships with `overworld.json` -> `tardis_rb:space` (void, fixed night).
- `SpaceTravel` (server tick every `space.check_ticks`): above the ceiling -> 16 blocks over the same spot on the ball
  (`PortalAPI.teleportEntity`); inside the ball (radius + 2) or near the bottom of space -> the spot below, at
  ceiling - arrive_below, with Slow Falling for `space.landing_slow_fall` seconds. Riders and vehicles with riders
  are skipped for now (move them together later).
- Low gravity: a transient `Attributes.GRAVITY` modifier `tardis_rb:space_gravity` (ADD_MULTIPLIED_TOTAL,
  `space.gravity` - 1) on living things in space, set on join and every check, removed when they leave.
- The globe: `SkyNet.Globes` is sent on login and dimension change; `GlobeRenderer` draws a shaded ball with
  placeholder sea/land/ice colours at AFTER_SOLID_BLOCKS. Not yet drawn from the real terrain.
- Tests: `SpaceTests` (toSpace/toSurface round trip, wrap); world check moves an armor stand up and back down.
- Not tried in a game client yet: how the globe looks, IP smoothness of the jump, flying in low gravity.
