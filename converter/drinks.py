"""
LOTR's drinks and its barrel (the port's keg), from export/drinks.json (LOTRItemMug's stats, the strength tables, the
vessels and LOTRBrewingRecipes, read in the running game):

  lotr_port/content/vessels.json        the vessels a drink can be poured into (LOTR's Vessel enum, by id)
  lotr_port/content/drink_fluids.json   the fluid each drink is in a keg or a pipe, with its colour
  data/lotr_port/drinking/settings.json  LOTR's five strengths and the keg's sizes and brew time
  textures/item/drink/<item>/<vessel>.png     each drink in each vessel, as LOTRDrinkIcons drew it
  textures/gui/keg.png, keg_brewing.png       LOTR's barrel screen and its gauge marks and bubbles

The drink items themselves (what drinking does) are extra.py's, and the keg runs the brewing recipes generate.py
writes from the exporter's machines (lotr_port:brewing MachineRecipe).
"""
import io
import json
import os

from PIL import Image

import nbt
from assets import NS, snake, write_json

# LOTRTileEntityBarrel: a drink gets one strength stronger every brewTime ticks (12000, its static final int), and
# brewing takes the three bucket slots' water.
BREW_TICKS = 12000
WATER_MB = 3000
SERVING_MB = 250  # a mug, in Create's fluid units (a bucket 1000)
# Drinks that are a fluid of another mod: a mug of water is water, a mug of milk is NeoForge's milk.
SHARED_FLUIDS = {"lotr:item.mugWater": "minecraft:water", "lotr:item.mugMilk": "minecraft:milk"}  # NeoForge's milk
MASK = (0xFF, 0x00, 0xFF)  # LOTRDrinkIcons: vessel pixels of this colour show the drink


