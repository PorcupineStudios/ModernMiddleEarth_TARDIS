#!/usr/bin/env python3
"""
Turns the LOTR export (converter/export/*.json, written by the 1.7.10 exporter in CI) plus the LOTR jar's textures and
lang files into the 1.21.1 port's generated resources:

  lotr/src/generated/resources/lotr_port/content/   block, item and creative tab lists the mod registers from
  lotr/src/generated/resources/assets/lotr_port/     blockstates, models, textures, lang files
  lotr/src/generated/resources/data/                 recipes, loot tables, tags, Middle-earth world gen (world.py)
  lotr/src/generated/resources/lotr_port/map/        the Middle-earth biome map (world.py)
  converter/idtable.json                              old id + metadata -> new id (for structures and saves later)
  converter/report.json                               everything that was skipped or guessed

Run from the repo root: python3 converter/generate.py
"""
import collections
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from assets import NS, Jar, Textures, id_from_name, snake, write_json  # noqa: E402
import vanilla  # noqa: E402
from world import World  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXPORT = os.path.join(ROOT, "converter", "export")
OUT = os.path.join(ROOT, "lotr", "src", "generated", "resources")
ASSETS = os.path.join(OUT, "assets", NS)
DATA = os.path.join(OUT, "data")

SKIP_ITEMS = {"lotr:item.spawnEgg", "lotr:item.structureSpawner"}  # NPC and structure phases
TAB_MAP = {"buildingBlocks": "blocks", "redstone": "util"}  # LOTR things LOTR put in vanilla tabs
SKIP_TABS = {"spawning"}
REQUIRES_TOOL = {"rock", "iron", "anvil"}
CUTOUT = "minecraft:cutout"


def load(name):
    with open(os.path.join(EXPORT, name), encoding="utf-8") as f:
        return json.load(f)


