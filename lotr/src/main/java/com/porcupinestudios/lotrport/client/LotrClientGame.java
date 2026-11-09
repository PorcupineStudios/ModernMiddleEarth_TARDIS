package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client game events: the map key asks the server for the map; LOTR's thick fog in foggy biomes (Fangorn...). */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LotrClientGame {
    /** Biomes LOTR drew in thick fog (its biomeColors foggy), from the converter. */
    private static final TagKey<Biome> FOGGY = TagKey.create(Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID, "foggy"));
    private static float fog;
    private static float lastFog;

    private LotrClientGame() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (LotrClient.JUKEBOX_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                mc.setScreen(new JukeboxScreen());
            }
        }
        while (LotrClient.MAP_KEY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                PacketDistributor.sendToServer(new MapNet.OpenMap());
            }
        }
        lastFog = fog;
        boolean foggy = mc.level != null && mc.player != null
                && mc.level.getBiome(mc.player.blockPosition()).is(FOGGY);
        fog = Math.max(0, Math.min(1, fog + (foggy ? 0.05f : -0.05f))); // fades over a second across a border
    }

    /** LOTR's foggy biomes: fog from a tenth of the view distance to 60% of it (at least 64 blocks). */
    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog event) {
        float f = lastFog + (fog - lastFog) * (float) event.getPartialTick();
        if (f <= 0 || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN || event.getType() != FogType.NONE) {
            return;
        }
        float far = event.getFarPlaneDistance();
        // a mist that leaves the next trees clear (the Nether's 0.05..0.5 hid everything past a few trees)
        event.setNearPlaneDistance(event.getNearPlaneDistance() + (far * 0.1f - event.getNearPlaneDistance()) * f);
        event.setFarPlaneDistance(far + (Math.min(far, Math.max(64f, far * 0.6f)) - far) * f);
        event.setCanceled(true);
    }
}
