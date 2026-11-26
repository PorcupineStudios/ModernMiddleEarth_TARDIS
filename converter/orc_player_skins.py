"""Fallback skins for orc and Uruk players (2026-11-26).

Orc and Uruk players are drawn on the 64x64 orc and uruk bodies (lotr client/ShapedModel) in their own skin made
orcish (client/RaceSkinRemap, rules in assets/lotr_port/race_skin/orc.json and uruk.json). When a skin can't be
converted (not loaded yet, not 64x64, or the client turned conversion off) the race's own skin is drawn instead; LOTR's
orc skins are 64x32, so this pads one of each to 64x64 (the humanoid's top half is the same in both layouts).
Writes lotr/src/main/resources/assets/lotr_port/textures/entity/orc/player/orc.png and uruk.png. Run: python orc_player_skins.py
"""
import os

from PIL import Image

HERE = os.path.dirname(__file__)
LOTR = os.path.join(HERE, "..", "lotr", "src", "generated", "resources", "assets", "lotr_port", "textures", "entity",
                    "orc")
OUT = os.path.join(HERE, "..", "lotr", "src", "main", "resources", "assets", "lotr_port", "textures", "entity", "orc",
                   "player")


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, source in (("orc", "orc/0.png"), ("uruk", "uruk_hai/0.png")):
        old = Image.open(os.path.join(LOTR, source)).convert("RGBA")
        tall = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        tall.paste(old, (0, 0))
        tall.save(os.path.join(OUT, name + ".png"))
    print("orc and Uruk player skins written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
