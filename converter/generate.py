#!/usr/bin/env python3
"""
Turns the LOTR export (converter/export/*.json, written by the 1.7.10 exporter in CI) plus the LOTR jar's textures and
lang files into the 1.21.1 port's generated resources:

  lotr/src/generated/resources/lotr_port/content/   block, item and creative tab lists the mod registers from
  lotr/src/generated/resources/assets/lotr_port/     blockstates, models, textures, lang files
  lotr/src/generated/resources/data/                 recipes, loot tables, tags, Middle-earth world gen (world.py)
  lotr/src/generated/resources/lotr_port/map/        the Middle-earth biome map (world.py)
  lotr/src/generated/resources/data/lotr_port/faction/  LOTR's factions (factions.py)
  converter/idtable.json                              old id + metadata -> new id (for structures and saves later)
  converter/report.json                               everything that was skipped or guessed

Run from the repo root: python3 converter/generate.py
"""
import collections
import io
import json
import os
import shutil
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from assets import NS, Jar, Textures, id_from_name, snake, write_json  # noqa: E402
import vanilla  # noqa: E402
from world import World  # noqa: E402
from factions import FactionData  # noqa: E402
from npcs import Npcs  # noqa: E402
from structures import Structures  # noqa: E402
from quests import Quests  # noqa: E402
from colonies import Colonies  # noqa: E402
from saplings import Saplings  # noqa: E402
from classfile import ClassFile  # noqa: E402
from animals import Animals  # noqa: E402
from armor_models import ArmorModels  # noqa: E402
from drinks import Drinks  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXPORT = os.path.join(ROOT, "converter", "export")
OUT = os.path.join(ROOT, "lotr", "src", "generated", "resources")
ASSETS = os.path.join(OUT, "assets", NS)
DATA = os.path.join(OUT, "data")

SKIP_ITEMS = {"lotr:item.spawnEgg", "lotr:item.structureSpawner"}  # NPC and structure phases
# LOTR items that modern Minecraft has itself (1.7.10 had no mutton): use the vanilla item everywhere.
VANILLA_ITEMS = {"lotr:item.muttonRaw": "minecraft:mutton", "lotr:item.muttonCooked": "minecraft:cooked_mutton",
                # LOTR made these from vanilla horse armor (templateItem)
                "lotr:item.horseArmorIron": "minecraft:iron_horse_armor",
                "lotr:item.horseArmorGold": "minecraft:golden_horse_armor",
                "lotr:item.horseArmorDiamond": "minecraft:diamond_horse_armor"}
TAB_MAP = {"buildingBlocks": "blocks", "redstone": "util"}  # LOTR things LOTR put in vanilla tabs
SKIP_TABS = {"spawning"}
REQUIRES_TOOL = {"rock", "iron", "anvil"}
CUTOUT = "minecraft:cutout"


def load(name):
    with open(os.path.join(EXPORT, name), encoding="utf-8") as f:
        return json.load(f)


# LOTR blocks whose metadata 2-5 says which way the front faces, like a furnace.
FACING_CLASSES = {"LOTRBlockForgeBase", "LOTRBlockHobbitOven", "LOTRBlockBarrel", "LOTRBlockKebabStand",
                  "LOTRBlockChest", "LOTRBlockMillstone"}


# LOTR's melee weapon families, most specific first (a pike is a long polearm is a polearm is a sword).
WEAPON_TYPES = (("LOTRItemLance", "lance"), ("LOTRItemPike", "pike"), ("LOTRItemPolearmLong", "long_polearm"),
                ("LOTRItemTrident", "trident"), ("LOTRItemPolearm", "polearm"), ("LOTRItemSpear", "spear"),
                ("LOTRItemDagger", "dagger"), ("LOTRItemSauronMace", "hammer"), ("LOTRItemHammer", "hammer"),
                ("LOTRItemBattleaxe", "battleaxe"), ("LOTRItemBalrogWhip", "whip"))
