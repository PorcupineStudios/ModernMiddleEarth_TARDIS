"""
The wild lands (Aaron 2026-11-25, with four reference photos: "that gritty wilderness feel, but not overly foresty
(patches of forests, seeded and following rules that make it feel life like) ... a rocky feel, with variation in the
ground so its not mundane grass through and through ... custom blocks that match the pallet and visually blend
together well contrast-and-hue-wise. Variation without it being jarring. Add boulder formations, moss growth, dried up
creek beds, ravines"). First for the Lone-lands (and the Weather Hills, lone_lands_hills).

Tolkien's Lone-lands: wild and pathless, bushes and stunted trees in dense patches with wide barren ground between,
barer and stonier eastwards towards the Hoarwell, thicket-ier westwards; ruined Rhudaur on the hills. The references:
tawny tussock grass, grey lichened rock breaking through, mossy hollows, dark conifer clumps, rocky gorges.

What this writes (``python wilds.py``; generate.py runs it after regions.py; idempotent):

1. Six blocks (content/WildBlocks.java registers them): weathered_stone, lichen_stone (rock and its lichened face),
   scree (loose rubble, does not fall), moorland_soil (dark peaty ground), tussock and bracken (dry plants, not
   tinted). Textures are vanilla 1.21.1's own (stone, gravel, rooted dirt, short grass, fern) gradient-mapped onto
   small palettes taken from the references, so they keep vanilla's pixel structure and value spread and match each
   other: 5-7 tones per texture, neighbouring values close (no salt-and-pepper), shadows shifted cool and highlights
   warm (hue shifting), plants a little lighter than the ground they stand on, as dry grass catches the light.
2. The biomes' look: tawny grass and olive foliage colours.
3. Their plants and ground (replacing the old variant patches there): ``lotr_port:ground_cover`` (one feature, rules
   on slope, hollows, shade, creek beds and seeded noise patches: rock on steep ground, rubble below it, moss and peat
   in hollows and under trees, coarse ground and bare rock on exposed flats, more of it eastwards; tussock everywhere
   on the grass, bracken and heather in patches), ``lotr_port:boulders`` (clusters and tors, lichen on top, moss on
   the shaded north side), groves (``lotr_port:grove``: trees in dense seeded clumps that favour hollows, scrub round
   their edges, open land between).
4. Terrain (map.json ``channels``, map/Channels.java): seeded dry creek beds and sparse gorges cut into the ground.
"""
import colorsys
import json
import os
import shutil
import sys
import zipfile

from PIL import Image

from woodsets import MC_1211, OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
BIOMES = ["lone_lands", "lone_lands_hills"]

# Palettes from the references (dark -> light). Rock: cool blue-grey shadows to warm buff highlights. Rubble a step
# darker than the rock it fell from. Peat: near-black brown to dry brown. Dry grass: umber to straw. Bracken: rust.
ROCK = [(64, 64, 70), (86, 85, 87), (106, 103, 99), (126, 121, 112), (146, 139, 126), (166, 158, 142)]
SCREE = [(56, 55, 58), (76, 73, 72), (96, 91, 85), (116, 109, 99), (136, 127, 113)]
PEAT = [(50, 41, 34), (66, 54, 43), (83, 67, 52), (100, 81, 62), (119, 97, 73)]
LICHEN = [(122, 125, 100), (140, 141, 110), (158, 154, 114), (174, 164, 114)]
TUSSOCK = [(96, 74, 44), (128, 101, 58), (158, 129, 74), (186, 156, 94), (208, 181, 118)]
# Stony earth (worn ground between the grass): between peat and rock. Heath moss: olive, the conifers' own green.
EARTH = [(70, 60, 50), (88, 76, 63), (106, 92, 76), (124, 108, 89), (140, 124, 104)]
MOSS = [(56, 66, 38), (72, 82, 46), (90, 98, 56), (108, 114, 66), (126, 128, 78)]
BRACKEN = [(84, 46, 28), (112, 62, 34), (141, 82, 42), (168, 106, 56), (189, 132, 74)]
# biome colours: grass_block's grey top x this gives a tawny ground (~115,102,60), conifers dark olive
GRASS_COLOR = 0xC2AC66
FOLIAGE_COLOR = 0x6B7342

