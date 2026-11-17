package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

/**
 * Title screen music: the first track from {@code assets/<ns>/music/menu.json} whose sound event exists (the optional
 * lotr_port_music mod adds them). Without the music mod, vanilla's menu music plays as before.
 */
@EventBusSubscriber(modid = "lotr_port", value = Dist.CLIENT)
public final class MenuMusic {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath("lotr_port", "music/menu.json");
    private static final List<ResourceLocation> TRACKS = new ArrayList<>();
    private static Music music;
    private static boolean resolved;

    private MenuMusic() {}

    @EventBusSubscriber(modid = "lotr_port", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    static final class Reload {
        @SubscribeEvent
        static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) MenuMusic::load);
        }
    }

    private static void load(ResourceManager rm) {
        TRACKS.clear();
        for (Resource r : rm.getResourceStack(FILE)) {
            try (Reader in = r.openAsReader()) {
                List<ResourceLocation> list = new ArrayList<>();
                for (var e : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("tracks")) {
                    ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
                    if (id != null) {
                        list.add(id);
                    }
                }
                TRACKS.clear(); // the top pack wins
                TRACKS.addAll(list);
            } catch (Exception ignored) {
                // a broken file leaves the previous pack's list
            }
        }
        resolved = false;
        music = null;
    }

    @SubscribeEvent
    public static void select(SelectMusicEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null || mc.player != null) {
            return; // only on the menus, before a world is loaded
        }
        if (!resolved) {
            resolved = true; // sounds.json is loaded by now, so a missing event really is missing
            for (ResourceLocation id : TRACKS) {
                if (mc.getSoundManager().getSoundEvent(id) != null) {
                    music = new Music(Holder.direct(SoundEvent.createVariableRangeEvent(id)), 20, 600, true);
                    break;
                }
            }
        }
        if (music != null) {
            event.setMusic(music);
        }
    }
}
