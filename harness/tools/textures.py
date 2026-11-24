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


def wheel():
    """textures/entity/wagon_wheel.png (32x16): wood (0,0)-(15,15), iron tyre (16,0)-(31,15); and the item icon."""
    img = Image.new("RGBA", (32, 16))
    fill(img, 0, 0, 16, 16, (122, 86, 52), 0.12)   # oak-ish wood
    for y in range(0, 16, 3):                          # grain
        for x in range(16):
            if rng.random() < 0.5:
                img.putpixel((x, y), shade((122, 86, 52), 0.8))
    fill(img, 16, 0, 32, 16, (78, 78, 84), 0.10)    # iron
    img.save(OUT / "entity/wagon_wheel.png")
    icon = Image.new("RGBA", (16, 16))
    wood, iron = (122, 86, 52), (90, 90, 96)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = (dx * dx + dy * dy) ** 0.5
            if 6.2 <= r <= 7.6:
                icon.putpixel((x, y), shade(iron, 1 + rng.uniform(-0.1, 0.1)))
            elif 5.0 <= r < 6.2:
                icon.putpixel((x, y), shade(wood, 0.85))
            elif r < 1.8:
                icon.putpixel((x, y), shade(iron, 1.2))
            elif r < 5.0 and (abs(dx) < 0.8 or abs(dy) < 0.8 or abs(abs(dx) - abs(dy)) < 0.8):
                icon.putpixel((x, y), shade(wood, 1 + rng.uniform(-0.1, 0.1)))
    icon.save(OUT / "item/wagon_wheel.png")


entity()
item()
wheel()
print("wrote", OUT / "entity/harness.png", "and", OUT / "item/harness.png")
