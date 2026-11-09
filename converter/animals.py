"""
LOTR's animals for the port: who lives in each biome, and the LOTR animals modern Minecraft lacks.

  lotr_port/content/animals.json             the animal types the mod registers (size, stats, kind, model, skins)
  data/lotr_port/loot_table/entities/<id>    their drops (LOTR's dropFewItems, as loot tables)
  assets/lotr_port/textures/entity/animal/   skins, copied from the jar
  biome "spawners" (world.py asks spawners())  LOTR's own per-biome lists, from the exporter's "animals"

Animals Minecraft now has itself become the vanilla mob (LOTR's horse, camel, rabbit and fish; sheep, pigs...).
Models: each LOTR model class is replayed on armor_models' stack machine (constructor, then setRotationAngles at a few
inputs), giving the boxes, the resting pose, and per part how far it swings with walking and turns with the head.
Not yet: LOTR's birds, butterflies, midges, swans, crocodiles, scorpions (flying, swimming and hostile mobs).
"""
import math
import os
import zipfile

from armor_models import Machine, Obj, Renderer, Unsupported
from assets import NS, snake, write_json
from creatures import Creatures

# Animals modern Minecraft has: LOTR's spawn entry -> the vanilla mob
VANILLA = {"Sheep": "minecraft:sheep", "Pig": "minecraft:pig", "Chicken": "minecraft:chicken", "Cow": "minecraft:cow",
           "Wolf": "minecraft:wolf", "Bat": "minecraft:bat", "Squid": "minecraft:squid",
           "lotr.Horse": "minecraft:horse", "lotr.Camel": "minecraft:camel", "lotr.Rabbit": "minecraft:rabbit",
           "lotr.Fish": "minecraft:cod"}
# Spawn list -> 1.21 spawn category (LOTR's own ambient list holds rabbits and dik-diks, so creatures here)
CATEGORY = {"creature": "creature", "lotr_ambient": "creature", "water": "water_ambient", "cave": "ambient",
            "monster": "monster"}

COOKED = {"minecraft:porkchop": "minecraft:cooked_porkchop", "minecraft:beef": "minecraft:cooked_beef"}
HIDE = ("minecraft:leather", 0, 2)  # LOTR's usual nextInt(3) + nextInt(1 + looting)
DEER_MEAT = ("lotr:item.deerRaw", 0, 2, "lotr:item.deerCooked")

