---
name: tardis-model-sources
description: Researched 3D model sources for TARDIS consoles/interiors (10th coral, 12th Capaldi) and their licenses, 2026-11-04
metadata:
  type: reference
  modified: 2026-11-04T01:19:45.387Z
---

Researched 2026-11-04 for the 1.21.1 tardis-ringbearer console ([[future-port-tardis]]).

- **AIT "Adventures in Time"** (github.com/pavatus/ait, Fabric/Forge 1.20.1): **LGPL-3.0** repo-wide incl. assets. Has
  `CoralConsoleModel` (9th/10th) and `ToyotaConsoleModel` (11th/12th Capaldi era), plus Copper, Hartnell, Steam etc.
  Models are Blockbench-exported Java ModelPart code (same LayerDefinition API as 1.21.1). Best legal option; LGPL
  obligations (credit, keep license, share changes to those files).
- **TARDIS Refined** (WhoCraft): **PolyForm Shield** = noncompete clause; our TARDIS mod competes, so NOT usable.
  Audrey's Additions: All Rights Reserved.
- Sketchfab "10th Doctor's TARDIS" by WizeyDizey: CC BY 4.0, 2.8k tris, but appears exterior-only.
- liamjmwilson Smith/Capaldi interior (Sketchfab preview, sold on CGTrader $15, 700k tris): photoreal, royalty-free
  but too heavy and paid; useful only as reference.
- 2026-11-04: Aaron BOUGHT the liamjmwilson Smith/Capaldi model (files on PC: Downloads\TardisOBJ). Capaldi room reduced
  1.04M -> 200k tris, grid-aligned, exported to /mnt/project-files/tardis-interior/capaldi/ (README, parts.json);
  light 75k version (Aaron: "we can afford to lose a lot of detail") in capaldi_light/, same layout.
  Aaron is OK with it not matching Minecraft style; exits must face cardinal directions on the block grid.
- BBC owns the designs; every fan model is unlicensed fan art of BBC IP. Fine for a private modpack, risky to publish.
- 2026-11-04: Aaron chose the CUBE (voxel) style. Output /mnt/project-files/tardis-interior/capaldi_blocky/ (PC:
  TardisOBJ\minecraft_ready_blocky): 56k tris, 125 clickable controls with hitboxes in parts.json, quarter-block
  collision.json, basement floor y=-3 (~2.7 blocks headroom), palette textures.
- 2026-11-04 later: Aaron's review (/mnt/project-files/tardis-interior/review/minecraft-style-guide.md) replaced voxelising
  with HAND-BUILT style. Current deliverable: /mnt/project-files/tardis-interior/capaldi_minecraft/ (PC:
  TardisOBJ\minecraft_style): room shell = blocks in structure .nbt (placeholder ns `tardis:`), console/rotor/arches =
  cuboid .bbmodel + OBJ (~4.3k tris), 28 controls. Generator scripts in its tools/ folder. capaldi_blocky is obsolete.
- 2026-11-04 05:30 UTC: capaldi_minecraft integrated in tardis_rb (commit 81a21b08, CI green, jar installed): real blocks +
  structure `interior/console_room_capaldi`, models via `tools/capaldi.py`, rooms grow from 3 hallway ends. Old
  worlds need `/tardis rebuild_interior`. Notes: tardis/docs/console.md, rooms.md.
