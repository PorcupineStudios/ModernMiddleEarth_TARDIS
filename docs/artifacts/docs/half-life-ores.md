# Half-life ores: design proposal

Oct 25, 2026 · @Aaron

## Core idea: yield is a pure function of time

Every half-life ore block is worth an amount of energy that depends only on the time it is harvested at, never on when its chunk was generated. Nothing ticks: the server computes the value at the moment of extraction, so it costs nothing to have millions of these blocks in the world.

```latex
E(t) = E_{floor} + (E_{birth} - E_{floor}) \cdot 2^{-t / T_{half}}
```

- **t** = world age in ticks, the same clock the history recorder uses (`totalWorldTime`, which only runs while the server is up). t = 0 is world creation. In a past trip, t is the trip's playhead, so the same block is worth more there.
- **E\_floor** = the present-day trickle (1-5 per block) so ores never become worthless. Without it, exponential decay hits 0 within a day of uptime.
- **Before creation (t < 0)** the curve keeps climbing, but on its own slower half-life (`prehistoryHalfLife`) and up to a cap (`deepTimeCap`, 10x the birth value by default). That makes the "far distant past" a real distance away (default: the cap is reached 100 Minecraft days before creation) rather than a few hours.
- Ores generated in brand-new chunks late in the game are just as decayed as old ones. The element is as old as the universe, not the chunk, so exploring can't dodge the decay; travelling back in time can.

## Ore tiers

Four tiers, each with its own energy type and decay speed, so they reward different play: tier 1 rewards a fresh world, tier 2 the first day, tier 3 and 4 only time travel. All numbers are per block and configurable per tier.

| Tier | Ore (energy) | Present floor | At creation | After 6 h uptime | Half-life | Far past cap | Where |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | Pitchblende (Radiation) | 2 | 200 | \~5 | 1 h | 2,000 | Overworld y 5-50, ME in most rock types; iron-like rarity |
| 2 | Chronium (Chronal) | 3 | 500 | \~65 | 2 h | 5,000 | Overworld y 5-30, ME mountains (Misty, Iron Hills, Ered Luin); gold-like |
| 3 | Entangled Quartz (Quantum) | 5 | 1,000 | \~5 | 20 min | 10,000 | Overworld y 5-16, ME deep under Mordor and Angmar; diamond-like |
| 4 | Primordial Shard (Primordial) | 0 | 0 | 0 | n/a | 10,000 | Only in worlds visited before creation (see time travel) |

Half-lives are in world (server-up) time. A 4th, deep-time-only tier gives a reason to make the long, expensive trip even once tier 1-3 are worked out.

In Middle-earth the ore swaps its texture to the rock it sits in (Mordor rock, Gondor rock, etc.), the way LOTR does its own ores, so it reads as native.

## Collecting: Half-life Cells

The only way to get the energy is to drain the ore with a Half-life Cell, a battery item that freezes the value at the moment of extraction. The ore is never an item, so there is nothing to stockpile and cash in later.

1. Right-click (or hold use on) the ore with a cell. After a short drain the block turns into its depleted rock, and E(t) is added to the cell.
2. Mining it any other way (pickaxe, silk touch, explosions, quarries, the kinetics deployer without a cell) gives depleted rock only: "the energy escapes".
3. A cell holds one energy type, set by the first ore it drains. It stores the amount as a long in NBT and shows it in the tooltip and a durability-style bar.

Cell tiers handle the exponential range (capacities configurable):

| Cell | Capacity | Made from |
| --- | --- | --- |
| Lead-lined Cell | 5,000 | iron sheets, glass, redstone |
| Shielded Cell | 100,000 | chronosteel sheet, Lead-lined Cell |
| Stasis Cell | 5,000,000 | precision mechanism, kontron, Shielded Cell |
| Quantum Cell | 250,000,000 | temporal mechanism, Stasis Cell; required for tier 3-4 |

Optional twist (config, off by default for tier 1): stored energy keeps decaying in a cell at the ore's half-life, except in a Stasis or Quantum Cell, or while the cell sits in a TARDIS interior. That makes the TARDIS the natural vault for a big haul.

