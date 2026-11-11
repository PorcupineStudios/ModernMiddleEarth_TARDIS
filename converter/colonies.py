"""
RingRealms colonies: who migrates into each faction's colonies, and the English text of the colony, quest, travel
and map screens (the mod's own strings, so they sit in the generated lang files beside LOTR's).

  data/lotr_port/colony_migration/<faction>.json   NPC types (not traders, captains, leaders or rare races), weighted
  data/lotr_port/tags/block/colony/<counter>.json  blocks a building's room counts (beds, chests, tables...), read by
                                                   colony_building requirements

Rare races (data/lotr_port/recruit/*.json, hand-written in lotr/src/main/resources) never migrate.
"""
import json
import os
import re

from assets import NS, write_json

NOT_SETTLERS = re.compile(r"banner_bearer|captain|chieftain|lord|warlord|marshal|commander|champion|slaver|renegade"
                          r"|bandit|scrap_trader|spy|brute|gandalf|saruman|tormented|slave|wraith|wight|balrog|gollum")
FIGHTERS = re.compile(r"warrior|soldier|archer|guard|levyman|berserker|axe|scout|marine|knight|uruk|bombardier"
                      r"|crossbower|sapper|thrower|blowgunner|warden|shirriff|ranger")

# first match wins: half-trolls before orcs (no word clash today, but order is the rule)
RACES = [
    ("half_troll", re.compile(r"half_troll")),
    ("hobbit", re.compile(r"hobbit")),
    ("dwarf", re.compile(r"dwarf")),
    ("elf", re.compile(r"elf|galadhrim|rivendell")),
    ("goblin", re.compile(r"goblin")),
    ("orc", re.compile(r"orc|uruk|snaga")),
]

UI_EN = {
    "key.lotr_port.map": "Middle-earth Map",
    "key.lotr_port.jukebox": "Jukebox (music here)",
    "key.lotr_port.map_teleport": "Map Teleport",
    "key.lotr_port.fast_travel": "Fast Travel",
    "key.lotr_port.units": "Units (hired and colony soldiers)",
    "key.categories.lotr_port": "The Lord of the Rings",
    "screen.lotr_port.map": "Middle-earth",
    "item.lotr_port.colony_charter": "Colony Charter",
    "itemGroup.lotr_port.colonies": "Colonies",
    "item.lotr_port.building_writ": "Building Writ",
}

BUILDINGS_EN = {
    "house": "House", "storehouse": "Storehouse", "lumber_camp": "Lumber Camp", "farmstead": "Farmstead",
    "kitchen": "Kitchen", "market": "Market", "tavern": "Tavern", "smeltery": "Smeltery", "workshop": "Workshop",
    "smithy": "Smithy", "masonry": "Masonry", "brewery": "Brewery", "mill": "Mill", "library": "Library",
    "barracks": "Barracks",
}

JOBS_EN = {
    "courier": "Courier", "forester": "Forester", "farmer": "Farmer", "cook": "Cook", "merchant": "Merchant",
    "innkeeper": "Innkeeper", "smelter": "Smelter", "carpenter": "Carpenter", "smith": "Smith", "mason": "Mason",
    "brewer": "Brewer", "miller": "Miller", "researcher": "Researcher",
}


def opt(tag):
    """A tag entry that may be missing (another mod's tag)."""
    return {"id": tag, "required": False}


class Colonies:
    def __init__(self, gen, data_dir, npc_types, recruit_dir):
        self.gen = gen
        self.data = os.path.join(data_dir, NS)
        self.types = npc_types
        self.recruit_dir = recruit_dir

    def rare(self):
        out = set()
        if os.path.isdir(self.recruit_dir):
            for f in os.listdir(self.recruit_dir):
                with open(os.path.join(self.recruit_dir, f), encoding="utf-8") as fh:
                    out.add(json.load(fh)["npc"].split(":")[1])
        return out

    def run(self):
        rare = self.rare()
        tables = {}
        for tid, t in sorted(self.types.items()):
            fac = (t.get("faction") or "").split(":")[-1]
            # creatures (wargs, spiders, trolls, ents) are drawn on their own model: never colonists
            if not fac or tid in rare or NOT_SETTLERS.search(tid) or ":" in (t.get("model") or "") \
                    or any(k in t for k in ("buy", "sell", "units")):
                continue
            tables.setdefault(fac, []).append({"npc": f"{NS}:{tid}", "weight": 1 if FIGHTERS.search(tid) else 3})
        for fac, entries in tables.items():
            write_json(os.path.join(self.data, "colony_migration", fac + ".json"), {"entries": entries})
        # LOTR's faction banners claim land (LOTR placed them as protecting banner entities): kind "banner"
        banners = 0
        for it in self.gen.items:
            if it["id"].startswith("banner_of_") and it.get("kind") == "item":
                it["kind"] = "banner"
                banners += 1
        counters = self.counter_tags()
        races = self.races()
        en = self.gen.lang["en_us"]
        for k, v in BUILDINGS_EN.items():
            en.setdefault(f"colony_building.{NS}.{k}", v)
        for k, v in JOBS_EN.items():
            en.setdefault(f"colony_job.{NS}.{k}", v)
        for k, v in UI_EN.items():
            en.setdefault(k, v)
        return {"migration_tables": len(tables), "claim_banners": banners, "room_counters": counters, "colony_races": races}

    def races(self):
        """Each person type's race, for job proficiency (RingRealms' races): colony_race/<race>.json lists its types;
        a type in no list is a man."""
        out = {}
        for tid, t in sorted(self.types.items()):
            if ":" in (t.get("model") or ""):
                continue
            race = next((r for r, rx in RACES if rx.search(tid)), None)
            if race:
                out.setdefault(race, []).append(f"{NS}:{tid}")
        for race, types in out.items():
            write_json(os.path.join(self.data, "colony_race", race + ".json"), {"types": types})
        return {r: len(v) for r, v in out.items()}

    def counter_tags(self):
        """What a colony room counts (RingRealms' Validator counts), from the block kinds and machines."""
        blocks = self.gen.blocks
        ids = lambda f: sorted(f"{NS}:{b['id']}" for b in blocks if f(b))
        machine = lambda *m: ids(lambda b: b.get("machine") in m)
        tags = {
            "beds": ["#minecraft:beds"] + ids(lambda b: b["kind"] == "bed"),
            "chests": [opt("#c:chests"), "minecraft:barrel"] + machine("chest"),
            "faction_tables": ids(lambda b: b["kind"] == "faction_table"),
            "tables": ["minecraft:crafting_table", f"#{NS}:colony/faction_tables"],
            "furnaces": ["minecraft:furnace", "minecraft:smoker", "minecraft:blast_furnace"] + machine("oven"),
            "smelters": ["minecraft:furnace", "minecraft:blast_furnace"] + machine("forge"),
            "kegs": ids(lambda b: b["kind"] == "keg"),
            "millstones": machine("millstone"),
            "bookshelves": ["minecraft:bookshelf", "minecraft:chiseled_bookshelf"]
                           + ids(lambda b: b["id"] == "bookshelf_storage"),
            "bars": ["minecraft:iron_bars"] + ids(lambda b: b["id"].endswith("_bars")),
            "racks": machine("weapon_rack", "armor_stand"),
        }
        for name, values in tags.items():
            write_json(os.path.join(self.data, "tags", "block", "colony", name + ".json"),
                       {"replace": False, "values": values})
        return len(tags)
