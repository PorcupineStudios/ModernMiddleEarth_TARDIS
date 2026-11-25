# Interactive past: mobs and past players

Oct 29, 2026 · @Aaron

Travellers can step into a recorded moment and change it. They can kill a creeper before it blows, pull a past player out of a fight, or lure a zombie away. History then bends forward from that point, as far as the records can trace it, and no further.

## Immersion rules

Every feature below has to keep these rules. They are the test for each design choice.

1. **The past never visibly cheats.** A ghost never teleports, pops, flickers or fades in a traveller's sight. Any jump back onto the recording happens only when no traveller can see it.
2. **Touching the past makes it real.** A replay you disturb becomes a living entity, starting exactly where it stood, with no flash or swap. From then on it runs on normal Minecraft rules.
3. **Consequences follow causes.** What changes downstream is only what the records tie to your action: the blocks that mob broke, the hits it landed, the death it caused. Nothing is invented and nothing is waved away.
4. **Saving or ending a life has a real cost.** It is the biggest change a traveller can make, so it shows in the present and on the paradox meter.
5. **The world is the same on every visit.** A change, once made, is stored with history, so a later trip to the same moment sees the changed past.

## What the past is today

Today, past mobs and past players are only replays. They look real, but nothing a traveller does can reach them.

| Thing | What is recorded | How it plays in the past | What a traveller can do |
| --- | --- | --- | --- |
| Past players | Movement, look, pose, swings, held item, armour | Solid stand-ins (`GhostEntity`) | Punching makes them glitch, nothing else |
| Past mobs | Type, baby flag, position and turns (waypoints since today) | Client-only look-alikes | Nothing: they can't be hit or pushed |
| Blocks they changed | Each change, with the actor's type (`e:Creeper`), not which creeper | Played back as the playhead moves | Can't be stopped |
| Their items | Death drops, as ledger tosses | Not shown | Ripples can trace them |

Three things are missing for interaction:

- **Which mob did it.** Block changes name only the mob type, so an explosion can't be tied to the one creeper you killed.
- **The state behind the look.** Health, a creeper's fuse, a mob's target and a player's health are not recorded.
- **Deaths and hits.** No record says that this zombie hit that player for 4 hearts, or that the player died at tick X because of it.

## Core idea: touched replays come alive

A replay stays a replay until a traveller disturbs it. At that moment it becomes a real entity in the past dimension. It starts from its recorded state and the swap can't be seen. Everything the recording says it did after that moment is cut, and only what traces back to it is cut.

**What counts as disturbing** (any one of these):

- A traveller hits it, shoots it, or splashes a potion on it.
- A traveller pushes into it, stands in its recorded path, or blocks it with a placed block.
- A hostile mob gets a traveller in its sight within its normal follow range, so it can choose to hunt them.
- A live entity damages it, for example an earlier promoted mob or TNT a traveller lit.

**The swap.** The server spawns the mob type at the replay's exact position, turn and head turn, with its recorded health, fuse, target, baby flag, wool colour and so on. The client keeps the same model on screen, so nothing pops. The replay copy is retired.

**Cutting its recorded future.** From the swap tick on, the past stops playing anything that belongs to that mob:

- its later track points,
- block changes it made (a creeper's crater, an enderman's picked-up block),
- hits it landed and deaths it caused,
- items it picked up or dropped.

This needs every record to name the exact mob (its UUID), not just its type. That is the first recording change in the build plan.

**The live mob follows the past's rules.** It never leaves the past dimension, and it can't fight replays, only travellers and other live entities. A promoted mob that is still alive when the trip ends goes back to its recording if nothing it did changed history. Otherwise it is stored as a change, like a placed block.

## Creepers and hostile mobs

Killing a creeper before its recorded explosion means the explosion never happens. The crater stays solid in the past, and the present gets those blocks back unless something later changed them.

How a creeper plays out:

1. The recording shows the creeper hiss at tick T and blow at T+30. The fuse start is a new recorded event.
2. The traveller hits it at T+12. It turns live with 18 ticks of fuse left and the fuse still lit.
3. Kill it in time: no explosion. Its crater records are cut, so the past shows the blocks intact.
4. Miss: the live creeper explodes where it now stands. That is a real explosion in the past, recorded as the traveller's doing, since they set it off by disturbing it.
5. When the trip ends, the cut crater reaches the present through the existing past-to-present path (`Causality`). Each block comes back only if nothing in history touched it after the explosion. That check is the same one used for blocks edited in the past today.

