package com.porcupinestudios.lotrport.colony.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Colony society numbers, {@code data/<ns>/colony_people/settings.json} (reloads; RingRealms P4).
 *
 * @param incidentChance chance a day that a fully miserable citizen (happiness 0) quarrels; falls to 0 at
 *     {@code calm_at} happiness
 * @param calmAt happiness at and above which citizens don't start quarrels
 * @param grudgePerIncident grudge a quarrel adds between the two (both ways), times the races' clash
 * @param grudgeDecay grudge forgotten a day
 * @param brawlAt grudge at which a quarrel becomes a brawl
 * @param brawlHappiness happiness both brawlers lose
 * @param brawlUnity unity the colony loses per brawl
 * @param clash how badly two races get on, "race|race" (either order) to a factor (1 when not listed)
 * @param unityPerDay how far unity moves toward its target a day
 * @param unityWorkLow work factor at unity 0 (linear to {@code unity_work_high} at 100)
 * @param unityWorkHigh work factor at unity 100
 * @param unityHappiness happiness added at unity 100, taken at 0 (none at 50)
 * @param mixPenalty unity target lost per extra race living in the colony
 * @param edictSlotsBase edicts any colony may hold at once
 * @param citizensPerEdictSlot one more edict slot per this many citizens
 * @param reportsKept incidents the Council remembers
 */
public record PeopleSettings(double incidentChance, double calmAt, double grudgePerIncident, double grudgeDecay,
        double brawlAt, double brawlHappiness, double brawlUnity, Map<String, Double> clash, double unityPerDay,
        double unityWorkLow, double unityWorkHigh, double unityHappiness, double mixPenalty, int edictSlotsBase,
        int citizensPerEdictSlot, int reportsKept) {
    public static final PeopleSettings DEFAULT = new PeopleSettings(0.3, 55, 20, 4, 60, 15, 4, Map.of(), 10, 0.85,
            1.15, 8, 4, 1, 25, 20);

    private static final Codec<PeopleSettings> A = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("incident_chance", DEFAULT.incidentChance).forGetter(PeopleSettings::incidentChance),
            Codec.DOUBLE.optionalFieldOf("calm_at", DEFAULT.calmAt).forGetter(PeopleSettings::calmAt),
            Codec.DOUBLE.optionalFieldOf("grudge_per_incident", DEFAULT.grudgePerIncident)
                    .forGetter(PeopleSettings::grudgePerIncident),
            Codec.DOUBLE.optionalFieldOf("grudge_decay", DEFAULT.grudgeDecay).forGetter(PeopleSettings::grudgeDecay),
            Codec.DOUBLE.optionalFieldOf("brawl_at", DEFAULT.brawlAt).forGetter(PeopleSettings::brawlAt),
            Codec.DOUBLE.optionalFieldOf("brawl_happiness", DEFAULT.brawlHappiness)
                    .forGetter(PeopleSettings::brawlHappiness),
            Codec.DOUBLE.optionalFieldOf("brawl_unity", DEFAULT.brawlUnity).forGetter(PeopleSettings::brawlUnity),
            Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("clash", Map.of())
                    .forGetter(PeopleSettings::clash),
            Codec.DOUBLE.optionalFieldOf("unity_per_day", DEFAULT.unityPerDay).forGetter(PeopleSettings::unityPerDay),
            Codec.DOUBLE.optionalFieldOf("unity_work_low", DEFAULT.unityWorkLow).forGetter(PeopleSettings::unityWorkLow),
            Codec.DOUBLE.optionalFieldOf("unity_work_high", DEFAULT.unityWorkHigh)
                    .forGetter(PeopleSettings::unityWorkHigh),
            Codec.DOUBLE.optionalFieldOf("unity_happiness", DEFAULT.unityHappiness)
                    .forGetter(PeopleSettings::unityHappiness),
            Codec.DOUBLE.optionalFieldOf("mix_penalty", DEFAULT.mixPenalty).forGetter(PeopleSettings::mixPenalty),
            Codec.INT.optionalFieldOf("edict_slots_base", DEFAULT.edictSlotsBase).forGetter(PeopleSettings::edictSlotsBase),
            Codec.INT.optionalFieldOf("citizens_per_edict_slot", DEFAULT.citizensPerEdictSlot)
                    .forGetter(PeopleSettings::citizensPerEdictSlot),
            Codec.INT.optionalFieldOf("reports_kept", DEFAULT.reportsKept).forGetter(PeopleSettings::reportsKept))
            .apply(i, PeopleSettings::new));

    private static final ResourceLocation FILE = LotrPort.id("settings");
    private static final CodecJsonLoader<PeopleSettings> LOADER = new CodecJsonLoader<>("colony_people", A);

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static PeopleSettings get() {
        return LOADER.entries().getOrDefault(FILE, DEFAULT);
    }

    /** How badly two races get on (1 when not listed, either order). */
    public double clash(String a, String b) {
        Double v = clash.get(a + "|" + b);
        if (v == null) {
            v = clash.get(b + "|" + a);
        }
        return v == null ? 1 : v;
    }

    public double unityWork(double unity) {
        return unityWorkLow + (unityWorkHigh - unityWorkLow) * unity / 100;
    }
}
