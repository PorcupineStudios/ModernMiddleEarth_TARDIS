package com.porcupinestudios.lotrport.content;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * A tool tier made from one converted tool's numbers. Repair ingredients come with the recipe pass; until then tools
 * repair only by combining two of them.
 */
public record ConvertedTier(int uses, float speed, int level, int enchantmentValue) implements Tier {
    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return 0f;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return switch (level) {
            case 0 -> BlockTags.INCORRECT_FOR_WOODEN_TOOL;
            case 1 -> BlockTags.INCORRECT_FOR_STONE_TOOL;
            case 2 -> BlockTags.INCORRECT_FOR_IRON_TOOL;
            default -> BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
        };
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.EMPTY;
    }
}
