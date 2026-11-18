package com.porcupinestudios.lotrport.diplomacy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * {@code diplomacy/settings.json} (reloads): offer life, limits, and how great powers and AI realms weigh an offer
 * (they answer at once; a player kingdom's ruler answers on the diplomacy table).
 *
 * @param dayTicks a diplomacy day (truces)
 * @param offerDays days an offer stands
 * @param maxLand most squares one offer may move each way
 * @param landSearch how far (map pixels) from the receiver's capital ceded land is looked for
 * @param coinValue AI: worth of one coin
 * @param landValue AI: worth of one square
 * @param colonyValue AI: worth of a colony
 * @param keepFactor AI: what it gives away weighs this much more than what it gets
 * @param tradeValue AI: worth of a trade agreement (counts against it with an enemy)
 * @param peaceValue AI: worth of ending a war
 * @param warDayValue AI: added worth of peace per day at war (weariness)
 * @param allianceValue AI: worth of an alliance with a friend or ally (against it otherwise)
 * @param vassalValue AI: worth of the proposer swearing to it; it never swears to anyone
 * @param relationBias AI: added worth by current relation (ally, friend, neutral, enemy, mortal_enemy)
 */
public record DiplomacyData(int dayTicks, int offerDays, int maxLand, int landSearch, double coinValue,
        double landValue, double colonyValue, double keepFactor, double tradeValue, double peaceValue,
        double warDayValue, double allianceValue, double vassalValue, Map<String, Double> relationBias) {
    public static final DiplomacyData DEFAULT = new DiplomacyData(24000, 3, 20, 48, 0.02, 4, 40, 1.5, 5, 10, 1, 10,
            30, Map.of("ally", 10.0, "friend", 5.0, "neutral", 0.0, "enemy", -10.0, "mortal_enemy", -30.0));
    public static final Codec<DiplomacyData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("day_ticks", DEFAULT.dayTicks).forGetter(DiplomacyData::dayTicks),
            Codec.INT.optionalFieldOf("offer_days", DEFAULT.offerDays).forGetter(DiplomacyData::offerDays),
            Codec.INT.optionalFieldOf("max_land", DEFAULT.maxLand).forGetter(DiplomacyData::maxLand),
            Codec.INT.optionalFieldOf("land_search", DEFAULT.landSearch).forGetter(DiplomacyData::landSearch),
            Codec.DOUBLE.optionalFieldOf("coin_value", DEFAULT.coinValue).forGetter(DiplomacyData::coinValue),
            Codec.DOUBLE.optionalFieldOf("land_value", DEFAULT.landValue).forGetter(DiplomacyData::landValue),
            Codec.DOUBLE.optionalFieldOf("colony_value", DEFAULT.colonyValue).forGetter(DiplomacyData::colonyValue),
            Codec.DOUBLE.optionalFieldOf("keep_factor", DEFAULT.keepFactor).forGetter(DiplomacyData::keepFactor),
            Codec.DOUBLE.optionalFieldOf("trade_value", DEFAULT.tradeValue).forGetter(DiplomacyData::tradeValue),
            Codec.DOUBLE.optionalFieldOf("peace_value", DEFAULT.peaceValue).forGetter(DiplomacyData::peaceValue),
            Codec.DOUBLE.optionalFieldOf("war_day_value", DEFAULT.warDayValue).forGetter(DiplomacyData::warDayValue),
            Codec.DOUBLE.optionalFieldOf("alliance_value", DEFAULT.allianceValue)
                    .forGetter(DiplomacyData::allianceValue),
            Codec.DOUBLE.optionalFieldOf("vassal_value", DEFAULT.vassalValue).forGetter(DiplomacyData::vassalValue),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("relation_bias", DEFAULT.relationBias)
                    .forGetter(DiplomacyData::relationBias))
            .apply(i, DiplomacyData::new));

    private static final CodecJsonLoader<DiplomacyData> LOADER = new CodecJsonLoader<>("diplomacy", CODEC);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static DiplomacyData get() {
        return LOADER.entries().getOrDefault(LotrPort.id("settings"), DEFAULT);
    }

}
