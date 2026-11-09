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
  (`drag_free`); speed capped at `max_speed` (40; the server's "moved too quickly" check is off in space levels, `SpaceSpeedMixin`, Aaron 2026-11-04: faster navigation). Sprint + forward in the air boosts along the look by `boost` (0.25) a tick, sprint + back brakes. Jump / sneak in the air
  push up / down (`thrust`). Players run it on their client (`ClientSpacePhysics`, tuning comes in `SkyNet.Planets`),
  everything else on the server (`SpacePhysics`, EntityTickEvent.Post). Creative flyers, spectators, riders skip it.
- Players' sky / floor / edge checks run every tick (others every `check_ticks`), so fast movers can't overshoot far.
- **`/gravity`** (`gravity/GravityScale`, op 2): `get [targets]`, `set <targets> <scale>`, `reset <targets>`,
  `dimension <dim> [<scale>|reset]` (saved, `tardis_rb_gravity`). Both are GRAVITY attribute multipliers
  (`tardis_rb:gravity_scale`, `tardis_rb:dimension_gravity`), so they reach the client and `Orbits` multiplies by them.
- **Gravity direction (built)**: `/gravity direction <targets> [<down|up|north|south|east|west> [turn_ms]]`
  (`gravity/GravityDirection`). Gravity Changer's 1.21.1 mixins are ported in `gravity/gc` (licence in its
  `NOTICE.md`): config `tardis_rb.mixins.json`, `defaultRequire` 1 so a bad target crashes at load instead of silently
  doing nothing. The direction is a field on every entity (`gc/mixin/EntityGravityDataMixin`, NBT
  `tardis_rb:gravity`), synced by `SkyNet.GravityDir` (on change, start tracking, login, dimension change). Only
  living things, projectiles and minecarts turn (`gc/EntityTags`). **While it isn't down, delta movement is in the
  entity's own frame**: use `GravityChangerAPI.getWorldVelocity`/`setWorldVelocity` (Orbits does).
- Port fixes (upstream 1.21.1 port bugs or NeoForge differences): `Entity.collide` replaced by our own HEAD inject
  (`tardis_rb$collide`, vanilla 1.21.1 step-up along local axes; upstream's injections didn't fit the new code and
  passed through walls); `isFree` ModifyArgs set all three args; Camera hooks `setRotation(FFF)` (NeoForge adds roll)
  and uses the real partial tick; dropped: LivingEntity.baseTick bubble-column redirect (NeoForge dead-codes it), the
  fluid-push pair (NeoForge rewrote fluid pushing; currents push in the entity's frame), the biome ambient sound mixin
  (intermediary lambda name), item throw (BehaviorUtils) and debug mixins.
- Checking mixin targets without a client: `tardis/tools/mixin-check/check.sh` counts each injector's target
  instructions in the merged NeoForge jar (catches NeoForge differences in client classes too). Seen in a headless
  client (`docs/headless-client.md`): the player stood upside down on a ceiling and came back down.
- Next: slice 2, the solar system zone (3D, every body pulls, a sun; leaving a layer's top); slice 3, tilting.
- Tests: `SpaceTests.gravityFadesAndOrbitsHold`, `gravityCommandsScaleGravity`, `gravityDirectionTurns` (falls onto
  a ceiling and a wall, saved).
- Fast flight loads no chunks (Aaron 2026-11-04: extreme speeds were laggy; `space/SpaceChunks`). Space is void,
  yet at 40 blocks a tick Minecraft still made, lit, saved and sent dozens of empty chunks a tick, and the client
  froze whenever it outran them. A player faster than `chunk_skip_speed` (4 blocks a tick) is left out of chunk
  loading until slower than half that: vanilla (`SpaceChunkMapMixin` on `ChunkMap.skipPlayer`, as a spectator who may
  not generate chunks) and Immersive Portals (`compat/immportals` `ChunkVisibilityMixin`: no base loaders, so none
  through portals either). The client makes the void itself (`client/SpaceVoidChunks`): empty chunks where it is and
  where it will be in the next two ticks, loaded through the normal chunk packet handler; a chunk the server sends
  replaces one, and still-empty ones further than render distance + 2 are dropped (Immersive Portals' client chunk
  map has no range of its own). Known: while skipping, blocks someone built in space are not loaded (you fly through
  them) and far players and entities are not seen. Test: `SpaceTests.fastSpaceFlightSkipsChunkLoading` (the rule, and
  both hooks merged).
