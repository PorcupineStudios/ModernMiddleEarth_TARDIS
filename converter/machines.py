"""
LOTR's machines and display blocks: the forges, hobbit oven, millstone, unsmeltery, kebab stand, plates, armour stand,
weapon rack, Ent jar and chests. Run after the blocks, items and recipes are generated. It

  - marks each block with the machine its LOTR class was (`machine` in content/blocks.json, read by
    content/LotrBlocks), so the block gets that machine's block entity;
  - gives the forges, oven and millstone a lit look (`lit=true`, LOTR's *_active textures);
  - converts LOTR's tile entity models (weapon rack, armour stand, kebab stand, unsmeltery) to npc_model JSON for the
    block entity renderers, and copies their textures and LOTR's chest textures;
  - copies the machines' screen textures (gui/forge.png ...);
  - lets raw iron and gold go where LOTR's forges took iron and gold ore (1.21 ores drop raw ore);
  - writes the forges' smeltable tag (LOTR's forges smelt only rock, sand and clay blocks, clay items and logs);
  - says which item places which block (`places` in content/items.json): plates, the armour stand, seeds.

The numbers (smelting times, kebab slots, Ent jar size) are LOTR's, in data/lotr_port/machine/settings.json.
"""
import glob
import json
import os

from assets import NS, write_json
from entity_models import EntityModels

# LOTR block class -> the port's machine
MACHINE_CLASSES = {
    "LOTRBlockAlloyForge": "forge", "LOTRBlockDwarvenForge": "forge", "LOTRBlockElvenForge": "forge",
    "LOTRBlockOrcForge": "forge", "LOTRBlockHobbitOven": "oven", "LOTRBlockMillstone": "millstone",
    "LOTRBlockUnsmeltery": "unsmeltery", "LOTRBlockKebabStand": "kebab_stand", "LOTRBlockPlate": "plate",
    "LOTRBlockArmorStand": "armor_stand", "LOTRBlockWeaponRack": "weapon_rack", "LOTRBlockEntJar": "ent_jar",
    "LOTRBlockChest": "chest",
}
# machines with a lit look: LOTR's texture names for the faces that change
LIT = {"forge": "front", "oven": "front", "millstone": "all"}
# block entity models LOTR drew (LOTRModel*), their texture in the jar, and the port texture
BER_MODELS = {
    "weapon_rack": ("LOTRModelWeaponRack", ["item/weaponRack"]),
    "armor_stand": ("LOTRModelArmorStand", ["item/armorStand"]),
    "kebab_stand": ("LOTRModelKebabStand", None),  # stand textures per block (item/kebab/stand*.png)
    "unsmeltery": ("LOTRModelUnsmeltery", ["item/unsmeltery/idle", "item/unsmeltery/active"]),
}
# LOTR's numbers (read from the tile entity classes' bytecode): ticks per run, slots
SETTINGS = {
    "forge_ticks": 200,          # LOTRTileEntityAlloyForgeBase.getSmeltingDuration
    "oven_ticks": 400,           # LOTRTileEntityHobbitOven (all nine at once)
    "millstone_ticks": 200,      # LOTRTileEntityMillstone.fullMillTime (while powered by redstone)
    "unsmeltery_ticks": 400,     # LOTRTileEntityUnsmeltery.getSmeltingDuration
    "kebab_ticks": 200,          # LOTRTileEntityKebabStand.cookTimeMax, per piece of meat
    "kebab_slots": 8,            # LOTRTileEntityKebabStand.MEAT_SLOTS
    "ent_jar_capacity": 6,       # LOTRTileEntityEntJar.MAX_CAPACITY
    "ent_jar_rain_chance": 2500,  # 1 in n ticks fills a rained-on jar with a serving of water
}
GUIS = {"forge": "forge", "oven": "oven", "millstone": "millstone", "unsmelter": "unsmeltery",
        "armor_stand": "armor_stand", "pouch": "pouch", "coin_exchange": "coin_exchange"}
# LOTR item classes that place a block (the block they're the drop of)
PLACING_ITEM_CLASSES = {"ItemReed", "ItemSeeds", "ItemSeedFood", "LOTRItemArmorStand", "LOTRItemGrapeSeeds",
                        "LOTRItemPlate"}
# 1.21 ores drop raw ore: LOTR's forges took the ore block
RAW_ORES = {"minecraft:iron_ore": [{"tag": "c:ores/iron"}, {"item": "minecraft:raw_iron"}],
            "minecraft:gold_ore": [{"tag": "c:ores/gold"}, {"item": "minecraft:raw_gold"}]}