BLOCKS = {  # id -> (kind, English name)
    "weathered_stone": ("stone", "Weathered Stone"),
    "lichen_stone": ("stone", "Lichen Stone"),
    "scree": ("rubble", "Scree"),
    "moorland_soil": ("soil", "Moorland Soil"),
    "stony_earth": ("soil", "Stony Earth"),
    "heath_moss": ("soil", "Heath Moss"),
    "tussock": ("plant", "Tussock Grass"),
    "bracken": ("plant", "Dry Bracken"),
}


def luminance(px):
    r, g, b = px[:3]
    return 0.299 * r + 0.587 * g + 0.114 * b


def gradient_map(src, ramp, lo=None, hi=None):
    """Maps each pixel's luminance (stretched lo..hi) onto a palette ramp, picking the nearest step (no blending: the
    palette stays small). Alpha is kept."""
    img = src.convert("RGBA")
    lums = [luminance(img.getpixel((x, y))) for y in range(img.height) for x in range(img.width)
            if img.getpixel((x, y))[3] > 0]
    lo = min(lums) if lo is None else lo
    hi = max(lums) if hi is None else hi
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            p = img.getpixel((x, y))
            if p[3] == 0:
                continue
            t = 0 if hi <= lo else (luminance(p) - lo) / (hi - lo)
            i = max(0, min(len(ramp) - 1, int(round(t * (len(ramp) - 1)))))
            out.putpixel((x, y), ramp[i] + (p[3],))
    return out


def textures(jar):
    def van(name):
        import io
        return Image.open(io.BytesIO(jar.read(f"assets/minecraft/textures/block/{name}.png"))).convert("RGBA")

    out = {}
    stone = gradient_map(van("andesite"), ROCK)
    out["weathered_stone"] = stone
    # lichen where mossy cobblestone has moss (green-dominant pixels), keeping the rock's own value in the lichen tone
    lichen = stone.copy()
    mossy = van("mossy_cobblestone")
    for y in range(16):
        for x in range(16):
            r, g, b, a = mossy.getpixel((x, y))
            if g > r + 14 and g > b + 14:  # the mossiest pixels only: lichen in flecks and runs, not a coat
                v = luminance(stone.getpixel((x, y)))
                i = max(0, min(len(LICHEN) - 1, int((v - 60) / 110 * len(LICHEN))))
                lichen.putpixel((x, y), LICHEN[i] + (255,))
    out["lichen_stone"] = lichen
    out["scree"] = gradient_map(van("gravel"), SCREE)
    out["moorland_soil"] = gradient_map(van("rooted_dirt"), PEAT)
    out["stony_earth"] = gradient_map(van("coarse_dirt"), EARTH)
    out["heath_moss"] = gradient_map(van("moss_block"), MOSS)
    out["tussock"] = gradient_map(van("short_grass"), TUSSOCK)
    out["bracken"] = gradient_map(van("fern"), BRACKEN)
    return out


def write_assets(out, jar):
    tex = textures(jar)
    assets = os.path.join(out, "assets", NS)
    data = os.path.join(out, "data", NS)
    for name, img in tex.items():
        path = os.path.join(assets, "textures", "block", name + ".png")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        img.save(path)
    for name, (kind, _) in BLOCKS.items():
        write_json(os.path.join(assets, "blockstates", name + ".json"),
                   {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        if kind == "plant":
            write_json(os.path.join(assets, "models", "block", name + ".json"),
                       {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{name}"},
                        "render_type": "minecraft:cutout"})
            write_json(os.path.join(assets, "models", "item", name + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{name}"}})
            # like vanilla grass: shears take it, otherwise nothing
            loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:{name}"}], "conditions": [
                {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}}]}],
                    "random_sequence": f"{NS}:blocks/{name}"}
        else:
            write_json(os.path.join(assets, "models", "block", name + ".json"),
                       {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}})
            write_json(os.path.join(assets, "models", "item", name + ".json"), {"parent": f"{NS}:block/{name}"})
            drop = name
            loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:{drop}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}],
                    "random_sequence": f"{NS}:blocks/{name}"}
        write_json(os.path.join(data, "loot_table", "blocks", name + ".json"), loot)
    lang_path = os.path.join(out, "assets", NS, "lang", "en_us.json")
    with open(lang_path, encoding="utf-8") as f:
        lang = json.load(f)
    for name, (_, en) in BLOCKS.items():
        lang[f"block.{NS}.{name}"] = en
    write_json(lang_path, lang)
    return tex


