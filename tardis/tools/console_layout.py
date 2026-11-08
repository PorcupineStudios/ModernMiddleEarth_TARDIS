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

# What each action does, in a few words (lang tardis_rb.action.<id>; also the screenshots' legend).
ACTIONS = {
    "screen": "Says where the TARDIS is",
    "x": "Course along X by the step (sneak: back)",
    "y": "Course along Y by the step (sneak: back)",
    "z": "Course along Z by the step (sneak: back)",
    "step": "Changes the step size",
    "reset": "Clears the course",
    "hop": "Flies a short hop along the course",
    "destination": "Picks the next open destination",
    "random": "Picks a random open destination",
    "jump": "Jumps to the picked destination",
    "handbrake": "Handbrake on/off (on: no hop or jump)",
    "door": "Opens or closes the doors",
    "none": "Moves only (planned)",
}

# The six sides (Aaron: each side has a theme). Rows go from the operator's edge up toward the rotor: (v, [controls]);
# a control is (model, {click part: action}, requirements, turn) and is spaced evenly along its row.
SIDES = [
    {"theme": "Flight", "monitor": "flight state, handbrake, charge, the picked destination",
     "rows": [(0.22, [("handbrake", {"arm": "handbrake"}, NAV1), ("demat_lever", {"arm": "jump"}, CORE),
                      ("takeoff_lever", {"lever": "hop"}, NAV1), ("throttle", {}, NAV1)]),
              (0.66, [("speed_lever", {}, NAV1), ("flight_button", {}, NAV1)])]},
    {"theme": "Navigation", "monitor": "where the TARDIS is, the course and the step size",
     "rows": [(0.2, [("coordinate_wheels", {"wheel_x": "x", "wheel_y": "y", "wheel_z": "z"}, NAV1),
                     ("valve_wheel", {"wheel": "step"}, NAV1), ("coordinate_lever", {"lever": "reset"}, NAV1)]),
              (0.56, [("directional_pointer", {}, NAV1), ("vector_tracker", {}, NAV1)])]},
    {"theme": "Exterior", "monitor": "the exterior's state (landed, loose, hovering), dimension and height",
     "rows": [(0.22, [("phase_key", {}, NAV1), ("phase_lever", {}, NAV1), ("thrusters", {}, NAV1)]),
              (0.58, [("stabilisers", {}, NAV1), ("flight_yoke", {}, NAV1)])]},
    {"theme": "Destinations", "monitor": "the picked destination and every open one",
     "rows": [(0.22, [("telepathic_circuits", {"plate": "destination"}, CORE), ("memory_bank", {}, NAV1)]),
              (0.6, [("randomiser", {"die": "random"}, CORE), ("fast_return", {}, NAV1)])]},
    {"theme": "Systems", "monitor": "charge, every subsystem's repair level and the stage",
     "rows": [(0.16, [("gauge", {}, None), ("twin_gauge", {}, NAV1), ("console_lamp", {}, NAV1)]),
              (0.48, [("helmic_regulator", {}, NAV1), ("push_switch", {}, NAV1), ("drift_lever", {}, NAV1)]),
              (0.74, [("sub_levers", {}, NAV1)])]},
    {"theme": "Interior & crew", "monitor": "crew, rooms used of the cap, the doors",
     "rows": [(0.22, [("door_control", {"wheel": "door"}, NAV1), ("schema_chooser", {}, NAV1),
                      ("scanner_screen", {"screen": "screen"}, NAV1)]),
              (0.6, [("room_deleter", {}, NAV1), ("screwdriver_holder", {}, NAV1)])]},
]
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


def load_console():
    return json.load(open(os.path.join(ASSETS, "console", "capaldi.json")))


def load_models():
    out = {}
    for f in glob.glob(os.path.join(ASSETS, "console", "controls", "*.json")):
        out[os.path.basename(f)[:-5]] = json.load(open(f))
    return out


def footprint(model):
    """The model's rest-pose extent across (x) and along (z) its panel, in blocks."""
    pts = [v for p in model["parts"].values() for q in p["quads"] for v in q["v"]]
    return ((max(p[0] for p in pts) - min(p[0] for p in pts)) * SCALE,
            (max(p[2] for p in pts) - min(p[2] for p in pts)) * SCALE)


def half_width(v):
    r = 1.31 - v * math.cos(math.radians(21.6))
    return r * math.tan(math.radians(30)) - MARGIN


