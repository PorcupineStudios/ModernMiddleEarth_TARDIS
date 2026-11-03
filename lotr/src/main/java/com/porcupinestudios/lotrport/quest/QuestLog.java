package com.porcupinestudios.lotrport.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/**
 * A player's mini-quests, stored on the player as a data attachment and kept on death. Immutable.
 *
 * @param dataVersion saved layout version; bump it and migrate in {@link #CODEC} when the layout changes
 * @param active open quests, oldest first
 * @param completed how many quests the player has finished
 */
public record QuestLog(int dataVersion, List<ActiveQuest> active, int completed) {
    public static final int DATA_VERSION = 1;
    public static final QuestLog EMPTY = new QuestLog(DATA_VERSION, List.of(), 0);

    public static final Codec<QuestLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("dataVersion", DATA_VERSION).forGetter(QuestLog::dataVersion),
            ActiveQuest.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(QuestLog::active),
            Codec.INT.optionalFieldOf("completed", 0).forGetter(QuestLog::completed))
            .apply(i, (version, active, completed) -> new QuestLog(DATA_VERSION, List.copyOf(active), completed)));

    public QuestLog with(ActiveQuest quest) {
        List<ActiveQuest> copy = new ArrayList<>(active);
        copy.add(quest);
        return new QuestLog(DATA_VERSION, List.copyOf(copy), completed);
    }

    public QuestLog replace(int index, ActiveQuest quest) {
        List<ActiveQuest> copy = new ArrayList<>(active);
        copy.set(index, quest);
        return new QuestLog(DATA_VERSION, List.copyOf(copy), completed);
    }

    public QuestLog without(int index, boolean finished) {
        List<ActiveQuest> copy = new ArrayList<>(active);
        copy.remove(index);
        return new QuestLog(DATA_VERSION, List.copyOf(copy), completed + (finished ? 1 : 0));
    }
}
