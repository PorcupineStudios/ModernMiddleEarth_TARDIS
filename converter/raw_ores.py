"""
Raw ores, as in vanilla's Caves & Cliffs: LOTR's metal ores that dropped themselves (copper, tin, silver, mithril,
Morgul iron) now drop a raw ore item that has to be smelted, forged or milled. Silk touch still gives the ore block;
fortune multiplies the raw drop like vanilla iron ore (`minecraft:ore_drops`). Nine raw ores pack into a block of raw
ore and back.

Runs on top of the generated content (no LOTR jar needed), after renames.py and before tags.py (which tags the new
entries), and can be rerun alone (``python raw_ores.py``). Every entry it writes carries "raw_ore" in blocks.json /
items.json and a rerun first removes its own earlier output, so it is idempotent.

Which ores: every block in blocks.json named `<metal>_ore` or `<metal>_ore_<variant>` that is not an item-dropping
ore (kind `ore`, ores.py) and whose loot table drops the block itself. The raw item is `raw_<metal>` and its block
`raw_<metal>_block`, one per metal (Morgul iron's Mordor-rock ore shares Morgul iron's).

What it writes, so the raw ore goes everywhere the ore went:
- loot tables of the ores (silk touch: the ore; else raw x DROPS, fortune, explosion decay) and of the raw blocks;
- every recipe that took the ore (smelting, alloy forges, Create mixing...) takes the raw ore too (an ingredient
  list); smelting recipes get a blasting twin (half the time, as vanilla's ores) in `recipe/own/raw_ore/`;
- 9 raw <-> block crafting (which Create's mechanical press and crafters also use);
- Create: metals Create has crushed forms for (tin, silver, copper) are crushed by Create's own tag recipes once the
  raw ore is in `c:raw_materials/<metal>` and its block in `c:storage_blocks/raw_<metal>` (tags.py writes both);
  create_compat.py already smelts and washes Create's crushed tin and silver into LOTR's ingots and nuggets. Metals
  Create has no crushed form for (mithril, Morgul iron) are crushed like Create's raw iron and raw iron block, giving
  what create_compat.py's crushing of the ore gives (Create's ore crushing: 1.75 per ore, raw: 1 per raw ore);
- the harvest level tags of the ore for the raw block, models, recoloured textures (vanilla's raw iron pictures in
  the colours of the metal's ingot), English names, creative tabs (after the ingot / the ore's storage block).
"""
import copy
import gzip
import json
import os
import re
import sys

from PIL import Image

from woodsets import OUT, Recolour, Vanilla, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
NS = "lotr_port"

# raw ores per ore broken (before fortune); vanilla iron and gold drop 1 (copper drops 2-5)
DROPS = {}  # metal -> count or {"min": a, "max": b}; missing = 1
CREATE_DIGEST = os.path.join(HERE, "create", "recipes.json.gz")
CREATE_CONDITION = [{"type": "neoforge:mod_loaded", "modid": "create"}]
OWN = f"data/{NS}/recipe/own/raw_ore"


def ore_metal(block_id):
    m = re.fullmatch(r"([a-z_]+?)_ore(?:_[a-z]+)?", block_id)
    return m.group(1) if m and not block_id.startswith("raw_") else None