The same rules cover other mobs:

| Mob | What a traveller can change | What follows in the present |
| --- | --- | --- |
| Creeper | Stop or move the explosion | Crater blocks restored, or a new crater made |
| Enderman | Stop it taking a block | The block stays where it was |
| Zombie at a door (hard mode) | Kill it before the door breaks | The door stays |
| Skeleton or zombie fighting a past player | Kill or distract it | The player's damage and death may be undone (next section) |
| Animal killed by a past player | Lead it away before the kill | The kill's drops and the food eaten are a ripple, like a past container take |
| Any mob that is still alive in the present | Kill it in the past | It is removed from the present when its chunk loads. Its drops in the past are the traveller's, and any items it carried go with it |

Paradox: killing a mob that changed nothing costs nothing. Undoing what it did costs the same as the ripple it causes: blocks, items, or a saved life.

## Past players: saving and ending lives

A past player is saved when the damage a traveller cut out of history is what killed them. The real player then gets back what that death cost them. Past players' own choices stay fixed: you can stop the zombie, but you can't stop them jumping into lava.

### Saving a life

The recording gets two cheap new events for players. One is **health on change**. The other is **hurt**: who hit them, how hard, and with what. To decide whether a death is undone:

1. Take the recorded health line.
2. Add back every hit that has been cut, because its source turned live and was killed, led away or blocked.
3. If health at the death tick is still above zero, the death is averted.
4. If other damage still kills them (fire, a fall, a second zombie nobody touched), they still die. The trip shows that death exactly as recorded.

Deaths from the world itself (lava, falling, drowning, the void) can't be undone. Changing them would mean re-running the player's own movements, and their choices belong to them.

### What the real player gets back in the present

When the trip ends, a saved life ripples forward like any other change:

- **Items:** everything that dropped on that death comes back. The ledger already logs death drops. If someone else picked them up, those items ripple out of their hands, chests or crafts, exactly as with a past container take.
- **Grave:** a GraveStone grave from that death is removed and its contents returned.
- **XP and stats:** the XP lost comes back, and the death is taken off their death count.
- **Where they are:** unchanged. They still respawned and walked on, so their present position stays.
- **What they feel:** one quiet line on next login, "You remember dying near the river. The world no longer does." No popup.

The traveller pays paradox for the items restored plus a fixed cost for the life. It is refunded if the save is undone, like any ripple.

### Ending a life

A traveller still can't punch a past player to death (today's rule stays). They can cause a death indirectly: an angry creeper they woke blows up beside a past player, or they lead a skeleton into a fight. For immersion, a past player near a live explosion should flinch, take knockback and lose health. Damage that passes through them would break the rules above.

If live damage takes a past player's health to zero, that player dies in the past. The timeline then mends around that death (see "When a past player dies: the timeline mends" below).

### Meeting yourself

The same rules apply when the traveller is the past player. Saving your past self returns your own lost items. Getting your past self killed costs you whatever the rule for ending a life says.

## Survivors and reconvergence

A disturbed mob or past player that survives goes back to its recording only when no traveller can see the moment it happens. Until then it acts on its own, so the past never visibly snaps back.

**Unseen** means no traveller within render range has it in their field of view with a clear line of sight. Chrono-Glasses viewers in the present count too.

**Mobs.** A mob that turned live but changed nothing (hit once and walked off) runs normal AI for a while, then paths toward where its recording is at that moment. Once it is within a block of that point and unseen, the live mob is retired and the replay picks up. If it never gets back by the end of the trip, it is stored as a change.

**Saved past players.** The recording after a saved death is useless: it starts with a respawn at their bed. The survivor finishes the moment (the fight ends, they back away), then walks toward their recorded respawn point, as if they went home. The recording resumes once they are out of sight or arrive there. A traveller who follows them the whole way sees them walk home.

**Nudged past players.** Knockback or a blocked path moves a past player off their track by a little. They side-step back onto it over a few seconds, again with the final step taken only when unseen. Their own recorded swings and block changes keep playing, so their story still holds.

### Blocked paths: reroute, mine, catch up

A past player whose recorded path is blocked by a traveller's change finds their own way through, then makes up the lost time from slack. They react like a real player would: they go around what they can see, and they dig only when going around costs too much.

Each tick the stand-in checks the next stretch of its recorded path against the past world. When it hits a change (a placed wall, a mined-out gap, a pit, water poured in its way), it picks the cheapest of these:

