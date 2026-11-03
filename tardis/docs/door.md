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
- Use the box (crew): open/close; sneak-use: old teleport in. Same at the interior door. `Flight.land` closes the doors
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
