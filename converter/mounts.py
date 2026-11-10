"""
LOTR's mounts (missing-features task 27): horses, Shire ponies, zebras, wild boars, camels, elk, rhinos and giraffes
(LOTREntityHorse and its subclasses, all horses to Minecraft), mount armour, and NPCs that spawn riding.

Numbers are LOTR's, read from these classes (values only):
- LOTREntityHorse: vanilla's random horse stats, then in LOTRBiomeGenRohan biomes health x (1 + rand x 0.5), speed
  x (1 + rand x 0.3), jump + 0.2 (Dor-en-Ernil 0.3 / 0.2 / 0.1); apples feed and breed it.
- Subclasses' onLOTRHorseSpawn and clampChild*: pony health x 0.75, jump x 0.5, speed x 0.8, scale 0.8; boar
  health at most 25, attack 3, fights back at 1.2; camel jump x 0.5, carries a chest, wears a carpet; elk health
  x (1 + rand x 0.5), attack 3, fights back at 1.25; rhino health x 1.5 (at least 40), speed x 1.2, jump x 0.5,
  attack 4, fights back; giraffe jump x 0.8, eats leaves; zebras give milk. Foals' stats clamp to each breed's
  range (clamp_health / speed / jump).
- LOTRItemMountArmor: protection = the material's chestplate + leggings armour; the texture is
  armor/mount/<mount>_<material>.
- LOTREntityNPC: spawnRidingHorse = nextInt(n) == 0 in each NPC's constructor, the mount from createMountToRide
  (behaviours.py rider).

Horses, ponies and zebras are drawn on 1.7.10's horse model, since LOTR's zebra and horse armour textures are
painted for it: the model is read from Minecraft 1.7.10's client jar (downloaded once into converter/cache) by the
same bytecode replay as LOTR's models, and the vanilla 1.7.10 horse textures come from that jar too. The rest use
LOTR's own models (entity_models.py).
"""
import hashlib
import json
import os
import urllib.request

import armor_models
from armor_models import BASE, MR, Machine, Renderer
from assets import NS, Jar, snake, write_json
from entity_models import EntityModels

MC_1710 = ("https://launcher.mojang.com/v1/objects/e80d9b3bf5085002218d4be59e668bac718abbc6/client.jar",
           "e80d9b3bf5085002218d4be59e668bac718abbc6")
# 1.7.10's obfuscated names: ModelHorse (bhs), ModelRenderer (bix), ModelBase (bhr), and ModelHorse's fields in
# their source order
HORSE_MODEL, RENDERER, MODEL_BASE = "bhs", "bix", "bhr"
HORSE_PARTS = dict(zip(
    "a b c d e f g h i j k l m n o v w x y z A B C D E F G H I J K L M N O P Q R S".split(),
    ["head", "mouth_top", "mouth_bottom", "left_ear", "right_ear", "mule_left_ear", "mule_right_ear", "neck",
     "face_ropes", "mane", "body", "tail_base", "tail_middle", "tail_tip", "back_left_leg", "back_left_shin",
     "back_left_hoof", "back_right_leg", "back_right_shin", "back_right_hoof", "front_left_leg", "front_left_shin",
     "front_left_hoof", "front_right_leg", "front_right_shin", "front_right_hoof", "mule_left_chest",
     "mule_right_chest", "saddle_bottom", "saddle_front", "saddle_back", "left_saddle_rope", "left_saddle_metal",
     "right_saddle_rope", "right_saddle_metal", "left_face_metal", "right_face_metal", "left_rein", "right_rein"]))
SRG = {("a", "(FFF)V"): "func_78793_a", ("a", "(FFFIII)Lbix;"): "func_78789_a",
       ("a", "(FFFIIIF)V"): "func_78790_a", ("a", "(II)Lbix;"): "func_78784_a",
       ("b", "(II)Lbix;"): "func_78787_b", ("a", "(Lbix;)V"): "func_78792_a"}
