# Middle-earth music pack

The mod plays a shuffled playlist per biome (`client/LotrMusic`) but ships no tracks. Put your own audio in a
**resource pack** kept on your machine. Don't commit audio to this repo: the files are someone else's recordings,
and pushing them to GitHub distributes them, even from a private repo.

## Layout

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
    "shire": ["lotr_port:music.shire.track_one", "lotr_port:music.shire.track_two"]
  },
  "biomes": { "old_forest": "shire" },
  "transition": { "settle_ticks": 100 }
}
```

## Rules

- Each biome plays the playlist named in `biomes`. The mod's file maps every biome to its LOTR music region.
- **Shuffle:** every track on the list plays once, in random order, before any track repeats. Between tracks there
  is a silence of `gap_min_ticks` to `gap_max_ticks` (default 400–1800 ticks, 20 s–90 s).
- **Changing biome:** if the new biome uses another playlist, you have to stay there for `settle_ticks` (100,
  5 s) so walking along a border doesn't flip the music. Then:
  - if `keep_shared_track` is true and the current track is also on the new list, it keeps playing;
  - otherwise it fades out over `fade_out_ticks` (80), and the new list starts `change_gap_ticks` (60) later,
    fading in over `fade_in_ticks` (40).
- **Leaving Middle-earth:** the track fades out.
- **Vanilla music:** it's held back in any biome whose playlist has tracks. A list left empty lets vanilla music
  play there.
- `volume`: 0–1, on top of the Music volume slider.
- Changes apply with F3+T.

## Playlists (LOTR's music regions)

angmar, barrow_downs, bree, brown_lands, dale, dead_marshes, dorwinion, dunland, dwarven, enedwaith, eregion, eriador, fangorn, far_harad, far_harad_jungle, forodwaith, gondor, grey_mountains, isengard, lindon, lothlorien, mirkwood, misty_mountains, mordor, near_harad, old_forest, perdorogwaith, pukel, rhovanion, rhun, rivendell, rohan, sea, shire, woodland_realm
