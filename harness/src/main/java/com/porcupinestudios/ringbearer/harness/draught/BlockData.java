package com.porcupinestudios.ringbearer.harness.draught;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Data files that apply to blocks, {@code data/<ns>/<folder>/<name>.json}, each naming its {@code blocks}; reloaded
 * with /reload. Wheels ({@code wheel/}) and slope contact ({@code slope_contact/}).
 */
public final class BlockData<T> extends SimpleJsonResourceReloadListener {
    private static final Logger LOG = LogUtils.getLogger();
    private final Codec<T> codec;
    private final Function<T, List<ResourceLocation>> blocks;
    private volatile Map<Block, T> byBlock = Map.of();

    public BlockData(String folder, Codec<T> codec, Function<T, List<ResourceLocation>> blocks) {
        super(new Gson(), folder);
        this.codec = codec;
        this.blocks = blocks;
    }

    @Nullable
    public T of(Block block) {
        return byBlock.get(block);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        Map<Block, T> out = new HashMap<>();
        files.forEach((id, json) -> codec.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> LOG.error("{}: {}", id, err))
                .ifPresent(t -> blocks.apply(t).forEach(b -> BuiltInRegistries.BLOCK.getOptional(b)
                        .ifPresent(block -> out.put(block, t)))));
        byBlock = Map.copyOf(out);
    }
}
