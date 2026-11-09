"""
What the exporter's creatures.json says about LOTR's mobs, as port data (used by animals.py):

  sounds      each type's ambient, hurt, death, step and attack sound: LOTR's own (copied with sounds.json entries to
              assets/lotr_port/sounds/) or the modern vanilla event for 1.7.10's name
  riding      the faction and alignment LOTR checks before you may ride, feed or saddle a warg or spider, which mount
              armor fits, and the riders a warg may spawn with
  looks       LOTR's random variants with how often each came up (warg colours, spider kinds and sizes)
  spawn       spawn rules read from the summaries of LOTR's spawn checks (the shapes LOTR uses, each a rule type the
              mod knows); a check of an unknown shape is reported, not guessed
  mount armor LOTR's mount armor items: mount, protection, texture (copied to textures/entity/mount_armor/)
"""
import json
import os

from assets import NS, snake

# 1.7.10 sound names -> modern events, by slot where 1.7.10 used one name for two sounds (the pig's "say" was its hurt
# sound too)
VANILLA_SOUNDS = {
    "mob.pig.say": "entity.pig.ambient", ("hurt", "mob.pig.say"): "entity.pig.hurt", "mob.pig.death": "entity.pig.death",
    "mob.pig.step": "entity.pig.step", "mob.cow.step": "entity.cow.step",
    "mob.spider.say": "entity.spider.ambient", ("hurt", "mob.spider.say"): "entity.spider.hurt",
    "mob.spider.death": "entity.spider.death", "mob.spider.step": "entity.spider.step",
    "mob.horse.idle": "entity.horse.ambient", "mob.horse.hit": "entity.horse.hurt",
    "mob.horse.death": "entity.horse.death", "mob.horse.soft": "entity.horse.step",
    "mob.skeleton.say": "entity.skeleton.ambient", "mob.skeleton.hurt": "entity.skeleton.hurt",
    "mob.skeleton.death": "entity.skeleton.death", "mob.silverfish.say": "entity.silverfish.ambient",
    "mob.silverfish.hit": "entity.silverfish.hurt", "mob.silverfish.kill": "entity.silverfish.death",
    "game.neutral.hurt": "entity.generic.hurt", "game.neutral.die": "entity.generic.death",
    "game.hostile.hurt": "entity.hostile.hurt", "game.hostile.die": "entity.hostile.death",
    "dig.wood": "block.wood.break",
}
DEFAULT_STEP = "step.grass"  # the block's own step sound: the mob doesn't override it
MATERIALS = {"plants": "plants", "vine": "vine", "water": "water", "leaves": "leaves"}


