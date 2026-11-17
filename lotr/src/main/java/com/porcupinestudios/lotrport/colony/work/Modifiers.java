package com.porcupinestudios.lotrport.colony.work;

import com.porcupinestudios.lotrport.colony.Colony;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/**
 * Colony-wide modifiers other systems add to colony life without {@code Life} knowing them: unity, edicts and the
 * tech tree register here. Work modifiers multiply every worker's output; happiness modifiers add to every citizen's
 * happiness target. Each is named so the citizen page and the Council can show where a number comes from.
 */
public final class Modifiers {
    /** A colony-wide number from one source. */
    public interface Source {
        double value(MinecraftServer server, Colony colony);
    }

    private static final Map<String, Source> WORK = new LinkedHashMap<>();
    private static final Map<String, Source> HAPPINESS = new LinkedHashMap<>();

    private Modifiers() {}

    /** A work multiplier (1 = no change). */
    public static void work(String name, Source s) {
        WORK.put(name, s);
    }

    /** A happiness target addition (0 = no change). */
    public static void happiness(String name, Source s) {
        HAPPINESS.put(name, s);
    }

    public static double work(MinecraftServer server, Colony c) {
        double f = 1;
        for (Source s : WORK.values()) {
            f *= s.value(server, c);
        }
        return Math.max(0, f);
    }

    public static double happiness(MinecraftServer server, Colony c) {
        double h = 0;
        for (Source s : HAPPINESS.values()) {
            h += s.value(server, c);
        }
        return h;
    }

    /** Each source's part, for screens: name, work factor, happiness. */
    public record Part(String name, double work, double happiness) {}

    public static List<Part> parts(MinecraftServer server, Colony c) {
        List<Part> out = new ArrayList<>();
        java.util.Set<String> names = new java.util.LinkedHashSet<>(WORK.keySet());
        names.addAll(HAPPINESS.keySet());
        for (String n : names) {
            Source w = WORK.get(n);
            Source h = HAPPINESS.get(n);
            out.add(new Part(n, w == null ? 1 : w.value(server, c), h == null ? 0 : h.value(server, c)));
        }
        return out;
    }
}
