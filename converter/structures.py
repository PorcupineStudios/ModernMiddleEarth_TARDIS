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
GATE_DIRECTION = ["down", "up", "north", "south", "west", "east", "north", "north"]
HOOK_FACING = ["south", "west", "north", "east"]  # tripwire hooks, beds, pumpkins: 1.7.10's player-facing order
BUTTON = {0: ("ceiling", "north"), 1: ("wall", "east"), 2: ("wall", "west"), 3: ("wall", "south"),
          4: ("wall", "north"), 5: ("floor", "north")}
LEVER = {0: ("ceiling", "east"), 1: ("wall", "east"), 2: ("wall", "west"), 3: ("wall", "south"),
         4: ("wall", "north"), 5: ("floor", "north"), 6: ("floor", "east"), 7: ("ceiling", "north")}
DOUBLE_PLANTS = ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"]
ANVILS = ["anvil", "chipped_anvil", "damaged_anvil"]
TORCH_FACING = {1: "east", 2: "west", 3: "south", 4: "north"}
# 1.7.10 kept a pot's plant in its tile entity, which the export doesn't carry; LOTR's houses fill pots with flowers,
# so each pot gets one of these, picked from its position (the same every run).
POTTED = ["potted_poppy", "potted_dandelion", "potted_blue_orchid", "potted_allium", "potted_azure_bluet",
          "potted_red_tulip", "potted_orange_tulip", "potted_white_tulip", "potted_pink_tulip", "potted_oxeye_daisy",
          "potted_cornflower", "potted_fern"]
