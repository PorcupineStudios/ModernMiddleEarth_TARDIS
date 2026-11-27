"""
Per-biome cave settings (Aaron 2026-11-18: "tone down the cave entrances and ESPECIALLY the ravines. Below the
surface I still want all the same caves. Make a biome config that has every biome split up into its country's
regions").

Writes three things:

1. ``data/lotr_port/biome_config.json``: every Middle-earth biome under its land and region, with the default of
   each cave setting. The mod builds its biome config file (``config/lotr_port-biomes.toml``, BiomeCaveConfig.java)
   from this list, so the config always has every biome; the numbers here are only the defaults it starts with.
   Regions are LOTR's waypoint regions (``#lotr_port:waypoint_region/*``); lands group them (LANDS below).
2. Configured carvers ``lotr_port:cave``, ``lotr_port:cave_extra_underground`` and ``lotr_port:canyon``: vanilla's
   own three with the carver type swapped for the port's (world/feature/BiomeCarvers.java), which ask the biome's
   settings how often to carve and how far under the ground to stay. Every biome's vanilla carvers are swapped for
   them.
3. Nothing else: the noise caves read the same settings through the density function ``lotr_port:cave_setting``
   (noisecaves.py).

Runs on the generated data (``python biomeconfig.py``); generate.py calls it after noisecaves.py. Idempotent.
"""
import json
import os
import sys
import zipfile

from woodsets import MC_1211, OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"

# land -> its waypoint regions, west to east and north to south
LANDS = {
    "lindon": ["lindon", "blue_mountains"],
    "eriador": ["shire", "bree_land", "eriador", "lone_lands", "midgewater", "old_forest", "trollshaws",
                "rivendell_vale", "ettenmoors", "coldfells", "angmar", "eregion", "minhiriath", "enedwaith",
                "dunland", "pukel"],
    "rhovanion": ["misty_mountains", "grey_mountains", "vales_of_anduin", "lothlorien", "mirkwood", "woodland_realm",
                  "wilderland", "dale", "iron_hills", "brown_lands", "emyn_muil", "nindalf", "dagorlad"],
    "rohan": ["rohan", "fangorn", "nan_curunir"],
    "gondor": ["gondor", "white_mountains", "ithilien", "lossarnach", "lebennin", "lamedon", "blackroot_vale",
               "pinnath_gelin", "dor_en_ernil", "tolfalas"],
    "mordor": ["mordor", "nan_ungol", "nurn"],
    "rhun": ["rhun", "rhun_khaganate", "tol_rhunaer", "dorwinion", "red_mountains"],
    "harad": ["harondor", "lostladen", "harad_desert", "harnedor", "southron_coasts", "umbar", "gulf_harad",
              "harad_mountains", "far_harad", "far_harad_jungle", "pertorogwaith", "pertorogwaith_forest"],
    "forodwaith": ["forodwaith"],
    "seas": ["ocean", "meneltarma", "waters"],
}
# biomes in no waypoint region
EXTRA = {"river": "waters", "lake": "waters", "fangorn_wasteland": "fangorn", "mordor_mountains": "mordor",
         "entwash_mouth": "rohan", "fangorn_felled_eaves": "fangorn"}  # the last: compat.py's, on fangorn_wasteland
WATER = ("ocean", "river", "lake", "beach")

# The settings, in config order: (key, kind, low, high, help). Kind "chance" and "number" are decimals, "blocks" a
# whole number, "switch" true/false. The Java side reads this list too, so a new setting is added here only.
SETTINGS = [
    ("caveEntrances", "number", 0.0, 1.0,
     "How much the 1.18 noise caves open up at the ground: 0 = never (caves stay closed under the ground), "
     "1 = vanilla 1.18 (big open pits and many tunnel mouths). Only the top few blocks: the caves below are the same."),
    ("ravines", "number", 0.0, 1.0,
     "Ravines (vanilla's canyon carver and the port's mountain canyons): the share of them kept, 1 = all (vanilla), "
     "0.5 = half, 0 = none."),
    ("carverCaves", "number", 0.0, 1.0,
     "Classic winding carver tunnels (vanilla's cave carvers and the port's mountain caves): the share kept, "
     "1 = all (vanilla), 0 = none."),
    ("surfaceCover", "blocks", 0, 32,
     "Ravines and carver tunnels stop this many blocks under the ground, so they cut through the underground but "
     "don't open at the surface. 0 = they may open at the surface (vanilla)."),
    ("cheeseCaves", "switch", None, None, "1.18 cheese caves: big caverns with stone pillars."),
    ("spaghettiCaves", "switch", None, None, "1.18 spaghetti caves: long, wide winding tunnels."),
    ("noodleCaves", "switch", None, None, "1.18 noodle caves: thin, twisting tunnels."),
    ("lushCaves", "number", 0.0, 4.0,
     "Lush cave pockets (moss, azalea, glow berries, axolotls) where this biome has them: how many against the "
     "default 1; 0 = none. Biomes without lush pockets don't have this setting."),
    ("dripstoneCaves", "number", 0.0, 4.0,
     "Dripstone cave pockets where this biome has them: how many against the default 1; 0 = none. Biomes without "
     "dripstone pockets don't have this setting."),
]


