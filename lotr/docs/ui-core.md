# UI core: management screens built on the server, drawn by one client screen

Read when: you add or change a management screen (a colony, all colonies, permissions, the treasury, warehouses,
stewards, wars...), or touch `ui/`, `client/ui/` or `colony/ui/`. Written 2026-11-11, branch `claude/ui-core` (on
`claude/project-thread-3r3clf`). Look: the mockup in Aaron's message of 2026-11-11 (Minas Test, People tab).

## Status

Builds in CI (2026-11-11, `ModernMiddleEarth_TARDIS`), and `TableQueryTest` (4 cases, including the mockup's filters)
passes there. **The screen has never been looked at in game**: expect sizes, offsets and colours to need a pass. The
"Where the first build may fail" list at the end is what to check when it's first drawn; it compiled cleanly.

## The idea

A management screen is data. The server builds a `View` for one player (tabs plus one page plus the actions they may
take) and sends it; the client has one screen, `UiScreen`, that can draw any view. A new screen is a server class
(a `ViewProvider`) and no client code. The server checks every action again, so a modified client can only ask.

```
client                                   server
UiScreen.request(target) ── Open ──────▶ Views.open → provider.build(player, context, tab) → View
UiScreen.show(view)      ◀── Show ────── (or Close if they may not see it)
tab click                ── Open ──────▶ same, other tab (only the open tab is built and sent)
button / row / stepper   ── Act ───────▶ Views.act → build (still allowed?) → provider.act → Result
                         ◀── Show ────── the same view rebuilt, or Result.next (another view)
every 5 s while open     ── Open ──────▶ refresh (open = false: never pops a screen up)
```

Search, filters, sorting and scrolling happen on the client (`TableQuery`), so typing sends nothing.

## Packages

| Package | Side | Holds |
|---|---|---|
| `ui/` | common | The model (`View`, `Page`, `Column`, `Row`, `Cell`, `FilterGroup`, `Action`, `Stat`, `Tone`), `TableQuery`, `TableBuilder`, `ViewProvider`, `Views` (registry), `UiNet` (4 payloads), `UiCodecs` |
| `client/ui/` | client | `UiScreen`, `TablePane`, `StatsPane`, `ProfilePane`, `Sections` (stat lines, shared), `Popups` (sort menu, filters, choices, confirm), `Imm` (drawing + clicks), `UiTheme` (sprites, colours, sizes), `TableState` |
| `colony/ui/` | common | `ColonyViews`: the colony view (5 tabs) and the all-colonies view; `CitizenViews`: one citizen's page |

`ui/` never names a client class; `UiNet.showHandler` / `closeHandler` are set in `LotrClient`.

## The model (`ui/`)

- `View(id, context, title, tabs, tab, page, back)`. `id` is the provider; `context` says what it is about (a
  colony's UUID as text; empty for views about the player). `back` adds a Back button to another view.
  `View.Target(view, context, tab)` names a place to go.
- `Page` is sealed: `Page.Table`, `Page.Stats` or `Page.Profile`. Add a page kind by adding a record to the `permits` list, a case in
  `UiCodecs.writePage`/`readPage` (bump `UiNet.VERSION`), and a pane in `client/ui/` drawn from `UiScreen.init`.
- `Page.Table(columns, filters, rows, sortColumn, descending, open, rowActions, buttons, total, empty)`:
  - `Column(id, label, weight, align, numeric, searchable)`: width is `weight` against the other columns. Numeric
    columns sort by `Cell.sort`; text columns by text, ignoring case.
  - `Row(id, cells, tags)`: `id` comes back with actions. `tags` are what filters match.
  - `Cell(text, tone, sort)`: text is resolved on the server (as the Ledger did); `sort` orders numeric columns, so
    "3 / 4" sorts by 0.75 and "-" can sort first.
  - `FilterGroup(id, label, filters)`: ticks in one group widen (homeless **or** jobless), ticks in different groups
    narrow (**and** a dwarf). Unknown groups in the client's saved ticks are ignored.
  - `open`: the action a double-click on a row sends. `rowActions` act on the selected row (greyed until one is
    picked). `buttons` don't need a row.
  - `total`: rows before the server cut the list (`UiCodecs.MAX_ROWS` = 2000, or `TableBuilder.limit`). The footer says
    "first N sent" when cut.
- `Page.Stats(sections, buttons)`: `Section(title, stats)`; `Stat(label, value, tone, bar, stepper)`. `bar` 0-1 draws a
  bar (reddish under a third), negative for none. `Stepper(action, value, min, max, step)` draws arrows that send the
  new value; the server clamps it again. `Picker(action, current, choices)` draws a drop-down; picking a
  `Choice(id, label, enabled, why)` sends `action` with the choice's id as the row (the server checks it again).
- `Page.Profile(portrait, name, rename, subtitle, left, right, buttons)`: one person up close. `portrait` is an entity
  UUID: the client draws that body's head and shoulders (following the mouse) when it is loaded there, else "Far
  away". `rename` is the action a new name goes back with, as the row (the screen's one text input; empty: no Rename
  button). `left` and `right` are sections in two columns, scrolled together with the wheel.
- `Action(id, label, enabled, why, confirm)`: disabled actions are drawn greyed with `why` as a tooltip, so players
  see what exists and why they can't. `confirm` asks "Are you sure?" first.
- `Tone`: NORMAL, MUTED, GOOD, WARN, BAD. The server says how text reads; the client picks colours for brown or beige
  backgrounds (`UiTheme.ON_DARK` / `ON_LIGHT`). Never send colours.

## Writing a view

```java
public static void register() {            // once, at mod setup (LotrPort)
    Views.register(LotrPort.id("warehouse"), new WarehouseProvider());
}

final class WarehouseProvider implements ViewProvider {
    public Optional<View> build(ServerPlayer player, String context, String tab) {
        Colony c = /* parse context defensively; return Optional.empty() if not allowed */;
        Page.Table page = TableBuilder.of(items, e -> e.getKey().toString())
                .column(Column.text("item", label("Item"), 4), e -> Cell.text(name(e)))
                .column(Column.number("count", label("Count"), 1), e -> Cell.number(e.getValue()))
                .filterBy("kind", label("Kind"), "kind:", e -> kindOf(e), k -> label(k))
                .sortBy("count", true)
                .rowAction(Action.of("take", label("Take a stack")))
                .build();
        return Optional.of(new View(ID, context, Component.literal(c.name()), List.of(), "", page,
                Optional.empty()));
    }

    public Result act(ServerPlayer player, String context, String tab, String action, String row, int value) {
        // check the permission again, do it, then:
        return Result.say(Component.literal("Taken"));   // the view refreshes by itself
    }
}
```

Open it from server code with `Views.open(player, new View.Target(id, context, tab), true)`, or from a client screen
with `UiScreen.request(target)`.

Rules for providers:

- `build` and `act` get whatever a client sent. Parse `context` and `row` defensively (bad UUIDs, ids from another
  colony); `Views` catches exceptions and logs them, but don't rely on it.
- `build` returning empty means "may not see": the player gets `denied()` and an open screen closes. `Views.act`
  calls `build` before `act`, so someone who lost access can't act through an open screen.
- Check the permission inside `act` again; the action being shown enabled proves nothing.
- Build only the asked tab. A tab that is expensive to build (a world list) should `limit` its rows.
- Text: `Component.translatableWithFallback("ui.lotr_port.<view>.<key>", "English")` for labels, as the rest of
  the port does (no lang file needed). Cell text is plain strings resolved on the server.

## The client (`client/ui/`)

- `UiScreen` draws: title (gold, centred), tabs over the frame (selected tab drawn after the frame so it joins it),
  the beige frame, the page, then Done across the frame's bottom edge and Back at its left. Max 420 x 250 GUI units.
- **Immediate mode** (`Imm`): each frame, panes draw through `Imm` and every clickable thing leaves a `Hit`. A click
  runs the topmost hit under the mouse (popups are layer 1, the page layer 0). Nothing to keep in step with the view:
  a refresh just draws the new data. The search box is the one real widget (an `EditBox` without its border, over an
  inset sprite), so typing works as in vanilla. New widgets are methods on `Imm` (see `button`, `dropdown`,
  `checkbox`, `bar`).
- `TablePane`: tool bar (search, "Sort: X" drop-down, "Filters (n)" drop-down when there are filters), headings
  (click to sort, again to reverse; numbers start high to low), rows (only those in view are drawn, so long lists
  cost nothing), scroll bar (drag or wheel), the "3 of 140 shown" line and buttons at the bottom right. One click
  selects a row, a double click sends `open`.
- `StatsPane`: sections in one column, or two when the page is 300+ wide (each section goes to the shorter column).
  `Sections` draws the lines for it and for `ProfilePane` (labels cut with a tooltip, short values right-aligned).
- `ProfilePane`: portrait (`InventoryScreen.renderEntityInInventoryFollowsMouse`, scale and offset so the top 42% of
  the body fills the box), name at 1.5x with Rename (`UiScreen.startRename` puts an `EditBox` over it: Enter sends,
  Escape or a click elsewhere drops it), the subtitle, then the two columns with a thin scroll bar when they overflow.
- `Popups`: the sort menu, the filter box ("Show only", a heading per group, Clear all; long groups carry on in the
  next column), the choice menu (ticks the current choice, scrolls past 14) and the confirm box. A click
  outside a popup closes it; Escape closes it before the screen.
- `TableState`: search, ticks, sort, scroll and selection per view + context + tab, for the session. They survive
  refreshes, tab switches and reopening.
- `UiTheme`: every sprite id and colour. Restyle here. Sprites are Kenney's "UI Pack - RPG Expansion" (CC0, see
  `textures/gui/sprites/ui/CREDITS.txt`), nine-sliced by their `.mcmeta` at 2 texels per GUI unit, made by
  `converter/ui_kenney.py`.

