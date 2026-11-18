"""Borders: the original territory map and each faction's people for land it takes.

Writes, from the generated NPC spawn tables (run after npcs/world):
- data/lotr_port/border_base/biomes.json: {"biomes": {biome: faction}}, the faction whose people spawn most in
  each biome (summed entry weight); a biome whose leading people only come out at night (and whose table doesn't
  allow hostiles by day) is wilderness and isn't listed. This is the original map's territory; play never changes it (the server keeps
  conquests as an overlay, lotr/docs/borders.md).
- data/lotr_port/border_spawns/<faction ns>/<faction path>.json: npc_spawns-format tables of a faction's own people,
  merged over the biomes it holds, for spawning in land it takes from someone else (LOTR's conquest spawn lists).

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
        base[biome] = owner
        tables[owner].append(table)
        for e in table.get("entries", []):
            if factions.get(e["npc"]) == owner:
                p = people[owner][e["npc"]]
                p["weight"] += e.get("weight", 1)
                p["min"].append(e.get("min", 1))
                p["max"].append(e.get("max", 1))
                p["dark"] = p["dark"] or e.get("dark", False)
    write_json(os.path.join(DATA, "border_base", "biomes.json"), {"biomes": dict(sorted(base.items()))})
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
    return {"biomes_with_owner": len(base), "factions_with_land": len(people)}


if __name__ == "__main__":
    print(json.dumps(run()))
