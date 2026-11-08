# Time Travel: History Storage Plan

Oct 18, 2026 · @Aaron

## Summary

The recorder keeps every block change and every player movement losslessly. For a 2 to 5 player server it should need about 0.2 to 0.6 MB per hour of active play, or roughly 0.2 to 0.6 GB for 1,000 hours. Those figures are estimates; the first build will measure the real ones.

Five ideas keep it small:

- **Rewind from the present.** The past is built by taking today's chunks and undoing changes backwards. No chunk copies are saved, except rewind points in very busy spots so a rewind never takes too long.
- **Tiny change records.** Each block change is a few numbers (when, where, from what, to what, by whom). Packed and compressed, that is about 2 to 4 bytes per change.
- **Columnar compression.** Changes are stored grouped by field: all the times together, then all the positions, then all the blocks. Similar values side by side compress 2 to 3 times better than mixed rows.
- **Smart sampling for players.** A movement point is saved only when a player stops moving in a straight line. That gives smooth ghosts at about 3 points a second instead of 20.
- **Squash only the noise.** Water flow, redstone clocks and machine ticks are merged into short summaries. Nothing a player or mob does is ever dropped.

On top of that, the recording itself never slows the game. The server only copies a few numbers into a queue, and a background thread does the compressing and writing.

## What gets recorded, and the clock

History is kept for the dimensions in a config list (default: Overworld and Middle-earth). TARDIS interiors are never recorded.

| Stream | What it holds | Fidelity |
| --- | --- | --- |
| Block changes | Every block placed, broken or changed, with its cause | Lossless; noise is merged (see Noise control) |
| Rewind points | A busy section's full state, once per 50,000 changes (config) | Exact |
| Containers and tile entities | Chest contents, signs, banners, LOTR tables | Exact at each change, by snapshot |
| Player tracks | Position, look, pose, held items, armor, arm swings | Smooth; within 1/32 of a block |
| Visits | The same tracks and edits for time travelers, tagged with their trip | Same as above |
| Session table | When the server was up and who was online | Exact |

Mobs, animals and NPCs are not recorded; the past spawns its own.

**The clock.** Every record is stamped with the world's total tick count. Minecraft only advances that counter while the server runs, so time when nobody was on is skipped automatically and the timeline has no gaps. The session table maps ticks to real dates for the timeline console.

**Block identity.** Blocks are stored by name (for example `lotr:tile.brick` with metadata 3), never by numeric ID. In 1.7.10 numeric IDs can move when the mod list changes; a name stays correct. A per-world table maps each name to a small permanent number, so every record stays compact.

## Block changes

Every block change in the game passes through one method in the chunk code, so a single coremod hook there catches all of them: players, mobs, explosions, other mods' machines, and even WorldEdit. Forge events alone would miss a lot.

**Cause.** Before the game runs something that can change blocks, a small marker records who is acting. A player breaking or placing, a creeper exploding and an enderman picking up a block each set the marker, and the hook reads it. Anything without a marker is filed as natural. Worldgen during chunk population is tagged too, since trees from a neighbor chunk can spill into one that was already recorded.

**One record** holds, before packing:

| Field | Size | Notes |
| --- | --- | --- |
| Time | 1–2 bytes | Ticks since the previous record in the same section |
| Position | 12 bits | Place inside the 16×16×16 section |
| Old block, new block | 1 byte each, usually | Index into a small table of blocks used in this file |
| Actor | 1 byte, usually | Index into a table: a player, a mob type, natural or worldgen |
| Flags | a few bits | Cause type, and whether a container snapshot follows |

That is about 6 to 8 bytes raw. Storing the old block as well as the new one costs a byte but makes history readable in both directions, so scrubbing backwards is as cheap as forwards.

**Columnar packing.** A finished batch is written column by column: all the time gaps, then all the positions, and so on. Each column is mostly small, repetitive numbers, so standard compression shrinks the batch to about 2 to 4 bytes per change. Java's built-in Deflater is enough, which avoids adding a native library to the pack.

**Bulk events** get one record instead of hundreds. An explosion is stored as its center, its size and a list of the blocks it removed. A tree felled by a mod, or a quarry layer, is stored as a run of positions.

## Rewinding from the present

The past is rebuilt by starting from the present world and undoing changes backwards, not from the seed (1.7.10 worldgen does not regenerate the same terrain reliably) and not from stored chunk copies. No chunk is ever snapshotted just because it changed.

**Rewinding.** Every change record holds both the old and the new block. To see a chunk at moment T, read the present chunk straight from the live world (no copy of the world file), then walk its changes from newest to oldest, putting back each old block, until reaching T. Untouched areas need no work at all. Container and sign data rewinds the same way, from its snapshots.

**Rewind points.** Rewinding a busy base by a year could mean undoing hundreds of thousands of changes per chunk. So when a 16×16×16 section collects 50,000 changes since its last rewind point, its full state is saved as a new rewind point. A rewind then starts from the nearest saved point after T, or from the present, whichever is closer. Quiet areas never get one, so this costs very little disk. The 50,000 figure is the config option `rewindPointInterval`: lower means faster rewinds and more disk, higher means the reverse.

