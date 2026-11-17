"""
Every vanilla and common (c:) tag a LOTR block or item should be in, so vanilla and other mods treat them like their
vanilla kin: axes mine wooden blocks, carvers cut LOTR rock, foxes eat LOTR berries, Create sees LOTR ingots...

How: each LOTR block and item gets a vanilla *analogue* (rules on its port kind, its LOTR class, LOTR's material and
its id: a LOTR wooden fence is an oak fence, Gondor rock is stone, a LOTR flower is a poppy), and copies the
analogue's memberships in vanilla 1.21.1's own tag files (read from the client jar), limited to the functional
tags in ALLOWED (never identity tags like minecraft:oak_logs). Then name rules add the c: tags (ingots, gems, ores,
storage blocks, foods, tools...). Things with no analogue get only what their kind implies; LOTR's own harvest tools
from the export (written by generate.py) always stay.

Runs on top of the generated content and can be rerun alone (``python tags.py``); generate.py calls it last, after
woodsets.py. The values it added are remembered in ``content/tag_sources.json``, so a rerun replaces only its own
part of each tag file. ``converter/tags_report.json`` lists every entry's analogue and tags, and the entries it found
no analogue for; GameTest ``ModernTests.everyBlockHasAMiningTool`` fails on any block a tool should mine faster.
"""
import collections
import json
import os
import re
import sys
import zipfile

from woodsets import MC_1211, OUT, Vanilla, write_json

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
MAIN = os.path.join(ROOT, "lotr", "src", "main", "resources")
NS = "lotr_port"

# Vanilla tags that say what a thing *does* (copied from the analogue); identity tags (oak_logs, stone_bricks...) are
# never copied.
ALLOWED_BLOCK = {
    "mineable/axe", "mineable/pickaxe", "mineable/shovel", "mineable/hoe", "sword_efficient",
    "logs", "logs_that_burn", "planks", "wooden_slabs", "wooden_stairs", "wooden_fences", "wooden_doors",
    "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates", "slabs", "stairs", "walls", "fences",
    "fence_gates", "doors", "trapdoors", "buttons", "pressure_plates", "stone_buttons", "stone_pressure_plates",
    "standing_signs", "wall_signs", "signs", "ceiling_hanging_signs", "wall_hanging_signs", "all_hanging_signs",
    "all_signs", "leaves", "saplings", "flowers", "small_flowers", "tall_flowers", "crops", "climbable", "dirt", "sand",
    "snow", "ice", "beds", "wool", "wool_carpets", "replaceable", "replaceable_by_trees", "enderman_holdable",
    "base_stone_overworld", "stone_ore_replaceables", "overworld_carver_replaceables", "terracotta",
    "animals_spawnable_on", "sniffer_diggable_block", "azalea_root_replaceable", "moss_replaceable",
    "lush_ground_replaceable", "mushroom_grow_block", "big_dripleaf_placeable", "small_dripleaf_placeable",
    "dead_bush_may_place_on", "foxes_spawnable_on", "wolves_spawnable_on", "rabbits_spawnable_on",
    "goats_spawnable_on", "frogs_spawnable_on", "parrots_spawnable_on", "axolotls_spawnable_on",
    "armadillo_spawnable_on", "polar_bears_spawnable_on_alternate", "valid_spawn", "bee_growables",
    "maintains_farmland", "convertable_to_mud", "combination_step_sound_blocks", "inside_step_sound_blocks",
    "dampens_vibrations", "occludes_vibration_signals", "smelts_to_glass", "sculk_replaceable",
    "azalea_grows_on", "enchantment_power_provider", "impermeable", "snow_layer_can_survive_on",
    "unstable_bottom_center", "fall_damage_resetting", "guarded_by_piglins", "mangrove_logs_can_grow_through",
    "frog_prefer_jump_to", "bamboo_plantable_on",
}
ALLOWED_ITEM = {
    "logs", "logs_that_burn", "planks", "wooden_slabs", "wooden_stairs", "wooden_fences", "wooden_doors",
    "wooden_trapdoors", "wooden_buttons", "wooden_pressure_plates", "slabs", "stairs", "walls", "fences",
    "fence_gates", "doors", "trapdoors", "buttons", "stone_buttons", "signs", "hanging_signs", "boats", "chest_boats",
    "leaves", "saplings", "flowers", "small_flowers", "tall_flowers", "wool", "wool_carpets", "beds", "dirt", "sand",
    "terracotta", "smelts_to_glass", "stone_crafting_materials", "stone_tool_materials", "completes_find_tree_tutorial",
    "arrows", "swords", "axes", "pickaxes", "shovels", "hoes", "head_armor", "chest_armor", "leg_armor", "foot_armor",
    "trimmable_armor", "meat", "fishes", "horse_food", "pig_food", "chicken_food", "sheep_food", "cow_food",
    "goat_food", "rabbit_food", "parrot_food", "fox_food", "bee_food", "wolf_food", "cat_food", "llama_food",
    "camel_food", "turtle_food", "armadillo_food", "villager_plantable_seeds", "coals", "bookshelf_books",
    "lectern_books", "enchantable/durability", "enchantable/mining", "enchantable/mining_loot", "decorated_pot_sherds",
    "piglin_loved", "dampens_vibrations", "breaks_decorated_pots", "noteblock_top_instruments",
}

