# Time Travel: Gameplay Guide

Oct 18, 2026 · @Aaron

The mod turns the server's whole history into a place you can visit. Pick a moment on the TARDIS timeline, land there, and see the world exactly as it was, with every player who was online moving around as a ghost.

## What you can do with it

- **Watch your base being built.** Stand in your main hall and scrub from the day it was an empty hill to today. Walls go up, chests appear, and your past self runs around placing blocks.
- **Find out what happened.** A creeper hole in the wall, a missing chest, a burned-down farm: go back to just before, and watch who or what did it.
- **Relive moments with friends.** Revisit the first night on the server, a big LOTR battle or the day you finished a build together. Ghosts show full skins, armor and held items.
- **Recover lost builds.** Something was broken and nobody remembers how it looked. Go back, look at it, and rebuild it in the present using what you saw. With a Paradox Machine you can even pull the old version forward.
- **Plan with hindsight.** Go back to before a valley was cleared and see the terrain that used to be there.
- **Fix small mistakes.** Place the torch your past self forgot in a spot nobody touched later, and it appears in the present.
- **Leave a mark.** Put a sign in the past near where a friend will build, so they find it there today.
- **Show off a base's story.** Give a friend a timelapse tour of your build with the Chrono-Glasses, without anyone leaving the present.

## A trip, step by step

1. **Pick a moment.** At the timeline console in the TARDIS cockpit, scroll to the time you want and click it. Then pick where to land on the nav map, either in the Overworld or in Middle-earth.
2. **Take off.** The TARDIS dematerializes and stays in flight while the past is prepared around the landing spot. A long jump, or a busy area like your main base, means a slightly longer flight.
3. **Land and step out.** The world looks exactly as it did at that moment. Ghosts of everyone who was online then go about what they were doing. The ghosts of anyone who visited this moment on an earlier trip are there too, including your own.
4. **Explore.** Time runs forward from where you landed. Punching a ghost just makes it glitch.
5. **Change things, carefully.** Building and breaking work, but every change is judged by the paradox rules below. Your Paradox Meter shows on screen.
6. **Go home.** Get back in the TARDIS and fly to the present. Changes that passed the rules fade into the present world. Anything that broke the rules stays behind or fades from your inventory.

Companions who travel in the same TARDIS are one crew. They share a Paradox Meter, and they share its consequences.

## Timeline console

The timeline is a new screen on the TARDIS cockpit. It shows the server's whole history as one bar measured in hours of play, with every moment nobody was online cut out.

&#91;embedded content: timeline console mock-up · example sessions\]

The sessions above are made-up examples. Hour 4 and Hour 9 are where offline time was cut out, so a new date starts there.

**How it reads:**

- **The axis** counts hours of server time with someone on, from Hour 0 (the first login after the recorder was installed) to now. Each cut-out gap shows as a thin break mark, with the real date and time printed under it so you always know when you are.
- **Player bars** sit above the axis, one row per player in their own color. A bar runs from login to logout, so you can see at a glance who was on together.
- **Zoom and pan.** The scroll wheel zooms from the whole history down to single minutes, and dragging pans along it. Tick labels switch from days of play to hours to minutes as you zoom in.
- **Hover** over a bar to see the player's name and their login and logout times. If the server allows it, you also see where they were at that exact moment.
- **Markers** show your own earlier trips (small TARDIS icons), Paradox Machine rewrites, and collapsed trips.
- **Click** to set the destination time. The nav map then shows that moment, so you pick the landing spot in the world as it was.

**Seeing coordinates** is a server setting with four choices: everyone's, only your own, only for server operators, or nobody's. When coordinates are hidden, hovering still shows who was online.

## Chrono-Glasses

Chrono-Glasses let you look into the past without leaving the present. You wear them in the helmet slot.

