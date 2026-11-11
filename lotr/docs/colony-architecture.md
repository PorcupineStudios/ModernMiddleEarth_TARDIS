# Colony code architecture (groups, people, colonists)

Read when: you touch `group/`, `colonist/`, `colony/`, `npc/People` or `npc/Hiring`. Written 2026-11-10 for branch
`claude/colony-core` (commits `a5cd5a9d`, `fcc10257`), stacked on `claude/project-thread-3r3clf`.

Design and Aaron's decisions: https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N (read it with the Claude Docs tools).
This file is the code as it stands; the plan is where it is going.

## Status: unbuilt

Nothing here has been compiled against NeoForge. CI is offline and the cloud sandbox has no Maven. What was checked:

- `group/` and the plain rules in `colonist/` were compiled with `javac` against hand-written stand-ins for the few
  Minecraft classes they use, and their logic was run (a scratch check for `group/`, and `ColonistRulesTest`).
- Every edited file was parsed by `javac`; no syntax errors. Missing-symbol errors could not be told apart from real
  ones, so **type errors against the real APIs are possible everywhere outside the plain rules**.
- No GameTest has run. `ColonistRulesTest` has not run under real JUnit.

First thing to do on a machine that builds: `./gradlew :lotr:build`, fix what breaks, then `:lotr:test` and
`:lotr:runGameTestServer`. The likeliest breakages are listed under "Where the first build may fail".

## The idea in one paragraph

A colony and a player's band of hired units are the same kind of thing: a **group** that owns **people**. A group is
an id, a kind, an owner, a member list and a bag of **parts**. A person is a record in `People` plus their own bag of
parts. A feature (land, stores, orders, traits, needs...) is a part type plus rules, so features are added, changed
or removed without editing `Group`, `People` or each other. Game rules are plain Java that takes data in and returns
data out, so they are unit-tested without the game.

## Packages

| Package | Holds | May depend on |
|---|---|---|
| `group/` | `Group`, `Groups` (store), `Owner`, `Member`, ids, `PartType`, `Parts`, `PartBag` | NBT, Codec, `LotrPort` (ids, log) |
| `colonist/` rules | `TraitRules`, `NeedRules`, `MoodRules`, `KinRules`, `RaceRules` and their records | `java.util`, `ResourceLocation` only |
| `colonist/` glue | `ColonistData` (Codecs, loaders, part types), `Colonists` (hooks) | `group/`, `npc/People`, `colony/ColonyData` |
| `npc/` | `People` (person records + person parts), `Hiring`, `LotrNpc` | `group/` |
| `colony/` | `Colonies`, `Colony`, `Migrants`, `Recruiting`, `ColonyData`, `ColonyCommand` | `group/`, `colonist/`, `npc/` |

Keep the rules classes free of game classes. If a rule needs the world, pass the value in (see how `KinRules.household`
takes lookup functions).

## group/: the shared core

### Ids

- `GroupId(UUID)`, `PersonId(UUID)`: typed wrappers. Hold these across systems, never a `Group` object.
- **A colony's group has the colony's own UUID** (`GroupId.of(colony.id())`). There is no separate mapping.
- **A person's id is their NPC entity's UUID** for now, the same key `People` uses. `PersonId` exists so a person can
  later outlive or change bodies without touching callers.

### Group

Fields: `id`, `kind` (`ResourceLocation`), `owner`, `name`, `created` (game time), `members`
(`LinkedHashMap<PersonId, Member>`), `parts` (`PartBag`). Fields are package-private and mutable; **only `Groups`
writes them**. Public surface is read-only: `id()`, `kind()`, `owner()`, `name()`, `created()`, `members()` (a copy),
`size()`, `member(PersonId)`, `part(PartType<T>)`.

- `Owner(Kind kind, UUID id)`, `Kind` = `PLAYER`, `AI_RULER`, `REALM`. An AI ruler's UUID is `AiRulers.uuid(id)`.
- `Member(PersonId person, ResourceLocation role, long joined, Set<String> flags)`. Roles so far:
  `GroupKinds.ROLE_CITIZEN`, `ROLE_FOLLOWER`. Flag so far: `GroupKinds.FLAG_RARE` (joined by a recruitment quest).
