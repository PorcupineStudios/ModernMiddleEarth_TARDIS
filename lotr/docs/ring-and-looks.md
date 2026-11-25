# Rings of power, Curios slots and the screen looks

Read when: you touch `ring/`, `client/RingSight`, `client/ColorGrade`, `compat/iris`, the `grade.*` shader, or
`assets/<ns>/atmosphere` / `ring_look` / `data/<ns>/ring_power` files.

## Curios (required, 2026-11-25)
- Curios is the slot mod (asked for 2026-11-25: a ring slot now, a cloak slot and a backpack slot later). Dev and compile use
  9.5.1+1.21.1 (`lotr/gradle.properties curios_version`): 9.6 needs NeoForge 21.1.265, newer than the repo's
  `neo_version`. mods.toml accepts `[9.5,)`.
- Slots are data. `data/lotr_port/curios/entities/players.json` gives players the `ring` slot,
  `data/lotr_port/curios/slots/ring.json` makes it 2 wide, and item tag `curios:ring` says what fits (the One Ring
  plus LOTR's gold, silver, mithril and marriage rings). To add the cloak slot later: `curios/slots/cloak.json`
  (order, icon), add `"cloak"` to `players.json`, and a `curios:cloak` item tag. The backpack works the same way, or
  uses Curios' preset `back` slot.
- GameTest mock players: Curios syncs its slots to every player without checking the channel, and NeoForge throws
  for an in-memory test connection. That failed every test that made a mock player (47). `gametest/MockPlayerChannels`
  opens Curios' channels on such connections (only an `EmbeddedChannel`) before Curios' datapack-sync and login
  listeners run. If Curios adds a payload, add its id there.

## Rings of power (`ring/`)
- `RingPowers`: `data/<ns>/ring_power/<id>.json`. `items` (which ring items have the power), `invisible`,
  `mob_visibility` (0..1, a multiplier on NeoForge's visibility event), `sees_through` (entity type ids, `#entity`
  tags, or LOTR NPC type ids; the One Ring: barrow wights, marsh wraiths, `#lotr_port:sees_ring_bearers`),
  `lose_targets` / `lose_targets_range` (mobs targeting the wearer drop them every 10 ticks). Reloads with `/reload`.
- Invisibility without the potion's swirl. No effect is applied: each server tick sets the entity's invisible flag
  while the ring is worn, and a transient attachment `hidden_by_ring` restores it (to "has the invisibility
  potion") when the ring comes off.
- `RingItem` (`one_ring`, epic, fire resistant, creative tab "misc"; no recipe or loot yet) is an `ICurioItem`, so a
  right-click puts it on. Its texture is a placeholder recoloured from LOTR's gold ring.
- GameTests `RingTests`: the data loads, Curios has the slot, and the ring hides the wearer with no effect. A
  zombie's visibility of the wearer is 0 and it drops them as a target; taking the ring off shows the wearer again.
  The test ticks the ring itself (`RingPowers.update`): mock players aren't ticked by a connection.

## Ring looks (`client/RingSight`)
- `assets/<ns>/ring_look/<id>.json`: what wearing a ring looks like from inside. `items`, `grade` (the same keys as an
  atmosphere file, below), `tint` + `tint_strength`, `fade_in_seconds` / `fade_out_seconds`, `start` + `grow_minutes`
  (it deepens the longer you wear it), and `eye` (`x`, `z`, `strength`, `glow`): in Middle-earth the dark side of the
  screen leans toward Barad-dur, and facing it a slow fire-coloured pulse shows. Also `hide_wearer` and
  `wearers_see_wearers`.
- Other players: a ring-hidden player is not drawn at all (`RenderLivingEvent.Pre` cancelled), because vanilla still
  draws an invisible player's armour, held items and name. The exception is when you wear a ring yourself. This reads
  the other player's Curios slots on the client (Curios syncs them to players tracking the wearer). Not checked with
  two clients yet.
- Config (CLIENT, Mods > Config): `ring.look`, `ring.strength`.

## Screen looks (`client/ColorGrade`, shader `shaders/core/grade.*`)
- One full-screen pass. Region grade (atmosphere files, eased by `Atmosphere`) + the ring's grade, then tint and Eye.
  `grade` keys: `saturation`, `contrast`, `warmth`, `brightness`, `vignette`, `bloom`, and the dark-land effects
  `grain` (film noise), `aberration` (colour fringes toward the edges), `wobble` (heat shimmer, stronger low on the
  screen), `blur` (edges smeared). All are offsets: 0 = off. The dark lands use them (Mordor, its fringe, the Dead
  Marshes, Morgul Vale, Angmar, the Barrow-downs, Isengard, the Brown Lands, Mirkwood), in the style of the Insanity
  shader pack. That pack is not ours to ship: its licence forbids redistributing it, so none of its code is used.
- Config `atmosphere.reduceMotion` drops grain, shimmer and the Eye's pulse (accessibility).
- Contrast pivots on dark grey (`CONTRAST_PIVOT` 0.2), not 0.5. Around mid-grey, Mordor's grade crushed an
  already dark frame to black (mean brightness 30 -> 1 of 255; now 30 -> 25, same view, 2026-11-25).
- Grain (2026-11-25, feedback: "almost pixelated, like grainy"): the first hash repeated as a striped pixel grid, worst in
  the darks. Now a pattern-free hash, two samples summed, strongest in the mid-tones, ~1/3 as strong. Client config
  `atmosphere.grain` scales it (0 = none).
- Camera smear (2026-11-25, asked for: "the screen almost smears" on sharp turns, never on small mouse movements):
  grade key `smear` (1 = up to 4% of the screen; the One Ring has 1, no region yet). `ColorGrade.smear` measures the
  camera's turn speed each frame: under `atmosphere.smearFrom` (180 degrees a second) nothing, full at
  `atmosphere.smearFull` (720), eased in over ~40 ms and out over ~150 ms; a jump of over 150 degrees in one frame is a
  teleport, not a turn. The shader averages 9 taps along the turn (middle weighted). Client config `atmosphere.smear`
  scales it; reduce motion turns it off. Checked headless (`devscripts/smear_shots.txt`, `turn` step in DevScript):
  120 degrees a second no smear, 400 some, 700 a clear diagonal smear. The headless client runs at ~10 fps, so the
  feel at 60+ fps (how quickly it comes and goes) is untested.
- Shader packs (Iris, optional, `compat/iris/IrisCompat` by reflection on Iris's public API). With a pack on, Iris
  draws the world into its own targets and composites at the end of the frame, so the pass moves from
  `RenderLevelStageEvent.AFTER_LEVEL` to a GUI layer under everything (over the finished frame; the hand gets graded
  too). Region grading is scaled by `atmosphere.shaderPackGrading` (0.5: packs grade colour themselves); the Ring's
  look is not. `atmosphere.overShaderPacks` turns it off. So a player can run Insanity, Complementary or BSL and still
  get the Ring and the dark lands. That's why there is no shader pack of our own yet: one would only be worth it for
  effects a post pass can't do (depth fog, light shafts), and it would need a `ringFactor` uniform through Iris
  internals.
- Checked in game (2026-11-25, headless client, `ring_shots.txt`): the region looks, the Ring fading in, the Eye's
  glow facing Barad-dur and the dark side leaning toward it; and with `-Piris` (Iris 1.8.12 + Sodium 0.6.13, the
  Insanity pack in `run/shaderpacks`, never committed) the Ring's look drawn over the pack's frame. Not checked:
  hiding a wearer from a second player, the look at full growth (10 minutes worn).

## Screenshots
`lotr/devscripts/ring_shots.txt` (also the "UI screenshots" workflow, which runs any changed devscript): the Shire,
the Dead Marshes, Morgul Vale and Gorgoroth, without and with the One Ring, facing toward and away from Barad-dur.
