package com.porcupinestudios.lotrport.drink;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** The keg's numbers ({@code data/<ns>/drinking/settings.json}); what drinking does is the drink item's own data. */
public final class DrinkData {
    /**
     * LOTR's strength factors (weak to potent; their count is the strengths a keg brews through), and the keg's sizes:
     * LOTR's barrel held 16 mugs and three buckets of water, and a drink got one strength stronger every 12000 ticks.
     */
    public record Settings(List<Float> alcoholStrength, int servingMb, int kegServings, int kegWaterMb,
            int brewTicks) {
        public static final Settings DEFAULT = new Settings(List.of(0.25f, 0.5f, 1f, 2f, 3f), 250, 16, 3000, 12000);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.listOf().fieldOf("alcohol_strength").forGetter(Settings::alcoholStrength),
                Codec.INT.optionalFieldOf("serving_mb", 250).forGetter(Settings::servingMb),
                Codec.INT.optionalFieldOf("keg_servings", 16).forGetter(Settings::kegServings),
                Codec.INT.optionalFieldOf("keg_water_mb", 3000).forGetter(Settings::kegWaterMb),
                Codec.INT.optionalFieldOf("brew_ticks", 12000).forGetter(Settings::brewTicks))
                .apply(i, Settings::new));

        /** The highest strength (potent). */
        public int maxStrength() {
            return alcoholStrength.size() - 1;
        }
    }

    private static final CodecJsonLoader<Settings> SETTINGS = new CodecJsonLoader<>("drinking", Settings.CODEC);

    private DrinkData() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(SETTINGS));
    }

    /** Server data; a client gets the defaults (LOTR's numbers), which only tooltips and item fluid handlers read. */
    public static Settings settings() {
        return SETTINGS.entries().getOrDefault(LotrPort.id("settings"), Settings.DEFAULT);
    }
}
