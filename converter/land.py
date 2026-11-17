"""
Animals and plants vanilla gained after 1.7.10, placed where they fit Middle-earth (Aaron 2026-11-17: new animals may
be added). Spawn numbers are vanilla's for its matching biome.

- Bees: wild nests on the trees (feature lotr_port:bee_nest, BeeNestFeature) - often in the Shire, Bree, Dorwinion,
  Ithilien and the other flower lands (BEES_COMMON), now and then in every other green land with trees.
  LOTR's flowers are in #minecraft:flowers (tags.py), so the bees pollinate them.
- Frogs in the wetlands (warm frogs in the south, temperate elsewhere; biome tags vanilla reads), lily pads there.
- Foxes in the northern and western woods, rabbits on the grasslands, parrots and ocelots in the Far Harad jungle,
  armadillos on the Far Harad savanna.
- Jungle: bamboo, vines and melons. Woods: brown and red mushrooms. Green lands: pumpkins. The northern taiga:
  sweet berries.

Runs on the generated data (``python land.py``; generate.py calls it after mountains.py). Idempotent.
"""
import json
import os
import sys

from woodsets import OUT, write_json

NS = "lotr_port"
BEES_COMMON = {"shire", "shire_woodlands", "white_downs", "breeland", "dorwinion", "dorwinion_hills", "ithilien",
               "ithilien_hills", "lossarnach", "lebennin", "anduin_vale", "rivendell", "lothlorien", "eriador",
               "imloth_melui", "pelennor", "dor_en_ernil", "lindon", "blackroot_vale", "rohan"}
BEE_RARITY = {"common": 3, "rare": 24}  # 1 in N chunks tries for a nest
FROGS = {"shire_marshes": "temperate", "long_marshes": "temperate", "nindalf": "temperate",
         "midgewater": "temperate", "gladden_fields": "temperate", "swanfleet": "temperate",
         "entwash_mouth": "temperate", "anduin_mouth": "temperate", "far_harad_swamp": "warm",
         "nurn_marshes": "warm"}
FOXES = {"old_forest", "mirkwood_north", "woodland_realm", "chetwood", "trollshaws", "eryn_vorn", "rhun_forest",
         "rhun_red_forest", "rhun_island_forest", "fangorn", "gondor_woodlands", "rohan_woodlands", "shire_woodlands",
         "lindon_woodlands", "eregion", "angle", "dunland", "lone_lands_hills", "pukel"}
RABBITS = {"shire", "shire_moors", "white_downs", "eriador", "eriador_downs", "rohan", "breeland", "lone_lands",
           "minhiriath", "enedwaith", "wold", "wilderland", "wilderland_north", "rhun_land_steppe", "barrow_downs",
           "pelennor", "lamedon", "anduin_hills", "dale", "adornland", "tower_hills", "lindon"}
JUNGLE = {"far_harad_jungle", "far_harad_jungle_edge", "far_harad_cloud_forest", "far_harad_jungle_lake",
          "tauredain_clearing", "far_harad_forest"}
SAVANNA = {"far_harad", "far_harad_bushland", "far_harad_bushland_hills", "far_harad_arid", "far_harad_arid_hills"}
TAIGA = {"taiga", "coldfells"}
LILY_PADS = set(FROGS) - {"far_harad_swamp"} | {"far_harad_swamp"}


def spawner(t, weight, lo, hi):
    return {"type": f"minecraft:{t}", "weight": weight, "minCount": lo, "maxCount": hi}


# Every feature the modern post-steps add (waters.py, mountains.py, land.py, caves.py), in one global order. Minecraft
# refuses biomes that list shared features in different orders, so after the steps each biome's features are LOTR's
# own first (as world.py wrote them), then these in this order (normalize_order).
MODERN_ORDER = [
    "minecraft:amethyst_geode", "minecraft:iceberg_packed", "minecraft:iceberg_blue", "minecraft:blue_ice",
    "minecraft:ice_spike", "minecraft:ice_patch", "minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel",
    f"{NS}:lush_cave_clay", f"{NS}:lush_cave_vegetation", f"{NS}:lush_cave_ceiling", f"{NS}:cave_spore_blossom",
    f"{NS}:cave_lichen", f"{NS}:cave_lichen_rare",
    "minecraft:warm_ocean_vegetation", "minecraft:seagrass_cold", "minecraft:seagrass_normal", "minecraft:seagrass_warm",
    "minecraft:seagrass_deep_warm", "minecraft:seagrass_simple", "minecraft:seagrass_river", "minecraft:seagrass_swamp",
    "minecraft:kelp_cold", "minecraft:kelp_warm", "minecraft:sea_pickle", "minecraft:patch_waterlily",
    "minecraft:bamboo_light", f"{NS}:bee_nests_common", f"{NS}:bee_nests_rare", "minecraft:patch_tall_grass_2",
    "minecraft:flower_meadow", "minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal",
    "minecraft:patch_pumpkin", "minecraft:patch_melon", "minecraft:vines", "minecraft:patch_berry_common",
]