## Time travel tie-in

Time travel is the main way to beat the decay: the same ore is worth far more at an earlier playhead, and the most valuable tiers only exist before the world began.

**Normal past trips.** Draining an ore during a trip pays E(playhead). It is a normal causality amendment: the ore is depleted rock from that tick on, so it is gone in the present too. Time keeps running in the trip, so lingering lowers the yield.

**Double dipping.** If history shows the block was drained or mined later (you already took it in the present), the past copy is a contradiction. Proposal: the cell refuses it with "already harvested in your future". Alternative: allow it and add paradox in proportion to the energy taken (open question).

**Deep Time trips (before creation).** The timeline desk's scale runs past the creation mark into negative time, down to `deepTime.maxDepth` (default 100 Minecraft days before creation).

- A Deep Time trip is a trip dimension built straight from the world seed. There is no history to rewind, so it is cheaper to build than a normal trip: every chunk is pristine worldgen, with no players, ghosts, builds or recorded mobs.
- Primordial Shards (tier 4) are added only by a Deep Time worldgen pass, and every other half-life ore there is worth up to its far past cap.
- Changes there are amendments at t < 0, so they reach the present like any other trip edit unless history later touched the block (draining a vein in deep time also removes it now; chunks not generated yet get it via the existing overlay).
- Cost scales with depth before creation (Chronons), and it is gated by TARDIS level (config, e.g. 15). The cost growth is what balances the exponential yield.

**Existing worlds.** The live server world is already old, so its present ore is at the floor. Optional retrogen places ores in old chunks without recording them, so history treats them as having always been there and past trips see them too.

## What the energies are for (first pass)

Each energy plugs into systems the mod already has, so the ores feed the rest of the pack instead of being a side mod.

| Energy | Main use | Converters |
| --- | --- | --- |
| Radiation | Raw power: Decay Generator gives RF / AE / artron; a radiation-heated basin for heated mixing | RF needs RFTools, AE needs AE2, artron needs TARDIS |
| Chronal | Time travel fuel: Chronons, trip and Deep Time costs, escaping high paradox | needs TARDIS |
| Quantum | High-end crafting: Quantum Cells, TARDIS upgrades, Matrix Builder speed, Data Vault capacity boosts | each with its mod |
| Primordial | Rarest crafting: top-tier gear, maybe resetting paradox or a TARDIS-level boost | needs TARDIS |

The loop: Chronal energy pays for trips, trips reach richer ore, so a fresh world or a deep trip pays back more than it costs, while the present-day floor still gives a slow trickle without time travel. With no TARDIS installed, Radiation still works as a power source and the rest fall back to plain crafting uses.

## Desks, worldgen, config and exploits

**Desk controls.** An Isotope Desk (two or three `SoulDeskBlock`-style controls) for the cockpit or Data Vault: a screen showing each tier's yield now and at the timeline playhead, a cell slot with a charge gauge, and a discharge lever that feeds the converters. The timeline desk gets the negative-time scale for Deep Time.

**Server-side by design.** Decay is a formula, the ores don't tick, and cells are plain NBT. The client only needs ore textures, the cell bar and the desk screens.

**Worldgen.** One `IWorldGenerator` per tier, with a per-dimension list (default Overworld + Middle-earth) and per-dimension replaceable rock lists. To check during build: that LOTR's chunk provider runs registered world generators in dim 100.

**Config (per tier).** `floor`, `birthValue`, `halfLifeTicks`, `prehistoryHalfLifeTicks`, `deepTimeCap`, vein size, veins per chunk, y range, dimensions, rock lists, cell decay on/off. Global: cell capacities, Deep Time max depth, cost and level gate, retrogen on/off.

**Exploits covered.**

- Stockpiling ore blocks to cash in later: impossible, the ore never becomes an item.
- Fresh chunks late in the game: same world age, same low value.
- Past copy of ore already taken in the present: refused (or paradox).
- Machines (quarries, deployer, explosions) mining it: depleted rock only.
- Echo items from the past: cells must be exempt from the echo rule or the whole tie-in is pointless (open question).

