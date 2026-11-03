package com.porcupinestudios.lotrport.world;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World-wide LOTR state, saved on the overworld (replaces {@code LOTRLevelData}). Fellowships, custom waypoints and
 * invasions move in here as their phases land. Uses named keys so the file stays readable.
 */
public final class LotrWorldData extends SavedData {
    public static final String FILE = "lotr_port";
    public static final int DATA_VERSION = 1;

    private static final SavedData.Factory<LotrWorldData> FACTORY =
            new SavedData.Factory<>(LotrWorldData::new, LotrWorldData::load, null);

    /** True for a Middle-earth world type: the world starts in Middle-earth, and so does the TARDIS wreck. */
    private boolean startsInMiddleEarth;

    public static LotrWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public boolean startsInMiddleEarth() {
        return startsInMiddleEarth;
    }

    public void setStartsInMiddleEarth(boolean value) {
        startsInMiddleEarth = value;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putBoolean("startsInMiddleEarth", startsInMiddleEarth);
        return tag;
    }

    public static LotrWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        LotrWorldData data = new LotrWorldData();
        data.startsInMiddleEarth = tag.getBoolean("startsInMiddleEarth");
        return data;
    }
}
