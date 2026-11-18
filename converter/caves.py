"""
Varied caves under Middle-earth (Aaron 2026-11-17: lush caves, "make sure there's still variety underground within
each country and that it's not just 1 consecutive lush cave").

Middle-earth's biomes are one per map column at every height, so a vanilla cave biome would fill a whole region's
underground. Instead, vanilla's cave decorations are placed only inside scattered *pockets* (placement filter
lotr_port:cave_pocket, CavePocket.java): a few flattened balls per 112-block cell, of a kind per cell, deep enough
under the ground. Every country keeps plain caves between them:

- lush pockets (moss, azalea, clay pools with dripleaf, cave vines with glow berries, spore blossoms; axolotls in
  their water) under the green, wet lands;
- dripstone pockets (dripstone clusters, large and pointed dripstone) under the dry, rocky and mountain lands;
- both under lands that are both; glow lichen everywhere, as in vanilla.

The features are vanilla's own configured features with vanilla's placement, counts scaled up by COUNT_SCALE
(vanilla's counts fill a whole cave biome; here most tries fall outside a pocket) and the pocket filter put before
the floor/ceiling scans so missed tries cost little. Runs on the generated data (``python caves.py``; generate.py
calls it last). Idempotent.
"""
import copy
import json
import os
import sys
import zipfile

from land import normalize_order
from woodsets import MC_1211, OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
POCKETS = {
    # kind: (chance per cell, radius min, max, {our name: (vanilla placed feature, step, count scale)})
    "lush": (0.25, 14, 30, {
        "lush_cave_clay": ("lush_caves_clay", 9, 6), "lush_cave_vegetation": ("lush_caves_vegetation", 9, 6),
        "lush_cave_ceiling": ("lush_caves_ceiling_vegetation", 9, 6), "lush_cave_vines": ("cave_vines", 9, 6),
        "lush_cave_spore_blossom": ("spore_blossom", 9, 6), "lush_cave_azalea": ("rooted_azalea_tree", 9, 6)}),
    "dripstone": (0.25, 14, 30, {
        "dripstone_pocket_large": ("large_dripstone", 2, 4), "dripstone_pocket_cluster": ("dripstone_cluster", 7, 4),
        "dripstone_pocket_pointed": ("pointed_dripstone", 7, 4)}),
}
HEIGHT = {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 16}, "max_inclusive": {"absolute": 320}}
LIFELESS = {"mirkwood_corrupted", "dol_guldur", "fangorn_wasteland", "ithilien_wasteland", "dead_marshes", "mordor",
            "gorgoroth", "udun", "nan_ungol", "morgul_vale"}
AXOLOTLS = {"type": "minecraft:axolotl", "weight": 10, "minCount": 4, "maxCount": 6}


class Caves:
    def __init__(self, out=OUT):
        self.out = out
        self.data = os.path.join(out, "data", NS)
        self.biomes = os.path.join(self.data, "worldgen", "biome")
        self.jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))

    def vanilla_placed(self, name):
        return json.loads(self.jar.read(f"data/minecraft/worldgen/placed_feature/{name}.json"))

    def write_features(self):
        for kind, (chance, rmin, rmax, feats) in POCKETS.items():
            pocket = {"type": f"{NS}:cave_pocket", "kind": kind, "chance": chance, "min_radius": rmin,
                      "max_radius": rmax}
            for ours, (vanilla, _, k) in feats.items():
                p = copy.deepcopy(self.vanilla_placed(vanilla))
                # k times vanilla's tries: a count first (counts are capped at 256 each), each copy then spread by
                # vanilla's own in_square and height_range
                out = [{"type": "minecraft:count", "count": k}]
                for mod in p["placement"]:
                    if mod["type"] == "minecraft:height_range":
                        out += [dict(mod, height=HEIGHT), pocket]  # the pocket test before any scan
                        continue
                    out.append(mod)
                if pocket not in out:
                    out.insert(0, pocket)
                p["placement"] = out
                write_json(os.path.join(self.data, "worldgen", "placed_feature", ours + ".json"), p)

    def kinds(self, name, b):
        """Which pocket kinds this land has underground."""
        if name.endswith("ocean") or name in ("river", "lake", "beach", "beach_gravel", "beach_white"):
            return []
        trees = any("trees/" in x for step in b["features"] for x in step)
        out = []
        if trees and b["downfall"] >= 0.75 and 0.5 <= b["temperature"] <= 1.3 and name not in LIFELESS:
            out.append("lush")
        rocky = any(w in name for w in ("mountains", "hills", "foothills", "downs", "highlands", "emyn", "fells"))
        if b["downfall"] <= 0.45 or rocky:
            out.append("dripstone")
        return out

    def run(self):
        self.write_features()
        counts = {"lush": 0, "dripstone": 0, "both": 0, "plain": 0}
        all_ours = {f"{NS}:{n}" for _, _, _, feats in POCKETS.values() for n in feats}
        for f in sorted(os.listdir(self.biomes)):
            name = f[:-5]
            path = os.path.join(self.biomes, f)
            b = json.load(open(path, encoding="utf-8"))
            b["features"] = [[x for x in step if x not in all_ours] for step in b["features"]]
            kinds = self.kinds(name, b)
            for kind in kinds:
                for ours, (_, step, _) in POCKETS[kind][3].items():
                    b["features"][step].append(f"{NS}:{ours}")
            if "lush" in kinds:
                if not any(s["type"] == AXOLOTLS["type"] for s in b["spawners"].get("axolotls", [])):
                    b["spawners"].setdefault("axolotls", []).append(dict(AXOLOTLS))
            else:
                b["spawners"].pop("axolotls", None)
            if "minecraft:glow_lichen" not in b["features"][9]:
                b["features"][9].append("minecraft:glow_lichen")
            write_json(path, b)
            counts["both" if len(kinds) == 2 else kinds[0] if kinds else "plain"] += 1
        normalize_order(self.biomes)
        return counts


if __name__ == "__main__":
    print(json.dumps(Caves(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
