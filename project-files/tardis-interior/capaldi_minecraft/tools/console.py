# Hand-built Capaldi console + time rotor from rotated boxes (16 px = 1 block).
import sys, json, math, os
import numpy as np
from cub import Atlas, Group, export, walk, box_world
OUT = sys.argv[1]; os.makedirs(OUT + '/models', exist_ok=True); os.makedirs(OUT + '/textures', exist_ok=True)
T30 = math.tan(math.radians(30))
C = dict(silver=(.80, .81, .82), silver2=(.66, .68, .70), steel=(.50, .53, .57), trim=(.20, .24, .31), dark=(.10, .11, .13),
         copper=(.78, .48, .24), amber=(1, .72, .32), red=(.72, .14, .10), green=(.35, .95, .45), blue=(.35, .65, 1),
         white=(.97, .97, .95), glass=(.78, .90, 1, .42), black=(.05, .05, .06), brass=(.85, .70, .35))
GLOW = {'amber', 'green', 'blue', 'white'}
A = Atlas(256, 256); SW = {}
def sw(c):
    """2x2 solid swatch for a named colour (shared)."""
    if c not in SW:
        r = A.alloc(2, 2); col = C[c]; A.fill(r, col, col[:3] if c in GLOW else None); SW[c] = r
    return SW[c]
def F(c, which=('north', 'east', 'south', 'west', 'up', 'down')):
    return {f: sw(c) for f in which}
SIDES = ('north', 'east', 'south', 'west')

def hexprism(g, name, a, y0, y1, col, faces=SIDES + ('up', 'down'), eps=0.02):
    s = 2 * a * T30
    for i, r in enumerate((0, 60, 120)):
        fc = {f: sw(col) for f in faces if f in ('north', 'south') or (f in ('up', 'down'))}
        g.box(f"{name}_{i}", (-s / 2, y0 + i * eps, -a), (s / 2, y1 - i * eps, a), fc, rot=(0, r, 0), origin=(0, 0, 0))

def ring(g, name, r, y0, y1, thick, col):
    for k in range(6):
        sg = Group(f"{name}_{k}", (0, 0, 0), (0, 60 * k + 30, 0), g)
        s = 2 * r * T30
        sg.box(name, (-s / 2, y0, r - thick), (s / 2, y1, r), F(col))

CTRL = []
def control(name, kind, grp, boxes):
    """boxes: list of (from, to, colour) in the panel's frame. Returns the group."""
    cg = Group("ctl_" + name, grp.origin, (0, 0, 0), grp)
    for i, (f, t, c) in enumerate(boxes): cg.box(f"{name}_{i}", f, t, F(c))
    CTRL.append((name, kind, cg))
    return cg

# ---------------- console ----------------
con = Group("console")
hexprism(con, "pedestal", 5, -48, 7, 'steel')
hexprism(con, "rim", 21, 10, 13, 'silver2', faces=SIDES)
hexprism(con, "rim_band", 21.3, 11, 12, 'trim', faces=SIDES)
hexprism(con, "hub", 8, 17.5, 19, 'silver2', faces=SIDES + ('up',))
hexprism(con, "collar", 6, 19, 21, 'copper')

def slope(g, name, a0, y0, a1, y1, t, n, region, side_col, painted_up=True, under=False):
    """Tilted panel from (apothem a0, y0) to (a1, y1), split into n strips that follow the hexagon's taper."""
    dz, dy = a1 - a0, y1 - y0; L = math.hypot(dz, dy); th = math.degrees(math.atan2(-dy, dz))
    pg = Group(name, (0, y0, a0), (th, 0, 0), g)
    W = region[2] if region else 0
    for i in range(n):
        s0, s1 = L * i / n, L * (i + 1) / n
        ao = a0 + dz * (i + 1) / n if dz > 0 else a0 + dz * i / n      # outer apothem of this strip
        w = 2 * ao * T30
        fc = {f: sw(side_col) for f in ('north', 'south', 'east', 'west')}
        top = 'down' if under else 'up'
        if region:
            x, y, rw, rh = region; ux = x + (rw - w) / 2; vy = y + rh * i / n
            fc[top] = (ux, vy, w, rh / n)
        else: fc[top] = sw(side_col)
        fc['down' if not under else 'up'] = sw(side_col)
        yy = (y0 - t, y0) if not under else (y0, y0 + t)
        pg.box(f"{name}_{i}", (-w / 2, yy[0], a0 + s0), (w / 2, yy[1], a0 + s1), fc)
    return pg, L

