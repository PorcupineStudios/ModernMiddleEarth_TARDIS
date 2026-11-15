package com.porcupinestudios.lotrport.mount;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LOTR's mounts (missing-features task 27): horses, Shire ponies, zebras, wild boars, camels, elk, rhinos and
 * giraffes, all {@link LotrHorse}s of one breed each. A breed's entity type, size and looks are
 * {@code content/mounts.json} (read at startup); its numbers are {@code data/<ns>/mount/<breed>.json} and reload.
 * Both come from {@code converter/mounts.py}.
 */
public final class Mounts {
    /** A breed's looks: what the client draws and how big the entity is. */
    public record Breed(String id, float width, float height, String model, String saddleModel,
            List<ResourceLocation> textures, List<ResourceLocation> markings, ResourceLocation saddle,
            List<ResourceLocation> carpet, float scale, float seat) {
        public boolean oldHorse() {
            return model.endsWith(":old_horse");
        }
    }

    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);
    public static final Map<String, Breed> BREEDS = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<LotrHorse>>> TYPE_BY_BREED =
            new LinkedHashMap<>();
    /** Vanilla horse armour item -> its 1.7.10 texture (the old horse model's layout). */
    public static final Map<ResourceLocation, ResourceLocation> VANILLA_ARMOUR = new LinkedHashMap<>();
    private static final CodecJsonLoader<JsonElement> STATS = new CodecJsonLoader<>("mount", ExtraCodecs.JSON);
    private static final JsonObject NO_STATS = new JsonObject();

    private Mounts() {}

    public static void register(IEventBus modBus) {
        JsonObject file = ContentData.readObject("mounts.json");
        if (file.has("breeds")) {
            for (JsonElement e : file.getAsJsonArray("breeds")) {
                JsonObject b = e.getAsJsonObject();
                if (!b.has("model") || b.get("model").isJsonNull()) {
                    continue;
                }
                Breed breed = new Breed(b.get("id").getAsString(), ContentData.num(b, "width", 1.4f),
                        ContentData.num(b, "height", 1.6f), b.get("model").getAsString(),
                        ContentData.str(b, "saddle_model", null), ids(b, "textures"), ids(b, "markings"),
                        b.has("saddle") && !b.get("saddle").isJsonNull()
                                ? ResourceLocation.parse(b.get("saddle").getAsString()) : null,
                        ids(b, "carpet"), ContentData.num(b, "scale", 1f),
                        ContentData.num(b, "seat", ContentData.num(b, "height", 1.6f) * 0.9f));
                BREEDS.put(breed.id(), breed);
                TYPE_BY_BREED.put(breed.id(), TYPES.register(breed.id(),
                        () -> EntityType.Builder.<LotrHorse>of(LotrHorse::new, MobCategory.CREATURE)
                                .sized(breed.width(), breed.height()).eyeHeight(breed.height() * 0.95f)
                                .passengerAttachments(breed.seat())
                                .clientTrackingRange(10).build(LotrPort.MOD_ID + ":" + breed.id())));
            }
        }
        if (file.has("vanilla_armour")) {
            file.getAsJsonObject("vanilla_armour").entrySet().forEach(e -> VANILLA_ARMOUR.put(
                    ResourceLocation.parse(e.getKey()), ResourceLocation.parse(e.getValue().getAsString())));
        }
        TYPES.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> TYPE_BY_BREED.values()
                .forEach(t -> event.put(t.get(), LotrHorse.createAttributes().build())));
        modBus.addListener((RegisterSpawnPlacementsEvent event) -> TYPE_BY_BREED.values()
                .forEach(t -> event.register(t.get(), SpawnPlacementTypes.ON_GROUND,
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules,
                        RegisterSpawnPlacementsEvent.Operation.REPLACE)));
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(STATS));
    }

    private static List<ResourceLocation> ids(JsonObject o, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (o.has(key) && o.get(key).isJsonArray()) {
            o.getAsJsonArray(key).forEach(e -> out.add(ResourceLocation.parse(e.getAsString())));
        }
        return out;
    }

    public static Breed breed(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        Breed b = BREEDS.get(id.getPath());
        return b != null ? b : BREEDS.values().iterator().next();
    }

    /** The breed's numbers ({@code mount/<breed>.json}); empty until data loads. */
    public static JsonObject stats(String breed) {
        JsonElement e = STATS.entries().get(LotrPort.id(breed));
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : NO_STATS;
    }
}
