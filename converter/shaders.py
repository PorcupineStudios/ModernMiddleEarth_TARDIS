"""
Shader packs and LOTR's plants. Packs (Iris / OptiFine) wave leaves, grass and flowers in the wind by the vanilla
block names in their block.properties; a few (Photon) also read the vanilla tags minecraft:leaves, small_flowers,
saplings and crops. So every LOTR leaf, sapling, flower, grass, vine, bush, reed and crop gets
- an alias, its vanilla counterpart, in assets/lotr_port/shaders/block_aliases.json: client/ShaderAliases gives it
  that block's shader id when Iris builds a pack's id map (mixin/iris/IrisBlockIdMixin), so every pack waves it;
- the vanilla tag packs read, where it was missing (LOTR's single-block irises and hibiscus were only tall_flowers).
Chandeliers are "plant" blocks in LOTR's class table but hang still: not aliased.

Runs on the generated content (``python shaders.py``); generate.py calls it after tags.py. Idempotent.
"""
import json
import os
import sys

from woodsets import OUT, write_json

NS = "lotr_port"
# by port block kind
BY_KIND = {"leaves": "minecraft:oak_leaves", "sapling": "minecraft:oak_sapling",
           "berry_bush": "minecraft:sweet_berry_bush", "reed": "minecraft:sugar_cane", "corn": "minecraft:sugar_cane",
           "crop": "minecraft:wheat"}
# "plant" blocks by id; any other plant: poppy if it is a flower (minecraft:flowers), else short grass
BY_ID = {
    "riverweed": "minecraft:seagrass", "mirkwood_vines": "minecraft:vine", "willow_vines": "minecraft:vine",
    "morgul_shroom": "minecraft:brown_mushroom", "web_of_ungoliant": "minecraft:cobweb",
    "dead_marsh_plant": "minecraft:dead_bush", "corrupt_mallorn_sapling": "minecraft:oak_sapling",
    "pipe_weed_plant": "minecraft:fern", "tears_of_yavanna": "minecraft:poppy", "elfsong": "minecraft:poppy",
    "sunfruit": "minecraft:poppy", "moonflower": "minecraft:poppy",
}
STILL = ("_chandelier",)
# single-block flowers packs only find by tag in small_flowers
SMALL_FLOWER_EXTRA = ["black_iris", "yellow_iris", "hibiscus", "flame_of_harad"]


def tag_values(out, name):
    path = os.path.join(out, "data", "minecraft", "tags", "block", name + ".json")
    if not os.path.exists(path):
        return path, {"replace": False, "values": []}
    return path, json.load(open(path, encoding="utf-8"))


def run(out=OUT):
    blocks = json.load(open(os.path.join(out, NS, "content", "blocks.json"), encoding="utf-8"))
    blocks = blocks if isinstance(blocks, list) else blocks["blocks"]
    _, flowers = tag_values(out, "flowers")
    flower_ids = {v if isinstance(v, str) else v["id"] for v in flowers["values"]}
    aliases = {}
    for b in blocks:
        bid, kind = b["id"], b["kind"]
        if kind in BY_KIND:
            aliases[f"{NS}:{bid}"] = BY_KIND[kind]
        elif kind == "plant" and not bid.endswith(STILL):
            aliases[f"{NS}:{bid}"] = BY_ID.get(bid) or (
                "minecraft:poppy" if f"{NS}:{bid}" in flower_ids else "minecraft:short_grass")
    write_json(os.path.join(out, "assets", NS, "shaders", "block_aliases.json"),
               {"aliases": dict(sorted(aliases.items()))})
    path, small = tag_values(out, "small_flowers")
    have = {v if isinstance(v, str) else v["id"] for v in small["values"]}
    known = {f"{NS}:{b['id']}" for b in blocks}
    added = [f"{NS}:{x}" for x in SMALL_FLOWER_EXTRA if f"{NS}:{x}" in known and f"{NS}:{x}" not in have]
    if added:
        small["values"] = small["values"] + added
        write_json(path, small)
    return {"aliases": len(aliases), "small_flowers_added": len(added)}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
