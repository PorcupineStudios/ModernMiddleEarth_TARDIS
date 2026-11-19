"""
The crafted parts TARDIS repairs and upgrades cost (Aaron 2026-11-19: "upgrades cost special items you have to craft
(eg. artron battery made from gold and jewels and bronze)"), and the repair / upgrade data that uses them.

Writes, under tardis/src/main/resources:
- per part (items registered in block/TardisBlocks.PARTS): its crafting recipe, item model, a placeholder picture
  (until Aaron's art) and English name;
- the costs: every repair costs its subsystem's part, `level` of them (PART_FOR), plus the charge it had;
- the upgrade tree (research): multi-level nodes and the two flight upgrades that unlock the console's flight
  controls (Aaron 2026-11-19: "the only unlockable console controls should be the materialized flight and the
  dematerialized flight (including xyz and dimensions), time and rooms will come later");
- the console controls' requirements: flight controls need their upgrade, time and room controls (and the two that
  do nothing yet) can't be unlocked for now.

Run from the repo root: python3 tardis/tools/upgrade_parts.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "tardis_rb"
DATA = os.path.join(ROOT, "data", NS)
ASSETS = os.path.join(ROOT, "assets", NS)


def tag(t):
    return {"tag": t}


def item(i):
    return {"item": i}


# id -> (English name, pattern, key, count, picture colours (body, detail))
PARTS = {
    "artron_battery": ("Artron Battery", ["BGB", "GJG", "BGB"],
                       {"B": [tag("c:ingots/bronze"), tag("c:ingots/brass")], "G": tag("c:ingots/gold"),
                        "J": tag("c:gems")}, 1, ((214, 170, 60), (90, 200, 255))),
    "hull_plate": ("Hull Plate", ["III", "IBI", "III"],
                   {"I": tag("c:ingots/iron"), "B": [tag("c:ingots/bronze"), tag("c:ingots/brass")]}, 2,
                   ((150, 160, 175), (60, 80, 140))),
    "temporal_circuit": ("Temporal Circuit", ["RQR", "CGC", "RQR"],
                         {"R": tag("c:dusts/redstone"), "Q": tag("c:gems/quartz"), "C": tag("c:ingots/copper"),
                          "G": tag("c:ingots/gold")}, 2, ((40, 120, 60), (230, 200, 80))),
    "chronon_lens": ("Chronon Lens", ["GAG", "ARA", "GAG"],
                     {"G": tag("c:glass_panes"), "A": tag("c:gems/amethyst"), "R": tag("c:dusts/redstone")}, 1,
                     ((170, 120, 220), (230, 240, 255))),
    "stabilizer_coil": ("Stabilizer Coil", ["CIC", "CRC", "CIC"],
                        {"C": tag("c:ingots/copper"), "I": tag("c:ingots/iron"), "R": tag("c:dusts/redstone")}, 1,
                        ((200, 110, 60), (180, 180, 190))),
}
# subsystem -> part its repairs cost
PART_FOR = {"power": "artron_battery", "hull": "hull_plate", "rooms": "hull_plate", "navigation": "temporal_circuit",
            "console": "temporal_circuit", "scanner": "chronon_lens", "stabilizers": "stabilizer_coil"}
# the upgrade tree: id -> node
RESEARCH = {
    "artron_theory": {"levels": [
        {"cost": {"items": [{"ingredient": item(f"{NS}:artron_battery"), "count": 1}]}, "stats": {"charge_cap": 100}},
        {"cost": {"items": [{"ingredient": item(f"{NS}:artron_battery"), "count": 2}]}, "stats": {"charge_cap": 200}},
        {"cost": {"items": [{"ingredient": item(f"{NS}:artron_battery"), "count": 4}]}, "stats": {"charge_cap": 400}},
    ]},
    "hop_calibration": {"levels": [
        {"cost": {"items": [{"ingredient": item(f"{NS}:temporal_circuit"), "count": n}]}, "stats": {"range": 1000}}
        for n in (1, 2, 3)]},
    "spatial_mapping": {"parents": [f"{NS}:artron_theory"],
                        "cost": {"items": [{"ingredient": item(f"{NS}:chronon_lens"), "count": 2}], "charge": 50}},
    "materialised_flight": {
        "requirements": {"type": f"{NS}:subsystem_level", "subsystem": f"{NS}:navigation", "min": 1},
        "cost": {"items": [{"ingredient": item(f"{NS}:temporal_circuit"), "count": 2},
                           {"ingredient": item(f"{NS}:stabilizer_coil"), "count": 1}]}},
    "dematerialised_flight": {
        "parents": [f"{NS}:materialised_flight"],
        "cost": {"items": [{"ingredient": item(f"{NS}:temporal_circuit"), "count": 4},
                           {"ingredient": item(f"{NS}:artron_battery"), "count": 1}], "charge": 50}},
    # flight speed (Aaron 2026-11-19): x(1 + stat) on the box's speed in the world / in the vortex
    "flight_speed": {"parents": [f"{NS}:materialised_flight"], "levels": [
        {"cost": {"items": [{"ingredient": item(f"{NS}:stabilizer_coil"), "count": n},
                            {"ingredient": item(f"{NS}:artron_battery"), "count": 1}]}, "stats": {"flight_speed": 0.25}}
        for n in (1, 2, 4)]},
    "vortex_speed": {"parents": [f"{NS}:dematerialised_flight"], "levels": [
        {"cost": {"items": [{"ingredient": item(f"{NS}:temporal_circuit"), "count": n},
                            {"ingredient": item(f"{NS}:artron_battery"), "count": 1}]}, "stats": {"vortex_speed": 0.25}}
        for n in (2, 3, 5)]},
}
MATERIALISED = {"thrusters", "flight_yoke", "phase_lever", "phase_key", "door_control", "facing_compass"}
DEMATERIALISED = {"coordinated_switch", "demat_lever", "handbrake", "land_switch", "dimension_dial", "relative_switch", "randomiser",
                  "fast_return", "save_switch", "memory_bank", "scanner_screen"}
LATER = {"time_lever", "time_drums", "time_unit_dial", "room_deleter", "schema_chooser", "screwdriver_holder",
         "telepathic_circuits"}
NEVER = {"type": "ringbearer_api:not", "condition": {"type": "ringbearer_api:always"}}
STATS = {"range": "Hop range", "charge_cap": "Charge capacity", "room_cap": "Room capacity",
         "controls": "Console controls", "health": "Hull health", "wormhole_sight": "Wormhole sight",
         "pull_resist": "Pull resistance", "flight_speed": "Flight speed bonus",
         "vortex_speed": "Vortex speed bonus"}
NODES = {"artron_theory": "Artron Theory", "hop_calibration": "Hop Calibration", "spatial_mapping": "Spatial Mapping",
         "materialised_flight": "Materialised Flight", "dematerialised_flight": "Dematerialised Flight",
         "flight_speed": "Flight Speed", "vortex_speed": "Vortex Speed"}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def picture(path, body, detail):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    dark = tuple(max(0, c - 60) for c in body)
    d.rectangle([3, 2, 12, 13], fill=body + (255,), outline=dark + (255,))
    d.rectangle([5, 5, 10, 10], fill=detail + (255,))
    d.line([3, 2, 12, 2], fill=tuple(min(255, c + 50) for c in body) + (255,))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def main():
    lang_path = os.path.join(ASSETS, "lang", "en_us.json")
    with open(lang_path, encoding="utf-8") as f:
        lang = json.load(f)
    for pid, (name, pattern, key, count, colours) in PARTS.items():
        write(os.path.join(DATA, "recipe", pid + ".json"), {
            "type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key,
            "result": {"id": f"{NS}:{pid}", "count": count}})
        write(os.path.join(ASSETS, "models", "item", pid + ".json"),
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{pid}"}})
        picture(os.path.join(ASSETS, "textures", "item", pid + ".png"), *colours)
        lang[f"item.{NS}.{pid}"] = name
    for k, v in STATS.items():
        lang[f"{NS}.stat.{k}"] = v
    for k, v in NODES.items():
        lang[f"{NS}.node.{k}"] = v
    lang[f"{NS}.workshop.tab.upgrades"] = "Upgrades"
    write(lang_path, dict(sorted(lang.items())))

    # repairs: their subsystem's part, `level` of them, plus the charge they had
    repairs = os.path.join(DATA, "tardis", "repair")
    for f in sorted(os.listdir(repairs)):
        with open(os.path.join(repairs, f), encoding="utf-8") as fh:
            r = json.load(fh)
        sub = r["subsystem"].split(":")[1]
        old = r.get("cost")
        charge = old.get("charge", 0) if isinstance(old, dict) else 0
        cost = {"items": [{"ingredient": item(f"{NS}:{PART_FOR[sub]}"), "count": r["level"]}]}
        if charge:
            cost["charge"] = charge
        r["cost"] = cost
        write(os.path.join(repairs, f), r)
    research = os.path.join(DATA, "tardis", "research")
    for f in os.listdir(research):
        os.remove(os.path.join(research, f))
    for rid, node in RESEARCH.items():
        write(os.path.join(research, rid + ".json"), node)

    controls = os.path.join(DATA, "tardis", "control")
    for f in sorted(os.listdir(controls)):
        cid = f[:-5]
        path = os.path.join(controls, f)
        with open(path, encoding="utf-8") as fh:
            c = json.load(fh)
        if cid in LATER:
            c["requirements"] = NEVER
        elif cid in MATERIALISED or cid in DEMATERIALISED or cid.startswith(("x_", "y_", "z_")):
            node = "materialised_flight" if cid in MATERIALISED else "dematerialised_flight"
            c["requirements"] = {"type": f"{NS}:research", "research": f"{NS}:{node}"}
        write(path, c)
    # the flight features follow their upgrades
    for feat, node in (("piloting", "materialised_flight"), ("thrusters", "materialised_flight"),
                       ("exterior_phase", "materialised_flight"), ("short_hop", "dematerialised_flight")):
        path = os.path.join(DATA, "tardis", "feature", feat + ".json")
        if os.path.exists(path):
            with open(path, encoding="utf-8") as fh:
                feature = json.load(fh)
            feature["requirements"] = {"type": f"{NS}:research", "research": f"{NS}:{node}"}
            write(path, feature)


if __name__ == "__main__":
    main()
