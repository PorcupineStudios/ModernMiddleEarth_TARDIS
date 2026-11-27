# User stories (2026-11-27)

User stories for the work still to do, drawn from every plan and design doc in the repo: `docs/plans/*.md`,
`tardis/docs/`, `lotr/docs/` and `harness/docs/`. Only partial and not-started work is listed; what is already built
is left out (the docs themselves describe it, and some of it has never been tried in a game client). Each story says who wants what and why, when it counts as done, its size, and the doc it comes from,
so it can be filed as a ticket in the format `docs/plans/README.md` gives (`### Story`, `### Mod`, `### Done when`,
`### Size`, `### Plan it comes from`).

The plans that are still only on claude.ai (shared roadmap, plan review, TARDIS port plan and architecture, LOTR class
map, RingRealms colony design, rig proposal) could not be read, so they are not covered here. Stories drawn from them
may be missing.

**Status**:
- **Partial**: some of it is built; the story names what is left.
- **Open**: nothing is built yet.

**Who**:
- **Player**: anyone playing.
- **Crew**: a TARDIS's owner or crew.
- **Founder / steward**: runs a colony.
- **Ruler**: runs a kingdom.
- **Admin**: server operator.
- **Pack maker**: tunes the modpack through data, config and KubeJS.
- **Dev**: works on the mods.

LOTR's missing 1.7.10 features are already tickets: issues #14 to #72 in "Ring Bearer sprints", from
`docs/plans/lotr-feature-audit.md`. Section LT-P groups them as epics and doesn't file them again.

---

## Shared API (`api`)

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| API-1 | As a **dev**, I want every system to log its changes into one world history from day one, so that time travel, the TARDIS recorder and AI rulers can rebuild any past tick. | Each system that changes saved state writes deltas, and a GameTest rebuilds a past tick from them (borders do this today). | L | Partial (borders, wars and colonies log; not every system) | root CLAUDE.md, `borders.md` |
| API-3 | As a **crew member who travels in time**, I want the world to show me the moment I'm in, so that the past looks like the past. | A `TimeProvider` is registered, and `TimeShiftEvent` fires on arrival and return. The LOTR map then shows that tick's borders. | L | Open (the LOTR side listens already; nothing posts the events) | root CLAUDE.md, `console.md`, `borders.md` |
| API-4 | As an **admin**, I want to roll the world back to an earlier tick, so that a disaster or a story beat can be undone. | `HistoryRewindEvent` makes borders, conquest pressure, diplomacy and colonies all roll back to the same tick. | L | Partial (only borders roll back) | `borders.md` |

---

## TARDIS (`tardis`)

### Wreck, repairs and progression

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-3 | As **crew**, I want the repair core under the console to show the TARDIS's state with lamps, bars and screens, so that I can read its health at a glance. | Each slot in `core/layout.json` lights or fills from the record (upgrades installed, charge, hull). | M | Partial (the slots are blank insets) | `repair-loop.md` |
| TD-7 | As **crew**, I want my colony's researchers to speed up TARDIS research, so that the two mods help each other. | Researchers from the LOTR colony shorten a running node, and the screen shows how many are helping. | S | Partial (it works; the screen doesn't show it) | `repair-loop.md` |

### Console

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-14 | As **crew**, I want the room controls, sonic screwdriver holder, telepathic circuits, randomiser, stabilisers, helmic regulator and throttle to do what their tables say, so that every control is real. | Each control in `flight-controls.md` with "Does (planned)" has an action and a test. | L | Open (they still can't be unlocked: `not always`) | `flight-controls.md`, `repair-loop.md` |
| TD-15 | As **crew**, I want a room deleter (arm, then sneak-press), so that I can undo rooms. | Pressing it removes every added room after the two-step confirm. | S | Open | `flight-controls.md`, `rooms.md` |
| TD-16 | As **crew**, I want the flight-control button minigame from the classic set (press it in time or drift), so that coordinated flight takes skill. | During a jump the button rises and glows; missing it adds drift unless the stabilisers are on. | M | Open | `flight-controls.md` |

### Jumps, destinations and rooms

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-21 | As **crew in a Middle-earth world**, I want to jump to a plain vanilla overworld, so that "the other world" exists. | A jump to `lotr_port:vanilla_overworld` lands there. | S | Partial (the dimension exists; the jump is untested) | `destinations.md` |
| TD-22 | As a **pack maker**, I want the Nether and End milestones to have real requirements, so that players don't need `/tardis milestone`. | `vortex_drive` and `void_anchor` pass from play. | S | Open | `destinations.md` |
| TD-24 | As **crew**, I want real room builds instead of 9x5x9 placeholders, so that rooms are worth building. | Aaron's library and workshop templates replace the placeholders. | M | Open (waiting on Aaron's builds) | `rooms.md` |

