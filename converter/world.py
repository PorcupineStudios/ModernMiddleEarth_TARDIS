"""
Middle-earth world generation data from the LOTR export (world.json) and LOTR's map image:

  lotr/src/generated/resources/lotr_port/map/        map.json (settings, biome table) + biomes.bin.gz (biome per pixel)
  lotr/src/generated/resources/data/lotr_port/       biomes, noise settings (terrain shape + surface blocks), terrain
                                                      noise, dimension type, dimension, world preset
  lotr/src/generated/resources/data/minecraft/       world preset tag (shows "Middle-earth" on the world type button)

Every number here that changes how the world looks is a starting value; the generated JSON is the thing to tune.
"""
import colorsys
import gzip
import json
import math
import io
import os

from PIL import Image

from assets import NS, snake, write_json
from trees import Trees
import flowers

# Old biome numbers -> blocks. LOTR's heightBaseParameter is roughly vanilla's depth (0 = sea level, 1 = high hills,
# 2 = mountains) and heightVariation its scale.
SEA_LEVEL = 62
# Modern stone LOTR had no blocks for, generated the way LOTR spread dirt and gravel underground (blobs in stone)
STONE_BLOBS = ["minecraft:ore_granite_upper", "minecraft:ore_granite_lower", "minecraft:ore_diorite_upper",
               "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower",
               "minecraft:ore_tuff"]
# 1.17+ deepslate versions of ores, so LOTR's ores still show in the deepslate under y 0
DEEPSLATE_ORE = {f"minecraft:{o}_ore": f"minecraft:deepslate_{o}_ore"
                 for o in ("coal", "iron", "gold", "copper", "diamond", "emerald", "lapis", "redstone")}
GROUND_AT_ZERO = 64  # surface y for heightBaseParameter 0
BLOCKS_PER_BASE = 34  # surface y rise per 1.0 of heightBaseParameter
BLOCKS_PER_VARIATION = 48  # hilliness in blocks per 1.0 of heightVariation
MIN_HEIGHT, MAX_HEIGHT = -40, 300
PEAK_BIOME_MIN = 1000  # columns a biome holding a peak needs before its survey isn't mostly the mountain
SURVEY_MIN = 32  # biomes with fewer surveyed columns keep the formula from LOTR's height parameters
STD_PER_VARIATION = 1.0
JAGGED_FROM, JAGGED = 10.0, 3.0  # (2.0 until 2026-11-16: mountains too rounded) crags for hilliness over 10 blocks (mountains), up to 2 x the excess
RIDGES = 2.0  # (1.2 until 2026-11-16: spread was 23 vs LOTR 32-40) and broad ridges and valleys (the hill noise at half the frequency) of 1.2 x the excess
HILL_BIAS = 0.4  # the 4x-steeper ground below lifts the mean ~0.4 x hilliness (measured); lowered by that
# Noise only near the surface (Aaron 2026-11-15: "make it as cheap as possible ... very fast chunk loading"): the
# hills' noise (3D blended, 2D hills, crags) is worked out only where (ground level - y) / (hilliness + 2) lies in
# [-NOISE_BAND_ABOVE, NOISE_BAND_BELOW); further above the ground is air and further below solid whatever the noise
# says, so the plain ground shape stands in (minecraft:range_choice). The bounds must cover the noise's largest swing:
# GameTest NoiseBandTests measures it and fails if they don't. MiddleEarthMap.noiseTop (a mixin) also stops filling
# chunks and scanning for ground at the band's top, so the sky over low land costs nothing.
NOISE_BAND_ABOVE, NOISE_BAND_BELOW = 9.0, 4.0  # measured 2026-11-15: needs 8.35 above, 4 below
MAX_3D = 20.0  # hilliness given LOTR's 3D noise; mountains need it for their crags (8 left them smooth domes)
HILL_2D = 2.5  # 2D hills per block of hilliness: measured against LOTR (terrainRoughness / the exporter's rough), 1.0 gave a third of its spread
VALLEYS = 0.25  # the 2D hills' valleys at a quarter depth (1.18 quarter_negative): hills rise from the land, low
# ground doesn't dip under sea level into ponds everywhere (Aaron, 2026-11-14: too many lakes next to LOTR)
VALLEY_SHIFT = 0.1  # the lifted valleys raise the mean ~0.1 x HILL_2D x hilliness; lowered by that
# Aaron (2026-11-14): a hillier Shire than LOTR's: biome name -> (blocks added to its height, hilliness factor)
# Aaron (2026-11-14): the Shire's own hills ("downs", MiddleEarthMap.downs): round hills that only rise from the land
# (noise hills dip as often as they rise, into ponds). Biome name -> (tallest hill in blocks, share of LOTR's noise
# hilliness kept as roughness on top).
# 2026-11-14 (Aaron: steep round hills that stay grassy): 30 / 20 / 18 on a 112-block grid; the steepest slope stays
# under one block per block (about 0.7, 0.9 in "hills" patches), so every step is a grass edge, never a dirt wall.
DOWNS = {"shire": (30, 0.4), "shire_moors": (20, 0.45), "shire_woodlands": (18, 0.5),
         # Aaron 2026-11-16: the marshes were all water. LOTR's are mostly grass a block or two over the water with
         # pools (marsh rule) in the low ground; low hummocks over a base at the pool level give that
         "shire_marshes": (4, 0.35)}
# ground heights set by hand where the survey is off (shireMarshes: 96 columns, mostly the Brandywine's water)
HEIGHT_FIX = {"shire_marshes": 61.4,
              # #97 (Aaron 2026-11-28): the Dead Marshes are mostly water with a maze of banks; their ground at the
              # pool level, so the water rule (DEAD_MARSH_RULE) reaches most of it
              "dead_marshes": 61.0, "dead_marshes_bogs": 61.0}

# Biome variants (LOTR's LOTRBiomeVariant patches; Aaron 2026-11-14: "many biomes within the Shire"). Not separate
# biomes, as in LOTR: patches of a jittered grid (`cell` blocks), a variant with chance `chance`, else plain
# (MiddleEarthMap / map/Variants). downs / variation scale the terrain (blended smoothly between patches); trees,
# orchards and flowers are placed features gated by lotr_port:in_variant. The Shire's list is LOTR's (FLOWERS, FOREST,
# FOREST_LIGHT, HILLS, HILLS_FOREST, FOREST_BIRCH, FOREST_ASPEN, ORCHARD_SHIRE, ORCHARD_PLUM) at a higher chance.
# trees: (per chunk, [(tree, weight)]); orchard: (row spacing, fill, [(tree, weight)]); flowers: placed features.
VARIANTS = {
    # LOTR's swampLowland (marsh, few trees) and swampUpland (higher, 6x trees): willows, swamp oaks, oaks
    "shire_marshes": {"cell": 96, "chance": 0.75, "list": [
        {"name": "lowland", "weight": 2, "downs": 0.5, "variation": 0.6},
        {"name": "upland", "weight": 1, "downs": 1.8, "variation": 1.2,
         "trees": (4, [("oak_swamp", 4), ("willow", 3), ("oak", 2), ("oak_large", 1)])},
    ]},
    "shire": {"cell": 176, "chance": 0.6, "list": [
        {"name": "meadows", "weight": 3, "downs": 0.55, "variation": 0.7,
         # the flower forest (Aaron 2026-11-25: lots of flowers, LOTR's and vanilla's mixed): flowers.py mixes
         "flowers": ["mix:SHIRE_FLOWER_FOREST", "mix:SHIRE_FLOWER_FOREST_TALL", "minecraft:patch_tall_grass"]},
        {"name": "light_forest", "weight": 3,
         "trees": (3, [("oak", 4), ("oak_large", 2), ("chestnut", 3), ("birch", 2), ("aspen", 1)])},
        {"name": "forest", "weight": 2,
         "trees": (9, [("oak", 4), ("oak_large", 3), ("chestnut", 3), ("chestnut_large", 2), ("birch", 2)])},
        {"name": "hills", "weight": 3, "downs": 1.3, "variation": 1.2, "flowers": ["minecraft:patch_tall_grass"]},
        {"name": "wooded_hills", "weight": 2, "downs": 1.25, "variation": 1.15,
         "trees": (4, [("oak", 4), ("oak_large", 2), ("chestnut", 3), ("birch", 2)])},
        {"name": "birch_wood", "weight": 1,
         "trees": (7, [("birch", 5), ("birch_large", 2), ("birch_tall", 2)])},
        {"name": "aspen_wood", "weight": 1, "trees": (7, [("aspen", 5), ("aspen_large", 2)])},
        # Aaron 2026-11-25: the 176-block orchard patches became small fenced orchards (ENCLOSURES); their patches
        # (same weight) are the Shire's open farmland, where most of the fenced orchards and fields lie. Patches are
        # seeded by the world (map/Variants), so they lie differently in every world.
        {"name": "farmland", "weight": 3, "downs": 0.6, "variation": 0.6,
         "flowers": ["minecraft:patch_tall_grass"]},
    ]},
}

# Eriador's wide lands (Aaron 2026-11-25: Eriador is "primarily just travelled through, the biomes should be large
# scale to avoid players changing biomes too often during travels but there still being variation"). On LOTR's map
# Eriador is one biome of ~1100 km2 (Lone-lands 650, Minhiriath 520, Enedwaith 750), so a traveller rode through the
# same land for 20 km. Here each gets patches about as wide as a ride of a minute or two (cell 640-768 blocks; the
# Shire's are 176), with meandering edges (warp) and plants thinning out over ~50 blocks (feather). The kinds follow
# Tolkien's Eriador: the grass and heath of a land emptied when Arnor fell ("the Lone-lands"), the tors and standing
# stones of the Downs, the remnants of the old forests that once covered Minhiriath and Enedwaith (felled by the
# Numenoreans for their ships: stump-lands and young regrowth), pinewoods towards Evendim and the north, thickets in
# the Lone-lands, and Arnor's lost farms: tumbled dry-stone field walls and fruit trees gone wild.
# trees: (per chunk, a fraction = 1 in n chunks; [(tree, weight)]); extras: (configured feature under variant/,
# per chunk or a fraction, "land" | "plants" | "chunk"); "chunk" features run once per chunk and gate themselves.
_MEADOW = ["minecraft:flower_meadow", "minecraft:patch_tall_grass"]
_HEATH = {"variation": 0.95, "trees": (0.3, [("birch", 3), ("pine", 2), ("oak_shrub", 3)]),
          # heather: one 24-try patch a chunk, ~2.7 plants at PLANT_YIELD = LOTR's Shire Moors (3.1 per chunk in the
          # census, its most heathery biome; LOTR had no heather in Eriador at all). Three 48-try patches were ~16,
          # five times that (Aaron 2026-11-25: "a LOT of shire heather").
          "extras": [("heather", 1, "plants"), ("coarse_ground", 2, "land"), ("ferns", 1, "plants")]}
_COPSES = {"trees": (2, [("oak", 4), ("beech", 3), ("chestnut", 2), ("birch", 2), ("oak_large", 1)]),
           "flowers": ["minecraft:flower_default"]}
_TORS = {"variation": 1.55, "flowers": ["minecraft:patch_tall_grass"],
         "extras": [("grey_boulder", 1, "land"), ("mossy_boulder", 0.5, "land"), ("standing_stone", 1 / 40, "land")]}
_OLD_FIELDS = {"variation": 0.6, "flowers": _MEADOW, "trees": (0.25, [("oak", 3), ("apple", 1), ("pear", 1)]),
               "extras": [("old_walls", 1, "chunk")]}
_THICKETS = {"trees": (3, [("oak_shrub", 4), ("pine_shrub", 2), ("birch", 1)]),
             "extras": [("brambles", 2, "plants")]}
_WILD = {"cell": 768, "chance": 0.9, "warp": 0.35, "feather": 56}


def _v(name, weight, base=None, **kw):
    out = {"name": name, "weight": weight}
    out.update(base or {})
    out.update(kw)
    return out


