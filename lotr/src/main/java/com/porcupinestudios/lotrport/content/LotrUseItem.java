package com.porcupinestudios.lotrport.content;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * LOTR's hold-to-use items, as far as their sounds go (each plays when LOTR played it):
 * <ul>
 * <li>{@code horn} (horns of command, warhorns): blown for 40 ticks, then {@code item.horn}, loud (volume 4, as
 * LOTRItemCommandHorn). Ordering hired units and calling invasions come with those systems.</li>
 * <li>{@code pipe} (the smoking pipe): smoked for 40 ticks with pipe-weed in the inventory, which it uses up, then
 * {@code item.puff} and a smoke ring of particles (LOTRItemHobbitPipe).</li>
 * <li>{@code vessel} (mugs, goblets, cups): used on water it becomes a vessel of water, with {@code item.mug_fill}
 * (LOTRItemMug).</li>
 * </ul>
 */
public class LotrUseItem extends Item {
    private final String mode;
    private final String fills;

    public LotrUseItem(Properties properties, String mode, String fills) {
        super(properties);
        this.mode = mode;
        this.fills = fills;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return mode.equals("vessel") ? 0 : 40;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return switch (mode) {
            case "horn" -> UseAnim.TOOT_HORN;
            case "pipe" -> UseAnim.DRINK;
            default -> UseAnim.NONE;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (mode.equals("vessel")) {
            return fill(level, player, hand, stack);
        }
        if (mode.equals("pipe") && !player.getAbilities().instabuild && findPipeWeed(player).isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (mode.equals("horn")) {
            LotrSounds.playAt(level, entity.getX(), entity.getY(), entity.getZ(), "item.horn", SoundSource.PLAYERS,
                    4f, 1f);
            if (entity instanceof Player p) {
                p.getCooldowns().addCooldown(this, 40);
            }
        } else if (mode.equals("pipe") && entity instanceof Player player) {
            if (!player.getAbilities().instabuild) {
                ItemStack weed = findPipeWeed(player);
                if (weed.isEmpty()) {
                    return stack;
                }
                weed.shrink(1);
            }
            LotrSounds.playAt(level, player.getX(), player.getY(), player.getZ(), "item.puff", SoundSource.PLAYERS,
                    1f, 1f);
            if (level instanceof ServerLevel server) {
                var look = player.getLookAngle();
                for (int i = 0; i < 16; i++) {
                    server.sendParticles(ParticleTypes.CLOUD, player.getX() + look.x * 0.6,
                            player.getEyeY() + look.y * 0.6, player.getZ() + look.z * 0.6, 1,
                            0.05, 0.05, 0.05, 0.01);
                }
            }
        }
        return stack;
    }

    private static ItemStack findPipeWeed(Player player) {
        Item weed = BuiltInRegistries.ITEM.get(LotrPort.id("pipe_weed"));
        for (ItemStack s : player.getInventory().items) {
            if (s.is(weed)) {
                return s;
            }
        }
        return ItemStack.EMPTY;
    }

    private InteractionResultHolder<ItemStack> fill(Level level, Player player, InteractionHand hand,
            ItemStack stack) {
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);
        if (hit.getType() != HitResult.Type.BLOCK || fills == null
                || !level.getFluidState(hit.getBlockPos()).is(Fluids.WATER)) {
            return InteractionResultHolder.pass(stack);
        }
        Item water = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(fills));
        LotrSounds.playAt(level, player.getX(), player.getY(), player.getZ(), "item.mug_fill", SoundSource.PLAYERS,
                1f, 1f);
        if (player instanceof ServerPlayer sp) {
            sp.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(stack, player, new ItemStack(water)),
                level.isClientSide());
    }
}
