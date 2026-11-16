# Minecraft's own 64x32 -> 64x64 skin conversion (left arm and leg = mirrored right ones), for LOTR's orc skins.
import sys
from PIL import Image
src, dst = sys.argv[1], sys.argv[2]
old = Image.open(src).convert("RGBA")
new = Image.new("RGBA", (64, 64)); new.paste(old, (0, 0))
def copy(x, y, dx, dy, w, h):
    new.paste(old.crop((x, y, x + w, y + h)).transpose(Image.FLIP_LEFT_RIGHT), (x + dx, y + dy))
for (ox, oy) in ((0, 16), (40, 16)):  # leg, arm: same face moves as vanilla's processLegacySkin
    dx, dy = (16, 32) if ox == 0 else (-8, 32)
    copy(ox+4, oy, dx, dy, 4, 4); copy(ox+8, oy, dx, dy, 4, 4)
    copy(ox+0, oy+4, dx+8, dy, 4, 12); copy(ox+4, oy+4, dx, dy, 4, 12)
    copy(ox+8, oy+4, dx-8, dy, 4, 12); copy(ox+12, oy+4, dx, dy, 4, 12)
new.save(dst)
