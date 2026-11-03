"""1.7.10 vanilla item ids and metadata -> 1.21.1 ids or tags, for the ids LOTR recipes use."""

COLORS = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
DYES = ["ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye", "light_gray_dye",
        "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye", "bone_meal"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak"]

BY_META = {
    "minecraft:planks": [f"{w}_planks" for w in WOODS],
    "minecraft:log": [f"{w}_log" for w in WOODS[:4]],
    "minecraft:log2": ["acacia_log", "dark_oak_log"],
    "minecraft:leaves": [f"{w}_leaves" for w in WOODS[:4]],
    "minecraft:leaves2": ["acacia_leaves", "dark_oak_leaves"],
    "minecraft:wool": [f"{c}_wool" for c in COLORS],
    "minecraft:stained_glass": [f"{c}_stained_glass" for c in COLORS],
    "minecraft:stained_hardened_clay": [f"{c}_terracotta" for c in COLORS],
    "minecraft:dye": DYES,
    "minecraft:coal": ["coal", "charcoal"],
    "minecraft:sand": ["sand", "red_sand"],
    "minecraft:dirt": ["dirt", "coarse_dirt", "podzol"],
    "minecraft:stonebrick": ["stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks"],
    "minecraft:sandstone": ["sandstone", "chiseled_sandstone", "cut_sandstone"],
    "minecraft:double_plant": ["sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony"],
    "minecraft:stone_slab": ["smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab", "brick_slab",
                             "stone_brick_slab", "nether_brick_slab", "quartz_slab"],
    "minecraft:skull": ["skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head", "creeper_head"],
    "minecraft:fish": ["cod", "salmon", "tropical_fish", "pufferfish"],
}

# Whole-id renames (any meta).
RENAMED = {
    "minecraft:brick_block": "bricks", "minecraft:hardened_clay": "terracotta", "minecraft:melon": "melon_slice",
    "minecraft:reeds": "sugar_cane", "minecraft:web": "cobweb", "minecraft:fence": "oak_fence",
    "minecraft:wooden_door": "oak_door", "minecraft:trapdoor": "oak_trapdoor", "minecraft:tallgrass": "short_grass",
    "minecraft:potion": "potion", "minecraft:grass": "grass_block",
    "minecraft:cooked_fished": "cooked_cod",
}

# Wildcard (meta 32767) on a multi-variant id -> a tag.
WILDCARD_TAG = {
    "minecraft:planks": "minecraft:planks", "minecraft:log": "minecraft:logs", "minecraft:wool": "minecraft:wool",
    "minecraft:coal": "minecraft:coals", "minecraft:sand": "minecraft:sand", "minecraft:dirt": "minecraft:dirt",
    "minecraft:stonebrick": "minecraft:stone_bricks", "minecraft:stone": "c:stones",
    "minecraft:cobblestone": "c:cobblestones",
}

# Forge ore dictionary names -> common tags. Names not here become lotr_port:<snake_name> tags.
ORE_TAG = {
    "plankWood": "minecraft:planks", "logWood": "minecraft:logs", "stickWood": "c:rods/wooden",
    "cobblestone": "c:cobblestones", "ingotIron": "c:ingots/iron", "ingotGold": "c:ingots/gold",
    "nuggetGold": "c:nuggets/gold", "gemLapis": "c:gems/lapis", "feather": "c:feathers", "bone": "c:bones",
    "ingotCopper": "c:ingots/copper", "ingotBronze": "c:ingots/bronze", "ingotSilver": "c:ingots/silver",
    "ingotTin": "c:ingots/tin", "nuggetSilver": "c:nuggets/silver", "clayBall": "c:clay_balls",
    "apple": "lotr_port:apples", "vine": "lotr_port:vines",
}
for i, c in enumerate(COLORS):
    name = "".join(p.capitalize() for p in c.split("_"))
    ORE_TAG["dye" + name] = f"c:dyes/{c}"

# Vanilla items that belong in the common tags above (the ore dictionary held them in 1.7.10).
VANILLA_IN_TAG = {
    "c:clay_balls": ["minecraft:clay_ball"], "lotr_port:apples": ["minecraft:apple"],
    "lotr_port:vines": ["minecraft:vine"],
}


def vanilla_item(old, meta):
    """Returns ('item', id) or ('tag', id) or None."""
    if meta == 32767:
        if old in WILDCARD_TAG:
            return ("tag", WILDCARD_TAG[old])
        meta = 0
    if old in BY_META:
        names = BY_META[old]
        return ("item", "minecraft:" + names[meta]) if meta < len(names) else None
    if old in RENAMED:
        return ("item", "minecraft:" + RENAMED[old])
    return ("item", old)
