"""
New chunks get LOTR's animal density, as 1.7.10 had it: one 10% roll per chunk (vanilla's and LOTR's default
creature spawn chance) over the biome's whole creature list, where vanilla's animals (sheep, cows, LOTR's horse family
on the vanilla lists...) and LOTR's NPC animals (deer, aurochs, bears: npc_animals/) shared one weighted list.

Here they are two passes (vanilla's chunk-generation spawner, then NpcSpawner.populate), so each gets its weight's
share of the one roll: the biome's `creature_spawn_probability` = CHANCE x vanilla weight / all weight, and its
npc_animals `generation_chance` = CHANCE x LOTR weight / all weight. Before, each rolled the full 10%, about twice
LOTR's animals per chunk. Runs after land.py (which adds to the vanilla lists); rerun alone with
``python animal_balance.py``; idempotent.
"""
import json
import os
import sys

from renames import NS, OUT

CHANCE = 0.1


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def save(path, data):
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def run(out=OUT):
    biomes = os.path.join(out, "data", NS, "worldgen", "biome")
    animals = os.path.join(out, "data", NS, "npc_animals", NS)
    n = 0
    for f in sorted(os.listdir(biomes)):
        path = os.path.join(biomes, f)
        biome = load(path)
        vanilla = sum(e.get("weight", 0) for e in (biome.get("spawners") or {}).get("creature", []))
        table_path = os.path.join(animals, f)
        table = load(table_path) if os.path.exists(table_path) else None
        lotr = sum(e.get("weight", 0) for e in table["entries"]) if table else 0
        total = vanilla + lotr
        if total == 0:
            continue
        biome["creature_spawn_probability"] = round(CHANCE * vanilla / total, 5)
        save(path, biome)
        if table:
            table["generation_chance"] = round(CHANCE * lotr / total, 5)
            save(table_path, table)
        n += 1
    return {"biomes": n}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
