package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client game events: the map key asks the server for the map. */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LotrClientGame {
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
    }
}
