# LOTR Port: Colony Core, Hired Units and Multiplayer Plan

Written 2026-11-10. Copied into the repo 2026-11-20 from https://claude.ai/artifact/LZs7i9p8w6MaN7pdLV2K5N; this file is now the one to edit.

## Summary

Colonies and hired units get one shared core, sized for 50 colonies of 100+ colonists, and LOTR's multiplayer features are rebuilt around colonies. Nothing here is built yet; it is a plan for Aaron to correct.

Three decisions from Aaron (2026-11-10) drive it:

- Hired units use a code structure that RingRealms colonies use too.
- The colony core must be easy to refactor, extend or replace, and carry 30 to 50 colonies with 100+ colonists each.
- Multiplayer systems are designed with colonies in mind: a map visibility screen, colony managers set at the Hall, banners and land protection tied to colonies, and a colony war system in place of conquest.

**Where it fits.** It covers Tier 4 tasks 45 (invasions and conquest), 46 (banners and land protection), 47 (fellowships), 48 (hired-unit depth) and 54 (map and waypoints) of `lotr/docs/missing-features.md`, and phases 9 and 10 of the port plan. I found no existing doc with a section numbered "9. Multiplayer systems", so the multiplayer notes are a new section here.

**Where the code is today** (from `lotr/CLAUDE.md`, branch `claude/project-thread-3r3clf`): hired units and colonists are separate paths. `npc/Hiring` and `npc/People` keep records for hired units only. `colony/Colonies` holds colonies, one per player, with citizens marked by an entity attachment. AI colonies are a counted population with no bodies.

## Targets

The design target is 50 colonies with 120 colonists each: 6,000 saved people, plus hired units. The numbers below are proposed budgets to measure against, not measured results.

| What | Proposed budget |
| --- | --- |
| Saved people the core must hold | 10,000 |
| Colonies simulated at once | 50 |
| Server time for all colony work | 2 ms per tick average, 5 ms worst |
| Work done per person per tick outside loaded chunks | none |
| Land lookup for a block break or place | one map lookup by chunk |
| Save | one file per colony, written only when changed |
| Sent to a client | a short summary per visible colony; details only while a screen is open |

A fixed-seed benchmark world with 50 colonies of 120 runs in CI, and a perf command reports time per system. A change that breaks the budget fails the build.

## Core structure

A colony and a player's band of hired units are the same kind of thing: a **group** that owns people. Everything else is a part added to a group.

**Person.** One saved record per person: id, name, race, traits, skills, gear, the group they belong to and their role in it. The record lives in a person store and does not need an entity. The entity (`LotrNpc`) is a body that points at a person id while its chunk is loaded.

**Group.** Id, owner (a player, a realm or an AI ruler), member ids and a set of parts. `Group` is a working name.

**Parts.** Each part is a small data holder with its own Codec and data version, registered by id. A group has only the parts it needs:

| Group | Parts |
| --- | --- |
| Colony | access, land, buildings, stores, treasury, jobs, research, military |
| Company (hired units) | access, orders, upkeep, squads |
| Army (mustered for a war) | orders, supply, squads |
| Camp (a banner claim with no colonists) | access, land |

A new feature is a new part plus a new system. The `Group` class does not change. A part the game no longer knows is kept as raw data on load, so removing a feature never wipes a save.

**Systems.** One stateless system per part runs in a fixed pipeline, as RingRealms already does in `colony/sys`. Systems talk through events, never by calling each other.

**Roles.** A person's role (farmer, smith, guard, soldier, follower) is a data file that names the work and the entity goals. A hired warrior and a colony guard are both `soldier`; only their group differs.

**Commands.** Every change goes through one command (for example `SetManager`, `Hire`, `Muster`). The command checks access, applies the change, writes the history log, marks the group changed and syncs clients. Screens, chat commands, KubeJS and AI rulers all call the same commands.

**Layers.** Code depends downward only, and CI checks it.

| Layer | Holds | May use |
| --- | --- | --- |
| `def` | Data definitions: roles, jobs, buildings, permissions, war rules | nothing |
| `state` | Person, Group, parts, Codecs, migrations | `def` |
| `rules` | Plain logic with no Minecraft classes, tested with JUnit | `def`, `state` |
| `sim` | Systems, the scheduler, commands, history | the three above |
| `body` | Entities, goals, pathfinding | `sim` and above |
| `net`, `client` | Payloads and screens | read-only views from `sim` |

## Hired units

Each player gets a Company group, and hiring a unit adds a person to it. `Hiring.hire` becomes the `Hire` command; `People` becomes the shared person store.