class Machines:
    def __init__(self, gen, export, assets, data, jar):
        self.gen = gen
        self.export = export
        self.assets = assets
        self.data = data
        self.jar = jar
        self.counts = {}

    def new_id(self, old, meta=0):
        n = self.gen.idtable.get(f"{old}#{meta}")
        return n.split("[")[0].split(":", 1)[1] if n else None

    def run(self):
        blocks = {b["id"]: b for b in self.gen.blocks}
        with open(os.path.join(self.export, "blocks.json"), encoding="utf-8") as f:
            exported = json.load(f)
        marked = {}
        for b in exported:
            cls = b["classes"][0].split(".")[-1]
            machine = MACHINE_CLASSES.get(cls)
            if not machine:
                continue
            for v in b["variants"]:
                new = self.new_id(b["id"], v["meta"])
                if not new or new not in blocks:
                    continue
                e = blocks[new]
                e["machine"] = machine
                marked[new] = machine
                if machine in LIT:
                    self.lit(new, e, b, v, machine)
                if machine in BER_MODELS or machine == "chest":
                    e["render"] = "entity"  # drawn by the block entity renderer
                    icon = self.gen.tex.item(v.get("itemIcon")) if v.get("itemIcon") else None
                    if icon and new not in self.gen.hidden:
                        self.gen.item_model(new, layer=icon)
                if machine in ("plate", "weapon_rack", "armor_stand") and not e.get("facing"):
                    # DisplayBlock faces the placer; the model doesn't turn (plates are round, the others are
                    # drawn and turned by their block entity renderer)
                    e["facing"] = True
                    model = f"{NS}:block/{new}"
                    write_json(os.path.join(self.assets, "blockstates", new + ".json"),
                               {"variants": {f"facing={f}": {"model": model}
                                             for f in ("south", "west", "north", "east")}})
                if machine == "unsmeltery":
                    # MachineBlock adds lit; the model only gives the particle texture (the renderer draws it)
                    model = f"{NS}:block/{new}"
                    write_json(os.path.join(self.assets, "blockstates", new + ".json"),
                               {"variants": {f"facing={f},lit={lit}": {"model": model}
                                             for f in ("south", "west", "north", "east")
                                             for lit in ("false", "true")}})
                if machine == "kebab_stand":
                    e["texture"] = "stand_sand" if "Sand" in b["id"] else "stand"
                if machine == "chest":
                    e["texture"] = self.chest_texture(b)
        self.counts["machine_blocks"] = len(marked)
        self.ber_models()
        self.guis()
        write_json(os.path.join(self.data, NS, "machine", "settings.json"), SETTINGS)
        self.raw_ores()
        self.smeltable_tag()
        # LOTR brewed Ent-draughts only in LOTRBiomeGenFangorn and its subclasses
        write_json(os.path.join(self.data, NS, "tags", "worldgen", "biome", "ent_jar_brewing.json"),
                   {"values": [f"{NS}:fangorn", f"{NS}:fangorn_clearing"]})
        self.placing_items(exported)
        return self.counts

    # ------------------------------------------------------------------ lit looks

    def lit(self, new, e, b, v, machine):
        icons = list(v["icons"])
        changed = False
        for i, icon in enumerate(icons):
            if machine == "millstone" or (i == 3 and icon.endswith("_front")):
                base = icon[:-len("_front")] if icon.endswith("_front") else icon
                active = base + "_active"
                if self.jar.has(f"assets/lotr/textures/blocks/{active.split(':', 1)[1]}.png"):
                    icons[i] = active
                    changed = True
        if not changed:
            return
        tex = [self.gen.tex.block(i) for i in icons]
        m = self.gen.cube(new + "_lit", tex, None)
        normal = f"{NS}:block/{new}"
        var = {}
        for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270)):
            for lit, model in (("false", normal), ("true", m)):
                d = {"model": model}
                if y:
                    d["y"] = y
                var[f"facing={f},lit={lit}"] = d
        write_json(os.path.join(self.assets, "blockstates", new + ".json"), {"variants": var})
        e["lit"] = True

    # ------------------------------------------------------------------ block entity models and textures

    def ber_models(self):
        models = EntityModels(self.jar, self.assets, self.gen.report)
        for machine, (cls, textures) in BER_MODELS.items():
            name = models.build("lotr.client.model." + cls)
            if not name:
                self.gen.report["machine_models_failed"].append(cls)
                continue
            models.models[name].pop("anim", None)  # drawn still; the renderers move parts themselves
        models.write_all()
        copied = 0
        for path in ("item/weaponRack", "item/armorStand", "item/unsmeltery/idle", "item/unsmeltery/active"):
            copied += self.copy(f"assets/lotr/{path}.png", "entity/block/" + path.split("/", 1)[1].replace(
                "weaponRack", "weapon_rack").replace("armorStand", "armor_stand"))
        for n in self.jar.names:
            for prefix, dst in (("assets/lotr/item/kebab/", "entity/block/kebab/"),
                                ("assets/lotr/item/chest/", "entity/block/chest/")):
                if n.startswith(prefix) and n.endswith(".png"):
                    stem = n[len(prefix):-4]
                    copied += self.copy(n, dst + snake_lower(stem))
        self.counts["machine_textures"] = copied

    def chest_texture(self, b):
        """LOTR's chest texture name (LOTRBlockChest.getChestTextureName), from the block's own id."""
        old = b["id"].split(".", 1)[1]
        name = old[len("chest"):] if old.startswith("chest") else "Stone"
        name = name[0].lower() + name[1:]
        if not self.jar.has(f"assets/lotr/item/chest/{name}.png"):
            self.gen.report["chest_textures_missing"].append(f"{old}: {name}")
            return "stone"
        return snake_lower(name)

    def copy(self, src, dst_rel):
        if not self.jar.has(src):
            self.gen.report["machine_textures_missing"].append(src)
            return 0
        dst = os.path.join(self.assets, "textures", dst_rel + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "wb") as f:
            f.write(self.jar.read(src))
        return 1

    def guis(self):
        for src, dst in GUIS.items():
            self.copy(f"assets/lotr/gui/{src}.png", f"gui/{dst}")

    # ------------------------------------------------------------------ recipes

    def raw_ores(self):
        n = 0
        for path in glob.glob(os.path.join(self.data, NS, "recipe", "alloy_forge", "*.json")):
            with open(path, encoding="utf-8") as f:
                r = json.load(f)
            ings = []
            changed = False
            for ing in r["ingredients"]:
                raw = RAW_ORES.get(ing.get("item")) if isinstance(ing, dict) else None
                ings.append(raw or ing)
                changed |= raw is not None
            if changed:
                r["ingredients"] = ings
                write_json(path, r)
                n += 1
        self.counts["forge_recipes_taking_raw_ore"] = n

    def smeltable_tag(self):
        """LOTRTileEntityAlloyForgeBase.getSmeltingResult: blocks of rock, sand or clay, clay balls, clay mugs and
        plates, and logs smelt in a forge (as in a furnace); everything else needs the forge's own recipes."""
        values = ["#c:ores", "#c:raw_materials", "#minecraft:logs", "#minecraft:sand", "#c:cobblestones",
                  "#c:stones", "#c:sandstone/blocks", "minecraft:clay_ball", "minecraft:clay", "minecraft:netherrack",
                  "minecraft:stone_bricks", "minecraft:wet_sponge"]
        for old in ("lotr:item.redClayBall", "lotr:item.clayMug", "lotr:item.clayPlate", "lotr:item.ceramicPlate",
                    "lotr:tile.redClay"):
            new = self.gen.idtable.get(f"{old}#0")
            if new:
                values.append(new.split("[")[0])
        # LOTR's own rock: every block whose old material was rock
        with open(os.path.join(self.export, "blocks.json"), encoding="utf-8") as f:
            for b in json.load(f):
                if b.get("material") in ("rock", "sand", "clay") and b["classes"][0].split(".")[-1] in (
                        "LOTRBlockRock", "LOTRBlockSand", "LOTRBlockOre", "LOTRBlockOreMordorVariant",
                        "LOTRBlockRedClay", "LOTRBlockWhiteSand", "LOTRBlockMordorDirt", "LOTRBlockOreGem"):
                    for v in b["variants"]:
                        new = self.gen.idtable.get(f"{b['id']}#{v['meta']}")
                        if new and new.split(":", 1)[1] not in self.gen.hidden:
                            values.append(new.split("[")[0])
        values = sorted(set(values), key=lambda x: (not x.startswith("#"), x))
        write_json(os.path.join(self.data, NS, "tags", "item", "forge_smeltable.json"),
                   {"replace": False, "values": [{"id": v, "required": False} if v.startswith("#c:") else v
                                                 for v in values]})

    # ------------------------------------------------------------------ items that place blocks

    def placing_items(self, exported):
        items = {i["id"]: i for i in self.gen.items}
        n = 0
        with open(os.path.join(self.export, "items.json"), encoding="utf-8") as f:
            classes = {i["id"]: {c.split(".")[-1] for c in i["classes"]} for i in json.load(f)}
        for b in exported:
            drops = [d for d in b.get("drops") or [] if d.get("item")]
            if not drops:
                continue
            new = self.new_id(b["id"])
            if not new or new not in self.gen.hidden:
                continue
            item_old = drops[0]["item"]
            if item_old == b["id"] or not (classes.get(item_old, set()) & PLACING_ITEM_CLASSES):
                continue
            item_new = self.new_id(item_old, drops[0].get("damage", 0))
            it = items.get(item_new)
            if it is None or "places" in it:
                continue
            it["places"] = new
            n += 1
        self.counts["items_placing_blocks"] = n


def snake_lower(s):
    out = []
    for i, ch in enumerate(s):
        if ch.isupper() and i and not s[i - 1].isupper():
            out.append("_")
        out.append(ch.lower())
    return "".join(out)
