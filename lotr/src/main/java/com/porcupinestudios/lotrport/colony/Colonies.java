package com.porcupinestudios.lotrport.colony;

import com.mojang.serialization.Codec;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * colony day ({@link ColonyData.Settings#dayTicks}), a colony whose Hall is loaded takes in newcomers while it has
 * free beds near the Hall; who comes is drawn from its faction's migration table. Rare races join only by
 * {@link Recruiting}. Every change is written to the history log.
 */
public final class Colonies extends SavedData {
    public static final String FILE = "lotr_port_colonies";
    public static final int DATA_VERSION = 1;
    public static final ResourceLocation FOUNDED_HISTORY = LotrPort.id("colony_founded");
    public static final ResourceLocation ARRIVE_HISTORY = LotrPort.id("citizen_arrive");
    public static final ResourceLocation DIED_HISTORY = LotrPort.id("citizen_die");
    private static final SavedData.Factory<Colonies> FACTORY = new SavedData.Factory<>(Colonies::new, Colonies::load,
            null);

    private final Map<UUID, Colony> colonies = new LinkedHashMap<>();

    public static Colonies get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE);
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

    public Optional<Colony> ofFounder(UUID player) {
        return colonies.values().stream().filter(c -> c.founder.equals(player)).findFirst();
    }

    /** The colony an entity lives in, if it is a citizen. */
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
                hall.immutable(), now, now / ColonyData.settings().dayTicks(), List.of(), Map.of());
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

    /** One colony day: newcomers while there are free beds (and the first settlers before any). Returns arrivals. */
    public int day(ServerLevel level, Colony colony) {
        ColonyData.Settings s = ColonyData.settings();
        int beds = beds(level, colony);
        int living = colony.citizens.size();
        int room = Math.max(beds, s.firstSettlers()) - living;
        int arrivals = Math.max(0, Math.min(room, s.arrivalsPerDay()));
        int came = 0;
        for (int i = 0; i < arrivals; i++) {
            Optional<ResourceLocation> type = pickMigrant(colony, level.random);
            if (type.isEmpty() || arrive(level, colony, type.get()).isEmpty()) {
                break;
            }
            came++;
        }
        Recruiting.tryJoinAll(level.getServer(), colony);
        return came;
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

    /** A newcomer of {@code type} walks in next to the Hall. */
    public Optional<LotrNpc> arrive(ServerLevel level, Colony colony, ResourceLocation type) {
        BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                colony.hall.offset(level.random.nextInt(5) - 2, 0, level.random.nextInt(5) - 2));
        Optional<LotrNpc> npc = LotrNpc.spawn(level, type, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        npc.ifPresent(n -> join(level.getServer(), colony, n, false));
        return npc;
    }

    /** Makes an NPC a citizen: it stays (never despawns), is marked with the colony and recorded. */
    public void join(MinecraftServer server, Colony colony, LotrNpc npc, boolean rare) {
        npc.setPersistenceRequired();
        npc.setData(LotrRegistries.COLONY, colony.id.toString());
        Colony.Citizen citizen = new Colony.Citizen(npc.getUUID(), npc.typeId(), npc.personName(),
                server.overworld().getGameTime(), rare);
        colony.citizens.add(citizen);
        setDirty();
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
            if (level == null || c.lastDay >= day || !level.hasChunkAt(c.hall)) {
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

    private static void log(MinecraftServer server, ResourceLocation type, Colony colony, BlockPos pos,
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
