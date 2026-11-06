"""
Create cross-mod recipes for the LOTR port.

1. `python3 converter/create_compat.py --extract <create jar>` reads every recipe in Create's jar into
   `converter/create/recipes.json.gz` (committed; the jar is not). Run it again when the pack's Create version
   changes (tardis/gradle.properties create_version).
2. `generate.py` calls `CreateCompat(...).run()`. It cross-references Create's recipes with LOTR's (crafting,
   smelting, machines), LOTR's ore dictionary and the exported block drops, and writes cross-mod recipes under
   `data/lotr_port/recipe/create/`. Each recipe loads only when Create is installed (neoforge:mod_loaded). It also
   writes the common `c:ores/*` and `c:nuggets/*` tags that Create's own tag-driven recipes look for.

Every rule copies a pattern Create already uses for a vanilla analogue, so numbers (chances, counts, processing
time) come from Create's own recipe, never made up:

- ores:      LOTR's ore dictionary -> c:ores/<metal>. Create's own crushing recipes for c:ores/tin, c:ores/silver and
             c:ores/copper then apply. Their crushed ores smelt and wash into LOTR's ingots and nuggets where Create
             only ships other mods' outputs.
- gem ores:  ores that drop an item (sulfur, saltpeter, durnor, edhelvir, gulduril, gems, LOTR glowstone) are
             crushed like Create's diamond ore.
- metal ores without a crushed form (mithril, Morgul iron) are crushed like Create's iron ore, giving the smelted
             ingot instead of a crushed ore.
- millstone: LOTR's millstone recipes become create:milling (timing from Create's cobblestone milling), unless
             Create already mills that input.
- dyes:      LOTR's one-item flower -> dye crafting recipes are milled like Create's poppy (two dyes).
- alloys:    each alloy forge's two-input alloys become heated create:mixing (like Create's brass).
- brewing:   the barrel's drinks become create:mixing with 1000 mB water (like Create's dough).
- washing and crushing look-alikes: LOTR sands and gravels (white sand, Mordor gravel, obsidian gravel) are
             washed and crushed like vanilla sand and gravel.
"""
import argparse
import collections
import copy
import gzip
import json
import os
import zipfile

from assets import NS, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
DIGEST = os.path.join(HERE, "create", "recipes.json.gz")
CONDITION = [{"type": "neoforge:mod_loaded", "modid": "create"}]

# LOTR ore dictionary names -> common tag paths (oreTin -> c:ores/tin)
OREDICT_PREFIX = {"ore": "ores", "nugget": "nuggets", "ingot": "ingots", "gem": "gems", "dust": "dusts"}
# Create's crushed ores and the LOTR metal each one smelts and washes into
CRUSHED = {"tin": "create:crushed_raw_tin", "silver": "create:crushed_raw_silver"}
# Common tags LOTR recipes use and the vanilla item Create's own recipes use for the same thing
TAG_ITEM = {"c:cobblestones": "minecraft:cobblestone", "c:stones": "minecraft:stone", "c:sands": "minecraft:sand",
            "c:gravels": "minecraft:gravel"}
# LOTR blocks that look like a vanilla one Create washes or crushes (suffix after the last "_")
LOOKALIKE_SUFFIXES = ("sand", "gravel")


def extract(jar, out=DIGEST):
    """Writes {path: recipe} for every recipe in Create's jar (data/*/recipe/**.json)."""
    recipes = {}
    with zipfile.ZipFile(jar) as z:
        for n in sorted(z.namelist()):
            if "/recipe/" in n and n.endswith(".json") and n.startswith("data/"):
                try:
                    recipes[n] = json.loads(z.read(n))
                except ValueError:
                    continue
    os.makedirs(os.path.dirname(out), exist_ok=True)
    with gzip.open(out, "wt", encoding="utf-8") as f:
        json.dump(recipes, f, sort_keys=True)
    return len(recipes)


def inputs(r):
    out = []
    for i in r.get("ingredients", []) or ([r["ingredient"]] if "ingredient" in r else []):
        out.extend(i if isinstance(i, list) else [i])
    return out


