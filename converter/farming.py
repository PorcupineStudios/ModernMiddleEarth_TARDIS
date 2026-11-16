"""
LOTR's farming blocks (missing-features task 32), turned into block kinds the port runs (`content/farm/`):

- `crop`: LOTR's leek, lettuce, turnip, yam, flax and pipe-weed crops (vanilla BlockCrops, ages 0-7). LOTR's
  getIcon: ages 0-6 show icon age/2 (6 counts as 5), age 7 the last icon (flax: the block's own, the flax plant).
  Seed and produce are LOTR's func_149866_i / func_149865_P. Loot as vanilla wheat (BlockCrops.getDrops: the produce
  when ripe, and seeds from three 1-in-15 tries).
- `berry_bush`: LOTR's one berry-bush block kept berries in metadata bit 8; the port has a ripe and a bare block per
  berry, swapped by growing (random ticks) and picking (right-click, 1-4 berries: LOTRBlockBerryBush.getBerryDrops).
- `corn`: LOTR's corn stalk (LOTRBlockCorn: grows to MAX_GROW_HEIGHT 3, ages to META_GROW_END 7 before the next
  block; middle stalks grow a cob, picked by right-click). Models: the stalk, and the stalk with a cob.
- `grapevine`: red and white vines on a grapevine post (LOTRBlockGrapevine: MAX_GROWTH 7, planted with grape seeds
  on a post up to 3 above farmland; ripe grapes are picked by right-click, which leaves a bare post).

Numbers are `data/lotr_port/farming/settings.json` (`content/farm/Farming.Settings`).
"""
import json
import os

from assets import NS, write_json

CROPS = {  # LOTR class -> (seed item, produce item, last icon or None for <name>_3)
    "LOTRBlockLeekCrop": ("lotr:item.leek", "lotr:item.leek", None),
    "LOTRBlockLettuceCrop": ("lotr:item.lettuce", "lotr:item.lettuce", None),
    "LOTRBlockTurnipCrop": ("lotr:item.turnip", "lotr:item.turnip", None),
    "LOTRBlockYamCrop": ("lotr:item.yam", "lotr:item.yam", None),
    "LOTRBlockFlaxCrop": ("lotr:item.flaxSeeds", "lotr:item.flax", "lotr:flaxPlant"),
    "LOTRBlockPipeweedCrop": ("lotr:item.pipeweedSeeds", "lotr:item.pipeweedLeaf", None),
}
BERRIES = ["blueberry", "blackberry", "raspberry", "cranberry", "elderberry", "wildberry"]
BERRY_ITEMS = {b: f"lotr:item.{b}" for b in BERRIES}
GRAPES = {"LOTRBlockGrapevineRed": ("red", "lotr:item.grapeRed", "lotr:item.seedsGrapeRed"),
          "LOTRBlockGrapevineWhite": ("white", "lotr:item.grapeWhite", "lotr:item.seedsGrapeWhite")}

SETTINGS = {
    # vanilla CropBlock.getGrowthSpeed (farmland, rows) drives every chance, as LOTR's getGrowthFactor did
    "light": 9,  # LOTR: no growth below block light 9
    "berry_divisor": 50,  # LOTR: chance growthFactor * 3 / 150 per random tick
    "berry_min": 1, "berry_max": 4,
    "corn_height": 3,  # LOTRBlockCorn.MAX_GROW_HEIGHT
    "corn_divisor": 25,
    "corn_cob_divisor": 100,
    "grape_height": 3,  # posts above farmland a vine may grow on
    "grape_divisor": 25,
    "grape_min": 1, "grape_max": 3,
}


