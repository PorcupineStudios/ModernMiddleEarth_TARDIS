"""
The console's six themed sides and the small controls on them (Aaron, 2026-11-08: "Change the tardis console ... to
have a plain texture. It should just be a blank slate for us to add the controls on top. Then add the controls on
top, complete with hit boxes" and "each side of the console should have a theme and the monitor should say
different things depending on what console side its at").

Run from the repo root after tools/capaldi.py and tools/flight_controls.py: python3 tardis/tools/console_layout.py

Writes:
  data/tardis_rb/tardis/control/<id>.json   one per control: model, panel, u/v/turn on the panel, what each
                                            clickable part does, gauges' values, requirements (old files removed)
  assets/tardis_rb/lang/en_us.json          control, action and side names (for Jade and messages)
  tools/console_layout/side_<k>.png         each side drawn with numbered controls and what each one is for
  tools/console_layout/overview.png         the whole console from above

Panel coordinates: `u` blocks to the right of the panel's middle (as its operator sees it), `v` blocks up the slope
from its outer edge, `turn` degrees round the panel's normal. Panel k faces 90 - 60k degrees (atan2(z, x)) from the
rotor axis; panel 0 faces the door.
"""
import glob
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import flight_controls as fc  # noqa: E402

RES = os.path.join(HERE, "..", "src", "main", "resources")
ASSETS = os.path.join(RES, "assets", "tardis_rb")
CDIR = os.path.join(RES, "data", "tardis_rb", "tardis", "control")
OUT = os.path.join(HERE, "console_layout")

NAV1 = {"type": "tardis_rb:subsystem_level", "subsystem": "tardis_rb:navigation", "min": 1}
CORE = {"type": "tardis_rb:milestone", "milestone": "tardis_rb:restored_core"}

TUNING = json.load(open(os.path.join(RES, "data", "tardis_rb", "tardis", "tuning", "main.json")))["console"]


def slot_text(axis, k, slot):
    """What one coordinate control does, from the tuning's coordinate rules."""
    rng = f"{slot['min']} to {slot['max']}"
    if slot.get("cycle"):
        return f"{axis} power wheel ({rng}, goes round)"
    if "power" in slot:
        return f"{axis} lever: {rng}, x {slot['base']} to the power (wheel {slot['power'] - 3} + 1)"
    return f"{axis} lever: {rng}, x {slot.get('times', 1)}"


# What each action does, in a few words (lang tardis_rb.action.<id>; also the screenshots' legend).
ACTIONS = {
    "screen": "Says where the TARDIS is",
    "relative": "Relative coordinates on/off (on: X and Z count from where the TARDIS stands)",
    "land": "Land on the ground on/off (on: Y is ignored, it lands on the surface)",
    "save": "Save mode on/off (on: a memory button saves the coordinates instead of loading them)",
    "memory": "Loads (or in save mode saves) the coordinate controls",
    "facing": "Turns the way the doors face on landing (sneak: back)",
    "destination": "Picks the dimension to travel to (sneak: back)",
    "random": "Sets the coordinate controls at random",
    "jump": "Flies to the set coordinates (a hop, or a jump to the picked dimension)",
    "handbrake": "Handbrake on/off (on: the TARDIS can't take off)",
    "door": "Opens or closes the doors",
    "none": "Moves only (planned)",
    "time_from": "Target time counts back from now (12 minutes ago) or, flipped, from the beginning of time",
    "time_unit": "Picks what the time drums count: " + ", ".join(u["name"] for u in TUNING["time"]["units"])
                 + " (sneak: back)",
}
for _k in range(TUNING["time"]["digits"]):
    ACTIONS[f"time{_k}"] = f"Time digit {_k + 1} of {TUNING['time']['digits']}: 0 to 9 (sneak: down)"
for _k, _slot in enumerate(TUNING["coordinates"]["xz"]):
    ACTIONS[f"x{_k}"] = slot_text("X", _k, _slot) + " (sneak: down)"
    ACTIONS[f"z{_k}"] = slot_text("Z", _k, _slot) + " (sneak: down)"
for _k, _slot in enumerate(TUNING["coordinates"]["y"]):
    ACTIONS[f"y{_k}"] = f"Y digit {_k + 1}: {_slot['min']} to {_slot['max']}, x {_slot['times']} (sneak: down)"