# LOTR's animals, read from their entity classes (size from setSize, food from isBreedingItem, drops from
# dropFewItems: (item, base, extra up to, cooked when burning)). kind: animal (walks and breeds), mount (rideable,
# tamed like a horse), pony (a modern vanilla horse, smaller). The zebra keeps 1.7.10's horse model (its texture).
SPECS = {
    "lotr.Boar": dict(id="wild_boar", kind="mount", model="LOTRModelBoar", size=(0.9, 0.8), food="minecraft:carrot",
                      skins="boar/boar.png", saddle="boar/saddle.png", fights=True,
                      drops=[("minecraft:porkchop", 1, 2, "minecraft:cooked_porkchop")]),
    "lotr.Deer": dict(id="deer", kind="animal", model="LOTRModelDeer", size=(0.8, 1.0), food="minecraft:wheat",
                      skins="deer/", male=["antlers"], drops=[HIDE, DEER_MEAT]),
    "lotr.ShirePony": dict(id="shire_pony", kind="pony", size=(1.12, 1.28), scale=0.8),
    "lotr.Zebra": dict(id="zebra", kind="mount", model="net/minecraft/client/model/ModelHorse", size=(1.4, 1.6),
                       skins="zebra.png", food="#minecraft:horse_food",
                       drops=[("minecraft:leather", 0, 1), ("lotr:item.zebraRaw", 1, 1, "lotr:item.zebraCooked")]),
    "lotr.Aurochs": dict(id="aurochs", kind="animal", model="LOTRModelAurochs", size=(1.5, 1.7), food="minecraft:wheat",
                         skins="aurochs/", fights=True, adult=["horns"],
                         drops=[("minecraft:leather", 2, 2), ("minecraft:beef", 2, 2, "minecraft:cooked_beef")]),
    "lotr.KineAraw": dict(id="kine_of_araw", kind="animal", model="LOTRModelAurochs", size=(1.725, 1.955), scale=1.15,
                          food="minecraft:wheat", skins="kineAraw/", fights=True, adult=["horns"],
                          drops=[("minecraft:leather", 2, 2), ("minecraft:beef", 2, 2, "minecraft:cooked_beef")]),
    "lotr.Bear": dict(id="bear", kind="animal", model="LOTRModelBear", size=(1.6, 1.8), scale=1.2, food="minecraft:cod",
                      skins="bear/", fights=True, drops=[("lotr:item.wargFur", 1, 2)]),
    "lotr.Elk": dict(id="elk", kind="mount", model="LOTRModelElk", size=(1.6, 1.8), food="minecraft:wheat",
                     skins="elk/elk/", saddle="elk/saddle.png", fights=True, drops=[HIDE, DEER_MEAT]),
    "lotr.Lion": dict(id="lion", kind="animal", model="LOTRModelLion", size=(1.4, 1.6), food="minecraft:cod",
                      skins="lion/lion.png", fights=True,
                      drops=[("lotr:item.lionFur", 2, 2), ("lotr:item.lionRaw", 1, 1, "lotr:item.lionCooked")]),
    "lotr.Lioness": dict(id="lioness", kind="animal", model="LOTRModelLion", size=(1.4, 1.6), food="minecraft:cod",
                         skins="lion/lioness.png", fights=True,
                         drops=[("lotr:item.lionFur", 2, 2), ("lotr:item.lionRaw", 1, 1, "lotr:item.lionCooked")]),
    "lotr.Giraffe": dict(id="giraffe", kind="mount", model="LOTRModelGiraffe", size=(1.7, 4.0), food="#minecraft:leaves",
                         skins="giraffe/giraffe.png", saddle="giraffe/saddle.png", drops=[HIDE]),
    "lotr.Rhino": dict(id="rhino", kind="mount", model="LOTRModelRhino", size=(1.7, 1.9), food="minecraft:wheat",
                       skins="rhino/rhino.png", saddle="rhino/saddle.png", fights=True,
                       drops=[("lotr:item.rhinoHorn", 0, 1), ("lotr:item.rhinoRaw", 0, 2, "lotr:item.rhinoCooked")]),
    "lotr.Gemsbok": dict(id="gemsbok", kind="animal", model="LOTRModelGemsbok", size=(0.9, 1.4), food="minecraft:wheat",
                         skins="gemsbok.png", drops=[("lotr:item.gemsbokHide", 1, 3), ("lotr:item.gemsbokHorn", 0, 1)]),
    "lotr.WhiteOryx": dict(id="white_oryx", kind="animal", model="LOTRModelGemsbok", size=(0.81, 1.26), scale=0.9,
                           food="minecraft:wheat", skins="whiteOryx/", drops=[HIDE, DEER_MEAT]),
    "lotr.Flamingo": dict(id="flamingo", kind="animal", model="LOTRModelFlamingo", size=(0.6, 1.8), food="minecraft:cod",
                          skins="flamingo/flamingo.png", drops=[("minecraft:feather", 0, 2)]),
    "lotr.DikDik": dict(id="dik_dik", kind="animal", model="LOTRModelDikDik", size=(0.6, 1.0), food="minecraft:wheat",
                        skins="dikdik/", drops=[HIDE]),
}
# LOTR's wargs and spiders: faction mobs you may ride with 50 alignment (creatures.json). Wargs: LOTR rolls 20-32
# health and 3-5 attack (applyEntityAttributes); drops 1-3 fur and 2-3 warg bones. Spiders: health, speed and
# attack follow the spider's size (12 + 6s, 0.35 - 0.03s, 2 + s), drops 0-2 string and sometimes a spider eye.
WARG_DROPS = [("lotr:item.wargFur", 1, 2), ("lotr:item.wargBone", 2, 1)]
for _w in ("MordorWarg", "AngmarWarg", "GundabadWarg", "UrukWarg", "UtumnoWarg", "UtumnoIceWarg", "UtumnoObsidianWarg",
           "UtumnoFireWarg"):
    SPECS["lotr." + _w] = dict(id=snake(_w), kind="warg", model="LOTRModelWarg", size=(1.5, 1.7), skins="warg/",
                               skin_by="getWargType", saddle="warg/saddle.png", fights=True, health=(20, 32),
                               food="#minecraft:meat",
                               attack=(3, 5), drops=WARG_DROPS)
