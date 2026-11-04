"""
LOTR's waypoints and mini-quests (quests.json from the exporter) as data for the port:

  data/lotr_port/waypoint/<id>.json                          position, map pixel, region, faction, hidden
  data/lotr_port/tags/worldgen/biome/waypoint_region/<r>.json biomes that unlock region r when entered
  data/lotr_port/fast_travel/settings.json                    LOTR's cooldown range (seconds)
  data/lotr_port/quest_pool/<group>.json                      collect and kill quests per LOTR quest group
  assets/lotr_port/textures/gui/map.png                       LOTR's map image, for the map screen
  assets/lotr_port/textures/gui/quest/*.png                   LOTR's Red Book and quest offer pages
  assets/lotr_port/textures/gui/map/*.png                     LOTR's map icons, compass and parchment overlay
  lang: waypoint.lotr_port.<id>, waypoint_region.lotr_port.<r>

NPC types whose LOTR entity gives quests get "quests": "lotr_port:<group>". Bounty, retrieve and pickpocket quests
need their own code (named targets, lost items) and are left out (report: quests_not_ported).
"""
import os

from classfile import ClassFile

from assets import NS, snake, write_json
from factions import faction_id
from npcs import type_id

# LOTR's map screen text -> the port's keys (the screen reads them with English fallbacks)
MAP_TEXT = {f"lotr.gui.map.{k}": f"screen.{NS}.map.{v}" for k, v in {
    "title": "title", "coords": "coords", "tp": "tp", "fastTravel.prompt": "travel_prompt",
    "fastTravel.moreTime": "travel_wait", "locked.region": "locked_region",
    "widget.toggleWPs": "widget.waypoints", "widget.zoomIn": "widget.zoom_in", "widget.zoomOut": "widget.zoom_out",
    "widget.fullScreen": "widget.fullscreen", "widget.sepia": "widget.sepia", "widget.labels": "widget.labels",
}.items()}

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
        # the Red Book and quest offer pages, and the map screen's icons and parchment overlay
        for src in ("gui/quest/redBook", "gui/quest/redBook_miniquests", "gui/quest/miniquest", "map/mapScreen",
                    "map/mapOverlay"):
            folder, name = src.rsplit("/", 1)
            src = f"assets/lotr/{src}.png"
            if self.jar.has(src):
                file = "".join("_" + c.lower() if c.isupper() else c for c in name)
                sub = "quest" if folder.endswith("quest") else "map"
                dst = os.path.join(self.assets, "textures", "gui", sub, file + ".png")
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, "wb") as fh:
                    fh.write(self.jar.read(src))
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

    def map_labels(self):
        """LOTR's map labels (region and sea names drawn over the map), read from the constants its label table
        passes in its class file: name (a lang key or a biome), map pixel, scale, angle and the zoom range (powers of
        two) it shows at. Written to assets/lotr_port/map/labels.json with lang keys map_label.lotr_port.<id>."""
        src = "lotr/client/gui/LOTRMapLabels.class"
        if not self.jar.has(src):
            self.report["map_labels_missing"].append(src)
            return
        biomes = {snake(b["fields"]["biomeName"]) for b in self.world.get("biomes", [])
                  if "biomeName" in b.get("fields", {})}
        labels, keys = [], {}
        for args in ClassFile(self.jar.read(src)).enum_entries():
            if len(args) != 9:
                self.report["map_labels_unread"].append(str(args[:1]))
                continue
            const, _, name, x, y, scale, angle, lo, hi = args
            out = {"id": snake(const), "x": x, "y": y, "scale": scale, "angle": angle, "min_zoom": lo, "max_zoom": hi}
            if isinstance(name, tuple):
                bid = snake(name[2])
                if bid not in biomes:
                    self.report["map_labels_unknown_biome"].append(name[2])
                    continue
                out["biome"] = f"{NS}:{bid}"
            else:
                keys[snake(const)] = f"lotr.map.{name}"
            labels.append(out)
        write_json(os.path.join(self.assets, "map", "labels.json"), {"labels": labels})
        for code in self.jar.langs():
            src_lang = self.jar.lang(code)
            dst = self.gen.lang[code.lower()]
            for lid, old in keys.items():
                if old in src_lang:
                    dst[f"map_label.{NS}.{lid}"] = src_lang[old]
            # the map screen's own text, as LOTR words it
            for old, new in MAP_TEXT.items():
                if old in src_lang:
                    dst[new] = src_lang[old].replace("%d", "%s")  # Minecraft formats only %s

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
        self.map_labels()
        pools, givers = self.pools()
        return {"waypoints": waypoints, "quest_pools": pools, "quest_givers": givers}