class Creatures:
    def __init__(self, gen, export, assets_dir, jar):
        self.gen = gen
        self.data = export or {"entities": {}, "mountArmor": []}
        self.assets = assets_dir
        self.jar = jar
        self.report = gen.report
        self.used_sounds = set()  # lotr sound events the port plays

    @staticmethod
    def load(path):
        if not os.path.exists(path):
            return None
        with open(path, encoding="utf-8") as f:
            return json.load(f)

    def entity(self, name):
        return self.data["entities"].get(name) or {}

    # ------------------------------------------------------------------ sounds

    def sound(self, name, slot=None):
        if not name or name == DEFAULT_STEP:
            return None
        if name.startswith("lotr:"):
            self.used_sounds.add(name[5:])
            return f"{NS}:{name[5:]}"
        new = VANILLA_SOUNDS.get((slot, name)) or VANILLA_SOUNDS.get(name)
        if not new:
            self.report["creature_sounds_unmapped"].append(name)
            return None
        return "minecraft:" + new

    def sounds(self, name):
        s = self.entity(name).get("sounds") or {}
        out = {}
        for slot in ("ambient", "step"):
            played = s.get(slot) or []
            if played:
                out[slot] = self.sound(played[0][0], slot)
        for slot in ("hurt", "death", "attack"):
            out[slot] = self.sound(s.get(slot), slot)
        out = {k: v for k, v in out.items() if v}
        if s.get("volume") is not None:
            out["volume"] = s["volume"]
        if s.get("interval") is not None:
            out["interval"] = s["interval"]
        return out

    def write_sounds(self):
        """sounds.json and the ogg files for every LOTR sound a ported mob plays."""
        lotr = json.loads(self.jar.read("assets/lotr/sounds.json").decode("utf-8"))
        out = {}
        for event in sorted(self.used_sounds):
            entry = lotr.get(event)
            if not entry:
                self.report["creature_sounds_missing"].append(event)
                continue
            files = []
            for s in entry["sounds"]:
                path = s if isinstance(s, str) else s["name"]
                src = f"assets/lotr/sounds/{path}.ogg"
                if not self.jar.has(src):
                    continue
                new = snake(path.replace("/", "__")).replace("__", "/")
                dst = os.path.join(self.assets, "sounds", *new.split("/")) + ".ogg"
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    fh.write(self.jar.read(src))
                files.append(f"{NS}:{new}")
            out[event] = {"sounds": files, "subtitle": f"subtitles.{NS}.{event}"}
            self.gen.lang["en_us"][f"subtitles.{NS}.{event}"] = " ".join(
                w.capitalize() for w in event.replace(".", " ").replace("say", "growls").split())
        with open(os.path.join(self.assets, "sounds.json"), "w", encoding="utf-8") as fh:
            json.dump(out, fh, indent=2, sort_keys=True)
        return sorted(out)

    # ------------------------------------------------------------------ riding

    def riding(self, name):
        """Faction and alignment for riding (wargs, spiders: LOTR checks one number in interact and every tick)."""
        m = self.entity(name).get("mount") or {}
        align = m.get("alignment") or {}
        out = {}
        if align and m.get("faction"):
            out["faction"] = f"{NS}:{m['faction'].lower()}"
            out["alignment"] = max(align.values())
        return out

    def armor_kind(self, name):
        """The kind of LOTR mount armor this mob wears (boar, rhino, warg...), if it has an armor slot."""
        m = self.entity(name).get("mount") or {}
        if m.get("kind") != "horse" and (m.get("inventory") or 0) < 2:
            return None  # spiders: no inventory (LOTR's armor check falls through to horse armor there)
        by_id = {a["id"]: a for a in self.data["mountArmor"]}
        kinds = {by_id[i]["mount"].lower() for i in m.get("armor") or [] if i in by_id}
        return kinds.pop() if len(kinds) == 1 else None

    def riders(self, name):
        m = self.entity(name).get("mount") or {}
        total = sum((m.get("riders") or {}).values())
        if not total:
            return {}
        chance = 0
        ops = (self.entity(name).get("spawn") or {}).get("onSpawnWithEgg", {}).get("ops", [])
        for i, op in enumerate(ops):  # LOTR: if canWargBeRidden() && rand.nextInt(N) == 0, a rider comes along
            if op == "call Random.nextInt" and i and isinstance(ops[i - 1], int):
                chance = ops[i - 1]
                break
        return {"rider_chance": chance, "riders": [
            {"npc": f"{NS}:{snake(r.removeprefix('lotr.'))}", "weight": n} for r, n in sorted(m["riders"].items())]}

    def looks(self, name):
        """LOTR's random variants: [{values..., weight}] from the sampled getters."""
        out = []
        for key, n in (self.entity(name).get("variants") or {}).items():
            look = {}
            for kv in key.split(","):
                k, v = kv.split("=")
                look[k] = v
            look["weight"] = n
            out.append(look)
        return out

    # ------------------------------------------------------------------ spawn rules

    def spawn_rules(self, name):
        """The rules in the mob's own getCanSpawnHere (and canWorldGenSpawnAt), from the exporter's summary."""
        spawn = self.entity(name).get("spawn") or {}
        rules = []
        for method in ("getCanSpawnHere", "canWorldGenSpawnAt"):
            s = spawn.get(method) or {}
            if not s.get("owner", "").startswith("lotr.") or not s.get("ops"):
                continue
            ops = s["ops"]
            got = self.read_rules(ops)
            if not got and "LOTREntityNPC" not in s["owner"]:
                self.report["creature_spawn_checks_unread"].append(f"{name} {method} ({s['owner']})")
            rules += [r for r in got if r not in rules]
        if spawn.get("spawnsInDarkness"):
            rules.append({"type": "dark"})
        return rules

    @staticmethod
    def read_rules(ops):
        rules = []
        if "call LOTRAmbientSpawnChecks.canSpawn" in ops:
            i = ops.index("call LOTRAmbientSpawnChecks.canSpawn")
            start = max((j for j in range(i) if isinstance(ops[j], str) and ops[j].startswith("call ")), default=-1)
            args = ops[start + 1:i]
            nums = [a for a in args if isinstance(a, int)]
            mats = [MATERIALS[a.split(".")[-1]] for a in args if isinstance(a, str) and a.startswith("field Material.")
                    and a.split(".")[-1] in MATERIALS]
            rules.append({"type": "near_blocks", "xz": nums[0], "y": nums[1], "attempts": nums[2],
                          "required": nums[3], "blocks": mats})
        if any(isinstance(o, str) and o.endswith(".anyFarmhandsNearby") for o in ops):
            i = next(i for i, o in enumerate(ops) if isinstance(o, str) and o.endswith(".anyFarmhandsNearby"))
            rng = next((o for o in ops[i + 1:] if isinstance(o, int)), 16)
            rules.append({"type": "no_farmhands", "range": rng})
        if "call LOTRBiomeDecorator.getVariantTreesPerChunk" in ops:
            i = ops.index("call LOTRBiomeDecorator.getVariantTreesPerChunk")
            least = next((o for o in ops[i + 1:] if isinstance(o, int)), 1)
            rules.append({"type": "trees", "min": max(1, least)})
        for i, o in enumerate(ops):  # posY < N || rand.nextInt(M) == 0 (scorpions, crocodiles)
            if isinstance(o, str) and o.endswith(".posY") and i + 2 < len(ops) and isinstance(ops[i + 1], float) \
                    and ops[i + 2] == "cmp":
                after = ops[i + 3:i + 8]
                chance = next((x for j, x in enumerate(after) if isinstance(x, int) and j + 1 < len(after)
                               and after[j + 1] == "call Random.nextInt"), 0)
                water = "field Material.water" in ops[max(0, i - 6):i]
                rules.append({"type": "above_y" if water else "below_y", "y": int(ops[i + 1]),
                              "else_one_in": chance})
        if "field Material.water" in ops and "call World.getEntitiesWithinAABB" in ops:  # crocodiles
            size = ops.index("call List.size")
            rules.append({"type": "near_water", "range": -next(o for o in ops if isinstance(o, int) and o < 0),
                          "max_nearby": next(o for o in ops[size:] if isinstance(o, int))})
        return rules

    # ------------------------------------------------------------------ mount armor

    def mount_armor(self, items):
        """Marks LOTR's mount armor items (by their new ids) and copies the textures."""
        by_new = {e["id"]: e for e in items}
        done = 0
        for a in self.data["mountArmor"]:
            new = self.gen.idtable.get(a["id"] + "#0")
            if not new or not new.startswith(NS + ":") or new.split(":", 1)[1] not in by_new:
                continue  # vanilla horse armor stands in (iron, gold, diamond)
            e = by_new[new.split(":", 1)[1]]
            tex = a.get("texture") or ""
            src = "assets/lotr/" + tex.split(":", 1)[-1]  # 1.7.10 paths are from the namespace root
            entry = {"mount": a["mount"].lower(), "protection": a.get("protection") or 0}
            if tex.startswith("lotr:") and self.jar.has(src):
                name = snake(os.path.basename(src)[:-4])
                if a["mount"] == "HORSE":  # vanilla horses draw body armor from the material's horse texture path
                    rel = f"entity/horse/armor/horse_armor_{e['id']}.png"
                else:
                    rel = f"entity/mount_armor/{name}.png"
                dst = os.path.join(self.assets, "textures", *rel.split("/"))
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    raw = self.jar.read(src)
                    fh.write(remap_horse_armor(raw) if a["mount"] == "HORSE" else raw)
                entry["texture"] = f"{NS}:textures/{rel}"
            e["kind"] = "mount_armor"
            e["mount_armor"] = entry
            e["stack"] = 1
            done += 1
        return done