class Farming:
    def __init__(self, gen, export, assets, data):
        self.gen = gen
        self.export = export
        self.assets = assets
        self.data = data
        self.counts = {}

    def new_id(self, old, meta=0):
        n = self.gen.idtable.get(f"{old}#{meta}")
        return n.split("[")[0].split(":", 1)[1] if n else None

    def item(self, old):
        n = self.gen.idtable.get(f"{old}#0")
        return n.split("[")[0] if n else None

    def run(self):
        blocks = {b["id"]: b for b in self.gen.blocks}
        with open(os.path.join(self.export, "blocks.json"), encoding="utf-8") as f:
            exported = json.load(f)
        crops = bushes = 0
        for b in exported:
            cls = b["classes"][0].split(".")[-1]
            new = self.new_id(b["id"])
            if cls in CROPS and new in blocks:
                self.crop(new, blocks[new], b, *CROPS[cls])
                crops += 1
            elif cls == "LOTRBlockCorn" and new in blocks:
                self.corn(new, blocks[new])
            elif cls in GRAPES and new in blocks:
                self.grapevine(new, blocks[new], *GRAPES[cls])
        for berry in BERRIES:
            ripe, bare = f"{berry}_bush", f"bare_{berry}_bush"
            if ripe in blocks and bare in blocks:
                item = self.item(BERRY_ITEMS[berry])
                for bid, is_ripe in ((ripe, True), (bare, False)):
                    e = blocks[bid]
                    e["kind"] = "berry_bush"
                    e["berry"] = {"ripe": ripe, "bare": bare, "item": item, "has_berries": is_ripe}
                    e.pop("shape", None)
                bushes += 1
        write_json(os.path.join(self.data, NS, "farming", "settings.json"), SETTINGS)
        self.counts.update({"farm_crops": crops, "farm_berry_bushes": bushes})
        return self.counts

    # ------------------------------------------------------------------ crops

    def crop(self, new, e, b, seed, produce, last):
        base = b["variants"][0]["icons"][0].rsplit("_", 1)[0]  # lotr:leek_0 -> lotr:leek
        icons = [f"{base}_{i}" for i in range(3)] + [last or f"{base}_3"]
        tex = [self.gen.tex.block(i) for i in icons]
        models = [self.gen.bm(f"{new}_stage{i}", "minecraft:block/crop", {"crop": t}, {"render_type": "minecraft:cutout"})
                  for i, t in enumerate(tex)]
        variants = {}
        for age in range(8):
            stage = 3 if age == 7 else (5 if age == 6 else age) >> 1
            variants[f"age={age}"] = {"model": models[stage]}
        self.gen.bs(new, {"variants": variants})
        seed_id, produce_id = self.item(seed), self.item(produce)
        e["kind"] = "crop"
        e["crop"] = {"seed": seed_id, "produce": produce_id}
        block = f"{NS}:{new}"
        ripe = {"condition": "minecraft:block_state_property", "block": block, "properties": {"age": "7"}}
        pools = [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": produce_id, "conditions": [ripe]},
            {"type": "minecraft:item", "name": seed_id}]}]},
            {"rolls": 1, "conditions": [ripe], "entries": [{"type": "minecraft:item", "name": seed_id, "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                 "formula": "minecraft:binomial_with_bonus_count", "parameters": {"extra": 3, "probability": 0.5333}}]}]}]
        write_json(os.path.join(self.data, NS, "loot_table", "blocks", new + ".json"),
                   {"type": "minecraft:block", "functions": [{"function": "minecraft:explosion_decay"}], "pools": pools})

    # ------------------------------------------------------------------ corn

    def corn(self, new, e):
        stalk = self.gen.tex.block("lotr:cornStalk")
        cob = self.gen.tex.block("lotr:cornStalk_corn")
        plain = self.gen.bm(new, "minecraft:block/cross", {"cross": stalk}, {"render_type": "minecraft:cutout"})
        with_cob = self.gen.bm(new + "_corn", "minecraft:block/cross", {"cross": cob},
                               {"render_type": "minecraft:cutout"})
        self.gen.bs(new, {"variants": {f"age={a},corn={c}": {"model": with_cob if c == "true" else plain}
                                       for a in range(8) for c in ("false", "true")}})
        e["kind"] = "corn"
        e["corn"] = {"item": self.item("lotr:item.corn")}

    # ------------------------------------------------------------------ grapevines

    def grapevine(self, new, e, colour, grapes, seeds):
        post = self.gen.tex.block("lotr:grapevine")
        vine = self.gen.tex.block(f"lotr:grapevine{colour.title()}_vine")
        ripe = self.gen.tex.block(f"lotr:grapevine{colour.title()}_grapes")
        cross = [{"from": [0.8, 0, 8], "to": [15.2, 16, 8], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45,
                                                                          "rescale": True},
                  "shade": False, "faces": {"north": {"uv": [0, 0, 16, 16], "texture": "#vine"},
                                            "south": {"uv": [0, 0, 16, 16], "texture": "#vine"}}},
                 {"from": [8, 0, 0.8], "to": [8, 16, 15.2], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45,
                                                                          "rescale": True},
                  "shade": False, "faces": {"west": {"uv": [0, 0, 16, 16], "texture": "#vine"},
                                            "east": {"uv": [0, 0, 16, 16], "texture": "#vine"}}}]
        post_el = {"from": [6, 0, 6], "to": [10, 16, 10], "faces": {
            f: {"uv": [6, 0, 10, 16] if f not in ("up", "down") else [6, 6, 10, 10], "texture": "#post"}
            for f in ("north", "south", "west", "east", "up", "down")}}
        models = {}
        for name, tex in (("growing", vine), ("ripe", ripe)):
            models[name] = self.gen.bm(f"{new}_{name}", "minecraft:block/block",
                                       {"post": post, "vine": tex, "particle": post},
                                       {"render_type": "minecraft:cutout", "elements": [post_el] + cross})
        self.gen.bs(new, {"variants": {f"age={a}": {"model": models["ripe" if a == 7 else "growing"]}
                                       for a in range(8)}})
        e["kind"] = "grapevine"
        e["grapevine"] = {"grapes": self.item(grapes), "seeds": self.item(seeds), "post": f"{NS}:grapevine_post"}
        e.pop("shape", None)
        for it in self.gen.items:
            if f"{NS}:{it['id']}" == e["grapevine"]["seeds"]:
                it["kind"] = "grape_seeds"  # LOTRItemGrapeSeeds: planted on a grapevine post
                it["places"] = new
