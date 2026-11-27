"""Set-down drinking vessels (LOTR's placeable mugs, goblets, horns...): small 3D block models, open at the top so
the drink drawn inside by the client's VesselRenderer shows. Walls take their colours from the vessel's item picture.
Run on its own to rewrite just these models: python vessels.py <assets/lotr_port dir>."""
import json
import os
import sys

NS = "lotr_port"
CUTOUT = "minecraft:cutout"
TRANSLUCENT = "minecraft:translucent"

# block -> (wall texture, uv region of that texture used on the walls, shape)
VESSELS = {
    "mug_block": ("item/mug", [4, 5, 12, 11], "mug"),
    "ceramic_mug_block": ("item/ceramic_mug", [4, 5, 12, 11], "mug"),
    "goblet_gold": ("item/goblet_gold", [3, 2, 12, 7], "goblet"),
    "goblet_silver": ("item/goblet_silver", [3, 2, 12, 7], "goblet"),
    "goblet_copper": ("item/goblet_copper", [3, 2, 12, 7], "goblet"),
    "goblet_wood": ("item/goblet_wood", [3, 2, 12, 7], "goblet"),
    "skull_cup_block": ("item/skull_cup", [4, 3, 12, 8], "skull"),
    "wine_glass_block": ("block/vessel_glass", [0, 0, 16, 16], "glass"),
    "ale_horn_block": ("item/ale_horn", [9, 4, 14, 8], "horn"),
    "ale_horn_gold": ("item/ale_horn_gold", [9, 4, 14, 8], "horn"),
    "glass_bottle": ("block/vessel_glass", [0, 0, 16, 16], "bottle"),
}
CORK = "minecraft:block/stripped_spruce_log"  # the bottle's stopper


def box(f, t, uv, tex="#wall"):
    faces = {d: {"uv": uv, "texture": tex} for d in ("down", "up", "north", "south", "west", "east")}
    return {"from": f, "to": t, "faces": faces}


def cup(x0, x1, y0, y1, uv, floor=True, w=1):
    """An open cup: four walls w thick and a floor between them. No two boxes overlap: overlapping boxes put two
    faces in one plane, which flicker (z-fighting)."""
    out = [box([x0, y0, x0], [x1, y1, x0 + w], uv), box([x0, y0, x1 - w], [x1, y1, x1], uv),
           box([x0, y0, x0 + w], [x0 + w, y1, x1 - w], uv), box([x1 - w, y0, x0 + w], [x1, y1, x1 - w], uv)]
    if floor:
        out.insert(0, box([x0 + w, y0, x0 + w], [x1 - w, y0 + 1, x1 - w], uv))
    return out


def pane(f, t, sides, uv=(0, 0, 16, 16)):
    """Glass: one box, only the faces named. Thick walls made of several boxes showed their touching faces through
    the clear glass, which read as flickering overlapping lines (2026-11-27). The glass is our own seamless
    vessel_glass (converter/VesselGlass.java), drawn translucent: vanilla's glass put a framed square on each face."""
    return {"from": f, "to": t, "faces": {d: {"uv": list(uv), "texture": "#wall"} for d in sides}}


AROUND = ("north", "south", "west", "east")


def elements(shape, uv):
    if shape == "mug":
        return cup(5, 11, 0, 7, uv) + [box([11, 1.5, 7.5], [13, 2.5, 8.5], uv), box([11, 4.5, 7.5], [13, 5.5, 8.5], uv),
                                       box([12, 2.5, 7.5], [13, 4.5, 8.5], uv)]
    if shape == "goblet":
        return [box([5.5, 0, 5.5], [10.5, 1, 10.5], uv), box([7.5, 1, 7.5], [8.5, 5, 8.5], uv)] + cup(5, 11, 5, 10, uv)
    if shape == "glass":  # foot, stem, open bowl
        return [pane([6, 0, 6], [10, 0.5, 10], AROUND + ("down", "up"), (0, 0, 16, 16)),
                pane([7.5, 0.5, 7.5], [8.5, 4, 8.5], AROUND), pane([6, 4, 6], [10, 8, 10], AROUND + ("down",))]
    if shape == "skull":
        return cup(5, 11, 0, 6, uv)
    if shape == "horn":  # standing on its narrow end, as LOTR set it down: a tip, then the open mouth
        return [box([7, 0, 7], [9, 2, 9], uv), box([6.5, 2, 6.5], [9.5, 4, 9.5], uv)] + cup(6, 10, 4, 10, uv)
    if shape == "bottle":  # body, shoulder, neck as single glass panes (the drink shows through), cork
        return [pane([5, 0, 5], [11, 6, 11], AROUND + ("down",)), pane([5, 6, 5], [11, 7, 11], AROUND, (0, 0, 16, 1)),
                pane([5, 6, 5], [11, 7, 11], ("up",)), pane([7, 7, 7], [9, 9, 9], AROUND),
                box([7.25, 9, 7.25], [8.75, 10, 8.75], [0, 0, 4, 4], "#cork")]
    raise ValueError(shape)


def model(block):
    tex, uv, shape = VESSELS[block]
    tex = tex if ":" in tex else f"{NS}:{tex}"
    textures = {"wall": tex, "particle": tex}
    if shape == "bottle":
        textures["cork"] = CORK
    glass = shape in ("glass", "bottle")
    return {"parent": "minecraft:block/block", "textures": textures, "render_type": TRANSLUCENT if glass else CUTOUT,
            "elements": elements(shape, uv)}


if __name__ == "__main__":
    for b in VESSELS:
        with open(os.path.join(sys.argv[1], "models", "block", b + ".json"), "w", encoding="utf-8") as f:
            json.dump(model(b), f, indent=2)
            f.write("\n")
