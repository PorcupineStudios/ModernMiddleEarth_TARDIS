# Making the Capaldi interior look clean and Minecraft-like

Review of `minecraft_ready_blocky` (state of 2026-11-04 04:04 UTC) against the original Capaldi model and the
TARDIS interiors in Minecraft mods (TARDIS Refined, AIT, Teab's Doctor Who mod; Tennant coral and Capaldi/Smith
roundel rooms). Side-by-side: `style_comparison.jpg` in this folder.

## Why it doesn't read as Minecraft yet
The mods' interiors look clean because they are **built, not voxelised**. Ours is the original mesh sampled into
1/4- and 1/16-block cubes, so:
- Every slope is a staircase of tiny steps (console panels, stair sides, ceiling). Mods use flat tilted panels.
- Detail turned into noise: controls are scattered black/grey pixels, the roundel walls are lost, the floor grilles
  became white blobs.
- Colour comes from a 16x16 palette, so faces are flat single colours and the room is almost all grey. The original
  is warm: amber/copper roundels, silver console, dark blue-grey frame, white light strips.
- Sizes are mixed (1/4-block room cubes, 1/16 console, 1/32 controls). Minecraft looks right when everything sits on
  one 16-pixels-per-block grid.
- The numbers show it: 56k triangles, and 1475 different collision shapes for 1865 block spaces (almost every block
  space is unique).

## What to change

**1. Split the room into blocks and models, like the mods do.**
- Room shell (floor, walls, roundel walls, stairs, balcony, ceiling, bookshelves) = real blocks placed from a
  structure `.nbt`: a handful of custom TARDIS blocks (roundel wall, roundel wall with light, floor plate, glowing
  grille plate, dark frame/pillar, light strip) plus vanilla-style slabs and stairs. Lighting, culling and collision
  then come for free and `collision.json` goes away.
- Only the console, the time rotor, the doors and the curved metal arches stay as models (block-entity renderers).

**2. Model by hand in cuboids (Blockbench style), not by voxelising.**
- Every part is a box measured in whole pixels (1/16 block), rotated as needed. A model renderer can rotate a box
  by any angle, so slopes are one rotated box, never steps.
- Budget: console about 300-600 boxes, rotor about 50, each arch about 10-20. That's roughly 6-10k triangles for
  everything, versus 56k now.
- Nothing smaller than 1 pixel; controls 2 pixels or more.

**3. Console: hexagonal pyramid of flat panels.**
- Six panels, each a single thin box (1-2 px thick) tilted about 20-25 degrees, meeting on a hexagon rim, with a
  2 px trim box along each seam. Under it, a six-sided skirt sloping inward to a narrower pedestal (six more tilted
  boxes). Panel top edge at about 0.9-1 block, so the player looks down onto it.
- Detail is painted, not modelled: each panel gets a 16 px/block texture with buttons, lights and grilles drawn as
  pixels (like TARDIS Refined's panels). Only the controls you click are raised boxes: the big levers, the slider
  bank, the dials, the two screens. Aim for 20-40 raised controls, not 125 single-pixel bumps. Small buttons can
  stay painted and be grouped into one clickable control.
- Screens = a flat plane with an emissive texture (vortex image), set into a 1 px frame.
- Hitboxes at least 2x2 px, with 1 px gap between neighbours. Keep the console centred on the rotor axis.

**4. Time rotor: hexagonal.**
- Hex prism = three identical boxes rotated 0, 60 and 120 degrees around the vertical axis. Same trick for the glass
  column (semi-transparent texture) and the hex collars at top and bottom. Spinner rings = six short boxes in a ring.

**5. Metal arches at 1/16 block.**
- Each arch is a chain of 1-2 px thick boxes, each about 1 block long, rotated to follow the curve (steps of about
  10-15 degrees). Flat metal texture with a 1 px highlight edge. No stair-stepping.

**6. Textures and colour.**
- Use proper per-face textures at 16 px per block (one texture atlas for the console, a few block textures for the
  room), not one colour per cube from a palette. Keep a small warm palette from the original: amber/copper roundel
  glow, silver-grey console, dark blue-grey frame, white emissive strips and grilles.
- Roundel walls: a flat block face with the hexagon roundel painted in (amber ring, dark centre), emissive on the
  ring. The hexagon honeycomb then lines up with the block grid.

**7. More flat areas.**
- Floor: flat 1-block plates; the light grilles are a texture with emissive dots, flush with the floor.
- Walls and ceiling: large flat faces, break them up with texture and a few framing boxes, not surface noise.
- Keep the layout and exits as now: door centred x=0.5 at z=8, north doorway z=-9, east x=9, main floor y=0,
  basement floor y=-3 with 2.7+ blocks of headroom.

## Reference
- Licence check before copying anything: AIT (LGPL) has Coral (Tennant) and Toyota (Smith/Capaldi) consoles that are
  fine to study; TARDIS Refined is non-compete, so look only, never reuse.
- Small fixes in the current files: README says 1864 cells / 1479 shapes but parts.json says 1865 / 1475; collision
  masks are written as signed hex (e.g. `-fff0fff0fff1000`), so the Java side must use `Long.parseLong(s, 16)`, not
  `parseUnsignedLong`. Moot if the shell becomes blocks.
