# Capaldi console room, game-ready

Reduced from Aaron's purchased Smith/Capaldi TARDIS model (CGTrader, liamjmwilson). 1,039,599 → 74,984 triangles (light version). Same layout, grid and part names as ../capaldi (200k).

- `models/*.obj` + `.mtl`: one file per part, all in the same room coordinates (Minecraft axes: +X east, +Y up, +Z south, 1 unit = 1 block).
  `room` (148k, static), `console` (43k), screens, rotor parts, three door parts, 2 handles, 2 main levers, 5 sliders.
- `textures/`: one 1024 diffuse per material plus a 512 `_emissive` glow map where the original had one. `rotor_glass.png` has alpha.
- `parts.json`: triangle counts, bounding boxes, exits and grid facts. `previews/`: renders before and after.

Grid: main floor at y=0, front door centred on x=0.5 with the frame face on z=8.0 (south). Openings sit on block edges:
south door z=8, north doorway z=-9, east doorway x=9 (centred on z=-1.5). The west wall has no doorway.

Floor light grilles are solid glowing plates here. Pieces under 2 cm (outside the console) were dropped.
Licence: CGTrader royalty-free; do not ship the raw model as a separate download.
