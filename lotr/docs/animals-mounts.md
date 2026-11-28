# Animals and mounts

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Animals and mounts
- Animals are creature NPCs (above); `converter/world.py GRAZERS` adds vanilla spawners on grass biomes. NPCs with
  `spawnsInDarkness` spawn only in the dark unless the biome has `hostiles_in_day` (`npc_spawns`, `NpcSpawner`).
  LOTR mutton and the three vanilla horse armours are vanilla items (`VANILLA_ITEMS`).
- LOTR's animal NPCs (deer, aurochs, bears, kine araw...: `npc_animals/<biome>`, from LOTR's creature lists by
  `extra.py ANIMAL_SPAWNING`) spawn as 1.7.10's creatures did (2026-11-18, Aaron: aurochs were rare and vanished):
  with the land (`generation_chance` 0.1 per new chunk, mixin `AnimalGenerationMixin` after vanilla's creatures,
  `NpcSpawner.populate`), then every 400 ticks up to `cap` 10 within `radius` 128 counting only animals
  (`count_only_animals`: villages don't keep them away), and they don't despawn (`LotrNpc.removeWhenFarAway`,
  `NpcSpawns.isAnimal`). LOTR's horse family (boars...) is on the biomes' vanilla spawn lists. GameTest
  `lotrAnimalsSpawnAndStayLikeAnimals`.
- Animal density (2026-11-19, Aaron: "a LOT of animals"): 1.7.10 had one 10% roll per new chunk over one creature
  list (vanilla's and LOTR's animals together). Post-step `converter/animal_balance.py` splits that roll by weight
  between vanilla's pass (biome `creature_spawn_probability`) and LOTR's (`generation_chance`); before, each rolled
  10%. The ongoing animal cap counts vanilla animals (and LOTR's horses) too, not only LOTR's animal NPCs.
- Mounts (2026-11-10, `mount/`, `converter/mounts.py`): LOTR's horse family is one entity class, `LotrHorse` (a
  vanilla `AbstractChestedHorse`, so taming, saddle, inventory screen, jumping and breeding are vanilla's), with one
  entity type per breed: `horse`, `shire_pony`, `zebra`, `wild_boar`, `camel`, `elk`, `rhino`, `giraffe`. Looks and
  sizes are `content/mounts.json` (startup: model, textures, markings, saddle, carpet, scale, `seat`); numbers are
  `data/<ns>/mount/<breed>.json` and reload: `food` (items or `#tags`), `armour` (which mount armour fits), `attack`,
  `fights_back` (speed; boars, elk, rhinos), `spawn` (ops on the vanilla roll: `mul`, `mul_rand`, `min`, `max`),
  `boosts` (biome tag: Rohan and Dor-en-Ernil horses), `clamp_*` (foals: mean of parents and a fresh roll, clamped),
  `chest`, `milk`, `sounds`. `/summon` skips the rolls (vanilla does too).
- `MountArmorItem` (kind `mount_armor`: mount, protection, texture) goes in the body slot; vanilla horse armour fits
  breeds with `armour: horse` (horses, ponies, zebras, as in LOTR), wool carpets fit camels. Warg armour items exist
  but wargs don't wear them yet. Giraffe and camel armour (iron, golden, diamond) is the port's own (Aaron
  2026-11-10; LOTR's Mount enum had both but no items): `mounts.py NEW_ARMOUR_*` paints plates onto the model's
  texture layout (`paint_armour`), stamps vanilla's icon, and writes a recipe (vanilla horse armour + 2 leather).
- Drawing (`client/MountModel`, `MountRenderer`): horses, ponies and zebras use Minecraft 1.7.10's horse model,
  because LOTR's zebra and horse armour textures are painted for its 128x128 layout. The converter reads it from
  Mojang's 1.7.10 client jar (downloaded to `converter/cache/`, gitignored) into `npc_model/old_horse.json`; its
  animation is written in `MountModel` from 1.7.10's numbers. The others use LOTR's models through `CreatureModel`
  (`CreatureAnim.Ctx.state` answers what they ask of a horse), with `<id>_saddle` (inflated) for saddle, armour and
  carpet passes. Foals of the 1.7.10 models are the adult at half size.
- NPC riders: `npcs.py` reads LOTR's `spawnRidingHorse` chance and `createMountToRide` (mount and its armour) into
  gear `mount` / `mount_armor` and `ride_chance`; `LotrNpc.maybeRide` (wild and invasion spawns) and hired units go
  through `Hiring.mount`. An NPC's mount is tame, saddled and `NpcOwned` (despawns like its rider; players can't use
  it while the NPC rides). The NPC entity has a riding offset so riders sit in the saddle. The rider's feet are at
  half the mount's height (LOTR), which is `seat`.
- Rhosgobel rabbits (2026-11-24, Aaron: Radagast's sleigh rabbits, from a film still): the port's own breed
  `rhosgobel_rabbit` (`converter/mounts.py OWN_BREEDS`), pony-sized shaggy hares, 1.2 x 1.3, carrots. Own model, hop
  and three coats from `converter/rhosgobel_rabbit.py` (writes `src/main/resources/.../npc_model/rhosgobel_rabbit.json`
  and `textures/entity/rhosgobel_rabbit/`): crouched long body and big rump, thick neck, block-long ears swept back,
  long hind feet; the hop is a bound driven by limb swing (body lifts and pitches, hind legs push together, forelegs
  reach together, ears flatten in flight; idle ear twitch and nose wiggle). They live round Rhosgobel (waypoint
  68288, 4160, in southern Mirkwood at its western eaves): biome modifier `rhosgobel_rabbits.json` puts them on
  `mirkwood_corrupted` and `anduin_vale` (weight 8, 2-4), and the breed's `spawn_near` (`Mounts.nearHome`, in the
  spawn placement rule) keeps them within 2048 blocks of the waypoint. GameTest `rhosgobelRabbitsLiveRoundRhosgobel`.
  Saddled, they carry a rider (2026-11-25): the same script writes `rhosgobel_rabbit_saddle.json` (the same parts, so
  it moves with them, plus pommel, cantle and stirrups) and `textures/entity/rhosgobel_rabbit/saddle.png`; seat 1.4.
  With the harness mod they also pull, saddle and harness worn together. The generated entries (mounts.json, `mount/rhosgobel_rabbit.json`, en_us
  name) were written from `OWN_BREEDS` without a full converter run (the LOTR jar isn't in the sandbox).
- For the harness mod (optional, no dependency either way): `LotrNpc` is an `OwnableEntity` (its hirer), and
  `LotrPort` sends IMC `draught_key` naming NPCs by NPC type, so harness data can list `lotr_port:mordor_warg` etc.
  `client/PartPoses` records where each creature model's parts were drawn (the first `renderToBuffer` per entity
  after `RenderLivingEvent.Pre`; old horses too) and `LotrClient` offers it as IMC `part_pose`, so the harness sits on
  the neck and body and follows the animation. `NpcRenderer` posts `RenderLivingEvent.Post` for rigged NPCs too (their rig skips `LivingEntityRenderer.render`),
  so other mods' overlays draw on them.
- GameTests `MountTests`. Earlier batches leave blocks outside the 1-block `empty` template: clear the space first.
  Screenshot script: `lotr/run/mount_shots.txt`.