- **Open the timeline** with a key while wearing them. It's the same screen as the cockpit console, so you pick a moment the same way.
- **The world around you shifts** to that moment, but only on your screen. Blocks that didn't exist yet fade to a ghostly outline, blocks that are gone now reappear, and ghosts of past players walk through the area.
- **Scrub live.** Pause, rewind or fast forward history like a timelapse. Stand where a room will be and watch its walls and chests appear around you.
- **Look only.** You stay in the present: you can't touch anything in the past, and the present can still touch you, so mobs can still attack. That means the glasses carry no paradox risk at all.
- **Range.** The glasses show about 4 chunks around you (a server setting) and use TARDIS energy linked to your TARDIS key, so they don't work if your TARDIS is drained.
- **Sharing.** Friends wearing glasses within the same range can link to your view and watch the same timelapse together.

## Changing the past

One rule decides what carries into the present: **your change survives only where nothing in the recorded history touched that spot later.**

| You do this in the past | What happens in the present |
| --- | --- |
| Build a wall in a field nobody touched afterwards | The wall fades into the present |
| Build where a friend later built their house | Their recorded build wins, as it would have anyway, and your wall is gone |
| Mine an ore nobody mined later | It disappears from the present too, a fair trade |
| Mine an ore someone mined later, or take from a chest someone later emptied | The item is an echo: it works in the past, fades when you get home, and adds heavy paradox |
| Block a ghost's path with a small obstacle | The ghost glitches through it; its recorded actions still happen |
| Stand where a ghost is mining or placing | The ghost and the block glitch until the action finishes |
| Break into a chest or base in the past | Blocked if that area is protected (see Protecting your base) |

Past ghosts can't be hurt or killed, and nothing you do changes what another player owns in the present.

## Paradox

Each crew has a Paradox Meter from 0 to 100. It rises when you fight the timeline and falls slowly once you're back in the present.

**What raises it:**

- Taking echo items: by far the biggest cost, scaled by the item's value (ores and valuables cost the most)
- Changes that conflict with later history, such as building over a spot someone later changed
- Forcing your way into a protected area
- Another crew in the same past chunk at the same time (small)

**What happens as it rises:**

| Meter | Stage | Effects |
| --- | --- | --- |
| 0–24 | Stable | Nothing |
| 25–49 | Strained | TARDIS energy costs double; ghosts and the sky flicker |
| 50–74 | Unstable | Escaping costs far more energy; travel cooldown after you return; carried items randomly glitch and can't be used for a few seconds; small rifts open |
| 75–89 | Fracturing | Causality switches off, so nothing from this trip will reach the present; chunks shift up and down, blocks randomize, rifts grow and hurt |
| 90–100 | Collapse | The past dimension falls apart and is deleted; every crew member dies and loses everything they carried; the TARDIS exterior is lost and must be resummoned; all edits from the trip are wiped |

The escape cost shows on the console at all times, so the crew always knows how close they are to being trapped.

**Why duping never pays:**

- **Echo items never come home.** A duped diamond fades the moment you return, so the best possible outcome of a dupe is nothing.
- **One dupe attempt costs more than it's worth.** Each echo item adds paradox in proportion to its value. Taking a stack of diamonds is enough on its own to push a crew into Unstable, and taking two can trigger Fracturing.
- **Greed ends in collapse.** Keep going and the crew loses its entire inventory, so the gear you brought is gone too, along with the TARDIS exterior.
- **It follows you.** The meter falls slowly (about 10 points per hour of play in the present), and the travel cooldown blocks a quick retry. A crew that keeps trying starts every trip closer to collapse.
- **No shortcut.** Paradox Machines and Temporal Anchors (below) reshape areas of the world. Neither can turn an echo item into a real one.

## Paradox Machine and Temporal Anchors

These are the two blocks for deliberate, large changes to time. Both work on whole chunks and can be shared with other players.

