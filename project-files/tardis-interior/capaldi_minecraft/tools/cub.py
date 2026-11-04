# Tiny Blockbench-style cuboid modeller: groups (pivot + rotation) holding boxes in pixel units (16 px = 1 block).
# Exports a Blockbench "free" .bbmodel, an OBJ (world blocks), and controls with hitboxes.
import json, math, uuid
import numpy as np

def rotm(r):
    rx, ry, rz = [math.radians(a) for a in r]
    X = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    Y = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    Z = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return Z @ Y @ X          # Blockbench order: X first, then Y, then Z

class Atlas:
    def __init__(s, w=256, h=256): s.w, s.h = w, h; s.img = np.zeros((h, w, 4), np.float32); s.emi = np.zeros((h, w, 4), np.float32); s.x = s.y = s.row = 0
    def alloc(s, w, h):
        w, h = max(1, int(math.ceil(w))), max(1, int(math.ceil(h)))
        if s.x + w > s.w: s.x = 0; s.y += s.row; s.row = 0
        r = (s.x, s.y, w, h); s.x += w; s.row = max(s.row, h)
        assert s.y + h <= s.h, "atlas full"
        return r
    def fill(s, r, col, emi=None):
        x, y, w, h = r; s.img[y:y + h, x:x + w, :3] = col[:3]; s.img[y:y + h, x:x + w, 3] = col[3] if len(col) > 3 else 1
        if emi is not None: s.emi[y:y + h, x:x + w, :3] = emi; s.emi[y:y + h, x:x + w, 3] = 1
    def px(s, r, i, j, col, emi=False):
        x, y, w, h = r
        if 0 <= i < w and 0 <= j < h:
            s.img[y + j, x + i, :3] = col; s.img[y + j, x + i, 3] = 1
            if emi: s.emi[y + j, x + i, :3] = col; s.emi[y + j, x + i, 3] = 1
    def save(s, path):
        from PIL import Image
        for a, p in ((s.img, path), (s.emi, path.replace('.png', '_emissive.png'))):
            Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), 'RGBA').save(p)

FACES = ['north', 'east', 'south', 'west', 'up', 'down']

class Box:
    def __init__(s, name, frm, to, faces, rot=(0, 0, 0), origin=None):
        s.name, s.frm, s.to, s.faces, s.rot = name, np.array(frm, float), np.array(to, float), faces, rot
        s.origin = np.array(origin if origin is not None else (s.frm + s.to) / 2, float)

class Group:
    def __init__(s, name, origin=(0, 0, 0), rot=(0, 0, 0), parent=None):
        s.name, s.origin, s.rot, s.parent, s.kids = name, np.array(origin, float), rot, parent, []
        if parent: parent.kids.append(s)
    def box(s, *a, **k): b = Box(*a, **k); s.kids.append(b); return b
    def world(s, p):
        g = s
        while g:
            p = rotm(g.rot) @ (p - g.origin) + g.origin; g = g.parent
        return p

def corners(b):
    f, t = b.frm, b.to
    c = np.array([[x, y, z] for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])])
    return (rotm(b.rot) @ (c - b.origin).T).T + b.origin

def walk(g):
    for k in g.kids:
        if isinstance(k, Box): yield g, k
        else: yield from walk(k)

def box_world(g, b): return np.array([g.world(p) for p in corners(b)])

# face quads as indices into corners() (x major, then y, then z) and outward normal
QUADS = {'north': [6, 2, 0, 4], 'south': [1, 3, 7, 5], 'east': [7, 6, 4, 5], 'west': [2, 3, 1, 0], 'up': [3, 2, 6, 7], 'down': [0, 4, 5, 1]}
# texture axes per face: (local axis, +1/-1) for image x and image y (image y grows downward)
FACEUV = {'south': ((0, 1), (1, -1)), 'north': ((0, -1), (1, -1)), 'east': ((2, -1), (1, -1)), 'west': ((2, 1), (1, -1)),
          'up': ((0, 1), (2, 1)), 'down': ((0, 1), (2, -1))}
# corner index = xi*4+yi*2+zi;  north=-z, south=+z, east=+x, west=-x

def export(root, atlas, path, texname, scale_origin):
    """OBJ in blocks (world = model/16 + scale_origin), bbmodel in pixels."""
    V, VT, F = [], [], []
    for g, b in walk(root):
        W = box_world(g, b) / 16 + np.array(scale_origin)
        for fn, uv in b.faces.items():
            if uv is None: continue
            x, y, w, h = uv
            q = QUADS[fn]; base = len(V)
            V.extend(W[q].tolist())
            L = np.array([[xi, yi, zi] for xi in (0, 1) for yi in (0, 1) for zi in (0, 1)], float)[q]
            ua, va = FACEUV[fn]
            for c in L:
                u = c[ua[0]] if ua[1] > 0 else 1 - c[ua[0]]
                v = c[va[0]] if va[1] > 0 else 1 - c[va[0]]
                VT.append(((x + u * w) / atlas.w, 1 - (y + v * h) / atlas.h))
            F.append([base + i for i in range(4)])
    with open(path + '.obj', 'w') as f:
        f.write(f"mtllib {path.split('/')[-1]}.mtl\nusemtl {texname}\n")
        for v in V: f.write("v %.5f %.5f %.5f\n" % tuple(v))
        for t in VT: f.write("vt %.5f %.5f\n" % t)
        for q in F: f.write("f " + " ".join(f"{i+1}/{i+1}" for i in q) + "\n")
    open(path + '.mtl', 'w').write(f"newmtl {texname}\nKd 1 1 1\nmap_Kd ../textures/{texname}.png\nmap_Ke ../textures/{texname}_emissive.png\n")
    # bbmodel
    els, outl = [], []
    def grp(g):
        node = {"name": g.name, "origin": g.origin.tolist(), "rotation": list(g.rot), "uuid": str(uuid.uuid4()), "children": []}
        for k in g.kids:
            if isinstance(k, Box):
                u = str(uuid.uuid4())
                faces = {fn: ({"uv": [k.faces[fn][0], k.faces[fn][1], k.faces[fn][0] + k.faces[fn][2], k.faces[fn][1] + k.faces[fn][3]], "texture": 0} if k.faces.get(fn) else {"uv": [0, 0, 0, 0], "texture": None}) for fn in FACES}
                els.append({"name": k.name, "type": "cube", "uuid": u, "from": k.frm.tolist(), "to": k.to.tolist(), "origin": k.origin.tolist(), "rotation": list(k.rot), "faces": faces, "box_uv": False})
                node["children"].append(u)
            else: node["children"].append(grp(k))
        return node
    outl = [grp(root)]
    import base64, io
    from PIL import Image
    buf = io.BytesIO(); Image.fromarray((np.clip(atlas.img, 0, 1) * 255).astype(np.uint8), 'RGBA').save(buf, 'PNG')
    bb = {"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False}, "name": texname,
          "resolution": {"width": atlas.w, "height": atlas.h}, "elements": els, "outliner": outl,
          "textures": [{"name": texname + ".png", "id": "0", "width": atlas.w, "height": atlas.h, "uv_width": atlas.w, "uv_height": atlas.h,
                        "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(), "uuid": str(uuid.uuid4())}]}
    json.dump(bb, open(path + '.bbmodel', 'w'))
    return len(F) * 2