# block analogue by (port kind, condition) - the first rule that matches wins. A rule is (kinds, test, analogue);
# test(e) sees e["id"], e["cls"] (LOTR class), e["mat"] (LOTR material), e["sound"].
def _has(*words):
    return lambda e: any(w in e["id"] for w in words)


def _cls(*words):
    return lambda e: any(w in e["cls"] for w in words)


def _mat(*mats):
    return lambda e: e["mat"] in mats


def _all(*tests):
    return lambda e: all(t(e) for t in tests)


ANY = lambda e: True  # noqa: E731
WOODEN = lambda e: e["mat"] == "wood" or e["sound"] == "wood" or bool(e.get("woodset"))  # noqa: E731

BLOCK_RULES = [
    # woodsets.py
    ({"sign"}, ANY, "oak_sign"), ({"wall_sign"}, ANY, "oak_wall_sign"),
    ({"hanging_sign"}, ANY, "oak_hanging_sign"), ({"wall_hanging_sign"}, ANY, "oak_wall_hanging_sign"),
    ({"pillar"}, _all(_has("stripped_"), _has("_beam")), "stripped_oak_wood"),
    ({"pillar"}, _has("stripped_"), "stripped_oak_log"),
    ({"button"}, WOODEN, "oak_button"), ({"button"}, ANY, "stone_button"),
    ({"pressure_plate"}, WOODEN, "oak_pressure_plate"), ({"pressure_plate"}, ANY, "stone_pressure_plate"),
    # shapes
    ({"leaves"}, ANY, "oak_leaves"), ({"sapling"}, ANY, "oak_sapling"),
    ({"door"}, WOODEN, "oak_door"), ({"door"}, ANY, "iron_door"),
    ({"trapdoor"}, WOODEN, "oak_trapdoor"), ({"trapdoor"}, ANY, "iron_trapdoor"),
    ({"fence"}, WOODEN, "oak_fence"), ({"fence"}, ANY, "nether_brick_fence"),
    ({"fence_gate"}, ANY, "oak_fence_gate"),
    ({"slab"}, WOODEN, "oak_slab"), ({"slab"}, _all(_mat("ground", "grass"), _has("dirt", "mud")), "dirt"),
    ({"slab"}, _mat("sand"), "sandstone_slab"), ({"slab"}, _has("cobble"), "cobblestone_slab"),
    ({"slab"}, _mat("glass"), "glass"), ({"slab"}, ANY, "stone_brick_slab"),
    ({"stairs"}, WOODEN, "oak_stairs"), ({"stairs"}, _mat("sand"), "sandstone_stairs"),
    ({"stairs"}, _has("cobble"), "cobblestone_stairs"), ({"stairs"}, _mat("ground", "grass"), "dirt"),
    ({"stairs"}, ANY, "stone_brick_stairs"),
    ({"wall"}, _has("cobble"), "cobblestone_wall"), ({"wall"}, ANY, "stone_brick_wall"),
    ({"pane"}, _mat("glass"), "glass_pane"), ({"pane"}, ANY, "iron_bars"),
    ({"ladder"}, ANY, "ladder"), ({"bed"}, ANY, "red_bed"), ({"carpet"}, ANY, "white_carpet"),
    ({"torch"}, ANY, "torch"), ({"crop"}, ANY, "wheat"), ({"corn"}, ANY, "wheat"),
    ({"berry_bush"}, ANY, "sweet_berry_bush"), ({"reed"}, ANY, "sugar_cane"), ({"grapevine"}, ANY, "@axe"),
    ({"faction_table"}, ANY, "crafting_table"), ({"keg"}, ANY, "@axe"),
    ({"gate"}, WOODEN, "@axe"), ({"gate"}, ANY, "@pickaxe"), ({"orc_bomb"}, ANY, "tnt"),
    ({"ore"}, ANY, "iron_ore"),
    # plants
    ({"plant"}, _cls("DoubleFlower"), "rose_bush"), ({"plant"}, _cls("Flower"), "poppy"),
    ({"plant"}, _cls("TallGrass", "Grass", "Fern"), "short_grass"), ({"plant"}, _cls("Mushroom"), "brown_mushroom"),
    ({"plant"}, _cls("DeadPlant", "Dead"), "dead_bush"), ({"plant"}, _cls("Chandelier"), "@pickaxe"),
    ({"plant"}, ANY, "short_grass"),
    # pillars
    ({"pillar"}, _all(WOODEN, _has("beam")), "oak_wood"), ({"pillar"}, WOODEN, "oak_log"),
    ({"pillar"}, _mat("plants", "grass", "leaves"), "hay_block"), ({"pillar"}, ANY, "quartz_pillar"),
    # set-down vessels, plates, pots and the like (instant to break), chains, rails, remains, ice spikes
    ({"shaped"}, _cls("AleHorn", "ClayMug", "Goblet", "SkullCup", "Plate", "FlowerPot", "Mug"), "flower_pot"),
    ({"shaped"}, _cls("ArmorStand"), "@axe"), ({"shaped"}, _cls("OrcChain"), "chain"),
    ({"shaped"}, _cls("MechanisedRail"), "rail"), ({"cube", "shaped"}, _cls("Remains"), "bone_block"),
    ({"shaped"}, _cls("Stalactite", "RhunFireJar"), "@pickaxe"),
    ({"shaped"}, _all(_cls("BirdCage"), WOODEN), "@axe"), ({"shaped"}, _cls("BirdCage"), "@pickaxe"),
    ({"shaped"}, _cls("Portal", "MarshLights", "RhunFire"), "@none"),
    # cubes and shaped blocks by LOTR class, then material
    ({"cube", "shaped"}, _cls("FallenLeaves"), "@hoe"), ({"cube", "shaped"}, _cls("Planks"), "oak_planks"),
    ({"cube", "shaped"}, _cls("OreStorage", "GemStorage"), "iron_block"),
    ({"cube", "shaped"}, _cls("BlockOre"), "iron_ore"),
    ({"cube", "shaped"}, _cls("StainedGlass", "Glass"), "glass"),
    ({"cube", "shaped"}, _cls("ClayTile", "Terracotta"), "terracotta"),
    ({"cube", "shaped"}, _cls("Rock"), "stone"), ({"cube"}, _has("cobble"), "cobblestone"),
    ({"cube", "shaped"}, _cls("Brick", "Pillar", "SmoothStone", "Utumno"), "stone_bricks"),
    ({"cube", "shaped"}, _cls("Chest"), "chest"), ({"cube", "shaped"}, _cls("TreasurePile"), "@shovel"),
    ({"cube", "shaped"}, _cls("PlaceableFood"), "cake"), ({"cube", "shaped"}, _cls("Thatch"), "hay_block"),
    ({"cube"}, _all(_mat("grass"), _has("grass")), "grass_block"), ({"cube"}, _mat("ground"), "dirt"),
    ({"cube"}, _has("mud"), "mud"), ({"cube", "slab"}, _has("gravel"), "gravel"), ({"cube"}, _mat("sand"), "sand"), ({"cube"}, _mat("clay"), "clay"),
    ({"cube"}, _mat("cloth"), "white_wool"), ({"cube"}, _mat("packedIce"), "packed_ice"),
    ({"cube"}, _mat("web"), "cobweb"),
    ({"cube"}, _mat("rock"), "stone_bricks"), ({"cube"}, WOODEN, "oak_planks"), ({"cube"}, _mat("iron"), "iron_block"),
    ({"cube", "shaped"}, _mat("glass"), "glass"),
    ({"cube", "shaped"}, _mat("rock", "iron"), "@pickaxe"), ({"cube", "shaped"}, WOODEN, "@axe"),
    ({"cube", "shaped"}, _mat("ground", "sand", "clay", "grass", "snow", "craftedSnow"), "@shovel"),
    ({"cube", "shaped"}, _mat("leaves", "plants", "vine"), "@hoe"),
]

