"""
Leaves drop like vanilla's oak leaves, and LOTR's fruit trees drop their fruit (2026-11-19: before, leaves dropped
nothing without shears or silk touch: no saplings, sticks or fruit).

Every block of kind `leaves` gets vanilla oak leaves' loot table: the leaves with shears or silk touch; otherwise a
sapling (SAPLING chances by fortune), sticks (STICK chances) and, for the trees in FRUIT, their fruit (FRUIT_CHANCES:
LOTR's fruit trees are where fruit comes from, so it drops more often than vanilla oak's apples). Runs on top of the
generated content; rerun alone with ``python leaf_drops.py``; rewrites the leaves' loot tables every run.
"""
import json
import os
import sys

from renames import NS, OUT

# vanilla 1.21.1 oak leaves (fortune 0..4)
SAPLING = [0.05, 0.0625, 0.083333336, 0.1]
STICK = [0.02, 0.022222223, 0.025, 0.033333335, 0.1]
# fruit: four times vanilla's apple rate (0.005 ...), fortune raises it as vanilla's does
FRUIT_CHANCES = [0.02, 0.0222, 0.025, 0.0333, 0.1]
# leaves -> fruit items (ids); several = an even pick
FRUIT = {
    "apple_leaves": ["minecraft:apple", f"{NS}:apple"], "pear_leaves": [f"{NS}:pear"],
    "cherry_leaves": [f"{NS}:cherries"], "mango_leaves": [f"{NS}:mango"], "banana_leaves": [f"{NS}:banana"],
    "lemon_leaves": [f"{NS}:lemon"], "orange_leaves": [f"{NS}:orange"], "lime_leaves": [f"{NS}:lime"],
    "olive_leaves": [f"{NS}:olives"], "almond_leaves": [f"{NS}:almond"], "plum_leaves": [f"{NS}:plum"],
    "pomegranate_leaves": [f"{NS}:pomegranate"], "date_palm_leaves": [f"{NS}:date"],
    "chestnut_leaves": [f"{NS}:conker"],
}


def shears_or_silk():
    return {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
        {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}


def bonus(chances):
    return {"condition": "minecraft:table_bonus", "enchantment": "minecraft:fortune", "chances": chances}


def run(out=OUT):
    content = os.path.join(out, NS, "content")
    with open(os.path.join(content, "blocks.json"), encoding="utf-8") as f:
        blocks = {b["id"]: b for b in json.load(f)}
    with open(os.path.join(content, "items.json"), encoding="utf-8") as f:
        items = {f"{NS}:{i['id']}" for i in json.load(f)}
    loot = os.path.join(out, "data", NS, "loot_table", "blocks")
    n, fruit = 0, 0
    for bid, b in sorted(blocks.items()):
        if b.get("kind") != "leaves":
            continue
        not_cut = {"condition": "minecraft:inverted", "term": shears_or_silk()}
        first = [{"type": "minecraft:item", "name": f"{NS}:{bid}", "conditions": [shears_or_silk()]}]
        sapling = bid[: -len("_leaves")] + "_sapling"
        if sapling in blocks:
            first.append({"type": "minecraft:item", "name": f"{NS}:{sapling}",
                          "conditions": [{"condition": "minecraft:survives_explosion"}, bonus(SAPLING)]})
        pools = [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:alternatives", "children": first}]},
                 {"rolls": 1, "bonus_rolls": 0, "conditions": [not_cut], "entries": [{
                     "type": "minecraft:item", "name": "minecraft:stick", "conditions": [bonus(STICK)],
                     "functions": [{"function": "minecraft:set_count",
                                    "count": {"type": "minecraft:uniform", "min": 1, "max": 2}},
                                   {"function": "minecraft:explosion_decay"}]}]}]
        drops = [i for i in FRUIT.get(bid, []) if i.startswith("minecraft:") or i in items]
        if drops:
            pools.append({"rolls": 1, "bonus_rolls": 0,
                          "conditions": [not_cut, {"condition": "minecraft:survives_explosion"}, bonus(FRUIT_CHANCES)],
                          "entries": [{"type": "minecraft:item", "name": i} for i in drops]})
            fruit += 1
        with open(os.path.join(loot, bid + ".json"), "w", encoding="utf-8") as f:
            json.dump({"type": "minecraft:block", "pools": pools, "random_sequence": f"{NS}:blocks/{bid}"}, f,
                      indent=2)
            f.write("\n")
        n += 1
    return {"leaves": n, "fruit_trees": fruit}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
