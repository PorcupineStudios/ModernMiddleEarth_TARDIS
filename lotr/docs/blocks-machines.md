# Blocks and machines: gates, drinks, recipes, machines, farming, Create

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Gates and connected textures (2026-11-04)
- LOTR drew many blocks with connected textures (gates, portcullises, dwarven bricks): a base plus side, corner and
  inside-corner pieces by neighbour. `converter/assets.py sheet` bakes each into `textures/block/connected/<tex>.png`:
  5 tiles x 2 rows (tile per quarter: 0 nothing connected, 1 beside only, 2 above/below only, 3 both but not the
  diagonal, 4 all; row 0 shut, row 1 open = no base, only the frame). Gates without LOTR pieces (iron/bronze
  portcullis, cross-braced wooden gate) get a frame from their own solid bars (thickest full-width line top and
  bottom, thickest full-height line down the sides); LOTR cut their outer 3/16, which for bars is a row of dots.
  Blocks use model loader `lotr_port:connected` (`client/ConnectedModel`, shapes `cube`,
  `gate`, `full_gate`): it reads the neighbours in `getModelData` (NeoForge calls it per block in chunk builds) and
  draws each face a quarter at a time. The rule is LOTR's (`GateBlock.connects`): same block; a gate only in its
  plane, same axis and open state; an open upright gate also to the whole row under it (diagonals too, or the bottom
  joints get inner corners).
- Checked in game 2026-11-04 with `client/DevScript` (dev only): `JAVA_TOOL_OPTIONS=-Dlotr_port.devscript=<file in
  run/>` plus `./gradlew :lotr:runClient -PquickPlay=<save>` runs a script of commands, waits, right-clicks and
  screenshots (`run/screenshots/`), then quits; steps in its javadoc. The save needs `allowCommands` and
  `confirmedExperimentalSettings`, and `run/options.txt` `onboardAccessibility:false`, or a screen blocks quick play.
- Gates (`content/GateBlock`): use or redstone opens/shuts the whole gate (same block, same axis, in its plane, max 64);
  with a bow in hand the click shoots (LOTR); with a block it works the gate, sneak to place (Aaron 2026-11-16). Open gates have no collision and draw
  their frame (they used to render as air: "right-clicking deletes it"). Dwarven doors (`full`) are whole blocks while
  shut; they have no LOTR texture of their own to frame, so they keep a plain panel (`gates_without_frame`). LOTR's
  gate sounds aren't ported: vanilla wooden/iron door sounds.
- NPCs open gates (Aaron 2026-11-04, unlike LOTR, for colonists): a shut upright gate is `DOOR_WOOD_CLOSED` to the
  pathfinder, `LotrNpc` navigation opens doors, `npc/OpenGateGoal` opens a gate on its path and shuts it once through
  (not while someone stands in it); vanilla `OpenDoorGoal` for doors. GameTest `KegGateTests.gatesOpenAsOne`.

## Drinks and kegs (2026-11-04, merged onto the cloud drinks 2026-11-09, `drink/`)
- Drinks are the cloud's `LotrDrinkItem` (stats in `content/drinks.json`, components `LotrComponents.DRINK_STRENGTH`
  and `VESSEL`, a vessel id like `goblet_gold`, none = mug). Brewing recipes are `MachineRecipe` BREWING
  (`recipe/brewing/*.json`). `converter/drinks.py` adds `drinking/settings.json` (`DrinkData`: strength factors,
  serving 250 mB, keg 16 servings, 3000 mB water, `brew_ticks` 12000), `content/vessels.json`,
  `content/drink_fluids.json`, each drink's picture per vessel (`item/drink/<item>/<vessel>`, model predicate
  `lotr_port:vessel`) and the keg screen textures (`gui/keg*.png`).
