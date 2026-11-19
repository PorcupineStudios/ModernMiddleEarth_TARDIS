# Wreck, interior, hops, winder (phase 2)
Read when: touching `block/`, `interior/`, `flight/`, `net/ConsoleNet`, `client/`, `compat/create/`, `tools/rooms.py`.

- **Police box** `block/ExteriorBlock`: two blocks (HALF), lower holds the TARDIS id (`ExteriorBlockEntity`).
  Unbreakable, no item; placed/lifted only through `Exteriors`. Use: unclaimed → claimed if `mayOwnAnother`, crew →
  enter, others → locked. Placeholder textures from a PIL script (Aaron paints the real ones).
- **Interior**: one void dimension `tardis_rb:interior` (datapack `dimension/` + `dimension_type/`). Plot n at
  x = n x `plot_spacing`; the plot is handed out by `TardisManager.assignPlot` and the room template
  (since 2026-11-04 `structure/interior/console_room_capaldi.nbt` from `tools/capaldi.py`, corner at `room_offset`; see `docs/console.md`) placed once. Blocks find their
  TARDIS by plot (`Interiors.at`). The **game test server makes only the overworld**, so the interior is checked by
  the dedicated-server world check (`gametest/WorldCheck`, CI step "TARDIS world").
- **Crash site** `flight/CrashSite`: first `ServerStartedEvent`, an unclaimed record "The Wreck" placed `offset`
  blocks east of the start dimension's spawn (`Providers.startDimension`), flag saved in `TardisManager`.
- **Salvage wrecks**: block `salvage_wreck` with a loot table, scattered by worldgen data (`worldgen/` + NeoForge
  biome modifier on `#tardis_rb:has_salvage_wreck`). Never a second TARDIS.
- **Hops** `flight/Flight.hop`: feature `short_hop`, same dimension, distance ≤ `range` stat, cost
  max(`min_charge`, distance x `charge_per_block`), `Landing.find` (nearest column, then up/down; sturdy floor, two
  free dry blocks). Events `JumpEvent.Plan/Cost/Land/Arrived`; history type `tardis_rb:hop`. The box keeps its facing.
- **Console**: no screen (Aaron 2026-11-04). Only physical controls, `ConsoleNet.Press`; see `docs/console.md`.
- **Winder** (`compat/create/`): `RotatedPillarKineticBlock` + `KineticBlockEntity`; charge per second =
  |RPM| x `charge_per_rpm_second`, fed to the interior it stands in or a box within `link_range`. Stress impact is
  registered in `BlockStressValues.IMPACTS` with a supplier reading the tuning file. Client uses Create's
  `ShaftRenderer`. Create comes from Modrinth (`maven.modrinth:create`, see `tardis/build.gradle`); its jar-in-jar libraries are unpacked for compiling.
- **Artron Alternator** (2026-11-19, Aaron's drawing: a gear on top, arms down round the police box, lightning;
  `compat/create/AlternatorBlock` + `AlternatorBlockEntity`, `client/AlternatorRenderer`): set it over the box (at
  most `alternator.reach` blocks above its top half, only air between) and turn it with a Create shaft from above
  (vertical axis). Charge per second = |RPM| x `alternator.charge_per_rpm_second` (4x the winder's), into that TARDIS,
  up to its cap. Clients get `drop` (how far down the box is) and `charging`; the renderer draws Create's cogwheel
  (a Flywheel visual when it can), four iron arms out and down to the box, and flickering lightning onto it while it
  charges. Stress impact `alternator.stress`. Recipe: iron, a large cogwheel, lightning rods, brass casing. Test
  `AlternatorTests`. Not seen in a client yet.
- **GameTest coordinates**: a template's floor is at relative y 1 (the structure block sits below), so things stand at y 2.
- All numbers: `data/tardis_rb/tardis/tuning/main.json` (`progression/Tuning`).
