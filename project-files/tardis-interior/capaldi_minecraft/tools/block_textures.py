# 16x16 block textures (and _emissive masks) for the room blocks.
import sys, os, numpy as np
from PIL import Image
OUT = sys.argv[1]; os.makedirs(OUT, exist_ok=True)
def new(c): a = np.zeros((16, 16, 3)); a[:] = c; return a
def border(a, c):
    a[0, :] = c; a[-1, :] = c; a[:, 0] = c; a[:, -1] = c
def save(name, a, e=None):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)).save(f"{OUT}/{name}.png")
    if e is not None:
        Image.fromarray((np.clip(e, 0, 1) * 255).astype(np.uint8)).save(f"{OUT}/{name}_emissive.png")
yy, xx = np.mgrid[0:16, 0:16] + 0.5
def hexdist(cx, cy):        # distance in a hexagon metric (flat top), so the roundel reads as a hexagon-ish ring
    dx, dy = np.abs(xx - cx), np.abs(yy - cy)
    return np.maximum(dy * 1.0, dx * 0.866 + dy * 0.5)
def roundel(lit):
    a = new((.30, .29, .28)); border(a, (.20, .19, .19))
    a[1, 1:-1] = (.38, .37, .35); a[1:-1, 1] = (.38, .37, .35)
    d = hexdist(8, 8); ring = (d >= 4.2) & (d < 6.0); centre = d < 4.2
    amber = np.array((1, .74, .36)) if lit else np.array((.80, .55, .28))
    a[ring] = amber; a[centre] = (.12, .11, .11); a[(d >= 3.4) & (d < 4.2)] = (.22, .2, .19)
    e = np.zeros_like(a)
    if lit: e[ring] = amber
    return a, (e if lit else None)
save("roundel_wall", *roundel(False)); save("roundel_wall_lit", *roundel(True))
a = new((.62, .63, .65)); border(a, (.48, .49, .52))
for i in range(2, 14, 4): a[i:i + 1, 2:14] = (.56, .57, .6)
save("floor_plate", a)
a = new((.38, .40, .44)); border(a, (.30, .32, .36)); e = np.zeros_like(a)
for i in range(2, 14, 2):
    for j in range(2, 14, 2): a[i, j] = (.95, .96, .92); e[i, j] = (.95, .96, .92)
save("grille_plate", a, e)
a = new((.20, .24, .31)); a[0, :] = (.32, .37, .45); a[:, 0] = (.32, .37, .45); a[-1, :] = (.13, .16, .21); a[:, -1] = (.13, .16, .21)
save("frame", a)
a = new((.72, .73, .75)); border(a, (.60, .61, .64)); a[7:8, 1:-1] = (.64, .65, .68)
save("wall_panel", a)
a = new((.72, .73, .75)); border(a, (.60, .61, .64)); e = np.zeros_like(a); a[6:10, 1:-1] = (.98, .98, .95); e[6:10, 1:-1] = (.98, .98, .95)
save("light_strip", a, e)
a = new((.30, .20, .12)); border(a, (.22, .14, .08))
rng = np.random.default_rng(5); cols = [(.55, .15, .12), (.15, .3, .5), (.6, .5, .3), (.2, .4, .25), (.45, .35, .25)]
for row in (1, 9):
    x = 1
    while x < 15:
        w = int(rng.integers(1, 3)); a[row:row + 6, x:x + w] = cols[int(rng.integers(len(cols)))]; x += w
save("bookshelf_preview", a)
print(sorted(os.listdir(OUT)))