|  | Temporal Anchor | Paradox Machine |
| --- | --- | --- |
| Area | 3×3 chunks | 5×5 chunks |
| Placed | In the present | In the past, during a trip |
| Job | Protects an area's history (see the next section) | Forces a big change into the present |
| Cost | Small, steady TARDIS energy | Large energy cost and +30 paradox for the crew |
| Who can use it | Owner and shared players | Only in areas you own, or where the anchor's owner has shared with you |

**The Paradox Machine has two modes:**

- **Restore.** Pull the area forward exactly as it was at that moment. This is how you bring back a griefed build, a base that blew up, or terrain that was dug away. Land just before the damage, place the machine and activate it.
- **Rewrite.** For the rest of the trip, your changes inside the area win even against later history. Use it to redesign a build "from the start" so it has always looked that way.

In both modes the current present version of the area is archived first, so nothing is truly lost. On return the area changes in the present with a big rift and glitch effect.

**Paradox Collapse** undoes a machine. Use the machine again in the present to restore the archived version, with an even bigger rift show. Each area can only be rewritten once every few hours of play, so the world can't flicker back and forth.

## Protecting your base

The forward rule already guards everything you built. If someone goes back to before your base existed and builds on the spot, your recorded build overwrites theirs.

The gaps are spots your history never touched: the air inside your rooms, the stone under your floor, the hillside next to your walls. A traveler could go back and put a block in the middle of your hall, dig a tunnel under it or pour lava beside it, and that would survive into the present.

**A Temporal Anchor closes those gaps.** It protects its 3×3 chunks backwards in time as well as forwards:

- **How far back.** By default the protection starts from the first block you ever changed in the area. A Deep Anchor costs more energy and protects all the way back to the start of history.
- **Locked for others.** Inside the protected area and time, players not on the share list can't place or break blocks; blocks they hit just glitch. They can't open containers or place a Paradox Machine either, and nothing they do there reaches the present.
- **Optional veil.** The owner can turn on a veil that blurs the area's past for anyone not shared, including through Chrono-Glasses. Nobody can scout your base's layout or vault from history.
- **Alerts.** The owner gets a chat message when someone not shared lands inside the area's past, with their name and the moment they visited.
- **Shared with friends.** Players on the share list can travel and build in the area's past normally.

An anchor only stops trips made after it is placed. Changes from trips taken before that are already part of history, so place one early in a base's life.

## Server settings

| Setting | Default | Choices |
| --- | --- | --- |
| Dimensions with history | Overworld, Middle-earth | Any list of dimensions; TARDIS interiors are never recorded |
| See past coordinates | Only your own | Everyone's, only your own, operators only, nobody's |
| Chrono-Glasses range | 4 chunks | 1–8 chunks |
| Paradox strength | Normal | A multiplier on every paradox cost |
| Paradox decay | 10 points per hour of play | Any rate |
| Collapse item loss | On | On or off |
| Item value for paradox | Ores and valuables high, common blocks low | A list of items and their values |
| Deep Anchors | Allowed | Allowed or not |
| Rewrite cooldown per area | 4 hours of play | Any length |
| Rewind point interval | 50,000 changes | Any number (see the storage plan) |

## Decisions

Aaron settled these on Oct 18, 2026.

