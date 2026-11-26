"""Goblin Town's own blocks (2026-11-26, Aaron: scrappy wood scaffolding that ties in, contrast and hue wise).

Writes, into lotr/src/main/resources (hand-registered content, content/goblin/GoblinBlocks.java):
textures (drawn here from one palette: weathered warm browns, beige rope and rusty iron against the mountains' grey
rock, lit by orange fire), block and item models, blockstates, loot tables and recipes. Run: python goblin_blocks.py
Lang and mining tags for these blocks are in colonies.py UI_EN and tags.py HAND (generated files are patched there).
"""
import colorsys
import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "lotr", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "lotr_port")
DATA = os.path.join(ROOT, "data", "lotr_port")
NS = "lotr_port"

# the palette: hue (0-1), saturation, value; everything is drawn from these
WOOD = (0.075, 0.38)          # warm, desaturated brown
WOOD_DARK = (0.070, 0.42)
ROPE = (0.105, 0.33)          # beige hemp
IRON = (0.065, 0.22)          # dark iron, a little rust
STRAW = (0.110, 0.42)         # dirty straw, darker than hay
DUST = (0.095, 0.13)          # dusty cave floor: near-neutral tan-grey, sits on mountain stone


def hsv(base, v, s_mul=1.0, h_off=0.0):
    r, g, b = colorsys.hsv_to_rgb((base[0] + h_off) % 1, max(0, min(1, base[1] * s_mul)), max(0, min(1, v)))
    return int(r * 255), int(g * 255), int(b * 255), 255


CLEAR = (0, 0, 0, 0)


def save(img, name):
    path = os.path.join(ASSETS, "textures", "block", name + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def grain(rng, base, x, y, v0, amp):
    return v0 + amp * (rng.random() - 0.5)


def goblin_planks():
    """Mismatched long boards in four rows: each board its own tint, horizontal grain, thin seams, a joint or two
    per row, nails at the joints, and a lighter patch board nailed across."""
    rng = random.Random(41)
    img = Image.new("RGBA", (16, 16))
    joints = [[5], [11], [2, 13], [8]]
    for row in range(4):
        y0 = row * 4
        cuts = joints[row]
        edges = [0] + cuts + [16]
        for bi in range(len(edges) - 1):
            a, b = edges[bi], edges[bi + 1]
            tint_v = 0.36 + rng.uniform(-0.06, 0.07)
            tint_h = rng.uniform(-0.01, 0.012)
            tint_s = rng.uniform(0.8, 1.15)
            for x in range(a, b):
                for y in range(y0, y0 + 4):
                    if y == y0 + 3:
                        c = hsv(WOOD_DARK, 0.26)
                    elif x == a and a != 0:
                        c = hsv(WOOD_DARK, 0.28)
                    else:
                        grain_line = (y == y0 + 1 and (x + row * 5) % 7 < 4) or (y == y0 and (x * 3 + row) % 9 == 0)
                        v = tint_v - (0.045 if grain_line else 0) + rng.uniform(-0.02, 0.02)
                        c = hsv(WOOD, v, tint_s, tint_h)
                    img.putpixel((x, y), c)
            if a != 0:
                img.putpixel((a + 1 if a + 1 < 16 else a, y0 + 1), (104, 98, 92, 255))
    for x in range(9, 15):
        for y in range(5, 7):
            img.putpixel((x, y), hsv(WOOD, 0.45 + rng.uniform(-0.02, 0.02), 0.85, 0.01))
    img.putpixel((9, 6), (110, 104, 98, 255))
    img.putpixel((14, 6), (110, 104, 98, 255))
    save(img, "goblin_planks")


def lashed_post():
    """A dark trunk with two rope lashings (side), and its rings (top)."""
    rng = random.Random(7)
    side = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            band = (x // 2) % 3
            v = 0.24 + band * 0.025 + rng.uniform(-0.03, 0.03)
            side.putpixel((x, y), hsv(WOOD_DARK, v, 1.05))
    for y0 in (2, 10):
        for y in range(y0, y0 + 4):
            for x in range(16):
                twist = (x + y) % 4
                v = [0.62, 0.56, 0.47, 0.54][twist] + rng.uniform(-0.03, 0.03)
                side.putpixel((x, y), hsv(ROPE, v))
            side.putpixel((rng.randint(0, 15), y), hsv(ROPE, 0.42))
    save(side, "lashed_post")
    top = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            v = 0.30 + 0.06 * ((int(d) % 3) == 0) + rng.uniform(-0.02, 0.02)
            top.putpixel((x, y), hsv(WOOD, v, 0.9) if d < 7.6 else hsv(WOOD_DARK, 0.2))
    save(top, "lashed_post_top")


def scaffold():
    """A square frame of dark beams with an X brace; see-through between."""
    rng = random.Random(3)
    img = Image.new("RGBA", (16, 16), CLEAR)

    def beam(x, y, light=False):
        v = (0.36 if light else 0.27) + rng.uniform(-0.03, 0.03)
        img.putpixel((x, y), hsv(WOOD_DARK, v))

    for i in range(16):
        for t in (0, 1):
            beam(i, t)
            beam(i, 15 - t)
            beam(t, i)
            beam(15 - t, i)
    for i in range(2, 14):
        beam(i, i, True)
        beam(i, 15 - i, True)
        if i + 1 < 14:
            beam(i + 1, i, True)
            beam(14 - i, i, True)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 7)):
        img.putpixel((x, y), hsv(ROPE, 0.6))
    save(img, "goblin_scaffold")


