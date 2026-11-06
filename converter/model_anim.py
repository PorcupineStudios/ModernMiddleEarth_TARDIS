"""
LOTR's creature animations (each LOTRModel*'s setRotationAngles) as data for the port.

The method is run symbolically: the inputs are names (limb swing, limb swing amount, age in ticks, head yaw, head
pitch, the attack swing, entity state), arithmetic and MathHelper/Math calls build expression trees, and every write
to a part's rotation or position is kept as an expression. A branch on something not known until the game runs
(the swing, the entity's state) forks the run; at the end the paths are merged into "ite" (if-then-else)
expressions. Calls to the superclass's or the model's own helper methods are followed; ModelBiped's (vanilla's)
animation becomes the marker "biped", which the client fills with vanilla's humanoid animation.

Output, per model, in npc_model/<name>.json under "anim":
    {"biped": bool, "set": [[part, field, EXPR], ...]}
    EXPR = number | ["in", name] | ["rest", part, field] | [op, EXPR...]
    ops: add sub mul div neg rem cos sin abs min max sqrt rad (degrees->radians) atan2 floor
         ite [cond, a, b]; cond ops: lt le gt ge eq ne and or not
    in: limb, amount, age, yaw, pitch, swing, riding, child, sneak, bow, held_right, held_left,
        entity.<method or field> (entity state; the client answers the ones it knows, else 0)
Fields: xRot yRot zRot x y z visible.
"""
import math
import struct

from classfile import ClassFile

ROT = {"field_78795_f": "xRot", "field_78796_g": "yRot", "field_78808_h": "zRot",
       "field_78800_c": "x", "field_78797_d": "y", "field_78798_e": "z", "field_78806_j": "visible",
       "field_78807_k": "hidden"}
MODEL_FIELDS = {"field_78095_p": "swing", "field_78093_q": "riding", "field_78091_s": "child",
                "field_78117_n": "sneak", "field_78118_o": "bow", "field_78120_m": "held_right",
                "field_78119_l": "held_left"}
BIPED_RENDERERS = {"field_78116_c": "head", "field_78114_d": "hat", "field_78115_e": "body",
                   "field_78112_f": "right_arm", "field_78113_g": "left_arm", "field_78123_h": "right_leg",
                   "field_78124_i": "left_leg"}
MATH = {("net/minecraft/util/MathHelper", "func_76134_b"): "cos", ("net/minecraft/util/MathHelper", "func_76126_a"): "sin",
        ("java/lang/Math", "abs"): "abs", ("java/lang/Math", "min"): "min", ("java/lang/Math", "max"): "max",
        ("java/lang/Math", "sqrt"): "sqrt", ("net/minecraft/util/MathHelper", "func_76129_c"): "sqrt",
        ("java/lang/Math", "toRadians"): "rad", ("java/lang/Math", "cos"): "cos", ("java/lang/Math", "sin"): "sin",
        ("java/lang/Math", "atan2"): "atan2", ("java/lang/Math", "toDegrees"): "deg", ("net/minecraft/util/MathHelper", "func_76135_e"): "abs",
        ("net/minecraft/util/MathHelper", "func_76141_d"): "floor", ("net/minecraft/util/MathHelper", "func_76128_c"): "floor",
        ("net/minecraft/util/MathHelper", "func_76131_a"): "clamp", ("java/lang/Math", "signum"): "sign"}
MAX_PATHS = 512
MAX_UNROLL = 6
SET_ANIM = ("func_78087_a", "(FFFFFFLnet/minecraft/entity/Entity;)V")
RENDER = ("func_78088_a", "(Lnet/minecraft/entity/Entity;FFFFFF)V")
PRE_RENDER = ("func_77041_b", "(Lnet/minecraft/entity/EntityLivingBase;F)V")
GL = "org/lwjgl/opengl/GL11"


class Part:
    """A model part reference (the renderer in a field)."""

    def __init__(self, name):
        self.name = name


class Unknown(Exception):
    pass


def pick_ops(op_lists, conds, ex):
    """Merging GL matrix ops of several paths: identical lists stay, otherwise each path's ops are kept with
    its condition (["when", cond, [ops...]])."""
    first = op_lists[0]
    if all(o == first for o in op_lists):
        return first
    out = []
    for c, ops in zip(conds, op_lists):
        if ops:
            out.append(["when", c, ops])
    return out


