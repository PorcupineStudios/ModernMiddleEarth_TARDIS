"""Writes the harness fits of the LOTR port's animals (run from harness/: python3 tools/fits.py).

Reads each animal's model (lotr/src/generated/resources/assets/lotr_port/npc_model/<model>.json: LOTR's parts with
pivots, rotations and cubes, in model pixels, y down, -z forward) and lays the harness out on its own parts, so it
moves with them in game (client/BodyHarness draws a piece in its part's frame, from the LOTR port's part poses):

- on the body part: a girth round the body in the gap between the front and hind legs; a breeching round the upper
  hindquarters behind (or above) the hind legs; a spine strap and backpad on top; side straps from girth to breeching,
  above the thighs where the legs sit beside the body; brass tug rings on the side straps just behind the girth (the
  ropes' ends: rope_anchor, in blocks in the animal's frame, worked out from the model at rest);
- on the neck part (or the head, for animals whose neck is part of it): a padded collar round the neck near its base,
  with brass hames, so it turns and nods with the neck.

Writes src/main/resources/assets/ringbearer_harness/harness_fit/lotr_<name>.json, keeping each file's "body" and
"collar" (the fallback drawn where no part poses are offered). The spider (a procedural rig, no part poses) keeps its
hand fit.
"""
import json
import math
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent.parent
MODELS = HERE.parent / "lotr/src/generated/resources/assets/lotr_port/npc_model"
OUT = HERE / "src/main/resources/assets/ringbearer_harness/harness_fit"

WARGS = ["angmar_warg", "angmar_warg_bombardier", "gundabad_warg", "mordor_warg", "mordor_warg_bombardier",
         "uruk_warg", "uruk_warg_bombardier", "utumno_warg", "utumno_fire_warg", "utumno_ice_warg",
         "utumno_obsidian_warg"]
# name: model, entity kinds, body part, neck part (the collar's), front legs, hind legs, render scale (the breed's
# scale times the LOTR renderer's own pre-render scale)
ANIMALS = {
    "lotr_horse": ("old_horse", ["horse", "zebra"], "body", "neck",
                   ["front_left_leg", "front_right_leg"], ["back_left_leg", "back_right_leg"], 1.0),
    "lotr_shire_pony": ("old_horse", ["shire_pony"], "body", "neck",
                        ["front_left_leg", "front_right_leg"], ["back_left_leg", "back_right_leg"], 0.8),
    "lotr_wild_boar": ("boar", ["wild_boar"], "body", "head", ["leg3", "leg4"], ["leg1", "leg2"], 1.0),
    "lotr_camel": ("camel", ["camel"], "body", "head", ["leg3", "leg4"], ["leg1", "leg2"], 1.25),
    "lotr_elk": ("elk", ["elk"], "body", "head", ["leg3", "leg4"], ["leg1", "leg2"], 1.0),
    "lotr_rhino": ("rhino", ["rhino"], "body", "neck", ["leg3", "leg4"], ["leg1", "leg2"], 1.0),
    "lotr_giraffe": ("giraffe", ["giraffe"], "body", "neck", ["leg3", "leg4"], ["leg1", "leg2"], 1.0),
    "lotr_warg": ("warg", WARGS, "body", "head", ["leg3", "leg4"], ["leg1", "leg2"], 1.0),
    "lotr_rhosgobel_rabbit": ("rabbit", ["rhosgobel_rabbit"], "body", None, ["right_arm", "left_arm"],
                              ["right_leg", "left_leg"], 2.8 * 0.75),
}
# Per animal: a rabbit has no neck to hang a collar on and its forelegs sit against its chest, so it wears a breast
# collar (a strap across the chest above the forelegs, as small draught animals do), a girth over the back and sides
# only (no belly strap through the forelegs) at mid-back, and no breeching (its haunches are its hind legs).
OPTIONS = {
    "lotr_rhosgobel_rabbit": {"breast": True, "belly": False, "breeching": False, "girth_at": 0.55},
}
PROUD, BAND, STRAP = 0.4, 0.9, 0.6  # pixels: gap over the hide, band thickness, side strap thickness


# --- model geometry ----------------------------------------------------------------------------------------------

def rot(r):
    """ModelPart rotation (zRot * yRot * xRot) as a 3x3 matrix."""
    x, y, z = r
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    rx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    ry = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    rz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(rz, mul(ry, rx))


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def transpose(m):
    return [[m[j][i] for j in range(3)] for i in range(3)]