- Kinds so far: `GroupKinds.COLONY`, `GroupKinds.COMPANY`. A new kind is a new constant; nothing else changes.

### Groups (SavedData `lotr_port_groups`, overworld storage)

The only writer of groups. It keeps two lookups in step with every change, so nothing scans all groups (the target is
50 colonies of 100+ people):

- `byOwner`: owner UUID → set of group ids. Read by `ownedBy(UUID owner, ResourceLocation kind)`, which returns
  oldest first (`created`, then id).
- `byPerson`: person → group id. Read by `groupOf(PersonId)`.

Methods: `get(server)`, `get(GroupId)`, `all()`, `ownedBy`, `ensure(id, kind, owner, name, now)` (returns the existing
group unchanged if the id exists), `company(player, now)`, `setOwner`, `rename`, `groupOf`, `join(group, person, role,
now, flags)`, `leave(person)`, `disband(id)`, `setPart`, `removePart`.

Invariants to keep:

- **A person is in at most one group.** `join` removes them from the group they were in. Garrisoning a hired unit in
  a colony is one `join`.
- Every method that changes anything calls `setDirty()`. If you add a mutator, do the same and update both lookups.
- `load` rebuilds both lookups from the saved groups; they are never saved.
- `company(player, now)` returns the player's first company or makes one. **One company per player is not decided**
  (Aaron, 2026-11-10); this method is the only place that assumes it. Do not copy the assumption elsewhere.

### Parts

- `PartType<T>(id, dataVersion, codec, migrate)`; make one with `PartType.of(id, version, codec)`. `migrate` is
  `(savedVersion, tag) -> tag in today's shape`, identity by default.
- `Parts.register(type)` once, at mod setup. Registering an id twice throws.
- `PartBag` holds the parts of one group or one person. `get`, `set`, `remove`, `isEmpty`, `save`, `load`.

Saved shape, per part: `"<type id>": { "dataVersion": <int>, "data": <the codec's NBT> }`.

A saved part goes to the bag's `unread` map, exactly as saved, and is written back untouched when:
its type is not registered, its codec fails to parse it, or its `dataVersion` is newer than the registered type's.
This is how removing a feature or a mod keeps its data. Two consequences:

- A part type must be registered **before** the SavedData holding it loads, or its data stays unread for that session
  (it is not lost, but `get` returns empty). `ColonistData`'s part types are static fields, forced to load by
  `ColonistData.register()` in `LotrPort`'s setup. Do the same for new part holders.
- `set` on a type removes any unread copy under that id.

No part types are registered on groups yet. Land, access, orders, stores and the rest come with their plan steps.

### Save format (`lotr_port_groups`)

```
{ dataVersion: 1,
  groups: [ { id: <uuid>, kind: "lotr_port:colony", owner: { kind: "player"|"ai_ruler"|"realm", id: <uuid> },
              name: "...", created: <long>,
              members: [ { person: <uuid>, role: "lotr_port:citizen", joined: <long>, flags: ["rare"] } ],
              parts: { "<type id>": { dataVersion: 1, data: ... } } } ] }
```

The envelope is hand-written NBT (like `People`); each part's `data` goes through its Codec with `NbtOps`.

## colony/: how colonies use groups

**Today a colony is mirrored into a group; it has not moved into one.** `Colony` still has its own `founder`, `ai`
and `citizens` list in its Codec, and `Colonies` (the only writer of those) copies each change into `Groups`:

| `Colonies` method | Mirror |
|---|---|
| `found`, `foundAi` | `groups.ensure(GroupId.of(colony.id), COLONY, owner, name, founded)` |
| `transfer` | `groups.setOwner(group, owner)` |
| `join` | `groups.join(group, person, ROLE_CITIZEN, joined, flags(rare))`, then `People.settled`, then `Colonists.settle` |
| a citizen dies | `Colonies.onDeath` removes them from `colony.citizens`; `LotrNpc.die` calls `Groups.leave` |

- `Colonies.get(server)` sets the private `groups` field on first use and runs `syncGroups()`, which gives every
  colony a group and every citizen a membership. That is the migration for worlds saved before groups existed.
