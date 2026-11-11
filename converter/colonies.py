"""
RingRealms colonies: who migrates into each faction's colonies, and the English text of the colony, quest, travel
and map screens (the mod's own strings, so they sit in the generated lang files beside LOTR's).

  data/lotr_port/colony_migration/<faction>.json   NPC types (not traders, captains, leaders or rare races), weighted

Rare races (data/lotr_port/recruit/*.json, hand-written in lotr/src/main/resources) never migrate.
"""
import json
import os
import re

from assets import NS, write_json

NOT_SETTLERS = re.compile(r"banner_bearer|captain|chieftain|lord|warlord|marshal|commander|champion|slaver|renegade"
                          r"|bandit|scrap_trader|spy|brute|gandalf|saruman|tormented|slave")
FIGHTERS = re.compile(r"warrior|soldier|archer|guard|levyman|berserker|axe|scout|marine|knight|uruk|bombardier"
                      r"|crossbower|sapper|thrower|blowgunner|warden|shirriff|ranger")

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
}


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
            if not fac or tid in rare or NOT_SETTLERS.search(tid) or any(k in t for k in ("buy", "sell", "units")):
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
        en = self.gen.lang["en_us"]
        for k, v in UI_EN.items():
            en.setdefault(k, v)
        return {"migration_tables": len(tables), "claim_banners": banners}
