# RingRealms project memory

## Reference sources
- Original Tardis mod source (reference, NOT law): https://github.com/DarkholmeTenk/TardisMod (main class: src/main/java/tardis/TardisMod.java). Aaron wants to change a LOT during the 1.26.3 future-port; use it as an example of similar structure only.
- Graph of original TardisMod (255 classes): hubs are TardisMod, TMRegistry, Helper (global singletons); logic concentrated in CoreTileEntity (~2k lines), ConsoleTileEntity, EngineTileEntity, TardisDataStore; depends on darkcore library in 149 places, which the port must replace.

## Tardis design decisions (Aaron)
- Clean rewrite using old code as reference. Kinetics backport is scrapped; replace with Create integration.
- Target: custom progression modpack. Player starts with (or quickly gets) a heavily damaged TARDIS crash-landed in Middle-earth. At first it can barely teleport short XYZ distances, no dimensions; must be wound up with Create rotation for power.
- Repair stages unlock tech trees and features (e.g. teleporter room). Everything must be easy to enable/disable/change over time; exterior and interior visuals/textures change as it is upgraded.
- 2026-11-03 additions: seamless Outer Wilds-style space (rockets too); TARDIS can fly in space without dematerializing, 3rd-person view of it spinning/flying; seamless door with see-through in/out; console is a custom model with non-block-shaped buttons like the original mod; repairs are NOT physical blocks but a repair/upgrade skill-tree GUI in the TARDIS basement.
- Immersive Portals (NeoForge 6.0.7, Apache-2.0) is a required dependency; used for the see-through door (done) and space.
- Planets (Aaron approved): in space, a globe rendered from the planet's terrain map; entering atmosphere drops you onto a flat world that wraps at edges via Immersive Portals world-wrapping. Space travel must be compatible across other dimensions. LOTR celestial bodies (Sun/Moon/Eärendil) as destinations = backburner.
- Full details: [[future-port-tardis]] (design: crash sites, repair stages/milestones, costs, research, look) and [[future-port-scope]] (modules: Souls, half-life, bank, RFTools kept; Create: Aeronautics for airships; Refined Storage replaces AE2).

## RingRealms design decisions (Aaron)
- NPC/AI colonies only start generating after the player founds their colony (no head start).
- Migration is selective: migrants appear at the city hall offering to settle; player accepts/denies.

## Aaron's PC (AARONPC)
- Folder: C:\Users\aadag\Documents\Claude Minecraft Modding (Remote Control used)
- Installed: ponytail plugin 5.3.3, graphify 0.9.101 (via uv, /graphify skill), Node.js LTS. No system Python.

## Build progress
- [[ringbearer-progress]]: phases done in TARDIS-RingBearer (LOTR phases 1-10, TARDIS phases 1-5, Stage 4, vision rework, see-through door, as of 2026-11-03). Space is next.

## Asset sources
- [[tardis-model-sources]]: TARDIS console model sources + licenses (AIT LGPL has Coral and Toyota/Capaldi consoles; TARDIS Refined is noncompete, unusable).
