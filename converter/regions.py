"""
The region layer and the sub-biomes painted with it (Aaron 2026-11-25: "the enchanted forest from the hobbit movie
with the drowsy effect and cursed water, along with a rarer spider infested biome that can only be found within ...
make it only generate on the areas it's allowed to (maybe make a "allowed biomes" map that adheres on top of the
geographical map of middle earth (backend only)) this would help with future phases of world gen like the mines of
moria, goblin town").

1. REGIONS: named areas drawn over LOTR's map in map pixels (128 blocks each): polygons, rough-edged circles, cut to
   another region. Written to ``lotr_port/map/regions.json`` (map/Regions.java). Nobody sees them in game; they say
   where things may be. Moria and Goblin-town are drawn now for the underground phases to come (nothing generates
   there yet).
2. OVERLAYS (map.json "overlays", MiddleEarthMap.paint): a sub-biome is painted, as the map loads, over chosen biomes
   inside a region and nowhere else: Enchanted Mirkwood over northern Mirkwood between the Forest Gate, the Forest
   River and the Mountains of Mirkwood (where the Elf-path of The Hobbit ran); the Enchanted River over the river
   LOTR's map already paints from the Mountains of Mirkwood north to the Forest River, stopping short of the
   confluence (in Tolkien the enchantment ends where the waters join).
2b. LAIRS (map.json "lairs", map/Lairs.java): seeded nests inside the enchanted forest, each a small core and a ring of
   infested ground that is the spider nests' biome; LAIR_DATA what breaking their egg sacs does.
3. SUBBIOMES: each sub-biome is its base biome (copied fresh each run) with its own colours, plants, NPC spawns and
   effects, and is listed in every tag and table its base is in (TABLES), so fast travel regions, music, borders,
   conquest, invasions, roads and surface rules treat it as its base. Its map.json entry takes the average ground of
   what it paints over, so the terrain doesn't change. Its rivers keep its own biome (river keep_biome): the water
   there is the cursed water.
4. REGION_EFFECTS (data/lotr_port/region_effect/, effect/RegionEffects.java): the drowsiness of the enchanted forest
   and the sleep of its black water.

Runs on the generated data (``python regions.py``, ``python regions.py --preview out.png`` also draws the regions on
the map); generate.py calls it after caves.py and before biomeconfig.py and borders.py, which then see the new
biomes. Idempotent: sub-biomes are rebuilt from their bases, tables only gain what is missing.
"""
import copy
import glob
import gzip
import json
import math
import os
import sys

from woodsets import OUT, write_json

NS = "lotr_port"

# Map pixel coordinates (x, z) of LOTR's map; its waypoints for reference: Forest Gate 1303,655; Enchanted River
# 1396,650 (the Elf-path's crossing); Thranduil's Halls 1420,633; Mountains of Mirkwood 1430,672; Old Ford 1284,702
# (the Old Forest Road, south of the Elf-path); Moria West-gate 1134,873, Dimrill Dale 1177,864, Caradhras 1166,845,
# Celebdil 1158,864, Fanuidhol 1191,854; Goblin-town 1220,696 under the High Pass 1222,706.
REGIONS = [
    # The Hobbit's enchanted forest: from the western eaves round the Forest Gate east along the Elf-path to the
    # Enchanted River and the Elvenking's realm, south of the Forest River and north of the Mountains of Mirkwood and
    # the Old Forest Road
    {"name": "mirkwood_enchanted", "polygon": [
        (1288, 598), (1340, 610), (1375, 622), (1398, 630), (1412, 636), (1410, 650), (1430, 656), (1442, 660),
        (1442, 666), (1376, 664), (1352, 668), (1345, 690), (1290, 694), (1284, 650)]},
    # the Enchanted River from its spring in the Mountains of Mirkwood to just above the Forest River
    {"name": "enchanted_river", "polygon": [(1388, 633), (1418, 633), (1418, 670), (1388, 670)]},
    # reserved for the underground phases: Moria under Caradhras, Celebdil and Fanuidhol between its West-gate and
    # East-gate (Dimrill Dale); Goblin-town under the High Pass, east of Rivendell
    {"name": "moria", "reserved": True, "polygon": [
        (1128, 872), (1136, 852), (1156, 838), (1180, 840), (1196, 852), (1190, 870), (1176, 880), (1150, 884),
        (1132, 882)]},
    {"name": "goblin_town", "reserved": True, "polygon": [(1210, 690), (1228, 688), (1234, 700), (1226, 712),
                                                           (1212, 710)]},
]

