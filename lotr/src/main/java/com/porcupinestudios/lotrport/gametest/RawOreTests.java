package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * raw_ores.py and renames.py: LOTR's metal ores drop raw ore (silk touch: the ore, fortune: more, as vanilla iron
 * ore), raw ore smelts or forges into the ingot and carries the common tags Create's crushing reads, and LOTR's
 * amethyst is tanzanite (not in vanilla's c:gems/amethyst). Create's own recipes can't run here (no Create on the
 * test server); its tag recipes are checked through the tags.
 */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RawOreTests {
    private RawOreTests() {}

    /** ore -> raw ore, metal (c:raw_materials/<metal>). */
    private static final List<String[]> ORES = List.of(
            new String[] {"copper_ore", "raw_copper", "copper"}, new String[] {"tin_ore", "raw_tin", "tin"},
            new String[] {"silver_ore", "raw_silver", "silver"}, new String[] {"mithril_ore", "raw_mithril", "mithril"},
            new String[] {"morgul_iron_ore", "raw_morgul_iron", "morgul_iron"},
            new String[] {"morgul_iron_ore_block", "raw_morgul_iron", "morgul_iron"});

    private static Block block(String id) {
        return BuiltInRegistries.BLOCK.get(LotrPort.id(id));
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    private static TagKey<Item> c(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static ItemStack pick(GameTestHelper helper, ResourceKey<Enchantment> ench, int level) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (ench != null) {
            tool.enchant(helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                    .getHolderOrThrow(ench), level);
        }
        return tool;
    }

    private static List<ItemStack> drops(GameTestHelper helper, Block ore, ItemStack tool) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        return Block.getDrops(ore.defaultBlockState(), helper.getLevel(), pos, null, null, tool);
    }

    @GameTest(template = "empty", batch = "raw_ores")
    public static void oresDropRawOreSilkTouchAndFortune(GameTestHelper helper) {
        ItemStack plain = pick(helper, null, 0);
        ItemStack silk = pick(helper, Enchantments.SILK_TOUCH, 1);
        ItemStack fortune = pick(helper, Enchantments.FORTUNE, 3);
        for (String[] o : ORES) {
            Block ore = block(o[0]);
            Item raw = item(o[1]);
            helper.assertTrue(raw != Items.AIR, o[1] + " isn't registered");
            List<ItemStack> d = drops(helper, ore, plain);
            helper.assertTrue(d.size() == 1 && d.get(0).is(raw) && d.get(0).getCount() == 1,
                    o[0] + " mined drops " + d + ", not 1 " + o[1]);
            d = drops(helper, ore, silk);
            helper.assertTrue(d.size() == 1 && d.get(0).is(ore.asItem()) && d.get(0).getCount() == 1,
                    o[0] + " with silk touch drops " + d + ", not the ore");
            int total = 0;
            int most = 0;
            for (int n = 0; n < 200; n++) {
                for (ItemStack s : drops(helper, ore, fortune)) {
                    helper.assertTrue(s.is(raw), o[0] + " with fortune drops " + s);
                    total += s.getCount();
                    most = Math.max(most, s.getCount());
                }
            }
            // ore_drops with fortune III: 1-4 per ore, 2.2 on average (vanilla iron ore)
            helper.assertTrue(total > 300 && most <= 4,
                    o[0] + " with fortune III dropped " + total + " in 200 (most " + most + ")");
            helper.assertTrue(new ItemStack(raw).is(c("raw_materials/" + o[2])),
                    o[1] + " isn't in c:raw_materials/" + o[2] + " (Create's crushing reads it)");
            Block rawBlock = block(o[1] + "_block");
            helper.assertTrue(new ItemStack(rawBlock).is(c("storage_blocks/raw_" + o[2])),
                    o[1] + "_block isn't in c:storage_blocks/raw_" + o[2]);
            d = drops(helper, rawBlock, plain);
            helper.assertTrue(d.size() == 1 && d.get(0).is(rawBlock.asItem()), o[1] + "_block drops " + d);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "raw_ores")
    public static void rawOreSmeltsLikeTheOre(GameTestHelper helper) {
        var level = helper.getLevel();
        for (String metal : List.of("copper", "tin", "silver")) {
            for (var type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
                var r = level.getRecipeManager().getRecipeFor(type,
                        new SingleRecipeInput(new ItemStack(item("raw_" + metal))), level);
                helper.assertTrue(r.isPresent() && r.get().value().getResultItem(level.registryAccess())
                        .is(item(metal + "_ingot")), "raw " + metal + " doesn't " + type + " into its ingot");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "raw_ores")
    public static void amethystIsTanzanite(GameTestHelper helper) {
        for (String id : List.of("tanzanite", "tanzanite_block", "tanzanite_vein")) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(LotrPort.id(id)), id + " isn't registered");
        }
        for (String id : List.of("amethyst", "amethyst_block", "amethyst_vein")) {
            helper.assertFalse(BuiltInRegistries.ITEM.containsKey(LotrPort.id(id)), "lotr_port:" + id + " still exists");
        }
        helper.assertFalse(new ItemStack(item("tanzanite")).is(c("gems/amethyst")), "tanzanite counts as amethyst");
        helper.assertTrue(new ItemStack(item("tanzanite")).is(c("gems/tanzanite")), "tanzanite isn't c:gems/tanzanite");
        helper.succeed();
    }
}