1. **Step around.** It pathfinds through the past world to the first point further along its own recording that it can reach. A short detour costs a few seconds of delay.
2. **Jump or climb.** It crosses a one-block step or a gap of up to 3 blocks, as vanilla players do.
3. **Mine through.** If the detour costs more than mining, and its recorded hotbar has a tool that can break the block, it mines at that tool's real speed, with the swing and crack animation. Without a tool it punches through soft blocks only (dirt, sand, leaves, glass). The broken block drops on the ground as a real item, because it was the traveller's block.
4. **Wait.** If it is boxed in with nothing it can break, it waits at the wall and looks around. The delay grows.

Once it is back on its track, it is running late. Slack absorbs the delay exactly as after a death: idle stretches are skipped until it is back in sync.

Limits that keep it believable:

- **It avoids what it can see.** It won't walk into lava, a visible pit or a mob it would have fled. A hidden trap (a hole covered by a carpet) can still catch it, and that is the traveller's doing, with the paradox that brings.
- **It never mines what its owner wouldn't.** It won't break other players' doors, chests or beds, or anything a protection mod guards. Then it detours or waits.
- **Its recorded building still happens where it can.** If a block it recorded placing now has a traveller's block in the way, the placement fails, like any contradiction under the paradox rules. Every other placement goes ahead.
- **The delay budget is shared.** Reroutes, deaths and knockback all add to one running delay per past player. If slack can't pay it back before the trip ends, the rest is paradox for whoever caused it.

**What the traveller does to the past world stays.** A wall the past player mined through is gone in the past, so it never reaches the present. The rerouted path is stored with the trip, so a later visit to that moment sees the same detour.

## What to record, and build order

The recording additions are small: rare events plus a few numbers per mob. My estimate is under 10% on top of today's mob tracks. The block format doesn't change.

| New record | Where | When written | Size |
| --- | --- | --- | --- |
| Mob state: health, fuse, target, wool colour, profession, age, saddle, sheared | Mob track, on each appear | At file start and when the mob is first seen | About 10 bytes per mob |
| Mob events: fuse lit, explode (radius), hurt (who, how much), death (killer) | Mob track | When they happen | A few bytes each, rare |
| Player health | Player track | When it changes | 2 bytes |
| Player hurt and death (source mob or cause) | Player track | When they happen | Rare |

The explosion event is what lets a crater be cut without naming each mob in block records. Block changes at that tick, inside that radius, with actor `e:Creeper`, belong to that creeper.

### Build order

Each phase works and can ship on its own.

| Phase | Builds | What players notice |
| --- | --- | --- |
| 1. Causes | The records above | Chrono-Glasses can show who hit whom and why someone died |
| 2. Live mobs | Disturbing a replay mob swaps it for a live one; its later track is cut | Past mobs can be fought, pushed and led |
| 3. Mob consequences | Cut craters and broken blocks; killed mobs leave the present; drops | Killing the creeper saves the house, now and in the present |
| 4. Saving lives | Health check at the death tick; item, grave, XP and stat ripples; the survivor walks home | Rescuing a friend's past self gets them their stuff back |
| 5. Reconvergence | Line-of-sight check; mobs and nudged players walk back and rejoin unseen | Nothing ever snaps back in view |
| 6. Live harm to past players | Past players flinch, take knockback and can die from live damage | Waking a creeper next to someone has real stakes (after the decision below) |

## When a past player dies: the timeline mends

Killing a past player should not erase what they did afterwards. In Minecraft death is a setback, not an ending: you respawn and carry on. Erasing their later work would wipe hours of a real person's play, plus everything other people built from it. So the timeline treats the death as a **delay**. It mends around it and leaves a few small scars.

### Pitch 1: Delay, not erasure (recommended)

The past player dies, respawns and catches back up with their own recording.

1. **They die where it happened.** Their drop scatters on the ground in the past, exactly as a vanilla death would.
2. **They respawn out of sight** at their recorded bed or spawn point.
3. **They walk back.** Once they reach where their recording is now, it resumes, running late by however long the walk took.
4. **Slack absorbs the lateness.** Recordings are full of slack: standing still, AFK, sorting chests, wandering. The late past player skips slack until they are back in sync, so they never visibly speed up or teleport. Most deaths are mended within minutes.
5. **Their changes still happen.** Every block they placed, craft they made and item they handed over still happens, only a little later while the delay lasts. By the present it is fully back on track.