class RawOres:
    def __init__(self, out=OUT, cache=os.path.join(HERE, "cache")):
        self.out = out
        self.cache = cache
        self.content = os.path.join(out, NS, "content")
        self.assets = os.path.join(out, "assets", NS)
        self.data = os.path.join(out, "data")
        self.blocks = self.load(os.path.join(self.content, "blocks.json"))
        self.items = self.load(os.path.join(self.content, "items.json"))
        self.tabs = self.load(os.path.join(self.content, "tabs.json"))
        self.lang_path = os.path.join(self.assets, "lang", "en_us.json")
        self.lang = self.load(self.lang_path)
        self.counts = {"metals": 0, "ores": 0, "recipes_widened": 0, "recipes_written": 0}

    @staticmethod
    def load(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)

    # ------------------------------------------------------------------ which ores

    def drops_itself(self, block_id):
        path = os.path.join(self.data, NS, "loot_table", "blocks", block_id + ".json")
        if not os.path.exists(path):
            return False
        text = json.dumps(self.load(path))
        names = re.findall(r'"name": "([^"]+)"', text)
        return names == [f"{NS}:{block_id}"] or (f'"{NS}:raw_' in text and "silk_touch" in text)

    def ores(self):
        """metal -> its ore ids, in blocks.json order."""
        out = {}
        for b in self.blocks:
            metal = ore_metal(b["id"])
            if metal and b.get("kind") != "ore" and not b.get("raw_ore") and self.drops_itself(b["id"]):
                out.setdefault(metal, []).append(b["id"])
        return out

    # ------------------------------------------------------------------ run

    def clean(self):
        old = {b["id"] for b in self.blocks if b.get("raw_ore")} | {i["id"] for i in self.items if i.get("raw_ore")}
        self.blocks = [b for b in self.blocks if not b.get("raw_ore")]
        self.items = [i for i in self.items if not i.get("raw_ore")]
        for t in self.tabs:
            t["items"] = [i for i in t["items"] if i not in old]
        for k in [k for k in self.lang if k.split(".")[-1] in old]:
            del self.lang[k]
        own = os.path.join(self.out, OWN)
        if os.path.isdir(own):
            for f in os.listdir(own):
                os.remove(os.path.join(own, f))

    def run(self):
        self.clean()
        self.vanilla = Vanilla(self.cache)
        with gzip.open(CREATE_DIGEST, "rt", encoding="utf-8") as f:
            self.create = json.load(f)
        ores = self.ores()
        for metal, ore_ids in ores.items():
            raw, block = f"raw_{metal}", f"raw_{metal}_block"
            first = next(b for b in self.blocks if b["id"] == ore_ids[0])
            self.entries(metal, raw, block, first)
            for ore in ore_ids:
                self.ore_loot(ore, raw, DROPS.get(metal, 1))
                self.counts["ores"] += 1
            self.block_loot(block)
            self.widen_recipes(ore_ids, raw)
            self.storage_recipes(raw, block)
            self.create_crushing(metal, ore_ids, raw, block)
            self.harvest_tags(ore_ids[0], block)
            self.models_and_textures(metal, raw, block, ore_ids[0])
            self.names_and_tabs(metal, raw, block, ore_ids[0])
            self.counts["metals"] += 1
        self.save()
        return self.counts

    # ------------------------------------------------------------------ content

    def entries(self, metal, raw, block, ore):
        self.items.append({"id": raw, "kind": "item", "stack": 64, "raw_ore": metal})
        # vanilla's raw iron block: hardness 5, resistance 6
        self.blocks.append({"id": block, "kind": "cube", "hardness": 5.0, "resistance": 6.0, "light": 0,
                            "sound": "stone", "requiresTool": True, "solid": True, "raw_ore": metal})

    def ore_loot(self, ore, raw, count):
        silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
            {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
        functions = []
        if count != 1:
            functions.append({"function": "minecraft:set_count", "count": count if isinstance(count, (int, float))
                              else {"type": "minecraft:uniform", **count}})
        functions += [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                       "formula": "minecraft:ore_drops"}, {"function": "minecraft:explosion_decay"}]
        write_json(os.path.join(self.data, NS, "loot_table", "blocks", ore + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
                "type": "minecraft:alternatives", "children": [
                    {"type": "minecraft:item", "name": f"{NS}:{ore}", "conditions": [silk]},
                    {"type": "minecraft:item", "name": f"{NS}:{raw}", "functions": functions}]}]}],
            "random_sequence": f"{NS}:blocks/{ore}"})

    def block_loot(self, block):
        write_json(os.path.join(self.data, NS, "loot_table", "blocks", block + ".json"), {
            "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
                {"type": "minecraft:item", "name": f"{NS}:{block}"}],
                "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": f"{NS}:blocks/{block}"})

    # ------------------------------------------------------------------ recipes

    def widen(self, node, ores, raw, compound):
        """Every ingredient naming one of `ores` also takes `raw`. Returns (node, changed)."""
        raw_ing = {"item": f"{NS}:{raw}"}
        if isinstance(node, dict):
            if set(node) == {"item"} and node["item"] in ores:
                alts = [dict(node), raw_ing]
                return ({"type": "neoforge:compound", "ingredients": alts} if compound else alts), True
            if node.get("type") == "neoforge:compound" and any(i.get("item") in ores for i in node["ingredients"]):
                if raw_ing in node["ingredients"]:
                    return node, False
                node["ingredients"].append(raw_ing)
                return node, True
            changed = False
            for k, v in node.items():
                if k in ("result", "results"):
                    continue
                node[k], c = self.widen(v, ores, raw, compound)
                changed |= c
            return node, changed
        if isinstance(node, list):
            if any(isinstance(i, dict) and i.get("item") in ores for i in node) and \
                    all(isinstance(i, dict) and ("item" in i or "tag" in i) for i in node):
                return (node, False) if raw_ing in node else (node + [raw_ing], True)  # a choice of items
            changed = False
            out = []
            for v in node:
                v, c = self.widen(v, ores, raw, compound)
                out.append(v)
                changed |= c
            return out, changed
        return node, False

    def widen_recipes(self, ore_ids, raw):
        ores = {f"{NS}:{o}" for o in ore_ids}
        root = os.path.join(self.data, NS, "recipe")
        for dirpath, _, files in os.walk(root):
            rel = os.path.relpath(dirpath, root).replace(os.sep, "/")
            if rel.startswith("create/crushing") or rel.startswith("own/raw_ore"):
                continue  # ore crushing stays the ore's; the raw ore gets its own crushing (create_crushing)
            for f in files:
                path = os.path.join(dirpath, f)
                r = self.load(path)
                if not any(f'"{o}"' in json.dumps(r) for o in ores):
                    continue
                compound = r.get("type", "").startswith("create:")
                for key in ("ingredient", "ingredients", "key"):
                    if key not in r:
                        continue
                    if key == "ingredients" and isinstance(r[key], list):
                        # a list of ingredients: widen each one, never treat the list as a choice
                        changed = False
                        for n, ing in enumerate(r[key]):
                            r[key][n], c = self.widen(ing, ores, raw, compound)
                            changed |= c
                    else:
                        r[key], changed = self.widen(r[key], ores, raw, compound)
                    if changed:
                        write_json(path, r)
                        self.counts["recipes_widened"] += 1
                if r.get("type") == "minecraft:smelting" and not rel.startswith("create"):
                    self.blasting_twin(f[:-5], r)

    def blasting_twin(self, name, smelting):
        b = copy.deepcopy(smelting)
        b["type"] = "minecraft:blasting"
        b["cookingtime"] = max(1, smelting.get("cookingtime", 200) // 2)
        self.write(f"{OWN}/{name}_from_blasting.json", b)

    def storage_recipes(self, raw, block):
        self.write(f"{OWN}/{block}.json", {
            "type": "minecraft:crafting_shaped", "category": "building", "pattern": ["###", "###", "###"],
            "key": {"#": {"item": f"{NS}:{raw}"}}, "result": {"id": f"{NS}:{block}"}})
        self.write(f"{OWN}/{raw}.json", {
            "type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": [{"item": f"{NS}:{block}"}],
            "result": {"id": f"{NS}:{raw}", "count": 9}})

    def create_crushing(self, metal, ore_ids, raw, block):
        """Create crushes the raw ore: by its own tag recipes where it has a crushed form, else like raw iron."""
        if f"data/create/recipe/crushing/raw_{metal}.json" in self.create:
            return  # Create's c:raw_materials/<metal> and c:storage_blocks/raw_<metal> recipes (tags.py)
        out = os.path.join(self.data, NS, "recipe", "create", "crushing")
        product = None
        for ore in ore_ids:
            p = os.path.join(out, ore + ".json")
            if os.path.exists(p):
                product = self.load(p)["results"][0]["id"]
                break
        if not product:
            print(f"raw_ores: no Create crushing for {raw} (Create has no crushed {metal} and the ore isn't crushed)")
            return
        for src, name, what in (("raw_iron", raw, raw), ("raw_iron_block", block, block)):
            r = copy.deepcopy(self.create[f"data/create/recipe/crushing/{src}.json"])
            r["ingredients"] = [{"item": f"{NS}:{what}"}]
            for res in r["results"]:
                if res["id"] == "create:crushed_raw_iron":
                    res["id"] = product
            write_json(os.path.join(out, name + ".json"), {"neoforge:conditions": CREATE_CONDITION, **r})
            self.counts["recipes_written"] += 1

    def write(self, rel, recipe):
        write_json(os.path.join(self.out, rel), recipe)
        self.counts["recipes_written"] += 1

    # ------------------------------------------------------------------ tags, looks, names

    def harvest_tags(self, ore, block):
        """The raw block needs the ore's tool tier (vanilla: raw iron block = iron ore = stone tool)."""
        folder = os.path.join(self.data, "minecraft", "tags", "block")
        for tag in ("needs_stone_tool", "needs_iron_tool", "needs_diamond_tool", "mineable/pickaxe"):
            path = os.path.join(folder, tag + ".json")
            if not os.path.exists(path):
                continue
            t = self.load(path)
            ids = [v["id"] if isinstance(v, dict) else v for v in t["values"]]
            if f"{NS}:{ore}" in ids and f"{NS}:{block}" not in ids:
                t["values"].append(f"{NS}:{block}")
                write_json(path, t)

    def texture_of_model(self, model):
        path = os.path.join(self.assets, "models", "item", model + ".json")
        if not os.path.exists(path):
            return None
        tex = self.load(path).get("textures", {}).get("layer0")
        if not tex:
            return None
        ns, rel = tex.split(":", 1) if ":" in tex else ("minecraft", tex)
        png = os.path.join(self.out, "assets", ns, "textures", rel + ".png")
        if not os.path.exists(png):
            return None
        img = Image.open(png).convert("RGBA")
        return img.crop((0, 0, img.width, img.width))  # first frame of an animated strip

    def palette(self, metal, ore):
        """The metal's colours: its ingot's picture, else what the ore smelts or forges into."""
        img = self.texture_of_model(f"{metal}_ingot")
        if img is None:
            for dirpath, _, files in os.walk(os.path.join(self.data, NS, "recipe")):
                for f in files:
                    r = self.load(os.path.join(dirpath, f))
                    if f'"{NS}:{ore}"' in json.dumps(r.get("ingredient") or r.get("ingredients")) and \
                            isinstance(r.get("result"), dict):
                        img = self.texture_of_model(r["result"]["id"].split(":")[1])
                        if img is not None:
                            return img
        return img

    def models_and_textures(self, metal, raw, block, ore):
        tex = os.path.join(self.assets, "textures")
        pal = self.palette(metal, ore)
        for template, rel in (("item/raw_iron", f"item/{raw}"), ("block/raw_iron_block", f"block/{block}")):
            img = self.vanilla.png(template)
            if pal is not None:
                img = Recolour(img, pal).image(img, True)
            os.makedirs(os.path.dirname(os.path.join(tex, rel + ".png")), exist_ok=True)
            img.save(os.path.join(tex, rel + ".png"))
        models = os.path.join(self.assets, "models")
        write_json(os.path.join(models, "item", raw + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{raw}"}})
        write_json(os.path.join(models, "block", block + ".json"),
                   {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{block}"}})
        write_json(os.path.join(models, "item", block + ".json"), {"parent": f"{NS}:block/{block}"})
        write_json(os.path.join(self.assets, "blockstates", block + ".json"),
                   {"variants": {"": {"model": f"{NS}:block/{block}"}}})

    def names_and_tabs(self, metal, raw, block, ore):
        name = self.lang.get(f"block.{NS}.{ore}", metal.replace("_", " ").title() + " Ore")
        name = re.sub(r"\s*Ore$", "", name)
        self.lang[f"item.{NS}.{raw}"] = f"Raw {name}"
        self.lang[f"block.{NS}.{block}"] = f"Block of Raw {name}"
        for new, after in ((raw, f"{metal}_ingot"), (block, f"block_of_{metal}"), (block, ore)):
            tab = next((t for t in self.tabs if after in t["items"]), None)
            if tab and not any(new in t["items"] for t in self.tabs):
                tab["items"].insert(tab["items"].index(after) + (0 if new == raw else 1), new)
        for new in (raw, block):  # no ingot or storage block to sit by: the ore's tab
            if not any(new in t["items"] for t in self.tabs):
                tab = next((t for t in self.tabs if ore in t["items"]), self.tabs[0])
                tab["items"].insert(tab["items"].index(ore) + 1 if ore in tab["items"] else len(tab["items"]), new)

    def save(self):
        write_json(os.path.join(self.content, "blocks.json"), self.blocks)
        write_json(os.path.join(self.content, "items.json"), self.items)
        write_json(os.path.join(self.content, "tabs.json"), self.tabs)
        write_json(self.lang_path, self.lang)


def run(out=OUT):
    return RawOres(out).run()


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