# region -> sub-biome painted over the listed biomes, in this order (later overlays paint over earlier ones)
OVERLAYS = [
    {"region": "mirkwood_enchanted", "on": ["mirkwood_north", "mirkwood_corrupted"], "biome": "mirkwood_enchanted"},
    {"region": "enchanted_river", "on": ["river"], "biome": "enchanted_river"},
]

# Lairs (map.json "lairs", map/Lairs.java; Aaron 2026-11-25: the nests "relatively small only about a 200 sq/blocks of
# actual spider nests, and with spiders spawning within a larger radius around it (with spawning being affected by
# destroying nests)", seeded "so there's randomness on new worlds"). Per world seed, a nest in about one 512-block
# cell in three of the enchanted forest (~100 in all, ~900 blocks apart): a core of radius 100 (Aaron: "200 by 200")
# choked with webs, egg sacs and silk bundles, and infested ground 56 blocks round it (ragged edge) that is the
# "Mirkwood Spider Nests" biome, where spiders come by day too. Breaking its sacs kills it (LAIR_DATA).
LAIRS = {
    "mirkwood_spider_nests": {"biome": "mirkwood_spider_nests", "on": ["mirkwood_enchanted"],
                              "regions": ["mirkwood_enchanted"], "cell": 512, "chance": 0.33, "core": 100,
                              "radius": 160, "ragged": 0.2, "sacs": 12},
}
# What happens to them (lair/LairLife.java, reloads): 3 of the 4 egg sacs gone and the nest is dead; each broken sac
# may hatch a spider or two; the dead nest's ground spawns as the plain enchanted forest (spiders by night only).
LAIR_DATA = {
    "mirkwood_spider_nests": {"dead_at": 9,
                              "hatch": {"npc": f"{NS}:mirkwood_spider", "min": 1, "max": 2, "chance": 0.75},
                              "fallback_biome": f"{NS}:mirkwood_enchanted",
                              "damaged": "lair.lotr_port.spider_nest.damaged",
                              "destroyed": "lair.lotr_port.spider_nest.destroyed"},
}

