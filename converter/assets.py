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
        for piece in self.PIECES:
            src = f"assets/lotr/textures/blocks/{stem}_{piece}.png"
            if self.jar.has(src):
                layer = Image.open(io.BytesIO(self.jar.read(src))).convert("RGBA")
                if layer.size == img.size:
                    img.alpha_composite(layer)
        dst = os.path.join(self.out, "textures", dst_rel + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        img.save(dst)
        self.copied[dst_rel] = base
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
