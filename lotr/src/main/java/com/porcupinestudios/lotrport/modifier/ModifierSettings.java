package com.porcupinestudios.lotrport.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Where modifiers come from and what the anvil charges ({@code data/<ns>/modifier_settings/settings.json}).
 *
 * <p>{@code rolls} / {@code skilful_rolls}: the chance of a first modifier, then of a second given a first, and so
 * on. Plain rolls are for crafted gear, skilful ones for what traders sell and ancient items. {@code crafting},
 * {@code trades}: whether those get rolls at all. Anvil costs are XP levels: {@code scroll_cost} plus
 * {@code scroll_cost_per_anvil_cost} times the modifier's {@code anvil_cost} to apply a Smith's Scroll,
 * {@code combine_cost} to combine two scrolls into the next one, {@code reforge_cost} to reforge (gear that isn't
 * damaged plus one of its repair material: its modifiers rolled again). {@code heads}: what headhunting drops per
 * entity type (players always drop their own head).
 */
public record ModifierSettings(List<Float> rolls, List<Float> skilfulRolls, boolean crafting, boolean trades,
        int scrollCost, int scrollCostPerAnvilCost, int combineCost, int reforgeCost, float headChance,
        Map<ResourceLocation, ResourceLocation> heads) {
    public static final ModifierSettings DEFAULT = new ModifierSettings(List.of(0.35f, 0.15f), List.of(0.85f, 0.3f),
            true, true, 1, 2, 5, 3, 1f, Map.of());

    public static final Codec<ModifierSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.listOf().optionalFieldOf("rolls", DEFAULT.rolls()).forGetter(ModifierSettings::rolls),
            Codec.FLOAT.listOf().optionalFieldOf("skilful_rolls", DEFAULT.skilfulRolls())
                    .forGetter(ModifierSettings::skilfulRolls),
            Codec.BOOL.optionalFieldOf("crafting", true).forGetter(ModifierSettings::crafting),
            Codec.BOOL.optionalFieldOf("trades", true).forGetter(ModifierSettings::trades),
            Codec.INT.optionalFieldOf("scroll_cost", 1).forGetter(ModifierSettings::scrollCost),
            Codec.INT.optionalFieldOf("scroll_cost_per_anvil_cost", 2).forGetter(ModifierSettings::scrollCostPerAnvilCost),
            Codec.INT.optionalFieldOf("combine_cost", 5).forGetter(ModifierSettings::combineCost),
            Codec.INT.optionalFieldOf("reforge_cost", 3).forGetter(ModifierSettings::reforgeCost),
            Codec.FLOAT.optionalFieldOf("head_chance", 1f).forGetter(ModifierSettings::headChance),
            Codec.unboundedMap(ResourceLocation.CODEC, ResourceLocation.CODEC).optionalFieldOf("heads", Map.of())
                    .forGetter(ModifierSettings::heads))
            .apply(i, ModifierSettings::new));
}
