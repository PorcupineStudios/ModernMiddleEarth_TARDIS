"""
Optional integration with Regions Unexplored (regions_unexplored) and Biomes O' Plenty (biomesoplenty) (Aaron
2026-11-26: "add regions unexplored and biomes o plenty as optional integrations ... merge as many overlapping blocks
and items with each other and with the LOTR mod so we don't have duplicates. then read through every biome and choose
which are good fits in middle earth and rename them appropriately for each region").

Neither mod is needed: everything here is data that switches itself on with ``neoforge:mod_loaded``.

1. UNIFY: every block or item of theirs that is the same thing as one of LOTR's (the eight woods they share with LOTR,
   chalk, white sand and sandstone, clover, lavender, hibiscus, willow vines...) and, when both mods are installed,
   every one they share with each other (dead wood, barley, cattail, Spanish moss...), has one canonical item: LOTR's,
   else Regions Unexplored's. Loot (block drops, chests, mobs) gives the canonical item instead (global loot modifier
   ``lotr_port:replace_drops``, compat/ReplaceDropsModifier.java), and a 1:1 shapeless recipe turns the other into it.
   Their trees still stand in the world with their own blocks; once broken they are LOTR's. Their naming differs from
   LOTR's: their ``<wood>_log`` is LOTR's ``<wood>_wood``, their ``<wood>_wood`` (bark on all sides) LOTR's
   ``<wood>_bark``, their ``<wood>_planks`` LOTR's ``<wood>_wood_planks`` (slabs, stairs likewise).
2. SUBBIOMES: the biomes of theirs that fit Middle-earth become sub-biomes of the LOTR biome they suit, under a
   Middle-earth name, painted in seeded patches over that biome by converter/regions.py (which calls ``extend``). Each
   is its base biome everywhere (spawns, music, roads, factions, tags) with its grass and foliage a little towards the
   mod biome's. When its mod is installed, a biome modifier adds that biome's own vegetation (trees, flowers, grass
   from the mod's namespace) and, if the mod biome has trees, takes out the base's LOTR trees. Where both mods offer
   one, the first listed wins and the other is used only when the first is missing. Without either mod they are just
   named patches of their base biome.

Left out on purpose: the Shire, the Lone-lands and Eriador's patches (hand-tuned in world.py / wilds.py), Lothlorien
and Fangorn (LOTR's own trees are the point), Mordor; and the mods' fantasy biomes (glowing, crystal, nether, end,
mushroom, redstone, "painted" and Americana ones like Joshua desert and saguaro).

Input: converter/compat/mods.json (their ids, names and per-biome vegetation, extracted from their 1.21.1 sources).
Runs on the generated data (``python compat.py``) after regions.py, biomeconfig.py and borders.py. Idempotent.
"""
import glob
import gzip
import json
import math
import os
import random
import sys

from woodsets import OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
RU, BOP = "regions_unexplored", "biomesoplenty"
MODS = (RU, BOP)  # canonical order where both have a thing: Regions Unexplored first
MAIN = os.path.join(HERE, "..", "lotr", "src", "main", "resources")

# ---------------------------------------------------------------- 1. unify

# their wood-set id (with {w} the wood) -> LOTR's
WOOD = {"{w}_log": "{w}_wood", "{w}_wood": "{w}_bark", "stripped_{w}_log": "stripped_{w}_wood",
        "stripped_{w}_wood": "stripped_{w}_bark", "{w}_planks": "{w}_wood_planks", "{w}_slab": "{w}_wood_slab",
        "{w}_stairs": "{w}_wood_stairs"}
WOOD_SAME = ("fence", "fence_gate", "door", "trapdoor", "button", "pressure_plate", "sign", "hanging_sign", "boat",
             "chest_boat", "leaves", "sapling")
# the same thing under another id (theirs -> LOTR's), checked by hand against the three block lists
SYNONYMS = {
    BOP: {"willow_vine": "willow_vines", "reed": "reeds"},
    RU: {"polished_chalk": "smooth_chalk", "chalk_bricks": "chalk_brick"},
}
# ids they share with LOTR that are not the same thing
NOT_SAME = set()
SKIP_PREFIX = ("potted_",)
SKIP_PART = ("wall_sign", "wall_hanging_sign")  # wall-mounted forms have no item of their own


def lotr_ids(out):
    lang = load(os.path.join(out, "assets", NS, "lang", "en_us.json"))
    return {k.split(".", 2)[2] for k in lang if k.count(".") == 2 and k.split(".")[0] in ("block", "item")
            and k.split(".")[1] == NS}


