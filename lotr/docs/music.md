# Middle-earth music pack

Read when: you touch `client/LotrMusic`, `converter/music_pack.py`, `music-mod/` or the soundtrack.

The soundtrack (184 levelled tracks from the six films' scores) is not in this repo and must never be committed here:
the scores are not ours to publish. It lives in the private repo `PorcupineStudios/LOTR_music` (moved there
2026-11-11 with its history, when this repo went public). `.gitignore` keeps both old paths out.

Since 2026-11-15 the music is its **own mod**, `lotr_port_music` (resources only, `modLoader = "lowcodefml"`), and
`lotr_port` never contains it:

- `lotr_port` declares it as an optional client dependency (`[1.0,2.0)`, `lotr/src/main/templates/META-INF`) and has
  no code for it. The music mod's `assets/lotr_port/` (`sounds.json`, `music/playlists.json`, `music/tracks.json`)
  loads as mod resources, which `LotrMusic` reads like any pack's. Without it the mod runs with no soundtrack.
- The split: `lotr_port`'s own `assets/lotr_port/music/playlists.json` keeps the biome → land map and the timings, with
  empty land lists. The music mod fills each land's calm/suspense/fight lists with its own sound events. New biomes only
  need mapping to an existing land here, so the music jar keeps working across `lotr_port` updates. Rebuild it only
  when the music changes, or for a new land that needs its own tracks.
- Building the music mod, kept private: Actions > "Private music build" > Run workflow
  (`.github/workflows/music-build.yml`). It packs LOTR_music with `music-mod/META-INF/neoforge.mods.toml` (the only
  part in this repo) into `lotr_port_music-<version>.jar` and puts it in the `music-build` release of LOTR_music, never
  here. It needs the `MUSIC_TOKEN` secret (fine-grained token, LOTR_music only, Contents read/write).
- By hand: copy LOTR_music's `assets/` and `pack.mcmeta` plus `music-mod/META-INF/` (with `${version}` replaced) into one
  folder and zip it as a `.jar`. Never publish it.
- Or use it as a normal resource pack: zip the music repo's contents (`pack.mcmeta` at the zip's top) into
  `resourcepacks/`. It merges the same way.
- Rebuilding the pack from your own soundtrack files: `converter/music_pack.py --into-mod <tracks folder>` writes into
  the ignored `lotr/src/main/resources/resourcepacks/middle_earth_music`; copy the result into your LOTR_music clone and
  commit it there, not here. (`lotr_port` no longer loads that folder.)
