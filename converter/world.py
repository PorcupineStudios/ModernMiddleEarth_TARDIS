"""
Middle-earth world generation data from the LOTR export (world.json) and LOTR's map image:

  lotr/src/generated/resources/lotr_port/map/        map.json (settings, biome table) + biomes.bin.gz (biome per pixel)
  lotr/src/generated/resources/data/lotr_port/       biomes, noise settings (terrain shape + surface blocks), terrain
                                                      noise, dimension type, dimension, world preset
  lotr/src/generated/resources/data/minecraft/       world preset tag (shows "Middle-earth" on the world type button)

Every number here that changes how the world looks is a starting value; the generated JSON is the thing to tune.
"""
import colorsys
import gzip
import json
import io
import os

from PIL import Image

from assets import NS, snake, write_json
from trees import Trees

# Old biome numbers -> blocks. LOTR's heightBaseParameter is roughly vanilla's depth (0 = sea level, 1 = high hills,
# 2 = mountains) and heightVariation its scale.
SEA_LEVEL = 62
GROUND_AT_ZERO = 64  # surface y for heightBaseParameter 0
BLOCKS_PER_BASE = 34  # surface y rise per 1.0 of heightBaseParameter
BLOCKS_PER_VARIATION = 48  # hilliness in blocks per 1.0 of heightVariation
MIN_HEIGHT, MAX_HEIGHT = -40, 300
BORDER_WOBBLE = 40  # blocks a biome border or slope can shift
BLUR = 0  # pixels of box blur on heights (0 keeps one-pixel rivers)
OCEAN = "ocean"
ROAD_HALF_WIDTH = 1.6  # blocks either side of a road's middle
# Small rivers (LOTR made these with a river layer, not on the map). Widths are in noise units: one is about 11 blocks.
RIVERS = {"biome": f"{NS}:river", "scale": 900, "width": 0.025, "bank": 3.0, "biome_width": 1.5, "bed": 56,
          "max_height": 100, "min_height": 63, "seed": 1937}

# Old surface blocks (registry name + meta) that are vanilla in 1.21.
VANILLA_SURFACE = {
    "minecraft:grass#0": "minecraft:grass_block",
    "minecraft:dirt#0": "minecraft:dirt",
    "minecraft:dirt#1": "minecraft:coarse_dirt",
    "minecraft:dirt#2": "minecraft:podzol",
    "minecraft:stone#0": "minecraft:stone",
    "minecraft:sand#0": "minecraft:sand",
    "minecraft:sand#1": "minecraft:red_sand",
    "minecraft:gravel#0": "minecraft:gravel",
    "minecraft:snow#0": "minecraft:snow_block",
    "minecraft:ice#0": "minecraft:packed_ice",
    "minecraft:clay#0": "minecraft:clay",
    "minecraft:hardened_clay#0": "minecraft:terracotta",
}

# Every biome takes a subset of these in this order, so the feature order is the same everywhere (1.21 requires it).
ORES = ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_diorite_upper",
        "minecraft:ore_andesite_upper", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
        "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
        "minecraft:ore_redstone", "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper",
        "minecraft:ore_clay"]
VEGETATION = ["minecraft:trees_taiga", "minecraft:trees_jungle", "minecraft:trees_birch_and_oak",
              "minecraft:trees_plains", "minecraft:trees_savanna", "minecraft:flower_default",
              "minecraft:patch_grass_forest", "minecraft:patch_grass_plain", "minecraft:patch_dead_bush",
              "minecraft:patch_cactus_desert", "minecraft:patch_sugar_cane"]
CARVERS = ["minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon"]
GRAZERS = [("minecraft:sheep", 12, 2, 4), ("minecraft:pig", 10, 2, 4), ("minecraft:chicken", 10, 2, 4),
           ("minecraft:cow", 8, 2, 4)]


