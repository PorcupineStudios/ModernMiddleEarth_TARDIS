# Road works (`road/`)

Roads that change after the world is made. The plan, and what is still to come (caravans wearing trails, the Waymaker
station, tunnels): `docs/plans/roads.md`.

LOTR's own roads (`world/map/Roads`, `roads.json`) are world generation. The terrain is shaped around them, so they never
change. Road works are a separate layer laid on top of the land.

## Pieces

- **Tiers** (`RoadTier`, `data/<ns>/road_tier/<id>.json`, reloads): `rank` (order on the ladder), `built_by`
  (`traffic` or `waymakers`), `wear` (wear needed to reach it by traffic), `surface` (weighted blocks), `biome_style`
  (lay the biome's LOTR road blocks from `roads.json` instead), `coverage` (share of columns laid), `half_width`,
  `route_road` (routes treat it as road). Shipped: `trail`, `dirt_path` (traffic), `gravel_road`, `cobble_road`,
  `paved_road` (waymakers).
- **Settings** `road_works/settings.json`: `columns_per_tick` (512).
- **Saved data** `RoadWorks`, file `lotr_port_road_works` (`dataVersion` 1): stretches with an id, dimension, line of
  points, tier, wear, source (`traffic`, `waymaker`, `command`) and the chunks their tier has been laid in.
  `add`, `setTier`, `addWear`, `remove`, `at(dim, x, z)` (the stretch a column is on), `touching(dim, chunk)`.
  `addWear` climbs the ladder only to tiers built by traffic. Adding a stretch or changing its tier goes in the history
  log as `lotr_port:road_changed`.
- **Paving** `RoadPaving`: lays a stretch's tier in its loaded chunks, and in each chunk that loads later, a chunk at a
  time within the tick budget. It replaces only natural ground (block tag `lotr_port:road_surface`), the tiers' own
  blocks and LOTR's road blocks. It never touches what players built, skips water, and clears plants on the road. A
  tier change lays the stretch again, so roads can also go down a tier. Slabs on steps are not laid yet.
- **Routes**: `MapTerrain.road` is also true on stretches whose tier is a `route_road` (`RoadWorks.snapshot()`, safe
  to read off the server thread), so caravans and journeys plan along them.
- **Commands** `/lotr road tiers|list|add <tier> <x1> <z1> <x2> <z2>|tier <id> <tier>|wear <id> <amount>|remove <id>`.
- **GameTests** `RoadWorksTests`: a stretch paves natural ground but not planks; wear takes a trail to a dirt path and
  no further; routes see the stretch as road.

Not run in game yet. The cloud sandbox cannot build it; CI builds it and runs the GameTests.
