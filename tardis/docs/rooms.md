# TARDIS: rooms
Read when: adding rooms to the interior, the room cap, room templates (`interior/Rooms`, data `tardis/room/`).

- A room file (`progression/Room`): `template` (structure id), `cost` (inline or a cost id), optional `requirements`.
  Templates are 9 x 5 x 9 placeholders written by `tools/rooms.py` (library, workshop) until Aaron's builds replace them.
- `Rooms.add` checks, in order: the room exists, the `tardis_rb:add_room` feature (rooms level 1), the cap, the room's
  requirements; then fires `RoomEvent.Pre` (cancel or change cost), pays (`Repairs.pay`), builds, and records
  `TardisRecord.addRoom` (fires `RoomEvent.Post`) plus a `tardis_rb:room_added` history entry.
- Cap: the `room_cap` stat (subsystem `rooms` levels: 1, 2, 3, 5), counting the console room. Research `stats` can
  raise it too.
- Layout (Aaron 2026-11-04, Capaldi room): rooms grow from the open ends of the console room's three lower-deck
  hallways (`interior.room_anchors`: min/max cells + facing; north, east, west). Slot n uses anchor (n-1) % 3 at depth
  (n-1) / 3, `room_step` (9) deeper each time. `Rooms.placement` turns the template so local +z runs along the
  hallway (NORTH = 180, WEST = CW 90, EAST = CCW 90) and lines its local x `openX`..+width (centred) up with the
  hallway; an opening the hallway's size (4 x 3) is cut in the front wall (and the previous room's back wall).
  Templates are 9 wide with their floor at local y 0 = the lower deck's floor. Rooms can't be removed yet.
- `/tardis rebuild_interior`: a fresh plot with the console room and recorded rooms built on the current layout
  (for interiors made before 2026-11-04); the old plot is left; players inside are moved.
- The record's codec group is full (16 fields), so `rooms` sits beside them through `Codec.mapPair`; the save stays flat.
- Built from the Repairs & research screen (kind ROOM, "Build") or `/tardis room <id>`.
- Tests: `gametest/RoomTests` (builds 20 blocks above a test platform), and the CI "TARDIS world" check adds a library
  in the real interior dimension.
