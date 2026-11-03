package com.porcupinestudios.lotrport.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A player's fast travel state, stored on the player as a data attachment. Immutable.
 *
 * @param dataVersion saved layout version; bump it and migrate in {@link #CODEC} when the layout changes
 * @param regions waypoint regions the player has unlocked by entering them
 * @param readyAt game time from which the player may travel again
 * @param trips how many times the player has fast travelled
 */
public record TravelData(int dataVersion, Set<String> regions, long readyAt, int trips) {
    public static final int DATA_VERSION = 1;
    public static final TravelData EMPTY = new TravelData(DATA_VERSION, Set.of(), 0L, 0);

    public static final Codec<TravelData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("dataVersion", DATA_VERSION).forGetter(TravelData::dataVersion),
            Codec.STRING.listOf().optionalFieldOf("regions", List.of())
                    .forGetter(d -> List.copyOf(new TreeSet<>(d.regions()))),
            Codec.LONG.optionalFieldOf("ready_at", 0L).forGetter(TravelData::readyAt),
            Codec.INT.optionalFieldOf("trips", 0).forGetter(TravelData::trips))
            .apply(i, (version, regions, readyAt, trips) -> new TravelData(DATA_VERSION, Set.copyOf(regions),
                    readyAt, trips)));

    public TravelData withRegion(String region) {
        Set<String> copy = new HashSet<>(regions);
        copy.add(region);
        return new TravelData(DATA_VERSION, Set.copyOf(copy), readyAt, trips);
    }

    public TravelData travelled(long nextReady) {
        return new TravelData(DATA_VERSION, regions, nextReady, trips + 1);
    }

    public TravelData ready() {
        return new TravelData(DATA_VERSION, regions, 0L, trips);
    }
}
