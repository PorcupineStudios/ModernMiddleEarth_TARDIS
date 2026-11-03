# LOTR Port (Minecraft 1.21.1 NeoForge)

Future-port of The Lord of the Rings Mod (1.7.10, Update 36.15) to Minecraft 1.21.1, part of the TARDIS: Ring Bearer
modpack project. Mod id `lotrport` (placeholder until Aaron picks one), main class `LotrPort`, package
`com.porcupinestudios.lotrport`.

**Keep this repo private.** LOTR's code belongs to Mevans and has no public source or license to redistribute. The
reference is the compiled jar `libs/LOTRMod v36.15.jar` in PorcupineStudios/RingRealms; its class map:
https://claude.ai/artifact/KqhSSTCuKXGuwKHBoYJdcu. Rebuild features in modern code; never paste decompiled code.

## Approach (agreed in the project thread)
- Split the old `LOTRMod` god class into one registry per area.
- Player/world state (old `LOTRPlayerData`, `LOTRLevelData`) as saved data and data attachments with Codecs.
- Factions and alignment first (most things depend on them, and RingRealms and the TARDIS mod read them).
- NPCs: one base entity + data files per race, not one class each.
- World gen as data: biomes, structures (`.nbt` templates), loot tables. A converter that runs the old mod and exports
  structures, recipes and models as data is the planned big time-saver.
- Middle-earth is the TARDIS start dimension: expose it to TARDIS: Ring Bearer through its API (`CrashSiteProvider`),
  and add `lotr:alignment`-style requirement types there.

## Build
JDK 21, `./gradlew build`, `runClient`, `runServer`, `runGameTestServer`, `runData`. ModDevGradle 2, NeoForge 21.1.x.
CI builds every branch.

## Rules
- Client classes never referenced from common code. Saved data via Codecs, readable formats.
- Every number and list a pack maker might tune goes in data files.
- Don't call something verified unless you ran it; say how.
