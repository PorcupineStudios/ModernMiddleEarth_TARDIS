"""
Builds a Middle-earth music resource pack from your own soundtrack files, on your own computer. Nothing it makes
belongs in this repo (the output goes outside it, and *.ogg packs are git-ignored).

    python3 converter/music_pack.py <folder with your tracks> [--map my_map.json] [--out <resourcepacks folder>]

- Needs ffmpeg on PATH for anything that isn't already .ogg (mp3, flac, m4a, wav...).
- Each track goes on one or more playlists (the names are LOTR's music regions, listed in
  lotr/src/generated/resources/assets/lotr_port/music/playlists.json). Which ones: the map file, a JSON object
  {"word in the file name": ["playlist", ...]}, tried in order, first match wins. Without --map the built-in
  guesses below are used; tracks that match nothing go to "unsorted" and are listed so you can add a rule.
  Run with --write-map to get the built-in map as a file to edit.
- Writes a folder pack "Middle-earth Music" (or a .zip with --zip) into --out (default: Minecraft's
  resourcepacks folder for this OS). Enable it in Options > Resource Packs.
"""
import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile

AUDIO = (".ogg", ".mp3", ".flac", ".m4a", ".wav", ".aac", ".opus", ".wma")

# file-name words -> playlists (music regions); edit with --write-map / --map
DEFAULT_MAP = {
    "frodo": ["shire"], "ending": ["shire", "lindon"], "main theme": ["eriador", "rhovanion"],
    "journey in the dark": ["misty_mountains"], "glorfindel": ["rivendell"], "shire": ["shire"], "hobbit": ["shire"], "bag end": ["shire"], "concerning": ["shire"], "bree": ["bree"],
    "prancing": ["bree"], "old forest": ["old_forest"], "bombadil": ["old_forest"], "barrow": ["barrow_downs"],
    "weathertop": ["eriador"], "amon sul": ["eriador"], "ford": ["rivendell"], "rivendell": ["rivendell"],
    "elrond": ["rivendell"], "council": ["rivendell"], "fellowship": ["eriador", "rhovanion"],
    "caradhras": ["misty_mountains"], "misty": ["misty_mountains"], "moria": ["misty_mountains"],
    "khazad": ["misty_mountains"], "balrog": ["misty_mountains"], "bridge": ["misty_mountains"],
    "lothlorien": ["lothlorien"], "lorien": ["lothlorien"], "galadriel": ["lothlorien"],
    "anduin": ["rhovanion"], "great river": ["rhovanion"], "argonath": ["rhovanion"], "amon hen": ["rhovanion"],
    "isengard": ["isengard"], "saruman": ["isengard"], "orthanc": ["isengard"], "uruk": ["isengard"],
    "rohan": ["rohan"], "edoras": ["rohan"], "helm": ["rohan"], "riders": ["rohan"], "fangorn": ["fangorn"],
    "ent": ["fangorn"], "gondor": ["gondor"], "minas tirith": ["gondor"], "osgiliath": ["gondor"],
    "ithilien": ["gondor"], "pelennor": ["gondor"], "mordor": ["mordor"], "black gate": ["mordor"],
    "morgul": ["mordor"], "mount doom": ["mordor"], "nazgul": ["mordor"], "ringwraith": ["mordor"],
    "shelob": ["mordor"], "dark lord": ["mordor"], "mirkwood": ["mirkwood"], "dol guldur": ["mirkwood"],
    "dale": ["dale"], "erebor": ["dale"], "dwarf": ["dwarven"], "harad": ["near_harad"], "umbar": ["near_harad"],
    "grey havens": ["lindon"], "havens": ["lindon"], "lindon": ["lindon"],
}


def minecraft_dir():
    home = os.path.expanduser("~")
    if sys.platform.startswith("win"):
        return os.path.join(os.environ.get("APPDATA", home), ".minecraft")
    if sys.platform == "darwin":
        return os.path.join(home, "Library", "Application Support", "minecraft")
    return os.path.join(home, ".minecraft")


