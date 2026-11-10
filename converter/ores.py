"""
LOTR's ores that drop an item (missing-features task 37): sulfur, saltpeter, naurite, quendite, gulduril,
glowstone ore and the eight gems. Read from LOTRBlockOre / OreGem / OreMordorVariant:
the item and its count (`drops`, exported), fortune as vanilla ores (quantityDroppedWithBonus), and the experience
LOTR's dropBlockAsItemWithChance gave (MathHelper.getRandomIntegerInRange). Silk touch gives the ore block.
Blocks become kind `ore` with `xp` [min, max] (`DropExperienceBlock`).
"""
import json
import os

from assets import NS, write_json

XP = {  # LOTR block id -> experience range
    "lotr:tile.oreNaurite": (0, 2), "lotr:tile.oreQuendite": (2, 5), "lotr:tile.oreGlowstone": (2, 4),
    "lotr:tile.oreGulduril": (2, 5), "lotr:tile.oreSulfur": (0, 2), "lotr:tile.oreSaltpeter": (0, 2),
    "lotr:tile.oreGem": (0, 2),
}


class Ores:
    def __init__(self, gen, export, data):
        self.gen = gen
        self.export = export
        self.data = data

    def run(self):
        blocks = {b["id"]: b for b in self.gen.blocks}
        n = 0
        with open(os.path.join(self.export, "blocks.json"), encoding="utf-8") as f:
            exported = json.load(f)
        for b in exported:
            if b["id"] not in XP:
                continue
            for v in b["variants"]:
                new = self.gen.idtable.get(f"{b['id']}#{v['meta']}")
                if not new:
                    continue
                new = new.split("[")[0].split(":", 1)[1]
                drop = next((d for d in b.get("drops") or [] if d["meta"] == v["meta"]), None)
                item = drop and self.gen.idtable.get(f"{drop['item']}#{drop.get('damage', 0)}")
                if drop and drop["item"].startswith("minecraft:"):
                    item = drop["item"]
                if new not in blocks or not item or item.split("[")[0] == f"{NS}:{new}":
                    continue
                item = item.split("[")[0]
                e = blocks[new]
                e["kind"] = "ore"
                e["xp"] = list(XP[b["id"]])
                count = drop["min"] if drop["min"] == drop["max"] else {
                    "type": "minecraft:uniform", "min": drop["min"], "max": drop["max"]}
                silk = {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
                    {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}
                write_json(os.path.join(self.data, NS, "loot_table", "blocks", new + ".json"), {
                    "type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{
                        "type": "minecraft:alternatives", "children": [
                            {"type": "minecraft:item", "name": f"{NS}:{new}", "conditions": [silk]},
                            {"type": "minecraft:item", "name": item, "functions": [
                                {"function": "minecraft:set_count", "count": count},
                                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                                 "formula": "minecraft:ore_drops"},
                                {"function": "minecraft:explosion_decay"}]}]}]}]})
                n += 1
        # LOTRItemPouch: small, medium and large hold 9, 18 and 27 (getCapacityForMeta: (meta + 1) * 9)
        pouches = 0
        for meta in range(3):
            pid = self.gen.idtable.get(f"lotr:item.pouch#{meta}")
            for it in self.gen.items:
                if pid and f"{NS}:{it['id']}" == pid.split("[")[0]:
                    it["kind"] = "pouch"
                    it["slots"] = (meta + 1) * 9
                    pouches += 1
        for code in self.gen.jar.langs():  # LOTR's "%d of %d slots in use", in every language it had
            src = self.gen.jar.lang(code)
            if "item.lotr.pouch.slots" in src:
                self.gen.lang[code.lower()][f"item.{NS}.pouch.slots"] = src["item.lotr.pouch.slots"].replace("%d", "%s")
        return {"item_ores": n, "pouches": pouches}