class Drinks:
    def __init__(self, gen, export_dir, assets, data, content):
        self.gen = gen
        path = os.path.join(export_dir, "drinks.json")
        self.data = json.load(open(path, encoding="utf-8")) if os.path.exists(path) else {}
        self.assets = assets
        self.out_data = data
        self.content = content
        self.by_old = {d["id"]: d for d in self.data.get("items", [])}

    def is_drink(self, old):
        """A full LOTR mug (a drink), as opposed to an empty vessel or any other item."""
        return bool(self.by_old.get(old, {}).get("isFullMug"))

    # ------------------------------------------------------------------ icons

    def _png(self, path):
        if not self.gen.jar.has(path):
            return None
        return Image.open(io.BytesIO(self.gen.jar.read(path))).convert("RGBA")

    def liquid(self, icon):
        """The drink's liquid texture from its exported icon name ('lotr:textures/items/mugAle_mug')."""
        stem = icon.split("/")[-1]
        if stem.endswith("_mug"):
            stem = stem[:-len("_mug")]
        return self._png(f"assets/lotr/textures/items/{stem}_liquid.png")

    def models(self, new, icon):
        """Writes the drink's item model: the drink in a mug, and an override per vessel (lotr_port:vessel is the
        stack's vessel id). Returns False if LOTR had no liquid texture for it."""
        liquid = self.liquid(icon or "")
        if liquid is None:
            return False
        overrides, base = [], None
        for v in self.data.get("vessels", []):
            vessel = self._png(f"assets/lotr/textures/items/drink_{v['name']}.png")
            if vessel is None or vessel.size != liquid.size:
                self.gen.report["drink_vessels_without_texture"].append(f"{new}/{v['name']}")
                continue
            px, lq = vessel.load(), liquid.load()
            for x in range(vessel.size[0]):
                for y in range(vessel.size[1]):
                    if px[x, y][:3] == MASK:
                        px[x, y] = lq[x, y]
            rel = f"item/drink/{new}/{snake(v['name'])}"
            dst = os.path.join(self.assets, "textures", rel + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            vessel.save(dst)
            self.gen.tex.copied[rel] = f"drink_{v['name']}+{icon}"
            model = {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:{rel}"}}
            if v["id"] == 0:
                base = model
            else:
                name = f"{new}_{snake(v['name'])}"
                write_json(os.path.join(self.assets, "models", "item", name + ".json"), model)
                overrides.append({"predicate": {f"{NS}:vessel": v["id"]}, "model": f"{NS}:item/{name}"})
        if base is None:
            return False  # no mug picture: the plain icon path draws it
        write_json(os.path.join(self.assets, "models", "item", new + ".json"), {**base, "overrides": overrides})
        return True

    @staticmethod
    def colour(img):
        """The liquid's average opaque colour: the drink's fluid tint."""
        n, r, g, b = 0, 0, 0, 0
        for pr, pg, pb, pa in img.getdata():
            if pa > 0:
                n, r, g, b = n + 1, r + pr, g + pg, b + pb
        return (r // n << 16 | g // n << 8 | b // n) if n else 0xFFFFFF

    # ------------------------------------------------------------------ data

    def new_id(self, old):
        new = self.gen.idtable.get(f"{old}#0")
        if new:
            return new.split("[")[0]
        return old if old.startswith("minecraft:") else None

    def components(self, old, meta):
        """
        The item components for a LOTR drink stack with metadata meta (LOTRItemMug: meta % vesselMeta is its
        strength, meta / vesselMeta its vessel), or None: lotr_port:drink_strength for a brewable drink and
        lotr_port:vessel (extra.py's vessel id) when it isn't in a mug.
        """
        d = self.by_old.get(old)
        if not d or not d["isFullMug"] or not self.data:
            return None
        per = self.data.get("vesselMeta", 100)
        out = {}
        if d["isBrewable"]:
            out[f"{NS}:drink_strength"] = nbt.Int(min(meta % per, len(self.data["strengthNames"]) - 1))
        vessel = next((v for v in self.data["vessels"] if v["id"] == meta // per), None)
        if vessel and vessel["id"] != 0:
            out[f"{NS}:vessel"] = snake(vessel["enum"])
        return out or None

    def run(self):
        if not self.data:
            self.gen.report["drinks_not_exported"].append("export/drinks.json")
            return {}
        vessels = []
        for v in self.data["vessels"]:
            item = self.new_id(v["emptyItem"])
            if item:
                vessels.append({"id": v["id"], "name": v["name"], "item": item})
        write_json(os.path.join(self.content, "vessels.json"), vessels)

        fluids, drinks = [], 0
        names = self.data["strengthNames"]
        for d in self.data["items"]:
            if not d["isFullMug"]:
                continue
            new = self.new_id(d["id"])
            if not new:
                continue
            short = new.split(":", 1)[1]
            fluid = SHARED_FLUIDS.get(d["id"], new)
            icon = next((i for it in [self.gen.items_by_old.get(d["id"])] if it
                         for v in it["variants"] for i in v.get("icons", []) if i), "")
            liquid = self.liquid(icon)
            fluids.append({"item": new, "fluid": fluid, "color": self.colour(liquid) if liquid else 0xFFFFFF})
            if fluid == new:
                self.gen.lang["en_us"][f"fluid_type.{NS}.{short}"] = self.gen.lang["en_us"].get(f"item.{NS}.{short}",
                                                                                                short)
            drinks += 1
        write_json(os.path.join(self.content, "drink_fluids.json"), fluids)
        write_json(os.path.join(self.out_data, NS, "drinking", "settings.json"),
                   {"alcohol_strength": self.data["strengths"], "serving_mb": SERVING_MB,
                    "keg_servings": self.data["barrelCapacity"], "keg_water_mb": WATER_MB, "brew_ticks": BREW_TICKS})
        for src, name in (("barrel", "keg"), ("brewing", "keg_brewing")):
            path = f"assets/lotr/gui/barrel/{src}.png"
            if self.gen.jar.has(path):
                dst = os.path.join(self.assets, "textures", "gui", name + ".png")
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    fh.write(self.gen.jar.read(path))
        self.lang(names)
        return {"drink_fluids": drinks, "vessels": len(vessels)}

    def lang(self, names):
        """The keg's screen and the strength names, in every language LOTR had them in."""
        keys = {f"container.{NS}.keg.start_brewing": "container.lotr.barrel.startBrewing",
                f"container.{NS}.keg.stop_brewing": "container.lotr.barrel.stopBrewing",
                f"container.{NS}.keg.empty": "container.lotr.barrel.empty",
                f"container.{NS}.keg.brewing": "container.lotr.barrel.brewing",
                f"container.{NS}.keg.full": "container.lotr.barrel.full",
                f"item.{NS}.drink.explode": "item.lotr.drink.explode"}
        for i, n in enumerate(names):
            keys[f"item.{NS}.drink.strength.{i}"] = f"item.lotr.drink.{n}"
        for code in self.gen.jar.langs():
            src = self.gen.jar.lang(code)
            for new, old in keys.items():
                if old in src:
                    # LOTR's format strings were %s/%d positional in order; Minecraft's %s takes any value
                    self.gen.lang[code.lower()][new] = src[old].replace("%d", "%s")