# panel geometry: inner edge apothem 8 at y=18, outer edge apothem 21 at y=13 (about 21 degrees)
PW, PH = 25, 14
PANELS = []
for k in range(6):
    side = Group(f"side_{k}", (0, 0, 0), (0, 60 * k, 0), con)
    reg = A.alloc(PW, PH)
    pg, L = slope(side, f"panel_{k}", 8, 18, 21, 13, 1.5, 4, reg, 'silver2')
    slope(side, f"skirt_{k}", 5, 7, 21, 10, 1.5, 3, None, 'steel', under=True)
    # seam trim on the corner between this panel and the next
    cg = Group(f"seam_{k}", (0, 0, 0), (0, 30, 0), side)
    ri, ro = 8 / math.cos(math.radians(30)), 21 / math.cos(math.radians(30))
    tl = math.hypot(ro - ri, 5); tg = Group("t", (0, 18, ri), (math.degrees(math.atan2(5, ro - ri)), 0, 0), cg)
    tg.box(f"seam_{k}", (-1, 18, ri), (1, 19.2, ri + tl), F("trim"))
    PANELS.append((k, side, pg, reg, L))

# ---- painted panel textures (image y grows toward the outer edge, i.e. toward the player) ----
rng = np.random.default_rng(3)
def paint(reg, k):
    x, y, w, h = reg
    A.fill(reg, C['silver'])
    for i in range(w): A.px(reg, i, 0, C['steel']); A.px(reg, i, h - 1, C['steel'])
    for j in range(h): A.px(reg, 0, j, C['steel']); A.px(reg, w - 1, j, C['steel'])
    # a grille strip and a row of little lights on every panel, different per side
    for i in range(4, w - 4, 2): A.px(reg, i, 2, C['dark'])
    lights = ['green', 'amber', 'red', 'blue']
    for i in range(5, w - 5, 3): A.px(reg, i, h - 3, C[lights[(i + k) % 4]], emi=True)
    for _ in range(6):
        i, j = rng.integers(3, w - 3), rng.integers(4, h - 4); A.px(reg, i, j, C['black'])
for k, side, pg, reg, L in PANELS: paint(reg, k)

def on(pg, u, s, w, d, h, c, lift=0):
    """box on a panel: centre u across, s along the slope from the inner edge, size w x d, height h."""
    oz = pg.origin[2]; oy = pg.origin[1]
    return ((u - w / 2, oy + lift, oz + s - d / 2), (u + w / 2, oy + lift + h, oz + s + d / 2), c)

P = {k: pg for k, side, pg, reg, L in PANELS}
# side 0 faces the door (south): main levers and the slider bank
control("main_lever_left", "lever", P[0], [on(P[0], -7, 9, 2, 2, 1, 'dark'), on(P[0], -7, 9, 1, 1, 5, 'steel'), on(P[0], -7, 9, 2, 2, 2, 'red', 5)])
control("main_lever_right", "lever", P[0], [on(P[0], 7, 9, 2, 2, 1, 'dark'), on(P[0], 7, 9, 1, 1, 5, 'steel'), on(P[0], 7, 9, 2, 2, 2, 'red', 5)])
for i in range(5):
    control(f"slider_{i+1}", "slider", P[0], [on(P[0], -4 + 2 * i, 5, 1, 4, 1, 'dark'), on(P[0], -4 + 2 * i, 5, 1, 1, 3, 'brass', 1)])
control("buttons_front", "buttons", P[0], [on(P[0], 0, 11, 6, 2, 1, 'black'), on(P[0], -2, 11, 1, 1, 1, 'green', 1), on(P[0], 0, 11, 1, 1, 1, 'amber', 1), on(P[0], 2, 11, 1, 1, 1, 'red', 1)])
# screens on the two panels next to the front one (sides 1 and 5)
for n, k in (("screen_1", 1), ("screen_2", 5)):
    control(n, "screen", P[k], [on(P[k], 0, 7, 10, 8, 1, 'dark'), on(P[k], 0, 7, 8, 6, 1, 'blue', 0.5)])
    control(n.replace("screen", "dial"), "dial", P[k], [on(P[k], -8, 10, 3, 3, 1, 'brass'), on(P[k], -8, 10, 1, 1, 2, 'black', 1)])
    control(n.replace("screen", "buttons"), "buttons", P[k], [on(P[k], 8, 10, 3, 3, 1, 'black'), on(P[k], 8, 10, 1, 1, 1, 'amber', 1)])
