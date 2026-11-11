# Middle-earth music pack

The mod plays a shuffled playlist per biome (`client/LotrMusic`). The tracks (the six films' scores, 184 levelled
tracks, ~310 MB) are **not in this repo**: they live in the private repo `PorcupineStudios/LOTR_music` (moved
2026-11-11, when this repo went public; where it fits and how to build with it: `lotr/docs/music.md`). A private build
copies them into `lotr/src/main/resources/resourcepacks/middle_earth_music` (ignored by git), and `LotrMusic` then
registers that folder as an always-on built-in pack ("Middle-earth Music" in the pack list). Its `sounds.json` and
`music/playlists.json` merge over the mod's own. `tracklist.txt` there lists every land's calm, suspense and fight
tracks.

The recordings are copyrighted: never commit them here, and never upload or share a jar built with them (CurseForge,
Modrinth, a public modpack). Changes to the pack are committed to `LOTR_music`.

To rebuild the built-in copy (new tracks, changed rules), from the repo root (it writes the ignored folder; then copy
it into your `LOTR_music` clone and commit there):

```bash
python3 converter/music_pack.py "/path/to/your/soundtracks" --into-mod
```

A separate resource pack still works on top (it merges over the built-in one), e.g. a zip of the folder:
`python3 converter/music_pack.py --zip-pack lotr/src/main/resources/resourcepacks/middle_earth_music`.

## Quick way: build it from your files

On the computer with your tracks (Python 3, plus ffmpeg for anything that isn't .ogg):

```bash
python3 converter/music_pack.py "/path/to/your/soundtrack"
```

It converts every audio file in the folder (subfolders too, so one folder per film works) to small mono Ogg
(about 0.4 MB a minute; `--quality 3` or `--stereo` for more fidelity), sorts tracks onto playlists by words in
their file names (Shire, Moria, Rohan, Mordor...), and writes the pack straight into Minecraft's `resourcepacks`
folder, outside this repo. The built-in rules cover the scores of all six films: whole words only, specific names
before catch-alls like "hobbit" or "fellowship" (album names often sit in file names). A track can go on several
playlists, and then keeps playing as you cross between them. On top of the rules, an `extra` table lends
fitting tracks to lands no film scene is set in (Angmar, Rhûn, Harad, the sea...).

**Moods.** Every track is tagged `calm`, `suspense` or `fight` (`MOOD_RULES`, by the scene it scores; unknown
titles are guessed from title words and from loudness against the rest of their album). Each land's list is split
by mood, and any land with fewer than 3 tracks in a mood borrows that mood's tracks from the nearest lands
(`NEIGHBOURS`, breadth-first), so every land has at least 3 of each. The pack's `tracklist.txt` shows what went
where, borrowed tracks starred.

**Volume.** Every track is measured (EBU R128) and levelled to the same loudness, `--loudness` (−20 LUFS), with
its range held to `--range` (9 LU) so quiet openings and big climaxes sit closer together. Albums differ by up
to 10 dB as mastered, so without this the Music slider would need riding.

Measurements and converted tracks are cached next to the pack, so a re-run only converts new files. It lists the
tracks it couldn't place. To change the sorting:

```bash
python3 converter/music_pack.py --write-map my_map.json
```

Edit `my_map.json` (`rules`: file-name word → playlists, the first match wins; `extra`: playlist → more titles;
`moods`: title words → moods), then:

```bash
python3 converter/music_pack.py "/path/to/your/soundtrack" --map my_map.json
```

## Layout (what the script makes)

```
middle-earth-music/
  pack.mcmeta
  assets/lotr_port/
    sounds.json
    sounds/music/shire/track_one.ogg        (Ogg Vorbis, mono or stereo)
    music/playlists.json
```

`pack.mcmeta`: `{"pack": {"pack_format": 34, "description": "Middle-earth music"}}`

`sounds.json`: one event per track. `stream: true` keeps long tracks out of memory:

```json
{
  "music.shire.track_one": {"sounds": [{"name": "lotr_port:music/shire/track_one", "stream": true}]}
}
```

`music/playlists.json`: only what you change; it's merged over the mod's file:

```json
{
  "playlists": {
    "shire": {
      "calm": ["lotr_port:music.shire.track_one", "lotr_port:music.shire.track_two"],
      "suspense": ["lotr_port:music.shire.black_rider"],
      "fight": ["lotr_port:music.shire.bucklebury_ferry"]
    },
    "bree": ["lotr_port:music.bree.prancing_pony"]
  },
  "biomes": { "old_forest": "shire" },
  "transition": { "settle_ticks": 100 }
}
```

A plain list (like `bree` above) is used for every mood.

## Rules

- Each biome plays the playlist named in `biomes`. The mod's file maps every biome to its LOTR music region.
- **Mood** (the `mood` section): which of the land's three lists plays.
  - **fight** while any mob within `fight_range` (16) is aggressive, meaning it has a target. Vanilla melee mobs
    and every LOTR NPC set that flag, and it syncs to the client. Being hurt with a hostile near counts too
    (`hurt_starts_fight`).
  - **suspense** with a hostile within `suspense_range` (32), at night, in a thunderstorm, more than
    `underground_depth` (8) below sea level with no sky, or under `low_health_percent` (35) health. Each can be
    switched off (`night_is_suspense: 0`...).
  - **calm** otherwise.
  - Fight holds `fight_hold_ticks` (400, 20 s) after the last trigger, suspense `suspense_hold_ticks` (300), so a
    lull doesn't flip the music.
  - A fight cuts in fast: `fight_settle_ticks` (10), the old track fading over `fight_fade_out_ticks` (40), the
    fight track in over `fight_fade_in_ticks` (30), only `fight_gap_ticks` (40) between fight tracks. Other mood
    changes wait `mood_settle_ticks` (40).
  - A mood with no tracks falls back: fight → suspense → calm, suspense → calm → fight, calm → suspense → fight.
  - Everything below (shared tracks, crossfades, stepping back) works for mood changes too.
- **Shuffle:** every track on the list plays once, in random order, before any track repeats. Between tracks there
  is a silence of `gap_min_ticks` to `gap_max_ticks` (default 400–1800 ticks, 20 s–90 s).
- **Shared tracks:** one track can be on several playlists (the pack script puts it on every list its rule
  names). With `keep_shared_track` (on by default), walking into a biome whose list also has the playing track
  doesn't touch it: it plays to the end, then the new list's shuffle carries on (without that track coming
  straight back). This happens at once, with no settle wait.
- **Changing to a list without the track:** you have to stay in the new biome for `settle_ticks` (100, 5 s) so
  walking along a border doesn't flip the music. Then it **crossfades**: the old track fades out over
  `fade_out_ticks` (100, 5 s) while the new list's first track starts `crossfade_delay_ticks` (20, 1 s) later and
  fades in over `fade_in_ticks` (80, 4 s).
- **Stepping back:** going back to the old biome while its track is still fading out brings that track back up
  rather than cutting it.
- **Arriving in a quiet spell** (the gap between tracks): the new list starts within `crossfade_delay_ticks`.
- **Leaving Middle-earth:** the track fades out; coming straight back fades it back in.
- **Vanilla music:** it's held back in any biome whose playlist has tracks. A list left empty lets vanilla music
  play there.
- `volume`: 0–1, on top of the Music volume slider.
- Changes apply with F3+T.

## Playlists (LOTR's music regions)

angmar, barrow_downs, bree, brown_lands, dale, dead_marshes, dorwinion, dunland, dwarven, enedwaith, eregion, eriador, fangorn, far_harad, far_harad_jungle, forodwaith, gondor, grey_mountains, isengard, lindon, lothlorien, mirkwood, misty_mountains, mordor, near_harad, old_forest, perdorogwaith, pukel, rhovanion, rhun, rivendell, rohan, sea, shire, woodland_realm

## Places: roads and underground

On top of the land's list, the music follows where you are:

- **Road**: within `road_range` (16) blocks of the edge of one of the map's roads, at ground level (within
  `road_height`, 12). You leave it past `road_leave_range` (40), so a road's edge doesn't flip the music.
