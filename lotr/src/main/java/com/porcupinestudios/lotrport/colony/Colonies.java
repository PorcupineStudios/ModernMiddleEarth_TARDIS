package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.group.Group;
import com.porcupinestudios.lotrport.group.GroupId;
import com.porcupinestudios.lotrport.group.GroupKinds;
import com.porcupinestudios.lotrport.group.Groups;
import com.porcupinestudios.lotrport.group.Owner;
import com.porcupinestudios.lotrport.group.PersonId;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.npc.People;
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
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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
 *
 * <p>Each colony is also a {@link Group} with the colony's id (kind {@link GroupKinds#COLONY}): its owner and its
 * citizens are mirrored there by this class, the only writer of both, so colonies and hired units share one
 * structure and "whose colonies are these" is a map lookup. A player may own several colonies.
 */
public final class Colonies extends SavedData {
    public static final String FILE = "lotr_port_colonies";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation FOUNDED_HISTORY = LotrPort.id("colony_founded");
    public static final ResourceLocation ARRIVE_HISTORY = LotrPort.id("citizen_arrive");
    public static final ResourceLocation DIED_HISTORY = LotrPort.id("citizen_die");
    public static final ResourceLocation TRANSFER_HISTORY = LotrPort.id("colony_transferred");
    public static final ResourceLocation APPLICANT_HISTORY = LotrPort.id("migrant_offer");
    public static final ResourceLocation DENIED_HISTORY = LotrPort.id("migrant_leave");
    private static final SavedData.Factory<Colonies> FACTORY = new SavedData.Factory<>(Colonies::new, Colonies::load,
            null);

    private final Map<UUID, Colony> colonies = new LinkedHashMap<>();
    /** The group store, set on first use through {@link #get(MinecraftServer)}; null on a bare loaded copy. */
    private Groups groups;

    public static Colonies get(MinecraftServer server) {
        Colonies all = server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
        if (all.groups == null) {
            all.groups = Groups.get(server);
            all.syncGroups();
        }
        return all;
    }

    /** Gives every colony its group and every citizen their membership (worlds saved before groups existed). */
    private void syncGroups() {
        for (Colony c : colonies.values()) {
            Group g = group(c);
            for (Colony.Citizen ci : c.citizens) {
                PersonId person = PersonId.of(ci.entity());
                if (g.member(person).isEmpty()) {
                    groups.join(g, person, GroupKinds.ROLE_CITIZEN, ci.joined(), flags(ci.rare()));
                }
            }
        }
    }

    /** The colony's group, made if it has none yet. Only call when {@link #groups} is set. */
    private Group group(Colony c) {
        return groups.ensure(GroupId.of(c.id), GroupKinds.COLONY, owner(c.founder, c.ai), c.name, c.founded);
    }

    private static Owner owner(UUID id, boolean ai) {
        return ai ? Owner.aiRuler(id) : Owner.player(id);
    }

