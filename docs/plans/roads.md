# Roads that change: plan

Aaron, 2026-11-20. How the code works now: `lotr/docs/roads.md`.

## What Aaron wants

- **Caravans wear paths in.** Caravans and travellers going the same way again and again beat a trail into the ground,
  and the trail turns into a dirt path.
- **Better roads take waymakers.** Gravel, cobble and brick roads never wear in. Only colonists build them, working to
  a route someone draws at a Waymaker station.
- **Tunnels** (talked about the same day, not planned in detail yet): roads at one fixed height through mountains, like
  the road through Moria.

## The tier ladder

Each tier is a data file in `data/<ns>/road_tier/`. `built_by` says how a stretch gets to that tier.

| Rank | Tier | Built by | Wear to reach | Ground |
|---|---|---|---|---|
| 0 | `trail` | traffic | (start) | patchy coarse dirt and path, 1 block each side |
| 1 | `dirt_path` | traffic | 250 | dirt path, 1.5 each side |
| 2 | `gravel_road` | waymakers | - | gravel |
| 3 | `cobble_road` | waymakers | - | cobblestone, some mossy |
| 4 | `paved_road` | waymakers | - | the biome's own LOTR road bricks |

Wear only moves a stretch up to a tier built by traffic. At `dirt_path` it stops, however much traffic goes along it.

## Steps

1. **Framework. Built 2026-11-20.** Road works saved data (stretches, tier, wear, source), the tier ladder as data,
   laying stretches on loaded ground (natural ground only), routes treating them as roads, `/lotr road` commands,
   history entries, GameTests `RoadWorksTests`.
2. **Caravans wear trails.** Not started. Each finished caravan leg adds wear along its route. Where no stretch exists
   yet, enough journeys over the same ground make a `trail` stretch (`source: traffic`). Needs: how much wear one leg
   adds, how wear fades on roads nobody uses, and how close two routes must be to count as the same road.
3. **Waymaker station.** Not started. A colony block where a player draws a route on the map and picks a tier. It makes
   a stretch (`source: waymaker`) at the stretch's current tier and a job for the colony's waymaker colonists, who
   carry the materials and lay it section by section. Costs per tier go in the tier files.
4. **Tunnels.** Not started. A stretch with a fixed `y`: colonists dig a corridor 3 high through rock and line it, and
   the paver lays the floor at that height instead of on top of the ground.

## Open questions

- Should roads decay back down a tier if nobody uses them for a long time?
- Do AI rulers' colonies build roads between their own colonies?
- Should roads laid in chunks that are loaded later (a long road across the map) wait until a player is nearby, as now,
  or be pregenerated?
