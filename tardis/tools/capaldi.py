"""
The Capaldi console room (Aaron, 2026-11-04): the hand-built room in tools/capaldi/ turned into game files.
Run from the repo root: python3 tardis/tools/capaldi.py

The parts that look like full blocks are real blocks, placed from a structure; only the shapes that aren't cubes stay
models (the console with its pedestal down to the lower deck, the time rotor, the arches), drawn by the console's
renderer. Writes:
- data/tardis_rb/structure/interior/console_room_capaldi.nbt: the room (31 x 12 x 26, min corner at room_offset
  -15,-4,-15 from the plot origin, which is the floor in front of the console). Adds what our mod needs to the
  hand-built layout: the console at 0,0,0, invisible console_hull blocks round it (its collision), the interior door
  at 0,0,8 facing north (the 2-wide opening's other column is frame).
- the room's blocks: blockstates, block and item models, textures (glowing ones get a full-bright overlay layer).
- assets/tardis_rb/console/capaldi.json: the models as quads in blocks from the console block's corner: body, the
  monitor (ctl_screen_1, with its click box), rotor_glass, rotor_column, rotor_spinners, rotor_spinner_mid, arches;
  textures/entity/capaldi/console.png + console_glow.png, and console_plain.png for the body.
  Also each panel's frame (`panels`, where the small controls stand) and how the monitor stands (`monitor`).
- Aaron 2026-11-08: the console is a blank slate. The hand-built model's own controls are left out (all but the
  monitor) and the body gets one plain colour; the controls on it are the small models of tools/flight_controls.py,
  placed by tools/console_layout.py (which also writes the control data files).
- Aaron 2026-11-10, the basement under the console: the platform's floor grille is a see-through grating (a thin
  plate, open squares, drawn cutout) over a 5 x 5 field round the console (GRATING), leaving 2 blocks of solid floor
  to the platform's edges; the old dotted grilles elsewhere become floor plate. Under the console, filling the
  lower deck's 2-block headroom, stands the repair core (`repair_core` in the middle of the bottom layer, drawn by
  client/RepairCoreRenderer, and invisible `core_casing` round it: 3 x 3 x 2, CORE_CELLS) in place of the pedestal
  post.
"""
import json
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "capaldi")
RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tardis_rb")
DATA = os.path.join(RES, "data", "tardis_rb")
sys.path.insert(0, os.path.join(HERE, "..", "..", "converter"))
import nbt  # noqa: E402

DATA_VERSION = 3955
ROOM_MIN = (-15, -4, -15)
ROOM_SIZE = (31, 12, 26)
FULL = ["floor_plate", "wall_panel", "frame", "light_strip", "roundel_wall", "roundel_wall_lit"]
GLOWING = {"light_strip", "roundel_wall_lit"}
# the see-through floor grating over the basement: x/z cells (y -1, the platform) round the console at 0,0
GRATING = [(x, z) for x in range(-2, 3) for z in range(-2, 3)]
# the repair core under the console, on the lower deck (floor top y -3, ceiling the platform at y -1)
CORE = (0, -3, 0)
CORE_CELLS = [(x, y, z) for x in range(-1, 2) for y in (-3, -2) for z in range(-1, 2)]
STAIRS = {"frame_stairs": "frame", "floor_plate_stairs": "floor_plate"}
SLABS = {"frame_slab": "frame"}
INVISIBLE = ["collision_post", "collision_pillar", "console_hull", "core_casing", "repair_core"]
# console_hull[part=i] stands at these cells round the console (0,0,0); its shape is in block/ConsoleHullBlock
HULL_CELLS = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]
DOOR = (0, 0, 8)
DOOR_FILL = [(1, 0, 8), (1, 1, 8), (1, 2, 8), (0, 2, 8)]