**Rewind point format.** A section is stored as a small block table (usually under 32 entries) plus 4,096 packed indexes, at 5 bits each for a 32-entry table. Lighting is not stored, since the game recalculates it. A compressed section is typically 200 to 800 bytes.

**No gaps on purpose.** Rewinding needs every change recorded, so grass and mycelium spread are recorded too. Anything a mod changes without going through the hook is caught by the self-test.

**History before install.** Rewinding stops at the oldest record, so anything before the recorder was installed shows the terrain as it was on install day. On the timeline that time is an unrecorded era.

## Noise control

A few kinds of change can produce thousands of records a minute while meaning almost nothing. Each kind gets its own rule, and every rule is a config option.

| Kind | Default rule | Why |
| --- | --- | --- |
| Player, mob, explosion | Record everything | This is the history people care about |
| Fire | Record everything | Burned builds should show in the past |
| Flowing water and lava | Record the settled result about 5 seconds after flow stops | One record per final block instead of every flow step |
| Redstone clocks, lamps, pistons, doors | Detect repeating flips; store one "blinking" record with start, period and end | A clock flipping 10 times a second would otherwise fill the disk |
| Crop growth | Record growth stages | Only a few stages per crop |
| Leaf decay | Record everything | Rare, and needed for felled trees to look right |
| Grass and mycelium spread | Record each spread | Needed for rewinding; spread only happens near players, so it's a few thousand records an hour at most |
| Machines changing their own block state | Coalesce to once per 10 seconds per block | Furnaces and RFTools machines flicker between on and off |

Coalescing is lossless in what a viewer sees. A blinking lamp still blinks in the past, and water still ends up where it ended up. Only the in-between steps are rebuilt instead of stored.

## Containers and tile entities

Chests, signs, banners and other blocks with stored data (tile entities) are captured as snapshots of that data. Logging every slot change would be far larger, and other mods' inventories would be hard to hook reliably.

**When a snapshot is taken:**

- When a player closes a container's screen. The snapshot is tagged with that player, which is what the anti-dupe rule needs.
- When the block is placed or broken.
- At each autosave (about every 45 seconds), for any tile entity whose data changed since last time. A quick fingerprint of the data spots the changes, so unchanged ones cost nothing.

**Keeping it small.** Each new snapshot is compressed using the previous version of the same block as a reference, so a chest where one stack moved compresses to a few dozen bytes. Item stacks go into a shared table, like blocks do.

**Opt-out list.** Some blocks hold huge, constantly changing data that is useless to replay: AE2 drives and cables, RFTools machines, and MyMod's own Data Vault and soul vault. By default these record only that the block exists, not what is inside. The list is a config option. That also keeps the past from holding a second copy of an ME network's contents.

**For the anti-dupe check,** two snapshots in a row show which items left a container and who closed it. That is enough to answer "did someone later take this item?" without tracking each item individually.

## Player and ghost tracks

Ghosts need to move as smoothly as the real player did, but storing all 20 positions a second is wasteful.

**Smart sampling.** The recorder keeps guessing where the player will be if they carry on in a straight line. A new point is saved only when the real position drifts more than 1/32 of a block from the guess, or the look direction drifts more than about 1.5 degrees. Walking in a line saves about 1 point a second, and fighting or building saves more. Playback fills the gaps with smooth curves.

**Packing.** Positions are stored at 1/32-block precision and look angles at 256 steps per circle. That is the same precision vanilla multiplayer uses, so a ghost looks exactly like the player did to others. Each point is stored as a difference from the previous one, at about 4 to 6 bytes.

**Actions** are separate small records: arm swing, sneak, sprint, riding, sleeping, and changes to held item or armor. An item is stored once in a table, so a ghost shows the right sword, enchant glint and armor.

**Cost:** about 20 to 50 KB per player per hour after compression, which is tiny next to block history.

**Time travelers** are recorded the same way, but filed under the past moment they visited, tagged with a trip number. When someone later visits that moment, every trip that overlaps it plays back alongside the original recording, which is how you see your earlier self watching.

## Files, writing and crash safety

History lives in a `history` folder inside the world save, split by dimension and by 32×32-chunk region, like Minecraft's own region files.

```
world/history/
  palette.dat           block and item name tables (permanent numbers)
  sessions.dat          uptime and who was online, tick to real date
  DIM0/r.0.-1/
    e0042.tth           one epoch: change batches + rewind points for this region
    index.dat           per section: which epochs hold its changes and rewind points
  DIM100/...            Middle-earth
  players/<uuid>/       player tracks, one file per play session
  visits/<trip>/        tracks and edits made by time travelers
  journal.log           crash-safety journal (recent, not yet sealed)
```

**Write path:**

1. The hook copies a few numbers into an in-memory queue. That is the only work done on the game thread, well under a microsecond per change.
2. A background thread drains the queue, groups records by section and appends them to the journal. The journal is flushed to disk every second, so a crash loses at most a second.
3. Every 10 minutes of play, or every 50,000 records, each region's batch is packed column by column, compressed and sealed into its current epoch file. The journal is then trimmed.
4. On startup, anything left in the journal is sealed first. A cut-off last entry is detected by its checksum and dropped.

