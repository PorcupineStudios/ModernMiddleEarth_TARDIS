#!/usr/bin/env python3
"""
Sets the port's plants against LOTR's: LOTR's census (export/plants_census.json, the exporter's PlantCensus) and the
port's (lotr/run/plant_census_port.json, the dev tool -Dlotr_port.plantcensus=<patches>, world/PortPlantCensus), both
plants per chunk per biome. Only plants world.py's lotr_plants places are compared (not crops, vines, reeds or the
vanilla patches). Prints the ratio port / LOTR over all biomes and per kind, the biomes and plants furthest off, and
the PLANT_YIELD that would make the totals match (yield is plants per try: the port places per_chunk / (tries x
yield) patches, so a measured ratio r means the real yield is PLANT_YIELD x r).

    python3 converter/plant_compare.py [port census] [--json out.json]
"""
import json
import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import world  # noqa: E402
from assets import snake  # noqa: E402


class Ids:
    """world.py's own plant id mapping, without the rest of the generator."""

    def __init__(self):
        self.idtable = json.load(open(os.path.join(HERE, "idtable.json"), encoding="utf-8"))

    def plant_id(self, old):
        return world.VANILLA_PLANTS.get(old) or self.idtable.get(old)


def kind_of(old, rules):
    for kind in ("grass", "double_grass", "double_flowers"):
        if old in {world.PLANT_ALIASES.get(k.split("#")[0], k.split("#")[0]) + "#" + k.split("#")[1]
                   for k in rules.get(kind, {})}:
            return kind
    if old in {f"{f['block']}#{f['meta']}" for f in rules.get("flowers", [])}:
        return "flowers"
    return "extra"


def main():
    argv = sys.argv[1:]
    if "--json" in argv:
        i = argv.index("--json")
        argv = argv[:i] + argv[i + 2:]
    args = [a for a in argv if not a.startswith("--")]
    port_path = args[0] if args else os.path.join(HERE, "..", "lotr", "run", "plant_census_port.json")
    lotr = json.load(open(os.path.join(HERE, "export", "plants_census.json"), encoding="utf-8"))["biomes"]
    port = json.load(open(port_path, encoding="utf-8"))["biomes"]
    rules = {b["fields"]["biomeName"]: b.get("plants", {}) for b in
             json.load(open(os.path.join(HERE, "export", "world.json"), encoding="utf-8"))["biomes"]
             if "fields" in b}
    ids = Ids()
    rows = []  # biome, kind, plant, lotr per chunk, port per chunk
    for name, got in lotr.items():
        pid = f"lotr_port:{snake(name)}"
        if pid not in port or got["columns"] < 256 * 8 or port[pid]["columns"] < 256 * 8:
            continue
        mine = port[pid]["plants"]
        for old, v in got["plants"].items():
            if old.startswith(world.CENSUS_SKIP_PREFIX):
                continue
            new = ids.plant_id(old)
            if not new:
                continue
            rows.append((name, kind_of(old, rules.get(name, {})), new, v["per_chunk"],
                         mine.get(new, {}).get("per_chunk", 0.0)))
    if not rows:
        raise SystemExit("no biome in both censuses")
    # one plant id can come from two LOTR keys (vanilla + LOTR): sum per (biome, plant)
    merged = {}
    for name, kind, new, a, b in rows:
        k = (name, new)
        if k in merged:
            merged[k] = (kind, merged[k][1] + a, b)
        else:
            merged[k] = (kind, a, b)

    def ratio(pairs):
        a = sum(p[0] for p in pairs)
        b = sum(p[1] for p in pairs)
        return (b / a if a else float("nan")), a, b

    total = ratio([(a, b) for (_, _), (_, a, b) in merged.items()])
    print(f"biomes compared: {len({n for n, _ in merged})}")
    print(f"all plants: port/LOTR {total[0]:.3f} ({total[2]:.1f} vs {total[1]:.1f} plants per chunk summed)")
    out = {"all": total[0], "kinds": {}, "plants": {}, "biomes": {}}
    for kind in world.PLANT_KINDS:
        r = ratio([(a, b) for (_, _), (k, a, b) in merged.items() if k == kind])
        out["kinds"][kind] = r[0]
        print(f"  {kind:15s} {r[0]:.3f} ({r[2]:.1f} vs {r[1]:.1f})")
    per_plant = {}
    for (name, new), (kind, a, b) in merged.items():
        per_plant.setdefault(new, []).append((a, b))
    print("plants (LOTR >= 2 per chunk summed), port/LOTR:")
    for new, pairs in sorted(per_plant.items(), key=lambda e: -sum(p[0] for p in e[1])):
        r = ratio(pairs)
        out["plants"][new] = r[0]
        if r[1] >= 2:
            print(f"  {new:40s} {r[0]:.3f} ({r[2]:.1f} vs {r[1]:.1f}, {len(pairs)} biomes)")
    per_biome = {}
    for (name, new), (kind, a, b) in merged.items():
        per_biome.setdefault(name, []).append((a, b))
    logs = []
    for name, pairs in per_biome.items():
        r = ratio(pairs)
        out["biomes"][name] = r[0]
        if r[1] >= 1 and r[2] > 0:
            logs.append((math.log(r[0]), name, r))
    logs.sort()
    if logs:
        med = logs[len(logs) // 2]
        print(f"median biome ratio {math.exp(med[0]):.3f}; furthest under: "
              + ", ".join(f"{n} {r[0]:.2f}" for _, n, r in logs[:5]) + "; furthest over: "
              + ", ".join(f"{n} {r[0]:.2f}" for _, n, r in logs[-5:]))
    zero = sorted(n for n, pairs in per_biome.items() if ratio(pairs)[1] >= 1 and ratio(pairs)[2] == 0)
    if zero:
        print("biomes with LOTR plants but none in the port:", ", ".join(zero))
    print(f"PLANT_YIELD to match: {world.PLANT_YIELD * total[0]:.3f} (now {world.PLANT_YIELD})")
    if "--json" in sys.argv:
        path = sys.argv[sys.argv.index("--json") + 1]
        json.dump(out, open(path, "w"), indent=1)


if __name__ == "__main__":
    main()