def slug(name):
    s = re.sub(r"^\d+[\s._-]*", "", os.path.splitext(name)[0].lower())  # drop track numbers
    return re.sub(r"[^a-z0-9]+", "_", s).strip("_") or "track"


def playlists_for(name, rules):
    low = name.lower().replace("_", " ")
    for word, lists in rules.items():
        if word.lower() in low:
            return lists
    return ["unsorted"]


def convert(src, dst):
    if src.lower().endswith(".ogg"):
        shutil.copyfile(src, dst)
        return True
    if not shutil.which("ffmpeg"):
        print(f"  skipped (needs ffmpeg): {src}")
        return False
    r = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", src, "-vn", "-c:a", "libvorbis", "-q:a", "5",
                        dst])
    return r.returncode == 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("tracks", nargs="?", help="folder with your soundtrack files (searched recursively)")
    ap.add_argument("--map", help="JSON map of file-name words to playlists")
    ap.add_argument("--write-map", metavar="FILE", help="write the built-in map to FILE and stop")
    ap.add_argument("--out", help="where to put the pack (default: Minecraft's resourcepacks folder)")
    ap.add_argument("--zip", action="store_true", help="make a .zip instead of a folder")
    a = ap.parse_args()
    if a.write_map:
        json.dump(DEFAULT_MAP, open(a.write_map, "w", encoding="utf-8"), indent=2)
        print(f"wrote {a.write_map}")
        return
    if not a.tracks:
        ap.error("give the folder with your tracks")
    rules = json.load(open(a.map, encoding="utf-8")) if a.map else DEFAULT_MAP
    out = a.out or os.path.join(minecraft_dir(), "resourcepacks")
    pack = os.path.join(out, "Middle-earth Music")
    if os.path.exists(pack):
        shutil.rmtree(pack)
    sounds_dir = os.path.join(pack, "assets", "lotr_port", "sounds", "music")
    os.makedirs(sounds_dir)
    sounds, playlists, unsorted, used = {}, {}, [], set()
    files = sorted(os.path.join(r, f) for r, _, fs in os.walk(a.tracks) for f in fs if f.lower().endswith(AUDIO))
    for src in files:
        name = slug(os.path.basename(src))
        while name in used:
            name += "_2"
        used.add(name)
        if not convert(src, os.path.join(sounds_dir, name + ".ogg")):
            continue
        event = f"music.track.{name}"
        sounds[event] = {"sounds": [{"name": f"lotr_port:music/{name}", "stream": True}]}
        for p in playlists_for(os.path.basename(src), rules):
            playlists.setdefault(p, []).append(f"lotr_port:{event}")
            if p == "unsorted":
                unsorted.append(os.path.basename(src))
    with open(os.path.join(pack, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 34, "description": "Middle-earth music (private)"}}, f, indent=2)
    with open(os.path.join(pack, "assets", "lotr_port", "sounds.json"), "w", encoding="utf-8") as f:
        json.dump(sounds, f, indent=2)
    os.makedirs(os.path.join(pack, "assets", "lotr_port", "music"), exist_ok=True)
    with open(os.path.join(pack, "assets", "lotr_port", "music", "playlists.json"), "w", encoding="utf-8") as f:
        json.dump({"playlists": playlists}, f, indent=2)
    if a.zip:
        z = pack + ".zip"
        with zipfile.ZipFile(z, "w", zipfile.ZIP_STORED) as zf:  # ogg is already compressed
            for r, _, fs in os.walk(pack):
                for fn in fs:
                    p = os.path.join(r, fn)
                    zf.write(p, os.path.relpath(p, pack))
        shutil.rmtree(pack)
        pack = z
    print(f"{len(sounds)} tracks -> {pack}")
    for p, ids in sorted(playlists.items()):
        print(f"  {p}: {len(ids)}")
    if unsorted:
        print("Matched no rule (playlist 'unsorted', which no biome plays). Add words for these to a --map file:")
        for u in unsorted:
            print("  " + u)


if __name__ == "__main__":
    main()