def normalize_order(biomes_dir):
    """LOTR's features first in each step, then the modern ones in MODERN_ORDER (see there)."""
    rank = {f: i for i, f in enumerate(MODERN_ORDER)}
    for f in sorted(os.listdir(biomes_dir)):
        path = os.path.join(biomes_dir, f)
        b = json.load(open(path, encoding="utf-8"))
        steps = [[x for x in step if x not in rank] + sorted((x for x in step if x in rank), key=rank.get)
                 for step in b["features"]]
        if steps != b["features"]:
            b["features"] = steps
            write_json(path, b)


class Land:
    def __init__(self, out=OUT):
        self.out = out
        self.data = os.path.join(out, "data", NS)
        self.biomes = os.path.join(self.data, "worldgen", "biome")

    def bee_features(self):
        wg = os.path.join(self.data, "worldgen")
        write_json(os.path.join(wg, "configured_feature", "bee_nest.json"),
                   {"type": f"{NS}:bee_nest", "config": {"depth": 24, "min_bees": 2, "max_bees": 3}})
        for name, rarity in BEE_RARITY.items():
            write_json(os.path.join(wg, "placed_feature", f"bee_nests_{name}.json"), {
                "feature": f"{NS}:bee_nest", "placement": [
                    {"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
                    {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}]})

    def run(self):
        self.bee_features()
        counts = {"bees_common": 0, "bees_rare": 0, "frogs": 0, "foxes": 0, "rabbits": 0, "jungle": 0, "savanna": 0,
                  "woods": 0}
        frog_tags = {"warm": [], "cold": []}
        for f in sorted(os.listdir(self.biomes)):
            name = f[:-5]
            path = os.path.join(self.biomes, f)
            b = json.load(open(path, encoding="utf-8"))
            creatures = {s["type"] for s in b["spawners"].get("creature", [])}
            trees = any("trees/" in x for step in b["features"] for x in step)
            green = "minecraft:sheep" in creatures or "minecraft:cow" in creatures
            add = []
            if trees and green and 0.5 <= b["temperature"] <= 1.1:
                kind = "common" if name in BEES_COMMON else "rare"
                self.feature(b, 9, f"{NS}:bee_nests_{kind}")
                counts[f"bees_{kind}"] += 1
            if name in FROGS:
                add.append(spawner("frog", 10, 2, 5))
                if FROGS[name] in frog_tags:
                    frog_tags[FROGS[name]].append(name)
                self.feature(b, 9, "minecraft:patch_waterlily")
                counts["frogs"] += 1
            if name in FOXES:
                add.append(spawner("fox", 8, 2, 4))
                counts["foxes"] += 1
            if name in RABBITS:
                add.append(spawner("rabbit", 4, 2, 3))
                counts["rabbits"] += 1
            if name in JUNGLE:
                add += [spawner("parrot", 40, 1, 2), spawner("ocelot", 2, 1, 3)]
                for feat in ("minecraft:bamboo_light", "minecraft:vines", "minecraft:patch_melon"):
                    self.feature(b, 9, feat)
                counts["jungle"] += 1
            if name in SAVANNA:
                add.append(spawner("armadillo", 10, 2, 3))
                counts["savanna"] += 1
            if name in TAIGA:
                self.feature(b, 9, "minecraft:patch_berry_common")
            if trees and green and b["downfall"] >= 0.8:
                for feat in ("minecraft:brown_mushroom_normal", "minecraft:red_mushroom_normal"):
                    self.feature(b, 9, feat)
                counts["woods"] += 1
            if green and 0.5 <= b["temperature"] <= 1.0:
                self.feature(b, 9, "minecraft:patch_pumpkin")
            have = {s["type"] for s in b["spawners"].get("creature", [])}
            for s in add:
                if s["type"] not in have:
                    b["spawners"].setdefault("creature", []).append(dict(s))
            write_json(path, b)
        for variant, names in frog_tags.items():
            write_json(os.path.join(self.out, "data", "minecraft", "tags", "worldgen", "biome",
                                    f"spawns_{variant}_variant_frogs.json"),
                       {"replace": False, "values": [f"{NS}:{n}" for n in names]})
        normalize_order(self.biomes)
        return counts

    @staticmethod
    def feature(biome, step, feat):
        if feat not in biome["features"][step]:
            biome["features"][step].append(feat)


if __name__ == "__main__":
    print(json.dumps(Land(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