- A `Colonies` made by `Colonies.load(...)` directly (tests do this) has `groups == null`. Every mirror call is
  guarded by `groups != null`, and `ownedBy` falls back to a scan. Keep that guard when you add mirror calls.
- `ownedBy(UUID)` reads the owner lookup. `ofFounder(UUID)` is now "the owner's oldest colony".
- **A player may own several colonies** (Aaron, 2026-11-10). `found` checks `max_colonies` in `colony/settings.json`
  (0 = no limit). Code that means "the colony this player is acting on" must use `Colonies.nearest(player)`: the
  nearest owned colony in the player's dimension, else the oldest. `ColonyCommand` and `Recruiting.interact` do.
  `Recruiting.killed` and `visited` loop over every owned colony. `researchers(player)` sums over all of them.

Known gaps in the mirror, to close when `Colony` moves fully into the group:

- Moving a citizen with `Groups.join` or `leave` alone does **not** touch `colony.citizens`. Until the move, change
  citizenship only through `Colonies`.
- Applicants (`Migrants`) are not members of anything; they become members in `Colonies.join` when accepted.
- A colony's name is copied to the group at founding and not kept in step after.
- `Colonies.transfer` has no "disband" path; nothing removes a colony's group yet because nothing removes colonies.

The intended end state: `Colony`'s remaining fields (hall, dimension, faction, recruits, applicants, lastDay,
virtualPopulation) become a `colony` part on the group; `Colony.Citizen`'s type and name live in `People`, `rare` is
the member flag; `Colonies` becomes a thin view. Do it after a first clean build, in one commit, with `ColonyTests`
green before and after.

## npc/: people

- `People` (SavedData `lotr_port_people`) is the person store: `Person(entity, type, name, faction, owner, since,
  alive)` keyed by entity UUID. Records are made by `hired(server, npc, owner)` (writes history) and
  `settled(server, npc)` (no owner, no history; the colony logs `citizen_arrive`). A random orc gets none.
  `died` keeps the record with `alive = false`.
- Person parts: `People.part(uuid, type)` and `setPart(uuid, type, value)`. `setPart` does nothing for someone with
  no record. Parts are saved inside the person's tag under `parts` (a `PartBag`).
- Which group a person is in is **not** in `People`; ask `Groups.groupOf`. `Person.owner` remains for hired units and
  is redundant with the company's owner; prefer the group.
- `Hiring.hire` records the unit, then `groups.join(groups.company(player, now), person, ROLE_FOLLOWER, now, Set.of())`.
  A mount hired alongside (a warg) gets `hireTo` but no record and no membership.
- `LotrNpc.die` calls `People.died` and `Groups.leave` for every NPC; both do nothing for an NPC with no record.

## colonist/: traits, needs, mood, kin

### Data (reloaded with /reload; folders under `data/<namespace>/`)

| Folder | Record | Fields (JSON keys) |
|---|---|---|
| `colonist_trait/` | `TraitDef` | `kind` (positive, negative, neutral), `scale`?, `weight` (10), `heritable` (0), `work_speed` (1), `need_rates` {need: mult}, `thoughts` [ids], `on_break`?, `conflicts` [trait ids] |
| `colonist_need/` | `NeedDef` | `fall_per_day`, `start` (80), `levels` [{`below`, `thought`}] |
| `colonist_thought/` | `ThoughtDef` | `mood`, `duration_ticks` (0), `stack_limit` (1) |
| `colonist_race/` | `RaceDef` | `match` [words], `days_per_year` (1), `adult_age` (18), `extended_household` (false), `need_rates`, `settler_age_min`, `settler_age_max` |
| `colonist/settings.json` | `ColonistSettings` | `max_traits`, `extra_trait_chance`, `base_mood`, `major_break`, `minor_break`, `unhappy`, `inspired`, `default_race`, `own_home`, `shared_home`, `barracks`, `homeless`, `barracks_households` |

Read them through `ColonistData.traits()`, `needs()`, `thoughts()`, `races()`, `settings()`. Those maps come from
`Map.copyOf`, so **their order is not stable**: any rule whose result depends on order must sort by id first
(`TraitRules.pick` and `RaceRules.raceOf` do). `ColonistData.loadProblems()` lists load errors and every id that
points at nothing; `ColonistTests.colonistDataLoads` asserts it is empty.

