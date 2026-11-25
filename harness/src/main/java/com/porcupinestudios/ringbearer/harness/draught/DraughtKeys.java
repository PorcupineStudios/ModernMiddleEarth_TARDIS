package com.porcupinestudios.ringbearer.harness.draught;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModProcessEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Which kind of animal an entity is, for the draught and harness-fit files: its entity type id, unless another mod
 * says better. A mod whose creatures share one entity type (the LOTR port's NPCs: wargs, spiders...) sends, without
 * depending on this mod,
 * {@code InterModComms.sendTo("ringbearer_harness", "draught_key", () -> (Function<Entity, ResourceLocation>) e -> ...)}
 * from its {@code InterModEnqueueEvent}: the function returns the kind's id (e.g. {@code lotr_port:angmar_warg}) or
 * null for entities it doesn't name.
 */
public final class DraughtKeys {
    public static final String IMC_METHOD = "draught_key";
    private static final Logger LOG = LogUtils.getLogger();
    private static final List<Function<Entity, ResourceLocation>> NAMERS = new CopyOnWriteArrayList<>();

    private DraughtKeys() {}

    @SuppressWarnings("unchecked")
    public static void onProcess(InterModProcessEvent event) {
        event.getIMCStream(IMC_METHOD::equals).forEach((InterModComms.IMCMessage m) -> {
            if (m.messageSupplier().get() instanceof Function<?, ?> f) {
                NAMERS.add((Function<Entity, ResourceLocation>) f);
                LOG.info("Draught kinds named by {}", m.senderModId());
            }
        });
    }

    /** The other mods' names for this entity, then its entity type id (the files list either). */
    public static List<ResourceLocation> of(Entity e) {
        ResourceLocation named = named(e);
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return named == null ? List.of(type) : List.of(named, type);
    }

    @Nullable
    private static ResourceLocation named(Entity e) {
        for (Function<Entity, ResourceLocation> f : NAMERS) {
            try {
                ResourceLocation id = f.apply(e);
                if (id != null) {
                    return id;
                }
            } catch (RuntimeException ex) {
                LOG.warn("draught_key namer failed on {}", e, ex);
            }
        }
        return null;
    }
}
