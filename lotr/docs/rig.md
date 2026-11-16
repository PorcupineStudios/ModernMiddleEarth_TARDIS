# Rigs: procedural animation for big creatures

Read when touching `rig/`, `npc/RiggedNpc`, `client/RigRenderer` or a `data/<ns>/rig/*.json` file.
Proposal agreed with Aaron 2026-11-16: https://claude.ai/artifact/SAvtwnRnAbBWj2ZwUMXNbX

## How it fits
- A rig file `data/<ns>/rig/<name>.json` (`RigDef`, reloads with /reload, synced to clients by `Rigs.Sync` on join and
  reload) names the NPC types that use it (`npc_types`). `LotrNpc.spawn` and `bodyFor` make those types a
  `lotr_port:rigged_npc` (`RiggedNpc`, a `LotrNpc` with 12 reserved hitbox part ids); every other type stays
  `lotr_port:npc`. Structure templates still place `lotr_port:npc`, so their spiders are the old kind.
- Both sides tick `RigState` from the same inputs (position, body yaw, movement, walk speed): foot planner, body lift and
  tilt, chain lag. The server poses the skeleton (`RigPose`) only for hitboxes; the client poses it per frame with the
  partial tick and draws (`RigRenderer`), with IK only within 64 blocks. No feet are synced; the sides agree closely
  because they get the same inputs.
- Rig space: blocks, origin on the ground under the entity, y up, +z forward. Model pixel point (x, y, z) =
  (x/16, (24-y)/16, -z/16), which is exactly where Minecraft's living renderer draws a model point; rotations
  (deg, z-y-x) become `rotationZYX(-z, -y, x)`. Bone pivots are absolute (Blockbench), cube origins relative to their
  bone pivot (LOTR/Blockbench Java export), so LOTR's model JSON pieces paste in.
- Hitboxes: NeoForge multipart (`PartEntity`, like the Ender Dragon). `RigPart` forwards hits to `RiggedNpc.hurtPart`
  times the part's `damage`; `solid` parts can be stood on (the NPC ignores its own parts when moving).
- Footfalls: client `RigRenderer.footfall` (step sound of the ground block, dust and camera shake by `gait.heavy`);
  server stomp damage (`gait.stomp`) to smaller enemies under the foot.

## Rig file fields
`bones` (parent, pivot, rotation, cubes), `body` (root bone that tilts), `look` (bone that follows head yaw/pitch),
`legs` (upper, lower, `foot` tip at rest, `group` that steps together, `bend` for straight legs, `spread` = how far out
feet plant vs. the rest pose), `gait` (step distance/ticks/height, lead, reach up/down, max tilt, body spring, heavy,
stomp, `overlap`: how far through its step a group must be before the next starts; steps also get shorter at speed so
feet keep up), `sways`, `chains` (bones that lag behind turns), `parts`, `scale`, `scale_state` (NPC state multiplying
the size, e.g. `getSpiderScaleAmount`), `hide` (below).

## Hiding as blocks (stone giants)
`hide`: `block`, `blocks` (whole-block offsets from the feet, rig axes), `wake` (6), `settle` (100 ticks). Server
(`RiggedNpc.hideTick`): no target within `wake` and not hurt for `settle` ticks -> snaps to the block centre and a quarter
turn, stops, state `rig_hidden` 1 (synced, saved). A near target or a hit wakes it. Hidden: part hitboxes off (only the
entity box), not pushable; the client draws the blocks with `renderSingleBlock` (`RigRenderer.drawHidden`) and throws
block dust when it changes. Like huorns: the AI may target from further away, it just stays hidden until `wake`.

## Models
Spider is LOTR's model. Mûmak, stone giant and dragon are our own (Aaron 2026-11-16, references only):
`converter/rig_models.py` writes their rig JSON and painted textures (`textures/entity/rig/<name>.png`). NPC types
`data/lotr_port/npc/{mumak,stone_giant,dragon}.json` are hand-written (not in `src/generated`); their `model` is only
a fallback when the rig is missing. Offline viewer (not in the repo): dump posed boxes from RigState/RigPose and draw
side/iso/top views, rest/walking/slope.

## Tests
`RigTest` (JUnit, no game): IK lengths/reach, rest pose = model, walking steps in turns and keeps up, slope tilt, chain
lag, and every shipped rig file parses and walks without NaNs. They also run outside Gradle with JOML + DFU jars.

## Status
- 2026-11-16: core, spider rig (LOTR's spider model, legs split at the knee); mûmak, stone giant (hides as stone),
  dragon (walks only). Compiled on the PC, RigTest green; not yet seen in game. No natural spawns yet (`/lotr npc spawn`).
- Not yet: wall climbing for spiders (legs keep looking for ground below), FABRIK for 3+ bone legs, riding seats,
  players carried on solid parts, dragon flight, spawn lists for the new creatures.