class CreateCompat:
    def __init__(self, gen, data_dir, export_dir):
        self.gen = gen  # generate.Gen: idtable, ingredient(), result(), report, items/blocks
        self.data = data_dir
        self.export = export_dir
        self.out = os.path.join(data_dir, NS, "recipe", "create")
        self.count = collections.Counter()
        self.crossref = []  # what matched what, for the report
        with gzip.open(DIGEST, "rt", encoding="utf-8") as f:
            self.create = json.load(f)
        self.by_type = collections.defaultdict(list)
        for path, r in self.create.items():
            self.by_type[r.get("type", "")].append((path, r))
        self.items = {f"{NS}:{i['id']}" for i in gen.items}
        self.items |= {f"{NS}:{b['id']}" for b in gen.blocks if b["id"] not in gen.hidden}

    # ------------------------------------------------------------------ helpers

    def template(self, path):
        return copy.deepcopy(self.create[path])

    def create_inputs(self, rtype):
        """Every single item id Create already uses as an input of this recipe type."""
        out = set()
        for _, r in self.by_type[rtype]:
            for i in inputs(r):
                if isinstance(i, dict) and "item" in i:
                    out.add(i["item"])
        return out

    def new_id(self, old, meta=0):
        ing = self.gen.ingredient({"item": old, "meta": meta})
        return ing.get("item") if isinstance(ing, dict) else None

    def write(self, rtype, name, recipe, why):
        recipe = {"neoforge:conditions": CONDITION, **recipe}
        for res in recipe.get("results", []):
            if res.get("id", "").startswith(NS + ":") and res["id"] not in self.items:
                self.gen.report["create_compat_unknown_items"].append(f"{name}: {res['id']}")
                return
        folder = rtype.split(":")[1]
        write_json(os.path.join(self.out, folder, name + ".json"), recipe)
        self.count[rtype] += 1
        self.crossref.append({"recipe": f"{NS}:create/{folder}/{name}", "type": rtype, "because": why})

    def load_export(self, name):
        p = os.path.join(self.export, name)
        if not os.path.exists(p):
            return None
        with open(p, encoding="utf-8") as f:
            return json.load(f)

    # ------------------------------------------------------------------ rules

    def tags(self):
        """c:ores/<x>, c:nuggets/<x>... from LOTR's ore dictionary, for Create's tag-driven recipes."""
        ore = self.load_export("oredict.json") or {}
        tags = collections.defaultdict(set)
        for name, stacks in ore.items():
            for prefix, folder in OREDICT_PREFIX.items():
                if name.startswith(prefix) and name[len(prefix):][:1].isupper():
                    path = f"{folder}/{name[len(prefix):].lower()}"
                    for s in stacks:
                        if s["item"].startswith("lotr:"):
                            new = self.new_id(s["item"], s.get("meta", 0))
                            if new and new in self.items:
                                tags[path].add(new)
        for path, ids in sorted(tags.items()):
            if path.startswith("ingots/"):
                continue  # generate.py already writes ingot tags (recipes use them)
            write_json(os.path.join(self.data, "c", "tags", "item", path + ".json"),
                       {"replace": False, "values": sorted(ids)})
            self.crossref.append({"tag": f"c:{path}", "values": sorted(ids),
                                  "because": "LOTR's ore dictionary; Create's recipes for this tag now apply"})
        return tags

    def crushed_metals(self, tags):
        """Create crushes c:ores/tin and c:ores/silver into its crushed ores but only ships other mods' ingots."""
        for metal, crushed in CRUSHED.items():
            ingot = next(iter(sorted(tags.get(f"ingots/{metal}", []))), None)
            if not ingot or not tags.get(f"ores/{metal}"):
                continue
            for kind, time in (("minecraft:smelting", 200), ("minecraft:blasting", 100)):
                self.write(kind, f"{metal}_ingot_from_crushed_{kind.split(':')[1]}",
                           {"type": kind, "ingredient": {"item": crushed}, "result": {"id": ingot},
                            "experience": 0.1, "cookingtime": time},
                           f"Create crushes LOTR {metal} ore into {crushed}; it smelts into LOTR's {metal} ingot")
            nugget = next(iter(sorted(tags.get(f"nuggets/{metal}", []))), None)
            if nugget:
                r = self.template("data/create/recipe/splashing/crushed_raw_iron.json")
                r["ingredients"] = [{"item": crushed}]
                r["results"][0]["id"] = nugget
                for extra in r["results"][1:]:
                    if extra.get("id") == "minecraft:iron_nugget":
                        extra["id"] = nugget
                self.write("create:splashing", f"crushed_raw_{metal}", r,
                           f"like washing crushed iron: {crushed} -> LOTR {metal} nuggets")

    def ores(self, tags):
        """LOTR ores Create has no recipe for: gem ores like diamond ore, metal ores like iron ore."""
        blocks = {b["id"]: b for b in (self.load_export("blocks.json") or [])}
        smelt = {}
        rec = self.load_export("recipes.json") or {}
        # LOTR's precious ores (mithril, Morgul iron) smelt only in its forges, so read those too
        forge_smelting = [x for f in ((rec.get("machines") or {}).get("forges") or {}).values()
                          for x in f.get("smelting", [])]
        for s in rec.get("smelting", []) + forge_smelting:
            smelt.setdefault(f"{s['input']['item']}#{s['input'].get('meta', 0)}", s["output"])
        tagged = set().union(*[tags[k] for k in tags if k.startswith("ores/")]) if tags else set()
        covered = {"copper", "tin", "silver"}  # Create's own tag recipes
        for old, b in sorted(blocks.items()):
            if not any(c.endswith("LOTRBlockOre") or "LOTRBlockOre" in c for c in b.get("classes", [])):
                continue
            for d in b.get("drops", []):
                if not d.get("item") or d["meta"] > 15:
                    continue
                ore = self.new_id(old, d["meta"])
                if not ore or ore not in self.items or ore in tagged and any(m in ore for m in covered):
                    continue
                if any(c["ore"] == ore for c in self.crossref if "ore" in c):
                    continue
                dropped = self.new_id(d["item"], d.get("damage", 0))
                name = ore.split(":")[1]
                if dropped and dropped != ore:
                    r = self.template("data/create/recipe/crushing/diamond_ore.json")
                    r["ingredients"] = [{"item": ore}]
                    for res in r["results"][:2]:
                        res["id"] = dropped
                        if d.get("max", 1) > 1:
                            res["count"] = d["max"]
                    self.write("create:crushing", name, r, f"gem ore: crushed like diamond ore into {dropped}")
                    self.crossref[-1]["ore"] = ore
                else:
                    out = smelt.get(f"{old}#{d['meta']}") or smelt.get(f"{old}#0")
                    ingot = self.gen.result(out)["id"] if out and self.gen.result(out) else None
                    if not ingot:
                        continue
                    r = self.template("data/create/recipe/crushing/iron_ore.json")
                    r["ingredients"] = [{"item": ore}]
                    for res in r["results"][:2]:
                        res["id"] = ingot
                    self.write("create:crushing", name, r,
                               f"metal ore Create has no crushed form for: crushed like iron ore, giving {ingot}")
                    self.crossref[-1]["ore"] = ore

    def millstone(self):
        """LOTR's millstone recipes as create:milling, unless Create already mills that input."""
        taken = self.create_inputs("create:milling")
        base = self.template("data/create/recipe/milling/cobblestone.json")
        folder = os.path.join(self.data, NS, "recipe", "millstone")
        if not os.path.isdir(folder):
            return
        for f in sorted(os.listdir(folder)):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                lotr = json.load(fh)
            ing = lotr["ingredients"][0]
            if ing.get("item") in taken or TAG_ITEM.get(ing.get("tag")) in taken:
                self.crossref.append({"skipped": f"{NS}:millstone/{f[:-5]}", "because": "Create already mills it"})
                continue
            res = {"id": lotr["result"]["id"]}
            if lotr["result"].get("count", 1) != 1:
                res["count"] = lotr["result"]["count"]
            if lotr.get("chance", 1) < 1:
                res["chance"] = lotr["chance"]
            r = {"type": "create:milling", "ingredients": [ing],
                 "processing_time": base.get("processing_time", 250), "results": [res]}
            self.write("create:milling", f[:-5], r, "LOTR millstone recipe on Create's millstone")

    def dyes(self):
        """LOTR's single-item crafting into a dye (flowers) is milled like Create's poppy: twice the dye."""
        poppy = self.template("data/create/recipe/milling/poppy.json")
        folder = os.path.join(self.data, NS, "recipe", "crafting")
        taken = self.create_inputs("create:milling")
        for f in sorted(os.listdir(folder)):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                r = json.load(fh)
            if r.get("type") != "minecraft:crafting_shapeless" or len(r.get("ingredients", [])) != 1:
                continue
            out = r["result"]["id"]
            ing = r["ingredients"][0]
            if not out.endswith("_dye") or "item" not in ing or ing["item"] in taken:
                continue
            m = copy.deepcopy(poppy)
            m["ingredients"] = [ing]
            m["results"] = [{"count": 2 * r["result"].get("count", 1), "id": out}]
            self.write("create:milling", f[:-5], m, f"like Create milling a poppy: crafted dye x2 ({out})")

    def alloys(self):
        """Alloy forge alloys (two inputs) as heated create:mixing, like Create's brass."""
        brass = self.template("data/create/recipe/mixing/brass_ingot.json")
        folder = os.path.join(self.data, NS, "recipe", "alloy_forge")
        if not os.path.isdir(folder):
            return
        seen = set()
        for f in sorted(os.listdir(folder)):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                r = json.load(fh)
            if len(r["ingredients"]) != 2:
                continue
            key = json.dumps([r["ingredients"], r["result"]], sort_keys=True)
            if key in seen:
                continue  # the same alloy at several forges
            seen.add(key)
            m = copy.deepcopy(brass)
            m["ingredients"] = r["ingredients"]
            res = {"id": r["result"]["id"]}
            if r["result"].get("count", 1) != 1:
                res["count"] = r["result"]["count"]
            m["results"] = [res]
            self.write("create:mixing", f[:-5], m, "LOTR alloy forge alloy, heated mixing like Create's brass")

    def brewing(self):
        """Barrel drinks as create:mixing with water, like Create's dough (flour + 1000 mB water)."""
        dough = self.template("data/create/recipe/mixing/dough_by_mixing.json")
        water = next(i for i in dough["ingredients"] if i.get("fluid"))
        folder = os.path.join(self.data, NS, "recipe", "brewing")
        if not os.path.isdir(folder):
            return
        for f in sorted(os.listdir(folder)):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                r = json.load(fh)
            m = {"type": "create:mixing", "ingredients": r["ingredients"] + [water], "results": [r["result"]]}
            self.write("create:mixing", "brewing_" + f[:-5], m, "LOTR barrel drink, mixed with water")

    def lookalikes(self):
        """LOTR sands and gravels washed and crushed like vanilla's (Create's splashing/crushing of sand, gravel)."""
        for b in self.gen.blocks:
            name = b["id"]
            if b["id"] in self.gen.hidden:
                continue
            suffix = name.rsplit("_", 1)[-1]
            if suffix not in LOOKALIKE_SUFFIXES or name == suffix or not name.endswith("_" + suffix):
                continue
            for rtype in ("splashing", "crushing"):
                path = f"data/create/recipe/{rtype}/{suffix}.json"
                if path not in self.create:
                    continue
                r = self.template(path)
                r["ingredients"] = [{"item": f"{NS}:{name}"}]
                self.write(f"create:{rtype}", name, r, f"looks like vanilla {suffix}: Create's {rtype} of {suffix}")

    def run(self):
        tags = self.tags()
        self.crushed_metals(tags)
        self.ores(tags)
        self.millstone()
        self.dyes()
        self.alloys()
        self.brewing()
        self.lookalikes()
        write_json(os.path.join(HERE, "create", "crossref.json"),
                   {"create_recipes_read": len(self.create), "written": dict(self.count), "matches": self.crossref})
        return {"create_compat_recipes": sum(self.count.values())}


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--extract", metavar="CREATE_JAR", required=True)
    n = extract(ap.parse_args().extract)
    print(f"{n} Create recipes -> {DIGEST}")
