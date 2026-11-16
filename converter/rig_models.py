"""Builds the port's own blocky creature models for procedural rigs (lotr/docs/rig.md).

Each model is boxes on bones in model pixels (Blockbench style: y down, ground at y 24, head towards -z). This script
packs every box's UV net into one texture, paints it (base colour, pixel noise, shaded edges, a few details) and writes:
  lotr/src/main/resources/data/lotr_port/rig/<name>.json            the rig (bones, legs, gait, parts...)
  lotr/src/main/resources/assets/lotr_port/textures/entity/rig/<name>.png

The shapes are our own, made with reference pictures Aaron picked (an elephant, a blocky dragon, a stone giant) for
proportions only; nothing is copied from them. Run: python3 converter/rig_models.py
"""

import json
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "lotr", "src", "main", "resources")
RIG_DIR = os.path.join(ROOT, "data", "lotr_port", "rig")
TEX_DIR = os.path.join(ROOT, "assets", "lotr_port", "textures", "entity", "rig")


# ---------------------------------------------------------------------------------------------------------- painting

def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, f):
    return tuple(clamp(x * f) for x in c[:3]) + (255,)


class Material:
    """How a box's faces are painted."""

    def __init__(self, base, noise=0.12, top=None, bottom=None, pattern=None, edge=0.82):
        self.base = base
        self.noise = noise
        self.top = top or base
        self.bottom = bottom or base
        self.pattern = pattern  # f(img, x, y, w, h, face, rnd)
        self.edge = edge


def speckle(img, x0, y0, w, h, colour, noise, edge, rnd):
    for y in range(h):
        for x in range(w):
            f = 1 + rnd.uniform(-noise, noise)
            if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                f *= edge
            img.putpixel((x0 + x, y0 + y), shade(colour, f))


def wrinkles(img, x0, y0, w, h, face, rnd):
    """Elephant hide: darker horizontal creases."""
    for y in range(1, h - 1):
        if rnd.random() < 0.28:
            x = rnd.randrange(w)
            ln = rnd.randint(2, 5)
            for i in range(ln):
                if 0 <= x + i < w:
                    c = img.getpixel((x0 + x + i, y0 + y))
                    img.putpixel((x0 + x + i, y0 + y), shade(c, 0.78))


