# TARDIS: the see-through door (Immersive Portals)
Read when: the doors, Immersive Portals, portals for space.

- Aaron 2026-11-03: seeing in and out of the TARDIS through the open door, walking through with no loading screen;
  the same tech is used for space. **Immersive Portals is required** (Modrinth `immersive-portals-neoforge` 6.0.7, one
  `-all` jar with mod ids `immersive_portals_core`, `imm_ptl`, `q_misc_util`; needs Cloth Config at runtime). Apache-2.0.
  Upstream (Fabric) is archived; community NeoForge forks fix Sodium 0.8 / Iris / Distant Horizons crashes.
  Sodium clash (Aaron 2026-11-03): IP 6.0.7 works only with Sodium 0.6.0, Create 6.0.10 refuses Sodium below 0.6.9,
  so the test instance runs without Sodium (removed from the test-build bundle). Candidate: the CurseForge
  "Immersive Portals - Sodium/Iris/DH Compat Fork" (not yet checked for 1.21.1 NeoForge).
- `door/Doors`: an open door = two portals made with `PortalAPI` (`Portal.ENTITY_TYPE`, `setOrientationAndSize`,
  `PortalAPI.setPortalTransformation`, `createReversePortal`, `spawnServerEntity`), tagged `tardis_rb:door`. The
  outside one stands in the police box's doorway, the inside one in the interior doorway (tuning
  `door.interior_door`/`interior_facing`). Aaron (2026-11-03): in line with the doorway, not in front of the box. Open
  door blocks (`open=true`) are hollow shells with a 1 px frame (2 px on top), models from `tools/door_models.py`;
  the portal stands `door.offset` (-0.07) behind the face, just behind the frame, and the collision keeps only the
  side and back walls so players walk in. The interior door has `half=lower/upper`.
  `rotation(from, to)` turns looking-in at the box into looking-into-the-room. State + portal ids in the record's
  module data `tardis_rb:door`.
- Use the box: open/close if `DoorLock` allows (outside: OPEN all / OWNER / CREW (default) / LOCKED; inside: anyone
  unless LOCKED). Sneak-use cycles the lock with the original mod's rules (`DoorLock.cycle`; crew stand in for its key
  holders; LOCKED only from inside). The sneak teleport in/out was removed (it crashed Aaron's server, 2026-11-04). `Flight.land` closes the doors
  before any hop/jump. A door portal loaded from disk that its TARDIS no longer lists is dropped (`onJoin`).
- Tests: `gametest/DoorTests` (turn maths, a real portal pair in the test world), `WorldCheck.checkDoor` (opens the
  wreck's door on a dedicated server, checks both portals and where they lead, closes it). Untried in a client.
- **Police box model** (Aaron 2026-11-03): his Blockbench glTF is kept in `tools/police_box/` (model.gltf, paint.png,
  glow.png); `python3 tardis/tools/police_box.py` writes `assets/tardis_rb/police_box/model.json` (triangles, door
  hinges, `door_swing` degrees/ticks, `glow_from_look`) and `textures/entity/police_box/look_0..3.png` + `glow.png`.
  `ExteriorBlock` renders as ENTITYBLOCK_ANIMATED; `client/ExteriorRenderer` draws it, turned by FACING (model front
  = -z = north), and swings both doors open outward on their hinges over `ticks` when OPEN changes (client-only
  `doorAnim` on the block entity). Outward, because the portal fills the doorway and would hide doors swinging in.
  Untried in a client.
- **Closing** (Aaron 2026-11-04): using open doors calls `Doors.shut`: `closing_at` (game time + `door.close_ticks`,
  keep equal to the model's `door_swing.ticks`) goes in the module data and the box gets `closing=true`, so the
  renderer swings the doors shut while OPEN (portal, hollow shape) stays. `Doors.onTick` calls `close` when due; using
  the doors meanwhile reopens them. `close` (also used at once by Flight) first steps every entity overlapping a
  door's two blocks out in front of it (`clearDoorway`: outside the box, or into the console room), so nobody is
  left inside the solid door. `WorldCheck.checkDoor` tests both with an armor stand.
- **Using the doorway**: the portal eats right-clicks, so `client/DoorInput` walks the view in 0.05 steps and, if it
  reaches an open door block before anything solid (not sneaking, not standing in it), sends `DoorNet.Use`; the server
  checks reach (8) and OPEN and runs the block's normal use. Immersive Portals takes clicks aimed through a portal
  before NeoForge's `InteractionKeyMappingTriggered` (Aaron's test, 2026-11-04: nothing happened), so it runs in
  `ClientTickEvent.Pre`, draining `keyUse.consumeClick()` and `setDown(false)` before the game handles keys.
- **Portal size = the model's doorway** (2026-11-04): Aaron saw glitches at the edges at steep angles: the portal
  (0.875 x 1.875) was bigger than the police box model's doorway (x +-0.375, y 0.125-1.75, doors' outer face at
  z -0.4375). Now 0.75 x 1.625, `bottom` 0.125, offset -0.0625; the interior door shell's frame and `DoorShapes`
  walls match (2 px sides, 2 px sill, lintel at 28 px). Portals opened before keep their old size until reopened.
- The glow layer is a full-bright `entityCutoutNoCull`, not `RenderType.eyes`: the eyes pass doesn't seem to get IP's
  clipping, and the glowing lantern showed as a white box through the doorway (a guess from the screenshot).
- **A loose box's doorway** (2026-11-09): `Doors.follow` moves the outside portal (and the inside one's destination)
  every tick on the server. Door portals have Immersive Portals' default animation off (`disableDefaultAnimation`):
  it eased each move over several ticks, so on clients the doorway trailed ~5 blocks behind a box flying the vortex.
  The server's update still reaches a client a tick or more after the box (flown from that client, or lerped), so
  `client/TardisBodyRenderer.keepDoorway` also puts the box's door portal (tag `tardis_rb:door`, within 3 blocks) on
  the box as drawn, each frame.