def axis_side(a, theme, monitor, extra):
    """An X or Z side, after the 1.7.10 console: four levers, two power wheels, the big x71 lever."""
    lev = [(f"{a}_lever_{n + 1}", "coordinate_lever", {"lever": f"{a}{n}"}, NAV1) for n in range(4)]
    wheels = [(f"{a}_wheel_{n + 1}", "valve_wheel", {"wheel": f"{a}{n + 4}"}, NAV1) for n in range(2)]
    big = (f"{a}_lever_5", "coordinate_lever", {"lever": f"{a}6"}, NAV1)
    return {"theme": theme, "monitor": monitor, "rows": [(0.18, lev + [big]), (0.46, wheels + extra)]}


# The six sides (Aaron 2026-11-08: "1. X coords (see tardis mod 1.7.10 for the XYZ coord controls) 2. Z coords
# 3. Dimension + Time controls 4. Exterior controls (facing direction, dematerialization, entity, physics object)
# 5. Y + save/load coord controls 6. Room controls", plus a few exceptions on each). Side 0 faces the door. Rows go
# from the operator's edge up toward the rotor: (v, [(id, model, {click part: action}, requirements)]), spaced
# evenly along the row.
SIDES = [
    axis_side("x", "X coordinates", "e.g. \"Target X 1210\", \"(here +10)\", \"Now X 1200\"",
              [("relative_switch", "toggle_switch", {"bat": "relative"}, NAV1)]),
    axis_side("z", "Z coordinates", "e.g. \"Target Z -340\", \"(absolute)\", \"Now Z -52\"",
              [("randomiser", "randomiser", {"die": "random"}, NAV1)]),
    {"theme": "Dimension & time", "monitor":
     "e.g. \"Target Middle-earth[lotr_port:middle_earth]\", \"Target time: 12 minutes ago\"",
     "rows": [(0.22, [("dimension_dial", "dimension_dial", {"knob": "destination"}, CORE),
                      ("time_lever", "time_lever", {"lever": "time_from"}, NAV1),
                      ("time_unit_dial", "time_unit_dial", {"pointer": "time_unit"}, NAV1),
                      ("fast_return", "fast_return", {}, NAV1)]),
              (0.6, [("time_drums", "time_drums", {f"drum_{n + 1}": f"time{n}" for n in range(3)}, NAV1),
                     ("telepathic_circuits", "telepathic_circuits", {}, NAV1)])]},
    {"theme": "Exterior", "monitor": "landed or not, facing, handbrake, charge, doors",
     "rows": [(0.2, [("facing_compass", "facing_compass", {"arrow": "facing"}, NAV1),
                     ("demat_lever", "demat_lever", {"arm": "jump"}, NAV1),
                     ("handbrake", "handbrake", {"arm": "handbrake"}, NAV1)]),
              (0.53, [("phase_key", "phase_key", {}, NAV1), ("phase_lever", "phase_lever", {}, NAV1),
                      ("thrusters", "thrusters", {}, NAV1)]),
              (0.75, [("flight_yoke", "flight_yoke", {}, NAV1)])]},
    {"theme": "Y & memory", "monitor":
     "e.g. \"Target Y 70\" (or \"Target Y: the ground\"), land on ground, save mode, saved slots",
     "rows": [(0.2, [(f"y_lever_{n + 1}", "y_lever", {"lever": f"y{n}"}, NAV1) for n in range(4)]
               + [("land_switch", "toggle_switch", {"bat": "land"}, NAV1)]),
              (0.58, [("memory_bank", "memory_bank", {f"slot_{n}": "memory" for n in range(20)}, NAV1),
                      ("save_switch", "push_switch", {"rod": "save"}, NAV1)])]},
    {"theme": "Rooms", "monitor": "rooms used of the cap, crew, doors",
     "rows": [(0.22, [("door_control", "door_control", {"wheel": "door"}, NAV1),
                      ("schema_chooser", "schema_chooser", {}, NAV1),
                      ("scanner_screen", "scanner_screen", {"screen": "screen"}, NAV1)]),
              (0.58, [("room_deleter", "room_deleter", {}, NAV1),
                      ("screwdriver_holder", "screwdriver_holder", {}, NAV1),
                      ("twin_gauge", "twin_gauge", {}, None)])]},
]
# Controls that stand bigger or smaller than SCALE.
SCALES = {"x_lever_5": 1.0, "z_lever_5": 1.0, "flight_yoke": 0.65}
# Gauges show these values (0..1 of full), one per needle in the model's order.
SHOWS = {"gauge": ["charge"], "twin_gauge": ["charge", "rooms"]}
SCALE = 0.8       # the controls stand a little smaller on the console than modelled
GAP = 0.04
MARGIN = 0.07     # kept clear of the panel's side ridges
V_MAX = 0.84      # the panel's top ends where the rotor's collar starts


