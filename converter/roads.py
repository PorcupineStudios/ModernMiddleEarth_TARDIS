"""
LOTR's road and bridge blocks per biome (lotr.common.world.map.LOTRRoadType, LOTRBiome.getRoadBlock/getBridgeBlock),
read from the decompiled 1.7.10 code (Vineflower on converter/libs) and kept here as a table: the weights are the
chances LOTRRoadType.getBlock's random branches give. Biome classes inherit their parent's road (superclasses read
from the jar). The result goes into lotr_port/map/roads.json next to the road lines; RoadFeature lays it.
"""

# Old blocks: "lotr:tile.<field>#meta" (LOTRMod) or vanilla names; weights per road type for the road's top block
# and for the half slab LOTR lays where a road steps up.
V = {"dirt1": "minecraft:dirt#1", "gravel": "minecraft:gravel#0", "sand": "minecraft:sand#0",
     "redsand": "minecraft:sand#1", "cobble": "minecraft:cobblestone#0", "cobble_slab": "minecraft:stone_slab#3"}


def L(name, meta=0):
    return f"lotr:tile.{name}#{meta}"


def ruined(base, a, b):
    return [(base, 0.75), (a, 0.125), (b, 0.125)]


TYPES = {
    "path": {"top": [(L("dirtPath"), 0.5), (V["dirt1"], 0.3), (V["gravel"], 0.2)],
             "slab": [(L("slabSingleDirt", 1), 0.5), (L("slabSingleDirt"), 0.3), (L("slabSingleGravel"), 0.2)]},
    "dirt": {"top": [(V["dirt1"], 1)], "slab": [(L("slabSingleDirt"), 1)]},
    "galadhrim": {"top": [(L("brick", 11), 1)], "slab": [(L("slabSingle2", 3), 1)]},
    "high_elven": {"top": [(L("brick3", 2), 1)], "slab": [(L("slabSingle5", 5), 1)]},
    "high_elven_ruined": {"top": ruined(L("brick3", 2), L("brick3", 3), L("brick3", 4)),
                          "slab": ruined(L("slabSingle5", 5), L("slabSingle5", 6), L("slabSingle5", 7))},
    "wood_elven": {"top": [(L("brick3", 5), 1)], "slab": [(L("slabSingle6", 2), 1)]},
    "wood_elven_ruined": {"top": ruined(L("brick3", 5), L("brick3", 6), L("brick3", 7)),
                          "slab": ruined(L("slabSingle6", 2), L("slabSingle6", 3), L("slabSingle6", 4))},
    "arnor": {"top": ruined(L("brick2", 3), L("brick2", 4), L("brick2", 5)),
              "slab": ruined(L("slabSingle4", 1), L("slabSingle4", 2), L("slabSingle4", 3))},
    "gondor": {"top": [(L("brick", 1), 1)], "slab": [(L("slabSingle", 3), 1)]},
    # 1 in 8 smooth rock (a double slab), then 1 in 8 of the rest mossy or cracked
    "gondor_mix": {"top": [(L("slabDouble", 2), 0.125), (L("brick", 2), 0.0547), (L("brick", 3), 0.0547),
                           (L("brick", 1), 0.7656)],
                   "slab": [(L("slabSingle", 2), 0.125), (L("slabSingle", 4), 0.0547), (L("slabSingle", 5), 0.0547),
                            (L("slabSingle", 3), 0.7656)]},
    "dol_amroth": {"top": [(L("brick3", 9), 1)], "slab": [(L("slabSingle6", 7), 1)]},
    "rohan_mix": {"top": [(V["dirt1"], 1 / 6), (L("dirtPath"), 1 / 6), (L("brick", 4), 1 / 3), (L("rock", 2), 1 / 3)],
                  "slab": [(L("slabSingleDirt"), 1 / 6), (L("slabSingleDirt", 1), 1 / 6), (L("slabSingle", 6), 1 / 3),
                           (L("slabSingle11", 4), 1 / 3)]},
    "dwarven": {"top": [(L("brick", 6), 1)], "slab": [(L("slabSingle", 7), 1)]},
    "dale": {"top": [(L("brick5", 1), 1)], "slab": [(L("slabSingle9", 6), 1)]},
    "harad": {"top": [(L("brick", 15), 1)], "slab": [(L("slabSingle4", 0), 1)]},
    "harad_path": {"top": [(L("brick", 15), 0.2475), (L("brick3", 11), 0.0825), (V["sand"], 0.34),
                           (L("dirtPath"), 0.33)],
                   "slab": [(L("slabSingle4", 0), 0.2475), (L("slabSingle7", 1), 0.0825), (L("slabSingleSand"), 0.34),
                            (L("slabSingleDirt", 1), 0.33)]},
    "umbar": {"top": [(L("brick6", 6), 1)], "slab": [(L("slabSingle13", 2), 1)]},
    "gulf_harad": {"top": [(V["dirt1"], 0.25), (V["redsand"], 0.25), (L("brick3", 13), 0.25), (L("brick3", 14), 0.25)],
                   "slab": [(L("slabSingleDirt"), 0.25), (L("slabSingleSand", 1), 0.25), (L("slabSingle7", 2), 0.25),
                            (L("slabSingle7", 3), 0.25)]},
    "tauredain": {"top": ruined(L("brick4", 0), L("brick4", 1), L("brick4", 2)),
                  "slab": ruined(L("slabSingle8", 0), L("slabSingle8", 1), L("slabSingle8", 2))},
    "mordor": {"top": [(L("mordorDirt"), 1)], "slab": [(L("slabSingleDirt", 3), 1)]},
    "dorwinion": {"top": [(L("brick5", 2), 1)], "slab": [(L("slabSingle9", 7), 1)]},
    "rhun": {"top": [(L("brick5", 11), 0.875), (L("brick5", 13), 0.0625), (L("brick5", 14), 0.0625)],
             "slab": [(L("slabSingle12", 0), 0.875), (L("slabSingle12", 1), 0.0625), (L("slabSingle12", 2), 0.0625)]},
}

