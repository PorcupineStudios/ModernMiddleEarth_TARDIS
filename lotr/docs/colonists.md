# Colonists: traits, mood, kin and family homes

Read when: you touch `colonist/`, home assignment (`colony/work/Assign`), happiness (`colony/work/Life.needs`) or the
`colonist_*` data. Written 2026-11-11 (moved from the abandoned `claude/colony-core` branch onto the society core).
Design: the "Colonists" section of https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N.

## Status

Green in CI (2026-11-11): `ColonistRulesTest` (8) and `HouseRulesTest` (5) pass, and so do all 84 LOTR GameTests,
including `LifeTests` (its citizens have no traits and a man's appetite, so its exact counts test the base rules) and
the 50-colony budget test.

## Performance

`SocietyTests.fiftyColoniesRunTenDaysWithinTheBudget` allows 2 ms of colony work a tick and at most 3 slow ticks; the
base code already peaked at 1.66 ms, so colonist work has almost no room. What got it back to 0 slow ticks (1.8 ms
worst): traits are rolled from ids sorted once per reload (`TraitRules.ordered`); citizens from older saves get traits
once at server start (`Colonists.onStarted`), never inside a colony pass; anyone with no parents and no spouse is
their own household without walking the tree; households are worked out once per colony per tick and shared by the
homes and needs passes; home thoughts count households per house, not a set per person; `HouseRules` stops at once
when nobody is homeless; mood is summed without building lists or sets. Keep new per-citizen daily work that cheap.

## How it sits on the society core

- Three person components (`ColonistData`): `lotr_port:traits` (list of trait ids), `lotr_port:mood` (remembered
  thoughts), `lotr_port:kin` (race, birth tick, parents, spouse, children). Registered at mod setup by
  `Colonists.register()` (before `ComponentType` freezes).
- **Happiness stays the one mood number.** `Life.needs` adds `Colonists.moodToday(...)` to its happiness target: trait
  thoughts + the home thought + the ration's thought + live memories. `Colonists.thoughts` lists the same for the
  citizen page. Their numbers are on the same scale as `colony/life` settings (a
  normal target is ~70; colonists leave under 25).
- **Food** is still `WorkComponents.Needs.food`; traits and races scale how fast it runs down (`need_rates` with
  `lotr_port:food`, the only need id allowed so far). My separate need bars (rest, leisure, company, safety) were
  dropped: nothing filled them, and two hunger systems would fight.
- **Work speed**: `Life.worker` multiplies output by `Colonists.workSpeed` (traits' `work_speed`).
- **Settling**: a person gets traits, an empty mood and kin when the `citizen` component is set
  (`SocietyEvent.ComponentChanged`), seeded by their id. Citizens from older saves get theirs when the server starts.
- **Names**: settling also names a citizen whose NPC type has no name bank (LOTR's men) from their people's bank
  (`gondor_man` → `npc_names/gondor_male`, else `bree_male`; `Colonists.nameFor`, steady per person). Anyone can be
  renamed (`Persons.rename`, history `person_renamed`, `SocietyEvent.Renamed`: a loaded body takes the name over its
  head).
- **Orders** (person part `orders`, `colonist.Orders`, set on the citizen page): a workplace, rest (no job), a home,
  a ration. Absent = the colony decides. `Assign.jobs` places ordered workers first while their workplace has a slot;
  `HouseRules` step 0 puts ordered residents in their home first while it has a bed (this does move someone else
  out, the one exception to "nobody is moved out"). A ration multiplies the food rate and may add a standing thought
  (`moodToday`, `thoughts`).
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

## Sleep, schedules and tasks

- **Schedules** (colony part `schedules`, `colonist/Schedules`): ONI's 24 hour blocks, Work / Leisure / Sleep / Any,
  as letters `W L S A` from midnight (Minecraft's day starts at 06:00: `Schedule.hourOf`). The first is the standard
  one (`colonist_sleep/settings.json standard_schedule`: sleep 22-06, work 06-12 and 13-18, Any at noon, leisure
  18-22). A colonist keeps the one their `orders.schedule` names; a removed schedule falls back to the first.
- **Race rhythms** (`colonist_race/*.json rhythm`: `awake_from`, `awake_to`, `sleep_hours`, `leisure_hours`), ONI's
  early birds and night owls by race:

  | Race | Likes to be up | Sleep | Leisure | On the standard day |
  |---|---|---|---|---|
  | Man | 06-22 | 8 h | 3 h | fits exactly |
  | Hobbit | 08-23 | 9 h | 5 h | up 2 h early (odd hours), tired without the noon Any hour |
  | Dwarf | 04-20 | 7 h | 2 h | evening leisure 20-22 is late for them (odd hours) |
  | Elf | 10-04 | 4 h | 6 h | work 06-10 is the wrong hours, too little leisure: give them a late schedule |
  | Orc | 18-10 | 8 h | 2 h | the whole working day is the wrong hours: a night schedule |

- **Energy** (person part `rest`, `colonist/Rest`, `ScheduleRules`): the needs pass runs each colonist's day hour by
  hour. Asleep refills `day_drain / sleep_hours` an hour (x`homeless_sleep` without a home); up drains `day_drain /
  (24 - sleep_hours)`, so keeping their race's hours never empties them (`day_drain` 70: lowest ~30 at bedtime).
  Any hours go to sleep under `any_sleep_below`, else leisure until the race's want is met, else work. Thoughts:
  `tired` (lowest < 25) or `exhausted` (< 10); `odd_hours` (1-3 h up outside their hours) or `wrong_hours` (4+);
  `enough_leisure` or `no_leisure`. Work output x work hours / `standard_work_hours` (10, capped at 1.25); hours
  worked while exhausted count 0.6. The day is memoised per (schedule, race, home, start energy) in a pass: everyone
  in the same case gives the same day, which keeps `SocietyTests` in budget.
- **Task queue** (`colonist_task/*.json`, `TaskDef`, `TaskRules`): sleep, eat, work, leisure, potter about. Each has an
  action, a place (home, workplace, food = nearest storehouse or kitchen, hall), a priority per block (missing = not
  in those hours), the need it answers with `want_below` (offered only then, except in `always_in` blocks) and
  `urgent_below` (jumps to `urgent_priority` 10), and requirements (`job`, `paid`, `works`, `food_place`, `home`).
  Sleep and eat have no Work-hours priority, so they never interrupt work; they only climb high when the need is low.
  `Colonists.taskState` works out energy and food at this hour; the first open task is what they do. Loaded bodies
  follow it (`Bodies`: `LotrNpc.TaskGoal` walks them to the place; sleepers with a home go indoors at their door).
  Unloaded colonies keep the daily calculation; the queue is only for bodies and the citizen page.

## Data

| Folder | Fields |
|---|---|
| `colonist_trait/` (17) | `kind` (positive, negative, neutral), `scale`, `weight`, `heritable`, `work_speed`, `need_rates`, `thoughts`, `on_break`, `conflicts` |
| `colonist_thought/` (25) | `name` (the page's English; lang `thought.<ns>.<id>` wins), `mood`, `duration_ticks` (0 = lasts while its cause does), `stack_limit` |
| `colonist_sleep/settings.json` | the standard schedule, `day_drain`, `homeless_sleep`, `any_sleep_below`, `tired_below`, `exhausted_below`, `exhausted_work`, `standard_work_hours`, `max_work_factor`, `odd_hours_from`, `wrong_hours_from`, the six thoughts |
| `colonist_task/` (5) | `action`, `place`, `priority` by block, `need`, `want_below`, `always_in`, `urgent_below`, `urgent_priority`, `requires` |
| `colonist_ration/` (3) | `food_rate`, `thought`, `order`: small (70%, -6), normal, large (140%, +4) |
| `colonist_race/` (5) | `rhythm`, `days_per_year`, `adult_age`, `extended_household`, `need_rates`, `settler_age_min`, `settler_age_max` |
| `colonist/settings.json` | `max_traits`, `extra_trait_chance`, the six home thoughts, `barracks_households` |

`ColonistData.loadProblems()` lists dangling ids (no GameTest asserts it yet).

## UI

The People tab (`colony/ui/ColonyViews`) has a Traits column and a Trait filter (traits present in the colony). The
filter box flows into extra columns when it would be taller than the screen. The citizen page (`colony/ui/
CitizenViews`, see `lotr/docs/ui-core.md`) shows needs, every mood part with days left, traits, family and the
orders, and renames.

## Not yet

Marriage and births (the `wedding`, `child_born`, `came_of_age` thoughts exist as data only), so every household is a
single settler until those land; migrants arriving as households; children's bodies and size; break behaviours
(`on_break` is read by `TraitRules.onBreak` but nothing acts on it); a family tree screen; more needs (rest, leisure: the page shows only what the colony tracks); per-colonist rules beyond
work, home and ration (drinking, curfew, which food); `saw_death`, `raid` and
`festival` memories (no events post them yet).
