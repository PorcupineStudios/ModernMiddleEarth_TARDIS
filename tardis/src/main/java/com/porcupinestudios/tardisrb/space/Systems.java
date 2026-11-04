package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.progression.SystemFile;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/**
 * The solar systems of this server ({@code tardis/system} files whose dimension exists and that have at least one of
 * this world's planets). A planet belongs to the first system (by id) that names it. Worked out again on reload.
 */
public final class Systems {
    private static List<Planet> builtFrom;
    private static List<StarSystem> systems = List.of();

    private Systems() {}

    public static synchronized List<StarSystem> all(MinecraftServer server) {
        List<Planet> planets = Planets.all(server);
        if (planets != builtFrom) {
            systems = resolve(TardisContent.get().systems(), planets, d -> server.getLevel(d) != null);
            builtFrom = planets;
        }
        return systems;
    }

    /** Resolves the files in id order, each planet in one system only. */
    public static List<StarSystem> resolve(Map<ResourceLocation, SystemFile> files, List<Planet> planets,
            java.util.function.Predicate<ResourceKey<Level>> exists) {
        List<StarSystem> out = new ArrayList<>();
        List<ResourceLocation> taken = new ArrayList<>();
        files.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (!exists.test(e.getValue().dimension())) {
                return;
            }
            List<Planet> free = planets.stream().filter(p -> !taken.contains(p.id())).toList();
            StarSystem s = StarSystem.resolve(e.getKey(), e.getValue(), free);
            if (s != null) {
                s.bodies().forEach(b -> taken.add(b.planet().id()));
                out.add(s);
            }
        });
        return List.copyOf(out);
    }

    /** The system a planet is in, with its body. */
    public static Optional<StarSystem> ofPlanet(MinecraftServer server, ResourceLocation planet) {
        return all(server).stream().filter(s -> s.body(planet) != null).findFirst();
    }

    /** The systems in a dimension. */
    public static List<StarSystem> in(MinecraftServer server, ResourceKey<Level> dimension) {
        return all(server).stream().filter(s -> s.dimension().equals(dimension)).toList();
    }

    public static boolean isSystem(MinecraftServer server, ResourceKey<Level> dimension) {
        return all(server).stream().anyMatch(s -> s.dimension().equals(dimension));
    }

    /** The nearest system to a point of a system dimension (by its sun), if any. */
    public static Optional<StarSystem> near(List<StarSystem> systems, ResourceKey<Level> dimension,
            net.minecraft.world.phys.Vec3 pos) {
        StarSystem best = null;
        double bestD = Double.MAX_VALUE;
        for (StarSystem s : systems) {
            if (s.dimension().equals(dimension)) {
                double d = pos.distanceToSqr(s.sunCenter());
                if (d < bestD) {
                    best = s;
                    bestD = d;
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
