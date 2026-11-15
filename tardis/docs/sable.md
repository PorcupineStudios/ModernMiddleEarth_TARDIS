# Create: Aeronautics / Sable compat (`compat/sable`)

Optional. Aeronautics 1.3.2 ships fly on Sable 2.0.6 "sub-levels". Built against both (`tardis/build.gradle`); dev runs
load them only with `-Paeronautics`. `tardis_rb.sable.mixins.json` applies only when Sable is loaded
(`SableMixinPlugin`), and fixes three clashes with Immersive Portals 6.0.7 plus one with our gravity port:

| Clash | Fix |
|---|---|
| IP and Sable both `@Redirect` `collide` in `Entity.move` (startup crash) | MixinSquared drops IP's `redirectHandleCollisions`; `PortalCollisionMixin` puts IP's cross-portal collision back as a `@WrapOperation` over Sable's |
| IP `@Overwrite`s `ChunkMap$TrackedEntity.updatePlayer` empty; Sable redirects inside it (startup crash) | MixinSquared drops Sable's `sable$trackSubLevelEntities` (it had nothing to act on) |
| Sable wraps each level's entity getter; IP casts it to its accessor (server tick crash) | `SubLevelEntityGetterMixin` answers IP's accessor from the wrapped vanilla getter |
| Sable sets on-ground in world axes; turned gravity never lands on a ceiling | `TurnedGravityGroundMixin` recomputes the collision flags in the entity's own axes when gravity isn't down |

Known gaps: IP tracks entities by its own chunk records, so entities riding a ship may not show to players who don't
have the ship's plot chunks loaded (untested). IP's portal-entity lookups don't see ship-borne entities. Verified only
by `runGameTestServer -Paeronautics` (43/43), not in a client.

## Ships in a solar system (`ShipOrbits`, `ShipAlign`)
On every Sable pre-physics tick, in a solar-system dimension (`Systems.isSystem`):
- Sable's own world-down gravity is set to zero there (`DimensionPhysicsData`, top priority; re-applied after a reload).
- Each ship gets the focus body's pull (`StarSystem.gravity`, blocks/tick² x 400 = blocks/s²) as added velocity.
- With `space.motion.ship_align` on, the ship's up eases toward "away from the focus centre": a damped spring about
  `up x want` (`ship_align_strength` 4, `ship_align_damping` 4: critically damped, about 2 s). Yaw stays free.
- Test: `SpaceTests.shipAlignSettles` (the spring alone, pure maths). Not yet run on a real ship in a client.

**Next step:** players standing on a ship still use their own gravity rules (six-way gravity, free tilt), not the ship's
floor. Make them take the ship's up while on board.
