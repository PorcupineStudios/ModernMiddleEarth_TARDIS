# Write the room layout as a vanilla structure file (.nbt, gzip) for 1.21.1 (DataVersion 3955).
import sys, json, gzip, struct
src, out, ns = sys.argv[1], sys.argv[2], (sys.argv[3] if len(sys.argv) > 3 else "tardis")
B = json.load(open(src))["blocks"]
def state(shape, kind):
    if kind == "bookshelf": return ("minecraft:bookshelf", {})
    if shape == "full": return (f"{ns}:{kind}", {})
    if shape.startswith("slab"): return (f"{ns}:{kind}_slab", {"type": shape.split("_")[1], "waterlogged": "false"})
    p = shape.split("_")
    return (f"{ns}:{kind}_stairs", {"facing": p[1], "half": "top" if len(p) > 2 else "bottom", "shape": "straight", "waterlogged": "false"})
xs = [b[0] for b in B]; ys = [b[1] for b in B]; zs = [b[2] for b in B]
mn = (min(xs), min(ys), min(zs)); size = [max(xs) - mn[0] + 1, max(ys) - mn[1] + 1, max(zs) - mn[2] + 1]
pal, blocks = [], []
for x, y, z, s, k in B:
    st = state(s, k); key = json.dumps(st, sort_keys=True)
    ids = [json.dumps(p, sort_keys=True) for p in pal]
    if key not in ids: pal.append(st); ids.append(key)
    blocks.append(([x - mn[0], y - mn[1], z - mn[2]], ids.index(key)))
# fill the rest of the bounding box with air so placing it clears the room
occ = {tuple(p) for p, _ in blocks}; pal.append(("minecraft:air", {})); air = len(pal) - 1
for x in range(size[0]):
    for y in range(size[1]):
        for z in range(size[2]):
            if (x, y, z) not in occ: blocks.append(([x, y, z], air))
# --- minimal NBT writer ---
def s_(t): b = t.encode(); return struct.pack(">H", len(b)) + b
def tag(tp, name, payload): return bytes([tp]) + s_(name) + payload
def comp(items): return b"".join(items) + b"\x00"
def lst(tp, payloads): return bytes([tp]) + struct.pack(">i", len(payloads)) + b"".join(payloads)
def ints(v): return b"".join(struct.pack(">i", i) for i in v)
palette = [comp([tag(8, "Name", s_(n))] + ([tag(10, "Properties", comp([tag(8, k, s_(v)) for k, v in sorted(p.items())]))] if p else [])) for n, p in pal]
blk = [comp([tag(9, "pos", lst(3, [struct.pack(">i", i) for i in p])), tag(3, "state", struct.pack(">i", st))]) for p, st in blocks]
root = tag(10, "", comp([tag(3, "DataVersion", struct.pack(">i", 3955)), tag(9, "size", lst(3, [struct.pack(">i", i) for i in size])),
                         tag(9, "palette", lst(10, palette)), tag(9, "blocks", lst(10, blk)), tag(9, "entities", lst(10, []))]))
gzip.open(out, "wb").write(root)
print("size", size, "offset", mn, "palette", len(pal), "blocks", len(B))
json.dump({"offset": mn, "size": size, "palette": [{"Name": n, "Properties": p} for n, p in pal]}, open(out.replace(".nbt", ".palette.json"), "w"), indent=1)