### The scars (small and real)

- **The drop becomes echoes.** Items scattered by a mended death are echo items (your existing rule: labelled, look different, useless in crafting). A traveller can pick them up as trophies, but they can't be used to duplicate anything, because the recording still uses the real ones.
- **The real player feels it.** One more death on their stats, the XP a vanilla death would cost taken now, and a short "temporal bruise" at next login (30 seconds of slowness and nausea). Plus the quiet line: "You remember a death that didn't stick."
- **The traveller pays.** Heavy paradox for a killing, scaled by how long the mend took. The cause carries the cost, not the victim.

### When the mend can't absorb it

Some recorded moments are time-critical: a hand-over to another player, a trade, a fight, a boss kill. If the delay is still open when one of those comes up:

- **Hand-overs and trades** wait for the late player; the other side's recording waits too, so it adds slack to them. If that can't fit, the hand-over ripples through the ledger like a missing item.
- **Fights and boss kills** happen without them if they are too late. The loot they would have got goes to whoever else was there, or stays on the ground.
- **If the delay never closes** before the next anchor point or the trip's end, the leftover becomes paradox for the traveller instead of a change to the world.

### Other pitches considered

| Pitch | How it mends | Why not first choice |
| --- | --- | --- |
| Erasure | Everything they did after the death is undone | Wipes hours of a real person's work and everything built from it: the major alter to avoid |
| Echo stand-in | A faint copy finishes their recorded actions | Mends perfectly, but a ghost doing the work breaks the rugged feel |
| Fixed point | Past players can't be killed by live damage; they flinch and stay at 1 heart | Safe and simple, but the stakes are gone |
| Butterfly limit | Changes spread only a few minutes past the death, then everything snaps back to the recording | Easy to build, but the snap-back is the kind of visible cheat the rules forbid |

The same delay idea fits other big changes too. A past player whose house a traveller burned "rebuilds" by catching up, rather than history losing everything they did inside it.

## Several crews in the past at once

The earliest crew's changes come first, and every later crew inherits them already mended. This builds on your existing crew rule: each crew has its own past dimension, ordered by time, and changes flow forward to later ones and to the present. Below, crew A is further back in time than crew B, whichever of them left first.

- **Mends travel as data, not just results.** Crew A's changes are stored as amendments: blocks, rerouted paths, remaining delay per past player, and the slack already used. Crew B's dimension plays history with those amendments applied, so B sees the same detours A caused.
- **Unfinished mends carry over.** If a past player A delayed is still running late at B's time, B sees them late, and slack keeps paying the delay back in B's dimension. If A's delay was fully paid back before B's time, B sees the original recording.
- **Changes from A reach B only out of sight.** A's trip runs at the same time as B's, so its changes can land in B's world mid-visit. Each one applies only in chunks no B traveller is looking at, using the same rule as reconvergence. If a change stays in view too long (60 seconds by default), it lands with a small timeline tremor: a camera shake and a low sound, which reads as time settling, not as a glitch.
- **Stacked mends share one budget.** If B also delays a past player A already delayed, the delays add, and slack pays them back in order. A delay that can't be paid back is charged as paradox to whichever crew added the part that overflowed.
- **Earlier always wins.** If A changes something B's mend depended on (A blocks the path B's detour used), B's mended path is worked out again from A's new state. B's own changes are kept where they still fit; where they don't, they fail like any contradiction, with paradox for B.
- **Same moment, different crews.** Two crews can't stand in the same moment: the existing rule gives each crew its own dimension. Two crews close in time behave exactly as above: the earlier one is crew A.

## Decisions for Aaron

One choice changes the feel of the whole system. The rest have a default you can override.

**1. A past player killed by a traveller's doing.** I recommend Pitch 1, delay, not erasure. Their work still happens, the timeline catches up through slack, and the scars are echo drops, a death on their stats, XP, a short bruise, and paradox for the traveller. The other pitches are in the section above.

**Defaults I'll use unless you say otherwise:**

- Deaths from lava, falls, drowning and the void can't be undone.
- A saved life costs the rescuer paradox for the items restored, plus a fixed cost per life (config).
- A saved player gets one quiet chat line at login, not a popup.
- Promoted mobs never leave the past (your existing rule), and a past mob that is killed and still alive in the present is removed when its chunk loads.
- Reconvergence waits up to 60 seconds for a moment out of sight. After that, the mob is kept as a change instead of snapping back.