def simplify(op, *args):
    if all(isinstance(a, (int, float)) and not isinstance(a, bool) for a in args):
        try:
            a = args
            return {"add": lambda: a[0] + a[1], "sub": lambda: a[0] - a[1], "mul": lambda: a[0] * a[1],
                    "div": lambda: a[0] / a[1], "neg": lambda: -a[0], "cos": lambda: math.cos(a[0]),
                    "sin": lambda: math.sin(a[0]), "abs": lambda: abs(a[0]), "rad": lambda: math.radians(a[0]),
                    "min": lambda: min(a), "max": lambda: max(a), "sqrt": lambda: math.sqrt(a[0])}[op]()
        except (KeyError, ZeroDivisionError, ValueError):
            pass
    if op == "mul" and (args[0] == 0 or args[1] == 0):
        return 0.0
    if op == "mul" and args[0] == 1:
        return args[1]
    if op == "mul" and args[1] == 1:
        return args[0]
    if op == "add" and args[0] == 0:
        return args[1]
    if op in ("add", "sub") and args[1] == 0:
        return args[0]
    return [op, *args]


class State:
    def __init__(self, locals_, writes, cond, gl=None, draws=None):
        self.locals = locals_
        self.writes = writes  # (part, field) -> expr
        self.cond = cond  # list of conditions on this path
        self.gl = gl if gl is not None else [[]]  # GL matrix stack: list of op lists
        self.color = [1.0, 1.0, 1.0, 1.0]
        self.blend = False
        self.draws = draws if draws is not None else []

    def fork(self):
        s = State(list(self.locals), dict(self.writes), list(self.cond), [list(m) for m in self.gl],
                  list(self.draws))
        s.color = list(self.color)
        s.blend = self.blend
        s.loops = getattr(self, "loops", 0)
        return s


