"""Throwaway: sets Middle-earth's top to TOP (argv[1]) in the generated data, for benchmarks."""
import json, sys, re
top = int(sys.argv[1])
G = "lotr/src/generated/resources"
p = f"{G}/data/lotr_port/dimension_type/middle_earth.json"
d = json.load(open(p)); d["height"] = d["logical_height"] = top + 64; json.dump(d, open(p, "w"))
p = f"{G}/data/lotr_port/worldgen/noise_settings/middle_earth.json"
d = json.load(open(p)); d["noise"]["height"] = top + 64
def walk(o):
    if isinstance(o, dict):
        if o.get("type") == "minecraft:y_clamped_gradient":
            o["to_y"] = top; o["to_value"] = -top
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
walk(d); json.dump(d, open(p, "w"))
p = f"{G}/lotr_port/map/map.json"
d = json.load(open(p)); d["ranges"]["ceiling"] = top - 30
if "noise_band" in d: d["noise_band"]["top"] = top
json.dump(d, open(p, "w"))
p = "lotr/src/main/java/com/porcupinestudios/lotrport/world/map/MapTerrain.java"
s = open(p).read(); s = s.replace("case HEIGHT -> 640;", f"case HEIGHT -> {top};").replace("? 640 : 256", f"? {top} : 256")
open(p, "w").write(s)
print("top", top)
