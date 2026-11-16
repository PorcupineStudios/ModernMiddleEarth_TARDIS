# LOTR port: blocks and items missing their original behavior

How this was made: every port block/item registered as a plain kind (cube, shaped, plant, pillar, leaves, slab,
stairs, wall, torch, or plain item without food/placing) was traced back to its LOTR v36.15 class through
`converter/idtable.json`, and that class (and its LOTR parents) was checked with `javap` for overridden behavior
methods. Tags: rclick = right-click, touch = entity inside/touching, tick = scheduled/random tick, particles = display
particles, canstay/placement/neighbour = placement rules and popping off, collision = custom collision box,
harvest = custom drops/xp, useon = use on a block, useentity = use on a mob, eaten = drink/eat effect.
Caveat: some of these may already be handled by an event elsewhere in the port; I check each when implementing.

Format: count, LOTR class [missing hooks]: port ids.

== blocks.json
  2 LOTRBlockAleHorn [canstay,neighbour,placement,rclick]: ale_horn_block, ale_horn_gold
  1 LOTRBlockBanana [canstay,collision,neighbour,tick]: banana_block
  1 LOTRBlockBeacon [canstay,neighbour,particles,placement,rclick,touch]: beacon_of_gondor
  4 LOTRBlockBirdCage [canstay,neighbour,placement]: bronze_bird_cage, iron_bird_cage, silver_bird_cage, golden_bird_cage
  1 LOTRBlockBirdCageWood [canstay,neighbour,placement]: wooden_bird_cage
  1 LOTRBlockBookshelfStorage [rclick]: bookshelf_storage
  1 LOTRBlockButterflyJar [canstay,neighbour,placement]: butterfly_jar
 16 LOTRBlockChandelier [canstay,collision,neighbour,particles,placement]: bronze_chandelier, iron_chandelier, silver_chandelier, gold_chandelier, mithril_chandelier
  1 LOTRBlockClayMug [canstay,neighbour,placement,rclick]: ceramic_mug_block
  1 LOTRBlockCommandTable [rclick]: table_of_command
  1 LOTRBlockCoralReef [walkon]: coral_reef
  1 LOTRBlockCorruptMallorn [particles,tick]: corrupt_mallorn_sapling
  3 LOTRBlockDartTrap [onadd,placedby,rclick]: taurethrim_dart_trap, golden_taurethrim_dart_trap, obsidian_taurethrim_dart_trap
  1 LOTRBlockDate [canstay,collision,neighbour,tick]: date_block
  4 LOTRBlockDoubleFlower [canstay,placedby,placement]: black_iris, yellow_iris, hibiscus, flame_of_harad
  2 LOTRBlockDoubleTorch [canstay,collision,neighbour,particles]: orc_torch_block, tauredain_double_torch
  1 LOTRBlockElvenPortal [onadd,particles,touch]: elven_portal
  1 LOTRBlockElvenTorch [particles]: high_elven_torch
 44 LOTRBlockFallenLeaves [canstay,neighbour,placement]: fallen_oak_leaves, fallen_spruce_leaves, fallen_birch_leaves, fallen_jungle_leaves, fallen_acacia_leaves
  1 LOTRBlockFlowerPot [particles,rclick]: flower_pot
  1 LOTRBlockGlassBottle [canstay,neighbour,placement,rclick]: glass_bottle
  1 LOTRBlockGoblet$Copper [canstay,neighbour,placement,rclick]: goblet_copper
  1 LOTRBlockGoblet$Gold [canstay,neighbour,placement,rclick]: goblet_gold
  1 LOTRBlockGoblet$Silver [canstay,neighbour,placement,rclick]: goblet_silver
  1 LOTRBlockGoblet$Wood [canstay,neighbour,placement,rclick]: goblet_wood
  2 LOTRBlockGoran [rclick]: goran, cargoran
  1 LOTRBlockGrapevine [canstay,collision,neighbour,placement,rclick,tick]: grapevine_post
  1 LOTRBlockGrass [canstay]: arid_grass
  1 LOTRBlockHearth [particles]: hearth_block
  4 LOTRBlockLeaves [particles]: shire_pine_leaves, mallorn_leaves, mirk_oak_leaves, red_oak_leaves
  4 LOTRBlockLeaves4 [tick]: chestnut_leaves, baobab_leaves, cedar_leaves, fir_leaves
  4 LOTRBlockLeaves7 [particles]: aspen_leaves, green_oak_leaves, laireloss_leaves, almond_leaves
  4 LOTRBlockMallornTorch [particles]: silver_mallorn_torch, blue_mallorn_torch, golden_mallorn_torch, green_mallorn_torch
  1 LOTRBlockMarshLights [canstay,collision,neighbour,particles,placement]: marsh_lights
  2 LOTRBlockMechanisedRail [particles,rclick]: mechanised_rail_off, mechanised_rail_on
  1 LOTRBlockMobSpawner [rclick]: mob_spawner
  1 LOTRBlockMordorGrass [canstay]: mordor_scrub
  1 LOTRBlockMordorMoss [canstay,neighbour,placement,tick]: withered_moss
  1 LOTRBlockMordorThorn [canstay,touch]: stabbing_thorn
  1 LOTRBlockMorgulFlower [canstay,particles,tick,touch]: morgul_flowers
  1 LOTRBlockMorgulPortal [onadd,particles,touch]: morgul_portal
  1 LOTRBlockMorgulShroom [canstay,tick]: morgul_shroom
  1 LOTRBlockMorgulTorch [particles]: morgul_torch
  1 LOTRBlockMudFarmland [fallon,neighbour,tick]: jungle_mud_farmland
  1 LOTRBlockMudGrass [tick]: jungle_grass
  1 LOTRBlockMug [canstay,neighbour,placement,rclick]: mug_block
  1 LOTRBlockOrcChain [canstay,collision,neighbour,placement,rclick]: orc_chain
  5 LOTRBlockOre [harvest]: copper_ore, mithril_ore, rock_salt, silver_ore, tin_ore
  2 LOTRBlockOreMordorVariant [harvest]: morgul_iron_ore, morgul_iron_ore_block
  1 LOTRBlockPipeweedPlant [particles]: pipe_weed_plant
  7 LOTRBlockPlaceableFood [canstay,collision,neighbour,placement,rclick]: apple_crumble_block, banana_cake_block, berry_pie_block, cherry_pie_block, dalish_pastry_block
  1 LOTRBlockQuagmire [collision,touch]: quagmire
  1 LOTRBlockQuenditeGrass [particles]: elvengrass
  1 LOTRBlockRemains [harvest]: remains
  1 LOTRBlockRhunFire [tick]: rhun_fire
  1 LOTRBlockRhunFireJar [neighbour,onadd,rclick,tick,touch]: kham_ls_fire
  6 LOTRBlockRock [particles]: mordor_rock, gondor_rock, rohan_rock, luigon, cargon
  1 LOTRBlockSkullCup [canstay,neighbour,placement,rclick]: skull_cup_block
  3 LOTRBlockSlabGravel [neighbour,onadd,tick]: gravel_slab, mordor_gravel_slab, obsidian_gravel_slab
  3 LOTRBlockSlabSand [neighbour,onadd,tick]: sand_slab, red_sand_slab, white_sand_slab
  3 LOTRBlockSpawnerChest [rclick]: spawner_chest, spawner_chest_ancient_harad, spawner_chest_stone
  6 LOTRBlockStalactite [canstay,fallon,neighbour,particles]: stalactite, stalagmite, ice_stalactite, ice_stalagmite, obsidian_stalactite
  6 LOTRBlockTallGrass [canstay,touch]: short_grass, flowery_grass, wheatgrass, thistle, nettles
  2 LOTRBlockTermite [destroyed]: infested_termite_mound, termite_mound
  1 LOTRBlockThatchFloor [canstay,collision,neighbour,placement]: thatch_flooring
  6 LOTRBlockTreasurePile [canstay,collision,fallon,neighbour,onadd,placement,rclick,tick,walkon]: copper_treasure_pile, copper_treasure_pile_block, gold_treasure_pile, gold_treasure_pile_block, silver_treasure_pile
  3 LOTRBlockTrollTotem [placedby,rclick]: troll_totem_head, troll_totem_body, troll_totem_base
  1 LOTRBlockUtumnoPortal [onadd,touch]: utumno_portal
  1 LOTRBlockUtumnoReturnLight [collision]: utumno_return_light
  1 LOTRBlockUtumnoReturnPortal [onadd]: utumno_return_portal
  1 LOTRBlockUtumnoReturnPortalBase [collision]: utumno_return_portal_base
  1 LOTRBlockWaste [collision,touch]: waste_block
  1 LOTRBlockWineGlass [canstay,neighbour,placement,rclick]: wine_glass_block
  1 LOTRBlockWoodElvenTorch [particles]: wood_elven_torch
