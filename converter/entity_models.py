"""
LOTR's creature models (wargs, trolls, half-trolls, spiders, ents, the Balrog, wights, wraiths, Gollum) for the
port's NPCs. The same bytecode replay as armor_models.py runs each LOTRModel* constructor; every ModelRenderer field
becomes a part (children nested), with its boxes, texture offsets, pivot and rest angles. Output:
assets/lotr_port/npc_model/<model>.json:

    {"texture": [w, h], "render_scale": s, "parts": {"<field name>": PART, ...}, "hidden": [names]}

client/CreatureModel builds an EntityModel from it and animates parts by name the way LOTR's setRotationAngles
mostly did: legs walk (opposite pairs swing together), arms swing against the legs, the head looks, tails and
wings sway. Parts LOTR drew only sometimes (a troll's weapons, its hurt face) are listed in "hidden".

Render scale (LOTR's renderer preRenderCallback, read from the bytecode by hand): Gollum 0.85, marsh-wraith 0.9375,
Balrog 2; trolls scale per kind (LOTREntityTroll.getTrollScale): mountain 1.6, olog-hai 1.25, Mirk 1.2, snow 0.8.
"""
import os

from armor_models import BIPED_PARTS, Machine, Renderer
from assets import snake, write_json

RENDER_SCALE = {"Gollum": 0.85, "MarshWraith": 0.9375, "Balrog": 2.0}
TROLL_SCALE = {"LOTREntityMountainTroll": 1.6, "LOTREntityOlogHai": 1.25, "LOTREntityMirkTroll": 1.2,
               "LOTREntitySnowTroll": 0.8}
HIDDEN_WORDS = ("hurt", "club", "warhammer", "battleaxe", "outfit")
BIPED_NAMES = {k: v for k, v in BIPED_PARTS.items()}


def model_name(model_class):
    return snake(model_class.split(".")[-1].split("/")[-1].replace("LOTRModel", ""))


class EntityModels:
    def __init__(self, jar, assets_dir, report):
        self.jar = jar
        self.assets = assets_dir
        self.report = report
        self.done = {}

    def build(self, model_class):
        """Writes the model once; returns its name ("warg") or None if it can't be read."""
        name = model_name(model_class)
        if name in self.done:
            return self.done[name]
        cls = "lotr/client/model/" + model_class.split(".")[-1]
        m = Machine(self.jar)
        cf = m.cls(cls)
        if cf is None:
            self.done[name] = None
            return None
        inits = sorted((d for (n, d), c in cf.methods.items() if n == "<init>" and c), key=len)
        try:
            desc = inits[0]
            args = [0.0 if p == "F" else 0 for p in desc[1:desc.index(")")]]
            this = m.construct(cls, args, desc=desc)
        except Exception as e:  # noqa: BLE001 - reported; the type is skipped
            self.report["npc_models_failed"].append(f"{model_class}: {e}")
            self.done[name] = None
            return None
        parts = {k: v for k, v in this.fields.items() if isinstance(v, Renderer)}
        kids = {id(c) for r in parts.values() for c in r.children}
        roots = {}
        for field, r in parts.items():
            if id(r) in kids:
                continue
            roots[BIPED_NAMES.get(field, snake(field))] = r.json()
        if not any(p["cubes"] or p["children"] for p in roots.values()):
            self.report["npc_models_empty"].append(model_class)
            self.done[name] = None
            return None
        hidden = sorted(n for n in roots if any(w in n for w in HIDDEN_WORDS))
        write_json(os.path.join(self.assets, "npc_model", name + ".json"), {
            "texture": [this.fields.get("field_78090_t", 64), this.fields.get("field_78089_u", 32)],
            "render_scale": RENDER_SCALE.get(model_class.split(".")[-1].replace("LOTRModel", ""), 1.0),
            "parts": roots, "hidden": hidden})
        self.done[name] = name
        return name


def troll_scale(classes):
    for c in classes:
        if c.split(".")[-1] in TROLL_SCALE:
            return TROLL_SCALE[c.split(".")[-1]]
    return None
