# Create: Aeronautics / Sable compat (`compat/sable`)

Optional. Aeronautics 1.3.2 ships fly on Sable 2.0.6 "sub-levels". Built against both (`tardis/build.gradle`); dev runs
load them only with `-Paeronautics`. `tardis_rb.sable.mixins.json` applies only when Sable is loaded
(`SableMixinPlugin`), and fixes four clashes with Immersive Portals 6.0.7 plus one with our gravity port:

| Clash | Fix |
|---|---|
| IP and Sable both `@Redirect` `collide` in `Entity.move` (startup crash) | MixinSquared drops IP's `redirectHandleCollisions`; `PortalCollisionMixin` puts IP's cross-portal collision back as a `@WrapOperation` over Sable's |
| IP `@Overwrite`s `ChunkMap$TrackedEntity.updatePlayer` empty; Sable redirects inside it (startup crash) | MixinSquared drops Sable's `sable$trackSubLevelEntities` (it had nothing to act on) |
| Sable wraps each level's entity getter; IP casts it to its accessor (server tick crash) | `SubLevelEntityGetterMixin` answers IP's accessor from the wrapped vanilla getter |
| Sable sets on-ground in world axes; turned gravity never lands on a ceiling | `TurnedGravityGroundMixin` recomputes the collision flags in the entity's own axes when gravity isn't down |
| IP `@Overwrite`s `Player.canPlayerFitWithinBlocksAndEntitiesWhen`; Sable wraps a call inside it (client startup crash) | MixinSquared drops Sable's `sable$noCollisionWithSubLevels`; `PlayerFitMixin` (priority 1100) re-adds it on IP's version |

Known gaps: IP tracks entities by its own chunk records, so entities riding a ship may not show to players who don't
have the ship's plot chunks loaded (untested). IP's portal-entity lookups don't see ship-borne entities. Verified only
by `runGameTestServer -Paeronautics` and a dev `runClient -Paeronautics` reaching the title screen; no world joined.

## Space stations are ships (`ShipOrbits`, `ShipAlign`, `Ships`, `gravity/Decks`)
Aaron 2026-11-15: a space station is an Aeronautics ship that really moves and turns (the fixed-frame stations are
retired, `docs/space.md`). On every Sable pre-physics tick, in a solar-system dimension (`Systems.isSystem`):
- Sable's world-down gravity is cancelled each step (its pipeline keeps the gravity it started with, so changing the
  dimension data alone does nothing), and the dimension gets zero gravity, drag and air pressure (`DimensionPhysicsData`,
  top priority, re-applied after a reload).
- Each ship gets the focus body's pull (`StarSystem.gravity`, blocks/tick² x 400 = blocks/s²) as added velocity.
- With `space.motion.ship_align` on, the ship's up eases toward "away from the focus": a damped spring about
  `up x want` (`ship_align_strength` 9, `ship_align_damping` 6: critically damped, about 1.5 s). The damping works
  against the spin beyond the orbit's own turning rate ((r x v) / r²), so an orbiting ship doesn't lag behind. Yaw
  stays free.
- `/tardis station orbit` (op 2): the ship you stand on or look at (64 blocks) gets a circular orbit round its focus:
  speed sqrt(g r) along your look made square to the radius, and one turn of spin per orbit.
- **Decks:** standing on or inside a ship in a solar system, a player's down is the ship's down (its orientation on
  0, -1, 0) at `space.stations.deck_gravity` (1.0) x normal, on top of the focus pull that the ship feels too. Free
  tilt leans the view to the ship's floor even standing; the six-way hitbox takes the nearest direction. So a player
  on a deck turns with the station as it orbits.
- Tests: `SpaceTests.shipAlignSettles` (the spring alone: 90 degrees off settles within 2, overshoot under 10) and
  `SpaceTests.stationOrbitKeepsItsFloorDown` (a real 3x3 Sable deck, level orbit radius 16, 16 s a turn: after a
  quarter orbit its floor is within 5 degrees of facing the centre; passes trivially without Sable).

**Gaps:**
- Ships can't cross between an orbit layer and the solar-system zone: Sable has no API to move a sub-level to another
  dimension (each level has its own `SubLevelContainer`), so a ship rising out of a layer stays there.
- Ships in an orbit layer get Sable's normal world-down gravity; nothing there co-orbits them.
- Not tried in a game client: the deck lean, walking on a turning deck, `/tardis station orbit`.
