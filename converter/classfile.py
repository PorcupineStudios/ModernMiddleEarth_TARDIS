"""
A small reader for Java class files: the constant pool and the constants an enum's static initialiser passes to its
constructor. Enough to read tables LOTR keeps in code (its map labels) without running or copying that code.
"""
import struct


class ClassFile:
    def __init__(self, data):
        self.data = data
        self.pos = 8
        count = self.u2()
        self.pool = [None] * count
        i = 1
        while i < count:
            tag = self.u1()
            if tag == 1:
                n = self.u2()
                self.pool[i] = ("utf8", self.data[self.pos:self.pos + n].decode("utf-8", "replace"))
                self.pos += n
            elif tag == 3:
                self.pool[i] = ("int", struct.unpack(">i", self.take(4))[0])
            elif tag == 4:
                self.pool[i] = ("float", struct.unpack(">f", self.take(4))[0])
            elif tag in (5, 6):
                self.take(8)
                self.pool[i] = ("wide", None)
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                self.pool[i] = ({7: "class", 8: "string"}.get(tag, "other"), self.u2())
            elif tag in (9, 10, 11, 12, 17, 18):
                self.pool[i] = ({9: "field", 10: "method", 11: "imethod", 12: "nat"}.get(tag, "other"),
                                (self.u2(), self.u2()))
            elif tag == 15:
                self.take(3)
                self.pool[i] = ("other", None)
            else:
                raise ValueError(f"constant tag {tag}")
            i += 1
        self.take(6)  # access, this, super
        self.take(2 * self.u2())  # interfaces
        for _ in range(self.u2()):  # fields
            self.take(6)
            self.skip_attributes()
        self.methods = {}
        for _ in range(self.u2()):
            self.take(2)
            name = self.utf8(self.u2())
            desc = self.utf8(self.u2())
            code = None
            for _ in range(self.u2()):
                aname = self.utf8(self.u2())
                alen = struct.unpack(">I", self.take(4))[0]
                body = self.take(alen)
                if aname == "Code":
                    clen = struct.unpack(">I", body[4:8])[0]
                    code = body[8:8 + clen]
            self.methods[(name, desc)] = code

    def u1(self):
        self.pos += 1
        return self.data[self.pos - 1]

    def u2(self):
        self.pos += 2
        return struct.unpack(">H", self.data[self.pos - 2:self.pos])[0]

    def take(self, n):
        self.pos += n
        return self.data[self.pos - n:self.pos]

    def skip_attributes(self):
        for _ in range(self.u2()):
            self.take(2)
            self.take(struct.unpack(">I", self.take(4))[0])

    def utf8(self, i):
        return self.pool[i][1]

    def ref_name(self, i):
        """(owner class, member name) of a field or method reference."""
        cls, nat = self.pool[i][1]
        return self.utf8(self.pool[cls][1]), self.utf8(self.pool[nat][1][0])

    def constant(self, i):
        kind, v = self.pool[i]
        if kind == "string":
            return self.utf8(v)
        if kind in ("int", "float"):
            return v
        return None

    def enum_entries(self):
        """Each constructor call in <clinit> as the list of its constant arguments (getstatic gives ('field', owner,
        name)). Only straight-line code of constants is understood; anything else ends the current entry."""
        code = self.methods.get(("<clinit>", "()V"))
        entries, args, i = [], None, 0
        while code and i < len(code):
            op = code[i]
            if op == 0xBB:  # new
                args = []
                i += 3
            elif op == 0x59:  # dup
                i += 1
            elif op in (0x12,):  # ldc
                if args is not None:
                    args.append(self.constant(code[i + 1]))
                i += 2
            elif op in (0x13,):  # ldc_w
                if args is not None:
                    args.append(self.constant(struct.unpack(">H", code[i + 1:i + 3])[0]))
                i += 3
            elif op == 0x10:  # bipush
                if args is not None:
                    args.append(struct.unpack(">b", code[i + 1:i + 2])[0])
                i += 2
            elif op == 0x11:  # sipush
                if args is not None:
                    args.append(struct.unpack(">h", code[i + 1:i + 3])[0])
                i += 3
            elif 0x02 <= op <= 0x08:  # iconst_m1..5
                if args is not None:
                    args.append(op - 0x03)
                i += 1
            elif 0x0B <= op <= 0x0D:  # fconst_0..2
                if args is not None:
                    args.append(float(op - 0x0B))
                i += 1
            elif op == 0xB2:  # getstatic
                if args is not None:
                    args.append(("field",) + self.ref_name(struct.unpack(">H", code[i + 1:i + 3])[0]))
                i += 3
            elif op == 0xB7:  # invokespecial
                _, name = self.ref_name(struct.unpack(">H", code[i + 1:i + 3])[0])
                if name == "<init>" and args is not None:
                    entries.append(args)
                args = None
                i += 3
            else:
                # the enum's values() array and anything else: stop collecting
                args = None
                if op in (0xB3, 0xB4, 0xB5, 0xB6, 0xB8, 0xBD, 0xC0):
                    i += 3
                elif op in (0xB9,):
                    i += 5
                elif op in (0x19, 0x15, 0x3A, 0x36, 0xBC):
                    i += 2
                else:
                    i += 1
        return entries
