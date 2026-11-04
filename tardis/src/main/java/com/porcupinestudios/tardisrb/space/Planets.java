package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.ringbearer.api.provider.Providers;
import com.porcupinestudios.tardisrb.progression.SkyLink;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * The planets of this server: every sky link whose dimension and space both exist, with its surface from a planet
 * provider when one answers (the LOTR port for Middle-earth). Worked out again when the data reloads.
 */
public final class Planets {
    private static Object builtFrom;
    private static MinecraftServer builtFor;
    /** How many levels the server had: levels are made one by one at start, so look again once they all exist. */
    private static int builtLevels;
    private static List<Planet> planets = List.of();

    private Planets() {}

    public static synchronized List<Planet> all(MinecraftServer server) {
        Object content = TardisContent.get();
        int levels = server.levelKeys().size();
        if (content != builtFrom || server != builtFor || levels != builtLevels) {
            List<Planet> list = new ArrayList<>();
            for (Map.Entry<ResourceLocation, SkyLink> e : TardisContent.get().skies().entrySet()) {
                SkyLink link = e.getValue();
                ServerLevel level = server.getLevel(link.dimension());
                if (level == null || server.getLevel(link.space()) == null) {
                    continue;
                }
                link = Providers.planet(level).map(link::withSurface).orElse(link);
                if (link.problem() == null) {
                    list.add(new Planet(e.getKey(), link));
                }
            }
            planets = List.copyOf(list);
            builtFrom = content;
            builtFor = server;
            builtLevels = levels;
        }
        return planets;
    }

    /** The planet a dimension is. */
    public static Optional<Planet> of(MinecraftServer server, ResourceKey<Level> dimension) {
        return all(server).stream().filter(p -> p.link().dimension().equals(dimension)).findFirst();
    }

    /** The planet under a point of a space dimension: the one whose layer it's in, else the nearest. */
    public static Optional<Planet> under(MinecraftServer server, ResourceKey<Level> space, double x, double z) {
        Planet best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Planet p : all(server)) {
            if (p.link().space().equals(space)) {
                double d = p.distanceToLayer(x, z);
                if (d < bestDistance) {
                    best = p;
                    bestDistance = d;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean isSpace(MinecraftServer server, ResourceKey<Level> dimension) {
        return all(server).stream().anyMatch(p -> p.link().space().equals(dimension));
    }

    /** The planets a player in this dimension needs to know about: its own, or all those in this space. */
    public static List<Planet> seenFrom(MinecraftServer server, ResourceKey<Level> dimension) {
        return all(server).stream().filter(p -> p.link().dimension().equals(dimension)
                || p.link().space().equals(dimension)).toList();
    }
}
