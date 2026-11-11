package com.porcupinestudios.lotrport.colonist;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Which race an NPC type is. Plain rules: data in, data out. */
public final class RaceRules {
    private RaceRules() {}

    /** The first race (in id order) one of whose {@code match} words is in the NPC type's path, else the default. */
    public static ResourceLocation raceOf(Map<ResourceLocation, RaceDef> races, ResourceLocation npcType,
            ResourceLocation fallback) {
        Map<String, ResourceLocation> ordered = new TreeMap<>();
        races.keySet().forEach(id -> ordered.put(id.toString(), id));
        for (ResourceLocation id : ordered.values()) {
            for (String word : races.get(id).match()) {
                if (!word.isEmpty() && npcType.getPath().contains(word)) {
                    return id;
                }
            }
        }
        return fallback;
    }
}
