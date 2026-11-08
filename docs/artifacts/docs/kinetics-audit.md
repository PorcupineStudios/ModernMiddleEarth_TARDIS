# Kinetics audit (Create backport)

Oct 26, 2026 · @Aaron

Audited the `kinetics` package on the PR #10 branch (car driving, clutches, Furnace Engine), about 11k lines. Read-only; no code changed. Findings are ranked by impact within each section.

## Verdict

The core is sound. The rotation model (signed RPM, ratio BFS, stress = impact x RPM against summed capacity) is the same model Create uses, and most numbers match Create closely. The weak spots are client rendering cost, how speed changes are sent to clients, and moving structures (item loss, what they grab, no protection checks).

## Bugs and risks (fix first)

1. **Moving structures can delete items.** When a bearing, piston, gantry or vehicle puts blocks back and a spot is now occupied, `Contraption.drop` drops only the block itself. A chest's (or any tile entity's) contents are lost. Fix: rebuild the tile entity from its NBT and drop its inventory too.
2. **Bearings, pistons and gantries grab everything face-connected**, up to 512 blocks, including terrain (only vehicles leave ground blocks behind). Placed against the ground, a bearing tries to lift the hillside. Create only moves what is glued or chassis-attached. Suggestion: reuse `VehicleEntity.ground` for all hosts, or add a Super Glue item.
3. **No protection or blacklist when lifting blocks.** Blocks are cleared with `setBlock` and no `BreakEvent`, so claim/protection mods are bypassed on a server, and other mods' tile entities (AE2 cables and machines, TARDIS parts with normal hardness) are moved by NBT copy, which can break their grids or duplicate state. Fix: fire a `BreakEvent` per block with a fake player, and keep a config blacklist of blocks and tile entity classes (default: AE2 grid blocks).
4. **Networks over 4096 blocks**: the solver marks the first 4096 jammed, and the rest keep their old speed. Rare, but they should be stopped too.

## Performance

1. **Every shaft, cog and gear is redrawn from scratch every frame** (`KineticRenderer` builds the geometry with the Tessellator per block per frame, cog teeth included). This is the biggest FPS cost in a large build. Fix: compile each model (shaft, cog, large cog, gearbox end) once into a display list and only rotate and call it. Expect a several-times speed-up for big rigs. Going further, stopped parts could be drawn into the normal chunk mesh.
2. **Each speed change re-sends every block in the network.** `applyNetwork` calls `markBlockForUpdate`, which sends a block-change packet and makes the client rebuild that chunk section. A network starting or stopping rebuilds every chunk it touches, and 64+ changed blocks in one chunk makes vanilla resend the whole chunk. Fix: send speeds in one small custom packet per network (or `addBlockEvent`) and skip the chunk rebuild.
3. **Items on belts and in fans are real item entities.** Each belt segment runs an entity search every tick, and every item ticks, collides and syncs. Fine for a few lines; it will lag a big factory. Create keeps belt items inside the belt's tile entity. That is a large rewrite, so only worth it if belts become central.
4. **All kinetic blocks tick on both sides.** On the server, shafts and cogs do nothing after their first tick. The client spin could come from world time x speed (as Create does), which also keeps teeth lined up after reloads. Small win.
5. Fine as is: solving only on real changes (cranks, engines, motors and water wheels schedule only when their output changes), the water wheel checking every 20 ticks, cached mixer recipe scans, display lists for moving structures.

## Future-proofing

1. **No permanent tests.** Every feature was verified with a temporary harness that was then deleted, so nothing catches regressions in the solver, recipes or structures. Suggestion: keep a dev-only self-test command (the time travel branch already has self-tests) covering ratios, jams, stress, a press recipe and a structure lift/place.
2. **Moving structures save blocks by numeric id.** Fine within one world in 1.7.10, but it breaks if that NBT ever moves between worlds (schematics, time travel snapshots, a future port). Saving the registry name is cheap now and costly later.
3. **Old `mymodid*` NBT keys** (`mymodidStep`, `mymodidCrush`, `mymodidFanTime`...) are still in use after the rename. Harmless, but they must never be renamed without a migration. Worth a comment so nobody tidies them.
4. State packed into metadata (axis, powered, pulley, extract bits) is close to full for several blocks. Any new state needs tile entity fields.
5. Good: stress and speed numbers are config-driven, client code is kept in `SideOnly` and client-only classes, optional mod recipes stay in `compat/*`.

## How close to modern Create

**Matches Create:** RPM/SU model, gearbox and cog ratios (large to small 2x), clutch and gearshift, hand crank (32 RPM, 256 SU), machine stress costs (millstone 4, press 8, mixer 4, fan 2, crushing wheels 8, deployer/saw/drill/bearing 4 SU per RPM), belt speed (RPM / 480 blocks per tick, exact), fan range up to 20, 256 RPM cap, sequenced assembly, deploying, andesite alloy, rose quartz and the other ingredients. LOTR forges standing in for the Blaze Burner is a good fit.

**Differs (by choice or simplification):**

- Conflicts jam the network. Create breaks a block (opposing sources, or over max speed). Jamming is friendlier; keep it.
- Water wheel adds speed per pushed side (Create 0.3 style). Modern Create's wheel turns at a fixed slow speed and the Large Water Wheel trades speed for power.
- Furnace Engine is from Create 0.2/0.3. Modern Create replaced it with the Steam Engine (boiler + tanks + heat).
- Creative Motor capacity is 65536 SU (Create: 16384). Windmill is in the same ballpark as Create.
- Moving structures: no collision with their sides, and no glue or chassis (see risk 2).
- Belt items are entities rather than held on the belt.

**Missing, most valuable first:**

1. Goggles or a Stressometer/Speedometer (sneak-click works but is not discoverable).
2. Rotation Speed Controller, Sequenced Gearshift, Encased Chain Drive.
3. Mechanical Arm and Mechanical Crafter (the brass tier).
4. Fluids: pipes, pump, tanks, spout, item drain, then the Steam Engine. This is the biggest gap and the biggest job; 1.7.10 Forge fluids make it doable.
5. Super Glue / chassis, rope and elevator pulleys, harvester and plough on structures, weighted ejector, item vault.
6. Trains: out of scope for 1.7.10.

## Suggested order

1. Fix the item loss on blocked placement (small).
2. Ground filter for every host plus a block blacklist and `BreakEvent` (small to medium).
3. Cached models in `KineticRenderer` (medium, biggest FPS win).
4. One speed packet per network instead of `markBlockForUpdate` (medium).
5. A permanent kinetics self-test (medium).
6. Then new content: goggles/gauges, speed controller, fluids and steam.
