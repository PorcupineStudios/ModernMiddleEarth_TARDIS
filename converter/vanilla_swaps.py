"""
LOTR things vanilla 1.21.1 now has itself: LOTR's own copper (ingot, ore, block) is dropped and vanilla's copper is
used everywhere instead, so the pack has one copper (Create, other mods and vanilla's copper blocks all take it).
Copper goblets and treasure piles stay: they are LOTR things made of copper.

Runs on top of the generated content (no LOTR jar needed), after renames.py and before raw_ores.py (so LOTR's copper
ore is gone before raw ores are made: vanilla copper ore already drops vanilla raw copper). Rerun alone with
``python vanilla_swaps.py``; idempotent. For each LOTR id -> vanilla id in SWAPS:
- every `lotr_port:<id>` in JSON (recipes, trades, quests, loot, advancements, worldgen...), structure templates
  (.nbt) and converter/idtable.json becomes the vanilla id; ore features place the deepslate form (DEEPSLATE) in
  their deepslate targets;
- the LOTR entry, its blockstate, models, loot table, lang keys and creative tab places are removed, and pictures
  nothing uses any more;
- LOTR recipes that now only repeat a vanilla one (a vanilla result from vanilla ingredients: smelting copper ore,
  9 ingots <-> block) are removed; recipes that make LOTR things keep working with vanilla copper;
- the vanilla ids are taken out of LOTR's copies of vanilla and c: tag files (vanilla and NeoForge tag them already).
"""
import gzip
import json
import os
import re
import sys

from renames import NS, OUT, HERE

SWAPS = {
    "copper_ingot": "minecraft:copper_ingot",
    "copper_ore": "minecraft:copper_ore",
    "block_of_copper": "minecraft:copper_block",
    # raw_ores.py's, if it ran before this step
    "raw_copper": "minecraft:raw_copper",
    "raw_copper_block": "minecraft:raw_copper_block",
}
DEEPSLATE = {"minecraft:copper_ore": "minecraft:deepslate_copper_ore"}


def full(i):
    return f"{NS}:{i}"


