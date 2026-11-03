package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MapBiomeSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Middle-earth's dimension. In a Middle-earth world type the overworld itself is Middle-earth (players start and
 * respawn there as normal); in any other world it is the datapack dimension {@code lotr_port:middle_earth}.
 */
public final class LotrDimensions {
    public static final ResourceKey<Level> MIDDLE_EARTH = ResourceKey.create(Registries.DIMENSION,
            LotrPort.id("middle_earth"));
    /**
     * A plain vanilla Overworld, for Middle-earth worlds where the real overworld is Middle-earth; the TARDIS reaches
     * it as its second dimension (data {@code tardis/destination/vanilla_overworld.json}).
     */
    public static final ResourceKey<Level> VANILLA_OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            LotrPort.id("vanilla_overworld"));

    private LotrDimensions() {}

    public static boolean overworldIsMiddleEarth(MinecraftServer server) {
        return isMiddleEarth(server.overworld());
    }

    public static boolean isMiddleEarth(ServerLevel level) {
        return level.getChunkSource().getGenerator().getBiomeSource() instanceof MapBiomeSource;
    }

    /** The level that is Middle-earth in this world, or null if there is none. */
    public static ServerLevel middleEarth(MinecraftServer server) {
        return overworldIsMiddleEarth(server) ? server.overworld() : server.getLevel(MIDDLE_EARTH);
    }

    /** The first open block above the ground at a column (loads the chunk). */
    public static BlockPos surface(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