def preview(path, jar):
    """A sheet: each texture tiled 4x4 (seams show), next to vanilla grass under the biome tint, for judging the set."""
    tex = textures(jar)
    import io
    grass = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/block/grass_block_top.png"))).convert("RGBA")
    tint = ((GRASS_COLOR >> 16) & 255, (GRASS_COLOR >> 8) & 255, GRASS_COLOR & 255)
    g = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            p = grass.getpixel((x, y))
            g.putpixel((x, y), tuple(int(p[i] * tint[i] / 255) for i in range(3)) + (255,))
    moss = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/block/moss_block.png"))).convert("RGBA")
    coarse = Image.open(io.BytesIO(jar.read("assets/minecraft/textures/block/coarse_dirt.png"))).convert("RGBA")
    tiles = [("grass", g)] + list(tex.items())
    sheet = Image.new("RGBA", (len(tiles) * 72, 72), (40, 40, 40, 255))
    for i, (name, t) in enumerate(tiles):
        for a in range(4):
            for b in range(4):
                base = g if name in ("tussock", "bracken") else t
                sheet.alpha_composite(base, (i * 72 + a * 16 + 4, b * 16 + 4))
                if name in ("tussock", "bracken"):
                    sheet.alpha_composite(t, (i * 72 + a * 16 + 4, b * 16 + 4))
    sheet = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    sheet.save(path)
    for name, t in tiles:
        px = [t.getpixel((x, y)) for y in range(16) for x in range(16) if t.getpixel((x, y))[3]]
        print(f"{name:16s} mean luminance {sum(luminance(p) for p in px) / len(px):6.1f}")


# Where Tolkien's Lone-lands grow barer and stonier: block x from the Bree-land side to the Hoarwell (the biome's span)
EAST_FROM, EAST_TO = 17024, 48512
_EAST = {"east_from": EAST_FROM, "east_to": EAST_TO}

PALETTE = {"rock": {"Name": f"{NS}:weathered_stone"}, "lichen": {"Name": f"{NS}:lichen_stone"},
           "rubble": {"Name": f"{NS}:scree"}, "peat": {"Name": f"{NS}:moorland_soil"},
           "moss": {"Name": f"{NS}:heath_moss"}, "coarse": {"Name": f"{NS}:stony_earth"},
           "gravel": {"Name": "minecraft:gravel"}}