def mod_ids(mod):
    m = MODDATA[mod]
    return {i for i in set(m["blocks"]) | set(m["items"])
            if not i.startswith(SKIP_PREFIX) and not any(p in i for p in SKIP_PART)}


def to_lotr(mod, ids, lotr):
    """their id -> LOTR's, for everything of theirs LOTR has."""
    woods = {k[:-len("_wood_planks")] for k in lotr if k.endswith("_wood_planks")}
    theirs = {k[:-len("_planks")] for k in ids if k.endswith("_planks")}
    out = {}
    for w in sorted(woods & theirs):
        rules = dict(WOOD, **{f"{{w}}_{s}": f"{{w}}_{s}" for s in WOOD_SAME})
        for a, b in rules.items():
            a, b = a.format(w=w), b.format(w=w)
            if a in ids and b in lotr:
                out[a] = b
    for a, b in SYNONYMS.get(mod, {}).items():
        if a in ids and b in lotr:
            out[a] = b
    wood_forms = {r.format(w=w) for w in theirs for r in list(WOOD) + [f"{{w}}_{s}" for s in WOOD_SAME]}
    for i in sorted(ids):
        # the same id is the same thing, except a wood set's forms (their <wood>_wood is LOTR's bark)
        if i not in out and i in lotr and i not in wood_forms and i not in NOT_SAME:
            out[i] = i
    return out


def run_unify(out):
    lotr = lotr_ids(out)
    data = os.path.join(out, "data", NS)
    tables = {}  # (from mod, to namespace) -> {from id: to id}
    ids = {m: mod_ids(m) for m in MODS}
    for mod in MODS:
        tables[(mod, NS)] = to_lotr(mod, ids[mod], lotr)
    # shared by the two mods and not LOTR's: Regions Unexplored's is canonical
    tables[(BOP, RU)] = {i: i for i in sorted(ids[BOP] & ids[RU]) if i not in tables[(BOP, NS)]}
    entries = []
    recipes = 0
    for (mod, to), table in tables.items():
        need = [mod] if to == NS else [mod, to]
        cond = [{"type": "neoforge:mod_loaded", "modid": m} for m in need]
        name = f"unify/{mod}" if to == NS else f"unify/{mod}_to_{to}"
        write_json(os.path.join(data, "loot_modifiers", name + ".json"), {
            "neoforge:conditions": cond, "type": f"{NS}:replace_drops", "conditions": [],
            "replace": {f"{mod}:{a}": f"{to}:{b}" for a, b in sorted(table.items())}})
        entries.append(f"{NS}:{name}")
        rdir = os.path.join(data, "recipe", name)
        for old in glob.glob(os.path.join(rdir, "*.json")):
            os.remove(old)
        for a, b in sorted(table.items()):
            write_json(os.path.join(rdir, a + ".json"), {
                "neoforge:conditions": cond, "type": "minecraft:crafting_shapeless", "category": "misc",
                "group": f"{NS}:unify", "ingredients": [{"item": f"{mod}:{a}"}], "result": {"id": f"{to}:{b}"}})
            recipes += 1
    glm_path = os.path.join(out, "data", "neoforge", "loot_modifiers", "global_loot_modifiers.json")
    glm = load(glm_path) if os.path.exists(glm_path) else {"replace": False, "entries": []}
    glm["entries"] += [e for e in entries if e not in glm["entries"]]
    write_json(glm_path, glm)
    report = {f"{m}->{t}": t_ for (m, t), t_ in tables.items()}
    write_json(os.path.join(HERE, "compat", "unify_report.json"), report)
    return {"merged": {f"{m}->{t}": len(t_) for (m, t), t_ in tables.items()}, "recipes": recipes}


# ---------------------------------------------------------------- 2. sub-biomes

