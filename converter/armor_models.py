"""
LOTR's special armor models (helmets with crests and wings, robes, turbans, hats) as data for the 1.21.1 port.

LOTR maps some armor items to their own ModelBiped subclasses in LOTRArmorModels.setupArmorModels(); each model builds
its boxes in its constructor. This reads those constructors' bytecode and replays them on a tiny stack machine that
understands only what model constructors do (new ModelRenderer, addBox, setRotationPoint, setTextureOffset, addChild,
rotation and mirror fields, simple arithmetic, loops), so we get the boxes, texture offsets, pivots and angles
without running or copying LOTR's code. Output: lotr_port/content/armor_models.json

    {"items": {"<item id>": {"model": "<model>", "inflate": f}},
     "models": {"<model>@<inflate>": {"texture": [w, h], "parts": {"head": PART, ...}}}}
    PART = {"pivot": [x, y, z], "rotation": [rx, ry, rz] (radians), "cubes": [CUBE], "children": [PART]}
    CUBE = {"uv": [u, v], "origin": [x, y, z], "size": [w, h, d], "inflate": f, "mirror": bool}

Coordinates and UVs mean the same in 1.7.10's ModelRenderer and 1.21's CubeListBuilder/PartPose, so the client builds
a HumanoidModel from this unchanged (client/ArmorModels.java). Animated parts (the swan wings' flap, robes following
the legs) keep their resting pose.
"""
import math
import struct

from assets import snake
from classfile import ClassFile

SETUP = "lotr/client/model/LOTRArmorModels"
MR = "net/minecraft/client/model/ModelRenderer"
BIPED = "net/minecraft/client/model/ModelBiped"
BASE = "net/minecraft/client/model/ModelBase"

# ModelBiped's renderers (obfuscated field names) -> the 1.21 HumanoidModel part they become.
BIPED_PARTS = {"field_78116_c": "head", "field_78114_d": "hat", "field_78115_e": "body",
               "field_78112_f": "right_arm", "field_78113_g": "left_arm", "field_78123_h": "right_leg",
               "field_78124_i": "left_leg"}
RENDERER_FIELDS = {"field_78800_c": ("pivot", 0), "field_78797_d": ("pivot", 1), "field_78798_e": ("pivot", 2),
                   "field_78795_f": ("rotation", 0), "field_78796_g": ("rotation", 1),
                   "field_78808_h": ("rotation", 2)}


class Renderer:
    def __init__(self, tex_w, tex_h, u=0, v=0):
        self.tex = [tex_w, tex_h]
        self.uv = [u, v]
        self.pivot = [0.0, 0.0, 0.0]
        self.rotation = [0.0, 0.0, 0.0]
        self.mirror = False
        self.hidden = False
        self.cubes = []
        self.children = []

    def box(self, x, y, z, w, h, d, inflate=0.0):
        self.cubes.append({"uv": list(self.uv), "origin": [r(x), r(y), r(z)], "size": [int(w), int(h), int(d)],
                           "inflate": r(inflate), "mirror": self.mirror})

    def json(self):
        out = {"pivot": [r(p) for p in self.pivot], "rotation": [r(a) for a in self.rotation],
               "cubes": [] if self.hidden else self.cubes, "children": [c.json() for c in self.children]}
        return out


class CubeList:
    """renderer.cubeList, only so models can clear() it."""

    def __init__(self, owner):
        self.owner = owner


def r(x):
    return round(float(x), 4)