class Swapper:
    def __init__(self, out=OUT):
        self.out = out
        self.content = os.path.join(out, NS, "content")
        self.assets = os.path.join(out, "assets", NS)
        self.data = os.path.join(out, "data")
        self.pattern = re.compile(r"\b" + NS + r":(" + "|".join(map(re.escape, sorted(SWAPS, key=len, reverse=True)))
                                  + r")(?![a-z0-9_/.\-])")
        self.counts = {"entries_removed": 0, "files_rewritten": 0, "files_removed": 0, "recipes_removed": 0,
                       "templates": 0}

    def load(self, path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)

    def save(self, path, data):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")

    def remove(self, path):
        if os.path.exists(path):
            os.remove(path)
            self.counts["files_removed"] += 1

    def run(self):
        textures = self.remove_entries()
        self.rewrite_all()
        self.deepslate()
        self.remove_duplicate_recipes()
        self.clean_tags()
        self.remove_unused_textures(textures)
        return self.counts

    # ------------------------------------------------------------------ the LOTR entries

    def remove_entries(self):
        """Drops the LOTR blocks and items; returns the pictures their models used."""
        textures = set()
        for name in ("blocks.json", "items.json"):
            path = os.path.join(self.content, name)
            entries = self.load(path)
            keep = [e for e in entries if e["id"] not in SWAPS]
            if len(keep) != len(entries):
                self.counts["entries_removed"] += len(entries) - len(keep)
                self.save(path, keep)
        tabs_path = os.path.join(self.content, "tabs.json")
        tabs = self.load(tabs_path)
        for t in tabs:
            t["items"] = [i for i in t["items"] if i not in SWAPS]
        self.save(tabs_path, tabs)
        for i in SWAPS:
            for rel in (f"models/item/{i}.json", f"models/block/{i}.json", f"blockstates/{i}.json"):
                p = os.path.join(self.assets, rel)
                if os.path.exists(p) and rel.startswith("models"):
                    for v in self.load(p).get("textures", {}).values():
                        textures.add(v)
                self.remove(p)
            self.remove(os.path.join(self.data, NS, "loot_table", "blocks", i + ".json"))
        lang_dir = os.path.join(self.assets, "lang")
        for f in os.listdir(lang_dir):
            p = os.path.join(lang_dir, f)
            lang = self.load(p)
            keep = {k: v for k, v in lang.items() if not any(k in (f"block.{NS}.{i}", f"item.{NS}.{i}") for i in SWAPS)}
            if keep != lang:
                self.save(p, keep)
        return textures

    # ------------------------------------------------------------------ every reference

    def rewrite_all(self):
        roots = [os.path.join(self.out, s) for s in (f"assets/{NS}", f"data/{NS}", NS, "data/c", "data/minecraft",
                                                    "data/neoforge")]
        main = os.path.join(os.path.dirname(self.out), "..", "main", "resources")
        for root in roots + [os.path.normpath(main)]:
            for dirpath, _, files in os.walk(root):
                for f in files:
                    p = os.path.join(dirpath, f)
                    if f.endswith(".json"):
                        self.rewrite(p)
                    elif f.endswith(".nbt"):
                        self.template(p)
        self.rewrite(os.path.join(HERE, "idtable.json"))

    def rewrite(self, path):
        if not os.path.exists(path):
            return
        with open(path, encoding="utf-8") as f:
            text = f.read()
        out = self.pattern.sub(lambda m: SWAPS[m.group(1)], text)
        if out != text:
            with open(path, "w", encoding="utf-8") as f:
                f.write(out)
            self.counts["files_rewritten"] += 1

    def template(self, path):
        with open(path, "rb") as f:
            raw = f.read()
        packed = raw[:2] == b"\x1f\x8b"
        data = gzip.decompress(raw) if packed else raw
        out, last, n = bytearray(), 0, 0
        for m in re.finditer(rb"lotr_port:[a-z0-9_]+", data):
            s, e = m.span()
            i = m.group(0).decode()[len(NS) + 1:]
            if i not in SWAPS or s < 2 or int.from_bytes(data[s - 2:s], "big") != e - s:
                continue
            new = SWAPS[i].encode()
            out += data[last:s - 2] + len(new).to_bytes(2, "big") + new
            last, n = e, n + 1
        if n:
            data = bytes(out + data[last:])
            with open(path, "wb") as f:
                f.write(gzip.compress(data, mtime=0) if packed else data)
            self.counts["templates"] += 1

    def deepslate(self):
        folder = os.path.join(self.data, NS, "worldgen", "configured_feature")
        for dirpath, _, files in os.walk(folder):
            for f in files:
                p = os.path.join(dirpath, f)
                feat = self.load(p)
                changed = False
                for t in (feat.get("config") or {}).get("targets", []):
                    name = t.get("state", {}).get("Name")
                    if name in DEEPSLATE and "deepslate" in json.dumps(t.get("target")):
                        t["state"]["Name"] = DEEPSLATE[name]
                        changed = True
                if changed:
                    self.save(p, feat)
                    self.counts["files_rewritten"] += 1

    # ------------------------------------------------------------------ recipes and tags

    def remove_duplicate_recipes(self):
        targets = set(SWAPS.values())
        root = os.path.join(self.data, NS, "recipe")
        counted = 0
        for dirpath, _, files in os.walk(root):
            for f in files:
                p = os.path.join(dirpath, f)
                r = self.load(p)
                res = r.get("result") or {}
                if not isinstance(res, dict) or res.get("id") not in targets:
                    continue
                inputs = {k: v for k, v in r.items() if k in ("ingredient", "ingredients", "key")}
                ids = re.findall(r'"(?:item|tag)": "([^"]+)"', json.dumps(inputs))
                if ids and all(i.startswith("minecraft:") for i in ids) and r.get("type", "").startswith("minecraft:"):
                    self.remove(p)
                    self.counts["recipes_removed"] += 1
                    rel = os.path.relpath(p, root).replace(os.sep, "/")
                    if not rel.startswith(("own/", "create/")):
                        counted += 1
        if counted:  # summary.json counts the converted recipes (GameTest everyRecipeLoaded)
            path = os.path.join(self.content, "summary.json")
            summary = self.load(path)
            summary[0]["recipes"] -= counted
            self.save(path, summary)

    def clean_tags(self):
        """Vanilla and NeoForge's own tags already hold vanilla copper: LOTR's copies drop it."""
        targets = set(SWAPS.values()) | set(DEEPSLATE.values())
        for sub in ("minecraft", "c"):
            folder = os.path.join(self.data, sub, "tags")
            for dirpath, _, files in os.walk(folder):
                for f in files:
                    p = os.path.join(dirpath, f)
                    t = self.load(p)
                    keep = [v for v in t["values"] if (v["id"] if isinstance(v, dict) else v) not in targets]
                    if keep == t["values"]:
                        continue
                    if keep:
                        t["values"] = keep
                        self.save(p, t)
                    else:
                        self.remove(p)
        sources = os.path.join(self.content, "tag_sources.json")
        if os.path.exists(sources):
            s = self.load(sources)
            s = {k: [v for v in vs if v not in targets] for k, vs in s.items()}
            self.save(sources, {k: v for k, v in s.items() if v})

    def remove_unused_textures(self, textures):
        if not textures:
            return
        used = set()
        for dirpath, _, files in os.walk(os.path.join(self.assets, "models")):
            for f in files:
                with open(os.path.join(dirpath, f), encoding="utf-8") as fh:
                    used |= set(re.findall(NS + r":[a-z0-9_/]+", fh.read()))
        for tex in textures:
            if tex.startswith(NS + ":") and tex not in used:
                base = os.path.join(self.assets, "textures", tex.split(":", 1)[1])
                self.remove(base + ".png")
                self.remove(base + ".png.mcmeta")


def run(out=OUT):
    return Swapper(out).run()


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
