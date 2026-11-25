"""
A Create mechanical crafter per LOTR faction crafting table (compat/create, only with Create installed).

Each is Create's crafter in every way, tinted the faction's colour; a crafter grid holding one also crafts that
table's recipes (Java: FactionCrafters, FactionCrafting, mixin/create). Each is made at its own table from Create's
crafter recipe (brass casing, electron tube, crafting table -> 3) and needs alignment over 0 with the table's faction
(the recipe's `alignment`, checked by FactionCraftingMenu); machines never make it.

Runs on top of the generated content (no LOTR jar or Create jar needed): rerun alone with ``python
create_crafters.py``; it rewrites its own files every run. Everything it writes that names the crafters is safe
without Create: the recipes carry `neoforge:mod_loaded create`, tag entries are `required: false`, loot tables and
models of unregistered blocks are never read.
"""
import json
import os
import sys

from renames import NS, OUT

# table -> faction (LOTR's LOTRBlockCraftingTable subclasses name their faction)
TABLES = {
    "angmar": "angmar", "blue_mountains": "blue_mountains", "bree_land": "bree", "dalish": "dale",
    "dol_amroth": "gondor", "dol_guldur": "dol_guldur", "dorwinion": "dorwinion", "dunlending": "dunland",
    "dwarven": "durins_folk", "galadhrim": "lothlorien", "gondorian": "gondor", "gulfen": "near_harad",
    "gundabad": "gundabad", "half_troll": "half_troll", "haradric": "near_harad", "hobbit": "hobbit",
    "lindon": "high_elf", "morgul": "mordor", "morwaith": "morwaith", "ranger": "ranger_north", "rh_nic": "rhudel",
    "rivendell": "high_elf", "rohirric": "rohan", "taurethrim": "taurethrim", "umbaric": "near_harad",
    "uruk": "isengard", "wood_elven": "wood_elf",
}
# the tint is the faction colour lightened this far towards white, so Create's brass and wood still show through
LIGHTEN = 0.3
# Create's mechanical crafter recipe (data/create/recipe/crafting/kinetics/mechanical_crafter.json)
PATTERN = ["B", "C", "R"]
KEY = {"B": {"item": "create:electron_tube"}, "C": {"item": "create:brass_casing"},
       "R": {"item": "minecraft:crafting_table"}}
COUNT = 3
ALIGNMENT_ABOVE = 0.0
CONDITION = [{"type": "neoforge:mod_loaded", "modid": "create"}]
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
POINTING = ("up", "down", "left", "right")


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def lighten(c, t=LIGHTEN):
    r, g, b = (c >> 16) & 255, (c >> 8) & 255, c & 255
    r, g, b = (round(x + (255 - x) * t) for x in (r, g, b))
    return (r << 16) | (g << 8) | b


def run(out=OUT):
    data, assets, content = os.path.join(out, "data"), os.path.join(out, "assets", NS), os.path.join(out, NS, "content")
    blocks = {b["id"] for b in load(os.path.join(content, "blocks.json"))}
    lang_path = os.path.join(assets, "lang", "en_us.json")
    lang = load(lang_path)
    for k in [k for k in lang if k.startswith(f"block.{NS}.") and k.endswith("_mechanical_crafter")]:
        del lang[k]
    recipes = os.path.join(data, NS, "recipe", "own", "faction_crafter")
    if os.path.isdir(recipes):
        for f in os.listdir(recipes):
            os.remove(os.path.join(recipes, f))
    defs = []
    for table_base, faction in sorted(TABLES.items()):
        table = f"{table_base}_crafting_table"
        if table not in blocks:
            print(f"create_crafters: no table {table}")
            continue
        colour = load(os.path.join(data, NS, "faction", faction + ".json"))["color"]
        cid = f"{table_base}_mechanical_crafter"
        defs.append({"id": cid, "table": f"{NS}:{table}", "faction": f"{NS}:{faction}", "tint": lighten(colour)})
        write(os.path.join(assets, "blockstates", cid + ".json"), {"variants": {
            f"facing={f},pointing={p}": ({"model": "create:block/mechanical_crafter/block", "y": y} if y else
                                         {"model": "create:block/mechanical_crafter/block"})
            for f, y in FACINGS.items() for p in POINTING}})
        write(os.path.join(assets, "models", "item", cid + ".json"), {"parent": "create:item/mechanical_crafter"})
        write(os.path.join(data, NS, "loot_table", "blocks", cid + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
                                                  "entries": [{"type": "minecraft:item", "name": f"{NS}:{cid}"}],
                                                  "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{cid}"})
        write(os.path.join(recipes, cid + ".json"), {
            "neoforge:conditions": CONDITION, "type": f"{NS}:faction_crafting", "table": f"{NS}:{table}",
            "alignment": {"faction": f"{NS}:{faction}", "above": ALIGNMENT_ABOVE},
            "recipe": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": PATTERN, "key": KEY,
                       "result": {"id": f"{NS}:{cid}", "count": COUNT}}})
        name = lang.get(f"block.{NS}.{table}", table.replace("_", " ").title())
        lang[f"block.{NS}.{cid}"] = name.replace("Crafting Table", "Mechanical Crafter")
    write(os.path.join(content, "create_crafters.json"), defs)
    write(lang_path, lang)
    # mined like Create's crafter (axe or pickaxe); optional entries, since without Create they don't exist
    for tag in ("mineable/axe", "mineable/pickaxe"):
        path = os.path.join(data, "minecraft", "tags", "block", tag + ".json")
        t = load(path) if os.path.exists(path) else {"replace": False, "values": []}
        t["values"] = [v for v in t["values"] if not (isinstance(v, dict) and v["id"].endswith("_mechanical_crafter"))]
        t["values"] += [{"id": f"{NS}:{d['id']}", "required": False} for d in defs]
        write(path, t)
    return {"crafters": len(defs)}


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