# The hand-built model's controls we keep: only the monitor. The rest are left out (Aaron 2026-11-08: a blank slate).
KEEP_CONTROLS = {"screen_1"}
# The body's one plain colour (Aaron paints it later).
PLAIN = (154, 149, 140, 255)
# The six panels: panel k faces atan2(z, x) = 90 - 60k degrees from the rotor axis (panel 0 faces the door, +z).
# Their top is one plane sloping up toward the rotor: it passes through the outer edge (OUTER blocks out from the
# axis, at height EDGE_Y) and rises SLOPE degrees (measured from the model's planks).
OUTER = 1.31
EDGE_Y = 0.812
SLOPE = 21.6
# How the monitor stands once raised off its panel: tilted toward upright, lifted and moved in toward the rotor.
MONITOR = {"tilt": 68.4, "lift": 0.65, "inward": 0.22}


def write_json(path, data, indent=2):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=indent)
        f.write("\n")


# ------------------------------------------------------------------ blocks

def tex(name):
    return "tardis_rb:block/capaldi/" + name


def block_assets():
    tdir = os.path.join(ASSETS, "textures", "block", "capaldi")
    os.makedirs(tdir, exist_ok=True)
    for name in FULL:
        Image.open(os.path.join(SRC, "textures", "blocks", name + ".png")).convert("RGBA").save(
            os.path.join(tdir, name + ".png"))
        if name in GLOWING:
            # the mask is black where nothing glows: make that clear
            glow = Image.open(os.path.join(SRC, "textures", "blocks", name + "_emissive.png")).convert("RGBA")
            glow.putdata([(r, g, b, 0 if max(r, g, b) < 24 else 255) for r, g, b, _ in glow.getdata()])
            glow.save(os.path.join(tdir, name + "_glow.png"))
    models = os.path.join(ASSETS, "models", "block", "capaldi")
    for name in FULL:
        if name in GLOWING:
            faces = {d: {"texture": "#all", "cullface": d} for d in ("north", "south", "east", "west", "up", "down")}
            glow = {d: {"texture": "#glow", "cullface": d} for d in faces}
            model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                     "textures": {"particle": tex(name), "all": tex(name), "glow": tex(name + "_glow")},
                     "elements": [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces},
                                  {"from": [0, 0, 0], "to": [16, 16, 16], "faces": glow,
                                   "neoforge_data": {"block_light": 15, "sky_light": 15}}]}
        else:
            model = {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}}
        write_json(os.path.join(models, name + ".json"), model)
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": "tardis_rb:block/capaldi/" + name}}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"),
                   {"parent": "tardis_rb:block/capaldi/" + name})
    for name, base in STAIRS.items():
        t = {"bottom": tex(base), "top": tex(base), "side": tex(base)}
        for kind, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
            write_json(os.path.join(models, name + kind + ".json"),
                       {"parent": "minecraft:block/" + parent, "textures": t})
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), stairs_states("tardis_rb:block/capaldi/" + name))
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": "tardis_rb:block/capaldi/" + name})
    for name, base in SLABS.items():
        t = {"bottom": tex(base), "top": tex(base), "side": tex(base)}
        write_json(os.path.join(models, name + ".json"), {"parent": "minecraft:block/slab", "textures": t})
        write_json(os.path.join(models, name + "_top.json"), {"parent": "minecraft:block/slab_top", "textures": t})
        m = "tardis_rb:block/capaldi/" + name
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"), {"variants": {
            "type=bottom": {"model": m}, "type=top": {"model": m + "_top"},
            "type=double": {"model": "tardis_rb:block/capaldi/" + base}}})
        write_json(os.path.join(ASSETS, "models", "item", name + ".json"), {"parent": m})
    grating(tdir, models)
    write_json(os.path.join(models, "invisible.json"), {"textures": {"particle": tex("frame")}, "elements": []})
    for name in INVISIBLE:
        write_json(os.path.join(ASSETS, "blockstates", name + ".json"),
                   {"variants": {"": {"model": "tardis_rb:block/capaldi/invisible"}}})


