"""
LOTR's own ground per biome (the 31 overrides of LOTRBiome.generateBiomeTerrain, read from the decompiled 1.7.10 code
and kept here as a table) and its marsh pools (biomes with the swamp variants). Almost every override is one pattern:
where noise at two or three scales, summed, passes a threshold, the top (and sometimes filler) becomes another block;
the first patch that passes wins. world.py turns each into surface rules.

A patch: (noise, {scale: weight}, threshold, top, filler or None). Noises with the same name in one class are one noise
(LOTR's noiseDirt, noiseSand...). Blocks are old ids "block#meta", resolved like the biome's own top block.
Left out: Dorwinion's vineyard rows, Kanuka's paths, Rhun's sandstone under the ground, the Red Forest's podzol (a tree
variant), and the 1-in-5 random gravel/sandstone in Lostladen's patches.
"""

STONE, COARSE, GRAVEL, SAND, RED_SAND = "minecraft:stone#0", "minecraft:dirt#1", "minecraft:gravel#0", "minecraft:sand#0", "minecraft:sand#1"
SANDSTONE, GRASS, DIRT = "minecraft:sandstone#0", "minecraft:grass#0", "minecraft:dirt#0"
M_GRAVEL, M_DIRT, M_ROCK = "lotr:tile.mordorGravel#0", "lotr:tile.mordorDirt#0", "lotr:tile.rock#0"
WHITE_SAND = "lotr:tile.whiteSand#0"


def two(a, b, wb=1.0):
    return {a: 1.0, b: wb}


GROUNDS = {
    "LOTRBiomeGenAndrast": [("stone", two(0.07, 0.3), 1.1, STONE, STONE), ("dirt", two(0.07, 0.3), 0.6, COARSE, None)],
    "LOTRBiomeGenAngmar": [("terrain", two(0.07, 0.4), 0.5, STONE, STONE)],
    "LOTRBiomeGenBrownLands": [("terrain", two(0.08, 0.7), 0.1, COARSE, COARSE)],
    "LOTRBiomeGenDagorlad": [("mordor_gravel", two(0.09, 0.6), 0.5, M_GRAVEL, None),
                             ("gravel", two(0.09, 0.6), 0.6, GRAVEL, None), ("dirt", two(0.09, 0.6), 0.6, COARSE, None)],
    "LOTRBiomeGenEastBight": [("terrain", two(0.09, 0.4), 0.3, COARSE, COARSE)],
    "LOTRBiomeGenFarHaradArid": [("sand", two(0.07, 0.15), 0.6, SAND, SAND), ("dirt", two(0.07, 0.15), 0.1, COARSE, None)],
    "LOTRBiomeGenFarHaradCoast": [("sandstone", two(0.06, 0.47), 0.8, SANDSTONE, SAND),
                                  ("sand", two(0.06, 0.47), 0.6, SAND, SAND),
                                  ("dirt", two(0.06, 0.47), 0.5, COARSE, COARSE),
                                  ("grass", two(0.06, 0.47), 0.4, GRASS, DIRT)],
    "LOTRBiomeGenFarHaradVolcano": [("dirt", two(0.09, 0.4), 0.2, COARSE, COARSE)],
    "LOTRBiomeGenGulfHarad": [("red_sand", {0.002: 1, 0.07: 1, 0.25: 1}, 0.9, RED_SAND, RED_SAND),
                              ("sand", {0.002: 1, 0.07: 1, 0.25: 1}, 1.2, SAND, SAND),
                              ("dirt", {0.002: 1, 0.07: 1, 0.25: 1}, 0.4, COARSE, None)],
    "LOTRBiomeGenHarnedor": [("sand", two(0.09, 0.4), 0.6, SAND, SAND), ("dirt", two(0.09, 0.4), 0.2, COARSE, COARSE)],
    "LOTRBiomeGenHarondor": [("sand", {0.002: 1, 0.09: 0.5, 0.4: 0.5}, 0.7, SAND, SAND),
                             ("dirt", two(0.09, 0.4), 0.3, COARSE, COARSE)],
    "LOTRBiomeGenLamedon": [("terrain", two(0.07, 0.4), 0.7, COARSE, COARSE)],
    "LOTRBiomeGenLastDesert": [("terrain", two(0.07, 0.4, 0.6), 0.7, GRASS, DIRT),
                               ("terrain", two(0.07, 0.4, 0.6), 0.2, COARSE, COARSE)],
    "LOTRBiomeGenLostladen": [("stone", two(0.09, 0.4), 0.3, STONE, STONE), ("sand", two(0.09, 0.4), 0.1, SAND, SAND),
                              ("dirt", two(0.09, 0.4), -0.2, COARSE, None)],
    "LOTRBiomeGenMeneltarma": [("terrain", {0.1: 1}, -0.1, STONE, STONE)],
    "LOTRBiomeGenMorgulVale": [("rock", two(0.06, 0.3), 1.1, M_ROCK, M_ROCK),
                               ("gravel", two(0.06, 0.3), 0.7, M_GRAVEL, M_GRAVEL),
                               ("dirt", two(0.06, 0.3), 0.7, M_DIRT, M_DIRT)],
    "LOTRBiomeGenNanCurunir": [("terrain", two(0.09, 0.4), 0.3, COARSE, COARSE)],
    "LOTRBiomeGenNearHaradFertile": [("red_sand", {0.002: 1, 0.07: 1, 0.25: 1}, 1.6, RED_SAND, RED_SAND),
                                     ("sand", {0.002: 1, 0.07: 1, 0.25: 1}, 0.9, SAND, SAND),
                                     ("dirt", {0.002: 1, 0.07: 1, 0.25: 1}, 0.4, COARSE, None)],
    "LOTRBiomeGenNearHaradHills": [("stone", two(0.09, 0.4), 0.6, STONE, STONE),
                                   ("sandstone", two(0.09, 0.4), 0.2, SANDSTONE, SANDSTONE)],
    "LOTRBiomeGenNearHaradSemiDesert": [("terrain", two(0.08, 0.3), 0.3, COARSE, COARSE)],
    "LOTRBiomeGenPelargir": [("terrain", two(0.05, 0.5, 0.4), 0.7, WHITE_SAND, WHITE_SAND)],
    "LOTRBiomeGenPukel": [("stone", two(0.06, 0.4), 1.3, STONE, STONE), ("dirt", two(0.06, 0.4), 0.7, COARSE, None)],
    "LOTRBiomeGenRhunLandHills": [("sand", two(0.09, 0.4), 1.1, SAND, SAND), ("stone", two(0.09, 0.4), 0.4, STONE, STONE)],
    "LOTRBiomeGenTundra": [("stone", two(0.07, 0.3), 1.2, STONE, STONE), ("dirt", two(0.07, 0.3), 0.8, COARSE, None)],
    "LOTRBiomeGenUmbar": [("sand", {0.002: 1, 0.07: 1, 0.25: 1}, 1.1, SAND, SAND),
                          ("dirt", {0.002: 1, 0.07: 1, 0.25: 1}, 0.6, COARSE, None)],
    "LOTRBiomeGenWold": [("terrain", two(0.005, 0.4), 1.0, COARSE, COARSE)],
}
# LOTRBiomeGenMordor's soils only in Gorgoroth (isGorgoroth && hasMordorSoils): by biome name
GORGOROTH = [("gravel", two(0.08, 0.4), 0.8, M_GRAVEL, M_GRAVEL), ("dirt", two(0.08, 0.4), 0.5, M_DIRT, M_DIRT)]
# biomes with LOTR's swamp variants (SWAMP_LOWLAND / SET_SWAMP): pools at sea level where marshNoise(0.1) > -0.1
MARSH = {"LOTRBiomeGenAnduinMouth", "LOTRBiomeGenDeadMarshes", "LOTRBiomeGenEntwashMouth", "LOTRBiomeGenFarHaradSwamp",
         "LOTRBiomeGenGladdenFields", "LOTRBiomeGenLongMarshes", "LOTRBiomeGenMidgewater", "LOTRBiomeGenNindalf",
         "LOTRBiomeGenNurnMarshes", "LOTRBiomeGenShireMarshes", "LOTRBiomeGenSwanfleet"}