# id, name, LOTR biomes painted over, mod biomes (first wins), patches: share of the base's 8-pixel cells holding one,
# radius in map pixels (128 blocks); "near": only within that many pixels of another biome (its pixels)
SUBBIOMES = [
    # Eriador
    ("withywindle_willow_groves", "Withywindle Willow-groves", ["old_forest"],
     [(RU, "willow_forest")], 0.6, 2.5),
    ("midgewater_fens", "Midgewater Fens", ["midgewater"], [(RU, "fen"), (BOP, "bog")], 0.6, 3),
    ("chetwood_deepwood", "Deepwood of Chetwood", ["chetwood"], [(RU, "deciduous_forest"), (BOP, "woodland")], 0.5, 2.5),
    ("ettenmoor_crags", "Crags of the Ettenmoors", ["ettenmoors", "coldfells"],
     [(BOP, "crag"), (RU, "rocky_meadow")], 0.35, 3),
    ("trollshaw_pinewoods", "Trollshaw Pinewoods", ["trollshaws"], [(RU, "pine_taiga"), (BOP, "coniferous_forest")],
     0.5, 2.5),
    ("imladris_autumn_woods", "Autumn Woods of Imladris", ["rivendell", "rivendell_hills"],
     [(RU, "autumnal_maple_forest"), (BOP, "seasonal_forest")], 0.6, 2),
    ("angmar_ashen_woods", "Ashen Woods of Angmar", ["angmar"], [(RU, "ashen_woodland"), (BOP, "dead_forest")],
     0.35, 3),
    ("lindon_silver_birchwoods", "Silver Birchwoods of Lindon", ["lindon", "lindon_woodlands"],
     [(RU, "silver_birch_forest"), (BOP, "aspen_glade")], 0.4, 3),
    ("ered_luin_pine_slopes", "Pine Slopes of Ered Luin", ["blue_mountains_foothills"],
     [(RU, "pine_slopes"), (BOP, "coniferous_forest")], 0.45, 3),
    ("eryn_vorn_blackwoods", "Blackwoods of Eryn Vorn", ["eryn_vorn"],
     [(RU, "blackwood_taiga"), (BOP, "old_growth_woodland")], 0.5, 3),
    ("dunland_scrub", "Scrublands of Dunland", ["dunland"], [(BOP, "rocky_shrubland"), (RU, "shrubland")], 0.4, 3),
    ("forochel_muskeg", "Muskeg of Forochel", ["tundra"], [(BOP, "muskeg"), (RU, "cold_boreal_taiga")], 0.35, 4),
    ("northern_golden_larchwoods", "Golden Larchwoods of the North", ["taiga"],
     [(RU, "golden_boreal_taiga"), (BOP, "coniferous_forest")], 0.35, 3),
    # Rhovanion
    ("anduin_floodmeadows", "Floodmeadows of the Anduin", ["anduin_vale"],
     [(BOP, "floodplain"), (RU, "flower_fields")], 0.4, 3),
    ("gladden_iris_fens", "Iris-fens of the Gladden", ["gladden_fields"], [(RU, "fen"), (BOP, "wetland")], 0.6, 2.5),
    ("elvenking_old_woods", "Old Woods of the Elvenking", ["woodland_realm", "woodland_realm_hills"],
     [(BOP, "old_growth_woodland"), (RU, "temperate_grove")], 0.45, 3),
    ("dol_guldur_shadow_woods", "Shadow-woods of Dol Guldur", ["mirkwood_corrupted"],
     [(BOP, "ominous_woods")], 0.7, 3, {"near": "dol_guldur", "within": 40}),
    ("brown_lands_deadwood", "Deadwood of the Brown Lands", ["brown_lands"], [(BOP, "dead_forest")], 0.2, 3),
    ("wilderland_long_grass", "Long Grass of Wilderland", ["wilderland", "wilderland_north"],
     [(RU, "prairie"), (BOP, "prairie")], 0.3, 4),
    ("grey_mountain_boreal_woods", "Boreal Woods under the Grey Mountains", ["grey_mountains_foothills"],
     [(RU, "boreal_taiga"), (BOP, "coniferous_forest")], 0.45, 3),
    ("dale_orchards", "Orchards of Dale", ["dale"], [(RU, "orchard"), (BOP, "orchard")], 0.4, 2),
    ("emyn_muil_crags", "Crags of Emyn Muil", ["emyn_muil"], [(BOP, "crag")], 0.4, 2.5),
    ("dead_marshes_bogs", "Bogs of the Dead Marshes", ["dead_marshes"], [(BOP, "bog")], 0.4, 3),
    ("wetwang_reedbeds", "Reedbeds of the Wetwang", ["nindalf"], [(BOP, "wetland"), (RU, "marsh")], 0.6, 3),
    # Rohan and Gondor
    ("eastemnet_tall_grass", "Tall Grass of the Eastemnet", ["rohan"], [(RU, "grassland"), (BOP, "grassland")],
     0.25, 4),
    ("ered_nimrais_upland_meadows", "Upland Meadows of Ered Nimrais", ["white_mountains_foothills"],
     [(RU, "rocky_meadow"), (BOP, "highland")], 0.4, 3),
    ("ithilien_gardens", "Gardens of Ithilien", ["ithilien", "ithilien_hills"],
     [(BOP, "mediterranean_forest"), (RU, "magnolia_woodland")], 0.5, 3),
    ("lebennin_flower_fields", "Flower-fields of Lebennin", ["lebennin"], [(BOP, "lavender_field"), (RU, "flower_fields")],
     0.35, 3),
    ("lossarnach_flowering_vales", "Flowering Vales of Lossarnach", ["lossarnach"],
     [(RU, "poppy_fields"), (BOP, "field")], 0.45, 2.5),
    ("dor_en_ernil_groves", "Groves of Dor-en-Ernil", ["dor_en_ernil", "dor_en_ernil_hills"],
     [(RU, "temperate_grove"), (BOP, "seasonal_forest")], 0.4, 3),
    ("ethir_anduin_marshes", "Marshes of the Ethir Anduin", ["anduin_mouth"], [(BOP, "marsh"), (RU, "marsh")], 0.6, 3),
    ("pinnath_gelin_green_downs", "Green Downs of Pinnath Gelin", ["pinnath_gelin"],
     [(RU, "clover_plains"), (BOP, "pasture")], 0.4, 3),
    # Harad and Rhun
    ("haradwaith_drylands", "Drylands of the Haradwaith", ["near_harad_semi_desert"],
     [(BOP, "dryland"), (RU, "steppe")], 0.35, 4),
    ("harnedor_scrublands", "Scrublands of Harnedor", ["harnedor"], [(BOP, "scrubland"), (RU, "dry_bushland")], 0.4, 3),
    ("far_harad_baobab_savanna", "Baobab Savanna of Far Harad", ["far_harad"],
     [(RU, "baobab_savanna"), (BOP, "lush_savanna")], 0.3, 4),
    ("taurethrim_rainforest", "Rainforest of the Taurethrim", ["far_harad_jungle"],
     [(RU, "rainforest"), (BOP, "rainforest")], 0.3, 4),
    ("far_harad_volcanic_fields", "Volcanic Fields of Far Harad", ["far_harad_volcano"], [(BOP, "volcanic_plains")],
     0.5, 2.5),
    ("rhun_dry_steppe", "Dry Steppe of Rhun", ["rhun_land_steppe"], [(RU, "steppe"), (BOP, "wasteland_steppe")],
     0.35, 4),
    ("eastern_desolation_wastes", "Wastes of the Eastern Desolation", ["eastern_desolation"], [(BOP, "wasteland")],
     0.35, 4),
]
CELL = 8  # map pixels per patch cell (1024 blocks)
COLOUR_BLEND = 0.5  # grass and foliage this far from the base's towards the mod biome's
SEED = 20261126


