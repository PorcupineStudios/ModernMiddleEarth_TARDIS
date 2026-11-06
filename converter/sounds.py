"""
LOTR's sounds for the 1.21.1 port.

Copies every .ogg under assets/lotr/sounds/ and turns LOTR's sounds.json (101 events) into the port's:
- assets/lotr_port/sounds/<path>.ogg, path segments in snake_case (1.21 resource paths are lower case)
- assets/lotr_port/sounds.json with a subtitle per event (English in en_us)
- lotr_port/content/sounds.json, the list LotrSounds registers
- assets/lotr_port/ambience.json, the numbers of LOTR's client ambience (LOTRAmbience), read in
  client/LotrAmbience: wight moans per biome, wind high up and in storms, sea waves on coasts, the rain and thunder
  that replace vanilla's in Middle-earth

Event ids are LOTR's with each dot-separated part snake_cased: "halfTroll.say" -> "half_troll.say".

Where each sound plays was found by scanning LOTR's classes for the "lotr:<event>" strings (see WHERE). NPC voices go
on the NPC types (npcs.py calls npc_sounds); creatures the port doesn't have yet keep their table in CREATURE_SOUNDS
for when they arrive.
"""
import json
import os

from assets import NS, snake, write_json

# LOTR entity class -> its voice per event (LOTREntityOrc.getLivingSound etc.). Checked from the most specific class.
NPC_SOUNDS = {
    "LOTREntityOrc": {"ambient": "orc.say", "hurt": "orc.hurt", "death": "orc.death"},
    "LOTREntityDwarf": {"hurt": "dwarf.hurt", "attack": "dwarf.attack", "kill": "dwarf.kill"},
    "LOTREntityElf": {"ambient": "elf.male.say", "attack": "elf.male.attack"},
    "LOTREntityHalfTroll": {"ambient": "half_troll.say", "hurt": "half_troll.hurt", "death": "half_troll.death"},
    "LOTREntitySaruman": {"ambient": "orc.say"},
    "LOTREntityGollum": {"ambient": "gollum.say", "hurt": "gollum.hurt", "death": "gollum.death"},
    "LOTREntityBarrowWight": {"hurt": "wight.hurt", "death": "wight.death"},
    "LOTREntityMarshWraith": {"hurt": "wight.hurt", "death": "wight.death"},
    "LOTREntityTroll": {"ambient": "troll.say", "step": "troll.step"},
    "LOTREntityBalrog": {"ambient": "troll.say", "step": "troll.step"},
    "LOTREntityEnt": {"step": "ent.step"},
    "LOTREntitySpiderBase": {"ambient": "minecraft:entity.spider.ambient", "hurt": "minecraft:entity.spider.hurt",
                             "death": "minecraft:entity.spider.death", "step": "minecraft:entity.spider.step"},
    "LOTREntitySkeletalWraith": {"ambient": "minecraft:entity.skeleton.ambient",
                                 "hurt": "minecraft:entity.skeleton.hurt", "death": "minecraft:entity.skeleton.death",
                                 "step": "minecraft:entity.skeleton.step"},
    "LOTREntityWarg": {"ambient": "warg.say", "hurt": "warg.hurt", "death": "warg.death", "attack": "warg.attack"},
}
# Creatures not ported yet (missing-features task 9/26): their LOTR sounds, for the creature types when they land.
CREATURE_SOUNDS = {
    "aurochs": {"ambient": "aurochs.say", "hurt": "aurochs.hurt", "death": "aurochs.death"},
    "bear": {"ambient": "bear.say", "hurt": "bear.hurt", "death": "bear.death"},
    "bird": {"ambient": "bird.say", "hurt": "bird.hurt"},
    "crebain": {"ambient": "bird.crow.say", "hurt": "bird.crow.hurt"},
    "seagull": {"ambient": "bird.seagull.say", "hurt": "bird.seagull.hurt"},
    "crocodile": {"ambient": "crocodile.say", "death": "crocodile.death", "attack": "crocodile.snap"},
    "deer": {"ambient": "deer.say", "hurt": "deer.hurt", "death": "deer.death"},
    "elk": {"ambient": "elk.say", "hurt": "elk.hurt", "death": "elk.death"},
    "ent": {"step": "ent.step"},
    "flamingo": {"ambient": "flamingo.say", "hurt": "flamingo.hurt", "death": "flamingo.death"},
    "lion": {"ambient": "lion.say", "hurt": "lion.hurt", "death": "lion.death"},
    "midges": {"ambient": "midges.swarm"},
    "rabbit": {"hurt": "rabbit.hurt", "death": "rabbit.death", "flee": "rabbit.flee"},
    "rhino": {"ambient": "rhino.say", "hurt": "rhino.hurt", "death": "rhino.death"},
    "swan": {"ambient": "swan.hiss"},
    "troll": {"ambient": "troll.say", "step": "troll.step", "sniff": "troll.sniff", "sneeze": "troll.sneeze",
              "transform": "troll.transform"},
    "zebra": {"ambient": "zebra.say", "hurt": "zebra.hurt", "death": "zebra.death"},
}