def inherited(cls, table, jar, default=None):
    """table's entry for a biome class or its nearest superclass in the jar."""
    from classfile import ClassFile
    name = cls
    while name:
        short = name.replace("/", ".").rsplit(".", 1)[-1]
        if short in table:
            return table[short]
        path = name.replace(".", "/") + ".class"
        if not jar.has(path):
            break
        name = ClassFile(jar.read(path)).super_name
    return default


def for_biome(b, jar):
    """(patches, marsh) for an exported biome."""
    patches = inherited(b["class"], GROUNDS, jar, [])
    if b["fields"]["biomeName"].lower().startswith("gorgoroth"):
        patches = GORGOROTH
    marsh = inherited(b["class"], {c: True for c in MARSH}, jar, False)
    return patches, marsh


# LOTR's generateMountainTerrain overrides: (snow height or None, stone height, stone, sand under the stone or None).
# Above stone height - rockDepth (2..8 by noise) the biome's top and filler become the stone; above snow height - rockDepth
# the top becomes snow. Foothills override it with nothing. "all" turns every stone of the column into the rock
# (Mordor). Left out: the Far Harad jungle mountains' mud and the Wind Mountains' clay seams.
ROCK = "lotr:tile.rock#{}"
MOUNTAINS = {
    "LOTRBiomeGenAngmarMountains": (130, 110, STONE, None),
    "LOTRBiomeGenBlueMountains": (110, 90, ROCK.format(3), None),
    "LOTRBiomeGenErebor": (200, 100, STONE, None),
    "LOTRBiomeGenFarHaradJungleMountains": (None, 120, STONE, None),
    "LOTRBiomeGenForodwaithMountains": (100, 70, STONE, None),
    "LOTRBiomeGenGreyMountains": (150, 110, STONE, None),
    "LOTRBiomeGenHaradMountains": (None, 100, STONE, None),
    "LOTRBiomeGenMirkwoodMountains": (150, 120, STONE, None),
    "LOTRBiomeGenMistyMountains": (120, 90, STONE, None),
    "LOTRBiomeGenRedMountains": (None, 110, ROCK.format(4), RED_SAND),
    "LOTRBiomeGenTolfalas": (None, 90, STONE, None),
    "LOTRBiomeGenWhiteMountains": (None, 100, ROCK.format(1), None),
    "LOTRBiomeGenWindMountains": (150, 110, STONE, None),
    "LOTRBiomeGenMordor": "all",
    **{f"LOTRBiomeGen{m}Foothills": None for m in ("BlueMountains", "GreyMountains", "MistyMountains", "RedMountains",
                                                    "WhiteMountains", "WindMountains")},
}


def mountain(b, jar):
    """The biome's generateMountainTerrain entry (see MOUNTAINS), or None."""
    return inherited(b["class"], MOUNTAINS, jar, None)
