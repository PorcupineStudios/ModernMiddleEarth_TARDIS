#!/usr/bin/env python3
"""
Post-step (2026-11-25): rewrites only LOTR's ground-plant features (world.py lotr_plants) and each biome's list of
them after PLANT_YIELD / PLANT_YIELDS change, without the jar or a full converter run: the biome's run of
plant_<kind>/ features is replaced in place by the list at the current yields, and plant features no biome uses any
more are deleted. Idempotent; generate.py gives the same result in a full run.

    python3 converter/plant_rates.py
"""
import json
import os
import sys
import types

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import world  # noqa: E402
from assets import NS, write_json  # noqa: E402

OUT = os.path.normpath(os.path.join(HERE, "..", "lotr", "src", "generated", "resources"))
KINDS = [f"plant_{k}" for k in world.PLANT_KINDS]


def references():
    """Every lotr_port id named in the generated data."""
    refs = set()
    for root, _, files in os.walk(os.path.join(OUT, "data")):
        for f in files:
            if f.endswith(".json"):
                text = open(os.path.join(root, f), encoding="utf-8").read()
                refs.update(part.split('"')[0] for part in text.split(f'"{NS}:')[1:])
    return refs


def is_plant(feature):
    return feature.split(":", 1)[-1].split("/")[0] in KINDS


def main(args):
    gen = types.SimpleNamespace(idtable=json.load(open(os.path.join(HERE, "idtable.json"), encoding="utf-8")),
                                report={})
    export = json.load(open(os.path.join(HERE, "export", "world.json"), encoding="utf-8"))
    w = world.World(gen, export, OUT, None)
    gen_dir = os.path.join(OUT, "data", NS, "worldgen")
    changed = 0
    for b in export["biomes"]:
        path = os.path.join(gen_dir, "biome", w.biome_id(b) + ".json")
        if not os.path.exists(path):
            continue  # a biome the port doesn't generate
        plants = w.lotr_plants(b)
        data = json.load(open(path, encoding="utf-8"))
        for step in data["features"]:
            at = [i for i, f in enumerate(step) if is_plant(f)]
            if not at:
                continue
            if at != list(range(at[0], at[-1] + 1)):
                print(f"{w.biome_id(b)}: plant features not in one run; left as it is")
                break
            if step[at[0]:at[-1] + 1] != plants:
                step[at[0]:at[-1] + 1] = plants
                write_json(path, data)
                changed += 1
            break
        else:
            if plants:
                print(f"{w.biome_id(b)}: no plant features in the biome file to replace; left as it is")
    # plant features nothing refers to any more: placed ones first, then the configured ones only they used
    removed = 0
    for subs in (["placed_feature/" + k for k in KINDS], ["configured_feature/plant_patch"]):
        refs = references()
        for sub in subs:
            folder = os.path.join(gen_dir, sub)
            if not os.path.isdir(folder):
                continue
            for f in sorted(os.listdir(folder)):
                if sub.split("/", 1)[1] + "/" + f[:-5] not in refs:
                    print("remove", sub, f) if "-v" in args else None
                    os.remove(os.path.join(folder, f))
                    removed += 1
    print(f"{changed} biomes' plants changed, {removed} unused plant features removed")


if __name__ == "__main__":
    main(sys.argv[1:])