LOTR's hired-unit features (task 48) map onto the shared core like this:

| LOTR feature | In the shared core |
| --- | --- |
| Follow, guard, stay | The `orders` part, also used by colony guards and armies |
| Squadrons, horn of command | The `squads` part: named sub-lists that orders can target |
| Levelling and XP | Person skills, the same ones colonists use for jobs |
| Hired-unit inventory screen | The person screen, shared with the colonist screen |
| Hired farmers | A person with the `farmer` role and a work area; the same job code as a colony farmer |
| Mercenaries | Membership with an end date |
| Wages | The `upkeep` part, using the colony treasury's wage rules |
| Unit trade screen | A screen over the `Hire` command, replacing the chat list |

Because both are groups, moving people is one command:

- Garrison a hired unit in a colony: the person changes group and keeps their role.
- Muster colonists for a campaign: soldiers move from the colony to an Army group and return after.
- A colony barracks trains soldiers who can be handed to a player's Company.

Units with no record today (wild orcs, bandits) stay light and get a person record only when they join a group.

## Scaling

Only colonies near a player run in full; the rest run as numbers. Each colony is at one of three levels, and moves between them as chunks load and unload.

| Level | When | What runs |
| --- | --- | --- |
| Live | Its chunks are loaded | Bodies, pathfinding, jobs done in person |
| Mirrored | Unloaded, has person records | No bodies. Production, needs, wages and taxes worked out per colony in steps, as RingRealms' storehouse mirror does today |
| Counted | AI colony nobody has visited | A population number and strength, as `Colony.ai` does today. People get records when a player arrives or attacks |

Rules that keep it inside the budget:

- **A scheduler with a time budget.** Systems ask for a slice. Mirrored colonies take turns, a few per tick, so 50 colonies never step on the same tick.
- **No scans.** Land is found by chunk, people by id, group members by list. No event handler loops over every colony.
- **Path budget.** Live colonies share a cap on path searches per tick and reuse paths between common points (home, work, stores).
- **Body cap.** A config value limits bodies per live colony; people over the cap count as indoors and still work as numbers. See the open questions.
- **Storage.** One file per colony with its people inside, plus a small index. Only changed colonies are written.
- **Sync.** The map gets id, name, position, owner and tier per visible colony. Full state goes only to a player with that colony's screen open.

## Rules that keep the code easy to change

These nine rules are what let a system be refactored, extended or swapped without touching the others.

1. **Ids, not references.** Systems hold `PersonId`, `GroupId` and `ColonyId`, never another system's objects.
2. **Parts own their data.** No part reads another part's fields; it asks through a small interface or listens for an event.
3. **Rules are plain Java.** Game rules take data in and return data out, with no Minecraft classes, so they run in JUnit.
4. **One command per change.** No screen, packet or AI writes state directly.
5. **Everything tunable is data.** Roles, jobs, buildings, permissions and war rules load through reload listeners.
6. **Every part has a data version and migrations.** Unknown parts are kept, not dropped.
7. **Every system has a config switch.** A system can be turned off or replaced by another mod's.
8. **No static state.** One colony service per server, reached from the level.
9. **CI checks the layers.** A build fails if `rules` imports Minecraft or `state` imports `sim`.

## Multiplayer systems

One access system answers every "may this player do that here" question, and the map screen, the Hall, banners and wars all use it.

### Access

The `access` part of a group lists who holds which role. A role is a data file naming its permissions.

| Role | Default permissions |
| --- | --- |
| Founder | Everything, including naming stewards and handing over the colony |
| Steward | Manage workers, orders, stores and buildings; spend from the treasury |
| Member | Build on the land, open shared containers, deposit through the Ledger |
| Ally | Enter, trade, use doors and gates |
| Visitor | Enter only |

A role can be given to a player, a fellowship, a realm or everyone. Fellowships (task 47) stay as LOTR's player groups and become the easy way to grant access to several players at once. Stewards may spend from the treasury (Aaron, 2026-11-10); this replaces the 2026-11-01 rule that only the Founder opens coffers.

### Map visibility screen

A screen opened from the map sets who sees your icon: nobody, chosen players, chosen fellowships, your realm, allies or everyone. The server sends your position only to players on that list, so a modified client cannot reveal hidden players. It replaces the per-fellowship map toggle in LOTR.

### Managers at the Hall

The Hall screen gets a Stewards page. The Founder adds a player or fellowship and picks a role. Stewards can run the colony while the Founder is offline, and every change they make is written to the colony's history with their name.

