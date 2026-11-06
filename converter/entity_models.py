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

from armor_models import BIPED_PARTS, Machine, Renderer, r as rnd
from assets import snake, write_json
from model_anim import AnimExporter, Unknown

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

    def part_json(self, r, names):
        return {"pivot": [rnd(p) for p in r.pivot], "rotation": [rnd(a) for a in r.rotation],
                "cubes": [] if r.hidden else r.cubes,
                "children": [{**self.part_json(c, names), **({"name": names[id(c)]} if id(c) in names else {})}
                             for c in r.children]}

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
        names = {id(r): BIPED_NAMES.get(f, snake(f)) for f, r in parts.items()}
        kids = {id(c) for r in parts.values() for c in r.children}
        roots = {}
        for field, r in parts.items():
            if id(r) in kids:
                continue
            roots[BIPED_NAMES.get(field, snake(field))] = self.part_json(r, names)
        if not any(p["cubes"] or p["children"] for p in roots.values()):
            self.report["npc_models_empty"].append(model_class)
            self.done[name] = None
            return None
        hidden = sorted(n for n in roots if any(w in n for w in HIDDEN_WORDS))
        out = {"texture": [this.fields.get("field_78090_t", 64), this.fields.get("field_78089_u", 32)],
               "render_scale": RENDER_SCALE.get(model_class.split(".")[-1].replace("LOTRModel", ""), 1.0),
               "parts": roots, "hidden": hidden}
        try:
            anim = AnimExporter(self.jar).run(cls)
        except Unknown as e:
            self.report["npc_model_anim_failed"].append(f"{model_class}: {e}")
            anim = None
        if anim:
            known = set(names.values())
            for entry in anim["set"]:
                entry[0] = BIPED_NAMES.get(entry[0], entry[0]) if entry[0] in BIPED_NAMES else snake(entry[0])
            anim["set"] = [e for e in anim["set"] if e[0] in known]
            fix_rest_names(anim["set"])
            anim["defs"], anim["set"] = share(anim["set"])
            out["anim"] = anim
            if any(e[1] == "visible" for e in anim["set"]):
                out["hidden"] = []  # LOTR's own code shows and hides parts
        write_json(os.path.join(self.assets, "npc_model", name + ".json"), out)
        self.done[name] = name
        return name


def fix_rest_names(node):
    """["rest", part, field] refer to parts by the port's names."""
    if isinstance(node, list):
        if len(node) == 3 and node[0] == "rest" and isinstance(node[1], str):
            node[1] = BIPED_NAMES.get(node[1], snake(node[1]))
        for n in node:
            fix_rest_names(n)


def share(sets):
    """Repeated subtrees (merged branches repeat a lot) go into a table: ["ref", i] points at defs[i]."""
    import json as _json
    counts = {}

    def walk(n):
        if isinstance(n, list) and n and isinstance(n[0], str) and n[0] not in ("in", "rest", "ref"):
            k = _json.dumps(n)
            counts[k] = counts.get(k, 0) + 1
            if counts[k] == 1:
                for c in n[1:]:
                    walk(c)
    for e in sets:
        walk(e[2])
    defs, index = [], {}

    def rebuild(n):
        if isinstance(n, list) and n and isinstance(n[0], str) and n[0] not in ("in", "rest", "ref"):
            k = _json.dumps(n)
            node = [n[0], *[rebuild(c) for c in n[1:]]]
            if counts.get(k, 0) > 1 and len(k) > 40:
                if k not in index:
                    index[k] = len(defs)
                    defs.append(node)
                return ["ref", index[k]]
            return node
        return n
    return defs, [[e[0], e[1], rebuild(e[2])] for e in sets]


def troll_scale(classes):
    for c in classes:
        if c.split(".")[-1] in TROLL_SCALE:
            return TROLL_SCALE[c.split(".")[-1]]
    return None
