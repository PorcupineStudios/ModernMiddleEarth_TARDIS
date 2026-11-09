package com.porcupinestudios.lotrport.animal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * One LOTR mob from {@code lotr_port/content/animals.json} ({@code converter/animals.py}, from the exporter's
 * creatures.json): kind (animal, mount, pony, warg or spider), size, LOTR's stats (health and attack may be a range),
 * whether it fights back, its food, skins, saddle texture, model, sounds, the mount armor it wears, LOTR's spawn
 * rules, and for wargs and spiders their faction, the alignment riding takes, their looks and the riders they bring.
 */
public record AnimalType(String id, String kind, float width, float height, double health, double healthMax,
        double speed, double attack, double attackMax, boolean fights, float scale, int eggColor, int eggSpots,
        String food, List<ResourceLocation> skins, ResourceLocation saddle, List<String> maleParts,
        List<String> adultParts, String model, Sounds sounds, @Nullable String armor, List<JsonObject> spawnRules,
        @Nullable ResourceLocation faction, float alignment, List<Look> looks, int riderChance, List<Rider> riders) {

    /** LOTR's sounds for the mob (null: the vanilla default); volume and ticks between ambient sounds. */
    public record Sounds(@Nullable ResourceLocation ambient, @Nullable ResourceLocation hurt,
            @Nullable ResourceLocation death, @Nullable ResourceLocation step, @Nullable ResourceLocation attack,
            float volume, int interval) {
        static Sounds parse(@Nullable JsonObject o) {
            if (o == null) {
                return new Sounds(null, null, null, null, null, 1f, 80);
            }
            return new Sounds(loc(o, "ambient"), loc(o, "hurt"), loc(o, "death"), loc(o, "step"), loc(o, "attack"),
                    o.has("volume") ? o.get("volume").getAsFloat() : 1f,
                    o.has("interval") ? o.get("interval").getAsInt() : 80);
        }

        @Nullable
        public static SoundEvent event(@Nullable ResourceLocation id) {
            return id == null ? null : BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
        }
    }

    /** One of LOTR's random variants (a warg colour; a spider's kind and size) and how often it came up. */
    public record Look(int weight, int skin, float scale, String venom, boolean rideable, double health, double speed,
            double attack) {
        static Look parse(JsonObject o) {
            return new Look(o.get("weight").getAsInt(), o.has("skin") ? o.get("skin").getAsInt() : 0,
                    o.has("scale") ? o.get("scale").getAsFloat() : 1f,
                    o.has("venom") ? o.get("venom").getAsString() : "none",
                    !o.has("rideable") || o.get("rideable").getAsBoolean(), num(o, "health"), num(o, "speed"),
                    num(o, "attack"));
        }
    }

    /** An NPC type that may ride this mob when it spawns, and its weight. */
    public record Rider(ResourceLocation npc, int weight) {}

    static AnimalType parse(JsonObject o) {
        JsonArray egg = o.getAsJsonArray("egg");
        double health = o.get("health").getAsDouble();
        double attack = o.get("attack").getAsDouble();
        List<JsonObject> rules = new ArrayList<>();
        if (o.has("spawn_rules")) {
            o.getAsJsonArray("spawn_rules").forEach(r -> rules.add(r.getAsJsonObject()));
        }
        List<Look> looks = new ArrayList<>();
        if (o.has("looks")) {
            o.getAsJsonArray("looks").forEach(l -> looks.add(Look.parse(l.getAsJsonObject())));
        }
        List<Rider> riders = new ArrayList<>();
        if (o.has("riders")) {
            o.getAsJsonArray("riders").forEach(r -> riders.add(new Rider(
                    ResourceLocation.parse(r.getAsJsonObject().get("npc").getAsString()),
                    r.getAsJsonObject().get("weight").getAsInt())));
        }
        return new AnimalType(o.get("id").getAsString(), o.get("kind").getAsString(), o.get("width").getAsFloat(),
                o.get("height").getAsFloat(), health, o.has("health_max") ? o.get("health_max").getAsDouble() : health,
                o.get("speed").getAsDouble(), attack,
                o.has("attack_max") ? o.get("attack_max").getAsDouble() : attack, o.get("fights").getAsBoolean(),
                o.get("scale").getAsFloat(), egg.get(0).getAsInt(), egg.get(1).getAsInt(),
                o.has("food") ? o.get("food").getAsString() : null, locations(o.get("skins")),
                o.has("saddle") ? ResourceLocation.parse(o.get("saddle").getAsString()) : null,
                strings(o.get("male_parts")), strings(o.get("adult_parts")),
                o.has("model") ? o.get("model").getAsString() : null,
                Sounds.parse(o.has("sounds") ? o.getAsJsonObject("sounds") : null),
                o.has("armor") ? o.get("armor").getAsString() : null, rules, loc(o, "faction"),
                o.has("alignment") ? o.get("alignment").getAsFloat() : 0f, looks,
                o.has("rider_chance") ? o.get("rider_chance").getAsInt() : 0, riders);
    }

    /** Wargs and spiders: faction mobs, ridden only with enough alignment. */
    public boolean factionMob() {
        return kind.equals("warg") || kind.equals("spider");
    }

    /** A look at random, by LOTR's frequencies (none: the plain mob). */
    @Nullable
    public Look rollLook(RandomSource random) {
        int total = looks.stream().mapToInt(Look::weight).sum();
        int roll = total <= 0 ? 0 : random.nextInt(total);
        for (Look l : looks) {
            roll -= l.weight();
            if (roll < 0) {
                return l;
            }
        }
        return null;
    }

    public boolean isFood(ItemStack stack) {
        if (food == null) {
            return false;
        }
        return food.startsWith("#")
                ? stack.is(TagKey.create(Registries.ITEM, ResourceLocation.parse(food.substring(1))))
                : stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.parse(food)));
    }

    private static double num(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsDouble() : Double.NaN;
    }

    @Nullable
    private static ResourceLocation loc(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? ResourceLocation.parse(o.get(key).getAsString()) : null;
    }

    private static List<String> strings(JsonElement e) {
        List<String> out = new ArrayList<>();
        if (e != null) {
            e.getAsJsonArray().forEach(x -> out.add(x.getAsString()));
        }
        return out;
    }

    private static List<ResourceLocation> locations(JsonElement e) {
        return strings(e).stream().map(ResourceLocation::parse).toList();
    }
}
