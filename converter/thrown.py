"""
LOTR's thrown weapons and missiles (missing-features task 28): spears thrown after a draw, throwing axes, darts
from the Taurethrim blowgun, the sling and its pebbles, conkers, fire pots, mystery webs, plates, and orc bombs.

Numbers are LOTR's, read from these classes (values only):
- LOTREntityProjectileBase (an arrow carrying its item): damage = speed x the item's ranged multiplier, rounded up,
  crits add nextInt(damage / 2 + 2); 1200 ticks in the ground (6000 when it can be picked up).
- LOTRItemSpear: drawn up to getMaxDrawTime 20, power f = min(1, (f^2 + 2f) / 3), no throw under 0.1, speed f x 2,
  crit at full power; multiplier = (attack damage + enchantment bonus) x 0.7 (exported as rangedDamage x 2).
- LOTRItemThrowingAxe: thrown at once at speed 2; multiplier = (material damage + 4) x 0.5 (rangedDamage x 2);
  LOTREntityThrowingAxe spins a full turn every 9 ticks.
- LOTRItemBlowgun: draw 5, no shot under 0.65, speed f x 2; LOTREntityDart: damage factor 1, knockback x 0.5,
  poisoned darts give LOTR's standard poison.
- EntityThrowable subclasses (speed = func_70182_d, gravity = func_70185_h): pebble 1.0 / 0.04 (1 damage, 2 from a
  sling), conker 1.0 / 0.04 (1), fire pot 1.2 / 0.04 (1 to all within 3 blocks, 3 on a direct hit, burning
  2-4 s, twice that on a direct hit), plate 1.5 / 0.02 (1; breaks glass, then 3 in 4 of the glass within 2 blocks),
  mystery web 0.5 / 0.01 (1 in 4 a Mirkwood spider).
- LOTRBlockOrcBomb / LOTREntityOrcBomb: meta & 7 strength, & 8 fire; fuse 40 + strength x 20 ticks (from another
  blast: nextInt(fuse / 4) + fuse / 8); blast power (strength + 1) x 4.
- LOTREntityAIRangedAttack and LOTREntityNPC.npcArrowAttack / npcCrossbowAttack for NPC archers (see behaviours.py).
"""
import json
import os

from assets import NS, write_json

SETTINGS = {
    "spear_draw": 20, "spear_min_power": 0.1, "spear_speed": 2.0,
    "axe_speed": 2.0, "axe_spin_ticks": 9,
    "blowgun_draw": 5, "blowgun_min_power": 0.65, "blowgun_speed": 2.0, "dart_damage": 1.0, "dart_knockback": 0.5,
    "ground_ticks": 1200, "ground_ticks_pickup": 6000,
    "pebble_speed": 1.0, "pebble_gravity": 0.04, "pebble_damage": 1.0, "sling_damage": 2.0,
    "conker_speed": 1.0, "conker_gravity": 0.04, "conker_damage": 1.0,
    "fire_pot_speed": 1.2, "fire_pot_gravity": 0.04, "fire_pot_damage": 1.0, "fire_pot_hit_damage": 3.0,
    "fire_pot_range": 3.0, "fire_pot_burn_min": 2, "fire_pot_burn_max": 4,
    "plate_speed": 1.5, "plate_gravity": 0.02, "plate_damage": 1.0, "plate_glass_range": 2,
    "plate_glass_chance": 0.75,
    "web_speed": 0.5, "web_gravity": 0.01, "web_spider_chance": 0.25,
    "bomb_fuse": 40, "bomb_fuse_per_strength": 20, "bomb_power_per_strength": 4.0,
    # LOTREntityNPC: arrows at 1.3 + distance / 80, bolts at 1 + distance / 16 x 0.015; 1.7.10 spread 0.0075 per
    # point of npcRangedAccuracy (1.21's shoot() spreads 0.0172275 per point)
    "npc_arrow_speed": 1.3, "npc_arrow_speed_per_block": 0.0125, "npc_bolt_speed": 1.0,
    "npc_bolt_speed_per_block": 0.0009375, "npc_spread_scale": 0.4354,
    "npc_flee_distance": 5.0, "npc_flee_min": 8, "npc_flee_max": 16, "npc_flee_speed": 1.5, "npc_stop_after": 20,
}

THROWABLES = {  # LOTR item class -> missile
    "LOTRItemPebble": "pebble", "LOTRItemConker": "conker", "LOTRItemFirePot": "fire_pot",
    "LOTRItemMysteryWeb": "mystery_web", "LOTRItemPlate": "plate",
}


class Thrown:
    def __init__(self, gen, export, data):
        self.gen = gen
        self.export = export
        self.data = data

    def new_id(self, old, meta=0):
        new = self.gen.idtable.get(f"{old}#{meta}")
        return new.split("[")[0].split(":", 1)[1] if new and new.startswith(NS + ":") else None

    def run(self):
        items = {i["id"]: i for i in self.gen.items}
        blocks = {b["id"]: b for b in self.gen.blocks}
        with open(os.path.join(self.export, "items.json"), encoding="utf-8") as f:
            exported = json.load(f)
        counts = {"thrown_items": 0, "orc_bombs": 0}
        for x in exported:
            cls = (x.get("classes") or [""])[0].split(".")[-1]
            for v in x["variants"]:
                new = self.new_id(x["id"], v["meta"])
                e = items.get(new)
                if not e:
                    continue
                w = v.get("weapon") or {}
                if cls == "LOTRItemSpear":
                    e["throw"] = {"damage": round(w.get("rangedDamage", 0) * 2, 4)}
                elif cls == "LOTRItemThrowingAxe":
                    e["kind"] = "throwing_axe"
                    e["throw"] = {"damage": round(w.get("rangedDamage", 0) * 2, 4)}
                elif cls == "LOTRItemBlowgun":
                    e["kind"] = "blowgun"
                elif cls == "LOTRItemDart":
                    e["kind"] = "dart"
                    e["dart"] = {"poisoned": "Poisoned" in x["id"]}
                elif cls == "LOTRItemSling":
                    e["kind"] = "sling"
                elif cls in THROWABLES:
                    e["throwable"] = THROWABLES[cls]
                    if not e.get("places"):
                        e["kind"] = "throwable"
                else:
                    continue
                counts["thrown_items"] += 1
        for meta in range(16):
            new = self.new_id("lotr:tile.orcBomb", meta)
            if new in blocks and self.gen.idtable.get(f"lotr:tile.orcBomb#{meta}"):
                blocks[new]["kind"] = "orc_bomb"
                blocks[new]["bomb"] = {"strength": meta & 7, "fire": bool(meta & 8)}
                counts["orc_bombs"] += 1
        write_json(os.path.join(self.data, NS, "thrown", "settings.json"), SETTINGS)
        tags = os.path.join(self.data, NS, "tags", "item")
        write_json(os.path.join(tags, "darts.json"),
                   {"values": sorted(f"{NS}:{i['id']}" for i in self.gen.items if i.get("kind") == "dart")})
        write_json(os.path.join(tags, "sling_ammo.json"), {"values": [f"{NS}:pebble"]})
        return counts