# ---------------------------------------------------------------------- 1.7.10 horse armor on the modern horse

# LOTR's horse armor is painted for 1.7.10's ModelHorse (128x128); modern horses draw body armor on their own mesh
# (64x64). Box pairs (uv, size w h d): old (several stack top to bottom, as the old three-part legs) -> new.
HORSE_BOXES = [
    ([((0, 34), (10, 10, 24))], ((0, 32), (10, 10, 22))),  # body
    ([((0, 12), (4, 14, 8))], ((0, 35), (4, 12, 7))),  # neck
    ([((0, 0), (5, 5, 7))], ((0, 13), (6, 5, 7))),  # head
    ([((58, 0), (2, 16, 4))], ((56, 36), (2, 16, 2))),  # mane
    ([((24, 18), (4, 3, 6))], ((0, 25), (4, 5, 5))),  # upper mouth
    ([((44, 29), (3, 8, 4)), ((44, 41), (3, 5, 3)), ((44, 51), (4, 3, 4))], ((48, 21), (4, 11, 4))),  # leg
]


def _faces(u, v, w, h, d):
    """A box's six faces as (x, y, width, height), in Minecraft's box unwrap."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def remap_horse_armor(data):
    """LOTR's 1.7.10 horse armor texture redrawn on the modern horse's texture layout."""
    import io
    from PIL import Image
    old = Image.open(io.BytesIO(data)).convert("RGBA")
    sx, sy = old.width / 128, old.height / 128  # LOTR's are 128x128; scale if a pack's differ
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    def crop(x, y, w, h):
        return old.crop((round(x * sx), round(y * sy), round((x + w) * sx), round((y + h) * sy)))

    for olds, ((nu, nv), (nw, nh, nd)) in HORSE_BOXES:
        target = _faces(nu, nv, nw, nh, nd)
        for face, (tx, ty, tw, th) in target.items():
            if tw <= 0 or th <= 0:
                continue
            if face in ("top", "bottom"):  # the stack's top face is the first box's, its bottom the last's
                (u, v), (w, h, d) = olds[0] if face == "top" else olds[-1]
                img = crop(*_faces(u, v, w, h, d)[face])
            else:  # side faces stack top to bottom
                parts = [crop(*_faces(u, v, w, h, d)[face]) for (u, v), (w, h, d) in olds]
                width = max(p.width for p in parts)
                img = Image.new("RGBA", (width, sum(p.height for p in parts)))
                y = 0
                for p in parts:
                    img.paste(p.resize((width, p.height), Image.NEAREST), (0, y))
                    y += p.height
            out.paste(img.resize((tw, th), Image.NEAREST), (tx, ty))
    buf = io.BytesIO()
    out.save(buf, "PNG")
    return buf.getvalue()
