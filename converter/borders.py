"""Borders: the original territory map and each faction's people for land it takes.

Writes, from the generated NPC spawn tables (run after npcs/world):
- data/lotr_port/border_base/biomes.json: {"biomes": {biome: faction}, "fill": [water biomes]}, the faction whose people spawn most in
  each biome (summed entry weight); a biome whose leading people only come out at night (and whose table doesn't
  allow hostiles by day) is wilderness and isn't listed. "fill" lists the rivers and lakes, which take the holder of
  the land around them so borders run across rivers, not round them. This is the original map's territory; play never changes it (the server keeps
  conquests as an overlay, lotr/docs/borders.md).
- data/lotr_port/border_spawns/<faction ns>/<faction path>.json: npc_spawns-format tables of a faction's own people,
  merged over the biomes it holds, for spawning in land it takes from someone else (LOTR's conquest spawn lists).

- data/lotr_port/conquest_terrain/biomes.json: {"biomes": {biome: factor}}, how much a biome multiplies the
  defence of land in it: 1 + hilliness / HILL_PER_STEP, times RANGE_FACTOR in the mountain ranges, plus KEYWORD
  bonuses (forests, marshes); at most MAX_FACTOR. Misty Mountains ~6, the Shire ~1.3.

Run alone: python borders.py
"""
import glob
import json
import os
from collections import defaultdict

from woodsets import OUT, NS, write_json

DATA = os.path.join(OUT, "data", NS)
# factions that never own land on the map (LOTR's hostile and unaligned lists)
NO_LAND = {f"{NS}:hostile", f"{NS}:unaligned", f"{NS}:utumno"}


HILL_PER_STEP = 10.0   # hilliness (map.json variation) per +1 defence factor
RANGE_FACTOR = 1.5     # extra for the mountain ranges (map.json ranges.biomes)
KEYWORDS = {"forest": 0.5, "wood": 0.4, "fangorn": 0.6, "mirkwood": 0.6, "marsh": 0.6, "swamp": 0.6,
            "fen": 0.5, "mountain": 0.5, "hills": 0.3, "highlands": 0.3}
MIN_FACTOR = 0.5
# map biomes that are water inside the land (take the holder around them), not the sea
WATER = ("river", "lake")
NOT_WATER = ("riverbank", "ocean")
MAX_FACTOR = 6.0


def terrain(out):
    with open(os.path.join(out, NS, "map", "map.json"), encoding="utf-8") as f:
        m = json.load(f)
    ranges = set(m.get("ranges", {}).get("biomes", {}))
    factors = {}
    for b in m["biomes"]:
        bid = b["id"]
        if bid == f"{NS}:river" or "ocean" in bid or "lake" in bid:
            factors[bid] = 1.0
            continue
        f_ = 1 + b.get("variation", 0) / HILL_PER_STEP
        if bid in ranges:
            f_ *= RANGE_FACTOR
        f_ += sum(v for k, v in KEYWORDS.items() if k in bid.split(":", 1)[1])
        factors[bid] = round(max(MIN_FACTOR, min(MAX_FACTOR, f_)), 2)
    write_json(os.path.join(out, "data", NS, "conquest_terrain", "biomes.json"), {"biomes": dict(sorted(factors.items()))})
    return len(factors)


def npc_factions():
    out = {}
    for path in glob.glob(os.path.join(DATA, "npc", "*.json")):
        with open(path, encoding="utf-8") as f:
            npc = json.load(f)
        if npc.get("faction"):
            out[f"{NS}:" + os.path.basename(path)[:-5]] = npc["faction"]
    return out


def run(out=OUT):
    factions = npc_factions()
    base = {}
    people = defaultdict(lambda: defaultdict(lambda: {"weight": 0, "min": [], "max": [], "dark": False}))
    tables = defaultdict(list)
    for path in sorted(glob.glob(os.path.join(DATA, "npc_spawns", "*", "*.json"))):
        ns = os.path.basename(os.path.dirname(path))
        biome = f"{ns}:" + os.path.basename(path)[:-5]
        with open(path, encoding="utf-8") as f:
            table = json.load(f)
        weight = defaultdict(int)
        for e in table.get("entries", []):
            fac = factions.get(e["npc"])
            if fac and fac not in NO_LAND:
                weight[fac] += e.get("weight", 1)
        if not weight:
            continue
        owner = max(sorted(weight), key=lambda k: weight[k])
        own = [e for e in table.get("entries", []) if factions.get(e["npc"]) == owner]
        if all(e.get("dark", False) for e in own) and not table.get("hostiles_in_day", False):
            continue  # only night raiders (orcs roaming Eriador): wilderness, nobody's land
        if table.get("wilderness", False):
            continue  # marked wild by its maker (regions.py: Mirkwood's spider nests are nobody's land)
        base[biome] = owner
        tables[owner].append(table)
        for e in table.get("entries", []):
            if factions.get(e["npc"]) == owner:
                p = people[owner][e["npc"]]
                p["weight"] += e.get("weight", 1)
                p["min"].append(e.get("min", 1))
                p["max"].append(e.get("max", 1))
                p["dark"] = p["dark"] or e.get("dark", False)
    # rivers and lakes lie within borders: they take the holder of the land around them (lotr border/WaterFill)
    with open(os.path.join(out, NS, "map", "map.json"), encoding="utf-8") as f:
        map_biomes = [b["id"] for b in json.load(f)["biomes"]]
    fill = sorted(b for b in map_biomes if b not in base and any(k in b.split(":", 1)[1] for k in WATER)
                  and not any(k in b for k in NOT_WATER))
    write_json(os.path.join(DATA, "border_base", "biomes.json"), {"biomes": dict(sorted(base.items())), "fill": fill})
    for fac, entries in sorted(people.items()):
        ts = tables[fac]
        n = len(ts)
        rows = []
        for npc, p in sorted(entries.items()):
            rows.append({"npc": npc, "weight": max(1, round(p["weight"] / n)),
                         "min": round(sum(p["min"]) / len(p["min"])), "max": round(sum(p["max"]) / len(p["max"])),
                         "dark": p["dark"]})
        table = {"entries": rows,
                 "cap": round(sum(t.get("cap", 8) for t in ts) / n),
                 "chance": round(sum(t.get("chance", 0.25) for t in ts) / n, 3),
                 "interval": round(sum(t.get("interval", 100) for t in ts) / n)}
        fns, fpath = fac.split(":", 1)
        write_json(os.path.join(DATA, "border_spawns", fns, fpath + ".json"), table)
    return {"biomes_with_owner": len(base), "factions_with_land": len(people), "terrain_factors": terrain(out)}


if __name__ == "__main__":
    print(json.dumps(run()))
