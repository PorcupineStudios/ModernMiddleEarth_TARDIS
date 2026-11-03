"""
LOTR's tree types as 1.21 tree features built from vanilla trunk and foliage placers, with LOTR's own logs and leaves:

  data/lotr_port/worldgen/configured_feature/tree/<type>.json   one tree
  data/lotr_port/worldgen/placed_feature/tree/<type>.json       the tree, only where a sapling could grow
  data/lotr_port/worldgen/configured_feature/trees/<biome>.json the biome's weighted mix (LOTR's weights)
  data/lotr_port/worldgen/placed_feature/trees/<biome>.json     how many per chunk (LOTR's treesPerChunk)

The shapes are stand-ins chosen by name (large, tall, dead, pine-like...); LOTR's hand-made shapes come later.
"""
import os
import re

from assets import NS, write_json

LARGE = ("LARGE", "PARTY", "EXTREME", "HUGE", "FANGORN", "BOUGHS", "WEIRWOOD", "HIDEOUT")
CONIFER = ("SPRUCE", "FIR", "PINE", "SHIRE_PINE", "LARCH", "CEDAR", "REDWOOD", "CYPRESS")
JUNGLE = ("JUNGLE", "MAHOGANY", "BANANA")
SAVANNA = ("ACACIA", "BAOBAB", "DATE_PALM", "PALM", "DRAGONBLOOD", "KANUKA")
VANILLA_WOOD = {"OAK": "oak", "BIRCH": "birch", "SPRUCE": "spruce", "JUNGLE": "jungle", "ACACIA": "acacia",
                "DARK_OAK": "dark_oak"}
SUFFIXES = ("_ITHILIEN_HIDEOUT", "_EXTREME_SAPLING", "_FANGORN_DEAD", "_MEGA_THIN", "_TALLER", "_TALL", "_LARGE",
            "_PARTY", "_FANGORN", "_SWAMP", "_DEAD", "_DESERT", "_SHRUB", "_THIN", "_MEGA", "_CLOUD", "_HUGE",
            "_EXTREME", "_BOUGHS", "_WEIRWOOD", "_WATER", "_MORDOR")
