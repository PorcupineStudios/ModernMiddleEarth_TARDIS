package com.porcupinestudios.ringbearer.harness.draught;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The loaded {@code draught/} files, by kind ({@link DraughtKeys}: an entity type id, or another mod's name for the
 * creature). Server side (the pulling is server physics).
 */
public final class DraughtAnimals extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile Map<ResourceLocation, DraughtAnimal> byKind = Map.of();

    public DraughtAnimals() {
        super(new com.google.gson.Gson(), "draught");
    }

    @Nullable
    public static DraughtAnimal of(Entity entity) {
        for (ResourceLocation kind : DraughtKeys.of(entity)) {
            DraughtAnimal d = byKind.get(kind);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, DraughtAnimal> out = new HashMap<>();
        files.forEach((id, json) -> DraughtAnimal.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOG.error("draught {}: {}", id, err))
                .ifPresent(d -> {
                    for (ResourceLocation kind : d.entities()) {
                        out.put(kind, d);
                    }
                }));
        byKind = Map.copyOf(out);
        LOG.info("Loaded {} draught animal kinds", out.size());
    }
}
