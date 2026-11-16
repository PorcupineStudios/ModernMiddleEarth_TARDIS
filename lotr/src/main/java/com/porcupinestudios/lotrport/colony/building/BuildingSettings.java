package com.porcupinestudios.lotrport.colony.building;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * How rooms are measured and buildings kept, {@code data/<ns>/colony_rooms/settings.json} (reloads). RingRealms'
 * numbers.
 *
 * @param maxRoof every open block needs something solid within this many blocks above it
 * @param maxFloor most floor blocks a building may have, over all its storeys
 * @param volumePerFloor the fill gives up (the room is open to the outside) past maxFloor x this many blocks
 * @param storeyFloor a level counts as a storey with at least this many floor blocks
 * @param reach farthest a building's door may be from the Hall, in blocks
 * @param recheckTicks a building is measured again this often while its colony is in the world
 * @param recheckPerPulse most buildings measured again in one colony pulse
 * @param qualityBase quality every building starts with (0-100)
 * @param qualityPerFloor quality per floor block, up to qualityCap
 * @param qualityPerLight quality per light, up to qualityCap
 * @param qualityPerFurnishing quality per chest or table, up to qualityCap
 * @param qualityCap most quality one of the three parts above gives
 */
public record BuildingSettings(int maxRoof, int maxFloor, int volumePerFloor, int storeyFloor, int reach,
        int recheckTicks, int recheckPerPulse, int qualityBase, double qualityPerFloor, int qualityPerLight,
        int qualityPerFurnishing, int qualityCap) {
    public static final BuildingSettings DEFAULT = new BuildingSettings(30, 500, 12, 4, 160, 6000, 2, 20, 0.5, 5, 4,
            20);
    public static final Codec<BuildingSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("max_roof", DEFAULT.maxRoof()).forGetter(BuildingSettings::maxRoof),
            Codec.INT.optionalFieldOf("max_floor", DEFAULT.maxFloor()).forGetter(BuildingSettings::maxFloor),
            Codec.INT.optionalFieldOf("volume_per_floor", DEFAULT.volumePerFloor())
                    .forGetter(BuildingSettings::volumePerFloor),
            Codec.INT.optionalFieldOf("storey_floor", DEFAULT.storeyFloor()).forGetter(BuildingSettings::storeyFloor),
            Codec.INT.optionalFieldOf("reach", DEFAULT.reach()).forGetter(BuildingSettings::reach),
            Codec.INT.optionalFieldOf("recheck_ticks", DEFAULT.recheckTicks()).forGetter(BuildingSettings::recheckTicks),
            Codec.INT.optionalFieldOf("recheck_per_pulse", DEFAULT.recheckPerPulse())
                    .forGetter(BuildingSettings::recheckPerPulse),
            Codec.INT.optionalFieldOf("quality_base", DEFAULT.qualityBase()).forGetter(BuildingSettings::qualityBase),
            Codec.DOUBLE.optionalFieldOf("quality_per_floor", DEFAULT.qualityPerFloor())
                    .forGetter(BuildingSettings::qualityPerFloor),
            Codec.INT.optionalFieldOf("quality_per_light", DEFAULT.qualityPerLight())
                    .forGetter(BuildingSettings::qualityPerLight),
            Codec.INT.optionalFieldOf("quality_per_furnishing", DEFAULT.qualityPerFurnishing())
                    .forGetter(BuildingSettings::qualityPerFurnishing),
            Codec.INT.optionalFieldOf("quality_cap", DEFAULT.qualityCap()).forGetter(BuildingSettings::qualityCap))
            .apply(i, BuildingSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<BuildingSettings> LOADER = new CodecJsonLoader<>("colony_rooms", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static BuildingSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }

    /** RingRealms' quality: a base, plus floor, lights and furnishings, each capped. */
    public int quality(int floor, int lights, int furnishings) {
        int q = qualityBase + Math.min(qualityCap, (int) (floor * qualityPerFloor))
                + Math.min(qualityCap, lights * qualityPerLight) + Math.min(qualityCap, furnishings * qualityPerFurnishing);
        return Math.max(0, Math.min(100, q));
    }
}
