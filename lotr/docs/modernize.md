# Modernising Middle-earth: what 1.8 – 1.21.1 added that the port doesn't use yet

`missing-features.md` lists what LOTR 1.7.10 had that the port lacks. This is the other direction: what vanilla gained
since 1.7.10 that Middle-earth should have. Checked against the generated data on 2026-11-17 (169 biomes in
`data/lotr_port/worldgen/biome`, noise settings, `content/blocks.json`, recipes). "Checked: none" means the data was
searched; the rest are ideas to decide on. In sections marked done, "Checked" describes the state before the work.

**Done 2026-11-17** (lotr/CLAUDE.md "Modern Middle-earth"): sections 1 (seas by latitude, sea and river life, sea
floor; no coast structures or fishing loot yet), 2 (powder snow, ice, goats, foxes, rabbits, polar bears, ice spikes,
meadows, geodes), 3 (lush and dripstone cave pockets, glow lichen; noise caves and aquifers since 2026-11-18; per-biome cave config `lotr_port-biomes.toml` with fewer entrances and ravines), 4 (bees, frogs,
foxes, rabbits, parrots, ocelots, armadillos, jungle and forest plants), 5 (full wood sets, every tag, stonecutter,
waterlogging (2026-11-18); bark blocks and stripped bark for every wood (2026-11-18); lanterns and archaeology not
done), 8 in part (LOTR's animals spawn with the land, don't despawn, have their own cap: 2026-11-18), and no vanilla
monsters in Middle-earth (Aaron). Still open: 6, 7,
the DESIGN items, ore veins, carved signs (LOTR's chisel).

Most of this is worldgen data: biome `features` and `spawners` come from `converter/world.py`, so the change goes
there (a table per region), not into the generated JSON.

## 1. Oceans, rivers, lakes (done; open: coast structures, fishing loot)
Checked: no water mobs in any biome (the only spawners are land animals), no underwater plants, no coral, one `ocean`
biome for every sea. The ocean even spawns sheep, chickens and boars and grows grass and "ocean trees" (LOTR's
filler for islands).
- **Fish and sea life**: cod and salmon in cold and temperate seas; salmon in northern rivers (Anduin above the
  Gladden, Forochel); tropical fish and pufferfish on the Harad and Umbar coasts and the Bay of Belfalas; squid in open
  sea, glow squid deep; dolphins in warm and temperate open sea; turtles and turtle eggs on Harad beaches. Spawn
  categories `water_creature` / `water_ambient`, per region.
- **Sea floor**: seagrass and tall seagrass everywhere underwater; kelp in cold and temperate seas (Lindon, Forochel,
  the coasts of Eriador); warm reefs (coral blocks, fans, sea pickles) in the south only; sand, gravel and clay disks
  on sea and river beds.
- **Split the sea by temperature**: frozen (Ice Bay of Forochel: icebergs, blue ice), cold, temperate, lukewarm, warm
  (Harad, Umbar), each with its own water colour. Real depth: deep sea away from the coasts.
- **Rivers and lakes**: seagrass, lily pads in slow water and marshes (Nindalf, Long Marshes, Shire marshes,
  Nurn), salmon and squid in big rivers.
- **Coast structures** (DESIGN): shipwrecks as Corsair or Númenórean wrecks, ocean ruins as drowned Númenórean
  ruins, buried treasure from a map.
- **Fishing**: regional loot (tropical fish in the south, salmon in the north).

## 2. Mountains and the cold north (done; open: strays, ore veins)
- **Powdered snow** pockets and snow slopes on high peaks (Misty, Grey, Ered Mithrin, Forodwaith); deeper snow
  layers by height; ice and packed ice faces; ice spikes and frozen lakes in Forodwaith. Checked: today only
  `freeze_top_layer` and Forodwaith's packed-ice "ore".
- **Goats** on the high slopes (their horns could tie in with LOTR's horns), foxes (snow foxes in the north),
  rabbits (white in snow), polar bears in Forochel, strays in Forodwaith or Angmar (DESIGN).
- **Amethyst geodes** (LOTR's amethyst is renamed tanzanite, so this is vanilla's), calcite bands, bigger ore veins.
- **Alpine meadows** on the foothills: flowers, bees.

## 3. Caves (done; open: copper and iron ore veins, deep dark)
Checked: aquifers off, ore veins off, no noise caves (cheese, spaghetti, noodle); only the vanilla carvers plus the
port's mountain caves. Deepslate and dripstone are in.
- Modern noise caves and aquifers (watch the noise band cuts in `NoiseTop` and chunk speed).
- **Lush caves** under green forests (Lothlórien, Fangorn, Ithilien): azalea, moss, glow berries, axolotls,
  spore blossoms.
- **Glow lichen** and hanging roots everywhere underground.
- **Deep dark** (DESIGN): skip, or keep it for Moria or Utumno.
- **Copper and iron ore veins** (the big 1.18 veins).

## 4. Forests, fields, marshes, deserts (done; open: archaeology)
Checked: vanilla plants used are grass, tall grass, ferns, flowers, sugar cane, cactus and dead bushes; the vanilla
animals are sheep, chickens, pigs, cows and wolves (18 biomes).
- **Bees and bee nests** in the Shire, its orchards and meadows (honey for mead and food); `#flowers` tags on
  LOTR's flowers so bees use them.
- **Frogs and tadpoles** in marshes (variants by temperature), fireflies if a later version adds them.
- **Foxes** (Old Forest, Mirkwood, Fangorn edges), rabbits, wolf variants by region, parrots and ocelots in the
  Far Harad jungle, armadillos in Harad savanna.
- **Jungle**: bamboo, cocoa, vines, melons (Far Harad).
- **Forest floor**: moss carpets, pumpkins, mushrooms, sweet berries, pink petals or wildflowers in meadows.
- **Marshes**: lily pads, mangrove roots or mud next to LOTR's own mangroves and mud.
- **Desert**: suspicious sand and pottery sherds at Harad ruins (archaeology: see 5).

## 5. Blocks and building (done; open: lanterns/chains, archaeology, smoker and blast furnace recipes)
Checked on the 39 LOTR woods: logs (LOTR's `*_wood`), planks, stairs, slabs, fences, gates, doors and trapdoors
exist; there are no stripped logs, no signs or hanging signs, no boats, and buttons and pressure plates for only 6.
No stonecutter recipes at all.
- **Full wood sets**: stripped logs (axe use), wood blocks, signs, hanging signs, buttons, pressure plates, boats and
  chest boats for every LOTR wood.
- **Stonecutter recipes** for every LOTR stone and brick family (slabs, stairs, walls, carved and pillar forms).
- **Tags and data maps**: `#logs`, `#planks`, `#leaves`, `#saplings`, `#flowers`, `#small_flowers`, `#dirt`, `#sand`,
  `#climbable` on LOTR blocks; composter values for LOTR crops; furnace fuel for LOTR woods. These make vanilla and
  other mods treat LOTR blocks right.
- **Waterlogging** for LOTR's thin blocks (fences, panes, gates, plants by water).
- **Lanterns, candles, campfires and chains** in LOTR materials (or use LOTR's chandeliers and Rhûn fire where those
  fit).
- **Archaeology** (DESIGN): suspicious sand and gravel in barrows, Harad ruins and Númenórean ruins; LOTR-themed
  pottery sherds and decorated pots.
- **Smoker and blast furnace** recipes for LOTR foods and ores; flower pots for LOTR flowers.

## 6. Items and combat
Done: attack cooldown, weapon reach and knockback, modifiers as enchantments, crossbows.
- **Shields** (1.9): LOTR's shields were cosmetic. Real off-hand shields per faction (Gondor, Rohan, Mordor...),
  maybe with LOTR's banner patterns (DESIGN).
- **Off hand**: torches, maps, horns.
- **Armour trims** on LOTR armour; smithing-table upgrades (LOTR's gilded, mithril) instead of netherite.
- **Food**: honey, suspicious stew; LOTR foods in the modern food component (eat time, effects).
- **Enchanting table** (DESIGN): whether it can roll LOTR modifiers (today they come only from crafting, traders,
  scrolls and reforging).

## 7. Atmosphere, weather, sound
Done: fog, grading, sky, music, ambience.
- **Biome particles**: Mordor ash (like basalt deltas), spores in Mirkwood, petals in Lothlórien.
- **Biome mood sounds and cave additions** per region.
- **Water colour and water fog** per region; most biomes share the default today.
- **Snow vs rain** by height on mountains (1.18 temperature falloff), snow piling up in the north.

## 8. Mobs and spawning
- Spawn categories beyond `creature`: `water_creature`, `water_ambient`, `underground_water_creature`, `axolotls`,
  `ambient` (bats).
- Hostile vanilla mobs (zombies, skeletons, creepers, phantoms) in Middle-earth at night (DESIGN): LOTR replaced them
  with orcs and wights.
- Breeding and tempting by item tags, leads on LOTR mounts, wolf variants.

## Order to do it in (suggested)
1. Water life and the sea floor (section 1): data only, the biggest visible gain.
2. Mountain snow, goats and foxes (2).
3. Wood sets, stonecutter recipes and tags (5): converter generators.
4. Noise caves, aquifers and lush caves (3): needs chunk-speed checks against the noise band.
5. The rest, as they come up.
