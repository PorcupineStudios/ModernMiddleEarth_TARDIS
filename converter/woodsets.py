"""
Full modern wood sets for every LOTR wood (1.7.10 had only logs, planks, stairs, slabs, fences, gates, doors and
trapdoors): stripped logs and stripped beams (an axe strips them), signs, hanging signs, buttons, pressure plates,
boats and chest boats.

Runs on top of the generated content (no LOTR jar needed), so it can be rerun alone:

    python woodsets.py            # from converter/: rewrites lotr/src/generated/resources

generate.py also calls it last. Every entry it writes carries "woodset" in blocks.json / items.json, and a rerun
first removes its own earlier output, so it is idempotent.

Pictures are vanilla 1.21.1's oak ones recoloured into each LOTR wood: a template pixel's brightness is placed in
oak planks' brightness spread and takes the colour at the same place in the LOTR planks' spread. Grey and dark pixels
(chains, iron, the chest latch) are kept. Vanilla's client jar is downloaded once into converter/cache/ (gitignored).
"""
import io
import json
import os
import sys
import urllib.request
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "lotr", "src", "generated", "resources")
NS = "lotr_port"
MC_1211 = ("https://piston-data.mojang.com/v1/objects/30c73b1c5da787909b2f73340419fdf13b9def88/client.jar",
           "minecraft-1.21.1-client.jar")
RECIPES = f"data/{NS}/recipe/own/woodset"

# What each wood gets (id pattern -> kind); {b} is the wood's base name ("mallorn")
BLOCKS = {
    "stripped_{b}_wood": "pillar", "stripped_{b}_wood_beam": "pillar",
    "{b}_button": "button", "{b}_pressure_plate": "pressure_plate",
    "{b}_sign": "sign", "{b}_wall_sign": "wall_sign",
    "{b}_hanging_sign": "hanging_sign", "{b}_wall_hanging_sign": "wall_hanging_sign",
}
ITEMS = {"{b}_sign": "sign", "{b}_hanging_sign": "hanging_sign", "{b}_boat": "boat", "{b}_chest_boat": "chest_boat"}
NAMES = {
    "stripped_{b}_wood": "Stripped {n} Wood", "stripped_{b}_wood_beam": "Stripped {n} Wood Beam",
    "{b}_button": "{n} Button", "{b}_pressure_plate": "{n} Pressure Plate", "{b}_sign": "{n} Sign",
    "{b}_wall_sign": "{n} Wall Sign", "{b}_hanging_sign": "{n} Hanging Sign",
    "{b}_wall_hanging_sign": "{n} Wall Hanging Sign", "{b}_boat": "{n} Boat", "{b}_chest_boat": "{n} Boat with Chest",
}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


class Vanilla:
    """Vanilla 1.21.1's textures, from the client jar (downloaded once)."""

    def __init__(self, cache):
        path = os.path.join(cache, MC_1211[1])
        if not os.path.exists(path):
            os.makedirs(cache, exist_ok=True)
            urllib.request.urlretrieve(MC_1211[0], path)
        self.jar = zipfile.ZipFile(path)

    def png(self, rel):
        return Image.open(io.BytesIO(self.jar.read(f"assets/minecraft/textures/{rel}.png"))).convert("RGBA")


def _pixels(img):
    return img.get_flattened_data() if hasattr(img, "get_flattened_data") else img.getdata()


def _lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


def _woody(p):
    """A pixel in oak's colour range (warm, not grey): recoloured; anything else (iron, chain, latch) is kept."""
    r, g, b, a = p
    if a == 0:
        return False
    mx, mn = max(r, g, b), min(r, g, b)
    return mx > 40 and (mx - mn) / mx > 0.18 and r >= b


class Recolour:
    """Maps oak's brightness spread onto a LOTR planks texture's colours."""

    def __init__(self, oak, target):
        self.oak = sorted(_lum(p) for p in _pixels(oak) if p[3] > 0)
        self.target = sorted((p for p in _pixels(target.convert("RGBA")) if p[3] > 0), key=_lum)

    def colour(self, p):
        lum = _lum(p)
        lo, hi = 0, len(self.oak)
        while lo < hi:  # share of oak's pixels darker than this one
            mid = (lo + hi) // 2
            if self.oak[mid] < lum:
                lo = mid + 1
            else:
                hi = mid
        q = lo / max(1, len(self.oak) - 1)
        c = self.target[min(len(self.target) - 1, round(q * (len(self.target) - 1)))]
        return c[0], c[1], c[2], p[3]

    def image(self, template, everything=False):
        out = template.copy()
        px = out.load()
        for x in range(out.width):
            for y in range(out.height):
                if everything and px[x, y][3] > 0 or _woody(px[x, y]):
                    px[x, y] = self.colour(px[x, y])
        return out


