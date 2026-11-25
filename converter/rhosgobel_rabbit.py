"""The Rhosgobel rabbit's model, hop animation and skins (the port's own animal, not converted from LOTR).

Run from the repo root: python3 converter/rhosgobel_rabbit.py (needs Pillow). Writes hand-made content (not
src/generated: generate.py doesn't touch it):
- lotr/src/main/resources/assets/lotr_port/npc_model/rhosgobel_rabbit.json: the model in LOTR's model format (parts,
  pivots, cubes, box UVs; client/CreatureModel builds it) with its animation as expressions (client/CreatureAnim);
- lotr/src/main/resources/assets/lotr_port/textures/entity/rhosgobel_rabbit/{0,1,2}.png: three coats;
- the saddle pass (Aaron 2026-11-25: rideable with a saddle): npc_model/rhosgobel_rabbit_saddle.json (the same parts,
  so it moves with them, plus pommel, cantle and stirrups) and textures/entity/rhosgobel_rabbit/saddle.png.

Radagast's sleigh rabbits (The Hobbit films): pony-sized shaggy hares, crouched long and low with a big rump, a thick
neck, and long upright ears; grey-brown fur with rusty face and ears. Units: model pixels (16 a block), y down from
the ground at y 24, -z forward. Hop: a bound, both hind legs pushing together and both forelegs reaching together,
the body arching and lifting clear of the ground with the stride (limb swing), ears swept back in flight; idle, the
ears twitch and the nose wiggles.
"""
import json
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "lotr/src/main/resources/assets/lotr_port"
TEX_W, TEX_H = 256, 128


# --- texture atlas: each cube gets its box-UV block, packed in rows -----------------------------------------------

class Atlas:
    def __init__(self):
        self.x, self.y, self.row = 0, 0, 0
        self.cubes = []  # (uv, size, look)

    def place(self, size, look):
        w, h, d = (math.ceil(s) for s in size)
        bw, bh = 2 * (w + d), h + d
        if self.x + bw > TEX_W:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        uv = [self.x, self.y]
        self.x += bw
        self.row = max(self.row, bh)
        if self.y + bh > TEX_H:
            raise SystemExit(f"texture full at {size}")
        self.cubes.append((uv, (w, h, d), look))
        return uv


ATLAS = Atlas()


def cube(origin, size, look, inflate=0.0, mirror=False):
    """A cube with its box UV; look names how it's painted (fur, belly, ear, eye...)."""
    return {"uv": ATLAS.place(size, look), "origin": list(origin), "size": list(size), "inflate": inflate,
            "mirror": mirror}


def part(name, pivot, cubes, rotation=(0, 0, 0), children=()):
    return {"name": name, "pivot": list(pivot), "rotation": list(rotation), "cubes": cubes, "children": list(children)}


def leg_pair(side):
    s = 1 if side == "left" else -1
    # crouched low: the body's pivot at y 9, so forelegs of 10 and hind feet flat under the rump reach the ground
    front = part(f"front_{side}_leg", (3.5 * s, 3, -8), [
        cube((-1.5, 0, -1.5), (3, 10, 3), "leg"),
        cube((-1.5, 10, -3.5), (3, 2, 4), "paw"),
    ])
    foot = part(f"hind_{side}_foot", (0, 10, 1), [cube((-1.5, 1, -10), (3, 2, 12), "paw")])
    hind = part(f"hind_{side}_leg", (5 * s, 2, 9), [
        cube((-2, -2, -6), (4, 10, 9), "fur"),     # the thigh, high on the haunch
        cube((-1.5, 5, -1), (3, 6, 3), "leg"),     # the shank down to the foot
    ], children=[foot])
    return front, hind


def ear(side):
    s = 1 if side == "left" else -1
    return part(f"{side}_ear", (2.5 * s, -6, -0.5), [cube((-1.5, -16, -1), (3, 16, 2), "ear", mirror=s < 0)],
                rotation=(-0.25, 0, 0.15 * s))


