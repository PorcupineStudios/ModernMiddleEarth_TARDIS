"""
Fallen leaves of LOTR's grey vanilla-leaf icons (oak, dark oak, acacia) drew grey (Aaron 2026-11-25: "fix oak fallen
leaves to have color"): client/LotrClient registers a foliage colour for them, but their exported models had no
tintindex, so the colour was never applied. This post-step (idempotent; generate.py runs it after wilds.py) puts
tintindex 0 on every face of those models.
"""
import json
import os

from woodsets import OUT, write_json

NS = "lotr_port"
TINTED = ["fallen_oak_leaves", "fallen_dark_oak_leaves", "fallen_acacia_leaves"]  # = LotrClient.TINTED_FALLEN


def run(out=OUT):
    done = 0
    for name in TINTED:
        path = os.path.join(out, "assets", NS, "models", "block", name + ".json")
        with open(path, encoding="utf-8") as f:
            model = json.load(f)
        for element in model.get("elements", []):
            for face in element["faces"].values():
                face["tintindex"] = 0
        write_json(path, model)
        done += 1
    return {"models": done}


if __name__ == "__main__":
    print(json.dumps(run()))
