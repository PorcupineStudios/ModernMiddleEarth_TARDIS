"""Draws a 64x64 skin on the goblin body or the plain humanoid outside the game (2026-11-26), to check a skin and the
body's shape without starting Minecraft. The boxes, pivots, turns and scales copy lotr client/NpcModels and
client/GoblinModel (standing pose); textures are mapped as Minecraft maps a box (top, bottom, right, front, left,
back). Usage: python body_preview.py out.png goblin skin1.png [skin2.png ...]   (or humanoid)
Each skin gets a front three-quarter, a side and a back three-quarter view, side by side.
"""
import math
import sys

from PIL import Image, ImageDraw

# GoblinModel's numbers
HEAD, BODY_XY, BODY_Z, ARM_XZ, ARM_Y, LEG_XZ, LEG_Y, HUNCH = 1.2, 0.85, 0.9, 0.75, 1.15, 0.8, 0.75, 0.35
LEGS_DOWN = 12 * (1 - LEG_Y)
BODY_LEN = 12 * BODY_XY
BODY_DOWN = LEGS_DOWN + 12 * (1 - BODY_XY) + BODY_LEN * (1 - math.cos(HUNCH))
BODY_FORWARD = -BODY_LEN * math.sin(HUNCH)
SHOULDER = 2 * ARM_Y
ARM_DOWN = BODY_DOWN + SHOULDER * math.cos(HUNCH) - 2
ARM_FORWARD = BODY_FORWARD + SHOULDER * math.sin(HUNCH)
ARM_IN = 5 - (4 * BODY_XY + 1 * ARM_XZ)
HEAD_DOWN = BODY_DOWN + 0.8
HEAD_FORWARD = BODY_FORWARD - 0.5


class Part:
    def __init__(self, pivot, boxes, rot=(0, 0, 0), scale=(1, 1, 1), children=()):
        self.pivot, self.boxes, self.rot, self.scale, self.children = list(pivot), boxes, list(rot), list(scale), list(children)


def box(u, v, x, y, z, w, h, d, mirror=False, grow=0.0):
    return (u, v, x, y, z, w, h, d, mirror, grow)


def humanoid(goblin):
    head_children = []
    if goblin:
        back, up = 0.6, 0.18
        head_children = [
            Part((-4, -5, 0), [box(0, 32, -4, -2, -0.5, 4, 4, 1), box(0, 37, -7, -2, -0.5, 3, 2, 1)], (0, back, up)),
            Part((4, -5, 0), [box(0, 32, 0, -2, -0.5, 4, 4, 1, True), box(0, 37, 4, -2, -0.5, 3, 2, 1, True)],
                 (0, -back, -up)),
            Part((0, -3, -4), [box(16, 32, -1, -2, -2, 2, 3, 2), box(16, 37, -1, 1, -2, 2, 1, 1)], (0.15, 0, 0)),
        ]
    head = Part((0, 0, 0), [box(0, 0, -4, -8, -4, 8, 8, 8)], children=head_children)
    hat = Part((0, 0, 0), [box(32, 0, -4, -8, -4, 8, 8, 8, grow=0.5)])
    body = Part((0, 0, 0), [box(16, 16, -4, 0, -2, 8, 12, 4)])
    rarm = Part((-5, 2, 0), [box(40, 16, -3, -2, -2, 4, 12, 4)], (0, 0, 0.06))
    larm = Part((5, 2, 0), [box(40, 16, -1, -2, -2, 4, 12, 4, True)], (0, 0, -0.06))
    rleg = Part((-1.9, 12, 0), [box(0, 16, -2, 0, -2, 4, 12, 4)])
    lleg = Part((1.9, 12, 0), [box(0, 16, -2, 0, -2, 4, 12, 4, True)])
    if goblin:  # GoblinModel.posture()
        head.scale = [HEAD] * 3
        hat.scale = [HEAD] * 3
        body.scale = [BODY_XY, BODY_XY, BODY_Z]
        for a in (rarm, larm):
            a.scale = [ARM_XZ, ARM_Y, ARM_XZ]
            a.pivot[1] += ARM_DOWN
            a.pivot[2] += ARM_FORWARD
        rarm.pivot[0] += ARM_IN
        larm.pivot[0] -= ARM_IN
        for leg in (rleg, lleg):
            leg.scale = [LEG_XZ, LEG_Y, LEG_XZ]
            leg.pivot[1] += LEGS_DOWN
        body.pivot[1] += BODY_DOWN
        body.pivot[2] = BODY_FORWARD
        body.rot[0] += HUNCH
        for h in (head, hat):
            h.pivot[1] += HEAD_DOWN
            h.pivot[2] = HEAD_FORWARD
    return [head, hat, body, rarm, larm, rleg, lleg]


