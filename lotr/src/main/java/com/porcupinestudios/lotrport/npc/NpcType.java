package com.porcupinestudios.lotrport.npc;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A kind of NPC from data: {@code data/<namespace>/npc/<name>.json}, reloaded with {@code /reload}. Its name is the
 * lang key {@code npc.<namespace>.<name>}. One entity ({@code lotr_port:npc}) plays every type.
 *
 * @param faction who it fights for; kills of it move alignment with that faction
 * @param skins textures it picks one of when it spawns (humanoid skins)
 * @param skinHeight 32 for classic 64x32 skins, 64 for 64x64 ones
 * @param scale size against a player (hobbits and dwarves are smaller)
 * @param health max health
 * @param speed movement speed attribute
 * @param attack melee damage
 * @param gear item ids by slot (mainhand, offhand, head, chest, legs, feet); a list means one picked at random
 * @param names name bank ({@code npc_names/<bank>.json}) it takes a first name from; empty means no name
 * @param hostile whether it attacks players with negative alignment with its faction
 * @param buy trade pool of what it sells to players
 * @param sell trade pool of what it buys from players
 * @param units unit pool of the soldiers and workers it hires out
 * @param tradeAlignment alignment a player needs with its faction to trade or hire
 * @param quests quest pool ({@code quest_pool/<pool>.json}) it gives mini-quests from
 * @param persona how it looks and talks: its body model and speech bank
 */
public record NpcType(Optional<ResourceLocation> faction, List<ResourceLocation> skins, int skinHeight, float scale,
        double health, double speed, double attack, Map<String, List<ResourceLocation>> gear, Optional<ResourceLocation> names,
        boolean hostile, Optional<ResourceLocation> buy, Optional<ResourceLocation> sell,
        Optional<ResourceLocation> units, float tradeAlignment, Optional<ResourceLocation> quests, Persona persona) {

    /** One id, or a list of ids. */
    private static final Codec<List<ResourceLocation>> ONE_OR_MORE =
            Codec.either(ResourceLocation.CODEC, ResourceLocation.CODEC.listOf()).xmap(
                    e -> e.map(List::of, l -> l),
                    l -> l.size() == 1 ? Either.left(l.get(0)) : Either.right(l));

    public static final Codec<NpcType> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(NpcType::faction),
            ResourceLocation.CODEC.listOf().optionalFieldOf("skins", List.of()).forGetter(NpcType::skins),
            Codec.INT.optionalFieldOf("skin_height", 32).forGetter(NpcType::skinHeight),
            Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(NpcType::scale),
            Codec.DOUBLE.optionalFieldOf("health", 20.0).forGetter(NpcType::health),
            Codec.DOUBLE.optionalFieldOf("speed", 0.25).forGetter(NpcType::speed),
            Codec.DOUBLE.optionalFieldOf("attack", 2.0).forGetter(NpcType::attack),
            Codec.unboundedMap(Codec.STRING, ONE_OR_MORE)
                    .optionalFieldOf("gear", Map.of())
                    .forGetter(NpcType::gear),
            ResourceLocation.CODEC.optionalFieldOf("names").forGetter(NpcType::names),
            Codec.BOOL.optionalFieldOf("hostile", true).forGetter(NpcType::hostile),
            ResourceLocation.CODEC.optionalFieldOf("buy").forGetter(NpcType::buy),
            ResourceLocation.CODEC.optionalFieldOf("sell").forGetter(NpcType::sell),
            ResourceLocation.CODEC.optionalFieldOf("units").forGetter(NpcType::units),
            Codec.FLOAT.optionalFieldOf("trade_alignment", 0f).forGetter(NpcType::tradeAlignment),
            ResourceLocation.CODEC.optionalFieldOf("quests").forGetter(NpcType::quests),
            Persona.MAP_CODEC.forGetter(NpcType::persona))
            .apply(i, NpcType::new));

    /**
     * How an NPC looks and talks, kept as flat keys in the type's JSON (a codec group holds at most 16 fields).
     *
     * @param model the body the client draws: human, hobbit, dwarf, elf or orc (hair layer, ears, feet)
     * @param speech speech bank folder ({@code speech/<bank>/<mood>.json}) it talks from when right-clicked
     * @param sounds LOTR's voice for each event, see {@link #sound}
     * @param width creature hitbox width (0 = the person default, sized by {@code scale})
     * @param height creature hitbox height
     * @param renderScale how big a creature model is drawn (LOTR's renderer scale)
     * @param behaviours LOTR creature behaviours this type has ({@code npc/behaviour/Behaviours})
     * @param lotrClasses its LOTR entity class chain (simple names), which LOTR's animations test
     * @param behaviourData the behaviours' numbers for this type (troll scale, sun immunity, spider venom...)
     */
    public record Persona(String model, Optional<ResourceLocation> speech, Map<String, ResourceLocation> sounds,
            float width, float height, float renderScale, List<String> behaviours, List<String> lotrClasses,
            Map<String, Float> behaviourData) {
        public static final MapCodec<Persona> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("model", "human").forGetter(Persona::model),
                ResourceLocation.CODEC.optionalFieldOf("speech").forGetter(Persona::speech),
                Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("sounds", Map.of())
                        .forGetter(Persona::sounds),
                Codec.FLOAT.optionalFieldOf("width", 0f).forGetter(Persona::width),
                Codec.FLOAT.optionalFieldOf("height", 0f).forGetter(Persona::height),
                Codec.FLOAT.optionalFieldOf("render_scale", 1f).forGetter(Persona::renderScale),
                Codec.STRING.listOf().optionalFieldOf("behaviours", List.of()).forGetter(Persona::behaviours),
                Codec.STRING.listOf().optionalFieldOf("lotr_classes", List.of()).forGetter(Persona::lotrClasses),
                Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf("behaviour_data", Map.of())
                        .forGetter(Persona::behaviourData))
                .apply(i, Persona::new));
    }

    /**
     * A creature body: {@code model} is a creature model id ({@code lotr_port:warg},
     * {@code assets/<ns>/npc_model/<path>.json}) rather than a humanoid kind. Creatures have their own hitbox
     * ({@code width} x {@code height}, LOTR's) and are drawn at {@code render_scale}.
     */
    public boolean creature() {
        return persona.model().contains(":");
    }

    /**
     * The sound this type makes for an event: {@code ambient}, {@code hurt}, {@code death}, {@code attack} (each
     * swing that hits) or {@code kill}. From LOTR's entity classes (orcs, dwarves, elves, half-trolls...); empty
     * means the vanilla default.
     */
    public Optional<ResourceLocation> sound(String event) {
        return Optional.ofNullable(persona.sounds().get(event));
    }

    public String model() {
        return persona.model();
    }

    public Optional<ResourceLocation> speech() {
        return persona.speech();
    }

    public boolean trader() {
        return buy.isPresent() || sell.isPresent();
    }

    public boolean captain() {
        return units.isPresent();
    }
}
