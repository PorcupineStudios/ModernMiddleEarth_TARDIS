"""
LOTR's static tables from export/extra.json (ReflectionDump in the exporter) as port data:

- enchantments: LOTR's 59 modifiers as 1.21 data-driven enchantments (`enchantment/<id>.json`), with the item tags
  they apply to; plus every LOTR weapon, tool and armour piece in vanilla's enchantable tags.
- achievements: advancements (`advancement/<category>/<name>.json`), one tab per LOTR category; granted from code
  (`achievement/Achievements`), so every criterion is `minecraft:impossible`.
- titles, shields, capes: `content/titles.json`, `shields.json`, `capes.json` with their unlock rules; textures copied.
- drinks: each drink item gets a `drink` block (heal, saturation, alcohol, effects, damage, cures) and the shared
  strengths and vessels go in `content/drinks.json`.
- calendar: `content/calendar.json` (Shire Reckoning months and weekdays, day length, the year offset).
- music: `content/music.json` (biome -> LOTR music region) when the export has biome fields.

Everything here is numbers and names read from the running mod; no LOTR code.
"""
import gzip
import json
import os

from assets import NS, snake, write_json

ENCH = "lotr.common.enchant.LOTREnchantment"
ACH = "lotr.common.LOTRAchievement"
CAT = "lotr.common.LOTRAchievement$Category"
TITLE = "lotr.common.LOTRTitle"
SHIELD = "lotr.common.LOTRShields"
CAPE = "lotr.common.LOTRCapes"
DATE = "lotr.common.LOTRDate"
MONTH = "lotr.common.LOTRDate$ShireReckoning$Month"
DAY = "lotr.common.LOTRDate$ShireReckoning$Day"
MUG = "lotr.common.item.LOTRItemMug"
VESSEL = "lotr.common.item.LOTRItemMug$Vessel"

# LOTR item kinds (LOTREnchantmentType) -> the port's item tag of things it applies to
TYPE_TAGS = {"MELEE": "melee", "THROWING_AXE": "melee", "RANGED_LAUNCHER": "ranged", "TOOL": "tool",
             "SHEARS": "tool", "BREAKABLE": "breakable", "ARMOR": "armor", "ARMOR_FEET": "foot_armor"}
# port item kind -> port enchantable tags
KIND_TAGS = {"sword": ["melee", "breakable"], "bow": ["ranged", "breakable"], "crossbow": ["ranged", "breakable"],
             "pickaxe": ["tool", "breakable"], "axe": ["tool", "melee", "breakable"],
             "shovel": ["tool", "breakable"], "hoe": ["tool", "breakable"], "armor": ["armor", "breakable"]}
# port item kind -> vanilla enchantable tags (so vanilla enchantments work on LOTR gear too)
VANILLA = {"sword": ["weapon", "sharp_weapon", "sword", "fire_aspect", "durability", "vanishing"],
           "axe": ["weapon", "sharp_weapon", "mining", "mining_loot", "durability", "vanishing"],
           "pickaxe": ["mining", "mining_loot", "durability", "vanishing"],
           "shovel": ["mining", "mining_loot", "durability", "vanishing"],
           "hoe": ["mining", "mining_loot", "durability", "vanishing"],
           "bow": ["bow", "durability", "vanishing"], "crossbow": ["crossbow", "durability", "vanishing"]}
ARMOR_SLOT = {"head": "head_armor", "chest": "chest_armor", "legs": "leg_armor", "feet": "foot_armor"}
VESSEL_ITEMS = {"MUG": "lotr:item.mug", "MUG_CLAY": "lotr:item.ceramicMug", "GOBLET_GOLD": "lotr:item.gobletGold",
                "GOBLET_SILVER": "lotr:item.gobletSilver", "GOBLET_COPPER": "lotr:item.gobletCopper",
                "GOBLET_WOOD": "lotr:item.gobletWood", "SKULL": "lotr:item.skullCup", "GLASS": "lotr:item.wineGlass",
                "SKIN": "lotr:item.waterskin", "HORN": "lotr:item.aleHorn", "HORN_GOLD": "lotr:item.aleHornGold"}
