# World generation: variants, enclosures, wild lands, regions and lairs

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Variants, enclosures, wild lands, regions and lairs (World generation)
- Biome variants (2026-11-14, LOTR's LOTRBiomeVariant; Aaron: "many biomes within the Shire"): `map/Variants`, set in
  `converter/world.py VARIANTS` -> map.json `variants`. Patches of a jittered 176-block grid (seeded by the world
  since 2026-11-25, so each world's patches lie differently) inside a biome (Shire:
  meadows, light forest, forest, hills, wooded hills, birch wood, aspen wood, farmland; 60% of cells) that
  scale its downs and hilliness (blended smoothly between patches) and get their own plants: placed features
  `<biome>_variant/*` gated by placement filter `lotr_port:in_variant` (looked up up to the biome's `feather`, 20 by
  default, away, so edges feather). `lotr_port:orchard_grid` is unused since orchards became enclosures.
  Not separate biomes, as in LOTR: NPCs, structures, roads,
  music and waypoint regions keyed to the biome are unchanged. `MiddleEarthMap.variantAt` names a column's patch.
- Enclosures (2026-11-25, Aaron: Shire orchards "flatter and a lot smaller ... enclosed in fences", and Farmer
  Maggot's fields with 2-block crops sized so farmland makes sense): `map/Enclosures`, set in `converter/world.py
  ENCLOSURES` -> map.json `enclosures`. A 128-block grid (a chunk is always inside one cell); each cell holds at most one
  plot (seeded by the world: Aaron 2026-11-25, "randomness on new worlds") of a kind rolled by `chance` (x `boost`
  in a variant patch), sized in the kind's ranges, kept only if the biome is
  the same over it and its fade, its patch is in `variants`, no road within `road_gap` and the map's ground varies by at
  most `max_relief`. `heightAt` flattens it to one level (fading over `fade` blocks), `variationAt` keeps `roughness` of
  the noise. Feature `lotr_port:enclosure` (`world/feature/EnclosureFeature`, configured `shire_enclosure/<kind>`): fence
  or (by chance) hedge, gates with dirt paths, orchard tree rows, corn 2 high on farmland. Trees stay out (placement
  filter `lotr_port:outside_enclosures` on `trees/<biome>`, and `in_variant`), LOTR structures too (`LotrStructure`).
  Shire: orchards 18-34 x 14-26 (apple/pear, plum; trees 7 apart: at 6 their branches met and chunk order decided
  which grew), corn fields 40-72 x 28-48 (a third hedged, the Marish's hedgerows); flat to 4 blocks past the fence
  (`PLATEAU`: terrain samples the map's height every 4 blocks, so a slope at the fence leaned the plot's edge), then
  20 blocks of fade; the old 176-block orchard patches are the `farmland` variant, where most plots gather. Sizes
  reasoned in world.py (real fields ~200 m, Middle-earth ~11x shorter
  than Tolkien's). GameTests `LandscapeTests`; world check builds the nearest plot of each kind.
- Large variants (2026-11-25, Aaron: Eriador is "primarily just travelled through ... large scale ... but there still
  being variation"): Eriador (1100 km2 as one biome on LOTR's map), Minhiriath and Enedwaith (the Lone-lands had them
  until the wild-land rules below replaced them) have variant
  patches of 640-768 blocks with `warp` (edges meander by noise, share of a cell) and `feather` (plants thin out over ~56
  blocks at an edge). Kinds: meadows, heath, copses, old woods, pinewood, tors (boulders, standing stones), old fields
  (Arnor's tumbled dry-stone walls, `lotr_port:old_walls`, and feral fruit trees), thickets, stump-lands and regrowth
  (Minhiriath's felled forests), wetland. Extras are `configured_feature/variant/*` (world.py `VARIANT_FEATURES`).
- Wild lands (2026-11-25, Aaron, with four reference photos of the Lone-lands: "gritty wilderness ... not overly
  foresty (patches of forests, seeded and following rules that make it feel life like) ... a rocky feel ... custom
  blocks that match the pallet ... Variation without it being jarring ... boulder formations, moss growth, dried up
  creek beds, ravines"): post-step `converter/wilds.py` (after regions.py) for `lone_lands` and `lone_lands_hills`
  (Weather Hills). Blocks (`content/WildBlocks`): weathered_stone, lichen_stone, scree (doesn't fall), moorland_soil,
  stony_earth, heath_moss (the last three in `minecraft:dirt`), tussock and bracken (dry, untinted). Textures are
  vanilla 1.21.1's (andesite, gravel, rooted dirt, coarse dirt, moss, short grass, fern) gradient-mapped onto 5-6 tone
  ramps from the references (cool shadows, warm lights, neighbouring values close); vanilla's moss and coarse dirt
  were too green and too orange beside the tawny grass (grass 0xC2AC66, foliage 0x6B7342). `wilds.py --preview
  <png>` tiles the set. Feature `lotr_port:ground_cover` (`world/feature/GroundCoverFeature`, first in the vegetal
  step, per column on the real ground): dry creek beds cut 1-2 deep (seeded noise zero lines in a sparse mask, never
  by water or on slopes), rock down steep faces and rubble a step less steep, gorge floors rubble, wet hollows moorland
  soil and moss, exposed ground stony earth / scree / flush rock by a patch noise plus eastward stoniness (block x
  17024 -> 48512, Tolkien's barer east towards the Hoarwell); then bracken and heather in patches, tussock elsewhere.
  `lotr_port:boulders` (`BoulderFeature`): clusters of 2-6 half-sunk boulders and tors, lichen tops, moss on the north
  side, more eastwards. Trees only through placement filter `lotr_port:grove` (`Grove`): dense clumps where a seeded
  patch noise (+ hollows 40 blocks round, - east) passes `above`, scrub in a band just under it, a few lone stunted
  trees; the old `trees/lone_lands`, variant patches, old walls and green grass patches are dropped from these biomes.
  Gorges are terrain (`map/Wilds`, map.json `wilds.gorges`, `MiddleEarthMap.gorgeAt`): winding seeded cuts, flat
  floor, steep walls, up to 16 deep, never under sea + 3, faded at roads, rivers and the biome border; terrain is
  sampled every 4 blocks, so narrow creeks are the feature's job. Numbers in wilds.py `SETTINGS`/`GORGES` -> the
  configured feature JSONs. GameTests `WildLandTests`.
  Eriador (Aaron 2026-11-25: "slightly tune the 7 and then add milder versions on top"): its seven variant kinds keep
  their plants and get a light ground-cover pass each (`ground_cover` and `boulders` take `variants`; wilds.py
  `ERIADOR`): stony earth spots in meadows, stony patches and bracken on the heath, mossy hollows in the woods, rock,
  scree and boulders round the tors, a few dry creeks; four milder Lone-lands kinds join map.json's list (`ERIADOR_NEW`,
  a third of the weight): stony_heath, moorland (wet, creeks, pine clumps), rocky_dells (boulders, groves in hollows),
  brushland (scrub patches, lone trees). No gorges, and Eriador keeps its green grass, so it turns tawnier past Bree.
- Seeding (2026-11-25, Aaron: "ensure that this stays seeded so there's randomness on new worlds"): variant patches
  (and their edges), enclosures, lairs and old walls all mix `MiddleEarthMap.seed()` (the world seed, set as the server
  starts; 0 on a client, which never knows it) into their hashes; the planners' per-thread caches reset when it changes.
  The Shire's downs and the region layer's areas stay fixed: they follow LOTR's map, like the biomes.
- Region layer (2026-11-25, Aaron: an "allowed biomes" map over the geographical one, "backend only", for the enchanted
  forest and later Moria and Goblin-town): post-step `converter/regions.py` draws named regions in map pixels into
  `map/regions.json` (`map/Regions`: polygons, rough circles, cut to another region); map.json `overlays` paint a
  sub-biome over chosen biomes inside a region as the map loads (`MiddleEarthMap.paint`), so everything reading the map
  sees it. Sub-biomes are their base biome copied fresh each run plus their own looks, plants and spawns, and join every
  tag and table the base is in. `MiddleEarthMap.inRegion`/`regionsAt`, placement filter `lotr_port:in_region`. Moria
  and Goblin-town are drawn (reserved) but nothing generates there yet: an underground phase would answer
  `MapBiomeSource.getNoiseBiome` by y inside those regions.
- Lairs (2026-11-25, Aaron: nests "relatively small only about a 200 sq/blocks of actual spider nests, and with
  spiders spawning within a larger radius around it (with spawning being affected by destroying nests)"): `map/Lairs`,
  set in `converter/regions.py LAIRS` -> map.json `lairs`. Seeded points on a per-kind grid (spider nests: a 512-block
  cell in three of the enchanted forest's, ~100 a world), each a core (radius 100, Aaron: "200 by 200"; feature
  `lotr_port:spider_nest`: webs, 4 egg sacs, silk bundles) and infested ground (radius 160, ragged) that
  `biomeIndexAt` answers as the lair's biome (`landIndexAt` is the map without lairs). `lair/LairLife`: an egg sac
  (`lair/EggSacBlock`, `spider_egg_sac`) gone hatches spiders and counts against its lair (SavedData
  `lotr_port_lairs`); a spawn on its ground keeps its own table with chance = strength (1 - broken / `dead_at`), else
  the `fallback_biome`'s (data `lair/<kind>.json`, reloads). World check builds the nest nearest the Forest Gate.
- Enchanted Mirkwood (2026-11-25, The Hobbit films): northern Mirkwood from the Forest Gate along the Elf-path to the
  Enchanted River, between the Forest River and the Mountains of Mirkwood, is `mirkwood_enchanted` (dark fog, black
  water, drifting spores, light webs, spiders at night and Wood-elves); its spider nests are lairs (above), their
  ground `mirkwood_spider_nests` (webs, dead trees, spiders by day; `wilderness` so borders.py leaves it nobody's
  land); the map's painted river from the Mountains of Mirkwood to above the Forest River is `enchanted_river`.
  Effects are data
  (`region_effect/*.json`, `effect/RegionEffects`, effects `lotr_port:drowsy` and `enchanted_sleep` in
  `effect/LotrEffects`): drowsiness and now and then nausea in `#lotr_port:enchanted_forest`; in water of
  `#lotr_port:cursed_water` an enchanted sleep (no moving, blind, can't drown) and on waking a turn about; bottles filled
  there are Black Water. Atmosphere files `mirkwood_enchanted.json`, `mirkwood_spider_nests.json`. Not looked at in game
  yet.
- Regions Unexplored / Biomes O' Plenty (2026-11-26, optional; `converter/compat.py`, input `converter/compat/mods.json`):
  their copies of LOTR's things (the 8 shared woods with their id shifts `<w>_log` -> `<w>_wood`, `<w>_wood` ->
  `<w>_bark`, planks/slabs/stairs `<w>_wood_*`; chalk, white sand/sandstone, clover, lavender, hibiscus, reeds, willow
  vines) drop as LOTR's (global loot modifier `lotr_port:replace_drops`, `compat/ReplaceDropsModifier`) and craft 1:1
  into them; what only the two mods share goes to Regions Unexplored's. Report `converter/compat/unify_report.json`.
  Their trees in the world keep their own blocks until broken. 39 sub-biomes (compat.py `SUBBIOMES`: "Gardens of
  Ithilien" from BOP's Mediterranean forest, "Withywindle Willow-groves" from RU's willow forest...) painted in seeded
  patches over their LOTR base by regions.py (`compat.extend`); base everything plus grass/foliage halfway to the mod
  biome's; biome modifiers `neoforge/biome_modifier/compat/` add the mod biome's own vegetation and take out the
  base's `trees/<base>` when that mod is installed (first listed source wins). Not looked at in game with either mod.
