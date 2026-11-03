package com.porcupinestudios.tardisrb.core;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.tardisrb.event.DamageEvent;
import com.porcupinestudios.tardisrb.event.MilestoneEvent;
import com.porcupinestudios.tardisrb.event.RepairEvent;
import com.porcupinestudios.tardisrb.event.ResearchEvent;
import com.porcupinestudios.tardisrb.event.RoomEvent;
import com.porcupinestudios.tardisrb.event.StageEvent;
import com.porcupinestudios.tardisrb.progression.Repair;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

/**
 * One TARDIS: who owns it, how far each subsystem is repaired and damaged, its stage, milestones, finished repairs and
 * research, stored charge, and extra data per module. Blocks, screens and commands keep only its id and look it up
 * in {@link TardisManager}. Every change goes through a method here, which fires its event and marks the save dirty.
 */
public final class TardisRecord {
    /** Save format version; {@link TardisManager} migrates older records on load. */
    public static final int DATA_VERSION = 1;

    private static final Codec<Set<ResourceLocation>> ID_SET =
            ResourceLocation.CODEC.listOf().xmap(l -> new LinkedHashSet<>(l), s -> List.copyOf(s));

    /** A subsystem's saved state: highest repaired level, and levels lost to damage. */
    public record SubsystemState(int level, int damage) {
        static final Codec<SubsystemState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("level", 0).forGetter(SubsystemState::level),
                Codec.INT.optionalFieldOf("damage", 0).forGetter(SubsystemState::damage))
                .apply(i, SubsystemState::new));
    }

    /** Research under way: the node and the overworld game time it finishes. */
    public record ResearchJob(ResourceLocation node, long finishAt) {
        static final Codec<ResearchJob> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("node").forGetter(ResearchJob::node),
                Codec.LONG.fieldOf("finish_at").forGetter(ResearchJob::finishAt))
                .apply(i, ResearchJob::new));
    }

    /** A room added behind the console room; {@code slot} 1 is the first behind it. */
    public record PlacedRoom(ResourceLocation room, int slot) {
        static final Codec<PlacedRoom> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("room").forGetter(PlacedRoom::room),
                Codec.INT.fieldOf("slot").forGetter(PlacedRoom::slot))
                .apply(i, PlacedRoom::new));
    }

    /** The first 16 fields (a codec group holds at most 16); {@link #CODEC} adds the rest beside them, flat. */
    private static final MapCodec<TardisRecord> BASE = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("data_version", DATA_VERSION).forGetter(r -> DATA_VERSION),
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(r -> r.id),
            Codec.STRING.optionalFieldOf("name", "").forGetter(r -> r.name),
            UUIDUtil.STRING_CODEC.optionalFieldOf("owner").forGetter(r -> r.owner),
            UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("crew", List.of()).forGetter(r -> r.crew),
            Codec.BOOL.optionalFieldOf("claimed", true).forGetter(r -> r.claimed),
            ResourceLocation.CODEC.optionalFieldOf("stage").forGetter(r -> r.stage),
            ID_SET.optionalFieldOf("milestones", Set.of()).forGetter(r -> r.milestones),
            ID_SET.optionalFieldOf("repairs", Set.of()).forGetter(r -> r.repairs),
            ID_SET.optionalFieldOf("research", Set.of()).forGetter(r -> r.research),
            Codec.unboundedMap(ResourceLocation.CODEC, SubsystemState.CODEC).optionalFieldOf("subsystems", Map.of())
                    .forGetter(TardisRecord::subsystemStates),
            ResearchJob.CODEC.optionalFieldOf("researching").forGetter(r -> r.researching),
            Codec.LONG.optionalFieldOf("charge", 0L).forGetter(r -> r.charge),
            Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC).optionalFieldOf("modules", Map.of())
                    .forGetter(r -> r.modules),
            GlobalPos.CODEC.optionalFieldOf("location").forGetter(r -> r.location),
            Codec.INT.optionalFieldOf("plot", -1).forGetter(r -> r.plot))
            .apply(i, TardisRecord::new));

    public static final Codec<TardisRecord> CODEC = Codec.mapPair(BASE,
            PlacedRoom.CODEC.listOf().optionalFieldOf("rooms", List.of()))
            .xmap(p -> {
                p.getFirst().rooms.addAll(p.getSecond());
                return p.getFirst();
            }, r -> Pair.of(r, List.copyOf(r.rooms)))
            .codec();

    private final UUID id;
    private String name;
    private Optional<UUID> owner;
    private final List<UUID> crew;
    private boolean claimed;
    private Optional<ResourceLocation> stage;
    private final Set<ResourceLocation> milestones;
    private final Set<ResourceLocation> repairs;
    private final Set<ResourceLocation> research;
    private final Map<ResourceLocation, Integer> levels;
    private final Map<ResourceLocation, Integer> damage;
    private Optional<ResearchJob> researching;
    private long charge;
    private final Map<String, CompoundTag> modules;
    /** Where the exterior stands; empty while it is in flight or not placed yet. */
    private Optional<GlobalPos> location;
    /** Its plot in the shared interior dimension; -1 until the interior is first built. */
    private int plot;
    private final List<PlacedRoom> rooms = new ArrayList<>();

    /** Goes up on every change; caches compare it. Not saved. */
    private int revision;
    private Runnable onChange = () -> {};

    private TardisRecord(int dataVersion, UUID id, String name, Optional<UUID> owner, List<UUID> crew,
            boolean claimed, Optional<ResourceLocation> stage, Set<ResourceLocation> milestones,
            Set<ResourceLocation> repairs, Set<ResourceLocation> research,
            Map<ResourceLocation, SubsystemState> subsystems, Optional<ResearchJob> researching, long charge, Map<String, CompoundTag> modules,
            Optional<GlobalPos> location, int plot) {
        this.id = id;
        this.name = name;
        this.owner = owner;
        this.crew = new ArrayList<>(crew);
        this.claimed = claimed;
        this.stage = stage;
        this.milestones = new LinkedHashSet<>(milestones);
        this.repairs = new LinkedHashSet<>(repairs);
        this.research = new LinkedHashSet<>(research);
        this.levels = new LinkedHashMap<>();
        this.damage = new LinkedHashMap<>();
        subsystems.forEach((sub, st) -> {
            if (st.level() > 0) {
                levels.put(sub, st.level());
            }
            if (st.damage() > 0) {
                damage.put(sub, st.damage());
            }
        });
        this.researching = researching;
        this.charge = charge;
        this.modules = new LinkedHashMap<>(modules);
        this.location = location;
        this.plot = plot;
    }

    static TardisRecord fresh(UUID id, String name, Optional<UUID> owner, boolean claimed) {
        return new TardisRecord(DATA_VERSION, id, name, owner, List.of(), claimed, Optional.empty(), Set.of(),
                Set.of(), Set.of(), Map.of(), Optional.empty(), 0L, Map.of(), Optional.empty(), -1);
    }

    void attach(Runnable onChange) {
        this.onChange = onChange;
    }

    private void changed() {
        revision++;
        onChange.run();
    }

    // ---------------------------------------------------------------- reading

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Optional<UUID> owner() {
        return owner;
    }

    public List<UUID> crew() {
        return Collections.unmodifiableList(crew);
    }

    public boolean claimed() {
        return claimed;
    }

    /** Owner or crew. */
    public boolean isCrew(UUID player) {
        return owner.map(player::equals).orElse(false) || crew.contains(player);
    }

    public Optional<ResourceLocation> stage() {
        return stage;
    }

    public Set<ResourceLocation> milestones() {
        return Collections.unmodifiableSet(milestones);
    }

    public Set<ResourceLocation> repairs() {
        return Collections.unmodifiableSet(repairs);
    }

    public Set<ResourceLocation> research() {
        return Collections.unmodifiableSet(research);
    }

    /** The highest level this subsystem was ever repaired to. Use {@link Unlocks#level} for the level that counts. */
    public int repairedLevel(ResourceLocation subsystem) {
        return levels.getOrDefault(subsystem, 0);
    }

    /** Levels lost to damage, below the repaired level. */
    public int damage(ResourceLocation subsystem) {
        return damage.getOrDefault(subsystem, 0);
    }

    private Map<ResourceLocation, SubsystemState> subsystemStates() {
        Map<ResourceLocation, SubsystemState> out = new LinkedHashMap<>();
        levels.forEach((sub, l) -> out.put(sub, new SubsystemState(l, damage(sub))));
        damage.forEach((sub, d) -> out.putIfAbsent(sub, new SubsystemState(0, d)));
        return out;
    }

    public Optional<ResearchJob> researching() {
        return researching;
    }

    public long charge() {
        return charge;
    }

    public Optional<GlobalPos> location() {
        return location;
    }

    public int plot() {
        return plot;
    }

    /** Rooms added behind the console room, in the order they were built. */
    public List<PlacedRoom> rooms() {
        return Collections.unmodifiableList(rooms);
    }

    /** All rooms including the console room. */
    public int roomCount() {
        return 1 + rooms.size();
    }

    public int revision() {
        return revision;
    }

    /** A module's own data (Souls, the bank...), or an empty tag. Change it with {@link #setModuleData}. */
    public CompoundTag moduleData(String module) {
        CompoundTag tag = modules.get(module);
        return tag == null ? new CompoundTag() : tag.copy();
    }

    // ---------------------------------------------------------------- changing

    public void rename(String newName) {
        name = newName;
        changed();
    }

    public void claim(UUID player) {
        owner = Optional.of(player);
        claimed = true;
        changed();
    }

    public void addCrew(UUID player) {
        if (!crew.contains(player)) {
            crew.add(player);
            changed();
        }
    }

    public void removeCrew(UUID player) {
        if (crew.remove(player)) {
            changed();
        }
    }

    /** Records where the exterior now stands. Moving the block itself is {@code Flight}'s job. */
    public void setLocation(Optional<GlobalPos> where) {
        location = where;
        changed();
    }

    /** Set once, by {@code TardisManager}, when the interior is first built. */
    void setPlot(int newPlot) {
        plot = newPlot;
        changed();
    }

    /** Records a room that {@code Rooms.add} already checked, paid for and built. Fires {@link RoomEvent.Post}. */
    public void addRoom(PlacedRoom room) {
        rooms.add(room);
        changed();
        NeoForge.EVENT_BUS.post(new RoomEvent.Post(this, room.room(), room.slot()));
    }

    public void setModuleData(String module, CompoundTag tag) {
        modules.put(module, tag.copy());
        changed();
    }

    /**
     * Applies a repair that was already checked and paid for ({@code Repairs.attempt} does both): the subsystem's
     * effective level becomes the repair's level, clearing damage first. Fires {@link RepairEvent.Post}.
     */
    public void applyRepair(ResourceLocation repairId, Repair repair) {
        ResourceLocation sub = repair.subsystem();
        int repaired = repairedLevel(sub);
        if (repair.level() <= repaired) {
            setOrClear(damage, sub, repaired - repair.level());
        } else {
            levels.put(sub, repair.level());
            damage.remove(sub);
        }
        repairs.add(repairId);
        changed();
        NeoForge.EVENT_BUS.post(new RepairEvent.Post(this, repairId, repair));
    }

    /**
     * Knocks a subsystem down by up to {@code amount} levels (never below 0). Finished repairs and research are kept;
     * the lost levels are repaired again with the same repairs. Fires {@link DamageEvent}; returns the levels lost.
     */
    public int damage(ResourceLocation subsystem, int amount) {
        DamageEvent event = NeoForge.EVENT_BUS.post(new DamageEvent(this, subsystem, amount));
        if (event.isCanceled()) {
            return 0;
        }
        int effective = repairedLevel(subsystem) - damage(subsystem);
        int lost = Math.min(event.amount(), Math.max(0, effective));
        if (lost <= 0) {
            return 0;
        }
        damage.put(subsystem, damage(subsystem) + lost);
        changed();
        return lost;
    }

    /** Starts a research job (checked and paid by {@code Researching.start}). */
    public void startResearch(ResearchJob job) {
        researching = Optional.of(job);
        changed();
    }

    /** Finishes a node; clears the job if it was the one under way. */
    public void completeResearch(ResourceLocation node) {
        if (researching.map(j -> j.node().equals(node)).orElse(false)) {
            researching = Optional.empty();
            changed();
        }
        if (research.add(node)) {
            changed();
            NeoForge.EVENT_BUS.post(new ResearchEvent.Post(this, node));
        }
    }

    public void setStage(ResourceLocation newStage) {
        if (stage.map(newStage::equals).orElse(false)) {
            return;
        }
        Optional<ResourceLocation> old = stage;
        stage = Optional.of(newStage);
        changed();
        NeoForge.EVENT_BUS.post(new StageEvent(this, old, newStage));
    }

    public void passMilestone(ResourceLocation milestone) {
        if (milestones.add(milestone)) {
            changed();
            NeoForge.EVENT_BUS.post(new MilestoneEvent(this, milestone));
        }
    }

    /** Adds charge up to the current cap; returns how much was stored. */
    public long addCharge(long amount) {
        long room = Math.max(0, Unlocks.chargeCap(this) - charge);
        long added = Math.max(0, Math.min(amount, room));
        if (added > 0) {
            charge += added;
            changed();
        }
        return added;
    }

    /** Takes charge if there is enough. */
    public boolean useCharge(long amount) {
        if (amount <= 0) {
            return true;
        }
        if (charge < amount) {
            return false;
        }
        charge -= amount;
        changed();
        return true;
    }

    private static void setOrClear(Map<ResourceLocation, Integer> map, ResourceLocation key, int value) {
        if (value <= 0) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }
}
