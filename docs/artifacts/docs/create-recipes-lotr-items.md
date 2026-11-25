# Create recipes for LOTR items

Oct 26, 2026 · @Aaron · Brainstorm, nothing built yet

## Top picks

If you only build a few, these give the most new automation for the least code:

1. **Barrel drinks in the mixer.** A basin with a water bucket, empty mugs and a barrel recipe's ingredients brews LOTR drinks (ale, mead, cider, wine, miruvor, orc draught and the rest) straight into mugs. Plain heat gives the normal strength; a lit LOTR forge or lava gives a stronger one. Recipes are read from `LOTRBrewingRecipes.recipes`, which we already read for NEI, so every drink comes for free.
2. **Mechanical Saw (new machine).** Logs into 6 planks (crafting gives 4) for every LOTR wood, and a stonecutter mode that turns one rock into one of its brick, pillar, carved or slab shapes. LOTR has hundreds of building blocks made from rock in a crafting grid, so this is the biggest builder win. Recipes can be generated from the crafting list (any recipe of one block type giving shaped variants).
3. **Haunting and Hallowing (new fan modes).** A fan blowing past a Morgul torch turns things Mordor-like (dirt to Mordor dirt, gravel to Mordor gravel, rock to Mordor rock, mushrooms to Morgul shrooms, iron ore to Morgul iron ore). Past an elven torch it reverses some of that (Mordor dirt back to dirt). This is Create's soul-fire haunting with a Middle-earth twist.
4. **Smoking at a hearth (new fan mode).** A fan past a lit LOTR hearth cooks only food (meat, pipeweed leaf, maple), so food lines no longer smelt ores by accident.
5. **Metal sheets for LOTR metals.** Press bronze, copper, tin, silver, orc steel, uruk steel, dwarven steel, Morgul steel, black uruk steel and galvorn ingots into sheets, and use those sheets in a few cross-mod recipes (see Pressing).

## Modern Create recipe types, and what we have

| Create recipe type | What it does in Create | Ours |
| --- | --- | --- |
| Milling | Millstone grinds (wheat to flour, flowers to dye) | Built |
| Crushing | Crushing wheels, ores to crushed ore, stone to gravel | Built |
| Pressing | Ingots to sheets, sugar cane to paper | Built |
| Compacting | Press on a basin, 9 or 4 items into one | Built |
| Mixing / heated / superheated | Mixer on a basin, with blaze burner heat tiers | Built (plain and heated; no superheated) |
| Bulk washing | Fan through water | Built |
| Bulk smelting / blasting | Fan through fire or lava | Built (smelting only; 1.7.10 has no blast furnace) |
| Bulk smoking | Fan through a campfire, food only | **Missing** (no campfire in 1.7.10, use the LOTR hearth) |
| Bulk haunting | Fan through soul fire | **Missing** |
| Sandpaper polishing | Hold sandpaper on an item | Built (deploying sandpaper) |
| Deploying / item application | Deployer applies an item to a block or item | Built |
| Sequenced assembly | Multi-step chain on a belt | Built (precision mechanism only) |
| Mechanical crafting | Big grid crafter | Partly (Faction Mechanism for LOTR tables) |
| Cutting | Mechanical saw, logs and stonecutting | **Missing** |
| Filling / emptying / spouting / draining | Fluids in and out of items | **Missing** (we have no fluid system) |

## Already automated for LOTR (no work needed)

- Every LOTR furnace recipe works in a smelting fan, because the fan uses the furnace recipe list.
- Every shapeless LOTR crafting recipe with 2+ ingredients works in the mixer, and every 2x2 or 3x3 recipe of one ingredient works in compacting (for example nuggets into ingots).
- Every faction table recipe works in the Faction Mechanism.
- Built LOTR recipes: copper, tin, silver, mithril and Morgul iron ore crushing and washing; gem and crystal ores; Mordor, Gondor, Rohan, blue and red rock; chalk; bones of every race; corn and flax; obsidian shards; lembas; forge alloys in heated mixing.

## New recipe ideas by machine

Item names are from memory of LOTR v36 and need checking against the jar before building.

### Mixing

- **Brewing** (top pick 1). Inputs: water bucket + barrel ingredients + empty mugs. Output: filled mugs, buckets come back. Fermentation time becomes mixing time.
- **Hot drinks with heat.** Hot chocolate (milk + cocoa) and similar mug recipes need heat.
- **Dwarven tonic, athelas brew, miruvor** as heated mixing only, so the special drinks still need a forge or lava.
- **Mortar or daub** for LOTR building blocks: clay + gravel + water into daub blocks, mud + straw (dried reeds) into mud bricks.
- **Dyes from LOTR flowers** are milling in Create; most LOTR flowers already have crafting dye recipes, so milling should give 2 dye instead of 1.

### Milling and crushing