BRIDGES = {
    "default": {"deck": "minecraft:planks#0", "slab": "minecraft:wooden_slab#0", "edge": L("woodBeamV1"), "fence": "minecraft:fence#0"},
    "mirkwood": {"deck": L("planks", 2), "slab": L("woodSlabSingle", 2), "edge": L("woodBeam1", 2), "fence": L("fence", 2)},
    "charred": {"deck": L("planks", 3), "slab": L("woodSlabSingle", 3), "edge": L("woodBeam1", 3), "fence": L("fence", 3)},
}

# Biome class -> (road type, repair: the share of road blocks laid; LOTRRoadType.setRepair), bridge. Others inherit.
ROADS = {
    "LOTRBiome": ("path", 1.0), "LOTRBiomeGenAdornland": ("rohan_mix", 0.8), "LOTRBiomeGenBlueMountains": ("dwarven", 1.0),
    "LOTRBiomeGenDagorlad": ("mordor", 1.0), "LOTRBiomeGenDale": ("dale", 1.0),
    "LOTRBiomeGenDorEnErnil": ("dol_amroth", 1.0), "LOTRBiomeGenDorwinion": ("dorwinion", 1.0),
    "LOTRBiomeGenEasternDesolation": ("dirt", 1.0), "LOTRBiomeGenErebor": ("dwarven", 1.0),
    "LOTRBiomeGenEregion": ("high_elven_ruined", 0.7), "LOTRBiomeGenEriador": ("arnor", 0.92),
    "LOTRBiomeGenFarHaradJungle": ("tauredain", 0.8), "LOTRBiomeGenFarHaradJungleEdge": ("tauredain", 0.5),
    "LOTRBiomeGenGondor": ("gondor_mix", 1.0), "LOTRBiomeGenGulfHarad": ("gulf_harad", 1.0),
    "LOTRBiomeGenHarnedor": ("harad_path", 1.0), "LOTRBiomeGenHarondor": ("gondor_mix", 0.6),
    "LOTRBiomeGenIronHills": ("dwarven", 1.0), "LOTRBiomeGenIthilien": ("gondor_mix", 0.7),
    "LOTRBiomeGenLindon": ("high_elven", 1.0), "LOTRBiomeGenLoneLands": ("arnor", 0.4),
    "LOTRBiomeGenLostladen": ("harad", 0.3), "LOTRBiomeGenLothlorien": ("galadhrim", 1.0),
    "LOTRBiomeGenMirkwood": ("wood_elven_ruined", 0.6), "LOTRBiomeGenMordor": ("mordor", 1.0),
    "LOTRBiomeGenNearHarad": ("harad", 0.5), "LOTRBiomeGenNearHaradFertile": ("harad_path", 1.0),
    "LOTRBiomeGenPelennor": ("gondor", 1.0), "LOTRBiomeGenRedMountains": ("dwarven", 1.0),
    "LOTRBiomeGenRhunLand": ("rhun", 1.0), "LOTRBiomeGenRivendell": ("high_elven", 1.0),
    "LOTRBiomeGenRohan": ("rohan_mix", 0.9), "LOTRBiomeGenTauredainClearing": ("tauredain", 1.0),
    "LOTRBiomeGenTrollshaws": ("arnor", 0.7), "LOTRBiomeGenUmbar": ("umbar", 1.0),
    "LOTRBiomeGenWoodlandRealm": ("wood_elven", 1.0),
}
BRIDGE_OF = {"LOTRBiome": "default", "LOTRBiomeGenMirkwood": "mirkwood", "LOTRBiomeGenMordor": "charred"}


def build(biomes, jar, resolve, biome_id):
    """The road data for roads.json: types and bridges as 1.21 block ids, and each biome's road. resolve maps an
    old 'block#meta' to a 1.21 id (or None)."""
    from grounds import inherited

    def new(old):
        got = resolve(old)
        if got is None and old.startswith("lotr:tile.slabDouble"):  # a double slab: its single slab's full block
            single = resolve(old.replace("slabDouble", "slabSingle"))
            got = single[:-5] if single and single.endswith("_slab") else single
        if got is None:
            raise KeyError(f"road block {old} has no 1.21 id")
        return got

    def own(cls, table):
        return inherited(cls, table, jar, table["LOTRBiome"])

    types = {t: {part: [{"block": new(b), "weight": round(w, 4)} for b, w in spec[part]] for part in ("top", "slab")}
             for t, spec in TYPES.items()}
    bridges = {t: {k: new(v) for k, v in spec.items()} for t, spec in BRIDGES.items()}
    per_biome = {}
    for b in biomes:
        cls = b["class"]
        road, repair = own(cls, ROADS)
        per_biome[f"lotr_port:{biome_id(b)}"] = {"road": road, "repair": repair, "bridge": own(cls, BRIDGE_OF)}
    return {"types": types, "bridges": bridges, "biomes": per_biome}
