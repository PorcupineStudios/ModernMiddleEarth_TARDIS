# TARDIS Code Architecture

Nov 3, 2026 · @Aaron

## Summary

The mod is one saved record per TARDIS, a set of datapack registries that describe what can exist, and one unlock check between them. Every feature, from a jump to a room to a texture, reads the record, asks the unlock check, and announces what it did through an event. This page turns the [TARDIS 1.21.1 Port Plan](https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ) into packages, classes and hooks for NeoForge 1.21.1.

Five rules every module follows:

1. **No feature is on by default in code.** It exists only if a data file defines it and the unlock check allows it.
2. **Numbers live in data, not Java.** Ranges, costs, caps and stage lists come from datapack JSON.
3. **Other mods are reached only through compat packages** that load when `ModList.isLoaded` says so. Create is the one hard dependency.
4. **Anything another mod or a pack maker may want to change gets an event or a service interface.** Nothing reaches into internals.
5. **State changes go through the record's methods,** so damage, repair and research all fire the same events and sync the same way.

## Project layout

The mod is **TARDIS: Ring Bearer**. It lives in the \`tardis/\` folder of the shared Ring Bearer repo. It depends on the shared API (\`api/\`, mod id \`ringbearer\_api\`), which the LOTR port also uses; neither mod depends on the other. The mod id is `tardis_rb`; ids on this page are written `tardis:…` for readability, and in code they use the mod id.

| Part | Package | What lives there |
| --- | --- | --- |
| Shared API (own subproject) | `api` | History log, condition types, providers, events and read-only views. Shared with the LOTR port; depends on neither mod. |
| Core | `core` | The TARDIS record, its saved data, the record manager, sync packets |
| Progression | `progression` | Stages, milestones, the unlock check, requirement types |
| Repair and research | `repair`, `research` | Broken part blocks, repair lists, research tree, the research hook |
| Power | `power` | Charge storage and costs; the Create winder sits in `compat/create` |
| Flight | `flight` | Destinations, jump planning, landing search, crash sites |
| Interior | `interior` | The shared interior dimension, plot allocation, rooms as structure templates |
| Exterior and looks | `look` | Look resolution by stage; renderers in `client` |
| Space | `space` | Rockets, the shared space dimension, planets, seamless transitions |
| Time travel | `timetravel` | Trips, paradox, ghosts, ripples, future trips; the recorder writes to the shared history log from day one |
| Other modules | `module/souls`, `module/halflife`, `module/bank` | Ported addon features, each behind its own unlock |
| Compat | `compat/<modid>` | Create, the LOTR port (with RingRealms), Refined Storage, Aeronautics, RFTools, KubeJS |
| Client | `client` | Renderers, models, screens; never referenced from common code |
| Data generation | `datagen` | Generates the default datapack, models, lang and loot |

## The TARDIS record

Each TARDIS is one `TardisRecord`, keyed by a UUID and saved in a single `SavedData` on the Overworld. Blocks, screens and commands hold only the UUID and look the record up through `TardisManager`.

| Field | Holds |
| --- | --- |
| Identity | UUID, name, owner, crew list, claimed flag (for random crash sites) |
| Subsystems | One level per subsystem id from data, plus damage per subsystem |
| Progress | Current stage, milestones passed, finished repairs, finished research nodes |
| Power | Stored charge and current cap |
| Location | Exterior position and dimension, current time (past, present, future), current planet |
| Interior | Plot in the interior dimension, placed rooms and their repair state |
| Look | Exterior and console look ids, chosen or derived from stage |
| Module data | A map of extra data per module id, so Souls, the bank and others store their own state without touching the core |

- **Saving.** Every field has a `Codec`, so the save format is readable and version-tagged. A `dataVersion` field and migration steps carry old saves forward, as in the current addon.
- **Changing it.** Only `TardisRecord` methods change state, such as `repair`, `damage`, `addCharge`, `completeResearch` and `setStage`. Each one fires its event and marks the record dirty.
- **Syncing.** Players inside a TARDIS, or with its screen open, get a compact view through a `CustomPacketPayload`. Clients never see the whole record.
- **Ownership.** A config value decides whether one player may own several TARDISes. Crew lists let anyone share.

## Data-driven content

Everything a pack maker tunes is a data folder loaded by a reload listener, so `/reload` and KubeJS apply changes live. Each loader syncs its entries to clients with our own payload so screens can show them. Destinations that are dimensions or planets stay datapack registries, because world generation needs them when the world loads. The mod ships its default progression as a datapack made by data generation; a pack overrides any file by path. A validator checks every file at load and rejects missing ids and loops, with a clear log message.

| Data folder | One file describes | Example fields |
| --- | --- | --- |
| `tardis/subsystem` | A subsystem | max level, what each level sets (range, charge cap, room cap) |
| `tardis/stage` | A progression stage | order, the repair list that completes it, the milestone that opens it |
| `tardis/repair` | One repair | subsystem, level it sets, costs, requirements, broken part block if physical |
| `tardis/research` | A research tree node | parents, costs, time, what it unlocks |
| `tardis/feature` | A switchable feature | requirements, enabled by default or not |
| `tardis/destination` | A place a TARDIS can go | dimension or planet, required level, charge multiplier |
| `tardis/room` | A room type | structure template, size, requirements, repaired and damaged versions |
| `tardis/look` | An exterior or console look | model per stage, texture per stage, damage overlays |
| `tardis/cost` | Reusable cost bundles | items, tags, fluids, charge |

A repair, as a pack maker would write it:

```json
{
  "subsystem": "tardis:navigation",
  "sets_level": 3,
  "stage": "tardis:local_travel",
  "physical_part": "tardis:broken_nav_circuit",
  "cost": [
    { "item": "create:precision_mechanism", "count": 2 },
    { "tag": "c:ingots/mithril", "count": 1 }
  ],
  "requires": [
    { "type": "tardis:research", "node": "tardis:star_charts" }
  ]
}
```

## The unlock check

One class answers every "may this happen?" question: `Unlocks.check(record, featureId)` returns allowed or not, plus the reason shown to the player. `Unlocks.level(record, subsystemId)` returns the effective level after damage and milestone caps.

- **Requirements are typed.** Each requirement in a data file has a `type`, and each type is a codec registered in the `ringbearer_api:condition_type` registry. Built in: `stage`, `milestone`, `subsystem_level`, `repair`, `research`, `config`, `mod_loaded`, `all`, `any`, `not`.
- **Other mods add types.** The LOTR port's colonies could add `lotr_port:colony_tier` and its factions `lotr_port:alignment`. They register through the shared API with no change to our code, and the same conditions work in LOTR quests and colony research.
- **Caps come from milestones.** A subsystem's effective level is the lower of its repaired level and the cap of the last milestone passed. Damage lowers the repaired level, so going backward needs no special case.
- **Config sits on top.** A server config lists features forced on or off. It wins over data, so a server owner can switch something off without editing a datapack.
- **Results are cached** per record and cleared whenever the record or the datapacks change, because some features check every tick.

## Hooks

Other mods and KubeJS scripts reach the TARDIS two ways: events they listen to, and service interfaces they register. Both live in the API jar.

### Events

All fire on `NeoForge.EVENT_BUS`. A cancellable event can stop the action; a modifiable one lets listeners change its numbers.

| Event | Fires when | Cancel / modify |
| --- | --- | --- |
| `TardisCreatedEvent` | A TARDIS is made or a crash site generates | No |
| `TardisClaimedEvent` | A player claims a TARDIS | Cancel |
| `JumpEvent.Plan` | A jump is requested, before checks | Cancel, change destination |
| `JumpEvent.Cost` | The charge cost is worked out | Modify cost |
| `JumpEvent.Land` | The landing spot is chosen | Modify spot |
| `JumpEvent.Arrived` | The TARDIS has landed | No |
| `RepairEvent.Pre` / `Post` | A repair is attempted / done | Cancel, modify cost / no |
| `ResearchEvent.Pre` / `Post` | A research node starts / finishes | Cancel, modify time / no |
| `DamageEvent` | A subsystem loses levels | Cancel, modify amount |
| `StageEvent` | The TARDIS enters a new stage | No |
| `MilestoneEvent` | A milestone is passed | No |
| `UnlockCheckEvent` | Any feature is checked | Override the result |
| `RoomEvent.Place` / `Remove` | A room is added or removed | Cancel |
| `LookEvent` | The exterior or console look is resolved | Change the look |
| `TimeTripEvent.Start` / `Close` | A time trip begins / ends | Cancel / no |
| `PlanetTransitionEvent` | The TARDIS or a rocket crosses between planet and space | Cancel |

### Service interfaces

Providers live in the shared API and are registered once at startup through a registration event on the mod bus. Code types (conditions, costs, destination types) are custom registries. No `ServiceLoader` or IMC, which are awkward on NeoForge.

| Interface | Lets another mod | Default in our mod |
| --- | --- | --- |
| `ResearchProvider` | Supply researchers and their speed | Player research screen |
| `PowerSource` | Feed charge into a TARDIS | Create winder |
| `DestinationType` | Add a kind of place to travel | Position, dimension, planet, time |
| `RequirementType` | Add a requirement for data files | The built-in types above |
| `CostType` | Add a kind of cost | Items, tags, fluids, charge |
| `CrashSiteProvider` | Choose where the spawn wreck goes | Spawn point of the start dimension |
| `TardisView` | Read a record without changing it | Read-only wrapper used by compat |

## A jump, step by step

A jump shows how the pieces meet: two checks can stop it, and four events let other mods change or follow it. In normal play the unlock check never fails here, because the console has no control for a destination until its repair is done. The check is a backstop against commands, scripts and other mods.

&#91;embedded content: one jump · 2 checks, 4 events\]

Every other action (repair, research, room placement, damage) follows the same pattern: a cancellable event, the unlock check, the cost, the change through the record, then a follow-up event.

## Integrations

Each integration is one package under `compat/<modid>` with a single entry class. The main mod calls it only when `ModList.isLoaded` is true, so the mod runs with any set of optional mods.

| Mod | Required? | How it plugs in |
| --- | --- | --- |
| Create | Required | The winder is a Create kinetic block entity that turns rotation into charge through `PowerSource`. Repair costs use Create parts, and stage requirements can check Create items. |
| LOTR port | Required for the modpack, optional in code | Supplies Middle-earth as the start dimension through `CrashSiteProvider`. Adds `lotr_port:alignment` and LOTR ore tags for costs. Without it, the start dimension falls back to the Overworld. |
| LOTR port colonies (RingRealms) | Optional | Registers a `ResearchProvider` for colonists when its own config allows. The TARDIS mod has no colony code at all. |
| Refined Storage | Optional | A storage room and a TARDIS network link, behind their own features. |
| Create: Aeronautics | Optional | Airships can dock in the Vehicle Bay room and ride inside a jump. |
| RFTools Dimensions | Optional | Its dimensions become a `DestinationType`, unlocked last. |
| RFTools | Optional | Screen modules that show TARDIS charge and location. |
| KubeJS | Optional | A plugin exposes our events to scripts and lets scripts register requirement and cost types. |

Both mods depend on the shared API and never on each other, so the dependency arrows point one way. They share one repo and one CI run, so an API change lands in the same pull request as the code that uses it.

## Client and visuals

The server decides which look applies; the client only draws it. That keeps every visual change a data or resource-pack change.

- **Looks.** A `tardis/look` file maps each stage to a model and texture set, plus damage overlays. The server resolves the look id from the record (after `LookEvent`) and sends it with the sync payload.
- **Exterior.** One block entity renderer draws the look's model. New stages or skins need only new files under `assets/<modid>/models/look/` and `textures/look/`, which Aaron paints.
- **Console and parts.** Console and broken part blocks use the same look system, so a repaired panel swaps model and texture without new code.
- **Interior rooms.** Rooms are structure templates placed into the TARDIS plot. Repairing a room places its repaired template over the damaged one.
- **Screens.** The research tree, the repair list and the navigation map read the synced registries and the record view. They show why something is locked, using the reason from the unlock check.
- **Seamless space.** Planets and space share one dimension. Custom sky and planet rendering hides the transition, which is the riskiest client work and gets the first prototype in the space phase.
- **Controls appear with repairs.** Console controls are physical parts tied to repairs. A control for a destination, a dimension or a feature doesn't exist on the console until its repair is done, so players never see a button that can't work.

## Decided

- **Name:** TARDIS: Ring Bearer (mod id `T``A``R``D``I``S``_``R``B`, written tardis\_rb in code because NeoForge mod ids must be lowercase).
- **Research** belongs to the TARDIS and is shared by its crew, like repairs.
- **Damage** lowers subsystem levels only; finished research is kept.
- **Repository:** the shared Ring Bearer repo (TARDIS-RingBearer), folder \`tardis/\`, next to \`api/\` and \`lotr/\`.
- **Controls:** destination and feature controls only exist once repaired; the unlock check is a backstop.
- **Tests:** one GameTest per phase done-when, run by `runGameTestServer` in CI.
- **Create:** pinned to one version; every Create call stays in `compat/create`.
