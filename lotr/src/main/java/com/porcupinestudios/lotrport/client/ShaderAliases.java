package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.porcupinestudios.lotrport.LotrPort;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Which vanilla block each LOTR plant looks like to a shader pack: {@code assets/<ns>/shaders/block_aliases.json}
 * ({@code {"aliases": {"lotr_port:thistle": "minecraft:short_grass", ...}}}, every resource pack's copy merged, later
 * packs winning; written by {@code converter/shaders.py}). {@link #apply} gives each state of an aliased block that has
 * no shader id the id of the vanilla state with the same values for the properties they share (a crop's age, a double
 * plant's half), else of the vanilla block's default state, else of any of its states.
 */
public final class ShaderAliases {
    private static final ResourceLocation FILE = ResourceLocation.fromNamespaceAndPath(LotrPort.MOD_ID,
            "shaders/block_aliases.json");

    private ShaderAliases() {}

    /** The aliases from every loaded resource pack (empty if none or unreadable). */
    public static Map<Block, Block> load() {
        Map<Block, Block> out = new LinkedHashMap<>();
        try {
            for (var resource : Minecraft.getInstance().getResourceManager().getResourceStack(FILE)) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject aliases = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("aliases");
                    for (Map.Entry<String, JsonElement> e : aliases.entrySet()) {
                        Block from = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(e.getKey()));
                        Block to = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(e.getValue().getAsString()));
                        if (from != Blocks.AIR && to != Blocks.AIR) {
                            out.put(from, to);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LotrPort.LOG.warn("Could not read the shader block aliases: {}", e.toString());
        }
        return out;
    }

    /** Fills the shader ids of aliased blocks' states that the pack left without one. */
    public static void apply(Object2IntMap<BlockState> ids) {
        if (ids == null) {
            return;
        }
        int filled = 0;
        for (Map.Entry<Block, Block> alias : load().entrySet()) {
            for (BlockState state : alias.getKey().getStateDefinition().getPossibleStates()) {
                if (ids.containsKey(state)) {
                    continue;
                }
                int id = idOf(ids, state, alias.getValue());
                if (id >= 0) {
                    ids.put(state, id);
                    filled++;
                }
            }
        }
        LotrPort.LOG.info("Shader pack ids for {} LOTR plant and leaf states", filled);
    }

    private static int idOf(Object2IntMap<BlockState> ids, BlockState state, Block vanilla) {
        BlockState match = vanilla.defaultBlockState();
        for (Property<?> p : state.getProperties()) {
            Property<?> same = vanilla.getStateDefinition().getProperty(p.getName());
            if (same != null) {
                match = with(match, same, state.getValue(p).toString());
            }
        }
        if (ids.containsKey(match)) {
            return ids.getInt(match);
        }
        if (ids.containsKey(vanilla.defaultBlockState())) {
            return ids.getInt(vanilla.defaultBlockState());
        }
        for (BlockState s : vanilla.getStateDefinition().getPossibleStates()) {
            if (ids.containsKey(s)) {
                return ids.getInt(s);
            }
        }
        return -1;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
