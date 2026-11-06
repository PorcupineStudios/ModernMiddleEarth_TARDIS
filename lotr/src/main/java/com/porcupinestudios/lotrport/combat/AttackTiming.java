package com.porcupinestudios.lotrport.combat;

import com.porcupinestudios.lotrport.LotrConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import java.lang.reflect.Field;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * LOTR's melee timing (LOTRAttackTiming on the client, LOTRNetHandlerPlayServer on the server) in place of vanilla's
 * attack cooldown. Vanilla lets a player hit as fast as they click and weakens early hits; LOTR made the player wait:
 * after hitting a living thing, another hit on a living thing does nothing at all until the held weapon's time is up.
 * Blocks, boats, item frames and the like are never timed, as in LOTR.
 *
 * <p>The wait is the weapon's attack time in ticks, {@code 20 / attack speed}. A LOTR weapon's attack speed is set to
 * {@code 20 / LOTR's ticks} by {@code LotrWeaponItem}, so this gives back LOTR's own numbers (sword 15, dagger 10,
 * spear 18, polearm and hammer 22, battleaxe 20, pike and lance 30), and speed enchantments count. Every hit that lands
 * is at full strength: vanilla's strength ticker is held full, which also keeps vanilla's cooldown indicator away.
 *
 * <p>Both sides keep their own wait on the {@link LotrRegistries#ATTACK_WAIT} attachment, counted in the player's
 * own ticks. The client swallows early clicks (see {@code client.LotrHud}), so nothing is sent; the server refuses
 * early hits anyway, with {@link LotrConfig#ATTACK_WAIT_GRACE} ticks of slack for network jitter.
 * {@link LotrConfig#FORCED_ATTACK_WAIT} turns it all off.
 */
public final class AttackTiming {
    /** Vanilla's attack strength ticker (LivingEntity); Mojang names are the runtime names on NeoForge 1.21. */
    private static final Field STRENGTH_TICKER =
            ObfuscationReflectionHelper.findField(LivingEntity.class, "attackStrengthTicker");
    /** Far past any weapon's delay, so the strength scale reads 1. */
    private static final int FULL_STRENGTH = 10_000;

    /**
     * A wait in progress.
     *
     * @param start the player's tick count when the hit landed
     * @param ready the tick count from which the next hit is allowed
     * @param item what the hit was made with (the attack meter draws it)
     */
    public record Wait(int start, int ready, ItemStack item) {
        public static final Wait NONE = new Wait(0, 0, ItemStack.EMPTY);

        public int length() {
            return ready - start;
        }
    }

    private AttackTiming() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AttackTiming::onAttack);
        NeoForge.EVENT_BUS.addListener(AttackTiming::onPlayerTick);
    }

    /** Whether LOTR's forced wait replaces vanilla's cooldown (false until the server config has arrived). */
    public static boolean enabled() {
        return LotrConfig.SPEC.isLoaded() && LotrConfig.FORCED_ATTACK_WAIT.get();
    }

    /** The wait after a hit with what the player holds now: 20 / attack speed, in ticks, at least 1. */
    public static int ticks(Player player) {
        return Math.max(1, Math.round(player.getCurrentItemAttackStrengthDelay()));
    }

    public static Wait wait(Player player) {
        return player.getData(LotrRegistries.ATTACK_WAIT);
    }

    /** Ticks left before the player may hit a living thing again (0 = ready). */
    public static int remaining(Player player) {
        return Math.max(0, wait(player).ready() - player.tickCount);
    }

    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!enabled() || !(event.getTarget() instanceof LivingEntity)) {
            return;
        }
        int grace = player.level().isClientSide() ? 0 : LotrConfig.ATTACK_WAIT_GRACE.get();
        if (player.tickCount < wait(player).ready() - grace) {
            event.setCanceled(true);
            return;
        }
        int ticks = ticks(player);
        player.setData(LotrRegistries.ATTACK_WAIT,
                new Wait(player.tickCount, player.tickCount + ticks, player.getMainHandItem().copy()));
        fullStrength(player);
    }

    /** After each tick (when vanilla may have reset it for a swing or an item switch), the strength is full again. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (enabled()) {
            fullStrength(event.getEntity());
        }
    }

    private static void fullStrength(Player player) {
        try {
            STRENGTH_TICKER.setInt(player, FULL_STRENGTH);
        } catch (IllegalAccessException e) {
            LotrPort.LOG.error("Can't set the attack strength ticker", e);
        }
    }
}