- LOTR flowers and plants: milled into 2 dye (bluebell, asphodel, simbelmyne, lavender, marigold, haradflower and so on).
- Pipeweed leaf: milled into pipeweed (skips the drying step, small loss).
- Reeds and papyrus: milled into sugar or paper pulp (see pressing).
- Mumak tusk and rhino horn: crushed into bone meal + a chance of ivory or horn pieces if LOTR has them.
- LOTR gem blocks and amber: crushed back into gems, like Create recycles.
- Scorched stone and Mordor rock bricks: crushed into gravel or ash-like Mordor gravel.
- Any LOTR ores we missed (check the jar for durnor, edhelvir, or similar newer ores).

### Pressing and compacting

- **LOTR metal sheets** (top pick 5): bronze, copper, tin, silver, orc steel, uruk steel, dwarven steel, black uruk steel, Morgul steel, galvorn, mithril (built). Each registered as `plateX` in the ore dictionary so other mods can use them.
- Papyrus or reeds into paper (Create does this with sugar cane).
- Dried reeds compacted into thatch; LOTR thatch is a crafting recipe so check it is not already a 2x2.
- Grapes, apples, pears and olives pressed into juice or oil. Needs fluids or a bottled item, so it waits for a fluid system.
- **Coin press (optional, off by default).** Silver nuggets pressed into silver coins. LOTR coins normally come only from trading and loot, so this would change the LOTR economy. Only worth adding as a server option.

### Washing

- Sand washed into a chance of salt (LOTR salt).
- Mordor gravel and Mordor dirt washed into plain gravel and dirt.
- Rotten LOTR food (rotten flesh from orcs, Morgul shrooms) washed into nothing useful; skip.
- Crushed tin and copper washed into nuggets plus a chance of the other (Create gives side outputs this way).

### Haunting and Hallowing (new fan modes)

- Morgul torch (or Morgul fire): dirt to Mordor dirt, gravel to Mordor gravel, stone to Mordor rock, mushrooms to Morgul shrooms, iron ore to Morgul iron ore, bones to orc bones, rotten flesh to maggoty bread (orc food).
- Elven torch: Mordor dirt to dirt, Mordor gravel to gravel, rotten flesh to leather (Create-like recovery). This makes reclaiming Mordor land automatable.

### Smoking (new fan mode)

- Fan past a lit hearth cooks only food: LOTR meats (deer, bear, rabbit, camel, rhino, mumak, zebra), pipeweed leaf, maple, chestnuts. Uses the furnace list filtered to food items.

### Cutting (new machine: Mechanical Saw)

- Logs to 6 planks for every LOTR wood (mallorn, mirk oak, charred, apple, pear, cherry, lebethron, beech, holly, banana, maple, larch, date palm, mangrove, chestnut, baobab, cedar, fir, pine, lemon, orange, lime, almond, plum, olive, aspen, green oak, red oak, cypress, redwood, kanuka and so on).
- Stonecutting: each LOTR rock and brick into its shapes 1:1 (Gondor, Rohan, Mordor, blue, red, chalk, Arnor, dwarven, high elven, wood elven, Dol Amroth, Ithilien and the rest). Generated from crafting recipes, so new LOTR blocks are picked up automatically.
- Saw cutting down trees placed in front of it, like Create, so a tree farm can be automated.

### Deploying and sequenced assembly