# Subtitle verbs for the last part of an event id
VERBS = {"say": "calls", "hurt": "hurts", "death": "dies", "attack": "attacks", "kill": "cheers", "step": "stomps",
         "snap": "snaps", "flee": "flees", "hiss": "hisses", "swarm": "swarm", "open": "opens", "close": "closes",
         "stone_open": "grinds open", "stone_close": "grinds shut", "break": "breaks", "place": "placed",
         "sniff": "sniffs", "sneeze": "sneezes", "transform": "turns to stone"}

# Subtitles that the id doesn't spell out well
SUBTITLES = {
    "ambient.terrain.sea": "Waves crash", "ambient.weather.rain": "Rain falls", "ambient.weather.thunder": "Thunder",
    "ambient.weather.wind": "Wind howls", "wight.ambience": "Something moans", "event.fast_travel": "Journey ends",
    "event.pledge": "Pledge sworn", "event.unpledge": "Pledge broken", "event.trade": "Coins clink",
    "item.horn": "Horn blows", "item.puff": "Pipe puffs", "item.mug_fill": "Vessel fills",
    "item.crossbow": "Crossbow fires", "item.crossbow_load": "Crossbow loads", "item.dart": "Dart flies",
    "item.balrog_whip": "Whip cracks", "item.mace_sauron": "Mace smashes", "item.gandalf_fireball": "Fireball",
    "item.structure_spawner": "Structure appears", "music.jazzelf": "Elves play jazz", "misc.heyguys": "Hey guys",
    "orc.fire": "Orc lights a fuse", "elf.wood_elf_teleport": "Wood-elf vanishes",
    "ent.mallorn.leaf_attack": "Mallorn-ent hurls leaves", "ent.mallorn.summon_ent": "Mallorn-ent calls",
    "troll.olog_hai_hammer": "Olog-hai smashes", "troll.rock_smash": "Rock smashes", "wraith.spawn": "Wraith rises",
    "wraith.marsh_wraith_shoot": "Marsh-wraith casts",
}

# LOTRAmbience, read from its bytecode (constants in updateAmbience / onPlaySound)
AMBIENCE = {
    "wight": {  # lotr:wight.ambience: 1 in `chance` per tick in these biomes, pitch x (0.8 + 0.4 random)
        "sound": f"{NS}:wight.ambience",
        "biomes": {"LOTRBiomeGenBarrowDowns": [3000, 1.0], "LOTRBiomeGenDolGuldur": [1000, 0.85],
                   "LOTRBiomeGenDeadMarshes": [2400, 1.0], "LOTRBiomeGenMirkwoodCorrupted": [3000, 1.0],
                   "LOTRBiomeGenOldForest": [6000, 1.0], "LOTRBiomeGenUtumno": [1000, 0.75]},
        "cave_biomes": {"LOTRBiomeGenUtumno": 500},  # vanilla cave sound, 1 in 500
    },
    "wind": {  # lotr:ambient.weather.wind: at most 4 at once, 1 in 20 ticks, height band by weather
        "sound": f"{NS}:ambient.weather.wind", "max": 4, "chance": 20, "range": 16,
        "clear": [100, 180], "rain": [80, 120], "sandstorm": [60, 80],
        # LOTRWeatherRenderer.isSandstormBiome: no rain and a sand top block (filled in from world.json)
        "sandstorm_biomes": [],
    },
    "sea": {  # lotr:ambient.terrain.sea: at most 3, near ocean/beach/coast biomes, surface water within 64 blocks
        "sound": f"{NS}:ambient.terrain.sea", "max": 3, "range": 64, "tries": 8,
        "biomes": ["LOTRBiomeGenOcean", "LOTRBiomeGenBeach", "LOTRBiomeGenLindonCoast", "LOTRBiomeGenFarHaradCoast"],
    },
    "weather": {  # onPlaySound: vanilla rain/thunder -> LOTR's, in Middle-earth
        "minecraft:weather.rain": f"{NS}:ambient.weather.rain",
        "minecraft:weather.rain.above": f"{NS}:ambient.weather.rain",
        "minecraft:entity.lightning_bolt.thunder": f"{NS}:ambient.weather.thunder",
    },
}