VARIANTS.update({
    "eriador": dict(_WILD, list=[
        _v("wild_meadows", 3, variation=0.75, flowers=_MEADOW),
        _v("heath", 3, _HEATH),
        _v("copses", 3, _COPSES),
        _v("old_woods", 2, variation=1.1, trees=(9, [("oak_large", 4), ("oak", 3), ("beech_large", 2),
                                                     ("beech", 2), ("chestnut_large", 1), ("oak_tall", 1)]),
           extras=[("ferns", 1, "plants")]),
        _v("pinewood", 1, trees=(6, [("spruce", 4), ("pine", 3), ("larch", 2), ("fir", 2)]),
           extras=[("ferns", 2, "plants")]),
        _v("tors", 2, _TORS),
        _v("old_fields", 2, _OLD_FIELDS),
    ]),
    # the Lone-lands (and Weather Hills) have their own wild-land rules instead: converter/wilds.py (2026-11-25)
    "minhiriath": dict(_WILD, list=[
        _v("stump_lands", 3, variation=0.7, flowers=["minecraft:patch_tall_grass"],
           trees=(0.5, [("birch", 2), ("aspen", 2), ("oak", 1)]), extras=[("stumps", 3, "land")]),
        _v("regrowth", 3, trees=(5, [("birch", 3), ("aspen", 2), ("oak", 2), ("beech", 1), ("birch_tall", 1)])),
        _v("old_fields", 2, _OLD_FIELDS),
        _v("wild_meadows", 2, variation=0.75, flowers=_MEADOW),
        _v("wetland", 1, variation=0.45, trees=(1, [("willow", 2), ("oak_swamp", 2)]),
           extras=[("ferns", 2, "plants")]),
    ]),
    "enedwaith": dict(_WILD, list=[
        _v("grassland", 3, variation=0.8, flowers=["minecraft:patch_tall_grass"],
           extras=[("wheatgrass", 2, "plants")]),
        _v("scrub", 2, _THICKETS),
        _v("copses", 2, _COPSES),
        _v("tors", 2, _TORS),
        _v("heath", 1, _HEATH),
    ]),
})

