# TARDIS: the see-through door (Immersive Portals)
Read when: the doors, Immersive Portals, portals for space.

- Aaron 2026-11-03: seeing in and out of the TARDIS through the open door, walking through with no loading screen;
  the same tech is used for space. **Immersive Portals is required** (Modrinth `immersive-portals-neoforge` 6.0.7, one
  `-all` jar with mod ids `immersive_portals_core`, `imm_ptl`, `q_misc_util`; needs Cloth Config at runtime). Apache-2.0.
  Upstream (Fabric) is archived; community NeoForge forks fix Sodium 0.8 / Iris / Distant Horizons crashes.
- `door/Doors`: an open door = two portals made with `PortalAPI` (`Portal.ENTITY_TYPE`, `setOrientationAndSize`,
  `PortalAPI.setPortalTransformation`, `createReversePortal`, `spawnServerEntity`), tagged `tardis_rb:door`. The
  outside one stands `door.offset` in front of the police box's front face (so a player walking up crosses it; the
  box itself is solid), the inside one in front of the interior door (tuning `door.interior_door`/`interior_facing`).
  `rotation(from, to)` turns looking-in at the box into looking-into-the-room. State + portal ids in the record's
  module data `tardis_rb:door`.
- Use the box (crew): open/close; sneak-use: old teleport in. Same at the interior door. `Flight.land` closes the doors
  before any hop/jump. A door portal loaded from disk that its TARDIS no longer lists is dropped (`onJoin`).
- Tests: `gametest/DoorTests` (turn maths, a real portal pair in the test world), `WorldCheck.checkDoor` (opens the
  wreck's door on a dedicated server, checks both portals and where they lead, closes it). Untried in a client.
