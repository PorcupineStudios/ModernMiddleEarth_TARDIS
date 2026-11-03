package com.porcupinestudios.tardisrb.progression;

import com.porcupinestudios.ringbearer.api.condition.ConditionContext;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Moves a TARDIS along: passes milestones whose requirements now hold, and enters the next stage once the current
 * stage's repairs are all done and the next stage's milestone (if any) is passed. Call after any change that could
 * matter (repairs, research, data reloads); it is cheap.
 */
public final class Progression {
    private Progression() {}

    public static void update(MinecraftServer server, TardisRecord tardis) {
        TardisContent.Snapshot data = TardisContent.get();
        List<Map.Entry<ResourceLocation, Stage>> order = data.stageOrder();
        if (order.isEmpty()) {
            return;
        }
        if (tardis.stage().isEmpty() || !data.stages().containsKey(tardis.stage().get())) {
            tardis.setStage(order.get(0).getKey());
        }
        boolean moved = true;
        while (moved) {
            moved = false;
            ConditionContext ctx = Unlocks.context(server, tardis, Optional.empty());
            for (Map.Entry<ResourceLocation, Milestone> m : data.milestones().entrySet()) {
                if (!tardis.milestones().contains(m.getKey())
                        && m.getValue().requirements().map(c -> c.test(ctx)).orElse(false)) {
                    tardis.passMilestone(m.getKey());
                    moved = true;
                }
            }
            int at = indexOf(order, tardis.stage().get());
            if (at + 1 < order.size() && finished(tardis, order.get(at).getValue())) {
                Map.Entry<ResourceLocation, Stage> next = order.get(at + 1);
                if (next.getValue().milestone().map(tardis.milestones()::contains).orElse(true)) {
                    tardis.setStage(next.getKey());
                    moved = true;
                }
            }
        }
    }

    /** Every repair the stage lists is done (and not undone by damage). */
    public static boolean finished(TardisRecord tardis, Stage stage) {
        for (ResourceLocation id : stage.repairs()) {
            Repair r = TardisContent.get().repairs().get(id);
            if (r == null || Unlocks.level(tardis, r.subsystem()) < r.level()) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(List<Map.Entry<ResourceLocation, Stage>> order, ResourceLocation stage) {
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getKey().equals(stage)) {
                return i;
            }
        }
        return 0;
    }
}
