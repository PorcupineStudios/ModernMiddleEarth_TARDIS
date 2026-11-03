package com.porcupinestudios.lotrport.quest;

import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** The {@code quest_pool} data folder; reloads with /reload. */
public final class QuestData {
    private static final CodecJsonLoader<QuestPool> POOLS = new CodecJsonLoader<>("quest_pool", QuestPool.CODEC);

    private QuestData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(POOLS));
    }

    public static Optional<QuestPool> pool(ResourceLocation id) {
        return Optional.ofNullable(POOLS.entries().get(id));
    }

    public static Map<ResourceLocation, QuestPool> pools() {
        return POOLS.entries();
    }

    public static List<String> loadProblems() {
        return POOLS.lastErrors();
    }
}