### Banners and land protection

Land belongs to a group through its `land` part, and protection asks the access system. LOTR's banner whitelist screen goes away; the colony's roles replace it.

- **Inside a colony's reach**, a banner extends or marks the border, up to a limit set by the colony's tier.
- **Outside any colony**, a banner founds a Camp: a small claim with access roles and no colonists. This keeps LOTR's use of banners for a personal base.
- **Protection flags** per claim are data: break, place, containers, explosions, fire, harming colonists.
- **At war**, a siege gives attackers limited rights on the land under siege, set by the war rules.

### Colony wars instead of conquest

LOTR's conquest grid and `/conquest` are not ported. Territory comes from colony land, and it changes hands through war.

1. **Declare.** A realm declares war on another realm; faction relations and alignment set who may.
2. **Muster.** Soldiers from colonies and hired Companies join an Army group.
3. **March.** The defender gets a warning with the arrival time.
4. **Siege.** The attacker must take the Hall. A loaded siege is fought in person; an unloaded one is resolved from both sides' strength with a seeded roll, as AI sieges are today.
5. **Outcome.** Tribute, vassalage, handover of the colony, or razing.

RingRealms' offline protections apply to player colonies. Every step is written to the history log so time travel can replay it. LOTR invasions become raids run by AI rulers through the same steps. The map's conquest overlay becomes a realm borders overlay. The 30 skipped bounty quests can later target players of a realm you are at war with.

## Build order

The core goes in first, before any new hired-unit or multiplayer feature, because moving them later is the expensive refactor. Each step is one branch with a GameTest for its done-when.

1. **Core.** Ids, Group, the part registry and the person store. Move `Colonies` and `People` onto it. Done when: existing GameTests pass, and a hired unit and a citizen are both group members that survive a restart.
2. **Commands and layers.** Route existing changes through commands and add the CI layer check. Done when: no screen or packet handler writes state directly.
3. **Access and land index.** Done when: a visitor cannot break a block on colony land, a member can, and the lookup is one map read.
4. **Simulation levels and scheduler.** Done when: the 50 by 120 benchmark world stays inside the tick budget in CI.
5. **Hired units as Companies.** Orders, squads and the shared person screen. Done when: a hired unit is garrisoned in a colony and taken back out with its skills and gear.
6. **Stewards page and map visibility screen.** Done when: a steward manages a colony whose founder is offline, and a hidden player's position never reaches another client.
7. **Banners and camps.** Done when: a banner extends a colony border and a banner in the wild founds a camp.
8. **Colony wars.** Done when: a player colony and an AI colony fight a war to an outcome, and replaying the history log gives the same result.

## Colonists: traits, needs, mood and families

Every colonist gets traits, a set of needs, a mood made of thoughts, and a place in a family tree (Aaron, 2026-11-10). Nothing in this section is built.

All four are parts on the person, with their own Codec and data version, the same way groups carry parts. Step 1 only has parts on groups, so person parts are new work. RingRealms on 1.7.10 has a fixed trait list and one happiness number; this replaces both.

### Traits

Traits are data files, in the style of Oxygen Not Included and RimWorld.

- **Good and bad.** Each colonist rolls at least one positive and one negative trait, weighted by race and culture.
- **Effects.** A trait changes skills, work speed, how fast a need falls, or which thoughts a colonist gets or ignores.
- **Degrees.** Some traits sit on one scale, such as Industrious, Hard-working, Lazy, Slothful, and a colonist has one point on it.
- **Interests.** A colonist learns faster in jobs they are interested in and is unhappy in jobs they dislike.
- **Breaking point.** One trait says what a colonist does when mood collapses: brawl, drink the tavern dry, refuse to work, wander off, or smash things.
- **Inherited.** Each trait has a chance to pass to children.

RingRealms' seven traits (Strong, Lazy, Greedy, Devout, Xenophobe, Cosmopolitan, Drunkard) become the first trait files.

### Needs

Each need is a bar from 0 to 100 that falls at a rate set per race in data.

| Need | Filled by |
| --- | --- |
| Food | Meals; variety and race taste count |
| Rest | Sleep in a bed |
| Home | Quality of their house and whether it suits their culture |
| Safety | Guards, walls, no recent raids or deaths |
| Leisure | Tavern, festivals, pipe-weed, music |
| Company | Time with family and friends |
| Race needs | Starlight and trees for elves, stone halls for dwarves, many meals for hobbits, fighting for orcs |

A need stores its value and the tick it was last set, and the current value is worked out when read. So 6,000 colonists cost nothing per tick. In a mirrored colony the colony step fills needs from the stores.

