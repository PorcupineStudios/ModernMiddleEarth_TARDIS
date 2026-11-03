package com.porcupinestudios.lotrport.npc;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.quest.QuestData;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** The NPC data folders: types ({@code npc}), trade pools, unit pools and name banks. All reload with /reload. */
public final class NpcData {
    private static final CodecJsonLoader<TradePool> TRADES = new CodecJsonLoader<>("npc_trades", TradePool.CODEC);
    private static final CodecJsonLoader<UnitPool> UNITS = new CodecJsonLoader<>("npc_units", UnitPool.CODEC);
    private static final CodecJsonLoader<List<String>> NAMES =
            new CodecJsonLoader<>("npc_names", Codec.STRING.listOf());
    /** Speech lines by bank and mood: {@code speech/<bank>/<mood>.json} = {"lines": [...]}. */
    private static final CodecJsonLoader<List<String>> SPEECH = new CodecJsonLoader<>("speech",
            Codec.STRING.listOf().fieldOf("lines").codec());
    private static final CodecJsonLoader<NpcType> TYPES = new CodecJsonLoader<>("npc", NpcType.CODEC);

    private NpcData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(TRADES);
            event.addListener(UNITS);
            event.addListener(NAMES);
            event.addListener(SPEECH);
            event.addListener(TYPES);
        });
    }

    public static Map<ResourceLocation, NpcType> types() {
        return TYPES.entries();
    }

    public static Optional<NpcType> type(ResourceLocation id) {
        return Optional.ofNullable(TYPES.entries().get(id));
    }

    public static Optional<TradePool> trades(ResourceLocation id) {
        return Optional.ofNullable(TRADES.entries().get(id));
    }

    public static Optional<UnitPool> units(ResourceLocation id) {
        return Optional.ofNullable(UNITS.entries().get(id));
    }

    public static List<String> names(ResourceLocation bank) {
        return NAMES.entries().getOrDefault(bank, List.of());
    }

    /** The lines of one mood of a speech bank ({@code bank} plus {@code /mood}); empty when it has none. */
    public static List<String> speech(ResourceLocation bank, String mood) {
        return SPEECH.entries().getOrDefault(bank.withSuffix("/" + mood), List.of());
    }

    /** Whether any mood of the bank has lines. */
    public static boolean hasSpeech(ResourceLocation bank) {
        String prefix = bank.getPath() + "/";
        return SPEECH.entries().keySet().stream()
                .anyMatch(id -> id.getNamespace().equals(bank.getNamespace()) && id.getPath().startsWith(prefix));
    }

    public static List<String> loadProblems() {
        List<String> all = new ArrayList<>(TRADES.lastErrors());
        all.addAll(UNITS.lastErrors());
        all.addAll(NAMES.lastErrors());
        all.addAll(SPEECH.lastErrors());
        all.addAll(TYPES.lastErrors());
        all.addAll(brokenReferences());
        return all;
    }

    /** References between the folders that point at nothing (checked after loading: folders load in any order). */
    public static List<String> brokenReferences() {
        List<String> problems = new ArrayList<>();
        TYPES.entries().forEach((id, type) -> {
            type.buy().filter(p -> !TRADES.entries().containsKey(p))
                    .ifPresent(p -> problems.add(id + ": unknown trade pool " + p));
            type.sell().filter(p -> !TRADES.entries().containsKey(p))
                    .ifPresent(p -> problems.add(id + ": unknown trade pool " + p));
            type.units().filter(p -> !UNITS.entries().containsKey(p))
                    .ifPresent(p -> problems.add(id + ": unknown unit pool " + p));
            type.names().filter(p -> !NAMES.entries().containsKey(p))
                    .ifPresent(p -> problems.add(id + ": unknown name bank " + p));
            type.speech().filter(p -> !hasSpeech(p))
                    .ifPresent(p -> problems.add(id + ": unknown speech bank " + p));
            type.quests().filter(p -> QuestData.pool(p).isEmpty())
                    .ifPresent(p -> problems.add(id + ": unknown quest pool " + p));
        });
        UNITS.entries().forEach((id, pool) -> pool.units().stream()
                .filter(u -> !TYPES.entries().containsKey(u.npc()))
                .forEach(u -> problems.add(id + ": unit " + u.npc() + " is not an NPC type")));
        return problems;
    }
}
