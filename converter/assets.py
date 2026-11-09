"""Shared helpers for generate.py: names, the LOTR jar, textures and lang files."""
import json
import os
import re
import zipfile

NS = "lotr_port"
JAR = os.path.join(os.path.dirname(__file__), "libs", "LOTRMod v36.15.jar")


def snake(name):
    """camelCase / odd LOTR names -> a valid 1.21.1 resource path segment."""
    name = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", name)
    name = re.sub(r"([A-Z]+)([A-Z][a-z])", r"\1_\2", name)
    name = name.lower()
    name = re.sub(r"[^a-z0-9_./-]+", "_", name)
    name = re.sub(r"_+", "_", name).strip("_")
    return name


def id_from_name(display):
    """'Mordor Rock' -> 'mordor_rock'."""
    s = display.lower().replace("'", "")
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s


class Jar:
    def __init__(self, path=JAR):
        self.zip = zipfile.ZipFile(path)
        self.names = set(self.zip.namelist())

    def read(self, name):
        return self.zip.read(name)

    def has(self, name):
        return name in self.names

    def lang(self, code):
        """Parses assets/lotr/lang/<code>.lang into a dict."""
        out = {}
        path = f"assets/lotr/lang/{code}.lang"
        if not self.has(path):
            return out
        for line in self.read(path).decode("utf-8", "replace").splitlines():
            line = line.strip().lstrip("﻿")
            if not line or line.startswith("#") or "=" not in line:
                continue
            k, v = line.split("=", 1)
            out[k] = v
        return out

    def langs(self):
        return sorted(n.split("/")[-1][:-5] for n in self.names
                      if n.startswith("assets/lotr/lang/") and n.endswith(".lang"))


