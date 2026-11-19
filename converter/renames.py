"""
Renames LOTR things whose name 1.21.1 already uses for something else, so players and other mods don't mix them up.

LOTR's amethyst (a gem ore "Amethyst Vein", the gem, "Amethyst Block") becomes tanzanite: vanilla 1.21 has its own
amethyst (shards, geodes, blocks), and the common tag `c:gems/amethyst` means vanilla's shard (the TARDIS scanner lens
takes it), so LOTR's gem was counted as vanilla amethyst.

Runs on top of the generated content (no LOTR jar needed), first of generate.py's post-steps, so every later step and
every file already written sees the new ids; it can be rerun alone (``python renames.py``) and is idempotent (after
one run there is nothing left to rename). What it changes, for each old -> new word in RENAMES:
- ids `lotr_port:<...old...>` wherever they appear (any `_`- or `/`-separated part of the path equal to the old word):
  content lists, models, blockstates, loot tables, recipes, tags, trades, quests, worldgen, advancements;
- file names under lotr_port's assets and data (and `data/c/tags/**/<old>.json`, LOTR's own c: tags);
- `#c:<group>/<old>` tag references, and the c: tag folders' names;
- bare ids in the mod's content lists (`lotr_port/content/*.json`: blocks, items, tabs...);
- lang keys `<kind>.lotr_port.<...old...>` and, in those keys only, the word itself in every language (LANG);
- block names in structure templates (.nbt) and converter/idtable.json values (for structures exported later).
"""
import gzip
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "lotr", "src", "generated", "resources")
NS = "lotr_port"

RENAMES = {"amethyst": "tanzanite"}

# The word as LOTR's translations wrote it -> the new word, per language (tried in order, so longer forms first).
LANG = {
    "amethyst": {
        "en_us": [("Amethyst", "Tanzanite"), ("amethyst", "tanzanite")],
        "en_gb": [("Amethyst", "Tanzanite"), ("amethyst", "tanzanite")],
        "de_de": [("Amethyst", "Tansanit"), ("amethyst", "tansanit")],
        "es_mx": [("Amatista", "Tanzanita"), ("amatista", "tanzanita")],
        "es_ar": [("Amatista", "Tanzanita"), ("amatista", "tanzanita")],
        "fr_fr": [("d'améthyste", "de tanzanite"), ("Améthyste", "Tanzanite"), ("améthyste", "tanzanite")],
        "fr_ca": [("d'améthyste", "de tanzanite"), ("Améthyste", "Tanzanite"), ("améthyste", "tanzanite")],
        "hu_hu": [("Ametiszt", "Tanzanit"), ("ametiszt", "tanzanit")],
        "it_it": [("Ametista", "Tanzanite"), ("ametista", "tanzanite")],
        "ja_jp": [("アメジスト", "タンザナイト")],
        "nl_nl": [("Amethist", "Tanzaniet"), ("amethist", "tanzaniet")],
        "pl_pl": [("ametystu", "tanzanitu"), ("Ametyst", "Tanzanit"), ("ametyst", "tanzanit")],
        "pt_br": [("Ametista", "Tanzanita"), ("ametista", "tanzanita")],
        "pt_pt": [("Ametista", "Tanzanite"), ("ametista", "tanzanite")],
        "ru_ru": [("Аметист", "Танзанит"), ("аметист", "танзанит")],
        "tr_tr": [("Mor Kuvars", "Tanzanit"), ("mor kuvars", "tanzanit")],
        "zh_cn": [("紫晶", "坦桑石")],
    },
}

ID = re.compile(NS + r":[a-z0-9_/.\-]+")
LANG_KEY = re.compile(r"\b([a-z_]+)\." + NS + r"\.([a-z0-9_.]+)")


def part(old, new, path):
    """`path` with every `_`/`/`-separated part equal to `old` replaced."""
    return "".join(new if p == old else p for p in re.split(r"([_/.])", path))


def rename_text(text, old, new):
    text = ID.sub(lambda m: NS + ":" + part(old, new, m.group(0)[len(NS) + 1:]), text)
    text = LANG_KEY.sub(lambda m: f"{m.group(1)}.{NS}.{part(old, new, m.group(2))}", text)
    return re.sub(r"(#?c:(?:[a-z_]+/)+)" + re.escape(old) + r"\b", lambda m: m.group(1) + new, text)


def rename_nbt(data, old, new):
    """Renames lotr_port ids stored as NBT strings (2-byte big-endian length, then the UTF-8 text)."""
    out, last, n = bytearray(), 0, 0
    for m in re.finditer(rb"lotr_port:[a-z0-9_/.\-]+", data):
        s, e = m.span()
        if s < 2 or int.from_bytes(data[s - 2:s], "big") != e - s:
            continue
        renamed = (NS + ":" + part(old, new, m.group(0).decode()[len(NS) + 1:])).encode()
        if renamed == m.group(0):
            continue
        out += data[last:s - 2] + len(renamed).to_bytes(2, "big") + renamed
        last, n = e, n + 1
    return (bytes(out + data[last:]), n) if n else (data, 0)


