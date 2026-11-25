# Time Travel: Implementation Plan

Oct 18, 2026 · @Aaron

The foundation is the recorder: the server-side system that logs every block change, container change and player movement into the history files. It goes first for two reasons. Every other feature reads its data. And every day it runs on your server is history you'll be able to visit later, so starting it early gives the later phases a real past to test against.

The work is split into seven phases. Each one ends with something you can see working on the server before the next one starts, and each builds only on the phases before it. The design details live in the [storage plan](https://claude.ai/code/artifact/069b97de-f188-4bdc-a5b5-f7573d7d20a8) and the [gameplay guide](https://claude.ai/code/artifact/40a4cd46-733d-4152-9e33-f6bcdfc8ba3e).

## Roadmap

&#91;embedded content: roadmap · 7 phases, each playable on its own\]

Each phase ends with something you can test on the server, so if a later phase runs into trouble, the earlier ones are still finished features.

## Phase 0: Rename and groundwork

Goal: the existing mod builds and runs under its new name, with nothing broken, and has a home for the new code.

- [ ] Rename MyMod to TardisTimeTravelAddon: mod id, package names, jar name and coremod class names, in the PorcupineStudios/TardisTimeTravelAddon repo
- [ ] Keep old world data loading: map the old mod id's blocks and items to the new names so existing TARDIS rooms, Data Vaults and the Bestiary survive the rename
- [ ] Add a `timetravel` package split into server-only code (recorder, storage, past dimensions) and a thin client side (ghost rendering, timeline screen, glasses view)
- [ ] Add the time-travel config file with the settings decided so far (dimensions, rewind point interval, coordinates visibility and the rest), each with a default
- [ ] Swap the renamed jar onto the server using the backup routine below, and confirm everything still works

## Phase 1, the foundation: the recorder

Goal: the server silently records history in the Overworld and Middle-earth, with no noticeable lag, and can prove the recording is accurate. Players see nothing new except an admin stats command.

Built in this order, because each step feeds the next:

1. **Clock and name tables.** The gap-free tick clock, the session table (who was online when) and the permanent block and item name tables.
2. **Block hook.** A coremod hook on the chunk's block-setting method, plus metadata changes. It copies each change into a lock-free in-memory queue and does nothing else on the game thread.
3. **Cause tracking.** Markers set by player break, place and use events, explosions, endermen, fire and worldgen, so every record knows who or what caused it.
4. **Writer thread and journal.** A background thread drains the queue, keeps the in-memory hot tail for live reads, and appends to the crash-safety journal every second.
5. **Packed files.** Column-by-column packing, compression, sealed epoch files per region, the per-section index with touched bitmaps, and journal recovery on startup.
6. **Noise rules.** Fluid settling, redstone blink detection, machine coalescing and crop stages, each switchable in config.
7. **Rewind points.** A full section save every `rewindPointInterval` changes in busy spots.
8. **Containers and tile entities.** Snapshots on container close, place, break and autosave, the opt-out list (AE2, RFTools, Data Vault, soul vault) and compression against the previous version.
9. **Player tracks.** Smart-sampled movement, look, pose, held items, armor and actions, one file per player per session. Weather and time jumps go in here too.
10. **Self-test and stats.** The autosave check that live blocks match their newest records, and `/timetravel stats` for disk use, records per hour and mismatches.

**Done when:** the recorder has run on the server through normal play for at least a few days. Lag is unchanged, the self-test shows no unexplained mismatches, and the real disk use per hour is known. That real figure replaces the estimate in the storage plan.

This is the phase I'd start with as soon as you say go.

## Phase 2: Timeline console and Chrono-Glasses

Goal: players can look into the past from the present. This is the first visible feature, and it carries no paradox risk.

- [ ] **Rewind engine.** Build a section's blocks at any moment T from the present, rewind points and the change records. Both Chrono-Glasses and real trips depend on this.
- [ ] **History streaming.** The server sends nearby block changes and track points for a time window to a client, in both directions. The client holds only what's on screen.
- [ ] **Timeline screen.** Player bars for each login and logout, axis in hours of play with offline time cut out, break marks with real dates, zoom and pan, hover cards, and coordinates obeying the visibility setting.
- [ ] **Cockpit console.** A new desk-style control in the TARDIS cockpit that opens the timeline. Clicking a moment sets it on the nav map.
- [ ] **Ghost rendering.** Player-shaped ghosts with full skin, armor, held items, arm swings and smooth movement. Punching one makes it glitch.
- [ ] **Chrono-Glasses.** The helmet item, a key to open the timeline, a view of the past for nearby chunks (outlines for blocks not built yet, missing blocks shown again), pause, rewind and fast forward, range and energy cost, and linking views with friends.

**Done when:** you can stand in your base wearing the glasses and watch it get built from the first day, with both of you as ghosts.

## Phase 3: Visiting the past (read-only)

Goal: a crew can fly to a past moment, walk around in it and come home, but can't change anything yet. Blocks can't be broken or placed in the past during this phase.

- [ ] **Past dimensions.** Create a temporary dimension for each crew, hidden from the TARDIS list, and fill its chunks with the rewind engine as they load. Natural ticking is off.
- [ ] **Crew and trip records.** Who is on the trip, the landing moment, the replay clock and the departure tick, saved so trips survive logouts and restarts. The safety net: a dimension is never deleted while any crew member is still out.
- [ ] **Flight and landing.** Pick a time on the console. The TARDIS stays in flight while the landing area rebuilds, and can't overlap its own ghost TARDIS.
- [ ] **Live replay.** Each tick the replay clock advances, block changes up to it play in loaded chunks, ghosts move and weather follows the record. Mobs never spawn.
- [ ] **Recording visits.** The crew's own movement is recorded as a visit, so later trips see their ghosts, including your earlier self.
- [ ] **Catching up and the seamless return.** Preload present chunks about 30 seconds ahead, skip the loading screen, play the vortex shimmer and keep position and movement. Ghost TARDISes can never be entered.
- [ ] **Rules in the past.** Respawn in the default dimension after death. Block ender chests, backpacks and AE2 wireless. Portals only between recorded dimensions, with a synced past copy. LOTR map travel works, and a never-generated chunk is generated and saved in the present too.
- [ ] **Hidden dimensions.** Past dimensions hidden from the TARDIS, Waystones and RFTools lists, plus the hide list in config.

**Done when:** the two of you can visit an old moment together, watch your past selves, stay until you catch up, and land back in the present smoothly.

## Phase 4: Changing the past

Goal: changes made in the past carry forward fairly, and duping is impossible.

- [ ] **Trip layers.** Store each trip's block and container changes in its own layer, stamped with the past moment they happened.
- [ ] **The forward rule.** A change survives only where no later record touches that block or slot, checked quickly with the touched bitmaps.
- [ ] **Applying forward.** Surviving changes fade into the present and into every open past dimension later in time, live, ordered by past moment across all crews. Players standing where a block appears get nudged clear.
- [ ] **Echo items.** Mark items taken from spots that later history also took. They're labeled and look distinct, fade when they leave the past dimension (including into the TARDIS interior), and anything crafted from them is a useless echo.
- [ ] **Ghost conflicts.** Ghosts glitch through obstacles, and a ghost mid-action glitches together with its target block.
- [ ] **Multiple crews.** Separate dimensions for each crew, one crew following another and seeing its TARDIS arrive, and the right order of changes between them.

**Done when:** one of you builds a wall in the past and it fades into the present. Trying to dupe ores or chest items in every way we can think of leaves you with nothing.

## Phase 5: Paradox, anchors and the Paradox Machine

Goal: the full risk and protection layer.

- [ ] **Paradox Meter.** One meter per crew, shown on screen and on the console. It's raised by echo items (scaled by item value), conflicting edits, forcing protected areas, and other crews in the same chunk. It decays in the present.
- [ ] **Stages.** Strained, Unstable and Fracturing effects: energy multipliers, travel cooldown, items glitching, rifts, causality switching off, chunks shifting and blocks randomizing.
- [ ] **Escape cost and collapse.** Escape energy that rises with paradox and shows on the console. Collapse deletes the dimension, kills the crew, applies item loss (config), loses the TARDIS exterior, wipes the trip's edits, and still applies to anyone who logged out.
- [ ] **Temporal Anchor.** 3×3 chunks, share list, backwards protection from your first build (Deep Anchor to the start), locked blocks for others, the veil and visit alerts.
- [ ] **Paradox Machine.** 5×5 chunks, Restore and Rewrite modes, archive of the replaced version, Paradox Collapse to undo, and a rewrite cooldown per area.
- [ ] **Progression.** Recipes and TARDIS level gates for the console, anchor and machine, with Chrono-Glasses kept the cheapest (artron only). Everything else runs on Chronons (see Energy and fuel).

**Done when:** a deliberate dupe attempt ends in collapse, an anchor stops a test intruder, and a Paradox Machine restores a blown-up test build.

### Energy and fuel

Aaron chose this on Oct 18, 2026.

- [ ] **Artron rework.** Much slower natural regeneration and much faster drain during flight, both config multipliers so they can be tuned. This changes existing gameplay, so it ships with Phase 5 rather than earlier.
- [ ] **Artron Charger.** Turns AE and RF into artron, losing a lot in conversion, so your power setups feed the TARDIS. It complements the existing artron-to-RF and artron-to-AE converters.
- [ ] **Chronons.** The time-travel fuel, stored in a Chronon Tank in the TARDIS.
- [ ] **Chronon refining.** A process that drains artron plus base materials (kontron and chronosteel) to make Chronons, built on the temporal basin machines.
- [ ] **Rift harvesting.** Rifts in an Unstable or worse past can be tapped for Chronons. It's a last-ditch way to scrape together enough fuel to escape, at the cost of staying longer in a collapsing world.
- [ ] **Trip cost.** A base cost plus a cost that grows with how far back you go, more slowly the further back, multiplied by crew size and by the Paradox Meter. The escape cost uses the same formula.

## Phase 6: Extras

These come after the main mod works end to end:

- [ ] **Mob ghosts.** Record mobs near players, with the radius and mob list in config, and replay them as untouchable ghosts.
- [ ] **Admin rollback.** Commands to restore an area to an earlier moment, using the same rewind engine.
- [ ] **Compaction.** Merge old history files (lossless compaction) for another 20 to 40 percent of disk savings.
- [ ] **Polish.** Sounds, rift and shimmer effects, and your own TARDIS map icon if you draw one.

## How each phase gets tested

1. Build and run the client and a local test server on your PC first, using the existing dev setup.
2. Before touching the real server, make a full backup of the Server folder.
3. Install the test build in the server's mods folder, keeping only one TardisTimeTravelAddon jar there.
4. Test the phase's "done when" goals.
5. Restore the backup, remove the test build and put the main build back.

Phase 1 is the exception: once it passes, the recorder should stay installed on the real server, since the point is to start collecting real history. Its records go in their own `history` folder and change nothing else in the world. Removing the mod just stops recording.

## Open items

- [ ] Settled: Chronons are the advanced fuel (see Phase 5, Energy and fuel). Exact costs get balanced during Phase 5 testing.
- [ ] Decide whether the recorder should stay running on the real server after Phase 1 testing
- [ ] Settled: ME drive, Data Vault and soul vault contents stay out of history (Aaron, Oct 18)
