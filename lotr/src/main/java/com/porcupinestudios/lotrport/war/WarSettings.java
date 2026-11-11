package com.porcupinestudios.lotrport.war;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Colony war numbers, {@code data/<ns>/colony_war/settings.json} (reloads). Two groups: {@code armies} (warning,
 * marches, raids, sieges) and {@code peace} (score, exhaustion, tribute, AI). Times are game ticks.
 */
public record WarSettings(Armies armies, Peace peace) {
    /**
     * @param warningTicks from declaration until the war may be fought
     * @param marchBase march time for any distance
     * @param marchPer500 added per 500 blocks between the Halls
     * @param marchMax longest march
     * @param musterCost coins per soldier, paid from the attacker's treasury
     * @param raidCap most soldiers in a raid
     * @param raidCooldown after a raid, how long the colony can't be raided again
     * @param lootShare share of the treasury and of each stock a won raid takes
     * @param raidLootCap most coins a raid takes
     * @param raidItemCap most items a raid takes
     * @param raidLosses share of a beaten raid's soldiers that die
     * @param siegeHold how long attackers must hold the Hall's zone, with no defender in it
     * @param siegeZone the zone's radius around the Hall
     * @param siegeLanding how far from the Hall a siege army appears
     * @param attackerGrace how long the attacking side may be offline before the siege fails
     * @param defenderGrace how long the defending side may be offline before the attacker wins
     */
    public record Armies(int warningTicks, int marchBase, int marchPer500, int marchMax, int musterCost, int raidCap,
            int raidCooldown, double lootShare, int raidLootCap, int raidItemCap, double raidLosses, int siegeHold,
            int siegeZone, int siegeLanding, int attackerGrace, int defenderGrace) {
        public static final Armies DEFAULT = new Armies(24000, 2400, 1200, 36000, 2, 6, 24000, 0.1, 200, 64, 0.5,
                2400, 8, 24, 600, 6000);
        public static final Codec<Armies> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("warning_ticks", DEFAULT.warningTicks).forGetter(Armies::warningTicks),
                Codec.INT.optionalFieldOf("march_base", DEFAULT.marchBase).forGetter(Armies::marchBase),
                Codec.INT.optionalFieldOf("march_per_500", DEFAULT.marchPer500).forGetter(Armies::marchPer500),
                Codec.INT.optionalFieldOf("march_max", DEFAULT.marchMax).forGetter(Armies::marchMax),
                Codec.INT.optionalFieldOf("muster_cost", DEFAULT.musterCost).forGetter(Armies::musterCost),
                Codec.INT.optionalFieldOf("raid_cap", DEFAULT.raidCap).forGetter(Armies::raidCap),
                Codec.INT.optionalFieldOf("raid_cooldown", DEFAULT.raidCooldown).forGetter(Armies::raidCooldown),
                Codec.DOUBLE.optionalFieldOf("loot_share", DEFAULT.lootShare).forGetter(Armies::lootShare),
                Codec.INT.optionalFieldOf("raid_loot_cap", DEFAULT.raidLootCap).forGetter(Armies::raidLootCap),
                Codec.INT.optionalFieldOf("raid_item_cap", DEFAULT.raidItemCap).forGetter(Armies::raidItemCap),
                Codec.DOUBLE.optionalFieldOf("raid_losses", DEFAULT.raidLosses).forGetter(Armies::raidLosses),
                Codec.INT.optionalFieldOf("siege_hold", DEFAULT.siegeHold).forGetter(Armies::siegeHold),
                Codec.INT.optionalFieldOf("siege_zone", DEFAULT.siegeZone).forGetter(Armies::siegeZone),
                Codec.INT.optionalFieldOf("siege_landing", DEFAULT.siegeLanding).forGetter(Armies::siegeLanding),
                Codec.INT.optionalFieldOf("attacker_grace", DEFAULT.attackerGrace).forGetter(Armies::attackerGrace),
                Codec.INT.optionalFieldOf("defender_grace", DEFAULT.defenderGrace).forGetter(Armies::defenderGrace))
                .apply(i, Armies::new));

        /** March time between two Halls this far apart. */
        public int march(double distance) {
            return (int) Math.min(marchMax, marchBase + marchPer500 * (distance / 500.0));
        }
    }

    /**
     * @param siegeScore score for a won siege (to the defender for a beaten one)
     * @param raidScore score for a won raid (to the defender for a beaten one)
     * @param soldierScore score for each enemy soldier killed
     * @param exhaustionPerDay exhaustion each side gains a colony day while the war is fought
     * @param exhaustionPerSoldier exhaustion a side gains per soldier lost
     * @param exhaustionMax at this, peace is forced on the score
     * @param coinsPerScore tribute a peace may ask per point of score lead
     * @param tributeGoal score at which a tribute war ends in the attacker's favour
     * @param vassalTribute coins a vassal pays its liege a day (if it has them)
     * @param defensePerCitizen a citizen's worth in an abstract fight (a soldier is 1)
     * @param reportTicks how far back a player who logs in is told of raids and sieges
     * @param aiRaidsPlayers AI rulers may declare on and raid player colonies of hostile factions
     * @param aiRaidRange how far from an AI ruler's home a player colony may be raided
     */
    public record Peace(int siegeScore, int raidScore, int soldierScore, double exhaustionPerDay,
            double exhaustionPerSoldier, double exhaustionMax, int coinsPerScore, int tributeGoal, int vassalTribute,
            double defensePerCitizen, int reportTicks, boolean aiRaidsPlayers, int aiRaidRange) {
        public static final Peace DEFAULT = new Peace(50, 10, 1, 5, 2, 100, 5, 50, 5, 0.1, 24000, false, 3000);
        public static final Codec<Peace> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("siege_score", DEFAULT.siegeScore).forGetter(Peace::siegeScore),
                Codec.INT.optionalFieldOf("raid_score", DEFAULT.raidScore).forGetter(Peace::raidScore),
                Codec.INT.optionalFieldOf("soldier_score", DEFAULT.soldierScore).forGetter(Peace::soldierScore),
                Codec.DOUBLE.optionalFieldOf("exhaustion_per_day", DEFAULT.exhaustionPerDay)
                        .forGetter(Peace::exhaustionPerDay),
                Codec.DOUBLE.optionalFieldOf("exhaustion_per_soldier", DEFAULT.exhaustionPerSoldier)
                        .forGetter(Peace::exhaustionPerSoldier),
                Codec.DOUBLE.optionalFieldOf("exhaustion_max", DEFAULT.exhaustionMax).forGetter(Peace::exhaustionMax),
                Codec.INT.optionalFieldOf("coins_per_score", DEFAULT.coinsPerScore).forGetter(Peace::coinsPerScore),
                Codec.INT.optionalFieldOf("tribute_goal", DEFAULT.tributeGoal).forGetter(Peace::tributeGoal),
                Codec.INT.optionalFieldOf("vassal_tribute", DEFAULT.vassalTribute).forGetter(Peace::vassalTribute),
                Codec.DOUBLE.optionalFieldOf("defense_per_citizen", DEFAULT.defensePerCitizen)
                        .forGetter(Peace::defensePerCitizen),
                Codec.INT.optionalFieldOf("report_ticks", DEFAULT.reportTicks).forGetter(Peace::reportTicks),
                Codec.BOOL.optionalFieldOf("ai_raids_players", DEFAULT.aiRaidsPlayers).forGetter(Peace::aiRaidsPlayers),
                Codec.INT.optionalFieldOf("ai_raid_range", DEFAULT.aiRaidRange).forGetter(Peace::aiRaidRange))
                .apply(i, Peace::new));
    }

    public static final WarSettings DEFAULT = new WarSettings(Armies.DEFAULT, Peace.DEFAULT);
    public static final Codec<WarSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Armies.CODEC.optionalFieldOf("armies", Armies.DEFAULT).forGetter(WarSettings::armies),
            Peace.CODEC.optionalFieldOf("peace", Peace.DEFAULT).forGetter(WarSettings::peace))
            .apply(i, WarSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<WarSettings> LOADER = new CodecJsonLoader<>("colony_war", CODEC);
    /** Tests may set numbers here (shorter warnings and marches); null = the data file. */
    static WarSettings override;

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static WarSettings get() {
        return override != null ? override : LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    /** For tests: use these numbers until called again with null. */
    public static void override(WarSettings settings) {
        override = settings;
    }

    public static List<String> loadProblems() {
        return LOADER.lastErrors();
    }
}
