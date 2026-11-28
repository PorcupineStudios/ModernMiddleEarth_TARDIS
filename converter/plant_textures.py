#!/usr/bin/env python3
"""
Post-step (2026-11-25, Aaron: "get the right textures for all the plants, the thistles look off"): re-copies every LOTR
plant's textures from the jar and rewrites its block model with generate.py's own rules, without running the whole
converter: grass-tinted plants get LOTR's untinted overlay (flowery grass's flowers, the thistle's head, wheatgrass's
ears: <icon>_overlay) where the jar has one, clovers their petals and stem. Idempotent; generate.py does the same in
a full run. Prints, per plant, the jar textures named after its icon (names only: the log is public).

    python3 converter/plant_textures.py     (needs converter/libs/LOTRMod v36.15.jar)
"""
import json
import os
import sys
import types

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import generate as g  # noqa: E402
from assets import Jar, Textures  # noqa: E402

TEX = "assets/lotr/textures/blocks/"


def main():
    jar = Jar()
    report = {}
    fake = types.SimpleNamespace(jar=jar, tex=Textures(jar, g.ASSETS, report))
    for name in ("clover", "grass_overlay", "tinted_overlay", "bm", "bs", "item_model", "CLOVER_HEIGHT",
                 "CLOVER_SIZE"):
        a = getattr(g.Gen, name)
        setattr(fake, name, a.__get__(fake) if callable(a) else a)
    idtable = json.load(open(os.path.join(HERE, "idtable.json"), encoding="utf-8"))
    content = os.path.join(g.OUT, "lotr_port", "content", "blocks.json")
    entries = {e["id"]: e for e in json.load(open(content, encoding="utf-8"))}
    textures = sorted(n[len(TEX):-4] for n in jar.names if n.startswith(TEX) and n.endswith(".png"))
    done = []
    for b in g.load("blocks.json"):
        classes = " ".join(b["classes"])
        tinted = (b.get("blockColor", 0xFFFFFF) & 0xFFFFFF) != 0xFFFFFF
        for v in b["variants"]:
            new = (idtable.get(f"{b['id']}#{v['meta']}") or "").split(":", 1)[-1].split("[")[0]
            e = entries.get(new)
            if not e or e.get("kind") != "plant":
                continue
            icon = v["icons"][2]
            side = fake.tex.block(icon)
            if not side:
                print(f"{new}: no texture for {icon}")
                continue
            path = icon.split(":", 1)[-1]
            print(f"{new}: {icon} -> {side}; jar has {[t for t in textures if t.startswith(path)]}")
            item_icon = fake.tex.item(v.get("itemIcon")) or side
            if "LOTRBlockClover" in classes:
                fake.clover(new, side, 4 if v["meta"] == 1 else 3)
                legacy = os.path.join(g.ASSETS, "models", "block", new + ".json")
                if os.path.exists(legacy):
                    os.remove(legacy)
            else:
                overlay = fake.grass_overlay(icon) if tinted else None
                if overlay and item_icon != side:
                    overlay_item = None  # an icon of its own: no overlay on it
                else:
                    overlay_item = overlay
                if overlay:
                    m = fake.tinted_overlay(new, side, overlay)
                    print(f"  overlay {overlay}")
                else:
                    m = fake.bm(new, "minecraft:block/tinted_cross" if tinted else "minecraft:block/cross",
                                {"cross": side}, {"render_type": g.CUTOUT})
                fake.bs(new, {"variants": {"": {"model": m}}})
                fake.item_model(new, layer=item_icon, overlay=overlay_item)
                done.append(new)
                continue
            fake.item_model(new, layer=item_icon)
            done.append(new)
    print(f"{len(done)} plants rewritten; missing textures: {report.get('missing_textures', [])}")


if __name__ == "__main__":
    main()