def click_name(click):
    """A clickable part's name for players (lang tardis_rb.click.<part>)."""
    fixed = {"wheel_x": "X wheel", "wheel_y": "Y wheel", "wheel_z": "Z wheel", "left": "Left switch",
             "right": "Right switch"}
    if click in fixed:
        return fixed[click]
    head, _, n = click.rpartition("_")
    if n.isdigit():
        return {"slot": "Slot", "btn": "Button", "lever": "Lever"}.get(head, head.title()) + " " + str(
            int(n) + (1 if head == "slot" else 0))
    return click.replace("_", " ").capitalize()


def display_name(cid, models):
    """A placed control's name for players (lang tardis_rb.control.<id>): the model's, numbered where it repeats."""
    head, _, n = cid.rpartition("_")
    if head in ("x_lever", "z_lever", "x_wheel", "z_wheel", "y_lever") and n.isdigit():
        axis, kind = head.split("_")
        return f"{axis.upper()} {'Power Wheel' if kind == 'wheel' else 'Lever'} {n}"
    named = {"relative_switch": "Relative Coordinates Switch", "land_switch": "Land on Ground Switch",
             "save_switch": "Memory Save Switch"}
    if cid in named:
        return named[cid]
    return models[cid]["name"] if cid in models else cid


def load_console():
    return json.load(open(os.path.join(ASSETS, "console", "capaldi.json")))


def load_models():
    out = {}
    for f in glob.glob(os.path.join(ASSETS, "console", "controls", "*.json")):
        out[os.path.basename(f)[:-5]] = json.load(open(f))
    return out


def footprint(model, scale=None):
    """The model's rest-pose extent across (x) and along (z) its panel, in blocks."""
    k = SCALE if scale is None else scale
    pts = [v for p in model["parts"].values() for q in p["quads"] for v in q["v"]]
    return ((max(p[0] for p in pts) - min(p[0] for p in pts)) * k,
            (max(p[2] for p in pts) - min(p[2] for p in pts)) * k)


def half_width(v):
    r = 1.31 - v * math.cos(math.radians(21.6))
    return r * math.tan(math.radians(30)) - MARGIN


def fit_side(k, side, models, f):
    """Places one side's rows, each just above the last, at scale factor f; returns (placed, problems)."""
    placed, problems, rects = [], [], []
    start = 0.03
    for _, row in side["rows"]:
        sizes = [footprint(models[m], SCALES.get(cid, SCALE) * f) for cid, m, _, _ in row]
        depth = max(d for _, d in sizes)
        v = start + depth / 2
        start += depth + GAP
        total = sum(w for w, _ in sizes) + GAP * (len(row) - 1)
        u = -total / 2
        for (cid, m, acts, req), (w, d) in zip(row, sizes):
            cu = u + w / 2
            u += w + GAP
            if abs(cu) + w / 2 > half_width(v + d / 2) + 1e-6:
                problems.append(f"side {k} {cid}: off the panel's side")
            if v + d / 2 > V_MAX:
                problems.append(f"side {k} {cid}: past the panel's top ({v + d / 2:.2f})")
            r = (cu - w / 2, v - d / 2, cu + w / 2, v + d / 2)
            for o, om in rects:
                if r[0] < o[2] - 0.005 and o[0] < r[2] - 0.005 and r[1] < o[3] - 0.005 and o[1] < r[3] - 0.005:
                    problems.append(f"side {k}: {cid} overlaps {om}")
            rects.append((r, cid))
            placed.append((k, cid, m, acts, req, round(cu, 4), round(v, 4), round(SCALES.get(cid, SCALE) * f, 3)))
    return placed, problems


def layout(models):
    """Every control's place: [(side, id, model, actions, requirements, u, v, scale)]. Each side's rows are stacked
    from the operator's edge; a side that doesn't fit is shrunk a little at a time (not below 0.6 of its size)."""
    placed = []
    for k, side in enumerate(SIDES):
        f = 1.0
        while True:
            got, problems = fit_side(k, side, models, f)
            if not problems:
                break
            if f <= 0.6:
                raise SystemExit("layout doesn't fit:\n  " + "\n  ".join(problems))
            f = round(f - 0.05, 2)
        placed.extend(got)
    return placed


