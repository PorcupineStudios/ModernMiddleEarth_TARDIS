"""
Life in Middle-earth's water (1.7.10 had none: no fish, no seagrass, kelp or coral, one ocean for every sea).

- The sea is split by latitude into five sea biomes (map.json "seas", MiddleEarthMap.seaAt): frozen (the Ice Bay of
  Forochel), cold (the far north), temperate (lotr_port:ocean: Lindon to Lond Daer), lukewarm (Belfalas, Ethir
  Anduin) and warm (Umbar, Harad). Each starts as LOTR's ocean and takes its fish, squid and dolphins, its sea floor
  (seagrass, kelp, coral, sea pickles, icebergs, sand/clay/gravel disks) and its water colours from vanilla 1.21.1's
  matching ocean (read from the client jar), so it reads like vanilla's seas.
- Rivers and lakes get vanilla river life (salmon, squid, river seagrass, bed disks); marshes lily pads and swamp
  seagrass (not the Dead Marshes: nothing lives there); beaches turtles; every biome bats and glow squid underground.
- Everywhere LOTR's data lists lotr_port:ocean (biome tags, structures, surface rules), the new seas are listed too.

Runs on the generated data (``python waters.py``); generate.py calls it after the rest. Idempotent: the seas are
rewritten from ocean.json each time, and additions are made only where missing.
"""
import copy
import json
import os
import sys
import zipfile

from woodsets import MC_1211, OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
OCEAN = f"{NS}:ocean"
# sea biome -> (vanilla template, below this block z; None = the rest). Mithlond z 0, Lond Daer 35000, Dol Amroth
# 77000, Umbar 123000; the Ice Bay of Forochel lies north of -45000.
SEAS = [
    ("frozen_ocean", "frozen_ocean", -45000),
    ("cold_ocean", "cold_ocean", -12000),
    ("ocean", "ocean", 60000),
    ("lukewarm_ocean", "lukewarm_ocean", 110000),
    ("warm_ocean", "warm_ocean", None),
]
SEA_WOBBLE = 3000  # blocks the borders between seas meander north and south
WATER_FEATURES = ("seagrass", "kelp", "warm_ocean_vegetation", "sea_pickle", "iceberg", "blue_ice", "disk_")
MARSH_WORDS = ("marsh", "swamp", "fen", "mere")
LIFELESS = {"dead_marshes"}
NAMES = {"frozen_ocean": "Frozen Sea", "cold_ocean": "Cold Sea", "lukewarm_ocean": "Lukewarm Sea",
         "warm_ocean": "Warm Sea"}


