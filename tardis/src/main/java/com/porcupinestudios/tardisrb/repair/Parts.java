package com.porcupinestudios.tardisrb.repair;

import com.porcupinestudios.tardisrb.block.PartBlock;
import com.porcupinestudios.tardisrb.block.PartBlockEntity;
import com.porcupinestudios.tardisrb.block.TardisBlocks;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.Repair;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Physical repairs: each repair with a {@code part_pos} has a part block in the console room. Placing them, fixing
 * them by hand, and keeping their look in step with the record (a damaged subsystem shows its parts broken again).
 */
public final class Parts {
    private Parts() {}

    public static boolean fixed(TardisRecord tardis, Repair repair) {
        return Unlocks.level(tardis, repair.subsystem()) >= repair.level();
    }

    public static void place(ServerLevel level, BlockPos pos, TardisRecord tardis, ResourceLocation repairId) {
        boolean isFixed = TardisContent.repair(repairId).map(r -> fixed(tardis, r)).orElse(false);
        level.setBlock(pos, TardisBlocks.PART.get().defaultBlockState().setValue(PartBlock.FIXED, isFixed),
                Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PartBlockEntity be) {
            be.set(tardis.id(), repairId);
        }
    }

    /** Places every physical repair's part in the TARDIS's console room (when the interior is built). */
    public static void placeAll(ServerLevel interior, TardisRecord tardis) {
        BlockPos origin = Interiors.origin(tardis.plot());
        for (Map.Entry<ResourceLocation, Repair> e : TardisContent.get().repairs().entrySet()) {
            e.getValue().partPos().ifPresent(p -> place(interior, origin.offset(p), tardis, e.getKey()));
        }
    }

    /** Makes the part blocks in the room show whether their repair is in effect. */
    public static void sync(MinecraftServer server, TardisRecord tardis) {
        if (tardis.plot() < 0) {
            return;
        }
        Optional<ServerLevel> interior = Interiors.level(server);
        if (interior.isEmpty()) {
            return;
        }
        BlockPos origin = Interiors.origin(tardis.plot());
        for (Repair r : TardisContent.get().repairs().values()) {
            if (r.partPos().isEmpty()) {
                continue;
            }
            BlockPos p = origin.offset(r.partPos().get());
            if (!interior.get().isLoaded(p)) {
                continue;
            }
            refresh(interior.get(), p, fixed(tardis, r));
        }
    }

    private static void refresh(ServerLevel level, BlockPos pos, boolean isFixed) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PartBlock && state.getValue(PartBlock.FIXED) != isFixed) {
            level.setBlock(pos, state.setValue(PartBlock.FIXED, isFixed), Block.UPDATE_CLIENTS);
        }
    }

    /** A player uses a part: tries its repair with what they carry. Returns the message to show. */
    public static Component use(ServerLevel level, BlockPos pos, ServerPlayer player) {
        if (!(level.getBlockEntity(pos) instanceof PartBlockEntity be) || be.tardis().isEmpty()
                || be.repair().isEmpty()) {
            return Component.translatableWithFallback("tardis_rb.part.dead", "A dead, scorched part");
        }
        Optional<TardisRecord> tardis = TardisManager.get(level.getServer()).get(be.tardis().get());
        if (tardis.isEmpty() || !tardis.get().isCrew(player.getUUID())) {
            return Component.translatableWithFallback("tardis_rb.part.not_yours", "This isn't your TARDIS");
        }
        Optional<Repair> repair = TardisContent.repair(be.repair().get());
        if (repair.isPresent() && fixed(tardis.get(), repair.get())) {
            return Component.translatableWithFallback("tardis_rb.part.working", "It hums quietly. It works.");
        }
        Repairs.Outcome out = Repairs.attempt(level.getServer(), tardis.get(), be.repair().get(),
                Optional.of(player));
        refresh(level, pos, repair.map(r -> fixed(tardis.get(), r)).orElse(false));
        return out.done() ? Component.translatableWithFallback("tardis_rb.part.fixed", "Fixed!")
                : out.problem().get();
    }
}
