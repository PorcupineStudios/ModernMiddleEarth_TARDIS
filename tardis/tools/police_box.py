"""Turns Aaron's police box (a Blockbench glTF, tools/police_box/model.gltf) into what the exterior renderer draws.

Writes assets/tardis_rb/police_box/model.json: triangles (x y z u v, normal) for the body and for each door, with the
door's hinge, plus the door swing and from which look the windows glow. Writes the textures
textures/entity/police_box/look_0..3.png: look 3 is the original paint; looks 0-2 are the same box with the paint
chipped and scratched off so bare wood shows (most on the crashed wreck, least when nearly repaired), and glow.png
(the lit windows and sign, drawn full bright). Run from the repo root: python3 tardis/tools/police_box.py
"""
import base64
import json
import math
import os
import random
import struct

from PIL import Image

SRC = "tardis/tools/police_box"
ASSETS = "tardis/src/main/resources/assets/tardis_rb"
# share of the paint worn down to wood, per look (look 3 = repaired, original paint)
WEAR = [0.55, 0.32, 0.12, 0.0]
GLOW_FROM_LOOK = 2  # the windows light up once the box is mostly repaired
DOOR_SWING = {"degrees": 100, "ticks": 8}  # doors swing outward, so they are seen opening around the portal
WOOD = [(122, 92, 58), (106, 78, 48), (138, 106, 68), (94, 68, 42), (150, 118, 78)]
DOOR_NODES = {"doorL": "door_left", "doorR": "door_right"}


def quat_rotate(q, v):
    x, y, z, w = q
    vx, vy, vz = v
    # v' = q v q*
    tx = 2 * (y * vz - z * vy)
    ty = 2 * (z * vx - x * vz)
    tz = 2 * (x * vy - y * vx)
    return (vx + w * tx + (y * tz - z * ty), vy + w * ty + (z * tx - x * tz), vz + w * tz + (x * ty - y * tx))


def read_accessor(g, buf, index):
    a = g["accessors"][index]
    view = g["bufferViews"][a["bufferView"]]
    start = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    size = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}[a["type"]]
    fmt = {5126: "f", 5123: "H", 5125: "I"}[a["componentType"]]
    width = struct.calcsize(fmt)
    stride = view.get("byteStride", width * size)
    out = []
    for i in range(a["count"]):
        vals = struct.unpack_from("<" + fmt * size, buf, start + i * stride)
        out.append(vals if size > 1 else vals[0])
    return out


def mesh_triangles(g, buf, mesh, transform):
    tris = []
    for p in g["meshes"][mesh]["primitives"]:
        pos = [transform(v) for v in read_accessor(g, buf, p["attributes"]["POSITION"])]
        uv = read_accessor(g, buf, p["attributes"]["TEXCOORD_0"])
        idx = read_accessor(g, buf, p["indices"])
        for i in range(0, len(idx), 3):
            a, b, c = idx[i:i + 3]
            pa, pb, pc = pos[a], pos[b], pos[c]
            n = cross(sub(pb, pa), sub(pc, pa))
            length = math.sqrt(sum(x * x for x in n)) or 1
            n = [round(x / length, 4) for x in n]
            tris.append({"v": [vert(pos[k], uv[k]) for k in (a, b, c)], "n": n})
    return tris


def sub(a, b):
    return [a[i] - b[i] for i in range(3)]


def cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def vert(p, uv):
    return [round(p[0], 5), round(p[1], 5), round(p[2], 5), round(uv[0], 5), round(uv[1], 5)]