class Waters:
    def __init__(self, out=OUT):
        self.out = out
        self.data = os.path.join(out, "data", NS)
        self.biomes = os.path.join(self.data, "worldgen", "biome")
        self.jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))

    def vanilla(self, name):
        return json.loads(self.jar.read(f"data/minecraft/worldgen/biome/{name}.json"))

    def load(self, name):
        return json.load(open(os.path.join(self.biomes, name + ".json"), encoding="utf-8"))

    def save(self, name, biome):
        write_json(os.path.join(self.biomes, name + ".json"), biome)

    @staticmethod
    def add_spawners(biome, template, categories):
        for cat in categories:
            have = {s["type"] for s in biome["spawners"].get(cat, [])}
            for s in template["spawners"].get(cat, []):
                if s["type"] not in have:
                    biome["spawners"].setdefault(cat, []).append(dict(s))

    @staticmethod
    def add_features(biome, template, words=WATER_FEATURES):
        for step, feats in enumerate(template["features"]):
            for f in feats:
                if any(w in f for w in words) and f not in biome["features"][step]:
                    biome["features"][step].append(f)

    def run(self):
        ocean = self.load("ocean")
        # LOTR's ocean as written by world.py: drop what an earlier run added, so the seas are rebuilt the same
        base = copy.deepcopy(ocean)
        for cat in ("water_creature", "water_ambient", "underground_water_creature", "ambient"):
            base["spawners"].pop(cat, None)
        base["features"] = [[f for f in step if not any(w in f for w in WATER_FEATURES)] for step in base["features"]]
        base.pop("temperature_modifier", None)
        counts = {"seas": 0, "rivers_lakes": 0, "marshes": 0, "beaches": 0, "all": 0}
        for name, template_name, _ in SEAS:
            t = self.vanilla(template_name)
            sea = copy.deepcopy(base)
            self.add_spawners(sea, t, ("water_creature", "water_ambient"))
            self.add_features(sea, t)
            for k in ("water_color", "water_fog_color"):
                sea["effects"][k] = t["effects"][k]
            if t.get("temperature_modifier") == "frozen":
                sea["temperature"] = t["temperature"]
                sea["temperature_modifier"] = "frozen"
            self.save(name, sea)
            counts["seas"] += 1
        river, swamp, beach = self.vanilla("river"), self.vanilla("swamp"), self.vanilla("beach")
        for f in sorted(os.listdir(self.biomes)):
            name = f[:-5]
            b = self.load(name)
            if name in ("river", "lake") or name.endswith("_lake") or name.endswith("riverbank"):
                self.add_spawners(b, river, ("water_creature", "water_ambient"))
                self.add_features(b, river)
                counts["rivers_lakes"] += 1
            elif any(w in name for w in MARSH_WORDS) and name not in LIFELESS:
                self.add_features(b, swamp, ("waterlily", "seagrass_swamp", "disk_clay"))
                counts["marshes"] += 1
            elif name.startswith("beach") or name.endswith("_coast"):
                if b["temperature"] >= 0.5:  # turtles nest on warm and temperate beaches, not Forochel's
                    self.add_spawners(b, beach, ("creature",))
                self.add_features(b, beach, ("disk_",))
                counts["beaches"] += 1
            # every biome: bats in caves, glow squid in dark underground water (vanilla has both everywhere)
            self.add_spawners(b, river, ("ambient", "underground_water_creature"))
            self.save(name, b)
            counts["all"] += 1
        counts["lists"] = self.list_seas()
        self.map_seas()
        self.lang()
        return counts

    def list_seas(self):
        """Wherever generated data lists lotr_port:ocean, list the other seas next to it."""
        extra = [f"{NS}:{s[0]}" for s in SEAS if s[0] != "ocean"]
        changed = 0
        for dirpath, _, files in os.walk(os.path.join(self.out, "data")):
            for f in files:
                if not f.endswith(".json"):
                    continue
                path = os.path.join(dirpath, f)
                text = open(path, encoding="utf-8").read()
                if f'"{OCEAN}"' not in text:
                    continue
                data = json.loads(text)
                if self._add_after(data, extra):
                    write_json(path, data)
                    changed += 1
        main_tag = os.path.join(HERE, "..", "lotr", "src", "main", "resources", "data", NS, "tags", "worldgen", "biome",
                                "middle_earth.json")
        tag = json.load(open(main_tag, encoding="utf-8"))
        if self._add_after(tag, extra):
            write_json(main_tag, tag)
            changed += 1
        return changed

    def _add_after(self, node, extra):
        changed = False
        if isinstance(node, list):
            if OCEAN in node:
                for x in reversed(extra):
                    if x not in node:
                        node.insert(node.index(OCEAN) + 1, x)
                        changed = True
            for v in node:
                changed |= self._add_after(v, extra)
        elif isinstance(node, dict):
            for v in node.values():
                changed |= self._add_after(v, extra)
        return changed

    def map_seas(self):
        path = os.path.join(self.out, NS, "map", "map.json")
        m = json.load(open(path, encoding="utf-8"))
        m["seas"] = {"wobble": SEA_WOBBLE, "zones": [dict({"biome": f"{NS}:{n}"}, **({"below_z": z} if z is not None
                                                                                    else {})) for n, _, z in SEAS]}
        write_json(path, m)

    def lang(self):
        path = os.path.join(self.out, "assets", NS, "lang", "en_us.json")
        lang = json.load(open(path, encoding="utf-8"))
        for k, v in NAMES.items():
            lang[f"biome.{NS}.{k}"] = v
        write_json(path, lang)


if __name__ == "__main__":
    print(json.dumps(Waters(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
