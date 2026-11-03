# TARDIS: Ring Bearer (Minecraft 1.21.1 NeoForge)

Mod id `tardis_rb` (Aaron's name TARDIS_RB; NeoForge needs lowercase), main class `TardisRB`, package
`com.porcupinestudios.tardisrb`. A clean rewrite of the TARDIS Mod + TardisTimeTravelAddon for a progression modpack:
a wrecked TARDIS crash-landed in Middle-earth, repaired stage by stage. DarkholmeTenk/TardisMod is reference only;
copy no code or textures (Aaron makes the art).

## Read first
- Port plan (decisions, progression, phases): https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ
- Code architecture and hooks: https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE
Read both with the Claude Docs tools, not a web fetch. Add notes in `docs/<area>.md` as systems get built, and a row here.

| Read when the task touches | File |
|---|---|
| TARDIS record, data folders + validator, unlocks, conditions, repairs, damage, stages, `/tardis` | `docs/progression.md` |

## Build
From the repo root: `./gradlew :tardis:build` (jar in `tardis/build/libs/`), `:tardis:runClient`, `:tardis:runServer`,
`:tardis:runGameTestServer`, `:tardis:runData`. Dev runs load the shared API too. Shared rules are in the root `CLAUDE.md`.

## Rules for every task
- No feature is on by default in code: it exists only if a data file defines it and `Unlocks` allows it.
- Numbers (ranges, costs, caps, stage repair lists) live in datapack JSON, never Java constants.
- State changes go only through `TardisRecord` methods, which fire events and sync.
- Other mods only inside `compat/<modid>`, loaded when `ModList.isLoaded`. Create is the only hard dependency.
- Never depend on the LOTR port (`lotr/`). Anything the two mods share goes in the shared API (`api/`).