def write_controls(placed, models):
    os.makedirs(CDIR, exist_ok=True)
    for f in glob.glob(os.path.join(CDIR, "*.json")):
        os.remove(f)
    for k, cid, model, acts, req, u, v, sc in placed:
        clicks = [a["click"] for a in models[model]["actions"] if a["click"]]
        data = {"model": model, "panel": k, "u": u, "v": v, "scale": sc,
                "actions": {c: acts.get(c, "none") for c in clicks}}
        if model in SHOWS:
            data["shows"] = SHOWS[model]
        if req:
            data["requirements"] = req
        write(os.path.join(CDIR, cid + ".json"), data)
    # the monitor is the console's own (it stands over whichever side it was turned to)
    write(os.path.join(CDIR, "monitor.json"), {"model": "monitor", "actions": {"screen": "screen"}})


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def write_lang(models, placed):
    path = os.path.join(ASSETS, "lang", "en_us.json")
    data = json.load(open(path))
    for key in [k for k in data if k.startswith(("tardis_rb.control.", "tardis_rb.action.", "tardis_rb.side.",
                                                       "tardis_rb.click."))]:
        del data[key]
    for mid, m in sorted(models.items()):
        data["tardis_rb.control." + mid] = m["name"]
    for p in placed:
        data["tardis_rb.control." + p[1]] = display_name(p[1], models)
    data["tardis_rb.control.monitor"] = "Monitor"
    for m in models.values():
        clicks = [a["click"] for a in m["actions"] if a["click"]]
        if len(clicks) > 1:
            for c in clicks:
                data["tardis_rb.click." + c] = click_name(c)
    for a, text in ACTIONS.items():
        data["tardis_rb.action." + a] = text
    for k, side in enumerate(SIDES):
        data["tardis_rb.side." + str(k)] = side["theme"]
    write(path, data)


# ---------------------------------------------------------------- pictures

def frame(console, k):
    p = console["panels"][k]
    return [p[n] for n in ("origin", "right", "normal", "down")]


def place(pt, fr, u, v, k=SCALE):
    o, r, n, d = fr
    return [o[i] + r[i] * (u + pt[0] * k) + n[i] * pt[1] * k + d[i] * (pt[2] * k - v) for i in range(3)]


def monitor_quads(console, k):
    """The monitor's quads (blocks) standing over side k: tilted up, lifted and moved in, then turned round."""
    mon = console["monitor"]
    quads = console["parts"]["ctl_screen_1"]
    pts = [v for q in quads for v in q["v"]]
    c = [sum(p[i] for p in pts) / len(pts) for i in range(3)]
    px, pz = console["pivot"][0], console["pivot"][2]
    out = [c[0] - px, 0, c[2] - pz]
    ln = math.hypot(out[0], out[2])
    out = [out[0] / ln, 0, out[2] / ln]
    axis = [out[2], 0, -out[0]]  # up x out: turns the glass's normal toward the outside
    home = math.degrees(math.atan2(c[2] - pz, c[0] - px))
    target = 90 - 60 * k
    def area(q):
        a = [q["v"][1][i] - q["v"][0][i] for i in range(3)]
        b = [q["v"][3][i] - q["v"][0][i] for i in range(3)]
        return math.sqrt(sum(x * x for x in (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                                              a[0] * b[1] - a[1] * b[0])))
    big = max(area(q) for q in quads)
    glass = max((q for q in quads if area(q) > big * 0.4), key=lambda q: sum(v[1] for v in q["v"]))
    res = []
    for q in quads:
        vs = []
        for v in q["v"]:
            p = fc.rotate(v[:3], axis, mon["tilt"], c)
            p = [p[0] - out[0] * mon["inward"], p[1] + mon["lift"], p[2] - out[2] * mon["inward"]]
            p = fc.rotate(p, "y", home - target, (px, 0, pz))
            vs.append(p)
        res.append((vs, q is glass))
    return res


