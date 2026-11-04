package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.InteriorDoorBlock;
import com.porcupinestudios.tardisrb.net.DoorNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Right-clicking an open doorway closes the doors (Aaron, 2026-11-04): the portal stands in the doorway, so the click
 * would otherwise go through it. The player's view is walked in small steps; if it enters an open police box or
 * interior door block before anything solid, that door is used ({@code DoorNet.Use}). Sneaking, or standing in the
 * doorway yourself, leaves the click alone.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class DoorInput {
    private static final double STEP = 0.05;

    private DoorInput() {}

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND || player == null || mc.level == null
                || player.isShiftKeyDown() || player.isSpectator()) {
            return;
        }
        BlockPos door = openDoorInView(mc.level, player);
        if (door == null) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new DoorNet.Use(door));
    }

    /** The first open door block along the player's view within reach, or null if something solid comes first. */
    private static BlockPos openDoorInView(Level level, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getViewVector(1F);
        double reach = player.blockInteractionRange();
        for (double d = 0; d <= reach; d += STEP) {
            Vec3 at = eye.add(dir.scale(d));
            BlockPos pos = BlockPos.containing(at);
            BlockState state = level.getBlockState(pos);
            if (isOpenDoor(state)) {
                if (player.getBoundingBox().intersects(new AABB(pos))) {
                    // standing in the doorway: let the click through
                    continue;
                }
                return pos;
            }
            if (solidAt(level, pos, state, at)) {
                return null;
            }
        }
        return null;
    }

    private static boolean isOpenDoor(BlockState state) {
        return (state.getBlock() instanceof ExteriorBlock || state.getBlock() instanceof InteriorDoorBlock)
                && state.getValue(BlockStateProperties.OPEN);
    }

    private static boolean solidAt(Level level, BlockPos pos, BlockState state, Vec3 at) {
        if (state.isAir()) {
            return false;
        }
        Vec3 local = at.subtract(pos.getX(), pos.getY(), pos.getZ());
        for (AABB box : state.getShape(level, pos).toAabbs()) {
            if (box.contains(local)) {
                return true;
            }
        }
        return false;
    }
}
