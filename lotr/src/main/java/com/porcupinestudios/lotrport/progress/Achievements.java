package com.porcupinestudios.lotrport.progress;

import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.content.ContentData;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * LOTR's achievements as advancements ({@code advancement/<category>/<name>}, one tab per LOTR category, generated
 * by {@code converter/extra.py}). Their criteria are {@code minecraft:impossible}: code grants them by LOTR name with
 * {@link #grant}. Granted here: entering Middle-earth and each biome's own achievement (biome_achievements.json).
 */
public final class Achievements {
    private static final JsonObject INDEX = ContentData.readObject("achievements.json");
    private static final JsonObject BIOMES = ContentData.readObject("biome_achievements.json");

    private Achievements() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Achievements::onTick);
    }

    /** Awards LOTR achievement {@code name} (e.g. "enterRohan"); false if there's no such achievement. */
    public static boolean grant(ServerPlayer player, String name) {
        if (!INDEX.has(name)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.parse(INDEX.getAsJsonObject(name).get("id").getAsString());
        AdvancementHolder adv = player.server.getAdvancements().get(id);
        if (adv == null) {
            return false;
        }
        if (!player.getAdvancements().getOrStartProgress(adv).isDone()) {
            player.getAdvancements().award(adv, "lotr");
        }
        return true;
    }

    public static boolean has(ServerPlayer player, String name) {
        if (!INDEX.has(name)) {
            return false;
        }
        AdvancementHolder adv = player.server.getAdvancements().get(
                ResourceLocation.parse(INDEX.getAsJsonObject(name).get("id").getAsString()));
        return adv != null && player.getAdvancements().getOrStartProgress(adv).isDone();
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        player.level().getBiome(player.blockPosition()).unwrapKey().ifPresent(key -> {
            ResourceLocation b = key.location();
            if (!b.getNamespace().equals("lotr_port")) {
                return;
            }
            grant(player, "enterMiddleEarth");
            if (BIOMES.has(b.getPath())) {
                grant(player, BIOMES.get(b.getPath()).getAsString());
            }
        });
    }
}
