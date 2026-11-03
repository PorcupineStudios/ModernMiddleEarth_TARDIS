"""
LOTR's structures (structures.json.gz from the exporter: each structure built with three seeds on a flat world) and
biome NPC spawns (world.json) as 1.21 data:

  data/lotr_port/structure/<id>/<n>.nbt                       one template per layout (blocks, chests, NPCs)
  data/lotr_port/worldgen/template_pool/<id>.json             the layouts, one picked per structure
  data/lotr_port/worldgen/structure/<id>.json                 a jigsaw structure of that pool, set into the ground
  data/lotr_port/worldgen/structure_set/<id>.json             how often (from LOTR's chance per chunk)
  data/lotr_port/tags/worldgen/biome/has_structure/<id>.json  the biomes LOTR builds it in
  data/lotr_port/tags/worldgen/structure/faction/<f>.json     a faction's structures (for checks and maps)
  data/lotr_port/npc_spawns/lotr_port/<biome>.json            who wanders the biome

Block states come from the id table plus the old metadata (stairs facing, slab halves, log axes, doors, gates,
ladders, persistent leaves). NPCs LOTR placed, or its spawners would make, become NPCs in the template.
"""
import gzip
import json
import math
import os
import re
import zlib

import nbt
from assets import NS, snake, write_json
from vanilla import BY_META, RENAMED

DATA_VERSION = 3955  # 1.21.1
STAIRS_FACING = ["east", "west", "south", "north"]
GATE_FACING = ["south", "west", "north", "east"]
DOOR_FACING = ["east", "south", "west", "north"]
TRAPDOOR_FACING = ["north", "south", "west", "east"]
LADDER_FACING = {2: "north", 3: "south", 4: "west", 5: "east"}
TORCH_FACING = {1: "east", 2: "west", 3: "south", 4: "north"}
VANILLA_KIND = {"stairs": "stairs", "_slab": "slab", "log": "pillar", "log2": "pillar", "fence_gate": "fence_gate",
                "wooden_door": "door", "iron_door": "door", "trapdoor": "trapdoor", "ladder": "ladder",
                "leaves": "leaves", "leaves2": "leaves", "torch": "torch", "hay_block": "pillar"}
VANILLA_BLOCKS = {
    "minecraft:stone_stairs": "cobblestone_stairs", "minecraft:stone_slab": None, "minecraft:wooden_slab": None,
    "minecraft:double_stone_slab": "smooth_stone", "minecraft:double_wooden_slab": None,
    "minecraft:fence": "oak_fence", "minecraft:fence_gate": "oak_fence_gate", "minecraft:wooden_door": "oak_door",
    "minecraft:trapdoor": "oak_trapdoor", "minecraft:wooden_pressure_plate": "oak_pressure_plate",
    "minecraft:wooden_button": "oak_button", "minecraft:carpet": None, "minecraft:stained_hardened_clay": None,
    "minecraft:lit_pumpkin": "jack_o_lantern", "minecraft:snow_layer": "snow", "minecraft:snow": "snow_block",
    "minecraft:tallgrass": "short_grass", "minecraft:yellow_flower": "dandelion", "minecraft:red_flower": "poppy",
    "minecraft:waterlily": "lily_pad", "minecraft:flowing_water": "water", "minecraft:flowing_lava": "lava",
    "minecraft:mob_spawner": None, "minecraft:bed": "red_bed", "minecraft:lit_furnace": "furnace",
    "minecraft:stonebrick": None, "minecraft:monster_egg": None, "minecraft:quartz_block": "quartz_block",
    "minecraft:nether_brick": "nether_bricks", "minecraft:melon_block": "melon", "minecraft:mossy_cobblestone":
    "mossy_cobblestone", "minecraft:cobblestone_wall": "cobblestone_wall", "minecraft:iron_bars": "iron_bars",
    "minecraft:glass_pane": "glass_pane", "minecraft:stained_glass_pane": None, "minecraft:cauldron": "cauldron",
    "minecraft:flower_pot": "flower_pot", "minecraft:skull": "skeleton_skull", "minecraft:portal": None,
}
SLABS = ["smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab",
         "nether_brick_slab", "quartz_slab"]
WOOD_SLABS = ["oak_slab", "spruce_slab", "birch_slab", "jungle_slab", "acacia_slab", "dark_oak_slab"]
COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
MAX_NPCS_PER_SPAWNER = 3


def structure_id(cls):
    return snake(cls.removeprefix("LOTRWorldGen"))


