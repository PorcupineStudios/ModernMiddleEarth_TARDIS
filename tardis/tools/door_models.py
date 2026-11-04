"""Writes the open-door block models and the exterior / interior door blockstates.

An open door is a hollow shell: no front face, a frame round the doorway the size of the police box model's (2 px at
the sides and the sill, 4 px on top: 12 x 26 px), so the Immersive Portals portal sits in the doorway, just behind the
1 px deep frame (door tuning: offset -0.0625, 0.75 x 1.625 from 0.125 up).
Run from the repo root: python3 tardis/tools/door_models.py
"""
import json
import os

ROOT = "tardis/src/main/resources/assets/tardis_rb"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def face(tex, cull=None):
    f = {"texture": "#" + tex}
    if cull:
        f["cullface"] = cull
    return f


def open_model(front, side, up, down, half, front_dir):
    """A hollow shell open at front_dir ('north' or 'south'), frame on the open side."""
    back = "south" if front_dir == "north" else "north"
    z0, z1 = (0, 1) if front_dir == "north" else (15, 16)
    faces = {back: face("side", back), "east": face("side", "east"), "west": face("side", "west")}
    # only the outer ends get a roof and a floor: a lower half's top and an upper half's bottom would show as a
    # seam across the doorway, between the two blocks
    if half == "upper":
        faces["up"] = face("up", "up")
    else:
        faces["down"] = face("down", "down")
    shell = {"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}
    els = [shell,
           {"from": [0, 0, z0], "to": [2, 16, z1], "faces": {front_dir: face("front", front_dir),
                                                             "east": face("front")}},
           {"from": [14, 0, z0], "to": [16, 16, z1], "faces": {front_dir: face("front", front_dir),
                                                               "west": face("front")}}]
    if half == "upper":
        els.append({"from": [2, 12, z0], "to": [14, 16, z1], "faces": {front_dir: face("front", front_dir),
                                                                       "down": face("front")}})
    else:
        els.append({"from": [2, 0, z0], "to": [14, 2, z1], "faces": {front_dir: face("front", front_dir),
                                                                     "up": face("front")}})
    return {"parent": "minecraft:block/block", "textures": {"particle": "#side", "front": front, "side": side,
                                                            "up": up, "down": down}, "elements": els}


def write(path, data):
    with open(os.path.join(ROOT, path), "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    variants = {}
    for look in range(4):
        for half in ("lower", "upper"):
            base = f"exterior_l{look}_{half}"
            t = json.load(open(os.path.join(ROOT, "models/block", base + ".json")))["textures"]
            write(f"models/block/{base}_open.json",
                  open_model(t["north"], t["south"], t["up"], t["down"], half, "north"))
            for facing, y in ROT.items():
                for is_open in ("false", "true"):
                    v = {"model": f"tardis_rb:block/{base}" + ("_open" if is_open == "true" else "")}
                    if y:
                        v["y"] = y
                    # closing: still open (portal up) while the doors swing shut
                    for closing in ("false", "true"):
                        variants[f"closing={closing},facing={facing},half={half},look={look},open={is_open}"] = v
    write("blockstates/exterior.json", {"variants": variants})

    door = "tardis_rb:block/interior_door"
    inner = {}
    for half in ("lower", "upper"):
        write(f"models/block/interior_door_{half}_open.json", open_model(door, door, door, door, half, "north"))
        inner[f"half={half},open=false"] = {"model": door}
        inner[f"half={half},open=true"] = {"model": f"tardis_rb:block/interior_door_{half}_open"}
    write("blockstates/interior_door.json", {"variants": inner})


if __name__ == "__main__":
    main()