def matmul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def transform(part):
    x, y, z = part.pivot
    t = [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]
    rx, ry, rz = part.rot
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]
    my = [[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]]
    mz = [[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]
    s = [[part.scale[0], 0, 0, 0], [0, part.scale[1], 0, 0], [0, 0, part.scale[2], 0], [0, 0, 0, 1]]
    return matmul(matmul(matmul(matmul(t, mz), my), mx), s)


def apply(m, p):
    return tuple(m[i][0] * p[0] + m[i][1] * p[1] + m[i][2] * p[2] + m[i][3] for i in range(3))


def quads(parts, skin, parent=None):
    """Every opaque texel of every box face as (corner points in model space, colour, outward normal)."""
    out = []
    for part in parts:
        m = transform(part) if parent is None else matmul(parent, transform(part))
        for (u, v, x, y, z, w, h, d, mirror, grow) in part.boxes:
            x0, y0, z0, x1, y1, z1 = x - grow, y - grow, z - grow, x + w + grow, y + h + grow, z + d + grow
            dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
            rects = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                     "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h),
                     "back": (u + 2 * d + w, v + d, w, h)}
            if mirror:
                rects["right"], rects["left"] = rects["left"], rects["right"]
            faces = {
                # name: (corner of texel (i, j), size of one texel along i, along j)
                "top": (lambda i, j: (x0 + i * dx / w, y0, z1 - j * dz / d), (dx / w, 0, 0), (0, 0, -dz / d)),
                "bottom": (lambda i, j: (x0 + i * dx / w, y1, z1 - j * dz / d), (dx / w, 0, 0), (0, 0, -dz / d)),
                "right": (lambda i, j: (x0, y0 + j * dy / h, z1 - i * dz / d), (0, 0, -dz / d), (0, dy / h, 0)),
                "front": (lambda i, j: (x0 + i * dx / w, y0 + j * dy / h, z0), (dx / w, 0, 0), (0, dy / h, 0)),
                "left": (lambda i, j: (x1, y0 + j * dy / h, z0 + i * dz / d), (0, 0, dz / d), (0, dy / h, 0)),
                "back": (lambda i, j: (x1 - i * dx / w, y0 + j * dy / h, z1), (-dx / w, 0, 0), (0, dy / h, 0)),
            }
            normals = {"top": (0, -1, 0), "bottom": (0, 1, 0), "right": (-1, 0, 0), "front": (0, 0, -1),
                       "left": (1, 0, 0), "back": (0, 0, 1)}
            for name, (corner, di, dj) in faces.items():
                tu, tv, tw, th = rects[name]
                for j in range(th):
                    for i in range(tw):
                        ti = tw - 1 - i if mirror else i
                        c = skin.getpixel((tu + ti, tv + j))
                        if c[3] < 128:
                            continue
                        a = corner(i, j)
                        pts = [a, tuple(a[k] + di[k] for k in range(3)),
                               tuple(a[k] + di[k] + dj[k] for k in range(3)), tuple(a[k] + dj[k] for k in range(3))]
                        world = [apply(m, p) for p in pts]
                        n0 = apply(m, (0, 0, 0))
                        nn = apply(m, normals[name])
                        out.append((world, c, tuple(nn[k] - n0[k] for k in range(3))))
        out += quads(part.children, skin, m)
    return out


def render(parts, skin, yaw, pitch, size=360, px=9.0):
    img = Image.new("RGBA", (size, size), (34, 30, 28, 255))
    draw = ImageDraw.Draw(img)
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)

    def view(p):
        x, y, z = p
        x, z = x * cy - z * sy, x * sy + z * cy  # turn about the vertical
        y, z = y * cp - z * sp, y * sp + z * cp  # look down a little
        return x, y, z

    items = []
    for pts, c, n in quads(parts, skin):
        vp = [view(p) for p in pts]
        nv = view(n)
        length = math.sqrt(sum(k * k for k in nv)) or 1
        if nv[2] / length > 0.02:
            continue  # facing away
        light = 0.55 + 0.45 * max(0.0, (-nv[1] * 0.6 - nv[2] * 0.8) / length)
        items.append((sum(p[2] for p in vp) / 4, vp, tuple(int(c[k] * light) for k in range(3)) + (255,)))
    items.sort(key=lambda t: -t[0])
    for _, vp, c in items:
        draw.polygon([(size / 2 + p[0] * px, size * 0.06 + (p[1] + 10) * px) for p in vp], fill=c)
    return img


def main():
    out, kind, skins = sys.argv[1], sys.argv[2], sys.argv[3:]
    parts = humanoid(kind == "goblin")
    views = [(-30, 12), (-90, 0), (150, 12)]
    sheet = Image.new("RGBA", (360 * len(views), 360 * len(skins)))
    for k, path in enumerate(skins):
        skin = Image.open(path).convert("RGBA")
        for i, (yaw, pitch) in enumerate(views):
            sheet.paste(render(parts, skin, math.radians(yaw), math.radians(pitch)), (360 * i, 360 * k))
    sheet.save(out)


if __name__ == "__main__":
    main()