class World:
    def __init__(self, gen, export, out, jar):
        self.gen = gen
        self.export = export
        self.out = out
        self.data = os.path.join(out, "data")
        self.jar = jar
        self.report = gen.report

    # ------------------------------------------------------------------ biomes

    def biome_id(self, b):
        return snake(b["fields"]["biomeName"])

    def surface_block(self, block, meta):
        key = f"{block}#{meta}"
        if key in VANILLA_SURFACE:
            return VANILLA_SURFACE[key]
        new = self.gen.idtable.get(key)
        if new:
            return new
        if block.startswith("minecraft:") and meta == 0:
            return block  # ores, ice: same name in 1.21
        self.report["unknown_surface_blocks"].append(key)
        return "minecraft:stone"

    def biome(self, b):
        f = b["fields"]
        dec = b.get("decorator", {})
        temp = float(f.get("temperature", 0.8))
        rain = max(0.0, min(1.0, float(f.get("rainfall", 0.5))))
        top = self.surface_block(f["topBlock"], f.get("topBlockMeta", 0))
        water = f.get("waterColorMultiplier", 0xFFFFFF) & 0xFFFFFF
        effects = {
            "fog_color": 12638463,
            "sky_color": sky_color(temp),
            "water_color": 4159204 if water == 0xFFFFFF else water,
            "water_fog_color": 329011,
            "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8,
                           "offset": 2.0},
        }
        trees = dec.get("treesPerChunk", 0)
        lotr_trees = self.trees.biome(self.biome_id(b), b)
        veg = set()
        if top == "minecraft:grass_block" or top.endswith("jungle_grass"):
            if trees > 0 and not lotr_trees:
                if temp < 0.3:
                    veg.add("minecraft:trees_taiga")
                elif top.endswith("jungle_grass") or trees >= 20:
                    veg.add("minecraft:trees_jungle")
                elif trees >= 3:
                    veg.add("minecraft:trees_birch_and_oak")
                elif temp >= 1.2:
                    veg.add("minecraft:trees_savanna")
                else:
                    veg.add("minecraft:trees_plains")
            if dec.get("flowersPerChunk", 0) > 0:
                veg.add("minecraft:flower_default")
            veg.add("minecraft:patch_grass_forest" if trees >= 3 else "minecraft:patch_grass_plain")
        if top in ("minecraft:sand", "minecraft:red_sand"):
            veg.add("minecraft:patch_dead_bush")
            if dec.get("cactiPerChunk", 0) > 0 or temp >= 1.5:
                veg.add("minecraft:patch_cactus_desert")
        if dec.get("canePerChunk", 0) > 0 or dec.get("reedPerChunk", 0) > 0:
            veg.add("minecraft:patch_sugar_cane")
        features = [[] for _ in range(11)]
        features[2] = [f"{NS}:roads"]
        features[6] = self.biome_ores.get(self.biome_id(b)) or list(ORES)
        features[9] = ([lotr_trees] if lotr_trees else []) + [v for v in VEGETATION if v in veg]
        features[10] = ["minecraft:freeze_top_layer"]
        creatures = []
        if top == "minecraft:grass_block" and temp < 1.5:
            creatures = [{"type": t, "weight": w, "minCount": a, "maxCount": c} for t, w, a, c in GRAZERS]
        return {
            "has_precipitation": bool(f.get("enableRain", True) or f.get("enableSnow", False)),
            "temperature": temp,
            "downfall": rain,
            "effects": effects,
            "spawners": {"creature": creatures} if creatures else {},
            "spawn_costs": {},
            "carvers": {"air": CARVERS},
            "features": features,
        }

    def height(self, b):
        f = b["fields"]
        h = GROUND_AT_ZERO + float(f.get("heightBaseParameter", 0.1)) * BLOCKS_PER_BASE
        return round(max(MIN_HEIGHT, min(MAX_HEIGHT, h)), 2)

    def variation(self, b):
        return round(max(0.0, float(b["fields"].get("heightVariation", 0.1))) * BLOCKS_PER_VARIATION, 2)

    # ------------------------------------------------------------------ map

    def write_map(self, biomes, world):
        ids = [self.biome_id(b) for b in biomes]
        index_of_old = {b["fields"]["biomeID"]: i for i, b in enumerate(biomes)}
        color_to_index = {int(c, 16): index_of_old[old] for c, old in world["colors"].items() if old in index_of_old}
        img = Image.open(io.BytesIO(self.jar.read("assets/lotr/map/map.png"))).convert("RGB")
        w, h = img.size
        raw = img.tobytes()
        lut = {}
        pixels = bytearray(w * h)
        unknown = set()
        for i in range(w * h):
            rgb = (raw[3 * i] << 16) | (raw[3 * i + 1] << 8) | raw[3 * i + 2]
            v = lut.get(rgb)
            if v is None:
                v = color_to_index.get(rgb, 255)
                if v == 255:
                    unknown.add(f"{rgb:06x}")
                lut[rgb] = v
            pixels[i] = v
        if unknown:
            self.report["unknown_map_colors"].extend(sorted(unknown))
        settings = world["map"]
        out = os.path.join(self.out, NS, "map")
        os.makedirs(out, exist_ok=True)
        with gzip.open(os.path.join(out, "biomes.bin.gz"), "wb", compresslevel=9) as f:
            f.write(bytes(pixels))
        write_json(os.path.join(out, "map.json"), {
            "width": w, "height": h,
            "blocks_per_pixel": settings.get("scale", 128),
            "origin_x": settings.get("originX", 0), "origin_z": settings.get("originZ", 0),
            "blur": BLUR, "border_wobble": BORDER_WOBBLE,
            "ocean": f"{NS}:{OCEAN}",
            "rivers": RIVERS,
            "biomes": [{"id": f"{NS}:{i}", "height": self.height(b), "variation": self.variation(b)}
                       for i, b in zip(ids, biomes)],
        })
        return w, h

    # ------------------------------------------------------------------ noise settings

    def surface_rule(self, biomes):
        groups = {}
        for b in biomes:
            f = b["fields"]
            top = self.surface_block(f["topBlock"], f.get("topBlockMeta", 0))
            filler = self.surface_block(f["fillerBlock"], f.get("fillerBlockMeta", 0))
            groups.setdefault((top, filler), []).append(f"{NS}:{self.biome_id(b)}")
        on_floor = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                    "secondary_depth_range": 0, "surface_type": "floor"}
        under_floor = {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                       "secondary_depth_range": 0, "surface_type": "floor"}
        above_water = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0,
                       "add_stone_depth": False}
        rules = [{
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                        "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
            "then_run": block_rule("minecraft:bedrock"),
        }]
        per_biome = []
        for (top, filler), ids in sorted(groups.items()):
            per_biome.append({
                "type": "minecraft:condition",
                "if_true": {"type": "minecraft:biome", "biome_is": sorted(ids)},
                "then_run": {"type": "minecraft:sequence", "sequence": [
                    {"type": "minecraft:condition", "if_true": on_floor, "then_run": {
                        "type": "minecraft:sequence", "sequence": [
                            {"type": "minecraft:condition", "if_true": above_water, "then_run": block_rule(top)},
                            block_rule(filler),  # under water: no grass or snow
                        ]}},
                    {"type": "minecraft:condition", "if_true": under_floor, "then_run": block_rule(filler)},
                ]},
            })
        rules.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"},
                      "then_run": {"type": "minecraft:sequence", "sequence": per_biome}})
        # Lands of rock (Mordor, Gorgoroth...) are that rock all the way down, as in LOTR, so their ores find it.
        rock_fillers = sorted({filler for (top, filler) in groups if filler.endswith("_rock")})
        for filler in rock_fillers:
            ids = sorted(i for (t, f), group in groups.items() if f == filler for i in group)
            rules.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": ids},
                          "then_run": {"type": "minecraft:condition", "if_true": {
                              "type": "minecraft:not", "invert": {"type": "minecraft:vertical_gradient",
                                                                 "random_name": "minecraft:deepslate",
                                                                 "true_at_and_below": {"absolute": -48},
                                                                 "false_at_and_above": {"absolute": -40}}},
                              "then_run": block_rule(filler)}})
        return {"type": "minecraft:sequence", "sequence": rules}

    def noise_settings(self, biomes):
        height = {"type": "minecraft:flat_cache", "argument": {"type": f"{NS}:map_terrain", "value": "height"}}
        variation = {"type": "minecraft:flat_cache", "argument": {"type": f"{NS}:map_terrain", "value": "variation"}}
        minus_y = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320, "from_value": 64,
                   "to_value": -320}
        # density = (ground height - y + hilliness * 3D noise) / 24: solid below the ground, air above
        base = {"type": "minecraft:add", "argument1": height, "argument2": minus_y}
        hills = {"type": "minecraft:mul", "argument1": variation,
                 "argument2": {"type": "minecraft:noise", "noise": f"{NS}:terrain", "xz_scale": 1.0,
                               "y_scale": 0.5}}
        final = {"type": "minecraft:interpolated", "argument": {
            "type": "minecraft:mul", "argument1": 1 / 24,
            "argument2": {"type": "minecraft:add", "argument1": base, "argument2": hills}}}
        prelim = {"type": "minecraft:mul", "argument1": 1 / 24, "argument2": base}
        zero = 0.0
        return {
            "sea_level": SEA_LEVEL,
            "disable_mob_generation": False,
            "aquifers_enabled": False,
            "ore_veins_enabled": False,
            "legacy_random_source": False,
            "default_block": {"Name": "minecraft:stone"},
            "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
            "noise": {"min_y": -64, "height": 384, "size_horizontal": 1, "size_vertical": 2},
            "noise_router": {
                "barrier": zero, "fluid_level_floodedness": zero, "fluid_level_spread": zero, "lava": zero,
                "temperature": zero, "vegetation": zero, "continents": zero, "erosion": zero, "depth": zero,
                "ridges": zero, "initial_density_without_jaggedness": prelim, "final_density": final,
                "vein_toggle": zero, "vein_ridged": zero, "vein_gap": zero,
            },
            "spawn_target": [],
            "surface_rule": self.surface_rule(biomes),
        }

    # ------------------------------------------------------------------ dimension, world preset, lang

    def dimension_files(self):
        d = os.path.join(self.data, NS)
        generator = {"type": "minecraft:noise", "settings": f"{NS}:middle_earth",
                     "biome_source": {"type": f"{NS}:middle_earth"}}
        write_json(os.path.join(d, "dimension_type", "middle_earth.json"), {
            "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True,
            "has_ceiling": False, "ambient_light": 0.0,
            "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
            "monster_spawn_block_light_limit": 0, "piglin_safe": False, "bed_works": True,
            "respawn_anchor_works": False, "has_raids": True, "logical_height": 384, "min_y": -64, "height": 384,
            "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld",
        })
        write_json(os.path.join(d, "dimension", "middle_earth.json"),
                   {"type": f"{NS}:middle_earth", "generator": generator})
        write_json(os.path.join(d, "worldgen", "world_preset", "middle_earth.json"), {"dimensions": {
            "minecraft:overworld": {"type": "minecraft:overworld", "generator": generator},
            "minecraft:the_nether": {"type": "minecraft:the_nether", "generator": {
                "type": "minecraft:noise", "settings": "minecraft:nether",
                "biome_source": {"type": "minecraft:multi_noise", "preset": "minecraft:nether"}}},
            "minecraft:the_end": {"type": "minecraft:the_end", "generator": {
                "type": "minecraft:noise", "settings": "minecraft:end",
                "biome_source": {"type": "minecraft:the_end"}}},
        }})
        write_json(os.path.join(self.data, "minecraft", "tags", "worldgen", "world_preset", "normal.json"),
                   {"replace": False, "values": [f"{NS}:middle_earth"]})
        write_json(os.path.join(d, "worldgen", "noise", "terrain.json"),
                   {"firstOctave": -7, "amplitudes": [1.0, 1.0, 0.5, 0.25]})

    def lang(self, biomes):
        en = self.gen.lang["en_us"]
        en[f"generator.{NS}.middle_earth"] = "Middle-earth"
        for code in self.jar.langs():
            src = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for b in biomes:
                name = b["fields"]["biomeName"]
                text = src.get(f"lotr.biome.{name}.name")
                if text:
                    dst[f"biome.{NS}.{self.biome_id(b)}"] = text
                elif code == "en_US":
                    dst[f"biome.{NS}.{self.biome_id(b)}"] = name
                    self.report["untranslated_biomes"].append(name)

    def ores(self, biomes):
        """LOTR's ores per biome as ore features; every biome lists its ores in one shared order."""
        specs = {}
        per_biome = {}
        for b in biomes:
            mine = []
            for o in b.get("ores", []):
                g = o.get("gen", {})
                block = g.get("field_150519_a")
                if not block or o.get("genClass", "").split(".")[-1] != "WorldGenMinable":
                    continue
                state = self.surface_block(block, g.get("mineableBlockMeta", 0))
                target = g.get("field_150518_c", "minecraft:stone")
                key = (state, target, g.get("numberOfBlocks", 8), float(o.get("oreChance", 1)),
                       o.get("minHeight", 0), o.get("maxHeight", 64))
                specs.setdefault(key, None)
                mine.append(key)
            per_biome[self.biome_id(b)] = mine
        order = sorted(specs, key=lambda k: (k[0], k[1], k[2], -k[3], k[4], k[5]))
        gen = os.path.join(self.data, NS, "worldgen")
        names = {}
        for i, (state, target, size, chance, lo, hi) in enumerate(order):
            name = f"ore/{state.split(':')[1]}_{i}"
            names[(state, target, size, chance, lo, hi)] = f"{NS}:{name}"
            if target == "minecraft:stone":
                rule = {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}
            else:
                rule = {"predicate_type": "minecraft:block_match", "block": self.surface_block(target, 0)}
            write_json(os.path.join(gen, "configured_feature", name + ".json"), {"type": "minecraft:ore", "config": {
                "targets": [{"target": rule, "state": {"Name": state}}], "size": size,
                "discard_chance_on_air_exposure": 0.0}})
            if chance >= 1:
                count = [{"type": "minecraft:count", "count": int(round(chance))}]
            else:
                count = [{"type": "minecraft:rarity_filter", "chance": max(1, int(round(1 / max(chance, 0.001))))}]
            bottom = -64 if lo <= 0 else lo
            write_json(os.path.join(gen, "placed_feature", name + ".json"), {"feature": f"{NS}:{name}", "placement":
                count + [{"type": "minecraft:in_square"},
                         {"type": "minecraft:height_range", "height": {
                             "type": "minecraft:uniform", "min_inclusive": {"absolute": bottom},
                             "max_inclusive": {"absolute": max(bottom + 1, min(hi, 319))}}},
                         {"type": "minecraft:biome"}]})
        rank = {k: i for i, k in enumerate(order)}
        self.biome_ores = {bid: [names[k] for k in sorted(set(keys), key=rank.get)] for bid, keys in per_biome.items()}
        return len(order)

    def roads(self, world):
        """LOTR's roads in block coordinates (map pixel -> block, the same way as the biome map), and the feature."""
        settings = world["map"]
        scale = settings.get("scale", 128)
        ox, oz = settings.get("originX", 0), settings.get("originZ", 0)
        roads = []
        all_points = [p for road in world.get("roads", []) for p in road["points"]]
        in_blocks = any(abs(p[0]) > 5000 or abs(p[1]) > 5000 for p in all_points)  # else map pixels
        for road in world.get("roads", []):
            if in_blocks:
                points = [[round(p[0], 1), round(p[1], 1)] for p in road["points"]]
            else:
                points = [[round((p[0] - ox) * scale, 1), round((p[1] - oz) * scale, 1)] for p in road["points"]]
            if len(points) >= 2:
                roads.append({"name": road["name"], "points": points})
        path = os.path.join(self.out, NS, "map", "roads.json")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:  # compact: ~150k points
            json.dump({"width": ROAD_HALF_WIDTH, "roads": roads}, f, separators=(",", ":"))
        gen = os.path.join(self.data, NS, "worldgen")
        write_json(os.path.join(gen, "configured_feature", "roads.json"), {"type": f"{NS}:roads", "config": {}})
        write_json(os.path.join(gen, "placed_feature", "roads.json"), {"feature": f"{NS}:roads", "placement": []})
        return len(roads)

    def run(self):
        world = self.export
        self.trees = Trees(self.gen, self.data, self.report)
        road_count = self.roads(world)
        biomes_all = [b for b in world["biomes"] if "biomeName" in b.get("fields", {})]
        ore_count = self.ores(biomes_all)
        biomes = [b for b in world["biomes"] if "biomeName" in b.get("fields", {})]
        if len(biomes) > 255:
            raise SystemExit(f"{len(biomes)} biomes do not fit the byte map")
        seen = set()
        for b in biomes:
            bid = self.biome_id(b)
            if bid in seen:
                raise SystemExit(f"two biomes are called {bid}")
            seen.add(bid)
            write_json(os.path.join(self.data, NS, "worldgen", "biome", bid + ".json"), self.biome(b))
        if OCEAN not in seen:
            raise SystemExit("no ocean biome in the export")
        w, h = self.write_map(biomes, world)
        write_json(os.path.join(self.data, NS, "worldgen", "noise_settings", "middle_earth.json"),
                   self.noise_settings(biomes))
        self.dimension_files()
        self.lang(biomes)
        return {"biomes": len(biomes), "map": f"{w}x{h}", "roads": road_count, "tree_types": len(self.trees.written),
                "ore_features": ore_count}


def block_rule(name):
    return {"type": "minecraft:block", "result_state": {"Name": name}}


def sky_color(temp):
    """Vanilla's sky colour for a temperature."""
    t = max(-1.0, min(1.0, temp / 3.0))
    r, g, b = colorsys.hsv_to_rgb((0.62222224 - t * 0.05) % 1.0, 0.5 + t * 0.1, 1.0)
    return (int(r * 255) << 16) | (int(g * 255) << 8) | int(b * 255)
