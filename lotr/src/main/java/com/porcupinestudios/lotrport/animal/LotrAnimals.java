package com.porcupinestudios.lotrport.animal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.ContentData;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * LOTR's animals that modern Minecraft lacks (boars, deer, aurochs, elk, Shire ponies...), one entity type each, from
 * {@code lotr_port/content/animals.json} (read at startup: entity types register before datapacks load). Kinds:
 * {@code animal} ({@link LotrAnimal}), {@code mount} ({@link LotrMount}, rideable like a horse) and {@code pony}
 * ({@link ShirePony}, a smaller vanilla horse). They spawn from the biomes' spawner lists, on grass like vanilla's.
 */
public final class LotrAnimals {
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, LotrPort.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(LotrPort.MOD_ID);
    public static final Map<String, AnimalType> TYPES = new LinkedHashMap<>();
    public static final Map<String, DeferredHolder<EntityType<?>, EntityType<? extends Mob>>> ENTITY = new LinkedHashMap<>();
    /** The converted LOTR models by name (client side builds them). */
    public static final Map<String, JsonObject> MODELS = new LinkedHashMap<>();
    private static final Map<EntityType<?>, AnimalType> BY_ENTITY = new IdentityHashMap<>();
    private static final List<DeferredItem<Item>> EGGS = new java.util.ArrayList<>();
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
            LotrPort.MOD_ID);
    /** LOTR's spawn checks for the vanilla mobs that stand in for its own (rabbits): entity id -> rules. */
    private static final Map<String, List<JsonObject>> VANILLA_RULES = new LinkedHashMap<>();

    static {
        List<JsonObject> file = ContentData.read("animals.json");
        if (!file.isEmpty()) {
            for (JsonElement e : file.get(0).getAsJsonArray("types")) {
                AnimalType t = AnimalType.parse(e.getAsJsonObject());
                TYPES.put(t.id(), t);
            }
            for (Map.Entry<String, JsonElement> e : file.get(0).getAsJsonObject("models").entrySet()) {
                MODELS.put(e.getKey(), e.getValue().getAsJsonObject());
            }
            JsonObject root = file.get(0);
            if (root.has("sounds")) { // LOTR's own sound events (assets/lotr_port/sounds.json)
                root.getAsJsonArray("sounds").forEach(s -> SOUNDS.register(s.getAsString(),
                        () -> SoundEvent.createVariableRangeEvent(LotrPort.id(s.getAsString()))));
            }
            if (root.has("vanilla_rules")) {
                for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("vanilla_rules").entrySet()) {
                    List<JsonObject> rules = new java.util.ArrayList<>();
                    e.getValue().getAsJsonArray().forEach(r -> rules.add(r.getAsJsonObject()));
                    VANILLA_RULES.put(e.getKey(), rules);
                }
            }
        }
        for (AnimalType t : TYPES.values()) {
            DeferredHolder<EntityType<?>, EntityType<? extends Mob>> holder = ENTITIES.register(t.id(), () -> {
                // wargs and spiders come from the NPC spawner and despawn like monsters
                EntityType<? extends Mob> type = EntityType.Builder.<Mob>of((et, level) -> create(t, et, level),
                                t.factionMob() ? MobCategory.MONSTER : MobCategory.CREATURE)
                        .sized(t.width(), t.height()).clientTrackingRange(10).build(LotrPort.MOD_ID + ":" + t.id());
                BY_ENTITY.put(type, t);
                return type;
            });
            ENTITY.put(t.id(), holder);
            EGGS.add(ITEMS.register(t.id() + "_spawn_egg", () -> new DeferredSpawnEggItem(holder, t.eggColor(),
                    t.eggSpots(), new Item.Properties())));
        }
    }

    private LotrAnimals() {}

    @SuppressWarnings("unchecked")
    private static Mob create(AnimalType t, EntityType<Mob> et, net.minecraft.world.level.Level level) {
        return switch (t.kind()) {
            case "mount" -> new LotrMount((EntityType<? extends LotrMount>) (EntityType<?>) et, level);
            case "warg", "spider" -> new FactionMount((EntityType<? extends FactionMount>) (EntityType<?>) et, level);
            case "pony" -> new ShirePony((EntityType<? extends ShirePony>) (EntityType<?>) et, level);
            default -> new LotrAnimal((EntityType<? extends LotrAnimal>) (EntityType<?>) et, level);
        };
    }

    /** The LOTR animal an entity type plays. */
    public static AnimalType type(EntityType<?> type) {
        return BY_ENTITY.get(type);
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(LotrAnimals::attributes);
        modBus.addListener(LotrAnimals::placements);
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                EGGS.forEach(event::accept);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static void attributes(EntityAttributeCreationEvent event) {
        for (AnimalType t : TYPES.values()) {
            AttributeSupplier.Builder b = t.kind().equals("animal") ? Mob.createMobAttributes()
                    : AbstractHorse.createBaseHorseAttributes();
            if (!t.kind().equals("pony")) { // the pony keeps a vanilla horse's (randomized) stats
                b.add(Attributes.MAX_HEALTH, t.health()).add(Attributes.MOVEMENT_SPEED, t.speed());
            }
            b.add(Attributes.ATTACK_DAMAGE, Math.max(1.0, t.attack())).add(Attributes.FOLLOW_RANGE, 16.0);
            event.put((EntityType<? extends net.minecraft.world.entity.LivingEntity>) ENTITY.get(t.id()).get(), b.build());
        }
    }

    @SuppressWarnings("unchecked")
    private static void placements(RegisterSpawnPlacementsEvent event) {
        // vanilla's camel only comes from villages, so it has no rule; LOTR's camels spawn like its other animals
        event.register(EntityType.CAMEL, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Animal::checkAnimalSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        for (AnimalType t : TYPES.values()) {
            // vanilla's animal check (grass, light), then LOTR's own (near plants, below a height...)
            event.register((EntityType<Animal>) (EntityType<?>) ENTITY.get(t.id()).get(), SpawnPlacementTypes.ON_GROUND,
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, spawnType, pos, random) ->
                            Animal.checkAnimalSpawnRules(type, level, spawnType, pos, random)
                                    && SpawnRules.test(t.spawnRules(), type, level, pos, random),
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
        for (Map.Entry<String, List<JsonObject>> e : VANILLA_RULES.entrySet()) {
            EntityType<Mob> type = (EntityType<Mob>) BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(e.getKey()));
            event.register(type, null, null, // AND keeps vanilla's placement and heightmap
                    (et, level, spawnType, pos, random) -> spawnType != net.minecraft.world.entity.MobSpawnType.NATURAL
                            && spawnType != net.minecraft.world.entity.MobSpawnType.CHUNK_GENERATION
                            || SpawnRules.test(e.getValue(), et, level, pos, random),
                    RegisterSpawnPlacementsEvent.Operation.AND);
        }
    }
}