def biped(this, f, y_off=0.0, w=64, h=32):
    """Vanilla 1.7.10 ModelBiped(f, yOffset, w, h): the boxes every armor model starts from."""
    this.fields["field_78090_t"], this.fields["field_78089_u"] = w, h
    parts = {}

    def part(name, u, v, box, pivot, inflate=f, mirror=False):
        p = Renderer(w, h, u, v)
        p.mirror = mirror
        p.box(*box, inflate)
        p.pivot = list(pivot)
        parts[name] = p

    part("field_78116_c", 0, 0, (-4, -8, -4, 8, 8, 8), (0, y_off, 0))
    part("field_78114_d", 32, 0, (-4, -8, -4, 8, 8, 8), (0, y_off, 0), f + 0.5)
    part("field_78115_e", 16, 16, (-4, 0, -2, 8, 12, 4), (0, y_off, 0))
    part("field_78112_f", 40, 16, (-3, -2, -2, 4, 12, 4), (-5, 2 + y_off, 0))
    part("field_78113_g", 40, 16, (-1, -2, -2, 4, 12, 4), (5, 2 + y_off, 0), mirror=True)
    part("field_78123_h", 0, 16, (-2, 0, -2, 4, 12, 4), (-1.9, 12 + y_off, 0))
    part("field_78124_i", 0, 16, (-2, 0, -2, 4, 12, 4), (1.9, 12 + y_off, 0), mirror=True)
    this.fields.update(parts)


def pig(this, f):
    """1.7.10's ModelPig (ModelQuadruped(6, f) and the snout), for LOTR's boar that extends it."""
    this.fields["field_78090_t"], this.fields["field_78089_u"] = 64, 32

    def part(name, u, v, box, pivot, rot=(0.0, 0.0, 0.0)):
        p = Renderer(64, 32, u, v)
        p.box(*box, f)
        p.pivot = [float(x) for x in pivot]
        p.rotation = [float(x) for x in rot]
        this.fields[name] = p
        return p
    head = part("field_78150_a", 0, 0, (-4.0, -4.0, -8.0, 8, 8, 8), (0, 12, -6))
    part("field_78148_b", 28, 8, (-5.0, -10.0, -7.0, 10, 16, 8), (0, 11, 2), (math.pi / 2, 0, 0))
    part("field_78149_c", 0, 16, (-2.0, 0.0, -2.0, 4, 6, 4), (-3, 18, 7))
    part("field_78146_d", 0, 16, (-2.0, 0.0, -2.0, 4, 6, 4), (3, 18, 7))
    part("field_78147_e", 0, 16, (-2.0, 0.0, -2.0, 4, 6, 4), (-3, 18, -5))
    part("field_78144_f", 0, 16, (-2.0, 0.0, -2.0, 4, 6, 4), (3, 18, -5))
    head.uv = [16, 16]
    head.box(-2.0, 0.0, -9.0, 4, 3, 1, f)


class Holder:
    """A `new` object before its constructor ran (`new X; dup; args; invokespecial X.<init>`); the constructor call
    fills it in, so every dup'd copy sees the built object."""

    def __init__(self, cls):
        self.cls = cls
        self.value = None


def unwrap(x):
    return x.value if isinstance(x, Holder) and x.value is not None else x


class Obj:
    def __init__(self, cls):
        self.cls = cls
        self.fields = {}


class Unsupported(Exception):
    pass


class Stack(list):
    def pop(self, *a):
        return unwrap(super().pop(*a))