for _s, _skins in (("MirkwoodSpider", ["spider/spider_mirkwood.png", "spider/spider_mirkwoodSlowness.png",
                                       "spider/spider_mirkwoodPoison.png"]),
                   ("MordorSpider", ["spider/spider_mordor.png"]), ("UtumnoIceSpider", ["spider/spider_utumnoIce.png"])):
    SPECS["lotr." + _s] = dict(id=snake(_s), kind="spider", model="LOTRModelSpider", size=(1.4, 0.8), skins=_skins,
                               skin_by="getSpiderType", fights=True, food="minecraft:bone",
                               drops=[("minecraft:string", 0, 2), ("minecraft:spider_eye", 0, 1)])
VENOM = {"0": "none", "1": "slowness", "2": "poison"}

# Vanilla 1.7.10 ModelQuadruped/ModelPig, the base LOTR's boar model adds to (SRG field names)
PIG = {"field_78150_a": "head", "field_78148_b": "body", "field_78149_c": "leg1", "field_78146_d": "leg2",
       "field_78147_e": "leg3", "field_78144_f": "leg4"}
SET_ANGLES = ("func_78087_a", "(FFFFFFLnet/minecraft/entity/Entity;)V")
RENDER = ("func_78088_a", "(Lnet/minecraft/entity/Entity;FFFFFF)V")
LIVING = ("func_78086_a", "(Lnet/minecraft/entity/EntityLivingBase;FFF)V")  # setLivingAnimations


MC_JAR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "build", "rfg", "srg_merged_minecraft.jar")


class Jars:
    """LOTR's jar, then 1.7.10 Minecraft's (SRG names, from the exporter's Gradle setup) for vanilla model bases."""

    def __init__(self, lotr):
        self.lotr = lotr
        self.mc = zipfile.ZipFile(MC_JAR) if os.path.exists(MC_JAR) else None
        self.mc_names = set(self.mc.namelist()) if self.mc else set()

    def has(self, name):
        return self.lotr.has(name) or name in self.mc_names

    def read(self, name):
        return self.lotr.read(name) if self.lotr.has(name) else self.mc.read(name)


class Zero(dict):
    def get(self, k, d=None):
        return super().get(k, 0.0)


def idle_entity():
    """The entity a model is posed for: every field and method 0 (standing, not eating or rearing, adult)."""
    e = Obj("net/minecraft/entity/Entity")
    e.fields = Zero()
    return e


class AnimalMachine(Machine):
    """armor_models' machine (here also running vanilla's model classes) plus a record of what render() draws."""

    def __init__(self, jar):
        super().__init__(jar)
        self.drawn = []

    def renderer_call(self, rd, name, args):
        if name in ("func_78785_a", "func_78791_b"):  # render, renderWithRotation
            if rd not in self.drawn:
                self.drawn.append(rd)
            return None
        if name == "func_78794_c":  # postRender
            return None
        return super().renderer_call(rd, name, args)

    def static(self, owner, name, desc):
        return 0.0 if desc in "FDIJ" else None  # render state (partial tick...)

    def invoke(self, op, owner, name, desc, target, args):
        if (op == 0xB6 and isinstance(target, Obj) and "/model/" in target.cls and desc.endswith(")V")
                and self.call(target, (name, desc), args)):
            return None  # the model's own helpers (the giraffe's setDefaultHeadNeckRotation)
        out = super().invoke(op, owner, name, desc, target, args)
        if out is None and desc[desc.index(")") + 1:] in "FDIZJ":
            return 0.0 if desc.endswith(("F", "D")) else 0  # the entity's state (eating, rearing...): idle, adult
        return out

    def call(self, this, method, args):
        """Runs this model's method (or its nearest superclass's) on this."""
        cls = this.cls
        while cls:
            cf = self.cls(cls)
            if cf is None:
                return False
            code = cf.methods.get(method)
            if code:
                self.run(cf, code, [this] + list(args))
                return True
            cls = cf.super_name if hasattr(cf, "super_name") else None
        return False