# Configured features the variants' extras use (configured_feature/variant/<name>.json).
def _patch(blocks, tries=48, spread=6):
    """A random patch of plants (weighted blocks) on grass."""
    provider = ({"type": "minecraft:simple_state_provider", "state": {"Name": blocks[0][0]}} if len(blocks) == 1 else
                {"type": "minecraft:weighted_state_provider",
                 "entries": [{"data": {"Name": b}, "weight": w} for b, w in blocks]})
    return {"type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": 3, "feature": {
        "feature": {"type": "minecraft:simple_block", "config": {"to_place": provider}},
        "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:matching_blocks", "offset": [0, -1, 0],
             "blocks": ["minecraft:grass_block", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:dirt"]}]}}]}}}


def _column(blocks, lo, hi):
    """A column of blocks lo-hi high standing on the ground (standing stones, stumps)."""
    provider = ({"type": "minecraft:simple_state_provider", "state": blocks[0][0]} if len(blocks) == 1 else
                {"type": "minecraft:weighted_state_provider",
                 "entries": [{"data": b, "weight": w} for b, w in blocks]})
    return {"type": "minecraft:block_column", "config": {
        "direction": "up", "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
        "prioritize_tip": False, "layers": [{"height": {"type": "minecraft:uniform", "min_inclusive": lo,
                                                        "max_inclusive": hi}, "provider": provider}]}}


VARIANT_FEATURES = {
    "heather": _patch([(f"{NS}:shire_heather", 6), ("minecraft:short_grass", 2), (f"{NS}:thistle", 1)], tries=24),
    "ferns": _patch([("minecraft:fern", 3), ("minecraft:large_fern", 1)], tries=32),
    "wheatgrass": _patch([(f"{NS}:wheatgrass", 4), ("minecraft:short_grass", 2), (f"{NS}:thistle", 1)]),
    "brambles": _patch([(f"{NS}:blackberry_bush", 2), (f"{NS}:wildberry_bush", 1), (f"{NS}:nettles", 2)], tries=24,
                       spread=4),
    "coarse_ground": {"type": "minecraft:disk", "config": {
        "state_provider": {"fallback": {"type": "minecraft:weighted_state_provider", "entries": [
            {"data": {"Name": "minecraft:coarse_dirt"}, "weight": 3}, {"data": {"Name": "minecraft:gravel"}, "weight": 1}]},
            "rules": []},
        "target": {"type": "minecraft:matching_blocks", "blocks": ["minecraft:grass_block"]},
        "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}, "half_height": 1}},
    "grey_boulder": {"type": "minecraft:forest_rock", "config": {"state": {"Name": "minecraft:andesite"}}},
    "mossy_boulder": {"type": "minecraft:forest_rock", "config": {"state": {"Name": "minecraft:mossy_cobblestone"}}},
    "standing_stone": _column([({"Name": "minecraft:stone"}, 3), ({"Name": "minecraft:andesite"}, 1)], 2, 4),
    "stumps": _column([({"Name": "minecraft:oak_log"}, 3), ({"Name": "minecraft:birch_log"}, 1)], 1, 2),
    # Arnor's tumbled field walls: LOTR's dry-stone with cobble, fields ~44 blocks across
    "old_walls": {"type": f"{NS}:old_walls", "config": {
        "blocks": [{"data": {"Name": f"{NS}:drystone"}, "weight": 5}, {"data": {"Name": "minecraft:cobblestone"}, "weight": 2},
                   {"data": {"Name": "minecraft:mossy_cobblestone"}, "weight": 3}],
        "field": 44, "keep": 0.6, "ruin": 0.45, "tall": 0.35, "variants": ["old_fields"]}},
}

# Enclosures (Aaron 2026-11-25: the Shire's orchards "flatter and a lot smaller, also it should be enclosed in
# fences", and "a farmer's field with 2 block high crops, research farmer maggot's fields ... think about how big it
# makes sense for farmland to be and tie that into the min/max size"): map/Enclosures (terrain) and the
# lotr_port:enclosure feature (fences, trees, crops). Sizes: a real English field of the Shire's model (enclosure-era
# Warwickshire/Oxfordshire) is 4-8 ha, 200-280 m a side, and Bamfurlong ("bean-field", Maggot's farm in the Marish)
# names a furlong, 220 yards. Middle-earth here is about 11x shorter than Tolkien's (the Shire is ~17 km across, his
# ~190) while houses are full size, so fields are scaled between the two (about a quarter of real width): corn fields
# 40-72 x 28-48 blocks (0.3 ha at most, a little hobbit field you can still get lost in, as Frodo and Sam did), orchards
# 18-34 x 14-26 (a smial's orchard of 6-16 trees seven blocks apart, as standard orchards' 7-10 m: at six, the fruit
# trees' branches met and chunk order decided which grew; the old patches were 176 blocks, ~50 times the
# area). Fields gather in the farmland patches (boost), all keep 10 blocks off roads. Ground under a plot may vary by
# max_relief before flattening, which keeps them out of the downs' steep sides. The Marish's fields have "hedges,
# gates and dikes": a third of the corn fields are hedged instead of fenced.
ENCLOSURES = {
    # fade 24 (a 4-block plateau, then 20 blocks of slope): up to 10 blocks of relief eases back at under a block per
    # block; 128 - 2 x (24 + 4) leaves exactly the 72 blocks of the longest field
    "cell": 128, "fade": 24, "gap": 4, "road_gap": 10,
    "biomes": {
        "shire": [
            {"name": "orchard", "chance": 0.07, "length": [18, 34], "width": [14, 26], "max_relief": 6,
             "roughness": 0.1, "gates": 1, "variants": ["plain", "meadows", "farmland", "light_forest"],
             "boost": {"farmland": 3},
             "feature": {"fence": {"Name": f"{NS}:apple_fence"}, "gate": {"Name": f"{NS}:apple_fence_gate"},
                         "path": {"Name": "minecraft:dirt_path"}, "headland": 1, "spacing": 7, "fill": 0.9,
                         "trees": [("apple", 3), ("pear", 2)]}},
            {"name": "plum_orchard", "chance": 0.035, "length": [18, 30], "width": [14, 24], "max_relief": 6,
             "roughness": 0.1, "gates": 1, "variants": ["plain", "meadows", "farmland", "light_forest"],
             "boost": {"farmland": 3},
             "feature": {"fence": {"Name": f"{NS}:plum_fence"}, "gate": {"Name": f"{NS}:plum_fence_gate"},
                         "path": {"Name": "minecraft:dirt_path"}, "headland": 1, "spacing": 7, "fill": 0.9,
                         "trees": [("plum", 1)]}},
            {"name": "corn_field", "chance": 0.2, "length": [40, 72], "width": [28, 48], "max_relief": 10,
             "roughness": 0, "gates": 2, "variants": ["plain", "meadows", "farmland"], "boost": {"farmland": 3},
             "feature": {"fence": {"Name": "minecraft:oak_fence"}, "gate": {"Name": "minecraft:oak_fence_gate"},
                         "post": {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}},
                         "hedge": {"Name": "minecraft:oak_leaves", "Properties": {"persistent": "true",
                                                                                  "distance": "7",
                                                                                  "waterlogged": "false"}},
                         "hedge_chance": 0.35, "hedge_height": 2, "path": {"Name": "minecraft:dirt_path"},
                         "headland": 1,
                         "crop": {"block": {"Name": f"{NS}:corn_stalks", "Properties": {"age": "7", "corn": "false"}},
                                  "min_height": 2, "max_height": 2, "flag": "corn", "flag_chance": 0.35,
                                  "soil": {"Name": "minecraft:farmland", "Properties": {"moisture": "7"}},
                                  "water_every": 0,
                                  # Aaron 2026-11-25: rows spaced apart, not a solid block of corn; a trodden
                                  # furrow between rows (no grass grows on paths)
                                  "row_spacing": 2, "furrow": {"Name": "minecraft:dirt_path"}}}},
        ],
    },
}
SURVEY_OFFSET = 1.0  # the survey's ground is the first air block; the port's map height lands about a block higher
# LOTR's ground patches compare one-octave Perlin sums to thresholds; 1.18's normal noise spreads about half as wide
NOISE_SCALE = 0.45
BORDER_WOBBLE = 40  # blocks a biome border or slope can shift
BLUR = 0  # pixels of box blur on heights (0 keeps one-pixel rivers)
OCEAN = "ocean"
# Mountain ranges' own terrain (Aaron 2026-11-15: "the misty mountains ... should be incredibly hard to navigate on
# foot ... change depending on seed ... a sense of awe at gigantic mountain ranges with basically no passes (but still
# huge variety)"; no needle spires): a seeded height field read off the map (map/Ranges, whose javadoc explains each
# number) replacing the biomes' noise hilliness. Prototyped on the map with perspective renders (2026-11-15): in the
# Misty Mountains valleys lie at 200-300, ridges 400-600, and the lowest crossing of any 6 km stretch tops out at
# y 340-490.
# Middle-earth's build limit (vanilla's is 320): its dimension type and noise go up to here.
TOP_Y = 640

RANGES = {
    # range biome: height scale (the Misty Mountains are the giants; the others are scaled down from them)
    "biomes": {f"{NS}:{b}": k for b, k in (("misty_mountains", 1.0), ("grey_mountains", 0.8),
                                           ("forodwaith_mountains", 0.75), ("angmar_mountains", 0.7),
                                           ("white_mountains", 0.9), ("blue_mountains", 0.75),
                                           ("mordor_mountains", 0.7), ("red_mountains", 0.7),
                                           ("wind_mountains", 0.6), ("harad_mountains", 0.65),
                                           ("mirkwood_mountains", 0.55))},
    "reach": 10.0,  # map pixels in from the edge to the core's floor height
    "floor": 85.0,  # the valley floors' climb from the edge to the core: the wall that leaves no low passes
    "relief": 300.0,  # ridges over the floor (x the region's own 0.75-1.25)
    "region": 2600.0, "blend": 0.35,  # region grid (blocks; each one kind of ridges) and how far regions blend
    "band": 12.0, "sharp": 3.0, "strata": 0.8,  # cliff bands on steep ground: height, sharpness, strength
    "ceiling": 610,  # summits eased under this y
}

ROAD_HALF_WIDTH = 3.1  # blocks either side of a road's middle (Aaron 2026-11-25: 6-7 wide, was 2.1 = 4-5; bridges
# 1 more each side)
# Plants on roads (Aaron 2026-11-25: no grass or flowers on roads, sparse overgrowth on run-down ones): feature
# lotr_port:road_verge (world/feature/RoadVergeFeature) runs after the vegetation and clears road columns. Roads
# with LOTR's repair 1 stay bare; on worn roads (repair < 1) a column stays green with chance (1 - repair) x stretch
# (smooth per-cell factor, so some stretches are clean and some overgrown) x edge (middle .. 1) x gap / laid.
ROAD_VERGE = {"margin": 8, "cell": 24, "stretch_min": 0.0, "stretch_max": 2.0, "middle": 0.3, "gap": 0.5,
              "laid": 0.08, "sow": 0.6, "plants": [
                  {"block": "minecraft:short_grass", "weight": 10}, {"block": f"{NS}:short_grass", "weight": 5},
                  {"block": f"{NS}:wheatgrass", "weight": 2}, {"block": "minecraft:fern", "weight": 1},
                  {"block": f"{NS}:thistle", "weight": 1}]}
# LOTR's ground plants (2026-11-25, Aaron: thistles and ALL LOTR weeds and flowers, with LOTR's spawning rules).
# Rules, from the exporter (world.json biome "plants"): which plant each of the decorator's patches is: "grass"
# (getRandomGrass: LOTR's short grass, flowery grass, wheatgrass, thistle, nettles, clover, flax, arid grass...),
# "double_grass", "flowers" (the biome's weighted flower list: elanor, niphredil, simbelmyne, bluebells...) and
# "double_flowers". Amounts, from the plant census (export/plants_census.json, PlantCensus: every plant counted on
# LOTR's own generated ground, per biome): plants per chunk. Each kind's total (all its plants per chunk in the
# census) is shared out by the rules; plants the census finds that no rule names (a biome's own decorate: athelas,
# berry bushes, corn...) become patches of their own at their measured rate and patch size. Without a census, the
# decorator's per-chunk counts x LOTR_PLANTS_PER_PATCH stand in.
SOIL_MIN_SHARE = 0.02  # a plant's ground counts for lotr_port:grows_on/<plant> above this share of its finds
VANILLA_DIRT = {"minecraft:dirt", "minecraft:grass_block", "minecraft:podzol", "minecraft:coarse_dirt",
                "minecraft:mycelium", "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:mud",
                "minecraft:farmland"}
RARITY_STEPS = [1, 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 25, 32, 40, 50, 64, 80, 100, 128, 160, 200, 256, 320, 400,
                512, 640, 800, 1000]
PLANT_TRIES = 32  # tries per patch (vanilla 1.18's grass patch)
# Plants per try in the port (2026-11-25: measured 0.17 with 32-try patches by plant_compare.py against the port's own
# census, -Dlotr_port.plantcensus; the port then had 0.34 of LOTR's plants at the guessed 0.5).
# Run 2 (rates and patch sizes per plant): 0.90 of LOTR's plants -> 0.153; outliers per plant in
# converter/plant_calibration.json (plant_compare.py --calibrate: carpets like withered moss survive anywhere, vanilla's
# own meadow flowers add dandelions...), dividing that plant's rate.
PLANT_YIELD = 0.153
PLANT_MIN_TRIES = 4  # a patch of LOTR's rare flowers is one to three plants
PLANT_MAX_TRIES = 64  # dense grass: several patches a chunk rather than one huge one
TRIES_STEPS = [4, 6, 8, 12, 16, 24, 32, 48, 64]  # PLANT_MIN_TRIES..PLANT_MAX_TRIES, shared by the biomes
LOTR_PLANTS_PER_PATCH = {"grass": 24, "double_grass": 8, "flowers": 12, "double_flowers": 6}  # no-census stand-in
PER_CHUNK = {"grass": "grassPerChunk", "double_grass": "doubleGrassPerChunk", "flowers": "flowersPerChunk",
             "double_flowers": "doubleFlowersPerChunk"}
PLANT_KINDS = ["grass", "double_grass", "flowers", "double_flowers", "extra"]
MIN_PER_CHUNK = 0.02  # plants per chunk below which a census-only plant is left out (one in 50 chunks)
# 1.7.10 vanilla plants -> 1.21 ids
VANILLA_PLANTS = {
    "minecraft:tallgrass#0": "minecraft:dead_bush", "minecraft:tallgrass#1": "minecraft:short_grass",
    "minecraft:tallgrass#2": "minecraft:fern", "minecraft:yellow_flower#0": "minecraft:dandelion",
    "minecraft:deadbush#0": "minecraft:dead_bush", "minecraft:waterlily#0": "minecraft:lily_pad",
    "minecraft:reeds#0": "minecraft:sugar_cane", "minecraft:cactus#0": "minecraft:cactus",
    "minecraft:red_mushroom#0": "minecraft:red_mushroom", "minecraft:brown_mushroom#0": "minecraft:brown_mushroom",
    "minecraft:pumpkin#0": "minecraft:pumpkin", "minecraft:melon_block#0": "minecraft:melon",
    "minecraft:vine#0": "minecraft:vine", "minecraft:sapling#0": "minecraft:oak_sapling",
    **{f"minecraft:red_flower#{i}": f"minecraft:{n}" for i, n in enumerate(
        ["poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip",
         "oxeye_daisy"])},
    **{f"minecraft:double_plant#{i}": f"minecraft:{n}" for i, n in enumerate(
        ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"])},
}
# Exported plant generators that hold no block of their own (LOTR's double flower generator places
# LOTRMod.doubleFlower with its meta)
PLANT_ALIASES = {"lotr.common.world.feature.LOTRWorldGenDoubleFlower": "lotr:tile.doubleFlower"}
# Census plants other features already place (vanilla patches, reeds, forest floor, crops of LOTR's decorator that
# world.py places elsewhere), or that are not ground plants (vines hang from trunks, saplings, crops).
CENSUS_SKIP_PREFIX = ("minecraft:vine", "minecraft:waterlily", "minecraft:reeds", "minecraft:cactus",
                      "minecraft:deadbush", "minecraft:red_mushroom", "minecraft:brown_mushroom", "minecraft:pumpkin",
                      "minecraft:melon_block", "minecraft:sapling", "minecraft:wheat", "minecraft:carrots",
                      "minecraft:potatoes", "lotr:tile.reed", "lotr:tile.deadMarshPlant", "lotr:tile.fallenLeaves",
                      "lotr:tile.sapling", "lotr:tile.leaves", "lotr:tile.vine", "lotr:tile.mirkVines",
                      "lotr:tile.willowVines", "lotr:tile.driedReeds", "minecraft:cocoa", "lotr:tile.banana",
                      "lotr:tile.date", "lotr:tile.yam", "lotr:tile.grapevine", "lotr:tile.lettuce", "lotr:tile.leek",
                      "lotr:tile.turnip", "lotr:tile.pipeweed#", "lotr:tile.flax#")  # crops and fruit: farms, trees
# Small rivers (LOTR made these with a river layer, not on the map). Widths are in noise units: one is about 11 blocks.
# Aaron 2026-11-14: fewer rivers (scale 1700; width in noise units scaled by 900/1700).
# Aaron 2026-11-16: LOTR's small rivers are narrow (its NarrowRivers layer); 0.025/1.5 gave wide green-banked channels.
# keep_biome: dark and dry lands keep their own ground on the banks and bed instead of the green river biome.
RIVER_KEEP = ["mordor", "mordor_mountains", "gorgoroth", "udun", "nan_ungol", "morgul_vale", "dagorlad",
              "eastern_desolation", "brown_lands", "dead_marshes", "dol_guldur", "mirkwood_corrupted", "angmar",
              "angmar_mountains", "ettenmoors", "coldfells", "fangorn_wasteland", "ithilien_wasteland", "nan_curunir",
              "emyn_muil", "near_harad", "near_harad_red_desert", "near_harad_semi_desert", "far_harad_arid",
              "far_harad_arid_hills", "harad_mountains", "last_desert", "forodwaith", "forodwaith_mountains",
              "forodwaith_glacier", "tundra", "far_harad_volcano", "half_troll_forest"]
RIVERS = {"biome": f"{NS}:river", "scale": 1700, "width": 0.00688, "bank": 2.5, "biome_width": 0.7, "bed": 56,
          "max_height": 100, "min_height": 63, "seed": 1937, "keep_biome": [f"{NS}:{b}" for b in RIVER_KEEP]}
# LOTRMountains peaks roughed up (MiddleEarthMap.MountainShape): lobed foot, ridged flanks, hills kept on the slopes
MOUNTAIN_SHAPE = {"warp": 0.35, "ridges": 0.22, "scale": 40.0, "flatten": 0.25, "seed": 1213}

# Old surface blocks (registry name + meta) that are vanilla in 1.21.
VANILLA_SURFACE = {
    "minecraft:grass#0": "minecraft:grass_block",
    "minecraft:dirt#0": "minecraft:dirt",
    "minecraft:dirt#1": "minecraft:coarse_dirt",
    "minecraft:dirt#2": "minecraft:podzol",
    "minecraft:stone#0": "minecraft:stone",
    "minecraft:sand#0": "minecraft:sand",
    "minecraft:sand#1": "minecraft:red_sand",
    "minecraft:gravel#0": "minecraft:gravel",
    "minecraft:snow#0": "minecraft:snow_block",
    "minecraft:ice#0": "minecraft:packed_ice",
    "minecraft:clay#0": "minecraft:clay",
    "minecraft:hardened_clay#0": "minecraft:terracotta",
}

# Every biome takes a subset of these in this order, so the feature order is the same everywhere (1.21 requires it).
ORES = ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_diorite_upper",
        "minecraft:ore_andesite_upper", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
        "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
        "minecraft:ore_redstone", "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper",
        "minecraft:ore_clay"]
VEGETATION = ["minecraft:trees_taiga", "minecraft:trees_jungle", "minecraft:trees_birch_and_oak",
              "minecraft:trees_plains", "minecraft:trees_savanna", "minecraft:flower_default",
              "minecraft:patch_grass_forest", "minecraft:patch_grass_plain", "minecraft:patch_dead_bush",
              "minecraft:patch_cactus_desert", "minecraft:patch_sugar_cane", "minecraft:patch_tall_grass",
              "minecraft:patch_large_fern"]
# vanilla's carvers as the port's biome-aware copies (biomeconfig.py writes them; BiomeCarvers.java)
CARVERS = ["lotr_port:cave", "lotr_port:cave_extra_underground", "lotr_port:canyon"]

GRAZERS = [("minecraft:sheep", 12, 2, 4), ("minecraft:pig", 10, 2, 4), ("minecraft:chicken", 10, 2, 4),
           ("minecraft:cow", 8, 2, 4)]

class World:
    def __init__(self, gen, export, out, jar):
        self.gen = gen
        self.export = export
        self.out = out
        self.data = os.path.join(out, "data")
        self.jar = jar
        self.report = gen.report
        # LOTR's real ground per biome and its mountain peaks, measured by the exporter's survey (ShotRunner)
        survey = os.path.join(os.path.dirname(os.path.abspath(__file__)), "export", "terrain_survey.json")
        self.ground_noises = {}
        self.peak_biomes = set()
        self.peak_biome_of = {}
        self.survey = json.load(open(survey, encoding="utf-8")) if os.path.exists(survey) else {"biomes": {}}

    # ------------------------------------------------------------------ biomes

    def biome_id(self, b):
        return snake(b["fields"]["biomeName"])

    def surface_block(self, block, meta):
        key = f"{block}#{meta}"
        if key in VANILLA_SURFACE:
            return VANILLA_SURFACE[key]
        new = self.gen.idtable.get(key)
        if new:
            return new
        if block.startswith("minecraft:") and meta == 0:
            return block  # ores, ice: same name in 1.21
        self.report["unknown_surface_blocks"].append(key)
        return "minecraft:stone"

    def biome(self, b):
        f = b["fields"]
        dec = b.get("decorator", {})
        temp = float(f.get("temperature", 0.8))
        rain = max(0.0, min(1.0, float(f.get("rainfall", 0.5))))
        top = self.surface_block(f["topBlock"], f.get("topBlockMeta", 0))
        water = f.get("waterColorMultiplier", 0xFFFFFF) & 0xFFFFFF
        effects = {
            "fog_color": 12638463,
            "sky_color": sky_color(temp),
            "water_color": 4159204 if water == 0xFFFFFF else water,
            "water_fog_color": 329011,
            "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8,
                           "offset": 2.0},
        }
        colors = b.get("biomeColors", {})  # LOTR's own biome colours (LOTRBiome.biomeColors)
        for old, new in (("grass", "grass_color"), ("foliage", "foliage_color"), ("sky", "sky_color"),
                         ("fog", "fog_color")):
            if isinstance(colors.get(old), int):
                effects[new] = colors[old] & 0xFFFFFF
        trees = dec.get("treesPerChunk", 0)
        lotr_trees = self.trees.biome(self.biome_id(b), b, enclosed=self.biome_id(b) in ENCLOSURES["biomes"])
        veg = set()
        if top == "minecraft:grass_block" or top.endswith("jungle_grass"):
            if trees > 0 and not lotr_trees:
                if temp < 0.3:
                    veg.add("minecraft:trees_taiga")
                elif top.endswith("jungle_grass") or trees >= 20:
                    veg.add("minecraft:trees_jungle")
                elif trees >= 3:
                    veg.add("minecraft:trees_birch_and_oak")
                elif temp >= 1.2:
                    veg.add("minecraft:trees_savanna")
                else:
                    veg.add("minecraft:trees_plains")
            if "plants" not in b:  # an export without LOTR's plant rules: vanilla's stand-ins
                if dec.get("flowersPerChunk", 0) > 0:
                    veg.add("minecraft:flower_default")
                veg.add("minecraft:patch_grass_forest" if trees >= 3 else "minecraft:patch_grass_plain")
                if dec.get("doubleGrassPerChunk", 0) > 0:
                    veg.add("minecraft:patch_tall_grass")
                if dec.get("enableFern"):
                    veg.add("minecraft:patch_large_fern")
        if top in ("minecraft:sand", "minecraft:red_sand"):
            veg.add("minecraft:patch_dead_bush")
            if dec.get("cactiPerChunk", 0) > 0 or temp >= 1.5:
                veg.add("minecraft:patch_cactus_desert")
        if dec.get("canePerChunk", 0) > 0:
            veg.add("minecraft:patch_sugar_cane")
        features = [[] for _ in range(11)]
        features[1] = [f"{NS}:crater_lava"]  # Mount Doom's lava (only near a volcano)
        features[2] = [f"{NS}:roads"]
        ores = self.biome_ores.get(self.biome_id(b)) or list(ORES)
        features[6] = ores + [x for x in STONE_BLOBS if x not in ores]
        features[7] = [f"{NS}:dripstone"]  # LOTR's 3 stalactites a chunk under y 60, as dripstone
        if dec.get("quagmirePerChunk", 0) > 0:  # LOTR's quagmire patches; mud round them (1.19's swamp ground)
            features[2] = [f"{NS}:quagmire_{dec['quagmirePerChunk']}", f"{NS}:mud_patch"] + features[2]
        features[9] = ([lotr_trees] if lotr_trees else []) + self.variant_plants(self.biome_id(b)) + \
            self.enclosure_features(self.biome_id(b)) + self.forest_floor(dec) + self.marsh_plants(b) + [
            v for v in VEGETATION if v in veg] + self.lotr_plants(b)
        features[10] = [f"{NS}:road_verge", "minecraft:freeze_top_layer"]
        creatures = []
        if top == "minecraft:grass_block" and temp < 1.5:
            creatures = [{"type": t, "weight": w, "minCount": a, "maxCount": c} for t, w, a, c in GRAZERS]
        return {
            "has_precipitation": bool(f.get("enableRain", True) or f.get("enableSnow", False)),
            "temperature": temp,
            "downfall": rain,
            "effects": effects,
            "spawners": {"creature": creatures} if creatures else {},
            "spawn_costs": {},
            "carvers": {"air": CARVERS},
            "features": features,
        }

    def variant_plants(self, biome):
        """Placed features for a biome's variants (VARIANTS): each patch's trees, orchard rows and flowers, gated by
        lotr_port:in_variant. Written under shire_variant/<variant>_<what> etc."""
        if biome not in VARIANTS:
            return []
        gen = os.path.join(self.data, NS, "worldgen")
        out = []
        on_land = [{"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                   {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"}]

        def write(name, feature, placement):
            write_json(os.path.join(gen, "placed_feature", f"{biome}_variant", name + ".json"),
                       {"feature": feature, "placement": placement})
            out.append(f"{NS}:{biome}_variant/{name}")

        def selector(name, trees):
            total = sum(w for _, w in trees)
            feats, left = [], 1.0
            for t, w in trees[:-1]:  # random_selector: each entry's chance is of what is left
                share = w / total
                feats.append({"feature": f"{NS}:tree/{t}", "chance": round(min(1.0, share / left), 4)})
                left -= share
            write_json(os.path.join(gen, "configured_feature", f"{biome}_variant", name + ".json"), {
                "type": "minecraft:random_selector",
                "config": {"features": feats, "default": f"{NS}:tree/{trees[-1][0]}"}})
            return f"{NS}:{biome}_variant/{name}"

        def count(n):
            return ([{"type": "minecraft:count", "count": round(n)}] if n >= 1
                    else [{"type": "minecraft:rarity_filter", "chance": max(1, round(1 / n))}])

        for v in VARIANTS[biome]["list"]:
            gate = {"type": f"{NS}:in_variant", "variants": [v["name"]]}
            if "trees" in v:
                n, trees = v["trees"]
                write(v["name"] + "_trees", selector(v["name"] + "_trees", trees),
                      count(n) + [{"type": "minecraft:in_square"}, gate] + on_land)
            if "orchard" in v:
                spacing, fill, trees = v["orchard"]
                write(v["name"] + "_rows", selector(v["name"] + "_rows", trees),
                      [{"type": f"{NS}:orchard_grid", "spacing": spacing, "fill": fill}, gate] + on_land)
            for i, flower in enumerate(v.get("flowers", [])):
                patches = [{"type": "minecraft:count", "count": 2}]
                if flower.startswith("mix:"):  # a flower mix of flowers.py, written for this variant
                    mix = getattr(flowers, flower[4:])
                    write_json(os.path.join(gen, "configured_feature", f"{biome}_variant", mix["name"] + ".json"),
                               flowers.configured(mix))
                    flower, patches = f"{NS}:{biome}_variant/{mix['name']}", flowers.count(mix)
                write(f"{v['name']}_flowers_{i}", flower,
                      patches + [{"type": "minecraft:in_square"}, gate,
                                 {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
                                 {"type": "minecraft:biome"}])
            for name, n, how in v.get("extras", []):
                feature = self.variant_feature(name)
                if how == "chunk":  # gates itself column by column; one shared placed feature runs once a chunk
                    shared = f"variant/{name}"
                    write_json(os.path.join(gen, "placed_feature", shared + ".json"),
                               {"feature": feature, "placement": []})
                    if f"{NS}:{shared}" not in out:
                        out.append(f"{NS}:{shared}")
                    continue
                where = on_land if how == "land" else [
                    {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]
                write(f"{v['name']}_{name}", feature, count(n) + [{"type": "minecraft:in_square"}, gate] + where)
        return out

    def variant_feature(self, name):
        """A configured feature of VARIANT_FEATURES (written once), by id."""
        write_json(os.path.join(self.data, NS, "worldgen", "configured_feature", "variant", name + ".json"),
                   VARIANT_FEATURES[name])
        return f"{NS}:variant/{name}"

    def enclosure_features(self, biome):
        """The biome's enclosure features (ENCLOSURES): one lotr_port:enclosure feature per kind, placed once a chunk
        (the feature asks the map whether the chunk's cell has one of its kind). Written under
        <biome>_enclosure/<kind>; orchard trees as <biome>_enclosure/<kind>_trees."""
        kinds = ENCLOSURES["biomes"].get(biome)
        if not kinds:
            return []
        gen = os.path.join(self.data, NS, "worldgen")
        out = []
        for k in kinds:
            config = {"kind": k["name"]}
            config.update({x: y for x, y in k["feature"].items() if x != "trees"})
            if "trees" in k["feature"]:
                trees = k["feature"]["trees"]
                total = sum(w for _, w in trees)
                feats, left = [], 1.0
                for t, w in trees[:-1]:
                    share = w / total
                    feats.append({"feature": f"{NS}:tree/{t}", "chance": round(min(1.0, share / left), 4)})
                    left -= share
                write_json(os.path.join(gen, "configured_feature", f"{biome}_enclosure", k["name"] + "_trees.json"), {
                    "type": "minecraft:random_selector",
                    "config": {"features": feats, "default": f"{NS}:tree/{trees[-1][0]}"}})
                config["trees"] = f"{NS}:{biome}_enclosure/{k['name']}_trees"
            write_json(os.path.join(gen, "configured_feature", f"{biome}_enclosure", k["name"] + ".json"),
                       {"type": f"{NS}:enclosure", "config": config})
            write_json(os.path.join(gen, "placed_feature", f"{biome}_enclosure", k["name"] + ".json"),
                       {"feature": f"{NS}:{biome}_enclosure/{k['name']}", "placement": []})
            out.append(f"{NS}:{biome}_enclosure/{k['name']}")
        return out

    def plant_patch(self, name, block, per_chunk, tall=(1, 1), on="ground", tries=16, spread=7):
        """A LOTR plant patch (random tries round a spot) as a placed feature; per_chunk under 1 is a 1-in-n chance.
        on: "ground" (solid or water below, as LOTR's reeds) or "water"."""
        below = {"type": "minecraft:matching_fluids", "offset": [0, -1, 0], "fluids": ["minecraft:water"]}
        if on == "ground":
            below = {"type": "minecraft:any_of", "predicates": [
                below, {"type": "minecraft:solid", "offset": [0, -1, 0]}]}
        column = {"type": "minecraft:block_column", "config": {
            "direction": "up", "allowed_placement": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            "prioritize_tip": False, "layers": [{"height": {"type": "minecraft:uniform", "min_inclusive": tall[0],
                                                             "max_inclusive": tall[1]},
                                                  "provider": {"type": "minecraft:simple_state_provider",
                                                               "state": {"Name": block}}}]}}
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", "plant", name + ".json"), {
            "type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": spread, "y_spread": 3, "feature": {
                "feature": column, "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}, below]}}]}}})
        count = ([{"type": "minecraft:count", "count": round(per_chunk)}] if per_chunk >= 1
                 else [{"type": "minecraft:rarity_filter", "chance": max(1, round(1 / per_chunk))}])
        write_json(os.path.join(gen, "placed_feature", "plant", name + ".json"), {
            "feature": f"{NS}:plant/{name}", "placement": count + [
                {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"}]})
        return f"{NS}:plant/{name}"

    def plant_id(self, old):
        """1.21 id of a 1.7.10 plant block#meta (None if the port has none)."""
        if old in VANILLA_PLANTS:
            return VANILLA_PLANTS[old]
        return self.gen.idtable.get(old)

    def calibration(self):
        if not hasattr(self, "_calibration"):
            path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "plant_calibration.json")
            self._calibration = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        return self._calibration

    def census(self):
        if not hasattr(self, "_census"):
            path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "export", "plants_census.json")
            self._census = json.load(open(path, encoding="utf-8"))["biomes"] if os.path.exists(path) else None
        return self._census

    def lotr_plants(self, b):
        """LOTR's ground plants for a biome (see PLANT_TRIES): patches per plant, ordered by (kind, block) so every
        biome lists them in the same order."""
        rules = b.get("plants")
        if not rules:
            return []
        dec = b.get("decorator", {})
        counted = (self.census() or {}).get(b["fields"]["biomeName"])
        seen = (counted or {}).get("plants", {})
        want = {}  # (kind, new id) -> [plants per chunk, plants in chunks that had it, chunks that had it]
        chunks = max(1.0, counted["columns"] / 256.0) if counted else 1.0

        def add(kind, old, per_chunk):
            new = self.plant_id(old)
            if new is None:
                self.report.setdefault("unknown_plants", []).append(old)
                return
            got = seen.get(old, {})
            w = want.setdefault((kind, new), [0.0, 0, 0])
            w[0] += per_chunk
            w[1] += got.get("count", 0)
            w[2] = max(w[2], got.get("chunks_with", 0))

        explained = set()
        for kind in ("grass", "double_grass", "flowers", "double_flowers"):
            if kind == "flowers":
                total_w = sum(f["weight"] for f in rules.get("flowers", []))
                shares = {f"{f['block']}#{f['meta']}": f["weight"] / total_w for f in rules.get("flowers", [])
                          if f["block"] and total_w > 0}
            else:
                shares = rules.get(kind, {})
            shares = {PLANT_ALIASES.get(k.split("#")[0], k.split("#")[0]) + "#" + k.split("#")[1]: v
                      for k, v in shares.items() if v > 0 and not k.startswith("none")}
            if dec.get(PER_CHUNK[kind], 0) <= 0 or not shares:
                continue  # the decorator places none: what the census finds of these is some other rule's
            explained |= set(shares)
            if counted is not None:
                total = sum(seen.get(k, {}).get("per_chunk", 0) for k in shares)
            else:
                total = dec[PER_CHUNK[kind]] * LOTR_PLANTS_PER_PATCH[kind]
            for old, share in shares.items():  # its measured rate; by the rules' share where LOTR showed none
                add(kind, old, seen[old]["per_chunk"] if old in seen else total * share)
        for old, got in sorted(seen.items()):  # plants of the biome's own decorate
            if old in explained or old.startswith(CENSUS_SKIP_PREFIX) or got["per_chunk"] < MIN_PER_CHUNK:
                continue
            add("extra", old, got["per_chunk"])
        out = []
        for (kind, new), (per_chunk, count, with_) in sorted(
                want.items(), key=lambda e: (PLANT_KINDS.index(e[0][0]), e[0][1])):
            # LOTR's patch size: its plants per chunk that had any (dense grass: the whole chunk's, so capped)
            size = count / with_ if with_ else PLANT_TRIES * PLANT_YIELD
            tries = min(TRIES_STEPS, key=lambda t: abs(math.log(t * PLANT_YIELD / max(size, 0.1))))
            patches = per_chunk / (tries * PLANT_YIELD) / self.calibration().get(new, 1.0)
            if patches < 1 / RARITY_STEPS[-1]:
                continue
            out.append(self.plant_feature(kind, new, patches, tries))
        return out

    def plant_soils(self):
        """Tags lotr_port:grows_on/<plant>: the grounds LOTR's plant census found each LOTR plant on (content/PlantBlock
        grows on dirt like vanilla's bushes, and on these: Harad flowers on sand...). A ground counts with at least
        SOIL_MIN_SHARE of the plant's finds; dirt and grass are vanilla's already."""
        census = self.census()
        if not census:
            return 0
        found = {}
        for biome in census.values():
            for old, got in biome["plants"].items():
                for ground, n in got.get("grounds", {}).items():
                    found.setdefault(old, {}).setdefault(ground, 0)
                    found[old][ground] += n
        tags = {}
        for old, grounds in found.items():
            new = self.plant_id(old)
            if not new or not new.startswith(f"{NS}:") or old.startswith(CENSUS_SKIP_PREFIX):
                continue
            total = sum(grounds.values())
            for ground, n in grounds.items():
                if ground == "minecraft:water" or n < 3 or n < SOIL_MIN_SHARE * total:
                    continue
                block, _, meta = ground.partition("#")
                soil = VANILLA_SURFACE.get(ground) or self.gen.idtable.get(ground) or (
                    block if block.startswith("minecraft:") and meta == "0" else None)
                if soil and soil not in VANILLA_DIRT:
                    tags.setdefault(new, set()).add(soil)
        for new, soils in tags.items():
            write_json(os.path.join(self.data, NS, "tags", "block", "grows_on", new.split(":")[1] + ".json"),
                       {"replace": False, "values": sorted(soils)})
        return len(tags)

    def plant_feature(self, kind, block, per_chunk, tries):
        """A plant patch shared by every biome that places it alike: configured plant_patch/<plant>_<tries>, placed
        plant_<kind>/<plant>_c<n> (n patches a chunk) or _r<n> (one patch in n chunks)."""
        short = block.split(":")[1] if block.startswith(f"{NS}:") else block.replace(":", "_")
        if per_chunk < 1:  # rarities on steps of about 25% (the census's own noise is larger), so biomes share them
            per_chunk = 1 / RARITY_STEPS[min(range(len(RARITY_STEPS)),
                                             key=lambda i: abs(math.log(RARITY_STEPS[i] * per_chunk)))]
        rate = f"c{round(per_chunk)}" if per_chunk >= 1 else f"r{round(1 / per_chunk)}"
        configured = f"plant_patch/{short}_{tries}"
        self.grass_patch(configured, block, per_chunk, tries, placed=f"plant_{kind}/{short}_{rate}")
        return f"{NS}:plant_{kind}/{short}_{rate}"

    def grass_patch(self, name, block, per_chunk, tries=32, placed=None):
        """A plant patch like vanilla's patch_grass (spread 7): the plant only where it can live; per_chunk patches a
        chunk (under 1: a 1-in-n chance)."""
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", name + ".json"), {
            "type": "minecraft:random_patch", "config": {"tries": tries, "xz_spread": 7, "y_spread": 3, "feature": {
                "feature": {"type": "minecraft:simple_block", "config": {"to_place": {
                    "type": "minecraft:simple_state_provider", "state": {"Name": block}}}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:all_of", "predicates": [
                        {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                        {"type": "minecraft:would_survive", "state": {"Name": block}}]}}]}}})
        count = ([{"type": "minecraft:count", "count": round(per_chunk)}] if per_chunk >= 1
                 else [{"type": "minecraft:rarity_filter", "chance": max(1, round(1 / per_chunk))}])
        write_json(os.path.join(gen, "placed_feature", (placed or name) + ".json"), {
            "feature": f"{NS}:{name}", "placement": count + [
                {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"}]})
        return f"{NS}:{name}"

    def reed_patch(self, name, block, per_chunk):
        """LOTRWorldGenReeds as feature lotr_port:reeds (world/feature/ReedsFeature): only over still water at most 4
        deep, the stem from the bed up and 1-3 above. (A plain plant patch let reeds stand on dry land and float.)"""
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", "plant", name + ".json"), {
            "type": f"{NS}:reeds", "config": {"block": {"Name": block}, "tries": 16}})
        count = ([{"type": "minecraft:count", "count": round(per_chunk)}] if per_chunk >= 1
                 else [{"type": "minecraft:rarity_filter", "chance": max(1, round(1 / per_chunk))}])
        write_json(os.path.join(gen, "placed_feature", "plant", name + ".json"), {
            "feature": f"{NS}:plant/{name}", "placement": count + [
                {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                {"type": "minecraft:biome"}]})
        return f"{NS}:plant/{name}"

    def marsh_plants(self, b):
        """LOTR's reeds (reedPerChunk patches, dried with dryReedChance) and, in the Dead Marshes, its dead plants
        (LOTRBiomeGenDeadMarshes.decorate)."""
        dec = b.get("decorator", {})
        n, dry = dec.get("reedPerChunk", 0), dec.get("dryReedChance", 0.1)
        out = []
        for block, k in ((f"{NS}:reeds", n * (1 - dry)), (f"{NS}:dried_reeds", n * dry)):
            if k > 0.01:
                name = f"{block.split(':')[1]}_{round(k, 2)}".replace(".", "_")
                out.append(self.reed_patch(name, block, k))
        if b["class"].endswith("LOTRBiomeGenDeadMarshes"):
            out.append(self.plant_patch("dead_marsh_plant", f"{NS}:dead_marsh_plant", 6, tries=64))
            # LOTR's marsh lights (4 a chunk over the water) wait: the block draws as a white cube, not particles
        return out

    def forest_floor(self, dec):
        """LOTR's litter after the trees: fallen leaves (trees / 2 a chunk), leaf bushes (trees / 3) and rotten logs."""
        trees, logs = dec.get("treesPerChunk", 0), dec.get("logsPerChunk", 0)
        if trees <= 0 and logs <= 0:
            return []
        name = f"forest_floor/{max(trees, 0)}_{logs}"
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", name + ".json"), {"type": f"{NS}:forest_floor", "config": {
            "fallen": round(max(trees, 0) / 2, 3), "bushes": round(max(trees, 0) / 3, 3), "logs": logs}})
        write_json(os.path.join(gen, "placed_feature", name + ".json"), {"feature": f"{NS}:{name}", "placement": []})
        return [f"{NS}:{name}"]

    def measured(self, b):
        """LOTR's surveyed ground for the biome; not for biomes holding a LOTRMountains peak, whose average
        includes the mountain (the port adds the peak itself)."""
        got = self.survey["biomes"].get(b["fields"]["biomeName"])
        if b["fields"]["biomeName"] in self.peak_biomes and (not got or got["n"] < PEAK_BIOME_MIN):
            return None
        return got if got and got["n"] >= SURVEY_MIN else None

    def height(self, b):
        f = b["fields"]
        if self.biome_id(b) in HEIGHT_FIX:
            return HEIGHT_FIX[self.biome_id(b)]
        if self.measured(b):
            h = self.measured(b)["mean"] - SURVEY_OFFSET
            return round(max(MIN_HEIGHT, min(MAX_HEIGHT, h)), 2)
        h = GROUND_AT_ZERO + float(f.get("heightBaseParameter", 0.1)) * BLOCKS_PER_BASE
        return round(max(MIN_HEIGHT, min(MAX_HEIGHT, h)), 2)

    def variation(self, b):
        if self.measured(b):  # LOTR's spread of ground heights; the noise gives about STD_PER_VARIATION of it
            return round(self.measured(b)["std"] / STD_PER_VARIATION * DOWNS.get(self.biome_id(b), (0, 1))[1], 2)
        return round(max(0.0, float(b["fields"].get("heightVariation", 0.1))) * BLOCKS_PER_VARIATION, 2)

    # ------------------------------------------------------------------ map

    def find_peak_biomes(self, biomes, world):
        """Names of the biomes the LOTRMountains peaks stand in (the map pixel under each peak)."""
        settings = world["map"]
        scale, ox, oz = settings.get("scale", 128), settings.get("originX", 0), settings.get("originZ", 0)
        index_of_old = {b["fields"]["biomeID"]: b["fields"]["biomeName"] for b in biomes}
        name_of = {int(c, 16): index_of_old.get(old) for c, old in world["colors"].items()}
        img = Image.open(io.BytesIO(self.jar.read("assets/lotr/map/map.png"))).convert("RGB")
        found = set()
        for m in self.survey.get("mountains", []):
            r, g, bl = img.getpixel((m["xCoord"] // scale + ox, m["zCoord"] // scale + oz))
            if name_of.get((r << 16) | (g << 8) | bl):
                found.add(name_of[(r << 16) | (g << 8) | bl])
                self.peak_biome_of[m["name"]] = name_of[(r << 16) | (g << 8) | bl]
        return found

    def mountain(self, m):
        """A LOTRMountains peak for map.json: a cone to its surveyed ground; hills flatten by LOTR's height boost. A
        volcano (lavaRange) has its crater floor at the surveyed ground and the cone's rim LOTR's 2.5x higher boost
        above the land (crater height = 0.4 x (1 - lava/range) x height); lava to LOTR's getLavaHeight."""
        out = {"x": m["xCoord"], "z": m["zCoord"], "range": m["range"], "peak": m["ground"], "boost": m["height"]}
        if m["lavaRange"] > 0:
            land = self.survey["biomes"][self.peak_biome_of[m["name"]]]["mean"]
            rim = land + (m["ground"] - land) / 0.4
            out.update(peak=round(land + (rim - land) * m["range"] / (m["range"] - m["lavaRange"])),
                       crater=m["lavaRange"], floor=m["ground"] - 1,
                       lava=round((1 - m["lavaRange"] / m["range"]) * m["height"] * 0.4 * 110))
        return out

    def write_map(self, biomes, world):
        ids = [self.biome_id(b) for b in biomes]
        index_of_old = {b["fields"]["biomeID"]: i for i, b in enumerate(biomes)}
        color_to_index = {int(c, 16): index_of_old[old] for c, old in world["colors"].items() if old in index_of_old}
        img = Image.open(io.BytesIO(self.jar.read("assets/lotr/map/map.png"))).convert("RGB")
        w, h = img.size
        raw = img.tobytes()
        lut = {}
        pixels = bytearray(w * h)
        unknown = set()
        for i in range(w * h):
            rgb = (raw[3 * i] << 16) | (raw[3 * i + 1] << 8) | raw[3 * i + 2]
            v = lut.get(rgb)
            if v is None:
                v = color_to_index.get(rgb, 255)
                if v == 255:
                    unknown.add(f"{rgb:06x}")
                lut[rgb] = v
            pixels[i] = v
        if unknown:
            self.report["unknown_map_colors"].extend(sorted(unknown))
        settings = world["map"]
        out = os.path.join(self.out, NS, "map")
        os.makedirs(out, exist_ok=True)
        with gzip.GzipFile(os.path.join(out, "biomes.bin.gz"), "wb", compresslevel=9, mtime=0) as f:
            f.write(bytes(pixels))
        write_json(os.path.join(out, "map.json"), {
            "width": w, "height": h,
            "blocks_per_pixel": settings.get("scale", 128),
            "origin_x": settings.get("originX", 0), "origin_z": settings.get("originZ", 0),
            "blur": BLUR, "border_wobble": BORDER_WOBBLE,
            "ocean": f"{NS}:{OCEAN}",
            "rivers": RIVERS,
            "ranges": RANGES,
            # for MiddleEarthMap.noiseTop: the noise band's top, and where water fills to
            "noise_band": {"above": NOISE_BAND_ABOVE, "below": NOISE_BAND_BELOW, "hill_bias": HILL_BIAS,
                           "sea_level": SEA_LEVEL, "top": TOP_Y},
            "variants": variants_json(),
            "enclosures": enclosures_json(),
            "mountain_shape": MOUNTAIN_SHAPE,
            # LOTRMountains: a cone from the land up to the peak's surveyed ground over `range` blocks
            "mountains": [self.mountain(m) for m in self.survey.get("mountains", [])],
            "biomes": [dict({"id": f"{NS}:{i}", "height": self.height(b), "variation": self.variation(b)},
                            **({"downs": DOWNS[i][0]} if i in DOWNS else {}))
                       for i, b in zip(ids, biomes)],
        })
        return w, h

    # ------------------------------------------------------------------ noise settings

    def surface_rule(self, biomes):
        groups = {}
        for b in biomes:
            f = b["fields"]
            top = self.surface_block(f["topBlock"], f.get("topBlockMeta", 0))
            filler = self.surface_block(f["fillerBlock"], f.get("fillerBlockMeta", 0))
            groups.setdefault((top, filler) + self.ground_style(b, top), []).append(f"{NS}:{self.biome_id(b)}")
        on_floor = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                    "secondary_depth_range": 0, "surface_type": "floor"}
        under_floor = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                       "secondary_depth_range": 0, "surface_type": "floor"}
        above_water = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0,
                       "add_stone_depth": False}
        rules = [{
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                        "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
            "then_run": block_rule("minecraft:bedrock"),
        }]
        per_biome = []
        for (top, filler, podzol, rocky, wet, mud, patches, marsh, mtn), ids in sorted(groups.items(), key=str):
            # LOTR's order: its biome's patches pick the top, then rock breaks through high up, marsh pools, podzol
            patch_top = [{"type": "minecraft:condition", "if_true": noise_at(key, thr),
                          "then_run": block_rule(ptop)} for key, thr, ptop, _ in patches]
            patch_fill = [{"type": "minecraft:condition", "if_true": noise_at(key, thr),
                           "then_run": block_rule(pfill or filler)} for key, thr, _, pfill in patches]
            # LOTR's generateMountainTerrain: snow tops and bare stone high on the mountain biomes
            mtn_top, mtn_fill = mountain_rules(mtn) if mtn and mtn != "all" else ([], [])
            on_top = ([DEAD_MARSH_RULE, MARSH_RULE] if marsh else []) + ([ROCKY_RULE] if rocky else []) + mtn_top + patch_top
            on_top += [MUD_RULE] if mud else []
            if podzol is not None:
                on_top.append(podzol_rule(podzol, wet))
            on_top.append(block_rule(top))
            under = ([ROCKY_RULE] if rocky else []) + mtn_fill + patch_fill
            sandstone = [{"type": "minecraft:condition", "if_true": SANDSTONE_DEPTH,  # LOTR: sandstone under sand
                          "then_run": block_rule(SANDSTONE[top])}] if top in SANDSTONE else []
            per_biome.append({
                "type": "minecraft:condition",
                "if_true": {"type": "minecraft:biome", "biome_is": sorted(ids)},
                "then_run": {"type": "minecraft:sequence", "sequence": [
                    {"type": "minecraft:condition", "if_true": on_floor, "then_run": {
                        "type": "minecraft:sequence", "sequence": [
                            {"type": "minecraft:condition", "if_true": above_water,
                             "then_run": {"type": "minecraft:sequence", "sequence": on_top}},
                            block_rule(filler),  # under water: no grass or snow
                        ]}},
                    {"type": "minecraft:condition", "if_true": under_floor,
                     "then_run": {"type": "minecraft:sequence", "sequence": under + [block_rule(filler)]}},
                ] + sandstone},
            })
        rules.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"},
                      "then_run": {"type": "minecraft:sequence", "sequence": per_biome}})
        # Lands of rock (Mordor, Gorgoroth...) are that rock all the way down, as in LOTR, so their ores find it.
        rock_of = {}
        for key, group in groups.items():
            rock = key[1] if key[1].endswith("_rock") else MORDOR_ROCK if key[-1] == "all" else None
            if rock:
                rock_of.setdefault(rock, []).extend(group)
        for filler, ids in sorted(rock_of.items()):
            ids = sorted(ids)
            rules.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": ids},
                          "then_run": {"type": "minecraft:condition", "if_true": {
                              "type": "minecraft:not", "invert": {"type": "minecraft:vertical_gradient",
                                                                 "random_name": "minecraft:deepslate",
                                                                 "true_at_and_below": {"absolute": -48},
                                                                 "false_at_and_above": {"absolute": -40}}},
                              "then_run": block_rule(filler)}})
        rules.append({"type": "minecraft:condition", "if_true": {  # 1.18's deepslate under y 0
            "type": "minecraft:vertical_gradient", "random_name": "minecraft:deepslate",
            "true_at_and_below": {"absolute": 0}, "false_at_and_above": {"absolute": 8}},
            "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:deepslate",
                                                                      "Properties": {"axis": "y"}}}})
        return {"type": "minecraft:sequence", "sequence": rules}

    def ground_style(self, b, top):
        """LOTR's shared ground rules for a biome (LOTRBiome.generateBiomeTerrain): the podzol threshold (grass
        biomes with trees and enablePodzol), rocky high ground (enableRocky), wet (moss in the podzol patches, a modern
        block LOTR would have used there) and mud (biomes with quagmires)."""
        f, dec = b["fields"], b.get("decorator", {})
        trees = dec.get("treesPerChunk", 0)
        podzol = None
        if f.get("enablePodzol", True) and trees >= 1 and top == "minecraft:grass_block":
            podzol = round(max(0.0, 0.8 - trees * 0.15), 2)  # LOTR clamps at 0
        wet = podzol is not None and float(f.get("rainfall", 0.5)) >= 0.9
        import grounds
        patches, marsh = grounds.for_biome(b, self.jar)
        old = lambda o: o and self.surface_block(o.split("#")[0], int(o.split("#")[1]))
        resolved = []
        for noise, scales, thr, ptop, pfiller in patches:
            key = f"ground/{b['class'].replace('/', '.').rsplit('.', 1)[-1].lower()}_{noise}"
            octaves = {round(math.log2(sc)): w for sc, w in scales.items()}
            lo = min(octaves)
            self.ground_noises[key] = {"firstOctave": lo,
                                       "amplitudes": [float(octaves.get(o, 0.0)) for o in range(lo, max(octaves) + 1)]}
            resolved.append((key, thr, old(ptop), old(pfiller)))
        mtn = grounds.mountain(b, self.jar)
        if mtn and mtn != "all":
            snow, stone_y, stone, sand = mtn
            mtn = (snow, stone_y, old(stone), old(sand))
        # no rock breaking through on the Shire's downs (Aaron 2026-11-14: hills stay grassy; they reach y 100+)
        rocky = bool(f.get("enableRocky", True)) and self.biome_id(b) not in DOWNS
        return (podzol, rocky, wet, dec.get("quagmirePerChunk", 0) > 0, tuple(resolved),
                marsh, mtn)

    def noise_settings(self, biomes):
        height = {"type": "minecraft:flat_cache", "argument": {"type": f"{NS}:map_terrain", "value": "height"}}
        variation = {"type": "minecraft:flat_cache", "argument": {"type": f"{NS}:map_terrain", "value": "variation"}}
        band = {"type": "minecraft:flat_cache", "argument": {"type": f"{NS}:map_terrain", "value": "band"}}
        minus_y = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": TOP_Y, "from_value": 64,
                   "to_value": -TOP_Y}
        # LOTR's shape (LOTRChunkProvider): 1.7's blended 3D noise (old_blended_noise, xz factor 400 as LOTR) scaled by
        # hilliness, a 2D hill noise, and ground 4x steeper below the map height so hills don't float or terrace
        level = {"type": "minecraft:add", "argument1": height,
                 "argument2": {"type": "minecraft:mul", "argument1": -HILL_BIAS, "argument2": variation}}
        base = {"type": "minecraft:add", "argument1": level, "argument2": minus_y}
        shaped = {"type": "minecraft:add", "argument1": {"type": "minecraft:min", "argument1": base, "argument2": 0.0},
                  "argument2": {"type": "minecraft:mul", "argument1": 4.0,
                                "argument2": {"type": "minecraft:max", "argument1": base, "argument2": 0.0}}}
        # flat_cache samples LOTR's blended noise at y 0 only: a 2D height offset, so every column's density falls
        # monotonically with y and crosses zero once (no floating islands or overhangs; Aaron 2026-11-26: "stable
        # through set rules rather than pure 3d randomization"). Its xz character, and so the roughness, is LOTR's.
        blended = {"type": "minecraft:flat_cache", "argument": {
            "type": "minecraft:old_blended_noise", "xz_scale": 1.0, "y_scale": 1.0, "xz_factor": 400.0,
            "y_factor": 160.0, "smear_scale_multiplier": 8.0}}
        hill2d = {"type": "minecraft:add", "argument1": -VALLEY_SHIFT, "argument2": {
            "type": "minecraft:quarter_negative" if VALLEYS == 0.25 else "minecraft:half_negative",
            "argument": {"type": "minecraft:noise", "noise": f"{NS}:terrain", "xz_scale": 1.0, "y_scale": 0.0}}}
        # the 3D part is capped at MAX_3D blocks of hilliness (more floats islands off mountains); the rest is 2D
        capped = {"type": "minecraft:min", "argument1": variation, "argument2": MAX_3D}
        hills = {"type": "minecraft:add",
                 "argument1": {"type": "minecraft:mul", "argument1": capped,
                               "argument2": {"type": "minecraft:mul", "argument1": 2.0, "argument2": blended}},
                 "argument2": {"type": "minecraft:mul", "argument1": variation,
                               "argument2": {"type": "minecraft:mul", "argument1": HILL_2D, "argument2": hill2d}}}
        # crags: LOTR's very hilly biomes (mountains) break into peaks and cliffs; 1.18's jagged noise (as vanilla's
        # jagged peaks) gives hilliness over JAGGED_FROM that, so plains and hills are untouched
        jagged = {"type": "minecraft:mul", "argument1": {"type": "minecraft:max", "argument1": {
            "type": "minecraft:add", "argument1": variation, "argument2": -JAGGED_FROM}, "argument2": 0.0},
            "argument2": {"type": "minecraft:mul", "argument1": JAGGED, "argument2": {
                "type": "minecraft:half_negative", "argument": {"type": "minecraft:noise", "noise": "minecraft:jagged",
                                                                "xz_scale": 1500.0, "y_scale": 0.0}}}}
        ridges = {"type": "minecraft:mul", "argument1": {"type": "minecraft:max", "argument1": {
            "type": "minecraft:add", "argument1": variation, "argument2": -JAGGED_FROM}, "argument2": 0.0},
            "argument2": {"type": "minecraft:mul", "argument1": RIDGES, "argument2": {
                "type": "minecraft:noise", "noise": f"{NS}:terrain", "xz_scale": 0.5, "y_scale": 0.0}}}
        hills = {"type": "minecraft:add", "argument1": hills, "argument2": {
            "type": "minecraft:add", "argument1": jagged, "argument2": ridges}}
        # the noise only within its band of the ground (NOISE_BAND_*): out of it the plain shape has the same sign
        near = {"type": "minecraft:range_choice", "input": {"type": "minecraft:mul", "argument1": base, "argument2": band},
                "min_inclusive": -NOISE_BAND_ABOVE, "max_exclusive": NOISE_BAND_BELOW,
                "when_in_range": {"type": "minecraft:add", "argument1": shaped, "argument2": hills},
                "when_out_of_range": shaped}
        final = {"type": "minecraft:interpolated", "argument": {
            "type": "minecraft:mul", "argument1": 1 / 24, "argument2": near}}
        prelim = {"type": "minecraft:mul", "argument1": 1 / 24, "argument2": base}
        zero = 0.0
        return {
            "sea_level": SEA_LEVEL,
            "disable_mob_generation": False,
            "aquifers_enabled": False,
            "ore_veins_enabled": False,
            "legacy_random_source": False,
            "default_block": {"Name": "minecraft:stone"},
            "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
            "noise": {"min_y": -64, "height": TOP_Y + 64, "size_horizontal": 1, "size_vertical": 2},
            "noise_router": {
                "barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
                "temperature": zero, "vegetation": zero, "continents": zero, "erosion": zero, "depth": zero,
                "ridges": zero, "initial_density_without_jaggedness": prelim, "final_density": final,
                "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero,
            },
            "spawn_target": [],
            "surface_rule": self.surface_rule(biomes),
        }

    # ------------------------------------------------------------------ dimension, world preset, lang

    def dimension_files(self):
        d = os.path.join(self.data, NS)
        generator = {"type": "minecraft:noise", "settings": f"{NS}:middle_earth",
                     "biome_source": {"type": f"{NS}:middle_earth"}}
        write_json(os.path.join(d, "dimension_type", "middle_earth.json"), {
            "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True,
            "has_ceiling": False, "ambient_light": 0.0,
            "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
            "monster_spawn_block_light_limit": 0, "piglin_safe": False, "bed_works": True,
            "respawn_anchor_works": False, "has_raids": True, "logical_height": TOP_Y + 64, "min_y": -64, "height": TOP_Y + 64,
            "infiniburn": "#minecraft:infiniburn_overworld", "effects": f"{NS}:middle_earth",
        })
        write_json(os.path.join(d, "dimension", "middle_earth.json"),
                   {"type": f"{NS}:middle_earth", "generator": generator})
        write_json(os.path.join(d, "worldgen", "world_preset", "middle_earth.json"), {"dimensions": {
            # Middle-earth's own dimension type: its build limit (TOP_Y) is over vanilla's 320
            "minecraft:overworld": {"type": f"{NS}:middle_earth", "generator": generator},
            "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
                "type": "minecraft:noise", "settings": "minecraft:nether",
                "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}}},
            "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
                "type": "minecraft:noise", "settings": "minecraft:end",
                "biome_source": {"type": "minecraft:the_end"}}},
        }})
        write_json(os.path.join(self.data, "minecraft", "tags", "worldgen", "world_preset", "normal.json"),
                   {"replace": False, "values": [f"{NS}:middle_earth"]})
        for key, noise in self.ground_noises.items():
            write_json(os.path.join(d, "worldgen", "noise", key + ".json"), noise)
        for name, octave, amps in (("podzol", -4, [1.0, 1.0]), ("rocky", -5, [1.0, 0.0, 0.0, 1.0]),
                                   ("ground_pick", 0, [1.0]), ("marsh", -3, [1.0]), ("mountain", -4, [1.0])):
            write_json(os.path.join(d, "worldgen", "noise", name + ".json"),
                       {"firstOctave": octave, "amplitudes": amps})
        write_json(os.path.join(d, "worldgen", "noise", "terrain.json"),
                   {"firstOctave": -7, "amplitudes": [1.0, 1.0, 0.5, 0.25]})

    def lang(self, biomes):
        en = self.gen.lang["en_us"]
        en[f"generator.{NS}.middle_earth"] = "Middle-earth"
        for code in self.jar.langs():
            src = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for b in biomes:
                name = b["fields"]["biomeName"]
                text = src.get(f"lotr.biome.{name}.name")
                if text:
                    dst[f"biome.{NS}.{self.biome_id(b)}"] = text
                elif code == "en_US":
                    dst[f"biome.{NS}.{self.biome_id(b)}"] = name
                    self.report["untranslated_biomes"].append(name)

    def ores(self, biomes):
        """LOTR's ores per biome as ore features; every biome lists its ores in one shared order."""
        specs = {}
        per_biome = {}
        for b in biomes:
            mine = []
            for o in b.get("ores", []):
                g = o.get("gen", {})
                block = g.get("field_150519_a")
                if not block or o.get("genClass", "").split(".")[-1] != "WorldGenMinable":
                    continue
                state = self.surface_block(block, g.get("mineableBlockMeta", 0))
                target = g.get("field_150518_c", "minecraft:stone")
                key = (state, target, g.get("numberOfBlocks", 8), float(o.get("oreChance", 1)),
                       o.get("minHeight", 0), o.get("maxHeight", 64))
                specs.setdefault(key, None)
                mine.append(key)
            per_biome[self.biome_id(b)] = mine
        order = sorted(specs, key=lambda k: (k[0], k[1], k[2], -k[3], k[4], k[5]))
        gen = os.path.join(self.data, NS, "worldgen")
        names = {}
        for i, (state, target, size, chance, lo, hi) in enumerate(order):
            name = f"ore/{state.split(':')[1]}_{i}"
            names[(state, target, size, chance, lo, hi)] = f"{NS}:{name}"
            if target == "minecraft:stone":
                rule = {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}
            else:
                rule = {"predicate_type": "minecraft:block_match", "block": self.surface_block(target, 0)}
            targets = [{"target": rule, "state": {"Name": state}}]
            if target == "minecraft:stone":  # under y 0 the stone is deepslate
                targets.append({"target": {"predicate_type": "minecraft:tag_match",
                                           "tag": "minecraft:deepslate_ore_replaceables"},
                                "state": {"Name": DEEPSLATE_ORE.get(state, state)}})
            write_json(os.path.join(gen, "configured_feature", name + ".json"), {"type": "minecraft:ore", "config": {
                "targets": targets, "size": size,
                "discard_chance_on_air_exposure": 0.0}})
            if chance >= 1:
                count = [{"type": "minecraft:count", "count": int(round(chance))}]
            else:
                count = [{"type": "minecraft:rarity_filter", "chance": max(1, int(round(1 / max(chance, 0.001))))}]
            bottom = -64 if lo <= 0 else lo
            write_json(os.path.join(gen, "placed_feature", name + ".json"), {"feature": f"{NS}:{name}", "placement":
                count + [{"type": "minecraft:in_square"},
                         {"type": "minecraft:height_range", "height": {
                             "type": "minecraft:uniform", "min_inclusive": {"absolute": bottom},
                             "max_inclusive": {"absolute": max(bottom + 1, min(hi, 319))}}},
                         {"type": "minecraft:biome"}]})
        rank = {k: i for i, k in enumerate(order)}
        self.biome_ores = {bid: [names[k] for k in sorted(set(keys), key=rank.get)] for bid, keys in per_biome.items()}
        return len(order)

    def ground_features(self, biomes):
        gen = os.path.join(self.data, NS, "worldgen")
        # LOTR's foggy biomes (Fangorn, the Old Forest...): the client draws them in thick fog
        write_json(os.path.join(self.data, NS, "tags", "worldgen", "biome", "foggy.json"), {"values": sorted(
            f"{NS}:{self.biome_id(b)}" for b in biomes if (b.get("biomeColors") or {}).get("foggy"))})
        # LOTR: 3 stalactites a chunk at y 0-60 (LOTRBiomeDecorator); here clusters of pointed dripstone to y -60
        write_json(os.path.join(gen, "placed_feature", "dripstone.json"), {
            "feature": "minecraft:pointed_dripstone", "placement": [
                {"type": "minecraft:count", "count": 32}, {"type": "minecraft:in_square"},  # most tries land in rock
                {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
                                                              "min_inclusive": {"absolute": -60},
                                                              "max_inclusive": {"absolute": 60}}},
                {"type": "minecraft:count", "count": {"type": "minecraft:uniform", "min_inclusive": 1,
                                                     "max_inclusive": 4}},
                {"type": "minecraft:random_offset",
                 "xz_spread": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 3.0,
                               "min_inclusive": -10, "max_inclusive": 10},
                 "y_spread": {"type": "minecraft:clamped_normal", "mean": 0.0, "deviation": 0.6,
                              "min_inclusive": -2, "max_inclusive": 2}},
                # LOTR hung them from a cave roof: from a cave's air, up to its ceiling
                {"type": "minecraft:environment_scan", "direction_of_search": "up", "max_steps": 12,
                 "target_condition": {"type": "minecraft:solid"},
                 "allowed_search_condition": {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"}},
                {"type": "minecraft:random_offset", "xz_spread": 0, "y_spread": -1},
                {"type": "minecraft:biome"}]})

        def disk(block, radius):
            return {"type": "minecraft:disk", "config": {
                "state_provider": {"fallback": {"type": "minecraft:simple_state_provider",
                                                "state": {"Name": block}}, "rules": []},
                "target": {"type": "minecraft:matching_blocks",
                           "blocks": ["minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
                                      "minecraft:mud"]},
                "radius": radius, "half_height": 1}}
        surface = [{"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"},
                   {"type": "minecraft:biome"}]
        write_json(os.path.join(gen, "configured_feature", "quagmire.json"),
                   disk(f"{NS}:quagmire", {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}))
        write_json(os.path.join(gen, "configured_feature", "mud_patch.json"),
                   disk("minecraft:mud", {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 5}))
        write_json(os.path.join(gen, "placed_feature", "mud_patch.json"),
                   {"feature": f"{NS}:mud_patch", "placement": [{"type": "minecraft:count", "count": 2}] + surface})
        for n in sorted({b.get("decorator", {}).get("quagmirePerChunk", 0) for b in biomes} - {0}):
            write_json(os.path.join(gen, "placed_feature", f"quagmire_{n}.json"),
                       {"feature": f"{NS}:quagmire", "placement": [{"type": "minecraft:count", "count": n}] + surface})

    def roads(self, world):
        """LOTR's roads in block coordinates (map pixel -> block, the same way as the biome map), and the feature."""
        settings = world["map"]
        scale = settings.get("scale", 128)
        ox, oz = settings.get("originX", 0), settings.get("originZ", 0)
        roads = []
        all_points = [p for road in world.get("roads", []) for p in road["points"]]
        in_blocks = any(abs(p[0]) > 5000 or abs(p[1]) > 5000 for p in all_points)  # else map pixels
        for road in world.get("roads", []):
            if in_blocks:
                points = [[round(p[0], 1), round(p[1], 1)] for p in road["points"]]
            else:
                points = [[round((p[0] - ox) * scale, 1), round((p[1] - oz) * scale, 1)] for p in road["points"]]
            if len(points) >= 2:
                roads.append({"name": road["name"], "points": points})
        path = os.path.join(self.out, NS, "map", "roads.json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:  # compact: ~150k points
            json.dump({"width": ROAD_HALF_WIDTH, "roads": roads, **self.road_blocks()}, f, separators=(",", ":"))
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", "roads.json"), {"type": f"{NS}:roads", "config": {}})
        write_json(os.path.join(gen, "placed_feature", "roads.json"), {"feature": f"{NS}:roads", "placement": []})
        write_json(os.path.join(gen, "configured_feature", "road_verge.json"), {"type": f"{NS}:road_verge",
                                                                               "config": ROAD_VERGE})
        write_json(os.path.join(gen, "placed_feature", "road_verge.json"), {"feature": f"{NS}:road_verge",
                                                                          "placement": []})
        write_json(os.path.join(gen, "configured_feature", "crater_lava.json"), {"type": f"{NS}:crater_lava",
                                                                                "config": {}})
        write_json(os.path.join(gen, "placed_feature", "crater_lava.json"), {"feature": f"{NS}:crater_lava",
                                                                           "placement": []})
        return len(roads)

    def road_blocks(self):
        """Each biome's LOTR road and bridge blocks (converter/roads.py)."""
        import roads

        extra = {"minecraft:planks#0": "minecraft:oak_planks", "minecraft:fence#0": "minecraft:oak_fence",
                 "minecraft:wooden_slab#0": "minecraft:oak_slab"}

        def resolve(old):
            if old in extra or old in VANILLA_SURFACE:
                return extra.get(old) or VANILLA_SURFACE[old]
            return self.gen.idtable.get(old)
        return roads.build(self.export["biomes"], self.jar, resolve, self.biome_id)

    def run(self):
        world = self.export
        self.trees = Trees(self.gen, self.data, self.report)
        road_count = self.roads(world)
        biomes_all = [b for b in world["biomes"] if "biomeName" in b.get("fields", {})]
        ore_count = self.ores(biomes_all)
        biomes = [b for b in world["biomes"] if "biomeName" in b.get("fields", {})]
        if len(biomes) > 255:
            raise SystemExit(f"{len(biomes)} biomes do not fit the byte map")
        seen = set()
        for b in biomes:
            bid = self.biome_id(b)
            if bid in seen:
                raise SystemExit(f"two biomes are called {bid}")
            seen.add(bid)
            write_json(os.path.join(self.data, NS, "worldgen", "biome", bid + ".json"), self.biome(b))
        if OCEAN not in seen:
            raise SystemExit("no ocean biome in the export")
        self.peak_biomes = self.find_peak_biomes(biomes, world)
        w, h = self.write_map(biomes, world)
        self.ground_features(biomes)
        self.plant_soils()
        write_json(os.path.join(self.data, NS, "worldgen", "noise_settings", "middle_earth.json"),
                   self.noise_settings(biomes))
        self.dimension_files()
        self.lang(biomes)
        return {"biomes": len(biomes), "map": f"{w}x{h}", "roads": road_count, "tree_types": len(self.trees.written),
                "ore_features": ore_count}


def variants_json():
    """map.json "variants" (map/Variants): per biome its patch grid and kinds' terrain."""
    out = {}
    for k, v in VARIANTS.items():
        entry = {"cell": v["cell"], "chance": v["chance"]}
        entry.update({x: v[x] for x in ("warp", "feather") if x in v})
        entry["list"] = [{x: e[x] for x in ("name", "weight", "downs", "variation") if x in e} for e in v["list"]]
        out[f"{NS}:{k}"] = entry
    return out


def enclosures_json():
    """map.json "enclosures" (map/Enclosures): the grid and each biome's kinds (sizes, chances, ground)."""
    keys = ("name", "chance", "length", "width", "max_relief", "roughness", "gates", "variants", "boost")
    return {"cell": ENCLOSURES["cell"], "fade": ENCLOSURES["fade"], "gap": ENCLOSURES["gap"],
            "road_gap": ENCLOSURES["road_gap"],
            "biomes": {f"{NS}:{b}": [{x: k[x] for x in keys if x in k} for k in kinds]
                       for b, kinds in ENCLOSURES["biomes"].items()}}


def noise_at(noise, lo, hi=99.0):
    """A LOTR noise threshold (lo..hi in LOTR's units) on one of our noises."""
    return {"type": "minecraft:noise_threshold", "noise": f"{NS}:{noise}", "min_threshold": lo * NOISE_SCALE,
            "max_threshold": hi * NOISE_SCALE}


def podzol_rule(threshold, wet):
    """LOTR: where the patch noise passes 0.8 - 0.15 x trees a chunk: 35% podzol, 15% barren (coarse) dirt, rest
    grass; in wet forests moss takes part of the grass."""
    picks = [{"type": "minecraft:condition", "if_true": noise_at("ground_pick", 0.13 / NOISE_SCALE),
              "then_run": block_rule("minecraft:podzol")},
             {"type": "minecraft:condition", "if_true": noise_at("ground_pick", 0.0),
              "then_run": block_rule("minecraft:coarse_dirt")}]
    if wet:
        picks.append({"type": "minecraft:condition", "if_true": noise_at("ground_pick", -99.0, -0.3 / NOISE_SCALE),
                      "then_run": block_rule("minecraft:moss_block")})
    return {"type": "minecraft:condition", "if_true": noise_at("podzol", threshold),
            "then_run": {"type": "minecraft:sequence", "sequence": picks}}


def _rocky():
    """LOTR: above y 90 rock breaks through, more the higher (threshold 1.2 - (y - 90) / 50); 1 in 5 gravel."""
    bands = []
    for y, thr in ((210, -1.2), (190, -0.8), (170, -0.4), (150, 0.0), (130, 0.4), (110, 0.8), (90, 1.2)):
        # LOTR's 1.2 - (y - 90) / 50, which keeps falling above 150: high peaks are almost all rock
        bands.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:y_above", "anchor": {
            "absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False},
            "then_run": {"type": "minecraft:condition", "if_true": noise_at("rocky", thr), "then_run": {
                "type": "minecraft:sequence", "sequence": [
                    {"type": "minecraft:condition", "if_true": noise_at("ground_pick", 0.3 / NOISE_SCALE),
                     "then_run": block_rule("minecraft:gravel")},
                    block_rule("minecraft:stone")]}}})
    return {"type": "minecraft:sequence", "sequence": bands}


