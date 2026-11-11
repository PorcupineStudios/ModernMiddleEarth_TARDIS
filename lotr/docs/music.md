# Middle-earth music pack

Read when: you touch `client/LotrMusic`, `converter/music_pack.py` or the soundtrack.

The soundtrack (184 levelled tracks from the six films' scores) is not in this repo and must never be committed here:
the scores are not ours to publish. It lives in the private repo `PorcupineStudios/LOTR_music` (moved there
2026-11-11 with its history, when this repo went public). `.gitignore` keeps both old paths out.

- The mod works without it: `LotrMusic` registers the built-in pack only when
  `resourcepacks/middle_earth_music/pack.mcmeta` is in the jar. The mod's own `assets/lotr_port/music/` (playlists,
  moods, regions) stays here; it names tracks the pack supplies.
- A private build with the music: clone the music repo into the ignored folder, then build:
  `git clone https://github.com/PorcupineStudios/LOTR_music lotr/src/main/resources/resourcepacks/middle_earth_music`
  Never publish a jar built that way.
- Or use it as a normal resource pack: zip the music repo's contents (`pack.mcmeta` at the zip's top) into
  `resourcepacks/`.
- Rebuilding the pack from your own soundtrack files: `converter/music_pack.py --into-mod <tracks folder>` writes into
  the same ignored folder; commit the result to the music repo, not here.
