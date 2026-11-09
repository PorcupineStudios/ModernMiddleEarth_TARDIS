package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ConsoleBlockEntity;
import com.porcupinestudios.tardisrb.net.ConsoleNet;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Clicking console controls: the controls stand outside the console block, so a right-click is tested against each
 * clickable part's hit box ({@link ConsoleAim}); the nearest hit is sent to the server ({@code ConsoleNet.Press}) as
 * the control and the part clicked. The time rotor's column counts as a control too: clicking it turns the monitor to
 * face the player, like the 1.7.10 mod. A block in front of the control wins as usual (the console's invisible hull
 * has no outline, so it never does).
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class ConsoleInput {
    private ConsoleInput() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || mc.player == null
                || mc.level == null) {
            return;
        }
        Optional<ConsoleAim.Aim> aim = ConsoleAim.find(1F);
        if (aim.isEmpty() || (aim.get().control() != null && aim.get().click().isEmpty())) {
            return; // nothing here, or a control with nothing to click (a gauge)
        }
        double best = aim.get().distance();
        if (mc.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && block.getLocation().distanceTo(mc.player.getEyePosition()) < best
                && !(mc.level.getBlockEntity(block.getBlockPos()) instanceof ConsoleBlockEntity)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new ConsoleNet.Press(aim.get().console().getBlockPos(), aim.get().pressed()));
    }
}