def model():
    g = json.load(open(os.path.join(SRC, "model.gltf")))
    buf = base64.b64decode(g["buffers"][0]["uri"].split(",", 1)[1])
    parts = {"body": {"tris": []}}

    def walk(node_id, offset, rotation, part):
        node = g["nodes"][node_id]
        t = node.get("translation", [0, 0, 0])
        r = node.get("rotation", [0, 0, 0, 1])
        name = node.get("name")
        if name in DOOR_NODES:
            part = DOOR_NODES[name]
            hinge = [offset[i] + t[i] for i in range(3)]
            parts[part] = {"hinge": [round(x, 5) for x in hinge], "tris": []}
        world_t = [offset[i] + t[i] for i in range(3)]

        def transform(v, rr=r, wt=world_t):
            rv = quat_rotate(rr, v)
            return [rv[0] + wt[0], rv[1] + wt[1], rv[2] + wt[2]]
        if "mesh" in node:
            parts[part]["tris"].extend(mesh_triangles(g, buf, node["mesh"], transform))
        for c in node.get("children", []):
            walk(c, world_t, r, part)

    for root in g["scenes"][g.get("scene", 0)]["nodes"]:
        walk(root, [0, 0, 0], [0, 0, 0, 1], "body")
    out = {"texture_size": [128, 128], "glow_from_look": GLOW_FROM_LOOK, "door_swing": DOOR_SWING,
           "door_open_sign": {"door_left": -1, "door_right": 1}, "parts": parts}
    path = os.path.join(ASSETS, "police_box", "model.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(out, f, separators=(",", ":"))
    return {k: len(v["tris"]) for k, v in parts.items()}


def painted(px):
    r, g, b, a = px
    return a > 0 and b > r + 25 and b > g  # the blue paint, not the grey inside, white signs or black text


def value_noise(w, h, cell, rng):
    grid = [[rng.random() for _ in range(w // cell + 2)] for _ in range(h // cell + 2)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            gx, gy = x / cell, y / cell
            x0, y0 = int(gx), int(gy)
            fx, fy = gx - x0, gy - y0
            top = grid[y0][x0] * (1 - fx) + grid[y0][x0 + 1] * fx
            bot = grid[y0 + 1][x0] * (1 - fx) + grid[y0 + 1][x0 + 1] * fx
            out[y][x] = top * (1 - fy) + bot * fy
    return out


def textures():
    paint = Image.open(os.path.join(SRC, "paint.png")).convert("RGBA")
    w, h = paint.size
    rng = random.Random(1963)
    # chips: two scales of noise; scratches: thin vertical streaks
    big, small = value_noise(w, h, 8, rng), value_noise(w, h, 3, rng)
    wear = [[0.65 * big[y][x] + 0.35 * small[y][x] for x in range(w)] for y in range(h)]
    for _ in range(60):
        x, y, n = rng.randrange(w), rng.randrange(h), rng.randrange(3, 10)
        for k in range(n):
            if 0 <= y + k < h:
                wear[y + k][x] = max(wear[y + k][x], 0.9 + rng.random() * 0.1)
    ranked = sorted(wear[y][x] for y in range(h) for x in range(w))
    out_dir = os.path.join(ASSETS, "textures", "entity", "police_box")
    os.makedirs(out_dir, exist_ok=True)
    src = paint.load()
    for look, share in enumerate(WEAR):
        img = paint.copy()
        px = img.load()
        if share > 0:
            cut = ranked[int(len(ranked) * (1 - share))]
            for y in range(h):
                for x in range(w):
                    if painted(src[x, y]) and wear[y][x] >= cut:
                        # wood grain runs along the boards (vertically), a little darker at the paint's edge
                        r, g, b = WOOD[(x * 7 + (y // 5) * 3) % len(WOOD)]
                        edge = wear[y][x] - cut < 0.02
                        f = 0.8 if edge else 1.0
                        px[x, y] = (int(r * f), int(g * f), int(b * f), 255)
                    elif painted(src[x, y]) and wear[y][x] >= cut - 0.08:
                        # faded, scuffed paint around the bare patches
                        r, g, b, a = src[x, y]
                        px[x, y] = (min(255, r + 25), min(255, g + 20), max(0, b - 20), a)
        img.save(os.path.join(out_dir, f"look_{look}.png"))
    Image.open(os.path.join(SRC, "glow.png")).convert("RGBA").save(os.path.join(out_dir, "glow.png"))


if __name__ == "__main__":
    print(model())
    textures()