def block_rule(name):
    return {"type": "minecraft:block", "result_state": {"Name": name}}


ROCKY_RULE = _rocky()
MORDOR_ROCK = "lotr_port:mordor_rock"


def _above_less_rock_depth(y, then):
    """Rules for LOTR's j >= y - rockDepth, rockDepth 2..8 from its stone noise (here bands of the mountain noise)."""
    out = []
    for depth, lo in ((8, 0.5), (6, 0.0), (4, -0.5), (2, -99.0)):
        out.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:y_above", "anchor": {
            "absolute": y - depth}, "surface_depth_multiplier": 0, "add_stone_depth": False}, "then_run": {
            "type": "minecraft:condition", "if_true": noise_at("mountain", lo / NOISE_SCALE), "then_run": then}})
    return out


def mountain_rules(mtn):
    """(top rules, filler rules) for a generateMountainTerrain entry (grounds.MOUNTAINS): snow, stone, then sand."""
    snow, stone_y, stone, sand = mtn
    stone_rules = _above_less_rock_depth(stone_y, block_rule(stone))
    sand_rules = _above_less_rock_depth(stone_y - 6, block_rule(sand)) if sand else []
    # the snow line: vanilla frozen peaks' powder snow, packed ice and ice patches, else snow (mountains.py)
    from mountains import snow_top
    top = (_above_less_rock_depth(snow, snow_top()) if snow else []) + stone_rules + sand_rules
    return top, stone_rules + sand_rules
