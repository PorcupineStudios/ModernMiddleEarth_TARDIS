package com.porcupinestudios.lotrport.colony.building;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.colony.Colonies;
import com.porcupinestudios.lotrport.colony.Colony;
import com.porcupinestudios.lotrport.colony.members.Perm;
import com.porcupinestudios.lotrport.colony.members.Permissions;
import com.porcupinestudios.lotrport.content.GateBlock;
import com.porcupinestudios.lotrport.land.Claims;
import com.porcupinestudios.lotrport.society.SocietyEvent;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Colony buildings (RingRealms' writs and validator): measure the room behind a door, make it a building of a type
 * that fits, measure buildings again now and then, and lose those that no longer fit. History {@code building_added},
 * {@code building_lost}, {@code building_retyped}; events {@link Added}, {@link Lost}.
 */
public final class ColonyBuildings {
    public static final ResourceLocation ADDED_HISTORY = LotrPort.id("building_added");
    public static final ResourceLocation LOST_HISTORY = LotrPort.id("building_lost");
    public static final ResourceLocation RETYPED_HISTORY = LotrPort.id("building_retyped");

    private ColonyBuildings() {}

    /** A building was made (or its door measured again into a new building). */
    public static final class Added extends SocietyEvent.OfColony {
        private final Building building;

        public Added(MinecraftServer server, Colony colony, Building building) {
            super(server, colony);
            this.building = building;
        }

        public Building building() {
            return building;
        }
    }

    /** A building no longer fits its type (or any) and was removed. */
    public static final class Lost extends SocietyEvent.OfColony {
        private final Building building;

        public Lost(MinecraftServer server, Colony colony, Building building) {
            super(server, colony);
            this.building = building;
        }

        public Building building() {
            return building;
        }
    }

    /** The doorway at a clicked block: its blocks and the open blocks beside it at its foot. */
    public record Doorway(BlockPos foot, Set<BlockPos> blocks, List<BlockPos> sides) {}

    /** Doors (both halves), fence gates and LOTR gates (the clicked column). */
    public static Optional<Doorway> doorway(Level level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        Set<BlockPos> blocks = new LinkedHashSet<>();
        BlockPos foot;
        if (state.getBlock() instanceof DoorBlock) {
            foot = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? clicked.below() : clicked;
            blocks.add(foot);
            blocks.add(foot.above());
        } else if (state.getBlock() instanceof FenceGateBlock) {
            foot = clicked;
            blocks.add(foot);
        } else if (state.getBlock() instanceof GateBlock) {
            BlockPos p = clicked;
            while (level.getBlockState(p.below()).is(state.getBlock())) {
                p = p.below();
            }
            foot = p;
            for (BlockPos q = foot; level.getBlockState(q).is(state.getBlock()) && blocks.size() < 16; q = q.above()) {
                blocks.add(q);
            }
        } else {
            return Optional.empty();
        }
        List<BlockPos> sides = new ArrayList<>();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up < 2; up++) {
                BlockPos s = foot.relative(d).above(up);
                if (!blocks.contains(s) && RoomScan.open(level, s, level.getBlockState(s))) {
                    sides.add(s);
                    break;
                }
            }
        }
        return Optional.of(new Doorway(foot, blocks, sides));
    }

    /**
     * Measures the room behind a doorway. Each open side is tried; of the closed rooms found, the one the player isn't
     * standing in wins (so a writ used from outside measures the inside), else the largest.
     */
    public static RoomScan.Room measure(Level level, Doorway door, BlockPos viewer) {
        BuildingSettings s = BuildingSettings.get();
        Set<String> counters = BuildingType.counters();
        RoomScan.Room best = null;
        RoomScan.Room failed = null;
        for (BlockPos side : door.sides()) {
            RoomScan.Room r = RoomScan.scan(level, side, door.blocks(), s, counters);
            if (!r.ok()) {
                failed = failed == null ? r : failed;
                continue;
            }
            boolean viewerIn = viewer != null && (r.cells().contains(viewer.asLong())
                    || r.cells().contains(viewer.above().asLong()));
            boolean bestViewerIn = best != null && viewer != null && (best.cells().contains(viewer.asLong())
                    || best.cells().contains(viewer.above().asLong()));
            if (best == null || (bestViewerIn && !viewerIn)
                    || (bestViewerIn == viewerIn && r.cells().size() > best.cells().size())) {
                best = r;
            }
        }
        if (best != null) {
            return best;
        }
        return failed != null ? failed : RoomScan.Room.fail("colony.lotr_port.room.no_side",
                "There is no open space beside this door");
    }

    /** What making a building gave: the building, or why not. */
    public record Result(Optional<Building> building, Component message) {
        static Result fail(String key, String fallback, Object... args) {
            return new Result(Optional.empty(), Component.translatableWithFallback(key, fallback, args)
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** The colony a player may put a building in at {@code pos}: the nearest they may build for, within reach. */
    public static Optional<Colony> colonyFor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        int reach = BuildingSettings.get().reach();
        return Colonies.get(player.server).all().stream()
                .filter(c -> c.dimension().equals(level.dimension()) && !c.ai()
                        && Permissions.can(player.getUUID(), c, Perm.BUILD)
                        && c.hall().distSqr(pos) <= (double) reach * reach)
                .min(Comparator.comparingDouble(c -> c.hall().distSqr(pos)));
    }

    /**
     * Makes (or measures again) the building behind the door at {@code clicked}. {@code wanted} = the type the writ
     * names, else the best type that fits.
     */
    public static Result add(ServerPlayer player, ServerLevel level, BlockPos clicked, Optional<ResourceLocation> wanted,
            Optional<String> name) {
        Optional<Doorway> door = doorway(level, clicked);
        if (door.isEmpty()) {
            return Result.fail("colony.lotr_port.building.not_door", "Use the writ on the building's door or gate");
        }
        Optional<Colony> colony = colonyFor(player, level, door.get().foot());
        if (colony.isEmpty()) {
            return Result.fail("colony.lotr_port.building.no_colony",
                    "This isn't within %s blocks of a Hall of a colony you may build for", BuildingSettings.get().reach());
        }
        Colony c = colony.get();
        var claim = Claims.get(player.server).at(level.dimension(), door.get().foot());
        if (claim.isPresent() && !claim.get().owner().equals(c.asOwner())
                && !claim.get().owner().equals(com.porcupinestudios.lotrport.society.Owner.player(player.getUUID()))) {
            return Result.fail("colony.lotr_port.building.foreign_land", "This land belongs to someone else");
        }
        RoomScan.Room room = measure(level, door.get(), player.blockPosition());
        if (!room.ok()) {
            return new Result(Optional.empty(), room.problem().copy().withStyle(ChatFormatting.RED));
        }
        Buildings all = Buildings.of(c);
        Optional<Building> existing = all.atDoor(door.get().foot());
        for (Building other : all.list()) {
            if (existing.isPresent() && other.id().equals(existing.get().id())) {
                continue;
            }
            for (long l : other.blocks()) {
                if (room.cells().contains(l)) {
                    return Result.fail("colony.lotr_port.building.overlap", "This room is already part of %s",
                            other.label());
                }
            }
        }
        List<ResourceLocation> fits = BuildingType.fitting(room.counts());
        ResourceLocation type;
        if (wanted.isPresent()) {
            Optional<BuildingType> t = BuildingType.get(wanted.get());
            if (t.isEmpty()) {
                return Result.fail("colony.lotr_port.building.unknown_type", "Unknown building type %s", wanted.get());
            }
            Optional<BuildingType.Requirement> miss = t.get().missing(room.counts());
            if (miss.isPresent()) {
                return new Result(Optional.empty(), Component.translatableWithFallback(
                        "colony.lotr_port.building.missing", "Not a %s: %s", t.get().title(wanted.get()),
                        miss.get().why()).withStyle(ChatFormatting.RED));
            }
            type = wanted.get();
        } else if (fits.isEmpty()) {
            return new Result(Optional.empty(), Component.translatableWithFallback(
                    "colony.lotr_port.building.nothing_fits", "No building fits this room (%s)",
                    describe(room.counts())).withStyle(ChatFormatting.RED));
        } else {
            type = existing.map(Building::type).filter(fits::contains).orElse(fits.get(0));
        }
        Building.Packed packed = Building.pack(room.cells());
        if (packed == null) {
            return Result.fail("colony.lotr_port.room.leaks", "The room is open to the outside, or too big");
        }
        long now = level.getGameTime();
        int quality = quality(room.counts());
        Building b = existing.map(e -> e.measured(type, packed.origin(), packed.cells(), room.counts(), quality, now))
                .orElseGet(() -> new Building(UUID.randomUUID(), type, door.get().foot(), name, packed.origin(),
                        packed.cells(), java.util.Map.copyOf(room.counts()), quality, now, now));
        if (existing.isEmpty() && name.isPresent()) {
            b = new Building(b.id(), b.type(), b.door(), name, b.origin(), b.cells(), b.counts(), b.quality(),
                    b.built(), b.checked());
        }
        Colonies.get(player.server).set(c, Buildings.TYPE, all.with(b));
        if (existing.isEmpty()) {
            log(player.server, ADDED_HISTORY, c, b, null);
            NeoForge.EVENT_BUS.post(new Added(player.server, c, b));
        }
        MutableComponent msg = Component.translatableWithFallback("colony.lotr_port.building.added",
                "%s: %s floor, quality %s", b.label(), b.floor(), b.quality()).withStyle(ChatFormatting.GREEN);
        if (wanted.isEmpty() && fits.size() > 1) {
            msg.append(Component.literal(" ").append(Component.translatableWithFallback(
                    "colony.lotr_port.building.could_be", "Could also be:").withStyle(ChatFormatting.GRAY)));
            for (ResourceLocation f : fits) {
                if (f.equals(type)) {
                    continue;
                }
                BlockPos d = b.door();
                msg.append(Component.literal(" [").append(BuildingType.titleOf(f)).append("]")
                        .withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(
                                ClickEvent.Action.RUN_COMMAND, "/lotr_colony building " + d.getX() + " " + d.getY()
                                        + " " + d.getZ() + " " + f))));
            }
        }
        return new Result(Optional.of(b), msg);
    }

    /** Changes a building's type to another that fits (the [type] links). */
    public static Result retype(ServerPlayer player, BlockPos door, ResourceLocation type) {
        for (Colony c : Colonies.get(player.server).all()) {
            Optional<Building> b = Buildings.of(c).atDoor(door);
            if (b.isEmpty()) {
                continue;
            }
            if (!Permissions.can(player.getUUID(), c, Perm.BUILD)) {
                return Result.fail("colony.lotr_port.no_permission", "You may not do that in %s", c.name());
            }
            Optional<BuildingType> t = BuildingType.get(type);
            if (t.isEmpty()) {
                return Result.fail("colony.lotr_port.building.unknown_type", "Unknown building type %s", type);
            }
            Optional<BuildingType.Requirement> miss = t.get().missing(b.get().counts());
            if (miss.isPresent()) {
                return new Result(Optional.empty(), Component.translatableWithFallback(
                        "colony.lotr_port.building.missing", "Not a %s: %s", t.get().title(type), miss.get().why())
                        .withStyle(ChatFormatting.RED));
            }
            Building nb = b.get().withType(type);
            Colonies.get(player.server).set(c, Buildings.TYPE, Buildings.of(c).with(nb));
            log(player.server, RETYPED_HISTORY, c, nb, null);
            return new Result(Optional.of(nb), Component.translatableWithFallback("colony.lotr_port.building.retyped",
                    "Now %s", nb.label()).withStyle(ChatFormatting.GREEN));
        }
        return Result.fail("colony.lotr_port.building.none_here", "No building has its door there");
    }

    /**
     * Measures a building again: the same door, its type if it still fits (else the best that does). Gone when its
     * room is broken or nothing fits; returns whether it still stands.
     */
    public static boolean recheck(ServerLevel level, Colony colony, Building b) {
        MinecraftServer server = level.getServer();
        Colonies colonies = Colonies.get(server);
        Optional<Doorway> door = doorway(level, b.door());
        Component why;
        if (door.isEmpty()) {
            why = Component.translatableWithFallback("colony.lotr_port.building.door_gone", "its door is gone");
        } else {
            RoomScan.Room room = measure(level, door.get(), null);
            if (room.ok()) {
                List<ResourceLocation> fits = BuildingType.fitting(room.counts());
                Building.Packed packed = Building.pack(room.cells());
                if (!fits.isEmpty() && packed != null) {
                    ResourceLocation type = fits.contains(b.type()) ? b.type() : fits.get(0);
                    Building nb = b.measured(type, packed.origin(), packed.cells(), room.counts(),
                            quality(room.counts()), level.getGameTime());
                    colonies.set(colony, Buildings.TYPE, Buildings.of(colony).with(nb));
                    if (!type.equals(b.type())) {
                        log(server, RETYPED_HISTORY, colony, nb, null);
                    }
                    return true;
                }
                why = Component.translatableWithFallback("colony.lotr_port.building.no_type",
                        "it no longer fits any building");
            } else if (room.problem().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc
                    && tc.getKey().equals("colony.lotr_port.room.unloaded")) {
                return true; // can't tell; try again later
            } else {
                why = room.problem();
            }
        }
        remove(server, colony, b, why.getString());
        return false;
    }

    public static void remove(MinecraftServer server, Colony colony, Building b, String reason) {
        Colonies.get(server).set(colony, Buildings.TYPE, Buildings.of(colony).without(b.id()));
        log(server, LOST_HISTORY, colony, b, reason);
        NeoForge.EVENT_BUS.post(new Lost(server, colony, b));
        ServerPlayer owner = server.getPlayerList().getPlayer(colony.founder());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatableWithFallback("colony.lotr_port.building.lost",
                    "%s lost %s: %s", colony.name(), b.label(), reason).withStyle(ChatFormatting.GOLD));
        }
    }

    public static int quality(java.util.Map<String, Integer> counts) {
        return BuildingSettings.get().quality(counts.getOrDefault(RoomScan.FLOOR, 0),
                counts.getOrDefault(RoomScan.LIGHTS, 0),
                counts.getOrDefault("chests", 0) + counts.getOrDefault("tables", 0));
    }

    /** "floor 12, beds 2, chests 1". */
    public static String describe(java.util.Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        counts.entrySet().stream().filter(e -> e.getValue() > 0 && !e.getKey().equals(RoomScan.VOLUME))
                .sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> {
                    if (sb.length() > 0) {
                        sb.append(", ");
                    }
                    sb.append(e.getKey()).append(' ').append(e.getValue());
                });
        return sb.toString();
    }

    private static void log(MinecraftServer server, ResourceLocation type, Colony colony, Building b, String reason) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("colony", colony.id());
        tag.putUUID("building", b.id());
        tag.putString("type", b.type().toString());
        b.name().ifPresent(n -> tag.putString("name", n));
        if (reason != null) {
            tag.putString("reason", reason);
        }
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), type, colony.dimension(),
                Optional.of(b.door()), tag));
    }
}
