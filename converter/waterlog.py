"""
Which LOTR blocks hold water (waterlogging came in 1.13; 1.7.10 had none). A LOTR block waterlogs when its vanilla
kin does: chests (vanilla's chest), gates and portcullises (iron bars, trapdoors), chandeliers and bird cages
(lanterns), orc chains (chains), stalactites (pointed dripstone), set-down vessels and plates (decorated pots,
candles). Marked "waterlog": true in blocks.json; content/Waterlog.java gives those blocks the waterlogged property.
Blocks on vanilla's own classes (slabs, stairs, walls, fences, panes, trapdoors, ladders, leaves, signs) waterlog
already and need no mark. Structures placed in water keep it in marked blocks (vanilla's template placement).

Runs on the generated content (``python waterlog.py``); generate.py calls it after tags.py. Idempotent.
"""
import json
import os
import sys

from tags import Tagger
from woodsets import OUT, write_json

NS = "lotr_port"
# LOTR block classes (exported, LOTRBlock...) that hold water, and machines (blocks.json "machine")
CLASSES = ("Chest", "Gate", "Chandelier", "OrcChain", "BirdCage", "Stalactite", "AleHorn", "ClayMug", "Goblet",
           "SkullCup", "Mug", "Plate")
MACHINES = {"chest", "plate"}
# only these port kinds are built on the classes that can take the property (ShapedBlock, FacingBlock, GateBlock)
KINDS = {"shaped", "gate", "plant", "cube"}


def run(out=OUT):
    path = os.path.join(out, NS, "content", "blocks.json")
    blocks = json.load(open(path, encoding="utf-8"))
    info = Tagger(out).lotr
    marked = []
    for b in blocks:
        cls = info.get(b["id"], {}).get("cls", "")
        water = b.get("kind", "cube") in KINDS and not b.get("full") and (  # dwarven doors are whole blocks
            b.get("machine") in MACHINES or any(c in cls for c in CLASSES))
        if water:
            b["waterlog"] = True
            marked.append(b["id"])
        else:
            b.pop("waterlog", None)
    write_json(path, blocks)
    return {"waterlogged": len(marked), "sample": marked[:8]}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
