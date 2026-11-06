package com.porcupinestudios.lotrport.npc.behaviour;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.NpcType;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * LOTR's spiders (LOTREntitySpiderBase and its kinds):
 * <ul>
 * <li>a size, scale 0.5 + size / 2 (Mirkwood: size 0-2; Mordor: 1-3), which scales body and hitbox;</li>
 * <li>a venom: Mirkwood spiders are half harmless, half slowing or poisoning (and look it), Mordor's poison;
 * a bite gives difficulty x (difficulty + 5) / 2 seconds of it;</li>
 * <li>they climb walls.</li>
 * </ul>
 * Data: {@code size_min}, {@code size_max}, {@code venom} (0 none, 1 slowness, 2 poison, -1 Mirkwood's mix).
 */
public class SpiderBehaviour implements CreatureBehaviour {
    public static final int NONE = 0;
    public static final int SLOWNESS = 1;
    public static final int POISON = 2;

    @Override
    public void init(LotrNpc npc, NpcType type) {
        int min = (int) CreatureBehaviour.data(type, "size_min", 0);
        int max = (int) CreatureBehaviour.data(type, "size_max", 2);
        int size = min + npc.random().nextInt(Math.max(1, max - min + 1));
        float scale = 0.5f + size / 2f;
        npc.setState("getSpiderScale", size);
        npc.setState("getSpiderScaleAmount", scale);
        npc.setBodySize(type.persona().width() * scale, type.persona().height() * scale);
        int venom = (int) CreatureBehaviour.data(type, "venom", -1);
        if (venom < 0) {
            venom = npc.random().nextBoolean() ? NONE : 1 + npc.random().nextInt(2);
        }
        npc.setState("getSpiderType", venom);
        // the Mirkwood skins show the venom: plain, ...Slowness, ...Poison
        List<ResourceLocation> skins = type.skins();
        String want = venom == SLOWNESS ? "slowness" : venom == POISON ? "poison" : null;
        for (ResourceLocation s : skins) {
            String p = s.getPath();
            boolean match = want == null ? !p.contains("slowness") && !p.contains("poison") : p.contains(want);
            if (match && skins.size() > 1) {
                npc.setSkin(s.toString());
                break;
            }
        }
    }

    @Override
    public void hit(LotrNpc npc, Entity target) {
        int venom = (int) npc.state("getSpiderType");
        if (!(target instanceof LivingEntity living) || venom == NONE) {
            return;
        }
        int d = npc.level().getDifficulty().getId();
        int seconds = d * (d + 5) / 2;
        if (seconds > 0) {
            living.addEffect(new MobEffectInstance(venom == SLOWNESS ? MobEffects.MOVEMENT_SLOWDOWN : MobEffects.POISON,
                    seconds * 20, 0));
        }
    }

    @Override
    public boolean climbs(LotrNpc npc) {
        return true;
    }
}