def grating(tdir, models):
    """
    The floor grating (Aaron 2026-11-10: more see-through): a 3 px plate at the top of the block, 1 px bars on a 5 px
    grid with 4 x 4 open squares (a bit over half of it open), drawn cutout so the basement shows through.
    """
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            edge = x in (0, 15) or y in (0, 15)
            if edge or x % 5 == 0 or y % 5 == 0:
                shade = 0 if edge else (12 if (x % 5 == 0 and y % 5 == 0) else 26)
                img.putpixel((x, y), (96 + shade, 102 + shade, 112 + shade, 255))
    img.save(os.path.join(tdir, "grille_plate.png"))
    side = {"texture": "#all", "uv": [0, 0, 16, 3]}
    faces = {d: dict(side, cullface=d) for d in ("north", "south", "east", "west")}
    faces["up"] = {"texture": "#all", "cullface": "up"}
    faces["down"] = {"texture": "#all"}
    model = {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
             "textures": {"particle": tex("grille_plate"), "all": tex("grille_plate")},
             "elements": [{"from": [0, 13, 0], "to": [16, 16, 16], "faces": faces}]}
    write_json(os.path.join(models, "grille_plate.json"), model)
    write_json(os.path.join(ASSETS, "blockstates", "grille_plate.json"),
               {"variants": {"": {"model": "tardis_rb:block/capaldi/grille_plate"}}})
    write_json(os.path.join(ASSETS, "models", "item", "grille_plate.json"),
               {"parent": "tardis_rb:block/capaldi/grille_plate"})


def stairs_states(m):
    """The vanilla stairs blockstate table."""
    y = {"east": 0, "south": 90, "west": 180, "north": 270}
    out = {}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                model = m + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                rot = y[facing]
                if half == "bottom" and shape in ("inner_left", "outer_left"):
                    rot -= 90
                if half == "top" and shape in ("inner_right", "outer_right"):
                    rot += 90
                v = {"model": model}
                if half == "top":
                    v["x"] = 180
                rot %= 360
                if rot:
                    v["y"] = rot
                if rot or half == "top":
                    v["uvlock"] = True
                out[f"facing={facing},half={half},shape={shape}"] = v
    return {"variants": out}


# ------------------------------------------------------------------ the room structure