    private static Set<String> flags(boolean rare) {
        return rare ? Set.of(GroupKinds.FLAG_RARE) : Set.of();
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Colonies::onTick);
        NeoForge.EVENT_BUS.addListener(Colonies::onDeath);
    }

    public Collection<Colony> all() {
        return colonies.values();
    }

    public Optional<Colony> get(UUID id) {
        return Optional.ofNullable(colonies.get(id));
    }

    /** The owner's oldest colony. A player may own several: see {@link #ownedBy} and {@link #nearest}. */
    public Optional<Colony> ofFounder(UUID player) {
        return ownedBy(player).stream().findFirst();
    }

    /** The colony a player's action is about: the nearest one they own in their dimension, else their oldest. */
    public Optional<Colony> nearest(ServerPlayer player) {
        List<Colony> mine = ownedBy(player.getUUID());
        BlockPos at = player.blockPosition();
        return mine.stream().filter(c -> c.dimension.equals(player.level().dimension()))
                .min(Comparator.comparingDouble((Colony c) -> c.hall.distSqr(at)))
                .or(() -> mine.stream().findFirst());
    }

    /** The colony an entity lives in, if it is a citizen. */
    /**
     * Researchers in the player's colonies: every rare citizen, plus one per {@code citizens_per_researcher} others
     * in each colony.
     */
    public int researchers(UUID player) {
        int per = ColonyData.settings().citizensPerResearcher();
        int total = 0;
        for (Colony c : ownedBy(player)) {
            int rare = (int) c.citizens.stream().filter(Colony.Citizen::rare).count();
            int common = c.citizens.size() - rare;
            total += rare + (per > 0 ? common / per : 0);
        }
        return total;
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

    /**
     * Founds a colony with its Hall at {@code hall} for {@code faction}. A player may found several (Aaron,
     * 2026-11-10), up to the settings' {@code max_colonies} (0 = no limit).
     */
    public Founding found(ServerPlayer player, String name, BlockPos hall, ResourceLocation faction) {
        int max = ColonyData.settings().maxColonies();
        if (max > 0 && ownedBy(player.getUUID()).size() >= max) {
            return new Founding(Optional.empty(), Component.translatableWithFallback("colony.lotr_port.max_colonies",
                    "You cannot found more than %s colonies", max));
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
                hall.immutable(), now, now / ColonyData.settings().dayTicks(), List.of(), Map.of(), false, 0, List.of());
        colonies.put(colony.id, colony);
        setDirty();
        if (groups != null) {
            group(colony);
        }
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
        if (groups != null) {
            List<Colony> out = new ArrayList<>();
            for (Group g : groups.ownedBy(owner, GroupKinds.COLONY)) {
                Colony c = colonies.get(g.id().value());
                if (c != null) {
                    out.add(c);
                }
            }
            return out;
        }
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
                now / ColonyData.settings().dayTicks(), List.of(), Map.of(), true, Math.max(0, population),
                List.of());
        colonies.put(id, colony);
        setDirty();
        if (groups != null) {
            group(colony);
        }
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
        if (groups != null) {
            groups.setOwner(group(colony), owner(owner, ai));
        }
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
        int waiting = s.selectiveMigration() ? colony.applicants.size() : 0;
        int room = homes - colony.citizens.size() - waiting;
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

    /** Makes an NPC a citizen: it stays (never despawns), is marked with the colony and recorded. */
    public void join(MinecraftServer server, Colony colony, LotrNpc npc, boolean rare) {
        npc.setPersistenceRequired();
        npc.setData(LotrRegistries.COLONY, colony.id.toString());
        Colony.Citizen citizen = new Colony.Citizen(npc.getUUID(), npc.typeId(), npc.personName(),
                server.overworld().getGameTime(), rare);
        colony.citizens.add(citizen);
        setDirty();
        if (groups != null) {
            groups.join(group(colony), PersonId.of(npc.getUUID()), GroupKinds.ROLE_CITIZEN, citizen.joined(),
                    flags(rare));
        }
        People.get(server).settled(server, npc);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id);
        tag.put("citizen", Colony.Citizen.CODEC.encodeStart(NbtOps.INSTANCE, citizen).getOrThrow());
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

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead.level().isClientSide) {
            return;
        }
        colonyOf(dead).ifPresent(id -> {
            MinecraftServer server = dead.getServer();
            Colonies all = get(server);
            all.get(id).ifPresent(c -> {
                if (c.citizens.removeIf(ci -> ci.entity().equals(dead.getUUID()))) {
                    all.setDirty();
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("colony", c.id);
                    tag.putUUID("entity", dead.getUUID());
                    log(server, DIED_HISTORY, c, dead.blockPosition(), tag);
                }
            });
        });
    }

    static void log(MinecraftServer server, ResourceLocation type, Colony colony, BlockPos pos,
            CompoundTag tag) {
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, colony.dimension,
                Optional.of(pos), tag));
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
        Codec.list(Colony.CODEC).parse(NbtOps.INSTANCE, tag.get("colonies"))
                .resultOrPartial(e -> LotrPort.LOG.error("[colonies] {}", e))
                .ifPresent(list -> list.forEach(c -> out.colonies.put(c.id, c)));
        return out;
    }
}