def defaults(name, mountain, pockets):
    """The starting settings: toned-down openings on the lowlands, more in hills, most in the mountains."""
    if mountain:
        d = {"caveEntrances": 0.5, "ravines": 0.6, "carverCaves": 1.0, "surfaceCover": 0}
    elif "hills" in name or "downs" in name or "moors" in name or name in ("emyn_muil", "ettenmoors", "coldfells"):
        d = {"caveEntrances": 0.3, "ravines": 0.3, "carverCaves": 1.0, "surfaceCover": 4}
    elif any(w in name for w in WATER):
        d = {"caveEntrances": 0.0, "ravines": 0.25, "carverCaves": 1.0, "surfaceCover": 8}
    else:
        d = {"caveEntrances": 0.15, "ravines": 0.2, "carverCaves": 1.0, "surfaceCover": 8}
    d.update({"cheeseCaves": True, "spaghettiCaves": True, "noodleCaves": True})
    for kind in ("lush", "dripstone"):
        if kind in pockets:
            d[kind + "Caves"] = 1.0
    return d


def pockets_of(biome):
    """Which cave pocket kinds (caves.py) a biome's features hold."""
    text = json.dumps(biome.get("features", []))
    return {k for k in ("lush", "dripstone") if f"{NS}:{k}_" in text}


def run(out=OUT):
    data = os.path.join(out, "data", NS)
    biome_dir = os.path.join(data, "worldgen", "biome")
    tag_dir = os.path.join(data, "tags", "worldgen", "biome", "waypoint_region")
    region_of = dict()
    for f in sorted(os.listdir(tag_dir)):
        for v in json.load(open(os.path.join(tag_dir, f), encoding="utf-8"))["values"]:
            region_of[(v if isinstance(v, str) else v["id"]).split(":")[1]] = f[:-5]
    for b, r in EXTRA.items():
        region_of.setdefault(b, r)
    land_of = {r: land for land, regions in LANDS.items() for r in regions}
    mountains = {v.split(":")[1] for v in json.load(open(os.path.join(
        out, "data", "minecraft", "tags", "worldgen", "biome", "is_mountain.json"), encoding="utf-8"))["values"]}

    lands = {land: {r: {} for r in regions} for land, regions in LANDS.items()}
    swapped = 0
    for f in sorted(os.listdir(biome_dir)):
        name = f[:-5]
        path = os.path.join(biome_dir, f)
        biome = json.load(open(path, encoding="utf-8"))
        region = region_of.get(name)
        if region is None or region not in land_of:
            raise SystemExit(f"biomeconfig: {name} (region {region}) has no land: add it to LANDS or EXTRA")
        lands[land_of[region]][region][name] = defaults(name, name in mountains, pockets_of(biome))
        carvers = biome.get("carvers", {})
        new = {step: [c.replace("minecraft:", f"{NS}:") if c in VANILLA_CARVERS else c for c in cs]
               for step, cs in carvers.items()}
        if new != carvers:
            biome["carvers"] = new
            write_json(path, biome)
            swapped += 1
    for land in lands.values():  # regions without biomes of their own (all theirs went elsewhere)
        for r in [r for r, bs in land.items() if not bs]:
            del land[r]
    settings = [{"key": k, "kind": kind, "min": lo, "max": hi, "help": h} for k, kind, lo, hi, h in SETTINGS]
    write_json(os.path.join(data, "biome_config.json"), {
        "_comment": "Generated by converter/biomeconfig.py: every Middle-earth biome by land and region with the "
                    "defaults of its cave settings. The game's config/lotr_port-biomes.toml is built from it.",
        "settings": settings, "lands": lands})

    jar = zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1]))
    for name in ("cave", "cave_extra_underground", "canyon"):
        carver = json.loads(jar.read(f"data/minecraft/worldgen/configured_carver/{name}.json"))
        carver["type"] = f"{NS}:{'canyon' if carver['type'] == 'minecraft:canyon' else 'cave'}"
        write_json(os.path.join(data, "worldgen", "configured_carver", f"{name}.json"), carver)
    return {"biomes": sum(len(bs) for land in lands.values() for bs in land.values()),
            "regions": sum(len(land) for land in lands.values()), "carvers_swapped": swapped}


VANILLA_CARVERS = ("minecraft:cave", "minecraft:cave_extra_underground", "minecraft:canyon")

if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
