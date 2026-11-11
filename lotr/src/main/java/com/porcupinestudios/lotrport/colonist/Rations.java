package com.porcupinestudios.lotrport.colonist;

import com.porcupinestudios.lotrport.LotrPort;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** The rations in the data, in list order. */
public final class Rations {
    public static final ResourceLocation NORMAL = LotrPort.id("normal");
    /** Used when the data has no {@code normal.json}. */
    private static final RationDef PLAIN = new RationDef(1.0, java.util.Optional.empty(), 0);

    private Rations() {}

    public static RationDef get(ResourceLocation id) {
        Map<ResourceLocation, RationDef> all = ColonistData.rations();
        RationDef def = all.get(id);
        return def != null ? def : all.getOrDefault(NORMAL, PLAIN);
    }

    public static List<ResourceLocation> ordered() {
        return ColonistData.rations().entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<ResourceLocation, RationDef> e) -> e.getValue().order())
                        .thenComparing(e -> e.getKey().toString()))
                .map(Map.Entry::getKey).toList();
    }
}