def model():
    fl, hl = leg_pair("left")
    fr, hr = leg_pair("right")
    head = part("head", (0, -7, -1), rotation=(-0.3, 0, 0), cubes=[
        cube((-4.5, -6, -8), (9, 8, 10), "head"),
        cube((-5.5, -1, -6), (11, 4, 5), "ruff", inflate=0.3),   # cheek fluff
    ], children=[
        part("nose", (0, -1, -8), [cube((-3, -1, -4), (6, 5, 4), "muzzle")]),
        ear("left"), ear("right"),
    ])
    neck = part("neck", (0, -2, -11), [cube((-4, -8, -4), (8, 9, 7), "fur", inflate=0.4)], rotation=(0.3, 0, 0),
                children=[head])
    tufts = [part(f"tuft{i}", (x, -6, z), [cube((-1.5, -2, -2), (3, 3, 4), "tuft")], rotation=(r, 0, 0))
             for i, (x, z, r) in enumerate(((1.5, -6, 0.5), (-2, 0, 0.4), (2, 6, 0.6)))]
    body = part("body", (0, 9, 2), [
        cube((-6, -6, -12), (12, 12, 20), "fur"),               # the long back
        cube((-7, -7, 2), (14, 14, 11), "fur"),                 # the big rump
        cube((-5, -3, -15), (10, 10, 4), "ruff", inflate=0.3),  # shaggy chest
    ], children=[
        neck, part("tail", (0, -3, 13), [cube((-2, -2, 0), (4, 4, 3), "tail", inflate=0.4)], rotation=(0.6, 0, 0)),
        fl, fr, hl, hr, *tufts,
    ])
    return {"body": body}


# --- animation ------------------------------------------------------------------------------------------------------

def e(op, *args):
    return [op, *args]


IN = lambda name: ["in", name]  # noqa: E731
P = e("mul", IN("limb"), 0.55)                                    # the stride's phase
A = e("min", e("mul", IN("amount"), 1.4), 1.0)                    # how hard it's bounding (0 standing)
DEFS = [P, A, e("sin", ["ref", 0]), e("cos", ["ref", 0])]
PH, AMT, SIN, COS = ["ref", 0], ["ref", 1], ["ref", 2], ["ref", 3]
IDLE = e("sub", 1.0, AMT)


def rest(part_, prop):
    return ["rest", part_, prop]


def anim():
    s = []
    # the body lifts clear with the stride and pitches: nose up pushing off, down landing
    s.append(["body", "y", e("sub", rest("body", "y"), e("mul", e("mul", AMT, 3.5), e("abs", SIN)))])
    s.append(["body", "xRot", e("mul", e("mul", AMT, 0.2), SIN)])
    # both hind legs push together; the feet stay flat a while, then flick back
    for side in ("left", "right"):
        s.append([f"hind_{side}_leg", "xRot", e("mul", e("mul", AMT, 0.85), COS)])
        s.append([f"hind_{side}_foot", "xRot", e("mul", e("mul", AMT, -0.6), COS)])
        # both forelegs reach together, a little after the hind legs
        s.append([f"front_{side}_leg", "xRot", e("mul", e("mul", AMT, -1.0), e("cos", e("add", PH, 0.7)))])
    # neck and head: look where it looks, held level against the body's pitch
    s.append(["neck", "xRot", e("sub", rest("neck", "xRot"), e("mul", e("mul", AMT, 0.12), SIN))])
    s.append(["neck", "yRot", e("mul", e("rad", IN("yaw")), 0.4)])
    s.append(["head", "yRot", e("mul", e("rad", IN("yaw")), 0.6)])
    s.append(["head", "xRot", e("add", rest("head", "xRot"), e("add", e("mul", e("rad", IN("pitch")), 0.7),
                                                             e("mul", e("mul", AMT, -0.1), SIN)))])
    # ears: swept back in flight; standing, a slow twitch each, out of step
    for side, k, sgn in (("left", 0.0, 1), ("right", 2.1, -1)):
        twitch = e("mul", e("mul", IDLE, 0.08), e("sin", e("add", e("mul", IN("age"), 0.17), k)))
        sweep = e("mul", e("mul", AMT, 0.55), e("add", 0.5, e("mul", 0.5, e("sin", e("sub", PH, 1.0)))))
        s.append([f"{side}_ear", "xRot", e("sub", e("add", rest(f"{side}_ear", "xRot"), twitch), sweep)])
        s.append([f"{side}_ear", "zRot", e("add", rest(f"{side}_ear", "zRot"),
                                           e("mul", 0.05 * sgn, e("sin", e("add", e("mul", IN("age"), 0.09), k))))])
    # nose wiggle when still; tail flicks with the stride
    s.append(["nose", "y", e("add", rest("nose", "y"), e("mul", e("mul", IDLE, 0.25), e("sin", e("mul", IN("age"),
                                                                                                    1.3))))])
    s.append(["tail", "xRot", e("add", rest("tail", "xRot"), e("mul", e("mul", AMT, 0.4), SIN))])
    return {"biped": False, "set": s}


