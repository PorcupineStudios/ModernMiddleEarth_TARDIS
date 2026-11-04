# Capaldi console room, hand-built Minecraft style

Built by hand from the original model's layout, following `../review/minecraft-style-guide.md`. Nothing is voxelised.
Coordinates are MC blocks: +x east, +y up, +z south. Main floor top y=0, lower deck floor top y=-3 (3 blocks of headroom),
main door frame face at z=8 with the door centred on x=0.5. Rotor and console axis at (0.5, 0.25).

## Room shell: blocks (`structure/`)
- `capaldi_console_room.nbt`: vanilla structure file (DataVersion 3955 = 1.21.1), size 31x12x26. Place its min corner
  at (-15, -4, -15) relative to the room origin. Air fills the rest of the box, so placing it clears the room.
- Block ids use the mod's namespace `tardis_rb:` (regenerate with `write_nbt.py <json> <out> <namespace>`).
  Blocks: `roundel_wall`, `roundel_wall_lit` (emissive ring), `floor_plate`, `grille_plate` (emissive dots),
  `frame`, `wall_panel`, `light_strip` (emissive), plus `floor_plate_stairs`, `frame_stairs` (incl. half=top) and
  `frame_slab`; bookshelves are `minecraft:bookshelf`. Invisible collision-only blocks: `collision_post` (a 4 px post
  inside each arch pillar) and `collision_pillar` (10 px, the console pedestal on the lower deck). Full list in `capaldi_console_room.palette.json`.
- Textures: `textures/blocks/*.png` (16x16) with `*_emissive.png` masks for the glowing ones.
- `room_design.json`: the same layout as a plain list [x, y, z, shape, kind], easy to edit or regenerate.
- Exits: main door south (z=8, x 0..1, y 0..2); north doorway at the lower deck (z=-9, x 0..1, y -3..-1), reached
  by stairs down from the platform; east doorway at the lower deck (x=9, z -2..-1). Stairs up to the gallery on the
  west and east sides of the platform. Collision now comes from the blocks, so `collision.json` is gone.
- Hallways for the rooms system: three 4-wide, 3-tall hallways on the lower deck (floor top y=-3) run north, east
  and west 6 blocks past the outer wall and end open. `room_design.json` -> `hallway_ends` gives each open end
  (facing, min/max block); new rooms or more hallway attach there. A walk test from the main door reaches all three
  ends and the gallery.

## Models (`models/`, one shared texture `textures/console.png` + `console_emissive.png`)
Each part is an `.obj` (in blocks, already placed) and a Blockbench `.bbmodel` (free format, pixels, pivot at the
rotor axis on the floor) for editing.
- `console`: hexagonal pyramid of six panels tilted 21 degrees, seam trims, rim, skirt and pedestal (down to the lower deck).
- `rotor_glass` (semi-transparent), `rotor_column` (emissive core): rise and fall in flight.
- `rotor_spinners`, `rotor_spinner_mid`: hexagonal ceiling rings, spin in opposite directions.
- `arches`: 18 metal ribs, 10 boxes each.

## Controls (`parts.json` -> `controls`)
28 raised controls, each a named group in the console model with a hitbox in world blocks, its panel (0 = facing
the door, counting anticlockwise seen from above) and kind (lever, slider, screen, dial, buttons, switch, wheel,
keyboard, phone). Raytrace the player's look against the boxes; small painted buttons are grouped into one control.
- `tools/`: the Python scripts that build everything (console.py -> models, room_design.py -> layout, write_nbt.py -> structure).