AIR = {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:air"}}


def state(name):
    return {"type": "minecraft:simple_state_provider", "state": {"Name": name}}


def base_wood(tree):
    tree = re.sub(r"_\d+$", "", tree)  # REDWOOD_2 ...: growth stages of one tree
    for suffix in SUFFIXES:
        if tree.endswith(suffix):
            return tree[: -len(suffix)]
    return tree


def placer(kind, base, a, b=0):
    return {"type": f"minecraft:{kind}_trunk_placer", "base_height": base, "height_rand_a": a, "height_rand_b": b}


class Trees:
    def __init__(self, gen, data_dir, report):
        self.gen = gen
        self.data = os.path.join(data_dir, NS, "worldgen")
        self.report = report
        self.block_ids = {b["id"] for b in gen.blocks}
        self.written = set()

    def blocks(self, tree):
        wood = base_wood(tree)
        if tree.startswith("CHARRED"):
            return f"{NS}:charred_wood", None
        if wood in VANILLA_WOOD:
            v = VANILLA_WOOD[wood]
            return f"minecraft:{v}_log", f"minecraft:{v}_leaves"
        if wood == "RED_OAK":
            return "minecraft:oak_log", f"{NS}:red_oak_leaves"
        name = {"LAIRELOSSE": "laireloss"}.get(wood, wood.lower())
        log, leaves = f"{name}_wood", f"{name}_leaves"
        if log not in self.block_ids or leaves not in self.block_ids:
            self.report["trees_with_oak_blocks"].append(tree)
            return "minecraft:oak_log", "minecraft:oak_leaves"
        return f"{NS}:{log}", f"{NS}:{leaves}"

    def config(self, tree):
        log, leaves = self.blocks(tree)
        wood = base_wood(tree)
        dead = "DEAD" in tree or tree.startswith("CHARRED") or leaves is None
        large = any(k in tree for k in LARGE)
        size = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}
        if wood in CONIFER or "SPRUCE" in tree:
            if "MEGA" in tree or wood == "REDWOOD" or large:
                trunk = placer("giant", 18 if wood == "REDWOOD" else 13, 2, 14)
                foliage = {"type": "minecraft:mega_pine_foliage_placer", "radius": 0, "offset": 0,
                           "crown_height": {"type": "minecraft:uniform", "min_inclusive": 13, "max_inclusive": 17}}
                size = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 1, "upper_size": 2}
            elif "THIN" in tree or wood in ("CYPRESS", "LARCH"):
                trunk = placer("straight", 7, 4)
                foliage = {"type": "minecraft:pine_foliage_placer", "radius": 1, "offset": 1,
                           "height": {"type": "minecraft:uniform", "min_inclusive": 3, "max_inclusive": 4}}
            else:
                trunk = placer("straight", 6 if wood != "SHIRE_PINE" else 8, 3, 1)
                foliage = {"type": "minecraft:spruce_foliage_placer",
                           "radius": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 3},
                           "offset": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 2},
                           "trunk_height": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}}
                size = {"type": "minecraft:two_layers_feature_size", "limit": 2, "lower_size": 0, "upper_size": 2}
        elif wood in JUNGLE:
            if large:
                trunk = placer("mega_jungle", 10, 2, 19)
                foliage = {"type": "minecraft:jungle_foliage_placer", "radius": 2, "offset": 0, "height": 2}
                size = {"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 1, "upper_size": 2}
            else:
                trunk = placer("straight", 5, 5)
                foliage = {"type": "minecraft:blob_foliage_placer", "radius": 2, "offset": 0, "height": 3}
        elif wood in SAVANNA:
            trunk = placer("forking", 5, 2, 2)
            foliage = {"type": "minecraft:acacia_foliage_placer", "radius": 2, "offset": 0}
        elif wood == "DARK_OAK" or wood == "MIRK_OAK":
            trunk = placer("dark_oak", 6, 2, 1)
            foliage = {"type": "minecraft:dark_oak_foliage_placer", "radius": 0, "offset": 0}
            size = {"type": "minecraft:three_layers_feature_size", "limit": 1, "upper_limit": 1, "lower_size": 0,
                    "middle_size": 1, "upper_size": 1}
        elif large:
            big = any(k in tree for k in ("EXTREME", "HUGE")) or wood == "MALLORN"
            trunk = placer("fancy", 14 if big else 8, 8 if big else 6, 6 if big else 0)
            foliage = {"type": "minecraft:fancy_foliage_placer", "radius": 2, "offset": 4, "height": 4}
            size = {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0,
                    "min_clipped_height": 4}
        elif "SHRUB" in tree:
            trunk = placer("straight", 1, 0)
            foliage = {"type": "minecraft:bush_foliage_placer", "radius": 2, "offset": 1, "height": 2}
            size = {"type": "minecraft:two_layers_feature_size", "limit": 0, "lower_size": 0, "upper_size": 0}
        else:
            tall = 2 if "TALLER" in tree else 1 if "TALL" in tree else 0
            trunk = placer("straight", 4 + 2 * tall, 2 + tall)
            foliage = {"type": "minecraft:blob_foliage_placer", "radius": 3 if wood == "WILLOW" else 2,
                       "offset": 0, "height": 3}
        if dead:
            trunk = placer("straight", 3, 3) if not large else trunk
        return {"type": "minecraft:tree", "config": {
            "trunk_provider": state(log),
            "trunk_placer": trunk,
            "foliage_provider": AIR if dead else state(leaves),
            "foliage_placer": foliage,
            "minimum_size": size,
            "dirt_provider": state("minecraft:dirt"),
            "decorators": [],
            "ignore_vines": True,
            "force_dirt": False,
        }}

    def tree(self, tree):
        name = tree.lower()
        if name not in self.written:  # each tree type is written once, shared by every biome that has it
            write_json(os.path.join(self.data, "configured_feature", "tree", name + ".json"), self.config(tree))
            write_json(os.path.join(self.data, "placed_feature", "tree", name + ".json"), {
                "feature": f"{NS}:tree/{name}",
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:would_survive", "state": {"Name": "minecraft:oak_sapling",
                                                                  "Properties": {"stage": "0"}}}}],
            })
            self.written.add(name)
        return f"{NS}:tree/{name}"

    def biome(self, biome_id, b):
        """The biome's tree placed feature id, or None when it has no trees."""
        trees = [(t["type"], t.get("weight", 1)) for t in b.get("trees", []) if t.get("type") and t["type"] != "NULL"]
        per_chunk = b.get("decorator", {}).get("treesPerChunk", 0)
        if not trees:
            return None
        trees.sort(key=lambda t: -t[1])
        remaining = sum(w for _, w in trees)
        entries = []
        for tree, weight in trees[:-1]:
            entries.append({"feature": self.tree(tree), "chance": round(weight / remaining, 4)})
            remaining -= weight
        config = {"type": "minecraft:random_selector",
                  "config": {"features": entries, "default": self.tree(trees[-1][0])}}
        write_json(os.path.join(self.data, "configured_feature", "trees", biome_id + ".json"), config)
        if per_chunk > 0:
            count = [{"type": "minecraft:count", "count": per_chunk}]
        else:
            count = [{"type": "minecraft:rarity_filter", "chance": 10}]  # LOTR's odd lone tree on open land
        write_json(os.path.join(self.data, "placed_feature", "trees", biome_id + ".json"), {
            "feature": f"{NS}:trees/{biome_id}",
            "placement": count + [
                {"type": "minecraft:in_square"},
                {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
                {"type": "minecraft:biome"},
            ],
        })
        return f"{NS}:trees/{biome_id}"
