"""
Which LOTR creature behaviours an NPC type has (npc/behaviour in the port) and their numbers, from the LOTR entity
class chain. Per-class values are read from the classes themselves (classfile.py); the shared constants are LOTR's,
read from the bytecode of the classes named beside them.
"""
from classfile import ClassFile

BEHAVIOURS = (
    ("LOTREntityTroll", "troll"),
    ("LOTREntityMountainTroll", "rock_thrower"),
    ("LOTREntitySnowTroll", "snow_thrower"),
    ("LOTREntitySpiderBase", "spider"),
    ("LOTREntityTree", "tree"),
    ("LOTREntityEnt", "ent"),
    ("LOTREntityHuornBase", "huorn"),
    ("LOTREntityBalrog", "balrog"),
    ("LOTREntityBarrowWight", "wight"),
    ("LOTREntityMarshWraith", "marsh_wraith"),
    ("LOTREntityGollum", "gollum"),
    ("LOTREntityWarg", "warg"),
    ("LOTREntityHalfTroll", "half_troll"),
    ("EntityAnimal", "animal"),
)
# LOTR animals that turn on what hurts them (LOTREntityBear, lions, rhinos, aurochs); every other animal panics
FIGHTS_BACK = ("LOTREntityBear", "LOTREntityLionBase", "LOTREntityRhino", "LOTREntityAurochs", "LOTREntityCrocodile")

# Shared constants (LOTR class they come from)
CONSTANTS = {
    "troll": {"burn_ticks": 300, "shade_range": 12, "tickle_alignment": 100},  # LOTREntityAITrollFleeSun, Troll
    # LOTREntityMountainTroll/SnowTroll: getMeleeRange 12, LOTREntityAIRangedAttack(1.2, 30-60 / 20-30, 24)
    "rock_thrower": {"melee_range": 12, "ranged_range": 24, "throw_min": 30, "throw_max": 60, "throw_damage": 5},
    "snow_thrower": {"melee_range": 12, "ranged_range": 24, "throw_min": 20, "throw_max": 30, "throw_damage": 3},
    "tree": {"tree_weakness": 2, "tree_resistance": 0.5},  # LOTREntityTree.doTreeDamageCalculation
    "ent": {"max_branches": 3, "heal_interval": 300, "heal_range": 24, "heal_ticks": 160},  # LOTREntityAIEntHealSapling
    "huorn": {"wake_range": 24},  # LOTREntityAINearestAttackableTargetHuorn
    "balrog": {"fire_chance": 80, "charge_after": 200, "charge_speed": 2.0, "charge_damage": 2.0},
    # LOTREntityMarshWraith: LOTREntityAIRangedAttack(1.6, 40, 12) - always casts, never closes in
    "marsh_wraith": {"melee_range": 0, "ranged_range": 12, "throw_min": 40, "throw_max": 40, "throw_damage": 5,
                     "despawn_ticks": 100},
    "gollum": {"fish_required": 20},  # LOTREntityGollum.prevFishRequired
}


def behaviours_of(e):
    chain = [c.split(".")[-1] for c in e.get("classes") or []]
    return [b for cls, b in BEHAVIOURS if cls in chain]


def _float_return(jar, cls, method):
    """The constant a no-arg float getter returns (getTrollScale: ldc 1.6f / fconst_1)."""
    path = cls.replace(".", "/") + ".class"
    if not jar.has(path):
        return None
    cf = ClassFile(jar.read(path))
    code = next((c for (n, d), c in cf.methods.items() if n == method and d == "()F" and c), None)
    if not code:
        return None
    if code[0] == 0x12:
        return cf.constant(code[1])
    if code[0] in (0x13,):
        return cf.constant(int.from_bytes(code[1:3], "big"))
    if 0x0B <= code[0] <= 0x0D:
        return float(code[0] - 0x0B)
    return None


def _sets_true(jar, cls, field):
    """Whether the class's constructor sets a boolean field to true (trollImmuneToSun)."""
    path = cls.replace(".", "/") + ".class"
    if not jar.has(path):
        return False
    cf = ClassFile(jar.read(path))
    for (n, d), code in cf.methods.items():
        if n != "<init>" or not code:
            continue
        for i in range(len(code) - 3):
            if code[i] == 0x04 and code[i + 1] == 0xB5:  # iconst_1; putfield
                _, name = cf.ref_name(int.from_bytes(code[i + 2:i + 4], "big"))
                if name == field:
                    return True
    return False


def behaviour_data(e, jar):
    classes = e.get("classes") or []
    data = {}
    for b in behaviours_of(e):
        data.update(CONSTANTS.get(b, {}))
    chain = [c.split(".")[-1] for c in classes]
    if "LOTREntityTroll" in chain:
        for c in classes:
            scale = _float_return(jar, c, "getTrollScale")
            if scale is not None:
                data["scale"] = round(scale, 4)
                break
        data["sun_immune"] = 1 if any(_sets_true(jar, c, "trollImmuneToSun") for c in classes) else 0
        if "LOTREntityMountainTrollChieftain" in chain:
            data["two_heads"] = 1
    if "EntityAnimal" in chain:
        data["fights_back"] = 1 if any(c in chain for c in FIGHTS_BACK) else 0
        data["panic_speed"] = 1.25  # EntityAnimal's EntityAIPanic speed in LOTR's animals
    if "LOTREntitySpiderBase" in chain:
        # LOTREntityMirkwoodSpider: size 0-2, venom half none half slowness/poison; Mordor: size 1-3, poison;
        # Utumno ice spiders: size 1-3, slowness
        name = e["name"]
        if "Mirkwood" in name:
            data.update({"size_min": 0, "size_max": 2, "venom": -1})
        elif "Mordor" in name:
            data.update({"size_min": 1, "size_max": 3, "venom": 2})
        else:  # LOTREntityUtumnoIceSpider: size nextInt(4), slowness
            data.update({"size_min": 0, "size_max": 3, "venom": 1})
    return data
