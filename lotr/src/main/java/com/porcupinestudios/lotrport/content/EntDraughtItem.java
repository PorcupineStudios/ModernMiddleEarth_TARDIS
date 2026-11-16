package com.porcupinestudios.lotrport.content;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * LOTR's Ent-draughts (LOTRItemEntDraught): drunk over 32 ticks, giving food and effects from {@code behaviors.json}
 * "draught". Anyone below 0 Fangorn alignment is poisoned for 5 s instead. Leaves a bowl.
 */
public class EntDraughtItem extends Item {
    private static final ResourceLocation FANGORN = LotrPort.id("fangorn");
    private final int heal;
    private final float saturation;
    private final List<MobEffectInstance> effects = new ArrayList<>();
    private final JsonObject spec;

    public EntDraughtItem(Properties properties, JsonObject spec) {
        super(properties);
        this.spec = spec;
        this.heal = ContentData.integer(spec, "heal", 0);
        this.saturation = ContentData.num(spec, "saturation", 0f);
    }

    private List<MobEffectInstance> effects() {
        if (effects.isEmpty() && spec.has("effects")) {
            spec.getAsJsonArray("effects").forEach(j -> {
                JsonObject o = j.getAsJsonObject();
                Holder<MobEffect> e = BuiltInRegistries.MOB_EFFECT
                        .getHolder(ResourceLocation.parse(o.get("effect").getAsString())).orElse(null);
                if (e != null) {
                    effects.add(new MobEffectInstance(e, 20 * o.get("seconds").getAsInt()));
                }
            });
        }
        return effects;
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
        if (!level.isClientSide) {
            if (entity instanceof Player p && !p.isCreative() && Alignment.get(p, FANGORN) < 0f) {
                p.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
            } else {
                if (entity instanceof Player p && heal > 0) {
                    p.getFoodData().eat(heal, saturation);
                }
                for (MobEffectInstance e : effects()) {
                    entity.addEffect(new MobEffectInstance(e));
                }
            }
        }
        if (entity instanceof Player p && p.getAbilities().instabuild) {
            return stack;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            return new ItemStack(Items.BOWL);
        }
        if (entity instanceof Player p && !p.getInventory().add(new ItemStack(Items.BOWL))) {
            p.drop(new ItemStack(Items.BOWL), false);
        }
        return stack;
    }
}
