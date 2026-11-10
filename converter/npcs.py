"""
LOTR's people (npcs.json from the exporter) as NPC data for the port:

  data/lotr_port/npc/<type>.json           faction, skins, size, health, speed, attack, gear, names, pools
  data/lotr_port/npc_trades/<pool>.json    what traders sell (buy pools) and buy (sell pools), in silver coins
  data/lotr_port/npc_units/<pool>.json     who captains hire out: unit, cost, alignment, pledge
  data/lotr_port/npc_names/<bank>.json     first names (orc names: every prefix + suffix)
  assets/lotr_port/textures/entity/...     skins, copied from the jar
  lang: npc.lotr_port.<type>

Only people (LOTR's human, elf, dwarf, hobbit and orc models) for now; animals, trolls, wargs and spiders need their own
models. One skin set per type (64x32 or 64x64): male skins (female NPCs and families come with colonists), no outfit overlays.
"""
import io
import json
import os

from entity_models import EntityModels
from behaviours import archer, behaviour_data, behaviours_of
from sounds import npc_sounds
import re

from assets import NS, snake, write_json
from vanilla import vanilla_item

def speech_path(folder):
    """LOTR's speech folder (camelCase) as a resource path."""
    return "/".join(snake(x) for x in folder.split("/"))


# LOTR mounts drawn on vanilla's horse and pig models: the vanilla animal stands in (dwarves' war boars ride pigs
# until LOTR's boar model converts)
MOUNT_STAND_INS = {"Horse": "minecraft:horse", "Boar": "minecraft:pig", "Camel": "minecraft:camel",
                   "ShirePony": "minecraft:donkey", "Zebra": "minecraft:horse"}
PERSON_MODELS = {"LOTRModelHuman", "LOTRModelOrc", "LOTRModelElf", "LOTRModelDwarf", "LOTRModelHobbit"}
# The head and body shape each LOTR model draws (hair layer, ears, hobbit feet); humans are the default.
MODELS = {"LOTRModelHobbit": "hobbit", "LOTRModelDwarf": "dwarf", "LOTRModelElf": "elf", "LOTRModelOrc": "orc"}
NOT_SKINS = ("female", "child", "outfit", "headwear", "ring", "glint", "shadow", "cape", "eyes", "hat", "overlay",
             "armor", "banner", "glow", "jacket", "robe", "hair", "beard", "trader", "hood", "scarf")
GEAR_SLOTS = {"mainhand": ("meleeWeapon", "mainhand", "rangedWeapon"), "head": ("head",), "chest": ("chest",),
              "legs": ("legs",), "feet": ("feet",)}
# Words that mark a soldier, for picking a speech bank by faction (soldiers shouldn't talk like the blacksmith)
MILITARY = {"archer", "soldier", "warrior", "captain", "banner", "levyman", "guard", "axeman", "marine", "warden",
            "knight", "rider", "bombardier", "thrower", "spearman", "ranger", "berserker", "commander", "marshal",
            "bounder", "shirriff", "mercenary", "uruk", "sentry"}
LEADERS = {"captain", "commander", "marshal", "warlord", "chieftain", "lord"}
# best first: a plain soldier's lines before a ranger's or a shieldmaiden's
SOLDIER_ROLES = ["soldier", "warrior", "elf_warrior", "guard", "bounder", "shirriff", "ranger", "shieldmaiden"]
PLAIN_ROLES = {"man", "elf", "dwarf", "hobbit", "orc", "tauredain", "moredain", "dunlending", "ent", "half_troll"}
SHOWN_OFFERS = 9  # LOTR traders show a random handful of their pool


def type_id(name):
    return snake(re.sub(r"^(lotr\.)+", "", name))


