# Item Causality Ripples Plan

Oct 28, 2026 · @Aaron

## Summary

Recommended: a **ripple debt** system. Taking an item in the past creates a debt of N items of that kind. The debt follows whoever would have received those items later, and is paid by removing the smallest possible set of things from the present. Paradox points are charged only for the part that cannot be removed at all.

- **Data cost:** under 50 KB per player per play hour of new data. That is a tenth of what the block recorder already writes (0.2 to 0.6 MB per hour). Most of the chain is already recorded: block placements and breaks carry the player, and container snapshots show who took what.
- **CPU cost:** a few cheap event hooks while playing. The heavy work runs once per past change, on the history writer thread, and only reads the ledger files of the players involved.
- **Feel:** items behave as interchangeable (64 iron is just 64 iron), so the game removes the least it has to. Spare stock goes first, then the newest things made from it. A craft is only unmade if the player had no spare items to make it with.
- **Reversible:** every ripple is stored with what it removed. Putting the item back in the past restores what it took, in reverse order. `/timetravel ripple undo last` reverses the last trip's ripples.

## What it builds on

About two thirds of the data and machinery already exists on the time-travel branch (PR #10 stack).

| Existing piece | What it gives ripples |
| --- | --- |
| Block records (`Recorder`, actor `p:<uuid>`, old + new state) | Who placed or broke every block and when, plus the state to put back when a placement is unmade. |
| Container snapshots (`TileTracker`) | A snapshot on every container close, tagged with the player. Comparing two snapshots shows what that player took or left, so no new hook is needed for chests. |
| `Containers` | Already counts items by kind, not slot, and works out the fewest of a kind a container holds up to the present. Taking up to that amount stays free with no ripple. |
| `Causality` amendments | Block changes that reach later pasts and the present. Unmaking a placed block becomes one more amendment, so later trips and the present get it for free. |
| `ParadoxRules` (`values`, grace, warn-first) | Item prices and the charge for anything left over. |
| `Echoes` | Today, a duplicate break gives echo drops plus paradox. With ripples, the drops become real, because the future player loses them instead. |
| Paradox offline list | The pattern for changes waiting for a player who is offline (applied at login). |

Missing pieces: crafting and smelting are not logged, items passed between players are not logged, and no record exists of what players carried over time.

## Data collection: the item ledger

The ledger stores **flows of item kinds between holders**, never individual items. A holder is a player, a container position, a machine, or a sink. It records only what existing history cannot work out later.

### What gets recorded

| Event | Source | Record (after packing) | Notes |
| --- | --- | --- | --- |
| Craft | `ItemCraftedEvent` (vanilla, LOTR tables, any `SlotCrafting`) | tick, player, output kind x count, input kinds x counts. About 20 to 30 bytes | Shift-click crafting fires once per craft. Identical crafts within 20 ticks merge into one record with a multiplier. |
| Smelt | `ItemSmeltedEvent` | tick, player, output kind x count | Input worked out from a cached reverse `FurnaceRecipes` map. Fuel is not tracked. |
| Hand-over | `EntityItemPickupEvent` when the item's thrower is another player | tick, from, to, kind x count | Covers trading by dropping items. Picking up your own drops or block drops is not logged, because the block record already names the breaker. |
| Our machines | Kinetics basin, press, crusher, faction mechanism (our code) | tick, machine position, inputs to outputs | Phase 5. The machine is the holder, and its input container snapshots say who filled it. |
| Inventory checkpoint | Logout, dimension change, autosave (every 5 min) | Kind to count map, stored only as changes from the player's last checkpoint | Usually 0 to 100 bytes. Gives the backward replay (below) fixed points to check against. |

Things that are **never** logged: hopper, belt and chute moves, AE2 traffic, and blocks broken by mobs. Automation shows up only as container snapshot changes that no player made, and is treated as a pool (see Edge cases).

### Tricks that keep it cheap

1. **Diff, don't log.** A player's take from or deposit to a chest is the difference between two `TileTracker` snapshots, worked out only when a ripple needs it. That costs nothing while playing.
2. **Backward replay instead of tracking stock.** How many iron a player had at any moment is rebuilt from the present inventory backwards through the ledger's flows. Checkpoints correct drift from untracked gains such as `/give`, villager trades and loot chests. Nothing is kept in memory between ripples.
3. **Hour blocks with a kind index.** Ledger files are `history/ledger/<uuid>/<hour>.ttl`. Each has a small bitset of the item kinds it mentions, so a search for iron skips every hour without iron. Kinds use the existing `Palette.item` ids, and ticks are stored as small differences.
4. **Same writer, same crash safety.** Records go through `HistoryWriter`'s journal like block records. While playing, the server thread only appends to a per-tick buffer.
5. **Nothing is built until it is needed.** There is no lineage graph. A ripple reads only the files of the players its chain reaches, from the take tick up to now.

### Estimates

| Item | Estimate |
| --- | --- |
| Ledger on disk, active player | 10 to 45 KB per play hour (300 to 1,500 crafts after merging) + about 3 KB of checkpoints |
| Server with 5 players, 4 hours a day | about 1 MB per day, about 30 MB per month (block history is about 100 to 360 MB per month) |
| Memory while playing | One buffer per online player, under 10 KB each |
| Memory during a ripple | The hour blocks involved, typically under 2 MB, freed afterwards |
| CPU while playing | One map lookup and an append per craft, smelt or hand-over |
| CPU per ripple | Usually milliseconds on the writer thread. Limited by `ripples.maxNodes` (2,000) and `maxDepth` (6). |

These are estimates. Phase 1 adds `/timetravel ledger stats` so the real numbers can be measured on the server before any ripple code is built.

## Ripple resolution

Each past change that takes items out of the timeline creates a **debt**: N of kind K, owed by whoever would have got them, starting at the tick they got them. The resolver pays debts using the **least-change rule**: remove what nobody used before touching anything that was made from it.

### What starts a debt

- **Taking from a container beyond the free amount.** Today `Containers` sends the extra back. With ripples on, the take is allowed, and the debt goes to the first later snapshot where that kind dropped, owed by the player who closed that container.
- **Breaking a block someone breaks later** (today a paradox duplicate with echo drops). The debt goes to the player named in the later break record, as the block's drop kinds.
- **Killing or capturing a mob, or taking dropped items** that a recorded player later picked up. Phase 5, and only if mob recording is on.

### Following one debt

For the debtor P, kind K and amount N from tick t1:

1. **Slack.** Replay P's count of K backwards from the present to t1. If P never had fewer than N, the debt is absorbed: P simply has N fewer now. Remove them from P's present inventory, then ender chest. Nothing else changes.
2. **Otherwise walk P's uses of K after t1, oldest first** (first in, first out), only as far as the shortfall reaches:
   - **Deposit** into a container: the debt moves to that container. Take from its present contents if they are there, otherwise follow whoever took K from it next.
   - **Craft or smelt**: the share of the outputs that the missing inputs paid for becomes new debts (for example, 1 of 3 iron missing means 1 of 3 parts of the pickaxe output). Fractions round up only at the end of the chain.
   - **Placed as a block**: the block is unmade. It becomes a `Causality` amendment at the placement tick that sets the old state from the block record. Later pasts and the present pick it up in the usual way.
   - **Hand-over** to another player: the debt moves to them.
   - **Gone without lasting trace** (eaten, burned as fuel, tool worn out, despawned, thrown in lava): absorbed for free. Nothing that still exists depended on it.
3. **Never make things worse.** A craft is only unmade when the player had no spare K at that moment. If they could have used other K (the slack test at the craft's tick), the debt jumps to that spare K instead.

### What removal means, by preference

| Order | Where the debt ends up | What happens |
| --- | --- | --- |
| 1 | Present inventory, ender chest or container | Items quietly removed. Online players see the slot flicker; offline players lose them at login. |
| 2 | A fungible store we can query: our Data Vault, an AE2 grid the player deposited into | `extractItems(K, n)`. AE2 storage is interchangeable, so a smaller total is realistic. |
| 3 | Placed blocks | Unmade through `Causality`, newest first within the same craft batch. Tile entity contents spill as a further debt to the block's owner. |
| 4 | Crafted item still in existence | The whole item is removed if its full share is owed. A partly owed tool loses that share of its durability, which costs nothing to work out and feels right. |
| 5 | Nowhere reachable | Remainder goes to paradox (next section). |

All removals of one trigger are collected into one **ripple set** first and checked against the budget. Then they are applied in one tick. The player sees a preview before the first take or break that would cost something (see Edge cases).

The diagram below shows one debt being followed.

Only the red box costs paradox points. Every other outcome either removes something real or costs nothing.

## Paradox only as a last resort

Points are charged only for the **unresolved remainder** of a ripple set, priced with the existing `values` table and grace. A fully resolved ripple costs 0 points, however long its chain.

The remainder exists only when the debt reaches something that cannot be removed:

- **Black-box storage that cannot be searched:** RFTools storage, opt-out containers, or AE2 grids with no link to the player.
- **Left the recorded world:** taken into a TARDIS interior (not recorded) or a dimension outside `recorder.dimensions`. As a step before paradox, the resolver first tries the player's present inventory and their TARDIS's Data Vault.
- **Given to an NPC:** LOTR trades, hired-unit gear, soul nexus contents.
- **Protected:** blocks under a Temporal Anchor, TARDIS blocks, unbreakable blocks.
- **Over budget:** the chain went past `maxNodes` or `maxDepth`.

With ripples on, a duplicate block break gives **real** drops when its debt resolves fully. Echo drops are only for the share that went to paradox. Warn-first (`warnFirstPoints`) shows the remainder's points, not the item's full value.

## Reversal

Every ripple set is saved with enough to undo it exactly: the trigger, and each removal with what was there (item kind and count plus holder, block position plus removed state and tile entity NBT, AE2 extraction, Causality amendment id, paradox points charged). It is stored in overworld saved data `tardistimetravel_ripples` and kept until the set is fully undone or older than `ripples.keepDays` (30).

### Putting the item back in the past

The debt is cancelled when the same kind is returned to the same holder at a moment no later than the first time someone wanted it (t1 for that debt):

- **Container:** leaving K in that container on any trip with its playhead before t1. `Containers` already checks that it fits. The amount left is matched to open debts on that container and kind, newest first.
- **Block:** placing the same block back at the same position before the later break.
- **Anyone can return it.** Any K counts, not just the stack that was taken. Echo items don't count, because they aren't real.

A return of n items undoes ripple removals worth n, in the reverse order they were applied. Restoring follows these rules:

- **Items** go back to the holder. If the inventory is full, they drop at the player or wait for their login.
- **Blocks** come back only if the position still holds what the ripple left there. If someone has built there since, the block's item goes to its original owner instead.
- **AE2 / Data Vault** get the items inserted back. If there's no room, they go to the owner.
- **Paradox points** charged for the remainder are refunded to that trip's meter if it is still open, and to the players' instability.

A partial return gives a partial undo. Returning more than was taken is an ordinary deposit.

### The command

| Command | Who | Effect |
| --- | --- | --- |
| `/timetravel ripple list [player]` | ops; players see their own | Ripple sets with trip, trigger, number of removals, points, and undone share |
| `/timetravel ripple info <id>` | same | Every removal in the set |
| `/timetravel ripple preview <id>` | same | What an undo would restore and what it would skip (occupied spots) |
| `/timetravel ripple undo last [player]` | ops; players with `ripples.playersUndoOwn` | Undoes every ripple set from that player's most recent trip, newest first |
| `/timetravel ripple undo <id>` | same | Undoes one set |

Undo reverses the ripples only, not the trip's own block edits. The existing admin rollback (`Rewinder`) stays the tool for those. An option `undo last --all` can also revert that trip's Causality and container amendments, using the same restore rules.

## Edge cases, config and phases

### Edge cases

- **When it lands.** Debts are worked out at once and shown as a preview. They are applied to the present when the trip ends (catch-up, take-off or return), so a player who puts the item back during the same trip causes no churn. Config `ripples.apply = tripEnd | immediate`.
- **Preview and warn-first.** The first take or break that starts a ripple is held once, with a chat summary such as "Taking 10 iron unmakes: Alex's iron pickaxe, 6 rails". Repeating the action within `warnFirstSeconds` goes ahead, as for paradox.
- **Your own future.** Taking from your own chest in the past can unmake your own present pickaxe. That is intended. The item you carry out is the one taken.
- **Automation pools.** If a container's count drops with no player closing it (hoppers, belts), the debt goes to the connected pool: the container below or beside it with a later snapshot. If that is not found within 3 hops, it goes to paradox.
- **Loops.** A chain that returns K to the starting container ends there, because the debt is paid. Visited (holder, kind, tick) entries are skipped.
- **Two trips, one chest.** Debts are ordered by take tick. A later trip only sees the items left after earlier ripples (`Containers` points already include amendments).
- **Creative players** in the chain: their stock is treated as unlimited (absorbed), as `ParadoxRules` already does.
- **Offline holders:** removals wait in a per-player list and apply at login, like paradox victims.

### Config (`ripples`)

| Key | Default | Meaning |
| --- | --- | --- |
| `enabled` | true | Off = today's rules (free amount only, duplicates go to paradox) |
| `apply` | tripEnd | When removals hit the present |
| `maxDepth` / `maxNodes` | 6 / 2000 | Budget per ripple set; the rest goes to paradox |
| `fungibleStores` | true | Allow extraction from Data Vault and AE2 |
| `durabilityShare` | true | Partly owed tools lose durability instead of vanishing |
| `playersUndoOwn` | false | Players may run `ripple undo last` for their own trips |
| `keepDays` | 30 | How long ripple sets stay undoable |
| `ledger.checkpointMinutes` / `mergeTicks` | 5 / 20 | Ledger settings |

### Phases

1. **Ledger capture.** Craft, smelt, hand-over and checkpoint records, plus `/timetravel ledger stats`. Run it on the real server for a week to measure real sizes. No gameplay change.
2. **Resolver as dry run.** `/timetravel ripple preview` for a planned take or break shows the full chain without applying anything. Tune the least-change rule here.
3. **Apply.** Ripple sets, present removals, block unmaking through Causality, the offline list, paradox for the remainder, real drops instead of echoes, and the chat preview.
4. **Reversal.** Return detection, restore rules, and the `ripple list / info / undo` commands.
5. **Wider reach.** AE2 and Data Vault extraction, kinetics machine hooks, mob drops, and a client effect for unmade items.

### Testing

Use the same temporary dev-server harness as before. Phase 1 must pass with crafts, smelts and hand-overs logged and stats matching. A scripted chain to check end to end: a chest of 9 iron in the past, a later player crafts a pickaxe and rails and places the rails. Taking 3 iron must remove the spare iron first. Taking all 9 must unmake the pickaxe and the rails. Returning the 9 must restore everything. `ripple undo last` must do the same, and the test is repeated with the player offline.
