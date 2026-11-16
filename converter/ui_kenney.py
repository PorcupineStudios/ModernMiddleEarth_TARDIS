"""
GUI sprites from Kenney's "UI Pack - RPG Expansion" (CC0, https://kenney.nl/assets/ui-pack-rpg-expansion): panels,
inset panels, buttons, text fields, scroll bars, and tabs drawn in the same style (the pack has none).

Usage: python3 ui_kenney.py <the pack's png folder> [out folder]
Default out: lotr/src/main/resources/assets/lotr_port/textures/gui/sprites/ui

Kenney's panels have small notches along their edges, and Minecraft's nine-slice tiles the edges, so a notch would
repeat. Each sprite is rebuilt from the original's clean corners plus one clean column and row repeated, at 2x the
size it is drawn (a 32 px sprite is 16 GUI units; the .mcmeta sets the nine-slice size and borders in GUI units),
which keeps the edges smooth at GUI scale 2-4.
"""
import json
import os
import sys

from PIL import Image, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'lotr', 'src', 'main', 'resources', 'assets', 'lotr_port', 'textures', 'gui', 'sprites',
                   'ui')
DENSITY = 2  # texels per GUI unit


def clean9(im, top, bottom, side, cx, cy, w, h):
    """A w x h copy of im: corners kept (top/bottom rows, side columns), edges and middle from column cx and row cy."""
    src = im.convert('RGBA')
    sw, sh = src.size
    out = Image.new('RGBA', (w, h))
    # corners
    out.paste(src.crop((0, 0, side, top)), (0, 0))
    out.paste(src.crop((sw - side, 0, sw, top)), (w - side, 0))
    out.paste(src.crop((0, sh - bottom, side, sh)), (0, h - bottom))
    out.paste(src.crop((sw - side, sh - bottom, sw, sh)), (w - side, h - bottom))
    # edges: one clean column (top/bottom) and one clean row (left/right) repeated
    col_top = src.crop((cx, 0, cx + 1, top))
    col_bottom = src.crop((cx, sh - bottom, cx + 1, sh))
    row_left = src.crop((0, cy, side, cy + 1))
    row_right = src.crop((sw - side, cy, sw, cy + 1))
    for x in range(side, w - side):
        out.paste(col_top, (x, 0))
        out.paste(col_bottom, (x, h - bottom))
    for y in range(top, h - bottom):
        out.paste(row_left, (0, y))
        out.paste(row_right, (w - side, y))
    fill = src.getpixel((cx, cy))
    for x in range(side, w - side):
        for y in range(top, h - bottom):
            out.putpixel((x, y), fill)
    return out


def tab(panel, selected):
    """A tab: the panel's rounded top and sides; selected tabs are open at the bottom (they join the panel below),
    others end in the panel's top rim so they sit behind it."""
    body = clean9(panel, 10, 10, 10, 35, 45, 40, 28)
    out = Image.new('RGBA', body.size)
    w, h = body.size
    out.paste(body.crop((0, 0, w, h - 10)), (0, 0))
    # stretch the sides down to the bottom edge
    for y in range(h - 10, h):
        out.paste(body.crop((0, h - 11, w, h - 10)), (0, y))
    if not selected:
        rim = panel.convert('RGBA').crop((35, 0, 36, 4))  # the panel's top rim, rows 0-3
        for x in range(w):
            out.paste(rim, (x, h - 4))
    return out


def brighter(im, f):
    rgb = ImageEnhance.Brightness(im.convert('RGBA')).enhance(f)
    rgb.putalpha(im.getchannel('A'))
    return rgb