def room_structure():
    design = json.load(open(os.path.join(SRC, "room_design.json")))
    cells = {}
    for x, y, z, shape, kind in design["blocks"]:
        name = "minecraft:bookshelf" if kind == "bookshelf" else "tardis_rb:" + kind
        if shape == "full":
            state = name
        elif shape == "slab_bottom":
            state = name + "_slab[type=bottom,waterlogged=false]"
        elif shape.startswith("stairs_"):
            parts = shape.split("_")
            half = "top" if parts[-1] == "top" else "bottom"
            state = f"{name}_stairs[facing={parts[1]},half={half},shape=straight,waterlogged=false]"
        else:
            raise SystemExit("unknown shape " + shape)
        cells[(x, y, z)] = state
    # the basement (Aaron 2026-11-10): grating only round the console, solid floor plate elsewhere; the core
    # replaces the pedestal post
    for p, state in list(cells.items()):
        if state == "tardis_rb:grille_plate":
            cells[p] = "tardis_rb:floor_plate"
    for x, z in GRATING:
        if cells.get((x, -1, z)) != "tardis_rb:floor_plate":
            raise SystemExit(f"the grating at {x},-1,{z} isn't over the platform floor: {cells.get((x, -1, z))}")
        cells[(x, -1, z)] = "tardis_rb:grille_plate"
    for p in CORE_CELLS:
        if cells.get(p) == "tardis_rb:collision_pillar":
            del cells[p]

    def put(p, state):
        if p in cells and not cells[p].startswith("minecraft:air"):
            raise SystemExit(f"{p} is already {cells[p]}, can't put {state}")
        cells[p] = state
    put((0, 0, 0), "tardis_rb:console")
    for i, (dx, dz) in enumerate(HULL_CELLS):
        put((dx, 0, dz), f"tardis_rb:console_hull[part={i}]")
    for p in CORE_CELLS:
        put(p, "tardis_rb:repair_core" if p == CORE else "tardis_rb:core_casing")
    put(DOOR, "tardis_rb:interior_door[half=lower,open=false]")
    put((DOOR[0], DOOR[1] + 1, DOOR[2]), "tardis_rb:interior_door[half=upper,open=false]")
    for p in DOOR_FILL:
        put(p, "tardis_rb:frame")
    palette, index, blocks = [], {}, []
    mx, my, mz = ROOM_MIN
    sx, sy, sz = ROOM_SIZE
    for x in range(mx, mx + sx):
        for y in range(my, my + sy):
            for z in range(mz, mz + sz):
                state = cells.get((x, y, z), "minecraft:air")
                if state not in index:
                    index[state] = len(palette)
                    name, _, props = state.partition("[")
                    entry = {"Name": name}
                    if props:
                        entry["Properties"] = dict(p.split("=") for p in props.rstrip("]").split(","))
                    palette.append(entry)
                blocks.append({"pos": [nbt.Int(x - mx), nbt.Int(y - my), nbt.Int(z - mz)],
                               "state": nbt.Int(index[state])})
    out = os.path.join(DATA, "structure", "interior", "console_room_capaldi.nbt")
    nbt.write(out, {"DataVersion": nbt.Int(DATA_VERSION), "size": [nbt.Int(v) for v in ROOM_SIZE],
                    "palette": palette, "blocks": blocks, "entities": []})
    return design["hallway_ends"]


# ------------------------------------------------------------------ models

def groups_by_element(outliner):
    """uuid -> the innermost ctl_* group name above it, or None."""
    out = {}

    def walk(children, ctl):
        for c in children:
            if isinstance(c, str):
                out[c] = ctl
            else:
                name = c["name"]
                walk(c["children"], name if name.startswith("ctl_") else ctl)
    walk(outliner, None)
    return out


def read_obj(path):
    verts, uvs, faces = [], [], []
    for line in open(path):
        p = line.split()
        if not p:
            continue
        if p[0] == "v":
            verts.append([float(v) for v in p[1:4]])
        elif p[0] == "vt":
            uvs.append([float(v) for v in p[1:3]])
        elif p[0] == "f":
            faces.append([tuple(int(i) - 1 for i in q.split("/")[:2]) for q in p[1:]])
    return verts, uvs, faces


def quad(verts, uvs, face):
    pts = [verts[vi] for vi, _ in face]
    a = [pts[1][i] - pts[0][i] for i in range(3)]
    b = [pts[2][i] - pts[0][i] for i in range(3)]
    n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
    length = sum(c * c for c in n) ** 0.5 or 1
    v = [[round(verts[vi][0], 5), round(verts[vi][1], 5), round(verts[vi][2], 5), round(uvs[ti][0], 5),
          round(1 - uvs[ti][1], 5)] for vi, ti in face]
    return {"v": v, "n": [round(c / length, 4) for c in n]}


