---
name: future-port-tardis
description: Aaron's design decisions for the 1.21.1 TARDIS future-port: damaged-TARDIS progression, repair stages, crash sites, look
metadata:
  type: project
  modified: 2026-11-03T07:34:15.791Z
---

Decisions for the future-port (target **Minecraft 1.21.1**, changed from 1.26.3 on 2026-11-03) of TardisMod + TardisTimeTravelAddon (merged), from Aaron 2026-11-03. Scope/which modules: [[future-port-scope]].

- Approach: clean rewrite, DarkholmeTenk/TardisMod as reference only. Proposed (not yet confirmed): TARDIS as one saved data record built from subsystems with repair levels, thin blocks; interiors as regions in one shared dimension; drop darkcore; rooms as .nbt structure templates.
- **Custom progression modpack, centred in Middle-earth (LOTR).** One crash site at the spawn of whichever dimension the world starts in (Middle-earth world type or normal Overworld); rare random wrecks elsewhere are **salvage for parts by default**, claimable only if config allows several TARDISes.
- Start: scrappy police box, bare unpainted wood showing through scratches, chunks sheared off. Interior starts as a cramped broken console room.
- Early TARDIS barely teleports short XYZ hops, no dimension travel. Power: wind it up with Create rotation.
- **Gradual progression**: each repair lets you comfortably go further; big milestones (e.g. dimension travel) still leave some dimensions locked until more repairs. **Major repair milestones cap which repair upgrades are possible.**
- Repair (CHANGED 2026-11-03 evening): no physical part blocks. A **basement repair room** under the console room holds
  the repair/upgrade skill tree as one GUI. Phase 3's part blocks must be replaced.
- **Console: custom model like the original TardisMod** (buttons, levers, dials not shaped like blocks; each control a
  clickable part of the model, appearing as repaired). Replaces the plain console block + flat screen.
- **Flight without dematerialising** (2026-11-03 evening): the TARDIS can fly as a real object through sky and space,
  pilot sees a third-person camera of it spinning. Jumps (dematerialise) stay too. Seamless Outer Wilds-style space.
- **Seamless door**: opening it shows real outside/inside (portal rendering), walk through with no teleport screen,
  where possible. Prototyped with space (TARDIS phase 6).
- **Immersive Portals is REQUIRED** (Aaron 2026-11-03): see-through door = IP portal pair (tardis/docs/door.md); space
  uses IP too. Planet idea (Aaron): flat looping planets via IP world wrapping (torus, no poles); globe seen from space
  is our own rendered sphere from a terrain map, entering the atmosphere drops you onto the flat world.
- **Space works in any dimension** (Aaron 2026-11-03): each dimension's sky can lead to space, set per dimension in
  data, incl. modded ones. Backburner (maybe later): LOTR Sun, Moon, Eärendil's star as visitable bodies.
- Repair costs are a mix: LOTR ores, time-travel ores, space ores (possible Galacticraft-style space program), overworld ores, Create recipes. Each stage needs its own Create-made part (Create has ~3 tiers only).
- Room types and maximum room count grow with upgrades. Exterior/interior textures change as it's repaired.
- **Repair can go backward** (damage) — support it in code even if unused at first.
- Research: player screen AND (optional, config) RingRealms colonists as researchers. RingRealms now lives inside the LOTR port (see [[future-port-scope]]); architecture must support both.
- Loader: **NeoForge** (1.21.1). Ownership: players can always share a TARDIS; a config decides whether one player may own several. Time travel unlocks **late but not last**. The space program is **part of this mod**, not separate.
- Answers 2026-11-03 (in plan doc): all old time-travel features carry over (recorder, trips, paradox, ghosts, ripples, future trips). Space: **rockets first**, used to gather what repairs the TARDIS's space capabilities; later the TARDIS flies to planets itself. Dimensions: many: the past, the future, Middle-earth, planets (one dimension per planet, or ideally seamless Outer Wilds-style planet-space-planet transitions), RFTools Dimensions, more later. Stages start from **fixed lists of repairs** (not points). Aaron does the textures himself.
- Dimension unlock order (may change): **start dimension → the other one (Middle-earth/Overworld) → space → time travel → RFTools Dimensions**. Planets: **seamless planet-space-planet (Outer Wilds style), large scope accepted**; prototype at the start of the space phase.
- Name: **TARDIS: Ring Bearer** (mod id **`tardis_rb`**; Aaron said TARDIS_RB, NeoForge needs lowercase). Lives in `tardis/` of the shared repo. Research is per TARDIS (shared by crew). Damage lowers levels only, research kept. Console controls physically appear only once repaired; code unlock check is just a backstop.
- **Design rule:** every feature easy to enable/disable/change (pack-maker tunable), never hard-coded. Pack-maker tweaks via datapacks and KubeJS (Aaron doesn't know either; Claude's call, "probably both").

**Why:** the mod is the spine of a modpack's progression, so balance will be iterated a lot.
**How to apply:** data-driven stages/unlocks/costs/visuals, one central unlock check, never assume a feature is always on.