class Npcs:
    def __init__(self, gen, export, data_dir, assets_dir, jar):
        self.gen = gen
        self.export = export
        self.data = os.path.join(data_dir, NS)
        self.assets = assets_dir
        self.jar = jar
        self.report = gen.report
        self.copied = set()
        self.banks = {}

    # ------------------------------------------------------------------ helpers

    def item(self, stack):
        if not stack:
            return None
        new = self.gen.idtable.get(f"{stack['item']}#{stack.get('meta', 0)}") or self.gen.idtable.get(
            f"{stack['item']}#0")
        if not new and stack["item"].startswith("minecraft:"):
            # a trader deals in one real item: "any coal" (meta 32767) is coal, "any wool" white wool
            meta = 0 if stack.get("meta", 0) == 32767 else stack.get("meta", 0)
            kind, vid = vanilla_item(stack["item"], meta) or (None, None)
            return vid if kind == "item" else None
        return new.split("[")[0] if new else None

    def skins(self, e, want=None):
        """The type's skin textures. People: 64x32 or 64x64. Creatures (want = the model's texture size): textures
        of exactly that size."""
        render = e.get("render") or {}
        textures = render.get("textures") or {}
        words = [w.lower() for w in re.findall(r"[A-Z][a-z]+", e["name"].removeprefix("lotr."))]
        best, best_score = None, -1
        if not any(isinstance(t, dict) for t in textures.values()):
            # only single texture files (lotr:mob/crocodile.png): the renderer's main one
            textures = {f: {"path": t[:-4]} for f, t in textures.items() if isinstance(t, str)
                        and t.startswith("lotr:") and t.endswith(".png") and "saddle" not in f.lower()}
        for field, tex in textures.items():
            if not isinstance(tex, dict) or any(n in field.lower() for n in NOT_SKINS):
                continue
            path = (tex.get("path") or "").lower()
            score = sum(1 for w in words if w in field.lower() or w in path)
            if score > best_score:
                best, best_score = tex, score
        if best is None:
            return [], 32
        folder = "assets/lotr/" + best["path"].split(":", 1)[1] + "/"
        files = sorted(n for n in self.jar.names if n.startswith(folder) and n.endswith(".png")
                       and "/" not in n[len(folder):])
        if not files and self.jar.has(folder[:-1] + ".png"):
            files = [folder[:-1] + ".png"]  # one texture, not a folder of them (mob/crocodile.png)
        sizes = {f: image_size(self.jar.read(f)) for f in files}
        usable = [f for f in files if (sizes[f] == tuple(want) if want else sizes[f] in ((64, 32), (64, 64)))
                  and not any(n in f.rsplit("/", 1)[1].lower() for n in NOT_SKINS)]
        if not usable:
            self.report["npc_skins_odd_size"].append(f"{folder} {sorted(set(map(str, sizes.values())))}")
            return [], 32
        if want:
            out = []
            for f in usable:
                new = "entity/" + snake(f[len("assets/lotr/"):-4].removeprefix("mob/"))
                if new not in self.copied:
                    dst = os.path.join(self.assets, "textures", new + ".png")
                    os.makedirs(os.path.dirname(dst), exist_ok=True)
                    with open(dst, "wb") as fh:
                        fh.write(self.jar.read(f))
                    self.copied.add(new)
                out.append(f"{NS}:textures/{new}.png")
            return out, want[1]
        heights = [sizes[f][1] for f in usable]
        height = max(set(heights), key=heights.count)  # one model per type: keep the usual size
        out = []
        for f in usable:
            if sizes[f][1] != height:
                self.report["npc_skins_odd_size"].append(f"{f} {sizes[f]}")
                continue
            new = "entity/" + snake(f[len("assets/lotr/"):-4].removeprefix("mob/"))
            if new not in self.copied:
                dst = os.path.join(self.assets, "textures", new + ".png")
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    fh.write(self.jar.read(f))
                self.copied.add(new)
            out.append(f"{NS}:textures/{new}.png")
        return out, height

    # Skins LOTR's renderers pick in code (by type or at random), as jar files under assets/lotr/mob/ or a vanilla
    # texture; read from LOTRRenderWarg/Spider/Ent/Skeleton and the char/ folder.
    CODE_SKINS = (("Gandalf", ["char/gandalf"]), ("Saruman", ["char/saruman"]), ("Gollum", ["char/gollum"]),
                  ("MallornEnt", ["ent/mallorn"]), ("Ent", ["ent/oak", "ent/beech", "ent/birch"]),
                  ("MirkwoodSpider", ["spider/spider_mirkwood", "spider/spider_mirkwoodPoison",
                                      "spider/spider_mirkwoodSlowness"]),
                  ("MordorSpider", ["spider/spider_mordor"]), ("UtumnoIceSpider", ["spider/spider_utumnoIce"]),
                  ("MarshWraith", ["wraith/marshWraith"]), ("ScrapTrader", ["scrapTrader/0", "scrapTrader/1"]),
                  ("HaradSlave", ["nearHarad/slave/gondor_male/0", "nearHarad/slave/gondor_male/1",
                                  "nearHarad/slave/morwaith_male/0", "nearHarad/slave/nearHarad_male/0",
                                  "nearHarad/slave/nearHarad_male/1", "nearHarad/slave/taurethrim_male/0"]),
                  ("Wraith", ["minecraft:textures/entity/skeleton/skeleton.png"]))
    WARG_COLOURS = ("black", "brown", "grey", "white")

    def code_skins(self, e):
        """(skins, height) for types whose renderer picks the texture in code, else None."""
        name = e["name"].removeprefix("lotr.")
        files = None
        if "Warg" in name:
            colour = self.warg_type(e)
            files = [f"warg/{colour}"] if colour else [f"warg/{c}" for c in self.WARG_COLOURS]
        else:
            for key, paths in self.CODE_SKINS:
                if name.endswith(key) or name == key:
                    files = paths
                    break
        if not files:
            return None
        out, height = [], 32
        for f in files:
            if f.startswith("minecraft:"):
                out.append(f)
                continue
            src = f"assets/lotr/mob/{f}.png"
            if not self.jar.has(src):
                self.report["npc_code_skins_missing"].append(f"{name}: {src}")
                continue
            height = image_size(self.jar.read(src))[1]
            new = "entity/" + snake(f)
            if new not in self.copied:
                dst = os.path.join(self.assets, "textures", new + ".png")
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    fh.write(self.jar.read(src))
                self.copied.add(new)
            out.append(f"{NS}:textures/{new}.png")
        return (out, height) if out else None

    def warg_type(self, e):
        """The WargType a warg class fixes in its constructor (getstatic LOTREntityWarg$WargType.BLACK...)."""
        from classfile import ClassFile
        for c in e.get("classes") or []:
            path = c.replace(".", "/") + ".class"
            if not self.jar.has(path):
                continue
            cf = ClassFile(self.jar.read(path))
            for entry in cf.pool:
                if entry and entry[0] == "field":
                    owner, field = cf.ref_name(cf.pool.index(entry))
                    if owner.endswith("LOTREntityWarg$WargType") and field.lower() in (
                            "black", "brown", "grey", "white", "fire", "ice", "obsidian"):
                        return field.lower()
        return None

    def creature_model(self, e):
        """(model name, model json) for an NPC drawn on one of LOTR's creature models, else None."""
        cls = ((e.get("render") or {}).get("model") or "")
        if not cls or cls.split(".")[-1] in PERSON_MODELS:
            return None
        if cls.endswith("LOTRModelHuorn"):
            # huorns are trees of log and leaf blocks with a face (client/HuornRenderer), not a box model
            return "huorn", {"texture": [64, 32], "render_scale": 1.0}, 0
        render = (e.get("render") or {}).get("class")
        name = self.models.build(cls, render)
        if not name:
            return None
        return name, self.models.models[name], self.models.renderer_index(name, render)

    def load_banks(self):
        raw = {}
        for n in self.jar.names:
            if n.startswith("assets/lotr/names/") and n.endswith(".txt"):
                words = [w.strip() for w in self.jar.read(n).decode("utf-8", "replace").splitlines() if w.strip()]
                raw[n.split("/")[-1][:-4]] = words
        for bank, words in raw.items():
            if bank.endswith("_male") or bank.endswith("_female") or bank == "troll":
                self.banks[snake(bank)] = words
        for bank in raw:
            if bank.endswith("_prefix") and bank[:-7] + "_suffix" in raw and bank.startswith("orc"):
                base = bank[:-7]
                self.banks[snake(base)] = sorted({p + s for p in raw[bank] for s in raw[base + "_suffix"]})

    def bank_for(self, sample):
        if not sample:
            return None
        first = sample.split()[0]
        for bank in sorted(self.banks):
            if bank.endswith("_female"):
                continue
            if first in self.banks[bank]:
                return bank
        return None

    # ------------------------------------------------------------------ pools

    def trade_pool(self, name, pool):
        entries = []
        for t in pool.get("entries", []):
            item = self.item(t.get("item"))
            if not item:
                self.report["npc_trades_unmapped"].append(f"{name}: {t.get('item')}")
                continue
            entries.append({"item": item, **({"count": t["item"]["count"]} if t["item"].get("count", 1) != 1 else {}),
                            "cost": t.get("cost", 1)})
        out = {"kind": (pool.get("type") or "BUY").lower(), "entries": entries}
        if len(entries) > SHOWN_OFFERS:
            out["offers"] = SHOWN_OFFERS
        return out

    def unit_pool(self, name, pool, types):
        units = []
        for u in pool.get("entries", []):
            npc = type_id(u.get("entity") or "")
            mount = None
            if u.get("mount"):
                # LOTR's mounted units: wargs are NPC types; its horses, boars and the like are vanilla stand-ins
                mid = type_id(u["mount"])
                mount = f"{NS}:{mid}" if mid in types else MOUNT_STAND_INS.get(u["mount"].split(".")[-1])
                if not mount:
                    self.report["npc_mounted_units_skipped"].append(f"{name}: {u.get('name')} ({u['mount']})")
                    continue
            if npc not in types:
                self.report["npc_units_not_people"].append(f"{name}: {u.get('entity')}")
                continue
            units.append({"npc": f"{NS}:{npc}", "cost": u.get("cost", 0), "alignment": u.get("alignment", 0),
                          **({"pledge": True} if u.get("pledge") not in (None, "NONE") else {}),
                          **({"mount": mount} if mount else {})})
        return {"units": units}

    # ------------------------------------------------------------------ types

    def npc_type(self, e, factions):
        attrs = e.get("attributes", {})
        out = {}
        fac = (e.get("faction") or "").lower()
        if fac in factions:
            out["faction"] = f"{NS}:{fac}"
        creature = self.creature_model(e)
        if creature:
            model_json = creature[1]
            skins, height = self.skins(e, model_json["texture"])
        else:
            skins, height = self.skins(e)
        if not skins:
            skins, height = self.code_skins(e) or ([], 32)
        if skins:
            out["skins"] = skins
            if height != 32:
                out["skin_height"] = height
        else:
            self.report["npcs_without_skins"].append(e["name"])
        model = MODELS.get(((e.get("render") or {}).get("model") or "").split(".")[-1])
        if creature:
            # a creature body (wargs, trolls, spiders...): its own model, its own hitbox, drawn at LOTR's scale
            out["model"] = f"{NS}:{creature[0]}"
            out["width"] = round(e.get("width", 0.6), 3)
            out["height"] = round(e.get("height", 1.8), 3)
            # LOTR's renderer scales (troll size, Gollum, Balrog...) come with the model's renderer ("pre")
            data = behaviour_data(e, self.jar)
            data["state.renderer"] = creature[2]
            out["behaviours"] = behaviours_of(e)
            out["lotr_classes"] = [c.split(".")[-1] for c in e.get("classes") or []]
            out["behaviour_data"] = data
        elif model:
            out["model"] = model
        voice = npc_sounds(e.get("classes") or [e.get("class", "")])
        if voice:
            out["sounds"] = voice
        size = e.get("height", 1.8)
        if not creature and abs(size - 1.8) > 0.01:
            out["scale"] = round(size / 1.8, 3)
        out["health"] = attrs.get("generic.maxHealth", 20.0)
        out["speed"] = attrs.get("generic.movementSpeed", 0.2)
        out["attack"] = attrs.get("lotr.npcAttackDamage", 2.0)
        gear = {}
        sampled = e.get("gear") or {}
        for slot, sources in GEAR_SLOTS.items():
            for src in sources:
                stacks = sampled.get(src) if isinstance(sampled, dict) else None
                items = sorted({i for i in (self.item(st) for st in stacks or []) if i})
                if items:
                    gear[slot] = items[0] if len(items) == 1 else items  # several: one is picked at spawn
                    break
        shots = archer(e, self.jar)
        if shots:
            # LOTR archers and throwers: their ranged weapon (pure archers hold it; elves and the like swap it with
            # their melee weapon by distance, LOTREntityNPC.onAttackModeChange)
            stacks = sampled.get("rangedWeapon") if isinstance(sampled, dict) else None
            ranged = sorted({i for i in (self.item(st) for st in stacks or []) if i})
            if ranged:
                gear["ranged" if shots["ranged_swap"] else "mainhand"] = ranged[0] if len(ranged) == 1 else ranged
            if ranged:
                out["behaviours"] = out.get("behaviours", []) + ["archer"]
                out["behaviour_data"] = {**out.get("behaviour_data", {}), **shots}
        if gear:
            out["gear"] = gear
        bank = self.bank_for(e.get("sampleName"))
        if bank:
            out["names"] = f"{NS}:{bank}"
        if (e.get("fields") or {}).get("isPassive"):
            out["hostile"] = False
        for key, field in (("buy", "buyPool"), ("sell", "sellPool"), ("units", "unitPool")):
            if e.get(field):
                out[key] = f"{NS}:{e[field].lower()}"
        return out

    def run(self):
        factions = {f[:-5] for f in os.listdir(os.path.join(self.data, "faction"))}
        self.load_banks()
        self.models = EntityModels(self.jar, self.assets, self.report)
        people = [e for e in self.export.get("entities", [])
                  if ((e.get("render") or {}).get("model") or "").split(".")[-1] in PERSON_MODELS
                  or ((".npc." in (e.get("classes") or [""])[0] or ".animal." in (e.get("classes") or [""])[0])
                      and self.creature_model(e))]
        types = {type_id(e["name"]): e for e in people}
        written = {}
        for tid, e in sorted(types.items()):
            written[tid] = self.npc_type(e, factions)
        used_trades = {v[k].split(":")[1] for v in written.values() for k in ("buy", "sell") if k in v}
        used_units = {v["units"].split(":")[1] for v in written.values() if "units" in v}
        for name, pool in self.export.get("trades", {}).items():
            if name.lower() in used_trades:
                write_json(os.path.join(self.data, "npc_trades", name.lower() + ".json"), self.trade_pool(name, pool))
        for name, pool in self.export.get("units", {}).items():
            if name.lower() in used_units:
                write_json(os.path.join(self.data, "npc_units", name.lower() + ".json"),
                           self.unit_pool(name, pool, types))
        used_banks = {v["names"].split(":")[1] for v in written.values() if "names" in v}
        for bank in used_banks:
            write_json(os.path.join(self.data, "npc_names", bank + ".json"), self.banks[bank])
        self.speech(types, written)
        self.written = written
        for tid, v in written.items():
            for key, folder in (("buy", "npc_trades"), ("sell", "npc_trades"), ("units", "npc_units")):
                if key in v and not os.path.exists(os.path.join(self.data, folder, v[key].split(":")[1] + ".json")):
                    self.report["npc_pools_missing"].append(f"{tid}: {v[key]}")
                    del v[key]
            write_json(os.path.join(self.data, "npc", tid + ".json"), v)
        self.models.write_all()
        self.lang(types)
        skipped = sorted({(e.get("render") or {}).get("model") or "none" for e in self.export.get("entities", [])
                          if e not in people})
        self.report["npc_models_not_ported"].extend(skipped)
        return {"npcs": len(written), "npc_skins": len(self.copied)}

    # ------------------------------------------------------------------ speech

    def speech(self, types, written):
        """LOTR's speech banks as speech/<folder>/<mood>.json (lines; # is the player's name), and each type's folder:
        the bank LOTR picked when exported, else the closest folder by name, else the most common one of its faction."""
        prefix = "assets/lotr/speech/"
        banks = {}
        for n in self.jar.names:
            if n.startswith(prefix) and n.endswith(".txt") and n.count("/") == 5:
                folder, mood = n[len(prefix):-4].rsplit("/", 1)
                if folder.startswith("miniquest/"):
                    continue  # quest offer lines, by quest rather than by mood
                lines = [w.strip() for w in self.jar.read(n).decode("utf-8", "replace").splitlines() if w.strip()]
                if lines:
                    banks.setdefault(folder, {})[mood] = lines
        by_name = {}
        for folder in banks:
            f, r = (snake(x) for x in folder.split("/"))
            # gondor/lamedonCaptain is the lamedon_captain's own bank
            for k in (f + "_" + r, r if r.startswith(f) or "_" in r else None, f if f == r else None):
                if k:
                    by_name.setdefault(k, folder)

        def guess(tid, e):
            bank = e.get("speechBank")
            if bank and bank.rsplit("/", 1)[0] in banks:
                return bank.rsplit("/", 1)[0]
            words = tid.split("_")
            for n in range(len(words), 0, -1):
                if "_".join(words[:n]) in by_name:
                    return by_name["_".join(words[:n])]
            return None

        picked = {tid: guess(tid, e) for tid, e in types.items()}
        by_faction = {}
        for tid, folder in picked.items():
            if folder and "faction" in written[tid]:
                by_faction.setdefault(written[tid]["faction"], []).append(folder)
        used = set()
        for tid, folder in sorted(picked.items()):
            if not folder and written[tid].get("faction") in by_faction:
                # every bank of the LOTR speech folders the faction's other NPCs use
                used_by = by_faction[written[tid]["faction"]]
                groups = {f.split("/")[0] for f in used_by}
                options = used_by + [f for f in banks if f.split("/")[0] in groups and f not in used_by]
                words = set(tid.split("_"))
                rank = "leader" if words & LEADERS else "soldier" if words & MILITARY else "folk"

                def fits(f):
                    # leaders talk like the faction's captains, soldiers like its soldiers (or its plain folk), and
                    # everyone else like its plain folk, never like its smith or innkeeper
                    role = snake(f.split("/")[1])
                    plain = role in PLAIN_ROLES or snake(f.split("/")[0]).startswith(role)
                    role_words = set(role.split("_"))
                    if rank == "leader":
                        if role_words & LEADERS:
                            return 3.1 if role in LEADERS else 3  # a plain captain before another lord's captain
                        return 2 if role in SOLDIER_ROLES else 1 if plain else 0
                    if rank == "soldier":
                        return 3 - SOLDIER_ROLES.index(role) / 10 if role in SOLDIER_ROLES else 2 if plain else 0
                    return 3 if plain else 0 if role_words & (MILITARY | LEADERS) else 1
                folder = max(sorted(set(options)), key=lambda f: (fits(f), options.count(f)))
                self.report["npc_speech_from_faction"].append(tid)
            if folder:
                written[tid]["speech"] = f"{NS}:{speech_path(folder)}"
                used.add(folder)
            else:
                self.report["npc_without_speech"].append(tid)
        for folder in used:
            for mood, lines in banks[folder].items():
                write_json(os.path.join(self.data, "speech", speech_path(folder), mood + ".json"), {"lines": lines})

    def lang(self, types):
        for code in self.jar.langs():
            src = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for tid, e in types.items():
                text = src.get(f"entity.{e['name']}.name")
                if text:
                    dst[f"npc.{NS}.{tid}"] = text
        for tid, e in types.items():
            key = f"npc.{NS}.{tid}"
            if key not in self.gen.lang["en_us"]:
                self.gen.lang["en_us"][key] = " ".join(re.findall(r"[A-Z][a-z]*", e["name"].removeprefix("lotr.")))


def image_size(data):
    try:
        from PIL import Image
        with Image.open(io.BytesIO(data)) as im:
            return im.size
    except Exception:  # noqa: BLE001 - an unreadable image is reported as not 64x32
        return None