class Machine:
    def __init__(self, jar):
        self.jar = jar
        self.classes = {}

    def cls(self, name):
        if name not in self.classes:
            path = name + ".class"
            self.classes[name] = ClassFile(self.jar.read(path)) if self.jar.has(path) else None
        return self.classes[name]

    def construct(self, cls_name, args, this=None, desc=None):
        """Runs <init> of cls_name on this (a new Obj) with the given arguments."""
        this = this or Obj(cls_name)
        if cls_name in (BIPED, "net/minecraft/client/model/ModelZombie"):
            if len(args) == 0:
                biped(this, 0.0)
            elif len(args) == 1:
                biped(this, args[0])
            else:
                biped(this, args[0], args[1], args[2], args[3])
            return this
        if cls_name == "net/minecraft/client/model/ModelPig":
            pig(this, args[0] if args else 0.0)
            return this
        if cls_name in (BASE, "java/lang/Object"):
            this.fields.setdefault("field_78090_t", 64)
            this.fields.setdefault("field_78089_u", 32)
            return this
        cf = self.cls(cls_name)
        if cf is None:
            raise Unsupported(f"no class {cls_name}")
        desc = desc or "(" + "".join("F" if isinstance(a, float) else "I" for a in args) + ")V"
        code = cf.methods.get(("<init>", desc))
        if code is None:
            # an int literal may have been meant as a float (fconst vs iconst): try every <init> with this arity
            for (n, d), c in cf.methods.items():
                if n == "<init>" and len(params(d)) == len(args) and all(p in "FIZ" for p in params(d)):
                    code, desc = c, d
                    args = [float(a) if p == "F" else a for a, p in zip(args, params(d))]
                    break
        if code is None:
            raise Unsupported(f"{cls_name}.<init>{desc}")
        self.run(cf, code, [this] + list(args))
        return this

    def run(self, cf, code, local_args):
        locals_ = list(local_args) + [None] * 32
        stack = Stack()
        pc = 0
        steps = 0

        def u2(at):
            return struct.unpack(">H", code[at:at + 2])[0]

        def s2(at):
            return struct.unpack(">h", code[at:at + 2])[0]

        while pc < len(code):
            steps += 1
            if steps > 200000:
                raise Unsupported("too many steps")
            op = code[pc]
            if op == 0x00:
                pc += 1
            elif op == 0x01:  # aconst_null
                stack.append(None)
                pc += 1
            elif 0x02 <= op <= 0x08:
                stack.append(op - 0x03)
                pc += 1
            elif 0x0B <= op <= 0x0D:
                stack.append(float(op - 0x0B))
                pc += 1
            elif op in (0x0E, 0x0F):
                stack.append(float(op - 0x0E))
                pc += 1
            elif op == 0x10:
                stack.append(struct.unpack(">b", code[pc + 1:pc + 2])[0])
                pc += 2
            elif op == 0x11:
                stack.append(s2(pc + 1))
                pc += 3
            elif op == 0x12:
                stack.append(cf.constant(code[pc + 1]))
                pc += 2
            elif op in (0x13, 0x14):
                stack.append(cf.constant(u2(pc + 1)))
                pc += 3
            elif 0x15 <= op <= 0x19:  # iload..aload n
                stack.append(locals_[code[pc + 1]])
                pc += 2
            elif 0x1A <= op <= 0x2D:  # xload_n
                stack.append(locals_[(op - 0x1A) % 4])
                pc += 1
            elif op == 0x32:  # aaload
                i, arr = stack.pop(), stack.pop()
                stack.append(arr[i])
                pc += 1
            elif 0x36 <= op <= 0x3A:
                locals_[code[pc + 1]] = stack.pop()
                pc += 2
            elif 0x3B <= op <= 0x4E:
                locals_[(op - 0x3B) % 4] = stack.pop()
                pc += 1
            elif op in (0x4F, 0x51, 0x53):  # iastore fastore aastore
                v, i, arr = stack.pop(), stack.pop(), stack.pop()
                arr[i] = v
                pc += 1
            elif op == 0x57:
                stack.pop()
                pc += 1
            elif op == 0x59:
                stack.append(list.__getitem__(stack, -1))
                pc += 1
            elif op == 0x5A:  # dup_x1
                a, b = stack.pop(), stack.pop()
                stack += [a, b, a]
                pc += 1
            elif op in (0x60, 0x62, 0x63):
                b, a = stack.pop(), stack.pop()
                stack.append(a + b)
                pc += 1
            elif op in (0x64, 0x66, 0x67):
                b, a = stack.pop(), stack.pop()
                stack.append(a - b)
                pc += 1
            elif op in (0x68, 0x6A, 0x6B):
                b, a = stack.pop(), stack.pop()
                stack.append(a * b)
                pc += 1
            elif op == 0x6C:
                b, a = stack.pop(), stack.pop()
                stack.append(int(a / b))
                pc += 1
            elif op in (0x6E, 0x6F):
                b, a = stack.pop(), stack.pop()
                stack.append(a / b)
                pc += 1
            elif op == 0x70:
                b, a = stack.pop(), stack.pop()
                stack.append(int(math.fmod(a, b)))
                pc += 1
            elif op in (0x74, 0x76, 0x77):
                stack.append(-stack.pop())
                pc += 1
            elif op == 0x84:  # iinc
                locals_[code[pc + 1]] += struct.unpack(">b", code[pc + 2:pc + 3])[0]
                pc += 3
            elif op in (0x86, 0x87, 0x8D, 0x8A, 0x8C, 0x85, 0x89, 0x90):  # i2f i2d f2d l2d.. d2f
                stack.append(float(stack.pop()))
                pc += 1
            elif op in (0x8B, 0x8E, 0x88):  # f2i d2i l2i
                stack.append(int(stack.pop()))
                pc += 1
            elif op in (0x95, 0x96, 0x97, 0x98):  # fcmpl fcmpg dcmpl dcmpg
                b, a = stack.pop(), stack.pop()
                stack.append((a > b) - (a < b))
                pc += 1
            elif 0x99 <= op <= 0x9E:
                a = stack.pop()
                ok = [a == 0, a != 0, a < 0, a >= 0, a > 0, a <= 0][op - 0x99]
                pc = pc + s2(pc + 1) if ok else pc + 3
            elif 0x9F <= op <= 0xA6:
                b, a = stack.pop(), stack.pop()
                ok = [a == b, a != b, a < b, a >= b, a > b, a <= b, a is b, a is not b][op - 0x9F]
                pc = pc + s2(pc + 1) if ok else pc + 3
            elif op == 0xA7:
                pc += s2(pc + 1)
            elif op in (0xC6, 0xC7):
                a = stack.pop()
                ok = (a is None) if op == 0xC6 else (a is not None)
                pc = pc + s2(pc + 1) if ok else pc + 3
            elif op in (0xAC, 0xAE, 0xB0, 0xB1):
                return stack.pop() if op != 0xB1 else None
            elif op == 0xB2:  # getstatic
                stack.append(self.static(*cf.ref_full(u2(pc + 1))))
                pc += 3
            elif op == 0xB3:
                stack.pop()
                pc += 3
            elif op == 0xB4:  # getfield
                _, name, _ = cf.ref_full(u2(pc + 1))
                obj = stack.pop()
                if isinstance(obj, Renderer):
                    if name in RENDERER_FIELDS:
                        attr, i = RENDERER_FIELDS[name]
                        stack.append(getattr(obj, attr)[i])
                    elif name == "field_78804_l":
                        stack.append(CubeList(obj))
                    elif name == "field_78809_i":
                        stack.append(obj.mirror)
                    else:
                        stack.append(None)
                elif isinstance(obj, Obj):
                    stack.append(obj.fields.get(name))
                else:
                    stack.append(None)
                pc += 3
            elif op == 0xB5:  # putfield
                _, name, _ = cf.ref_full(u2(pc + 1))
                v, obj = stack.pop(), stack.pop()
                if isinstance(obj, Renderer):
                    if name in RENDERER_FIELDS:
                        attr, i = RENDERER_FIELDS[name]
                        getattr(obj, attr)[i] = float(v)
                    elif name == "field_78809_i":
                        obj.mirror = bool(v)
                    elif name in ("field_78806_j",):  # showModel
                        obj.hidden = not v
                    elif name == "field_78807_k":  # isHidden
                        obj.hidden = bool(v)
                elif isinstance(obj, Obj):
                    obj.fields[name] = v
                pc += 3
            elif op in (0xB6, 0xB7, 0xB8, 0xB9):
                owner, name, desc = cf.ref_full(u2(pc + 1))
                n = len(params(desc))
                args = [stack.pop() for _ in range(n)][::-1]
                target = None if op == 0xB8 else list.pop(stack)
                result = self.invoke(op, owner, name, desc, target, args)
                if not desc.endswith(")V"):
                    stack.append(result)
                pc += 5 if op == 0xB9 else 3
            elif op == 0xBB:  # new
                stack.append(Holder(cf.utf8(cf.pool[u2(pc + 1)][1])))
                pc += 3
            elif op == 0xBC:  # newarray
                stack.append([0] * stack.pop())
                pc += 2
            elif op == 0xBD:  # anewarray
                stack.append([None] * stack.pop())
                pc += 3
            elif op == 0xBE:
                stack.append(len(stack.pop()))
                pc += 1
            elif op == 0xC0:  # checkcast
                pc += 3
            elif op == 0xC1:  # instanceof: models ask "ridden by a player?" and such; no
                stack.pop()
                stack.append(0)
                pc += 3
            else:
                raise Unsupported(f"opcode {op:#x} in {cf.this_name}")
        return None

    def static(self, owner, name, desc):
        return None  # static fields (LOTRMod items, render state) mean nothing to a model's shape

    def invoke(self, op, owner, name, desc, target, args):
        if op == 0xB7 and name == "<init>" and isinstance(target, Holder):
            if owner == MR:
                model = args[0]
                w = model.fields.get("field_78090_t", 64) if isinstance(model, Obj) else 64
                h = model.fields.get("field_78089_u", 32) if isinstance(model, Obj) else 32
                u, v = (int(args[1]), int(args[2])) if len(args) == 3 and isinstance(args[1], int) else (0, 0)
                target.value = Renderer(w, h, u, v)
            elif owner.startswith("lotr/client/model/") or owner == BIPED:
                target.value = self.construct(owner, args, desc=desc)
            else:
                target.value = Obj(owner)  # lists, item stacks and the like: inert
            return None
        target = unwrap(target)
        if op == 0xB7 and name == "<init>":
            if isinstance(target, Obj):  # super(...) inside a model constructor: same object
                self.construct(owner, args, this=target, desc=desc)
            return None
        if owner == MR or isinstance(target, Renderer):
            return self.renderer_call(target, name, args)
        if owner == "java/lang/Math":
            fn = {"toRadians": math.radians, "sin": math.sin, "cos": math.cos, "abs": abs,
                  "sqrt": math.sqrt}.get(name)
            if fn:
                return fn(args[0])
            if name in ("max", "min"):
                return (max if name == "max" else min)(args)
        if owner == "net/minecraft/util/MathHelper":
            fn = {"func_76126_a": math.sin, "func_76134_b": math.cos}.get(name)
            if fn:
                return fn(args[0])
        if isinstance(target, CubeList) and name == "clear":
            target.owner.cubes.clear()
            return None
        if isinstance(target, Obj) and name == "func_78085_a":  # ModelBase.setTextureOffset(name, u, v)
            return None
        return None

    def renderer_call(self, rd, name, args):
        if not isinstance(rd, Renderer):
            raise Unsupported(f"{name} on {rd!r}")
        if name == "func_78793_a":
            rd.pivot = [float(a) for a in args]
        elif name == "func_78790_a":
            rd.box(*args)
        elif name == "func_78789_a":
            rd.box(*args, 0.0)
        elif name == "func_78784_a":
            rd.uv = [int(args[0]), int(args[1])]
            return rd
        elif name == "func_78787_b":
            rd.tex = [int(args[0]), int(args[1])]
            return rd
        elif name == "func_78792_a":
            rd.children.append(args[0])
        else:
            raise Unsupported(f"ModelRenderer.{name}")
        return rd