**Live reads (the hot tail).** Records less than about 10 minutes old stay in memory, indexed by section, and can be read the same tick they're written. Every lookup checks the sealed files plus this hot tail, so a crew following another crew sees its ghosts and changes with no delay. A change made in the past is also pushed straight to every open past dimension later in time, so it can fade in there right away. Everything runs inside one server process, so none of this waits on the disk.

**Compaction.** Small sealed batches compress worse than big ones. A low-priority task merges a region's batches into one block per in-game day of play, which saves another 20 to 40 percent. It also rewrites the index so seeking stays fast. Nothing is ever thrown away. An optional, off-by-default setting can thin natural records older than a chosen age.

## Rebuilding the past and scrubbing

**Building a past chunk at moment T** happens when the past dimension asks for that chunk:

1. The present chunk is read straight from the live world.
2. For each section, the index finds the nearest rewind point after T, if one is closer than the present, and starts from it.
3. Changes newer than T are undone from newest to oldest, putting back each old block. Container and sign snapshots at or before T are applied.
4. Changes made by earlier time travelers on trips to this era are layered on top (see the next section).

While the TARDIS is in flight, the landing area (about 8 chunks around it, a config option) is rebuilt, and the TARDIS doesn't land until that is ready. The rest is rebuilt in the background as players walk outward. A rewind is capped by `rewindPointInterval`, so even the busiest chunk takes a fraction of a second. When everyone leaves, the past dimension is deleted, because it can always be rebuilt exactly.

**Scrubbing** on the timeline console or the Chrono-Glasses does not rebuild chunks. The server sends the client the changes for the nearby area between the old and new times, and the client plays them forwards or backwards. Each change holds both its old and new block, so dragging the timeline backwards is just as cheap. That is what makes walls and chests appear around you.

**Ghosts** stream the same way: the server sends nearby track points a few seconds ahead, and the client smooths between them.

## Changes made in the past

Edits made by time travelers never overwrite recorded history. They are stored in a separate layer for each trip, using the same record format, stamped with the past moment where they happened. That keeps the original history intact, so any trip can be undone or archived.

**The forward rule** (an edit survives only where nothing later touched that spot) needs one fast question: did any recorded change touch this block after time T? Every epoch's index carries a 4,096-bit "touched" map for each section it covers, 512 bytes before compression and far less after. Answering the question means checking a few bitmaps, with no change batches to unpack. Containers use the same check, on the slots that changed between two snapshots.

**When a trip ends:**

- Edits that pass the rule are written into the present as ordinary changes, with the cause "time travel" and a fade-in effect. They then become part of history like anything else.
- Items taken from spots that a later recording also took get an echo tag holding the trip number, and fade when their holder returns to the present.
- A Paradox Machine or Temporal Anchor marks its area as an override, so edits there win even against later history. The history they overwrite stays on disk, which is the archive; Paradox Collapse simply replays it.
- A trip that ends in collapse has all its edits deleted, so nothing from it ever reaches the present. The crew's movement tracks are kept, so later visitors can still see their ghosts.

## Size, fidelity checks and risks

A typical hour with 2 or 3 players on should use about 0.2 to 0.6 MB. These numbers are estimates from the record sizes above; the recorder will report the real ones.

| Stream | Per active hour | Main driver |
| --- | --- | --- |
| Block changes | 30–120 KB | 10,000–40,000 changes at about 3 bytes each |
| Rewind points | 10–50 KB | Only busy sections, once per 50,000 changes |
| Container and tile entity snapshots | 50–300 KB | How much chest and sign activity there is |
| Player tracks | 60–150 KB | 2 or 3 players at 20–50 KB each |
| **Total** | **about 0.2–0.6 MB** | Heavy sessions with WorldEdit or quarries could reach 3 MB |

At that rate, 1,000 hours of play is roughly 0.2 to 0.6 GB, and compaction trims that further.

**Fidelity checks.** A history that is quietly wrong is worse than none, so the recorder tests itself:

- At each autosave it picks a few random recorded chunks and checks that every block in the live world matches the newest record for that spot. A mismatch means a change happened without being recorded, which would break rewinding. Mismatches are logged with their position and block, which shows exactly which mod or mechanic slipped past the hook.
- A `/timetravel stats` command shows disk use per dimension and stream, records per hour, and the mismatch rate.

**Risks:**

- **Mods that bypass the normal block-setting method.** A few mods write directly into chunk data. The self-test will catch them, and they can get their own hook.
- **Removing a mod from the pack.** Its blocks vanish from the live world, so a rewind starting from the present loses them too. Where change records or rewind points name those blocks, the past shows a placeholder block instead.
- **Disk running low.** A configurable size cap warns admins in chat. The recorder never deletes history on its own.
- **Backups.** The history folder lives in the world save, so world backups grow with it. It can be backed up separately if that gets too large.
