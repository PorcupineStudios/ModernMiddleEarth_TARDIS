# LOTR 1.7.10 → port: what is missing (task list)

## Still open (checked 2026-11-18)

What LOTR 1.7.10 had that the port still lacks, biggest first. (The original numbered task list of 2026-11-04 is
done apart from these and was removed; what vanilla gained since 1.7.10 is in `modernize.md`.)

- **World:** Utumno (dimension, layers, caves, return portal) and the ring/elven/Morgul portals; fixed builds at map
  coordinates (Minas Tirith, Isengard walls, Rammas Echor, other waypoint settlements); LOTR's small dwarven mines (Moria is a delve now, `docs/delves.md`; the
  mines could be small delves) and Taurëdain pyramids; villages are one fixed layout per seed; sky (sun/moon/stars, northern lights), sandstorms, Mordor ash,
  seasonal grass, holidays.
- **NPCs:** families (hobbit/dwarf/man marriage, rings, children); travelling traders and scrap traders; Sauron;
  skeletal wraith; Gollum's fishing and inventory; troll chieftain boss fight; spider webs
  (mystery web loot); NPCs throwing spears; mounted lances; players riding wargs/spiders; structure NPCs riding.
- **Multiplayer and meta:** fellowships (replaced by colony membership, not built as LOTR's); bounty quests
  (30); custom waypoints; factions screen with faction map; Red Book tabs; shield and cape rendering on players;
  most achievement triggers (kills, crafting, trading, drinking); most LOTR commands; spawn damping.
- **Blocks still without LOTR behaviour** (`docs/behavior-audit.md`): spawner chests and mob spawner, NPC respawner,
  Taurëdain dart traps, treasure piles, troll totems, carved signs (LOTR's chisel; stone
  stonecutting is done), mechanism and mechanised rails, table of command,
  goran, Rhûn fire and fire jar, Mordor moss/shrooms, sand/gravel slabs falling, Gulduril/Edhelvir use-on, dye on mobs, ore glow particles.
- **Items and polish:** glowing elven blades near orcs (Sting, Glamdring, Orcrist); animated armour parts (swan wings,
  robes); dyeable hats/armour and the other special recipes (task 36); carved stone sign text; drunken speech and
  alcohol tolerance; barrels ageing drinks; pipe smoke colour; pouch restock from chests; mud farmland and fruit
  leaves; unsmeltery rocking.

Done since the list was written (don't redo): sounds, music, enchantments, achievements/titles, lore books,
creatures with LOTR models and animations, animals, invasions, 11 village kinds, drinks and kegs, set-down vessels,
machines and their screens, crops, ores, pouches, coins, pipes, thrown weapons, archers, mounts and riders, armour
models and per-item textures, fogs, terrain and mountains, gates, walls, structure signs, people/monster behaviours,
NPC drops, quests incl. pickpocket, calendar, colonies/hired units/wars (colony-first rework), modifiers and
ancient items, poisoned drinks, full wood sets, stonecutter recipes, every tag, warg bombardier bombs.

## 1.7.10 → 1.21.1: where the game itself changed under LOTR

LOTR was built for a game without the 1.9 combat update, data components, the flattening or data-driven
enchantments. Copying its numbers one-to-one gives wrong results in these places. **Done** means handled on this
branch, and **Decision** means it needs Aaron to choose.

### Combat

| 1.7.10 LOTR | 1.21.1 | What we do | Status |
|---|---|---|---|
| No attack cooldown. LOTR timed melee itself (`LOTRWeaponStats`): a player swings every 15 ticks ÷ class speed factor. Dagger 1.5 → 10 t, spear 0.833 → 18 t, polearm and hammer 0.667 → 22 t, battleaxe 0.75 → 20 t, long polearm, pike and lance 0.5 → 30 t | Attack speed attribute (attacks per second), and a hit before the cooldown ends is weaker. Vanilla swords are 1.6 | `attack_speed = 20 / LOTR ticks`: sword 1.33, dagger 2.0, spear 1.11, polearm and hammer 0.91, battleaxe 1.0, pike and lance 0.67. Speeds are read from `LOTRWeaponStats` by `generate.py` and from the export once CI reruns it (`Exporter.weaponStats`) | Done |
| Weapon damage (`generic.attackDamage` modifier) used fractions: daggers 3.5, hammers 8.5 | Same base +1 hand damage. 1.9 lowered vanilla swords by 1 (iron sword 7 → 6) | Keep LOTR's damage as is, because LOTR's NPC health is balanced around it. **Bug fixed:** `SwordItem.createAttributes(tier, (int) damage, …)` dropped every .5, so 3.5 became 3. Floats are kept now | Done |
| Reach = 3 blocks × factor (dagger 0.75, spear and polearm 1.5, pike, long polearm and lance 2.0) | `player.entity_interaction_range` attribute (base 3) | Mainhand modifier `+3 × (factor − 1)`: dagger −0.75, spear +1.5, pike +3 | Done |
| Extra knockback (hammer, lance +1) | `attack_knockback` attribute | +1 modifier | Done |
| No sweep attack | Swords sweep (1.9) | Swords sweep. Battleaxes sweep harder (+0.5 `sweeping_damage_ratio`, Aaron 2026-11-04). Daggers, spears, polearms and hammers hit one target | Done |
| NPCs swing every 20 ticks ÷ speed factor | `MeleeAttackGoal` always waits 20 ticks | `LotrNpc.WeaponMeleeGoal` uses the held weapon's `mob_ticks` | Done |
| NPC reach scaled with the weapon | Mob melee range comes from the hitbox (`isWithinMeleeAttackRange`), not an attribute | `LotrNpc.getAttackBoundingBox` grows by LOTR's NPC reach (sqrt(4 + width²)) × (weapon reach factor − 1): pike NPCs strike from further off, shorter weapons keep vanilla's box (2026-11-10) | Done |
| Crits whenever falling | Crits need a full-charge hit (1.9) | Nothing to port. Fast daggers crit easily, slow pikes rarely | Note |
| No off-hand, no shield | Off-hand and vanilla shields (1.9) block LOTR NPCs' melee and arrows | Kept: vanilla shields block LOTR NPCs (Aaron 2026-11-04). LOTR's "shields" are cosmetic back-banners | Done |
| Armor: flat 4% per point, capped at 80% | Reduction falls as hits get bigger, offset by toughness (1.9) | LOTR armor keeps its defense points with toughness 0, so trolls and 9–11 damage hammers pierce more than in 1.7.10. `armor.toughness` / `knockback_resistance` are data fields. Mithril gets toughness 3 (like netherite), dwarven and blue dwarven 2 (like diamond) (Aaron 2026-11-04, `ARMOR_TOUGHNESS` in `generate.py`) | Done |
| Bows: `LOTRItemBow` draw time (`bowPullTime`) and `arrowDamageFactor` | `BowItem` is fixed at 20 ticks | `LotrBowItem` scales held ticks to the bow's draw time and multiplies arrow damage (`customArrow`). Pull frames use LOTR's large `_pull_0..2` icons | Done (real numbers arrive with the next export; until then 20 t and ×1.0, see `report.json ranged_stats_defaulted`) |
| Crossbow = a bow subclass that fires bolts | Vanilla crossbow (load, then fire, 1.14) | `LotrCrossbowItem` extends `CrossbowItem`, takes only `#lotr_port:crossbow_bolts`, and scales bolt damage. Load time stays 1.25 s because `getChargeDuration` is static in 1.21.1. Bolts fly as vanilla arrows. Multishot and Piercing would now apply | Done (bolt entity look: Todo) |
| Poisoned daggers, bolts and darts: Poison I for n…2n−1 s, n = 1 + 2 × difficulty | Tipped arrows carry a fixed potion | Daggers apply LOTR's formula on hit (`LotrWeaponItem.applyPoison`). Poisoned arrows and bolts carry a fixed 7 s Poison (the normal-difficulty middle) | Done |
| LOTR trident = melee polearm | Vanilla trident is thrown (1.13) | Kept as melee polearm | Note |
| Spears: melee and thrown | No vanilla spear in 1.21.1 (it arrives in 1.21.11) | Melee done. Thrown like LOTR (draw up to 20 ticks, `content/thrown/ThrownWeapon`, 2026-11-10) | Done |
| LOTR enchantments ("modifiers") replace vanilla's | Enchantments are data (1.21) | Modifiers as data (`modifier/`, 2026-11-17: rolls, scrolls, anvil, reforging) | Done |

### Rendering and items

| 1.7.10 LOTR | 1.21.1 | What we do | Status |
|---|---|---|---|
| `LOTRRenderLargeItem`: 179 `large/` icons drawn 2× and 25 `large2/` icons 3× in the hand, scaled about the centre | Item models with display transforms | `separate_transforms` model: small icon in GUI, ground and frames, large icon on `item/large_2x` / `large_3x` (vanilla handheld with x and y scaled). 140 items | Done (in-hand offsets are untested in a client) |
| `LOTRArmorModels`: 34 armor pieces on their own ModelBiped (crested and winged helmets, Morgul spikes, lion head, turbans, robes, swan and Gulf chestplates, hats) | `IClientItemExtensions.getHumanoidArmorModel` | `converter/armor_models.py` replays each model's constructor bytecode into `armor_models.json`. `client/ArmorModels` builds a `HumanoidModel` from it. All 34 convert, and 31 distinct models result | Done |
| Animated armor parts (swan wings, robes following the legs) and items drawn on armor (leather hat feather) | `setupModelAnimations` hook | Resting pose only, so the swan chestplate's wings stay spread | Todo |
| Dyed hats via `getColorFromItemStack` | `dyed_color` component | Hats tinted their undyed LOTR colour. Dyeing: still open | Todo |
| Item damage values (metadata) for variants | Flattening (1.13), data components (1.20.5) | Separate ids. 138 drink metas merged | Partly |
| Horse armor in metadata, many mount types | Body slot / `AnimalArmorItem` | `MountArmorItem` in the body slot (armour attribute = LOTR's chestplate + leggings), one entity type per breed (`mount/`) | Done |
| `IItemRenderer` for bows (`LOTRRenderBow`), crossbows, elven blades glowing near orcs, skull staff, horns | Item model overrides and properties | Bows and crossbows done (pull, charged). Glowing blades: Todo | Partly |

### World and data (already handled by earlier phases, listed so nobody redoes them)

- World height is 0–256 in 1.7.10 and −64–320 in 1.21. Middle-earth heights are tuned in `converter/world.py`.
- LOTR's `WorldProvider` dimension is now a datapack dimension and world preset. Its biome IDs are now one data biome
  per LOTR biome (`MapBiomeSource`).
- TileEntities became BlockEntities with menus (`machine/`).
- Enchantments, recipes, loot and tags are data.
- Sounds are registered sound events (`sound/`).
- 1.7.10 saves are not converted (repo rule).

---