def scene(console, models, placed, side):
    """Every triangle to draw: (three points in blocks, colour, glowing)."""
    tris = []
    plain = (154, 149, 140)
    for q in console["parts"]["body"]:
        vs = [v[:3] for v in q["v"]]
        tris.append((vs, plain, False))
    for name in ("rotor_glass", "rotor_column"):
        for q in console["parts"][name]:
            if max(v[1] for v in q["v"]) > 2.4:
                continue
            tris.append(([v[:3] for v in q["v"]], (120, 170, 190) if name == "rotor_glass" else (90, 92, 100),
                         False))
    for vs, is_glass in monitor_quads(console, side):
        tris.append((vs, (70, 150, 180) if is_glass else (40, 44, 50), is_glass))
    for k, cid, model, acts, req, u, v, sc in placed:
        c = next(x for x in fc.CONTROLS if x.id == model)
        fr = frame(console, k)
        transforms, lit = fc.pose(c, "off")
        for name, p in c.parts.items():
            t = transforms.get(name, [])
            for b in p["boxes"]:
                for pts, n, colour in fc.quads_of(b):
                    pts = [fc.apply(pt, t, p["pivot"]) for pt in pts]
                    pts = [place([x / 16 for x in pt], fr, u, v, sc) for pt in pts]
                    rgb = fc.COLOURS[colour]
                    if colour in fc.GLOWS and name not in lit:
                        rgb = tuple(int(ch * 0.4) for ch in rgb)
                    tris.append((pts, rgb, colour in fc.GLOWS))
    return tris


def render(tris, eye, target, w, h, fov=34, ss=2):
    import numpy as np
    W, H = w * ss, h * ss
    img = np.zeros((H, W, 3), dtype=np.float32)
    img[:] = (24, 26, 32)
    zbuf = np.full((H, W), 1e9, dtype=np.float32)
    f = [target[i] - eye[i] for i in range(3)]
    fl = math.sqrt(sum(x * x for x in f))
    f = [x / fl for x in f]
    r = [f[2], 0, -f[0]]
    rl = math.sqrt(sum(x * x for x in r))
    r = [-x / rl for x in r]
    up = [r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]]
    focal = (H / 2) / math.tan(math.radians(fov / 2))
    light = (0.35, 0.85, 0.4)
    ys, xs = np.mgrid[0:H, 0:W].astype(np.float32) + 0.5

    def project(p):
        d = [p[i] - eye[i] for i in range(3)]
        z = sum(d[i] * f[i] for i in range(3))
        x = sum(d[i] * r[i] for i in range(3))
        y = sum(d[i] * up[i] for i in range(3))
        z = max(z, 0.01)
        return (W / 2 + x / z * focal, H / 2 - y / z * focal, z)

    for pts, rgb, glow in tris:
        a = [pts[1][i] - pts[0][i] for i in range(3)]
        b = [pts[2][i] - pts[0][i] for i in range(3)]
        n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
        nl = math.sqrt(sum(x * x for x in n)) or 1
        n = [x / nl for x in n]
        to_eye = [eye[i] - pts[0][i] for i in range(3)]
        if sum(n[i] * to_eye[i] for i in range(3)) < 0:
            n = [-x for x in n]
        k = 1.0 if glow else 0.45 + 0.55 * max(0, sum(n[i] * light[i] for i in range(3)))
        col = np.array([ch * k for ch in rgb], dtype=np.float32)
        proj = [project(p) for p in pts]
        for tri in ((proj[0], proj[1], proj[2]), (proj[0], proj[2], proj[3])) if len(proj) == 4 else (proj,):
            (x0, y0, z0), (x1, y1, z1), (x2, y2, z2) = tri
            area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
            if abs(area) < 1e-6:
                continue
            bx0, bx1 = max(0, int(min(x0, x1, x2))), min(W, int(max(x0, x1, x2)) + 1)
            by0, by1 = max(0, int(min(y0, y1, y2))), min(H, int(max(y0, y1, y2)) + 1)
            if bx0 >= bx1 or by0 >= by1:
                continue
            px, py = xs[by0:by1, bx0:bx1], ys[by0:by1, bx0:bx1]
            w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) / area
            w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) / area
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            depth = 1 / (w0 / z0 + w1 / z1 + w2 / z2)
            zb = zbuf[by0:by1, bx0:bx1]
            m = inside & (depth < zb - 1e-5)
            zb[m] = depth[m]
            img[by0:by1, bx0:bx1][m] = col
    out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8), "RGB").resize((w, h), Image.LANCZOS)
    return out, lambda p: tuple(c / ss for c in project(p)[:2])


def fonts():
    try:
        return (ImageFont.truetype("DejaVuSans-Bold.ttf", 20), ImageFont.truetype("DejaVuSans.ttf", 14),
                ImageFont.truetype("DejaVuSans-Bold.ttf", 14), ImageFont.truetype("DejaVuSans.ttf", 12))
    except OSError:
        f = ImageFont.load_default()
        return f, f, f, f