def event_id(old):
    return ".".join(snake(p) for p in old.split("."))


def npc_sounds(classes):
    """A port NPC type's sounds from its LOTR class chain (most specific first)."""
    for c in classes:
        name = c.split(".")[-1]
        if name in NPC_SOUNDS:
            return {k: v if ":" in v else f"{NS}:{v}" for k, v in NPC_SOUNDS[name].items()}
    return {}


class Sounds:
    def __init__(self, gen, jar, assets_dir, content_dir, world):
        self.gen = gen
        self.jar = jar
        self.assets = assets_dir
        self.content = content_dir
        self.world = world  # export world.json, to turn LOTR biome classes into port biome ids

    def copy(self, path):
        """lotr sound path ("halfTroll/say1") -> copied file, port path ("half_troll/say1")."""
        src = f"assets/lotr/sounds/{path}.ogg"
        new = "/".join(snake(p) for p in path.split("/"))
        if not self.jar.has(src):
            self.gen.report["sounds_missing_file"].append(path)
            return None
        dst = os.path.join(self.assets, "sounds", new + ".ogg")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "wb") as f:
            f.write(self.jar.read(src))
        return new

    def subtitle(self, eid):
        if eid in SUBTITLES:
            return SUBTITLES[eid]
        parts = eid.split(".")
        verb = VERBS.get(parts[-1], parts[-1].replace("_", " "))
        subject = " ".join(p.replace("_", " ") for p in parts[:-1] if p not in ("item", "block", "event", "misc"))
        return f"{subject.title()} {verb}".strip() if subject else verb.capitalize()

    def biome_ids(self, classes):
        out = []
        for b in self.world.get("biomes", []):
            cls = b.get("class", "").split(".")[-1]
            name = b.get("fields", {}).get("biomeName")
            if cls in classes and name:
                out.append(f"{NS}:{snake(name)}")
        return sorted(set(out))

    def ambience(self):
        a = json.loads(json.dumps(AMBIENCE))
        w = a["wight"]
        w["biomes"] = {bid: {"chance": c, "pitch": p} for cls, (c, p) in AMBIENCE["wight"]["biomes"].items()
                       for bid in self.biome_ids({cls})}
        w["cave_biomes"] = {bid: c for cls, c in AMBIENCE["wight"]["cave_biomes"].items()
                            for bid in self.biome_ids({cls})}
        a["wind"]["sandstorm_biomes"] = sorted(
            f"{NS}:{snake(b['fields']['biomeName'])}" for b in self.world.get("biomes", [])
            if b.get("fields", {}).get("biomeName") and b["fields"].get("enableRain") is False
            and "sand" in str(b["fields"].get("topBlock", "")))
        a["sea"]["biomes"] = self.biome_ids(set(AMBIENCE["sea"]["biomes"]))
        write_json(os.path.join(self.assets, "ambience.json"), a)
        return a

    def run(self):
        old = json.loads(self.jar.read("assets/lotr/sounds.json"))
        out, events = {}, []
        for name, spec in sorted(old.items()):
            eid = event_id(name)
            sounds = []
            for s in spec.get("sounds", []):
                path = s if isinstance(s, str) else s.get("name")
                new = self.copy(path)
                if not new:
                    continue
                if isinstance(s, str):
                    sounds.append(f"{NS}:{new}")
                else:
                    entry = {k: v for k, v in s.items() if k in ("volume", "pitch", "weight", "stream")}
                    sounds.append({"name": f"{NS}:{new}", **entry})
            if not sounds:
                continue
            out[eid] = {"sounds": sounds, "subtitle": f"subtitles.{NS}.{eid}"}
            if spec.get("category") in ("record", "music"):
                for s in out[eid]["sounds"]:
                    if isinstance(s, dict):
                        s["stream"] = True
            self.gen.lang["en_us"][f"subtitles.{NS}.{eid}"] = self.subtitle(eid)
            events.append({"id": eid, "category": spec.get("category", "neutral")})
        write_json(os.path.join(self.assets, "sounds.json"), out)
        write_json(os.path.join(self.content, "sounds.json"), events)
        amb = self.ambience()
        files = sum(1 for n in self.jar.names if n.startswith("assets/lotr/sounds/") and n.endswith(".ogg"))
        return {"sound_events": len(events), "sound_files": files,
                "ambience_biomes": len(amb["wight"]["biomes"]) + len(amb["sea"]["biomes"])}
