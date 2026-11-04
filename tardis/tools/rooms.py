"""
Writes the TARDIS room structure templates (src/main/resources/data/tardis_rb/structure/interior/*.nbt).
Placeholder rooms built from vanilla blocks until Aaron's builds replace them: run `python3 tardis/tools/rooms.py`
from the repo root. Uses the converter's small NBT writer.
"""
import os
import random
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "converter"))
import nbt  # noqa: E402

DATA_VERSION = 3955  # Minecraft 1.21.1
OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "data", "tardis_rb", "structure",
                   "interior")


def write(name, size, cells):
    palette, index, blocks = [], {}, []
    for pos, state in sorted(cells.items()):
        if state not in index:
            index[state] = len(palette)
            name_, _, props = state.partition("[")
            entry = {"Name": name_}
            if props:
                entry["Properties"] = dict(p.split("=") for p in props.rstrip("]").split(","))
            palette.append(entry)
        blocks.append({"pos": [nbt.Int(v) for v in pos], "state": nbt.Int(index[state])})
    os.makedirs(OUT, exist_ok=True)
    nbt.write(os.path.join(OUT, name + ".nbt"), {"DataVersion": nbt.Int(DATA_VERSION),
                                                 "size": [nbt.Int(v) for v in size],
                                                 "palette": palette, "blocks": blocks, "entities": []})


def shell(rnd, w, h, d, floor, wall, light):
    """A plain closed box of w x h x d; added rooms are carved open at the doorway when built."""
    cells = {}
    for x in range(w):
        for z in range(d):
            cells[(x, 0, z)] = floor
            cells[(x, h - 1, z)] = wall
    for y in range(1, h - 1):
        for x in range(w):
            for z in range(d):
                if x in (0, w - 1) or z in (0, d - 1):
                    cells[(x, y, z)] = wall
    for (x, z) in [(2, 2), (6, 2), (2, 6), (6, 6)]:
        cells[(x, h - 1, z)] = light
    return cells


def library():
    """9 x 5 x 9 placeholder library: shelves along the side walls, a lectern in the middle."""
    w, h, d = 9, 5, 9
    cells = shell(random.Random(21), w, h, d, "minecraft:dark_oak_planks", "minecraft:stripped_dark_oak_log",
                  "minecraft:sea_lantern")
    for y in range(1, 4):
        for z in range(2, 7):
            cells[(1, y, z)] = "minecraft:bookshelf"
            cells[(7, y, z)] = "minecraft:bookshelf"
    cells[(4, 1, 4)] = "minecraft:lectern[facing=north]"
    write("library", (w, h, d), cells)


def workshop():
    """9 x 5 x 9 placeholder workshop: crafting table, anvil, smithing table, chests."""
    w, h, d = 9, 5, 9
    cells = shell(random.Random(31), w, h, d, "minecraft:polished_andesite", "minecraft:stone_bricks",
                  "minecraft:sea_lantern")
    cells[(1, 1, 3)] = "minecraft:crafting_table"
    cells[(1, 1, 4)] = "minecraft:anvil[facing=east]"
    cells[(1, 1, 5)] = "minecraft:smithing_table"
    cells[(7, 1, 3)] = "minecraft:chest[facing=west]"
    cells[(7, 1, 5)] = "minecraft:chest[facing=west]"
    write("workshop", (w, h, d), cells)


def test_platform():
    """GameTest template (structure/platform.nbt): a 14 x 5 x 5 stone floor with room for a police box to hop."""
    global OUT
    saved, OUT = OUT, os.path.dirname(OUT)
    write("platform", (14, 5, 5), {(x, 0, z): "minecraft:stone" for x in range(14) for z in range(5)})
    OUT = saved


if __name__ == "__main__":
    library()
    workshop()
    test_platform()
