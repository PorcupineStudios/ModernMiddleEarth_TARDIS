# Items and combat: modifiers, rings, weapons, thrown weapons, sounds

_Part of the LOTR port's notes, moved here from `lotr/CLAUDE.md` on 2026-11-27 (its index says which file covers what). Add new notes here, not there._

## Modifiers (2026-11-17, `modifier/`)
- LOTR's modifiers are data enchantments (extra.py); `Modifiers` hooks them in: crafted gear rolls random ones
  (`mixin/CraftedModifierMixin` on `Item.onCraftedBy`, so shift-click too), traders' goods roll skilful ones and cost
  more by their `value` (`NpcTrading`), Smith's Scrolls (`SmithScrollItem`, component `lotr_port:modifier`; NPCs drop
  blank ones that get a random modifier on landing) apply on a vanilla anvil, two alike make the `upgrade`
  ("combineSmithScrolls"), undamaged gear + its repair material reforges (keeps `persists_reforge` and vanilla
  enchantments; "reforge"), looting/silk touch count as vanilla's (`acts_as`, `GetEnchantmentLevelEvent`),
  headhunting drops heads, tooltips green/red (`#lotr_port:modifier/negative`). Ancient items (`AncientItem`, data
  `ancient_item/<item>.json`) turn into an old weapon or armour piece with skilful modifiers when used.
- Data: `modifier/<enchantment>.json` (generated, `extra.py write_modifier_data`), `modifier_settings/settings.json`
  (roll chances, anvil XP costs, heads). The roll chances and the ancient lists are stand-ins, not LOTR's numbers.
  GameTests `ModifierVesselTests`.

## Rings of power and Curios (2026-11-25, `ring/`)
- Curios is required (worn-item slots: ring now, cloak and backpack later, all as data). The One Ring
  (`one_ring`, `ring_power/one_ring.json`) worn in a ring slot hides its wearer without the potion's particles, and
  mobs lose them, except the Unseen. GameTest mock players need `gametest/MockPlayerChannels` for Curios' sync.
  **Read `lotr/docs/ring-and-looks.md`.**

## Combat and armor models (2026-11-04)
- 1.7.10 had no attack cooldown. LOTR's melee timing (`LOTRWeaponStats`: 15 ticks for a player's sword ÷ the
  class's speed factor, reach 3 × factor, extra knockback) is generated onto each melee weapon's `weapon` block in
  `content/items.json`. `content/LotrWeaponItem` turns that into attack speed (20 / ticks), entity interaction
  range, attack knockback, sweep only for plain swords, and LOTR's poison for poisoned daggers. NPCs swing at the
  weapon's `mob_ticks` (`LotrNpc.WeaponMeleeGoal`). Damage is LOTR's own, with fractions kept.
- Bows (`LotrBowItem`: draw time, arrow damage factor), crossbows (`LotrCrossbowItem`: vanilla crossbow, bolts only,
  `#lotr_port:crossbow_bolts`), and bolts and poisoned arrows (`ArrowItem` + a fixed Poison effect). Pull and charged
  predicates are in `LotrClient.rangedProperties`.
- LOTR's large in-hand icons: `separate_transforms` item models on `item/large_2x` / `large_3x`, with
  `"gui_light": "front"` (a model without a parent defaults to block lighting: spears and pikes drew dark).
- Modifiers don't glint (`mixin/ModifierGlintMixin` on `Item.isFoil`): only lotr_port enchantments = no glint.
- Special armor models: `converter/armor_models.py` → `lotr_port/content/armor_models.json` → `client/ArmorModels`
  (`RegisterClientExtensionsEvent`). Animated parts keep their resting pose.
- Per-item armor textures (2026-11-16): LOTR's `new LOTRItemArmor(mat, slot, "extra")` pieces (special helmets,
  winged/Harnedor/Gulf bodies, trimmed dwarven sets) draw `armor/<mat>_<extra>.png`; `generate.py armor_extras` reads
  them from LOTRMod's bytecode. With the material texture their models showed broken. Check: `lotr/run/armor_script.txt`.
- Every 1.7.10 → 1.21.1 mismatch and what was decided: `lotr/docs/missing-features.md`.

## Thrown weapons and NPC archers (2026-11-10, `content/thrown/`)
- `converter/thrown.py` marks items by LOTR class (spear `throw`, `throwing_axe`, `blowgun`, `dart`, `sling`,
  `throwable` pebble/conker/fire_pot/mystery_web/plate) and orc bomb blocks (`bomb` strength, fire); LOTR's numbers
  go to `thrown/settings.json` (`Thrown.num`). Spears, axes and darts fly as `ThrownWeapon` (an arrow carrying its
  item, damage = speed x multiplier, comes back one point worn); the rest as `ThrownMissile`; `OrcBomb` is a TNT-like
  entity (fuse 40 + 20 x strength). Dispensers for all of them (`Thrown.dispensers`). Renderers in
  `client/ThrownRenderers`.
- NPC archers: `npcs.py` gives types with a ranged weapon behaviour `archer` and LOTR's AI numbers (`behaviours.py
  archer`, read from the constructor's LOTREntityAIRangedAttack). Pure archers hold the bow; swap types (elves...)
  keep it as `ranged` gear and draw it outside melee range. Pike reach: `LotrNpc.getAttackBoundingBox`.
- GameTests `ThrownTests` force-load the chunks they shoot across (the 1-block template keeps only its own loaded).

## Sounds (2026-11-06)
- `converter/sounds.py` copies LOTR's 190 sound files and 101 events. Ids are LOTR's, snake_cased per part:
  `half_troll.say`. `sound/LotrSounds` registers them from `content/sounds.json`.
- NPC types carry `sounds` (ambient/hurt/death/attack/kill), set from the LOTR class (`sounds.NPC_SOUNDS`) and
  played by `LotrNpc`. Blocks: `LotrBlocks.sound` (treasure, plate) and `GateBlock`. Items: `content/LotrUseItem`
  (horn, pipe, vessel). Crossbows: `sound/SoundHooks` swaps vanilla's crossbow sounds. Events: FastTravel, Pledges,
  `LotrNpc.notifyTrade`.
- `client/LotrAmbience` plays LOTR's ambience from `assets/lotr_port/ambience.json` (wight moans, wind, sea,
  rain and thunder). "In Middle-earth" = standing in a lotr_port biome.