# side 2: handbrake, crank wheel and a bank of toggle switches
control("handbrake", "lever", P[2], [on(P[2], -6, 8, 3, 2, 1, 'dark'), on(P[2], -6, 8, 2, 2, 4, 'steel', 1), on(P[2], -6, 8, 3, 3, 2, 'black', 5)])
control("crank", "wheel", P[2], [on(P[2], 4, 9, 5, 5, 1, 'brass'), on(P[2], 4, 9, 1, 1, 3, 'dark', 1)])
for i in range(4): control(f"toggle_{i+1}", "switch", P[2], [on(P[2], -4 + 3 * i, 3, 2, 2, 1, 'dark'), on(P[2], -4 + 3 * i, 3, 1, 1, 2, 'white', 1)])
# side 3 (north): typewriter keyboard, telephone, button group
control("keyboard", "keyboard", P[3], [on(P[3], 0, 9, 10, 5, 2, 'black'), on(P[3], 0, 9, 8, 3, 1, 'silver', 2)])
control("telephone", "phone", P[3], [on(P[3], -9, 7, 3, 5, 2, 'black')])
control("buttons_rear", "buttons", P[3], [on(P[3], 8, 6, 4, 4, 1, 'black'), on(P[3], 8, 6, 2, 2, 1, 'green', 1)])
# side 4: globe dial, three switches, a small lever
control("globe", "dial", P[4], [on(P[4], -5, 8, 5, 5, 4, 'copper'), on(P[4], -5, 8, 3, 3, 1, 'amber', 4)])
for i in range(3): control(f"switch_{i+1}", "switch", P[4], [on(P[4], 3 + 3 * i, 5, 2, 2, 1, 'dark'), on(P[4], 3 + 3 * i, 5, 1, 1, 2, 'red', 1)])
control("small_lever", "lever", P[4], [on(P[4], 5, 11, 2, 2, 1, 'dark'), on(P[4], 5, 11, 1, 1, 4, 'steel', 1), on(P[4], 5, 11, 2, 2, 1, 'brass', 4)])

# ---------------- time rotor (separate parts so they can animate) ----------------
glass = Group("rotor_glass")
hexprism(glass, "tube", 4, 21, 54, 'glass')
for k in range(6):
    side = Group(f"funnel_{k}", (0, 0, 0), (0, 60 * k, 0), glass)
    slope(side, f"funnel_{k}", 4, 54, 21, 70, 1, 4, None, 'glass', under=True)
column = Group("rotor_column")
hexprism(column, "core", 1.5, 19, 54, 'white')
for y in (26, 36, 46): hexprism(column, f"band{y}", 2.5, y, y + 2, 'copper')
hexprism(column, "top_collar", 5.5, 53, 55, 'trim')
spin = Group("rotor_spinners"); mid = Group("rotor_spinner_mid")
ring(spin, "ring_a", 24, 66, 68, 2, 'steel'); ring(mid, "ring_b", 29, 72, 74, 2, 'silver2')
ring(spin, "ring_c", 33, 78, 80, 2, 'steel'); ring(mid, "ring_d", 38, 84, 86, 2, 'silver2')

# ---------------- metal arches: 18 ribs, a straight pillar then a curve into the ceiling ----------------
arches = Group("arches")
def arch_points():
    pts = [(7.75, -3.0), (7.75, 3.6)]
    for i in range(1, 9):
        a = math.radians(90 * i / 8)
        pts.append((2.2 + 5.55 * math.cos(a), 3.6 + 3.0 * math.sin(a)))
    pts.append((1.6, 6.6))
    return [(r * 16, y * 16) for r, y in pts]
AP = arch_points()
for j in range(18):
    alpha = 20 * j                       # angle in the original model, 0 = east, 90 = north
    ag = Group(f"arch_{j}", (0, 0, 0), (0, 90 + alpha, 0), arches)
    for i in range(len(AP) - 1):
        (r0, y0), (r1, y1) = AP[i], AP[i + 1]
        L = math.hypot(r1 - r0, y1 - y0); th = math.degrees(math.atan2(-(y1 - y0), r1 - r0))
        sg = Group(f"seg_{i}", (0, y0, r0), (th, 0, 0), ag)
        fc = F('trim'); fc['down'] = sw('steel')          # the inner face catches a lighter metal edge
        sg.box(f"arch_{j}_{i}", (-1.5, y0 - 1.5, r0 - 0.5), (1.5, y0 + 1.5, r0 + L + 0.5), fc)

ORIGIN = (0.5, 0.0, 0.25)
info = {"units": "blocks; model files in pixels (16/block) with pivot at origin", "origin": ORIGIN, "parts": {}, "controls": []}
for root in (con, glass, column, spin, mid, arches):
    n = sum(1 for _ in walk(root))
    tris = export(root, A, f"{OUT}/models/{root.name}", "console", ORIGIN)
    info["parts"][root.name] = {"obj": f"models/{root.name}.obj", "bbmodel": f"models/{root.name}.bbmodel", "boxes": n, "triangles": tris}
for name, kind, cg in CTRL:
    pts = np.concatenate([box_world(g, b) for g, b in walk(cg)]) / 16 + np.array(ORIGIN)
    side = None
    for k, s_, pg, reg, L in PANELS:
        if cg.parent is pg: side = k
    info["controls"].append({"name": name, "kind": kind, "panel": side, "facing": ["south", "south-east", "north-east", "north", "north-west", "south-west"][side],
                             "hitbox_min": np.round(pts.min(0), 4).tolist(), "hitbox_max": np.round(pts.max(0), 4).tolist()})
A.save(f"{OUT}/textures/console.png")
json.dump(info, open(f"{OUT}/parts.json", "w"), indent=1)
print({k: (v["boxes"], v["triangles"]) for k, v in info["parts"].items()}, "controls", len(CTRL))
