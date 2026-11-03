package com.porcupinestudios.ringbearer.api.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.porcupinestudios.ringbearer.api.RingBearerApi;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Loads one folder of tuning data (repairs, costs, quests, trades...) from datapacks with a codec. It reloads on
 * {@code /reload}, unlike datapack registries, so pack makers and KubeJS can change balance without a restart.
 *
 * <p>Every file is checked: a file that can't be read, or that {@link #validate} rejects, is skipped and logged with
 * its id, and the rest still load. Register an instance with {@code AddReloadListenerEvent}.
 */
public class CodecJsonLoader<T> extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    private final String folder;
    private final Codec<T> codec;
    private Map<ResourceLocation, T> entries = Map.of();
    private List<String> lastErrors = List.of();

    public CodecJsonLoader(String folder, Codec<T> codec) {
        super(GSON, folder);
        this.folder = folder;
        this.codec = codec;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, T> read = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        files.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .ifSuccess(value -> read.put(id, value))
                .ifError(err -> errors.add(id + ": " + err.message())));
        for (String problem : validate(read)) {
            errors.add(problem);
        }
        errors.forEach(e -> RingBearerApi.LOG.error("[{}] {}", folder, e));
        entries = Map.copyOf(read);
        lastErrors = List.copyOf(errors);
        RingBearerApi.LOG.info("[{}] loaded {} files, {} problems", folder, entries.size(), errors.size());
    }

    /**
     * Checks across all files, such as ids that point at nothing or loops. Remove bad entries from {@code entries} and
     * return a message for each problem.
     */
    protected List<String> validate(Map<ResourceLocation, T> entries) {
        return List.of();
    }

    public Map<ResourceLocation, T> entries() {
        return entries;
    }

    public List<String> lastErrors() {
        return lastErrors;
    }
}