### Person parts (registered in `ColonistData`)

| Part id | Type | Meaning |
|---|---|---|
| `lotr_port:traits` | `List<ResourceLocation>` | Traits, for life |
| `lotr_port:needs` | `Needs(Map<need, Bar(value, tick)>)` | Each bar's value at the tick it was last set |
| `lotr_port:mood` | `Mood(List<Memory(thought, since, stacks)>)` | Remembered thoughts that still count |
| `lotr_port:kin` | `Kin(race, born, parents, spouse, children)` | Family tree links (person UUIDs) and birth tick |

### Rules (all static, all plain)

**`TraitRules`**
- `roll(defs, random, settings)`: one positive, one negative, then more while `random.nextDouble() <
  extraTraitChance`, up to `maxTraits`. `fits` forbids repeats, two traits on one `scale`, and `conflicts` in either
  direction. Weight 0 traits are never rolled.
- `inherit(defs, parents, random, settings)`: each parent trait passes with its `heritable` chance if it fits, then
  the same fill as `roll`.
- `workSpeed`, `needRate(defs, traits, need)` multiply. `thoughts` are the traits' standing thoughts. `onBreak` is the
  first trait's `on_break`, else the fallback string. Break behaviours are just strings so far (`idle`, `binge`,
  `brawl`, `wander`, `smash`); nothing acts on them.
- Rolls must be **deterministic** so pasts rebuild exactly. `Colonists.settle` seeds `Random` from the NPC's UUID
  (`most ^ least`). Never use an unseeded random or `level.random` in a rule.

**`NeedRules`**
- A bar is `(value, tick)`. `value(needs, need, def, rate, now, dayTicks)` = `clamp(value - fallPerDay * rate *
  days elapsed)`. Nothing ticks per colonist; cost is paid only when someone looks.
- `change(..., amount)` reads the value now, adds, and stores it at `now`. Use it for a meal or a night's sleep.
- `thought(def, value)`: of the levels the value is under, the one with the lowest `below`.
- `rate` is race × traits; `Colonists.needRates(server, person)` computes it.

**`MoodRules`**
- Two kinds of thought. A thought with `duration_ticks > 0` is **remembered**: `remember` stores it with `since =
  now`, stacking up to `stack_limit`; it stops counting `duration_ticks` after `since`. A thought with duration 0 is
  **standing**: it is never stored, and callers pass the standing thoughts that hold right now.
- `score(settings, defs, mood, standing, now)` = `base_mood` + Σ live memories (`mood × stacks`) + each distinct
  standing thought once, clamped 0–100.
- `state(settings, score)`: `< major_break` → `MAJOR_BREAK`, `< minor_break` → `MINOR_BREAK`, `< unhappy` →
  `UNHAPPY`, `>= inspired` → `INSPIRED`, else `CONTENT`.
- `forget(mood, defs, now)` drops faded memories; call it before saving a mood you changed, or on the day step.

**`KinRules`**
- `age(kin, race, now, dayTicks)` = colony days since `born` ÷ `days_per_year`. Men: 1 day = 1 year, adult at 18
  (Aaron, 2026-11-10). `bornFor(years, race, now, dayTicks)` is the inverse, used for settlers who arrive grown
  (their `born` can be before the world began; that is fine).
- `household(person, kinLookup, raceLookup, now, dayTicks)` returns a UUID that names the person's household; two
  people are one household exactly when it is equal. `kinLookup` must return a value **only for people who are alive
  and live in this colony**, so the dead and the departed don't hold households together. Walk:
  1. A minor goes to a present parent (lowest UUID if both).
  2. An adult of a race with `extended_household` also goes to a present parent, or failing that to a present
     parent of their spouse (someone who married in).
  3. Otherwise the household is the couple: the lower UUID of the person and their present spouse.
  The walk repeats from the parent, at most 16 steps.
- `homeThought(settings, myHousehold, housemates)`: `housemates` is the household id of everyone sleeping in the same
  house, the colonist included. Empty → `homeless`. Only own household → `own_home`. Otherwise `shared_home`, or
  `barracks` at `barracks_households` or more *other* households. This is the "families are not a dormitory" rule.