def model_json(jar, cls_name):
    """Boxes, resting pose and animation of one LOTR (or vanilla 1.7.10) animal model; see the module doc."""
    cls = cls_name if "/" in cls_name else "lotr/client/model/" + cls_name
    if not jar.has(cls + ".class"):
        raise Unsupported(f"no class {cls} (vanilla models need {MC_JAR})")

    def build(f=0.0, f1=0.0, f3=0.0, f4=0.0):
        m = AnimalMachine(jar)
        this = m.construct(cls, [0.0], desc="(F)V") if m.cls(cls).methods.get(("<init>", "(F)V")) \
            else m.construct(cls, [])
        this.fields["field_78091_s"] = 0  # isChild: draw the adult
        m.call(this, LIVING, [idle_entity(), f, f1, 0.0])  # vanilla's horse walks here, not in setRotationAngles
        m.call(this, SET_ANGLES, [f, f1, 0.0, f3, f4, 0.0625, idle_entity()])
        m.drawn = []
        this.fields["field_78091_s"] = 0
        everything = [v for v in this.fields.values() if isinstance(v, Renderer)]
        hidden = [(v, v.hidden) for v in everything for v in [v] + v.children]
        try:
            m.call(this, RENDER, [idle_entity(), f, f1, 0.0, f3, f4, 0.0625])
        except (Unsupported, TypeError, AttributeError):
            m.drawn = []
        for v, h in hidden:  # render() hides antlers and horns by sex and age: the type decides that
            v.hidden = h
        if not m.drawn:  # render() did something the machine can't follow: draw every top-level part
            kids = {id(c) for v in this.fields.values() if isinstance(v, Renderer) for c in v.children}
            m.drawn = [v for v in this.fields.values() if isinstance(v, Renderer) and id(v) not in kids]
        names = {id(v): k for k, v in this.fields.items() if isinstance(v, Renderer)}
        return this, m.drawn, names

    def flat(drawn):
        out = []

        def walk(rd):
            out.append(rd)
            for c in rd.children:
                walk(c)
        for rd in drawn:
            walk(rd)
        return out

    this, drawn, names = build()
    rest = flat(drawn)
    samples = {k: flat(build(**k_args)[1]) for k, k_args in (
        ("walk0", dict(f1=1.0)), ("walk1", dict(f=1.0, f1=1.0)), ("yaw", dict(f3=10.0)), ("pitch", dict(f4=10.0)))}
    anim = []
    for i, rd in enumerate(rest):
        a = {}
        x0 = rd.rotation[0]
        # walking: amp * cos(k * f + phase), phase 0 or pi, so at f=0 the change is +-amp and at f=1 +-amp * cos(k);
        # the angle and (vanilla horse's lower legs) the pivot move together
        d0 = [samples["walk0"][i].rotation[0] - x0] + [a - b for a, b in zip(samples["walk0"][i].pivot, rd.pivot)]
        d1 = [samples["walk1"][i].rotation[0] - x0] + [a - b for a, b in zip(samples["walk1"][i].pivot, rd.pivot)]
        big = max(range(4), key=lambda n: abs(d0[n]))
        if abs(d0[big]) > 1e-4:
            a["swing"] = [round(d0[0], 4), round(math.acos(max(-1.0, min(1.0, d1[big] / d0[big]))), 4)]
            if any(abs(v) > 1e-3 for v in d0[1:]):
                a["move"] = [round(v, 4) for v in d0[1:]]
        yaw = (samples["yaw"][i].rotation[1] - rd.rotation[1]) / 10
        pitch = (samples["pitch"][i].rotation[0] - x0) / 10
        if abs(yaw) > 1e-5 or abs(pitch) > 1e-5:
            a["look"] = [round(pitch, 5), round(yaw, 5)]
        anim.append(a)
    it = iter(anim)

    def part(rd):
        j = rd.json()
        j.update(next(it))
        if id(rd) in names:
            j["name"] = PIG.get(names[id(rd)], names[id(rd)])
        j["children"] = [part(c) for c in rd.children]
        return j
    # each part's texture size (giraffe and gemsbok set 128x64 per part with setTextureSize): the parts' usual one
    sizes = [tuple(rd.tex) for rd in rest if rd.cubes]
    w, h = max(set(sizes), key=sizes.count) if sizes else (64, 32)
    return {"texture": [w, h], "parts": [part(rd) for rd in drawn]}