class Textures:
    """Copies textures on demand and turns old icon names into new texture ids."""

    def __init__(self, jar, out_assets, report):
        self.jar = jar
        self.out = out_assets
        self.report = report
        self.copied = {}
        self.connected = {}  # texture id -> its connected sheet (see sheet())

    def _copy(self, src, dst_rel):
        dst = os.path.join(self.out, "textures", dst_rel + ".png")
        if dst_rel not in self.copied:
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            with open(dst, "wb") as f:
                f.write(self.jar.read(src))
            meta = src + ".mcmeta"
            if self.jar.has(meta):
                with open(dst + ".mcmeta", "wb") as f:
                    f.write(self.jar.read(meta))
            self.copied[dst_rel] = src
        return f"{NS}:{dst_rel}"

    def block(self, icon):
        return self._resolve(icon, "blocks", "block")

    def item(self, icon):
        return self._resolve(icon, "items", "item")

    PIECES = ("top", "bottom", "left", "right", "topLeft", "topRight", "bottomLeft", "bottomRight")
    SIDES = ("left", "right", "top", "bottom")
    CORNERS = ("topLeft", "topRight", "bottomLeft", "bottomRight")
    # The five looks a quarter of a face can have, by which of its two edges touch a connected block: tile 0 none
    # (both edges and the corner), 1 only the side one (top and bottom edges drawn), 2 only the top or bottom one
    # (left and right edges), 3 both but not the diagonal (inner corners), 4 all three (the base alone). LOTR's
    # getConnectedIcon draws a side where that neighbour isn't connected, a corner where both sides aren't, and an
    # inner corner where both are but the diagonal isn't, in that order over the base; every piece lies in its own
    # half or quarter, so a quarter's look needs only its own two neighbours and diagonal.
    SHEET_TILES = (SIDES + CORNERS, ("top", "bottom"), ("left", "right"), tuple(c + "Inv" for c in CORNERS), ())

    def sheet(self, dst_rel, base, pieces, open_pieces=None):
        """
        Writes block/connected/<name>.png for a connected texture: the five quarter looks (SHEET_TILES) side by side,
        the pieces on the base in row 0 (a shut gate, a brick) and open_pieces (default: the same) on nothing in row 1
        (an open gate: LOTR draws it without its base, so only the frame shows). Pieces map a name to an image of the
        base's size. Returns the sheet's id.
        """
        from PIL import Image
        w, h = base.size
        img = Image.new("RGBA", (w * 5, h * 2))
        for row, (under, layers) in enumerate(((base, pieces), (None, pieces if open_pieces is None else open_pieces))):
            for t, names in enumerate(self.SHEET_TILES):
                tile = under.copy() if under is not None else Image.new("RGBA", (w, h))
                for n in names:
                    if n in layers:
                        tile.alpha_composite(layers[n])
                img.paste(tile, (t * w, row * h))
        rel = "block/connected/" + dst_rel.split("/", 1)[1]
        dst = os.path.join(self.out, "textures", rel + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        img.save(dst)
        self.copied[rel] = self.copied.get(dst_rel, dst_rel)
        self.connected[f"{NS}:{dst_rel}"] = f"{NS}:{rel}"
        return f"{NS}:{rel}"

    def gate_sheet(self, tex):
        """
        The connected sheet for a gate's texture. LOTR's own connected gates have one (from their pieces); for the
        rest (portcullises, the cross-braced wooden gate) LOTR cut the frame from the texture's outer 3/16
        (registerNonConnectedGateIcons), which for bars is a row of bar tips (dots). Here the frame is the texture's
        own solid bars instead: its thickest full-width line (a crossbar, a plank) along the top and bottom, its
        thickest full-height one (a bar, a post) down the sides; the outer 3/16 only if it has none. Shut, the
        texture as it is; open, the frame alone, joined like a connected texture's sides and corners.
        """
        if tex in self.connected:
            return self.connected[tex]
        if not tex.startswith(NS + ":") or tex.split(":", 1)[1] not in self.copied:
            return None
        import io
        from PIL import Image
        dst_rel = tex.split(":", 1)[1]
        img = Image.open(io.BytesIO(self.jar.read(self.copied[dst_rel]))).convert("RGBA")
        w, h = img.size
        px = img.load()
        rows = self._thickest_run([sum(px[x, y][3] > 0 for x in range(w)) >= 0.9 * w for y in range(h)], h)
        cols = self._thickest_run([sum(px[x, y][3] > 0 for y in range(h)) >= 0.9 * h for x in range(w)], w)
        if rows and cols:
            eh, ev = rows[1] - rows[0], cols[1] - cols[0]
            horiz, vert = Image.new("RGBA", (w, h)), Image.new("RGBA", (w, h))
            bar = img.crop((0, rows[0], w, rows[1]))
            horiz.alpha_composite(bar, (0, 0))
            horiz.alpha_composite(bar, (0, h - eh))
            bar = img.crop((cols[0], 0, cols[1], h))
            vert.alpha_composite(bar, (0, 0))
            vert.alpha_composite(bar, (w - ev, 0))
            frame = horiz.copy()
            frame.alpha_composite(vert)
            # sides from their own bar alone, so a side running on past a joint has no cross piece on it
            src = {"top": horiz, "bottom": horiz, "left": vert, "right": vert}
        else:
            eh = ev = max(1, round(w / 16 * 3))
            frame, src = img, {}
        boxes = {"left": (0, 0, ev, h), "right": (w - ev, 0, w, h), "top": (0, 0, w, eh), "bottom": (0, h - eh, w, h),
                 "topLeft": (0, 0, ev, eh), "topRight": (w - ev, 0, w, eh), "bottomLeft": (0, h - eh, ev, h),
                 "bottomRight": (w - ev, h - eh, w, h)}
        pieces = {}
        for n, b in boxes.items():
            layer = Image.new("RGBA", (w, h))
            layer.paste(src.get(n, frame).crop(b), b[:2])
            pieces[n] = layer
            if n in self.CORNERS:
                pieces[n + "Inv"] = layer
        return self.sheet(dst_rel, img, {}, pieces)  # shut: the plain texture whatever its neighbours

    @staticmethod
    def _thickest_run(solid, size):
        """(start, end) of the longest run of True in solid, or None if there is none or it's over 3/8 of size (a
        solid texture, not a bar: its frame would cover a quarter's other half)."""
        best, i = None, 0
        while i < len(solid):
            j = i
            while j < len(solid) and solid[j]:
                j += 1
            if j > i and (best is None or j - i > best[1] - best[0]):
                best = (i, j)
            i = max(j, i + 1)
        return best if best and best[1] - best[0] <= size * 3 // 8 else None

    def _connected(self, path):
        """LOTR connected textures ('gateDwarven_511') are built at runtime from a base and edge pieces. Bake the
        look of a single block on its own: the base with every edge and corner on top."""
        import re as _re
        m = _re.match(r"^(.*)_(\d+)$", path)
        if not m:
            return None
        stem = m.group(1)
        base = f"assets/lotr/textures/blocks/{stem}_base.png"
        if not self.jar.has(base):
            plain = f"assets/lotr/textures/blocks/{stem}.png"
            return self._copy(plain, "block/" + snake(stem)) if self.jar.has(plain) else None
        dst_rel = "block/" + snake(stem)
        if dst_rel in self.copied:
            return f"{NS}:{dst_rel}"
        import io
        from PIL import Image
        img = Image.open(io.BytesIO(self.jar.read(base))).convert("RGBA")
        base_img = img.copy()
        pieces = {}
        for piece in self.PIECES + tuple(c + "Inv" for c in self.CORNERS):
            src = f"assets/lotr/textures/blocks/{stem}_{piece}.png"
            if self.jar.has(src):
                layer = Image.open(io.BytesIO(self.jar.read(src))).convert("RGBA")
                if layer.size == img.size:
                    pieces[piece] = layer
                    if piece in self.PIECES:
                        img.alpha_composite(layer)
        dst = os.path.join(self.out, "textures", dst_rel + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        img.save(dst)
        self.copied[dst_rel] = base
        self.sheet(dst_rel, base_img, pieces, None)
        return f"{NS}:{dst_rel}"

    def _resolve(self, icon, old_dir, new_dir):
        if not icon or icon.startswith("!"):
            return None
        if ":" in icon:
            ns, path = icon.split(":", 1)
        else:
            ns, path = "minecraft", icon
        if ns == "lotr":
            for prefix in ("textures/blocks/", "textures/items/"):
                if path.startswith(prefix):
                    path = path[len(prefix):]
            conn = self._connected(path)
            if conn:
                return conn
            for d in (old_dir, "blocks" if old_dir == "items" else "items"):
                src = f"assets/lotr/textures/{d}/{path}.png"
                if self.jar.has(src):
                    sub = "block" if d == "blocks" else "item"
                    return self._copy(src, sub + "/" + "/".join(snake(p) for p in path.split("/")))
            self.report.setdefault("missing_textures", []).append(icon)
            return None
        return vanilla_texture(path, new_dir, self.report)


# 1.7.10 vanilla texture names that LOTR blocks reuse, mapped to 1.21.1 names. Unknown ones fall back to the same
# name, which is right for most simple ones (stone, dirt, sand...).
VANILLA = {
    "planks_oak": "oak_planks", "planks_spruce": "spruce_planks", "planks_birch": "birch_planks",
    "planks_jungle": "jungle_planks", "planks_acacia": "acacia_planks", "planks_big_oak": "dark_oak_planks",
    "log_oak": "oak_log", "log_oak_top": "oak_log_top", "log_spruce": "spruce_log", "log_birch": "birch_log",
    "stonebrick": "stone_bricks", "stonebrick_mossy": "mossy_stone_bricks", "stonebrick_cracked": "cracked_stone_bricks",
    "stonebrick_carved": "chiseled_stone_bricks", "cobblestone_mossy": "mossy_cobblestone", "brick": "bricks",
    "stone_slab_top": "smooth_stone", "stone_slab_side": "smooth_stone_slab_side", "sandstone_normal": "sandstone",
    "sandstone_top": "sandstone_top", "sandstone_bottom": "sandstone_bottom", "sandstone_smooth": "cut_sandstone",
    "sandstone_carved": "chiseled_sandstone", "quartz_block_side": "quartz_block_side", "nether_brick": "nether_bricks",
    "grass_top": "grass_block_top", "grass_side": "grass_block_side", "wool_colored_white": "white_wool",
    "hardened_clay": "terracotta", "red_sand": "red_sand", "gravel": "gravel", "glass": "glass", "ice": "ice",
    "snow": "snow", "obsidian": "obsidian", "iron_block": "iron_block", "gold_block": "gold_block",
    "clay": "clay", "dirt": "dirt", "sand": "sand", "stone": "stone", "cobblestone": "cobblestone",
    "leaves_oak": "oak_leaves", "torch_on": "torch", "door_wood_lower": "oak_door_bottom",
    "door_wood_upper": "oak_door_top", "trapdoor": "oak_trapdoor", "ladder": "ladder", "crafting_table_top":
    "crafting_table_top", "furnace_front_off": "furnace_front", "furnace_side": "furnace_side", "furnace_top":
    "furnace_top", "farmland_dry": "farmland", "farmland_wet": "farmland_moist", "water_still": "water_still",
    "lava_still": "lava_still", "iron_bars": "iron_bars", "bookshelf": "bookshelf", "hay_block_side": "hay_block_side",
}


def vanilla_texture(path, kind, report):
    new = VANILLA.get(path)
    if new is None:
        new = path
        report.setdefault("guessed_vanilla_textures", [])
        if path not in report["guessed_vanilla_textures"]:
            report["guessed_vanilla_textures"].append(path)
    return f"minecraft:{kind}/{new}"


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