RENDERER_FIELDS = {"c": "field_78800_c", "d": "field_78797_d", "e": "field_78798_e", "f": "field_78795_f",
                   "g": "field_78796_g", "h": "field_78808_h", "i": "field_78809_i", "j": "field_78806_j",
                   "k": "field_78807_k"}
VANILLA_HORSE_TEXTURES = ["horse_white", "horse_creamy", "horse_chestnut", "horse_brown", "horse_black",
                          "horse_gray", "horse_darkbrown"]
VANILLA_MARKINGS = ["horse_markings_white", "horse_markings_whitefield", "horse_markings_whitedots",
                    "horse_markings_blackdots"]
VANILLA_ARMOUR = {"minecraft:iron_horse_armor": "horse_armor_iron", "minecraft:golden_horse_armor": "horse_armor_gold",
                  "minecraft:diamond_horse_armor": "horse_armor_diamond"}

# LOTR class -> the port's breed. Health/speed/jump: how the breed changes vanilla's random horse stats on spawn
# ("mul" = x, "mul_rand" = x (1 + rand x v), "add", "min" (at least), "max" (at most)); clamp_*: foals' ranges.
BREEDS = {
    "LOTREntityHorse": {
        "id": "horse", "name": "Horse", "lotr": "lotr.Horse", "width": 1.4, "height": 1.6, "model": "old_horse",
        "textures": "vanilla", "food": ["minecraft:apple", "#minecraft:horse_food"], "armour": "horse",
        "boosts": [{"biomes": "rohan_horses", "health": 0.5, "speed": 0.3, "jump": 0.2},
                   {"biomes": "dor_en_ernil_horses", "health": 0.3, "speed": 0.2, "jump": 0.1}]},
    "LOTREntityShirePony": {
        "id": "shire_pony", "name": "Shire Pony", "lotr": "lotr.ShirePony", "width": 1.12, "height": 1.28,
        "model": "old_horse", "textures": "vanilla", "scale": 0.8, "food": ["minecraft:apple",
                                                                           "#minecraft:horse_food"],
        "armour": "horse", "spawn": {"health": {"mul": 0.75}, "jump": {"mul": 0.5}, "speed": {"mul": 0.8}},
        "clamp_health": [10, 28], "clamp_jump": [0.2, 1.0], "clamp_speed": [0.08, 0.3]},
    "LOTREntityZebra": {
        "id": "zebra", "name": "Zebra", "lotr": "lotr.Zebra", "width": 1.4, "height": 1.6, "model": "old_horse",
        "textures": ["zebra"], "food": ["minecraft:apple", "#minecraft:horse_food"], "milk": True, "armour": "horse",
        "sounds": {"ambient": "zebra.say", "hurt": "zebra.hurt", "death": "zebra.death", "angry": "zebra.hurt"}},
    "LOTREntityWildBoar": {
        "id": "wild_boar", "name": "Wild Boar", "lotr": "lotr.Boar", "width": 0.9, "height": 0.8, "model": "boar",
        "model_class": "LOTRModelBoar", "render_class": "LOTRRenderWildBoar", "textures": ["boar/boar"],
        "saddle": "boar/saddle", "food": ["minecraft:carrot"], "armour": "boar", "attack": 3.0,
        "fights_back": 1.2, "spawn": {"health": {"max": 25}},
        "clamp_health": [10, 30], "clamp_jump": [0.3, 1.0], "clamp_speed": [0.08, 0.29],
        "sounds": {"ambient": "minecraft:entity.pig.ambient", "hurt": "minecraft:entity.pig.ambient",
                   "death": "minecraft:entity.pig.death", "angry": "minecraft:entity.pig.ambient"}},
    "LOTREntityCamel": {
        "id": "camel", "name": "Camel", "lotr": "lotr.Camel", "width": 1.6, "height": 1.8, "model": "camel",
        "model_class": "LOTRModelCamel", "render_class": "LOTRRenderCamel", "textures": ["camel/camel"],
        "saddle": "camel/saddle", "carpet": ["camel/carpet_base", "camel/carpet_overlay"],
        "food": ["minecraft:wheat"], "chest": True, "armour": "camel", "spawn": {"jump": {"mul": 0.5}},
        "clamp_health": [12, 36], "clamp_jump": [0.1, 0.6], "clamp_speed": [0.1, 0.35]},
    "LOTREntityElk": {
        "id": "elk", "name": "Elk", "lotr": "lotr.Elk", "width": 1.6, "height": 1.8, "model": "elk",
        "model_class": "LOTRModelElk", "render_class": "LOTRRenderElk", "textures": ["elk/elk/"],
        "saddle": "elk/saddle", "food": ["minecraft:wheat"], "armour": "elk", "attack": 3.0, "fights_back": 1.25,
        "spawn": {"health": {"mul_rand": 0.5}},
        "clamp_health": [16, 50], "clamp_jump": [0.3, 1.0], "clamp_speed": [0.08, 0.34],
        "sounds": {"ambient": "elk.say", "hurt": "elk.hurt", "death": "elk.death", "angry": "elk.hurt"}},
    "LOTREntityRhino": {
        "id": "rhino", "name": "Rhino", "lotr": "lotr.Rhino", "width": 1.7, "height": 1.9, "model": "rhino",
        "model_class": "LOTRModelRhino", "render_class": "LOTRRenderRhino", "textures": ["rhino/rhino"],
        "saddle": "rhino/saddle", "food": ["minecraft:wheat"], "armour": "rhino", "attack": 4.0,
        "fights_back": 1.0, "spawn": {"health": {"mul": 1.5, "min": 40}, "speed": {"mul": 1.2},
                                      "jump": {"mul": 0.5}},
        "clamp_health": [20, 50], "clamp_jump": [0.2, 0.8], "clamp_speed": [0.12, 0.42],
        "sounds": {"ambient": "rhino.say", "hurt": "rhino.hurt", "death": "rhino.death", "angry": "rhino.say"}},
    "LOTREntityGiraffe": {
        "id": "giraffe", "name": "Giraffe", "lotr": "lotr.Giraffe", "width": 1.7, "height": 4.0, "model": "giraffe",
        "model_class": "LOTRModelGiraffe", "render_class": "LOTRRenderGiraffe", "textures": ["giraffe/giraffe"],
        "saddle": "giraffe/saddle", "food": ["#minecraft:leaves"], "armour": "giraffe",
        "spawn": {"jump": {"mul": 0.8}},
        "clamp_health": [12, 34], "clamp_jump": [0.2, 1.0], "clamp_speed": [0.08, 0.35]},
}
BY_NAME = {b["lotr"]: f"{NS}:{b['id']}" for b in BREEDS.values()}
BY_CLASS = {cls: f"{NS}:{b['id']}" for cls, b in BREEDS.items()}
# LOTRBiomeGenRohan and LOTRBiomeGenDorEnErnil with their subclasses
BOOST_BIOMES = {"rohan_horses": ["rohan", "rohan_uruk_highlands", "rohan_woodlands", "wold"],
                "dor_en_ernil_horses": ["dor_en_ernil", "dor_en_ernil_hills"]}