## Crafted items by tier

Each tier's energy is spent by putting a charged cell in a normal crafting grid (or a mixer basin). The recipe drains the listed energy and hands the cell back, so no new crafting machine is needed. Higher tiers also need the lower tier's parts, so progression runs 1 to 4. Costs are starting points and configurable.

| Tier | Item | What it does | Energy |
| --- | --- | --- | --- |
| 1 Radiation | Geiger Counter | Ticks near half-life ore; shows the nearest vein's tier and yield now | 50 |
| 1 Radiation | Decay Generator | Burns Radiation from a cell into RF, AE or artron (whichever mods are present) | 200 |
| 1 Radiation | Radioisotope Heater | Heat block for heated mixing and LOTR forges, no fuel needed | 500 |
| 1 Radiation | Rad Lamp | Light 15 forever, no power | 20 |
| 1 Radiation | Irradiated Steel | Ingot for tier 2 parts and the Shielded Cell | 100 |
| 2 Chronal | Chrono-Glasses upgrade | Longer playhead range and faster scrubbing | 2,000 |
| 2 Chronal | Time Accelerator | Speeds up crops, furnaces and machines in a 5x5 area (random ticks + tile updates), burns Chronal while on | 3,000 |
| 2 Chronal | Temporal Anchor | Base protection: blocks other crews' trips to before your base was built, inside its radius | 5,000 |
| 2 Chronal | Rewind Wand | Owner undoes their own block changes from the last few minutes, read from history | 4,000 |
| 2 Chronal | Stasis Crate | Chest that stops cell decay | 2,500 |
| 3 Quantum | Entangled Chests | A linked pair sharing one inventory, any dimension | 20,000 |
| 3 Quantum | Quantum Waystone Charm | One warp to any waystone you've activated, no stone needed nearby | 5,000 per charge |
| 3 Quantum | TARDIS upgrades | +1 room slot, faster flight, Data Vault capacity x2 (one item each) | 50,000 |
| 3 Quantum | Quantum Cell | Top cell, needed to hold tier 3-4 energy | 25,000 |
| 4 Primordial | Paradox Damper | Console part that cuts paradox gain by 25% | 30,000 |
| 4 Primordial | Genesis Seed | Returns one chunk to its pristine worldgen state (owner/admin only) | 50,000 |
| 4 Primordial | Primordial Core | Raises the TARDIS level cap by 1 | 100,000 |

Tier 1 items work without TARDIS installed (the generator falls back to RF or AE). Tier 2-4 items need TARDIS, and the Rewind Wand and Temporal Anchor need the history recorder.

## Time travel fuel grades

Fuel comes in five grades. Each one has a **potency** (the furthest back it can take you) and an **efficiency** (how far one unit takes you). A trip can't go deeper than the grade it burns, so deep time needs top fuel however much of the cheap stuff you carry.

| Grade | Fuel | Made from | Max reach (potency) | Efficiency |
| --- | --- | --- | --- | --- |
| 1 | Chronon (existing) | artron + kontron + chronosteel | 24 h of world time back | 1x |
| 2 | Isotope Rod | 4 Chronons + 1,000 Radiation | 7 days back | 1.5x |
| 3 | Chronal Fuel Cell | Isotope Rod + 5,000 Chronal | anywhere in recorded history, up to world creation | 2x |
| 4 | Quantum Fuel | Chronal Fuel Cell + 20,000 Quantum | 10 Minecraft days before creation | 3x |
| 5 | Primordial Fuel | Quantum Fuel + 10,000 Primordial | full Deep Time (100 days before creation) | 5x |

Trip cost grows with the log of depth, so going twice as far back costs a fixed extra amount, not double:

```latex
units = \frac{baseCost \cdot \log_2(1 + depth / 1\,h)}{efficiency}
```

