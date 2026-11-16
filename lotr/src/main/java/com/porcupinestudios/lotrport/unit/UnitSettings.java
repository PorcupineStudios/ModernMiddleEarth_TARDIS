package com.porcupinestudios.lotrport.unit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Hired units and colony soldiers, {@code data/<ns>/units/settings.json} (reloads).
 *
 * @param wage coins a day a colony pays each stationed soldier
 * @param desertDays unpaid colony days before a soldier leaves
 * @param squadNameLength longest squad name
 */
public record UnitSettings(int wage, int desertDays, int squadNameLength) {
    public static final UnitSettings DEFAULT = new UnitSettings(2, 3, 24);
    public static final Codec<UnitSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("wage", DEFAULT.wage()).forGetter(UnitSettings::wage),
            Codec.INT.optionalFieldOf("desert_days", DEFAULT.desertDays()).forGetter(UnitSettings::desertDays),
            Codec.INT.optionalFieldOf("squad_name_length", DEFAULT.squadNameLength())
                    .forGetter(UnitSettings::squadNameLength))
            .apply(i, UnitSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<UnitSettings> LOADER = new CodecJsonLoader<>("units", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static UnitSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
