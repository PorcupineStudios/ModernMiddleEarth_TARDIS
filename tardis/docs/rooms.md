# TARDIS: rooms
Read when: adding rooms to the interior, the room cap, room templates (`interior/Rooms`, data `tardis/room/`).

- A room file (`progression/Room`): `template` (structure id), `cost` (inline or a cost id), optional `requirements`.
  Templates are 9 x 5 x 9 placeholders written by `tools/rooms.py` (library, workshop) until Aaron's builds replace them.
- `Rooms.add` checks, in order: the room exists, the `tardis_rb:add_room` feature (rooms level 1), the cap, the room's
  requirements; then fires `RoomEvent.Pre` (cancel or change cost), pays (`Repairs.pay`), builds, and records
  `TardisRecord.addRoom` (fires `RoomEvent.Post`) plus a `tardis_rb:room_added` history entry.
- Cap: the `room_cap` stat (subsystem `rooms` levels: 1, 2, 3, 5), counting the console room. Research `stats` can
  raise it too.
- Layout: slot n's corner = plot origin + n x `interior.room_step` along z (default 9). A 1 x 2 doorway is carved at
  `interior.doorway` (x, y) through the back wall of the room before and the new room's front wall. Rooms can't be
  removed yet.
- The record's codec group is full (16 fields), so `rooms` sits beside them through `Codec.mapPair`; the save stays flat.
- Built from the Repairs & research screen (kind ROOM, "Build") or `/tardis room <id>`.
- Tests: `gametest/RoomTests` (builds 20 blocks above a test platform), and the CI "TARDIS world" check adds a library
  in the real interior dimension.