# item analogue by port kind / LOTR class / id
ITEM_RULES = [
    ({"sword"}, ANY, "iron_sword"), ({"axe"}, ANY, "iron_axe"), ({"pickaxe"}, ANY, "iron_pickaxe"),
    ({"shovel"}, ANY, "iron_shovel"), ({"hoe"}, ANY, "iron_hoe"), ({"bow"}, ANY, "bow"),
    ({"crossbow"}, ANY, "crossbow"), ({"arrow"}, ANY, "arrow"), ({"boat"}, ANY, "oak_boat"),
    ({"chest_boat"}, ANY, "oak_chest_boat"), ({"sign"}, ANY, "oak_sign"), ({"hanging_sign"}, ANY, "oak_hanging_sign"),
    ({"armor"}, lambda e: e.get("slot") == "head", "iron_helmet"),
    ({"armor"}, lambda e: e.get("slot") == "chest", "iron_chestplate"),
    ({"armor"}, lambda e: e.get("slot") == "legs", "iron_leggings"),
    ({"armor"}, lambda e: e.get("slot") == "feet", "iron_boots"),
    ({"item", "food"}, _cls("Seeds"), "wheat_seeds"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("raw", "uncooked")), "beef"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("cooked", "roast", "kebab", "steak", "venison",
                                                          "mutton", "chop", "meat")), "cooked_beef"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("berry", "berries")), "sweet_berries"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("apple", "pear", "cherry", "plum", "orange", "lemon",
                                                          "lime", "mango", "banana", "date", "olive",
                                                          "almond", "pomegranate", "grape")), "apple"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("lettuce", "leek", "turnip", "onion", "corn", "potato",
                                                          "carrot")), "carrot"),
    ({"item", "food"}, _all(lambda e: e.get("food"), _has("bread", "cram", "lembas", "biscuit", "cake")), "bread"),
    ({"item"}, _has("bone"), "bone"), ({"item"}, _has("feather"), "feather"), ({"item"}, _has("_fur", "fur", "hide"),
                                                                                 "leather"),
    ({"item"}, _has("charcoal", "coal"), "charcoal"),
]