def rope_bridge():
    """Weathered slats across, gaps between, two ropes running through them."""
    rng = random.Random(11)
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y0 in range(0, 16, 4):
        v0 = 0.42 + rng.uniform(-0.06, 0.06)
        for y in range(y0, y0 + 3):
            for x in range(16):
                v = v0 + rng.uniform(-0.03, 0.03) - (0.06 if y == y0 + 2 else 0)
                img.putpixel((x, y), hsv(WOOD, v, 0.85))
        if rng.random() < 0.5:
            img.putpixel((rng.randint(3, 12), y0 + 1), hsv(WOOD_DARK, 0.2))
    for x in (2, 13):
        for y in range(16):
            img.putpixel((x, y), hsv(ROPE, [0.66, 0.56][(y // 2) % 2]))
    save(img, "rope_bridge")


def rope_texture():
    """Twisted hemp, for the rope rail."""
    rng = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            twist = (x + y * 2) % 6
            v = [0.64, 0.58, 0.51, 0.45, 0.51, 0.58][twist] + rng.uniform(-0.03, 0.03)
            img.putpixel((x, y), hsv(ROPE, v))
    save(img, "goblin_rope")


def cage():
    """Rusty iron bars in a frame (see-through between bars)."""
    rng = random.Random(13)
    img = Image.new("RGBA", (16, 16), CLEAR)
    for x in range(16):
        for y in range(16):
            frame = y < 2 or y > 13 or x < 1 or x > 14
            bar = x % 4 == 1 or x % 4 == 2
            if frame or bar:
                rust = rng.random() < 0.3
                v = 0.32 + rng.uniform(-0.05, 0.05) + (0.06 if x % 4 == 1 else 0)
                img.putpixel((x, y), hsv(IRON, v, 1.5 if rust else 0.6))
    for x in (1, 14):
        for y in (1, 14):
            img.putpixel((x, y), (120, 112, 104, 255))
    save(img, "goblin_cage")


def brazier():
    """Dark riveted iron with rust streaks, for the brazier bowl and legs."""
    rng = random.Random(17)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 0.2 + rng.uniform(-0.03, 0.03) + (0.05 if y % 5 == 0 else 0)
            rust = (x * 3 + y) % 11 == 0 or rng.random() < 0.08
            img.putpixel((x, y), hsv(IRON, v + (0.05 if rust else 0), 1.6 if rust else 0.6))
    for x in range(1, 16, 5):
        img.putpixel((x, 2), (130, 120, 110, 255))
        img.putpixel((x, 12), (130, 120, 110, 255))
    save(img, "goblin_brazier")


def straw():
    """Dirty straw: muddy yellow-brown, darker and greyer than hay, with grime."""
    rng = random.Random(19)
    for name, horizontal in (("goblin_straw", False), ("goblin_straw_top", True)):
        img = Image.new("RGBA", (16, 16))
        for x in range(16):
            for y in range(16):
                v = 0.40 + rng.uniform(-0.1, 0.08)
                img.putpixel((x, y), hsv(STRAW, v, rng.uniform(0.8, 1.1), rng.uniform(-0.01, 0.01)))
        for _ in range(26):
            x0, y0 = rng.randrange(16), rng.randrange(16)
            length = rng.randint(3, 7)
            v = 0.53 + rng.uniform(-0.05, 0.07)
            for k in range(length):
                xx = (x0 + (k if horizontal else 0) + (k // 3 if not horizontal else 0)) % 16
                yy = (y0 + (0 if horizontal else k)) % 16
                img.putpixel((xx, yy), hsv(STRAW, v, 0.9))
        for _ in range(14):
            img.putpixel((rng.randrange(16), rng.randrange(16)), hsv(WOOD_DARK, 0.18, 0.6))
        save(img, name)


def porch_floor():
    """The Front Porch's floor: dusty grit and pebbles that pass for any cave's floor."""
    rng = random.Random(23)
    img = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            img.putpixel((x, y), hsv(DUST, 0.5 + rng.uniform(-0.06, 0.06)))
    for _ in range(9):
        cx, cy = rng.randrange(16), rng.randrange(16)
        v = rng.choice([0.38, 0.42, 0.62, 0.66])
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if rng.random() < 0.85:
                img.putpixel(((cx + dx) % 16, (cy + dy) % 16), hsv(DUST, v, 1.3))
        img.putpixel(((cx + 1) % 16, (cy + 2) % 16), hsv(DUST, v - 0.12, 1.2))
    save(img, "porch_floor")


# ---- models, blockstates, loot, recipes ----

def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def model(name, obj):
    write(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, parent=None, texture=None):
    if texture:
        obj = {"parent": "minecraft:item/generated", "textures": {"layer0": texture}}
    else:
        obj = {"parent": parent or f"{NS}:block/{name}"}
    write(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def blockstate(name, obj):
    write(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def tex(n):
    return f"{NS}:block/{n}"


def loot_self(name):
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"{NS}:blocks/{name}"})


def loot_slab(name):
    write(os.path.join(DATA, "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{
            "type": "minecraft:item", "name": f"{NS}:{name}",
            "functions": [{"function": "minecraft:set_count", "count": 2, "add": False,
                           "conditions": [{"condition": "minecraft:block_state_property", "block": f"{NS}:{name}",
                                           "properties": {"type": "double"}}]},
                          {"function": "minecraft:explosion_decay"}]}]}],
        "random_sequence": f"{NS}:blocks/{name}"})


def recipe(name, obj):
    write(os.path.join(DATA, "recipe", "own", "goblin", name + ".json"), obj)


def box(fr, to, faces, rotation=None, shade=True):
    e = {"from": fr, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if not shade:
        e["shade"] = False
    return e


def all_faces(t, uv=None, cull=None):
    out = {}
    for f in ("north", "east", "south", "west", "up", "down"):
        d = {"texture": t}
        if uv:
            d["uv"] = uv
        out[f] = d
    return out


def models():
    # planks, slab, stairs (vanilla parents)
    model("goblin_planks", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("goblin_planks")}})
    for kind, parent in (("goblin_plank_slab", "minecraft:block/slab"),
                         ("goblin_plank_slab_top", "minecraft:block/slab_top")):
        model(kind, {"parent": parent, "textures": {"bottom": tex("goblin_planks"), "top": tex("goblin_planks"),
                                                    "side": tex("goblin_planks")}})
    for kind, parent in (("goblin_plank_stairs", "minecraft:block/stairs"),
                         ("goblin_plank_stairs_inner", "minecraft:block/inner_stairs"),
                         ("goblin_plank_stairs_outer", "minecraft:block/outer_stairs")):
        model(kind, {"parent": parent, "textures": {"bottom": tex("goblin_planks"), "top": tex("goblin_planks"),
                                                    "side": tex("goblin_planks")}})
    blockstate("goblin_planks", {"variants": {"": {"model": f"{NS}:block/goblin_planks"}}})
    blockstate("goblin_plank_slab", {"variants": {
        "type=bottom": {"model": f"{NS}:block/goblin_plank_slab"},
        "type=top": {"model": f"{NS}:block/goblin_plank_slab_top"},
        "type=double": {"model": f"{NS}:block/goblin_planks"}}})
    stairs = {}
    rot = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing, y in rot.items():
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                m = "goblin_plank_stairs" + ("_inner" if "inner" in shape else "_outer" if "outer" in shape else "")
                yy = y
                if shape in ("inner_left", "outer_left"):
                    yy = (y + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape in ("inner_left", "outer_left"):
                        yy = y
                    elif shape in ("inner_right", "outer_right"):
                        yy = (y + 90) % 360
                v = {"model": f"{NS}:block/{m}", "uvlock": True}
                if x:
                    v["x"] = x
                if yy:
                    v["y"] = yy
                if not x and not yy:
                    v.pop("uvlock")
                stairs[f"facing={facing},half={half},shape={shape}"] = v
    blockstate("goblin_plank_stairs", {"variants": stairs})

    # lashed post: a 6x6 post with its lashings, any axis (logs-like)
    post = {"parent": "minecraft:block/block", "textures": {"side": tex("lashed_post"), "end": tex("lashed_post_top"),
                                                            "particle": tex("lashed_post")},
            "elements": [box([5, 0, 5], [11, 16, 11], {
                "north": {"uv": [5, 0, 11, 16], "texture": "#side"}, "south": {"uv": [5, 0, 11, 16], "texture": "#side"},
                "east": {"uv": [5, 0, 11, 16], "texture": "#side"}, "west": {"uv": [5, 0, 11, 16], "texture": "#side"},
                "up": {"uv": [5, 5, 11, 11], "texture": "#end", "cullface": "up"},
                "down": {"uv": [5, 5, 11, 11], "texture": "#end", "cullface": "down"}}),
                # the rope lashings stand a hair proud of the post
                box([4.5, 2, 4.5], [11.5, 5, 11.5], {
                    "north": {"uv": [0, 2, 7, 5], "texture": "#side"}, "south": {"uv": [0, 2, 7, 5], "texture": "#side"},
                    "east": {"uv": [0, 2, 7, 5], "texture": "#side"}, "west": {"uv": [0, 2, 7, 5], "texture": "#side"}}),
                box([4.5, 10, 4.5], [11.5, 13, 11.5], {
                    "north": {"uv": [0, 10, 7, 13], "texture": "#side"}, "south": {"uv": [0, 10, 7, 13], "texture": "#side"},
                    "east": {"uv": [0, 10, 7, 13], "texture": "#side"}, "west": {"uv": [0, 10, 7, 13], "texture": "#side"}})]}
    model("lashed_post", post)
    blockstate("lashed_post", {"variants": {
        "axis=y": {"model": f"{NS}:block/lashed_post"},
        "axis=z": {"model": f"{NS}:block/lashed_post", "x": 90},
        "axis=x": {"model": f"{NS}:block/lashed_post", "x": 90, "y": 90}}})

    # scaffold: a see-through braced cube
    model("goblin_scaffold", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                              "textures": {"all": tex("goblin_scaffold")}})
    blockstate("goblin_scaffold", {"variants": {"": {"model": f"{NS}:block/goblin_scaffold"}}})

    # rope bridge deck: 3 high, at the bottom or the middle of the block, slats across the walking axis
    for half, y0 in (("bottom", 0), ("top", 8)):
        model(f"rope_bridge_{half}", {
            "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
            "textures": {"deck": tex("rope_bridge"), "particle": tex("rope_bridge")},
            "elements": [box([0, y0, 0], [16, y0 + 3, 16], {
                "up": {"uv": [0, 0, 16, 16], "texture": "#deck"},
                "down": {"uv": [0, 0, 16, 16], "texture": "#deck"},
                "north": {"uv": [0, 0, 16, 3], "texture": "#deck"},
                "south": {"uv": [0, 0, 16, 3], "texture": "#deck"},
                "east": {"uv": [0, 0, 3, 16], "texture": "#deck", "rotation": 90},
                "west": {"uv": [0, 0, 3, 16], "texture": "#deck", "rotation": 90}})]})
    blockstate("rope_bridge", {"variants": {
        f"axis={a},half={h}": ({"model": f"{NS}:block/rope_bridge_{h}"} if a == "z"
                                else {"model": f"{NS}:block/rope_bridge_{h}", "y": 90})
        for a in ("x", "z") for h in ("bottom", "top")}})

    # rope rail: a sagging-free hemp line at hand height along its axis, with a knot in the middle
    model("rope_rail", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"rope": tex("goblin_rope"), "particle": tex("goblin_rope")},
        "elements": [box([7, 10, 0], [9, 12, 16], {
            "north": {"uv": [0, 0, 2, 2], "texture": "#rope", "cullface": "north"},
            "south": {"uv": [0, 0, 2, 2], "texture": "#rope", "cullface": "south"},
            "east": {"uv": [0, 0, 16, 2], "texture": "#rope"}, "west": {"uv": [0, 0, 16, 2], "texture": "#rope"},
            "up": {"uv": [0, 0, 2, 16], "texture": "#rope"}, "down": {"uv": [0, 0, 2, 16], "texture": "#rope"}}),
            box([6.5, 9.5, 7], [9.5, 12.5, 9], all_faces("#rope", [0, 0, 3, 3]))]})
    blockstate("rope_rail", {"variants": {
        "axis=z": {"model": f"{NS}:block/rope_rail"}, "axis=x": {"model": f"{NS}:block/rope_rail", "y": 90}}})

    # cage: barred sides, solid-ish top and bottom
    model("goblin_cage", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout",
                          "textures": {"all": tex("goblin_cage")}})
    blockstate("goblin_cage", {"variants": {"": {"model": f"{NS}:block/goblin_cage"}}})

    # brazier: an iron bowl on three legs, burning
    fire = "minecraft:block/fire_0"
    legs = []
    for lx, lz in ((3, 3), (11, 3), (7, 12)):
        legs.append(box([lx, 0, lz], [lx + 2, 5, lz + 2], all_faces("#iron", [0, 0, 2, 5])))
    model("goblin_brazier", {
        "parent": "minecraft:block/block", "render_type": "minecraft:cutout",
        "textures": {"iron": tex("goblin_brazier"), "fire": fire, "particle": tex("goblin_brazier")},
        "elements": legs + [
            box([2, 5, 2], [14, 9, 14], {
                "north": {"uv": [2, 7, 14, 11], "texture": "#iron"}, "south": {"uv": [2, 7, 14, 11], "texture": "#iron"},
                "east": {"uv": [2, 7, 14, 11], "texture": "#iron"}, "west": {"uv": [2, 7, 14, 11], "texture": "#iron"},
                "down": {"uv": [2, 2, 14, 14], "texture": "#iron"}, "up": {"uv": [2, 2, 14, 14], "texture": "#iron"}}),
            box([3, 9, 3], [13, 9.01, 13], {"up": {"uv": [3, 3, 13, 13], "texture": "#iron"}}),
            box([0.8, 9, 8], [15.2, 22, 8], {"north": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                                              "south": {"uv": [0, 0, 16, 16], "texture": "#fire"}},
                {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, shade=False),
            box([8, 9, 0.8], [8, 22, 15.2], {"east": {"uv": [0, 0, 16, 16], "texture": "#fire"},
                                              "west": {"uv": [0, 0, 16, 16], "texture": "#fire"}},
                {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True}, shade=False)]})
    blockstate("goblin_brazier", {"variants": {"": {"model": f"{NS}:block/goblin_brazier"}}})

    # straw: a pillar-like bale texture without the bale's bands
    model("goblin_straw", {"parent": "minecraft:block/cube_column", "textures": {
        "side": tex("goblin_straw"), "end": tex("goblin_straw_top")}})
    blockstate("goblin_straw", {"variants": {"": {"model": f"{NS}:block/goblin_straw"}}})

    # porch floor: one block for the real porch and its decoys (what is under it decides), so nobody can tell
    model("porch_floor", {"parent": "minecraft:block/cube_all", "textures": {"all": tex("porch_floor")}})
    blockstate("porch_floor", {"variants": {"": {"model": f"{NS}:block/porch_floor"}}})

    for n in ("goblin_planks", "goblin_plank_slab", "goblin_plank_stairs", "lashed_post", "goblin_scaffold",
              "goblin_cage", "goblin_brazier", "goblin_straw", "porch_floor"):
        item_model(n)
    item_model("rope_bridge", parent=f"{NS}:block/rope_bridge_bottom")
    item_model("rope_rail", parent=f"{NS}:block/rope_rail")


def data():
    for n in ("goblin_planks", "goblin_plank_stairs", "lashed_post", "goblin_scaffold", "rope_bridge", "rope_rail",
              "goblin_cage", "goblin_brazier", "goblin_straw", "porch_floor"):
        loot_self(n)
    loot_slab("goblin_plank_slab")
    i = lambda n: {"item": n}
    t = lambda n: {"tag": n}
    recipe("goblin_planks", {"type": "minecraft:crafting_shapeless", "category": "building",
                             "ingredients": [t("minecraft:planks"), t("minecraft:planks"), t("minecraft:planks"),
                                             t("c:rods/wooden")],
                             "result": {"id": f"{NS}:goblin_planks", "count": 3}})
    recipe("goblin_plank_slab", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###"],
                                 "key": {"#": i(f"{NS}:goblin_planks")},
                                 "result": {"id": f"{NS}:goblin_plank_slab", "count": 6}})
    recipe("goblin_plank_stairs", {"type": "minecraft:crafting_shaped", "category": "building",
                                   "pattern": ["#  ", "## ", "###"], "key": {"#": i(f"{NS}:goblin_planks")},
                                   "result": {"id": f"{NS}:goblin_plank_stairs", "count": 4}})
    recipe("lashed_post", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["/", "s", "/"],
                           "key": {"/": t("c:rods/wooden"), "s": t("c:strings")},
                           "result": {"id": f"{NS}:lashed_post", "count": 4}})
    recipe("goblin_scaffold", {"type": "minecraft:crafting_shaped", "category": "building",
                               "pattern": ["/ /", " s ", "/ /"], "key": {"/": t("c:rods/wooden"), "s": t("c:strings")},
                               "result": {"id": f"{NS}:goblin_scaffold", "count": 4}})
    recipe("rope_bridge", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["s s", "###"],
                           "key": {"#": i(f"{NS}:goblin_plank_slab"), "s": t("c:strings")},
                           "result": {"id": f"{NS}:rope_bridge", "count": 6}})
    recipe("rope_rail", {"type": "minecraft:crafting_shaped", "category": "building", "pattern": ["sss"],
                         "key": {"s": t("c:strings")}, "result": {"id": f"{NS}:rope_rail", "count": 6}})
    recipe("goblin_cage", {"type": "minecraft:crafting_shaped", "category": "decorations",
                           "pattern": ["c", "#", "#"], "key": {"c": i("minecraft:chain"), "#": i("minecraft:iron_bars")},
                           "result": {"id": f"{NS}:goblin_cage", "count": 1}})
    recipe("goblin_brazier", {"type": "minecraft:crafting_shaped", "category": "decorations",
                              "pattern": ["ici", "i i"], "key": {"i": t("c:ingots/iron"), "c": t("minecraft:coals")},
                              "result": {"id": f"{NS}:goblin_brazier", "count": 1}})
    recipe("goblin_straw", {"type": "minecraft:crafting_shapeless", "category": "building",
                            "ingredients": [i("minecraft:hay_block"), i("minecraft:coarse_dirt")],
                            "result": {"id": f"{NS}:goblin_straw", "count": 2}})
    recipe("porch_floor", {"type": "minecraft:crafting_shapeless", "category": "building",
                           "ingredients": [i("minecraft:gravel"), i("minecraft:sand"), i("minecraft:gravel"),
                                           i("minecraft:sand")],
                           "result": {"id": f"{NS}:porch_floor", "count": 4}})


NAMES = {
    "goblin_planks": "Goblin Planks", "goblin_plank_slab": "Goblin Plank Slab",
    "goblin_plank_stairs": "Goblin Plank Stairs", "lashed_post": "Lashed Post", "goblin_scaffold": "Goblin Scaffold",
    "rope_bridge": "Rope Bridge", "rope_rail": "Rope Rail", "goblin_cage": "Goblin Cage",
    "goblin_brazier": "Goblin Brazier", "goblin_straw": "Filthy Straw", "porch_floor": "Dusty Cave Floor",
}

if __name__ == "__main__":
    goblin_planks()
    lashed_post()
    scaffold()
    rope_bridge()
    rope_texture()
    cage()
    brazier()
    straw()
    porch_floor()
    models()
    data()
    print("goblin blocks written")
