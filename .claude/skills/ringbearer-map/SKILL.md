---
name: ringbearer-map
description: Find the code and data an issue or change in this repo touches (api, tardis, lotr, harness, converter) without reading around. Use before opening files for a bug, feature or question about the Ring Bearer mods, and when a brief from the organizer lists files.
---

# Finding your way in Ring Bearer

~1,000 Java files and thousands of data files. Never browse folders or read whole packages: look things up.

1. **A brief exists?** (`.claude-brief.md` at the repo root, written by the organizer in
   `.github/workflows/claude-auto.yml`.) Start from its files and line numbers. Go beyond it only when what it
   lists doesn't explain the problem, and say so in the PR.
2. **Which notes?** For lotr, the table in `lotr/CLAUDE.md` maps each topic to one file in `lotr/docs/` (the past
   decisions and pitfalls for that area). Read that one file, never the whole folder.
3. **Which package?** `node tools/codemap/codemap.mjs` prints every package (class names only) and every data
   folder, about 4k tokens. Run it once per task, not again.
4. **Which class?** `node tools/codemap/codemap.mjs <term> [<term>...]` prints each matching class as
   `path:line kind Name — first javadoc sentence`, plus matching data files. A term matches part of a path, so a
   package (`npc/behaviour`), a class name (`RedBook`), or a data folder (`colony_job`) works.
5. **Read only those**, from the line given, with `offset`/`limit` for big files. Use Grep for a symbol
   (`Grep "class Foo|Foo::"`) instead of opening files to look for it.

## Where things are
- Mods: `api/` (shared: history, conditions, providers, bug reporter), `tardis/`, `lotr/`, `harness/`. The mod's own
  `CLAUDE.md` (`lotr/CLAUDE.md`, `tardis/CLAUDE.md`) holds its rules: read it before changing that mod.
- Java: `<mod>/src/main/java/com/porcupinestudios/...`; unit tests `<mod>/src/test`; GameTests in each mod's
  `gametest` package, run with `./gradlew :<mod>:runGameTestServer`.
- Numbers and lists are data, not Java (root `CLAUDE.md`): `<mod>/src/main/resources/data/<ns>/<type>/` and, for lotr,
  `lotr/src/generated/resources/data/` (written by a script: grep `converter/` and `runData` for the file's folder
  before editing a generated file, and change the script when one writes it).
- Player-reported symptoms ("coal at high Y", "floating bushes") are usually world generation: lotr `world/feature`,
  `world/delve`, `worldgen` data, and `converter/` for anything copied from LOTR 1.7.10.