# c: tags from the analogue (block / item), and from names
C_BY_ANALOGUE = {
    "cobblestone": (["c:cobblestones", "c:cobblestones/normal"], ["c:cobblestones", "c:cobblestones/normal"]),
    "stone": (["c:stones"], ["c:stones"]), "glass": (["c:glass_blocks"], ["c:glass_blocks"]),
    "glass_pane": (["c:glass_panes"], ["c:glass_panes"]), "oak_fence": (["c:fences/wooden", "c:fences"],
                                                                        ["c:fences/wooden", "c:fences"]),
    "nether_brick_fence": (["c:fences"], ["c:fences"]),
    "oak_fence_gate": (["c:fence_gates/wooden", "c:fence_gates"], ["c:fence_gates/wooden", "c:fence_gates"]),
    "chest": (["c:chests/wooden", "c:chests"], ["c:chests/wooden", "c:chests"]),
    "stripped_oak_log": (["c:stripped_logs"], ["c:stripped_logs"]),
    "stripped_oak_wood": (["c:stripped_woods"], ["c:stripped_woods"]),
    "sand": (["c:sands"], ["c:sands"]), "gravel": (["c:gravels"], ["c:gravels"]), "crafting_table": (["c:player_workstations/crafting_tables"],
                                                          ["c:player_workstations/crafting_tables"]),
    "iron_ore": (["c:ores"], ["c:ores"]), "iron_block": ([], []),
    "iron_sword": ([], ["c:tools/melee_weapon", "c:tools"]), "iron_axe": ([], ["c:tools/mining_tool", "c:tools"]),
    "iron_pickaxe": ([], ["c:tools/mining_tool", "c:tools"]), "iron_shovel": ([], ["c:tools"]),
    "iron_hoe": ([], ["c:tools"]), "bow": ([], ["c:tools/bow", "c:tools/ranged_weapon", "c:tools"]),
    "crossbow": ([], ["c:tools/crossbow", "c:tools/ranged_weapon", "c:tools"]),
    "iron_helmet": ([], ["c:armors"]), "iron_chestplate": ([], ["c:armors"]), "iron_leggings": ([], ["c:armors"]),
    "iron_boots": ([], ["c:armors"]),
    "wheat_seeds": ([], ["c:seeds"]), "beef": ([], ["c:foods", "c:foods/raw_meat"]),
    "cooked_beef": ([], ["c:foods", "c:foods/cooked_meat"]), "sweet_berries": ([], ["c:foods", "c:foods/berry"]),
    "apple": ([], ["c:foods", "c:foods/fruit"]), "carrot": ([], ["c:foods", "c:foods/vegetable"]),
    "bread": ([], ["c:foods", "c:foods/bread"]), "bone": ([], ["c:bones"]), "feather": ([], ["c:feathers"]),
    "leather": ([], ["c:leathers"]), "cake": (["c:foods/edible_when_placed"], []),
}
METALS = ("copper", "tin", "bronze", "silver", "gold", "iron", "mithril", "galvorn", "durnor", "morgul_iron",
          "orc_steel", "uruk_steel", "black_uruk_steel", "dwarven_steel", "blue_dwarven_steel", "elven_steel",
          "gilded_iron")
