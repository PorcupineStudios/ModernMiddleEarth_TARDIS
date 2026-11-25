# LOTR Rewrite Pitches for RingRealms

Nov 3, 2026 · @Aaron

## Summary

The old LOTR mod and RingRealms model the same world twice. LOTR has NPCs, villages, factions and invasions that are mostly scenery: a village never grows, an NPC has no home or job, and an invasion is a spawn wave. RingRealms built a full simulation of people, buildings, stores and realms on top, but only for player colonies.

The pitch is to rewrite LOTR so **there is one engine for everyone**. Every NPC is a person with a record, a few AI rulers run real colonies like bots in a strategy game, and every LOTR faction is a realm. A player colony is the same thing with a player as owner. Then recruiting, quests, trade and war all work between any two colonies, player or NPC, with no special cases.

Every pitch below can be turned off or scaled down in data, so the pack can start close to the original and grow into the living world.

## Pitches

### 1. Every NPC is a person

LOTR's NPC base becomes RingRealms' citizen model. Each NPC carries a record: name, race, culture, personal alignment, traits, skills, home, job, family and loyalty. The entity is only a body for that record, as RingRealms colonists already are.

- A Rohan guard, a Bree innkeeper and your own colonist are the same kind of thing. Hiring, recruiting and capturing just move a record from one colony to another.
- NPCs remember you: a trader you cheated, a village you saved. Their opinion of you sits beside faction alignment.
- Records outlive bodies. An NPC who dies stays in history, which time travel can show.

### 2. AI rulers with their own colonies

Ordinary LOTR villages stay light structures with NPCs, as in the original. On top of them, a few **AI rulers** play the game like the bots in Empire Earth. Each runs a realm with a handful of real colonies on the same engine as players, with diplomacy and war.

- Sparse by design: a few AI colonies across the whole map (a config number), each cheap. Unloaded colonies tick once a day in the abstract, and bodies exist only near players.
- Each AI ruler has a faction, a home region, a personality (warlike, trader, builder) and a difficulty, all in data.
- They found colonies, grow, trade, sign treaties and declare wars, against each other and against players, under the same rules players follow.
- Their colonies can be destroyed for good, but the AI can found new ones.
- Players can conquer AI colonies and keep them.

### 3. LOTR factions are realms

Gondor, Mordor and the rest become NPC realms with the same structure as a player realm: a capital, colonies, a treasury, armies and diplomacy. A player realm is the same type with a player as ruler.

- Faction relations become live diplomatic states (war, pact, alliance, vassal) instead of a fixed table, so the world can shift: Rohan and Gondor can fall out if the player stirs it up.
- Pledging to a faction makes you its vassal in the same system RingRealms uses for vassal charters.
- Alignment grows from more than kills: trade, finishing quests for its colonies, defending its villages, paying tribute.

### 4. One quest engine for everything

LOTR's single-step mini-quests become one-stage quests in RingRealms' multi-stage engine. Quests are data: gates, ordered stages, objectives, deadlines, rewards and dialogue.

- Objective types are a registry other modules add to: `deliver`, `kill`, `visit`, `build`, `have`, `alignment`, `escort`, `wait`, plus Create objectives (spin up a machine) and TARDIS objectives (visit an era, repair a part).
- Givers: NPC colony residents, rare-race envoys, your own citizens (petitions), your liege (commissions) and other rulers.
- Quest chains and storylines: a village elder's errand leads to a warband, then a siege, then a rare ally.
- Generated quests come from what's happening: a starving NPC colony asks for grain, a colony at war asks for soldiers.

### 5. Colonist recruitment from the living world

With NPC colonies, recruitment gets many paths:

- **Migration** as now, plus migrants from nearby AI colonies when they thrive.
- **Hiring** in LOTR villages and AI colonies moves a person to you. An AI colony really loses them.
- **Refugees** arrive when a war razes a nearby AI colony, carrying their own records and grudges.
- **Captives and defectors** come from wars. Low-loyalty residents of an enemy colony may defect to a better ruler.
- **Rare races** use envoys and multi-stage quests, as in RingRealms' rare-races design.
- **Marriage and alliances** between realms send people both ways.

### 6. Rare races as data

Race becomes a datapack registry with flags such as `migrates: false`, a recruitment quest, an envoy spawn rule and a per-colony limit. Ents, trolls, Beornings, stone giants, wizards by order and fire-drakes all use the one NPC base with their own models and behaviour.

- Some rare races live in their own NPC colonies: an Ent moot in Fangorn, a Beorning homestead, a troll lair. Recruiting one means winning over that community.
- Goblins, half-trolls and the Taurethrim become ordinary base races with their own villages.

### 7. Colony-based wars

