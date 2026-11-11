# Colonists: traits, mood, kin and family homes

Read when: you touch `colonist/`, home assignment (`colony/work/Assign`), happiness (`colony/work/Life.needs`) or the
`colonist_*` data. Written 2026-11-11 (moved from the abandoned `claude/colony-core` branch onto the society core).
Design: the "Colonists" section of https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N.

## Status: unbuilt

Not compiled against NeoForge. The plain rules ran here against stand-in classes: `ColonistRulesTest` (8) and
`HouseRulesTest` (5). The glue (`Colonists`, `ColonistData`) and the edits to `Assign` and `Life` were only parsed.

## How it sits on the society core

- Three person components (`ColonistData`): `lotr_port:traits` (list of trait ids), `lotr_port:mood` (remembered
  thoughts), `lotr_port:kin` (race, birth tick, parents, spouse, children). Registered at mod setup by
  `Colonists.register()` (before `ComponentType` freezes).
- **Happiness stays the one mood number.** `Life.needs` adds `Colonists.moodToday(...)` to its happiness target: trait
  thoughts + the home thought + live memories. Their numbers are on the same scale as `colony/life` settings (a
  normal target is ~70; colonists leave under 25).
- **Food** is still `WorkComponents.Needs.food`; traits and races scale how fast it runs down (`need_rates` with
  `lotr_port:food`, the only need id allowed so far). My separate need bars (rest, leisure, company, safety) were
  dropped: nothing filled them, and two hunger systems would fight.
- **Work speed**: `Life.worker` multiplies output by `Colonists.workSpeed` (traits' `work_speed`).
- **Settling**: a person gets traits, an empty mood and kin when the `citizen` component is set
  (`SocietyEvent.ComponentChanged`), seeded by their id. `Assign.run` also calls `settle` for every citizen each
  day, which back-fills citizens from older saves (a no-op for anyone with traits).
- **Grief**: on `SocietyEvent.Died`, the dead person's living parents, spouse and children remember `kin_died`.
- **Races**: which NPC type is which race stays `colony_race/` (`Races.of`). `colonist_race/<race>.json` adds how
  that race lives (days per year, adult age, extended households, food rate, settler ages). Files: man, hobbit,
  dwarf, elf, orc. Only Men's ageing is Aaron's (1 day = 1 year, adult at 18); the rest are first guesses.

## Households and homes

- `KinRules.household`: a couple and their children under age; races with `extended_household` (hobbits) keep grown
  children, spouses who married in and grandparents together. Only living citizens of the colony count.
- `HouseRules.assign` (called by `Assign.homes`): everyone keeps a home that still has their bed; each household
  gathers in the home where most of it sleeps; the homeless are housed by household, biggest first: their
  household's home, else an empty home that fits them all, else any home that fits them all, else one by one. Nobody
  is moved out to make room.
- `KinRules.homeThought` (per citizen, per day): no home = no thought (the colony's home bonus is already missing);
  alone with your household = `own_home` (single, +4) or `family_home` (+10); with one or two other households =
  `shared_home` (-2) or `family_shared` (-8); with three or more (`barracks_households`) = `barracks` (-5) or
  `family_barracks` (-14). So a child in the parents' house is never a dormitory penalty, and families want their own
  house.

## Data

| Folder | Fields |
|---|---|
| `colonist_trait/` (17) | `kind` (positive, negative, neutral), `scale`, `weight`, `heritable`, `work_speed`, `need_rates`, `thoughts`, `on_break`, `conflicts` |
| `colonist_thought/` (17) | `mood`, `duration_ticks` (0 = lasts while its cause does), `stack_limit` |
| `colonist_race/` (5) | `days_per_year`, `adult_age`, `extended_household`, `need_rates`, `settler_age_min`, `settler_age_max` |
| `colonist/settings.json` | `max_traits`, `extra_trait_chance`, the six home thoughts, `barracks_households` |

`ColonistData.loadProblems()` lists dangling ids (no GameTest asserts it yet).

## UI

The People tab (`colony/ui/ColonyViews`) has a Traits column and a Trait filter (traits present in the colony). The
filter box flows into extra columns when it would be taller than the screen.

## Not yet

Marriage and births (the `wedding`, `child_born`, `came_of_age` thoughts exist as data only), so every household is a
single settler until those land; migrants arriving as households; children's bodies and size; break behaviours
(`on_break` is read by `TraitRules.onBreak` but nothing acts on it); a family tree screen; `saw_death`, `raid` and
`festival` memories (no events post them yet).