# --- skins ---------------------------------------------------------------------------------------------------------

COATS = [
    # base fur, dark (back, streaks), light (belly, inner), rust (face, ears), tail
    {"fur": (112, 98, 84), "dark": (70, 58, 48), "light": (168, 152, 128), "rust": (138, 88, 52),
     "tail": (214, 204, 186)},                                                   # grey-brown, as in the films
    {"fur": (124, 118, 110), "dark": (78, 74, 70), "light": (182, 176, 166), "rust": (128, 96, 70),
     "tail": (222, 218, 210)},                                                   # grey
    {"fur": (96, 70, 48), "dark": (58, 40, 28), "light": (150, 118, 86), "rust": (122, 72, 38),
     "tail": (206, 190, 166)},                                                   # dark brown
]


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def paint(seed, coat):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()

    def fur(x, y, base, face, i, j, w, h):
        """Shaggy fur: vertical strands of noise, darker toward the top of side faces."""
        strand = ((i * 7 + seed * 13) % 5) / 4.0
        n = rnd.random() * 0.35 + strand * 0.25
        c = mix(base, coat["dark"], n * 0.7)
        if face in ("side", "front", "back") and h > 2:
            c = mix(c, coat["dark"], max(0.0, 0.35 - j / h * 0.5))        # the back darker
            if j > h * 0.7:
                c = mix(c, coat["light"], (j - h * 0.7) / (h * 0.3) * 0.5)  # lighter low on the flanks
        if rnd.random() < 0.08:
            c = mix(c, coat["rust"], 0.5)                                   # rusty flecks
        px[x, y] = (*c, 255)

    for (u, v), (w, h, d), look in ATLAS.cubes:
        faces = {  # name: (x0, y0, width, height, kind)
            "top": (u + d, v, w, d, "top"), "bottom": (u + d + w, v, w, d, "bottom"),
            "right": (u, v + d, d, h, "side"), "front": (u + d, v + d, w, h, "front"),
            "left": (u + d + w, v + d, d, h, "side"), "back": (u + 2 * d + w, v + d, w, h, "back"),
        }
        for name, (x0, y0, fw, fh, kind) in faces.items():
            for j in range(fh):
                for i in range(fw):
                    x, y = x0 + i, y0 + j
                    if look in ("fur", "ruff", "tuft"):
                        base = coat["light"] if kind == "bottom" else coat["fur"]
                        if look == "ruff":
                            base = mix(base, coat["light"], 0.35)
                        fur(x, y, base, kind, i, j, fw, fh)
                    elif look == "head":
                        fur(x, y, mix(coat["fur"], coat["rust"], 0.45), kind, i, j, fw, fh)
                    elif look == "muzzle":
                        c = mix(coat["light"], coat["rust"], 0.25 + rnd.random() * 0.1)
                        px[x, y] = (*c, 255)
                    elif look == "ear":
                        edge = i == 0 or i == fw - 1 or j == 0
                        if kind == "front" and not edge:   # the inside of the ear faces forward
                            c = mix((196, 150, 130), coat["rust"], 0.3 + 0.2 * (j / fh))
                        else:
                            c = mix(coat["rust"], coat["dark"], 0.35 + (0.4 if j < 3 else 0) + rnd.random() * 0.15)
                        px[x, y] = (*c, 255)
                    elif look == "leg":
                        fur(x, y, mix(coat["fur"], coat["light"], 0.2), kind, i, j, fw, fh)
                    elif look == "paw":
                        c = mix(coat["light"], coat["dark"], 0.15 + rnd.random() * 0.15)
                        px[x, y] = (*c, 255)
                    elif look == "tail":
                        c = mix(coat["tail"], coat["light"], rnd.random() * 0.3)
                        px[x, y] = (*c, 255)
        if look == "head":
            # eyes: dark, wide-set on the sides, with a glint; a pink nose on the muzzle comes below
            for x0 in (u, u + d + w):
                ex, ey = x0 + d - 4 if x0 == u else x0 + 2, v + d + 2
                for dx in range(2):
                    for dy in range(2):
                        px[ex + dx, ey + dy] = (20, 14, 10, 255)
                px[ex, ey] = (210, 200, 190, 255)
        if look == "muzzle":
            fx, fy = u + d, v + d     # front face
            for dx in range(w // 2 - 1, w // 2 + 1):
                px[fx + dx, fy] = (170, 110, 105, 255)
            px[fx + w // 2 - 1, fy + 2] = px[fx + w // 2, fy + 2] = (60, 40, 32, 255)  # mouth
    return img


# --- the saddle: the same parts, the back a little bigger, with a seat, pommel, cantle and stirrups ---------------

def saddle_model(main_parts):
    """The saddle pass's model: the rabbit's own parts (so it moves with them), the back cube inflated over the fur
    and painted only where the saddle sits, plus the saddle's own pieces on the body."""
    import copy
    parts = copy.deepcopy(main_parts)
    body = parts["body"]
    body["cubes"][0]["inflate"] = 0.5
    body["cubes"] += [
        cube((-2, -8.5, -8), (4, 2.5, 2), "saddle_pommel"),
        cube((-3, -8.5, 2), (6, 2.5, 1.5), "saddle_cantle"),
        cube((6.4, -5, -3.5), (0.6, 9, 1), "saddle_leather"),
        cube((-7.0, -5, -3.5), (0.6, 9, 1), "saddle_leather"),
        cube((6.2, 4, -4.5), (1, 1, 3), "saddle_iron"),
        cube((-7.2, 4, -4.5), (1, 1, 3), "saddle_iron"),
    ]
    return parts


def paint_saddle():
    img = Image.new("RGBA", (TEX_W, TEX_H), (0, 0, 0, 0))
    px = img.load()
    rnd = random.Random(7)
    seat, flap, edge, brass = (92, 56, 30), (120, 74, 40), (58, 34, 18), (204, 160, 64)
    back = next(c for c in ATLAS.cubes if c[1] == (12, 12, 20))
    (u, v), (w, h, d), _ = back

    def put(x, y, c):
        n = rnd.random() * 0.12
        px[x, y] = (*(int(ch * (1 - n)) for ch in c), 255)

    lo, hi = d // 2 - 5, d // 2 + 5          # the saddle's length, round the back's middle
    for j in range(lo, hi):                 # the seat on top
        for i in range(w):
            put(u + d + i, v + j, edge if j in (lo, hi - 1) or i in (0, w - 1) else seat)
    for x0 in (u, u + d + w):               # the flaps down each side, and the girth strap
        for i in range(lo + 1, hi - 1):
            for j in range(5):
                put(x0 + i, v + d + j, edge if j == 4 else flap)
        for j in range(h):
            put(x0 + d // 2, v + d + j, edge)
    for i in range(w):                      # the girth under the belly
        put(u + d + w + i, v + d // 2, edge)
    for (cu, cv), (cw, ch, cd), look in ATLAS.cubes:
        if not look.startswith("saddle_"):
            continue
        colour = brass if look == "saddle_iron" else edge if look == "saddle_leather" else flap
        for y in range(cv, cv + ch + cd):
            for x in range(cu, cu + 2 * (cw + cd)):
                put(x, y, colour)
    return img


def main():
    m = {"texture": [TEX_W, TEX_H], "render_scale": 1.0, "parts": model(), "hidden": [], "renderers": [],
         "anim": anim(), "defs": DEFS, "inputs": []}
    out = RES / "npc_model/rhosgobel_rabbit.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(m, indent=1) + "\n")
    tex = RES / "textures/entity/rhosgobel_rabbit"
    tex.mkdir(parents=True, exist_ok=True)
    for i, coat in enumerate(COATS):
        paint(100 + i, coat).save(tex / f"{i}.png")
    sm = dict(m, parts=saddle_model(m["parts"]))
    (RES / "npc_model/rhosgobel_rabbit_saddle.json").write_text(json.dumps(sm, indent=1) + "\n")
    paint_saddle().save(tex / "saddle.png")
    print(f"{out.relative_to(ROOT)}: {len(ATLAS.cubes)} cubes; {len(COATS)} coats in {tex.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
