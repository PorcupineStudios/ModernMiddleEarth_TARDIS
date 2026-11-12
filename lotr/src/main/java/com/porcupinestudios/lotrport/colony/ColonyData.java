package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * The colony data folders, all reloaded with /reload: {@code colony/settings.json} (growth tuning),
 * {@code colony_migration/<faction>.json} (who moves into a faction's colonies) and {@code recruit/<id>.json} (rare
 * races won by quest).
 */
public final class ColonyData {
    /**
     * @param dayTicks game ticks between colony days (arrivals happen once a day)
     * @param bedRadius beds within this many blocks of the Hall (horizontally) count as homes
     * @param bedHeight and within this many blocks up or down
     * @param hallGap fewest blocks between two colonies' Halls
     * @param firstSettlers settlers that may be welcomed before there are any beds (immigration: {@link Immigration})
     * @param citizensPerResearcher common citizens it takes to free one researcher (0 = none); every rare citizen
     *     is a researcher of its own (TARDIS research, through the shared API)
     */
    public record Settings(int dayTicks, int bedRadius, int bedHeight, int hallGap, int firstSettlers,
            int citizensPerResearcher) {
        public static final Settings DEFAULT = new Settings(24000, 32, 16, 96, 1, 5);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("day_ticks", DEFAULT.dayTicks()).forGetter(Settings::dayTicks),
                Codec.INT.optionalFieldOf("bed_radius", DEFAULT.bedRadius()).forGetter(Settings::bedRadius),
                Codec.INT.optionalFieldOf("bed_height", DEFAULT.bedHeight()).forGetter(Settings::bedHeight),
                Codec.INT.optionalFieldOf("hall_gap", DEFAULT.hallGap()).forGetter(Settings::hallGap),
                Codec.INT.optionalFieldOf("first_settlers", DEFAULT.firstSettlers()).forGetter(Settings::firstSettlers),
                Codec.INT.optionalFieldOf("citizens_per_researcher", DEFAULT.citizensPerResearcher())
                        .forGetter(Settings::citizensPerResearcher))
                .apply(i, Settings::new));
    }

    /** Who may move into a faction's colonies, by weight. */
    public record Migration(List<Entry> entries) {
        public record Entry(ResourceLocation npc, int weight) {
            public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                    ResourceLocation.CODEC.fieldOf("npc").forGetter(Entry::npc),
                    Codec.INT.optionalFieldOf("weight", 1).forGetter(Entry::weight))
                    .apply(i, Entry::new));
        }

        public static final Codec<Migration> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Migration::entries))
                .apply(i, Migration::new));
    }

    /** What a recruitment stage's objective asks. */
    public enum ObjectiveType {
        /** Give {@code count} of item {@code target} to the envoy (sneak + right-click it). */
        DELIVER,
        /** Kill {@code count} of faction {@code target}. */
        KILL_FACTION,
        /** Kill {@code count} NPCs of type {@code target}. */
        KILL_NPC,
        /** Stand within {@code radius} blocks of waypoint {@code target}. */
        VISIT,
        /** The colony has at least {@code count} living citizens. */
        POPULATION;

        public static final Codec<ObjectiveType> CODEC = Codec.STRING.xmap(
                s -> valueOf(s.toUpperCase(Locale.ROOT)), t -> t.name().toLowerCase(Locale.ROOT));
    }

    public record Objective(ObjectiveType type, Optional<ResourceLocation> target, int count, int radius) {
        public static final Codec<Objective> CODEC = RecordCodecBuilder.create(i -> i.group(
                ObjectiveType.CODEC.fieldOf("type").forGetter(Objective::type),
                ResourceLocation.CODEC.optionalFieldOf("target").forGetter(Objective::target),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Objective::count),
                Codec.INT.optionalFieldOf("radius", 32).forGetter(Objective::radius))
                .apply(i, Objective::new));

        /** How much counts as done: visits are 0 or 1. */
        public int goal() {
            return type == ObjectiveType.VISIT ? 1 : count;
        }
    }

    /** One stage: all its objectives must be met, then the next stage starts. Its text is lang {@code key}. */
    public record Stage(List<Objective> objectives) {
        public static final Codec<Stage> CODEC = RecordCodecBuilder.create(i -> i.group(
                Objective.CODEC.listOf().fieldOf("objectives").forGetter(Stage::objectives))
                .apply(i, Stage::new));
    }

    /**
     * A rare race that never migrates and joins a colony only by quest.
     *
     * @param npc the NPC type that can be recruited (its envoys are ordinary NPCs of that type)
     * @param faction alignment with this faction gates the quest
     * @param alignment the alignment needed
     * @param minPopulation living citizens the colony needs before the envoy listens
     * @param limit most of this race per colony (0 = no limit)
     * @param stages done in order
     */
    public record Recruit(ResourceLocation npc, Optional<ResourceLocation> faction, float alignment, int minPopulation,
            int limit, List<Stage> stages) {
        public static final Codec<Recruit> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Recruit::npc),
                ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Recruit::faction),
                Codec.FLOAT.optionalFieldOf("alignment", 0f).forGetter(Recruit::alignment),
                Codec.INT.optionalFieldOf("min_population", 0).forGetter(Recruit::minPopulation),
                Codec.INT.optionalFieldOf("limit", 1).forGetter(Recruit::limit),
                Stage.CODEC.listOf().fieldOf("stages").forGetter(Recruit::stages))
                .apply(i, Recruit::new));
    }

    private static final ResourceLocation SETTINGS = LotrPort.id("settings");
    private static final CodecJsonLoader<Settings> SETTINGS_LOADER = new CodecJsonLoader<>("colony", Settings.CODEC);
    private static final CodecJsonLoader<Migration> MIGRATION =
            new CodecJsonLoader<>("colony_migration", Migration.CODEC);
    private static final CodecJsonLoader<Recruit> RECRUITS = new CodecJsonLoader<>("recruit", Recruit.CODEC);

    private ColonyData() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(SETTINGS_LOADER);
            event.addListener(MIGRATION);
            event.addListener(RECRUITS);
        });
    }

    public static Settings settings() {
        return SETTINGS_LOADER.entries().getOrDefault(SETTINGS, Settings.DEFAULT);
    }

    /** The migration table for a faction: file {@code colony_migration/<faction path>.json} in its namespace. */
    public static Optional<Migration> migration(ResourceLocation faction) {
        return Optional.ofNullable(MIGRATION.entries().get(faction));
    }

    public static Map<ResourceLocation, Migration> migrations() {
        return MIGRATION.entries();
    }

    public static Map<ResourceLocation, Recruit> recruits() {
        return RECRUITS.entries();
    }

    public static Optional<Recruit> recruit(ResourceLocation id) {
        return Optional.ofNullable(RECRUITS.entries().get(id));
    }

    public static List<String> loadProblems() {
        List<String> all = new ArrayList<>(SETTINGS_LOADER.lastErrors());
        all.addAll(MIGRATION.lastErrors());
        all.addAll(RECRUITS.lastErrors());
        return all;
    }
}