| Topic | Decision |
| --- | --- |
| When past time starts | A past dimension's clock starts when the crew first steps out of the TARDIS, and runs forward from there. Chunks loaded later show that same moment. |
| Chrono-Glasses controls | Pause, rewind and fast forward |
| Dying in the past | Respawn in the default dimension, which is a config option |
| Traveling from inside the past | Allowed with enough energy. The destination is rebuilt from the present or the nearest rewind point, never from the past dimension you're in. |
| Several crews at once | Only one TARDIS crew shares a dimension. Other crews get their own, even at nearly the same moment. |
| Changes across dimensions | Every trip's changes are ordered by the past moment they happened at. A surviving change applies to the present and to every open past dimension later in time, with the fade-in effect. |
| Mobs | Mobs never spawn in the past. Mobs near players are recorded and replay as untouchable ghosts if that can be done cheaply. Nothing living can be brought out of the past. |
| LOTR map travel in the past | Allowed. A chunk the present has never generated is generated from the seed and also saved into the present world, so it never looks different later. |
| Equipment costs | Chrono-Glasses are the cheapest. Everything else is TARDIS-level-gated and runs on a more expensive fuel, still to be decided. |
| Echo items | Clearly labeled and visually distinct. Anything crafted from one is a useless echo too. |
| Admin rollback | A secondary feature, after the main mod |
| Code home | MyMod is renamed TardisTimeTravelAddon (repo PorcupineStudios/TardisTimeTravelAddon) |
| Client vs server | As much as possible runs on the server. The client only draws ghosts, glasses views and the timeline from data the server streams. |
| Testing on the live server | Allowed: back up the server, install the test build, test, restore the backup, then put the main build back |

**Catching up to the present.** Each crew's replay clock runs until it reaches the moment that crew left the present. At that point they watch their own past selves board the TARDIS and fly away. The replay then stops and they move to the present dimension as smoothly as possible. Each crew is tracked separately, because two trips can run at once and can have left at different moments. Past TARDISes (ghost TARDISes) can never be entered.

**Seamless return.** About 30 seconds before a crew catches up, the server quietly loads the present chunks around each crew member. At the catch-up moment the usual "Downloading terrain" screen is skipped. A short time-vortex shimmer, about half a second, covers the world swap while the already-loaded chunks are sent. Position, facing, movement, held item and anything being ridden carry over, and the arrival spot is nudged clear if a block now stands there.

**Following another crew.** Say Crew 1 goes to Hour 1, and Crew 2 later goes to Hour 0.9. A few minutes into its trip, Crew 2 watches Crew 1's TARDIS arrive and can follow Crew 1's ghosts, always a little behind. Crew 1 is always further along in time, so it never sees Crew 2. Changes Crew 2 makes still carry forward into Crew 1's dimension, under the usual rules: no damage to ghosts, and paradox for blocking paths.

**More edge cases, settled:**

- **No natural ticking in the past.** Water flow, fire spread, random ticks and natural redstone are off, so only the replay and crew actions change blocks. Things the crew places still work.
- **Echo items fade the moment they leave the past dimension,** including into the TARDIS interior.
- **Ender chests, backpacks and AE2 wireless** are blocked in the past.
- **Portals work in the past** between dimensions that have history, such as the Overworld and Middle-earth. The other side is a past copy that runs on the same crew clock. Portals to dimensions without history stay blocked, because the other side would be the present.
- **Logging out mid-trip.** A trip stays open until every crew member is home. A player who logs back in rejoins the crew's past dimension, which is rebuilt if needed. Crew members who reach the present are sent home as normal. The dimension stays open, saved but not loaded, until the offline player logs in and is sent home too. If the trip collapsed while they were offline, the collapse still applies to them on login, so logging out can't dodge it.
- **Landing near your own ghost TARDIS** is allowed, but the two can't overlap.
- **The Paradox Machine works even while another crew is later in time,** since it's an expensive late-game item.
- **Time of day and weather.** Time of day already follows the world clock, so only jumps such as sleeping or /time set are recorded, plus weather starting and stopping. That's a handful of bytes per day.

**Hidden from TARDIS.** Past dimensions are always hidden from the TARDIS dimension list, so nobody can fly straight into another crew's past. A config list can hide other dimensions too. The same hiding applies to other dimension lists the mod touches, such as Waystones and RFTools.

**Mob recording cost.** Recording mobs within about 64 blocks of a player, at a few points a second, would cost about 10 to 20 KB per mob per hour. With 20 to 40 mobs around, that's roughly 0.3 to 0.8 MB per hour, so it about doubles the history size. It should be a config option with a radius and a mob list (for example LOTR NPCs and hostiles, not chickens).
