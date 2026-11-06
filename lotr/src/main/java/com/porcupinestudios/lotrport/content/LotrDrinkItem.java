package com.porcupinestudios.lotrport.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * LOTR's drinks (LOTRItemMug): drunk like a potion, they feed ({@code heal}, {@code saturation}), give their effects
 * and make you drunk ({@code alcohol}: nausea), all scaled by the drink's strength (component
 * {@link LotrComponents#DRINK_STRENGTH}, factors from {@code drinks.json}); some hurt ({@code damage}) or cure
 * effects (milk). The empty vessel (component {@link LotrComponents#VESSEL}) comes back.
 */
public class LotrDrinkItem extends Item {
    private static final JsonObject TABLE = ContentData.readObject("drinks.json");
    private final JsonObject drink;

    public LotrDrinkItem(Properties properties, JsonObject drink) {
        super(properties);
        this.drink = drink;
    }

    static JsonObject strength(ItemStack stack) {
        JsonArray s = TABLE.has("strengths") ? TABLE.getAsJsonArray("strengths") : new JsonArray();
        if (s.isEmpty()) {
            JsonObject one = new JsonObject();
            one.addProperty("factor", 1f);
            one.addProperty("food_factor", 1f);
            return one;
        }
        Integer i = stack.get(LotrComponents.DRINK_STRENGTH.get());
        int idx = i == null ? ContentData.integer(TABLE, "default_strength", 2) : i;
        return s.get(Math.max(0, Math.min(s.size() - 1, idx))).getAsJsonObject();
    }

    static Item vesselItem(ItemStack stack) {
        String v = stack.getOrDefault(LotrComponents.VESSEL.get(), "mug");
        if (TABLE.has("vessels")) {
            for (JsonElement e : TABLE.getAsJsonArray("vessels")) {
                JsonObject o = e.getAsJsonObject();
                if (o.get("id").getAsString().equals(v)) {
                    return BuiltInRegistries.ITEM.get(ResourceLocation.parse(o.get("item").getAsString()));
                }
            }
        }
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse("lotr_port:mug"));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        JsonObject s = strength(stack);
        float factor = ContentData.num(s, "factor", 1f);
        float foodFactor = ContentData.num(s, "food_factor", 1f);
        if (!level.isClientSide) {
            if (entity instanceof Player p) {
                int heal = Math.round(ContentData.num(drink, "heal", 0) * foodFactor);
                if (heal > 0) {
                    p.getFoodData().eat(heal, ContentData.num(drink, "saturation", 0) * foodFactor);
                }
            }
            if (ContentData.bool(drink, "cures", false)) {
                entity.removeAllEffects();
            }
            if (drink.has("effects")) {
                for (JsonElement e : drink.getAsJsonArray("effects")) {
                    JsonObject o = e.getAsJsonObject();
                    BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(o.get("id").getAsString()))
                            .ifPresent(h -> entity.addEffect(new MobEffectInstance(h,
                                    Math.round(ContentData.integer(o, "duration", 200) * factor),
                                    ContentData.integer(o, "amplifier", 0))));
                }
            }
            float alcohol = ContentData.num(drink, "alcohol", 0) * factor;
            if (alcohol > 0) {
                // seconds of nausea per unit of alcohol x strength: a pack-tunable stand-in for LOTR's drunkenness
                int ticks = Math.round(alcohol * ContentData.num(TABLE, "nausea_seconds_per_alcohol", 30f) * 20);
                entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ticks));
            }
            float damage = ContentData.num(drink, "damage", 0) * factor;
            if (damage > 0) {
                entity.hurt(level.damageSources().magic(), damage);
            }
            if (entity instanceof ServerPlayer sp) {
                sp.awardStat(Stats.ITEM_USED.get(this));
            }
        }
        if (entity instanceof Player p && p.getAbilities().instabuild) {
            return stack;
        }
        ItemStack empty = new ItemStack(vesselItem(stack));
        stack.shrink(1);
        if (stack.isEmpty()) {
            return empty;
        }
        if (entity instanceof Player p && !p.getInventory().add(empty)) {
            p.drop(empty, false);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        JsonObject s = strength(stack);
        if (ContentData.bool(drink, "brewed", false) && s.has("lang")) {
            tooltip.add(Component.translatable(s.get("lang").getAsString()).withStyle(ChatFormatting.GRAY));
        }
        float alcohol = ContentData.num(drink, "alcohol", 0) * ContentData.num(s, "factor", 1f);
        if (alcohol > 0) {
            tooltip.add(Component.translatable("item.lotr.drink.alcoholicity")
                    .append(String.format(": %.2f", alcohol))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

}