# Per biome: ground rules (GroundCoverFeature), boulders, groves. The Weather Hills: rockier, fewer groves.
SETTINGS = {
    "lone_lands": {
        "rules": dict(patch=34, coarse=0.22, rubble=0.42, rock=0.58, lichen=0.25, wet=0.62, moss=0.82, steep=3,
                      east=0.35, **_EAST),
        "creeks": dict(scale=220, width=3, mask=0.05),
        "plants": [  # bracken and heather in their own patches, tussock everywhere else
            {"block": {"Name": f"{NS}:bracken"}, "chance": 0.5, "patch": 26, "above": 0.35},
            {"block": {"Name": f"{NS}:shire_heather"}, "chance": 0.35, "patch": 20, "above": 0.42},
            {"block": {"Name": f"{NS}:tussock"}, "chance": 0.2, "patch": 0, "above": 0},
        ],
        "boulders": dict(per_chunk=0.35, east=1.5, tor=0.07, size=1.0, **_EAST),
        "grove": dict(size=80, above=0.32, hollow=0.35, east=0.45, **_EAST),
        "grove_count": 14, "scrub_count": 5, "lone_rarity": 3,
    },
    "lone_lands_hills": {
        "rules": dict(patch=30, coarse=0.12, rubble=0.32, rock=0.5, lichen=0.2, wet=0.7, moss=0.88, steep=3,
                      east=0.0, east_from=0, east_to=0),
        "creeks": dict(scale=200, width=2.5, mask=0.25),
        "plants": [
            {"block": {"Name": f"{NS}:shire_heather"}, "chance": 0.4, "patch": 20, "above": 0.3},
            {"block": {"Name": f"{NS}:bracken"}, "chance": 0.4, "patch": 26, "above": 0.45},
            {"block": {"Name": f"{NS}:tussock"}, "chance": 0.24, "patch": 0, "above": 0},
        ],
        "boulders": dict(per_chunk=0.7, east=0, east_from=0, east_to=0, tor=0.15, size=1.1),
        "grove": dict(size=70, above=0.45, hollow=0.45, east=0),
        "grove_count": 10, "scrub_count": 4, "lone_rarity": 4,
    },
}
# Trees: dark conifer clumps with some oak and birch (the references), scrub round their edges and a few lone,
# stunted trees in the open
GROVE_TREES = [("spruce", 3), ("pine", 3), ("fir", 1), ("larch", 1), ("oak", 2), ("birch", 1)]
SCRUB = [("oak_shrub", 3), ("pine_shrub", 3), ("holly", 1)]
LONE = [("pine_shrub", 2), ("oak_shrub", 2), ("oak", 1)]
# Gorges (map.json "wilds", map/Wilds.java): blocks; sparse (the mask's threshold on a [-1, 1] noise)
GORGES = {"biomes": [f"{NS}:{b}" for b in BIOMES], "scale": 1100, "width": 22, "depth": 16, "floor": 0.3,
          "mask_scale": 1600, "mask": 0.25}
# What the old variant patches put here (removed: these rules replace them) and the green grass the tussock replaces
DROP_PREFIX = (f"{NS}:lone_lands_variant/", f"{NS}:trees/lone_lands", f"{NS}:variant/old_walls",
               f"{NS}:plant_grass/short_grass_", f"{NS}:plant_grass/minecraft_short_grass_",
               f"{NS}:plant_double_grass/minecraft_tall_grass_")
TAGS = {  # block tag -> our blocks
    "minecraft:mineable/pickaxe": ["weathered_stone", "lichen_stone"],
    "minecraft:mineable/shovel": ["scree", "moorland_soil", "stony_earth"],
    "minecraft:mineable/hoe": ["heath_moss"],
    "minecraft:dirt": ["moorland_soil", "stony_earth", "heath_moss"],
    "minecraft:overworld_carver_replaceables": ["weathered_stone", "lichen_stone", "scree", "moorland_soil",
                                                "stony_earth", "heath_moss"],
    "minecraft:replaceable": ["tussock", "bracken"],
    "minecraft:replaceable_by_trees": ["tussock", "bracken"],
    "minecraft:sword_efficient": ["tussock", "bracken"],
    "minecraft:enderman_holdable": ["moorland_soil", "stony_earth", "heath_moss"],
}


def _selector(trees):
    """A random_selector picking by weight (each entry's chance is taken of what the ones before it left)."""
    total = sum(w for _, w in trees)
    left, out = 1.0, []
    for t, w in trees[:-1]:
        out.append({"feature": f"{NS}:tree/{t}", "chance": round(min(1, (w / total) / left), 4)})
        left -= w / total
    return {"type": "minecraft:random_selector",
            "config": {"features": out, "default": f"{NS}:tree/{trees[-1][0]}"}}


def _trees_placed(feature, head, filters=()):
    return {"feature": feature, "placement": head + list(filters) + [
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}]}