class Part:
    def __init__(self, name, d, parent):
        self.name, self.parent = name, parent
        self.pivot = d.get("pivot", [0, 0, 0])
        self.r = rot(d.get("rotation") or [0, 0, 0])
        self.cubes = [(c["origin"], c["size"]) for c in d.get("cubes", [])]

    def to_root(self, p):
        """A point in this part's frame, in the model's root frame."""
        q = [a + b for a, b in zip(apply(self.r, p), self.pivot)]
        return self.parent.to_root(q) if self.parent else q

    def from_root(self, q):
        p = self.parent.from_root(q) if self.parent else q
        return apply(transpose(self.r), [a - b for a, b in zip(p, self.pivot)])

    def dir_from_root(self, v):
        d = self.parent.dir_from_root(v) if self.parent else v
        return apply(transpose(self.r), d)


def load(model):
    d = json.loads((MODELS / f"{model}.json").read_text())
    parts = {}

    def walk(items, parent):
        for key, p in (items.items() if isinstance(items, dict) else enumerate(items)):
            part = Part(p.get("name", key), p, parent)
            parts.setdefault(part.name, part)
            walk(p.get("children", []), part)

    walk(d["parts"], None)
    return parts


def corners(origin, size):
    return [[origin[0] + size[0] * (i & 1), origin[1] + size[1] * ((i >> 1) & 1), origin[2] + size[2] * (i >> 2)]
            for i in range(8)]


def bounds(points):
    return [min(p[i] for p in points) for i in range(3)], [max(p[i] for p in points) for i in range(3)]


# --- a frame on a part: lateral, up and forward axes of its own coordinates --------------------------------------

class Frame:
    """
    A body part's axes: x is always sideways (the models are symmetric in x); forward is the spine, the longer of the
    body's y and z (a boar's body is turned on its side, a rabbit's stands up), pointing from the hind legs to the
    front legs; up is the other, away from the legs (or the model's up, where the part isn't turned).
    """

    def __init__(self, part, front_local, hind_local, body_lo, body_hi):
        self.l_axis = 0
        self.u_axis = 1 if body_hi[1] - body_lo[1] > body_hi[2] - body_lo[2] else 2
        self.h_axis = 3 - self.u_axis
        self.u_sign = 1 if front_local[self.u_axis] - hind_local[self.u_axis] > 0 else -1
        up_local = part.dir_from_root([0, -1, 0])  # model up is -y
        if abs(up_local[self.h_axis]) > 0.5:
            self.h_sign = 1 if up_local[self.h_axis] > 0 else -1
        else:
            legs = (front_local[self.h_axis] + hind_local[self.h_axis]) / 2
            mid = (body_lo[self.h_axis] + body_hi[self.h_axis]) / 2
            self.h_sign = -1 if legs > mid else 1

    def luh(self, p):
        return p[self.l_axis], p[self.u_axis] * self.u_sign, p[self.h_axis] * self.h_sign

    def box(self, l0, l1, u0, u1, h0, h1):
        """A box given in (lateral, forward, up) back in the part's x/y/z."""
        lo, hi = [0.0] * 3, [0.0] * 3
        for axis, a, b, sign in ((self.l_axis, l0, l1, 1), (self.u_axis, u0, u1, self.u_sign),
                                 (self.h_axis, h0, h1, self.h_sign)):
            lo[axis], hi[axis] = sorted((a * sign, b * sign))
        return [round(v, 2) for v in lo + hi]


def leg_box(leg, body, frame):
    """A leg's top cube (the thigh, where it meets the body) in the body frame's (lateral, forward, up) ranges."""
    origin, size = leg.cubes[0]
    pts = [frame.luh(body.from_root(leg.to_root(c))) for c in corners(origin, size)]
    return bounds(pts)


