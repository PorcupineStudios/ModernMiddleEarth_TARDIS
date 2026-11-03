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
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Adding rooms. A room needs the {@code tardis_rb:add_room} feature, a free place under the room cap (the
 * {@code room_cap} stat, which counts the console room), its own requirements and its cost. Slot n's corner is the
 * plot origin plus n x {@code room_step} along z; a doorway is carved through the walls between slot n-1 and n.
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

    /** Adds a room to an interior whose console room's corner is {@code origin} (game tests build one anywhere). */
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
        build(level, origin, template.get(), slot);
        tardis.addRoom(new TardisRecord.PlacedRoom(id, slot));
        CompoundTag tag = new CompoundTag();
        tag.putUUID("tardis", tardis.id());
        tag.putString("room", id.toString());
        tag.putInt("slot", slot);
        BlockPos corner = corner(origin, slot);
        HistoryLog.get(server).append(new HistoryEntry(server.overworld().getGameTime(), ROOM_HISTORY,
                level.dimension(), Optional.of(corner), tag));
        return Repairs.Outcome.OK;
    }

    /** The corner of a slot's room. */
    public static BlockPos corner(BlockPos origin, int slot) {
        return origin.offset(0, 0, slot * TardisContent.tuning().interior().roomStep());
    }

    private static void build(ServerLevel level, BlockPos origin, StructureTemplate template, int slot) {
        Tuning.Interior t = TardisContent.tuning().interior();
        BlockPos corner = corner(origin, slot);
        template.placeInWorld(level, corner, corner, new StructurePlaceSettings(), level.getRandom(),
                Block.UPDATE_CLIENTS);
        // The doorway: the back wall of the room before (z = corner - 1) and this room's front wall (z = corner).
        for (int dz = -1; dz <= 0; dz++) {
            for (int dy = 0; dy < 2; dy++) {
                BlockPos p = corner.offset(t.doorway().getX(), t.doorway().getY() + dy, dz);
                level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