class Structures:
    def __init__(self, gen, export_dir, world, data_dir, npc_types):
        self.gen = gen
        self.export_dir = export_dir
        self.world = world
        self.data = os.path.join(data_dir, NS)
        self.npc_types = npc_types
        self.report = gen.report
        self.kinds = {b["id"]: b["kind"] for b in gen.blocks}

    # ------------------------------------------------------------------ block states

    def lookup(self, old, meta):
        """The new block for an old id and metadata, and the metadata bits left for its state."""
        for m in (meta, meta & 7, meta & 3, 0):
            new = self.gen.idtable.get(f"{old}#{m}")
            if new:
                return new, meta
        return None, meta

    def state(self, key):
        """'lotr:tile.x#meta' -> {'Name': ..., 'Properties': {...}} or None."""
        old, meta = key.rsplit("#", 1)
        meta = int(meta)
        if old.startswith("minecraft:"):
            return self.vanilla_state(old, meta)
        new, meta = self.lookup(old, meta)
        if not new:
            return None
        props = {}
        if "[" in new:
            new, p = new[:-1].split("[")
            props.update(dict(kv.split("=") for kv in p.split(",")))
        kind = self.kinds.get(new.split(":", 1)[1])
        props.update(self.props(kind, meta, props))
        return {"Name": new, **({"Properties": props} if props else {})}

    def props(self, kind, meta, existing):
        if kind == "stairs":
            return {"facing": STAIRS_FACING[meta & 3], "half": "top" if meta & 4 else "bottom"}
        if kind == "slab" and "type" not in existing:
            return {"type": "top" if meta & 8 else "bottom"}
        if kind == "pillar":
            return {"axis": {0: "y", 4: "x", 8: "z"}.get(meta & 12, "y")}
        if kind == "leaves":
            return {"persistent": "true"}
        if kind == "fence_gate":
            return {"facing": GATE_FACING[meta & 3], "open": str(bool(meta & 4)).lower()}
        if kind == "trapdoor":
            return {"facing": TRAPDOOR_FACING[meta & 3], "open": str(bool(meta & 4)).lower(),
                    "half": "top" if meta & 8 else "bottom"}
        if kind == "door":
            if meta & 8:
                return {"half": "upper", "hinge": "right" if meta & 1 else "left"}
            return {"half": "lower", "facing": DOOR_FACING[meta & 3], "open": str(bool(meta & 4)).lower()}
        if kind == "ladder":
            return {"facing": LADDER_FACING.get(meta, "north")}
        return {}

    def vanilla_state(self, old, meta):
        name = old.split(":", 1)[1]
        if old in BY_META:
            names = BY_META[old]
            base = meta & 3 if old in ("minecraft:log", "minecraft:log2", "minecraft:leaves",
                                       "minecraft:leaves2") else meta
            if base >= len(names):
                return None
            new = names[base]
        elif old == "minecraft:stone_slab":
            new = SLABS[meta & 7]
        elif old == "minecraft:wooden_slab":
            new = WOOD_SLABS[(meta & 7) % 6]
        elif old == "minecraft:double_wooden_slab":
            return {"Name": "minecraft:" + WOOD_SLABS[(meta & 7) % 6], "Properties": {"type": "double"}}
        elif old in ("minecraft:carpet", "minecraft:stained_glass_pane"):
            new = f"{COLORS[meta & 15]}_{'carpet' if 'carpet' in old else 'stained_glass_pane'}"
        elif old in VANILLA_BLOCKS:
            new = VANILLA_BLOCKS[old]
            if new is None:
                return None
        elif old in RENAMED:
            new = RENAMED[old]
        else:
            new = name
        props = {}
        kind = next((k for suffix, k in VANILLA_KIND.items() if name.endswith(suffix) or name == suffix), None)
        if kind == "torch":
            if meta in TORCH_FACING:
                return {"Name": "minecraft:wall_torch", "Properties": {"facing": TORCH_FACING[meta]}}
        elif kind == "slab" or old in ("minecraft:stone_slab", "minecraft:wooden_slab"):
            props = {"type": "top" if meta & 8 else "bottom"}
        elif kind:
            props = self.props(kind, meta, {})
        if new in ("chest", "furnace", "trapped_chest") and 2 <= meta <= 5:
            props = {"facing": LADDER_FACING[meta]}
        return {"Name": "minecraft:" + new, **({"Properties": props} if props else {})}

    def item(self, it):
        new = self.gen.idtable.get(f"{it['item']}#{it.get('meta', 0)}") or self.gen.idtable.get(f"{it['item']}#0")
        if new:
            return new.split("[")[0]
        if it["item"].startswith("minecraft:"):
            old = it["item"]
            if old in BY_META:
                names = BY_META[old]
                return "minecraft:" + names[it.get("meta", 0)] if it.get("meta", 0) < len(names) else None
            return "minecraft:" + RENAMED.get(old, old.split(":", 1)[1])
        return None

    # ------------------------------------------------------------------ templates

    def template(self, sid, n, layout, base_y):
        lo = layout["min"]
        hi = layout["max"]
        palette_keys = layout["palette"]
        states = [self.state(k) for k in palette_keys]
        for k, s in zip(palette_keys, states):
            if s is None:
                self.report["structure_blocks_unmapped"].append(k)
        chests = {tuple(c["pos"]): c for c in layout.get("chests", [])}
        by_pos = {}
        for x, y, z, p in layout["blocks"]:
            by_pos[(x, y, z)] = p
        palette, index, blocks = [], {}, []

        def state_index(st):
            key = json.dumps(st, sort_keys=True)
            if key not in index:
                index[key] = len(palette)
                palette.append(st)
            return index[key]

        for (x, y, z), p in by_pos.items():
            st = states[p]
            if st is None:
                continue
            if st.get("Properties", {}).get("half") == "upper" and "facing" not in st["Properties"]:
                below = by_pos.get((x, y - 1, z))
                below_state = states[below] if below is not None else None
                if below_state and "facing" in below_state.get("Properties", {}):
                    st = {"Name": st["Name"], "Properties": {**st["Properties"],
                                                             "facing": below_state["Properties"]["facing"],
                                                             "open": below_state["Properties"].get("open", "false")}}
            entry = {"pos": [x - lo[0], y - base_y, z - lo[2]]}
            chest = chests.get((x, y, z))
            if chest is not None:
                items = []
                for slot, it in enumerate(chest["items"]):
                    new = self.item(it)
                    if new:
                        items.append({"Slot": nbt.Byte(slot), "id": new, "count": nbt.Int(it.get("count", 1))})
                if not st["Name"].endswith("chest") and not st["Name"].endswith("barrel"):
                    st = {"Name": "minecraft:chest"}
                entry["nbt"] = {"id": "minecraft:chest", "Items": items}
            entry["state"] = nbt.Int(state_index(st))
            blocks.append(entry)
        entities = []
        for e in layout.get("entities", []):
            for npc, (ex, ey, ez) in self.npcs_of(e):
                rx, ry, rz = ex - lo[0], ey - base_y, ez - lo[2]
                rx = min(max(rx, 0), hi[0] - lo[0])
                rz = min(max(rz, 0), hi[2] - lo[2])
                entities.append({
                    "pos": [nbt.Double(rx), nbt.Double(ry), nbt.Double(rz)],
                    "blockPos": [nbt.Int(math.floor(rx)), nbt.Int(math.floor(ry)), nbt.Int(math.floor(rz))],
                    "nbt": {"id": f"{NS}:npc", "NpcType": f"{NS}:{npc}", "Fresh": nbt.Byte(1),
                            "PersistenceRequired": nbt.Byte(1)},
                })
        size = [hi[0] - lo[0] + 1, hi[1] - base_y + 1, hi[2] - lo[2] + 1]
        path = os.path.join(self.data, "structure", sid, f"{n}.nbt")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        nbt.write(path, {"DataVersion": nbt.Int(DATA_VERSION), "size": [nbt.Int(v) for v in size],
                         "palette": palette, "blocks": blocks, "entities": entities})
        return len(entities)

    def npcs_of(self, e):
        """The NPCs an exported entity becomes: itself, or a few of what a spawner would make."""
        name = e.get("id") or ""
        x, y, z = e["pos"]
        tid = snake(re.sub(r"^(lotr\.)+", "", name))
        if tid in self.npc_types:
            return [(tid, (x, y, z))]
        if name.endswith("NPCRespawner"):
            kinds = [snake(re.sub(r"^(lotr\.)+", "", e[k])) for k in ("spawnClass1", "spawnClass2") if e.get(k)]
            kinds = [k for k in kinds if k in self.npc_types]
            if not kinds:
                self.report["structure_spawners_not_people"].append(str(e.get("spawnClass1")))
                return []
            count = max(1, min(MAX_NPCS_PER_SPAWNER, int(e.get("spawnCap", 2)) // 2))
            return [(kinds[i % len(kinds)], (x + (i % 2) * 1.5 - 0.75, y, z + (i // 2) * 1.5 - 0.75))
                    for i in range(count)]
        return []

    # ------------------------------------------------------------------ worldgen

    def run(self):
        path = os.path.join(self.export_dir, "structures.json.gz")
        if not os.path.exists(path):
            self.report["structures_not_exported"].append(path)
            return {"structures": 0}
        with gzip.open(path, "rt", encoding="utf-8") as f:
            export = json.load(f)
        for err in export.get("errors", [])[:200]:
            self.report["structure_export_errors"].append(err)
        biomes_of = {}
        chance_of = {}
        for b in self.world["biomes"]:
            if "biomeName" not in b.get("fields", {}):
                continue
            bid = snake(b["fields"]["biomeName"])
            for st in b.get("structures", []):
                cls = (st.get("class") or "").split(".")[-1]
                biomes_of.setdefault(cls, set()).add(bid)
                chance_of[cls] = min(chance_of.get(cls, 10 ** 9), max(1, st.get("chunkChance") or 1))
        count = 0
        with_npcs = 0
        faction_tags = {}
        for cls, layouts in sorted(export.get("structures", {}).items()):
            if cls not in biomes_of:
                self.report["structures_in_no_biome"].append(cls)
                continue
            sid = structure_id(cls)
            base_y = min(l["min"][1] for l in layouts)
            ground = layouts[0].get("ground", 3)
            npcs = 0
            for n, layout in enumerate(layouts):
                npcs += self.template(sid, n, layout, base_y)
            self.worldgen(sid, len(layouts), ground - base_y, biomes_of[cls], chance_of[cls], cls)
            count += 1
            if npcs:
                with_npcs += 1
                for e in layouts[0].get("entities", []):
                    for npc, _ in self.npcs_of(e):
                        fac = (self.npc_types[npc].get("faction") or "").split(":")[-1]
                        if fac:
                            faction_tags.setdefault(fac, set()).add(f"{NS}:{sid}")
        for fac, ids in faction_tags.items():
            write_json(os.path.join(self.data, "tags", "worldgen", "structure", "faction", fac + ".json"),
                       {"values": sorted(ids)})
        spawn_tables = self.spawns()
        return {"structures": count, "structures_with_npcs": with_npcs, "npc_spawn_tables": spawn_tables}

    def worldgen(self, sid, layouts, ground, biomes, chunk_chance, cls):
        wg = os.path.join(self.data, "worldgen")
        write_json(os.path.join(wg, "template_pool", sid + ".json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": f"{NS}:{sid}/{n}",
                "processors": "minecraft:empty", "projection": "rigid"}} for n in range(layouts)],
        })
        write_json(os.path.join(wg, "structure", sid + ".json"), {
            "type": "minecraft:jigsaw",
            "biomes": f"#{NS}:has_structure/{sid}",
            "step": "surface_structures",
            "spawn_overrides": {},
            "terrain_adaptation": "beard_thin",
            "start_pool": f"{NS}:{sid}",
            "size": 1,
            # the template's ground layer sits one below the surface
            "start_height": {"absolute": -1 - ground},
            "project_start_to_heightmap": "WORLD_SURFACE_WG",
            "max_distance_from_center": 80,
            "use_expansion_hack": False,
        })
        # LOTR tries each structure in a chunk with chance 1/chunkChance; spacing^2 chunks gives about the same density
        spacing = max(6, min(96, round(math.sqrt(chunk_chance))))
        write_json(os.path.join(wg, "structure_set", sid + ".json"), {
            "structures": [{"structure": f"{NS}:{sid}", "weight": 1}],
            "placement": {"type": "minecraft:random_spread", "spacing": spacing, "separation": max(2, spacing // 3),
                          "salt": zlib.crc32(cls.encode()) & 0x7FFFFFFF},
        })
        write_json(os.path.join(self.data, "tags", "worldgen", "biome", "has_structure", sid + ".json"),
                   {"values": sorted(f"{NS}:{b}" for b in biomes)})

    def spawns(self):
        lists = self.world.get("spawnLists", {})
        written = 0
        for b in self.world["biomes"]:
            if "biomeName" not in b.get("fields", {}):
                continue
            bid = snake(b["fields"]["biomeName"])
            weights = {}
            groups = [g for g in b.get("spawns", []) if g.get("lists")]
            for g in groups:
                gw = max(1, g.get("weight") or 1)
                active = [l for l in g["lists"] if l.get("list") in lists and not (l.get("conquestThreshold") or 0) > 0]
                lw_total = sum(max(1, l.get("weight") or 1) for l in active) or 1
                for l in active:
                    entries = [e for e in lists[l["list"]] if e.get("entity")]
                    ew_total = sum(max(1, e.get("weight") or 1) for e in entries) or 1
                    for e in entries:
                        tid = snake(re.sub(r"^(lotr\.)+", "", e["entity"]))
                        if tid not in self.npc_types:
                            continue
                        share = gw * max(1, l.get("weight") or 1) / lw_total * max(1, e.get("weight") or 1) / ew_total
                        prev = weights.get(tid, (0, e.get("min") or 1, e.get("max") or 1))
                        weights[tid] = (prev[0] + share, prev[1], prev[2])
            if not weights:
                continue
            top = max(w for w, _, _ in weights.values())
            entries = [{"npc": f"{NS}:{t}", "weight": max(1, round(100 * w / top)), "min": mn, "max": max(mn, mx)}
                       for t, (w, mn, mx) in sorted(weights.items())]
            write_json(os.path.join(self.data, "npc_spawns", NS, bid + ".json"), {"entries": entries})
            written += 1
        return written
