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
FIGHTS_BACK = ("LOTREntityBear", "LOTREntityLionBase", "LOTREntityRhino", "LOTREntityAurochs", "LOTREntityCrocodile",
               "LOTREntityScorpion")

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


def is_animal(e):
    """LOTR's animals: everything in lotr.common.entity.animal (deer and bears are EntityAnimals; birds, rabbits,
    butterflies, crocodiles and scorpions are not, but live in the same package)."""
    return ".entity.animal." in ((e.get("classes") or [""])[0])


def behaviours_of(e):
    chain = [c.split(".")[-1] for c in e.get("classes") or []]
    out = [b for cls, b in BEHAVIOURS if cls in chain]
    if is_animal(e) and "animal" not in out:
        out.append("animal")
    return out


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
    if "EntityAnimal" in chain or is_animal(e):
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


RANGED_AI = "lotr/common/entity/ai/LOTREntityAIRangedAttack"
# creatures with their own ranged behaviour (rocks, snow, wraith balls, the mallorn ent's leaf bombs)
OWN_RANGED = ("LOTREntityMountainTroll", "LOTREntitySnowTroll", "LOTREntityMarshWraith", "LOTREntityMallornEnt")


def _calls(jar, cls, method, called):
    """Whether the class defines the method (any descriptor) and it invokes a method of this name: None when the class
    doesn't define it."""
    path = cls.replace(".", "/") + ".class"
    if not jar.has(path):
        return None
    cf = ClassFile(jar.read(path))
    codes = [code for (n, _), code in cf.methods.items() if n == method and code]
    if not codes:
        return None
    for code in codes:
        for i in range(len(code) - 2):
            if code[i] in (0xB6, 0xB7, 0xB9):
                try:
                    _, name = cf.ref_name(int.from_bytes(code[i + 1:i + 3], "big"))
                except (KeyError, TypeError, IndexError):
                    continue
                if name == called:
                    return True
    return False


def archer(e, jar):
    """LOTR's NPC archers and throwers: the LOTREntityAIRangedAttack(speed, min, [max], range) the most specific class
    builds, the NPC's npcRangedAccuracy, and whether it swaps to its melee weapon up close (onAttackModeChange
    reads getMeleeWeapon, as elves do) or only shoots and backs off (archers)."""
    classes = e.get("classes") or []
    chain = [c.split(".")[-1] for c in classes]
    if any(c in chain for c in OWN_RANGED):
        return None
    for c in classes:
        path = c.replace(".", "/") + ".class"
        if not jar.has(path):
            continue
        made = ClassFile(jar.read(path)).constructions(RANGED_AI)
        if not made:
            continue
        args = made[0]
        if len(args) == 3:
            speed, t, rng = args
            lo = hi = t
        elif len(args) == 4:
            speed, lo, hi, rng = args
        else:
            return None
        own = (_calls(jar, k, "onAttackModeChange", "getMeleeWeapon") for k in classes)
        swap = next((x for x in own if x is not None), False)
        for k in classes:  # LOTREntityEasterlingFireThrower: melee range Double.MAX_VALUE, so it never closes in
            kp = k.replace(".", "/") + ".class"
            code = jar.has(kp) and ClassFile(jar.read(kp)).methods.get(("getMeleeRange", "()D"))
            if code:
                cf = ClassFile(jar.read(kp))
                for i in range(len(code) - 2):
                    if code[i] == 0x14:
                        try:
                            v = cf.constant(int.from_bytes(code[i + 1:i + 3], "big"))
                        except IndexError:
                            continue
                        if isinstance(v, float) and v > 1e300:
                            swap = False
                break
        acc = (e.get("attributes") or {}).get("lotr.npcRangedAccuracy", 1.0)
        return {"ranged_speed": speed, "ranged_min": lo, "ranged_max": hi, "ranged_range": rng,
                "ranged_accuracy": acc, "ranged_swap": 1 if swap else 0}
    return None


def _riding_chance(cf, code):
    """LOTR's `spawnRidingHorse = rand.nextInt(n) == 0` in a constructor: n (1 = always), 0 when it's set false, None
    when this code doesn't set it."""
    for i in range(len(code) - 2):
        if code[i] != 0xB5:
            continue
        try:
            _, name = cf.ref_name(int.from_bytes(code[i + 1:i + 3], "big"))
        except (KeyError, TypeError, IndexError):
            continue
        if name != "spawnRidingHorse":
            continue
        for j in range(i - 3, max(0, i - 16), -1):
            if code[j] == 0xB6:
                try:
                    _, called = cf.ref_name(int.from_bytes(code[j + 1:j + 3], "big"))
                except (KeyError, TypeError, IndexError):
                    continue
                if called == "nextInt":
                    if code[j - 2] == 0x10:  # bipush
                        return code[j - 1]
                    if 0x03 <= code[j - 1] <= 0x08:  # iconst
                        return code[j - 1] - 0x03
        return 1 if code[i - 1] == 0x04 else 0
    return None


def rider(e, jar, mount_of):
    """LOTR's NPCs that spawn riding (LOTREntityNPC.spawnRidingHorse, set nextInt(n) == 0 in the most specific
    constructor that sets it) and what they ride (createMountToRide's `new` in the most specific class defining it,
    a LOTREntityHorse by default). mount_of maps a LOTR class to the port's id. None when it never rides."""
    classes = e.get("classes") or []
    chance = None
    for c in classes:
        path = c.replace(".", "/") + ".class"
        if not jar.has(path):
            continue
        cf = ClassFile(jar.read(path))
        for (n, _), code in cf.methods.items():
            if n == "<init>" and code:
                chance = _riding_chance(cf, code)
                if chance is not None:
                    break
        if chance is not None:
            break
    if not chance:
        return None
    armour_done = False
    mount = None
    armour = []
    for c in classes:
        path = c.replace(".", "/") + ".class"
        if not jar.has(path):
            continue
        cf = ClassFile(jar.read(path))
        code = next((code for (n, _), code in cf.methods.items() if n == "createMountToRide" and code), None)
        if not code:
            continue
        for i in range(len(code) - 2):
            try:
                if code[i] == 0xBB:  # new: the mount's class (an ItemStack is its armour)
                    k = cf.constant(int.from_bytes(code[i + 1:i + 3], "big"))
                    if isinstance(k, tuple) and mount_of(k[1].replace("/", ".")):
                        mount = mount_of(k[1].replace("/", "."))
                elif code[i] == 0xB2 and not armour_done:  # getstatic: LOTRMod.horseArmorGondor
                    owner, name = cf.ref_name(int.from_bytes(code[i + 1:i + 3], "big"))
                    if "Armor" in name or name in VANILLA_HORSE_ARMOUR:
                        armour.append(VANILLA_HORSE_ARMOUR.get(name, name))
            except (KeyError, TypeError, IndexError):
                continue
        armour_done = armour_done or bool(armour)
        if mount:
            break
    mount = mount or mount_of("lotr.common.entity.animal.LOTREntityHorse")
    return {"chance": chance, "mount": mount, "armour": armour} if mount else None


# 1.7.10's Items fields for vanilla horse armour (iron, gold, diamond)
VANILLA_HORSE_ARMOUR = {"field_151138_bX": "minecraft:iron_horse_armor", "field_151136_bY": "minecraft:golden_horse_armor",
                        "field_151125_bZ": "minecraft:diamond_horse_armor"}
