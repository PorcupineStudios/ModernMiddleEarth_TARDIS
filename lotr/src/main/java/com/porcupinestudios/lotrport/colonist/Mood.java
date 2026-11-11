package com.porcupinestudios.lotrport.colonist;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/** The things a colonist remembers that still move their mood (a person part). */
public record Mood(List<Memory> memories) {
    /** A remembered thought: when it was last felt and how many times it stacks. */
    public record Memory(ResourceLocation thought, long since, int stacks) {}

    public static final Mood EMPTY = new Mood(List.of());

    public Mood {
        memories = List.copyOf(memories);
    }
}
