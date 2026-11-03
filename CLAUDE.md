# TARDIS: Ring Bearer (Minecraft 1.21.1 NeoForge)

Mod id `tardis_rb` (Aaron's name TARDIS_RB; NeoForge needs lowercase), main class `TardisRB`, package
`com.porcupinestudios.tardisrb`. A clean rewrite of the TARDIS Mod + TardisTimeTravelAddon for a progression modpack:
a wrecked TARDIS crash-landed in Middle-earth, repaired stage by stage. DarkholmeTenk/TardisMod is reference only;
copy no code or textures (Aaron makes the art).

## Read first
- Port plan (decisions, progression, phases): https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ
- Code architecture and hooks: https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE
Read both with the Claude Docs tools, not a web fetch. Add notes in `docs/<area>.md` as systems get built, and a row here.

## Build
JDK 21, `./gradlew build` (jar in `build/libs/`), `runClient`, `runServer`, `runGameTestServer`, `runData`.
ModDevGradle 2, NeoForge 21.1.x, Parchment mappings. CI builds every branch (`.github/workflows/build.yml`).

## Rules for every task
- No feature is on by default in code: it exists only if a data file defines it and `Unlocks` allows it.
- Numbers (ranges, costs, caps, stage repair lists) live in datapack JSON, never Java constants.
- State changes go only through `TardisRecord` methods, which fire events and sync.
- Other mods only inside `compat/<modid>`, loaded when `ModList.isLoaded`. Create is the only hard dependency.
- `api` package: interfaces, events, read-only views; depends on nothing else in the mod (moves to its own jar).
- Client classes (renderers, screens) are never referenced from common code.
- Saved data uses Codecs with a `dataVersion` and migrations; formats stay readable.
- Network payloads: `CustomPacketPayload` + `StreamCodec`.
- Don't call something verified unless you ran it; say how.
