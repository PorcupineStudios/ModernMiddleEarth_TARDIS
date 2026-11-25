# Plan Review

Nov 3, 2026 · @Aaron

## Summary

I reviewed the four planning docs: the [TARDIS 1.21.1 Port Plan](https://claude.ai/artifact/DD8D3rShdupt1xVSpx16XZ), the [TARDIS Code Architecture](https://claude.ai/artifact/ELT5R1mp3XythfpsNpyKJE), the [LOTR 1.21.1 Port Plan](https://claude.ai/artifact/AgjUf8mwNgX4omnWC5zzN4) and the [LOTR Rewrite Pitches](https://claude.ai/artifact/NA3cAi3EezqNQETSreQ2qz). The overall direction holds up: data-driven progression, one record per TARDIS, a converter for LOTR content. The problems are in ordering, in how the pieces connect, and in a few technical assumptions that don't hold on 1.21.1.

The five that matter most:

1. **Time travel needs a past, but the recorder starts late.** Time travel is a late milestone, and it's built in TARDIS phase 7. The recorder that remembers the world has to run from the first day of the world, or the past is empty when the player finally gets there. The recorder belongs in the foundations; only the *travel* unlocks late.
2. **The two mods' APIs point at each other.** The TARDIS architecture says the LOTR port depends on the TARDIS API and never the other way round. The LOTR plan says the TARDIS mod reads factions and alignment through the LOTR API. Both can't be true without a circular dependency.
3. **Datapack registries don't reload on 1.21.1.** The architecture puts every tunable number in datapack registries and promises `/reload` and KubeJS tweaking. On 1.21.1 those registries load once when the world starts; `/reload` doesn't touch them. Pack makers would have to restart the world for every balance change.
4. **The LOTR phases are in the wrong order.** Full Middle-earth (phase 5) comes before LOTR's blocks and items (phase 6), but Middle-earth's terrain, trees and structures are made of LOTR blocks. Phase 4's done-when needs NPCs, which arrive in phase 7.
5. **The two projects have no shared schedule.** TARDIS phase 2 needs Middle-earth, which is LOTR phases 2 to 5. Nothing says which project goes first or how they meet.

## TARDIS plan and architecture

| Flaw | Why it matters | Fix |
| --- | --- | --- |
| Recorder built in phase 7 | The past only holds what was recorded. A late recorder means an empty past. | Recorder and a world history log go in phase 1. Record only near players, claims and colonies, and compact old history so saves stay small. |
| Tuning data in datapack registries | They load once at world start. `/reload` and KubeJS can't change them mid-game. | Load repairs, costs, research, stages and looks with reload listeners and sync them with our own payload. Keep datapack registries for things worldgen needs (dimensions, planets). |
| "Create's own tiers gate stages" | Create has about three informal tiers (andesite, brass, precision and sequenced assembly). That's not enough for many stages. | Gate on specific Create parts and on our own sequenced-assembly recipes, so each stage has its own Create-made component. |
| Charge is just "by distance" | If any trickle of rotation fills the TARDIS, a bigger Create setup doesn't matter. | Charge = stress × speed × time, with a cap on intake per repair level. Bigger factories charge faster, which makes Create progression feel real. |
| One-TARDIS config vs claimable wrecks | With one TARDIS per player, it's unclear what a player does with a second wreck. | Wrecks are salvage by default: strip them for parts, research data and room blueprints. Claiming one is an option only when the config allows several. |
| Start assumes Middle-earth | Aaron's LOTR answer lets a normal world start in the Overworld. The crash site and the dimension order assume a Middle-earth start. | Decide: in a normal world the wreck still waits in Middle-earth (reached by LOTR's portal), or it lands at the Overworld spawn and the order swaps. |
| Pack-maker pass last | Docs, a sample datapack and KubeJS come in phase 10, after all balance work. | Ship the default progression as a datapack from phase 1, write each file's docs when the registry is made, and validate data at load (missing ids, cycles). |
| Seamless planets in one dimension | Spherical planets in one block world need heavy custom rendering, and 1.21.1 build height limits how far "space" can be above a surface. | Prototype both: one shared dimension, and separate planet dimensions with a masked transition (an atmospheric entry effect hides a pre-loaded dimension change). Pick by results. |
| Interior reasoning | "A dimension per TARDIS is handled poorly" is overstated; other modern TARDIS mods create dimensions at runtime. | The shared dimension is still a good choice. Keep it for simpler saves and chunk handling, and note the trade-off: shared weather and time, and walls must stop leaks between plots. |
| `ServiceLoader` / IMC for hooks | Both are awkward on NeoForge's module system; IMC is legacy. | Use custom registries for code types (requirement, cost, destination) and a mod-bus registration event for providers. |
| No tests | Done-when checks are manual. | `runGameTestServer` is already set up: one GameTest per done-when. |
| Create version drift | Create's API changes between minor versions; 6.0 broke many addons. | Pin a Create version and keep all Create calls in `compat/create`, even though it's required. |

Small fix I made while reviewing: the TARDIS plan still said RingRealms was a separate mod in four places. Those now say the colonists come from the LOTR port.

## LOTR port plan

