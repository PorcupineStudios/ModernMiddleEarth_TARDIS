"""
LOTR's creature models (wargs, trolls, half-trolls, spiders, ents, the Balrog, wights, wraiths, Gollum) for the
port's NPCs. The same bytecode replay as armor_models.py runs each LOTRModel* constructor; every ModelRenderer field
becomes a part (children nested), with its boxes, texture offsets, pivot and rest angles. Output:
assets/lotr_port/npc_model/<model>.json:

    {"texture": [w, h], "render_scale": s, "parts": {"<field name>": PART, ...}, "hidden": [names]}

client/CreatureModel builds an EntityModel from it and animates parts by name the way LOTR's setRotationAngles
mostly did: legs walk (opposite pairs swing together), arms swing against the legs, the head looks, tails and
wings sway. Parts LOTR drew only sometimes (a troll's weapons, its hurt face) are listed in "hidden".

The model's animation and drawing are LOTR's own (model_anim.py: setRotationAngles and render, with every draw's GL
transforms), and "pre" holds its renderer's preRenderCallback transforms (troll and spider size, the Balrog's 2x
and charge lean, wraith fading). "inputs" lists the entity values they read, which LotrNpc's behaviours provide.
"""
import os

from armor_models import BIPED_PARTS, Machine, Renderer, r as rnd
from assets import NS, snake, write_json
from model_anim import AnimExporter, Unknown

