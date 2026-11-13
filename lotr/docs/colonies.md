# Colonies: society core, colony life and wars

Read when the task touches `society/`, `colony/`, `unit/`, `land/`, `social/`, `war/` or `ai/`. Built in the
2026-11-11 rework (Aaron: 30-50 colonies x 100+ colonists, easy to extend or replace; design doc
https://claude.ai/artifact/VLwZ5N7pkVMJW4PCYXh41G).

## Core (`society/`)
- Every NPC that matters is a `Person` in `Persons` (SavedData): an `Owner` (`player:`, `colony:`, `realm:`, `none`)
  and typed, versioned components (`ComponentType.register(id, codec)`). Hired units, citizens, applicants and colony
  soldiers are all Persons. A feature adds a component and a system, never a field on `Person` or `Colony`.
- Callers go through the facade `Society` (citizens, soldiers, population, colonyOf...). `Persons.set` posts
  `SocietyEvent.ComponentChanged`, `setQuiet` doesn't; null removes. Events: Added, OwnerChanged, Died, Removed,
  ColonyFounded, ColonyDisbanded.
- Colonies keep their feature state the same way: `Colonies.set(colony, type, value)` (members, land, treasury,
  buildings, stock, economy, liege...).

## Scheduler (`society/sim/`)
- `ColonySystem.of(id, Cadence.DAY|PULSE, tiers, after, body)` registered with `ColonySystems.register`; the
  scheduler orders systems by `after`, spreads colonies over ticks and runs missed days as arithmetic when a colony
  was unloaded (`SystemContext.days`). Tiers: ACTIVE (player near), LOADED, UNLOADED. Numbers in
  `colony_systems/settings.json`; `/lotr colony perf`.
- **A busy server comes first** (2026-11-12, `ColonyScheduler.Load`, settings `load`): the server's own tick time
  (Pre to Post, smoothed by `smoothing`) gives the load. Under `light_ms` (25): light, the backlog is caught up with up
  to `light_budget_factor` (2) x `budget_ms`. Over `heavy_ms` (40): heavy, colony days that fall due wait in the
  backlog and only ACTIVE colonies (a player there) get pulses, i.e. their bodies. Over `overloaded_ms` (48): only
  those pulses. In any load, a day that would start after the tick's budget is spent waits too. A day is marked done
  only when it runs, and then runs every day that went by (capped by `max_catch_up_days`), so waiting loses nothing.
  `/lotr colony perf` shows the load, days waited and caught up; `/lotr colony load <light|normal|heavy|overloaded|
  auto>` pins it to try. GameTest `colonyDaysWaitOutABusyServer`.
- **The benchmark is measured against the machine** (`SocietyTests.fiftyColoniesRunTenDaysWithinTheBudget`, 50 x 150
  for 10 days): a colony day's cost is divided by a reference job (UUID map, list sort; `calibrationNanos`, best of 9)
  timed in the same run, and may be at most `MAX_DAY_UNITS` 1.5 (0.48 on CI, 2026-11-12). Slow ticks and
  milliseconds are only logged: they say more about the machine than the code. Every colony must also have run all
  its days by the end (none stuck in the backlog).
- Hired units (`unit/Units`): one `Unit` component (order, post, wage, unpaid, squad) for a player's unit or a
  colony soldier; stationing is one owner change; unpaid soldiers desert (`units/settings.json`).

## Members, land, map (`colony/members`, `land/`, `social/`)
- Roles are data (`colony_role/*.json`: rank and permission names, `Perm`); the owner is the ruler. Town Hall
  screen `HallScreen` (`HallNet`). `Protection` checks the claim's colony and the permission; other systems open it
  up through `Protection.exception` (wars do). Banners claim whole chunks (`Claims`, `colony_land/settings.json`).
- `social/`: who sees your map icon (`MapPresence`, `SocialNet`).

## Colony life (`colony/building`, `colony/work`)
- Buildings: the Building Writ on a door measures the room with a 3D fill (`RoomScan`, RingRealms' rules: L-shape
  floor 56, two storeys 49) and picks the best fitting type (`colony_building/*.json` requirements by counter tags
  `lotr_port:colony/<counter>`); `building_recheck` re-measures, retypes or loses buildings.
- Jobs (`colony_job/*.json`), races (`colony_race/*.json`), `Assign` (homes by bed, jobs by priority and race
  proficiency), `Life` systems `homes_jobs` -> `work` -> `wages_taxes` -> `needs` (food, happiness, leaving),
  `Stores` (building chests, then the abstract `Stock`). Numbers in `colony_life/settings.json`.
- `Bodies`: at most `bodies_max` citizens walk about near players; Ledger (`LedgerNet`, `LedgerScreen`) and citizen
  page (sneak + right-click).

## Wars (`war/`, replaces LOTR's conquest)
- `Wars` (SavedData `lotr_port_wars`) holds `War`s between two parties (colonies, or AI realms): goal (`tribute`,
  `take_colony`, `vassal`, `free_vassal`), reason, warning until `active_from`, score and exhaustion per side, armies,
  a peace offer, reports. Numbers in `colony_war/settings.json` (`armies` and `peace` groups); tests may
  `WarSettings.override`.
- Muster (needs `Perm.WAR`): real soldiers of the colony, coins each; march = base + per 500 blocks (capped).
  Sieges need someone with `war` online on both sides; the bodies land `siege_landing` blocks from the Hall towards
  home and guard the Hall; holding the zone (`siege_zone`) with no defender (member or soldier) for `siege_hold`
  wins. Protection lifts for the attacking colony's members only while a siege runs. Attacking side offline past
  `attacker_grace` = siege fails; defending side offline past `defender_grace` = attacker wins (RingRealms' rule).
- Raids: only when the defender's leaders are offline, capped soldiers, cooldown per colony, fought in the abstract
  (soldiers + citizens x `defense_per_citizen`), loot a share of treasury and `Stock`, never blocks.
- Peace: the goal met (colony transferred with its members cleared / `Liege` component / vassal freed / tribute), an
  accepted offer (tribute capped by the score lead x `coins_per_score`), or exhaustion (per day and per soldier lost)
  forcing peace on the score. Vassals pay `vassal_tribute` a day (system `vassal_tribute`).
- AI rulers (`ai/AiRulers`) still fight each other in the abstract but their wars and sieges are `War`s on realm
  parties (no warning; AiRulers makes their peace). `ai_raids_players` (default off) lets them declare tribute wars on
  hostile player colonies and send counted raids. `/lotr_war` (list, targets, declare, muster, peace, accept).
- History: `war_declared`, `war_active`, `army_mustered`, `siege`, `raid`, `war_ended`, `colony_transferred`.
  Players are told in chat and again on login (reports within `report_ticks`). GameTest `WarTests`.
- Not yet: a war screen and map markers for marching armies, morale, exhaustion raising wages and lowering happiness,
  defenders spawning for AI colonies a player besieges, interception by third realms.

## Testing tips
- The GameTest world (`lotr/run/world`) persists between runs; tests found colonies at random far positions and
  clean up. `MountTests.boarsFightBack` can fail on a reused world and pass on a fresh one: move `run/world` aside.
- The GameTest server runs ticks as fast as it can, so `timeoutTicks` is game time, not seconds.
- Admin test commands: `/lotr colony found_ai <name>` (an AI-held colony at your feet), `settle <npcs>` (they join
  your nearest colony), `coins <n>`; `/lotr_colony citizen <npc>` opens a citizen page. Screenshot script:
  `lotr/run/colony_shots2.txt` (house, kitchen, storehouse, writ, Ledger pages via `click`, citizen page, a war).

## Screen textures
- `textures/gui/sprites/ui/` (Aaron 2026-11-11: smooth style): Kenney's UI Pack RPG Expansion (CC0), rebuilt by
  `converter/ui_kenney.py <pack png folder>` as 2x nine-slice sprites (edges from clean rows, so Kenney's notches
  don't repeat) with tabs, scroll bar and hover/pressed/disabled buttons drawn in its style. Draw with
  `GuiGraphics.blitSprite(LotrPort.id("ui/panel_beige"), x, y, w, h)`; draw the selected tab after the panel.
  Also list rows (`row`, `row_hover`: a box per clickable row), `checkbox[_checked]` and `arrow_down` for the filter drop-down. Not used by any screen yet (the shared list table will).

## Caravans (2026-11-12, `journey/`, `colony/ui/CaravanViews`, `RuleViews`, `RuleEditors`)
- Journeys (`Journey`, `Journeys` SavedData) are parties on the road for any kind (`JourneyKind`: caravans now, armies
  later). Routes are planned off-thread (`RoutePlanner`, A* over the map: biome speeds `journey/terrain.json`, roads,
  rivers only on road bridges or marked bridges). Data moves them; near a player they are bodies that follow the
  data's pace (`JourneyBodies`; GameTest `watchingDoesNotChangeSpeed`), with repath / hop / back-to-data when stuck.
- `Caravans`: load at home (never below floors), deliver to your own colony and bring its goods back, or trade by a
  friend's standing offers. Colony component `caravans` (`CaravanBook`): routes, offers, floors. Lines are a fixed
  amount, the excess over N, or a top-up of the other end to N.
- A route goes by hand or by its rules: a tree of shared conditions (`ringbearer_api:all/any/not`) with caravan
  blocks (`CaravanConditions`: colony_stock, colony_population, days_since_sent, time_of_day, caravans_out), checked
  every 10 s while none of the route is out. The editor (Rules tab) edits the tree as JSON through `RuleEditors`
  specs: another system adds a block by registering its condition type and a `RuleEditors.Spec`. Any condition also
  works without an editor via `/lotr_colony caravan route rules <n> <json>`.
- GameTests `CaravanTests`, `JourneyTests`; screenshots `ui_caravan*` in `devscripts/ui_shots.txt`.

## Hall events / policies (2026-11-12, `colony/events/ColonyEvents`, `colony/ui/EventViews`)
- A `colony_event/` def with a `policy` block shows on the colony view's Events tab, to proclaim once (perm `events`,
  Stewards have it). `policy`: title, description, cooldown_days, requires (any condition, "home" = the colony) +
  requires_text, cost (item -> count, from the stores), min_people, min_opinion (weddings: a lower bar, the Hall makes
  the match). `auto: false` makes it policy-only (feast, midsummer).
- Goods and the cooldown (`proclaimed` component) are only spent when the event found someone (no match, no cost).
- Marriage needs both of age: weddings check `Colonists.adult`; immigrant settlers are never younger than the race's
  adult age, and an offer's spouses are only linked if both are.

## Market, research points, locked stores (2026-11-12, `colony/market/`, `colony/ui/MarketViews`)
- The colony is self-contained (Aaron): containers in workplaces and storehouses (not homes) can't be opened
  (right-click opens the market), hoppers/droppers can't be placed against them, a store touched by one isn't counted
  (`StoreLock.fed`), and breaking one moves its goods into the colony stock. Ops in creative pass.
- `Market`: buy from the stores (never below caravan floors) and sell to them, prices `base x (target/stock)^curve`
  clamped, sell = price x `sell_share`, paid from/into the treasury. Each item has a daily intake cap the colony sets
  (Limits tab, perm `market`) up to a ceiling `base_limit + limit_per_level x level`. Numbers and base prices: `colony_market/settings.json`.
- `Research` (tiers, Aaron 2026-11-12): `colony_research_tier/<id>.json` (name, order, rate, cost per point,
  requires + requires_text). Researchers whose job building exists earn `rate x skill factor` a day of the tier the
  colony puts them on (market Research tab, perm `research`; component `research_plan`); the rest do basic (free:
  time and manpower only). Higher tiers take their goods from the stores, and earn less if short. Points per tier:
  component `research`. Market levels are `upgrades` in the settings: one `Cost` per level
  (`colony/market/Cost`: `research` points of any tiers, `items` from the stores, treasury `coins`, `requires` +
  `requires_text`), checked whole and paid whole.
- `Hatches`: barrels in a market building are delivery hatches; every 200 ticks their goods are sold within the caps
  and treasury, the rest waits; coins go to the placer's account (else the founder), collected on the Sell tab.