def features(out):
    """The configured and placed features (wilds/<name>); returns the feature ids per biome, in order."""
    data = os.path.join(out, "data", NS, "worldgen")
    cf = lambda n, v: write_json(os.path.join(data, "configured_feature", "wilds", n + ".json"), v)
    pf = lambda n, v: write_json(os.path.join(data, "placed_feature", "wilds", n + ".json"), v)
    cf("grove_trees", _selector(GROVE_TREES))
    cf("scrub", _selector(SCRUB))
    cf("lone_trees", _selector(LONE))
    ids = {}
    for biome, st in SETTINGS.items():
        cf(f"ground_cover_{biome}", {"type": f"{NS}:ground_cover", "config": {
            "biomes": [f"{NS}:{b}" for b in BIOMES], "blocks": PALETTE, "rules": st["rules"],
            "creeks": st["creeks"], "plants": st["plants"]}})
        pf(f"ground_cover_{biome}", {"feature": f"{NS}:wilds/ground_cover_{biome}", "placement": [
            {"type": "minecraft:biome"}]})
        cf(f"boulders_{biome}", {"type": f"{NS}:boulders", "config": dict(
            rock=PALETTE["rock"], lichen=PALETTE["lichen"], moss=PALETTE["moss"], **st["boulders"])})
        pf(f"boulders_{biome}", {"feature": f"{NS}:wilds/boulders_{biome}", "placement": [{"type": "minecraft:biome"}]})
        g = dict(st["grove"])
        grove = dict({"type": f"{NS}:grove"}, **g)
        scrub = dict(grove, above=round(g["above"] - 0.14, 3), below=g["above"])
        sq = {"type": "minecraft:in_square"}
        pf(f"groves_{biome}", _trees_placed(f"{NS}:wilds/grove_trees",
                                            [{"type": "minecraft:count", "count": st["grove_count"]}, sq], [grove]))
        pf(f"scrub_{biome}", _trees_placed(f"{NS}:wilds/scrub",
                                           [{"type": "minecraft:count", "count": st["scrub_count"]}, sq], [scrub]))
        pf(f"lone_trees_{biome}", _trees_placed(f"{NS}:wilds/lone_trees",
                                                [{"type": "minecraft:rarity_filter", "chance": st["lone_rarity"]}, sq]))
        ids[biome] = [f"{NS}:wilds/{n}_{biome}" for n in ("ground_cover", "boulders", "groves", "scrub",
                                                           "lone_trees")]
    return ids


def biomes(out, ids):
    """Colours and step 9 (vegetal decoration) of each biome: ours first, the old variant and green-grass features out."""
    done = {}
    for biome in BIOMES:
        path = os.path.join(out, "data", NS, "worldgen", "biome", biome + ".json")
        with open(path, encoding="utf-8") as f:
            b = json.load(f)
        b["effects"]["grass_color"] = GRASS_COLOR
        b["effects"]["foliage_color"] = FOLIAGE_COLOR
        step = [f for f in b["features"][9] if not f.startswith(DROP_PREFIX) and f not in ids[biome]]
        b["features"][9] = ids[biome] + step
        write_json(path, b)
        done[biome] = len(b["features"][9])
    return done


def map_settings(out):
    """map.json: the gorges, and the Lone-lands' old variant patches out (their own placed features go too)."""
    path = os.path.join(out, NS, "map", "map.json")
    with open(path, encoding="utf-8") as f:
        m = json.load(f)
    m["wilds"] = {"gorges": GORGES}
    for b in BIOMES:
        m.get("variants", {}).pop(f"{NS}:{b}", None)
    write_json(path, m)
    for b in BIOMES:
        shutil.rmtree(os.path.join(out, "data", NS, "worldgen", "placed_feature", f"{b}_variant"), ignore_errors=True)


def tags(out):
    for tag, names in TAGS.items():
        ns, path = tag.split(":")
        file = os.path.join(out, "data", ns, "tags", "block", path + ".json")
        t = {"values": []}
        if os.path.exists(file):
            with open(file, encoding="utf-8") as f:
                t = json.load(f)
        for n in names:
            if f"{NS}:{n}" not in t["values"]:
                t["values"].append(f"{NS}:{n}")
        write_json(file, t)


def run(out=OUT):
    jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))
    write_assets(out, jar)
    ids = features(out)
    map_settings(out)
    tags(out)
    return {"blocks": len(BLOCKS), "biomes": biomes(out, ids)}


if __name__ == "__main__":
    jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2], jar)
    else:
        print(json.dumps(run()))
