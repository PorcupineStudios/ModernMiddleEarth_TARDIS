# TARDIS: destinations and jumps
Read when: dimension travel, destination files (`tardis/destination/`), `flight/Destinations`, `Flight.jump`.

- A destination file (`progression/Destination`): `dimension`, optional `requirements`, `charge` per jump. A dimension
  with no file is never reachable. Shipped: overworld and middle_earth (milestone `restored_core`), the_nether
  (milestone `vortex_drive`), the_end (milestone `void_anchor`). The two later milestones have no requirements yet,
  so only `/tardis milestone` passes them until a later stage claims them.
- `Destinations.closed` order: unknown id; config FORCED_OFF / FORCED_ON (by destination id); the start dimension
  (`Providers.startDimension`) is always open so the TARDIS can get home; requirements; last, the dimension must exist.
  Locked is reported before missing, so a pack without the Nether still says "can't reach yet".
- `Flight.jump` shares landing with hops (`Flight.land`): JumpEvent.Plan/Cost/Land/Arrived, then a `tardis_rb:jump`
  history entry with `from_dimension`. With no target it lands near the dimension's spawn.
- In a Middle-earth world type `minecraft:overworld` *is* Middle-earth (the start), so the "overworld" destination is
  home there. A separate vanilla Overworld would need its own datapack dimension: an open question for Aaron.
- Console: a destination picker and Jump button (lands near the X/Y/Z boxes). Command: `/tardis jump <id> [pos]`.
- Test: `gametest/DestinationTests` (start dimension passed in as the interior, since the test server starts in the
  overworld; then a real jump).
