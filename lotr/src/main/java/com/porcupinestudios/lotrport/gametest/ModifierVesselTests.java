package com.porcupinestudios.lotrport.gametest;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.LotrBlocks;
import com.porcupinestudios.lotrport.drink.VesselBlockEntity;
import com.porcupinestudios.lotrport.modifier.ModifierDef;
import com.porcupinestudios.lotrport.modifier.Modifiers;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Set-down drinks (drink a full one where it stands, nothing on an empty one) and LOTR's modifiers hooked in. */
@GameTestHolder(LotrPort.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ModifierVesselTests {
    private ModifierVesselTests() {}

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(LotrPort.id(id));
    }

    private static Holder<Enchantment> ench(GameTestHelper helper, ResourceKey<Enchantment> key) {
        return helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
    }

    private static Holder<Enchantment> lotr(GameTestHelper helper, String id) {
        return ench(helper, ResourceKey.create(Registries.ENCHANTMENT, LotrPort.id(id)));
    }

    @GameTest(template = "empty")
    public static void setDownMugDrinksThenDoesNothing(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        p.getFoodData().setFoodLevel(10);
        BlockPos floor = new BlockPos(0, 1, 0);
        BlockPos at = floor.above();
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(at, LotrBlocks.BY_ID.get("mug_block").get());
        BlockPos abs = helper.absolutePos(at);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        VesselBlockEntity be = (VesselBlockEntity) helper.getLevel().getBlockEntity(abs);
        helper.assertTrue(be != null, "no vessel block entity");

        // empty: nothing happens, the mug stays
        BlockState state = helper.getLevel().getBlockState(abs);
        InteractionResult empty = state.useWithoutItem(helper.getLevel(), p, hit);
        helper.assertTrue(empty == InteractionResult.PASS, "empty mug did " + empty);
        helper.assertBlockPresent(LotrBlocks.BY_ID.get("mug_block").get(), at);

        // pour ale in from the hand: the hand gets the empty mug
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item("ale")));
        state.useItemOn(p.getMainHandItem(), helper.getLevel(), p, InteractionHand.MAIN_HAND, hit);
        helper.assertTrue(be.held().is(item("ale")), "ale not poured in: " + be.held());
        helper.assertTrue(p.getMainHandItem().is(item("mug")), "hand holds " + p.getMainHandItem());

        // full: drinking it feeds, the mug stays where it stands, empty
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult drank = state.useWithoutItem(helper.getLevel(), p, hit);
        helper.assertTrue(drank.consumesAction(), "full mug did " + drank);
        helper.assertTrue(p.getFoodData().getFoodLevel() > 10, "ale didn't feed");
        helper.assertBlockPresent(LotrBlocks.BY_ID.get("mug_block").get(), at);
        helper.assertTrue(be.held().is(item("mug")), "mug holds " + be.held());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void craftedGearRollsCompatibleModifiers(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        helper.assertTrue(!Modifiers.defs().isEmpty(), "no modifier data loaded");
        int enchanted = 0;
        for (int i = 0; i < 100; i++) {
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            sword.onCraftedBy(helper.getLevel(), p, 1); // the mixin rolls here
            if (!sword.getEnchantments().isEmpty()) {
                enchanted++;
            }
            Set<Holder<Enchantment>> seen = new HashSet<>();
            for (Holder<Enchantment> h : sword.getEnchantments().keySet()) {
                helper.assertTrue(h.value().canEnchant(sword), h + " can't go on a sword");
                helper.assertTrue(EnchantmentHelper.isEnchantmentCompatible(seen, h), "clashing modifiers " + seen + h);
                seen.add(h);
            }
        }
        helper.assertTrue(enchanted > 0, "100 crafted swords and none had a modifier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void reforgeKeepsBanesAndScrollsUpgrade(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(lotr(helper, "bane_orc"), 1);
        sword.enchant(lotr(helper, "weak3"), 1);
        ItemStack reforged = Modifiers.reforge(sword, RandomSource.create(1), registries);
        helper.assertTrue(reforged.getEnchantments().getLevel(lotr(helper, "bane_orc")) == 1, "orc-bane lost on reforge");

        ModifierDef keen = Modifiers.defs().get(LotrPort.id("strong1"));
        helper.assertTrue(keen != null && keen.upgrade().equals(java.util.Optional.of(LotrPort.id("strong2"))),
                "strong1 doesn't upgrade to strong2");
        ItemStack scroll = Modifiers.setScroll(new ItemStack(item("s_smiths_scroll")), lotr(helper, "strong1"));
        helper.assertTrue(Modifiers.scrollModifier(scroll, registries).map(h -> h.is(LotrPort.id("strong1")))
                .orElse(false), "scroll lost its modifier");
        ItemStack blank = new ItemStack(item("s_smiths_scroll"));
        Modifiers.fillScroll(blank, RandomSource.create(2), registries);
        helper.assertTrue(Modifiers.scrollModifier(blank, registries).isPresent(), "dropped scroll got no modifier");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lotrLootingCountsAsLooting(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(lotr(helper, "looting2"), 1);
        var looting = ench(helper, net.minecraft.world.item.enchantment.Enchantments.LOOTING);
        int level = EnchantmentHelper.getItemEnchantmentLevel(looting, sword);
        helper.assertTrue(level == 2, "LOTR looting2 reads as looting " + level);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void poisonedDrinkKnowsItsPoisonerAndPoisons(GameTestHelper helper) {
        @SuppressWarnings("removal")
        ServerPlayer p = (ServerPlayer) helper.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        var recipe = new com.porcupinestudios.lotrport.drink.PoisonDrinkRecipe(
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC);
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                java.util.List.of(new ItemStack(item("ale")), new ItemStack(item("bottle_of_poison"))));
        helper.assertTrue(recipe.matches(input, helper.getLevel()), "ale + poison doesn't match");
        ItemStack out = recipe.assemble(input, helper.getLevel().registryAccess());
        helper.assertTrue(out.is(item("ale")), "made " + out);
        helper.assertTrue(recipe.getRemainingItems(input).get(1).is(Items.GLASS_BOTTLE), "no empty bottle back");
        out.onCraftedBy(helper.getLevel(), p, 1);
        helper.assertTrue(p.getUUID().equals(out.get(com.porcupinestudios.lotrport.content.LotrComponents.POISONED.get())),
                "poisoner not stored");
        out.getItem().finishUsingItem(out, helper.getLevel(), p);
        helper.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.WITHER), "poisoned ale didn't poison");
        var twice = net.minecraft.world.item.crafting.CraftingInput.of(2, 1,
                java.util.List.of(new ItemStack(item("ale")), new ItemStack(Items.STICK)));
        helper.assertTrue(!recipe.matches(twice, helper.getLevel()), "ale + stick matched");
        helper.succeed();
    }
}