LOTR invasions are rewritten as campaigns between realms. An NPC realm musters real soldiers from its colonies, marches them with RingRealms' warning and march-time rules, and besieges a target colony.

- Losing soldiers costs that realm people it really had, so a war bleeds both sides.
- The conquest map shows who owns which colonies rather than an abstract strength number. A captured colony changes owner, its people may flee, stay or rebel.
- Players join as allies, vassals or mercenaries, defend NPC villages, raid supply caravans, or start wars of their own.
- RingRealms' offline protections stay: no building destruction while the owner is offline, capped loot, cooldowns.

### 8. A living Middle-earth

AI rulers think once per in-game day, in the abstract: grow, found colonies in empty land of their region, run caravans along LOTR's roads, trade, raid and make peace.

- The map changes over a long playthrough: an AI Mordor pushes into Ithilien, an AI Dale rebuilds, a ruined fort is resettled.
- Settings pick how many AI rulers there are, their difficulty and how fast they act. Zero AI rulers gives a world like the original.
- **Ages**: a world-level storyline (the War of the Ring, or none) moves through stages, unlocking events and armies, so the world has an arc.
- Named heroes and villains as rare people records, with deaths that change the world (no named canon characters required).

### 9. Hooks for the TARDIS and Create

- **Time travel**: colony state is event-sourced, so NPC colonies get pasts too. Visit a village before it was razed, or a fortress decades before you conquered it. Changing the past makes ripples in NPC colonies.
- **Crash sites** can land near an NPC colony that becomes your first contact, trader and source of first colonists.
- **Create**: Dwarf and Dale colonies use Create machines in higher tiers; NPC workshops can use real contraptions. Create recipes become colony production chains.
- **Colonists research** TARDIS tech (already planned) and NPC scholars can sell research.

### 10. Bigger ideas, any scope

- **Story engine.** Data-driven world events (a dragon awakens, a plague, a Shadow rises) with stages and choices, built on the quest engine.
- **Strategy map mode.** The LOTR map screen becomes a command view for realms: armies, caravans, diplomacy and colony overviews in one place.
- **Other ages of Middle-earth.** Second Age Númenor and First Age Beleriand as their own maps, reached by TARDIS almost exactly like the ancient-past trips.
- Kept for later, not picked yet: ruling dynasties and heirs, a world economy with real prices, news that travels along roads, roads that can be cut or guarded, ruins that can be resettled, and multiplayer politics (elections, councils, courts).

## Build order

The pitches stack: each needs the one before it. Fitting them into the port phases:

| Port phase | Adds | Pitches |
| --- | --- | --- |
| 4. Factions and alignment | Factions built as realms from the start, with live diplomatic states | 3 |
| 7. NPCs | The NPC base is the person record plus body; races as data with rare-race flags | 1, 6 |
| 8. Structures and spawning | The converter tags rooms; AI colonies and player colonies can use exported structures | 2 |
| 9. Map, waypoints and quests | The multi-stage quest engine replaces mini-quests | 4 |
| 10. Colonies and realms | Player colonies on the same engine; recruitment paths | 5 |
| New: 11. War and the living world | AI rulers, campaigns between realms, ages | 2, 7, 8 |
| Later | TARDIS and Create hooks, bigger ideas | 9, 10 |

The key choice is in phases 4 and 7: if factions and NPCs are built as realms and person records from day one, everything after is additions. Bolting it on later means rewriting both.

## Risks

- **Performance.** Thousands of NPC records and dozens of colonies must cost almost nothing when unloaded. Plan: abstract daily ticks for unloaded colonies, records stored compactly, bodies only near players, and a perf budget command from day one.
- **Save size.** Event-sourced history for every NPC colony grows forever. Plan: compact old history into snapshots, keeping only what time travel needs.
- **Fun versus simulation.** A living world can take away things players count on, like their favourite trader village. LOTR's own villages stay as they are, and the AI settings control how much changes.
- **Scope.** Pitches 1 to 5 are close to what RingRealms already does and fit the plan. Pitches 7, 8 and 10 are large and are best started after a playable port.

## Decisions

&#32;(read my response, all but #2)

&#32;(not sure what you mean)

&#32;(they can be destroyed, but the BOT AIs can make new colonies)

&#32;(yes, as well as sell their colonies/transfer ownership)

- **In the plan:** pitches 1 and 3 to 9, pitch 2 as sparse AI rulers, and three of the bigger ideas (story engine, strategy map, other ages).
- **AI colonies** can be destroyed for good, and AI rulers can found new ones.
- **Conquest:** players can conquer AI colonies and keep them. Players can also sell a colony or transfer it to another owner.
- **World mode:** replaced by the AI ruler settings (count, difficulty, pace).