def layout(models):
    """Every control's place: [(side, id, model, actions, requirements, u, v)]; fails if anything doesn't fit."""
    placed = []
    problems = []
    for k, side in enumerate(SIDES):
        rects = []
        for v, row in side["rows"]:
            widths = [footprint(models[m])[0] for m, _, _ in row]
            total = sum(widths) + GAP * (len(row) - 1)
            u = -total / 2
            for (m, acts, req), w in zip(row, widths):
                d = footprint(models[m])[1]
                cu = u + w / 2
                u += w + GAP
                for vv in (v - d / 2, v + d / 2):
                    if abs(cu) + w / 2 > half_width(vv) + 1e-6:
                        problems.append(f"side {k} {m}: off the panel's side at v {vv:.2f}")
                if v - d / 2 < 0.02 or v + d / 2 > V_MAX:
                    problems.append(f"side {k} {m}: off the panel's edge ({v - d / 2:.2f}..{v + d / 2:.2f})")
                r = (cu - w / 2, v - d / 2, cu + w / 2, v + d / 2)
                for o, om in rects:
                    if r[0] < o[2] + 0.01 and o[0] < r[2] - 0.01 and r[1] < o[3] + 0.01 and o[1] < r[3] - 0.01:
                        problems.append(f"side {k}: {m} overlaps {om}")
                rects.append((r, m))
                placed.append((k, m, m, acts, req, round(cu, 4), round(v, 4)))
    if problems:
        raise SystemExit("layout doesn't fit:\n  " + "\n  ".join(problems))
    return placed


def write_controls(placed, models):
    os.makedirs(CDIR, exist_ok=True)
    for f in glob.glob(os.path.join(CDIR, "*.json")):
        os.remove(f)
    for k, cid, model, acts, req, u, v in placed:
        clicks = [a["click"] for a in models[model]["actions"] if a["click"]]
        data = {"model": model, "panel": k, "u": u, "v": v, "scale": SCALE,
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


def write_lang(models):
    path = os.path.join(ASSETS, "lang", "en_us.json")
    data = json.load(open(path))
    for key in [k for k in data if k.startswith(("tardis_rb.control.", "tardis_rb.action.", "tardis_rb.side.",
                                                       "tardis_rb.click."))]:
        del data[key]
    for mid, m in sorted(models.items()):
        data["tardis_rb.control." + mid] = m["name"]
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


def place(pt, fr, u, v):
    o, r, n, d = fr
    return [o[i] + r[i] * (u + pt[0] * SCALE) + n[i] * pt[1] * SCALE + d[i] * (pt[2] * SCALE - v) for i in range(3)]


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
    for k, cid, model, acts, req, u, v in placed:
        c = next(x for x in fc.CONTROLS if x.id == model)
        fr = frame(console, k)
        transforms, lit = fc.pose(c, "off")
        for name, p in c.parts.items():
            t = transforms.get(name, [])
            for b in p["boxes"]:
                for pts, n, colour in fc.quads_of(b):
                    pts = [fc.apply(pt, t, p["pivot"]) for pt in pts]
                    pts = [place([x / 16 for x in pt], fr, u, v) for pt in pts]
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
    eye = [px + math.cos(a) * 2.6, 2.05, pz + math.sin(a) * 2.6]
    target = [px + math.cos(a) * 0.6, 1.2, pz + math.sin(a) * 0.6]
    pic, proj = render(scene(console, models, placed, k), eye, target, 760, 600)
    sheet = Image.new("RGB", (1360, 600), (16, 17, 22))
    sheet.paste(pic, (0, 0))
    d = ImageDraw.Draw(sheet)
    mine = [p for p in placed if p[0] == k]
    for i, (_, cid, model, acts, req, u, v) in enumerate(mine):
        top = place([0, 0.14, 0], frame(console, k), u, v)
        x, y = proj(top)
        d.ellipse((x - 11, y - 11, x + 11, y + 11), fill=(240, 200, 80), outline=(0, 0, 0))
        d.text((x, y), str(i + 1), fill=(0, 0, 0), font=bold, anchor="mm")
    d.text((780, 14), f"Side {k}: {SIDES[k]['theme']}", fill=(250, 235, 190), font=big)
    d.text((780, 42), "Monitor here shows: " + SIDES[k]["monitor"], fill=(150, 210, 240), font=small)
    y = 70
    for i, (_, cid, model, acts, req, u, v) in enumerate(mine):
        m = models[model]
        clicks = [x["click"] for x in m["actions"] if x["click"]]
        does = [ACTIONS[acts.get(c, "none")] for c in clicks] or ["Shows " + " and ".join(SHOWS.get(model, []))]
        works = any(acts.get(c, "none") != "none" for c in clicks) or model in SHOWS
        tag = "works now" if works else "planned"
        d.text((780, y), f"{i + 1}. {m['name']}", fill=(240, 230, 200), font=bold)
        d.text((1340, y), tag, fill=(120, 220, 140) if works else (170, 170, 180), font=small, anchor="ra")
        y += 18
        planned = next(c["does"] for c in json.load(open(os.path.join(fc.OUT, "controls.json"))) if c["id"] == model)
        text = "; ".join(sorted(set(does))) if works else "Planned: " + planned
        if len(clicks) > 1 and works:
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
    write_lang(models)
    os.makedirs(OUT, exist_ok=True)
    for k in range(6):
        side_picture(console, models, placed, k, SIDES[k]).save(os.path.join(OUT, f"side_{k}.png"))
    overview(console, models, placed).save(os.path.join(OUT, "overview.png"))
    print(len(placed), "controls placed")


if __name__ == "__main__":
    main()
