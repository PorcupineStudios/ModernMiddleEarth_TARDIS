package com.porcupinestudios.lotrport.ai;

import com.porcupinestudios.lotrport.society.Society;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.ColonyData;
import com.porcupinestudios.lotrport.faction.Factions;
import com.porcupinestudios.lotrport.faction.Realms;
import com.porcupinestudios.lotrport.travel.FastTravel;
import com.porcupinestudios.lotrport.travel.Waypoint;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * AI rulers: small realms that found sparse colonies, declare war on hostile rulers, besiege and take each other's
 * colonies, and make peace. One step per colony day ({@link #step}). Every decision comes from a random source seeded
 * by the world seed, the day and the ruler, and is written to the history log, so a past can be rebuilt
 * ({@link AiReplay}) and a TARDIS future trip can run the AI forward. AI rulers only attack AI colonies for now.
 */
public final class AiRulers extends SavedData {
    public static final String FILE = "lotr_port_ai_rulers";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation RELATION_HISTORY = LotrPort.id("ai_relation");
    public static final ResourceLocation SIEGE_HISTORY = LotrPort.id("ai_siege");
    public static final String CONQUEST = "conquest";

    private static final SavedData.Factory<AiRulers> FACTORY = new SavedData.Factory<>(AiRulers::new, AiRulers::load,
            null);

    /** A ruler's saved state: its realm, soldiers, wars (enemy -> day it began) and colonies founded so far. */
    public static final class State {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("realm").forGetter(s -> s.realm),
                Codec.INT.optionalFieldOf("strength", 0).forGetter(s -> s.strength),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.LONG).optionalFieldOf("wars", Map.of())
                        .forGetter(s -> s.wars),
                Codec.INT.optionalFieldOf("founded", 0).forGetter(s -> s.founded))
                .apply(i, State::new));

        Optional<ResourceLocation> realm;
        int strength;
        final Map<ResourceLocation, Long> wars;
        int founded;

        State(Optional<ResourceLocation> realm, int strength, Map<ResourceLocation, Long> wars, int founded) {
            this.realm = realm;
            this.strength = strength;
            this.wars = new TreeMap<>(wars);
            this.founded = founded;
        }

        public int strength() {
            return strength;
        }

        public Map<ResourceLocation, Long> wars() {
            return Map.copyOf(wars);
        }

        public Optional<ResourceLocation> realm() {
            return realm;
        }
    }

    private final Map<ResourceLocation, State> states = new TreeMap<>();
    private long lastDay = -1;

    public static AiRulers get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(AiRulers::onTick);
    }

    /** The UUID an AI ruler's colonies are owned by; the same in every world. */
    public static UUID uuid(ResourceLocation ruler) {
        return UUID.nameUUIDFromBytes(("lotr_port:ai_ruler/" + ruler).getBytes(StandardCharsets.UTF_8));
    }

    public Optional<State> state(ResourceLocation ruler) {
        return Optional.ofNullable(states.get(ruler));
    }

    public boolean atWar(ResourceLocation a, ResourceLocation b) {
        return state(a).map(s -> s.wars.containsKey(b)).orElse(false);
    }

    /** The dimension AI colonies are founded in: Middle-earth, or the overworld when there is none. */
    public static ResourceKey<Level> dimension(MinecraftServer server) {
        ServerLevel me = LotrDimensions.middleEarth(server);
        return me == null ? Level.OVERWORLD : me.dimension();
    }

    /** The daily random source for a ruler: the same seed, day and ruler always give the same decisions. */
    static RandomSource random(long seed, long day, ResourceLocation ruler) {
        long s = seed ^ (day * 0x9E3779B97F4A7C15L) ^ ((long) ruler.toString().hashCode() * 0xC2B2AE3D27D4EB4FL);
        return RandomSource.create(s);
    }

    /** Runs one AI day for these rulers, in id order. */
    public void step(MinecraftServer server, Map<ResourceLocation, AiData.RulerDef> defs, long day, long seed) {
        AiData.Settings settings = AiData.settings();
        Colonies colonies = Colonies.get(server);
        ResourceKey<Level> dim = dimension(server);
        Map<ResourceLocation, AiData.RulerDef> sorted = new TreeMap<>(defs);
        sorted.forEach((id, def) -> ensure(server, id, def, settings));
        for (Map.Entry<ResourceLocation, AiData.RulerDef> e : sorted.entrySet()) {
            ResourceLocation id = e.getKey();
            AiData.RulerDef def = e.getValue();
            State st = states.get(id);
            RandomSource rnd = random(seed, day, id);
            List<Colony> mine = colonies.ownedBy(uuid(id));

            for (Colony c : mine) {
                if (Society.population(server, c) < settings.maxPopulation()) {
                    colonies.growAi(c, 1);
                }
            }
            st.strength = Math.min(settings.maxStrength(),
                    st.strength + settings.strengthPerDay() + settings.strengthPerColony() * mine.size());

            if (mine.size() < def.maxColonies() && rnd.nextDouble() < def.expansion()) {
                found(server, colonies, id, def, st, dim, day, rnd, settings);
            }

            for (Map.Entry<ResourceLocation, AiData.RulerDef> other : sorted.entrySet()) {
                ResourceLocation o = other.getKey();
                double roll = rnd.nextDouble();
                if (o.equals(id) || st.wars.containsKey(o)) {
                    continue;
                }
                if (Factions.relation(server, def.faction(), other.getValue().faction()).isBad()
                        && roll < def.aggression()) {
                    setWar(server, id, o, true, day, dim);
                }
            }

            if (com.porcupinestudios.lotrport.war.WarSettings.get().peace().aiRaidsPlayers()) {
                raidPlayers(server, colonies, id, def, st, rnd);
            }

            List<ResourceLocation> enemies = new ArrayList<>(st.wars.keySet());
            double march = rnd.nextDouble();
            if (!enemies.isEmpty() && march < def.aggression()) {
                siege(server, colonies, id, def, st, enemies, defs, dim, rnd, settings);
            }

            for (ResourceLocation enemy : new ArrayList<>(st.wars.keySet())) {
                double roll = rnd.nextDouble();
                if (day - st.wars.get(enemy) >= settings.minWarDays() && roll < settings.peaceChance()) {
                    setWar(server, id, enemy, false, day, dim);
                }
            }
        }
        setDirty();
    }

    private void ensure(MinecraftServer server, ResourceLocation id, AiData.RulerDef def, AiData.Settings settings) {
        State st = states.computeIfAbsent(id, k -> new State(Optional.empty(), settings.startStrength(), Map.of(), 0));
        if (st.realm.isEmpty()) {
            st.realm = Optional.of(Realms.get(server).found(server, def.name(), Optional.empty(),
                    Optional.of(def.faction()), def.color()).id());
        }
    }

    private void found(MinecraftServer server, Colonies colonies, ResourceLocation id, AiData.RulerDef def, State st,
            ResourceKey<Level> dim, long day, RandomSource rnd, AiData.Settings settings) {
        Optional<Waypoint> home = FastTravel.waypoint(def.home());
        if (home.isEmpty()) {
            return;
        }
        for (int attempt = 0; attempt < 4; attempt++) {
            double angle = rnd.nextDouble() * Math.PI * 2;
            int dist = settings.foundMinDistance()
                    + rnd.nextInt(Math.max(1, settings.foundMaxDistance() - settings.foundMinDistance() + 1));
            BlockPos hall = new BlockPos(home.get().x() + (int) Math.round(Math.cos(angle) * dist), 64,
                    home.get().z() + (int) Math.round(Math.sin(angle) * dist));
            String name = def.colonyNames().isEmpty() ? def.name() + " " + (st.founded + 1)
                    : def.colonyNames().get(st.founded % def.colonyNames().size());
            UUID colonyId = UUID.nameUUIDFromBytes(("lotr_port:ai_colony/" + id + "/" + day + "/" + st.founded)
                    .getBytes(StandardCharsets.UTF_8));
            if (colonies.foundAi(server, colonyId, uuid(id), name, dim, hall, def.faction(),
                    settings.startPopulation()).isPresent()) {
                st.founded++;
                return;
            }
        }
    }

    private void siege(MinecraftServer server, Colonies colonies, ResourceLocation id, AiData.RulerDef def, State st,
            List<ResourceLocation> enemies, Map<ResourceLocation, AiData.RulerDef> defs, ResourceKey<Level> dim,
            RandomSource rnd, AiData.Settings settings) {
        Optional<Waypoint> home = FastTravel.waypoint(def.home());
        BlockPos from = home.map(w -> new BlockPos(w.x(), 64, w.z())).orElse(BlockPos.ZERO);
        Colony target = null;
        ResourceLocation defender = null;
        for (ResourceLocation enemy : enemies) {
            for (Colony c : colonies.ownedBy(uuid(enemy))) {
                if (c.ai() && (target == null || c.hall().distSqr(from) < target.hall().distSqr(from))) {
                    target = c;
                    defender = enemy;
                }
            }
        }
        if (target == null) {
            return;
        }
        State enemy = states.get(defender);
        int enemyColonies = Math.max(1, colonies.ownedBy(uuid(defender)).size());
        double defense = Society.population(server, target) * settings.defensePerPopulation()
                + (enemy == null ? 0 : enemy.strength * settings.garrisonShare() / enemyColonies);
        double attack = st.strength * (0.5 + rnd.nextDouble());
        if (st.strength < defense * settings.marchRatio()) {
            return; // too weak: wait and build up
        }
        boolean won = attack > defense;
        CompoundTag tag = new CompoundTag();
        tag.putString("attacker", id.toString());
        tag.putString("defender", defender.toString());
        tag.putUUID("colony", target.id());
        tag.putInt("attack", (int) attack);
        tag.putInt("defense", (int) defense);
        tag.putBoolean("won", won);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), SIEGE_HISTORY, dim,
                Optional.of(target.hall()), tag));
        State loser = states.get(defender);
        if (st.realm.isPresent() && loser != null && loser.realm.isPresent()) {
            com.porcupinestudios.lotrport.war.Wars.get(server).aiSiege(server, st.realm.get(), loser.realm.get(),
                    target, won);
        }
        if (won) {
            colonies.transfer(server, target, uuid(id), def.faction(), true, CONQUEST);
            colonies.growAi(target, -target.virtualPopulation() / 2);
            if (enemy != null) {
                enemy.strength = (int) (enemy.strength * (1 - settings.siegeLosses()));
            }
            st.strength = (int) Math.max(0, st.strength - defense * settings.siegeLosses());
        } else {
            st.strength = (int) (st.strength * (1 - settings.siegeLosses()));
        }
    }

    /**
     * With {@code ai_raids_players} on: the ruler may declare a tribute war on the nearest player colony of a hostile
     * faction near its home, and while that war is fought, send small raids when the colony's leaders are offline
     * (the same capped raids players make; they never break blocks).
     */
    private void raidPlayers(MinecraftServer server, Colonies colonies, ResourceLocation id, AiData.RulerDef def,
            State st, RandomSource rnd) {
        com.porcupinestudios.lotrport.war.WarSettings ws = com.porcupinestudios.lotrport.war.WarSettings.get();
        Optional<Waypoint> home = FastTravel.waypoint(def.home());
        double roll = rnd.nextDouble();
        if (home.isEmpty() || st.realm.isEmpty() || roll >= def.aggression()) {
            return;
        }
        BlockPos from = new BlockPos(home.get().x(), 64, home.get().z());
        long range = (long) ws.peace().aiRaidRange() * ws.peace().aiRaidRange();
        Colony target = null;
        for (Colony c : colonies.all()) {
            if (!c.ai() && c.hall().distSqr(from) <= range
                    && Factions.relation(server, def.faction(), c.faction()).isBad()
                    && (target == null || c.hall().distSqr(from) < target.hall().distSqr(from))) {
                target = c;
            }
        }
        if (target == null) {
            return;
        }
        com.porcupinestudios.lotrport.war.Wars wars = com.porcupinestudios.lotrport.war.Wars.get(server);
        com.porcupinestudios.lotrport.society.Owner realm = new com.porcupinestudios.lotrport.society.Owner.OfRealm(
                st.realm.get());
        Optional<com.porcupinestudios.lotrport.war.War> war = wars.between(realm, target.asOwner());
        if (war.isEmpty()) {
            wars.declare(server, realm, target.asOwner(), com.porcupinestudios.lotrport.war.War.Goal.TRIBUTE,
                    "ai", Optional.empty(), ws.armies().warningTicks());
            return;
        }
        int size = Math.min(ws.armies().raidCap(), st.strength / 10);
        if (wars.musterCounted(server, war.get(), size,
                com.porcupinestudios.lotrport.war.War.Army.Kind.RAID, from).isPresent()) {
            st.strength -= size;
        }
    }

    /** Starts or ends a war between two rulers (both sides), and writes it to history. */
    public void setWar(MinecraftServer server, ResourceLocation a, ResourceLocation b, boolean war, long day,
            ResourceKey<Level> dim) {
        State sa = states.get(a);
        State sb = states.get(b);
        if (sa == null || sb == null) {
            return;
        }
        if (war) {
            sa.wars.put(b, day);
            sb.wars.put(a, day);
        } else {
            sa.wars.remove(b);
            sb.wars.remove(a);
        }
        setDirty();
        // the realms' war lives with every other war (score, reports, peace)
        sa.realm.ifPresent(ra -> sb.realm.ifPresent(rb -> com.porcupinestudios.lotrport.war.Wars.get(server)
                .aiWar(server, ra, rb, war)));
        CompoundTag tag = new CompoundTag();
        tag.putString("a", a.toString());
        tag.putString("b", b.toString());
        tag.putBoolean("war", war);
        tag.putLong("day", day);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), RELATION_HISTORY, dim,
                Optional.empty(), tag));
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 0 || !AiData.settings().enabled() || AiData.rulers().isEmpty()) {
            return;
        }
        AiRulers ai = get(server);
        long day = server.overworld().getGameTime() / Math.max(1, ColonyData.settings().dayTicks());
        if (waits(AiData.settings(), ai.lastDay, Colonies.get(server).all().stream().anyMatch(c -> !c.ai()))) {
            return;
        }
        if (ai.lastDay < 0) {
            ai.lastDay = day; // the AI starts on the next day after it may start
            ai.setDirty();
            return;
        }
        long seed = server.overworld().getSeed();
        for (long d = ai.lastDay + 1; d <= day && d <= ai.lastDay + 8; d++) {
            ai.step(server, AiData.rulers(), d, seed);
        }
        if (day > ai.lastDay) {
            ai.lastDay = Math.min(day, ai.lastDay + 8);
            ai.setDirty();
        }
    }

    /**
     * Whether the AI is still waiting to start: with {@code wait_for_player}, until the first player colony is founded.
     * Once started ({@code lastDay >= 0}) it keeps going, even if every player colony is lost.
     */
    public static boolean waits(AiData.Settings settings, long lastDay, boolean anyPlayerColony) {
        return settings.waitForPlayer() && lastDay < 0 && !anyPlayerColony;
    }

    /** Runs {@code n} AI days now (admin command), continuing from the last day run. */
    public long runDays(MinecraftServer server, int n) {
        long seed = server.overworld().getSeed();
        long start = Math.max(0, lastDay);
        for (int i = 1; i <= n; i++) {
            step(server, AiData.rulers(), start + i, seed);
        }
        lastDay = start + n;
        setDirty();
        return lastDay;
    }

    public Map<ResourceLocation, State> states() {
        return new LinkedHashMap<>(states);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putLong("lastDay", lastDay);
        tag.put("rulers", Codec.unboundedMap(ResourceLocation.CODEC, State.CODEC)
                .encodeStart(NbtOps.INSTANCE, states).getOrThrow());
        return tag;
    }

    public static AiRulers load(CompoundTag tag, HolderLookup.Provider registries) {
        AiRulers out = new AiRulers();
        out.lastDay = tag.contains("lastDay") ? tag.getLong("lastDay") : -1;
        if (tag.contains("rulers")) {
            Codec.unboundedMap(ResourceLocation.CODEC, State.CODEC).parse(NbtOps.INSTANCE, tag.get("rulers"))
                    .resultOrPartial(e -> LotrPort.LOG.error("[ai] {}", e)).ifPresent(out.states::putAll);
        }
        return out;
    }
}
