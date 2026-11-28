# LOTR's own files from the player's LOTR jar

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## LOTR's own files come from the player's LOTR jar (2026-11-26, prototype, branch claude/legacy-jar-assets)
- Textures and sounds that are unchanged copies of LOTR's (pixels equal; 2994 of 4075) are not shipped: the build
  leaves them (and their .mcmeta) out of the mod jar, and `client/LegacyAssets` serves them from The Lord of the Rings
  Mod: Legacy jar in `mods/` (a modpack lists it from its own CurseForge page; NeoForge skips it with one warning,
  "for an old version of Minecraft Forge") as a required built-in resource pack, loaded at resource reload like any
  other. Without the jar: an error in the log and a toast on the title screen; LOTR's blocks draw as missing.
- The list is `src/main/resources/lotr_port_legacy_assets.tsv` (our path, jar path), written by
  `./gradlew :lotr:compileJava` then `java -cp lotr/build/classes/java/main converter/LegacyAssetMap.java "<LOTR jar>"`
  from the repo root after a converter run. It also writes `lotr_port_legacy_composites.tsv` (609 textures the
  converter built from several of LOTR's: drinks = vessel + liquid where the vessel is FF00FF, connected and gate
  sheets, the two dyed hats), each recipe checked pixel-exact; `client/LegacyComposer` (plain Java, Pillow's
  alpha_composite maths) rebuilds them in game at resource reload. The rest (~470: wood sets, raw ores, wild-land
  blocks, 1.7.10 horses, our own mount armour) are not LOTR's pixels and ship. The listed files are never in git:
  LegacyAssetMap also writes `src/generated/resources/.gitignore` from the two lists, so a converter run leaves them on
  disk (for the next comparison and dev runs) but untracked, and CI's "No LOTR files in git" step fails if one is.
- The 1.7.10 jar in mods/ makes NeoForge's load-warning screen show on every launch (and it blocks `-PquickPlay`).
  A modpack turns it off with `showLoadWarnings = false` in `config/neoforge-client.toml` (hides all load warnings).
- Checked 2026-11-26 (dev client to the title screen): with the jar, no lotr_port texture or sound missing; without
  it, all 190 sounds and the model textures missing.
