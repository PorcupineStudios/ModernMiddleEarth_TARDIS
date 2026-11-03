"""
LOTR's waypoints and mini-quests (quests.json from the exporter) as data for the port:

  data/lotr_port/waypoint/<id>.json                          position, map pixel, region, faction, hidden
  data/lotr_port/tags/worldgen/biome/waypoint_region/<r>.json biomes that unlock region r when entered
  data/lotr_port/fast_travel/settings.json                    LOTR's cooldown range (seconds)
  data/lotr_port/quest_pool/<group>.json                      collect and kill quests per LOTR quest group
  assets/lotr_port/textures/gui/map.png                       LOTR's map image, for the map screen
  lang: waypoint.lotr_port.<id>, waypoint_region.lotr_port.<r>

NPC types whose LOTR entity gives quests get "quests": "lotr_port:<group>". Bounty, retrieve and pickpocket quests
need their own code (named targets, lost items) and are left out (report: quests_not_ported).
"""
import os

from assets import NS, snake, write_json
from factions import faction_id
from npcs import type_id

# by quest factory class (the factories map is keyed by the quest class, which is the same for both kill kinds)
KINDS = {"QFCollect": "collect", "QFKillFaction": "kill_faction", "QFKillEntity": "kill_npc"}


class Quests:
    def __init__(self, gen, export, npc_export, world, data_dir, assets_dir, jar, npcs):
        self.gen = gen
        self.export = export or {}
        self.npc_export = npc_export
        self.world = world
        self.data = os.path.join(data_dir, NS)
        self.assets = assets_dir
        self.jar = jar
        self.npcs = npcs  # the Npcs generator: item mapping and the written NPC types
        self.report = gen.report

    def factions(self):
        return {f[:-5] for f in os.listdir(os.path.join(self.data, "faction"))}

    # ------------------------------------------------------------------ waypoints

    def waypoints(self):
        factions = self.factions()
        names = {}
        regions = set()
        for wp in self.export.get("waypoints", []):
            wid = snake(wp["code"])
            region = snake(wp.get("region") or "none")
            out = {"x": wp["x"], "z": wp["z"], "map_x": wp.get("imgX", 0), "map_z": wp.get("imgY", 0),
                   "region": region}
            fac = faction_id(wp["faction"]) if wp.get("faction") else None
            if fac in factions:
                out["faction"] = f"{NS}:{fac}"
            if wp.get("hidden"):
                out["hidden"] = True
            write_json(os.path.join(self.data, "waypoint", wid + ".json"), out)
            names[wid] = wp["code"]
            regions.add(region)
        biomes_of = {}
        for b in self.world.get("biomes", []):
            if "biomeName" in b.get("fields", {}) and b.get("waypointRegion"):
                biomes_of.setdefault(snake(b["waypointRegion"]), set()).add(f"{NS}:{snake(b['fields']['biomeName'])}")
        for region, biomes in biomes_of.items():
            write_json(os.path.join(self.data, "tags", "worldgen", "biome", "waypoint_region", region + ".json"),
                       {"values": sorted(biomes)})
        for region in sorted(regions - set(biomes_of)):
            self.report["waypoint_regions_without_biomes"].append(region)
        cd = self.export.get("cooldown", {})
        settings = {}
        # the live values are 0 until LOTR reads its config in a world: use its defaults then
        lo = cd.get("waypointCooldownMin") or cd.get("WAYPOINT_COOLDOWN_MIN_DEFAULT")
        hi = cd.get("waypointCooldownMax") or cd.get("WAYPOINT_COOLDOWN_DEFAULT")
        if lo:
            settings["cooldown_min"] = lo
        if hi:
            settings["cooldown_max"] = hi
        write_json(os.path.join(self.data, "fast_travel", "settings.json"), settings)
        self.waypoint_lang(names, regions)
        if self.jar.has("assets/lotr/map/map.png"):
            dst = os.path.join(self.assets, "textures", "gui", "map.png")
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as fh:
                fh.write(self.jar.read("assets/lotr/map/map.png"))
        return len(names)

    def waypoint_lang(self, names, regions):
        for code in self.jar.langs():
            src = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for wid, old in names.items():
                if f"lotr.waypoint.{old}" in src:
                    dst[f"waypoint.{NS}.{wid}"] = src[f"lotr.waypoint.{old}"]
                if f"lotr.waypoint.{old}.info" in src:
                    dst[f"waypoint.{NS}.{wid}.info"] = src[f"lotr.waypoint.{old}.info"]
        en = self.gen.lang["en_us"]
        for wid, old in names.items():
            en.setdefault(f"waypoint.{NS}.{wid}", old.replace("_", " ").title())
        for region in regions:
            en.setdefault(f"waypoint_region.{NS}.{region}", region.replace("_", " ").title())
        en["key.lotr_port.map"] = "Middle-earth Map"
        en["key.categories.lotr_port"] = "The Lord of the Rings"
        en["screen.lotr_port.map"] = "Middle-earth"

    # ------------------------------------------------------------------ quests

    def quest(self, q, factions, types):
        kind = KINDS.get(q.get("factory"))
        if kind == "collect":
            target = self.npcs.item(q.get("collectItem"))
        elif kind == "kill_faction":
            fac = faction_id(q["killFaction"]) if q.get("killFaction") else None
            target = f"{NS}:{fac}" if fac in factions else None
        elif kind == "kill_npc":
            tid = type_id(q.get("entityType") or "")
            target = f"{NS}:{tid}" if tid in types else None
        else:
            self.report["quests_not_ported"].append(f"{q.get('kind')} {q.get('questName')}")
            return None
        if not target:
            self.report["quest_targets_unmapped"].append(f"{q.get('kind')} {q.get('questName')}")
            return None
        lo = max(1, q.get("minTarget") or 1)
        return {"kind": kind, "target": target, "min": lo, "max": max(lo, q.get("maxTarget") or lo),
                "reward": round(q.get("rewardFactor") or 1.0, 3)}

    def pools(self):
        factories = self.export.get("factories", {})
        class_weights = factories.get("_classWeights", {})
        factions = self.factions()
        types = self.npcs.written
        written = set()
        for name, f in sorted(factories.items()):
            if name.startswith("_"):
                continue
            entries = []
            per_class = {}
            for q in f.get("quests", []):
                per_class[q.get("kind")] = per_class.get(q.get("kind"), 0) + 1
            for q in f.get("quests", []):
                e = self.quest(q, factions, types)
                if e:
                    # LOTR picks a quest class by its weight, then a quest of that class
                    e["weight"] = max(1, round(100 * (class_weights.get(q["kind"]) or 1) / per_class[q["kind"]]))
                    entries.append(e)
            if entries:
                pid = snake(name)
                write_json(os.path.join(self.data, "quest_pool", pid + ".json"), {"quests": entries})
                written.add(pid)
        givers = 0
        for e in self.npc_export.get("entities", []):
            tid = type_id(e["name"])
            group = snake(e.get("questGroup") or "")
            if tid in types and group in written:
                types[tid]["quests"] = f"{NS}:{group}"
                write_json(os.path.join(self.data, "npc", tid + ".json"), types[tid])
                givers += 1
        return len(written), givers

    def run(self):
        if not self.export:
            self.report["quests_not_exported"].append("quests.json")
            return {"waypoints": 0, "quest_pools": 0}
        waypoints = self.waypoints()
        pools, givers = self.pools()
        return {"waypoints": waypoints, "quest_pools": pools, "quest_givers": givers}