### Doors

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-33 | As a **player**, I want IP to work alongside Sodium, so that I don't lose performance mods. | A Sodium-compatible IP fork is checked on 1.21.1 NeoForge with Create. | M | Open | `door.md` |

### The time vortex

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-44 | As a **pilot**, I want a progress bar, vortex sounds and more kinds of obstacle, so that the trip has feedback and variety. | A HUD bar shows progress; the vortex has sounds and at least one more obstacle kind. | M | Open | `vortex.md` |
| TD-45 | As **crew** who let the autopilot fly, I want it to dodge arcs and wormholes, so that an unflown trip isn't punished. | The autopilot steers around obstacles in a test. | S | Open | `vortex.md` |
| TD-46 | As a **pilot**, I want the stabilisers to soften arc and wall knocks too, so that the upgrade feels complete. | Knockback falls with stabiliser level. | S | Open | `vortex.md` |
| TD-47 | As a **player**, I want to know whether a damaged TARDIS can still hop, so that the rules are clear. | Aaron decides, and the tests match the decision. | S | Open (design question since 94bd29b3) | PR #73 |

### Free flight

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-53 | As **crew inside**, I want a stand-in at the console showing who is flying, so that I know someone is at the controls. | A stand-in entity stays at the console while the pilot flies. | S | Open | `free-flight-plan.md` |
| TD-54 | As a **pilot**, I want the box to keep its chunk loaded and land safely after a crash or restart, so that I never lose it. | A ticket keeps the body's chunk loaded, and a missing body on load lands at its last spot. | M | Partial (recovery on door-open exists; tickets don't) | `free-flight-plan.md`, `vortex.md` |
| TD-55 | As a **pilot**, I want hard hits and falls to damage a loose box, so that it's a physics object with risk. | A fall over the speed limit costs health through the damage code. | S | Open | `free-flight-plan.md` |
| TD-56 | As a **pilot**, I want to fly from the surface through the sky into space and on to another planet with no loading screen, so that the port plan's phase 6 is met. | A world check flies box and pilot from Middle-earth to the orbit layer, the solar system and another planet. | L | Open (slice 6) | `free-flight-plan.md` |
| TD-57 | As **crew inside**, I want the rotor to speed up and the room to shake in flight, with the monitor showing altitude and speed, so that the inside feels the flight. | All three happen while the box flies. | S | Open (slice 7) | `free-flight-plan.md` |
| TD-58 | As **crew**, I want to know whether others can ride on the outside, so that group flight is clear. | Aaron decides, then it is built or refused. | S | Open (question for Aaron) | `free-flight-plan.md` |

### Space, gravity and stations

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| TD-66 | As a **builder**, I want my ship to rise from an orbit layer into the solar system, so that stations can travel between planets. | A ship crosses between the layer and the system. | L | Open (Sable has no API to move a sub-level between dimensions) | `sable.md` |
| TD-67 | As a **player**, I want an atmosphere rim, a sharper local map when low and terrain relief on the globe, so that coming down looks right. | All three are drawn. | M | Open | `space.md` |
| TD-68 | As a **player**, I want to see the far side before I cross a layer boundary, so that no swap shows at all. | One of the three "truly seamless" options is picked and built. | L | Open | `solar-system.md` |

---

## LOTR port (`lotr`)

