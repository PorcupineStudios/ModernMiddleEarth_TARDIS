package com.porcupinestudios.lotrport.border;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colonist.Colonists;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.tech.Techs;
import com.porcupinestudios.lotrport.faction.Faction;
import com.porcupinestudios.lotrport.faction.FactionKills;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.faction.Relation;
import com.porcupinestudios.lotrport.society.ComponentType;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.war.War;
import com.porcupinestudios.lotrport.war.Wars;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Taking land that someone holds (settling only takes unclaimed land: {@link Borders}). Each map pixel has a
 * <b>defence</b>: its holder's ({@code conquest_defence}: per great power, kingdoms, plus the holder's colonies in reach
 * by soldiers and people) times the terrain ({@code conquest_terrain}: the Misty Mountains ~6x the Shire's open
 * country), minus damage from assaults. Attackers build <b>pressure</b> there by killing the holder's people (LOTR's
 * conquest) and by assaults; near one of the attacker's own colonies, that colony's research perks
 * ({@code colony_tech} {@code conquest}) multiply it, so a new colony gives none and a built-up one a lot. When
 * pressure reaches defence x {@code flip_margin}, the pixel changes hands ({@link Borders#take}). Pressure decays and
 * damage heals each conquest day.
 *
 * <p>Who may press whom: a great power's or AI realm's land, any faction on bad terms with it; a player kingdom's land
 * only by someone at war with it (no land lost to mob kills while its players sleep).
 *
 * <p>Assaults ({@link #assault}) send a colony's soldiers at a pixel: they strike off defence and add pressure, and
 * some die (more against strong defence) or come back injured (can't fight for {@code injury_days}). Deaths are
 * Society deaths, so families grieve ({@code kin_died}); many of one race lost gives that race's citizens
 * {@code fallen_kinsfolk}, massive losses give the whole colony {@code heavy_losses}.
 */
public final class Conquest extends SavedData {
    public static final String FILE = "lotr_port_conquest";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation ASSAULT_HISTORY = LotrPort.id("assault");
    public static final ResourceLocation FALLEN_KINSFOLK = LotrPort.id("fallen_kinsfolk");
    public static final ResourceLocation HEAVY_LOSSES = LotrPort.id("heavy_losses");

    /** An injured soldier: can't assault until {@code until} (game time). */
    public record Injury(long until) {
        public static final Codec<Injury> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("until").forGetter(Injury::until)).apply(i, Injury::new));
    }

    public static final ComponentType<Injury> INJURY = ComponentType.register(LotrPort.id("injury"), Injury.CODEC);

    private static final SavedData.Factory<Conquest> FACTORY = new SavedData.Factory<>(Conquest::new, Conquest::load,
            null);

    /** Pixel -> attacking faction -> pressure. */
    private final Map<Integer, Map<ResourceLocation, Float>> pressure = new HashMap<>();
    /** Pixel -> defence struck off by assaults. */
    private final Map<Integer, Float> damage = new HashMap<>();
    private long lastDay = -1;

    private static Object terrainKey;
    private static double[] terrainByBiome = new double[0];

    public static Conquest get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    /** Call from the mod constructor (registers the injury component before components freeze). */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(Conquest::onDeath);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> get(e.getServer()).daily(e.getServer()));
    }

    // ---- defence

    /** A pixel's terrain factor (1 = open land), from its biome. */
    public static double terrain(int pixel) {
        if (pixel < 0) {
            return 1;
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        Object key = ConquestData.terrainVersion();
        if (key != terrainKey) {
            Map<ResourceLocation, Double> t = ConquestData.terrain();
            double[] arr = new double[map.biomes().size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = t.getOrDefault(ResourceLocation.parse(map.biomes().get(i).id()), 1.0);
            }
            terrainByBiome = arr;
            terrainKey = key;
        }
        int b = map.pixelIndex(pixel % map.width(), pixel / map.width());
        return b >= 0 && b < terrainByBiome.length ? terrainByBiome[b] : 1;
    }

    /** Distance in map pixels from a colony's Hall to a pixel (large if not in Middle-earth). */
    static double distance(MinecraftServer server, Colony c, int pixel) {
        ServerLevel me = LotrDimensions.middleEarth(server);
        int h = Borders.pixelOf(c.hall().getX(), c.hall().getZ());
        if (me == null || !c.dimension().equals(me.dimension()) || h < 0 || pixel < 0) {
            return Double.MAX_VALUE;
        }
        int w = MiddleEarthMap.get().width();
        return Math.hypot(h % w - pixel % w, h / w - pixel / w);
    }

    /** The defence of a pixel now: nobody's land has none. */
    public double defence(MinecraftServer server, int pixel) {
        Optional<ResourceLocation> holder = Borders.get(server).owner(pixel);
        if (holder.isEmpty()) {
            return 0;
        }
        ConquestData.Defence d = ConquestData.defence();
        boolean realm = Realms.get(server).get(holder.get()).isPresent();
        double base = realm ? d.kingdom() : d.factions().getOrDefault(holder.get(), d.defaultDefence());
        double reach = BorderData.settings().maxRadius() + 1;
        for (Colony c : Colonies.get(server).all()) {
            if (!c.faction().equals(holder.get())) {
                continue;
            }
            double dist = distance(server, c, pixel);
            if (dist <= reach) {
                long soldiers = Society.soldiers(server, c).stream().filter(Person::alive).count();
                base += (soldiers * d.perSoldier() + Society.population(server, c) * d.perCitizen())
                        * (1 - dist / (reach + 1));
            }
        }
        return Math.max(0, base * terrain(pixel) - damage.getOrDefault(pixel, 0f));
    }

    /** The attacker's pressure on a pixel. */
    public float pressure(int pixel, ResourceLocation faction) {
        Map<ResourceLocation, Float> m = pressure.get(pixel);
        return m == null ? 0 : m.getOrDefault(faction, 0f);
    }

    /** The perk multiplier for a faction at a pixel: the best of its colonies whose perks reach it, else 1. */
    public static double multiplier(MinecraftServer server, ResourceLocation faction, int pixel) {
        double best = 1;
        for (Colony c : Colonies.get(server).all()) {
            if (c.faction().equals(faction) && distance(server, c, pixel) <= Techs.conquestReach(c)) {
                best = Math.max(best, Techs.conquestPressure(c));
            }
        }
        return best;
    }

    // ---- who may press whom

    /** Whether {@code attacker} may take land from {@code holder}. */
    public static boolean hostile(MinecraftServer server, ResourceLocation attacker, ResourceLocation holder) {
        if (attacker.equals(holder)) {
            return false;
        }
        boolean playerKingdom = Realms.get(server).get(holder).map(r -> !r.ai()).orElse(false);
        if (!playerKingdom && Factions.relation(server, attacker, holder).isBad()) {
            return true;
        }
        for (War w : Wars.get(server).all()) {
            if (w.state() != War.State.ACTIVE) {
                continue;
            }
            Optional<ResourceLocation> a = partyFaction(server, w.attacker());
            Optional<ResourceLocation> b = partyFaction(server, w.defender());
            if ((a.equals(Optional.of(attacker)) && b.equals(Optional.of(holder)))
                    || (b.equals(Optional.of(attacker)) && a.equals(Optional.of(holder)))) {
                return true;
            }
        }
        return false;
    }

    private static Optional<ResourceLocation> partyFaction(MinecraftServer server, Owner party) {
        return switch (party) {
            case Owner.OfColony c -> Colonies.get(server).get(c.id()).map(Colony::faction);
            case Owner.OfRealm r -> Optional.of(r.id());
            default -> Optional.empty();
        };
    }

    // ---- pressure

    /**
     * Adds pressure from {@code faction} on a pixel (times its perk multiplier there) and takes the pixel if it is
     * enough. Nobody's land and land the faction may not press are left alone. Returns the pressure added.
     */
    public float press(MinecraftServer server, int pixel, ResourceLocation faction, float amount, String reason) {
        Optional<ResourceLocation> holder = Borders.get(server).owner(pixel);
        if (pixel < 0 || amount <= 0 || holder.isEmpty() || !hostile(server, faction, holder.get())) {
            return 0;
        }
        float added = (float) (amount * multiplier(server, faction, pixel));
        float now = pressure.computeIfAbsent(pixel, k -> new HashMap<>()).merge(faction, added, Float::sum);
        setDirty();
        if (now >= defence(server, pixel) * ConquestData.settings().flipMargin()) {
            pressure.remove(pixel);
            damage.remove(pixel);
            Borders.get(server).take(server, pixel, faction, reason);
        }
        return added;
    }

    /** LOTR's conquest: a player killing the holder's people (or its allies') in its land presses it. */
    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)
                || !(event.getEntity().level() instanceof ServerLevel level) || !LotrDimensions.isMiddleEarth(level)) {
            return;
        }
        Optional<ResourceLocation> victim = FactionKills.factionOf(event.getEntity());
        Optional<ResourceLocation> attacker = Kingdoms.colonyFaction(killer);
        if (victim.isEmpty() || attacker.isEmpty()) {
            return;
        }
        MinecraftServer server = level.getServer();
        BlockPos pos = event.getEntity().blockPosition();
        int pixel = Borders.pixelOf(pos.getX(), pos.getZ());
        Optional<ResourceLocation> holder = Borders.get(server).owner(pixel);
        if (holder.isEmpty() || (!holder.equals(victim)
                && Factions.relation(server, victim.get(), holder.get()) != Relation.ALLY)) {
            return; // a stray orc killed in the Shire doesn't weaken the hobbits' hold
        }
        float value = Factions.get(server, victim.get()).map(Faction::killValue).orElse(1f);
        get(server).press(server, pixel, attacker.get(), ConquestData.settings().killPressure() * value, "kill");
    }

    // ---- assaults

    public record AssaultResult(int sent, int died, int injured, double defenceBefore, double defenceAfter,
            float pressureAdded, boolean taken) {}

    /** Soldiers of a colony who can fight now (alive, not injured). */
    public static List<Person> fit(MinecraftServer server, Colony colony) {
        long now = server.overworld().getGameTime();
        return Society.soldiers(server, colony).stream().filter(Person::alive)
                .filter(p -> p.get(INJURY).map(i -> i.until() <= now).orElse(true)).toList();
    }

    /**
     * Sends up to {@code count} fit soldiers of {@code from} at a pixel. Empty if the land can't be pressed by the
     * colony's faction, or it has no fit soldiers.
     */
    public Optional<AssaultResult> assault(MinecraftServer server, Colony from, int pixel, int count,
            RandomSource random) {
        Optional<ResourceLocation> holder = Borders.get(server).owner(pixel);
        if (pixel < 0 || holder.isEmpty() || !hostile(server, from.faction(), holder.get())) {
            return Optional.empty();
        }
        List<Person> soldiers = fit(server, from);
        int n = Math.min(count, soldiers.size());
        if (n <= 0) {
            return Optional.empty();
        }
        ConquestData.Settings s = ConquestData.settings();
        long now = server.overworld().getGameTime();
        double before = defence(server, pixel);
        double mult = multiplier(server, from.faction(), pixel);
        float deathChance = (float) Math.min(s.deathMax(), s.deathBase() + s.deathPerDefence() * before / n);
        Persons persons = Persons.get(server);
        Map<ResourceLocation, Integer> deadByRace = new HashMap<>();
        int died = 0;
        int injured = 0;
        for (Person p : new ArrayList<>(soldiers.subList(0, n))) {
            if (random.nextFloat() < deathChance) {
                deadByRace.merge(Colonists.raceOf(p), 1, Integer::sum);
                persons.died(server, p.id(), Optional.empty()); // families grieve (kin_died)
                died++;
            } else if (random.nextFloat() < s.injuryChance()) {
                persons.set(server, p, INJURY, new Injury(now + (long) s.injuryDays() * s.dayTicks()));
                injured++;
            }
        }
        damage.merge(pixel, (float) (n * s.soldierAttack() * mult), Float::sum);
        setDirty();
        mourn(server, from, deadByRace, died, n, now);
        float added = press(server, pixel, from.faction(), n * s.assaultPressure(), "assault");
        boolean taken = Borders.get(server).owner(pixel).equals(Optional.of(from.faction()));
        double after = taken ? 0 : defence(server, pixel);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", from.id());
        tag.putString("faction", from.faction().toString());
        tag.putString("holder", holder.get().toString());
        tag.putInt("pixel", pixel);
        tag.putInt("sent", n);
        tag.putInt("died", died);
        tag.putInt("injured", injured);
        tag.putDouble("defence_before", before);
        tag.putDouble("defence_after", after);
        tag.putBoolean("taken", taken);
        MiddleEarthMap map = MiddleEarthMap.get();
        BlockPos at = map.blockOfPixel(pixel % map.width() + 0.5, pixel / map.width() + 0.5);
        ServerLevel me = LotrDimensions.middleEarth(server);
        HistoryLog.get(server).append(new HistoryEntry(now, ASSAULT_HISTORY,
                me != null ? me.dimension() : LotrDimensions.MIDDLE_EARTH, Optional.of(at), tag));
        return Optional.of(new AssaultResult(n, died, injured, before, after, added, taken));
    }

    /** Race-wide and colony-wide grief after an assault (families already grieve through the deaths). */
    private static void mourn(MinecraftServer server, Colony from, Map<ResourceLocation, Integer> deadByRace, int died,
            int sent, long now) {
        ConquestData.Settings s = ConquestData.settings();
        Persons persons = Persons.get(server);
        boolean heavy = died >= s.heavyLossMin() && died >= s.heavyLossShare() * sent;
        for (Person p : Society.citizens(server, from)) {
            if (!p.alive()) {
                continue;
            }
            Integer ofRace = deadByRace.get(Colonists.raceOf(p));
            if (ofRace != null && ofRace >= s.raceLossCount()) {
                Colonists.remember(persons, p, FALLEN_KINSFOLK, now);
            }
            if (heavy) {
                Colonists.remember(persons, p, HEAVY_LOSSES, now);
            }
        }
    }

    // ---- days

    private void daily(MinecraftServer server) {
        ConquestData.Settings s = ConquestData.settings();
        long day = server.overworld().getGameTime() / Math.max(1, s.dayTicks());
        if (lastDay < 0) {
            lastDay = day;
            return;
        }
        if (day <= lastDay) {
            return;
        }
        int days = (int) Math.min(30, day - lastDay);
        lastDay = day;
        float keep = (float) Math.pow(1 - s.pressureDecay(), days);
        float heal = (float) Math.pow(1 - s.damageRecovery(), days);
        pressure.values().forEach(m -> m.replaceAll((f, v) -> v * keep));
        pressure.values().forEach(m -> m.values().removeIf(v -> v < 0.01f));
        pressure.values().removeIf(Map::isEmpty);
        damage.replaceAll((p, v) -> v * heal);
        damage.values().removeIf(v -> v < 0.01f);
        setDirty();
    }

    // ---- saving

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putLong("lastDay", lastDay);
        ListTag p = new ListTag();
        pressure.forEach((pixel, m) -> m.forEach((f, v) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("pixel", pixel);
            t.putString("faction", f.toString());
            t.putFloat("pressure", v);
            p.add(t);
        }));
        tag.put("pressure", p);
        ListTag d = new ListTag();
        damage.forEach((pixel, v) -> {
            CompoundTag t = new CompoundTag();
            t.putInt("pixel", pixel);
            t.putFloat("damage", v);
            d.add(t);
        });
        tag.put("damage", d);
        return tag;
    }

    public static Conquest load(CompoundTag tag, HolderLookup.Provider registries) {
        Conquest c = new Conquest();
        // dataVersion 1 is the first format; later versions migrate here
        c.lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1;
        for (Tag e : tag.getList("pressure", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            ResourceLocation f = ResourceLocation.tryParse(t.getString("faction"));
            if (f != null) {
                c.pressure.computeIfAbsent(t.getInt("pixel"), k -> new HashMap<>()).put(f, t.getFloat("pressure"));
            }
        }
        for (Tag e : tag.getList("damage", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            c.damage.put(t.getInt("pixel"), t.getFloat("damage"));
        }
        return c;
    }
}
