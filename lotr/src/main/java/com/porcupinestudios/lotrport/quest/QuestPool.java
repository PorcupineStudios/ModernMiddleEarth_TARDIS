package com.porcupinestudios.lotrport.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * The mini-quests a group of NPCs gives: {@code data/<namespace>/quest_pool/<pool>.json}, reloaded with /reload. An
 * NPC type names its pool with {@code "quests"}.
 *
 * @param quests what can be asked, picked by weight
 * @param maxActive how many quests a player may have open at once from this pool's givers
 * @param coinsPerTarget coins paid per item brought or enemy killed (times the quest's reward factor)
 * @param alignmentPerTarget alignment gained with the giver's faction per item or kill (times the reward factor)
 * @param minAlignment alignment a player needs with the giver's faction to be offered a quest
 */
public record QuestPool(List<Entry> quests, int maxActive, float coinsPerTarget, float alignmentPerTarget,
        float minAlignment) {
    public static final Codec<QuestPool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Entry.CODEC.listOf().fieldOf("quests").forGetter(QuestPool::quests),
            Codec.INT.optionalFieldOf("max_active", 5).forGetter(QuestPool::maxActive),
            Codec.FLOAT.optionalFieldOf("coins_per_target", 2f).forGetter(QuestPool::coinsPerTarget),
            Codec.FLOAT.optionalFieldOf("alignment_per_target", 1f).forGetter(QuestPool::alignmentPerTarget),
            Codec.FLOAT.optionalFieldOf("min_alignment", 0f).forGetter(QuestPool::minAlignment))
            .apply(i, QuestPool::new));

    /** What a quest asks for. */
    public enum Kind {
        /** Bring {@code count} of an item. */
        COLLECT,
        /** Kill {@code count} members of a faction. */
        KILL_FACTION,
        /** Kill {@code count} NPCs of one type. */
        KILL_NPC;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)),
                k -> k.name().toLowerCase(Locale.ROOT));
    }

    /**
     * One quest that can be asked.
     *
     * @param target the item (collect), faction (kill_faction) or NPC type (kill_npc)
     * @param min fewest asked for
     * @param max most asked for
     * @param reward reward factor (LOTR's): harder quests pay more per target
     */
    public record Entry(Kind kind, ResourceLocation target, int min, int max, int weight, float reward) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(Entry::kind),
                ResourceLocation.CODEC.fieldOf("target").forGetter(Entry::target),
                Codec.INT.optionalFieldOf("min", 1).forGetter(Entry::min),
                Codec.INT.optionalFieldOf("max", 1).forGetter(Entry::max),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.FLOAT.optionalFieldOf("reward", 1f).forGetter(Entry::reward))
                .apply(i, Entry::new));
    }

    public Optional<Entry> pick(net.minecraft.util.RandomSource random) {
        int total = quests.stream().mapToInt(e -> Math.max(0, e.weight())).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Entry e : quests) {
            roll -= Math.max(0, e.weight());
            if (roll < 0) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
