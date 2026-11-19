"""
More Create and cooking recipes for LOTR content, each copying the pattern Create or vanilla use for their own kin.

Runs on top of the generated content (no LOTR jar needed), after woodsets.py (it needs the stripped woods); rerun
alone with ``python create_extra.py``. It owns `recipe/create/cutting/`, `recipe/create/mixing/keg_*` and
`recipe/own/cooking/` and rewrites them on every run, so it is idempotent.

- Sawing (Create's mechanical saw, `create:cutting`, like Create's recipes for other mods' woods): a LOTR log, beam
  or bark -> its stripped form; a stripped one -> 6 planks (50 ticks).
- Cooking: every LOTR furnace food also cooks in a smoker (half the time) and on a campfire (600 ticks), as vanilla
  foods do; so Create's fan smoking (which reads smoker recipes) cooks them too.
- Keg drinks in Create's mixer: the keg's 6 ingredients + water -> the drink as a fluid (a keg brews 16 servings from
  3000 mB of water; a basin tank holds 1000 mB, so the mixer makes 1000 mB = 4 servings from 1000 mB of water). A
  spout pours it into any mug or goblet, and pipes fill and drain kegs. Replaces create_compat.py's brewing recipes,
  which made one drink item from 1000 mB of water.
"""
import json
import os
import shutil
import sys

from renames import NS, OUT

CONDITION = [{"type": "neoforge:mod_loaded", "modid": "create"}]
SMOKER_FACTOR = 0.5
CAMPFIRE_TICKS = 600
SAW_TICKS = 50
MIX_WATER_MB = 1000
MIX_DRINK_MB = 1000


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


class CreateExtra:
    def __init__(self, out=OUT):
        self.out = out
        self.recipes = os.path.join(out, "data", NS, "recipe")
        self.content = os.path.join(out, NS, "content")
        self.counts = {"cutting": 0, "smoking": 0, "campfire": 0, "keg_mixing": 0}

    def fresh(self, rel):
        p = os.path.join(self.recipes, rel)
        if os.path.isdir(p):
            shutil.rmtree(p)
        return p

    def run(self):
        self.cutting()
        self.cooking()
        self.keg_mixing()
        return self.counts

    def cutting(self):
        folder = self.fresh("create/cutting")
        ids = {b["id"] for b in load(os.path.join(self.content, "blocks.json"))}
        for b in sorted(ids):
            if not b.endswith("_planks") or b.startswith("stripped_"):
                continue
            base = b[: -len("_wood_planks")] if b.endswith("_wood_planks") else b[: -len("_planks")]
            for raw, stripped in ((f"{base}_wood", f"stripped_{base}_wood"),
                                  (f"{base}_wood_beam", f"stripped_{base}_wood_beam"),
                                  (f"{base}_bark", f"stripped_{base}_bark")):
                if raw in ids and stripped in ids:
                    self.saw(folder, raw, stripped, 1)
                    self.saw(folder, stripped, b, 6)

    def saw(self, folder, src, dst, count):
        res = {"id": f"{NS}:{dst}"}
        if count != 1:
            res["count"] = count
        write(os.path.join(folder, src + ".json"), {
            "neoforge:conditions": CONDITION, "type": "create:cutting",
            "ingredients": [{"item": f"{NS}:{src}"}], "processing_time": SAW_TICKS, "results": [res]})
        self.counts["cutting"] += 1

    def cooking(self):
        folder = self.fresh("own/cooking")
        foods = {f"{NS}:{i['id']}" for i in load(os.path.join(self.content, "items.json")) if i.get("food")}
        smelting = os.path.join(self.recipes, "smelting")
        for f in sorted(os.listdir(smelting)):
            r = load(os.path.join(smelting, f))
            if r.get("result", {}).get("id") not in foods:
                continue
            base = {k: v for k, v in r.items() if k not in ("type", "cookingtime")}
            base["category"] = "food"
            write(os.path.join(folder, f"{f[:-5]}_from_smoking.json"), {
                "type": "minecraft:smoking", **base,
                "cookingtime": int(r.get("cookingtime", 200) * SMOKER_FACTOR)})
            write(os.path.join(folder, f"{f[:-5]}_from_campfire_cooking.json"), {
                "type": "minecraft:campfire_cooking", **base, "cookingtime": CAMPFIRE_TICKS})
            self.counts["smoking"] += 1
            self.counts["campfire"] += 1

    def keg_mixing(self):
        mixing = os.path.join(self.recipes, "create", "mixing")
        os.makedirs(mixing, exist_ok=True)
        for f in os.listdir(mixing):
            if f.startswith(("brewing_", "keg_")):
                os.remove(os.path.join(mixing, f))
        fluids = {d["item"]: d["fluid"] for d in load(os.path.join(self.content, "drink_fluids.json"))}
        brewing = os.path.join(self.recipes, "brewing")
        for f in sorted(os.listdir(brewing)):
            r = load(os.path.join(brewing, f))
            fluid = fluids.get(r["result"]["id"])
            if not fluid:
                continue
            write(os.path.join(mixing, f"keg_{f}"), {
                "neoforge:conditions": CONDITION, "type": "create:mixing",
                "ingredients": r["ingredients"] + [
                    {"type": "neoforge:single", "fluid": "minecraft:water", "amount": MIX_WATER_MB}],
                "results": [{"id": fluid, "amount": MIX_DRINK_MB}]})
            self.counts["keg_mixing"] += 1


def run(out=OUT):
    return CreateExtra(out).run()


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