def entries():
    for e in SUBBIOMES:
        sid, name, on, sources, share, radius = e[:6]
        yield sid, name, on, sources, share, radius, (e[6] if len(e) > 6 else {})


def blend(a, b, t):
    if a is None or b is None:
        return a if b is None else b
    ca = [(a >> s) & 255 for s in (16, 8, 0)]
    cb = [(b >> s) & 255 for s in (16, 8, 0)]
    c = [round(x + (y - x) * t) for x, y in zip(ca, cb)]
    return (c[0] << 16) | (c[1] << 8) | c[2]


def patches(raw, ids, width, on, share, radius, near, rnd):
    """Rough circles over a jittered CELL grid on the base biomes' pixels: (x, z, r) in map pixels."""
    want = {i for i, b in enumerate(ids) if b in on}
    cells = {}
    for i, k in enumerate(raw):
        if k in want:
            x, z = i % width, i // width
            cells.setdefault((x // CELL, z // CELL), []).append((x, z))
    if near:
        target = ids.index(near["near"])
        pts = [(i % width, i // width) for i, k in enumerate(raw) if k == target]
        cx, cz = sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts)
        cells = {c: [p for p in ps if math.hypot(p[0] - cx, p[1] - cz) <= near["within"]] for c, ps in cells.items()}
        cells = {c: ps for c, ps in cells.items() if ps}
    out = []
    for c in sorted(cells):
        if rnd.random() < share:
            x, z = rnd.choice(cells[c])
            out.append((x + 0.5, z + 0.5, radius * (0.75 + 0.5 * rnd.random())))
    if not out and cells:  # a small base biome still gets one
        c = sorted(cells)[len(cells) // 2]
        x, z = cells[c][len(cells[c]) // 2]
        out.append((x + 0.5, z + 0.5, radius))
    return out


def extend(regions, overlays, subbiomes, lang, out=OUT):
    """Adds the sub-biomes to regions.py's tables (it paints and builds them)."""
    map_dir = os.path.join(out, NS, "map")
    meta = load(os.path.join(map_dir, "map.json"))
    ids = [b["id"].split(":", 1)[1] for b in meta["biomes"]]
    with gzip.open(os.path.join(map_dir, "biomes.bin.gz")) as f:
        raw = f.read()
    width = meta["width"]
    biome_dir = os.path.join(out, "data", NS, "worldgen", "biome")
    have = {r["name"] for r in regions}
    for n, (sid, name, on, sources, share, radius, extra) in enumerate(entries()):
        if sid in have:
            continue
        rnd = random.Random(SEED + n)
        circles = patches(raw, ids, width, on, share, radius, extra, rnd)
        if not circles:
            raise SystemExit(f"compat: {sid} has no {on} to lie on")
        regions.append({"name": sid, "circles": circles, "rough": 0.35, "seed": SEED + n})
        overlays.append({"region": sid, "on": list(on), "biome": sid})
        base = load(os.path.join(biome_dir, on[0] + ".json"))["effects"]
        mod_biome = MODDATA[sources[0][0]]["biomes"][sources[0][1]]
        effects = {}
        for k in ("grass_color", "foliage_color"):
            if base.get(k) is not None and mod_biome.get(k) is not None:
                effects[k] = blend(base[k], mod_biome[k], COLOUR_BLEND)
        subbiomes[sid] = {"name": name, "base": on[0], "effects": effects}
        lang[f"biome.{NS}.{sid}"] = name


def run_modifiers(out):
    """Biome modifiers: each sub-biome's mod vegetation in, its LOTR trees out, when its mod is installed."""
    mdir = os.path.join(out, "data", NS, "neoforge", "biome_modifier", "compat")
    for old in glob.glob(os.path.join(mdir, "*.json")):
        os.remove(old)
    biome_dir = os.path.join(out, "data", NS, "worldgen", "biome")
    count = 0
    for sid, name, on, sources, share, radius, extra in entries():
        biome = load(os.path.join(biome_dir, sid + ".json"))
        lotr_trees = [f for f in biome["features"][9] if f.startswith(f"{NS}:trees/")]
        treed = []
        for i, (mod, src) in enumerate(sources):
            veg = MODDATA[mod]["biomes"][src]["vegetation"]
            if not veg:
                raise SystemExit(f"compat: {mod}:{src} has no vegetation of its own")
            cond = [{"type": "neoforge:mod_loaded", "modid": mod}]
            cond += [{"type": "neoforge:not", "value": {"type": "neoforge:mod_loaded", "modid": m}}
                     for m, _ in sources[:i]]  # only if no earlier source is there
            write_json(os.path.join(mdir, f"{sid}_{mod}.json"), {
                "neoforge:conditions": cond, "type": "neoforge:add_features", "biomes": f"{NS}:{sid}",
                "features": veg, "step": "vegetal_decoration"})
            count += 1
            if any("tree" in f for f in veg):
                treed.append(mod)
        if lotr_trees and treed:
            # the mod's own trees stand in for LOTR's (any source with trees installed)
            cond = [{"type": "neoforge:or", "values": [{"type": "neoforge:mod_loaded", "modid": m} for m in treed]}]
            write_json(os.path.join(mdir, f"{sid}_no_lotr_trees.json"), {
                "neoforge:conditions": cond, "type": "neoforge:remove_features", "biomes": f"{NS}:{sid}",
                "features": lotr_trees, "steps": ["vegetal_decoration"]})
            count += 1
    # the hand-kept Middle-earth biome tag (biome modifiers for ores, caves, delves... go by it)
    tag_path = os.path.join(MAIN, "data", NS, "tags", "worldgen", "biome", "middle_earth.json")
    tag = load(tag_path)
    for sid, *_ in entries():
        if f"{NS}:{sid}" not in tag["values"]:
            tag["values"].append(f"{NS}:{sid}")
    write_json(tag_path, tag)
    return {"subbiomes": len(SUBBIOMES), "biome_modifiers": count}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


MODDATA = load(os.path.join(HERE, "compat", "mods.json"))["mods"]


def run(out=OUT):
    return {"unify": run_unify(out), "biomes": run_modifiers(out)}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
