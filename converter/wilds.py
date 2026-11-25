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
PEAT = [(38, 31, 27), (52, 42, 34), (67, 54, 42), (83, 67, 51), (101, 82, 61)]
LICHEN = [(118, 124, 92), (142, 145, 106), (168, 160, 112), (186, 166, 98)]
TUSSOCK = [(96, 74, 44), (128, 101, 58), (158, 129, 74), (186, 156, 94), (208, 181, 118)]
BRACKEN = [(84, 46, 28), (112, 62, 34), (141, 82, 42), (168, 106, 56), (189, 132, 74)]
# biome colours: grass_block's grey top x this gives a tawny ground (~115,102,60), conifers dark olive
GRASS_COLOR = 0xC2AC66
FOLIAGE_COLOR = 0x6B7342

BLOCKS = {  # id -> (kind, English name)
    "weathered_stone": ("stone", "Weathered Stone"),
    "lichen_stone": ("stone", "Lichen Stone"),
    "scree": ("rubble", "Scree"),
    "moorland_soil": ("soil", "Moorland Soil"),
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
            if g > r + 8 and g > b + 8:
                v = luminance(stone.getpixel((x, y)))
                i = max(0, min(len(LICHEN) - 1, int((v - 60) / 110 * len(LICHEN))))
                lichen.putpixel((x, y), LICHEN[i] + (255,))
    out["lichen_stone"] = lichen
    out["scree"] = gradient_map(van("gravel"), SCREE)
    out["moorland_soil"] = gradient_map(van("rooted_dirt"), PEAT)
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
    tiles = [("grass", g), ("moss", moss), ("coarse", coarse)] + list(tex.items())
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


if __name__ == "__main__":
    jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2], jar)
    else:
        print(json.dumps(run()))
