"""
1.18's noise caves under Middle-earth: cheese caves (big caverns with pillars), spaghetti and noodle tunnels and
cave entrances, as vanilla's overworld has them, plus aquifers (air caves with water and lava pools at their own
levels; with aquifers off the game floods every air pocket under sea level, which is why both come together).

LOTR's terrain density (world.py: the map's ground, 4x steeper below it, LOTR's hills in their band) is on the scale
of vanilla's "sloped cheese" (about 0.17 a block under the ground against vanilla's 0.12), so vanilla's cave formula
wraps it as is: near the ground (density < 1.5625, ~9 blocks) only entrances cut it; deeper the cheese, spaghetti
and pillar caves do; noodles everywhere (vanilla's own density functions minecraft:overworld/caves/*). The terrain is
a named density function, lotr_port:middle_earth/terrain, cached once per position, so the cave branches share it.
Caves only below CAVE_TOP (saves the work in the 640-high ranges, whose own carvers make their caves); the
noise-band cuts (NoiseTop) stay right because caves only ever remove rock.

Runs on the generated noise settings (``python noisecaves.py``); generate.py calls it after world.py's output is
written. Idempotent: an already wrapped final density is left as it is.
"""
import json
import os
import sys
import zipfile

from woodsets import MC_1211, OUT, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"
CAVE_TOP = 200
TOP_Y = 640
TERRAIN = f"{NS}:middle_earth/terrain"
MARK = "minecraft:overworld/caves/entrances"


def underground(s):
    """Vanilla's NoiseRouterData.underground on terrain density s (cheese, entrances, spaghetti, pillars)."""
    cheese = {"type": "minecraft:add",
              "argument1": {"type": "minecraft:mul", "argument1": 4.0, "argument2": {"type": "minecraft:square",
                            "argument": {"type": "minecraft:noise", "noise": "minecraft:cave_layer", "xz_scale": 1.0,
                                         "y_scale": 8.0}}},
              "argument2": {"type": "minecraft:add",
                            "argument1": {"type": "minecraft:clamp", "input": {
                                "type": "minecraft:add", "argument1": 0.27, "argument2": {
                                    "type": "minecraft:noise", "noise": "minecraft:cave_cheese", "xz_scale": 1.0,
                                    "y_scale": 0.6666666666666666}}, "min": -1.0, "max": 1.0},
                            "argument2": {"type": "minecraft:clamp", "input": {
                                "type": "minecraft:add", "argument1": 1.5,
                                "argument2": {"type": "minecraft:mul", "argument1": -0.64, "argument2": s}},
                                "min": 0.0, "max": 0.5}}}
    caves = {"type": "minecraft:min",
             "argument1": {"type": "minecraft:min", "argument1": cheese, "argument2": "minecraft:overworld/caves/entrances"},
             "argument2": {"type": "minecraft:add", "argument1": "minecraft:overworld/caves/spaghetti_2d",
                           "argument2": "minecraft:overworld/caves/spaghetti_roughness_function"}}
    pillars = {"type": "minecraft:range_choice", "input": "minecraft:overworld/caves/pillars",
               "min_inclusive": -1000000.0, "max_exclusive": 0.03, "when_in_range": -1000000.0,
               "when_out_of_range": "minecraft:overworld/caves/pillars"}
    return {"type": "minecraft:max", "argument1": caves, "argument2": pillars}


def wrap(terrain_ref):
    """The final density: the terrain with vanilla's caves cut into it under CAVE_TOP, then noodles."""
    s = terrain_ref
    caved = {"type": "minecraft:range_choice", "input": s, "min_inclusive": -1000000.0, "max_exclusive": 1.5625,
             "when_in_range": {"type": "minecraft:min", "argument1": s, "argument2": {
                 "type": "minecraft:mul", "argument1": 5.0, "argument2": "minecraft:overworld/caves/entrances"}},
             "when_out_of_range": underground(s)}
    y = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": TOP_Y, "from_value": -64.0,
         "to_value": float(TOP_Y)}
    below = {"type": "minecraft:range_choice", "input": y, "min_inclusive": -1000.0, "max_exclusive": float(CAVE_TOP),
             "when_in_range": caved, "when_out_of_range": s}
    return {"type": "minecraft:min", "argument1": {"type": "minecraft:interpolated", "argument": below},
            "argument2": "minecraft:overworld/caves/noodle"}


def run(out=OUT):
    path = os.path.join(out, "data", NS, "worldgen", "noise_settings", "middle_earth.json")
    ns = json.load(open(path, encoding="utf-8"))
    router = ns["noise_router"]
    final = router["final_density"]
    if MARK in json.dumps(final):
        return {"wrapped": False}
    if final.get("type") != "minecraft:interpolated":
        raise SystemExit("noisecaves: final density isn't world.py's interpolated terrain")
    # the terrain itself becomes a named, once-cached function the cave branches share
    write_json(os.path.join(out, "data", NS, "worldgen", "density_function", "middle_earth", "terrain.json"),
               {"type": "minecraft:cache_once", "argument": final["argument"]})
    router["final_density"] = wrap(TERRAIN)
    # aquifers: vanilla's noises (with them off, the game floods every air pocket under sea level)
    vanilla = json.loads(zipfile.ZipFile(os.path.join(HERE, "cache", MC_1211[1])).read(
        "data/minecraft/worldgen/noise_settings/overworld.json"))["noise_router"]
    for k in ("barrier", "fluid_level_floodedness", "fluid_level_spread", "lava"):
        router[k] = vanilla[k]
    ns["aquifers_enabled"] = True
    write_json(path, ns)
    return {"wrapped": True, "cave_top": CAVE_TOP}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
