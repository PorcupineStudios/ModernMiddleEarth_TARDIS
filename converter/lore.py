"""
LOTR's lore books (assets/lotr/lore/*.txt, LOTRLore) as data and as written books in structure chests.

A lore file is `#title:`, `#author:`, `#types:` (categories) and an optional `#reward` header, then the text, with
`{name:<bank>}` (a name from LOTR's name banks), `{num:a,b}` and `{choose:A/B/...}`. Each one is written to
`data/lotr_port/lore/<id>.json` as is. Where LOTR's structures put a lore book in a chest (the export shows an empty
written book there), structures.py asks `book()` for one of the structure's categories, filled in with a roll seeded
by the chest's place, so each chest has its own fixed book.
"""
import os
import random
import re
import zlib

from assets import NS, snake, write_json

PAGE = 255  # LOTRLore splits text into pages of at most 256 characters

# structure generator name parts -> lore category (LOTR picked the category in each structure's code)
CATEGORY_WORDS = (
    ("BlackUruk", "mordor"), ("Rotten", "ruins"), ("Burnt", "ruins"), ("Hillman", "angmar"),
    ("Rivendell", "rivendell"), ("Hobbit", "shire"), ("Shire", "shire"), ("Bree", "bree"), ("Dale", "dale"),
    ("Esgaroth", "dale"), ("Gondor", "gondor"), ("DolAmroth", "gondor"), ("Lossarnach", "gondor"),
    ("Lebennin", "gondor"), ("Pelargir", "gondor"), ("Blackroot", "gondor"), ("PinnathGelin", "gondor"),
    ("Lamedon", "gondor"), ("Ithilien", "gondor"), ("Umbar", "umbar"), ("Corsair", "umbar"),
    ("NearHarad", "southron"), ("Southron", "southron"), ("Harnennor", "harnennor"), ("Harnedor", "harnennor"),
    ("Gulf", "gulf"), ("Nomad", "nomad"), ("Moredain", "southron"), ("Tauredain", "southron"),
    ("Easterling", "rhun"), ("Rhun", "rhun"), ("Dorwinion", "dorwinion"), ("Dunland", "dunland"),
    ("Rohan", "rohan"), ("Isengard", "isengard"), ("Uruk", "isengard"), ("Mordor", "mordor"),
    ("MinasMorgul", "mordor"), ("BlackUruk", "mordor"), ("Angmar", "angmar"), ("Rhudaur", "angmar"),
    ("Gundabad", "gundabad"), ("DolGuldur", "dol_guldur"), ("WoodElf", "woodland_realm"),
    ("Galadhrim", "lothlorien"), ("Lothlorien", "lothlorien"), ("HighElf", "lindon"), ("Lindon", "lindon"),
    ("Eregion", "eregion"), ("BlueMountains", "blue_mountains"), ("BlueDwarf", "blue_mountains"),
    ("Dwarf", "durins_folk"), ("Dwarven", "durins_folk"), ("Ranger", "eriador"), ("Arnor", "eriador"),
    ("Dunedain", "eriador"), ("Ruin", "ruins"), ("Ruined", "ruins"), ("Barrow", "ruins"))


class Lore:
    def __init__(self, jar, data_dir, report):
        self.jar = jar
        self.data = data_dir
        self.report = report
        self.books = []
        self.names = {}
        for n in sorted(jar.names):
            if n.startswith("assets/lotr/names/") and n.endswith(".txt"):
                self.names[n.split("/")[-1][:-4]] = [w.strip() for w in jar.read(n).decode("utf-8", "replace")
                                                     .splitlines() if w.strip()]
        for n in sorted(jar.names):
            if n.startswith("assets/lotr/lore/") and n.endswith(".txt"):
                self.books.append(self.parse(n))

    def parse(self, path):
        head, body = {}, []
        for line in self.jar.read(path).decode("utf-8", "replace").lstrip("\ufeff").splitlines():
            if line.startswith("#"):
                m = re.match(r"#(\w+):(.*)", line)
                if m:
                    head[m.group(1)] = m.group(2).strip()
                elif line.strip() == "#reward":
                    head["reward"] = "true"
                continue
            body.append(line)
        types = [t.strip() for t in head.get("types", "").split(",") if t.strip()]
        return {"id": snake(path.split("/")[-1][:-4]), "title": head.get("title", ""),
                "author": head.get("author", ""), "types": types, "reward": head.get("reward") == "true",
                "text": "\n".join(body).strip()}

    def run(self):
        for b in self.books:
            write_json(os.path.join(self.data, NS, "lore", b["id"] + ".json"), b)
        return {"lore_books": len(self.books)}

    @staticmethod
    def category(structure):
        for word, cat in CATEGORY_WORDS:
            if word in structure or snake(word) in structure:
                return cat
        return None

    def fill(self, text, rnd):
        def sub(m):
            kind, arg = m.group(1), m.group(2)
            if kind == "name":
                bank = self.names.get(arg) or ["Nobody"]
                return rnd.choice(bank)
            if kind == "num":
                a, b = (int(x) for x in arg.split(","))
                return str(rnd.randint(min(a, b), max(a, b)))
            if kind == "choose":
                return rnd.choice(arg.split("/"))
            return m.group(0)
        return re.sub(r"\{(\w+):([^}]*)\}", sub, text)

    @staticmethod
    def pages(text):
        out, cur = [], ""
        for word in re.split(r"(\s+)", text):
            if len(cur) + len(word) > PAGE and cur:
                out.append(cur.strip())
                cur = word.lstrip(" ")
            else:
                cur += word
        if cur.strip():
            out.append(cur.strip())
        return out[:100] or [""]

    def book(self, structure, seed):
        """The written_book_content component for a lore book in this structure's chest, or None."""
        cat = self.category(structure)
        pool = [b for b in self.books if cat in b["types"]] if cat else []
        if not pool:
            self.report["lore_chests_without_category"].append(structure)
            return None
        rnd = random.Random(zlib.crc32(seed.encode()))
        b = rnd.choice(pool)
        title = self.fill(b["title"], rnd)[:32]
        return {"title": {"raw": title}, "author": self.fill(b["author"], rnd), "generation": 0, "resolved": True,
                "pages": [{"raw": '"' + p.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n") + '"'}
                          for p in self.pages(self.fill(b["text"], rnd))]}
