# Hand-designed Capaldi console room as blocks, following the measured layout of the original model.
# MC coords: +x east, +y up, +z south. Main floor top y=0, basement floor top y=-3, door frame face z=8.
import json, math, sys
CX, CZ = 0.5, 0.0
B = {}
def put(x, y, z, kind, shape="full"): B[(x, y, z)] = (shape, kind)
def cut(x, y, z): B.pop((x, y, z), None)
def r_of(x, z): return math.hypot(x + 0.5 - CX, z + 0.5 - CZ)
def ang(x, z): return math.degrees(math.atan2(-(z + 0.5 - CZ), x + 0.5 - CX)) % 360   # 0 = east, 90 = north
R = range(-11, 12)
for x in R:
    for z in R:
        r = r_of(x, z); a = ang(x, z)
        if r > 9.6: continue
        put(x, -4, z, "floor_plate")                                                    # basement floor
        if 2.0 <= r < 3.0 and int(a // 30) % 2 == 0: put(x, -4, z, "grille_plate")       # glowing ring under the console
        if r >= 8.5:                                                                     # outer wall, roundels all the way up
            for y in range(-3, 6):
                lit = (int(a // 20) + y) % 2 == 0
                put(x, y, z, "roundel_wall_lit" if lit else "roundel_wall")
            put(x, 5, z, "light_strip"); put(x, 6, z, "frame")
        if r <= 9.6: put(x, 7, z, "wall_panel")                                          # ceiling
        if 7.5 <= r < 8.5: put(x, 6, z, "frame")
        if 6.5 <= r < 7.5:                                                               # sloped edge of the ceiling
            d = "east" if abs(x + .5 - CX) > abs(z + .5 - CZ) and x + .5 > CX else "west" if abs(x + .5 - CX) > abs(z + .5 - CZ) else "south" if z + .5 > CZ else "north"
            put(x, 6, z, "frame", f"stairs_{d}_top")
        if r <= 4.5:                                                                     # console platform
            put(x, -1, z, "floor_plate")
            sector = (a + 360 - 270) % 60                                                # grilles in front of each console panel
            if 2.5 <= r <= 3.7 and (sector < 12 or sector > 48): put(x, -1, z, "grille_plate")
        if 6.0 <= r < 8.5:                                                               # upper gallery ring
            put(x, 1, z, "floor_plate")
            if r < 7.0:
                for y in range(-3, 1): put(x, y, z, "roundel_wall_lit" if (int(a // 20) + y) % 2 else "roundel_wall")
                put(x, 2, z, "frame", "slab_bottom")                                     # low rail along the gallery edge
        if r >= 8.5 and 2 <= y < 4 if False else False: pass
# bookshelves on the west half of the gallery wall
for (x, y, z), (s, k) in list(B.items()):
    if k.startswith("roundel") and y in (2, 3) and r_of(x, z) >= 8.5 and 120 <= ang(x, z) <= 240: B[(x, y, z)] = ("full", "bookshelf")
# walkway from the platform to the main door (x -1..2), with kerbs; cut the gallery where it crosses
for z in range(3, 9):
    for x in range(-2, 4):
        for y in range(-3, 6): cut(x, y, z)
        put(x, -1, z, "floor_plate" if -1 <= x <= 2 else "frame")
        put(x, -4, z, "floor_plate")
    for y in range(-3, -1): put(-2, y, z, "wall_panel"); put(3, y, z, "wall_panel")
# main door opening in the outer wall (frame face at z=8): keep wall blocks at z>=8 except the door and its frame
for z in range(8, 11):
    for x in range(-2, 4):
        for y in range(0, 6):
            put(x, y, z, "frame" if x in (-1, 2) or y == 3 else "wall_panel")
for x in (0, 1):
    for y in (0, 1, 2): cut(x, y, 8)
# stairs up to the gallery, west and east of the platform
for z in (-1, 0, 1):
    put(-5, 0, z, "floor_plate", "stairs_west"); put(-6, 1, z, "floor_plate", "stairs_west"); put(-6, 0, z, "wall_panel")
    for y in (2,): cut(-6, y, z); cut(-7, y, z)
    put(5, 0, z, "floor_plate", "stairs_east"); put(6, 1, z, "floor_plate", "stairs_east"); put(6, 0, z, "wall_panel")
    for y in (2,): cut(6, y, z); cut(7, y, z)
    for x in (-6, 6):
        for y in range(-3, 0): put(x, y, z, "wall_panel")
# stairs down from the platform to the lower deck, heading north to the north doorway (x 0..1)
for x in (0, 1):
    put(x, -1, -5, "floor_plate", "stairs_south"); put(x, -2, -6, "floor_plate", "stairs_south"); put(x, -3, -7, "floor_plate", "stairs_south")
    for z in range(-8, -4):
        for y in range(-3, 1):
            if not B.get((x, y, z), ("", ""))[0].startswith("stairs"): cut(x, y, z)
    for z in (-9, -10):
        for y in (-3, -2, -1): cut(x, y, z)
    for z in range(-8, -5): put(-1, -3, z, "wall_panel"); put(2, -3, z, "wall_panel")
# east doorway at the lower deck (z -2..-1) through both rings
for x in range(5, 11):
    for z in (-2, -1):
        for y in (-3, -2, -1): cut(x, y, z)
# west doorway at the lower deck, mirroring the east one
for x in range(-11, -5):
    for z in (-2, -1):
        for y in (-3, -2, -1): cut(x, y, z)
# hallway stubs for the rooms system: 4 wide, 3 tall (lower deck level, floor top y=-3), open at the far end
ANCHORS = []
def hallway(cells, facing, end):
    for (x, z) in cells:
        for y in range(-3, 0): cut(x, y, z)
        put(x, -4, z, "floor_plate"); put(x, 0, z, "light_strip" if (x + z) % 3 == 0 else "wall_panel")
    ANCHORS.append({"facing": facing, **end})
def walls(x, z):
    if (x, -3, z) not in B:
        for y in range(-3, 0): put(x, y, z, "frame" if y == -1 else "wall_panel")
        put(x, -4, z, "floor_plate"); put(x, 0, z, "wall_panel")
HL = 6
north = [(x, z) for x in range(-1, 3) for z in range(-15, -8)]
east = [(x, z) for x in range(9, 9 + HL + 1) for z in range(-3, 1)]
west = [(x, z) for x in range(-9 - HL, -8) for z in range(-3, 1)]
hallway(north, "north", {"open_end_min": [-1, -3, -15], "open_end_max": [2, -1, -15]})
hallway(east, "east", {"open_end_min": [15, -3, -3], "open_end_max": [15, -1, 0]})
hallway(west, "west", {"open_end_min": [-15, -3, -3], "open_end_max": [-15, -1, 0]})
for x, z in north: walls(x - 1, z) if x == -1 else None; walls(x + 1, z) if x == 2 else None
for x, z in east + west: walls(x, z - 1) if z == -3 else None; walls(x, z + 1) if z == 0 else None
# the doorways themselves are 2 wide; widen the outer-wall openings to the hallway width
for x in range(-1, 3):
    for z in (-9, -10):
        for y in (-3, -2, -1): cut(x, y, z)
for z in range(-3, 1):
    for x in (8, 9, -9, -10):
        for y in (-3, -2, -1): cut(x, y, z)
# invisible collision for the modelled parts: arch pillars (thin post) and the console pedestal on the lower deck
for j in range(18):
    a = math.radians(20 * j); px = 0.5 + 7.75 * math.cos(a); pz = 0.25 - 7.75 * math.sin(a)
    bx, bz = math.floor(px), math.floor(pz)
    for y in range(-3, 4):
        walkway = -2 <= bx <= 3 and bz >= 3
        corridor = y <= -1 and ((-1 <= bx <= 2 and bz <= -5) or (-3 <= bz <= 0 and abs(bx + 0.5 - CX) >= 5))
        if (bx, y, bz) not in B and not walkway and not corridor: put(bx, y, bz, "collision_post")
for y in (-3, -2): put(0, y, 0, "collision_pillar")
# keep the lower deck under the platform open: headroom from -3 to -1 everywhere inside r<6
out = {"about": "block layout of the Capaldi console room; shape: full, slab_bottom, stairs_<side of tall back>[_top]",
       "hallway_ends": ANCHORS,
       "blocks": [[x, y, z, s, k] for (x, y, z), (s, k) in sorted(B.items())]}
json.dump(out, open(sys.argv[1], "w"))
from collections import Counter
print(len(B), Counter(k for s, k in B.values()))
