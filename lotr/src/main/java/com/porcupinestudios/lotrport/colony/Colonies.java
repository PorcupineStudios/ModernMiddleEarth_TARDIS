package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.society.ComponentMap;
import com.porcupinestudios.lotrport.society.CoreComponents;
import com.porcupinestudios.lotrport.society.Owner;
import com.porcupinestudios.lotrport.society.Person;
import com.porcupinestudios.lotrport.society.Persons;
import com.porcupinestudios.lotrport.society.Society;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Every colony (RingRealms, built in): founding, growth and citizens. Saved in the overworld's data storage. Once a
 * colony day ({@link ColonyData.Settings#dayTicks}), migrants come to a loaded Hall while it has free beds near it;
 * who comes is drawn from its faction's migration table. With selective migration (the default) they wait at the Hall
 * as applicants until the owner accepts or turns them away ({@link Migrants}). Rare races join only by
 * {@link Recruiting}. Every change is written to the history log.
 */
public final class Colonies extends SavedData {
    public static final String FILE = "lotr_port_colonies";
    public static final int DATA_VERSION = 2;
    public static final ResourceLocation FOUNDED_HISTORY = LotrPort.id("colony_founded");
    public static final ResourceLocation ARRIVE_HISTORY = LotrPort.id("citizen_arrive");
    public static final ResourceLocation DIED_HISTORY = LotrPort.id("citizen_die");
    public static final ResourceLocation TRANSFER_HISTORY = LotrPort.id("colony_transferred");
    public static final ResourceLocation APPLICANT_HISTORY = LotrPort.id("migrant_offer");
    public static final ResourceLocation DENIED_HISTORY = LotrPort.id("migrant_leave");
    private static final SavedData.Factory<Colonies> FACTORY = new SavedData.Factory<>(Colonies::new, Colonies::load,
            null);

    private final Map<UUID, Colony> colonies = new LinkedHashMap<>();
    /** Citizens and applicants read from a version 1 save, moved into {@link Persons} on first use. */
    private final List<CompoundTag> legacyPeople = new ArrayList<>();

    public static Colonies get(MinecraftServer server) {
        Colonies c = server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
        if (!c.legacyPeople.isEmpty()) {
            c.migratePeople(server);
        }
        return c;
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Colonies::onTick);
        NeoForge.EVENT_BUS.addListener(Colonies::onDied);
    }

    public Collection<Colony> all() {
        return colonies.values();
    }

    public Optional<Colony> get(UUID id) {
        return Optional.ofNullable(colonies.get(id));
    }

    public Optional<Colony> ofFounder(UUID player) {
        return colonies.values().stream().filter(c -> c.founder.equals(player)).findFirst();
    }

    /** The colony an entity lives in, if it is a citizen. */
    /** Researchers in the player's colony: every rare citizen, plus one per {@code citizens_per_researcher} others. */
    public int researchers(MinecraftServer server, UUID player) {
        return ofFounder(player).map(c -> {
            int rare = (int) Society.rareCitizens(server, c);
            int per = ColonyData.settings().citizensPerResearcher();
            int common = Society.citizenCount(server, c) - rare;
            return rare + (per > 0 ? common / per : 0);
        }).orElse(0);
    }

    public static Optional<UUID> colonyOf(Entity entity) {
        String id = entity.getData(LotrRegistries.COLONY);
        try {
            return id.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The result of founding: the colony, or why not. */
    public record Founding(Optional<Colony> colony, Component problem) {}

    /** Founds a colony with its Hall at {@code hall} for {@code faction}. One colony per player. */
    public Founding found(ServerPlayer player, String name, BlockPos hall, ResourceLocation faction) {
        if (ofFounder(player.getUUID()).isPresent()) {
            return new Founding(Optional.empty(), Component.translatableWithFallback("colony.lotr_port.one_only",
                    "You have already founded a colony"));
        }
        int gap = ColonyData.settings().hallGap();
        for (Colony c : colonies.values()) {
            if (c.dimension.equals(player.level().dimension()) && c.hall.distSqr(hall) < (double) gap * gap) {
                return new Founding(Optional.empty(), Component.translatableWithFallback(
                        "colony.lotr_port.too_close", "Too close to %s", c.name));
            }
        }
        long now = player.server.overworld().getGameTime();
        Colony colony = new Colony(UUID.randomUUID(), name, player.getUUID(), faction, player.level().dimension(),
                hall.immutable(), now, now / ColonyData.settings().dayTicks(), Map.of(), false, 0, new ComponentMap());
        colonies.put(colony.id, colony);
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.putString("name", name);
        tag.putUUID("founder", player.getUUID());
        tag.putString("faction", faction.toString());
        log(player.server, FOUNDED_HISTORY, colony, hall, tag);
        player.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.founded",
                "You founded %s for %s", name, Component.translatable("faction." + faction.getNamespace() + "."
                        + faction.getPath())));
        return new Founding(Optional.of(colony), Component.empty());
    }

    /** Every colony owned by this player or AI ruler, oldest first. */
    public List<Colony> ownedBy(UUID owner) {
        return colonies.values().stream().filter(c -> c.founder.equals(owner))
                .sorted(Comparator.comparingLong((Colony c) -> c.founded).thenComparing(c -> c.id))
                .toList();
    }

    /** The nearest Hall in a dimension closer than the settings' {@code hall_gap}, if any. */
    public Optional<Colony> tooClose(ResourceKey<Level> dimension, BlockPos hall) {
        int gap = ColonyData.settings().hallGap();
        return colonies.values().stream()
                .filter(c -> c.dimension.equals(dimension) && c.hall.distSqr(hall) < (double) gap * gap)
                .findFirst();
    }

    /**
     * An AI ruler founds a sparse colony: no blocks and no bodies, just a Hall position and a counted population.
     * {@code id} is chosen by the caller so a replay of the same seed makes the same colony.
     */
    public Optional<Colony> foundAi(MinecraftServer server, UUID id, UUID ruler, String name,
            ResourceKey<Level> dimension, BlockPos hall, ResourceLocation faction, int population) {
        if (colonies.containsKey(id) || tooClose(dimension, hall).isPresent()) {
            return Optional.empty();
        }
        long now = server.overworld().getGameTime();
        Colony colony = new Colony(id, name, ruler, faction, dimension, hall.immutable(), now,
                now / ColonyData.settings().dayTicks(), Map.of(), true, Math.max(0, population),
                new ComponentMap());
        colonies.put(id, colony);
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", id);
        tag.putString("name", name);
        tag.putUUID("founder", ruler);
        tag.putString("faction", faction.toString());
        tag.putBoolean("ai", true);
        log(server, FOUNDED_HISTORY, colony, hall, tag);
        return Optional.of(colony);
    }

    /** Changes an AI colony's counted population by {@code delta} (never below 0). */
    public void growAi(Colony colony, int delta) {
        int next = Math.max(0, colony.virtualPopulation + delta);
        if (colony.ai && next != colony.virtualPopulation) {
            colony.virtualPopulation = next;
            setDirty();
        }
    }

    /**
     * Hands a colony to a new owner (a player or an AI ruler) and faction: conquest, sale or gift. {@code ai} says
     * whether the new owner is an AI ruler. Written to history as {@code colony_transferred} with the reason.
     */
    public void transfer(MinecraftServer server, Colony colony, UUID owner, ResourceLocation faction, boolean ai,
            String reason) {
        UUID from = colony.founder;
        colony.founder = owner;
        colony.faction = faction;
        colony.ai = ai;
        setDirty();
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.putUUID("from", from);
        tag.putUUID("to", owner);
        tag.putString("faction", faction.toString());
        tag.putBoolean("ai", ai);
        tag.putString("reason", reason);
        log(server, TRANSFER_HISTORY, colony, colony.hall, tag);
    }

    /** Beds (head halves) near the Hall: each is a home. Counts loaded chunks only. */
    public static int beds(ServerLevel level, Colony colony) {
        ColonyData.Settings s = ColonyData.settings();
        int r = s.bedRadius();
        int h = s.bedHeight();
        int beds = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (x * x + z * z > r * r || !level.hasChunkAt(colony.hall.offset(x, 0, z))) {
                    continue;
                }
                for (int y = -h; y <= h; y++) {
                    p.set(colony.hall.getX() + x, colony.hall.getY() + y, colony.hall.getZ() + z);
                    BlockState state = level.getBlockState(p);
                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                        beds++;
                    }
                }
            }
        }
        return beds;
    }

    /**
     * One colony day: migrants come while there are free beds (and the first settlers before any). With selective
     * migration they wait at the Hall as applicants, and those who waited too long leave. Returns how many came.
     */
    public int day(ServerLevel level, Colony colony) {
        ColonyData.Settings s = ColonyData.settings();
        if (s.selectiveMigration()) {
            Migrants.age(this, level, colony);
        }
        int homes = homes(level, colony);
        MinecraftServer server = level.getServer();
        int waiting = s.selectiveMigration() ? Society.applicants(server, colony).size() : 0;
        int room = homes - Society.citizenCount(server, colony) - waiting;
        if (s.selectiveMigration()) {
            room = Math.min(room, s.maxApplicants() - waiting);
        }
        int arrivals = Math.max(0, Math.min(room, s.arrivalsPerDay()));
        int came = 0;
        for (int i = 0; i < arrivals; i++) {
            Optional<ResourceLocation> type = pickMigrant(colony, level.random);
            if (type.isEmpty()) {
                break;
            }
            Optional<LotrNpc> npc = s.selectiveMigration() ? Migrants.offer(this, level, colony, type.get())
                    : arrive(level, colony, type.get());
            if (npc.isEmpty()) {
                break;
            }
            came++;
        }
        Recruiting.tryJoinAll(level.getServer(), colony);
        return came;
    }

    /** Homes a colony has: its beds, or the first settlers' places before there are enough beds. */
    public static int homes(ServerLevel level, Colony colony) {
        return Math.max(beds(level, colony), ColonyData.settings().firstSettlers());
    }

    private static Optional<ResourceLocation> pickMigrant(Colony colony, RandomSource random) {
        Optional<ColonyData.Migration> table = ColonyData.migration(colony.faction);
        if (table.isEmpty() || table.get().entries().isEmpty()) {
            return Optional.empty();
        }
        int total = table.get().entries().stream().mapToInt(e -> Math.max(0, e.weight())).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (ColonyData.Migration.Entry e : table.get().entries()) {
            roll -= Math.max(0, e.weight());
            if (roll < 0) {
                return Optional.of(e.npc());
            }
        }
        return Optional.empty();
    }

    /** A newcomer of {@code type} walks in next to the Hall and joins. */
    public Optional<LotrNpc> arrive(ServerLevel level, Colony colony, ResourceLocation type) {
        Optional<LotrNpc> npc = spawnAtHall(level, colony, type);
        npc.ifPresent(n -> join(level.getServer(), colony, n, false));
        return npc;
    }

    /** Spawns an NPC of {@code type} next to the Hall. */
    static Optional<LotrNpc> spawnAtHall(ServerLevel level, Colony colony, ResourceLocation type) {
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                colony.hall.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2));
        return LotrNpc.spawn(level, type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
    }

    /**
     * Makes an NPC a citizen: it stays (never despawns), is marked with the colony, and its Person is owned by the
     * colony with a {@link CoreComponents#CITIZEN} (an applicant's record carries over).
     */
    public void join(MinecraftServer server, Colony colony, LotrNpc npc, boolean rare) {
        npc.setPersistenceRequired();
        npc.setData(LotrRegistries.COLONY, colony.id.toString());
        Persons persons = Persons.get(server);
        Person person = Society.recordOf(server, npc);
        long now = server.overworld().getGameTime();
        persons.setQuiet(person, CoreComponents.APPLICANT, null);
        persons.set(server, person, CoreComponents.CITIZEN, new CoreComponents.Citizen(now, rare));
        persons.setOwner(server, person, colony.asOwner(), "joined");
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        CompoundTag citizen = new CompoundTag();
        citizen.putUUID("entity", npc.getUUID());
        citizen.putString("type", npc.typeId().toString());
        citizen.putString("name", npc.personName());
        citizen.putLong("joined", now);
        citizen.putBoolean("rare", rare);
        tag.put("citizen", citizen);
        log(server, ARRIVE_HISTORY, colony, npc.blockPosition(), tag);
    }

    void changed() {
        setDirty();
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 100 != 0) {
            return;
        }
        Colonies all = get(server);
        long day = server.overworld().getGameTime() / Math.max(1, ColonyData.settings().dayTicks());
        for (Colony c : new ArrayList<>(all.colonies.values())) {
            ServerLevel level = server.getLevel(c.dimension);
            if (c.ai || level == null || c.lastDay >= day || !level.hasChunkAt(c.hall)) {
                continue;
            }
            c.lastDay = day;
            all.setDirty();
            all.day(level, c);
        }
    }

    /** A citizen's Person died: history {@code citizen_die} (the Person stays in the registry, marked dead). */
    public static void onDied(SocietyEvent.Died event) {
        Person p = event.person();
        if (!p.has(CoreComponents.CITIZEN)) {
            return;
        }
        p.owner().colonyId().flatMap(id -> get(event.server()).get(id)).ifPresent(c -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("colony", c.id);
            tag.putUUID("entity", p.id());
            log(event.server(), DIED_HISTORY, c, event.where().map(GlobalPos::pos).orElse(c.hall), tag);
        });
    }

    static void log(MinecraftServer server, ResourceLocation type, Colony colony, BlockPos pos,
            CompoundTag tag) {
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, colony.dimension,
                Optional.of(pos), tag));
    }

    /** Moves version 1 citizens and applicants into {@link Persons}, owned by their colony (also run by {@link #get}). */
    public void migratePeople(MinecraftServer server) {
        Persons persons = Persons.get(server);
        int moved = 0;
        for (CompoundTag legacy : legacyPeople) {
            Optional<UUID> colony = UUIDUtil.CODEC.parse(NbtOps.INSTANCE, legacy.get("colony")).result();
            if (colony.isEmpty() || !colonies.containsKey(colony.get())) {
                continue;
            }
            Owner owner = Owner.colony(colony.get());
            for (Tag e : legacy.getList("citizens", Tag.TAG_COMPOUND)) {
                moved += legacyPerson(persons, owner, (CompoundTag) e, true);
            }
            for (Tag e : legacy.getList("applicants", Tag.TAG_COMPOUND)) {
                moved += legacyPerson(persons, owner, (CompoundTag) e, false);
            }
        }
        legacyPeople.clear();
        setDirty();
        LotrPort.LOG.info("[colonies] moved {} citizens and applicants into the person registry", moved);
    }

    private static int legacyPerson(Persons persons, Owner owner, CompoundTag t, boolean citizen) {
        Optional<UUID> id = t.hasUUID("entity") ? Optional.of(t.getUUID("entity"))
                : UUIDUtil.CODEC.parse(NbtOps.INSTANCE, t.get("entity")).result();
        ResourceLocation type = ResourceLocation.tryParse(t.getString("type"));
        if (id.isEmpty() || type == null) {
            return 0;
        }
        long joined = t.getLong("joined");
        Person p = persons.migrated(id.get(), type, t.getString("name"), joined, owner);
        if (citizen) {
            persons.setQuiet(p, CoreComponents.CITIZEN, new CoreComponents.Citizen(joined, t.getBoolean("rare")));
        } else {
            persons.setQuiet(p, CoreComponents.APPLICANT, new CoreComponents.Applicant(t.getInt("days")));
        }
        return 1;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("dataVersion", DATA_VERSION);
        tag.put("colonies", Codec.list(Colony.CODEC).encodeStart(NbtOps.INSTANCE, List.copyOf(colonies.values()))
                .getOrThrow());
        return tag;
    }

    public static Colonies load(CompoundTag tag, HolderLookup.Provider registries) {
        Colonies out = new Colonies();
        if (!tag.contains("colonies")) {
            return out;
        }
        if (tag.getInt("dataVersion") < 2) {
            // Version 1 kept citizens and applicants in each colony; they become Persons (see migratePeople).
            for (Tag e : tag.getList("colonies", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                CompoundTag legacy = new CompoundTag();
                legacy.put("colony", c.get("id") == null ? new CompoundTag() : c.get("id").copy());
                legacy.put("citizens", c.getList("citizens", Tag.TAG_COMPOUND).copy());
                legacy.put("applicants", c.getList("applicants", Tag.TAG_COMPOUND).copy());
                out.legacyPeople.add(legacy);
            }
        }
        Codec.list(Colony.CODEC).parse(NbtOps.INSTANCE, tag.get("colonies"))
                .resultOrPartial(e -> LotrPort.LOG.error("[colonies] {}", e))
                .ifPresent(list -> list.forEach(c -> out.colonies.put(c.id, c)));
        return out;
    }
}
