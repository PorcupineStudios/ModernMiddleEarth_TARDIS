package com.porcupinestudios.ringbearer.harness.draught;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** The loaded {@code draught/} files, by entity type. Server side (the pulling is server physics). */
public final class DraughtAnimals extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private static volatile Map<EntityType<?>, DraughtAnimal> byType = Map.of();

    public DraughtAnimals() {
        super(new com.google.gson.Gson(), "draught");
    }

    @Nullable
    public static DraughtAnimal of(Entity entity) {
        return byType.get(entity.getType());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<EntityType<?>, DraughtAnimal> out = new HashMap<>();
        files.forEach((id, json) -> DraughtAnimal.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOG.error("draught {}: {}", id, err))
                .ifPresent(d -> {
                    for (ResourceLocation type : d.entities()) {
                        BuiltInRegistries.ENTITY_TYPE.getOptional(type).ifPresent(t -> out.put(t, d));
                    }
                }));
        byType = Map.copyOf(out);
        LOG.info("Loaded {} draught animal types", out.size());
    }
}
