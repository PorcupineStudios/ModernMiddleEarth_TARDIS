"""Goblin skins (2026-11-26, Aaron: a goblin faction with its own model, which players can be too).

Paints the 64x64 skins of the goblin body (lotr client/ShapedModel, layout in client/NpcModels): the humanoid boxes
where a player skin keeps them (head, body, right arm and leg; the left limbs mirror the right), the vanilla hat for
hoods and tufts, and the goblin parts under y 32 (bat ears, hooked nose). Colours come from one palette that sits
with Goblin Town's blocks (goblin_blocks.py): sallow grey-olive skin, ragged greyed browns, rope tan, rust.

Writes lotr/src/main/resources/assets/lotr_port/textures/entity/goblin/:
  goblin/0..7.png (warriors), archer/0..3.png (hooded), great_goblin.png (the Great Goblin: pallid, crowned).
Run: python goblin_skins.py
"""
import colorsys
import os
import random

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "lotr", "src", "main", "resources", "assets", "lotr_port",
                   "textures", "entity", "goblin")

# hue, saturation, value
SKINS = [
    (0.15, 0.22, 0.58),  # sallow olive
    (0.12, 0.20, 0.54),  # muddy grey-olive
    (0.10, 0.16, 0.62),  # ashen
    (0.18, 0.18, 0.50),  # grey-green
    (0.08, 0.20, 0.56),  # dusky
]
GREAT = (0.04, 0.17, 0.70)  # the Great Goblin: pallid, pinkish grey
RAG = (0.08, 0.14)           # greyed brown cloth (hue, saturation)
LEATHER = (0.065, 0.42)
ROPE = (0.105, 0.33)
FUR = (0.07, 0.25)
IRON = (0.065, 0.22)
GOLD = (0.11, 0.45)
BONE = (0.12, 0.14)