class AnimExporter:
    def __init__(self, jar, model_fields=None):
        self.jar = jar
        self.model_fields = model_fields or {}
        self.classes = {}
        self.biped = False

    def cls(self, name):
        if name not in self.classes:
            path = name + ".class"
            self.classes[name] = ClassFile(self.jar.read(path)) if self.jar.has(path) else None
        return self.classes[name]

    def read_field(self, st, part, field):
        return st.writes.get((part, field), ["rest", part, field])

    def run(self, model_cls):
        """Merged animation and drawing of the model class. Runs the model's render method (which calls its
        setRotationAngles and then draws each part under its own GL transforms), or setRotationAngles alone:
        {"biped": bool, "set": [[part, field, expr], ...], "draws": [DRAW, ...] or absent}
        DRAW = {"part", "ops": [[op, args...]...], "color": [r, g, b, a], "blend": bool, "cond": EXPR}"""
        self.biped = False
        self.model_cls = model_cls
        if self.owns(model_cls, RENDER) and self.declares_render(model_cls):
            params = ["this", "entity", ["in", "limb"], ["in", "amount"], ["in", "age"], ["in", "yaw"],
                      ["in", "pitch"], ["in", "scale"]]
            key = RENDER
        else:
            params = ["this", ["in", "limb"], ["in", "amount"], ["in", "age"], ["in", "yaw"], ["in", "pitch"],
                      ["in", "scale"], "entity"]
            key = SET_ANIM
        start = State(params + [None] * 40, {}, [])
        ends = self.method(model_cls, key, start)
        if not ends:
            return None
        out_draws = None
        if key == RENDER:
            out_draws = []
            seen = set()
            for d in ends[0].draws:
                k = str(d)
                if k not in seen:
                    seen.add(k)
                    out_draws.append(d)
        keys = []
        for st in ends:
            for k in st.writes:
                if k not in keys:
                    keys.append(k)
        out = []
        for part, field in keys:
            expr = None
            for st in reversed(ends):
                val = st.writes.get((part, field), ["rest", part, field])
                if expr is None:
                    expr = val
                    continue
                cond = ["and", *st.cond] if len(st.cond) > 1 else (st.cond[0] if st.cond else None)
                expr = val if cond is None or val == expr else ["ite", cond, val, expr]
            out.append([part, field, expr])
        res = {"biped": self.biped, "set": out}
        if out_draws is not None:
            res["draws"] = out_draws
        return res

    def declares_render(self, model_cls):
        """Whether a LOTR class (not vanilla ModelBiped) defines the render method this model uses."""
        o = model_cls
        while o:
            cf = self.cls(o)
            if cf is None:
                return False
            if RENDER in cf.methods and cf.methods[RENDER]:
                return True
            o = cf.super_name
        return False

    def model_method(self, model_cls, method):
        """Draws made by one of the model's own methods taking the scale (a troll's renderWoodenClub...)."""
        self.model_cls = model_cls
        cf = self.cls(model_cls)
        key = next((k for k in cf.methods if k[0] == method and cf.methods[k]), None)
        if key is None:
            return None
        start = State(["this", ["in", "scale"]] + [None] * 40, {}, [])
        ends = self.method(model_cls, key, start)
        return ends[0].draws if ends else None

    def pre_render(self, render_cls):
        """A LOTR renderer's preRenderCallback as GL ops (scale, rotate, translate) with entity inputs."""
        start = State(["this", "entity", ["in", "partial"]] + [None] * 40, {}, [])
        cf = self.cls(render_cls)
        if cf is None or not self.owns(render_cls, PRE_RENDER):
            return None
        ends = self.method(render_cls, PRE_RENDER, start)
        if not ends:
            return None
        return {"ops": ends[0].gl[-1], "color": ends[0].color, "blend": ends[0].blend}

    def method(self, owner, key, state):
        """Runs owner.key from `state`; returns the end states (one per path)."""
        cf = None
        o = owner
        while o:
            cf = self.cls(o)
            if cf is None:
                if o in ("net/minecraft/client/model/ModelBiped", "net/minecraft/client/model/ModelZombie"):
                    if key == SET_ANIM:
                        self.biped = True
                    elif key == RENDER:
                        # vanilla ModelBiped.render: setRotationAngles (the model's own, virtual), then the parts
                        args = [state.locals[i] for i in (2, 3, 4, 5, 6, 7)] + [state.locals[1]]
                        sub = State(["this", *args] + [None] * 40, state.writes, state.cond, state.gl, state.draws)
                        ends = self.method(self.model_cls, SET_ANIM, sub)
                        st = ends[0] if ends else state
                        for part in ("head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "hat"):
                            st.draws.append({"part": part, "ops": list(st.gl[-1]), "color": list(st.color),
                                             "blend": st.blend, "cond": self.cond_of(st)})
                        return [st]
                return [state]
            if key in cf.methods and cf.methods[key]:
                break
            o = cf.super_name
        else:
            return [state]
        code = cf.methods[key]
        return self.exec(cf, code, state)

    def exec(self, cf, code, state):
        """Runs the method's code forward, merging paths where they meet again (only forward jumps): the result
        is one state whose values are if-then-else expressions over the conditions that chose each path."""
        state.cond = []
        pending = {0: [(state, [])]}
        ends = []
        while pending:
            pc = min(pending)
            st, stack = self.merge(pending.pop(pc))
            result = self.step_until_branch(cf, code, st, pc, stack, stop=lambda at: at in pending)
            kind = result[0]
            if kind == "end":
                ends.append((result[1], []))
            elif kind == "join":
                _, st, at, stack = result
                pending.setdefault(at, []).append((st, stack))
            elif kind == "fork":
                _, st, cond, target, fall, stack = result
                if isinstance(cond, bool):
                    pending.setdefault(target if cond else fall, []).append((st, stack))
                    continue
                a = st.fork()
                a.cond.append(cond)
                b = st.fork()
                b.cond.append(["not", cond])
                pending.setdefault(target, []).append((a, list(stack)))
                pending.setdefault(fall, []).append((b, list(stack)))
            if len(pending) > MAX_PATHS:
                raise Unknown("too many paths")
        if not ends:
            return []
        st, _ = self.merge(ends)
        return [st]

    @staticmethod
    def cond_of(st):
        return ["and", *st.cond] if len(st.cond) > 1 else (st.cond[0] if st.cond else True)

    def merge(self, items):
        """One state from several paths reaching the same point."""
        if len(items) == 1:
            return items[0]
        base, stack = items[-1]
        out = base.fork()
        out.stack_ret = getattr(base, "ret", None)
        conds = [self.cond_of(st) for st, _ in items]

        def pick(values):
            expr = values[-1]
            for c, v in zip(reversed(conds[:-1]), reversed(values[:-1])):
                if self.same(v, expr):
                    continue
                expr = v if c is True else ["ite", c, v, expr]
            return expr

        n = max(len(st.locals) for st, _ in items)
        out.locals = [pick([st.locals[i] if i < len(st.locals) else None for st, _ in items]) for i in range(n)]
        keys = []
        for st, _ in items:
            for k in st.writes:
                if k not in keys:
                    keys.append(k)
        out.writes = {k: pick([st.writes.get(k, ["rest", *k]) for st, _ in items]) for k in keys}
        stacks = [s for _, s in items]
        depth = min(len(s) for s in stacks)
        merged_stack = [pick([s[i] for s in stacks]) for i in range(depth)]
        rets = [getattr(st, "ret", None) for st, _ in items]
        if any(r is not None for r in rets):
            out.ret = pick(rets)
        draws, seen = [], set()
        for st, _ in items:
            for d in st.draws:
                k = str(d)
                if k not in seen:
                    seen.add(k)
                    draws.append(d)
        out.draws = draws
        out.gl = base.gl
        if any(len(st.gl) == len(base.gl) for st, _ in items):
            out.gl = [[pick_ops([st.gl[i] if i < len(st.gl) else [] for st, _ in items], conds, self)
                       for i in range(len(base.gl))][j] for j in range(len(base.gl))]
        out.color = [pick([st.color[i] for st, _ in items]) for i in range(4)]
        out.blend = any(st.blend for st, _ in items)
        common = [c for c in items[0][0].cond if all(c in st.cond for st, _ in items)]
        rest = [self.cond_of(State([], {}, [c for c in st.cond if c not in common])) for st, _ in items]
        out.cond = common + ([] if any(r is True for r in rest) else [["or", *rest]])
        return out, merged_stack

    @staticmethod
    def same(a, b):
        if isinstance(a, Part) and isinstance(b, Part):
            return a.name == b.name
        return a == b

    def step_until_branch(self, cf, code, st, pc, stack, stop=lambda at: False):
        L = st.locals
        first = True

        def u2(at):
            return struct.unpack(">H", code[at:at + 2])[0]

        def s2(at):
            return struct.unpack(">h", code[at:at + 2])[0]

        steps = 0
        while pc < len(code):
            if not first and stop(pc):
                return ("join", st, pc, stack)
            first = False
            steps += 1
            if steps > 20000:
                raise Unknown("loop")
            op = code[pc]
            if op == 0x00:
                pc += 1
            elif op == 0x01:
                stack.append(None)
                pc += 1
            elif 0x02 <= op <= 0x08:
                stack.append(op - 0x03)
                pc += 1
            elif 0x09 <= op <= 0x0A:
                stack.append(op - 0x09)
                pc += 1
            elif 0x0B <= op <= 0x0F:
                stack.append(float(op - 0x0B) if op <= 0x0D else float(op - 0x0E))
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
            elif 0x15 <= op <= 0x19:
                stack.append(L[code[pc + 1]])
                pc += 2
            elif 0x1A <= op <= 0x2D:
                stack.append(L[(op - 0x1A) % 4])
                pc += 1
            elif 0x36 <= op <= 0x3A:
                L[code[pc + 1]] = stack.pop()
                pc += 2
            elif 0x3B <= op <= 0x4E:
                L[(op - 0x3B) % 4] = stack.pop()
                pc += 1
            elif op == 0x57:
                stack.pop()
                pc += 1
            elif op == 0x58:
                stack.pop()
                pc += 1
            elif op == 0x59:
                stack.append(stack[-1])
                pc += 1
            elif op == 0x5A:
                a, b = stack.pop(), stack.pop()
                stack += [a, b, a]
                pc += 1
            elif op == 0x5C:  # dup2 (a double/long counts as one entry here)
                stack.append(stack[-1])
                pc += 1
            elif op in (0x60, 0x61, 0x62, 0x63):
                b, a = stack.pop(), stack.pop()
                stack.append(simplify("add", a, b))
                pc += 1
            elif op in (0x64, 0x65, 0x66, 0x67):
                b, a = stack.pop(), stack.pop()
                stack.append(simplify("sub", a, b))
                pc += 1
            elif op in (0x68, 0x69, 0x6A, 0x6B):
                b, a = stack.pop(), stack.pop()
                stack.append(simplify("mul", a, b))
                pc += 1
            elif op in (0x6C, 0x6D, 0x6E, 0x6F):
                b, a = stack.pop(), stack.pop()
                stack.append(simplify("div", a, b))
                pc += 1
            elif op in (0x70, 0x71, 0x72, 0x73):
                b, a = stack.pop(), stack.pop()
                stack.append(simplify("rem", a, b))
                pc += 1
            elif op in (0x74, 0x75, 0x76, 0x77):
                stack.append(simplify("neg", stack.pop()))
                pc += 1
            elif op == 0x84:
                v = L[code[pc + 1]]
                L[code[pc + 1]] = simplify("add", v, struct.unpack(">b", code[pc + 2:pc + 3])[0])
                pc += 3
            elif 0x85 <= op <= 0x90:  # conversions
                v = stack.pop()
                if op in (0x8B, 0x8E, 0x88, 0x87 - 2) and isinstance(v, float):
                    v = int(v)
                elif op in (0x8B, 0x8E, 0x88) and not isinstance(v, (int, float)):
                    v = ["floor", v]
                stack.append(v)
                pc += 1
            elif op in (0x94, 0x95, 0x96, 0x97, 0x98):  # lcmp fcmpl fcmpg dcmpl dcmpg
                b, a = stack.pop(), stack.pop()
                stack.append(("cmp", a, b))
                pc += 1
            elif 0x99 <= op <= 0x9E or 0x9F <= op <= 0xA6 or op in (0xC6, 0xC7):
                if 0x99 <= op <= 0x9E:
                    v = stack.pop()
                    rel = ["eq", "ne", "lt", "ge", "gt", "le"][op - 0x99]
                    if isinstance(v, tuple) and v[0] == "cmp":
                        a, b = v[1], v[2]
                    else:
                        a, b = v, 0
                elif op in (0xC6, 0xC7):
                    v = stack.pop()
                    rel = "eq" if op == 0xC6 else "ne"
                    a, b = (0 if v is None else 1), 0
                else:
                    b, a = stack.pop(), stack.pop()
                    rel = ["eq", "ne", "lt", "ge", "gt", "le", "eq", "ne"][op - 0x9F]
                cond = self.compare(rel, a, b)
                if isinstance(cond, bool):
                    pc = pc + s2(pc + 1) if cond else pc + 3
                    continue
                return ("fork", st, cond, pc + s2(pc + 1), pc + 3, stack)
            elif op == 0xA7:
                target = pc + s2(pc + 1)
                if target <= pc:
                    # a loop: unrolled on this path; a loop whose count is only known in game (an ent's extra
                    # branches) is unrolled up to MAX_UNROLL times, each turn guarded by its condition
                    st.loops = getattr(st, "loops", 0) + 1
                    if st.loops > MAX_UNROLL:
                        return ("end", st)
                    pc = target
                    continue
                return ("join", st, target, stack)
            elif op in (0xAC, 0xAD, 0xAE, 0xAF, 0xB0, 0xB1):
                st.ret = stack.pop() if op != 0xB1 and stack else None
                return ("end", st)
            elif op == 0xB2:
                stack.append(["in", "static"])
                pc += 3
            elif op == 0xB3:
                stack.pop()
                pc += 3
            elif op == 0xB4:
                owner, name, desc = cf.ref_full(u2(pc + 1))
                obj = stack.pop()
                stack.append(self.getfield(st, obj, owner, name, desc))
                pc += 3
            elif op == 0xB5:
                owner, name, _ = cf.ref_full(u2(pc + 1))
                v, obj = stack.pop(), stack.pop()
                if isinstance(obj, Part) and name in ROT:
                    field = ROT[name]
                    if field == "hidden":
                        field, v = "visible", simplify("sub", 1, v) if not isinstance(v, (int, bool)) else (0 if v else 1)
                    st.writes[(obj.name, field)] = v
                elif obj == "this" and name not in MODEL_FIELDS:
                    st.writes[("this", name)] = v
                pc += 3
            elif op in (0xB6, 0xB7, 0xB8, 0xB9):
                owner, name, desc = cf.ref_full(u2(pc + 1))
                nargs = self.nargs(desc)
                args = [stack.pop() for _ in range(nargs)][::-1]
                target = None if op == 0xB8 else stack.pop()
                res = self.invoke(st, op, owner, name, desc, target, args)
                if res == "__forked__":
                    raise Unknown("helper with branches")
                if not desc.endswith(")V"):
                    stack.append(res)
                pc += 5 if op == 0xB9 else 3
            elif op == 0xBB:
                stack.append(["in", "new"])
                pc += 3
            elif op == 0xC0:
                pc += 3
            elif op == 0xC1:  # instanceof
                owner = cf.utf8(cf.pool[u2(pc + 1)][1])
                v = stack.pop()
                stack.append(["in", "entity.is." + owner.split("/")[-1]] if v == "entity" else 0)
                pc += 3
            elif op == 0xBE:
                stack.pop()
                stack.append(0)
                pc += 1
            elif op == 0x32 or op == 0x30 or op == 0x2E:
                stack.pop()
                stack.pop()
                stack.append(["in", "array"])
                pc += 1
            else:
                raise Unknown(f"opcode {op:#x}")
        return ("end", st)

    @staticmethod
    def nargs(desc):
        from armor_models import params
        return len(params(desc))

    @staticmethod
    def compare(rel, a, b):
        if isinstance(a, (int, float)) and isinstance(b, (int, float)):
            return {"eq": a == b, "ne": a != b, "lt": a < b, "ge": a >= b, "gt": a > b, "le": a <= b}[rel]
        return [rel, a, b]

    def getfield(self, st, obj, owner, name, desc):
        if obj == "this":
            if name in MODEL_FIELDS:
                return ["in", MODEL_FIELDS[name]]
            if name in BIPED_RENDERERS:
                return Part(BIPED_RENDERERS[name])
            if desc.endswith("ModelRenderer;"):
                return Part(name)
            if ("this", name) in st.writes:
                return st.writes[("this", name)]
            return self.model_fields.get(name, ["in", "model." + name])
        if isinstance(obj, Part):
            if name in ROT:
                return self.read_field(st, obj.name, ROT[name])
            return ["in", "part." + name]
        if obj == "entity":
            return ["in", "entity." + name]
        return ["in", "unknown." + name]

    def invoke(self, st, op, owner, name, desc, target, args):
        if (owner, name) in MATH:
            fn = MATH[(owner, name)]
            return simplify(fn, *args)
        if target == "entity" or (isinstance(target, list) and target[:2] == ["in", "entity"]):
            return ["in", "entity." + name]
        if target == "this" and op in (0xB7, 0xB6):
            key = (name, desc)
            if key == SET_ANIM or self.owns(owner, key):
                sub = State(["this", *args] + [None] * 40, st.writes, st.cond)
                sub.gl = st.gl
                sub.draws = st.draws
                sub.color = st.color
                sub.blend = st.blend
                ends = self.method(owner if op == 0xB7 else owner, key, sub)
                if len(ends) == 1:
                    st.writes = ends[0].writes
                    st.draws = ends[0].draws
                    st.gl = ends[0].gl
                    st.color = ends[0].color
                    st.blend = ends[0].blend
                    return getattr(ends[0], "ret", None)
                return "__forked__"
            return ["in", "model." + name]
        if owner == GL:
            top = st.gl[-1]
            if name == "glPushMatrix":
                st.gl.append(list(top))
            elif name == "glPopMatrix" and len(st.gl) > 1:
                st.gl.pop()
            elif name in ("glTranslatef", "glTranslated"):
                top.append(["translate", *args])
            elif name in ("glRotatef", "glRotated"):
                top.append(["rotate", *args])
            elif name in ("glScalef", "glScaled"):
                top.append(["scale", *args])
            elif name in ("glColor4f", "glColor3f"):
                st.color = list(args) + ([1.0] if len(args) == 3 else [])
            elif name == "glEnable" and args and args[0] == 3042:
                st.blend = True
            elif name == "glDisable" and args and args[0] == 3042:
                st.blend = False
            return None
        if isinstance(target, Part) and name == "func_78794_c":  # postRender: move into the part's frame
            st.gl[-1].append(["attach", target.name])
            return None
        if isinstance(target, Part) and name in ("func_78785_a", "func_78791_b"):
            st.draws.append({"part": target.name, "ops": list(st.gl[-1]), "color": list(st.color),
                             "blend": st.blend, "cond": self.cond_of(st)})
            return None
        if isinstance(target, Part):
            if name == "func_78793_a":  # setRotationPoint
                for f, v in zip(("x", "y", "z"), args):
                    st.writes[(target.name, f)] = v
                return None
            return None
        return ["in", "call." + name]

    def owns(self, owner, key):
        o = owner
        while o:
            cf = self.cls(o)
            if cf is None:
                # vanilla's ModelBiped animates and draws (emulated in method())
                return key in (SET_ANIM, RENDER) and o in ("net/minecraft/client/model/ModelBiped",
                                                           "net/minecraft/client/model/ModelZombie")
            if key in cf.methods and cf.methods[key]:
                return True
            o = cf.super_name
        return False