GEMS = ("amber", "amethyst", "diamond", "emerald", "opal", "pearl", "ruby", "sapphire", "topaz", "gulduril",
        "edhelvir", "glowstone")


def c_by_name(e, is_block):
    """c: tags from the id: ingots, nuggets, gems, ores and storage blocks of LOTR's metals and gems."""
    i, out = e["id"], []
    for m in METALS:
        if i == f"{m}_ingot":
            out.append(f"c:ingots/{m}")
            out.append("c:ingots")
        if i == f"{m}_nugget":
            out += [f"c:nuggets/{m}", "c:nuggets"]
        if i in (f"{m}_ore", f"deepslate_{m}_ore") or (i.endswith("_ore") and i[:-4].endswith(m)):
            out += [f"c:ores/{m}", "c:ores"]
        if i in (f"{m}_block", f"block_of_{m}"):
            out += [f"c:storage_blocks/{m}", "c:storage_blocks"]
    for g in GEMS:
        if not is_block and i == g:
            out += [f"c:gems/{g}", "c:gems"]
        if i.endswith("_ore") and g in i:
            out += [f"c:ores/{g}", "c:ores"]
        if i in (f"{g}_block", f"block_of_{g}"):
            out += [f"c:storage_blocks/{g}", "c:storage_blocks"]
    if e.get("drink"):
        out += ["c:drinks"]
        if "juice" in i:
            out.append("c:drinks/juice")
        if i == "milk":
            out.append("c:drinks/milk")
        if i == "water":
            out.append("c:drinks/water")
    if e.get("food") and "c:foods" not in out:
        out.append("c:foods")
    return out


# Blocks registered by hand in Java (not in blocks.json): their tags live here so all mining tags come from one place
HAND = {
    "minecraft:block/mineable/axe": ["building_plaque", "treasury_ledger", "town_hall_ledger", "customs_counter"],
}


class VanillaTags:
    """Vanilla 1.21.1's block and item tags (nested tags expanded), by id."""

    def __init__(self, vanilla):
        self.raw = {"block": {}, "item": {}}
        for name in vanilla.jar.namelist():
            m = re.match(r"data/minecraft/tags/(block|item)/(.+)\.json$", name)
            if m:
                self.raw[m.group(1)][m.group(2)] = json.loads(vanilla.jar.read(name))["values"]
        self.member = {"block": collections.defaultdict(set), "item": collections.defaultdict(set)}
        for reg in ("block", "item"):
            for tag in self.raw[reg]:
                for v in self.expand(reg, tag, set()):
                    self.member[reg][v].add(tag)

    def expand(self, reg, tag, seen):
        if tag in seen:
            return set()
        seen.add(tag)
        out = set()
        for v in self.raw[reg].get(tag, []):
            v = v["id"] if isinstance(v, dict) else v
            if v.startswith("#"):
                ref = v[1:].split(":", 1)
                if ref[0] == "minecraft":
                    out |= self.expand(reg, ref[1], seen)
            else:
                out.add(v.split(":", 1)[1] if ":" in v else v)
        return out

    def of(self, reg, vid):
        return self.member[reg].get(vid, set())


