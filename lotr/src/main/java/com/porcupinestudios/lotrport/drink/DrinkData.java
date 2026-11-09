package com.porcupinestudios.lotrport.drink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * What drinking a LOTR drink does: {@code data/<ns>/drink/<item>.json}, LOTRItemMug's stats read by
 * {@code converter/drinks.py}; reloads with /reload. Food and effects scale with a brewable drink's strength
 * ({@link Settings}).
 *
 * @param alcohol LOTR's alcoholicity: the chance of nausea at strength factor 1
 * @param faction Morgul draught: the effects only for drinkers with this faction's alignment above 0, others get
 *                {@code otherwise}
 * @param explode termite tequila: 1 in {@code chance} drinks explode at the drinker
 */
public record DrinkData(float alcohol, int nutrition, float saturation, float damage, boolean cures,
        boolean curesHarmful, List<Effect> effects, Optional<FactionGate> faction, Optional<Explode> explode) {
    public static final DrinkData NONE = new DrinkData(0, 0, 0, 0, false, false, List.of(), Optional.empty(),
            Optional.empty());

    public record Effect(Holder<MobEffect> id, int duration, int amplifier) {
        public static final Codec<Effect> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("id").forGetter(Effect::id),
                Codec.INT.fieldOf("duration").forGetter(Effect::duration),
                Codec.INT.optionalFieldOf("amplifier", 0).forGetter(Effect::amplifier)).apply(i, Effect::new));
    }

    public record FactionGate(ResourceLocation id, List<Effect> otherwise) {
        public static final Codec<FactionGate> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(FactionGate::id),
                Effect.CODEC.listOf().optionalFieldOf("otherwise", List.of()).forGetter(FactionGate::otherwise))
                .apply(i, FactionGate::new));
    }

    public record Explode(int chance, float power) {
        public static final Codec<Explode> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("chance").forGetter(Explode::chance),
                Codec.FLOAT.fieldOf("power").forGetter(Explode::power)).apply(i, Explode::new));
    }

    public static final Codec<DrinkData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("alcohol", 0f).forGetter(DrinkData::alcohol),
            Codec.INT.optionalFieldOf("nutrition", 0).forGetter(DrinkData::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0f).forGetter(DrinkData::saturation),
            Codec.FLOAT.optionalFieldOf("damage", 0f).forGetter(DrinkData::damage),
            Codec.BOOL.optionalFieldOf("cures", false).forGetter(DrinkData::cures),
            Codec.BOOL.optionalFieldOf("cures_harmful", false).forGetter(DrinkData::curesHarmful),
            Effect.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(DrinkData::effects),
            FactionGate.CODEC.optionalFieldOf("faction").forGetter(DrinkData::faction),
            Explode.CODEC.optionalFieldOf("explode").forGetter(DrinkData::explode)).apply(i, DrinkData::new));

    /**
     * {@code data/<ns>/drinking/settings.json}: LOTR's strength factors (weak to potent) for alcohol and effects and
     * for food, and the keg's sizes (LOTR's barrel held 16 mugs and three buckets of water).
     */
    public record Settings(List<Float> alcoholStrength, List<Float> foodStrength, int servingMb, int kegServings,
            int kegWaterMb) {
        public static final Settings DEFAULT = new Settings(List.of(0.25f, 0.5f, 1f, 2f, 3f),
                List.of(0.5f, 0.75f, 1f, 1.25f, 1.5f), 250, 16, 3000);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.listOf().fieldOf("alcohol_strength").forGetter(Settings::alcoholStrength),
                Codec.FLOAT.listOf().fieldOf("food_strength").forGetter(Settings::foodStrength),
                Codec.INT.optionalFieldOf("serving_mb", 250).forGetter(Settings::servingMb),
                Codec.INT.optionalFieldOf("keg_servings", 16).forGetter(Settings::kegServings),
                Codec.INT.optionalFieldOf("keg_water_mb", 3000).forGetter(Settings::kegWaterMb))
                .apply(i, Settings::new));

        /** The highest strength (potent). */
        public int maxStrength() {
            return alcoholStrength.size() - 1;
        }
    }

    private static final CodecJsonLoader<DrinkData> DRINKS = new CodecJsonLoader<>("drink", CODEC);
    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("drinking", Settings.CODEC);

    static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(DRINKS);
            event.addListener(SETTINGS);
        });
    }

    public static DrinkData of(Item item) {
        return DRINKS.entries().getOrDefault(BuiltInRegistries.ITEM.getKey(item), NONE);
    }

    /** Server data; a client gets the defaults (LOTR's numbers), which only tooltips and item fluid handlers read. */
    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }
}
