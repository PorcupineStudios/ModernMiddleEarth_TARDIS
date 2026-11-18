"""
Mountains and the cold north, with what vanilla gained after 1.7.10 (1.17-1.18's peaks, slopes and groves):

- Snow on LOTR's mountain tops (world.py mountain_rules: the biome's snow above its snow height) gets vanilla
  frozen-peaks surface patches: powder snow, packed ice and ice, in vanilla's own surface noises (powder_snow,
  packed_ice, ice). Thresholds: POWDER (a little sparser than vanilla's 0.35-0.6: Middle-earth's ranges are huge).
- Goats on the high ranges, rabbits and foxes in the cold lands, polar bears and ice spikes in Forodwaith, meadow
  flowers and tall grass on the foothills, amethyst geodes underground everywhere.
- Biome tags vanilla reads: snow foxes and white rabbits (spawns_snow_foxes, spawns_white_rabbits), gold rabbits in
  the deserts, is_mountain.

Runs on the generated data (``python mountains.py``; generate.py calls it after waters.py). Idempotent.
"""
import json
import os
import sys

from woodsets import OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
POWDER = (0.45, 0.58)  # vanilla frozen peaks / snowy slopes: 0.35 - 0.6
PACKED_ICE = (0.0, 0.2)  # vanilla frozen peaks
ICE = (0.0, 0.025)


def noise(name, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": f"minecraft:{name}", "min_threshold": lo, "max_threshold": hi}


def block(name):
    return {"type": "minecraft:block", "result_state": {"Name": f"minecraft:{name}"}}


def snow_top():
    """What a LOTR mountain's snow line becomes: vanilla frozen peaks' patches, else snow."""
    return {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": noise("powder_snow", *POWDER), "then_run": block("powder_snow")},
        {"type": "minecraft:condition", "if_true": noise("packed_ice", *PACKED_ICE), "then_run": block("packed_ice")},
        {"type": "minecraft:condition", "if_true": noise("ice", *ICE), "then_run": block("ice")},
        block("snow_block")]}


def spawner(t, weight, lo, hi):
    return {"type": f"minecraft:{t}", "weight": weight, "minCount": lo, "maxCount": hi}


# biome -> creatures added (vanilla's numbers for the matching vanilla biome)
GOATS = spawner("goat", 5, 1, 3)
RABBITS = spawner("rabbit", 4, 2, 3)
FOXES = spawner("fox", 8, 2, 4)
POLAR_BEARS = spawner("polar_bear", 1, 1, 2)
GEODE = "minecraft:amethyst_geode"  # step 2 (local_modifications), as in every vanilla overworld biome


class Mountains:
    def __init__(self, out=OUT):
        self.out = out
        self.data = os.path.join(out, "data", NS)
        self.biomes = os.path.join(self.data, "worldgen", "biome")

    def run(self):
        counts = {"snow_rules": self.snow(), "biomes": 0}
        tags = {"minecraft:spawns_snow_foxes": [], "minecraft:spawns_white_rabbits": [],
                "minecraft:spawns_gold_rabbits": [], "minecraft:is_mountain": []}
        for f in sorted(os.listdir(self.biomes)):
            name = f[:-5]
            path = os.path.join(self.biomes, f)
            b = json.load(open(path, encoding="utf-8"))
            temp = b["temperature"]
            high = name.endswith("_mountains") and "jungle" not in name
            foothills = name.endswith("_foothills")
            north = name.startswith("forodwaith") or name in ("tundra",)
            cold_land = temp <= 0.3 and not name.endswith("ocean")
            add = []
            if high and temp < 1.0:
                add.append(GOATS)
                tags["minecraft:is_mountain"].append(name)
            if foothills:
                tags["minecraft:is_mountain"].append(name)
                self.features(b, 9, ["minecraft:patch_tall_grass_2", "minecraft:flower_meadow"])
            if cold_land and not high:
                add.append(RABBITS)
                if name not in ("forodwaith_glacier",):
                    add.append(FOXES)
            if north:
                add.append(POLAR_BEARS)
                if name in ("forodwaith", "forodwaith_coast"):
                    self.features(b, 4, ["minecraft:ice_spike", "minecraft:ice_patch"])
            if temp <= 0.15 and not name.endswith("ocean"):
                tags["minecraft:spawns_snow_foxes"].append(name)
                tags["minecraft:spawns_white_rabbits"].append(name)
            if temp >= 1.5 and ("desert" in name or "harad" in name and "jungle" not in name):
                tags["minecraft:spawns_gold_rabbits"].append(name)
            have = {s["type"] for s in b["spawners"].get("creature", [])}
            for s in add:
                if s["type"] not in have:
                    b["spawners"].setdefault("creature", []).append(dict(s))
            self.features(b, 2, [GEODE])
            write_json(path, b)
            counts["biomes"] += 1
        for tag, names in tags.items():
            ns, path = tag.split(":")
            write_json(os.path.join(self.out, "data", ns, "tags", "worldgen", "biome", path + ".json"),
                       {"replace": False, "values": [f"{NS}:{n}" for n in names]})
        counts.update({t.split(":")[1]: len(v) for t, v in tags.items()})
        return counts

    @staticmethod
    def features(biome, step, feats):
        for f in feats:
            if f not in biome["features"][step]:
                biome["features"][step].append(f)

    def snow(self):
        """Mountain snow rules (LOTR's mountain noise -> snow block) become snow_top()."""
        path = os.path.join(self.data, "worldgen", "noise_settings", "middle_earth.json")
        ns = json.load(open(path, encoding="utf-8"))
        hits = 0

        def walk(n):
            nonlocal hits
            if isinstance(n, dict):
                t, then = n.get("if_true", {}), n.get("then_run")
                if (n.get("type") == "minecraft:condition" and t.get("type") == "minecraft:noise_threshold"
                        and t.get("noise") == f"{NS}:mountain" and isinstance(then, dict)
                        and then.get("result_state", {}).get("Name") == "minecraft:snow_block"):
                    n["then_run"] = snow_top()
                    hits += 1
                for v in n.values():
                    walk(v)
            elif isinstance(n, list):
                for v in n:
                    walk(v)
        walk(ns["surface_rule"])
        write_json(path, ns)
        return hits


if __name__ == "__main__":
    print(json.dumps(Mountains(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
