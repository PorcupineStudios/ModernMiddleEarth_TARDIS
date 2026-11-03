package com.porcupinestudios.lotrport.travel;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * A fast travel waypoint: {@code data/<namespace>/waypoint/<name>.json}, reloaded with /reload. Its name is the lang
 * key {@code waypoint.<namespace>.<name>}.
 *
 * @param x block x in Middle-earth
 * @param z block z in Middle-earth
 * @param mapX pixel x on the map image (for the map screen)
 * @param mapZ pixel y on the map image
 * @param region waypoint region; a player can travel to it once they have entered a biome of that region
 * @param faction travelling there needs non-negative alignment with this faction
 * @param hidden not shown on the map until its region is unlocked
 */
public record Waypoint(int x, int z, double mapX, double mapZ, String region, Optional<ResourceLocation> faction,
        boolean hidden) {
    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(Waypoint::x),
            Codec.INT.fieldOf("z").forGetter(Waypoint::z),
            Codec.DOUBLE.optionalFieldOf("map_x", 0.0).forGetter(Waypoint::mapX),
            Codec.DOUBLE.optionalFieldOf("map_z", 0.0).forGetter(Waypoint::mapZ),
            Codec.STRING.fieldOf("region").forGetter(Waypoint::region),
            ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Waypoint::faction),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(Waypoint::hidden))
            .apply(i, Waypoint::new));
}