- LOTR's barrel is the Keg (`keg`): `KegBlock`/`KegBlockEntity`/`KegMenu`, client `KegScreen`. 6 ingredients + 3
  water buckets (or piped water), Start Brewing, one strength per recipe `time` (else `brew_ticks`) to potent, Stop
  Brewing after the first step. Right-click with an empty vessel pours a serving. Structures' barrels load as full
  kegs, and their chests' drinks keep strength and vessel.
- Create: no dependency. The keg exposes `Capabilities.FluidHandler.BLOCK` (tank 0 the drink, tank 1 water) and
  `ItemHandler.BLOCK`; drinks and vessels expose `FluidHandler.ITEM` (one serving). Each drink is a fluid
  (`Drinks.DrinkFluid`, tinted water) carrying its strength. GameTests `KegGateTests`.
- Set-down vessels (2026-11-17, `drink/Vessels`, `VesselBlock`): sneak + use a vessel or drink on a block top sets it
  down. Use a full one: you drink it where it stands (`LotrDrinkItem.drink`), it stays, empty. Use an empty one with a
  drink in that kind of vessel: pours it in; otherwise nothing (it used to pick the block up, and structure vessels,
  holding nothing, vanished). Break it to take it. Models: `converter/vessels.py` (no two boxes overlap: overlapping
  floor and walls z-fought). The glass bottle and wine glass (2026-11-27) are single panes of our own seamless
  `vessel_glass` (`converter/VesselGlass.java`), drawn translucent, and `client/VesselRenderer` fills them with the
  drink (sides and top), not only a surface. Water drawn into a vessel stays in that vessel.
- Drink-in-vessel item models are `item/drink/<drink>/<vessel>` (2026-11-17): `<drink>_<vessel>` clashed with real
  items (the empty ale horn drew full ale; ale in a golden horn drew the set-down block).
