# Delves (Moria, Goblin Town) and goblins

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Delves: Moria (2026-11-25, `world/delve/`)
- Aaron: procedural caves, tunnels, great halls, mines, forges, stairs and bridges for Moria (later Goblin Town);
  Moria at full map scale from the West-gate (Eregion) to Dimrill Dale, a grand Third Age ruin, coded set pieces plus
  .nbt rooms, a hidden route through a maze. Feature `lotr_port:delve`; Moria's whole spec is
  `worldgen/configured_feature/moria.json`. A plan per world seed (`DelvePlanner`: grid of 48-block cells, cover rule
  under the mountain, set pieces, hidden route, Kruskal maze, prune, loops), built per chunk from a cell index.
  Planner and builders are plain Java (`Ground` = `MiddleEarthMap.heightAt`). `/lotr_delve info|goto|preview`.
  GameTests `DelveTests`; world check `WorldCheck.moriaIsBuilt`. **Read `lotr/docs/delves.md`** (tuning, template
  room contract with jigsaw doorways, numbers). Inhabitants by depth: `delve_spawns/moria.json` (`DelveSpawns`;
  orcs above, trolls below, the Balrog very rare under y -30, one at a time). Not checked in game yet.
- Goblin Town (2026-11-26, layout `goblin`: `GoblinPlanner`, `GoblinPieces`, `RiftShape`; blocks and the trap in
  `content/goblin/`, drawn by `converter/goblin_blocks.py`): THE town (`goblin_town.json`), generic copies
  (`goblin_towns.json`) and decoy porches (`goblin_porches.json`) scattered per region over the northern mountains.
  The Front Porch's floor swings open (`GoblinTraps`, block displays; armed = open drop under it, no block state, so
  decoys are indistinguishable; redstone springs it). Spawns by zone (Great Goblin at the throne, Gollum by his lake).
  GameTests `GoblinTests`; world check `goblinTownIsBuilt`; seen in the screenshot workflow's client
  (`devscripts/goblin_town.txt`, whose world step needs 10 GB like build.yml's), not played yet. Read the Goblin
  Town section of `lotr/docs/delves.md`.

## Goblins (2026-11-26, Aaron: a new faction with its own model, which players can be too)
- Faction `lotr_port:goblins` (`faction/goblins.json`, hand-written; lang keys in `converter/colonies.py UI_EN`):
  the Misty Mountains' goblins, friends of Gundabad, Angmar, Mordor and Dol Guldur, at war with the free peoples
  (Gundabad's list); control zones down the range from Goblin Town; pledge at Cutthroat (100). Counted in
  `FactionTests.HAND_MADE`.
- Body `goblin` (`client/ShapedModel` shape `GOBLIN`, an `NpcModels` kind): the humanoid boxes and 64x64 skin layout
  drawn at goblin proportions (head x1.2, narrow hunched torso, long thin arms x1.15, short legs x0.75; part scales
  and posture offsets added after vanilla's animation, so walk, swing, crouch and ride stay vanilla's; held items keep
  their shape), plus bat ears and a hooked nose in the rows under y 32 (layout in `NpcModels`). Vanilla's `setupAnim`
  never resets `head.z`/`body.z`: set them, don't add to them.
- NPCs `goblin`, `goblin_archer`, `great_goblin` (hand-written, `NpcTests.HAND_MADE`): skins painted by
  `converter/goblin_skins.py` (`textures/entity/goblin/`: 8 warriors, 4 hooded archers, the crowned Great Goblin;
  colours matched to Goblin Town's blocks), orc name bank and speech. Goblin Town's spawns and the Front Porch's
  ambush are goblins. `converter/body_preview.py` draws a skin on the goblin body (or the humanoid) outside the game.
- Player race `goblin` (`player_race/goblin.json`: scale 0.8, a little faster, 2 less health, home Goblin Town,
  `own_skin`): the player's own skin is converted to the goblin body (`RaceSkinRemap` goblin rules: head, body, limbs
  as they are, flattened second layers, the vanilla hat kept, ears and nose painted in the face's median-brightness
  colour with a darker ear hollow). `/lotr_race set <player> <race>` (op 2) sets a race without the picker.
- GameTests `GoblinFactionTests`; JUnit `RaceSkinRemapTest`; screenshot script `lotr/devscripts/goblins.txt`
  (`view front|back` turns the camera). Not yet: goblin traders, captains or quests; surface spawns (goblins live in
  the goblin towns); a goblin colony race.