== items.json
  6 LOTRItemAncientItem [rclick]: ancient_sword, ancient_dagger, ancient_helmet, ancient_chestplate, ancient_leggings
  1 LOTRItemBearRug [useon]: bearskin_rug
  2 LOTRItemBossTrophy [useon]: hill_troll_chieftain_skull, mallorn_ent_trunk
  1 LOTRItemBottlePoison [eaten,rclick]: bottle_of_poison
  1 LOTRItemBrandingIron [rclick,useentity,useon]: branding_iron
  2 LOTRItemChisel [useon]: chisel, moon_chisel
  1 LOTRItemDaleCracker [eaten,rclick]: dalish_cracker
  2 LOTRItemDoubleTorch [useon]: orc_torch, taurethrim_torch
  6 LOTRItemDye [useentity]: yellow_dye, white_dye, bluebell_blue, clover_green, charcoal_dust
  7 LOTRItemEntDraught [eaten,rclick,useon]: green_ent_draught, brown_ent_draught, golden_ent_draught, yellow_ent_draught, red_ent_draught
  1 LOTRItemGiraffeRug [useon]: giraffeskin_rug
  1 LOTRItemGuldurilCrystal [useon]: gulduril
  1 LOTRItemLionRug [useon]: lionskin_rug
  1 LOTRItemMatch [useon]: sulfur_match
  1 LOTRItemMechanism [useon]: mechanism
  1 LOTRItemNPCRespawner [rclick,useon]: npc_respawner
  1 LOTRItemQuenditeCrystal [useon]: edhelvir
  1 LOTRItemSalt [useon]: salt
  1 LOTRItemTermite [rclick]: exploding_termite
  3 LOTRItemTrollStatue [useon]: troll_statue_1, troll_statue_2, troll_statue_3
  8 LOTRItemUtumnoKey [useon]: key_of_ice, key_of_obsidian, key_handle, key_shaft, key_pin
  1 LOTRItemWargskinRug [useon]: wargskin_rug