# around quagmires, near the water line: mud (1.19)
MUD_RULE = {"type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": {
    "type": "minecraft:y_above", "anchor": {"absolute": SEA_LEVEL + 3}, "surface_depth_multiplier": 0,
    "add_stone_depth": False}}, "then_run": {"type": "minecraft:condition", "if_true": noise_at("podzol", 0.5),
                                              "then_run": block_rule("minecraft:mud")}}
# LOTR's marsh pools: the surface at sea level - 1 becomes water where marshNoise(0.1) > -0.1
MARSH_RULE = {"type": "minecraft:condition", "if_true": {"type": "minecraft:y_above", "anchor": {
    "absolute": SEA_LEVEL - 1}, "surface_depth_multiplier": 0, "add_stone_depth": False}, "then_run": {
    "type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": {
        "type": "minecraft:y_above", "anchor": {"absolute": SEA_LEVEL}, "surface_depth_multiplier": 0,
        "add_stone_depth": False}}, "then_run": {"type": "minecraft:condition", "if_true": noise_at("marsh", -0.1),
                                                  "then_run": block_rule("minecraft:water")}}}
# #97: in the Dead Marshes the ground at and just over the pool level is water except thin banks where the marsh noise
# crosses zero (winding lines: a maze of banks); higher ground stays land
DEAD_MARSHES = [f"{NS}:dead_marshes", f"{NS}:dead_marshes_bogs"]
DEAD_MARSH_BANK = 0.15  # LOTR units of the marsh noise either side of zero that stay bank
DEAD_MARSH_RULE = {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": DEAD_MARSHES},
                   "then_run": {"type": "minecraft:condition", "if_true": {
                       "type": "minecraft:y_above", "anchor": {"absolute": SEA_LEVEL - 1},
                       "surface_depth_multiplier": 0, "add_stone_depth": False}, "then_run": {
                       "type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": {
                           "type": "minecraft:y_above", "anchor": {"absolute": SEA_LEVEL + 1},
                           "surface_depth_multiplier": 0, "add_stone_depth": False}}, "then_run": {
                           "type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": noise_at(
                               "marsh", -DEAD_MARSH_BANK, DEAD_MARSH_BANK)},
                           "then_run": block_rule("minecraft:water")}}}}
SANDSTONE = {"minecraft:sand": "minecraft:sandstone", "minecraft:red_sand": "minecraft:red_sandstone"}
SANDSTONE_DEPTH = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                   "secondary_depth_range": 10, "surface_type": "floor"}


def sky_color(temp):
    """Vanilla's sky colour for a temperature."""
    t = max(-1.0, min(1.0, temp / 3.0))
    r, g, b = colorsys.hsv_to_rgb((0.62222224 - t * 0.05) % 1.0, 0.5 + t * 0.1, 1.0)
    return (int(r * 255) << 16) | (int(g * 255) << 8) | int(b * 255)
