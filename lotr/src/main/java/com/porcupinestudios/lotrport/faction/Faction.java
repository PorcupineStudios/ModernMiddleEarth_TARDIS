package com.porcupinestudios.lotrport.faction;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A faction as the game sees it: a great power from data or a realm (player or AI). Alignment, relations, kills and
 * pledging treat both the same way.
 *
 * @param ranks titles from lowest to highest alignment
 */
public record Faction(ResourceLocation id, Component name, int color, boolean playable, Optional<Float> fixedAlignment,
        List<FactionDef.Rank> ranks, Optional<String> pledgeRank, float killValue, boolean realm) {

    static Faction of(ResourceLocation id, FactionDef def) {
        List<FactionDef.Rank> ranks = def.ranks().stream()
                .sorted(Comparator.comparingDouble(FactionDef.Rank::alignment)).toList();
        return new Faction(id, Component.translatable("faction." + id.getNamespace() + "." + id.getPath()),
                def.color(), def.playable(), def.fixedAlignment(), ranks, def.pledgeRank(), def.killValue(), false);
    }

    /** The highest rank this alignment reaches, if any. */
    public Optional<FactionDef.Rank> rank(float alignment) {
        FactionDef.Rank best = null;
        for (FactionDef.Rank r : ranks) {
            if (alignment >= r.alignment()) {
                best = r;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Alignment needed to pledge; realms and factions without a pledge rank can't be pledged to. */
    public Optional<Float> pledgeAlignment() {
        return pledgeRank.flatMap(id -> ranks.stream().filter(r -> r.id().equals(id)).findFirst())
                .map(FactionDef.Rank::alignment);
    }

    public Component rankName(FactionDef.Rank rank) {
        return Component.translatable("faction." + id.getNamespace() + "." + id.getPath() + ".rank." + rank.id());
    }
}