- Poisoned drinks (2026-11-17, `drink/PoisonDrinkRecipe`, recipe `own/poison_drink`): any drink + the bottle of
  poison in a crafting grid = the drink with component `lotr_port:poisoned` (the crafter's UUID, set in
  `LotrDrinkItem.onCraftedBy`; only they see "Poisoned!"); the bottle comes back empty. Drinking it (hand or set-down
  vessel) gives `drinking/settings.json` `poison` (stand-in for LOTR's killing poison: Wither II 30 s + nausea).
- Jade (optional, `compat/jade`, compile only like JEI): a set-down drink shows as the drink with its own tooltip lines
  (strength, "Poisoned!" for the poisoner, or for everyone with COMMON config `drinks.poisonShownToAll`), or "Empty".
- Not yet: alcohol tolerance, drunken speech, NPCs drinking (LOTR's poisoned drinks also killed NPCs given them).

## Recipes and JEI (2026-11-06)
- Machine recipes are `crafting/MachineRecipe`: one recipe type and serializer per machine (`alloy_forge`,
  `millstone`, `brewing`, `ent_jar`, `unsmelting`). Inputs match in any order, plus optional `station`, `chance`
  and `time`. They load and show in JEI and the machine blocks run them (see Machines).
- JEI is optional (`compileOnly` API, `jei_version` in `lotr/gradle.properties`, optional dependency in
  mods.toml). `compat/jei/LotrJeiPlugin` adds one category for faction tables, which shows the table each recipe
  needs, and one category per machine, with the machine blocks as catalysts.

## Machines and display blocks (2026-11-10, `machine/`)
- Which block is which machine is `machine` in `blocks.json` (`converter/machines.py`, by LOTR class); `LotrBlocks`
  picks the block class from it. LOTR's numbers are `machine/settings.json` (`Machines.Settings`).
- `MachineBlock` + `MachineBlockEntity`: faction forges (4 columns of alloy + input; alloy recipes for this
  `station`, else single-ingredient ones, else a furnace result for `#lotr_port:forge_smeltable`), hobbit oven (9
  food slots), millstone (runs while redstone-powered, recipe `chance`), unsmeltery (yield from
  `content/unsmelting.json` × durability left). `lit` shows LOTR's active texture. One `client/MachineScreen` draws
  every `MachineMenu.Layout` (slot spots read from LOTR's containers). Hoppers through `SidedInvWrapper`.
- `DisplayBlock` (plate: food stack, eat with empty hand; weapon rack; armour stand: 4-slot screen), `KebabStandBlock`
  (fuel from the container below, LOTR's takeFuelFromBelow), `EntJarBlock` (biome tag `ent_jar_brewing`),
  `LotrChestBlock` (27 slots, 1.7.10 chest model in LOTR's texture). Renderers in `client/MachineRenderers` on
  LOTR's models (`npc_model/`, static); the armour stand's armour is drawn on an invisible vanilla armour stand.
  `WeaponRackBlock`: floor (faces the placer's look) or `wall` (faces the block it hangs on, breaks without it);
  the model takes LOTR's onWall pose. Racks and stands in structures: meta & 3 = north/east/south/west, & 4 = wall.
  The rack's weapon is drawn with `ItemDisplayContext.NONE` moved to 1.7.10's flat-icon frame (checked in game).
- Items that place another block (`places`): beds stay `BedItem`, the rest are `ItemNameBlockItem`.
- Orc and Taurëdain torches (2026-11-27): kind `double_torch` (`content/DoubleTorchBlock`, a vanilla double plant on
  any solid centre): a 2-pixel post, 16 + 8 high, `_bottom`/`_top` icons, light only on the upper half, flame there.
  Structures got their halves from metadata 1 (`structures.py`); the 35 templates written before were patched with
  `converter/DoubleTorchHalves.java` (no Python here). Delve palettes name `[half=lower]` and `Draw.set` adds the top.
  GameTests `DoubleTorchTests`.
- GameTests `MachineTests` (own batch: the 1-block `empty` template lets other tests land on long-running ones).

## Farming, ores and pouches (2026-11-10, `content/farm/`, `content/pouch/`)
- `converter/farming.py`: crops (`crop`, vanilla CropBlock, 8 ages on LOTR's 4 icons, age>>1), berry bushes (ripe and
  bare are two blocks that swap), corn (`corn`: stalks up to `corn_height`, cobs on non-bottom stalks, a guess),
  grapevines (`grapevine` on a post; seeds plant on a post with farmland within 3 blocks below; picking leaves the
  post). Numbers in `farming/settings.json` (`Farming.Settings`).
- `converter/ores.py`: 15 item-dropping ores (`ore` = DropExperienceBlock, LOTR's xp), loot with silk touch, fortune.
- Raw ores (2026-11-19, as vanilla's cave update): post-step `converter/raw_ores.py`. The metal ores that
  dropped themselves (copper, tin, silver, mithril, Morgul iron + its Mordor ore) drop `raw_<metal>` (silk touch: the
  ore; fortune: vanilla `ore_drops`; counts in the loot tables, `DROPS`); `raw_<metal>_block` packs 9. Every recipe
  that took the ore takes the raw ore too (smelting + a blasting twin, alloy forges, Create mixing). Create: tags
  `c:raw_materials/<metal>` / `c:storage_blocks/raw_<metal>` (tags.py) let Create's own recipes crush raw tin,
  silver and copper into its crushed ores (create_compat.py smelts and washes those into LOTR's ingots/nuggets;
  copper stays Create's, i.e. vanilla copper); raw mithril and Morgul iron are crushed like Create's raw iron into
  what their ore crushes into. Mithril and Morgul iron still smelt only in LOTR's forges. GameTests `RawOreTests`.
- LOTR's amethyst is **tanzanite** (2026-11-19: 1.21 has its own amethyst): post-step `converter/renames.py`
  (`RENAMES`, names per language in `LANG`) renames ids, files, lang, tags, templates after generation. Keep LOTR's
  old ids out of new code: always `tanzanite`, `tanzanite_vein`, `tanzanite_block`.
- LOTR's copper is vanilla's (2026-11-19, post-step `converter/vanilla_swaps.py`, `SWAPS`): copper ingot, ore
  (deepslate form in deepslate) and block are removed and every reference uses vanilla's; recipes that only repeated
  vanilla's are dropped. Copper goblets and treasure piles stay.
- `converter/create_extra.py` (last post-step): Create saw cutting for every LOTR wood (log/beam/bark -> stripped ->
  6 planks), smoker + campfire recipes for every LOTR furnace food (Create fan smoking), keg drinks as Create mixing
  into the drink fluid (6 ingredients + 1000 mB water -> 1000 mB; spouts fill vessels, pipes fill/drain kegs).
- Leaves (`converter/leaf_drops.py`, 2026-11-19): vanilla oak leaves' loot (shears/silk touch: the leaves; else
  sapling, sticks), and the 14 fruit trees (`FRUIT`) drop their fruit (`FRUIT_CHANCES`, 4x vanilla's apple rate).
- Saddles: also 3 leather + 2 iron (`src/main/resources/.../recipe/own/crafting/`); pouches: leather + string
  (small only, `LSL / L`; no silk item); bigger pouches only by combining (`content/pouch/PouchCombineRecipe`,
  `own/pouch_combine`: pouches whose slots add up to a bigger one, contents kept; 2 small = medium, small + medium
  or 3 small = large).
- `PouchItem` keeps items in `DataComponents.CONTAINER`; `PouchMenu`/`client/PouchScreen` on LOTR's layout with the
  name field (`Pouches.Rename` payload). Pick-ups go into pouches when the inventory is full.
- GameTests `FarmTests`. `GameTestHelper.destroyBlock` drops nothing: use `level.destroyBlock(pos, true)` to test loot.

## Create compat (2026-11-06)
- `converter/create_compat.py` writes 134 cross-mod recipes under `recipe/create/` (conditional on
  `neoforge:mod_loaded` create) and `c:ores/*` / `c:nuggets/*` tags; every match and why is in
  `converter/create/crossref.json`. Re-extract Create's recipes when the pack's Create version changes. The recipe
  count GameTest skips `create/`, because the test server runs without Create.
- Faction mechanical crafters (2026-11-19, Aaron: "literally duplicate the autocrafters, one for each faction ...
  tinted that faction's color"): `compat/create` (registered only when Create is loaded; lotr compiles against Create,
  `create_version` in lotr/gradle.properties, dev/GameTest runs load it with `-PwithCreate`). One
  `<table>_mechanical_crafter` per faction table (27, `converter/create_crafters.py` `TABLES` table -> faction ->
  `content/create_crafters.json` with the tint = faction colour lightened `LIGHTEN`). `FactionCrafterBlock` extends
  Create's `MechanicalCrafterBlock` with our block entity type (`FactionCrafterBlockEntity`); Create's renderer,
  cogwheel visual, stress impact 2, item handler and unpacking handler are registered for it. Mixins
  (`lotr_port.create.mixins.json`, applied only with Create via `CreateMixinPlugin`): `BlockEntryMixin` makes
  `AllBlocks.MECHANICAL_CRAFTER.has(state)` true for them (every Create crafter check: grids, connected inputs, arms);
  `CrafterRecipeMixin` wraps `RecipeGridHandler.tryToApplyRecipe` in the crafter's tick: when Create finds nothing, a
  grid with faction crafters (any in the chain) makes their tables' recipes (`FactionCrafting`; never ones with an
  `alignment` need). Models: Create's crafter models, every quad given tint index 0 (`FactionCraftersClient`,
  ModifyBakingResult); no connected textures. Made at their own table with Create's recipe (3 per craft) and
  `FactionRecipe.alignment` (faction, `above` 0), checked by `FactionCraftingMenu`. GameTests `FactionCrafterTests`.
  Read Create's code by decompiling the jar locally (allowed by Aaron 2026-11-19); never commit decompiled code.