BLACK_WATER = 0x16190F  # "the water was black" (The Hobbit); its fog darker still
SUBBIOMES = {
    "mirkwood_enchanted": {
        "name": "Enchanted Mirkwood",
        "base": "mirkwood_corrupted",
        "effects": {"fog_color": 0x2B3328, "sky_color": 0x56625A, "water_color": BLACK_WATER,
                    "water_fog_color": 0x050703, "grass_color": 0x2E4422, "foliage_color": 0x263C1E,
                    # drifting motes in the still air under the trees
                    "particle": {"options": {"type": "minecraft:spore_blossom_air"}, "probability": 0.006}},
        "features": {"webs": ({"type": f"{NS}:webs", "config": {
            "web": {"Name": "minecraft:cobweb"}, "tries": 2, "min_hang": 1, "max_hang": 3, "ground_chance": 0.2}}, [])},
        # The Hobbit's northern Mirkwood: spiders by night, the Wood-elves' hunting parties now and then
        "spawns": {"entries": [
            {"npc": f"{NS}:mirkwood_spider", "weight": 60, "min": 2, "max": 4, "dark": True},
            {"npc": f"{NS}:wood_elf_scout", "weight": 6, "min": 2, "max": 4},
            {"npc": f"{NS}:wood_elf_warrior", "weight": 3, "min": 2, "max": 3},
            {"npc": f"{NS}:huorn", "weight": 2, "min": 1, "max": 2}]},
        "tags": ["enchanted_forest", "cursed_water"],
    },
    "mirkwood_spider_nests": {
        "name": "Mirkwood Spider Nests",
        "base": "mirkwood_enchanted",
        "lair": True,  # not painted on the map: the infested ground round each seeded nest (LAIRS)
        "effects": {"fog_color": 0x262822, "sky_color": 0x4A4E48, "grass_color": 0x3B4232, "foliage_color": 0x30362A,
                    "particle": {"options": {"type": "minecraft:white_ash"}, "probability": 0.01}},
        "features": {
            # the nest itself: its core thick with webs, the egg sacs, silk-wrapped bundles
            "nest": ({"type": f"{NS}:spider_nest", "config": {
                "lair": "mirkwood_spider_nests", "web": {"Name": "minecraft:cobweb"},
                "sac": {"Name": f"{NS}:spider_egg_sac"}, "density": 0.35, "height": 7,
                "floor": {"Name": "minecraft:podzol", "Properties": {"snowy": "false"}}, "floor_chance": 0.4,
                "cocoon": {"Name": "minecraft:mushroom_stem", "Properties": {
                    "up": "true", "down": "true", "north": "true", "south": "true", "east": "true", "west": "true"}},
                "cocoons": 30}}, []),
            # round it, webs slung under the crowns and round the trunks, the odd bundle hanging from a bough
            "webs": ({"type": f"{NS}:webs", "config": {
                "web": {"Name": "minecraft:cobweb"}, "tries": 14, "min_hang": 1, "max_hang": 4,
                "cocoon": {"Name": "minecraft:mushroom_stem", "Properties": {
                    "up": "true", "down": "true", "north": "true", "south": "true", "east": "true", "west": "true"}},
                "cocoon_chance": 0.03, "ground_chance": 0.4}}, []),
            "dead_trees": ({"type": "minecraft:random_selector", "config": {
                "features": [{"feature": f"{NS}:tree/mirk_oak_dead", "chance": 0.6}],
                "default": f"{NS}:tree/oak_dead"}},
                [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 0},
                 {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"}, {"type": "minecraft:biome"}]),
            "ground_webs": ({"type": "minecraft:random_patch", "config": {
                "tries": 12, "xz_spread": 5, "y_spread": 2, "feature": {
                    "feature": {"type": "minecraft:simple_block", "config": {"to_place": {
                        "type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:cobweb"}}}},
                    "placement": [{"type": "minecraft:block_predicate_filter", "predicate": {
                        "type": "minecraft:all_of", "predicates": [
                            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
                            {"type": "minecraft:solid", "offset": [0, -1, 0]}]}}]}}},
                [{"type": "minecraft:count", "count": 2}, {"type": "minecraft:in_square"},
                 {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING_NO_LEAVES"},
                 {"type": "minecraft:biome"}]),
        },
        # the spiders' own ground: they hunt by day here too
        # (wilderness: borders.py leaves it nobody's land, as the rest of the enchanted forest)
        "spawns": {"entries": [{"npc": f"{NS}:mirkwood_spider", "weight": 100, "min": 2, "max": 4}],
                   "cap": 10, "chance": 0.4, "hostiles_in_day": True, "wilderness": True},
        "tags": ["enchanted_forest", "cursed_water"],
    },
    "enchanted_river": {
        "name": "Enchanted River",
        "base": "river",
        "like": "mirkwood_enchanted",  # in the tables (music, ambience, roads...): Mirkwood, not any river
        "effects": {"fog_color": 0x2B3328, "sky_color": 0x56625A, "water_color": BLACK_WATER,
                    "water_fog_color": 0x050703, "grass_color": 0x2E4422, "foliage_color": 0x263C1E,
                    "particle": {"options": {"type": "minecraft:spore_blossom_air"}, "probability": 0.006}},
        "spawners": {},  # nothing swims in the black water
        "tags": ["enchanted_forest", "cursed_water", "waypoint_region/mirkwood"],
    },
}

# What the enchanted forest does to those in it (effect/RegionEffects.java). Ticks: 20 a second.
REGION_EFFECTS = {
    "enchanted_forest": {
        "biome_tag": f"{NS}:enchanted_forest",
        "rules": [
            # the heavy, sleepy air: every 10 s a chance to grow drowsy for 45 s
            {"when": "in_biome", "interval": 200, "chance": 0.35, "cooldown": 600, "unless": f"{NS}:enchanted_sleep",
             "effects": [{"id": f"{NS}:drowsy", "duration": 900, "show_particles": False}],
             "message": "region_effect.lotr_port.drowsy"},
            # losing the path: now and then the forest seems to turn about you
            {"when": "in_biome", "interval": 400, "chance": 0.15, "cooldown": 2400, "unless": f"{NS}:enchanted_sleep",
             "effects": [{"id": "minecraft:nausea", "duration": 160, "show_particles": False, "show_icon": False}],
             "message": "region_effect.lotr_port.lost_way"},
        ],
    },
    "cursed_water": {
        "biome_tag": f"{NS}:cursed_water",
        "rules": [
            # Bombur fell in the black stream and slept: 15 s of enchanted sleep (no drowning while it lasts), then a
            # muddled waking, turned about, drowsy for a minute
            {"when": "in_water", "interval": 10, "cooldown": 1200, "unless": f"{NS}:enchanted_sleep",
             "effects": [{"id": f"{NS}:enchanted_sleep", "duration": 300},
                         {"id": "minecraft:blindness", "duration": 300, "show_icon": False},
                         {"id": "minecraft:water_breathing", "duration": 340, "show_particles": False,
                          "show_icon": False}],
             "message": "region_effect.lotr_port.enchanted_sleep",
             "wake": {"after": f"{NS}:enchanted_sleep", "turn": True, "message": "region_effect.lotr_port.wake",
                      "effects": [{"id": "minecraft:nausea", "duration": 120, "show_icon": False},
                                  {"id": f"{NS}:drowsy", "duration": 1200}]}},
        ],
        "bottle": {"name": "item.lotr_port.black_water", "color": BLACK_WATER,
                   "effects": [{"id": f"{NS}:enchanted_sleep", "duration": 400},
                               {"id": "minecraft:blindness", "duration": 400}]},
    },
}

LANG = {
    "biome.lotr_port.mirkwood_enchanted": "Enchanted Mirkwood",
    "biome.lotr_port.mirkwood_spider_nests": "Mirkwood Spider Nests",
    "biome.lotr_port.enchanted_river": "Enchanted River",
    "effect.lotr_port.drowsy": "Drowsy",
    "effect.lotr_port.enchanted_sleep": "Enchanted Sleep",
    "item.lotr_port.black_water": "Bottle of Black Water",
    "region_effect.lotr_port.drowsy": "The heavy air under the trees makes you drowsy...",
    "region_effect.lotr_port.lost_way": "The trees seem to shift about you. Which way was the path?",
    "region_effect.lotr_port.enchanted_sleep": "The black water pulls you down into an enchanted sleep...",
    "region_effect.lotr_port.wake": "You wake from dreams of feasting, and cannot tell which way you were going.",
    "block.lotr_port.spider_egg_sac": "Spider Egg Sac",
    "lair.lotr_port.spider_nest.damaged": "Egg sac destroyed (%s of %s): the nest is weakened",
    "lair.lotr_port.spider_nest.destroyed": "The nest is destroyed: its spiders will not come again",
}

# Tables keyed or listing biomes (bare name or lotr_port:name) that a sub-biome joins as its base (relative to the
# generated resources). Tags are all of them (every data/*/tags/worldgen/biome file).
TABLES = [f"data/{NS}/invasion_spawns/biomes.json", f"data/{NS}/conquest_terrain/biomes.json",
          f"{NS}/content/music.json", f"{NS}/content/biome_achievements.json", f"assets/{NS}/ambience.json",
          f"assets/{NS}/music/playlists.json"]
NOISE = f"data/{NS}/worldgen/noise_settings/middle_earth.json"
ROADS = f"{NS}/map/roads.json"


def rasterize(region, done):
    """The region's pixels as a set of (x, z)."""
    out = set()
    if "polygon" in region:
        poly = region["polygon"]
        xs = [p[0] for p in poly]
        zs = [p[1] for p in poly]
        for z in range(min(zs), max(zs) + 1):
            for x in range(min(xs), max(xs) + 1):
                if inside(poly, x + 0.5, z + 0.5):
                    out.add((x, z))
    for i, (cx, cz, r) in enumerate(region.get("circles", [])):
        rough = region.get("rough", 0)
        seed = region.get("seed", 0) + i * 101
        reach = int(math.ceil(r * (1 + rough))) + 1
        for z in range(int(cz) - reach, int(cz) + reach + 1):
            for x in range(int(cx) - reach, int(cx) + reach + 1):
                dx, dz = x + 0.5 - cx, z + 0.5 - cz
                a = math.atan2(dz, dx)
                # a lobed edge: three fixed waves round the circle
                wobble = sum(math.sin(k * a + seed * 0.731 * k) / k for k in (2, 3, 5)) / 1.8
                if math.hypot(dx, dz) <= r * (1 + rough * wobble):
                    out.add((x, z))
    if "inside" in region:
        out &= done[region["inside"]]
    return out


def inside(poly, x, z):
    hit = False
    j = len(poly) - 1
    for i in range(len(poly)):
        xi, zi = poly[i]
        xj, zj = poly[j]
        if (zi > z) != (zj > z) and x < (xj - xi) * (z - zi) / (zj - zi) + xi:
            hit = not hit
        j = i
    return hit


def encode(name, pixels, reserved):
    x0, z0 = min(p[0] for p in pixels), min(p[1] for p in pixels)
    x1, z1 = max(p[0] for p in pixels), max(p[1] for p in pixels)
    rows = []
    for z in range(z0, z1 + 1):
        runs, x = [], x0
        while x <= x1:
            if (x, z) in pixels:
                start = x
                while x <= x1 and (x, z) in pixels:
                    x += 1
                runs += [start - x0, x - start]
            else:
                x += 1
        rows.append(runs)
    out = {"name": name, "x": x0, "z": z0, "width": x1 - x0 + 1, "height": z1 - z0 + 1}
    if reserved:
        out["reserved"] = True
    out["rows"] = rows
    return out


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def add_like(table, base, new):
    """Adds `new` wherever `base` is in a JSON tree (keys and list items, bare or namespaced); True if changed."""
    changed = False
    if isinstance(table, dict):
        for key in list(table):
            for b, n in ((base, new), (f"{NS}:{base}", f"{NS}:{new}")):
                if key == b and n not in table:
                    table[n] = copy.deepcopy(table[key])
                    changed = True
        for v in table.values():
            changed |= add_like(v, base, new)
    elif isinstance(table, list):
        for b, n in ((base, new), (f"{NS}:{base}", f"{NS}:{new}")):
            if b in table and n not in table:
                table.insert(table.index(b) + 1, n)
                changed = True
        for v in table:
            changed |= add_like(v, base, new)
    return changed


def run(out=OUT):
    data = os.path.join(out, "data", NS)
    map_dir = os.path.join(out, NS, "map")
    tables = [os.path.join(out, t) for t in TABLES]
    noise = os.path.join(out, NOISE)
    roads = os.path.join(out, ROADS)
    meta = load(os.path.join(map_dir, "map.json"))
    ids = [b["id"].split(":", 1)[1] for b in meta["biomes"]]
    with gzip.open(os.path.join(map_dir, "biomes.bin.gz")) as f:
        raw = f.read()
    width = meta["width"]

    # 1. regions
    done = {}
    for r in REGIONS:
        done[r["name"]] = rasterize(r, done)
    write_json(os.path.join(map_dir, "regions.json"), {
        "_comment": "Generated by converter/regions.py: named areas over LOTR's map (map pixels, 128 blocks each), "
                    "rows of (start, length) runs from x. Read by world/map/Regions.java.",
        "regions": [encode(r["name"], done[r["name"]], r.get("reserved", False)) for r in REGIONS]})

    # 2. sub-biomes in the map's biome table (appended, so existing indexes keep their meaning), with the mean ground
    # of the pixels they paint over
    entry = {b["id"]: b for b in meta["biomes"]}
    painted = {}
    pixel_biome = {}
    for o in OVERLAYS:
        for (x, z) in done[o["region"]]:
            i = z * width + x
            current = pixel_biome.get(i, ids[raw[i]] if raw[i] < len(ids) else None)
            if current in o["on"]:
                pixel_biome[i] = o["biome"]
                painted.setdefault(o["biome"], []).append(raw[i])
    for name, sub in SUBBIOMES.items():
        if sub.get("lair"):  # round seeded lairs: the ground of what it lies on
            base = dict(entry[f"{NS}:{sub['base']}"])
            base["id"] = f"{NS}:{name}"
            if base["id"] in entry:
                entry[base["id"]].clear()
                entry[base["id"]].update(base)
            else:
                meta["biomes"].append(base)
                entry[base["id"]] = meta["biomes"][-1]
            continue
        under = painted.get(name)
        if not under:
            raise SystemExit(f"overlay {name} paints nothing")
        counts = {}
        for k in under:
            counts[k] = counts.get(k, 0) + 1
        n = len(under)
        bases = [entry[f"{NS}:{ids[k]}"] for k in counts]
        e = {"id": f"{NS}:{name}",
             "height": round(sum(entry[f"{NS}:{ids[k]}"]["height"] * c for k, c in counts.items()) / n, 2),
             "variation": round(sum(entry[f"{NS}:{ids[k]}"]["variation"] * c for k, c in counts.items()) / n, 2)}
        downs = [b.get("downs", 0) for b in bases]
        if any(downs):
            e["downs"] = max(downs)
        if e["id"] in entry:
            entry[e["id"]].clear()
            entry[e["id"]].update(e)
        else:
            meta["biomes"].append(e)
    if len(meta["biomes"]) > 255:
        raise SystemExit("too many biomes for the byte map")
    meta["overlays"] = [{"region": o["region"], "on": [f"{NS}:{b}" for b in o["on"]], "biome": f"{NS}:{o['biome']}"}
                        for o in OVERLAYS]
    meta["lairs"] = {k: dict(v, biome=f"{NS}:{v['biome']}", on=[f"{NS}:{b}" for b in v["on"]])
                     for k, v in LAIRS.items()}
    keep = meta["rivers"].setdefault("keep_biome", [])
    for name in SUBBIOMES:
        if f"{NS}:{name}" not in keep:
            keep.append(f"{NS}:{name}")
    write_json(os.path.join(map_dir, "map.json"), meta)

    # 3. the sub-biomes, from their bases (in order: a sub-biome may be based on one made before it)
    biome_dir = os.path.join(data, "worldgen", "biome")
    for name, sub in SUBBIOMES.items():
        biome = load(os.path.join(biome_dir, sub["base"] + ".json"))
        biome["effects"].update(sub.get("effects", {}))
        if "spawners" in sub:
            biome["spawners"] = sub["spawners"]
        veg = biome["features"][9]
        veg[:] = [f for f in veg if not f.startswith(f"{NS}:{sub['base']}/")]  # a base sub-biome's own extras
        for fname, (config, placement) in sub.get("features", {}).items():
            write_json(os.path.join(data, "worldgen", "configured_feature", name, fname + ".json"), config)
            write_json(os.path.join(data, "worldgen", "placed_feature", name, fname + ".json"),
                       {"feature": f"{NS}:{name}/{fname}", "placement": placement})
            veg.append(f"{NS}:{name}/{fname}")
        write_json(os.path.join(biome_dir, name + ".json"), biome)
        spawns = os.path.join(data, "npc_spawns", NS)
        if "spawns" in sub:
            write_json(os.path.join(spawns, name + ".json"), sub["spawns"])
        elif os.path.exists(os.path.join(spawns, sub["base"] + ".json")):
            write_json(os.path.join(spawns, name + ".json"), load(os.path.join(spawns, sub["base"] + ".json")))
        animals = os.path.join(data, "npc_animals", NS)
        if os.path.exists(os.path.join(animals, sub["base"] + ".json")):
            write_json(os.path.join(animals, name + ".json"), load(os.path.join(animals, sub["base"] + ".json")))

    # 4. every tag and table the base is in
    tag_files = glob.glob(os.path.join(out, "data", "*", "tags", "worldgen", "biome", "**", "*.json"), recursive=True)
    changed = 0
    for path in tables + tag_files + [noise, roads]:
        if not os.path.exists(path):
            continue
        table = load(path)
        hit = False
        for name, sub in SUBBIOMES.items():
            target = table["biomes"] if path == roads else table
            # tags and ground rules follow the biome it is built from; the other tables the land it lies in
            base = sub["base"] if path in tag_files or path == noise else sub.get("like", sub["base"])
            hit |= add_like(target, base, name)
        if hit:
            changed += 1
            if path == roads:  # 2.7 MB: keep it compact as roads.py writes it
                with open(path, "w", encoding="utf-8") as f:
                    json.dump(table, f, separators=(",", ":"), ensure_ascii=False)
            else:
                write_json(path, table)
    for name, sub in SUBBIOMES.items():
        for tag in sub.get("tags", []):
            path = os.path.join(data, "tags", "worldgen", "biome", tag + ".json")
            t = load(path) if os.path.exists(path) else {"replace": False, "values": []}
            if f"{NS}:{name}" not in t["values"]:
                t["values"].append(f"{NS}:{name}")
            write_json(path, t)

    # 5. effects and names
    for name, effect in REGION_EFFECTS.items():
        write_json(os.path.join(data, "region_effect", name + ".json"), effect)
    for name, lair in LAIR_DATA.items():
        write_json(os.path.join(data, "lair", name + ".json"), lair)
    lang_path = os.path.join(out, "assets", NS, "lang", "en_us.json")
    lang = load(lang_path)
    lang.update(LANG)  # new keys at the end, the rest in place
    write_json(lang_path, lang)
    return {"lairs": list(LAIRS), "regions": {r["name"]: len(done[r["name"]]) for r in REGIONS},
            "painted": {k: len(v) for k, v in painted.items()}, "tables": changed}


def preview(path):
    """Draws the regions over the map's biomes (each region its own tint) for checking where they lie."""
    from PIL import Image
    map_dir = os.path.join(OUT, NS, "map")
    meta = load(os.path.join(map_dir, "map.json"))
    with gzip.open(os.path.join(map_dir, "biomes.bin.gz")) as f:
        raw = f.read()
    width = meta["width"]
    done = {}
    for r in REGIONS:
        done[r["name"]] = rasterize(r, done)
    xs = [p[0] for v in done.values() for p in v]
    zs = [p[1] for v in done.values() for p in v]
    x0, z0, x1, z1 = min(xs) - 20, min(zs) - 20, max(xs) + 20, max(zs) + 20
    img = Image.new("RGB", (x1 - x0, z1 - z0))
    tints = [(255, 80, 80), (80, 255, 80), (80, 160, 255), (255, 220, 60), (220, 80, 255)]
    for z in range(z0, z1):
        for x in range(x0, x1):
            k = raw[z * width + x]
            c = [(k * 53) % 200 + 30, (k * 97) % 200 + 30, (k * 151) % 200 + 30]
            for i, r in enumerate(REGIONS):
                if (x, z) in done[r["name"]]:
                    c = [(c[j] + tints[i % len(tints)][j]) // 2 for j in range(3)]
            img.putpixel((x - x0, z - z0), tuple(c))
    img.resize(((x1 - x0) * 2, (z1 - z0) * 2), Image.NEAREST).save(path)


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "--preview":
        preview(sys.argv[2])
    else:
        print(json.dumps(run()))