### Mood

Mood is the sum of a colonist's thoughts, as in RimWorld. A thought is a data file: a mood value, how long it lasts, how many times it stacks, and what causes it.

- **Sources:** need levels, events (a wedding, a death, a raid), housing, family, grudges, traits, wages and edicts.
- **Expectations:** a small outpost expects little and a great city expects much, so the same hut pleases one and insults the other.
- **Low mood:** unhappy, then a minor break, then a major break, acted out by the colonist's breaking-point trait. In a major break a colonist can leave the colony for good. A killing between rivals is possible but very rare.
- **High mood:** an inspired spell with a work bonus.
- **Shown to the player:** the colonist screen lists every need bar and every thought with its number.

Mood feeds productivity and migration. Loyalty to the ruler stays a separate number.

### Families and homes

Every person records their parents, spouse and children, and the colony groups kin into households.

- **Family tree.** A screen opened from the colonist screen. The dead stay in it, since person records are kept after death.
- **Household.** A couple and their children who have not come of age. Who else stays is set per race: hobbits keep grandparents and grown kin under one roof, while most races live only with a spouse and their own children. A household is given a house as a unit, held in a `households` part on the colony.
- **No dormitory penalty for family.** The shared-room and barracks thoughts count only people outside the household. A child's bed in the parents' house is a proper home.
- **Families want their own house.** One household alone in a house gets a strong positive thought. Sharing a house with another household is negative. A family in a barracks is strongly negative.
- **Coming of age.** Children have bodies in the world. One Minecraft day is one year of age, so a child of Men comes of age after 18 days; other races age at their own speed, set in data. An adult can take a job and gains a growing wish for a home of their own. They move out when a free home exists.
- **Marriage.** A new couple forms a new household, which wants its own house.
- **Migration.** Arrivals come as households and are accepted or turned away together at the Hall.

Births, ageing and coming of age run on the colony day step, so they also happen in mirrored colonies.

### Where it goes in the build order

After step 4 (simulation levels), in three steps: person parts and traits; needs and mood; families and households. The `rare` flag and RingRealms' grudges move onto the same parts.

## Decisions

Aaron's answers of 2026-11-10, which override anything above that disagrees.

- **Colonies per player.** A player may own several. AI colonies must cost less to run than player colonies.
- **Outposts.** Small colonies can serve as outposts.
- **Watchtowers.** A watchtower building has a radius in which it sees incoming armies and other factions' trade caravans. Enemy armies within a few thousand blocks are always visible to the player under attack.
- **Map visibility.** The screen covers colony icons as well as the player icon, and visibility can be switched on for allies.
- **Bodies.** Unobserved colonists may be culled past a threshold, as long as immersion is never broken.
- **Camps.** Personal camps from banners outside a colony are allowed.
- **Stewards.** They may spend from the treasury.
- **Razing.** A player colony can be razed. Sieges are destructive. The time-travel record of block changes keeps what the colony had built, so Construction Worker colonists can rebuild it, for cheaper materials than the blocks themselves (for example one brick item per brick-type block, one ingot per metal block).
- **Name.** `Group` stays.
- **Companies.** One per player or several is undecided; the code keeps that choice in one method.

## Status

All work is on branch `claude/ui-core` of the public repo ModernMiddleEarth\_TARDIS (moved from TARDIS-RingBearer on 2026-11-11), built on the society core in `claude/project-thread-3r3clf`. It is green in CI: build, unit tests, all 84 LOTR GameTests including the 50-colony budget test, the TARDIS tests and both world checks. The UI screen has not been looked at in game yet.

- **Core:** the society core on `claude/project-thread-3r3clf` is the colony core (Aaron, 2026-11-11). It already covers most of steps 1 to 8 above. The earlier `group/` core on `claude/colony-core` is dropped, and that branch is marked superseded.
- **UI core:** server-built views drawn by one client screen. The colony view (Overview, Buildings, People, Stock, Members) and the all-colonies list are done; see `lotr/docs/ui-core.md`.
- **Colonists:** traits, mood memories and kin are person components. Mood adds to the colony's happiness target, traits scale work and food, and households are housed together with families placed first; see `lotr/docs/colonists.md`.
- **Changed from this plan:** happiness is the one mood number. The separate need bars (rest, leisure, company, safety) were dropped, since nothing filled them.
- **Not yet:** marriage, births, children's bodies, migrants arriving as families, break behaviours, the family tree screen, and text input in the UI (adding a player by name).
