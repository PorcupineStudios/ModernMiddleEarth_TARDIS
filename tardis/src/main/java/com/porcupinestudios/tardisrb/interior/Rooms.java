package com.porcupinestudios.tardisrb.interior;

import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.RoomEvent;
import com.porcupinestudios.tardisrb.progression.Room;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Tuning;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.repair.Repairs;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Adding rooms. A room needs the {@code tardis_rb:add_room} feature, a free place under the room cap (the
 * {@code room_cap} stat, which counts the console room), its own requirements and its cost. Rooms grow out from the
 * open ends of the console room's hallways (Aaron 2026-11-04, {@code interior.room_anchors}): slot 1 at the first
 * hallway, slot 2 at the second and so on round, then a room further along each ({@code room_step} deeper). A room is
 * turned to face along its hallway, and an opening the size of the hallway is cut through its front wall (and the back
 * wall of the room before it).
 */
public final class Rooms {
    public static final ResourceLocation ADD_ROOM = TardisRB.id("add_room");
    public static final ResourceLocation ROOM_HISTORY = TardisRB.id("room_added");

    private Rooms() {}

    /** How many rooms (console room included) this TARDIS may have now. */
    public static int cap(TardisRecord tardis) {
        return (int) Unlocks.stat(tardis, "room_cap");
    }

