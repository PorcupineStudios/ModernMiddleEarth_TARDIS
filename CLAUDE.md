# Ring Bearer (Minecraft 1.21.1 NeoForge)

One public repo for the TARDIS: Ring Bearer modpack's own mods (public since 2026-11-11; what must never be committed:
`lotr/CLAUDE.md`, top). Three Gradle subprojects, each its own NeoForge mod:

| Folder | Mod id | Package | Read first |
|---|---|---|---|
| `api/` | `ringbearer_api` | `com.porcupinestudios.ringbearer.api` | this file |
| `tardis/` | `tardis_rb` | `com.porcupinestudios.tardisrb` | `tardis/CLAUDE.md` |
| `lotr/` | `lotr_port` | `com.porcupinestudios.lotrport` | `lotr/CLAUDE.md` |

Plans (read with the Claude Docs tools, not a web fetch):
- Shared roadmap, both mods in one order: https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ
- Plan review that shaped it: https://claude.ai/artifact/1BciPgPdz8FbnnamU9E2Rs

## Dependency rule
`tardis` and `lotr` both depend on `api`, and never on each other. Anything they share goes in `api`:
- **history**: the world history log, recording from day one; the TARDIS recorder, colonies, AI rulers and time travel
  all use it.
- **condition**: one registry of condition types used by TARDIS unlocks, quests, the story engine and colony research.
- **provider**: crash sites and start dimension, researchers, destinations, power sources; registered on the mod bus.
`api` depends on nothing in either mod.

## Build
JDK 21. `./gradlew build` builds all three (jars in `*/build/libs/`); `./gradlew :tardis:runClient` etc. for one mod.
ModDevGradle 2, NeoForge 21.1.x, Parchment. Shared Gradle setup: `gradle/mod.gradle`; versions in `gradle.properties`,
per-mod values in each folder's `gradle.properties`. CI builds every branch. The cloud sandbox can't reach
maven.neoforged.net, so builds there are verified only by CI.

## Rules for every task
- Tuning data (repairs, costs, research, quests, trades) loads through reload listeners so `/reload` and KubeJS apply
  it live; datapack registries only for what world generation needs (dimensions, biomes, planets).
- Every number and list a pack maker might tune goes in data files, never Java constants.
- Saved data uses Codecs with a `dataVersion` and migrations; formats stay readable. New worlds only: 1.7.10 saves are
  not converted.
- Network payloads: `CustomPacketPayload` + `StreamCodec`. Client classes are never referenced from common code.
- Each phase's done-when gets a GameTest (`runGameTestServer`).
- Prefer scripts that do repetitive work (converter, ID tables, validators) over porting by hand.
- Don't call something verified unless you ran it; say how.