SIDES = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
# vanilla blocks that aren't a full solid side (fences, panes and walls don't join them)
NOT_SOLID = ("air", "stairs", "slab", "fence", "gate", "door", "trapdoor", "torch", "pane", "bars", "wall", "flower",
             "sapling", "leaves", "carpet", "ladder", "sign", "button", "lever", "pressure_plate", "rail", "grass",
             "fern", "chest", "bed", "pot", "skull", "head", "cauldron", "hopper", "anvil", "cake", "wheat", "carrots",
             "potatoes", "water", "lava", "snow", "banner", "lantern", "chain", "cobweb", "vine", "lily", "tripwire",
             "hook", "farmland", "dandelion", "poppy", "tulip", "orchid", "allium", "bluet", "daisy", "cornflower",
             "rose", "lilac", "peony", "sunflower", "bush", "cactus", "sugar_cane", "mushroom", "frame", "glass_pane",
             "brewing_stand", "enchanting_table", "end_portal", "piston_head", "repeater", "comparator", "redstone_wire",
             "daylight", "candle")
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
        self.facing = {b["id"] for b in gen.blocks if b.get("facing")}
        from lore import Lore
        self.lore = Lore(gen.jar, data_dir, gen.report)
        self.lore_placed = 0

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
        short = new.split(":", 1)[1]
        kind = self.kinds.get(short)
        if short in self.facing:
            props["facing"] = LADDER_FACING.get(meta & 7, "north")
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
        if kind == "gate":
            return {"facing": GATE_DIRECTION[meta & 7], "open": str(bool(meta & 8)).lower()}
        return {}

    def vanilla_state(self, old, meta):
        name = old.split(":", 1)[1]
        special = self.vanilla_special(name, meta)
        if special is not None:
            return special
        if old in ("minecraft:stone_slab", "minecraft:wooden_slab"):
            # 1.7.10 slabs: low 3 bits the material, bit 8 the top half
            slab = SLABS[meta & 7] if old == "minecraft:stone_slab" else WOOD_SLABS[(meta & 7) % 6]
            return {"Name": "minecraft:" + slab, "Properties": {"type": "top" if meta & 8 else "bottom"}}
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

    @staticmethod
    def vanilla_special(name, meta):
        """1.7.10 blocks whose metadata turns into facing/face/half/rotation (None: the usual mapping)."""
        def mc(n, **props):
            return {"Name": "minecraft:" + n, **({"Properties": props} if props else {})}
        if name == "tripwire_hook":
            return mc("tripwire_hook", facing=HOOK_FACING[meta & 3], attached=str(bool(meta & 4)).lower())
        if name == "lever":
            face, facing = LEVER[meta & 7]
            return mc("lever", face=face, facing=facing, powered=str(bool(meta & 8)).lower())
        if name in ("wooden_button", "stone_button"):
            face, facing = BUTTON.get(meta & 7, ("floor", "north"))
            return mc("oak_button" if name == "wooden_button" else "stone_button", face=face, facing=facing)
        if name == "skull":
            return mc("skeleton_wall_skull", facing=LADDER_FACING[meta & 7]) if 2 <= meta & 7 <= 5 \
                else mc("skeleton_skull")
        if name == "wall_sign":
            return mc("oak_wall_sign", facing=LADDER_FACING.get(meta, "north"))
        if name == "standing_sign":
            return mc("oak_sign", rotation=str(meta & 15))
        if name == "anvil":
            return mc(ANVILS[min(2, meta >> 2)], facing=["east", "south", "west", "north"][meta & 3])
        if name in ("pumpkin", "lit_pumpkin"):
            return mc("carved_pumpkin" if name == "pumpkin" else "jack_o_lantern", facing=HOOK_FACING[meta & 3])
        if name == "bed":
            return mc("red_bed", facing=HOOK_FACING[meta & 3], part="head" if meta & 8 else "foot")
        if name == "vine":
            return mc("vine", south=str(bool(meta & 1)).lower(), west=str(bool(meta & 2)).lower(),
                      north=str(bool(meta & 4)).lower(), east=str(bool(meta & 8)).lower())
        if name == "double_plant":
            if meta & 8:
                return {"Name": "@upper_of_below"}
            return mc(DOUBLE_PLANTS[meta % 6], half="lower")
        return None

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
        placed = {}

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
            if st["Name"] == "@upper_of_below":
                below = by_pos.get((x, y - 1, z))
                below_state = states[below] if below is not None else None
                if not below_state or below_state["Name"] == "@upper_of_below":
                    continue
                st = {"Name": below_state["Name"], "Properties": {**below_state.get("Properties", {}),
                                                                  "half": "upper"}}
            if st.get("Properties", {}).get("half") == "upper" and "facing" not in st["Properties"]:
                below = by_pos.get((x, y - 1, z))
                below_state = states[below] if below is not None else None
                if below_state and "facing" in below_state.get("Properties", {}):
                    st = {"Name": st["Name"], "Properties": {**st["Properties"],
                                                             "facing": below_state["Properties"]["facing"],
                                                             "open": below_state["Properties"].get("open", "false")}}
            if st.get("Properties", {}).get("half") == "lower" and "hinge" not in st["Properties"]:
                # 1.7.10 kept the hinge in the upper half only; both halves must agree or the door splits in two
                above = by_pos.get((x, y + 1, z))
                above_state = states[above] if above is not None else None
                if above_state and above_state.get("Properties", {}).get("half") == "upper":
                    st = {"Name": st["Name"], "Properties": {**st["Properties"],
                                                             "hinge": above_state["Properties"].get("hinge", "left")}}
            if st["Name"] in ("minecraft:flower_pot", f"{NS}:flower_pot"):
                pick = zlib.crc32(f"{sid}/{n}/{x},{y},{z}".encode()) % len(POTTED)
                st = {"Name": "minecraft:" + POTTED[pick]}
            entry = {"pos": [x - lo[0], y - base_y, z - lo[2]]}
            chest = chests.get((x, y, z))
            if chest is not None:
                items = []
                for slot, it in enumerate(chest["items"]):
                    new = self.item(it)
                    if new:
                        stack = {"Slot": nbt.Byte(slot), "id": new, "count": nbt.Int(it.get("count", 1))}
                        if new == "minecraft:written_book":
                            # LOTR's lore book (LOTRLore): the export shows it as an empty written book
                            book = self.lore.book(sid, f"{sid}/{n}/{x},{y},{z}/{slot}")
                            if not book:
                                continue
                            book["generation"] = nbt.Int(0)
                            book["resolved"] = nbt.Byte(1)
                            stack["components"] = {"minecraft:written_book_content": book}
                            self.lore_placed += 1
                        items.append(stack)
                # only vanilla chests keep their contents (LOTR's chests have no storage yet)
                if st["Name"] not in ("minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel"):
                    facing = st.get("Properties", {}).get("facing")
                    st = {"Name": "minecraft:chest", **({"Properties": {"facing": facing}} if facing else {})}
                entry["nbt"] = {"id": "minecraft:chest", "Items": items}
            placed[(x, y, z)] = (st, entry)
        # Real air only where LOTR cleared space; every other spot is left out (structure void), so the natural ground
        # stays there. Below the ground layer the export already holds the air LOTR dug (cellars). Above it, a column's
        # open space under its highest block is inside the building (rooms, under roofs and eaves) and is cleared, so
        # a hillside can't fill a room; open space beside or above the building keeps the terrain.
        ground_y = layout.get("ground", base_y)
        tops = {}
        for (x, y, z), (st, _) in placed.items():
            if y > ground_y and st["Name"] != "minecraft:air":
                tops[(x, z)] = max(tops.get((x, z), y), y)
        for (x, z), top in tops.items():
            for y in range(ground_y + 1, top):
                if (x, y, z) not in placed:
                    placed[(x, y, z)] = ({"Name": "minecraft:air"}, {"pos": [x - lo[0], y - base_y, z - lo[2]]})
        # templates are placed without block updates, so panes, fences and walls need their sides set here (a lone
        # pane post showed as a small dark square by the windows)
        for (x, y, z), (st, entry) in placed.items():
            st = self.connected(st, (x, y, z), placed)
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

    def joins(self, name):
        """'pane', 'fence' or 'wall' for blocks that join their neighbours, else None."""
        ns, short = name.split(":", 1)
        if ns == NS:
            kind = self.kinds.get(short)
            return kind if kind in ("pane", "fence", "wall") else ("gate" if kind == "fence_gate" else None)
        if short.endswith("glass_pane") or short == "iron_bars":
            return "pane"
        if short.endswith("_fence"):
            return "fence"
        if short.endswith("_fence_gate"):
            return "gate"
        if short.endswith("_wall") and not short.endswith(("wall_torch", "wall_sign", "wall_skull", "wall_banner",
                                                           "wall_head", "wall_fan", "wall_hanging_sign")):
            return "wall"
        return None

    def solid(self, name):
        """Whether a block has full solid sides (what panes, fences and walls join besides each other)."""
        ns, short = name.split(":", 1)
        if ns == NS:
            return self.kinds.get(short) in ("cube", "pillar", "faction_table")
        return not any(w in short for w in NOT_SOLID)

    def connected(self, st, pos, placed):
        """The state with north/east/south/west set from the neighbours, for panes, fences and walls."""
        kind = self.joins(st["Name"])
        if kind not in ("pane", "fence", "wall"):
            return st
        x, y, z = pos
        props = dict(st.get("Properties", {}))
        joined = {}
        for side, (dx, dz) in SIDES.items():
            other = placed.get((x + dx, y, z + dz))
            ok = False
            if other is not None:
                name = other[0]["Name"]
                theirs = self.joins(name)
                if kind == "pane":
                    ok = theirs in ("pane", "wall") or self.solid(name)
                elif kind == "fence":
                    ok = theirs in ("fence", "gate") or self.solid(name)
                else:
                    ok = theirs in ("pane", "wall", "gate", "fence") or self.solid(name)
            joined[side] = ok
        if kind == "wall":
            for side, ok in joined.items():
                props[side] = "low" if ok else "none"
            straight = (joined["north"] and joined["south"] and not joined["east"] and not joined["west"]) or \
                       (joined["east"] and joined["west"] and not joined["north"] and not joined["south"])
            props["up"] = "false" if straight and (x, y + 1, z) not in placed else "true"
        else:
            for side, ok in joined.items():
                props[side] = "true" if ok else "false"
        return {"Name": st["Name"], "Properties": props}

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
        # LOTR gives each biome its own chance per chunk for a structure (a Mordor camp is 1 in 20 chunks in one biome
        # and 1 in 3000 in another), so each chance gets its own structure and biome tag
        chance_in = {}
        for b in self.world["biomes"]:
            if "biomeName" not in b.get("fields", {}):
                continue
            bid = snake(b["fields"]["biomeName"])
            for st in b.get("structures", []):
                cls = (st.get("class") or "").split(".")[-1]
                per = chance_in.setdefault(cls, {})
                per[bid] = min(per.get(bid, 10 ** 9), max(1, st.get("chunkChance") or 1))
        biomes_of = {cls: set(per) for cls, per in chance_in.items()}
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
            by_chance = {}
            for bid, chance in chance_in[cls].items():
                by_chance.setdefault(chance, set()).add(bid)
            ids = []
            for chance, biomes in sorted(by_chance.items()):
                wid = sid if len(by_chance) == 1 else f"{sid}_{chance}"
                self.worldgen(wid, sid, len(layouts), ground - base_y, biomes, chance, cls)
                ids.append(wid)
            count += 1
            if npcs:
                with_npcs += 1
                for e in layouts[0].get("entities", []):
                    for npc, _ in self.npcs_of(e):
                        fac = (self.npc_types[npc].get("faction") or "").split(":")[-1]
                        if fac:
                            faction_tags.setdefault(fac, set()).update(f"{NS}:{w}" for w in ids)
        for fac, ids in faction_tags.items():
            write_json(os.path.join(self.data, "tags", "worldgen", "structure", "faction", fac + ".json"),
                       {"values": sorted(ids)})
        spawn_tables = self.spawns()
        return {"structures": count, "structures_with_npcs": with_npcs, "npc_spawn_tables": spawn_tables,
                **self.lore.run(), "lore_books_in_chests": self.lore_placed}

    def worldgen(self, wid, sid, layouts, ground, biomes, chunk_chance, cls):
        """One placement of a structure: lotr_port:lotr (LOTR's ground checks, foundations, no carving) in the biomes
        that share one LOTR chance per chunk. Every chunk rolls once with that chance, as LOTR's decorator did; then
        the structure itself turns down water, slopes and roads like LOTR's generators."""
        wg = os.path.join(self.data, "worldgen")
        write_json(os.path.join(wg, "structure", wid + ".json"), {
            "type": f"{NS}:lotr",
            "biomes": f"#{NS}:has_structure/{wid}",
            "step": "surface_structures",
            "spawn_overrides": {},
            "terrain_adaptation": "none",
            "templates": [f"{NS}:{sid}/{n}" for n in range(layouts)],
            # LOTR's chance per chunk (1 in chance), rolled by the structure itself with a well-mixed hash
            "chance": chunk_chance,
            "salt": zlib.crc32((cls + str(chunk_chance)).encode()) & 0x7FFFFFFF,
            # the template's layer that sits at the surface (the old flat world's grass layer)
            "ground": ground,
            "max_slope": 6,
            "road_distance": 16,
        })
        write_json(os.path.join(wg, "structure_set", wid + ".json"), {
            "structures": [{"structure": f"{NS}:{wid}", "weight": 1}],
            # every chunk is a candidate; the structure rolls LOTR's chance itself (vanilla's frequency seeds its random
            # linearly from the chunk and lined structures up in rows when every chunk rolls)
            "placement": {"type": "minecraft:random_spread", "spacing": 1, "separation": 0,
                          "salt": zlib.crc32((cls + str(chunk_chance)).encode()) & 0x7FFFFFFF},
        })
        write_json(os.path.join(self.data, "tags", "worldgen", "biome", "has_structure", wid + ".json"),
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