    /** Why this room can't be added now (ignoring its cost), or empty if it can. */
    public static Optional<Component> blocked(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Optional<Room> room = TardisContent.room(id);
        if (room.isEmpty()) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.room.unknown", "There is no room %s",
                    id.toString()));
        }
        Unlocks.Result feature = Unlocks.check(server, tardis, ADD_ROOM, player);
        if (!feature.allowed()) {
            return Optional.of(feature.reason());
        }
        if (tardis.roomCount() >= cap(tardis)) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.room.cap",
                    "No space for another room (%s of %s); repair the room systems further", tardis.roomCount(),
                    cap(tardis)));
        }
        if (room.get().requirements().isPresent()
                && !room.get().requirements().get().test(Unlocks.context(server, tardis, player))) {
            return Optional.of(Component.translatableWithFallback("tardis_rb.room.requirements",
                    "Something else has to come first"));
        }
        return Optional.empty();
    }

    /** Adds a room to the TARDIS's own interior, building the interior first if needed. */
    public static Repairs.Outcome add(MinecraftServer server, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        Optional<Component> blocked = blocked(server, tardis, id, player);
        if (blocked.isPresent()) {
            return new Repairs.Outcome(blocked);
        }
        if (!Interiors.ensure(server, tardis)) {
            return new Repairs.Outcome(Optional.of(Component.translatableWithFallback("tardis_rb.room.no_interior",
                    "The interior can't be reached")));
        }
        return add(Interiors.level(server).orElseThrow(), Interiors.origin(tardis.plot()), tardis, id, player);
    }

    /** Adds a room to an interior whose origin is {@code origin} (game tests build one anywhere). */
    public static Repairs.Outcome add(ServerLevel level, BlockPos origin, TardisRecord tardis, ResourceLocation id,
            Optional<ServerPlayer> player) {
        MinecraftServer server = level.getServer();
        Optional<Component> blocked = blocked(server, tardis, id, player);
        if (blocked.isPresent()) {
            return new Repairs.Outcome(blocked);
        }
        Room room = TardisContent.room(id).orElseThrow();
        Optional<StructureTemplate> template = server.getStructureManager().get(room.template());
        if (template.isEmpty()) {
            TardisRB.LOG.error("[rooms] template {} of room {} is missing", room.template(), id);
            return new Repairs.Outcome(Optional.of(Component.translatableWithFallback("tardis_rb.room.no_template",
                    "This room's building plan is missing")));
        }
        int slot = tardis.rooms().stream().mapToInt(TardisRecord.PlacedRoom::slot).max().orElse(0) + 1;
        RoomEvent.Pre pre = NeoForge.EVENT_BUS.post(new RoomEvent.Pre(tardis, id, slot,
                TardisContent.get().cost(room.cost())));
        if (pre.isCanceled()) {
            return new Repairs.Outcome(Optional.of(Component.translatableWithFallback("tardis_rb.room.cancelled",
                    "The room wasn't added")));
        }
        Optional<Component> unpaid = Repairs.pay(tardis, pre.cost(), player);
        if (unpaid.isPresent()) {
            return new Repairs.Outcome(unpaid);
        }
        Placement at = build(level, origin, template.get(), slot);
        tardis.addRoom(new TardisRecord.PlacedRoom(id, slot));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("tardis", tardis.id());
        tag.putString("room", id.toString());
        tag.putInt("slot", slot);
        BlockPos corner = at.corner();
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), ROOM_HISTORY,
                level.dimension(), Optional.of(corner), tag));
        return Repairs.Outcome.OK;
    }

    /** Builds a TARDIS's recorded rooms again in an interior whose origin is {@code origin} (a rebuilt plot). */
    public static void rebuild(ServerLevel level, BlockPos origin, TardisRecord tardis) {
        for (TardisRecord.PlacedRoom placed : tardis.rooms()) {
            Optional<StructureTemplate> template = TardisContent.room(placed.room())
                    .flatMap(r -> level.getServer().getStructureManager().get(r.template()));
            if (template.isEmpty()) {
                TardisRB.LOG.error("[rooms] can't rebuild room {}: no template", placed.room());
                continue;
            }
            build(level, origin, template.get(), placed.slot());
        }
    }

    /**
     * Where a slot's room goes: its structure {@code corner} and {@code rotation} (local +z runs along the hallway,
     * {@code facing}), the hallway's opening ({@code width} x {@code height}, starting at local x {@code openX}, y 1)
     * and how deep along the hallway it is ({@code depth}, 0 for the room at the hallway's end).
     */
    public record Placement(BlockPos corner, Rotation rotation, Direction facing, int openX, int width, int height,
            int depth) {
        /** A block of the room (local x, y, z in its template) in the world. */
        public BlockPos at(int x, int y, int z) {
            return corner.offset(StructureTemplate.transform(new BlockPos(x, y, z), Mirror.NONE, rotation,
                    BlockPos.ZERO));
        }
    }

    /** Where slot {@code slot} (1 and up) goes for a room template {@code roomWidth} blocks wide. */
    public static Placement placement(BlockPos origin, int slot, int roomWidth) {
        Tuning.Interior t = TardisContent.tuning().interior();
        List<Tuning.Interior.Anchor> anchors = t.roomAnchors();
        Tuning.Interior.Anchor anchor = anchors.get(Math.floorMod(slot - 1, anchors.size()));
        int depth = Math.max(0, slot - 1) / anchors.size();
        Direction f = anchor.facing();
        Rotation rotation = switch (f) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.CLOCKWISE_90;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        // the room's local +x in the world, and the hallway's last cell furthest that way back (its first)
        BlockPos side = StructureTemplate.transform(new BlockPos(1, 0, 0), Mirror.NONE, rotation, BlockPos.ZERO);
        BlockPos lo = anchor.min();
        BlockPos hi = anchor.max();
        int width = Math.abs(f.getAxis() == Direction.Axis.X ? hi.getZ() - lo.getZ() : hi.getX() - lo.getX()) + 1;
        int height = hi.getY() - lo.getY() + 1;
        BlockPos first = new BlockPos(side.getX() < 0 ? hi.getX() : lo.getX(), lo.getY(),
                side.getZ() < 0 ? hi.getZ() : lo.getZ());
        int openX = Math.max(0, (roomWidth - width) / 2);
        BlockPos front = origin.offset(first).relative(f, 1 + depth * t.roomStep());
        BlockPos corner = front.subtract(StructureTemplate.transform(new BlockPos(openX, 1, 0), Mirror.NONE, rotation,
                BlockPos.ZERO));
        return new Placement(corner, rotation, f, openX, width, height, depth);
    }

    private static Placement build(ServerLevel level, BlockPos origin, StructureTemplate template, int slot) {
        Placement at = placement(origin, slot, template.getSize().getX());
        template.placeInWorld(level, at.corner(), at.corner(), new StructurePlaceSettings().setRotation(at.rotation()),
                level.getRandom(), Block.UPDATE_CLIENTS);
        // the opening: this room's front wall, and the back wall of the room before it on this hallway
        for (int x = at.openX(); x < at.openX() + at.width(); x++) {
            for (int y = 1; y <= at.height(); y++) {
                BlockPos p = at.at(x, y, 0);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                if (at.depth() > 0) {
                    level.setBlock(p.relative(at.facing().getOpposite()), Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_CLIENTS);
                }
            }
        }
        return at;
    }
}
