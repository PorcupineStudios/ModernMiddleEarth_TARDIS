"""Paints the harness textures (run from harness/: python3 tools/textures.py). Writes:
- textures/entity/harness.png (64x32): leather (0,0)-(31,15), brass (32,0)-(63,15), collar padding (0,16)-(63,31),
  the regions HarnessModel's boxes use.
- textures/item/harness.png (16x16): a horse collar with brass hames.
Seeded noise, so reruns give the same pictures."""
import random
from pathlib import Path
from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/ringbearer_harness/textures"
rng = random.Random(1124)


def shade(rgb, k):
    return tuple(max(0, min(255, int(c * k))) for c in rgb) + (255,)


def fill(img, x0, y0, x1, y1, base, grain=0.10, stitch_every=0):
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = 1 + rng.uniform(-grain, grain)
            if stitch_every and (y - y0) % stitch_every == 0 and x % 2 == 0:
                k *= 0.78  # stitching rows
            img.putpixel((x, y), shade(base, k))


def entity():
    img = Image.new("RGBA", (64, 32))
    fill(img, 0, 0, 32, 16, (92, 58, 34), 0.10, stitch_every=4)   # leather
    fill(img, 32, 0, 64, 16, (186, 146, 64), 0.14)                 # brass
    for x in range(32, 64, 3):                                     # brass glints
        img.putpixel((x, rng.randrange(0, 16)), (236, 212, 140, 255))
    fill(img, 0, 16, 64, 32, (70, 44, 28), 0.08, stitch_every=3)   # collar padding, darker
    (OUT / "entity").mkdir(parents=True, exist_ok=True)
    img.save(OUT / "entity/harness.png")


def item():
    img = Image.new("RGBA", (16, 16))
    leather, dark, brass = (110, 70, 40), (70, 44, 28), (196, 156, 70)
    # an upright oval collar ring
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 6.2, (y - 8.5) / 7.0
            r = dx * dx + dy * dy
            if 0.42 <= r <= 1.0:
                img.putpixel((x, y), shade(dark if r > 0.8 or r < 0.5 else leather, 1 + rng.uniform(-0.08, 0.08)))
    # hames: brass bars down both sides, knobs on top
    for y in range(1, 14):
        for x in (2, 13):
            img.putpixel((x, y), shade(brass, 1 + rng.uniform(-0.1, 0.1)))
    for x in (1, 2, 13, 14):
        img.putpixel((x, 1), shade(brass, 1.2))
    # tug rings at the bottom
    for x, y in ((2, 13), (13, 13), (2, 14), (13, 14)):
        img.putpixel((x, y), shade(brass, 0.8))
    (OUT / "item").mkdir(parents=True, exist_ok=True)
    img.save(OUT / "item/harness.png")


entity()
item()
print("wrote", OUT / "entity/harness.png", "and", OUT / "item/harness.png")
