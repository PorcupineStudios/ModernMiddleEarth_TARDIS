# Forest floor, fog, colour grading and sky

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Forest floor, fog, colour grading and sky (World generation)
- Forest floor (`ForestFloorFeature`, LOTRBiomeDecorator): fallen leaves (trees / 2 a chunk), leaf bushes (trees / 3),
  rotten logs (`logsPerChunk`); ferns and tall grass where LOTR had them. Foggy biomes (tag `lotr_port:foggy`, LOTR's
  biomeColors foggy) get a client fog (entry `atmosphere/foggy.json`). Region looks (2026-11-16, `client/Atmosphere`): fog distance, fog colour and a screen filter per biome from `assets/lotr_port/atmosphere/*.json` (biome ids, `#tags`, `prefix*`; highest `priority` wins; F3+T reloads); client config `atmosphere.*` toggles and scales them. Screenshot scripts: `lotr/run/mt.txt`,
  `converter/run/client/shots3.txt` (the shot runner gives night vision; `goto ... floor` = ground under trees).
- Colour grading (2026-11-16, `client/ColorGrade`, shader `shaders/core/grade.*`): each atmosphere file's `grade`
  (offsets: saturation, contrast, warmth, brightness, vignette, bloom) runs as a post pass after the world, eased with
  the region look; `atmosphere/middle_earth.json` (priority -100) is the base look for every lotr_port biome. Config
  `atmosphere.grading`. Dark-land effects (grain, colour fringes, heat shimmer, edge blur), the One Ring's
  wraith-world and drawing over Iris shader packs (2026-11-25): **read `lotr/docs/ring-and-looks.md`**.
- Sky (2026-11-16, `client/MiddleEarthSky`, dimension type effects `lotr_port:middle_earth`): LOTR's sun (half-size 10),
  8-phase moon, painted night sky (3x2 cube maps `textures/environment/middle_earth/night_<n>`, one per night) and
  Earendil near the sun at dusk and dawn; vanilla sky colour, sunrise glow and clouds. Config `sky.enabled`.
  Night-box face orientation is a guess from LOTR's renderer, not checked in game.
