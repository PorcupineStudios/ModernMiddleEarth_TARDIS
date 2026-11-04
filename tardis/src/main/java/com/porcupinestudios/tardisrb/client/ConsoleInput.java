package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.console.ConsoleLayout;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Clicking console controls: the controls stand outside the console block, so a right-click is tested against each
 * shown control's click box from the console model along the player's view; the nearest hit is sent to the server
 * ({@code ConsoleNet.Press}). The time rotor's column counts as a control too: clicking it turns the monitor to
 * face the player, like the 1.7.10 mod. A block in front of the control wins as usual (the console's invisible hull has no
 * outline, so it never does).
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class ConsoleInput {
    /** The time rotor's glass round its axis, in blocks: a click inside it hits the column. */
    private static final double COLUMN_RADIUS = 0.4;

    private ConsoleInput() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.player == null
                || mc.level == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 end = eye.add(mc.player.getViewVector(1F).scale(ConsoleLayout.REACH));
        ConsoleModel model = ConsoleModel.get();
        double best = Double.MAX_VALUE;
        ConsoleBlockEntity console = null;
        String control = null;
        for (ConsoleBlockEntity be : ConsoleBlockEntity.clientLoaded()) {
            if (be.getLevel() != mc.level || be.isRemoved()
                    || Vec3.atCenterOf(be.getBlockPos()).distanceToSqr(eye) > 64) {
                continue;
            }
            Vec3 corner = Vec3.atLowerCornerOf(be.getBlockPos());
            for (ConsoleBlockEntity.Shown c : be.shown()) {
                ConsoleModel.ControlPart part = model.controls.get(c.part());
                if (part == null) {
                    continue;
                }
                AABB box = c.part().equals(ConsoleModel.MONITOR)
                        ? model.monitorBox(part.box(), model.monitorTurn(be.monitorAngle()))
                        : part.box();
                Optional<Vec3> hit = box.move(corner).inflate(0.02).clip(eye, end);
                if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
                    best = hit.get().distanceToSqr(eye);
                    console = be;
                    control = c.id();
                }
            }
            // the time rotor's column: clicking it turns the monitor toward the player
            AABB column = new AABB(model.pivotX - COLUMN_RADIUS, 1.0, model.pivotZ - COLUMN_RADIUS,
                    model.pivotX + COLUMN_RADIUS, 3.5, model.pivotZ + COLUMN_RADIUS).move(corner);
            Optional<Vec3> hit = column.clip(eye, end);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
                best = hit.get().distanceToSqr(eye);
                console = be;
                control = ConsoleControls.COLUMN;
            }
        }
        if (control == null) {
            return;
        }
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceToSqr(eye) < best
                && !(mc.level.getBlockEntity(block.getBlockPos()) instanceof ConsoleBlockEntity)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new ConsoleNet.Press(console.getBlockPos(), control));
    }
}
