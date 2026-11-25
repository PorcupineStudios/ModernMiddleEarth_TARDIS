"""
Flower mixes as patch features: one random_patch whose plant is picked per try from a weighted list (only where it
can live, on air). Used for vanilla's flowers outside LOTR's own rules, kept sparse (Aaron 2026-11-25: "tone down the
vanilla flowers but keep them in sparse patches"), and for the Shire's flower forest, which stays full of flowers,
half LOTR's and half vanilla's. LOTR's own flowers per biome come from its rules and census (world.py lotr_plants).
"""
import os

NS = "lotr_port"

# Vanilla's meadow and flower-forest flowers, sparse: on the foothills (mountains.py) in place of flower_meadow.
VANILLA_SPARSE = {"name": "vanilla_flowers_sparse", "rarity": 6, "tries": 12, "flowers": {
    "minecraft:dandelion": 3, "minecraft:poppy": 3, "minecraft:cornflower": 2, "minecraft:oxeye_daisy": 2,
    "minecraft:azure_bluet": 2, "minecraft:allium": 1, "minecraft:lily_of_the_valley": 1}}

# The Shire's flower forest (the "meadows" variant, LOTR's FLOWERS variant): dense, LOTR's flowers with vanilla's.
SHIRE_FLOWER_FOREST = {"name": "shire_flower_forest", "count": 5, "tries": 48, "flowers": {
    f"{NS}:marigold": 5, f"{NS}:bluebell": 4, f"{NS}:lavender": 4, f"{NS}:shire_heather": 3, f"{NS}:flax_plant": 1,
    "minecraft:dandelion": 3, "minecraft:poppy": 3, "minecraft:cornflower": 3, "minecraft:oxeye_daisy": 2,
    "minecraft:azure_bluet": 2, "minecraft:allium": 2, "minecraft:lily_of_the_valley": 2, "minecraft:red_tulip": 1,
    "minecraft:orange_tulip": 1, "minecraft:white_tulip": 1, "minecraft:pink_tulip": 1}}
SHIRE_FLOWER_FOREST_TALL = {"name": "shire_flower_forest_tall", "count": 1, "tries": 16, "flowers": {
    "minecraft:lilac": 2, "minecraft:rose_bush": 2, "minecraft:peony": 2}}


def configured(mix):
    """The configured feature of a mix."""
    entries = [{"weight": w, "data": {"Name": b}} for b, w in mix["flowers"].items()]
    return {"type": "minecraft:random_patch", "config": {"tries": mix["tries"], "xz_spread": 7, "y_spread": 3,
            "feature": {"feature": {"type": "minecraft:simple_block", "config": {"to_place": {
                "type": "minecraft:weighted_state_provider", "entries": entries}}},
                "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                    "type": "minecraft:matching_blocks", "blocks": "minecraft:air"}}]}}}


def count(mix):
    """Patches per chunk: count, or a 1 in rarity chance."""
    if "rarity" in mix:
        return [{"type": "minecraft:rarity_filter", "chance": mix["rarity"]}]
    return [{"type": "minecraft:count", "count": mix["count"]}]


def write_sparse(data_dir, write_json):
    """Writes lotr_port:vanilla_flowers_sparse (configured + placed); returns its id."""
    mix = VANILLA_SPARSE
    gen = os.path.join(data_dir, NS, "worldgen")
    write_json(os.path.join(gen, "configured_feature", mix["name"] + ".json"), configured(mix))
    write_json(os.path.join(gen, "placed_feature", mix["name"] + ".json"), {
        "feature": f"{NS}:{mix['name']}", "placement": count(mix) + [
            {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
            {"type": "minecraft:biome"}]})
    return f"{NS}:{mix['name']}"