class WoodSets:
    def __init__(self, out=OUT, cache=os.path.join(HERE, "cache")):
        self.out = out
        self.assets = os.path.join(out, "assets", NS)
        self.content = os.path.join(out, NS, "content")
        self.vanilla = Vanilla(cache)
        self.blocks = json.load(open(os.path.join(self.content, "blocks.json"), encoding="utf-8"))
        self.items = json.load(open(os.path.join(self.content, "items.json"), encoding="utf-8"))
        self.lang_path = os.path.join(self.assets, "lang", "en_us.json")
        self.lang = json.load(open(self.lang_path, encoding="utf-8"))
        self.tabs = json.load(open(os.path.join(self.content, "tabs.json"), encoding="utf-8"))

    # ------------------------------------------------------------------ discovery

    def woods(self):
        """(base, log, beam, planks) for every LOTR wood: planks are <base>_wood_planks (or rotten_planks)."""
        ids = {b["id"] for b in self.blocks}
        out = []
        for b in self.blocks:
            p = b["id"]
            if b.get("woodset") or not p.endswith("_planks") or b.get("kind", "cube") != "cube":
                continue
            base = p[: -len("_wood_planks")] if p.endswith("_wood_planks") else p[: -len("_planks")]
            log, beam = f"{base}_wood", f"{base}_wood_beam"
            if log in ids and beam in ids:
                out.append((base, log, beam, p))
        return out

    def model_texture(self, block, key):
        m = json.load(open(os.path.join(self.assets, "models", "block", block + ".json"), encoding="utf-8"))
        return m["textures"][key]

    def texture_png(self, ref):
        ns, path = ref.split(":")
        return Image.open(os.path.join(self.out, "assets", ns, "textures", path + ".png")).convert("RGBA")

    def name(self, planks):
        n = self.lang.get(f"block.{NS}.{planks}", planks.replace("_", " ").title())
        for suffix in (" Wood Planks", " Planks"):
            if n.endswith(suffix):
                return n[: -len(suffix)]
        return n

    # ------------------------------------------------------------------ run

    def clean(self):
        """Removes what an earlier run wrote."""
        old_ids = {b["id"] for b in self.blocks if b.get("woodset")} | {i["id"] for i in self.items if i.get("woodset")}
        self.blocks = [b for b in self.blocks if not b.get("woodset")]
        self.items = [i for i in self.items if not i.get("woodset")]
        for t in self.tabs:
            t["items"] = [i for i in t["items"] if i not in old_ids]
        for k in [k for k in self.lang if k.split(".")[-1] in old_ids]:
            del self.lang[k]
        return old_ids

    def run(self):
        self.clean()
        oak = self.vanilla.png("block/oak_planks")
        counts = {"woods": 0, "blocks": 0, "items": 0, "recipes": 0}
        for base, log, beam, planks in self.woods():
            log_entry = next(b for b in self.blocks if b["id"] == log)
            rc = Recolour(oak, self.texture_png(self.model_texture(planks, "all")))
            self.textures(base, rc)
            self.block_entries(base, log_entry, planks, counts)
            self.item_entries(base, counts)
            self.models(base, log, beam, planks)
            self.recipes(base, planks, counts)
            self.lang_and_tabs(base, planks)
            counts["woods"] += 1
        self.save()
        return counts

    # ------------------------------------------------------------------ pictures

    def textures(self, base, rc):
        v = self.vanilla
        tex = os.path.join(self.assets, "textures")
        jobs = {
            f"block/woodset/stripped_{base}_log": (v.png("block/stripped_oak_log"), True),
            f"block/woodset/stripped_{base}_log_top": (v.png("block/stripped_oak_log_top"), True),
            f"entity/signs/{base}": (v.png("entity/signs/oak"), False),
            f"entity/signs/hanging/{base}": (v.png("entity/signs/hanging/oak"), False),
            f"gui/hanging_signs/{base}": (v.png("gui/hanging_signs/oak"), False),
            f"entity/boat/{base}": (v.png("entity/boat/oak"), True),
            f"entity/chest_boat/{base}": (v.png("entity/chest_boat/oak"), False),
            f"item/woodset/{base}_sign": (v.png("item/oak_sign"), False),
            f"item/woodset/{base}_hanging_sign": (v.png("item/oak_hanging_sign"), False),
            f"item/woodset/{base}_boat": (v.png("item/oak_boat"), False),
            f"item/woodset/{base}_chest_boat": (v.png("item/oak_chest_boat"), False),
        }
        for rel, (template, everything) in jobs.items():
            dst = os.path.join(tex, rel + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            rc.image(template, everything).save(dst)

    # ------------------------------------------------------------------ content

    def block_entries(self, base, log_entry, planks, counts):
        for pattern, kind in BLOCKS.items():
            e = {"id": pattern.format(b=base), "kind": kind, "woodset": base, "sound": "wood",
                 "requiresTool": False}
            if kind == "pillar":
                e.update(hardness=log_entry["hardness"], resistance=log_entry["resistance"], solid=True,
                         stripped=True)
            elif kind in ("button", "pressure_plate"):
                e.update(hardness=0.5, resistance=0.5, solid=False, set="wood")
            else:  # signs: their item is the sign item, which places both
                e.update(hardness=1.0, resistance=1.0, solid=False, item="none")
            self.blocks.append(e)
            counts["blocks"] += 1

    def item_entries(self, base, counts):
        for pattern, kind in ITEMS.items():
            e = {"id": pattern.format(b=base), "kind": kind, "woodset": base,
                 "stack": 1 if "boat" in kind else 16}
            self.items.append(e)
            counts["items"] += 1

    def models(self, base, log, beam, planks):
        a = self.assets
        planks_tex = self.model_texture(planks, "all")
        side, top = f"{NS}:block/woodset/stripped_{base}_log", f"{NS}:block/woodset/stripped_{base}_log_top"
        for block in (f"stripped_{base}_wood", f"stripped_{base}_wood_beam"):
            write_json(os.path.join(a, "models", "block", block + ".json"),
                       {"parent": "minecraft:block/cube_column", "textures": {"end": top, "side": side}})
            write_json(os.path.join(a, "models", "block", block + "_horizontal.json"),
                       {"parent": "minecraft:block/cube_column_horizontal", "textures": {"end": top, "side": side}})
            m = f"{NS}:block/{block}"
            write_json(os.path.join(a, "blockstates", block + ".json"), {"variants": {
                "axis=y": {"model": m}, "axis=z": {"model": m + "_horizontal", "x": 90},
                "axis=x": {"model": m + "_horizontal", "x": 90, "y": 90}}})
            write_json(os.path.join(a, "models", "item", block + ".json"), {"parent": m})
        # button
        b = f"{base}_button"
        for suffix, parent in (("", "button"), ("_pressed", "button_pressed"), ("_inventory", "button_inventory")):
            write_json(os.path.join(a, "models", "block", b + suffix + ".json"),
                       {"parent": f"minecraft:block/{parent}", "textures": {"texture": planks_tex}})
        variants = {}
        for face, x in (("floor", 0), ("wall", 90), ("ceiling", 180)):
            for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                for powered in ("false", "true"):
                    v = {"model": f"{NS}:block/{b}" + ("_pressed" if powered == "true" else "")}
                    yy = (y + 180) % 360 if face == "ceiling" else y
                    if x:
                        v["x"] = x
                    if yy:
                        v["y"] = yy
                    if face == "wall":
                        v["uvlock"] = True
                    variants[f"face={face},facing={facing},powered={powered}"] = v
        write_json(os.path.join(a, "blockstates", b + ".json"), {"variants": variants})
        write_json(os.path.join(a, "models", "item", b + ".json"), {"parent": f"{NS}:block/{b}_inventory"})
        # pressure plate
        p = f"{base}_pressure_plate"
        write_json(os.path.join(a, "models", "block", p + ".json"),
                   {"parent": "minecraft:block/pressure_plate_up", "textures": {"texture": planks_tex}})
        write_json(os.path.join(a, "models", "block", p + "_down.json"),
                   {"parent": "minecraft:block/pressure_plate_down", "textures": {"texture": planks_tex}})
        write_json(os.path.join(a, "blockstates", p + ".json"), {"variants": {
            "powered=false": {"model": f"{NS}:block/{p}"}, "powered=true": {"model": f"{NS}:block/{p}_down"}}})
        write_json(os.path.join(a, "models", "item", p + ".json"), {"parent": f"{NS}:block/{p}"})
        # signs: drawn by the sign renderers; the block model is only the particle
        for block, particle in ((f"{base}_sign", planks_tex), (f"{base}_wall_sign", planks_tex),
                                (f"{base}_hanging_sign", side), (f"{base}_wall_hanging_sign", side)):
            write_json(os.path.join(a, "models", "block", block + ".json"), {"textures": {"particle": particle}})
            write_json(os.path.join(a, "blockstates", block + ".json"),
                       {"variants": {"": {"model": f"{NS}:block/{block}"}}})
        for item in (f"{base}_sign", f"{base}_hanging_sign", f"{base}_boat", f"{base}_chest_boat"):
            write_json(os.path.join(a, "models", "item", item + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/woodset/{item}"}})
        # loot: each block drops itself; signs drop the sign item
        drops = {f"stripped_{base}_wood": None, f"stripped_{base}_wood_beam": None, f"{base}_button": None,
                 f"{base}_pressure_plate": None, f"{base}_sign": None, f"{base}_wall_sign": f"{base}_sign",
                 f"{base}_hanging_sign": None, f"{base}_wall_hanging_sign": f"{base}_hanging_sign"}
        for block, drop in drops.items():
            write_json(os.path.join(self.out, "data", NS, "loot_table", "blocks", block + ".json"), {
                "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
                    {"type": "minecraft:item", "name": f"{NS}:{drop or block}"}],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

    def recipes(self, base, planks, counts):
        d = os.path.join(self.out, RECIPES)
        pl = {"item": f"{NS}:{planks}"}

        def shaped(name, pattern, key, result, count, group):
            write_json(os.path.join(d, name + ".json"), {
                "type": "minecraft:crafting_shaped", "category": "building" if group != "boat" else "misc",
                "group": group, "pattern": pattern, "key": key,
                "result": {"id": f"{NS}:{result}", "count": count}})
            counts["recipes"] += 1

        def shapeless(name, ingredients, result, count, group):
            write_json(os.path.join(d, name + ".json"), {
                "type": "minecraft:crafting_shapeless", "category": "building", "group": group,
                "ingredients": ingredients, "result": {"id": f"{NS}:{result}", "count": count}})
            counts["recipes"] += 1

        shaped(f"{base}_sign", ["###", "###", " X "], {"#": pl, "X": {"item": "minecraft:stick"}}, f"{base}_sign",
               3, "wooden_sign")
        shaped(f"{base}_hanging_sign", ["X X", "###", "###"],
               {"#": {"item": f"{NS}:stripped_{base}_wood"}, "X": {"item": "minecraft:chain"}},
               f"{base}_hanging_sign", 6, "hanging_sign")
        shapeless(f"{base}_button", [pl], f"{base}_button", 1, "wooden_button")
        shaped(f"{base}_pressure_plate", ["##"], {"#": pl}, f"{base}_pressure_plate", 1, "wooden_pressure_plate")
        shaped(f"{base}_boat", ["# #", "###"], {"#": pl}, f"{base}_boat", 1, "boat")
        shapeless(f"{base}_chest_boat", [{"tag": "c:chests/wooden"}, {"item": f"{NS}:{base}_boat"}],
                  f"{base}_chest_boat", 1, "chest_boat")
        for stripped in (f"stripped_{base}_wood", f"stripped_{base}_wood_beam"):
            shapeless(f"{planks}_from_{stripped}", [{"item": f"{NS}:{stripped}"}], planks, 4, "planks")

    def lang_and_tabs(self, base, planks):
        n = self.name(planks)
        for pattern, label in NAMES.items():
            i = pattern.format(b=base)
            key = "item" if pattern in ("{b}_boat", "{b}_chest_boat") else "block"
            self.lang[f"{key}.{NS}.{i}"] = label.format(n=n)
            if pattern in ("{b}_sign", "{b}_hanging_sign"):
                self.lang[f"item.{NS}.{i}"] = label.format(n=n)  # SignItem names by the standing block anyway
        new = [f"stripped_{base}_wood", f"stripped_{base}_wood_beam", f"{base}_button", f"{base}_pressure_plate",
               f"{base}_sign", f"{base}_hanging_sign", f"{base}_boat", f"{base}_chest_boat"]
        for t in self.tabs:
            if planks in t["items"]:
                at = t["items"].index(planks) + 1
                t["items"][at:at] = new
                break
        self.lang.setdefault(f"entity.{NS}.boat", "Boat")
        self.lang.setdefault(f"entity.{NS}.chest_boat", "Boat with Chest")

    def save(self):
        write_json(os.path.join(self.content, "blocks.json"), self.blocks)
        write_json(os.path.join(self.content, "items.json"), self.items)
        write_json(os.path.join(self.content, "tabs.json"), self.tabs)
        write_json(self.lang_path, self.lang)


if __name__ == "__main__":
    print(json.dumps(WoodSets(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