def cracks(img, x0, y0, w, h, face, rnd):
    """Stone: dark cracks wandering across, lighter chips."""
    for _ in range(max(1, (w * h) // 40)):
        x, y = rnd.randrange(w), rnd.randrange(h)
        for _ in range(rnd.randint(3, 8)):
            if 0 <= x < w and 0 <= y < h:
                c = img.getpixel((x0 + x, y0 + y))
                img.putpixel((x0 + x, y0 + y), shade(c, 0.6))
            x += rnd.choice((-1, 0, 1))
            y += rnd.choice((0, 1))
    for _ in range((w * h) // 30):
        x, y = rnd.randrange(w), rnd.randrange(h)
        c = img.getpixel((x0 + x, y0 + y))
        img.putpixel((x0 + x, y0 + y), shade(c, 1.18))


def mossy(img, x0, y0, w, h, face, rnd):
    cracks(img, x0, y0, w, h, face, rnd)
    if face == "top":
        for y in range(h):
            for x in range(w):
                if rnd.random() < 0.55:
                    img.putpixel((x0 + x, y0 + y), shade((74, 104, 52), 1 + rnd.uniform(-0.15, 0.15)))


def scales(img, x0, y0, w, h, face, rnd):
    """Dragon scales: offset rows of darker pixels."""
    for y in range(h):
        for x in range(w):
            if (x + (y // 2) * 2) % 4 == 0 and y % 2 == 0:
                c = img.getpixel((x0 + x, y0 + y))
                img.putpixel((x0 + x, y0 + y), shade(c, 0.72))


def planks(img, x0, y0, w, h, face, rnd):
    for y in range(h):
        if y % 4 == 3:
            for x in range(w):
                c = img.getpixel((x0 + x, y0 + y))
                img.putpixel((x0 + x, y0 + y), shade(c, 0.7))


# ---------------------------------------------------------------------------------------------------------- models

class Model:
    def __init__(self, name, scale):
        self.name = name
        self.scale = scale
        self.bones = {}
        self.order = []
        self.boxes = []  # (bone, abs origin, size, material, details)
        self.extra = {}

    def bone(self, name, parent, pivot, rotation=None):
        b = {"pivot": [float(v) for v in pivot]}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = [float(v) for v in rotation]
        self.bones[name] = b
        self.order.append(name)
        return name

    def box(self, bone, origin, size, mat, details=None):
        """A box at an absolute origin (model pixels, unrotated) on a bone."""
        self.boxes.append((bone, [float(v) for v in origin], [int(v) for v in size], mat, details or {}))

    def mirror_x(self, name):
        return name.replace("_l", "_r") if name.endswith("_l") or "_l_" in name else name

    def build(self):
        # pack UV nets on shelves, biggest first
        nets = []
        for i, (bone, o, s, mat, det) in enumerate(self.boxes):
            w, h, d = s
            nets.append((i, 2 * (d + w), d + h))
        nets.sort(key=lambda n: -n[2])
        size = 64
        while True:
            placed = {}
            x = y = row = 0
            ok = True
            for i, nw, nh in nets:
                if nw > size:
                    ok = False
                    break
                if x + nw > size:
                    x = 0
                    y += row
                    row = 0
                if y + nh > size:
                    ok = False
                    break
                placed[i] = (x, y)
                x += nw
                row = max(row, nh)
            if ok:
                break
            size *= 2
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rnd = random.Random(self.name)
        for i, (bone, o, s, mat, det) in enumerate(self.boxes):
            u, v = placed[i]
            w, h, d = s
            faces = {
                "top": (u + d, v, w, d, mat.top),
                "bottom": (u + d + w, v, w, d, mat.bottom),
                "right": (u, v + d, d, h, mat.base),
                "front": (u + d, v + d, w, h, mat.base),
                "left": (u + d + w, v + d, d, h, mat.base),
                "back": (u + 2 * d + w, v + d, w, h, mat.base),
            }
            for face, (fx, fy, fw, fh, col) in faces.items():
                if fw <= 0 or fh <= 0:
                    continue
                speckle(img, fx, fy, fw, fh, col, mat.noise, mat.edge, rnd)
                if mat.pattern:
                    mat.pattern(img, fx, fy, fw, fh, face, rnd)
                for (dface, px, py, colour) in det.get("pixels", []):
                    if dface == face and 0 <= px < fw and 0 <= py < fh:
                        img.putpixel((fx + px, fy + py), tuple(colour) + (255,))
            self.boxes[i] = (bone, o, s, mat, det, (u, v))
        bones = {}
        for name in self.order:
            bones[name] = dict(self.bones[name])
            bones[name]["cubes"] = []
        for bone, o, s, mat, det, uv in self.boxes:
            p = self.bones[bone]["pivot"]
            bones[bone]["cubes"].append({"uv": list(uv), "origin": [o[k] - p[k] for k in range(3)], "size": s})
        rig = {"texture": [size, size], "scale": self.scale}
        rig.update(self.extra)
        rig["bones"] = bones
        return rig, img

    def write(self):
        rig, img = self.build()
        os.makedirs(RIG_DIR, exist_ok=True)
        os.makedirs(TEX_DIR, exist_ok=True)
        with open(os.path.join(RIG_DIR, self.name + ".json"), "w") as f:
            json.dump(rig, f, indent=1)
            f.write("\n")
        img.save(os.path.join(TEX_DIR, self.name + ".png"))
        print(self.name, "bones", len(rig["bones"]), "boxes", len(self.boxes), "texture", rig["texture"])


def eye_pixels(face, xs, y, colour):
    return [(face, x, y, colour) for x in xs]


# ---------------------------------------------------------------------------------------------------------- mumak

def mumak():
    m = Model("mumak", 3.0)
    hide = Material((122, 112, 102), 0.08, top=(132, 121, 110), bottom=(98, 90, 82), pattern=wrinkles)
    dark = Material((96, 88, 80), 0.08, pattern=wrinkles)
    tusk = Material((228, 218, 190), 0.05, edge=0.9)
    foot = Material((70, 64, 58), 0.06)
    wood = Material((112, 74, 42), 0.1, pattern=planks)
    cloth = Material((150, 32, 28), 0.08, edge=0.75)
    gold = Material((206, 160, 54), 0.1)

    m.bone("body", None, [0, 0, 0])
    m.box("body", [-13, -19, -17], [26, 25, 34], hide)              # barrel of the body
    m.box("body", [-10, -23, -15], [20, 4, 14], hide)               # shoulder hump
    m.box("body", [-11, 6, -12], [22, 3, 26], dark)                 # belly
    # war howdah: a tower on a red cloth
    m.box("body", [-14, -20, -11], [28, 2, 22], cloth)
    m.box("body", [-11, -26, -9], [22, 6, 18], wood)
    m.box("body", [-12, -27, -10], [24, 1, 20], gold)
    for x in (-11, 9):
        for z in (-9, 7):
            m.box("body", [x, -38, z], [2, 11, 2], wood)
    m.box("body", [-12, -40, -10], [24, 2, 20], cloth)

    m.bone("head", "body", [0, -8, -17])
    m.box("head", [-9, -17, -30], [18, 18, 13], hide,
          {"pixels": eye_pixels("left", [3, 4], 7, (24, 18, 14)) + eye_pixels("right", [8, 9], 7, (24, 18, 14))})
    m.box("head", [-7, 1, -28], [14, 4, 9], dark)                   # jaw
    for side, sx in (("l", -1), ("r", 1)):
        ear = m.bone("ear_" + side, "head", [9 * sx, -13, -21])
        m.box(ear, [9 if sx > 0 else -11, -16, -22], [2, 16, 12], dark)    # flat against the head side
        # two tusks a side, angled forward and down
        for k, (dx, dy, ang) in enumerate(((7, 0, 35), (5, 3, 20))):
            t = m.bone("tusk_%s%d" % (side, k), "head", [dx * sx, dy, -27], [ang, 0, 0])
            m.box(t, [dx * sx - 1, dy - 1, -27 - 16 + 2 * k], [2, 2, 16 - 2 * k], tusk)
    m.bone("trunk1", "head", [0, -2, -30])
    m.box("trunk1", [-3, -3, -34], [6, 10, 5], hide)
    m.bone("trunk2", "trunk1", [0, 7, -32])
    m.box("trunk2", [-2.5, 7, -34], [5, 9, 4], hide)
    m.bone("trunk3", "trunk2", [0, 16, -32])
    m.box("trunk3", [-2, 16, -33.5], [4, 6, 3], hide)
    m.bone("trunk4", "trunk3", [0, 22, -32])
    m.box("trunk4", [-1.5, 21, -34], [3, 2, 4], dark)

    m.bone("tail1", "body", [0, -14, 17])
    m.box("tail1", [-1, -14, 17], [2, 10, 2], hide)
    m.bone("tail2", "tail1", [0, -4, 18])
    m.box("tail2", [-1.5, -4, 17.5], [3, 6, 3], dark)

    legs = []
    for name, x, z, grp in (("fl", -8, -10, 0), ("fr", 8, -10, 2), ("bl", -8, 11, 3), ("br", 8, 11, 1)):
        up = m.bone("leg_" + name, "body", [x, 3, z])
        lo = m.bone("leg_" + name + "_lower", up, [x, 13, z - 1])
        m.box(up, [x - 4.5, -1, z - 4.5], [9, 14, 9], hide)
        m.box(lo, [x - 4, 13, z - 5], [8, 9, 8], hide)
        m.box(lo, [x - 4.5, 22, z - 5.5], [9, 2, 9], foot)
        legs.append({"upper": up, "lower": lo, "foot": [x, 24, z - 1], "group": grp, "bend": [0, 0, -1]})

    m.extra = {
        "npc_types": ["lotr_port:mumak"],
        "body": "body",
        "look": "head",
        "legs": legs,
        "gait": {"step_distance": 0.75, "step_ticks": 12, "step_height": 0.12, "lead": 6, "reach_up": 0.5,
                 "reach_down": 0.6, "max_tilt": 10, "body_spring": 0.15, "heavy": 1.0, "stomp": 8,
                 "overlap": 0.5},
        "sways": [{"bone": "ear_l", "axis": "y", "amount": 10, "speed": 0.05, "walk": 2},
                  {"bone": "ear_r", "axis": "y", "amount": -10, "speed": 0.05, "walk": 2},
                  {"bone": "trunk1", "axis": "x", "amount": 6, "speed": 0.04, "walk": 2.5},
                  {"bone": "trunk2", "axis": "x", "amount": 8, "speed": 0.04, "phase": 0.8, "walk": 2},
                  {"bone": "trunk3", "axis": "x", "amount": 10, "speed": 0.04, "phase": 1.6, "walk": 2},
                  {"bone": "tail1", "axis": "z", "amount": 8, "speed": 0.07, "walk": 2},
                  {"bone": "body", "axis": "z", "amount": 1.2, "speed": 0.15, "walk": 1.5}],
        "chains": [{"bones": ["trunk1", "trunk2", "trunk3", "trunk4"], "drag": 0.5, "stiffness": 0.1},
                   {"bones": ["tail1", "tail2"], "drag": 0.6, "stiffness": 0.12}],
        "parts": [
            {"name": "head", "bone": "head", "center": [0, -8, -24], "width": 1.1, "height": 1.1, "damage": 1.5},
            {"name": "trunk", "bone": "trunk2", "center": [0, 12, -32], "width": 0.5, "height": 1.0},
            {"name": "front", "bone": "body", "center": [0, -7, -9], "width": 1.6, "height": 1.6, "solid": True},
            {"name": "back", "bone": "body", "center": [0, -7, 9], "width": 1.6, "height": 1.6, "solid": True},
            {"name": "howdah", "bone": "body", "center": [0, -30, 0], "width": 1.4, "height": 1.0},
            {"name": "leg_fl", "bone": "leg_fl_lower", "center": [-8, 17, -11], "width": 0.55, "height": 0.9,
             "damage": 0.75},
            {"name": "leg_fr", "bone": "leg_fr_lower", "center": [8, 17, -11], "width": 0.55, "height": 0.9,
             "damage": 0.75},
            {"name": "leg_bl", "bone": "leg_bl_lower", "center": [-8, 17, 10], "width": 0.55, "height": 0.9,
             "damage": 0.75},
            {"name": "leg_br", "bone": "leg_br_lower", "center": [8, 17, 10], "width": 0.55, "height": 0.9,
             "damage": 0.75},
        ],
    }
    m.write()


# ---------------------------------------------------------------------------------------------------------- stone giant

def stone_giant():
    m = Model("stone_giant", 4.0)
    stone = Material((128, 124, 116), 0.1, top=(140, 136, 126), pattern=cracks)
    moss = Material((128, 124, 116), 0.1, pattern=mossy)
    darkstone = Material((92, 90, 88), 0.1, pattern=cracks)
    glow = (190, 236, 255)

    m.bone("hips", None, [0, 9, 0])
    m.box("hips", [-8, 4, -5], [16, 7, 10], darkstone)
    m.bone("chest", "hips", [0, 5, 0])
    m.box("chest", [-12, -15, -7], [24, 20, 14], moss)              # barrel chest
    m.box("chest", [-14, -17, -6], [28, 6, 12], moss)               # shoulders
    m.box("chest", [-8, -19, -2], [16, 4, 9], moss)                 # hunched back
    m.bone("head", "chest", [0, -15, -4])
    m.box("head", [-7, -22, -14], [14, 10, 12], stone,
          {"pixels": [("front", 3, 6, glow), ("front", 4, 6, glow), ("front", 9, 6, glow), ("front", 10, 6, glow)]})
    m.box("head", [-8, -24, -15], [16, 4, 13], moss)                # heavy brow slab
    m.box("head", [-6, -12, -13], [12, 3, 9], darkstone)            # jaw

    for side, sx in (("l", -1), ("r", 1)):
        sh = m.bone("arm_" + side, "chest", [14 * sx, -13, 0])
        m.box(sh, [14 * sx - 5 if sx > 0 else -19, -16, -5], [10, 16, 10], moss)
        fa = m.bone("forearm_" + side, sh, [15 * sx, 0, 0])
        m.box(fa, [15 * sx - 4.5 if sx > 0 else -19.5, 0, -4.5], [9, 12, 9], stone)
        m.box(fa, [15 * sx - 5.5 if sx > 0 else -20.5, 11, -6], [11, 8, 11], darkstone)   # fist
    legs = []
    for side, sx, grp in (("l", -1, 0), ("r", 1, 1)):
        up = m.bone("leg_" + side, "hips", [5 * sx, 10, 0])
        lo = m.bone("leg_" + side + "_lower", up, [5 * sx, 16, -1])
        m.box(up, [5 * sx - 4, 9, -4], [8, 8, 8], stone)
        m.box(lo, [5 * sx - 3.5, 16, -4.5], [7, 6, 7], stone)
        m.box(lo, [5 * sx - 4.5, 21, -6.5], [9, 3, 10], darkstone)   # foot slab
        legs.append({"upper": up, "lower": lo, "foot": [5 * sx, 24, -1], "group": grp, "bend": [0, 0, -1],
                     "spread": 1.0})

    # the rock pile it hides as: blocks around its feet (x, y, z), at scale 4 about 3 x 3 x 3
    pile = []
    for x in (-1, 0, 1):
        for z in (-1, 0, 1):
            pile.append([x, 0, z])
    for x, z in ((-1, 0), (0, 0), (0, -1), (1, 0), (0, 1)):
        pile.append([x, 1, z])
    pile.append([0, 2, 0])

    m.extra = {
        "npc_types": ["lotr_port:stone_giant"],
        "body": "hips",
        "look": "head",
        "legs": legs,
        "gait": {"step_distance": 0.55, "step_ticks": 11, "step_height": 0.12, "lead": 5, "reach_up": 0.3,
                 "reach_down": 0.45, "max_tilt": 8, "body_spring": 0.2, "heavy": 1.2, "stomp": 10},
        "sways": [{"bone": "arm_l", "axis": "x", "amount": 4, "speed": 0.05, "walk": 5},
                  {"bone": "arm_r", "axis": "x", "amount": -4, "speed": 0.05, "walk": 5},
                  {"bone": "chest", "axis": "z", "amount": 1.5, "speed": 0.12, "walk": 3},
                  {"bone": "chest", "axis": "x", "amount": 1.5, "speed": 0.04, "walk": 1}],
        "parts": [
            {"name": "head", "bone": "head", "center": [0, -18, -8], "width": 0.9, "height": 0.8, "damage": 1.5},
            {"name": "chest", "bone": "chest", "center": [0, -6, 0], "width": 1.6, "height": 1.5, "solid": True},
            {"name": "arm_l", "bone": "forearm_l", "center": [-15, 10, 0], "width": 0.7, "height": 1.2,
             "damage": 0.6},
            {"name": "arm_r", "bone": "forearm_r", "center": [15, 10, 0], "width": 0.7, "height": 1.2,
             "damage": 0.6},
            {"name": "leg_l", "bone": "leg_l_lower", "center": [-5, 19, -1], "width": 0.55, "height": 0.6,
             "damage": 0.75},
            {"name": "leg_r", "bone": "leg_r_lower", "center": [5, 19, -1], "width": 0.55, "height": 0.6,
             "damage": 0.75},
        ],
        "hide": {"block": "minecraft:stone", "blocks": pile, "wake": 6},
    }
    m.write()


# ---------------------------------------------------------------------------------------------------------- dragon

def dragon():
    m = Model("dragon", 3.0)
    red = Material((150, 34, 38), 0.1, top=(166, 40, 42), bottom=(120, 28, 30), pattern=scales)
    belly = Material((222, 196, 150), 0.08, pattern=planks)
    wing = Material((112, 22, 30), 0.1, pattern=scales, edge=0.7)
    bone_white = Material((236, 232, 220), 0.05, edge=0.9)
    claw = Material((40, 34, 34), 0.05)
    eye = (246, 214, 40)

    m.bone("body", None, [0, 4, 0])
    m.box("body", [-8, -6, -12], [16, 13, 26], red)
    m.box("body", [-6, 7, -10], [12, 2, 22], belly)
    for z in (-10, -4, 2, 8):
        m.box("body", [-1, -8, z], [2, 2, 2], bone_white)          # back spikes
    # neck: three segments rising forward
    prev = "body"
    pivots = [[0, -2, -12], [0, -6, -18], [0, -10, -24]]
    for i, p in enumerate(pivots):
        n = m.bone("neck%d" % (i + 1), prev, p)
        m.box(n, [-4 + i * 0.5, p[1] - 4, p[2] - 7], [8 - i, 8 - i, 7], red)
        m.box(n, [-1, p[1] - 6, p[2] - 5], [2, 2, 2], bone_white)
        prev = n
    m.bone("head", prev, [0, -12, -31])
    m.box("head", [-5, -17, -40], [10, 8, 10], red,
          {"pixels": [("right", 2, 2, eye), ("right", 3, 2, eye), ("left", 6, 2, eye), ("left", 7, 2, eye)]})
    m.box("head", [-4, -14, -48], [8, 5, 8], red)                   # snout
    for sx in (-1, 1):
        h = m.bone("horn_%s" % ("l" if sx < 0 else "r"), "head", [4 * sx, -17, -33], [-25, 0, 0])
        m.box(h, [4 * sx - 1, -18, -33], [2, 2, 9], bone_white)
    m.bone("jaw", "head", [0, -10, -38])
    m.box("jaw", [-4, -10, -48], [8, 3, 10], red)
    for x in (-3, 2):
        m.box("jaw", [x, -11, -47], [1, 1, 1], bone_white)

    # tail: four segments tapering back
    prev = "body"
    z = 14
    for i in range(4):
        t = m.bone("tail%d" % (i + 1), prev, [0, -1 + i * 1.5, z])
        w = 8 - 2 * i
        m.box(t, [-w / 2, -1 + i * 1.5 - w / 2 + 1, z], [w, w - 1, 8], red)
        m.box(t, [-1, -1 + i * 1.5 - w / 2 - 1, z + 2], [2, 2, 2], bone_white)
        prev = t
        z += 8

    # folded wings along the back: arm bone at the shoulder, membrane on it
    for side, sx in (("l", -1), ("r", 1)):
        a = m.bone("wing_" + side, "body", [7 * sx, -5, -8], [0, 0, 25 * -sx])
        m.box(a, [7 * sx - 1.5 if sx > 0 else -8.5, -6, -8], [3, 3, 20], red)
        m.box(a, [7 * sx - 0.5 if sx > 0 else -7.5, -3, -6], [1, 8, 20], wing)
        f = m.bone("wing_tip_" + side, a, [7 * sx, -5, 12])
        m.box(f, [7 * sx - 1 if sx > 0 else -8, -5, 12], [2, 2, 12], red)
        m.box(f, [7 * sx - 0.5 if sx > 0 else -7.5, -3, 12], [1, 6, 12], wing)

    legs = []
    spec = (("fl", -6, -8, 0, 4), ("fr", 6, -8, 1, 4), ("bl", -6, 9, 1, 3), ("br", 6, 9, 0, 3))
    for name, x, z, grp, thick in spec:
        up = m.bone("leg_" + name, "body", [x, 5, z])
        lo = m.bone("leg_" + name + "_lower", up, [x, 14, z + (2 if name[0] == "b" else -1)])
        kz = z + (2 if name[0] == "b" else -1)
        m.box(up, [x - thick / 2 - 1, 3, z - 3], [thick + 2, 12, 6], red)
        m.box(lo, [x - thick / 2, 14, kz - 2], [thick, 9, 4], red)
        m.box(lo, [x - thick / 2 - 0.5, 22, kz - 4], [thick + 1, 2, 6], claw)
        legs.append({"upper": up, "lower": lo, "foot": [x, 24, kz], "group": grp,
                     "bend": [0, 0, 1 if name[0] == "b" else -1]})

    m.extra = {
        "npc_types": ["lotr_port:dragon"],
        "body": "body",
        "look": "head",
        "legs": legs,
        "gait": {"step_distance": 0.6, "step_ticks": 7, "step_height": 0.18, "lead": 4, "reach_up": 0.5,
                 "reach_down": 0.7, "max_tilt": 18, "body_spring": 0.25, "heavy": 0.7, "stomp": 6},
        "sways": [{"bone": "neck1", "axis": "x", "amount": 3, "speed": 0.05, "walk": 1.5},
                  {"bone": "neck2", "axis": "x", "amount": 3, "speed": 0.05, "phase": 0.6, "walk": 1.5},
                  {"bone": "jaw", "axis": "x", "amount": 4, "speed": 0.03, "walk": 1},
                  {"bone": "tail1", "axis": "y", "amount": 6, "speed": 0.06, "walk": 2},
                  {"bone": "tail3", "axis": "y", "amount": 8, "speed": 0.06, "phase": 1.2, "walk": 2},
                  {"bone": "wing_l", "axis": "z", "amount": 2, "speed": 0.04, "walk": 1},
                  {"bone": "wing_r", "axis": "z", "amount": -2, "speed": 0.04, "walk": 1}],
        "chains": [{"bones": ["neck1", "neck2", "neck3"], "drag": 0.35, "stiffness": 0.12},
                   {"bones": ["tail1", "tail2", "tail3", "tail4"], "drag": 0.7, "stiffness": 0.08}],
        "parts": [
            {"name": "head", "bone": "head", "center": [0, -14, -42], "width": 0.8, "height": 0.6, "damage": 1.5},
            {"name": "neck", "bone": "neck2", "center": [0, -6, -21], "width": 0.6, "height": 0.6},
            {"name": "body", "bone": "body", "center": [0, 0, 0], "width": 1.4, "height": 1.0, "solid": True},
            {"name": "tail", "bone": "tail2", "center": [0, 1, 26], "width": 0.6, "height": 0.5, "damage": 0.75},
            {"name": "tail_tip", "bone": "tail4", "center": [0, 3, 42], "width": 0.4, "height": 0.4,
             "damage": 0.5},
        ],
    }
    m.write()


if __name__ == "__main__":
    mumak()
    stone_giant()
    dragon()
