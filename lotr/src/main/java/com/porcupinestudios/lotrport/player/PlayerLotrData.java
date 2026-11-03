package com.porcupinestudios.lotrport.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A player's LOTR state, stored on the player as a data attachment (replaces {@code LOTRPlayerData}). Immutable: change
 * it through {@link com.porcupinestudios.lotrport.faction.Alignment}, which fires events and writes history.
 *
 * @param dataVersion saved layout version; bump it and migrate in {@link #CODEC} when the layout changes
 * @param alignment alignment per faction id; a faction missing from the map is 0
 * @param pledge the faction the player is pledged to, if any
 */
public record PlayerLotrData(int dataVersion, Map<ResourceLocation, Float> alignment, Optional<ResourceLocation> pledge) {
    public static final int DATA_VERSION = 1;
    public static final PlayerLotrData EMPTY = new PlayerLotrData(DATA_VERSION, Map.of(), Optional.empty());

    public static final Codec<PlayerLotrData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("dataVersion", DATA_VERSION).forGetter(PlayerLotrData::dataVersion),
            Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT)
                    .optionalFieldOf("alignment", Map.of()).forGetter(PlayerLotrData::alignment),
            ResourceLocation.CODEC.optionalFieldOf("pledge").forGetter(PlayerLotrData::pledge))
            .apply(i, (version, alignment, pledge) -> new PlayerLotrData(DATA_VERSION, Map.copyOf(alignment), pledge)));

    public float alignment(ResourceLocation faction) {
        return alignment.getOrDefault(faction, 0f);
    }

    public PlayerLotrData withAlignment(ResourceLocation faction, float value) {
        Map<ResourceLocation, Float> copy = new HashMap<>(alignment);
        if (value == 0f) {
            copy.remove(faction);
        } else {
            copy.put(faction, value);
        }
        return new PlayerLotrData(DATA_VERSION, Map.copyOf(copy), pledge);
    }

    public PlayerLotrData withPledge(Optional<ResourceLocation> faction) {
        return new PlayerLotrData(DATA_VERSION, alignment, faction);
    }
}
