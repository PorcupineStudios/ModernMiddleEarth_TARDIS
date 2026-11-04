"""
LOTR's sapling growth rules, read from the constants in its sapling classes' growTree (LOTRBlockSapling..9):

  data/lotr_port/sapling/<sapling block>.json   {"rules": [...]} tried in order, the first that fits grows:
      {"tree": "<type>", "square": 3}   a 3x3 (party tree) of the same sapling; 2 for the 2x2 trees, 4 and 5
                                        for findSaplingSquare (redwood, dragonblood)
      {"tree": "<type>", "cross": true} a plus of five of the same sapling (mallorn boughs)
      {"tree": "<type>", "chance": 10}  a lone sapling, 1 in 10 (LOTR's random large trees)
      {"tree": "<type>"}                a lone sapling
      {"tree": "<type>", "square": 9, "soil": "lotr_port:elvengrass"}  the mallorn's 9x9 on quendite grass

The tree ids are the configured features written by trees.py (lotr_port:tree/<type>). Which sapling block is which
(class, metadata) comes from the id table.
"""
import os
import struct

from assets import NS, write_json
from classfile import ClassFile

# operand bytes after each opcode (not counting tableswitch/lookupswitch/wide, handled apart)
OPERANDS = {0x10: 1, 0x11: 2, 0x12: 1, 0x13: 2, 0x14: 2, 0x15: 1, 0x16: 1, 0x17: 1, 0x18: 1, 0x19: 1, 0x36: 1,
            0x37: 1, 0x38: 1, 0x39: 1, 0x3A: 1, 0x84: 2, 0xA9: 1, 0xBC: 1, 0xBD: 2, 0xC0: 2, 0xC1: 2, 0xBB: 2,
            0xB2: 2, 0xB3: 2, 0xB4: 2, 0xB5: 2, 0xB6: 2, 0xB7: 2, 0xB8: 2, 0xB9: 4, 0xBA: 4, 0xC5: 3, 0xC6: 2,
            0xC7: 2, 0xC8: 4, 0xC9: 4}
for op in range(0x99, 0xA9):  # if*, goto, jsr
    OPERANDS[op] = 2
CLASSES = ["LOTRBlockSapling"] + [f"LOTRBlockSapling{i}" for i in range(2, 10)]


def instructions(code):
    """(offset, opcode, operand bytes) for each instruction."""
    i = 0
    while i < len(code):
        op = code[i]
        if op == 0xAA:  # tableswitch
            pad = (4 - (i + 1) % 4) % 4
            lo, hi = struct.unpack(">ii", code[i + 1 + pad + 4:i + 1 + pad + 12])
            n = 1 + pad + 12 + 4 * (hi - lo + 1)
        elif op == 0xAB:  # lookupswitch
            pad = (4 - (i + 1) % 4) % 4
            pairs = struct.unpack(">i", code[i + 1 + pad + 4:i + 1 + pad + 8])[0]
            n = 1 + pad + 8 + 8 * pairs
        elif op == 0xC4:  # wide
            n = 6 if code[i + 1] == 0x84 else 4
        else:
            n = 1 + OPERANDS.get(op, 0)
        yield i, op, code[i + 1:i + n]
        i += n


def int_of(cf, op, arg):
    if 0x02 <= op <= 0x08:
        return op - 0x03
    if op == 0x10:
        return struct.unpack(">b", arg)[0]
    if op == 0x11:
        return struct.unpack(">h", arg)[0]
    if op in (0x12, 0x13):
        idx = arg[0] if op == 0x12 else struct.unpack(">H", arg)[0]
        v = cf.constant(idx)
        return v if isinstance(v, int) else None
    return None


def rules_of(cf):
    """{metadata: [rule, ...]} from growTree: per metadata branch, each tree type with the check that guards it."""
    code = next((c for (name, _), c in cf.methods.items() if name == "growTree" and c), None)
    if code is None:
        return {}
    out = {}
    meta = 0
    ints = []  # recent int constants, newest last
    pending = {}
    for _, op, arg in instructions(code):
        v = int_of(cf, op, arg)
        if v is not None:
            ints.append(v)
            continue
        if op == 0xA0 and ints:  # if_icmpne after "meta == k": a new metadata branch
            meta = ints[-1]
            pending = {}
        elif op == 0xB8:  # invokestatic
            _, name = cf.ref_name(struct.unpack(">H", arg)[0])
            if name == "findPartyTree":
                pending = {"square": 3}
            elif name == "findCrossShape":
                pending = {"cross": True}
            elif name == "findSaplingSquare" and len(ints) >= 4:
                a, b = ints[-4], ints[-3]
                pending = {"square": b - a + 1}
        elif op == 0xB6:  # invokevirtual
            owner, name = cf.ref_name(struct.unpack(">H", arg)[0])
            if owner == "java/util/Random" and name == "nextInt" and ints:
                pending = {"chance": ints[-1]}
            elif name == "isSameSapling" and not pending:
                # growTree's own loop over the four 2x2 squares round the sapling (holly, cypress, olive...)
                pending = {"square": 2}
        elif op == 0xB2:  # getstatic
            owner, name = cf.ref_name(struct.unpack(">H", arg)[0])
            if owner.endswith("LOTRMod") and name == "quenditeGrass":
                pending = {"square": 9, "soil": "lotr:tile.quenditeGrass#0"}
            elif owner.endswith("LOTRTreeType"):
                out.setdefault(meta, []).append({"tree": f"{NS}:tree/{name.lower()}", **pending})
                pending = {}
        ints = [] if v is None and op not in (0x15, 0x16, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x2A, 0x2B, 0x2C, 0x2D) \
            else ints
    # the quendite check reads the grass block twice; keep its rule once
    for m, rules in out.items():
        seen = set()
        out[m] = [r for r in rules if not (r["tree"] in seen or seen.add(r["tree"]))]
    return out


class Saplings:
    def __init__(self, gen, data_dir, jar, trees):
        self.gen = gen
        self.data = os.path.join(data_dir, NS)
        self.jar = jar
        self.trees = trees  # the Trees generator: writes each tree type's feature on demand
        self.report = gen.report

    def run(self):
        written = 0
        for cls in CLASSES:
            path = f"lotr/common/block/{cls}.class"
            if not self.jar.has(path):
                continue
            rules = rules_of(ClassFile(self.jar.read(path)))
            old = "lotr:tile.sapling" + cls.removeprefix("LOTRBlockSapling")
            for meta, rs in sorted(rules.items()):
                new = self.gen.idtable.get(f"{old}#{meta}")
                if not new:
                    self.report["saplings_unmapped"].append(f"{old}#{meta}")
                    continue
                for r in rs:
                    self.trees.tree(r["tree"].split("/", 1)[1].upper())
                    if "soil" in r:
                        r["soil"] = self.gen.idtable.get(r["soil"], r["soil"]).split("[")[0]
                sid = new.split(":", 1)[1].split("[")[0]
                write_json(os.path.join(self.data, "sapling", sid + ".json"), {"rules": rs})
                written += 1
        return {"saplings": written}