Sprites used: `panel_beige` (frame), `panel_beigelight` (popups), `inset_brown` (table well), `inset_beigelight`
(search box, stats well), `row` / `row_hover` (rows; hover and selected), `tab` / `tab_hover` / `tab_selected`,
`button` / `button_hover` / `button_disabled`, `checkbox` / `checkbox_checked`, `arrow_down` (drop-downs),
`arrowbrown_left` / `_right` (steppers), `scroll_track` / `scroll_handle`. Not used yet: `button_pressed`, the blue
panels and insets, the beige arrows and icons.

## The colony views (`colony/ui/ColonyViews`)

`lotr_port:colony`, context = the colony's UUID, for members (`Permissions.isMember`):

| Tab | Page | Data | Actions |
|---|---|---|---|
| Overview | stats | citizens, soldiers, waiting at the Hall, buildings, homeless, jobless; happiness and food (bars); coins, head tax, wage bonus | head tax and wage bonus steppers with `treasury` (same limits as the Ledger) |
| Buildings | table | building, type, in use (residents/beds or workers/slots), quality, door | filters: kind (homes, workplaces, other), space (full, has room) |
| People | table | name, race, job ("Jobless" in warning tone), home ("Homeless"), traits, mood (coloured by `leave_below`, 50, 75) | filters: status (homeless, jobless, unhappy = under `leave_below`, hungry = under `eat_below`, soldiers), trait and race (those present). Double-click or "Citizen page" opens the citizen page |
| Stock | table | item, mod, count | search, sort |
| Members | table | player, role, rank, what the role may do | one "Make X" per assignable role, Remove (confirm); disabled without `members`; `Permissions.setRole` checks ranks |

