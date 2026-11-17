package com.porcupinestudios.lotrport.modifier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * An ancient item ({@code data/<ns>/ancient_item/<item>.json}, the file's id is the item): used, it turns into one
 * of {@code results} (picked evenly), with skilful modifiers rolled onto it when {@code skilful}.
 */
public record AncientItemDef(List<ResourceLocation> results, boolean skilful) {
    public static final Codec<AncientItemDef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf().fieldOf("results").forGetter(AncientItemDef::results),
            Codec.BOOL.optionalFieldOf("skilful", true).forGetter(AncientItemDef::skilful))
            .apply(i, AncientItemDef::new));
}