def rgb(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return int(r * 255), int(g * 255), int(b * 255), 255


def faces(u, v, w, h, d):
    """A box's six faces on the sheet: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


HEAD = faces(0, 0, 8, 8, 8)
HAT = faces(32, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)
EAR = faces(0, 32, 4, 4, 1)
EAR_TIP = faces(0, 37, 3, 2, 1)
NOSE = faces(16, 32, 2, 3, 2)
NOSE_TIP = faces(16, 37, 2, 1, 1)
SIDES = ("right", "front", "left", "back")


class Painter:
    def __init__(self, seed, skin):
        self.rng = random.Random(seed)
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.skin = skin

    def put(self, x, y, c):
        self.img.putpixel((x, y), c)

    def tone(self, base, v_mul=1.0, amp=0.05):
        h, s, v = base
        return rgb(h + self.rng.uniform(-0.008, 0.008), s * self.rng.uniform(0.9, 1.1),
                   v * v_mul + self.rng.uniform(-amp, amp))

    def skin_px(self, v_mul=1.0, amp=0.04):
        return self.tone(self.skin, v_mul, amp)

    def fill(self, rect, colour):
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                self.put(x, y, colour() if callable(colour) else colour)

    def mottle(self, rect, chance=0.12, dark=0.82):
        """Blotches and warts on skin already painted."""
        x0, y0, w, h = rect
        for y in range(y0, y0 + h):
            for x in range(x0, x0 + w):
                if self.rng.random() < chance:
                    self.put(x, y, self.skin_px(dark if self.rng.random() < 0.7 else 1.12, 0.02))

    def cloth(self, hue_sat, v=0.36, amp=0.05):
        return lambda: rgb(hue_sat[0] + self.rng.uniform(-0.01, 0.01), hue_sat[1] * self.rng.uniform(0.85, 1.15),
                           v + self.rng.uniform(-amp, amp))

    def skin_box(self, box, shade_sides=True):
        for name, rect in box.items():
            mul = {"top": 1.05, "bottom": 0.75, "right": 0.92, "left": 0.92, "back": 0.9, "front": 1.0}[name]
            self.fill(rect, lambda m=mul: self.skin_px(m if shade_sides else 1.0))
            self.mottle(rect)


def head(p, kind):
    p.skin_box(HEAD)
    fx, fy = HEAD["front"][0], HEAD["front"][1]
    dark = lambda: p.skin_px(0.68, 0.02)
    # heavy brow
    for x in range(1, 7):
        p.put(fx + x, fy + 2, dark())
    # small yellow eyes, pupils turned in
    eye = rgb(0.15, 0.55, 0.85) if kind != "great" else rgb(0.13, 0.45, 0.9)
    pupil = (24, 18, 12, 255)
    p.put(fx + 1, fy + 3, eye)
    p.put(fx + 2, fy + 3, pupil)
    p.put(fx + 5, fy + 3, pupil)
    p.put(fx + 6, fy + 3, eye)
    for x in (1, 2, 5, 6):
        p.put(fx + x, fy + 4, p.skin_px(0.78, 0.02))  # bags under the eyes
    # nostrils under where the nose sits, a wide mouth with crooked teeth
    p.put(fx + 3, fy + 5, p.skin_px(0.6, 0.02))
    p.put(fx + 4, fy + 5, p.skin_px(0.6, 0.02))
    mouth = (40, 26, 22, 255)
    tooth = rgb(0.12, 0.18, 0.82)
    for x in range(1, 7):
        p.put(fx + x, fy + 6, mouth)
    for x in p.rng.sample([1, 2, 3, 4, 5, 6], 3):
        p.put(fx + x, fy + 6, tooth)
    p.put(fx + 2, fy + 7, p.skin_px(0.85, 0.02))
    p.put(fx + 5, fy + 7, p.skin_px(0.85, 0.02))
    if kind == "great":
        # the Great Goblin's sagging chin and warts
        for x in range(2, 6):
            p.put(fx + x, fy + 7, p.skin_px(1.08, 0.02))
        for (x, y) in ((0, 5), (7, 4), (6, 7)):
            p.put(fx + x, fy + y, p.skin_px(0.75, 0.02))
    # a few stringy hairs on the scalp
    if kind != "great" and p.rng.random() < 0.5:
        hair = lambda: rgb(0.08, 0.12, 0.2 + p.rng.uniform(-0.04, 0.04))
        tx, ty = HEAD["top"][0], HEAD["top"][1]
        for _ in range(5):
            p.put(tx + p.rng.randrange(8), ty + p.rng.randrange(8), hair())


def ears_nose(p):
    for box in (EAR, EAR_TIP):
        p.skin_box(box, shade_sides=False)
    # darker, pinker hollow on the forward face of the ear
    ex, ey, ew, eh = EAR["front"]
    for y in range(ey + 1, ey + eh - 1):
        for x in range(ex + 1, ex + ew - 1):
            p.put(x, y, rgb(p.skin[0] - 0.05, p.skin[1] * 1.4, p.skin[2] * 0.62 + p.rng.uniform(-0.02, 0.02)))
    tx, ty, tw, th = EAR_TIP["front"]
    p.put(tx, ty, rgb(p.skin[0] - 0.05, p.skin[1] * 1.4, p.skin[2] * 0.66))
    p.skin_box(NOSE)
    p.skin_box(NOSE_TIP)
    nx, ny, nw, nh = NOSE["front"]
    p.put(nx + p.rng.randrange(nw), ny + p.rng.randrange(nh), p.skin_px(0.72, 0.02))  # wart
    for rect in NOSE_TIP.values():
        p.fill(rect, lambda: p.skin_px(0.8, 0.02))


def body(p, kind, outfit):
    p.skin_box(BODY)
    fx, fy, fw, fh = BODY["front"]
    bx, by, bw, bh = BODY["back"]
    # ribs and a pot belly
    for y in (2, 4):
        for x in (1, 2, 5, 6):
            p.put(fx + x, fy + y, p.skin_px(0.82, 0.02))
    for x in range(2, 6):
        p.put(fx + x, fy + 7, p.skin_px(1.08, 0.02))
    rag = p.cloth(RAG, 0.34)
    leather = p.cloth(LEATHER, 0.3)
    if outfit == "rags":
        for name in SIDES:
            x0, y0, w, h = BODY[name]
            for y in range(y0, y0 + h):
                for x in range(x0, x0 + w):
                    torn = p.rng.random() < 0.12 or (y == y0 + h - 1 and p.rng.random() < 0.5)
                    if not torn:
                        p.put(x, y, rag())
        p.fill(BODY["top"], rag)
    if outfit in ("strap", "fur", "quiver", "great"):
        # a strap from the right shoulder to the left hip, front and back
        for i in range(10):
            p.put(fx + min(7, i * 7 // 9), fy + i, leather())
            p.put(bx + 7 - min(7, i * 7 // 9), by + i, leather())
    if outfit == "fur":
        fur = p.cloth(FUR, 0.42, 0.08)
        p.fill((fx, fy, 4, 3), fur)
        p.fill((BODY["right"][0], BODY["right"][1], 4, 3), fur)
        p.fill((bx + 4, by, 4, 3), fur)
        p.fill((BODY["top"][0], BODY["top"][1], 4, 4), fur)
    if outfit == "quiver":
        # the quiver on the back: a leather tube with fletching at the top
        for y in range(0, 9):
            for x in (5, 6):
                p.put(bx + x, by + y, leather())
        for x in (5, 6):
            p.put(bx + x, by, rgb(0.0, 0.0, 0.85))
    if outfit == "great":
        bone = lambda: rgb(BONE[0], BONE[1], 0.8 + p.rng.uniform(-0.05, 0.05))
        for x in range(8):
            if x % 2 == 0:
                p.put(fx + x, fy + 1, bone())
        p.put(fx + 3, fy + 2, bone())
        p.put(fx + 4, fy + 2, bone())
    # belt and loincloth
    for name in SIDES:
        x0, y0, w, h = BODY[name]
        for x in range(x0, x0 + w):
            p.put(x, y0 + 9, leather())
            p.put(x, y0 + 10, rag())
            if p.rng.random() < 0.7:
                p.put(x, y0 + 11, rag())
    buckle = rgb(IRON[0], IRON[1], 0.45)
    p.put(fx + 3, fy + 9, buckle)
    p.put(fx + 4, fy + 9, buckle)


def arm(p, outfit):
    p.skin_box(ARM)
    wrap = p.cloth(ROPE, 0.42) if outfit != "great" else p.cloth(IRON, 0.32)
    for name in SIDES:
        x0, y0, w, h = ARM[name]
        for x in range(x0, x0 + w):
            for y in (y0 + 7, y0 + 8):
                p.put(x, y, wrap())
            p.put(x, y0 + 11, p.skin_px(0.7, 0.02))  # knuckles and claws
    if outfit == "fur":
        p.fill((ARM["top"][0], ARM["top"][1], 4, 4), p.cloth(FUR, 0.42, 0.08))
        for name in SIDES:
            x0, y0, w, h = ARM[name]
            p.fill((x0, y0, w, 2), p.cloth(FUR, 0.42, 0.08))


def leg(p, outfit):
    p.skin_box(LEG)
    rag = p.cloth(RAG, 0.3)
    rows = 7 if outfit in ("rags", "quiver") else 4
    for name in SIDES:
        x0, y0, w, h = LEG[name]
        for y in range(y0, y0 + rows):
            for x in range(x0, x0 + w):
                if p.rng.random() > 0.1:
                    p.put(x, y, rag())
        if p.rng.random() < 0.6:  # a bound shin
            for x in range(x0, x0 + w):
                p.put(x, y0 + 8, p.cloth(ROPE, 0.4)())
        for x in range(x0, x0 + w):
            p.put(x, y0 + 11, p.skin_px(0.72, 0.03))  # dirty feet
    fx, fy, fw, fh = LEG["front"]
    for x in (0, 2):
        p.put(fx + x, fy + 11, p.skin_px(0.5, 0.02))  # toes
    p.fill(LEG["bottom"], lambda: p.skin_px(0.6, 0.03))


def hat(p, kind):
    """The vanilla hat layer: a ragged hood for archers, a crude crown for the Great Goblin, else nothing."""
    if kind == "archer":
        hood = p.cloth(RAG, 0.3)
        p.fill(HAT["top"], hood)
        for name in ("right", "left", "back"):
            x0, y0, w, h = HAT[name]
            for y in range(y0, y0 + 6):
                for x in range(x0, x0 + w):
                    p.put(x, y, hood())
        x0, y0, w, h = HAT["front"]
        p.fill((x0, y0, w, 2), hood)
        for y in range(y0 + 2, y0 + 5):
            p.put(x0, y, hood())
            p.put(x0 + 7, y, hood())
    elif kind == "great":
        gold = lambda: rgb(GOLD[0], GOLD[1], 0.55 + p.rng.uniform(-0.06, 0.06))
        for name in SIDES:
            x0, y0, w, h = HAT[name]
            for x in range(x0, x0 + w):
                p.put(x, y0 + 1, gold())
                if (x - x0) % 3 == 0:
                    p.put(x, y0, gold())  # spikes
        p.put(HAT["front"][0] + 3, HAT["front"][1] + 1, rgb(0.0, 0.6, 0.5))  # a dull red stone
        p.put(HAT["front"][0] + 4, HAT["front"][1] + 1, rgb(0.0, 0.6, 0.5))


def paint(seed, kind, skin, outfit):
    p = Painter(seed, skin)
    head(p, kind)
    hat(p, kind)
    ears_nose(p)
    body(p, kind, outfit)
    arm(p, outfit)
    leg(p, outfit)
    return p.img


def main():
    os.makedirs(os.path.join(OUT, "goblin"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "archer"), exist_ok=True)
    outfits = ["bare", "strap", "rags", "fur"]
    for i in range(8):
        paint(100 + i, "warrior", SKINS[i % len(SKINS)], outfits[i % 4]).save(
            os.path.join(OUT, "goblin", f"{i}.png"))
    for i in range(4):
        paint(200 + i, "archer", SKINS[(i + 2) % len(SKINS)], "quiver").save(
            os.path.join(OUT, "archer", f"{i}.png"))
    paint(300, "great", GREAT, "great").save(os.path.join(OUT, "great_goblin.png"))
    print("goblin skins written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