`lotr_port:colonies`: every colony you belong to, with your role, citizens, happiness, coins and distance (nearest
first); filter by ruled / served; Manage (or double-click) opens it, with Back to the list. The colony view only has
Back when you belong to more than one colony.

`lotr_port:citizen`, context = `<colony id>/<person id>` (`CitizenViews`), for members; Back returns to People:

| Part | Shows | Changes (permission `citizens`: stewards, rulers) |
|---|---|---|
| Header | portrait, name, race · age · work | Rename (`Persons.rename`) |
| Needs | mood, food, comfort (home quality) as bars; wages paid or owed; skill | |
| About | traits, spouse, parents, children (†: dead), purse | |
| Mood | "Mood: 60, heading for 72", then each part RimWorld-style: the colony's (`Life.happinessParts`: base, home, fed, tavern, wages, tax) and each thought (`Colonists.thoughts`: traits, home, ration, memories with days left), strongest first | |
| Orders | Work (colony decides, rest, or a workplace with a free slot), Home (colony decides or a home with a free bed), Ration (`colonist_ration/`) | drop-downs; `colonist.Orders`, then `Assign.run` at once |

Also opened by sneak + right-click on a citizen and `/lotr_colony citizen <npc>` (`LedgerNet.sendCitizen`; the old
`CitizenScreen` is no longer opened).

Ways in: the Town Hall's **Manage** button (it replaced the Ledger button; the Ledger screen and `/lotr_colony ledger`
still work), `/lotr_colony manage` (nearest colony you belong to) and `/lotr_colony colonies`.

## What the other planned screens need

| Screen | Fits as | Missing from the core |
|---|---|---|
| Global colony management | done (`lotr_port:colonies`) | per-colony actions from the list (it only opens one) |
| Player permissions | the Members tab | adding a player by name: there is no text-input action yet (the Town Hall screen still does it). Add an `Action` field for a prompt, or a page kind with a form |
| Treasury | a stats page (balance, tax, wages) + a table of payments | a history source: the port keeps no ledger of payments yet (`HistoryLog` has `wages`/`taxes` totals per day) |
| Warehouses | a table per storehouse or one per colony with a filter by building | taking items out needs a server-side transfer action; counts per building need `Stores` |
| Map visibility | a table of players/groups with on/off row actions | the same text-input gap as permissions |

## Where the first build may fail

- `GuiGraphics.blitSprite` with the nine-slice `.mcmeta`s: the sprites are 2x; check they draw at the GUI sizes in
  `UiTheme` and not doubled.
- `EditBox` without a border puts text at its top-left; the box is placed 5 right and 4 down inside the inset. Check
  it lines up.
- `Screen.renderBackground` (1.21.1 draws the blurred world): the frame is drawn there so the search box draws over
  it. If the blur hides the frame, draw the frame in `render` before `super.render` instead.
- Popups translate 200 in z so they cover the search box's text. If the search text still shows through, flush or
  raise more.
- `ComponentSerialization.STREAM_CODEC` on `RegistryFriendlyByteBuf` in `UiCodecs`.
- `Page` is a sealed interface with a pattern `switch` (Java 21): fine on the port's toolchain.

## Not done

Keyboard navigation and narration (only the search and rename boxes are real widgets); scrolling a stats page; a
text input in general actions (only the profile's rename); the screens listed above; lang files (fallback English everywhere, as elsewhere in the port).
