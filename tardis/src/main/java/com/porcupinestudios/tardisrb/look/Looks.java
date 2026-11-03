package com.porcupinestudios.tardisrb.look;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.event.LookEvent;
import com.porcupinestudios.tardisrb.progression.Look;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

/** Works out the police box's look from the look rules and puts it on the box's blocks. */
public final class Looks {
    private Looks() {}

    public static int exterior(MinecraftServer server, TardisRecord tardis) {
        ConditionContext ctx = Unlocks.context(server, tardis, Optional.empty());
        int look = 0;
        for (Look.Rule rule : TardisContent.get().exteriorLook().rules()) {
            if (rule.look() > look && rule.requirements().test(ctx)) {
                look = rule.look();
            }
        }
        return NeoForge.EVENT_BUS.post(new LookEvent(tardis, look)).look();
    }

    /** Re-dresses the standing box if its look changed. */
    public static void update(MinecraftServer server, TardisRecord tardis) {
        Optional<GlobalPos> at = tardis.location();
        if (at.isEmpty()) {
            return;
        }
        ServerLevel level = server.getLevel(at.get().dimension());
        if (level == null || !level.isLoaded(at.get().pos())) {
            return;
        }
        int look = exterior(server, tardis);
        for (BlockPos p : new BlockPos[] {at.get().pos(), at.get().pos().above()}) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof ExteriorBlock && state.getValue(ExteriorBlock.LOOK) != look) {
                level.setBlock(p, state.setValue(ExteriorBlock.LOOK, look), Block.UPDATE_CLIENTS);
            }
        }
    }
}
