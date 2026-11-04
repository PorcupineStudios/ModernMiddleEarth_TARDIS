package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.space.Orbits;
import com.porcupinestudios.tardisrb.space.Planet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Space physics for this client's own player (Minecraft moves the player on the client): the planet's pull, orbits,
 * no drag ({@link Orbits}), and in the air jump / sneak push up and down.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class ClientSpacePhysics {
    private ClientSpacePhysics() {}

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || player != Minecraft.getInstance().player
                || !Orbits.moves(player)) {
            return;
        }
        Planet p = GlobeRenderer.planetBelow(player.level(), player.getX(), player.getZ());
        if (p == null) {
            return;
        }
        int thrust = 0;
        if (!player.onGround()) {
            thrust = (player.input.jumping ? 1 : 0) - (player.input.shiftKeyDown ? 1 : 0);
        }
        Orbits.step(player, p, GlobeRenderer.tuning(), thrust);
    }
}