# 1.7.10 LOTR: a player's melee attack takes LOTRWeaponStats.basePlayerMeleeTime / speed factor ticks, a mob's
# baseMobMeleeTime / speed factor; reach is 3 blocks x the reach factor. Read from LOTRWeaponStats' fields would need
# the running game, so these two are the class's constants (bipush 15 / bipush 20 in its <clinit>).
LOTR_PLAYER_MELEE_TICKS = 15
LOTR_MOB_MELEE_TICKS = 20
# LOTRItemDagger.applyStandardPoison (daggers, bolts, darts): n = 1 + 2 x difficulty, Poison I for n to 2n - 1
# seconds. A 1.21 arrow carries a fixed effect, so poisoned arrows and bolts take the normal-difficulty middle (7 s).
LOTR_POISON = {"base_seconds": 1, "per_difficulty": 2, "arrow_ticks": 140}
# Only plain swords sweep (1.9's sweeping edge; LOTR had none). Daggers, spears, polearms and hammers hit one target.
SWEEPING_WEAPONS = {"sword", "battleaxe"}
# Extra sweeping_damage_ratio (player attribute; a sweep hits for 1 + ratio x damage). Battleaxes cleave harder than
# swords: +0.5 is Sweeping Edge II's worth on top (Aaron, 2026-11-04).
SWEEP_RATIO = {"battleaxe": 0.5}
# Armor toughness (1.9; LOTR had none). Mithril like netherite, dwarven like diamond (Aaron, 2026-11-04).
ARMOR_TOUGHNESS = {"mithril": 3.0, "dwarven": 2.0, "blue_dwarven": 2.0}
# LOTR draws icons in folders large/ (2x) and large2/ (3x) at that scale in the hand (LOTRRenderLargeItem).
LARGE_ICON_FOLDERS = (("large2", 3), ("large", 2))


# Armor items whose texture is their own file in assets/lotr/armor/ (no _1/_2 layers): item id -> file stem.
ITEM_ARMOR = {"dalish_gambeson": "gambeson_dale", "dol_amroth_gambeson": "gambeson_dolAmroth",
              "gondorian_gambeson": "gambeson_gondor", "lamedon_jacket": "jacket_lamedon",
              "lebennin_gambeson": "gambeson_lebennin", "dol_amroth_chaps": "gambeson_dolAmrothLegs",
              "leather_hat": "hat", "party_hat": "partyhat"}
# LOTR dyes its hats; these are the undyed colours (leather brown, a festive red).
HAT_TINTS = {"leather_hat": (0x68, 0x4A, 0x36), "party_hat": (0xC8, 0x3A, 0x3A)}


