package com.porcupinestudios.lotrport.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

/**
 * A mini-quest a player has taken. Immutable; progress makes a new copy.
 *
 * @param giver the NPC that gave it (the quest is handed in to that NPC)
 * @param giverName its name when the quest was given, for the quest list
 * @param faction the giver's faction: the reward alignment goes there
 * @param count how many items or kills are asked
 * @param progress kills so far (collect quests count the inventory when handed in)
 * @param given game time it was given
 */
public record ActiveQuest(UUID giver, String giverName, ResourceLocation faction, QuestPool.Kind kind,
        ResourceLocation target, int count, int progress, int coins, float alignment, long given) {
    public static final Codec<ActiveQuest> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("giver").forGetter(ActiveQuest::giver),
            Codec.STRING.optionalFieldOf("giver_name", "").forGetter(ActiveQuest::giverName),
            ResourceLocation.CODEC.fieldOf("faction").forGetter(ActiveQuest::faction),
            QuestPool.Kind.CODEC.fieldOf("kind").forGetter(ActiveQuest::kind),
            ResourceLocation.CODEC.fieldOf("target").forGetter(ActiveQuest::target),
            Codec.INT.fieldOf("count").forGetter(ActiveQuest::count),
            Codec.INT.optionalFieldOf("progress", 0).forGetter(ActiveQuest::progress),
            Codec.INT.optionalFieldOf("coins", 0).forGetter(ActiveQuest::coins),
            Codec.FLOAT.optionalFieldOf("alignment", 0f).forGetter(ActiveQuest::alignment),
            Codec.LONG.optionalFieldOf("given", 0L).forGetter(ActiveQuest::given))
            .apply(i, ActiveQuest::new));

    public ActiveQuest withProgress(int value) {
        return new ActiveQuest(giver, giverName, faction, kind, target, count, value, coins, alignment, given);
    }

    public boolean killsDone() {
        return kind != QuestPool.Kind.COLLECT && progress >= count;
    }
}
