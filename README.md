# Ring Bearer

Private repo for the TARDIS: Ring Bearer modpack's mods on Minecraft 1.21.1 (NeoForge):

- `api/`: the shared Ring Bearer API (history log, conditions, providers)
- `tardis/`: TARDIS: Ring Bearer
- `lotr/`: the LOTR port, with RingRealms colonies built in

Build everything with `./gradlew build` (JDK 21).

## Other mods the pack needs
| Mod | Needed by | Why |
|---|---|---|
| Create | `tardis` | the winder, Create-made repair parts |
| Immersive Portals (+ Cloth Config) | `tardis` | the see-through door, seamless space |
| Curios API (9.5+) | `lotr` | worn-item slots: the ring slot (the One Ring), later cloak and backpack |

Optional: JEI, Jade, Iris (the LOTR port's region looks and the Ring's look draw over any shader pack).