def tint(img, rgb):
    """Multiplies an image by a colour, as LOTR's dyed hats are drawn."""
    r, g, b, a = img.split()
    r = r.point(lambda x: x * rgb[0] // 255)
    g = g.point(lambda x: x * rgb[1] // 255)
    b = b.point(lambda x: x * rgb[2] // 255)
    return Image.merge("RGBA", (r, g, b, a))


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
        self.melee = self.weapon_tables()
        self.drinks = Drinks(self, EXPORT, ASSETS, DATA, os.path.join(OUT, NS, "content"))

    def weapon_tables(self):
        """LOTR's per-class melee speed, reach and knockback tables, read from LOTRWeaponStats' static initialiser
        (registerMeleeSpeed(LOTRItemDagger.class, 1.5F) ...)."""
        out = {"registerMeleeSpeed": {}, "registerMeleeReach": {}, "registerMeleeExtraKnockback": {}}
        cf = ClassFile(self.jar.read("lotr/common/item/LOTRWeaponStats.class"))
        for name, args in cf.static_calls():
            if name in out and len(args) == 2 and isinstance(args[0], tuple):
                out[name][args[0][1].split("/")[-1]] = round(args[1], 3)
        return out

    def class_stat(self, chain, table, default):
        for c in chain:
            if c in self.melee[table]:
                return self.melee[table][c]
        return default

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
        self.items_by_old = {}
        for it in load("items.json"):
            self.items_by_old[it["id"]] = it
            if it["id"] in SKIP_ITEMS:
                self.report["skipped_items"].append(it["id"])
                continue
            if it["id"] in VANILLA_ITEMS:
                self.idtable[f"{it['id']}#0"] = VANILLA_ITEMS[it["id"]]
                self.old_to_new[it["id"]].append(VANILLA_ITEMS[it["id"]])
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
                       ("ItemSpade", "shovel"), ("ItemHoe", "hoe"), ("ItemArmor", "armor"),
                       ("LOTRItemRedBook", "red_book")):
            if cls in classes and (k != "armor" or "armor" in it):
                kind = k
                break
        chain = [c.split(".")[-1] for c in it["classes"]]
        if kind == "item":
            if "LOTRItemCrossbow" in chain:
                kind = "crossbow"
            elif "ItemBow" in chain:
                kind = "bow"
            elif "LOTRItemCrossbowBolt" in chain:
                kind = "bolt"
            elif "LOTRItemArrowPoisoned" in chain:
                kind = "arrow"
            kind = self.drinks.kind(it["id"]) or kind  # LOTR's mugs: full ones are drinks, empty ones vessels
        e["kind"] = kind
        if kind == "drink":
            e["drink"] = self.drinks.entry(it["id"])
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
        if kind == "sword":
            e["weapon"] = self.melee_weapon(it, v, chain)
        elif kind in ("bow", "crossbow"):
            e["ranged"] = self.ranged_weapon(it, v)
        if kind == "bolt":
            self.tags[f"{NS}:item/crossbow_bolts"].add(new)
        if (v.get("weapon") or {}).get("poisoned") or (kind in ("sword", "bolt", "arrow") and "oisoned" in it["id"]):
            e["poison"] = dict(LOTR_POISON)
        if "armor" in it:
            a = it["armor"]
            mat = snake(a["material"].replace("LOTR_", "")) or "armor"
            slot = ["head", "chest", "legs", "feet"][a["slot"]]
            texture = self.item_armor_texture(new, slot) or self.armor_texture(mat)
            e["armor"] = {"slot": slot, "defense": a["defense"], "material": mat, "texture": texture}
            if mat in ARMOR_TOUGHNESS:
                e["armor"]["toughness"] = ARMOR_TOUGHNESS[mat]
        food = v.get("food")
        if food and food.get("heal") is not None:
            e["food"] = {"nutrition": food["heal"], "saturation": food.get("saturation") or 0.3}
        self.items.append(e)
        self.unloc[new] = (v.get("unlocalized") or "") + ".name"
        self.lang["en_us"][f"item.{NS}.{new}"] = name

        if kind == "drink" and self.drinks.models(new, next((i for i in v.get("icons", []) if i), None)):
            return  # the drink in each vessel (drinks.py)
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
        raw = next((i for i in v.get("icons", []) if i), None)
        self.item_models(new, kind, icons, parent, raw)

    def melee_weapon(self, it, v, chain):
        """
        1.7.10 LOTR had no attack cooldown: a weapon's speed was LOTR's own timer (15 ticks for a sword, divided by the
        class's speed factor), and reach was 3 blocks times a reach factor. 1.21 has both as attributes: attack speed
        is attacks per second (20 / ticks; vanilla swords 1.6), reach is player.entity_interaction_range (base 3).
        Exported numbers (LOTRWeaponStats read in the running game) win; the class tables are the fallback.
        """
        typ = next((t for c, t in WEAPON_TYPES if c in chain), "sword")
        ex = v.get("weapon") or {}
        speed = ex.get("meleeSpeed", self.class_stat(chain, "registerMeleeSpeed", 1.0))
        reach = ex.get("meleeReach", self.class_stat(chain, "registerMeleeReach", 1.0))
        knock = ex.get("knockback", self.class_stat(chain, "registerMeleeExtraKnockback", 0))
        if not ex:
            self.report["weapon_stats_from_class_tables"].append(it["id"])
        ticks = ex.get("playerTicks") or max(1, round(LOTR_PLAYER_MELEE_TICKS / speed))
        mob_ticks = ex.get("mobTicks") or max(1, round(LOTR_MOB_MELEE_TICKS / speed))
        return {"type": typ, "attack_speed": round(20 / ticks, 3), "lotr_ticks": ticks, "mob_ticks": mob_ticks,
                "reach": round(3.0 * (reach - 1.0), 3), "knockback": knock, "sweep": typ in SWEEPING_WEAPONS,
                "sweep_ratio": SWEEP_RATIO.get(typ, 0.0)}

    def ranged_weapon(self, it, v):
        """Draw time (ticks to full power; vanilla bows 20) and arrow damage factor of a LOTR bow or crossbow, from
        the export. Until the exporter has run with weapon stats, vanilla's numbers stand in."""
        ex = v.get("weapon") or {}
        if "drawTime" not in ex:
            self.report["ranged_stats_defaulted"].append(it["id"])
        return {"draw_time": ex.get("drawTime", 20), "damage_factor": round(ex.get("damageFactor", 1.0), 3)}

    def large_icon(self, icon):
        """LOTR's bigger in-hand icon for a texture name, with its scale, or None."""
        if not icon or not icon.startswith("lotr:"):
            return None
        path = icon.split(":", 1)[1]
        for folder, scale in LARGE_ICON_FOLDERS:
            if self.jar.has(f"assets/lotr/textures/items/{folder}/{path}.png"):
                return self.tex.item(f"lotr:{folder}/{path}"), scale, folder, path
        return None

    def item_models(self, new, kind, icons, parent, raw_icon):
        """
        The item model. LOTR draws many weapons (and bows' pull frames) with a bigger icon in the hand, scaled 2x or 3x
        about the item's centre: those get NeoForge's separate_transforms model, the small icon in the GUI, on the
        ground and in frames, and the big one (on lotr_port:item/large_<n>x, vanilla handheld scaled) elsewhere.
        """
        small = {"parent": parent, "textures": {f"layer{i}": t for i, t in enumerate(icons)}}
        large = self.large_icon(raw_icon)

        def model(layer0_large, small_model):
            if not large:
                return small_model
            return {"loader": "neoforge:separate_transforms",
                    "base": {"parent": f"{NS}:item/large_{large[1]}x", "textures": {"layer0": layer0_large}},
                    "perspectives": {p: small_model for p in ("gui", "ground", "fixed")}}

        top = model(large[0] if large else None, small)
        if kind in ("bow", "crossbow"):
            frames = []
            if large:
                for n in range(3):
                    t = self.tex.item(f"lotr:{large[2]}/{large[3]}_pull_{n}")
                    if t:
                        frames.append(model(t, small))
            elif raw_icon:
                for n in range(3):
                    t = self.tex.item(f"{raw_icon}_pull_{n}")
                    if t:
                        frames.append({"parent": parent, "textures": {"layer0": t}})
            if frames:
                overrides = []
                for n, (f, pull) in enumerate(zip(frames, (0.0, 0.65, 0.9))):
                    name = f"{new}_pulling_{n}"
                    write_json(os.path.join(ASSETS, "models", "item", name + ".json"), f)
                    pred = {"minecraft:pulling": 1}
                    if pull:
                        pred["minecraft:pull"] = pull
                    overrides.append({"predicate": pred, "model": f"{NS}:item/{name}"})
                if kind == "crossbow":
                    overrides.append({"predicate": {"minecraft:charged": 1},
                                      "model": f"{NS}:item/{new}_pulling_{len(frames) - 1}"})
                top["overrides"] = overrides
            else:
                self.report["bows_without_pull_frames"].append(new)
        if large:
            self.report["large_in_hand_icons"].append(f"{new} ({large[2]})")
        write_json(os.path.join(ASSETS, "models", "item", new + ".json"), top)

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

    def item_armor_texture(self, item, slot):
        """
        Armor whose LOTR texture is per item, not per material: the gambesons and jacket (one armor/<name>.png already
        in the vanilla layer layout) and the hats (LOTR's own hat models, ported by armor_models.py, so the texture
        keeps LOTR's layout, tinted the colour LOTR dyes it). Returns the texture name (the item id), or None.
        """
        src = ITEM_ARMOR.get(item)
        if src is None or not self.jar.has(f"assets/lotr/armor/{src}.png"):
            return None
        img = Image.open(io.BytesIO(self.jar.read(f"assets/lotr/armor/{src}.png"))).convert("RGBA")
        if item in HAT_TINTS:
            # drawn on LOTR's own hat model (armor_models.py), so the texture keeps LOTR's layout; LOTR dyes it
            img = tint(img, HAT_TINTS[item])
        layer = 2 if slot == "legs" else 1
        dst = os.path.join(ASSETS, "textures", "models", "armor", f"{item}_layer_{layer}.png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        img.save(dst)
        return item

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
        if "LOTRBlockGate" in c.split():
            return "gate"
        if "LOTRBlockBarrel" in c.split():
            return "keg"  # LOTR's barrel; a keg so it isn't taken for vanilla's barrel
        if "LOTRBlockSaplingBase" in c.split():
            return "sapling"
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
        if kind == "keg":
            name = "Keg"
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
        if kind == "gate" and (b.get("bounds") or [])[:6] == [0, 0, 0, 1, 1, 1]:
            e["full"] = True  # LOTR's setFullBlock gates (dwarven doors): a whole block while shut
        if kind in ("shaped", "keg"):
            bb = b.get("bounds") or [0, 0, 0, 1, 1, 1]
            e["shape"] = [round(x * 16, 3) for x in bb]
            if b["material"] in ("plants", "vine", "circuits", "carpet", "portal", "web"):
                e["collision"] = False
        if kind == "door":
            e["item"] = "double_high"
        if kind in ("cube", "shaped", "keg") and FACING_CLASSES & {x.split(".")[-1] for x in b["classes"]}:
            e["facing"] = True  # furnace-like: metadata 2-5 is the way the front faces
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
        if new not in self.hidden:
            self.loot(new, kind)
        else:
            self.placed_loot(new, b, v)

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
        if kind in ("cube", "shaped", "keg") and FACING_CLASSES & {x.split(".")[-1] for x in b["classes"]}:
            # the exported front (LOTR's side 3) is south; turn it to face the block's facing
            m = self.cube(new, icons, rt) if kind == "cube" else self.shaped(new, icons, b)
            self.bs(new, {"variants": {f"facing={f}": ({"model": m, "y": y} if y else {"model": m})
                                       for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))}})
            self.item_model(new, m)
        elif kind == "gate":
            self.gate(new, side, (b.get("bounds") or [])[:6] == [0, 0, 0, 1, 1, 1])
        elif kind in ("cube", "faction_table"):
            m = self.cube(new, icons, rt)
            sheet = self.tex.connected.get(up) if kind == "cube" and len(set(icons)) == 1 else None
            self.bs(new, {"variants": {"": {"model": self.connected_model(new, sheet, up, "cube") if sheet else m}}})
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
        elif kind in ("plant", "sapling"):
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

    def connected_model(self, new, sheet, particle, shape):
        """
        A block model on the port's connected loader (lotr_port:connected, client/ConnectedModel): each face drawn
        from the sheet a quarter at a time by its neighbours, as LOTR's connected textures. shape: cube, or a gate's
        panel (full_gate: a whole block while shut).
        """
        write_json(os.path.join(ASSETS, "models", "block", new + "_connected.json"),
                   {"loader": f"{NS}:connected", "shape": shape, "render_type": CUTOUT,
                    "textures": {"sheet": sheet, "particle": particle}})
        return f"{NS}:block/{new}_connected"

    def gate(self, new, tex, full):
        """
        LOTR's gates (round hobbit doors, portcullises, dwarven doors): a 4 px panel facing its direction (dwarven
        doors a whole block while shut) on the connected model, which frames the whole gate and, open, draws only the
        frame (LOTR drew open gates without their base). The plain panel is the item, and the block for a gate with
        no texture of its own to frame.
        """
        faces = {f: {"texture": "#all"} for f in ("down", "up", "north", "south", "west", "east")}
        wall = self.bm(new, "minecraft:block/block", {"all": tex, "particle": tex},
                       {"render_type": CUTOUT, "elements": [{"from": [0, 0, 6], "to": [16, 16, 10], "faces": faces}]})
        sheet = self.tex.gate_sheet(tex)
        if sheet:
            shape = "full_gate" if full else "gate"
            self.bs(new, {"variants": {"": {"model": self.connected_model(new, sheet, tex, shape)}}})
        else:
            floor = self.bm(new + "_flat", "minecraft:block/block", {"all": tex, "particle": tex},
                            {"render_type": CUTOUT,
                             "elements": [{"from": [0, 6, 0], "to": [16, 10, 16], "faces": faces}]})
            cube = self.bm(new + "_full", "minecraft:block/cube_all", {"all": tex}) if full else None
            var = {}
            for f in ("north", "south", "east", "west", "up", "down"):
                panel = {"model": floor} if f in ("up", "down") else (
                    {"model": wall, "y": 90} if f in ("east", "west") else {"model": wall})
                var[f"facing={f},open=false"] = {"model": cube} if cube else panel
                var[f"facing={f},open=true"] = panel  # nothing to frame: open, the panel stays (and lets you by)
            self.report["gates_without_frame"].append(new)
            self.bs(new, {"variants": var})
        self.item_model(new, wall)

    def shaped(self, new, icons, b):
        if new == "flower_pot":
            # LOTR's flower pot is vanilla's shape; a plain box with the whole pot texture looked like a brown cube
            return self.bm(new, "minecraft:block/flower_pot", {"flowerpot": "minecraft:block/flower_pot",
                                                                 "dirt": "minecraft:block/dirt",
                                                                 "particle": "minecraft:block/flower_pot"},
                           {"render_type": CUTOUT})
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

    def placed_loot(self, new, b, v):
        """
        Blocks only an item places (crops, beds, placed food, plates, mugs, torches) drop what LOTR's block dropped:
        Block.getItemDropped / damageDropped / quantityDropped per metadata, exported as `drops`. The port's block
        has no age or part state yet, so the variant's own metadata is used, else the first that drops something.
        Blocks that drop nothing in LOTR (portals, placed food eaten in place) get no loot table.
        """
        drops = b.get("drops")
        if drops is None:
            self.report["placed_blocks_without_drop_export"].append(new)
            return
        hits = [d for d in drops if d.get("item")]
        d = next((x for x in hits if x["meta"] == v["meta"]), hits[0] if hits else None)
        if d is None:
            self.report["placed_blocks_dropping_nothing"].append(new)
            return
        ing = self.ingredient({"item": d["item"], "meta": d.get("damage", 0)})
        if not ing or "item" not in ing:
            self.report["placed_block_drops_unmapped"].append(f"{new}: {d['item']}#{d.get('damage', 0)}")
            return
        entry = {"type": "minecraft:item", "name": ing["item"]}
        lo, hi = d.get("min", 1), d.get("max", 1)
        if hi == 0:
            self.report["placed_blocks_dropping_nothing"].append(new)
            return
        if (lo, hi) != (1, 1):
            entry["functions"] = [{"function": "minecraft:set_count",
                                   "count": lo if lo == hi else {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        write_json(os.path.join(DATA, NS, "loot_table", "blocks", new + ".json"),
                   {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry],
                    "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
        self.report["placed_blocks_with_loot"].append(f"{new} -> {ing['item']}")

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
        # LOTR keeps smelting in a HashMap: sort so file names (name, name_2) don't change between exports
        smelting = sorted(rec["smelting"], key=lambda s: json.dumps(s, sort_keys=True))
        for s in smelting:
            ing = self.ingredient(s["input"])
            res = self.result(s["output"])
            if not ing or not res:
                self.report["unmapped_smelting"].append(s)
                continue
            if res["id"].startswith("minecraft:") and isinstance(ing, dict) and ing.get("item", "").startswith("minecraft:"):
                continue  # vanilla already smelts it (mutton)
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
                if not new or not new.startswith(NS + ":"):
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

    def large_parents(self):
        """lotr_port:item/large_2x and _3x: vanilla's handheld transforms with x and y scaled (LOTRRenderLargeItem
        scales the icon about its centre and leaves its thickness)."""
        hand = {"thirdperson_righthand": ([0, -90, 55], [0, 4.0, 0.5], [0.85, 0.85, 0.85]),
                "thirdperson_lefthand": ([0, 90, -55], [0, 4.0, 0.5], [0.85, 0.85, 0.85]),
                "firstperson_righthand": ([0, -90, 25], [1.13, 3.2, 1.13], [0.68, 0.68, 0.68]),
                "firstperson_lefthand": ([0, 90, -25], [1.13, 3.2, 1.13], [0.68, 0.68, 0.68])}
        for k in (2, 3):
            display = {p: {"rotation": r, "translation": t, "scale": [sc[0] * k, sc[1] * k, sc[2]]}
                       for p, (r, t, sc) in hand.items()}
            write_json(os.path.join(ASSETS, "models", "item", f"large_{k}x.json"),
                       {"parent": "minecraft:item/generated", "display": display})

    # ------------------------------------------------------------------ main

    def run(self):
        for sub in ("assets", "data", NS):
            shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)
        self.do_items()
        self.do_blocks()
        self.blocks_by_id = {b["id"]: b for b in self.blocks}
        self.do_recipes()
        drinks = self.drinks.run()
        self.recipe_count += drinks.get("brewing_recipes", 0)  # the keg's, converted too
        self.ore_tags()
        tabs = self.do_tabs()
        self.other_langs()
        self.write_tags()
        self.animals = Animals(self, DATA, ASSETS, os.path.join(OUT, NS, "content"), self.jar)
        animal_count = self.animals.run(load("npcs.json"))  # first: biomes list the animal types
        world_gen = World(self, load("world.json"), OUT, self.jar)
        world = world_gen.run()
        world.update(animal_count)
        world.update(Saplings(self, DATA, self.jar, world_gen.trees).run())
        factions = FactionData(self, load("factions.json"), DATA, self.jar).run()
        npc_gen = Npcs(self, load("npcs.json"), DATA, ASSETS, self.jar)
        npcs = npc_gen.run()
        npcs.update(Structures(self, EXPORT, load("world.json"), DATA, npc_gen.written).run())
        quests = load("quests.json") if os.path.exists(os.path.join(EXPORT, "quests.json")) else {}
        npcs.update(Quests(self, quests, load("npcs.json"), load("world.json"), DATA, ASSETS, self.jar, npc_gen).run())
        recruit_dir = os.path.join(ROOT, "lotr", "src", "main", "resources", "data", NS, "recruit")
        npcs.update(Colonies(self, DATA, npc_gen.written, recruit_dir).run())
        content = os.path.join(OUT, NS, "content")
        self.large_parents()
        armor_models = ArmorModels(self.jar, self.idtable, self.report).run()
        write_json(os.path.join(content, "armor_models.json"), armor_models)
        write_json(os.path.join(content, "blocks.json"), self.blocks)
        write_json(os.path.join(content, "items.json"), self.items)
        write_json(os.path.join(content, "tabs.json"), tabs)
        write_json(os.path.join(content, "summary.json"), [{"recipes": self.recipe_count, **factions, **npcs}])
        for code, entries in self.lang.items():
            write_json(os.path.join(ASSETS, "lang", code + ".json"), dict(sorted(entries.items())))
        write_json(os.path.join(ROOT, "converter", "idtable.json"), dict(sorted(self.idtable.items())))
        summary = {"blocks": len(self.blocks), "items": len(self.items), "recipes": self.recipe_count,
                   "textures": len(self.tex.copied), "languages": len(self.lang), "tabs": len(tabs), **world, **factions, **npcs,
                   **drinks}
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
