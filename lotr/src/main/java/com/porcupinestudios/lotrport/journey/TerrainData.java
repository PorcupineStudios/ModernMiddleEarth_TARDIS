package com.porcupinestudios.lotrport.journey;

import java.util.List;
import java.util.Map;

/**
 * How fast parties cross each kind of land ({@code journey/terrain.json}), against open grassland (1). A biome's speed
 * is its own entry if it has one, else the first pattern its id contains, else {@code fallback}; then rough ground
 * slows it further: divided by {@code 1 + hilliness / roughness} (the map's hilliness in blocks). Biomes in
 * {@code water} can't be crossed at all.
 *
 * @param biomes speed by biome id (namespace optional: "misty_mountains" is lotr_port's)
 * @param patterns a part of a biome id and its speed, tried in order ("_mountains" 0.2, "marsh" 0.45...)
 */
public record TerrainData(double fallback, Map<String, Double> biomes, List<Pattern> patterns, List<String> water,
        double roughness) {
    public record Pattern(String contains, double speed) {}

    public static final TerrainData DEFAULT = new TerrainData(1, Map.of(), List.of(), List.of("ocean", "lake"), 40);

    /** Speed of a biome by id, before roughness. */
    public double speedOf(String id) {
        String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
        if (water.contains(path) || water.contains(id)) {
            return 0;
        }
        Double own = biomes.containsKey(id) ? biomes.get(id) : biomes.get(path);
        if (own != null) {
            return own;
        }
        for (Pattern p : patterns) {
            if (path.contains(p.contains())) {
                return p.speed();
            }
        }
        return fallback;
    }
}