class Gen:
    def __init__(self):
        self.report = collections.defaultdict(list)
        self.jar = Jar()
        self.tex = Textures(self.jar, ASSETS, self.report)
        self.lang_en = self.jar.lang("en_US")
        self.used = set()
        self.hidden = set()  # blocks with no item
        self.tables = {}  # old table id ("gondorianCraftingTable") -> new block id
        self.idtable = {}  # "lotr:tile.rock#1" -> "lotr_port:gondor_rock"
        self.old_to_new = collections.defaultdict(list)  # "lotr:tile.rock" -> [new ids]
        self.blocks = []  # content entries
        self.items = []
        self.lang = collections.defaultdict(dict)  # code -> key -> text
        self.unloc = {}  # new id -> old unlocalized key (for other languages)
        self.tags = collections.defaultdict(set)  # "minecraft:block/mineable/pickaxe" -> ids
        self.recipe_count = 0

    # ------------------------------------------------------------------ names

    def english(self, unlocalized, fallback):
        if unlocalized and unlocalized + ".name" in self.lang_en:
            return self.lang_en[unlocalized + ".name"]
        return fallback

    def claim(self, name, old_base, suffix=None):
        base = id_from_name(name) or snake(old_base)
        new = base
        if new in self.used:
            new = f"{base}_{suffix}" if suffix else f"{base}_{snake(old_base)}"
        n = 2
        while new in self.used:
            new = f"{base}_{n}"
            n += 1
        self.used.add(new)
        return new

    def map_id(self, old, meta, new):
        self.idtable[f"{old}#{meta}"] = f"{NS}:{new}"
        self.old_to_new[old].append(f"{NS}:{new}")

    # ------------------------------------------------------------------ items

    def do_items(self):
        for it in load("items.json"):
            if it["id"] in SKIP_ITEMS:
                self.report["skipped_items"].append(it["id"])
                continue
            variants = it["variants"]
            names = {self.english(v.get("unlocalized"), v.get("name")) for v in variants}
            if len(variants) > 1 and len(names) == 1:
                # Same name for every meta (drink strengths, vessel states): one item; the state becomes data later.
                for v in variants[1:]:
                    self.report["merged_item_metas"].append(f"{it['id']}#{v['meta']}")
                variants = variants[:1]
            for v in variants:
                self.item(it, v)

    def item(self, it, v):
        old_base = it["id"].split(".", 1)[1]
        name = self.english(v.get("unlocalized"), v.get("name")) or old_base
        new = self.claim(name, old_base if v["meta"] == 0 else f"{old_base}_{v['meta']}")
        self.map_id(it["id"], v["meta"], new)
        classes = {c.split(".")[-1] for c in it["classes"]}
        e = {"id": new}
        kind = "item"
        for cls, k in (("ItemSword", "sword"), ("ItemPickaxe", "pickaxe"), ("ItemAxe", "axe"),
                       ("ItemSpade", "shovel"), ("ItemHoe", "hoe"), ("ItemArmor", "armor")):
            if cls in classes and (k != "armor" or "armor" in it):
                kind = k
                break
        e["kind"] = kind
        if it.get("maxDamage", 0) > 0 and not it.get("hasSubtypes"):
            e["durability"] = it["maxDamage"]
        else:
            e["stack"] = v.get("maxStack") or 64
        if it.get("enchantability"):
            e["enchant"] = it["enchantability"]
        dmg = v.get("attributes", {}).get("generic.attackDamage")
        if dmg is not None:
            e["damage"] = round(dmg, 2)
        levels = list(v.get("toolClasses", {}).values())
        level = max(levels) if levels else 2
        e["level"] = level
        e["speed"] = {0: 2.0, 1: 4.0, 2: 6.0, 3: 8.0}.get(level, 9.0)
        if "armor" in it:
            a = it["armor"]
            mat = snake(a["material"].replace("LOTR_", "")) or "armor"
            e["armor"] = {"slot": ["head", "chest", "legs", "feet"][a["slot"]], "defense": a["defense"],
                          "material": mat, "texture": self.armor_texture(mat)}
        food = v.get("food")
        if food and food.get("heal") is not None:
            e["food"] = {"nutrition": food["heal"], "saturation": food.get("saturation") or 0.3}
        self.items.append(e)
        self.unloc[new] = (v.get("unlocalized") or "") + ".name"
        self.lang["en_us"][f"item.{NS}.{new}"] = name

        icons = []
        for i in v.get("icons", []):
            if not i:
                continue
            got = self.tex.item(i)
            if not got and i.endswith("_mug"):
                # Drinks are drawn as the empty vessel with the drink's liquid on top.
                liquid = i.split("/")[-1][:-len("_mug")] + "_liquid"
                icons += [x for x in (self.tex.item("lotr:mug"), self.tex.item("lotr:" + liquid)) if x]
                continue
            if got:
                icons.append(got)
        if not icons:
            self.report["items_without_texture"].append(new)
            icons = ["minecraft:item/barrier"]
        parent = "minecraft:item/handheld" if it.get("full3D") or kind in ("sword", "pickaxe", "axe", "shovel",
                                                                             "hoe") else "minecraft:item/generated"
        write_json(os.path.join(ASSETS, "models", "item", new + ".json"),
                   {"parent": parent, "textures": {f"layer{i}": t for i, t in enumerate(icons)}})

    def armor_texture(self, mat):
        """Copies armor/<name>_1.png and _2.png to the 1.21.1 armor layer paths."""
        for cand in (mat, mat.replace("_", ""), mat.split("_")[0]):
            for src_name in self.armor_sources(cand):
                if src_name:
                    for layer in (1, 2):
                        src = src_name.replace("_1.png", f"_{layer}.png")
                        if self.jar.has(src):
                            dst = os.path.join(ASSETS, "textures", "models", "armor", f"{mat}_layer_{layer}.png")
                            os.makedirs(os.path.dirname(dst), exist_ok=True)
                            with open(dst, "wb") as f:
                                f.write(self.jar.read(src))
                    return mat
        self.report["armor_without_texture"].append(mat)
        return mat

    def armor_sources(self, cand):
        want = cand.replace("_", "").lower()
        for n in self.jar.names:
            if n.startswith("assets/lotr/armor/") and n.endswith("_1.png"):
                stem = n[len("assets/lotr/armor/"):-len("_1.png")]
                if stem.replace("_", "").lower() == want:
                    yield n

    # ------------------------------------------------------------------ blocks

    def kind(self, b):
        c = " ".join(x.split(".")[-1] for x in b["classes"])
        rt = b["renderType"]
        for cls, k in (("BlockStairs", "stairs"), ("BlockSlab", "slab"), ("BlockWall", "wall"),
                       ("LOTRBlockFenceGate", "fence_gate"), ("BlockFence", "fence"), ("BlockPane", "pane"),
                       ("BlockDoor", "door"), ("BlockTrapDoor", "trapdoor"), ("BlockLadder", "ladder"),
                       ("BlockLeavesBase", "leaves"), ("BlockTorch", "torch"), ("BlockButton", "button"),
                       ("BlockBasePressurePlate", "pressure_plate"), ("BlockRotatedPillar", "pillar"),
                       ("BlockBush", "plant"), ("BlockVine", "plant")):
            if cls in c.split():
                return k
        if "LOTRBlockCraftingTable" in c.split():
            return "faction_table"
        if rt == 1:
            return "plant"
        if rt == 0 and b["opaque"] and b["normalCube"]:
            return "cube"
        return "shaped"

    def do_blocks(self):
        self.in_tabs = {f"{s['item']}#{s.get('meta', 0)}" for t in load("tabs.json") for s in t["items"] if s}
        for b in load("blocks.json"):
            old_base = b["id"].split(".", 1)[1]
            kind = self.kind(b)
            if kind == "slab" and "Double" in old_base:
                continue  # double slabs become the slab's type=double state; mapped after singles
            variants = b["variants"]
            if kind == "slab":
                variants = [v for v in variants if v["meta"] < 8]
            for v in variants:
                self.block(b, v, kind, old_base)
        # Double slab ids point at their single slab (state type=double).
        for b in load("blocks.json"):
            old_base = b["id"].split(".", 1)[1]
            if self.kind(b) == "slab" and "Double" in old_base:
                single = b["id"].replace("Double", "Single")
                for v in b["variants"]:
                    new = self.idtable.get(f"{single}#{v['meta'] & 7}")
                    if new:
                        self.idtable[f"{b['id']}#{v['meta']}"] = new + "[type=double]"

    def block(self, b, v, kind, old_base):
        unloc = v.get("itemUnlocalized") or b.get("unlocalized")
        name = self.english(unloc, v.get("name"))
        if not name or name.endswith(".name"):
            name = self.english(b.get("unlocalized"), None)
        if not name:
            # Placed forms of items (filled mugs, pies, beds...) have no name of their own.
            name = snake(old_base).replace("_", " ").title()
            self.report["blocks_named_from_id"].append(b["id"])
        new = self.claim(name, old_base if v["meta"] == 0 else f"{old_base}_{v['meta']}", suffix="block")
        self.map_id(b["id"], v["meta"], new)
        icons = [self.tex.block(i) for i in v["icons"]]
        fallback = next((i for i in icons if i), None)
        if fallback is None:
            fallback = self.tex.item(v.get("itemIcon")) or "minecraft:block/stone"
            self.report["blocks_without_texture"].append(new)
        icons = [i or fallback for i in icons]
        hardness = b.get("hardness")
        e = {"id": new, "kind": kind,
             "hardness": -1 if hardness is not None and hardness < 0 else round(hardness or 0, 3),
             "resistance": round((b.get("resistance") or 0) / 3.0, 3),  # 1.7.10 stores resistance x3
             "light": b.get("light", 0), "sound": b.get("sound") or "stone",
             "requiresTool": b.get("material") in REQUIRES_TOOL and (hardness or 0) > 0,
             "solid": bool(b["opaque"]) and kind in ("cube", "pillar")}
        if b.get("slipperiness") and abs(b["slipperiness"] - 0.6) > 1e-6:
            e["friction"] = b["slipperiness"]
        if kind == "shaped":
            bb = b.get("bounds") or [0, 0, 0, 1, 1, 1]
            e["shape"] = [round(x * 16, 3) for x in bb]
            if b["material"] in ("plants", "vine", "circuits", "carpet", "portal", "web"):
                e["collision"] = False
        if kind == "door":
            e["item"] = "double_high"
        if kind == "faction_table":
            self.tables[old_base] = new
        if f"{b['id']}#{v['meta']}" not in self.in_tabs:
            e["item"] = "none"  # never obtainable as a block in 1.7.10 either
            self.hidden.add(new)
        self.blocks.append(e)
        self.unloc[new] = (unloc or "") + ".name"
        self.lang["en_us"][f"block.{NS}.{new}"] = name

        tool = v.get("harvestTool")
        if tool in ("pickaxe", "axe", "shovel", "hoe"):
            self.tags[f"minecraft:block/mineable/{tool}"].add(new)
        lvl = v.get("harvestLevel", -1)
        if lvl == 1:
            self.tags["minecraft:block/needs_stone_tool"].add(new)
        elif lvl == 2:
            self.tags["minecraft:block/needs_iron_tool"].add(new)
        elif lvl >= 3:
            self.tags["minecraft:block/needs_diamond_tool"].add(new)
        wood = b.get("material") == "wood"
        for k, tag in (("wall", "walls"), ("fence_gate", "fence_gates"), ("slab", "slabs"), ("stairs", "stairs"),
                       ("leaves", "leaves"), ("door", "doors"), ("trapdoor", "trapdoors"),
                       ("button", "buttons"), ("pressure_plate", "pressure_plates"), ("ladder", "climbable")):
            if kind == k:
                self.tags[f"minecraft:block/{tag}"].add(new)
                if k in ("slab", "stairs", "leaves", "door", "trapdoor", "button") or k == "fence_gate":
                    self.tags[f"minecraft:item/{tag}"].add(new)
        if kind == "fence":
            self.tags["minecraft:block/wooden_fences" if wood else "minecraft:block/fences"].add(new)
            self.tags["minecraft:block/fences"].add(new)
        if kind == "pillar" and wood:
            self.tags["minecraft:block/logs"].add(new)
            self.tags["minecraft:item/logs"].add(new)

        self.models(new, kind, icons, v, b)
        if new not in self.hidden:  # placed-only blocks (crops, beds, portals) drop nothing until their phase
            self.loot(new, kind)

    # ------------------------------------------------------------------ models and blockstates

    def bm(self, name, parent, textures, extra=None):
        data = {"parent": parent, "textures": textures}
        if extra:
            data.update(extra)
        write_json(os.path.join(ASSETS, "models", "block", name + ".json"), data)
        return f"{NS}:block/{name}"

    def bs(self, new, data):
        write_json(os.path.join(ASSETS, "blockstates", new + ".json"), data)

    def item_model(self, new, parent=None, layer=None):
        if layer:
            data = {"parent": "minecraft:item/generated", "textures": {"layer0": layer}}
        else:
            data = {"parent": parent}
        write_json(os.path.join(ASSETS, "models", "item", new + ".json"), data)

    def models(self, new, kind, icons, v, b):
        down, up, north, south, west, east = icons
        side = north
        rt = {"render_type": "minecraft:translucent"} if b.get("renderPass") == 1 else None
        item_icon = self.tex.item(v.get("itemIcon")) or side
        if kind in ("cube", "faction_table"):
            m = self.cube(new, icons, rt)
            self.bs(new, {"variants": {"": {"model": m}}})
            self.item_model(new, m)
        elif kind == "pillar":
            m = self.bm(new, "minecraft:block/cube_column", {"end": up, "side": side})
            h = self.bm(new + "_horizontal", "minecraft:block/cube_column_horizontal", {"end": up, "side": side})
            self.bs(new, {"variants": {"axis=y": {"model": m}, "axis=z": {"model": h, "x": 90},
                                       "axis=x": {"model": h, "x": 90, "y": 90}}})
            self.item_model(new, m)
        elif kind == "slab":
            t = {"bottom": down, "top": up, "side": side}
            m = self.bm(new, "minecraft:block/slab", t)
            mt = self.bm(new + "_top", "minecraft:block/slab_top", t)
            md = self.bm(new + "_double", "minecraft:block/cube_bottom_top", t)
            self.bs(new, {"variants": {"type=bottom": {"model": m}, "type=top": {"model": mt},
                                       "type=double": {"model": md}}})
            self.item_model(new, m)
        elif kind == "stairs":
            self.stairs(new, {"bottom": down, "top": up, "side": side})
        elif kind == "wall":
            t = {"wall": side}
            post = self.bm(new + "_post", "minecraft:block/template_wall_post", t)
            low = self.bm(new + "_side", "minecraft:block/template_wall_side", t)
            tall = self.bm(new + "_side_tall", "minecraft:block/template_wall_side_tall", t)
            inv = self.bm(new + "_inventory", "minecraft:block/wall_inventory", t)
            parts = [{"when": {"up": "true"}, "apply": {"model": post}}]
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                parts.append({"when": {d: "low"}, "apply": {"model": low, "y": y, "uvlock": True}})
                parts.append({"when": {d: "tall"}, "apply": {"model": tall, "y": y, "uvlock": True}})
            self.bs(new, {"multipart": parts})
            self.item_model(new, inv)
        elif kind == "fence":
            t = {"texture": side}
            post = self.bm(new + "_post", "minecraft:block/fence_post", t)
            sm = self.bm(new + "_side", "minecraft:block/fence_side", t)
            inv = self.bm(new + "_inventory", "minecraft:block/fence_inventory", t)
            parts = [{"apply": {"model": post}}]
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                parts.append({"when": {d: "true"}, "apply": {"model": sm, "y": y, "uvlock": True}})
            self.bs(new, {"multipart": parts})
            self.item_model(new, inv)
        elif kind == "fence_gate":
            t = {"texture": side}
            ms = {k: self.bm(new + suf, "minecraft:block/template_fence_gate" + suf, t)
                  for k, suf in (("c", ""), ("o", "_open"), ("wc", "_wall"), ("wo", "_wall_open"))}
            var = {}
            for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
                for wall in ("false", "true"):
                    for op in ("false", "true"):
                        key = ("w" if wall == "true" else "") + ("o" if op == "true" else "c")
                        var[f"facing={f},in_wall={wall},open={op}"] = {"model": ms[key], "y": y, "uvlock": True}
            self.bs(new, {"variants": var})
            self.item_model(new, ms["c"])
        elif kind == "pane":
            t = {"pane": side, "edge": side}
            m = {s: self.bm(f"{new}_{s}", f"minecraft:block/template_glass_pane_{s}", t, {"render_type": CUTOUT})
                 for s in ("post", "side", "side_alt", "noside", "noside_alt")}
            self.bs(new, {"multipart": [
                {"apply": {"model": m["post"]}},
                {"when": {"north": "true"}, "apply": {"model": m["side"]}},
                {"when": {"east": "true"}, "apply": {"model": m["side"], "y": 90}},
                {"when": {"south": "true"}, "apply": {"model": m["side_alt"]}},
                {"when": {"west": "true"}, "apply": {"model": m["side_alt"], "y": 90}},
                {"when": {"north": "false"}, "apply": {"model": m["noside"]}},
                {"when": {"east": "false"}, "apply": {"model": m["noside_alt"]}},
                {"when": {"south": "false"}, "apply": {"model": m["noside_alt"], "y": 90}},
                {"when": {"west": "false"}, "apply": {"model": m["noside"], "y": 270}}]})
            self.item_model(new, layer=item_icon if item_icon != side else side)
        elif kind == "door":
            self.door(new, side, b, v)
        elif kind == "trapdoor":
            t = {"texture": up}
            ms = {k: self.bm(f"{new}_{k}", f"minecraft:block/template_orientable_trapdoor_{k}", t,
                             {"render_type": CUTOUT}) for k in ("bottom", "top", "open")}
            var = {}
            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
                var[f"facing={f},half=bottom,open=false"] = {"model": ms["bottom"], "y": y}
                var[f"facing={f},half=top,open=false"] = {"model": ms["top"], "y": y}
                var[f"facing={f},half=bottom,open=true"] = {"model": ms["open"], "y": y}
                var[f"facing={f},half=top,open=true"] = {"model": ms["open"], "x": 180, "y": (y + 180) % 360}
            self.bs(new, {"variants": var})
            self.item_model(new, ms["bottom"])
        elif kind == "ladder":
            m = self.bm(new, "minecraft:block/ladder", {"particle": side, "texture": side}, {"render_type": CUTOUT})
            self.bs(new, {"variants": {f"facing={f}": {"model": m, "y": y} for f, y in
                                       (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
            self.item_model(new, layer=item_icon)
        elif kind == "leaves":
            m = self.bm(new, "minecraft:block/leaves", {"all": side}, {"render_type": CUTOUT})
            self.bs(new, {"variants": {"": {"model": m}}})
            self.item_model(new, m)
        elif kind == "plant":
            m = self.bm(new, "minecraft:block/cross", {"cross": side}, {"render_type": CUTOUT})
            self.bs(new, {"variants": {"": {"model": m}}})
            self.item_model(new, layer=item_icon)
        elif kind == "torch":
            m = self.bm(new, "minecraft:block/template_torch", {"torch": side}, {"render_type": CUTOUT})
            self.bs(new, {"variants": {"": {"model": m}}})
            self.item_model(new, layer=item_icon)
        elif kind == "button":
            t = {"texture": side}
            m = self.bm(new, "minecraft:block/button", t)
            mp = self.bm(new + "_pressed", "minecraft:block/button_pressed", t)
            inv = self.bm(new + "_inventory", "minecraft:block/button_inventory", t)
            var = {}
            rot = {"floor": {"east": 90, "north": 0, "south": 180, "west": 270},
                   "wall": {"east": 90, "north": 0, "south": 180, "west": 270},
                   "ceiling": {"east": 270, "north": 180, "south": 0, "west": 90}}
            for face, xr in (("floor", 0), ("wall", 90), ("ceiling", 180)):
                for f, y in rot[face].items():
                    for p in ("false", "true"):
                        d = {"model": mp if p == "true" else m, "y": y}
                        if xr:
                            d["x"] = xr
                        if face == "wall":
                            d["uvlock"] = True
                        var[f"face={face},facing={f},powered={p}"] = d
            self.bs(new, {"variants": var})
            self.item_model(new, inv)
        elif kind == "pressure_plate":
            t = {"texture": side}
            up_m = self.bm(new, "minecraft:block/pressure_plate_up", t)
            dn = self.bm(new + "_down", "minecraft:block/pressure_plate_down", t)
            self.bs(new, {"variants": {"powered=false": {"model": up_m}, "powered=true": {"model": dn}}})
            self.item_model(new, up_m)
        else:  # shaped
            m = self.shaped(new, icons, b)
            self.bs(new, {"variants": {"": {"model": m}}})
            self.item_model(new, m)

    def cube(self, new, icons, extra):
        down, up, north, south, west, east = icons
        if len(set(icons)) == 1:
            return self.bm(new, "minecraft:block/cube_all", {"all": up}, extra)
        if north == south == west == east:
            if up == down:
                return self.bm(new, "minecraft:block/cube_column", {"end": up, "side": north}, extra)
            return self.bm(new, "minecraft:block/cube_bottom_top", {"top": up, "bottom": down, "side": north}, extra)
        return self.bm(new, "minecraft:block/cube", {"down": down, "up": up, "north": north, "south": south,
                                                      "west": west, "east": east, "particle": north}, extra)

    def shaped(self, new, icons, b):
        bb = [round(x * 16, 3) for x in (b.get("bounds") or [0, 0, 0, 1, 1, 1])]
        if bb[3] - bb[0] <= 0 or bb[4] - bb[1] <= 0 or bb[5] - bb[2] <= 0:
            bb = [0, 0, 0, 16, 16, 16]
        down, up, north, south, west, east = icons
        faces = {}
        for face, tex in (("down", "#down"), ("up", "#up"), ("north", "#north"), ("south", "#south"),
                          ("west", "#west"), ("east", "#east")):
            faces[face] = {"texture": tex}
        return self.bm(new, "minecraft:block/block",
                       {"down": down, "up": up, "north": north, "south": south, "west": west, "east": east,
                        "particle": north},
                       {"render_type": CUTOUT, "elements": [{"from": bb[:3], "to": bb[3:], "faces": faces}]})

    def stairs(self, new, t):
        s = self.bm(new, "minecraft:block/stairs", t)
        i = self.bm(new + "_inner", "minecraft:block/inner_stairs", t)
        o = self.bm(new + "_outer", "minecraft:block/outer_stairs", t)
        var = {}
        for f, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
            for half in ("bottom", "top"):
                for shape in ("straight", "inner_left", "inner_right", "outer_left", "outer_right"):
                    model = s if shape == "straight" else (i if shape.startswith("inner") else o)
                    yy = y
                    if half == "bottom" and shape.endswith("left"):
                        yy = y - 90
                    if half == "top" and shape.endswith("right"):
                        yy = y + 90
                    d = {"model": model}
                    if half == "top":
                        d["x"] = 180
                    if yy % 360:
                        d["y"] = yy % 360
                    if "x" in d or "y" in d:
                        d["uvlock"] = True
                    var[f"facing={f},half={half},shape={shape}"] = d
        self.bs(new, {"variants": var})
        self.item_model(new, s)

    def door(self, new, lower, b, v):
        upper_name = (v["icons"][2] or "").replace("_lower", "_upper")
        upper = self.tex.block(upper_name) if "_upper" in upper_name else None
        if not upper:
            upper = lower
            self.report["doors_without_upper_texture"].append(new)
        t = {"bottom": lower, "top": upper}
        ms = {}
        for half in ("bottom", "top"):
            for hinge in ("left", "right"):
                for op in ("", "_open"):
                    k = f"{half}_{hinge}{op}"
                    ms[k] = self.bm(f"{new}_{k}", f"minecraft:block/door_{k}", t, {"render_type": CUTOUT})
        var = {}
        for f, y in (("east", 0), ("south", 90), ("west", 180), ("north", 270)):
            for half, hk in (("lower", "bottom"), ("upper", "top")):
                for hinge in ("left", "right"):
                    for op in ("false", "true"):
                        k = f"{hk}_{hinge}" + ("_open" if op == "true" else "")
                        yy = y
                        if op == "true":
                            yy = y + (90 if hinge == "left" else 270)
                        d = {"model": ms[k]}
                        if yy % 360:
                            d["y"] = yy % 360
                        var[f"facing={f},half={half},hinge={hinge},open={op}"] = d
        self.bs(new, {"variants": var})
        self.item_model(new, layer=self.tex.item(v.get("itemIcon")) or lower)

    # ------------------------------------------------------------------ loot

    def loot(self, new, kind):
        item = f"{NS}:{new}"
        entry = {"type": "minecraft:item", "name": item}
        pool = {"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}
        if kind == "slab":
            entry["functions"] = [{"function": "minecraft:set_count", "count": 2, "add": False, "conditions": [
                {"condition": "minecraft:block_state_property", "block": item, "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}]
            pool.pop("conditions")
        if kind == "door":
            pool["conditions"].append({"condition": "minecraft:block_state_property", "block": item,
                                       "properties": {"half": "lower"}})
        if kind == "leaves":
            pool["conditions"] = [{"condition": "minecraft:any_of", "terms": [
                {"condition": "minecraft:match_tool", "predicate": {"items": "minecraft:shears"}},
                {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]}]
        write_json(os.path.join(DATA, NS, "loot_table", "blocks", new + ".json"),
                   {"type": "minecraft:block", "pools": [pool]})

    # ------------------------------------------------------------------ recipes

    def ingredient(self, x):
        """Old ingredient -> 1.21.1 ingredient JSON, or None when it can't be mapped."""
        if x is None:
            return None
        if isinstance(x, list):
            parts = [self.ingredient(y) for y in x]
            parts = [p for p in parts if p]
            flat = []
            for p in parts:
                flat.extend(p if isinstance(p, list) else [p])
            return flat or None
        if "ore" in x:
            tag = vanilla.ORE_TAG.get(x["ore"]) or f"{NS}:{snake(x['ore'])}"
            self.used_ore_tags[tag] = x["ore"]
            return {"tag": tag}
        old, meta = x["item"], x.get("meta", 0)
        if old.startswith("lotr:"):
            if meta == 32767:
                news = sorted(set(self.old_to_new.get(old, [])))
                if not news:
                    return None
                return [{"item": n} for n in news] if len(news) > 1 else {"item": news[0]}
            new = self.idtable.get(f"{old}#{meta}") or self.idtable.get(f"{old}#0")
            return {"item": new.split("[")[0]} if new else None
        v = vanilla.vanilla_item(old, meta)
        if v is None:
            return None
        return {"tag": v[1]} if v[0] == "tag" else {"item": v[1]}

    def result(self, x):
        if not x:
            return None
        ing = self.ingredient({"item": x["item"], "meta": x.get("meta", 0)})
        if not ing or isinstance(ing, list) or "item" not in ing:
            return None
        r = {"id": ing["item"]}
        if x.get("count", 1) != 1:
            r["count"] = x["count"]
        return r

    def do_recipes(self):
        self.used_ore_tags = {}
        rec = load("recipes.json")
        out_dir = os.path.join(DATA, NS, "recipe")
        seen = collections.Counter()
        groups = [("crafting", rec["crafting"], None)]
        for k, v in rec["stations"].items():
            g = k.replace("Recipes", "")
            if g == "uncraftableUnsmelting":
                continue  # LOTR's list of things the unsmeltery may not take apart; read with that machine
            table = self.tables.get(g.replace("blueMountains", "blueDwarven") + "CraftingTable")
            if g != "woodenSlab" and not table:
                self.report["recipe_lists_without_table"].append(k)
                continue
            groups.append((snake(g) if table else "crafting", v, table))
        for group, recipes, table in groups:
            for r in recipes:
                j = self.recipe(r, group)
                if not j:
                    continue
                name = j["result"]["id"].split(":")[1]
                if table:
                    # Faction table recipes only work at their own table (lotr_port:faction_crafting).
                    j = {"type": f"{NS}:faction_crafting", "table": f"{NS}:{table}", "recipe": j}
                seen[(group, name)] += 1
                n = seen[(group, name)]
                fname = name if n == 1 else f"{name}_{n}"
                write_json(os.path.join(out_dir, group, fname + ".json"), j)
                self.recipe_count += 1
        for s in rec["smelting"]:
            ing = self.ingredient(s["input"])
            res = self.result(s["output"])
            if not ing or not res:
                self.report["unmapped_smelting"].append(s)
                continue
            name = res["id"].split(":")[1]
            seen[("smelting", name)] += 1
            n = seen[("smelting", name)]
            j = {"type": "minecraft:smelting", "ingredient": ing, "result": res,
                 "experience": s.get("xp") or 0.1, "cookingtime": 200}
            write_json(os.path.join(out_dir, "smelting", (name if n == 1 else f"{name}_{n}") + ".json"), j)
            self.recipe_count += 1

    def recipe(self, r, group):
        if r["type"] == "special":
            self.report["special_recipes"].append(r["class"])
            return None
        res = self.result(r.get("output"))
        if not res:
            self.report["unmapped_recipe_results"].append(r.get("output"))
            return None
        cat = "building" if res["id"] in {f"{NS}:{b['id']}" for b in self.blocks_by_id.values()} else "misc"
        if r["type"] == "shapeless":
            ings = [self.ingredient(i) for i in r["input"]]
            if not ings or any(i is None for i in ings):
                self.report["unmapped_recipe_inputs"].append(r)
                return None
            return {"type": "minecraft:crafting_shapeless", "category": cat, "group": group,
                    "ingredients": ings, "result": res}
        w, h = r.get("width"), r.get("height")
        cells = r["input"]
        if not w or not h or len(cells) != w * h:
            self.report["bad_shaped_recipes"].append(r)
            return None
        key, pattern, letters = {}, [], {}
        alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        for row in range(h):
            line = ""
            for col in range(w):
                cell = cells[row * w + col]
                if cell is None:
                    line += " "
                    continue
                ing = self.ingredient(cell)
                if ing is None:
                    self.report["unmapped_recipe_inputs"].append(r)
                    return None
                k = json.dumps(ing, sort_keys=True)
                if k not in letters:
                    letters[k] = alphabet[len(letters)]
                    key[letters[k]] = ing
                line += letters[k]
            pattern.append(line)
        return {"type": "minecraft:crafting_shaped", "category": cat, "group": group, "pattern": pattern,
                "key": key, "result": res}

    def ore_tags(self):
        """Tags for the ore dictionary names recipes use, filled from the exported ore dictionary."""
        ore = load("oredict.json")
        for tag, ore_name in self.used_ore_tags.items():
            values = set(vanilla.VANILLA_IN_TAG.get(tag, []))
            for s in ore.get(ore_name, []):
                new = self.idtable.get(f"{s['item']}#{s.get('meta', 0)}")
                if new:
                    values.add(new.split("[")[0])
            ns, path = tag.split(":", 1)
            if not values and ns != NS:
                continue  # an existing common tag (c:ingots/iron...) filled by vanilla and other mods
            write_json(os.path.join(DATA, ns, "tags", "item", path + ".json"),
                       {"replace": False, "values": sorted(values)})

    # ------------------------------------------------------------------ tabs, lang, tags

    def do_tabs(self):
        tabs = collections.OrderedDict()
        placed = set()
        for t in load("tabs.json"):
            label = TAB_MAP.get(t["label"], t["label"])
            if label in SKIP_TABS:
                continue
            tab = tabs.setdefault(label, {"id": label, "items": []})
            if "name" in t and t["label"] not in TAB_MAP:
                tab["name"] = t["name"]
            for s in t["items"]:
                if not s or not s["item"].startswith("lotr:"):
                    continue
                new = self.idtable.get(f"{s['item']}#{s.get('meta', 0)}")
                if not new:
                    continue
                new = new.split("[")[0].split(":")[1]
                if new not in placed and new not in self.hidden:
                    placed.add(new)
                    tab["items"].append(new)
        everything = [e["id"] for e in self.blocks if e["id"] not in self.hidden] + [e["id"] for e in self.items]
        missing = [i for i in everything if i not in placed]
        if missing:
            tabs.setdefault("misc", {"id": "misc", "items": []})["items"].extend(missing)
            self.report["added_to_misc_tab"] = missing
        out = []
        for t in tabs.values():
            if t["items"]:
                t["icon"] = t["items"][0]
                self.lang["en_us"][f"itemGroup.{NS}.{t['id']}"] = t.pop("name", t["id"].title())
                out.append(t)
        return out

    def other_langs(self):
        for code in self.jar.langs():
            if code == "en_US":
                continue
            src = self.jar.lang(code)
            dst = self.lang[code.lower()]
            for key in self.lang["en_us"]:
                new = key.split(".", 2)[-1]
                old = self.unloc.get(new)
                if old and old in src:
                    dst[key] = src[old]

    def write_tags(self):
        for tag, ids in self.tags.items():
            ns, path = tag.split(":", 1)
            write_json(os.path.join(DATA, ns, "tags", path + ".json"),
                       {"replace": False, "values": sorted(f"{NS}:{i}" for i in ids)})

    # ------------------------------------------------------------------ main

    def run(self):
        for sub in ("assets", "data", NS):
            shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)
        self.do_items()
        self.do_blocks()
        self.blocks_by_id = {b["id"]: b for b in self.blocks}
        self.do_recipes()
        self.ore_tags()
        tabs = self.do_tabs()
        self.other_langs()
        self.write_tags()
        world = World(self, load("world.json"), OUT, self.jar).run()
        content = os.path.join(OUT, NS, "content")
        write_json(os.path.join(content, "blocks.json"), self.blocks)
        write_json(os.path.join(content, "items.json"), self.items)
        write_json(os.path.join(content, "tabs.json"), tabs)
        write_json(os.path.join(content, "summary.json"), [{"recipes": self.recipe_count}])
        for code, entries in self.lang.items():
            write_json(os.path.join(ASSETS, "lang", code + ".json"), dict(sorted(entries.items())))
        write_json(os.path.join(ROOT, "converter", "idtable.json"), dict(sorted(self.idtable.items())))
        summary = {"blocks": len(self.blocks), "items": len(self.items), "recipes": self.recipe_count,
                   "textures": len(self.tex.copied), "languages": len(self.lang), "tabs": len(tabs), **world}
        self.report["missing_textures"] = [m for m in self.report["missing_textures"] if not m.endswith("_mug")]
        rep = {"summary": summary}
        for k, v in sorted(self.report.items()):
            rep[k] = {"count": len(v), "entries": v[:200] if not isinstance(v[0] if v else "", dict) else v[:30]}
        write_json(os.path.join(ROOT, "converter", "report.json"), rep)
        print(json.dumps(summary))
        for k, v in sorted(self.report.items()):
            print(f"  {k}: {len(v)}")


if __name__ == "__main__":
    Gen().run()