def params(desc):
    out, i, inner = [], 0, desc[1:desc.index(")")]
    while i < len(inner):
        c = inner[i]
        if c == "L":
            j = inner.index(";", i)
            out.append(inner[i:j + 1])
            i = j + 1
        elif c == "[":
            j = i
            while inner[j] == "[":
                j += 1
            if inner[j] == "L":
                j = inner.index(";", j)
            out.append(inner[i:j + 1])
            i = j + 1
        else:
            out.append(c)
            i += 1
    return out


class ArmorModels:
    def __init__(self, jar, idtable, report):
        self.jar = jar
        self.idtable = idtable
        self.report = report

    def mapping(self):
        """(LOTRMod item field, model class, constructor descriptor, constant args) from
        LOTRArmorModels.getSpecialModels(): getstatic LOTRMod.<item>, new <Model>, dup, constants, invokespecial."""
        cf = ClassFile(self.jar.read(SETUP + ".class"))
        code = next(c for (n, d), c in cf.methods.items() if n == "getSpecialModels" and c)
        out, item, model, args = [], None, None, []
        i = 0
        while i < len(code):
            op = code[i]
            if op == 0xB2:
                owner, name, _ = cf.ref_full(struct.unpack(">H", code[i + 1:i + 3])[0])
                if owner == "lotr/common/LOTRMod":
                    item, model, args = name, None, []
                i += 3
            elif op == 0xBB:
                cls = cf.utf8(cf.pool[struct.unpack(">H", code[i + 1:i + 3])[0]][1])
                if cls.startswith("lotr/client/model/"):
                    model, args = cls, []
                i += 3
            elif 0x02 <= op <= 0x08:
                args.append(op - 0x03)
                i += 1
            elif 0x0B <= op <= 0x0D:
                args.append(float(op - 0x0B))
                i += 1
            elif op == 0x12:
                args.append(cf.constant(code[i + 1]))
                i += 2
            elif op == 0xB7:
                owner, name, desc = cf.ref_full(struct.unpack(">H", code[i + 1:i + 3])[0])
                if item and model and owner == model and name == "<init>":
                    out.append((item, model, desc, args[-len(params(desc)):] if params(desc) else []))
                    item, model, args = None, None, []
                i += 3
            else:
                i += {0x13: 3, 0x14: 3, 0x10: 2, 0x11: 3, 0x19: 2, 0x3A: 2, 0x15: 2, 0x36: 2, 0xB4: 3, 0xB5: 3,
                      0xB3: 3, 0xB6: 3, 0xB8: 3, 0xB9: 5, 0xBD: 3, 0xC0: 3, 0xC1: 3, 0x99: 3, 0x9A: 3, 0xA7: 3,
                      0xC6: 3, 0xC7: 3, 0x84: 3}.get(op, 1)
        return out

    def build(self, model_cls, desc, args):
        this = Machine(self.jar).construct(model_cls, list(args), desc=desc)
        w, h = this.fields.get("field_78090_t", 64), this.fields.get("field_78089_u", 32)
        parts = {}
        for field, part in BIPED_PARTS.items():
            rd = this.fields.get(field)
            parts[part] = rd.json() if isinstance(rd, Renderer) else {"pivot": [0, 0, 0], "rotation": [0, 0, 0],
                                                                       "cubes": [], "children": []}
        return {"texture": [w, h], "parts": parts}

    def run(self):
        items, models = {}, {}
        for field, model_cls, desc, args in self.mapping():
            if "Plate" in model_cls:
                continue  # LOTR lets you wear a dinner plate on your head; not armor, left out
            new = self.idtable.get(f"lotr:item.{field}#0")
            if not new:
                self.report["armor_models_unmapped_items"].append(field)
                continue
            new = new.split("[")[0].split(":")[1]
            name = snake(model_cls.split("/")[-1].replace("LOTRModel", ""))
            key = name + "".join(f"@{a:g}" if isinstance(a, float) else f"@{a}" for a in args)
            if key not in models:
                try:
                    models[key] = self.build(model_cls, desc, args)
                except Exception as e:  # noqa: BLE001 - recorded, the piece keeps the plain armor look
                    self.report["armor_models_failed"].append(f"{model_cls}: {e}")
                    continue
            entry = {"model": key}
            items[new] = entry
        self.report["armor_models"] = sorted(f"{k} -> {v['model']}" for k, v in items.items())
        return [{"items": items, "models": models}]