def fit(name, model, body_name, neck_name, front, hind, scale):
    opts = OPTIONS.get(name, {})
    parts = load(model)
    body = parts[body_name]
    # the legs' tops and the body's extent in the body's own frame
    fr = [sum(body.from_root(parts[n].to_root([0, 0, 0]))[i] for n in front) / len(front) for i in range(3)]
    hr = [sum(body.from_root(parts[n].to_root([0, 0, 0]))[i] for n in hind) / len(hind) for i in range(3)]
    blo, bhi = bounds([p for o, s in body.cubes for p in corners(o, s)])
    f = Frame(body, fr, hr, blo, bhi)
    cubes = [bounds([f.luh(c) for c in corners(o, s)]) for o, s in body.cubes]
    cubes.sort(key=lambda b: -(b[1][0] - b[0][0]) * (b[1][1] - b[0][1]) * (b[1][2] - b[0][2]))
    u_min = min(b[0][1] for b in cubes)
    u_max = max(b[1][1] for b in cubes)
    fl = [leg_box(parts[n], body, f) for n in front]
    hl = [leg_box(parts[n], body, f) for n in hind]
    front_back = min(b[0][1] for b in fl)     # the front legs' back edge
    hind_front = max(b[1][1] for b in hl)     # the hind legs' front edge
    hind_back = min(b[0][1] for b in hl)
    leg_top = max(b[1][2] for b in fl + hl)   # highest thigh top
    legs_outside = max(abs(b[0][0]) for b in fl + hl), max(abs(b[1][0]) for b in fl + hl)

    def section(u):
        """The body cube under the band at u: half width, bottom, top."""
        for lo, hi in cubes:
            if lo[1] - 0.01 <= u <= hi[1] + 0.01:
                return max(abs(lo[0]), abs(hi[0])), lo[2], hi[2]
        lo, hi = cubes[0]
        return max(abs(lo[0]), abs(hi[0])), lo[2], hi[2]

    pieces = []

    def add(part, mat, box):
        pieces.append({"part": part, "material": mat, "box": box})

    def band(u0, u1, from_h=None):
        w, bot, top = section((u0 + u1) / 2)
        o = w + PROUD
        if from_h is None and not opts.get("belly", True):
            from_h = bot + 0.4 * (top - bot)
        h0 = bot - PROUD if from_h is None else from_h
        add(body_name, "leather", f.box(-o - BAND, o + BAND, u0, u1, top + PROUD, top + PROUD + BAND))
        if from_h is None:
            add(body_name, "leather", f.box(-o - BAND, o + BAND, u0, u1, h0 - BAND, h0))
        for s in (-1, 1):
            add(body_name, "leather", f.box(*sorted((s * o, s * (o + BAND))), u0, u1, h0, top + PROUD))

    # girth: in the gap between front and hind legs (behind the front legs if they overlap)
    gap0, gap1 = (hind_front, front_back) if hind_front < front_back else (front_back - 3, front_back)
    gw = max(1.0, min(2.0, gap1 - gap0 - 1.0))
    ug = (gap0 + gap1) / 2
    if "girth_at" in opts:  # a share of the body from its rear
        ug, gw = u_min + opts["girth_at"] * (u_max - u_min), 2.0
    band(ug - gw / 2, ug + gw / 2)
    # breeching: behind the hind legs if there's room, else just over them, round the upper hindquarters only
    w_r, bot_r, top_r = section(u_min + 1)
    ub = (u_min + hind_back) / 2 if hind_back - u_min >= 2.5 else u_min + 1.25
    breech_from = max(leg_top + 0.5, bot_r + 0.45 * (top_r - bot_r))
    if opts.get("breeching", True):
        band(ub - 0.75, ub + 0.75, from_h=breech_from)
    else:
        ub = u_min + 1.0
    # side straps, girth to breeching: above the thighs if the legs sit beside the body, else at mid flank
    w_g, bot_g, top_g = section(ug)
    beside = legs_outside[1] > w_g - 0.5
    hs = (max(leg_top, bot_g) + top_g) / 2 if beside and leg_top < top_g - 1.2 else bot_g + 0.55 * (top_g - bot_g)
    hs = min(hs, top_g - 0.8)
    for s in (-1, 1):
        o = w_g + PROUD
        add(body_name, "leather", f.box(*sorted((s * o, s * (o + STRAP))), ub + 0.75, ug - gw / 2, hs - 0.6, hs + 0.6))
        # tug ring just behind the girth
        add(body_name, "brass", f.box(*sorted((s * (o + STRAP), s * (o + STRAP + 0.9))), ug - gw / 2 - 2.2,
                                      ug - gw / 2 - 0.4, hs - 0.9, hs + 0.9))
    # spine strap and backpad
    add(body_name, "leather", f.box(-1.0, 1.0, ub, ug, top_g + PROUD, top_g + PROUD + STRAP))
    add(body_name, "leather", f.box(-w_g * 0.55, w_g * 0.55, ug + gw / 2, ug + gw / 2 + 3.5, top_g + PROUD,
                                    top_g + PROUD + 1.0))

    if opts.get("breast"):
        # breast collar: across the chest above the forelegs, round to the sides
        w_f, bot_f, top_f = section(u_max - 1)
        arm_top = max(b[1][1] for b in fl)
        ub0 = min(u_max - 0.2, arm_top + 0.3)
        o = w_f + PROUD
        add(body_name, "pad", f.box(-o - BAND, o + BAND, ub0, ub0 + 1.4, bot_f - PROUD - BAND, bot_f - PROUD))
        for s_ in (-1, 1):
            add(body_name, "leather", f.box(*sorted((s_ * o, s_ * (o + STRAP))), ug, ub0 + 1.4, bot_f, bot_f + 1.2))
    else:
        # collar on the neck: its cube nearest the body, ringed a little up from the end that meets the body
        neck = parts[neck_name]
        body_centre = body.to_root([sum(c) / 2 for c in zip(*bounds([p for o, s in body.cubes for p in corners(o, s)]))])
        origin, size = min(neck.cubes, key=lambda c: dist(neck.to_root([o + s / 2 for o, s in zip(*c)]), body_centre))
        centre = [o + s / 2 for o, s in zip(origin, size)]
        toward = neck.dir_from_root([a - b for a, b in zip(neck.to_root(centre), body_centre)])
        # the neck's length: its cube's long side if it has one, else the way from the body to it
        longest = max(range(3), key=lambda i: size[i])
        if all(size[longest] >= 1.3 * size[i] for i in range(3) if i != longest):
            along = longest
        else:
            along = max(range(3), key=lambda i: abs(toward[i]))
        sign = 1 if toward[along] > 0 else -1
        base = origin[along] if sign > 0 else origin[along] + size[along]  # the end nearest the body
        a0, a1 = sorted((base + sign * 1.0, base + sign * 3.5))
        lo = [origin[i] - PROUD for i in range(3)]
        hi = [origin[i] + size[i] + PROUD for i in range(3)]
        cross = [i for i in range(3) if i != along]
        pad = min(1.4, 0.3 * min(size[i] for i in cross))
        for i in cross:
            for side in (0, 1):
                b_lo, b_hi = list(lo), list(hi)
                b_lo[along], b_hi[along] = a0, a1
                j = [k for k in cross if k != i][0]
                b_lo[j], b_hi[j] = lo[j] - pad, hi[j] + pad
                if side == 0:
                    b_lo[i], b_hi[i] = lo[i] - pad, lo[i]
                else:
                    b_lo[i], b_hi[i] = hi[i], hi[i] + pad
                add(neck_name, "pad", [round(v, 2) for v in b_lo + b_hi])
        # hames: brass bars on the collar's two sides (the lateral axis is x for every model here)
        for side in (0, 1):
            b_lo, b_hi = [0.0] * 3, [0.0] * 3
            x = lo[0] - pad - 0.5 if side == 0 else hi[0] + pad
            b_lo[0], b_hi[0] = x, x + 0.5
            b_lo[along], b_hi[along] = a0 + 0.6, a1 - 0.6
            k = [c for c in cross if c != 0][0]
            span = hi[k] - lo[k]
            b_lo[k], b_hi[k] = lo[k] + 0.2 * span, hi[k] - 0.2 * span  # along the collar's side, inside its ring
            add(neck_name, "brass", [round(v, 2) for v in b_lo + b_hi])

    # where the ropes end: the tug ring's middle, in blocks in the animal's frame at rest
    o = w_g + PROUD + STRAP + 0.45
    ring = f.box(o, o, ug - gw / 2 - 1.3, ug - gw / 2 - 1.3, hs, hs)[:3]
    root = body.to_root(ring)
    anchor = [round(abs(root[0]) / 16 * scale, 3), round((24 - root[1]) / 16 * scale, 3),
              round(-root[2] / 16 * scale, 3)]
    return pieces, anchor


def dist(a, b):
    return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)))


def write(name, kinds, pieces, anchor):
    path = OUT / f"{name}.json"
    old = json.loads(path.read_text()) if path.exists() else {}
    d = {"entities": [f"lotr_port:{k}" for k in kinds], "rope_anchor": anchor,
         "rope_color": old.get("rope_color", "#5b3f26")}
    for k in ("body", "collar"):
        if k in old:
            d[k] = old[k]
    d["pieces"] = pieces
    s = json.dumps(d, indent=2)
    s = re.sub(r"\[\n\s+([^\[\]{}]*?)\n\s+\]", lambda m: "[" + re.sub(r",\n\s+", ", ", m.group(1)) + "]", s)
    s = re.sub(r"\{\n\s+(\"part\"[^{}]*?)\n\s+\}", lambda m: "{" + re.sub(r",\n\s+", ", ", m.group(1)) + "}", s)
    path.write_text(s + "\n")


if __name__ == "__main__":
    for name, (model, kinds, body, neck, front, hind, scale) in ANIMALS.items():
        pieces, anchor = fit(name, model, body, neck, front, hind, scale)
        write(name, kinds, pieces, anchor)
        print(f"{name}: {len(pieces)} pieces, ropes at {anchor}")