# LOTR's troll renderers (LOTRRenderTroll and subclasses): which outfit textures bindTrollOutfitTexture uses and
# which LOTRModelTroll weapon method renderTrollWeapon calls (read from their bytecode).
TROLL_RENDERERS = {
    "LOTRRenderTroll": {"outfit": ["troll/outfit_0", "troll/outfit_1", "troll/outfit_2"], "weapon": "renderWoodenClub"},
    "LOTRRenderOlogHai": {"outfit": ["troll/ologHai_armor/"], "weapon": "renderWarhammer"},
    "LOTRRenderMirkTroll": {"outfit": ["troll/mirkTroll_armor/"], "weapon": "renderBattleaxe"},
    "LOTRRenderSnowTroll": {"weapon": "renderWoodenClubWithSpikes"},
    "LOTRRenderUtumnoTroll": {"weapon": "renderWoodenClubWithSpikes"},
    "LOTRRenderMountainTroll": {"weapon": "renderWoodenClubWithSpikes", "rock_when": "isThrowingRocks"},
    "LOTRRenderMountainTrollChieftain": {"weapon": "renderWoodenClubWithSpikes", "rock_when": "isThrowingRocks"},
}
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
        self.models = {}
        self.names_by_model = {}
        self.model_class_of = {}

    def part_json(self, r, names):
        return {"pivot": [rnd(p) for p in r.pivot], "rotation": [rnd(a) for a in r.rotation],
                "cubes": [] if r.hidden else r.cubes,
                "children": [{**self.part_json(c, names), **({"name": names[id(c)]} if id(c) in names else {})}
                             for c in r.children]}

    def build(self, model_class, render_class=None, ctor=None, suffix=""):
        """Writes the model once; returns its name ("warg") or None if it can't be read. render_class is the
        LOTR renderer of the first NPC drawn with it, whose preRenderCallback transforms go in "pre"."""
        name = model_name(model_class) + suffix
        if name in self.done:
            self.add_renderer(name, render_class)
            return self.done[name]
        cls = "lotr/client/model/" + model_class.split(".")[-1]
        m = Machine(self.jar)
        cf = m.cls(cls)
        if cf is None:
            self.done[name] = None
            return None
        inits = sorted((d for (n, d), c in cf.methods.items() if n == "<init>" and c), key=len)
        try:
            if ctor:
                desc, args = ctor
            else:
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
               "render_scale": 1.0, "parts": roots, "hidden": hidden, "renderers": []}
        exporter = AnimExporter(self.jar, constants(this.fields))
        self.names_by_model[name] = names
        self.model_class_of[name] = cls
        try:
            anim = exporter.run(cls)
        except Unknown as e:
            self.report["npc_model_anim_failed"].append(f"{model_class}: {e}")
            anim = None
        if anim:
            known = set(names.values())
            for entry in anim["set"]:
                entry[0] = BIPED_NAMES.get(entry[0], entry[0]) if entry[0] in BIPED_NAMES else snake(entry[0])
            anim["set"] = [e for e in anim["set"] if e[0] in known]
            fix_rest_names(anim["set"])
            for d in anim.get("draws", []):
                d["part"] = BIPED_NAMES.get(d["part"], d["part"]) if d["part"] in BIPED_NAMES else snake(d["part"])
            anim["draws"] = [d for d in anim.get("draws", []) if d["part"] in known]
            if not anim["draws"]:
                anim.pop("draws")
            fix_rest_names(anim.get("draws", []))
            out["anim"] = anim
            if any(e[1] == "visible" for e in anim["set"]) or "draws" in anim:
                out["hidden"] = []  # LOTR's own code chooses which parts show
        self.models[name] = out
        self.done[name] = name
        self.add_renderer(name, render_class)
        return name

    def add_renderer(self, name, render_class):
        """A LOTR renderer drawing this model: its preRenderCallback transforms ("pre") and extra passes
        ("layers": a troll's outfit or armour, its weapon). NPC types point at it by index (state "renderer")."""
        out = self.models.get(name)
        if out is None or not render_class:
            return
        simple = render_class.split(".")[-1]
        if any(r["name"] == simple for r in out["renderers"]):
            return
        entry = {"name": simple}
        exporter = AnimExporter(self.jar)
        try:
            pre = exporter.pre_render(render_class.replace(".", "/"))
            if pre:
                entry["pre"] = pre
        except Unknown as e:
            self.report["npc_renderer_pre_failed"].append(f"{render_class}: {e}")
        layers = self.layers(simple, name)
        if layers:
            entry["layers"] = layers
        out["renderers"].append(entry)

    def renderer_index(self, name, render_class):
        out = self.models.get(name)
        simple = (render_class or "").split(".")[-1]
        for i, r in enumerate(out["renderers"] if out else []):
            if r["name"] == simple:
                return i
        return 0

    def layers(self, renderer, model):
        """The troll renderers' extra passes (LOTRRenderTroll and kin, read from their bytecode): outfit models with
        an outfit or armour texture, and the weapon drawn on the right arm with the weapons texture."""
        if model != "troll":
            return []
        spec = TROLL_RENDERERS.get(renderer)
        if not spec:
            return []
        out = []
        self.copy_textures(["troll/stone"])  # LOTREntityStoneTroll: a troll turned to stone
        outfit = spec.get("outfit")
        if outfit:
            for suffix, ctor in (("_shirt", ("(FI)V", [1.0, 0])), ("_trousers", ("(FI)V", [0.75, 1]))):
                sub = self.build("lotr.client.model.LOTRModelTroll", None, ctor, suffix)
                if sub:
                    out.append({"model": sub, "textures": self.copy_textures(outfit), "pick": "outfitSkin"})
        weapon = spec.get("weapon")
        if weapon:
            draws = AnimExporter(self.jar).model_method(self.model_class_of["troll"], weapon) or []
            names = self.names_by_model["troll"]
            for d in draws:
                d["part"] = snake(d["part"])
                for op in d["ops"]:
                    if op and op[0] == "attach":
                        op[1] = snake(op[1])
            known = set(names.values())
            draws = [d for d in draws if d["part"] in known]
            entry = {"draws": draws, "textures": self.copy_textures(["troll/weapons"])}
            if spec.get("rock_when"):
                entry["hide_when"] = spec["rock_when"]
                out.append({"rock_when": spec["rock_when"], "attach": "right_arm",
                            "translate": [0.375, 1.5, 0.0], "rotate": [45.0, 0.0, 1.0, 0.0]})
            out.append(entry)
        return out

    def copy_textures(self, paths):
        """lotr mob textures ("troll/outfit_0", or a folder "troll/ologHai_armor/") -> port texture ids."""
        files = []
        for p in paths:
            if p.endswith("/"):
                files += sorted(n[len("assets/lotr/mob/"):-4] for n in self.jar.names
                                if n.startswith("assets/lotr/mob/" + p) and n.endswith(".png"))
            else:
                files.append(p)
        out = []
        for f in files:
            src = f"assets/lotr/mob/{f}.png"
            if not self.jar.has(src):
                continue
            new = "entity/" + "/".join(snake(x) for x in f.split("/"))
            dst = os.path.join(self.assets, "textures", new + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as fh:
                fh.write(self.jar.read(src))
            out.append(f"{NS}:textures/{new}.png")
        return out

    def write_all(self):
        """Writes every model, sharing repeated subtrees across its animation, draws, renderers and layers."""
        for name, out in self.models.items():
            anim = out.get("anim")
            trees = []
            if anim:
                fix_rest_names(anim.get("draws", []))
                trees += [e[2] for e in anim["set"]]
                for d in anim.get("draws", []):
                    trees += [d["cond"], d["ops"], d["color"]]
            for r in out["renderers"]:
                if "pre" in r:
                    fix_rest_names(r["pre"])
                    trees += [r["pre"]["ops"], r["pre"]["color"]]
                for layer in r.get("layers", []):
                    for d in layer.get("draws", []):
                        trees += [d["cond"], d["ops"], d["color"]]
            defs, shared = share(trees)
            it = iter(shared)
            if anim:
                for e in anim["set"]:
                    e[2] = next(it)
                for d in anim.get("draws", []):
                    d["cond"], d["ops"], d["color"] = next(it), next(it), next(it)
            for r in out["renderers"]:
                if "pre" in r:
                    r["pre"]["ops"], r["pre"]["color"] = next(it), next(it)
                for layer in r.get("layers", []):
                    for d in layer.get("draws", []):
                        d["cond"], d["ops"], d["color"] = next(it), next(it), next(it)
            out["defs"] = defs
            out["inputs"] = sorted(set(entity_inputs([anim, out["renderers"], defs])))
            write_json(os.path.join(self.assets, "npc_model", name + ".json"), out)
            if any(e[1] == "visible" for e in anim["set"]) or "draws" in anim:
                out["hidden"] = []  # LOTR's own code chooses which parts show


def fix_rest_names(node):
    """["rest", part, field] refer to parts by the port's names."""
    if isinstance(node, list):
        if len(node) == 3 and node[0] == "rest" and isinstance(node[1], str):
            node[1] = BIPED_NAMES.get(node[1], snake(node[1]))
        for n in node:
            fix_rest_names(n)


def constants(fields):
    """The model's own non-part fields after construction (a troll outfit model's isOutiftModel...)."""
    return {k: (1.0 if v is True else 0.0 if v is False else float(v)) for k, v in fields.items()
            if isinstance(v, (int, float, bool)) and not k.startswith("field_7809")}


def entity_inputs(node):
    """The entity values an animation reads (["in", "entity.<name>"])."""
    if isinstance(node, dict):
        for v in node.values():
            yield from entity_inputs(v)
    elif isinstance(node, list):
        if len(node) == 2 and node[0] == "in" and isinstance(node[1], str) and node[1].startswith("entity."):
            yield node[1][7:]
        for n in node:
            yield from entity_inputs(n)


def share(trees):
    """Repeated subtrees (merged branches repeat a lot) go into a table: ["ref", i] points at defs[i]."""
    import json as _json
    counts = {}

    def walk(n):
        if isinstance(n, list) and n and not isinstance(n[0], str):
            for c in n:
                walk(c)
            return
        if isinstance(n, list) and n and isinstance(n[0], str) and n[0] not in ("in", "rest", "ref"):
            k = _json.dumps(n)
            counts[k] = counts.get(k, 0) + 1
            if counts[k] == 1:
                for c in n[1:]:
                    walk(c)
    for t in trees:
        walk(t)
    defs, index = [], {}

    def rebuild(n):
        if isinstance(n, list) and n and not isinstance(n[0], str):
            return [rebuild(c) for c in n]
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
    return defs, [rebuild(t) for t in trees]
