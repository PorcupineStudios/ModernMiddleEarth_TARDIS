package com.porcupinestudios.lotrport.journey;

import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.util.List;

/**
 * The land as Middle-earth's map has it, for routes: each biome's speed from {@code journey/terrain.json} (worked out
 * once per biome), slowed by the map's hilliness; roads from the road network; rivers where the map puts its river
 * biome; marked bridges from {@link Bridges}. Nothing here loads a chunk, so it can run off the server thread on a
 * snapshot of the bridges.
 */
public final class MapTerrain implements TerrainView {
    private final MiddleEarthMap map;
    private final Roads roads;
    private final double[] speeds;
    private final int river;
    private final double roughness;
    private final List<Bridges.Bridge> bridges;

    public MapTerrain(MiddleEarthMap map, Roads roads, TerrainData data, List<Bridges.Bridge> bridges) {
        this.map = map;
        this.roads = roads;
        this.river = map.riverBiome();
        this.roughness = Math.max(1, data.roughness());
        this.bridges = List.copyOf(bridges);
        List<MiddleEarthMap.Entry> biomes = map.biomes();
        this.speeds = new double[biomes.size()];
        for (int i = 0; i < biomes.size(); i++) {
            speeds[i] = data.speedOf(biomes.get(i).id());
        }
    }

    @Override
    public double speed(int x, int z) {
        int b = map.biomeIndexAt(x, z);
        double v = b >= 0 && b < speeds.length ? speeds[b] : 0;
        if (v <= 0 || b == river) {
            return b == river ? 1 : v;
        }
        return v / (1 + Math.max(0, map.variationAt(x, z)) / roughness);
    }

    @Override
    public boolean road(int x, int z) {
        return roads != null && roads.distance(x, z) <= roads.width() + 1;
    }

    @Override
    public boolean river(int x, int z) {
        return river >= 0 && map.biomeIndexAt(x, z) == river;
    }

    @Override
    public List<int[]> bridgeEnds() {
        return bridges.stream().map(b -> new int[] {b.a().getX(), b.a().getZ(), b.b().getX(), b.b().getZ()})
                .toList();
    }

    @Override
    public boolean bridge(int x, int z) {
        for (Bridges.Bridge b : bridges) {
            if (b.covers(x, z)) {
                return true;
            }
        }
        return false;
    }
}