- Lembas is built. Similar: cram (bread + ... on a depot), and wrapping food in leaves.
- Deploy athelas onto a mug of water: athelas brew.
- Deploy pipeweed onto an empty pipe: filled pipe (if LOTR pipes are refillable items).
- Deploy a poison bottle onto a dagger: poisoned dagger (LOTR's crafting recipe, done by a machine).
- Sequenced assembly for **Dwarven or Elven mechanisms**: a LOTR-flavoured version of the precision mechanism, using a mithril sheet, dwarven steel sheet and a gem, needed for higher-tier kinetics parts (for example a mithril-bladed saw).
- Sequenced assembly for arrows and crossbow bolts in bulk: shaft, head, fletching.

## Creative conversions and in-between items

Added after Aaron asked for wilder conversions and new items that link LOTR content together. Every LOTR name here was checked against the v36.15 jar. Items marked **new** would be ours (more `part` metas or a new block).

### New in-between items

| New item | Made from | Used for | What it links |
| --- | --- | --- | --- |
| **Naurite ember** | Naurite crushed | Fuel for the Naurite Burner (superheat); uruk and black uruk steel | Mordor fire to orc metalwork |
| **Starlight dust** | Quendite crystal crushed | Hallowing, ithildin, hithlain, elven mechanism | Elven lands to elven crafts |
| **Shadow essence** | Gulduril crystal crushed | Haunting, Morgul steel, gulduril bricks | Dol Guldur to Morgul |
| **Morgul extract** | Morgul shrooms, Mordor thorns and Mordor moss milled | Bottle of poison (mixing), poisoned arrows and darts | Mordor plants to weapons |
| **Rough gems** (amber, amethyst, opal, ruby, sapphire, topaz, diamond, emerald) | Gem ore crushed | Polished with sandpaper into 2 real gems | Create's polishing step to LOTR gems |
| **Mallorn pulp** | Mallorn leaves and mallorn nuts milled | Elven paper (pressed), Red Book and Mithril Book | Lothlórien to books |
| **Spider silk** | Mirkwood webs and Ungoliant webs crushed | Hithlain (silk + mallorn pulp + starlight dust, mixing), rope | Mirkwood to Lothlórien |
| **Black powder** | Sulfur + saltpeter + coal, mixing | Gunpowder, orc bombs (deployed into an orc steel sheet), sulfur matches, Rhûn fire jars | Mordor mines to siege weapons |
| **Tallow** | Man flesh, rotten flesh or mutton, heated mixing | Candles and torches (orc torch, mallorn torches), salted flesh | Orc camps to lighting |
| **Almond flour** | Almonds milled | Marzipan (+ sugar, mixing) | Dorwinion and Dale baking |
| **LOTR metal sheets** | Every LOTR ingot pressed | LOTR mechanism, casings, rings | All factions to Create machines |

### Creative conversions

- **LOTR mechanism by assembly.** LOTR already has a `mechanism` item (dwarven doors, traps). Sequenced assembly makes it: bronze sheet, cogwheel, iron nugget. This links our machines straight into LOTR's own crafting.
- **Ent growth (new fan mode, past an Ent jar).** Saplings grow, seeds become crops, cobblestone turns mossy, rotten logs and rotten planks become living wood, and corrupt mallorn is healed back to mallorn. Fangorn turned into a machine.
- **Corruption by haunting (past a Morgul torch).** The reverse: wood to rotten wood, mallorn to corrupt mallorn, grass to Mordor grass, flowers to Morgul flowers.
- **Superheated mixing with a Naurite Burner (new heat block, fed naurite embers).** It's the Create blaze burner using Mount Doom fire. Recipes: orc steel + naurite ember to uruk steel, uruk steel + shadow essence to black uruk steel, iron + shadow essence to Morgul steel, silver + starlight dust to ithildin, mithril + starlight dust to elf steel, dwarf steel + blue dye to blue dwarf steel. Galvorn is left out, as it is end-game and should stay rare.
- **Coin recycling.** Coins crushed into silver nuggets. It's a one-way sink, so it can't inflate the LOTR economy, which is safer than a coin press.
- **Treasure piles.** Gold, silver and copper treasure piles crushed back into nuggets, and nuggets compacted into treasure piles for decoration.
- **Rings and goblets.** Crushed back into nuggets. Gold and silver rings set with a gem by deploying (a gem-set ring item).
- **Coral and pearls.** Coral crushed into white sand and bone meal; coral washed for a chance of a pearl.
- **Hobbit kitchen line.** Mixing: flour + egg + milk to hobbit pancakes; maple syrup deployed onto a pancake. Our flour also goes into corn bread, banana bread, lemon cake, olive bread and apple crumble. A smoking fan makes raisins from grapes, chestnut roast and yam roast.
- **Kebab line.** Raw meat deployed onto a stick, then smoked into a shish kebab.
- **Drying.** A smoking fan turns reeds into dried reeds and wet sponges dry; dried reeds compacted into thatch.
- **Gilded iron.** A golden sheet deployed onto an iron block.
- **Bones.** 9 bones compacted into a bone block; a skull cup made by deploying a goblet onto a skeleton skull.

### New filler blocks

- **Faction casings**: dwarven (dwarf steel sheet), elven (mithril sheet + starlight dust), orc (orc steel sheet + chain), Gondor (Gondor rock + bronze). Decorative versions of the kinetic casing; the Faction Mechanism could need its faction's casing.
- **Naurite Burner**: the superheat block.
- **Ent water basin**: a basin variant that fans or mixers can use for Ent growth without an Ent jar.

## Things that need new code (not just recipes)

| Feature | Size | Notes |
| --- | --- | --- |
| Brewing in the mixer | Medium | Mug strength in metadata, buckets returned |
| Haunting and Hallowing fan modes | Small | Add catalyst blocks to the fan |
| Smoking fan mode | Small | Hearth catalyst, food filter |
| Mechanical Saw | Large | New block, renderer, recipe generation from crafting list |
| LOTR metal sheets | Small | New part metas and ore-dict names |
| Fluids (juice, oil, spouts) | Very large | Leave for later |

## Questions for you

- Which of these should I build first? My suggestion is brewing, the two new fan modes, and metal sheets as one batch, then the Mechanical Saw as its own job.
- Coin press: leave it out, or add it behind a config switch that is off by default?