class Animals:
    def __init__(self, gen, data_dir, assets_dir, content_dir, jar):
        self.gen = gen
        self.data = os.path.join(data_dir, NS)
        self.assets = assets_dir
        self.content = content_dir
        self.jar = jar
        self.jars = Jars(jar)
        self.report = gen.report
        self.types = {}  # LOTR entity name -> port id, for the biome lists
        self.creatures = Creatures(gen, Creatures.load(os.path.join(os.path.dirname(os.path.abspath(__file__)), "export",
                                                                    "creatures.json")), assets_dir, jar)
        self.rules = {}  # LOTR entity name -> spawn rules

    def item(self, old):
        if old.startswith("lotr:"):
            new = self.gen.idtable.get(old + "#0")
            if not new:
                self.report["animal_drops_unmapped"].append(old)
            return new and new.split("[")[0]
        return old

    def skins(self, path):
        """The jar's skin file (or files), or every png in the folder, copied to textures/entity/animal/."""
        base = "assets/lotr/mob/"
        files = [base + p for p in path] if isinstance(path, list) else [base + path] if path.endswith(".png") else sorted(
            n for n in self.jar.names if n.startswith(base + path) and n.endswith(".png")
            and "/" not in n[len(base + path):])
        out = []
        for f in files:
            new = "entity/animal/" + snake(f[len(base):-4].replace("/", "_"))
            dst = os.path.join(self.assets, "textures", new + ".png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as fh:
                fh.write(self.jar.read(f))
            out.append(f"{NS}:textures/{new}.png")
        if not out:
            self.report["animal_skins_missing"].append(path)
        return out

    def loot(self, aid, drops):
        pools = []
        for d in drops:
            item, base, extra = self.item(d[0]), d[1], d[2]
            if not item:
                continue
            funcs = [{"function": "minecraft:set_count",
                      "count": {"type": "minecraft:uniform", "min": base, "max": base + extra}},
                     {"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": 1}}]
            if len(d) > 3 and self.item(d[3]):
                funcs.append({"function": "minecraft:furnace_smelt", "conditions": [{
                    "condition": "minecraft:entity_properties", "entity": "this",
                    "predicate": {"flags": {"is_on_fire": True}}}]})
            pools.append({"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": funcs}]})
        write_json(os.path.join(self.data, "loot_table", "entities", aid + ".json"),
                   {"type": "minecraft:entity", "pools": pools})

    def run(self, npcs):
        by_name = {e["name"]: e for e in npcs["entities"]}
        types, models = [], {}
        for name, s in SPECS.items():
            e = by_name.get(name, {})
            att = e.get("attributes", {})
            t = {"id": s["id"], "kind": s["kind"], "width": s["size"][0], "height": s["size"][1],
                 "health": att.get("generic.maxHealth", 10.0), "speed": att.get("generic.movementSpeed", 0.25),
                 "attack": att.get("generic.attackDamage", 0.0), "fights": s.get("fights", False),
                 "scale": s.get("scale", 1.0), "egg": [e.get("egg", {}).get("primaryColor", 0xFFFFFF),
                                                       e.get("egg", {}).get("secondaryColor", 0)]}
            if "food" in s:
                t["food"] = s["food"]
            looks = self.creatures.looks(name)
            if s.get("skin_by") == "getWargType":  # one skin per warg colour LOTR rolled
                colours = sorted({lk["getWargType"] for lk in looks})
                t["skins"] = [self.skins(f"warg/{c.lower()}.png")[0] for c in colours]
            elif "skins" in s:
                t["skins"] = self.skins(s["skins"])
            if s["kind"] in ("warg", "spider"):
                t["looks"] = [self.look(s, lk, t, colours if s["kind"] == "warg" else None) for lk in looks]
                t.update(self.creatures.riding(name))
                t.update(self.creatures.riders(name))
            for k in ("health", "attack"):
                if isinstance(s.get(k), tuple):
                    t[k], t[k + "_max"] = s[k]
            sounds = self.creatures.sounds(name)
            if sounds:
                t["sounds"] = sounds
            armor = self.creatures.armor_kind(name)
            if armor:
                t["armor"] = armor
            self.rules[name] = self.creatures.spawn_rules(name)
            if self.rules[name]:
                t["spawn_rules"] = self.rules[name]
            if "saddle" in s:
                t["saddle"] = self.skins(s["saddle"])[0]
            for k in ("male", "adult"):
                if k in s:
                    t[k + "_parts"] = s[k]
            if "model" in s:
                key = snake(s["model"].split("/")[-1].removeprefix("LOTRModel").removeprefix("Model"))
                if key not in models:
                    try:
                        models[key] = model_json(self.jars, s["model"])
                    except Exception as ex:  # noqa: BLE001 - recorded; the animal is left out
                        self.report["animal_models_failed"].append(f"{s['model']}: {ex!r}")
                        continue
                t["model"] = key
            if "drops" in s:
                self.loot(s["id"], s["drops"])
            self.gen.lang["en_us"][f"entity.{NS}.{s['id']}"] = e.get("displayName") or " ".join(
                w.capitalize() for w in s["id"].split("_"))
            self.gen.lang["en_us"][f"item.{NS}.{s['id']}_spawn_egg"] = \
                self.gen.lang["en_us"][f"entity.{NS}.{s['id']}"] + " Spawn Egg"
            write_json(os.path.join(self.assets, "models", "item", s["id"] + "_spawn_egg.json"),
                       {"parent": "minecraft:item/template_spawn_egg"})
            types.append(t)
            self.types[name] = f"{NS}:{s['id']}"
        # vanilla stand-ins keep LOTR's own spawn checks (LOTR's rabbits only came near plants, away from farmhands)
        vanilla = {VANILLA[n]: self.creatures.spawn_rules(n) for n in VANILLA if n.startswith("lotr.")}
        vanilla = {k: v for k, v in vanilla.items() if v}
        armors = self.creatures.mount_armor(self.gen.items)
        self.gen.lang["en_us"][f"chat.{NS}.mount_alignment"] = "You need %s alignment with %s"
        sounds = self.creatures.write_sounds()
        write_json(os.path.join(self.content, "animals.json"), [{"types": types, "models": models,
                                                                 "vanilla_rules": vanilla, "sounds": sounds}])
        return {"animal_types": len(types), "mount_armor": armors, "creature_sounds": len(sounds)}

    @staticmethod
    def look(s, lk, t, colours):
        """One LOTR variant: skin, and for spiders size, venom, stats and whether it may be ridden."""
        out = {"weight": lk["weight"]}
        if colours is not None:
            out["skin"] = colours.index(lk["getWargType"])
            out["rideable"] = lk.get("canWargBeRidden") == "true"
            return out
        kind = lk.get("getSpiderType", "0")
        out["skin"] = int(kind) if len(t.get("skins", [])) > 1 else 0
        out["venom"] = VENOM.get(kind, "none")
        size = int(lk.get("getSpiderScale", "0"))
        out["scale"] = 0.5 + size / 2
        out["rideable"] = lk.get("canRideSpider") == "true"
        out["health"], out["speed"], out["attack"] = 12.0 + size * 6.0, round(0.35 - size * 0.03, 3), 2.0 + size
        return out

    def spawners(self, b):
        """The biome's LOTR animal lists as 1.21 spawners; entries for animals not ported yet are left out."""
        out = {}
        for lst, entries in (b.get("animals") or {}).items():
            cat = CATEGORY.get(lst)
            for e in entries:
                mob = VANILLA.get(e["name"]) or self.types.get(e["name"])
                trees = next((r["min"] for r in self.rules.get(e["name"], []) if r["type"] == "trees"), 0)
                # shortcut: LOTR asks the chunk's biome variant (a forest variant gives 1 tree); here the biome's own
                if trees and (b.get("decorator") or {}).get("treesPerChunk", 0) < trees:
                    continue
                if not mob or not cat:
                    if e["name"] not in self.report["animal_spawns_not_ported"]:
                        self.report["animal_spawns_not_ported"].append(e["name"])
                    continue
                out.setdefault(cat, []).append(
                    {"type": mob, "weight": e["weight"], "minCount": e["min"], "maxCount": e["max"]})
        if any(m["type"] == "minecraft:cod" for m in out.get("water_ambient", [])):
            out["water_ambient"].append({"type": "minecraft:salmon", "weight": 5, "minCount": 1, "maxCount": 4})
        return out