- **Loading.** A Fuel Port control on the cockpit desk takes fuel items into a per-TARDIS tank that keeps a count of each grade. A grade lever on the same desk picks which grade a trip burns. The timeline desk greys out times past that grade's reach and shows the cost before you commit.
- **Coming home** from a trip, and escaping high paradox, can burn any grade, so you're never stranded.
- **Grade 4-5 trips** also need the TARDIS level gate from the Deep Time section.
- Fuel doesn't decay: the energy was frozen when the cell drained it.

With the tier 1-3 numbers, one fresh-world vein of \~8 blocks (\~1,600 Radiation, \~4,000 Chronal) buys roughly one grade 2-3 fuel item. In the present (floor values) the same vein gives \~16-24, so present-day mining only tops up grade 1-2 fuel.

## Time upgrades for the TARDIS engine

TARDIS already lets other mods add engine upgrades, so time upgrades can be real items that go into the same physical slots as TARDIS's own, with no coremod. Read from TARDIS's source ([DarkholmeTenk/TardisMod](https://github.com/DarkholmeTenk/TardisMod), last commit June 2017; still to be checked against the pack's 0.994-124 jar).

**How TARDIS upgrades work today**

- Each TARDIS has 8 upgrade slots on the engine (`TardisDataStore.upgrades`, saved per TARDIS). Right-click a slot with an upgrade item to install it; click again to take it out. Installed upgrades are drawn as rods in the engine.
- Built-in upgrades (`UpgradeItem` metas, made from the Upgrade template crafting part): 4-8 add +1 to one of five stats (Speed, Shields, Max rooms, Energy, Energy regen), on top of the points your TARDIS level lets you spend on the engine buttons. 1-3 absorb half of one damage type (explosions, combat, missed controls) until 1,000 health is used up, then crack into a broken upgrade. Chameleon upgrades add exterior skins, and dimension upgrades unlock dimensions locked in config.
- **The hook:** `UpgradeFactory.registerUpgradeFactory` is public. Any mod can register a factory that turns its own item (or saved NBT `id`) into an `AbstractUpgrade` subclass. Subclasses can change a stat (`getUpgradeEffect`), absorb damage (`takeDamage`), run every tick (`tick`), refuse to go in next to other upgrades (`isValid`, so we can cap stacking), and carry their own NBT.

**Plan.** One `temporalUpgrade` item (metas below) and one factory in `compat/tardis`. Our time-travel code checks for them by scanning the TARDIS's installed upgrades. Wear-out upgrades copy the damage reducers: they have health, crack when spent, and can be repaired. All the numbers are config.

| Upgrade | Effect | Kind | Made with |
| --- | --- | --- | --- |
| Chronon Condenser | Slowly turns stored artron into Chronons (grade 1 fuel) while landed | passive | Radiation |
| Landing Stabiliser | Lands in the past once 4 landing chunks are ready instead of \~8, and cuts landing drift | passive | Radiation |
| Temporal Sensor Array | Timeline desk shows other crews' trips, half-life ore yield at the playhead, and how much paradox a trip would risk | passive | Radiation |
| Fuel Efficiency Coupling | -15% fuel per trip each, max 2 (isValid) | passive | Chronal |
| Reach Extender | One fuel grade reaches 50% further back (never past grade 5's limit) | passive | Chronal (Mk I), Quantum (Mk II) |
| Stasis Bay | Half-life cells inside this TARDIS stop decaying (makes the earlier optional twist an upgrade) | passive | Chronal |
| Escape Capacitor | If paradox would collapse the trip, pulls the crew home with no fuel cost, then cracks | single use | Chronal |
| Paradox Lattice | Raises the collapse threshold by 10 paradox | passive | Quantum |
| Echo Containment Field | Lets one stack per trip come home as a real item instead of an echo | passive | Quantum |
| Time Dilation Field | Interior random ticks and machines run 2x while in flight (config, off on busy servers) | passive | Quantum |
| Paradox Dampener | Absorbs half of all paradox gained until 100 points are soaked up, then cracks; repair with Primordial | wear-out | Primordial |
| Deep Time Hull | Needed for Deep Time trips (before creation); loses health per minute there and pulls you home at 0 | wear-out | Primordial |

These replace the Paradox Damper and "TARDIS upgrades" rows in the crafted items table.

**Slot pressure.** The 8 engine slots are shared with TARDIS's own upgrades, so they are the main balancing tool. Extra slots are bought with level points (next section).

### Unlocking more upgrade slots

A new level-up option, **Upgrade Slots**, sits next to Speed, Shields, Max rooms, Energy and Energy regen and uses the same level points (one per TARDIS level). Each extra slot costs twice the one before, so the number of slots grows with the log of the points spent.

| Extra slot | Cost (points) | Total spent | Total slots |
| --- | --- | --- | --- |
| 1 | 1 | 1 | 9 |
| 2 | 2 | 3 | 10 |
| 3 | 4 | 7 | 11 |
| 4 | 8 | 15 | 12 |
| 5 | 16 | 31 | 13 |
| 6 | 32 | 63 | 14 |
| 7 | 64 | 127 | 15 |
| 8 | 128 | 255 | 16 |

In general, n extra slots cost 2^n - 1 points in total. Config: `baseCost` 1, `costMultiplier` 2, `maxExtraSlots` 24. Points spent are permanent, like the other options. To check: TARDIS's real level cap. If levels stop around 30, only 4 extra slots are reachable, and a multiplier of 1.5 may suit better.

**No overflow: the Upgrade Rack.** The engine's slot row is already full: 8 rods spaced across the whole panel, and our coremod already squeezes that panel to fit the 9th permission switch. So extra slots don't go on the engine at all. They live in an **Upgrade Rack**, a TARDIS-style desk in the engine room or cockpit:

- One page shows 8 slots in a 4x2 grid with prev / next buttons (the same paging as the Bestiary desk), plus a page screen like "Page 2 / 3 - slots 9-16 - 11 unlocked". Any number of slots fits without getting wider.
- Locked slots are greyed out and show the cost of the next unlock. The Upgrade Slots buy button (sneak-press twice to confirm, like the engine) is on the same desk, so no new engine button is needed.
- Install and remove work like the engine: right-click a slot with an upgrade, right-click again to take it out. Saved per TARDIS in our own data, server-side.

**Which upgrades work in rack slots.** Our time upgrades work anywhere, since our code reads them. For TARDIS's own stat and damage upgrades (Speed, Shields, explosion protection...) to count from the rack, a small coremod pass-through (like the roundel hook) adds rack upgrades to TARDIS's `getLevel(mode)` and damage loop. Chameleon and dimension upgrades stay engine-only.

**Risks.** If our mod is removed, TARDIS can't rebuild our upgrades from NBT and they disappear from the slots on load. Custom upgrades need their own rod texture in the engine (small client part).

## Open questions for Aaron

- [ ] Is the clock right? "World age" = server-up time (what the recorder uses), so a world left running overnight decays even with nobody online.
- [ ] Present floor: keep a 1-5 trickle forever, or let tier 3 really reach \~0?
- [ ] How far is "far distant past": 100 Minecraft days before creation, or much deeper?
- [ ] Double dipping: refuse the past copy, or allow it for paradox?
- [ ] Should stored energy decay in basic cells (TARDIS as the safe vault)?
- [ ] Tier names and themes: Pitchblende / Chronium / Entangled Quartz / Primordial Shard, or your own?
- [ ] Is the 4th, deep-time-only tier wanted?
- [ ] Main use per energy: does the table above match what you had in mind?
- [ ] Retrogen the existing server world, or new chunks and deep time only?
- [ ] Any radiation hazard near tier 1 ore or full cells (config, off by default)?
- [ ] Fuel reach: are 24 h / 7 days / creation / 10 days before / 100 days before the right steps?
- [ ] Should cheaper fuel be allowed for the trip home only, or for all short trips too?
- [ ] Which crafted items to build first? Suggested: Geiger Counter, Decay Generator, cells, Isotope Rod.
- [ ] Upgrade Rack: engine room, cockpit, or its own room? And should TARDIS's own upgrades count from the rack (needs the small coremod hook)?
- [ ] Should Deep Time need the Deep Time Hull upgrade, a TARDIS level, or both?
