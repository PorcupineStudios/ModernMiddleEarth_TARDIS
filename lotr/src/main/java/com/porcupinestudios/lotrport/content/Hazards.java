package com.porcupinestudios.lotrport.content;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.faction.FactionKills;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * LOTR's blocks that do something to whoever walks into them (LOTRBlockQuagmire, Waste, CoralReef, MordorThorn,
 * TallGrass thistles and nettles, MorgulFlower). Which block is which: {@code behaviors.json} "hazard".
 */
public final class Hazards {
    public enum Hazard {
        QUAGMIRE, WASTE, CORAL, THORN, NETTLE, MORGUL
    }

    private static final ResourceLocation MORDOR = LotrPort.id("mordor");
    private static Map<String, Hazard> byId;

    private Hazards() {}

    public static Hazard of(String id) {
        if (byId == null) {
            byId = new HashMap<>();
            JsonObject s = ContentData.behaviours().getAsJsonObject("hazard");
            for (String key : s.keySet()) {
                Hazard h = Hazard.valueOf(key.toUpperCase(Locale.ROOT));
                s.getAsJsonArray(key).forEach(i -> byId.put(i.getAsString(), h));
            }
        }
        return byId.get(id);
    }

    /** Entity inside the block's space. */
    public static void inside(Hazard h, BlockState state, Level level, BlockPos pos, Entity entity) {
        switch (h) {
            // LOTR: setInWeb, as a cobweb
            case QUAGMIRE -> entity.makeStuckInBlock(state, new Vec3(0.25, 0.05, 0.25));
            case WASTE -> entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.4, 1, 0.4));
            case THORN -> {
                if (!level.isClientSide && !mordorFriend(entity)) {
                    entity.hurt(level.damageSources().sweetBerryBush(), 2f);
                }
            }
            case NETTLE -> {
                // LOTR: stings legs not covered by leggings and boots
                if (!level.isClientSide && entity instanceof LivingEntity l
                        && (l.getItemBySlot(EquipmentSlot.LEGS).isEmpty() || l.getItemBySlot(EquipmentSlot.FEET).isEmpty())
                        && entity.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) {
                    entity.hurt(level.damageSources().sweetBerryBush(), 0.25f);
                }
            }
            case MORGUL -> {
                if (!level.isClientSide && entity instanceof LivingEntity l && !mordorFriend(entity)) {
                    l.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
                    l.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100));
                }
            }
            default -> { }
        }
    }

    /** Entity walking on top of the block. */
    public static void stepOn(Hazard h, Level level, Entity entity) {
        if (h == Hazard.CORAL && !level.isClientSide && entity instanceof LivingEntity && !(entity instanceof WaterAnimal)) {
            entity.hurt(level.damageSources().cactus(), 0.5f);
        }
    }

    /** Mordor's own, creative players and players at +250 Mordor alignment are spared (LOTR). */
    private static boolean mordorFriend(Entity e) {
        if (e instanceof Player p) {
            return p.isCreative() || Alignment.get(p, MORDOR) >= 250f;
        }
        return FactionKills.factionOf(e).map(MORDOR::equals).orElse(false);
    }
}