### Colony core and scale

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-3 | As a **dev**, I want CI to fail a build where `rules` imports Minecraft or `state` imports `sim`, so that the colony code stays easy to change. | A layer check runs in CI. | S | Open (planned rule 9) | `colony-core.md` |
| LT-4 | As a **pack maker**, I want every colony system to have a config switch, so that I can turn one off or replace it with another mod's. | Each registered system can be disabled in config. | M | Open (planned rule 7) | `colony-core.md` |

### Colony life

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-15 | As a **founder**, I want the tech tree drawn as a tree, so that I can plan routes through it. | The Research tab draws lanes, stages and links. | M | Open | `colonies.md` |
| LT-17 | As a **founder**, I want districts (Surveyor's Rod), thralls and handcarts, so that the old RingRealms features come back. | Each one exists with a test. | L | Open | `colonies.md` |

### Colonists

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-23 | As a **founder**, I want colonists to marry, have children who grow up in the world and move out, so that a colony lasts generations. | A wedding and a birth happen through play; children have bodies and come of age. | L | Partial (weddings and births exist as colony events; children's bodies don't) | `colony-core.md`, `colonists.md`, `lotr-feature-audit.md` |
| LT-24 | As a **founder**, I want migrants to arrive as households, accepted or turned away together, so that families join as one. | The customs counter shows a household as one arrival. | M | Open | `colony-core.md`, `colonists.md` |
| LT-25 | As a **founder**, I want unhappy colonists to break in character (brawl, drink, refuse work, wander off), so that low mood has visible results. | `on_break` acts in the world and on the page. | M | Open | `colonists.md` |
| LT-26 | As a **founder**, I want raids, deaths and festivals to leave memories, so that events change mood. | `saw_death`, `raid` and `festival` thoughts are posted. | S | Open | `colonists.md` |
| LT-27 | As a **founder**, I want per-colonist rules for drink, curfew and food, so that I can manage individuals. | The orders page has those rules and the day obeys them. | M | Open | `colonists.md` |

### Hired units

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-31 | As a **player**, I want a hired unit's own inventory screen, so that I can equip them. | The person screen shows and changes their gear. | S | Open | `lotr-feature-audit.md` |
| LT-32 | As a **player**, I want the command sword to mark its target, so that I can see who I sent them at. | A marker shows on the target. | S | Open | `lotr-feature-audit.md` |

### Multiplayer: access, map and stewards

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-43 | As a **founder**, I want to add a player by typing their name in the UI, so that I don't need commands. | The UI core has a text-input action used by Members and map visibility. | S | Open | `ui-core.md`, `colony-core.md` |
| LT-45 | As a **player**, I want watchtowers that show incoming armies and caravans within their radius, so that I can see threats coming. | A watchtower building reveals armies and caravans in range on the map. | M | Open (Aaron's decision of 2026-11-10) | `colony-core.md` |

### Kingdoms, borders and conquest

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-53 | As a **ruler**, I want a kingdom screen for name, colour, capital and subjects, plus rename and dissolve, so that I can run my kingdom without commands. | The screen exists and does all of these. | M | Open | `borders.md` |
| LT-54 | As a **ruler**, I want NPC factions to send invasions to retake squares and my hired units' kills to count, so that borders stay alive. | Invasions target taken squares, and hired-unit kills add pressure. | M | Open | `borders.md` |
| LT-55 | As a **ruler**, I want assaults near players to be fought with real bodies, so that battles are something to watch and join. | A loaded assault spawns soldiers who fight. | L | Open | `borders.md` |
| LT-56 | As a **pack maker**, I want kingdoms to have their own spawn races and colonies to spawn garrisons, so that held land looks held. | `TerritorySpawns` uses kingdom races and garrisons. | M | Open | `borders.md` |

### Diplomacy and war

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-62 | As a **ruler**, I want a war screen with marching armies on the map, so that I can follow a war. | The war screen exists and armies show on the map. | M | Open | `colonies.md` |
| LT-63 | As a **ruler**, I want morale, exhaustion raising wages and lowering happiness, and third realms intercepting armies, so that long wars hurt. | Each effect works in a test. | M | Open | `colonies.md` |
| LT-64 | As a **player besieging an AI colony**, I want defenders to spawn, so that sieging it is a fight. | A siege on an AI colony spawns its defenders. | M | Open | `colonies.md` |
| LT-65 | As a **ruler**, I want AI powers to make offers and declare war on me, and remember broken treaties, so that they act as rivals. | AI offers arrive in Incoming, and a broken treaty lowers their answers. | M | Open | `diplomacy.md` |
| LT-66 | As a **ruler**, I want a steward to decide diplomacy while I'm away, and tribute paid over time, so that a kingdom runs without me. | A council permission lets a steward act, and tribute pays per day. | M | Open | `diplomacy.md` |
| LT-67 | As a **ruler**, I want a won war to be able to hand over land, so that wars can be fought for territory alone. | A war goal whose peace moves squares. | S | Open | `borders.md` |
| LT-68 | As a **player**, I want a razed colony to be rebuildable cheaply by builder colonists from the recorded blocks, so that destruction isn't the end. | Builders rebuild from history for the cheaper materials. | L | Open (Aaron's decision of 2026-11-10) | `colony-core.md` |

### Caravans and roads

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-71 | As a **player**, I want caravans that travel the same way often to wear a trail and then a dirt path, so that the land shows where people go. | Caravan legs add wear and create trail stretches. | M | Open (step 2) | `docs/plans/roads.md` |
| LT-72 | As a **founder**, I want a Waymaker station where I draw a route and pick gravel, cobble or paved, which colonists then build, so that I can build real roads. | A drawn route becomes a stretch and a job that waymakers lay section by section. | L | Open (step 3) | `docs/plans/roads.md` |
| LT-73 | As a **founder**, I want tunnels at a fixed height through mountains, so that I can build a road like Moria's. | A stretch with a fixed y is dug 3 high and lined. | L | Open (step 4) | `docs/plans/roads.md` |
| LT-74 | As a **founder**, I want to know whether unused roads decay and whether AI colonies build roads, so that road rules are clear. | Aaron answers the plan's open questions. | S | Open (questions) | `docs/plans/roads.md` |

### Places: delves and the world

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-84 | As an **explorer**, I want the Watcher in the Water, light shafts to the mountainside and the Dimrill Stair waterfall, so that Moria has its famous moments. | Each one is built. | M | Open | `delves.md` |
| LT-85 | As an **explorer**, I want goblins to haul prisoners and work the scaffolding's lifts, so that Goblin Town feels alive. | Goblins do both near players. | M | Open | `delves.md` |

### Creatures, the Ring and the look

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-91 | As a **player**, I want those creatures to spawn naturally, spiders to climb walls, dragons to fly and mûmakil to be ridden, so that they're part of the world. | Spawn lists, wall climbing, flight and riding seats. | L | Open | `rig.md` |
| LT-93 | As a **player**, I want the One Ring to be found somewhere, so that it isn't only a creative item. | The ring has loot or a quest source. | S | Open | `ring-and-looks.md` |
| LT-94 | As a **player**, I want cloak and backpack slots, so that more gear has a place. | `curios/slots/cloak.json` and a back slot exist with tagged items. | S | Open | `ring-and-looks.md` |

### Management screens (UI core)

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-101 | As a **founder**, I want treasury history, warehouse views with taking items out, and per-colony actions from the all-colonies list, so that the planned screens are complete. | Each one is built on the UI core. | L | Open | `ui-core.md` |
| LT-102 | As a **player** using a keyboard or a screen reader, I want keyboard navigation and narration in the management screens, so that they're accessible. | Tab moves through the controls and narration reads them. | M | Open | `ui-core.md` |
| LT-103 | As a **player** in another language, I want lang files for the screens, so that they can be translated. | The screens' strings come from lang keys. | M | Open | `ui-core.md` |

### LOTR 1.7.10 parity (epics over issues #14 to #72)

| Id | Epic | Size | Status | From |
|---|---|---|---|---|
| LT-P1 | As an **explorer**, I want Utumno and the Elven and Morgul portals, so that LOTR's other dimension is back. | L | Open | `lotr-feature-audit.md` gap 1 |
| LT-P2 | As an **explorer**, I want the 21 structures that were never exported (Angmar and Mordor towers, Rivendell and High Elven halls...), so that the lands have their landmarks. `StructureExporter` needs the old base class. | M | Open | gap 2 |
| LT-P3 | As an **explorer**, I want Isengard's walls, the Rammas Echor, waypoint settlements, small dwarven mines, Taurëdain pyramids and more village layouts, so that famous places stand where they should. | L | Open | gap 3 |
| LT-P4 | As a **player**, I want custom shared waypoints, bounties, the factions screen and map, the Red Book tabs, title and shield screens, shields and capes on players, and achievement triggers, so that LOTR's meta game is complete. | L | Open | gap 4 |
| LT-P5 | As a **player**, I want travelling traders, spawn damping, wild NPC families, Gollum's fishing, the troll chieftain and Sauron, Gandalf's fireball, riding wargs and spiders, and spears and lances, so that NPC life matches LOTR. | L | Open | gap 5 |
| LT-P6 | As a **player**, I want spawners, dart traps, carved signs, Rhûn fire, the crystals, treasure piles, jars, the mechanism and rails, falling slabs, Dale crackers, LOTR fishing and the rideable barrel to behave as in LOTR, so that blocks do what they look like. | L | Open | gap 6, `behavior-audit.md` |
| LT-P7 | As a **player**, I want drunken speech, pouch restocking, glowing elven blades, animated armour, dyeing, alignment pop-ups, LOTR's particles, northern lights, sandstorms, Mordor ash and LOTR's day length, so that the polish matches. | L | Open | gap 7 |

### Modern Minecraft in Middle-earth

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| LT-110 | As a **player**, I want Corsair and Númenórean shipwrecks, drowned ruins and regional fishing loot, so that the coasts are worth exploring. | The coast structures and loot exist. | M | Open | `modernize.md` §1 |
| LT-111 | As a **player**, I want archaeology at barrows and Harad and Númenórean ruins, so that ruins hide treasure. | Suspicious sand and gravel with LOTR sherds. | M | Open (design) | `modernize.md` §5 |
| LT-112 | As a **player**, I want real faction shields, armour trims and smithing upgrades for LOTR armour, so that 1.9+ combat suits Middle-earth. | Each one is decided by Aaron and built. | M | Open (design) | `modernize.md` §6 |
| LT-113 | As a **player**, I want Mordor ash, Mirkwood spores, Lothlórien petals, water colours by region and snow by height, so that each land looks its own. | Biome particles, water colours and snow lines in the data. | M | Open | `modernize.md` §7 |
| LT-114 | As a **player**, I want copper and iron ore veins and lanterns, chains and smoker recipes in LOTR materials, so that modern blocks fit in. | Veins generate and the recipes exist. | M | Open | `modernize.md` §3, §5 |

---

## Harness (`harness`)

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| HN-4 | As a **player**, I want teams in formation, several animals on one hitch, so that big wagons need big teams. | Several animals on one hitch all pull at the same point. | M | Open | `harness.md` |
| HN-5 | As a **player**, I want sleigh runners that slide forward but not sideways, so that sleds work on snow. | Runners with anisotropic friction. | M | Open | `harness.md` |
| HN-6 | As a **player**, I want a howdah on a mûmak, so that a big creature can carry a platform. | A carry mode for the howdah. | L | Open | `harness.md` |
| HN-7 | As a **player riding a pulling horse**, I want no rubber-banding when I push past the hold-back, so that riding a team feels smooth. | Checked in a client with a rider. | S | Open | `harness.md` |

---

## Cross-cutting

| Id | Story | Done when | Size | Status | From |
|---|---|---|---|---|---|
| X-2 | As a **dev**, I want every phase's done-when to have a GameTest that runs in CI, so that nothing regresses unseen. | CI runs LOTR, TARDIS and harness GameTests and they are green on main. | M | Partial (main is red until PR #13 and PR #73 merge) | root CLAUDE.md |
| X-3 | As a **dev**, I want the many "untried in a client" features played through once, so that the docs can say they work. | An in-game pass covers the TD and LT rows marked "needs a look", and each doc records it. | L | Open | most docs |
