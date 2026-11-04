# Capaldi TARDIS interior, cube version

Coordinates are Minecraft blocks: +x east, +y up, +z south. Main floor y=0, door centred x=0.5, door frame face at z=8.
Exits: south (main door), north doorway z=-9, east doorway x=9. Basement floor y=-3, about 2.7 blocks headroom.

- `models/*.obj`: room (static), console, rotor parts (animate), doors (animate), `controls.obj` (one object per control).
- `textures/palette.png` + `palette_emissive.png` (glow mask); `palette_glass.png` (semi-transparent) for rotor_glass.
- `parts.json`: parts, triangle counts, exits, and `controls`: name, kind (button/lever/slider/screen/handle),
  console_side, hitbox_min/max in blocks. Raytrace the player's look against these boxes to click a control.
- `collision.json`: `shapes` are 64-bit masks (hex) of 4x4x4 quarter-block cubes, bit = sx + 4*sy + 16*sz;
  `cells` are [x,y,z,shape] per block space. Turn each mask into boxes (merge runs) for an invisible collision block.
Stats: 56k triangles exported, 125 controls, 1864 collision cells, 1479 shapes.
