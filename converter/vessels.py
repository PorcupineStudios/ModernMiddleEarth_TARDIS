"""Set-down drinking vessels (LOTR's placeable mugs, goblets, horns...): small 3D block models, open at the top so
the drink drawn inside by the client's VesselRenderer shows. Walls take their colours from the vessel's item picture.
Run on its own to rewrite just these models: python vessels.py <assets/lotr_port dir>."""
import json
import os
import sys

NS = "lotr_port"
CUTOUT = "minecraft:cutout"

# block -> (wall texture, uv region of that texture used on the walls, shape)
VESSELS = {
    "mug_block": ("item/mug", [4, 5, 12, 11], "mug"),
    "ceramic_mug_block": ("item/ceramic_mug", [4, 5, 12, 11], "mug"),
    "goblet_gold": ("item/goblet_gold", [3, 2, 12, 7], "goblet"),
    "goblet_silver": ("item/goblet_silver", [3, 2, 12, 7], "goblet"),
    "goblet_copper": ("item/goblet_copper", [3, 2, 12, 7], "goblet"),
    "goblet_wood": ("item/goblet_wood", [3, 2, 12, 7], "goblet"),
    "skull_cup_block": ("item/skull_cup", [4, 3, 12, 8], "skull"),
    "wine_glass_block": ("minecraft:block/glass", [0, 0, 16, 16], "glass"),
    "ale_horn_block": ("item/ale_horn", [9, 4, 14, 8], "horn"),
    "ale_horn_gold": ("item/ale_horn_gold", [9, 4, 14, 8], "horn"),
}


def box(f, t, uv):
    faces = {d: {"uv": uv, "texture": "#wall"} for d in ("down", "up", "north", "south", "west", "east")}
    return {"from": f, "to": t, "faces": faces}


def cup(x0, x1, y0, y1, uv):
    """An open cup: a floor and four 1-pixel walls."""
    return [box([x0, y0, x0], [x1, y0 + 1, x1], uv),
            box([x0, y0, x0], [x1, y1, x0 + 1], uv), box([x0, y0, x1 - 1], [x1, y1, x1], uv),
            box([x0, y0, x0 + 1], [x0 + 1, y1, x1 - 1], uv), box([x1 - 1, y0, x0 + 1], [x1, y1, x1 - 1], uv)]


def elements(shape, uv):
    if shape == "mug":
        return cup(5, 11, 0, 7, uv) + [box([11, 1.5, 7.5], [13, 2.5, 8.5], uv), box([11, 4.5, 7.5], [13, 5.5, 8.5], uv),
                                       box([12, 2.5, 7.5], [13, 4.5, 8.5], uv)]
    if shape == "goblet":
        return [box([5.5, 0, 5.5], [10.5, 1, 10.5], uv), box([7.5, 1, 7.5], [8.5, 5, 8.5], uv)] + cup(5, 11, 5, 10, uv)
    if shape == "glass":
        return [box([6, 0, 6], [10, 0.5, 10], uv), box([7.5, 0.5, 7.5], [8.5, 4, 8.5], uv)] + cup(6, 10, 4, 8, uv)
    if shape == "skull":
        return cup(5, 11, 0, 6, uv)
    if shape == "horn":  # standing on its narrow end, as LOTR set it down: a tip, then the open mouth
        return [box([7, 0, 7], [9, 2, 9], uv), box([6.5, 2, 6.5], [9.5, 4, 9.5], uv)] + cup(6, 10, 4, 10, uv)
    raise ValueError(shape)


def model(block):
    tex, uv, shape = VESSELS[block]
    tex = tex if ":" in tex else f"{NS}:{tex}"
    return {"parent": "minecraft:block/block", "textures": {"wall": tex, "particle": tex}, "render_type": CUTOUT,
            "elements": elements(shape, uv)}


if __name__ == "__main__":
    for b in VESSELS:
        with open(os.path.join(sys.argv[1], "models", "block", b + ".json"), "w", encoding="utf-8") as f:
            json.dump(model(b), f, indent=2)
            f.write("\n")