def save(out_dir, name, im, border, size=None):
    """Writes name.png and its nine-slice .mcmeta (border = left, top, right, bottom in texels)."""
    im.save(os.path.join(out_dir, name + '.png'))
    w, h = size or im.size
    l, t, r, b = border
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": w // DENSITY, "height": h // DENSITY,
                                "border": {"left": l // DENSITY, "top": t // DENSITY, "right": r // DENSITY,
                                           "bottom": b // DENSITY}}}}
    with open(os.path.join(out_dir, name + '.png.mcmeta'), 'w') as f:
        json.dump(meta, f, indent=2)


def main():
    src = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else OUT
    os.makedirs(out, exist_ok=True)

    def load(n):
        return Image.open(os.path.join(src, n + '.png'))

    # panels: 100 x 100 originals, rim about 10 px
    for colour in ['beige', 'beigeLight', 'brown', 'blue']:
        save(out, 'panel_' + colour.lower(), clean9(load('panel_' + colour), 10, 10, 10, 35, 45, 40, 40),
             (10, 10, 10, 10))
    # inset panels (lists, text fields, scroll tracks): 93 x 94, rim about 8 px
    for colour in ['beige', 'beigeLight', 'brown', 'blue']:
        save(out, 'inset_' + colour.lower(), clean9(load('panelInset_' + colour), 8, 8, 8, 30, 40, 32, 32),
             (8, 8, 8, 8))
    # buttons: square 45 x 49 with a 4 px shadow under; pressed 45 x 45
    normal = clean9(load('buttonSquare_beige'), 10, 14, 10, 22, 22, 40, 40)
    save(out, 'button', normal, (10, 10, 10, 14))
    save(out, 'button_hover', brighter(normal, 1.08), (10, 10, 10, 14))
    pressed = Image.new('RGBA', (40, 40))
    pressed.paste(clean9(load('buttonSquare_beige_pressed'), 10, 10, 10, 22, 22, 40, 36), (0, 4))
    save(out, 'button_pressed', pressed, (10, 14, 10, 10))
    save(out, 'button_disabled', clean9(load('buttonSquare_grey'), 10, 14, 10, 22, 22, 40, 40), (10, 10, 10, 14))
    # tabs in the panel's style
    save(out, 'tab_selected', tab(load('panel_beige'), True), (10, 10, 10, 2))
    save(out, 'tab', tab(load('panel_brown'), False), (10, 10, 10, 4))
    save(out, 'tab_hover', brighter(tab(load('panel_brown'), False), 1.12), (10, 10, 10, 4))
    # scroll bar: a beige inset track and a small beige button as the handle
    save(out, 'scroll_track', clean9(load('panelInset_beige'), 8, 8, 8, 30, 40, 16, 32), (8, 8, 8, 8))
    save(out, 'scroll_handle', clean9(load('buttonSquare_beige'), 6, 8, 6, 22, 22, 16, 24), (6, 6, 6, 8))
    # list rows: a brown button box, so each row reads as something to click; lighter when hovered or picked
    row = clean9(load('buttonSquare_brown'), 10, 14, 10, 22, 22, 40, 40)
    save(out, 'row', row, (10, 10, 10, 14))
    save(out, 'row_hover', brighter(row, 1.15), (10, 10, 10, 14))
    # checkboxes for the filter drop-down: a dark brown inset box, with Kenney's beige check when ticked
    box = clean9(load('panelInset_brown'), 6, 6, 6, 30, 40, 24, 24)
    box.save(os.path.join(out, 'checkbox.png'))
    ticked = box.copy()
    check = load('iconCheck_beige').convert('RGBA')
    ticked.alpha_composite(check, ((24 - check.width) // 2, (24 - check.height) // 2))
    ticked.save(os.path.join(out, 'checkbox_checked.png'))
    # the drop-down's arrow: Kenney's right arrow turned to point down
    load('arrowBrown_right').convert('RGBA').rotate(-90, expand=True).save(os.path.join(out, 'arrow_down.png'))
    # small icons kept as they are (not nine-slice)
    for n in ['iconCheck_beige', 'iconCross_beige', 'iconCircle_beige', 'arrowBeige_left', 'arrowBeige_right',
              'arrowBrown_left', 'arrowBrown_right']:
        load(n).convert('RGBA').save(os.path.join(out, n.lower() + '.png'))
    with open(os.path.join(out, 'CREDITS.txt'), 'w') as f:
        f.write("UI Pack - RPG Expansion by Kenney (www.kenney.nl), CC0 1.0 (public domain).\n"
                "https://kenney.nl/assets/ui-pack-rpg-expansion\n"
                "Rebuilt for nine-slice and tabs added in the same style by converter/ui_kenney.py.\n")
    print('wrote', len(os.listdir(out)), 'files to', out)


if __name__ == '__main__':
    main()