- **Underground**: no sky above, no daylight reaching you, and at least `underground_depth` (8) blocks under the
  surface of that spot. Caves under mountains count; a cellar doesn't. Underground wins over road.
- A new place has to hold for `place_settle_ticks` (100, 5 s).

Lists tried, first with tracks wins: the land's own list for the place (`shire_road`, `dwarven_underground`), then the
place's list for all of Middle-earth (`road`, `underground`), then the land's list. Each one uses the mood rules
(calm, suspense, fight) as usual. The built-in pack ships `road` (travelling themes) and `underground` (Moria and
Erebor themes); `music_pack.py` fills them from your files by title (its `EXTRA` lists).

## Now playing and the jukebox

- When a track starts, its title and album show above the hotbar, like a record in a jukebox. Titles and albums come
  from `music/tracks.json` (`{"lotr_port:music.track.x": {"title": ..., "album": ...}}`); `music_pack.py` writes it
  from each file's tags, else the title from the file name and the album from the film headings in its rules, else
  the folder. `"announce": 0` in `transition` turns the line off.
- **J** opens the jukebox: every list that can play where you stand (land, road or underground; calm, suspense,
  fight), in the order they're tried, with the list and track playing now marked. Click a track's box to take it out
  of every shuffle or put it back; ▶ plays it now; Skip moves on.
- What you take out is saved in `config/lotr_port_jukebox.json`, with each track's title, album and the lists it's
  on, so it reads as a list of tracks to change or drop from the pack.
