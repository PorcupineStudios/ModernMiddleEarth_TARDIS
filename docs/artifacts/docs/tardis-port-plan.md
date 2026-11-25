# TARDIS 1.21.1 Port Plan

Nov 3, 2026 · @Aaron

## Summary

We rewrite the TARDIS Mod and the TardisTimeTravelAddon as one NeoForge mod for Minecraft 1.21.1, using DarkholmeTenk's TardisMod as reference only. It is the spine of a custom progression modpack. The player finds a wrecked TARDIS crash-landed at spawn in Middle-earth, winds it up with Create rotation for short hops, and repairs it stage by stage until it can reach the Overworld, space, the past and future, and RFTools dimensions.

We also port the LOTR mod to 1.21.1. Both mods live in one repo with a shared API, and the LOTR port is built first; the [Shared Roadmap](https://claude.ai/artifact/H9foA8JKxCZEc4ArAyaXjZ) gives the order.

One rule shapes every system: **nothing is hard-coded on.** Every feature, stage, cost, unlock and visual comes from data files that a pack maker can change, disable or rebalance without a rebuild.

## Decisions so far

All from Aaron on 2026-11-03.

| Topic | Decision |
| --- | --- |
| Version and loader | Minecraft 1.21.1 on NeoForge |
| Approach | Clean rewrite; old TardisMod is reference, not law |
| Setting | Centred in Middle-earth (the LOTR port is also in scope) |
| Start | One crash site at the spawn of whichever dimension the world starts in (Middle-earth world type: Middle-earth; normal world: the Overworld); rare random wrecks elsewhere |
| Look at start | Scrappy police box, bare unpainted wood through scratches, chunks sheared off; cramped broken console room |
| Power | Wind it up with Create rotation; early charge only covers short hops |
| Progression | Gradual: each repair goes further; big milestones (e.g. dimension travel) still leave some dimensions locked; major milestones cap which upgrades are possible |
| Repair | A repair room in the TARDIS's basement holds the repair and upgrade skill tree as one GUI; no broken blocks to fix by hand (changed 2026-11-03 evening) |
| Repair costs | A mix: LOTR ores, time-travel ores, space ores, overworld ores, Create recipes; each stage needs its own Create-made part (our sequenced-assembly recipes), since Create has only about three tiers of its own |
| Going backward | Supported in code (damage lowers repair), even if unused at first |
| Rooms | Room types and maximum room count grow with upgrades |
| Research | Player screen; colonists from the LOTR port (RingRealms is built into it) can research too when its config allows |
| Ownership | Players can always share; a config decides whether one player may own several |
| Time travel | Late milestone, not the last |
| Space program | Part of this mod |
| Pack-maker tools | Datapacks and KubeJS (Claude's call) |
| Time-travel features | All carry over: recorder, trips, paradox, ghosts, ripples, future trips |
| Space travel | Rockets first, to gather what repairs the TARDIS's space capabilities; then the TARDIS flies to planets itself |
| Dimensions | Many: Middle-earth, the past, the future, planets, RFTools Dimensions, more added later |
| What starts a stage | A fixed list of repairs per stage, not points |
| Random crash sites | Salvage by default: strip them for parts, research data and room blueprints, with lootable blocks around the crash. Claimable only when the config allows a player several TARDISes |
| LOTR | Future-ported to 1.21.1 as part of this project |
| Textures | Aaron makes them; nothing copied from the old mod |
| Dimension order | Start dimension first (Middle-earth, or the Overworld in a normal world), then the other one, space, time travel, RFTools Dimensions; may still change |
| Planets | Seamless planet-to-space-to-planet travel, Outer Wilds style; large scope accepted |
| Repository | One private repo (TARDIS-RingBearer) with api/, tardis/ and lotr/; both mods depend on the shared API, never on each other |
| Build order | The LOTR port goes first; TARDIS work starts once Middle-earth exists |
| History | The world is recorded from day one through the shared history log; only travel to the past unlocks late |

### Flight, door, console and repairs (Aaron, 2026-11-03 evening)

- **Seamless space, Outer Wilds style.** One continuous flight from a planet's surface, through space, to another planet, with no loading screen. Rockets stay as the first way up.
- **Space in any dimension.** Space is not tied to the Overworld. Each dimension's sky can lead into its own space (or a shared one), set per dimension in data files, so Middle-earth, the Overworld and modded dimensions can all have space travel.
- **Backburner: LOTR celestial bodies.** Maybe later: the Sun (Anor), the Moon (Isil) and Eärendil's star as places to visit from Middle-earth's sky. The LOTR mod only draws them as sky textures today. Not planned yet.
- **The TARDIS flies without dematerialising.** Besides jumps (dematerialise here, appear there), the TARDIS can take off and fly as a real object in the world, through the sky and through space. While flying, the pilot sees it from a third-person camera, spinning as it flies.
- **A seamless door.** Where possible, opening the door shows the real outside from inside and the real console room from outside, and you walk through with no teleport screen. This needs portal rendering (another place drawn live through the doorway), the hardest rendering job in the mod, so it gets its own prototype.
- **A custom console, like the original mod's.** One big console model with its own buttons, levers, dials and screens that don't follow block shapes. Each control is a clickable part of the model, and controls appear as they are repaired.
- **Repairs happen in the basement.** Below the console room is a repair room with the repair and upgrade skill tree as one GUI. There are no broken part blocks to fix by hand; the part blocks built in phase 3 get replaced.

## Architecture

Each TARDIS is one saved record made of subsystems, and no feature reads anything else. Blocks such as the console, engine and exterior are thin views onto that record.

&#91;embedded content: architecture · inputs, the TARDIS record, the unlock check, features\]

- **Subsystems and levels.** Each subsystem has a repair level from 0 (broken) up to a cap set by the current milestone. Its level sets its numbers, such as jump range, charge capacity, allowed dimensions and room count. Damage just lowers a level.
- **One unlock check.** Every feature asks the same question before it runs. The answer comes from data files, so a pack maker can turn a feature off or move it to another stage.
- **Research hook.** The mod defines who can research. It ships the player screen, and the LOTR port registers its colonists through the same hook when its config allows. Neither mod needs the other.
- **Power.** A Create-driven winder turns rotation into stored charge: stress × speed × time, with an intake cap per repair level, so a bigger Create setup charges faster. Each jump costs charge by distance, and repairs raise the charge cap.
- **Look by state.** The exterior and console models are picked by repair stage, so a new look is a resource pack change. Repairing a room places a new structure template over the damaged one.
- **Interiors.** All interiors live in one shared dimension, each TARDIS in its own walled-off region. This keeps saves and chunk handling simple; the trade-off is shared weather and time, and walls must stop anything leaking between plots.
- **History from day one.** The shared API's history log records the world from its first day, so the past is full when time travel unlocks. Recording is limited to areas near players, claims and colonies, and old history is compacted.
- **Tuning data reloads.** Repairs, costs, research, stages and looks load through reload listeners, so `/reload` and KubeJS apply changes live. Datapack registries are kept for what world generation needs (dimensions, planets).

## Progression model

Progression is a ladder of stages separated by major milestones. Inside each stage, many small repairs raise numbers gradually. Each milestone lifts the cap on how far those repairs can go. The stages follow the dimension order in Decisions so far; the milestone names are placeholders.

&#91;embedded content: progression · example stages and milestones, names not final\]

- **Gradual inside, gated between.** A repair raises one subsystem a little, so travel gets comfortably further over time. A milestone is a bigger repair that needs mixed materials: LOTR, time-travel, space and overworld ores plus Create parts at the matching Create tier.
- **Locks survive milestones.** Opening dimension travel unlocks one dimension. Each other dimension needs its own repair level, set per dimension in data.
- **Backward works too.** Damage lowers levels and can drop a TARDIS below a milestone. The code supports it from day one, even if the pack doesn't use it yet.

## Module scope

Each module is optional, plugs into the same unlock check, and is gated on its mod being present.

| Module | Fate in the port | Notes |
| --- | --- | --- |
| TARDIS core (flight, console, rooms, sonic) | Rewrite | The heart of the plan |
| Time travel (recorder, trips, paradox, ghosts) | Port | Recording runs from day one; travel is a late milestone |
| Souls | Port |  |
| Half-life ores | Port | Natural fit for time-travel ore costs |
| Dwarvish bank | Port | Needs the LOTR port |
| RFTools | Port | 1.21.1 NeoForge build exists |
| Kinetics | Scrapped | Replaced by Create integration |
| Airships | Replaced | Create: Aeronautics integration only |
| AE2 storage | Replaced | Refined Storage integration |
| Space program | New | Galacticraft-style; space ores feed TARDIS milestones |
| RingRealms research | Optional compat | LOTR port colonists research TARDIS tech when its config allows |
| RFTools Dimensions | Integration | Its dimensions become TARDIS destinations |

## Build phases

The order follows the dependencies: the data model and unlock check come first because everything else plugs into them. Each phase ends playable.

1. **Foundations.** The TARDIS record with subsystems and repair levels, the unlock check on the shared API's conditions, tuning data through reload listeners, the default progression shipped as a datapack, and a validator that rejects missing ids and loops at load. The shared repo, CI and the history log come from the shared roadmap's first phase.
   - Done when: a GameTest loads a TARDIS's subsystems from JSON, raises and lowers a repair level, and \`/reload\` picks up a changed cost.
2. **The wreck and short hops.** Crash site at the spawn of the start dimension (from the shared API's crash-site provider), random wrecks to salvage for parts, damaged police box exterior, cramped console room in the shared interior dimension, Create winding mechanism, short XYZ jumps costing charge.
   - Done when: a new world spawns the wreck, and winding it with Create lets the player hop a short distance.
3. **Repair loop.** The basement repair room with the repair and upgrade skill tree GUI, research, per-stage repair lists from data, visual stage swaps for the exterior and console, damage lowering levels.
   - Done when: one repair in the basement GUI and one researched node raise jump range and change the box's look.
4. **Rooms.** Room types as structure templates, room count capped by upgrades, room repair placing a new template.
   - Done when: a repaired TARDIS can add a second room, and the cap blocks a third.
5. **The Overworld and milestones.** Dimension travel milestone, per-dimension locks, milestone caps on upgrades.
   - Done when: one milestone opens the Overworld while later dimensions stay locked.
6. **Space.** Two prototypes first (one shared space dimension, and separate planet dimensions behind a masked atmospheric-entry transition), keep the better one, then rockets, space ores, the shared space dimension and planets, the TARDIS space repairs, and TARDIS free flight: flying without dematerialising, seen from a spinning third-person camera. The seamless door view is prototyped here too, since it needs the same live rendering of another place.
   - Done when: rockets bring back what the TARDIS needs, and the repaired TARDIS flies itself, without dematerialising, from a planet to space and on to another planet without a loading screen.
7. **Time travel.** Travel to the history the log has been recording since day one, plus all old features: trips, paradox, ghosts, ripples, future trips, with the past and future as destinations.
   - Done when: a TARDIS past the time milestone travels to the past and back.
8. **Other ported modules.** Souls, half-life ores, the Dwarvish bank, RFTools, and RFTools Dimensions as the last destinations, each behind its own unlock.
   - Done when: each module works with its unlock on, and is absent from the game with it off.
9. **Integrations.** Refined Storage, Create: Aeronautics, colonist research from the LOTR port.
   - Done when: the game runs with none, some or all of these mods installed.
10. **Pack-maker pass.** KubeJS bindings and a second, alternative progression datapack. Each data file's docs are written when its loader is made, not here.
    - Done when: a pack maker can rebalance a stage using only a datapack.

## Risks

- **Middle-earth comes from our own LOTR port.** The LOTR port goes first (Aaron's call), so Middle-earth exists before TARDIS phase 2. The crash site uses whichever start dimension the world has, so it also works in the Overworld.
- **Seamless planets are a large job, and we've accepted it.** Every 1.21.1 dimension is a separate world with a loading screen. Planet-to-space-to-planet travel means one shared space dimension with custom sky and planet rendering. Phase 6 opens with two prototypes, one shared dimension and one masked transition between dimensions, and keeps whichever works.
- **Licensing.** All code is rewritten and Aaron makes all textures, so nothing from DarkholmeTenk's mod ships.
- **Create version drift.** Create's API changes between minor versions. We pin one Create version and keep every Create call in `compat/create`, even though Create is required.

### Mod availability on 1.21.1 NeoForge

Every mod the plan needs has a 1.21.1 NeoForge build, so 1.21.1 stays the target.

| Mod | Newest 1.21.1 NeoForge build found | Published |
| --- | --- | --- |
| [Create: Aeronautics](https://modrinth.com/mod/create-aeronautics/version/YhZLrAFC) | 1.2.1 | 2026-04-28 |
| [Refined Storage](https://www.curseforge.com/minecraft/mc-mods/refined-storage/files/7946727/dependencies) | 2.0.3 | 2026-04-18 |
| [RFTools Dimensions](https://modrinth.com/mod/rftools-dimensions/version/4kio5dl3) | 1.21-12.0.2 | 2025-12-06 |

Create and RFTools Base aren't listed separately: Aeronautics needs Create and RFTools Dimensions needs RFTools Base, so both exist on 1.21.1 too.
