package com.porcupinestudios.lotrport.client;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * LOTR's display particles (randomDisplayTick: elven torch glitter, Morgul flames, chandelier candles, hearth smoke,
 * mallorn sparkle) for any block class, from {@code behaviors.json} "particles". Like vanilla's animateTick it
 * samples random spots near the player each tick, so it costs the same however many such blocks are about.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, value = Dist.CLIENT)
public final class BlockParticles {
    private record Spec(ParticleOptions particle, float chance, float y, float spread, int count) {}

    private static Map<Block, Spec> specs;

    private BlockParticles() {}

    private static Map<Block, Spec> specs() {
        if (specs == null) {
            specs = new HashMap<>();
            JsonObject all = ContentData.behaviours().getAsJsonObject("particles");
            for (String id : all.keySet()) {
                JsonObject o = all.getAsJsonObject(id);
                var type = BuiltInRegistries.PARTICLE_TYPE.get(ResourceLocation.parse(o.get("particle").getAsString()));
                Block block = BuiltInRegistries.BLOCK.get(LotrPort.id(id));
                if (type instanceof SimpleParticleType simple) {
                    specs.put(block, new Spec(simple, ContentData.num(o, "chance", 0.5f), ContentData.num(o, "y", 0.5f),
                            ContentData.num(o, "spread", 0.4f), ContentData.integer(o, "count", 1)));
                }
            }
        }
        return specs;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) {
            return;
        }
        Map<Block, Spec> map = specs();
        RandomSource r = level.random;
        BlockPos at = mc.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 667; i++) { // vanilla's animateTick sample count, within 16 blocks
            pos.set(at.getX() + r.nextInt(16) - r.nextInt(16), at.getY() + r.nextInt(16) - r.nextInt(16),
                    at.getZ() + r.nextInt(16) - r.nextInt(16));
            Spec s = map.get(level.getBlockState(pos).getBlock());
            if (s == null || r.nextFloat() >= s.chance) {
                continue;
            }
            for (int n = 0; n < s.count; n++) {
                double x = pos.getX() + 0.5 + (r.nextDouble() - 0.5) * 2 * s.spread;
                double z = pos.getZ() + 0.5 + (r.nextDouble() - 0.5) * 2 * s.spread;
                level.addParticle(s.particle, x, pos.getY() + s.y, z, 0, 0.01, 0);
            }
        }
    }
}