# LOTRItemMountArmor.Mount
ARMOUR_MOUNTS = {"horse": "horse", "boar": "boar", "elk": "elk", "rhino": "rhino", "warg": "warg",
                 "camel": "camel", "giraffe": "giraffe"}


def breed_of(lotr_class):
    """The port's mount entity id for a LOTR class (simple or full name), or None."""
    return BY_CLASS.get((lotr_class or "").split(".")[-1])


class Mounts:
    def __init__(self, gen, export, data, assets, content, cache):
        self.gen = gen
        self.content = content
        self.export = export
        self.data = data
        self.assets = assets
        self.cache = cache
        self.jar = gen.jar
        self.report = gen.report

    # ------------------------------------------------------------------ 1.7.10

    def minecraft_jar(self):
        """Minecraft 1.7.10's client jar (Mojang's download, checked by its sha1), or None offline."""
        path = os.path.join(self.cache, "minecraft-1.7.10-client.jar")
        if not os.path.exists(path):
            os.makedirs(self.cache, exist_ok=True)
            try:
                with urllib.request.urlopen(MC_1710[0], timeout=60) as r:
                    data = r.read()
            except OSError as e:
                self.report["mounts"].append(f"no Minecraft 1.7.10 jar: {e}")
                return None
            if hashlib.sha1(data).hexdigest() != MC_1710[1]:
                self.report["mounts"].append("Minecraft 1.7.10 jar: wrong sha1")
                return None
            with open(path, "wb") as f:
                f.write(data)
        return Jar(path)

    def horse_model(self, mc):
        """1.7.10's ModelHorse as an npc_model file (parts by MCP name, 128x128 texture)."""
        m = _NotchMachine(mc)
        this = m.construct(HORSE_MODEL, [], desc="()V")
        parts = {}
        for field, r in this.fields.items():
            if isinstance(r, Renderer) and field in HORSE_PARTS:
                part = r.json()
                part["children"] = []  # the mouth is drawn on its own here, each frame placed from the head
                parts[HORSE_PARTS[field]] = part
        tex = [this.fields.get("t", 128), this.fields.get("u", 128)]
        write_json(os.path.join(self.assets, "npc_model", "old_horse.json"),
                   {"texture": tex, "render_scale": 1.0, "parts": parts, "hidden": []})
        return len(parts)

    def boar_model(self, name, inflate):
        """LOTRModelBoar: 1.7.10's pig with tusks, walking as vanilla's four-legged animals do."""
        this = Machine(self.jar).construct("lotr/client/model/LOTRModelBoar", [inflate], desc="(F)V")
        names = {"field_78150_a": "head", "field_78148_b": "body", "field_78149_c": "leg1", "field_78146_d": "leg2",
                 "field_78147_e": "leg3", "field_78144_f": "leg4", "tusks": "tusks"}
        renderers = {names[f]: r for f, r in this.fields.items() if isinstance(r, Renderer) and f in names}
        kids = {id(c) for r in renderers.values() for c in r.children}
        parts = {n: r.json() for n, r in renderers.items() if id(r) not in kids}
        swing = ["mul", ["mul", ["cos", ["mul", ["in", "limb"], 0.6662]], 1.4], ["in", "amount"]]
        back = ["neg", swing]
        anim = {"biped": False, "set": [
            ["head", "xRot", ["rad", ["in", "pitch"]]], ["head", "yRot", ["rad", ["in", "yaw"]]],
            ["leg1", "xRot", swing], ["leg2", "xRot", back], ["leg3", "xRot", back], ["leg4", "xRot", swing]]}
        if "tusks" in parts:  # drawn with the head (LOTRModelBoar.render copies the head's angles to them)
            anim["set"] += [["tusks", "xRot", ["rad", ["in", "pitch"]]], ["tusks", "yRot", ["rad", ["in", "yaw"]]]]
        write_json(os.path.join(self.assets, "npc_model", name + ".json"),
                   {"texture": [64, 32], "render_scale": 1.0, "parts": parts, "hidden": [], "renderers": [],
                    "anim": anim, "defs": [], "inputs": []})

    def copy(self, jar, src, dst):
        if not jar.has(src):
            self.report["mounts"].append(f"missing texture {src}")
            return None
        out = os.path.join(self.assets, "textures", dst + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        with open(out, "wb") as f:
            f.write(jar.read(src))
        return f"{NS}:textures/{dst}.png"

    # ------------------------------------------------------------------ run

    def run(self):
        counts = {"mount_breeds": 0, "mount_armour": 0}
        mc = self.minecraft_jar()
        if mc:
            counts["old_horse_parts"] = self.horse_model(mc)
            for t in VANILLA_HORSE_TEXTURES + VANILLA_MARKINGS:
                self.copy(mc, f"assets/minecraft/textures/entity/horse/{t}.png", f"entity/mount/horse/{t}")
            for t in VANILLA_ARMOUR.values():
                self.copy(mc, f"assets/minecraft/textures/entity/horse/armor/{t}.png", f"entity/mount_armor/{t}")
        models = EntityModels(self.jar, self.assets, self.report)
        breeds = []
        for cls, b in BREEDS.items():
            out = {k: v for k, v in b.items() if k not in ("lotr", "model_class", "render_class", "name")}
            if b["model"] == "boar":
                for suffix, inflate in (("", 0.0), ("_saddle", 0.5)):
                    self.boar_model("boar" + suffix, inflate)
                out["model"], out["saddle_model"] = f"{NS}:boar", f"{NS}:boar_saddle"
            elif b.get("model_class"):
                lotr_model = "lotr.client.model." + b["model_class"]
                render = "lotr.client.render.entity." + b["render_class"]
                name = models.build(lotr_model, render)
                saddle = models.build(lotr_model, None, ("(F)V", [0.5]), "_saddle")
                out["model"] = f"{NS}:{name}" if name else None
                if saddle:
                    out["saddle_model"] = f"{NS}:{saddle}"
            else:
                out["model"] = f"{NS}:old_horse"
            if b["textures"] == "vanilla":
                out["textures"] = [f"{NS}:textures/entity/mount/horse/{t}.png" for t in VANILLA_HORSE_TEXTURES]
                out["markings"] = [f"{NS}:textures/entity/mount/horse/{t}.png" for t in VANILLA_MARKINGS]
            else:
                out["textures"] = models.copy_textures(b["textures"])
            if b.get("saddle"):
                out["saddle"] = (models.copy_textures([b["saddle"]]) or [None])[0]
            if b.get("carpet"):
                out["carpet"] = models.copy_textures(b["carpet"])
            if b.get("boosts"):
                out["boosts"] = [dict(x, biomes=f"#{NS}:{x['biomes']}") for x in b["boosts"]]
            breeds.append(out)
            self.gen.lang["en_us"][f"entity.{NS}.{b['id']}"] = b["name"]
            counts["mount_breeds"] += 1
        models.write_all()
        for tag, biomes in BOOST_BIOMES.items():
            write_json(os.path.join(self.data, NS, "tags", "worldgen", "biome", tag + ".json"),
                       {"values": [f"{NS}:{b}" for b in biomes]})
        counts["mount_armour"] = self.armour()
        if mc:
            counts["new_mount_armour"] = self.new_armour(mc)
        # looks and sizes are read once at startup (entity types); the numbers reload with /reload
        looks = ("id", "width", "height", "model", "saddle_model", "textures", "markings", "saddle", "carpet", "scale",
                 "seat")
        for b in breeds:
            # LOTREntityHorse.getMountedYOffset: the rider's feet at half the mount's height (0.6 = a sitting rider's
            # attachment above its feet in 1.21)
            b["seat"] = round(b["height"] * 0.5 + 0.6, 3)
        write_json(os.path.join(self.content, "mounts.json"),
                   {"breeds": [{k: v for k, v in b.items() if k in looks} for b in breeds],
                    "vanilla_armour": {k: f"{NS}:textures/entity/mount_armor/{v}.png"
                                       for k, v in VANILLA_ARMOUR.items()}})
        for b in breeds:
            write_json(os.path.join(self.data, NS, "mount", b["id"] + ".json"),
                       {k: v for k, v in b.items() if k not in looks})
        return counts

    def armour(self):
        """Mount armour items: kind mount_armor, {mount, protection, texture}."""
        items = {i["id"]: i for i in self.gen.items}
        materials = {}
        for i in self.gen.items:
            a = i.get("armor")
            if a and a.get("slot") in ("chest", "legs"):
                materials.setdefault(a["material"], {})[a["slot"]] = a["defense"]
        with open(os.path.join(self.export, "items.json"), encoding="utf-8") as f:
            exported = json.load(f)
        n = 0
        for x in exported:
            if not any(c.endswith("LOTRItemMountArmor") for c in x.get("classes") or []):
                continue
            new = self.gen.idtable.get(f"{x['id']}#0")
            if not new or not new.startswith(NS + ":"):
                continue
            e = items.get(new.split("[")[0].split(":", 1)[1])
            if not e:
                continue
            raw = x["id"].split(".")[-1]  # horseArmorGondor
            mount = raw[:raw.index("Armor")]
            material = snake(raw[raw.index("Armor") + 5:])
            if mount not in ARMOUR_MOUNTS:
                continue
            tex = self.copy(self.jar, f"assets/lotr/armor/mount/{mount}_{material}.png",
                            f"entity/mount_armor/{mount}_{material}")
            if not tex:
                continue
            m = materials.get(material) or materials.get(material.replace("_elven", "_elf")) or {}
            e["kind"] = "mount_armor"
            e["mount_armor"] = {"mount": mount, "protection": m.get("chest", 0) + m.get("legs", 0), "texture": tex}
            if not m:
                self.report["mounts"].append(f"no armour material for {raw}")
            n += 1
        return n


class _NotchMachine(Machine):
    """armor_models.Machine on 1.7.10's obfuscated classes: their names are mapped to the SRG ones it knows."""

    def cls(self, name):
        cf = super().cls(name)
        if cf is not None and not getattr(cf, "_mapped", False):
            orig = cf.ref_full

            def ref(i, orig=orig):
                o, n, d = orig(i)
                if o == RENDERER:
                    n = SRG.get((n, d), n) if "(" in d else RENDERER_FIELDS.get(n, n)
                    o = MR
                    d = d.replace(f"L{RENDERER};", f"L{MR};")
                elif o == MODEL_BASE:
                    o = BASE
                return o, n, d
            cf.ref_full = ref
            cf._mapped = True
        return cf

    def invoke(self, op, owner, name, desc, target, args):
        if owner == HORSE_MODEL and name == "a" and len(args) == 4:  # ModelHorse.setBoxRotation(renderer, x, y, z)
            armor_models.unwrap(args[0]).rotation = [float(a) for a in args[1:]]
            return None
        return super().invoke(op, owner, name, desc, target, args)


# ---------------------------------------------------------------------- armour LOTR never made

# LOTRItemMountArmor.Mount has GIRAFFE and CAMEL, but LOTR shipped no armour for them (Aaron 2026-11-10: add some).
# These are the port's own: vanilla's three horse armour tiers (1.21 protection), painted by paint_armour below.
NEW_ARMOUR_TIERS = {
    "iron": {"name": "Iron", "protection": 5, "vanilla": "iron_horse_armor", "icon": "iron_horse_armor",
             "colour": (198, 198, 198), "trim": (120, 120, 126)},
    "golden": {"name": "Golden", "protection": 7, "vanilla": "golden_horse_armor", "icon": "gold_horse_armor",
               "colour": (234, 208, 76), "trim": (168, 124, 30)},
    "diamond": {"name": "Diamond", "protection": 11, "vanilla": "diamond_horse_armor", "icon": "diamond_horse_armor",
                "colour": (104, 222, 214), "trim": (36, 136, 140)},
}
# Which boxes of the mount's model the armour covers: part -> [(cube index, top share of its sides covered)], and
# the badge stamped on the item icon so it isn't mistaken for horse armour.
NEW_ARMOUR_MOUNTS = {
    "giraffe": {"name": "Giraffe", "model": "giraffe_saddle", "badge": ((222, 178, 70), (120, 76, 30)),
                "plates": {"body": [(0, 0.7)], "neck": [(0, 1.0), (1, 0.45)], "head": [(0, 0.5)]}},
    "camel": {"name": "Camel", "model": "camel_saddle", "badge": ((206, 170, 110), (150, 112, 66)),
              "plates": {"body": [(0, 0.7)], "humps": [(0, 1.0)], "head": [(0, 0.5), (3, 0.5)]}},
}
NEW_ARMOUR_RECIPE_EXTRA = "minecraft:leather"


def _faces(uv, size, share):
    """A box's texture rectangles (x, y, w, h) in 1.7.10's box layout: top, then the four sides' top `share`."""
    (u, v), (w, h, d) = uv, size
    rows = max(1, round(h * share))
    return [(u + d, v, w, d), (u, v + d, d, rows), (u + d, v + d, w, rows), (u + d + w, v + d, d, rows),
            (u + d + w + d, v + d, w, rows)]


def paint_armour(model, tier, plates):
    """Plate armour for a creature model's texture layout: each covered face is a metal sheet with a dark rim, a
    highlight under its top edge, a little grain and a rivet in each lower corner."""
    from PIL import Image
    w, h = model["texture"]
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for part, cubes in plates.items():
        boxes = model["parts"][part]["cubes"]
        for index, share in cubes:
            if index >= len(boxes):
                continue
            for x0, y0, fw, fh in _faces(boxes[index]["uv"], boxes[index]["size"], share):
                for y in range(y0, min(h, y0 + fh)):
                    for x in range(x0, min(w, x0 + fw)):
                        edge = x in (x0, x0 + fw - 1) or y in (y0, y0 + fh - 1)
                        rivet = fw >= 5 and fh >= 4 and y == y0 + fh - 2 and x in (x0 + 1, x0 + fw - 2)
                        grain = ((x * 7 + y * 13) % 5 - 2) * 4
                        base = tier["trim"] if edge or rivet else tier["colour"]
                        lift = 18 if (not edge and y == y0 + 1) else 0
                        px[x, y] = tuple(max(0, min(255, c + grain + lift)) for c in base) + (255,)
    return img


def _new_armour(self, mc):
    """Writes the giraffe and camel armours: items, lang, entity textures, icons, models and recipes."""
    import io
    from PIL import Image
    n = 0
    for mount, m in NEW_ARMOUR_MOUNTS.items():
        with open(os.path.join(self.assets, "npc_model", m["model"] + ".json"), encoding="utf-8") as f:
            model = json.load(f)
        for tier_id, tier in NEW_ARMOUR_TIERS.items():
            new = f"{tier_id}_{mount}_armor"
            tex = os.path.join(self.assets, "textures", "entity", "mount_armor", f"{mount}_{tier_id}.png")
            os.makedirs(os.path.dirname(tex), exist_ok=True)
            paint_armour(model, tier, m["plates"]).save(tex)
            icon = Image.open(io.BytesIO(mc.read(f"assets/minecraft/textures/items/{tier['icon']}.png"))).convert("RGBA")
            light, dark = m["badge"]
            for y in range(11, 16):
                for x in range(11, 16):
                    rim = x in (11, 15) or y in (11, 15)
                    icon.putpixel((x, y), (dark if rim or (x + y) % 3 == 0 else light) + (255,))
            out = os.path.join(self.assets, "textures", "item", new + ".png")
            os.makedirs(os.path.dirname(out), exist_ok=True)
            icon.save(out)
            write_json(os.path.join(self.assets, "models", "item", new + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{new}"}})
            self.gen.items.append({"id": new, "kind": "mount_armor", "stack": 1, "mount_armor": {
                "mount": mount, "protection": tier["protection"],
                "texture": f"{NS}:textures/entity/mount_armor/{mount}_{tier_id}.png"}})
            self.gen.lang["en_us"][f"item.{NS}.{new}"] = f"{tier['name']} {m['name']} Armor"
            self.gen.tab_after[new] = "gondorian_horse_armor"
            write_json(os.path.join(self.data, NS, "recipe", "mount_armor", new + ".json"), {
                "type": "minecraft:crafting_shapeless", "category": "equipment",
                "ingredients": [{"item": f"minecraft:{tier['vanilla']}"}, {"item": NEW_ARMOUR_RECIPE_EXTRA},
                                {"item": NEW_ARMOUR_RECIPE_EXTRA}],
                "result": {"id": f"{NS}:{new}", "count": 1}})
            self.gen.recipe_count += 1
            n += 1
    return n


Mounts.new_armour = _new_armour