class Renamer:
    def __init__(self, out=OUT):
        self.out = out
        self.counts = {"files_renamed": 0, "files_rewritten": 0, "lang_entries": 0, "templates": 0}

    def roots(self):
        """Where lotr_port's own files live (c: tags are LOTR's own c: tag files)."""
        for sub in (f"assets/{NS}", f"data/{NS}", NS, "data/c/tags", "data/minecraft/tags", "data/neoforge"):
            p = os.path.join(self.out, sub)
            if os.path.isdir(p):
                yield sub, p

    def run(self):
        for old, new in RENAMES.items():
            self.lang(old, new)  # before the text pass, which would rename the keys without their names
            self.move_files(old, new)
            self.rewrite_files(old, new)
            self.content(old, new)
            for extra in ("idtable.json", os.path.join("create", "crossref.json")):
                p = os.path.join(HERE, extra)
                if os.path.exists(p):
                    self.rewrite(p, old, new)
        return self.counts

    def move_files(self, old, new):
        for sub, root in self.roots():
            for dirpath, _, files in os.walk(root, topdown=False):
                for f in files:
                    rel = os.path.relpath(os.path.join(dirpath, f), self.out).replace(os.sep, "/")
                    if sub.startswith("data/minecraft") or sub == "data/neoforge":
                        continue  # vanilla/neoforge tag files are named after vanilla things, never LOTR's
                    target = part(old, new, rel[len(sub):])
                    if target != rel[len(sub):]:
                        dst = os.path.join(self.out, sub + target)
                        os.makedirs(os.path.dirname(dst), exist_ok=True)
                        if os.path.exists(dst):
                            os.remove(dst)
                        os.replace(os.path.join(dirpath, f), dst)
                        self.counts["files_renamed"] += 1
                if os.path.isdir(dirpath) and not os.listdir(dirpath):
                    os.rmdir(dirpath)

    def rewrite_files(self, old, new):
        for sub, root in self.roots():
            for dirpath, _, files in os.walk(root):
                for f in files:
                    p = os.path.join(dirpath, f)
                    if f.endswith((".json", ".mcmeta", ".toml")):
                        self.rewrite(p, old, new)
                    elif f.endswith(".nbt"):
                        self.template(p, old, new)

    def content(self, old, new):
        """The mod's content lists (blocks.json, items.json, tabs.json...) name things by bare id."""
        folder = os.path.join(self.out, NS, "content")
        ids = set()
        for f in ("blocks.json", "items.json"):
            with open(os.path.join(folder, f), encoding="utf-8") as fh:
                ids |= {e["id"] for e in json.load(fh)}
        moved = {i: part(old, new, i) for i in ids if part(old, new, i) != i}
        if not moved:
            return
        pattern = re.compile('"(' + "|".join(map(re.escape, sorted(moved, key=len, reverse=True))) + ')"')
        for f in sorted(os.listdir(folder)):
            path = os.path.join(folder, f)
            with open(path, encoding="utf-8") as fh:
                text = fh.read()
            out = pattern.sub(lambda m: f'"{moved[m.group(1)]}"', text)
            if out != text:
                with open(path, "w", encoding="utf-8") as fh:
                    fh.write(out)
                self.counts["files_rewritten"] += 1

    def rewrite(self, path, old, new):
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        if old not in text:
            return
        out = rename_text(text, old, new)
        if out != text:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(out)
            self.counts["files_rewritten"] += 1

    def template(self, path, old, new):
        with open(path, "rb") as fh:
            raw = fh.read()
        packed = raw[:2] == b"\x1f\x8b"
        data = gzip.decompress(raw) if packed else raw
        if old.encode() not in data:
            return
        data, n = rename_nbt(data, old, new)
        if n:
            with open(path, "wb") as fh:
                fh.write(gzip.compress(data, mtime=0) if packed else data)
            self.counts["templates"] += 1

    def lang(self, old, new):
        """Names: in each renamed key's text, the old word in that language becomes the new one."""
        folder = os.path.join(self.out, "assets", NS, "lang")
        for f in sorted(os.listdir(folder)):
            path = os.path.join(folder, f)
            with open(path, encoding="utf-8") as fh:
                lang = json.load(fh)
            words = LANG.get(old, {}).get(f[:-5], [])
            out = {}
            for key, value in lang.items():
                m = LANG_KEY.fullmatch(key)
                renamed = m and part(old, new, m.group(2))
                if not m or renamed == m.group(2):
                    out[key] = value
                    continue
                for a, b in words:
                    value = value.replace(a, b)
                out[f"{m.group(1)}.{NS}.{renamed}"] = value
                self.counts["lang_entries"] += 1
            if out != lang:
                with open(path, "w", encoding="utf-8") as fh:
                    json.dump(out, fh, indent=2, ensure_ascii=False)
                    fh.write("\n")


def run(out=OUT):
    return Renamer(out).run()


if __name__ == "__main__":
    print(json.dumps(run(sys.argv[1] if len(sys.argv) > 1 else OUT)))