**`RaceRules.raceOf(races, npcType, fallback)`**: the first race, in id order, one of whose `match` words is contained
in the NPC type's path. NPC types have no race field, hence the word match. `man` has no words and is the default.

### Glue: `Colonists`

- `settle(server, npc)`: once per person (skips if they already have traits or have no `People` record). Sets
  traits, needs at their start values, an empty mood, and kin (race from the NPC type, a random settler age).
- `needRates`, `standingThoughts` (trait thoughts + need-level thoughts), `mood` (the score), `remember`.
- `summary` is the " [strong, lazy]" suffix `/lotr_colony` prints. It leaves mood out on purpose: nothing fills
  needs yet, so bars only fall and the number would mislead.

### Not built (the plan has the design)

Filling needs (food, sleep, tavern); applying mood (work speed, breaks, leaving, the rare killing); houses per
household, so `homeThought` has no caller yet (the port only counts beds near the Hall); marriage, births, children's
bodies, coming of age in the world (`came_of_age`, `wants_own_home` thoughts exist as data only); the family tree and
needs screens; job interests (no jobs in the port yet); the colony day step for any of this.

## How to add things

- **A trait, need, thought or race:** add a JSON file. No Java. Run `colonistDataLoads` to catch dangling ids.
- **A part on people:** a record, a Codec and a `PartType` in the feature's data class (like `ColonistData`), forced
  to load at setup; read and write with `People.part` / `setPart`.
- **A part on groups:** the same, with `Groups.setPart(group, type, value)` and `group.part(type)`.
- **A rule:** a static method in a plain class that takes definitions and values and returns a value. Add a JUnit
  case in `lotr/src/test/java`. Keep game classes out.
- **A new kind of group** (army, camp, garrison): a `GroupKinds` constant, `Groups.ensure(...)`, and parts for what
  it needs.
- **Changing a saved shape:** bump that part's `dataVersion` and give its `PartType` a `migrate` function. Never
  change what an old version's data means in place.

## Tests

| Test | Kind | Covers |
|---|---|---|
| `ColonistRulesTest` | JUnit (`:lotr:test`) | Traits, needs, mood, ages, households, home thoughts on made-up data |
| `GroupTests` | GameTest | Several groups per owner, moving a person, handover, part round trip, unread part kept |
| `ColonistTests` | GameTest | Data loads with no dangling ids; a settler gets parts and they survive saving |
| `ColonyTests` (edited) | GameTest | The colony's group mirrors its citizens; a second colony is allowed |
| `NpcTests` (edited) | GameTest | A hired unit is in its owner's company; a random orc is in no group |

`GroupTests` registers a part type `lotr_port:test_part` in a static field. It stays registered for the session.

## Where the first build may fail

Written from memory of the 1.21.1 APIs, mirrored from existing code where possible. Check these first:

- `DataResult.getOrThrow()` with no argument and `resultOrPartial(Consumer<String>)` (`PartBag`).
- `CompoundTag.getAllKeys()`, `getList(key, Tag.TAG_STRING)`, `StringTag.valueOf` (`PartBag`, `Member`).
- Generic capture in `PartBag.load` (`type.flatMap(p -> loadPart(p, part))` with `PartType<?>`).
- `UUIDUtil.CODEC.optionalFieldOf("spouse")` and `Codec.unboundedMap(ResourceLocation.CODEC, Codec.DOUBLE)` in
  `ColonistData`; `ColonistSettings`' Codec has 13 fields (the limit is 16).
- `Optional.or`, `Comparator.comparingDouble((Colony c) -> ...)` in `Colonies.nearest`.
- `Component.empty()` returned as `Component`, `MutableComponent.append(String)` in `Colonists.summary`.
- `ColonyTests`: the second-colony check now runs last, after the player was teleported to Weathertop.

## Next steps, in the plan's order

1. First real build and test run; fix what the list above turns up.
2. Move `Colony` fully into its group (see "The intended end state").
3. Commands as the one way to change state, and a CI check on package dependencies.
4. Access roles and the land lookup by chunk.
5. Simulation levels (live, mirrored, counted) and the scheduler with a time budget.
6. Then needs filling and mood effects, houses per household, and families in the world.