class Tagger:
    def __init__(self, out=OUT):
        self.out = out
        self.content = os.path.join(out, NS, "content")
        self.vanilla = Vanilla(os.path.join(HERE, "cache"))
        self.vt = VanillaTags(self.vanilla)
        self.blocks = json.load(open(os.path.join(self.content, "blocks.json"), encoding="utf-8"))
        self.items = json.load(open(os.path.join(self.content, "items.json"), encoding="utf-8"))
        self.tags = collections.defaultdict(set)  # "minecraft:block/logs" -> {"lotr_port:x"}
        self.report = {"blocks": {}, "items": {}, "no_analogue": []}
        self.lotr = self.lotr_info()

    def lotr_info(self):
        """LOTR's class and material for each port id, from the export and the id table."""
        info = {}
        export = os.path.join(HERE, "export")
        idtable = json.load(open(os.path.join(HERE, "idtable.json"), encoding="utf-8"))
        blocks = {b["id"]: b for b in json.load(open(os.path.join(export, "blocks.json"), encoding="utf-8"))}
        items_raw = json.load(open(os.path.join(export, "items.json"), encoding="utf-8"))
        items = {i["id"]: i for i in (items_raw["items"] if isinstance(items_raw, dict) else items_raw)}
        for old, new in idtable.items():
            nid = new.split("[")[0].split(":", 1)[1]
            o = old.split("#")[0]
            src = blocks.get(o) or items.get(o)
            if src and nid not in info:
                info[nid] = {"cls": src["classes"][0].split(".")[-1], "mat": src.get("material", "")}
        return info

    def entry(self, e):
        x = dict(e)
        x.update(self.lotr.get(e["id"], {"cls": "", "mat": ""}))
        x.setdefault("sound", e.get("sound", ""))
        if "armor" in e:
            x["slot"] = e["armor"].get("slot", "chest")
        return x

    def analogue(self, rules, e):
        kind = e.get("kind", "cube" if rules is BLOCK_RULES else "item")
        for kinds, test, ana in rules:
            if kind in kinds and test(e):
                return ana
        return None

    def add(self, reg, ns_tag, i):
        ns, tag = ns_tag.split(":", 1) if ":" in ns_tag else ("minecraft", ns_tag)
        self.tags[f"{ns}:{reg}/{tag}"].add(f"{NS}:{i}")

    def run(self):
        block_items = {}
        for b in self.blocks:
            e = self.entry(b)
            ana = self.analogue(BLOCK_RULES, e)
            tags = []
            if ana is None:
                self.report["no_analogue"].append(f"block {b['id']} ({e.get('kind')}, {e['cls']}, {e['mat']})")
            elif ana == "@none":
                tags = []  # portals, fire, marsh lights: nothing to mine
            elif ana.startswith("@"):
                tags = [f"mineable/{ana[1:]}"]
            else:
                tags = sorted(self.vt.of("block", ana) & ALLOWED_BLOCK)
                for c in C_BY_ANALOGUE.get(ana, ([], []))[0]:
                    self.add("block", c, b["id"])
            if b.get("hardness", 1) == 0:  # instant: a tool doesn't help, and vanilla tags no plant as mineable
                tags = [t for t in tags if not t.startswith("mineable/")]
            for t in tags:
                self.add("block", t, b["id"])
            for c in c_by_name(e, True):
                self.add("block", c, b["id"])
            self.report["blocks"][b["id"]] = {"analogue": ana, "tags": tags}
            if b.get("item", "block") != "none":
                block_items[b["id"]] = ana
        # block items: the analogue's item tags
        for i, ana in block_items.items():
            if ana and not ana.startswith("@"):  # (@tool analogues have no item tags)
                for t in sorted(self.vt.of("item", ana) & ALLOWED_ITEM):
                    self.add("item", t, i)
                for c in C_BY_ANALOGUE.get(ana, ([], []))[1]:
                    self.add("item", c, i)
            for c in c_by_name(self.entry(next(b for b in self.blocks if b["id"] == i)), True):
                self.add("item", c, i)
        for it in self.items:
            e = self.entry(it)
            ana = self.analogue(ITEM_RULES, e)
            tags = sorted(self.vt.of("item", ana) & ALLOWED_ITEM) if ana else []
            for t in tags:
                self.add("item", t, it["id"])
            for c in C_BY_ANALOGUE.get(ana, ([], []))[1] if ana else []:
                self.add("item", c, it["id"])
            for c in c_by_name(e, False):
                self.add("item", c, it["id"])
            self.report["items"][it["id"]] = {"analogue": ana, "tags": tags}
        self.wood_tags()
        for tag, ids in HAND.items():
            self.tags[tag] |= {f"{NS}:{i}" for i in ids}
        self.write()
        return {"tags": len(self.tags), "values": sum(len(v) for v in self.tags.values()),
                "blocks_without_analogue": sum(1 for x in self.report["no_analogue"] if x.startswith("block"))}

    def wood_tags(self):
        """Per wood: lotr_port:<wood>_logs (log, beam, stripped) like vanilla's oak_logs, which burn and make planks."""
        for b in self.blocks:
            base = b.get("woodset")
            if not base or b.get("kind") != "pillar":
                continue
            for i in (f"{base}_wood", f"{base}_wood_beam", f"stripped_{base}_wood", f"stripped_{base}_wood_beam"):
                for reg in ("block", "item"):
                    self.tags[f"{NS}:{reg}/{base}_logs"].add(f"{NS}:{i}")

    def no_tool(self):
        """lotr_port:no_mining_tool: blocks that take time to break but no tool is faster for (like vanilla's glass,
        beds and cakes). GameTest everyBlockHasAMiningTool checks every other block is in a mineable tag."""
        mined = set()
        for root in (self.out, MAIN):
            d = os.path.join(root, "data", "minecraft", "tags", "block", "mineable")
            for f in os.listdir(d) if os.path.isdir(d) else []:
                mined |= {v["id"] if isinstance(v, dict) else v for v in json.load(open(os.path.join(d, f)))["values"]}
        none = sorted(f"{NS}:{b['id']}" for b in self.blocks
                      if b.get("hardness", 1) > 0 and f"{NS}:{b['id']}" not in mined)
        write_json(os.path.join(self.out, "data", NS, "tags", "block", "no_mining_tool.json"),
                   {"replace": False, "values": none})
        self.report["no_mining_tool"] = none

    # ------------------------------------------------------------------ writing

    def tag_path(self, tag):
        ns, path = tag.split(":", 1)
        reg, name = path.split("/", 1)
        return os.path.join("data", ns, "tags", reg, name + ".json")

    def write(self):
        sources_path = os.path.join(self.content, "tag_sources.json")
        old = json.load(open(sources_path, encoding="utf-8")) if os.path.exists(sources_path) else {}
        mine = {}
        for tag in sorted(set(self.tags) | set(old)):
            rel = self.tag_path(tag)
            if os.path.exists(os.path.join(MAIN, rel)):
                # a hand-written tag file in src/main: ours would clash with it in the jar, so add to it there
                path = os.path.join(MAIN, rel)
            else:
                path = os.path.join(self.out, rel)
            data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {"replace": False, "values": []}
            values = [v for v in data["values"] if (v["id"] if isinstance(v, dict) else v) not in set(old.get(tag, []))]
            have = {v["id"] if isinstance(v, dict) else v for v in values}
            added = sorted(self.tags.get(tag, set()) - have)
            if not values and not added:
                if os.path.exists(path) and path.startswith(self.out):
                    os.remove(path)
                continue
            data["values"] = values + added
            write_json(path, data)
            if added:
                mine[tag] = added
        write_json(sources_path, mine)
        self.no_tool()
        write_json(os.path.join(HERE, "tags_report.json"), self.report)


if __name__ == "__main__":
    print(json.dumps(Tagger(sys.argv[1] if len(sys.argv) > 1 else OUT).run()))