def wrap(d, text, font, width):
    words, lines, line = text.split(), [], ""
    for wd in words:
        t = (line + " " + wd).strip()
        if d.textlength(t, font=font) > width and line:
            lines.append(line)
            line = wd
        else:
            line = t
    return lines + [line] if line else lines


def side_picture(console, models, placed, k, info):
    big, body, bold, small = fonts()
    a = math.radians(90 - 60 * k)
    px, pz = console["pivot"][0], console["pivot"][2]
    eye = [px + math.cos(a) * 3.0, 2.3, pz + math.sin(a) * 2.9]
    target = [px + math.cos(a) * 0.7, 1.2, pz + math.sin(a) * 0.75]
    pic, proj = render(scene(console, models, placed, k), eye, target, 760, 600)
    sheet = Image.new("RGB", (1360, 600), (16, 17, 22))
    sheet.paste(pic, (0, 0))
    d = ImageDraw.Draw(sheet)
    mine = [p for p in placed if p[0] == k]
    for i, (_, cid, model, acts, req, u, v, sc) in enumerate(mine):
        top = place([0, 0.14, 0], frame(console, k), u, v)
        x, y = proj(top)
        d.ellipse((x - 11, y - 11, x + 11, y + 11), fill=(240, 200, 80), outline=(0, 0, 0))
        d.text((x, y), str(i + 1), fill=(0, 0, 0), font=bold, anchor="mm")
    d.text((780, 14), f"Side {k}: {SIDES[k]['theme']}", fill=(250, 235, 190), font=big)
    y = 42
    for line in wrap(d, "Monitor here shows: " + SIDES[k]["monitor"], small, 560):
        d.text((780, y), line, fill=(150, 210, 240), font=small)
        y += 16
    y += 12
    for i, (_, cid, model, acts, req, u, v, sc) in enumerate(mine):
        m = models[model]
        clicks = [x["click"] for x in m["actions"] if x["click"]]
        does = [ACTIONS[acts.get(c, "none")] for c in clicks] or ["Shows " + " and ".join(SHOWS.get(model, []))]
        works = any(acts.get(c, "none") != "none" for c in clicks) or model in SHOWS
        tag = "works now" if works else "planned"
        d.text((780, y), f"{i + 1}. {display_name(cid, models)}", fill=(240, 230, 200), font=bold)
        d.text((1340, y), tag, fill=(120, 220, 140) if works else (170, 170, 180), font=small, anchor="ra")
        y += 18
        planned = next(c["does"] for c in json.load(open(os.path.join(fc.OUT, "controls.json"))) if c["id"] == model)
        text = "; ".join(sorted(set(does))) if works else "Planned: " + planned
        if len(clicks) > 1 and works and len({acts.get(c, "none") for c in clicks}) > 1:
            text = ", ".join(f"{click_name(c)}: {ACTIONS[acts.get(c, 'none')]}" for c in clicks)
        for line in wrap(d, text, small, 550):
            d.text((796, y), line, fill=(185, 185, 195), font=small)
            y += 15
        y += 6
    return sheet


def overview(console, models, placed):
    px, pz = console["pivot"][0], console["pivot"][2]
    pic, proj = render(scene(console, models, placed, 1), [px + 0.01, 5.6, pz + 2.6], [px, 0.9, pz], 900, 760)
    d = ImageDraw.Draw(pic)
    big, body, bold, small = fonts()
    for k, side in enumerate(SIDES):
        a = math.radians(90 - 60 * k)
        x, y = proj([px + math.cos(a) * 1.75, 0.8, pz + math.sin(a) * 1.75])
        d.text((x, y), f"{k}: {side['theme']}", fill=(250, 235, 190), font=bold, anchor="mm")
    d.text((10, 10), "The console from above (door side at the bottom)", fill=(200, 200, 210), font=body)
    return pic


def main():
    console = load_console()
    models = load_models()
    placed = layout(models)
    write_controls(placed, models)
    write_lang(models, placed)
    os.makedirs(OUT, exist_ok=True)
    for k in range(6):
        side_picture(console, models, placed, k, SIDES[k]).save(os.path.join(OUT, f"side_{k}.png"))
    overview(console, models, placed).save(os.path.join(OUT, "overview.png"))
    print(len(placed), "controls placed")


if __name__ == "__main__":
    main()