POTIONS = {"potion.moveSpeed": "speed", "potion.moveSlowdown": "slowness", "potion.digSpeed": "haste",
           "potion.digSlowDown": "mining_fatigue", "potion.damageBoost": "strength", "potion.heal": "instant_health",
           "potion.harm": "instant_damage", "potion.jump": "jump_boost", "potion.confusion": "nausea",
           "potion.regeneration": "regeneration", "potion.resistance": "resistance",
           "potion.fireResistance": "fire_resistance", "potion.waterBreathing": "water_breathing",
           "potion.invisibility": "invisibility", "potion.blindness": "blindness",
           "potion.nightVision": "night_vision", "potion.hunger": "hunger", "potion.weakness": "weakness",
           "potion.poison": "poison", "potion.wither": "wither", "potion.healthBoost": "health_boost",
           "potion.absorption": "absorption", "potion.saturation": "saturation"}
VANILLA_ANIMALS = {"EntitySheep": "minecraft:sheep", "EntityPig": "minecraft:pig", "EntityCow": "minecraft:cow",
                   "EntityChicken": "minecraft:chicken", "EntityHorse": "minecraft:horse", "EntityWolf": "minecraft:wolf",
                   "EntityOcelot": "minecraft:ocelot", "EntityMooshroom": "minecraft:mooshroom",
                   "EntityBat": "minecraft:bat", "EntitySquid": "minecraft:squid"}
# LOTR animals drawn on vanilla's pig and horse models: the vanilla animal stands in (rideable horses included)
STAND_INS = {"LOTREntityWildBoar": "minecraft:pig", "LOTREntityHorse": "minecraft:horse",
             "LOTREntityZebra": "minecraft:horse", "LOTREntityShirePony": "minecraft:donkey"}
LANG_PREFIXES = ("lotr.invasion.", "lotr.enchant.", "lotr.achievement.", "lotr.title.", "lotr.titles.", "lotr.shields.",
                 "lotr.date.", "item.lotr.drink.", "lotr.capes.")


def _named(table):
    return {k: v for k, v in table.items() if isinstance(v, dict)}