def models():
    parts = {}
    for name in ("console", "rotor_glass", "rotor_column", "rotor_spinners", "rotor_spinner_mid", "arches"):
        bb = json.load(open(os.path.join(SRC, "models", name + ".bbmodel")))
        verts, uvs, faces = read_obj(os.path.join(SRC, "models", name + ".obj"))
        group = groups_by_element(bb["outliner"])
        i = 0
        for e in bb["elements"]:
            n = sum(1 for f in e["faces"].values() if f.get("texture") is not None)
            ctl = group.get(e["uuid"]) if name == "console" else None
            part = ctl if ctl else ("body" if name == "console" else name)
            if ctl is None or ctl[4:] in KEEP_CONTROLS:
                quads = [quad(verts, uvs, f) for f in faces[i:i + n]]
                if part == "body":
                    for q in quads:  # the plain texture: each face takes the whole (one-colour) image
                        for k, (u, v) in enumerate(((0, 0), (0, 1), (1, 1), (1, 0))):
                            q["v"][k][3], q["v"][k][4] = u, v
                parts.setdefault(part, []).extend(quads)
            i += n
        if i != len(faces):
            raise SystemExit(f"{name}: {len(faces)} faces in the OBJ, {i} in the bbmodel")
    info = json.load(open(os.path.join(SRC, "parts.json")))
    controls = {}
    for c in info["controls"]:
        if c["name"] not in KEEP_CONTROLS:
            continue
        if "ctl_" + c["name"] not in parts:
            raise SystemExit("no model part for control " + c["name"])
        controls[c["name"]] = {"kind": c["kind"], "panel": c["panel"],
                               "box": [round(v, 4) for v in c["hitbox_min"] + c["hitbox_max"]]}
    write_json(os.path.join(ASSETS, "console", "capaldi.json"),
               {"pivot": info["origin"], "parts": parts, "controls": controls, "panels": panels(info["origin"]),
                "monitor": MONITOR}, indent=None)
    tdir = os.path.join(ASSETS, "textures", "entity", "capaldi")
    os.makedirs(tdir, exist_ok=True)
    Image.open(os.path.join(SRC, "textures", "console.png")).convert("RGBA").save(os.path.join(tdir, "console.png"))
    Image.open(os.path.join(SRC, "textures", "console_emissive.png")).convert("RGBA").save(
        os.path.join(tdir, "console_glow.png"))
    Image.new("RGBA", (16, 16), PLAIN).save(os.path.join(tdir, "console_plain.png"))
    return controls


def panels(origin):
    """Each panel's frame: `origin` (middle of its outer edge, on the top), `right` (x of a control standing on it),
    `normal` (its y) and `down` (its z: down the slope, toward the operator), in blocks from the block's corner."""
    out = []
    s, c = math.sin(math.radians(SLOPE)), math.cos(math.radians(SLOPE))
    for k in range(6):
        a = math.radians(90 - 60 * k)
        o = (math.cos(a), 0.0, math.sin(a))
        out.append({
            "origin": [round(origin[0] + o[0] * OUTER, 5), EDGE_Y, round(origin[2] + o[2] * OUTER, 5)],
            "right": [round(math.sin(a), 5), 0.0, round(-math.cos(a), 5)],
            "normal": [round(o[0] * s, 5), round(c, 5), round(o[2] * s, 5)],
            "down": [round(o[0] * c, 5), round(-s, 5), round(o[2] * c, 5)],
        })
    return out


def lang():
    path = os.path.join(ASSETS, "lang", "en_us.json")
    data = json.load(open(path))
    names = {"floor_plate": "Console Room Floor Plate", "wall_panel": "Console Room Wall Panel",
             "frame": "Console Room Frame", "light_strip": "Console Room Light Strip",
             "roundel_wall": "Roundel Wall", "roundel_wall_lit": "Lit Roundel Wall",
             "grille_plate": "Floor Grating", "frame_stairs": "Console Room Frame Stairs",
             "floor_plate_stairs": "Console Room Floor Stairs", "frame_slab": "Console Room Frame Slab",
             "collision_post": "Arch Post (invisible)", "collision_pillar": "Pedestal Post (invisible)",
             "console_hull": "Console Hull (invisible)", "repair_core": "Repair Core",
             "core_casing": "Repair Core Casing"}
    for k, v in names.items():
        data["block.tardis_rb." + k] = v
    write_json(path, data)


if __name__ == "__main__":
    block_assets()
    ends = room_structure()
    models()
    lang()
    print("hallway ends:", json.dumps(ends))
