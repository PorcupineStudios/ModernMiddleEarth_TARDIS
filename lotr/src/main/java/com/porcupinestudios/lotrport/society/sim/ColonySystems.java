package com.porcupinestudios.lotrport.society.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Every registered {@link ColonySystem}, in run order per cadence: each after the systems it names, otherwise in
 * registration order. Systems register while the mod loads (ours in {@code CoreSystems}; an addon in its own
 * constructor or common setup).
 */
public final class ColonySystems {
    private static final Map<ResourceLocation, ColonySystem> SYSTEMS = new LinkedHashMap<>();
    private static Map<ColonySystem.Cadence, List<ColonySystem>> ordered;

    private ColonySystems() {}

    public static synchronized void register(ColonySystem system) {
        if (SYSTEMS.putIfAbsent(system.id(), system) != null) {
            throw new IllegalStateException("colony system " + system.id() + " registered twice");
        }
        ordered = null;
    }

    public static Map<ResourceLocation, ColonySystem> all() {
        return Collections.unmodifiableMap(SYSTEMS);
    }

    /** Systems of a cadence in run order. */
    public static List<ColonySystem> ordered(ColonySystem.Cadence cadence) {
        Map<ColonySystem.Cadence, List<ColonySystem>> o = ordered;
        if (o == null) {
            o = new EnumMap<>(ColonySystem.Cadence.class);
            for (ColonySystem.Cadence c : ColonySystem.Cadence.values()) {
                o.put(c, sort(c));
            }
            ordered = o;
        }
        return o.get(cadence);
    }

    /** Depth-first topological order; a cycle is broken at the system that closes it (and reported). */
    private static List<ColonySystem> sort(ColonySystem.Cadence cadence) {
        List<ColonySystem> out = new ArrayList<>();
        Set<ResourceLocation> done = new LinkedHashSet<>();
        Set<ResourceLocation> visiting = new LinkedHashSet<>();
        for (ColonySystem s : SYSTEMS.values()) {
            if (s.cadence() == cadence) {
                visit(s, cadence, done, visiting, out);
            }
        }
        return List.copyOf(out);
    }

    private static void visit(ColonySystem s, ColonySystem.Cadence cadence, Set<ResourceLocation> done,
            Set<ResourceLocation> visiting, List<ColonySystem> out) {
        if (done.contains(s.id())) {
            return;
        }
        if (!visiting.add(s.id())) {
            com.porcupinestudios.lotrport.LotrPort.LOG.warn("[colony systems] cycle at {}", s.id());
            return;
        }
        for (ResourceLocation before : s.after()) {
            ColonySystem b = SYSTEMS.get(before);
            if (b != null && b.cadence() == cadence) {
                visit(b, cadence, done, visiting, out);
            }
        }
        visiting.remove(s.id());
        done.add(s.id());
        out.add(s);
    }
}
