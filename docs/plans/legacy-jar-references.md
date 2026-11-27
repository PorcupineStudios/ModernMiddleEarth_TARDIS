# Read LOTR's files from the player's jar instead of shipping them: plan (2026-11-27)

Goal: the port and this public repo carry none of The Lord of the Rings Mod's own files. Everything that is LOTR's
work is read at run time from the 1.7.10 jar the player (or server) puts in `mods/`, as a CurseForge modpack lists
it from its own page. How it works today: `lotr/CLAUDE.md`, "LOTR's own files come from the player's LOTR jar".

## Where it stands (measured 2026-11-27)

| What | Count | Today | Can it be read from the jar? |
|---|---|---|---|
| Textures and sounds identical to LOTR's | 2,996 | Read from the jar (`client/LegacyAssets`), left out of our jar, **still in git** | Done |
| Textures built from several of LOTR's (drinks, connected sheets, gates, hats) | 609 | Rebuilt in game (`client/LegacyComposer`), **still in git** | Done |
| Speech banks | 1,514 JSON | Shipped (`data/lotr_port/speech`) | Yes: the jar's `assets/lotr/speech/*.txt` (1,842) |
| Lore books | 114 JSON, plus their text baked into structure chests | Shipped (`data/lotr_port/lore`; `structures.py` writes `written_book_content` into the `.nbt`) | Yes: `assets/lotr/lore/*.txt` |
| Name banks | 20 JSON | Shipped (`npc_names/`) | Yes: `assets/lotr/names/*.txt` (71) |
| LOTR's names and strings | inside our 22 lang files | Shipped, merged with ours | Yes: `assets/lotr/lang/*.lang` (22) |
| Biome map | `biomes.bin.gz`, `map.json` | Shipped (`lotr_port/map/`), read by the server | Yes: `assets/lotr/map/map.png`, with the colour → biome table from the export |
| Edited LOTR pictures | ~15 (sky 6, orc/Uruk player skins 2, One Ring, others to check) | Shipped | Yes, as new composite recipes |
| Wood sets (vanilla oak recoloured with LOTR planks' colours) | 429 | Shipped | Yes: built in game from vanilla's textures and LOTR's planks |
| Our own art (Kenney UI, RingRealms icons, Goblin Town, rigs, rabbit, panorama) | ~130 | Shipped | Not LOTR's: stays |
| Built from LOTR's code, not its files (880 structures, models, recipes, loot, worldgen, NPCs, trades, quests, roads) | thousands | Shipped (converter outputs, which Aaron owns) | No file to read; see step 8 |
| Converter export (raw dumps from running LOTR) | 18 files, 48 MB | Committed in `converter/export/` | Only the converter needs it; see step 8 |

## Decisions (Aaron, 2026-11-27)

1. **Servers need the jar too.** Dedicated servers put the LOTR jar in `mods/` like clients, so steps 3 to 5 go ahead.
2. **History is rewritten** on the GitHub repo. A full mirror clone was kept first as a backup:
   `Documents/Claude Minecraft Modding/ModernMiddleEarth_TARDIS-backup-2026-11-27.git` (every branch, tag and PR
   ref as of 2026-11-27).
3. **The export moved to a private repo**: `PorcupineStudios/LOTR_export` (secret `LOTR_EXPORT_TOKEN` in CI).
4. **No rights**: the creator's permission was revoked, with a grace period to adjust. `lotr/CLAUDE.md` says so.

## Done (2026-11-27)

- Step 1: commit 68237789 on `claude/legacy-jar-assets` (PR #4).
- The export move (step 8's first part): same commit, and LOTR_export's first commit.
- Step 2, first pass: history rewritten without the 3,605 listed files, every older LOTR texture and sound path
  under `lotr/src/generated/resources/assets` (3,682 paths in all) and `converter/export/`. LOTR's text and map
  (steps 4 and 5) still ship, so they are still in history. A second pass after those steps removes them.
  Done with `git filter-branch --index-filter` in a separate mirror clone (no Python for filter-repo), checked
  (no purged path left in any branch or tag; each branch's tip lost only purged files), then every branch and tag
  force-pushed.

Still to do after the rewrite:
- **`main` has no LOTR textures or sounds until PR #4 merges**: the rewrite took them out, and the code that reads
  them from the jar is on PR #4's branch. So are PRs #13 and #73, which are based on main.
- **GitHub keeps the old commits** in its read-only PR refs (`refs/pull/*/head`, including closed PRs) and caches.
  Only GitHub Support can drop those ("remove sensitive data": ask them to purge the PR refs and run a GC).
- **Release assets and workflow artifacts** are not in git: the `test-build` and `merged-2026-11-25` releases and
  the "jars" artifacts are built mods from before the slimming, with LOTR's textures and sounds inside.
- Local clones made before 2026-11-27 must not push: re-clone, or reset each branch onto the rewritten one.

## Steps

Each step is one branch with a GameTest or CI check for its done-when.

1. **Stop committing LOTR's files.**
   - The converter writes LOTR's textures and sounds into a gitignored folder (`converter/cache/legacy/`), not
     `lotr/src/generated`. `LegacyAssetMap` compares that folder against the jar to write the two lists.
   - `plant-textures.yml` stops copying plant textures and commits only models and list entries.
   - Remove the 3,605 listed files from `lotr/src/generated/resources`. Dev clients already read them from
     `lotr/run/mods/`.
   - Add a CI check that fails if a path in either list is tracked again.
   - Done when: `git ls-files` lists none of them, the dev client still draws everything with the jar, and the check
     fails on a re-added file.

2. **Rewrite history** (if decision 2 is yes). `git filter-repo` on the listed paths, the plant-texture history and
   any other LOTR files, force-push every branch, and tell anyone with a clone. Done when no commit contains them.

3. **Read server data from the jar** (foundation for steps 4 and 5).
   - Move jar finding (`findJar`, the LOTR class marker) out of `client/` into common code. `LegacyAssets` stays
     client-only.
   - Add `LegacyData`: a `PackType.SERVER_DATA` pack from the same jar, registered in `AddPackFindersEvent` on both
     sides. Like the composites, it serves our JSON paths built on the fly from LOTR's text files. Translators move
     from Python (`npcs.py`, `lore.py`) into plain Java with JUnit tests.
   - Without the jar on a server: a clear error in the log and in `/lotr` output. Decide whether to refuse to start.
   - CI: the GameTest job gets the jar through `RINGREALMS_TOKEN` (as `convert.yml` does), deleted after the run.
   - Done when: a GameTest loads speech from the jar on a server with no shipped speech.

4. **Text from the jar.**
   - Speech (1,514), quest speech, name banks (20) and lore (114) served by `LegacyData`. Delete the shipped JSON.
   - Lore in structure chests: `structures.py` stops baking book text into the `.nbt`. A loot function or chest
     processor picks the book when the chest generates (seeded by its position, as now).
   - Lang: split our lang files into our own strings plus a key table (our key → LOTR key). The client pack reads
     LOTR's `.lang` files and serves the matching translations for all 22 languages.
   - Done when: NPCs speak, chests hold lore books, and names show in every language, with none of that text in our
     jar.

5. **The map from the jar.** At server start (and on the client for the map screen), decode
   `assets/lotr/map/map.png` into the biome grid with the colour table from the export. Delete `biomes.bin.gz` and the
   map parts of `map.json`. Done when `MiddleEarthTests` (planet edges, biomes at known spots) pass reading from the
   jar.

6. **Edited pictures become composite recipes.** Run a script over the ~577 shipped images to find each one's LOTR
   source (near-matches in the jar). Add `LegacyComposer` operations (pad, crop, recolour, overlay) for the sky, orc
   skins, One Ring and any others found, then drop them from the jar. Done when only our own art and the wood sets ship.

7. **Wood sets built in game.** Port `woodsets.py`'s recolour (a template pixel's brightness placed in oak planks'
   spread, coloured from the LOTR planks' spread) into `LegacyComposer`, reading vanilla's oak textures from the
   client's own resources. Drop the 429 shipped files. Done when every wood set draws the same as today
   (a pixel-exact check against the current files).

8. **What's built from LOTR's code.** Structures, models, recipes, loot, worldgen, NPCs and roads are converter
   outputs and stay shipped, as agreed. Two choices remain:
   - The 48 MB `converter/export/` is raw LOTR data. Move it private (decision 3).
   - LOTR's 75 structure scans (`assets/lotr/strscan/`) are files in the jar. The structures made from them could
     load from the jar like step 4, leaving only the generator-built ones shipped.

9. **Docs and the modpack.**
   - Update `lotr/CLAUDE.md` (the legacy section and the permission line).
   - Write the modpack's install notes: the LOTR jar for clients (and servers, if decision 1), and
     `showLoadWarnings = false`.
   - Run the CurseForge cross-version test: install the pack from CurseForge with the LOTR jar listed from its own
     page, and play.

## Order

Do 1 first: it costs nothing at run time and removes most of what the public repo holds. Then 3, which 4 and 5 need.
Steps 6 and 7 are client-only and can run alongside 3 to 5. Step 2 comes after 1 (so the files don't return) and when
Aaron says yes.
