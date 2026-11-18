"""
Stonecutter recipes for every LOTR stone family (1.7.10 had no stonecutter; LOTR's chisel carved text into stone
instead: see lotr/CLAUDE.md "Carved signs").

The families come from LOTR's own crafting recipes: a recipe whose only ingredient is one stone block A and whose
result is a stone block B (4 Gondor rock -> 4 Gondor brick, 3 bricks -> 6 slabs, 2 slabs -> a carved brick...) is an
edge A -> B. A full block can then be cut, as vanilla's stonecutter does, into every full block it reaches (1 each)
and their slabs (2), stairs (1) and walls (1). Mossy and cracked forms aren't single-ingredient recipes (vines, a
furnace), so they stay out, as in vanilla. Slabs and stairs are never inputs.

"Stone" is what converter/tags.py matched to vanilla stone, bricks, cobblestone, pillars, sandstone or terracotta
(tags_report.json), so run it after tags.py; generate.py does. Output: data/lotr_port/recipe/own/stonecutting/.
"""
import collections
import json
import os
import shutil
import sys

from woodsets import OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
STONE = {"stone", "stone_bricks", "cobblestone", "quartz_pillar", "terracotta", "sandstone_slab", "sandstone_stairs",
         "stone_brick_slab", "stone_brick_stairs", "stone_brick_wall", "cobblestone_slab", "cobblestone_stairs",
         "cobblestone_wall"}
YIELD = {"slab": 2, "stairs": 1, "wall": 1}


def crafting_recipes(root):
    """Every crafting recipe (faction table ones unwrapped) under data/lotr_port/recipe, own/ excluded."""
    base = os.path.join(root, "data", NS, "recipe")
    for dirpath, _, files in os.walk(base):
        if os.path.relpath(dirpath, base).split(os.sep)[0] in ("own", "create"):
            continue
        for f in files:
            r = json.load(open(os.path.join(dirpath, f), encoding="utf-8"))
            r = r.get("recipe", r)
            if r.get("type") in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
                yield r


def single_input(r):
    """(item id, how many) if the recipe's only ingredient is one item, else None."""
    if r["type"] == "minecraft:crafting_shaped":
        keys = r["key"]
        used = [c for row in r["pattern"] for c in row if c != " "]
        ids = {json.dumps(keys[c], sort_keys=True) for c in used}
        if len(ids) != 1:
            return None
        ing = keys[used[0]]
        n = len(used)
    else:
        ings = r["ingredients"]
        ids = {json.dumps(i, sort_keys=True) for i in ings}
        if len(ids) != 1:
            return None
        ing, n = ings[0], len(ings)
    if not isinstance(ing, dict) or "item" not in ing:
        return None
    return ing["item"], n


def run(out=OUT):
    report = json.load(open(os.path.join(HERE, "tags_report.json"), encoding="utf-8"))["blocks"]
    blocks = {b["id"]: b for b in json.load(open(os.path.join(out, NS, "content", "blocks.json"), encoding="utf-8"))}
    stone = {i for i, r in report.items() if r["analogue"] in STONE and blocks.get(i, {}).get("item", "block") != "none"}
    vanilla_in = collections.defaultdict(set)  # LOTR slabs, stairs and walls of vanilla blocks (stone wall...)
    kind = {i: blocks[i].get("kind", "cube") for i in stone}
    edges = collections.defaultdict(set)
    for r in crafting_recipes(out):
        src = single_input(r)
        if not src or not src[0]:
            continue
        b = r["result"]["id"].split(":", 1)[1]
        if src[0].startswith("minecraft:"):
            if b in stone and kind[b] in YIELD:
                vanilla_in[src[0]].add(b)
            continue
        a = src[0].split(":", 1)[1]
        if a in stone and b in stone and a != b and kind[a] in ("cube", "pillar"):
            edges[a].add(b)
    target = os.path.join(out, "data", NS, "recipe", "own", "stonecutting")
    shutil.rmtree(target, ignore_errors=True)
    count = 0
    for a in sorted(i for i in stone if kind[i] in ("cube", "pillar")):
        reach, todo = set(), [a]
        while todo:  # full blocks reachable from a
            for b in edges.get(todo.pop(), ()):
                if b not in reach and b != a and kind[b] in ("cube", "pillar"):
                    reach.add(b)
                    todo.append(b)
        outs = {b: 1 for b in reach}
        for full in reach | {a}:
            for v in edges.get(full, ()):
                if kind[v] in YIELD:
                    outs[v] = YIELD[kind[v]]
        for b, n in sorted(outs.items()):
            write_json(os.path.join(target, f"{b}_from_{a}.json"), {
                "type": "minecraft:stonecutting", "ingredient": {"item": f"{NS}:{a}"},
                "result": {"id": f"{NS}:{b}", "count": n}})
            count += 1
    for a, outs in sorted(vanilla_in.items()):
        for b in sorted(outs):
            write_json(os.path.join(target, f"{b}_from_{a.split(':')[1]}.json"), {
                "type": "minecraft:stonecutting", "ingredient": {"item": a},
                "result": {"id": f"{NS}:{b}", "count": YIELD[kind[b]]}})
            count += 1
    return {"stone_blocks": len(stone), "families": sum(1 for a in edges), "recipes": count}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
