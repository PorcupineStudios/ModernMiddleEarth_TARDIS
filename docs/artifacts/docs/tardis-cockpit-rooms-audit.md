# TARDIS cockpit and rooms audit

Oct 26, 2026 · @Aaron

Branch audited: `claude/project-thread-scmmpa` (PR #10). Read-only review, no code changed. Kinetics left alone. Findings are ranked by impact; each says what it means for players and what to do.

## 1. Guests can raid the TARDIS through room desks (high)

Only some controls check a TARDIS permission. Anyone who can walk into the interior can use the rest:

- **Soul vault** (Bestiary desk) and **chamber storage** (Teleport Chamber desk) open with no permission check (`Bestiary.init` GUI factories, `SoulVaultContainer.canInteractWith` checks distance only). A guest can take every filled Soul Nexus, including captured traders and hired units, even though deploying or trading those same souls needs TRANSMAT.
- **Data Vault ME terminal** checks the TARDIS level but no permission (`VaultTerminalTileEntity.openTerminal`), so a guest gets the whole TARDIS item store.
- **Upgrade Rack** GUI opens for anyone (`UpgradeRack` factory); only buying a slot checks POINTS, so a guest can pull the upgrades out.
- **Exterior camera** has no permission check (low risk, but it is a scouting tool).

Fix: one shared `DeskAccess.check(player, permission)` used by every GUI factory and desk action (TRANSMAT for souls, a storage-type permission for the terminal, POINTS for the rack, FLY or ROOMS for the camera), and the same check in each container's `canInteractWith` so an open screen closes if access is revoked. Small change, and it matters on the shared server.

## 2. Built rooms never get layout fixes (high, feel)

Bumping a schema `VERSION` (Cockpit is on v11, Teleport Chamber v6, Data Vault v6, Bestiary v4) only rewrites the schema file. Rooms already built in a world keep their old layout, so players don't get new desks (timeline, rack, safe travel, camera) unless they remove and rebuild the room, and old worlds keep legacy blocks (`BestiaryBlock` 12/13 page buttons, `SoulChamberBlock` pods and holos, `TerminalBlock` stands). That legacy is also why several blocks carry two generations of code.

Fix: a server-side **room refit**. When the room's SchemaComponent (or any of our desks) is sneak-clicked by the owner, compare our own blocks in that room against the current schema (rotation-aware, using the same offsets) and place what is missing or replace what is outdated, never touching TARDIS's or the player's blocks. Once that exists, the legacy metas can be converted on refit and later deleted.

## 3. The desk block is full (high, code)

`SoulDeskBlock` uses all 16 metadata values (0-15), so the next desk control needs a new block. Each control is also spread over three switch statements: `SoulDeskBlock.use`, `SoulDeskTileEntity.refresh/state` (8 branches) and `SoulDeskRenderer` (21 cases), so adding one touches three files and is easy to get half right.

Fix: a `DeskControl` table (id, label key, screen or lever, `use(world, pos, player)`, `state(world) -> NBT`, colour/text for the renderer). The block, tile entity and renderer then loop over it. Add a second desk block id (for example `tardisDesk2`, metas 16+ mapped to it) so new controls have room. Rename the class to `TardisDeskBlock` while keeping the registry name `tardisSoulDesk`, since it now carries camera, timeline, rack, safe travel and AE2 controls, not just souls.

## 4. Room schema code is copied four times, and a bad write sticks (medium)

`CockpitRoomSchema`, `SoulChamberRoomSchema`, `BestiaryRoomSchema` and `VaultRoomSchema` each carry the same `sync`, `existingVersion`, base-schema loading and `block()` helper. Two real problems ride along:

- The file is written in place. If the server stops mid-write the file is corrupt, `existingVersion` returns 0 ("not ours") and the room is never written again.
- Only the Cockpit honours an "add this room" config; the others are always written.

Fix: one `RoomSchemas` helper (`sync(name, versionTag, version, configFlag, builder)`), writing to a temp file and renaming, and treating an unreadable file that has our name as ours.

## 5. Desks feel unresponsive when refused (medium, feel)

- The client animates the button press and the server plays the click sound before any check, so a refused press looks like it worked, then a chat line says it didn't.
- Every refusal and result goes to chat. Safe travel broadcasts to everyone in the interior and in the past world.

Fix: play the click only when the action runs, and a low "denied" sound otherwise (server-side, no client code). For screens, briefly show the refusal on the desk's own screen (the tile entity already syncs a state tag) instead of chat.

## 6. Cockpit render cost (medium, measure first)

The cockpit desk draws each of the four console sides once, then once more per boxed control (`CockpitControlsDrawer`), so roughly 4 x (1 + controls per side) full console render calls per frame. The wall chart draws the same live picture again. On a weak PC that is the likeliest FPS drop in the TARDIS.

Fix: check FPS standing in the cockpit first. If it matters, draw the chart's desk picture only within about 8 blocks (plain boxes beyond), and skip the per-control passes when the player is far from the desk.

## 7. Cockpit map stand is not a desk (low, feel)

The navigation map is a `CockpitPanelBlock` meta-4 stand, while every other room control is now a small TARDIS-style desk. Making it a desk control (once item 3 frees space) keeps the cockpit consistent with your preference.

## 8. Smaller code cleanups (low)

- `SoulDeskTileEntity`: dead fields `absorbOut`, `spawnOutside` (absorb mode and pod destination are unused since v3) and unused `meta` locals in `getControlState`/`getColorRatio`. Desks that show nothing (camera, timeline) still tick every second.
- Hard-coded English chat strings (7, in `TimeDestination`, `fuel/*`, `time/TimeUpgrades`) while the soul and desk code uses lang keys. Move them to `en_US.lang`, and replace the inline fully qualified class names in `TimeDestination` with imports.
- `SoulChamberBlock` metas 6-15 (v3 holos) and `Bestiary.chamberHolo` (v4 holos) are two blocks for the same job; fold them once refit exists.
- `Bestiary` page per interior is memory-only, so it resets after a restart (cheap to save in `SoulVault`).

## Working well

- Cockpit click mapping reuses TARDIS's own `getControlFromHit` and `activateControl`, so it can't drift from the real console.
- `UnstableControlFix` and the per-control clipping fixed the two worst cockpit annoyances cleanly, with safe fallbacks.
- Desk facing from neighbours survives room rotation; collision boxes follow the CLAUDE.md rule.
- Server checks for absorb, deploy, trade, course and rack purchase are in place and consistent (TRANSMAT / FLY / POINTS + level).

## Suggested order

1 (permissions) first, it is small and protects the server. Then 3 + 4 together (desk table and schema helper), which makes 2 (refit) straightforward. 5-8 can ride along.