| Flaw | Why it matters | Fix |
| --- | --- | --- |
| Blocks after full Middle-earth | Biomes, trees, surface rules and structures are built from LOTR blocks. Phase 5 can't finish without phase 6. | New order: foundations, converter with blocks and items, small Middle-earth, factions, full Middle-earth, NPCs, structures, the rest. |
| Phase 4 done-when needs NPCs | "Killing an enemy NPC raises alignment" can't be tested until phase 7. | Test phase 4 with a GameTest that fires a kill event, or a placeholder NPC. |
| One export per structure | LOTR structures are random generators: block variants, chest loot, banners, NPC spawns. Building one into a test world captures one roll. | Export several seeds per structure, turn the random spots into structure processors and jigsaw pools, and export chests and NPC spawns as marker blocks instead of baked contents. |
| Converter ignores assets | Aaron chose to reuse LOTR's textures, sounds and map, but the converter only covers structures, recipes, loot, NPC data and models. | Add assets to the converter: textures moved into the 1.21.1 layout, `sounds.json`, and the lang files for every language LOTR ships. |
| "One NPC base, races as data" | Trolls, wargs, spiders, mûmakil and Ents don't share a body or AI with a Gondor soldier. | Two bases: **people** (humanoid NPCs with records, jobs, speech) and **creatures** (animals and monsters), each data-driven. |
| Pitches not in the plan yet | Factions-as-realms has to shape phase 4, and person records phase 7. Adding them after those phases means rewriting both. | Fold the accepted pitches into the plan before phase 4 starts. |
| Mod id is permanent | `lotr_port` ends up in every saved block, item and registry id. Renaming later breaks worlds. | Fine if it's the final name; otherwise pick the real one before phase 1 content. |
| No word on old worlds | 1.7.10 LOTR worlds and RingRealms colonies can't be loaded on 1.21.1. | Say so in the plan: new worlds only. |
| Map-image biome lookup is hot | 1.21.1 asks for biomes at 4-block steps millions of times during generation. | Pre-process the map into a fast lookup table at load, cache per region, and benchmark in phase 2 on the small map. |

## Pitches doc

- **"Every NPC is a person" fights the performance goal.** LOTR spawns huge numbers of throwaway orcs and bandits. A saved record for each one bloats saves fast. **Fix:** NPCs start light and get promoted to a full record only when they matter: hired, named, a quest giver, recruited, living in a colony, or someone the player dealt with.
- **AI rulers and faction realms overlap.** Pitch 2 has AI rulers with their own realms; pitch 3 makes Gondor and Mordor realms. It doesn't say whether an AI ruler *is* Gondor. **Fix:** AI rulers are small player-like realms aligned with a faction (they use the realm slots). The big LOTR factions stay as the world's great powers that AI and players serve or fight. That keeps LOTR's balance and keeps the AI cheap.
- **The AI isn't tied to time travel.** If AI rulers make random choices, the past can't be rebuilt and the future can't be forecast. **Fix:** seed every AI decision per in-game day and write it to the history log. Then pasts rebuild exactly, and a future trip can run the AI forward to show a real forecast of the world.
- **Three engines doing one job.** The quest engine, the story engine and the TARDIS unlock check each have their own conditions (objectives, stages, requirements). **Fix:** one shared vocabulary of conditions used by all three (see below).
- **Other ages need their own maps.** Second Age Númenor and First Age Beleriand reached like the ancient past still need their own map images, biomes and structures. **Fix:** build them on the same map-driven world generation, so a new age is mostly a new map image and data, and plan them after full Middle-earth works.

## Stronger ideas

### One repo for the whole pack

Put the TARDIS mod, the LOTR port, a shared API and the converter in one private repository as Gradle subprojects. One CI run builds everything, and a change to the shared API lands in the same pull request as the code that uses it. This also fixes the circular API problem: both mods depend on the shared API, and neither depends on the other.

### A shared API with three pieces

- **History log.** One world journal of what happened and when: blocks changed, colonies founded, battles, AI decisions, trips. The recorder, colonies, AI rulers, the Red Book and time travel all read and write it, so the past always rebuilds the same way.
- **Conditions.** One registry of condition types (alignment, stage, research, colony tier, item delivered, place visited, Create machine running, era visited) used by TARDIS unlocks, quests, the story engine and colony research. A new condition works everywhere at once.
- **Providers.** Crash sites, researchers, destinations and power sources, as already designed, but living in the shared API instead of the TARDIS API.

### Scripts that do the work

Aaron asked for scripts over hand-porting. The ones worth writing:

- **Converter** (already planned), extended to assets.
- **ID table generator** that reads the old jar's block and item list and writes the metadata-to-blockstate table and the new registrations.
- **Progression validator** that walks every stage, repair, research node and quest and checks each one is reachable, has no loops, and only costs items that some recipe or loot table actually gives.
- **Balance export** that writes all costs and unlock levels to a spreadsheet, and reads edits back into data files.
- **Benchmark world** with a fixed seed, a few colonies and AI rulers, run in CI to catch performance drops.

### A performance budget from day one

RingRealms already has `/rrcolony perf`. Give both mods a perf command and a budget per system (milliseconds per tick for colonies, AI, recorder, NPCs), checked by the benchmark world.

## Next steps

In order, once you've picked:

1. convert to 1 repo.
2. Move the recorder and history log into phase 1 of both plans.
3. Switch tuning data to reload listeners in the TARDIS architecture.
4. Reorder the LOTR phases and fix the done-when checks.
5. Fold the accepted pitches into the LOTR plan, with the fixes above.
6. Write one shared roadmap that interleaves both projects.

Questions for you:

- [ ] In a normal world, does the wreck wait in Middle-earth, or land at the Overworld spawn? (make a check to see what the world gen type was, whichever dimension you start in gets the crashlanding)
- [ ] Which project goes first: the TARDIS against a stand-in Overworld start, or the LOTR port until Middle-earth exists? (LOTR)
- [ ] Wrecks: salvage for parts by default, claim only when the config allows several TARDISes? (correct)
- [ ] OK to apply the fixes in this review to the plan docs? (yes)