class Extra:
    def __init__(self, gen, export_dir, data_dir, assets_dir, content_dir, jar):
        self.gen = gen
        path = os.path.join(export_dir, "extra.json.gz")
        self.d = json.load(gzip.open(path, "rt", encoding="utf-8")) if os.path.exists(path) else None
        self.data = data_dir
        self.assets = assets_dir
        self.content = content_dir
        self.jar = jar
        self.report = gen.report

    def run(self):
        if not self.d:
            self.report["extra_missing"].append("export/extra.json.gz (run the exporter)")
            return {}
        out = {}
        out["enchantments"] = self.enchantments()
        out["advancements"] = self.achievements()
        out["titles"] = self.titles()
        out["shields"], out["capes"] = self.cosmetics()
        out["drinks"] = self.drinks()
        out["dyeable"] = self.dyeable()
        self.calendar()
        self.lang()
        return out

    # ------------------------------------------------------------------ helpers

    def item_id(self, stack):
        if not stack:
            return "minecraft:barrier"
        if isinstance(stack, str):
            stack = {"item": stack.split(":", 1)[1] if stack.startswith("item:") else stack, "meta": 0}
        new = self.gen.idtable.get(f"{stack['item']}#{stack.get('meta', 0)}") or self.gen.idtable.get(
            f"{stack['item']}#0")
        if new:
            return new.split("[")[0]
        if stack["item"].startswith("minecraft:"):
            return stack["item"].replace("minecraft:item.", "minecraft:").replace("minecraft:tile.", "minecraft:")
        return "minecraft:barrier"

    def npc_types_for(self, classes):
        """The port's NPC type ids whose LOTR class chain includes one of the classes."""
        from npcs import type_id
        npcs = json.load(open(os.path.join(os.path.dirname(__file__), "export", "npcs.json")))["entities"]
        out = []
        for e in npcs:
            if any(c in (e.get("classes") or []) for c in classes):
                t = type_id(e["name"])
                if os.path.exists(os.path.join(self.data, NS, "npc", t + ".json")):
                    out.append(f"{NS}:{t}")
        return sorted(set(out))

    # ------------------------------------------------------------------ enchantments

    def enchantments(self):
        table = _named(self.d[ENCH])
        tags = self.gen.tags
        for it in self.gen.items:
            kind = it.get("kind")
            for t in KIND_TAGS.get(kind, []):
                tags[f"{NS}:item/enchantable/{t}"].add(it["id"])
            for t in VANILLA.get(kind, []):
                tags[f"minecraft:item/enchantable/{t}"].add(it["id"])
            if kind == "armor" and it.get("armor"):
                slot = it["armor"].get("slot")
                tags[f"minecraft:item/enchantable/armor"].add(it["id"])
                tags[f"minecraft:item/enchantable/equippable"].add(it["id"])
                tags[f"minecraft:item/enchantable/durability"].add(it["id"])
                tags[f"minecraft:item/enchantable/vanishing"].add(it["id"])
                if slot in ARMOR_SLOT:
                    tags[f"minecraft:item/enchantable/{ARMOR_SLOT[slot]}"].add(it["id"])
                if slot == "feet":
                    tags[f"{NS}:item/enchantable/foot_armor"].add(it["id"])
        groups = {}
        modifiers = []
        for name, e in table.items():
            if "enchantName" not in e:
                continue
            cls = e["_class"].split(".")[-1].replace("LOTREnchantment", "") or "Base"
            group = {"Damage": "damage", "Bane": None, "Durability": "durability", "MeleeSpeed": "melee_speed",
                     "MeleeReach": "melee_reach", "Knockback": "knockback", "ToolSpeed": "tool_speed",
                     "SilkTouch": "silk_touch", "Looting": "looting", "Protection": "protection",
                     "ProtectionFire": "protection_fire", "ProtectionFall": "protection_fall",
                     "ProtectionRanged": "protection_ranged", "ProtectionMithril": None,
                     "RangedDamage": "ranged_damage", "RangedKnockback": "ranged_knockback",
                     "WeaponSpecial": "weapon_special"}.get(cls)
            eid = snake(name)
            if group:
                groups.setdefault(group, []).append(eid)
            supported = sorted({TYPE_TAGS[t["_name"]] for t in e["itemTypes"] if t["_name"] in TYPE_TAGS})
            effects = self.effects(cls, e)
            if effects is None:
                self.report["enchantments_without_effect"].append(name)
                effects = {}
            slots = ["armor"] if supported and all("armor" in s for s in supported) else ["mainhand"]
            ench = {"description": {"translate": f"lotr.enchant.{name}"},
                    "supported_items": f"#{NS}:enchantable/{supported[0]}" if len(supported) == 1
                    else f"#{NS}:enchantable/{'_or_'.join(supported)}",
                    "weight": max(1, e.get("enchantWeight", 1)), "max_level": 1,
                    "min_cost": {"base": 1, "per_level_above_first": 0},
                    "max_cost": {"base": 41, "per_level_above_first": 0},
                    "anvil_cost": max(1, round(e.get("valueModifier", 1) * 2)), "slots": slots, "effects": effects}
            if len(supported) > 1:
                key = "_or_".join(supported)
                for s in supported:
                    tags[f"{NS}:item/enchantable/{key}"].update(tags[f"{NS}:item/enchantable/{s}"])
            if group:
                ench["exclusive_set"] = f"#{NS}:exclusive_set/{group}"
            write_json(os.path.join(self.data, NS, "enchantment", eid + ".json"), ench)
            modifiers.append({"id": f"{NS}:{eid}", "weight": e.get("enchantWeight", 0),
                              "value_modifier": round(e.get("valueModifier", 1), 4), "skilful": e.get("skilful", False),
                              "persists_reforge": e.get("persistsReforge", False),
                              "bypass_anvil_limit": e.get("bypassAnvilLimit", False),
                              "apply_to_projectile": e.get("applyToProjectile", False)})
        for group, ids in groups.items():
            write_json(os.path.join(self.data, NS, "tags", "enchantment", "exclusive_set", group + ".json"),
                       {"values": [f"{NS}:{i}" for i in sorted(ids)]})
        # LOTR's modifiers came from its anvil and forge, not the enchanting table: weight > 0 ones may be rolled
        write_json(os.path.join(self.data, NS, "tags", "enchantment", "modifier", "random.json"),
                   {"values": [m["id"] for m in modifiers if m["weight"] > 0]})
        write_json(os.path.join(self.content, "modifiers.json"), modifiers)
        return len(modifiers)

    @staticmethod
    def _projectile(expected=True):
        return {"condition": "minecraft:damage_source_properties",
                "predicate": {"tags": [{"id": "minecraft:is_projectile", "expected": expected}]}}

    def effects(self, cls, e):
        add = lambda v: {"type": "minecraft:add", "value": round(v, 4)}  # noqa: E731
        if cls == "Damage":
            return {"minecraft:damage": [{"effect": add(e["baseDamageBoost"])}]}
        if cls == "Bane":
            if "entityAttribute" in e:
                tag = {"ARTHROPOD": "#minecraft:arthropod", "UNDEAD": "#minecraft:undead"}[e["entityAttribute"]["_name"]]
                pred = {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"type": tag}}
                types = self.npc_types_for(["lotr.common.entity.npc.LOTREntitySpiderBase"]
                                           if "ARTHROPOD" in tag else
                                           ["lotr.common.entity.npc.LOTREntityBarrowWight",
                                            "lotr.common.entity.npc.LOTREntitySkeletalWraith"])
            else:
                pred = None
                types = self.npc_types_for(e["entityClasses"])
            terms = [pred] if pred else []
            terms += [{"condition": "minecraft:entity_properties", "entity": "this",
                       "predicate": {"nbt": "{NpcType:\"%s\"}" % t}} for t in types]
            if not terms or not e.get("baneDamage"):
                return {}
            return {"minecraft:damage": [{"effect": add(e["baneDamage"]),
                                          "requirements": {"condition": "minecraft:any_of", "terms": terms}}]}
        if cls == "Durability":
            # LOTR spreads each point of wear over durabilityFactor uses
            return {"minecraft:item_damage": [{"effect": {"type": "minecraft:multiply",
                                                          "factor": round(1 / e["durabilityFactor"], 4)}}]}
        if cls in ("MeleeSpeed", "MeleeReach", "ToolSpeed"):
            attr = {"MeleeSpeed": "minecraft:generic.attack_speed",
                    "MeleeReach": "minecraft:player.entity_interaction_range",
                    "ToolSpeed": "minecraft:player.block_break_speed"}[cls]
            f = e["speedFactor"] if "speedFactor" in e else e["reachFactor"]
            return {"minecraft:attributes": [{"id": f"{NS}:enchantment.{snake(e['enchantName'])}", "attribute": attr,
                                              "amount": {"type": "minecraft:linear", "base": round(f - 1, 4),
                                                         "per_level_above_first": 0},
                                              "operation": "add_multiplied_total"}]}
        if cls == "Knockback":
            return {"minecraft:knockback": [{"effect": add(e["knockback"]),
                                             "requirements": self._projectile(False)}]}
        if cls == "RangedKnockback":
            return {"minecraft:knockback": [{"effect": add(e["knockback"]), "requirements": self._projectile()}]}
        if cls == "RangedDamage":
            return {"minecraft:damage": [{"effect": {"type": "minecraft:multiply", "factor": e["damageFactor"]},
                                          "requirements": self._projectile()}]}
        if cls.startswith("Protection"):
            req = {"Protection": None, "ProtectionMithril": None, "ProtectionFire": "minecraft:is_fire",
                   "ProtectionFall": "minecraft:is_fall", "ProtectionRanged": "minecraft:is_projectile"}[cls]
            tags = [{"id": "minecraft:bypasses_invulnerability", "expected": False}]
            if req:
                tags.append({"id": req, "expected": True})
            return {"minecraft:damage_protection": [{"effect": add(e["protectLevel"]),
                                                     "requirements": {"condition": "minecraft:damage_source_properties",
                                                                      "predicate": {"tags": tags}}}]}
        if cls == "WeaponSpecial":
            n = e["enchantName"]
            if n == "fire":
                # same burn as vanilla Fire Aspect I (LOTR's own burn time is set in code, not exported)
                return {"minecraft:post_attack": [{"enchanted": "attacker", "affected": "victim",
                                                   "effect": {"type": "minecraft:ignite",
                                                              "duration": {"type": "minecraft:linear", "base": 4,
                                                                           "per_level_above_first": 4}},
                                                   "requirements": {"condition": "minecraft:damage_source_properties",
                                                                    "predicate": {"is_direct": True}}}]}
            if n == "chill":
                return {"minecraft:post_attack": [{"enchanted": "attacker", "affected": "victim",
                                                   "effect": {"type": "minecraft:apply_mob_effect",
                                                              "to_apply": "minecraft:slowness",
                                                              "min_duration": 5, "max_duration": 5,
                                                              "min_amplifier": 1, "max_amplifier": 1}}]}
            return None  # headhunting: drops heads, needs a loot modifier
        return None  # silk touch, looting: vanilla ties these to its own enchantment ids in loot tables

    # ------------------------------------------------------------------ achievements

    def achievements(self):
        cats = _named(self.d[CAT])
        n = 0
        for cname, c in cats.items():
            if "codeName" not in c:
                continue
            rgb = c.get("categoryColors") or [1, 1, 1]
            write_json(os.path.join(self.data, NS, "advancement", snake(cname), "root.json"), {
                "display": {"icon": {"id": "minecraft:filled_map"},
                            "title": {"translate": f"lotr.achievement.category.{cname}"},
                            "description": {"translate": f"lotr.achievement.category.{cname}"},
                            "background": "minecraft:textures/block/stone_bricks.png" if cname != "GENERAL"
                            else "minecraft:textures/block/grass_block_top.png",
                            "show_toast": False, "announce_to_chat": False},
                "criteria": {"lotr": {"trigger": "minecraft:impossible"}}})
        index = {}
        for name, a in _named(self.d[ACH]).items():
            if "ID" not in a:
                continue
            cat = a["category"]["_name"] if isinstance(a.get("category"), dict) else "GENERAL"
            path = f"{snake(cat)}/{snake(name)}"
            write_json(os.path.join(self.data, NS, "advancement", path + ".json"), {
                "parent": f"{NS}:{snake(cat)}/root",
                "display": {"icon": {"id": self.item_id(a.get("icon"))},
                            "title": {"translate": f"lotr.achievement.{name}.title"},
                            "description": {"translate": f"lotr.achievement.{name}.desc"},
                            "frame": "challenge" if a.get("isSpecial") else "task"},
                "criteria": {"lotr": {"trigger": "minecraft:impossible"}}})
            index[name] = {"id": f"{NS}:{path}", "biome": a.get("isBiomeAchievement", False)}
            n += 1
        write_json(os.path.join(self.content, "achievements.json"), index)
        return n

    # ------------------------------------------------------------------ titles, shields, capes

    def faction(self, f):
        from factions import faction_id
        name = f["_name"] if isinstance(f, dict) else str(f)
        return f"{NS}:{faction_id(name)}"

    def titles(self):
        out = []
        for name, t in _named(self.d[TITLE]).items():
            if "titleID" not in t:
                continue
            kind = t["titleType"]["_name"].lower()
            e = {"id": t["name"], "type": kind, "hidden": t.get("isHidden", False),
                 "lang": f"lotr.achievement.{t['name']}.title" if t.get("useAchievementName")
                 else f"lotr.title.{t['name']}"}
            if kind == "alignment":
                e["factions"] = [self.faction(f) for f in t.get("alignmentFactions") or []]
                e["alignment"] = t.get("alignmentRequired", 0)
                e["any"] = t.get("anyAlignment", False)
            if kind == "achievement":
                e["achievement"] = t["name"]
            out.append(e)
        # rank and achievement titles hang off ranks and achievements, which the static fields don't list
        for name, a in _named(self.d[ACH]).items():
            at = a.get("achievementTitle")
            if isinstance(at, dict) and not any(x["id"] == at["name"] for x in out):
                out.append({"id": at["name"], "type": "achievement", "hidden": at.get("isHidden", False),
                            "lang": f"lotr.achievement.{name}.title", "achievement": name})
        write_json(os.path.join(self.content, "titles.json"), sorted(out, key=lambda x: x["id"]))
        return len(out)

    def cosmetics(self):
        shields = []
        for name, s in _named(self.d[SHIELD]).items():
            if "shieldTexture" not in s:
                continue
            tex = s["shieldTexture"].split(":", 1)[1]
            self.copy(f"assets/lotr/{tex}", f"textures/entity/{tex}")
            e = {"id": snake(name), "type": s["shieldType"]["_name"].lower(),
                 "texture": f"{NS}:textures/entity/{tex}", "hidden": s.get("isHidden", False),
                 "lang": f"lotr.shields.{name}"}
            if isinstance(s.get("alignmentFaction"), dict):
                e["faction"] = self.faction(s["alignmentFaction"])
                e["alignment"] = 1000  # "Awarded for reaching +1000 alignment" (LOTRShields' alignment shields)
            shields.append(e)
        capes = []
        for name, c in self.d[CAPE].items():
            if not isinstance(c, str) or not c.startswith("lotr:cape/"):
                continue
            tex = c.split(":", 1)[1]
            self.copy(f"assets/lotr/{tex}", f"textures/entity/{tex}")
            capes.append({"id": snake(name), "texture": f"{NS}:textures/entity/{tex}"})
        write_json(os.path.join(self.content, "shields.json"), shields)
        write_json(os.path.join(self.content, "capes.json"), capes)
        return len(shields), len(capes)

    def copy(self, src, dst):
        if self.jar.has(src):
            p = os.path.join(self.assets, dst)
            os.makedirs(os.path.dirname(p), exist_ok=True)
            open(p, "wb").write(self.jar.read(src))
        else:
            self.report["extra_missing"].append(src)

    # ------------------------------------------------------------------ drinks

    def drinks(self):
        mug = self.d[MUG]
        vessels = []
        for name, v in _named(self.d[VESSEL]).items():
            if "id" not in v:
                continue
            item = VESSEL_ITEMS.get(name)
            vid = self.item_id({"item": item}) if item else "minecraft:glass_bottle"
            vessels.append({"id": snake(name), "item": vid, "can_place": v.get("canPlace", False),
                            "extra_price": v.get("extraPrice", 0)})
        names = mug.get("strengthNames") or ["weak", "light", "moderate", "strong", "potent"]
        write_json(os.path.join(self.content, "drinks.json"), {
            "strengths": [{"name": n, "lang": f"item.lotr.drink.{n}", "factor": s, "food_factor": f}
                          for n, s, f in zip(names, mug["strengths"], mug["foodStrengths"])],
            "default_strength": 2, "nausea_seconds_per_alcohol": 30, "vessels": vessels})
        by_id = {it["id"]: it for it in self.gen.items}
        n = 0
        for m in self.d["mugs"]:
            if not m.get("isFullMug"):
                continue
            new = self.item_id({"item": m["id"]})
            it = by_id.get(new.split(":", 1)[1])
            if not it:
                self.report["unmapped_drinks"].append(m["id"])
                continue
            effects = []
            for p in m.get("potionEffects") or []:
                eff = POTIONS.get(p["potion"])
                if eff:
                    effects.append({"id": f"minecraft:{eff}", "duration": p["duration"], "amplifier": p["amplifier"]})
                else:
                    self.report["unmapped_potions"].append(p["potion"])
            it["kind"] = "drink"
            it["drink"] = {"heal": m.get("foodHealAmount", 0), "saturation": m.get("foodSaturationAmount", 0),
                           "alcohol": m.get("alcoholicity", 0), "effects": effects,
                           "damage": m.get("damageAmount", 0), "cures": m.get("curesEffects", False),
                           "brewed": m.get("isBrewable", False), "food_drink": m.get("isFoodDrink", False)}
            n += 1
        return n

    # ------------------------------------------------------------------ dyes

    # LOTR's dye recipes (LOTRRecipePartyHatDye, Pouch, FeatherDye, LeatherHatDye, HaradRobesDye) as vanilla's
    # armour-dye recipe: items in #minecraft:dyeable take the dyed_color component; armour layers tint
    DYEABLE = ("party_hat", "small_pouch", "medium_pouch", "large_pouch", "dyed_feather", "leather_hat", "harad_robe",
               "harad_robe_leggings", "harad_robe_shoes", "harad_turban")

    def dyeable(self):
        n = 0
        for it in self.gen.items:
            if it["id"] in self.DYEABLE:
                it["dyeable"] = True
                if it.get("armor"):
                    it["armor"]["dyeable"] = True
                self.gen.tags["minecraft:item/dyeable"].add(it["id"])
                n += 1
        return n

    # ------------------------------------------------------------------ calendar, music

    def calendar(self):
        date = self.d[DATE]
        months = []
        for name, m in _named(self.d[MONTH]).items():
            if "days" not in m:
                continue
            months.append({"id": m["name"], "lang": f"lotr.date.shire.month.{m['name']}", "days": m["days"],
                           "weekdays": m.get("hasWeekdayName", True), "leap_only": m.get("isLeapYear", False),
                           "season": m["season"]["name"] if isinstance(m.get("season"), dict) else None})
        days = [{"id": d["_name"].lower(), "lang": f"lotr.date.shire.day.{d['_name'].lower()}"}
                for name, d in _named(self.d[DAY]).items() if "_name" in d]
        write_json(os.path.join(self.content, "calendar.json"), {
            # the port keeps vanilla's 24000-tick day (LOTR's Middle-earth day was ticksInDay long)
            "ticks_per_day": 24000, "lotr_ticks_per_day": date["ticksInDay"], "start_year": date["THIRD_AGE_CURRENT"],
            "shire_offset": date["SR_TO_TA"], "start_day": 0, "months": months, "weekdays": days})

    def biome_data(self):
        """Per-biome tables, after the NPC types exist: music regions, biome achievements, invasions, animals."""
        if not self.d or not self.d.get("biomes"):
            return {}
        from npcs import type_id
        npcs = json.load(open(os.path.join(os.path.dirname(__file__), "export", "npcs.json")))["entities"]
        by_class = {}
        for e in npcs:
            t = type_id(e["name"])
            if os.path.exists(os.path.join(self.data, NS, "npc", t + ".json")):
                by_class.setdefault(e.get("class"), f"{NS}:{t}")
        music, achievements, invasions, animals = {}, {}, {}, 0
        for b in self.d["biomes"]:
            bid = snake(b.get("biomeName") or "")
            if not bid:
                continue
            m = b.get("getBiomeMusic")
            if isinstance(m, dict) and isinstance(m.get("region"), dict):
                music[bid] = {"region": snake(m["region"]["regionName"]), "sub": snake(m.get("subregion") or "")}
            a = b.get("getBiomeAchievement")
            if isinstance(a, dict) and a.get("name"):
                achievements[bid] = a["name"]
            rows = []
            for pair in (b.get("invasionSpawns") or {}).get("invasionsByChance") or []:
                if len(pair) == 2 and isinstance(pair[0], dict):
                    names = [x["_name"] if isinstance(x, dict) else x for x in pair[1]]
                    rows.append({"chance_per_second": pair[0]["chancePerSecond"], "invasions": [
                        f"{NS}:{snake(n)}" for n in names]})
            if rows:
                invasions[bid] = rows
            # animals: LOTR's creature list, vanilla ones as themselves, LOTR's as their creature NPC type
            spawn = []
            for s in b.get("spawnableCreatureList") or []:
                if not isinstance(s, dict):
                    continue
                cls = s["entity"]
                if cls.startswith("net.minecraft.entity."):
                    vid = VANILLA_ANIMALS.get(cls.split(".")[-1])
                    if vid:
                        spawn.append({"type": vid, "weight": s["weight"], "minCount": s["min"],
                                      "maxCount": s["max"]})
                elif cls.split(".")[-1] in STAND_INS:
                    spawn.append({"type": STAND_INS[cls.split(".")[-1]], "weight": s["weight"], "minCount": s["min"],
                                  "maxCount": s["max"]})
                elif by_class.get(cls):
                    spawn.append({"npc": by_class[cls], "weight": s["weight"], "min": s["min"], "max": s["max"]})
                else:
                    self.report["unmapped_animals"].append(cls)
            vanilla = [x for x in spawn if "type" in x]
            lotr = [x for x in spawn if "npc" in x]
            path = os.path.join(self.data, NS, "worldgen", "biome", bid + ".json")
            if os.path.exists(path):
                biome = json.load(open(path))
                biome["spawners"] = {"creature": vanilla} if vanilla else {}
                biome["effects"].pop("music", None)  # client/LotrMusic plays playlists instead
                write_json(path, biome)
            if lotr:
                write_json(os.path.join(self.data, NS, "npc_animals", NS, bid + ".json"),
                           {"entries": lotr, "cap": 4, "chance": 0.1, "interval": 400})
                animals += 1
        for name, inv in _named(self.d["lotr.common.world.spawning.LOTRInvasions"]).items():
            if "invasionMobs" not in inv:
                continue
            mobs = []
            for m in inv["invasionMobs"] or []:
                t = by_class.get(m.get("entityClass")) if isinstance(m, dict) else None
                if t:
                    mobs.append(t)
            fac = inv.get("invasionFaction")
            write_json(os.path.join(self.data, NS, "invasion", snake(name) + ".json"), {
                "faction": self.faction(fac) if fac else None, "mobs": mobs,
                "icon": self.item_id(inv.get("invasionIcon")), "lang": f"lotr.invasion.{name}"})
        write_json(os.path.join(self.data, NS, "invasion_spawns", "biomes.json"), invasions)
        events = json.load(open(os.path.join(self.content, "sounds.json")))
        sounds = json.load(open(os.path.join(self.assets, "sounds.json")))
        for region in sorted({m["region"] for m in music.values()}):
            events.append({"id": f"music.{region}", "category": "music"})
            sounds[f"music.{region}"] = {"sounds": []}
        write_json(os.path.join(self.content, "sounds.json"), events)
        write_json(os.path.join(self.assets, "sounds.json"), sounds)
        write_json(os.path.join(self.content, "music.json"), music)
        # client/LotrMusic: each biome plays its LOTR music region's playlist. LOTR shipped no tracks; a resource
        # pack lists sound events (defined in its own sounds.json) under "playlists" and may override any biome.
        write_json(os.path.join(self.assets, "music", "playlists.json"), {
            "biomes": {b: m["region"] for b, m in sorted(music.items())},
            "playlists": {r: [] for r in sorted({m["region"] for m in music.values()})},
            "transition": {"settle_ticks": 100, "fade_out_ticks": 100, "fade_in_ticks": 80, "gap_min_ticks": 400,
                           "gap_max_ticks": 1800, "crossfade_delay_ticks": 20, "keep_shared_track": True,
                           "volume": 1.0},
            "mood": {"fight_range": 16, "suspense_range": 32, "fight_hold_ticks": 400, "suspense_hold_ticks": 300,
                     "fight_settle_ticks": 10, "mood_settle_ticks": 40, "fight_fade_in_ticks": 30,
                     "fight_fade_out_ticks": 40, "fight_gap_ticks": 40, "hurt_starts_fight": 1,
                     "night_is_suspense": 1, "storm_is_suspense": 1, "underground_is_suspense": 1,
                     "underground_depth": 8, "low_health_percent": 35,
                     # places (client/LotrMusic): playlists "road" and "underground", or "<land>_road" etc.
                     "road_range": 16, "road_leave_range": 40, "road_height": 12, "place_settle_ticks": 100}})
        write_json(os.path.join(self.content, "biome_achievements.json"), achievements)
        return {"music_biomes": len(music), "biome_achievements": len(achievements),
                "invasion_biomes": len(invasions), "animal_biomes": animals}

    # ------------------------------------------------------------------ lang

    def lang(self):
        for code in self.jar.langs():
            src = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for k, v in src.items():
                if k.startswith(LANG_PREFIXES):
                    dst[k] = v
