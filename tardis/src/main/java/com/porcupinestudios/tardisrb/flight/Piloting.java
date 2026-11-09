package com.porcupinestudios.tardisrb.flight;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.entity.TardisBody;
import com.porcupinestudios.tardisrb.progression.Unlocks;
import com.porcupinestudios.tardisrb.vortex.Vortex;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import qouteall.imm_ptl.core.api.PortalAPI;

/**
 * Flying the loose box with the flight yoke (docs/free-flight-plan.md slice 4, Aaron 2026-11-08: one control enters the
 * third-person view to pilot; Shift only stops piloting). {@link #start} moves the crew member out to the box (Immersive
 * Portals' seamless teleport across dimensions, no loading screen) and seats them in it, invisible and unhurt; their
 * client then steers it ({@code TardisBody}) and shows it from behind ({@code client/PilotClient}). Getting off (Shift,
 * the thrusters going off, the box setting down or jumping) puts them back where they stood at the console. Where to go
 * back to is kept on the player ({@value #TAG}), so a pilot who logs out mid-flight comes back inside.
 */
public final class Piloting {
    public static final ResourceLocation FEATURE = TardisRB.id("piloting");
    static final String TAG = "tardis_rb_pilot";
    /** Ticks to wait for the box to be there to sit in after the move out. */
    private static final int MOUNT_WAIT = 40;

    private static final Map<UUID, UUID> PENDING = new HashMap<>();
    private static final Map<UUID, Integer> WAIT = new HashMap<>();
    private static final Set<UUID> RETURNING = new HashSet<>();

    private Piloting() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(Piloting::onTick);
        NeoForge.EVENT_BUS.addListener(Piloting::onMount);
        NeoForge.EVENT_BUS.addListener(Piloting::onDamage);
        NeoForge.EVENT_BUS.addListener(Piloting::onLogin);
    }

    /** True while the player is flying a box (or on the way out to it). */
    public static boolean isPilot(ServerPlayer player) {
        return player.getPersistentData().contains(TAG);
    }

    /** Puts {@code player} at the controls of the loose box. Says what happened. */
    public static Component start(MinecraftServer server, TardisRecord tardis, ServerPlayer player) {
        Unlocks.Result unlocked = Unlocks.check(server, tardis, FEATURE, Optional.of(player));
        if (!unlocked.allowed()) {
            return unlocked.reason();
        }
        return seat(server, tardis, player);
    }

    /**
     * {@link #start} without the feature check: the time vortex seats whoever pulled the dematerialisation lever
     * ({@code vortex/Vortex}), piloting unlocked or not.
     */
    public static Component seat(MinecraftServer server, TardisRecord tardis, ServerPlayer player) {
        if (!FreeFlight.isLoose(tardis)) {
            return Component.translatableWithFallback("tardis_rb.pilot.not_loose",
                    "The box has to come loose first (phase key, then the phase lever)");
        }
        if (!FreeFlight.thrusters(tardis) && !Vortex.inVortex(tardis)) {
            return Component.translatableWithFallback("tardis_rb.pilot.no_thrusters", "Switch the thrusters on first");
        }
        Optional<TardisBody> body = FreeFlight.body(server, tardis);
        if (body.isEmpty()) {
            return Component.translatableWithFallback("tardis_rb.pilot.no_body", "The box can't be reached");
        }
        if (body.get().isVehicle() || isPilot(player)) {
            return Component.translatableWithFallback("tardis_rb.pilot.taken", "Someone is already flying it");
        }
        CompoundTag back = new CompoundTag();
        back.putUUID("tardis", tardis.id());
        back.putString("dimension", player.level().dimension().location().toString());
        back.putDouble("x", player.getX());
        back.putDouble("y", player.getY());
        back.putDouble("z", player.getZ());
        back.putFloat("yaw", player.getYRot());
        back.putFloat("pitch", player.getXRot());
        player.getPersistentData().put(TAG, back);
        player.setInvisible(true);
        move(player, (ServerLevel) body.get().level(), body.get().position().add(0, 2.2, 0));
        PENDING.put(player.getUUID(), body.get().getUUID());
        WAIT.put(player.getUUID(), MOUNT_WAIT);
        return Component.translatableWithFallback("tardis_rb.pilot.start",
                "Flying: W A S D to move, Q / E up and down, sprint to boost, jump to brake, Shift to stop");
    }

    /** Puts the pilot back where they stood at the console. */
    public static void stop(ServerPlayer player) {
        if (!isPilot(player)) {
            return;
        }
        CompoundTag back = player.getPersistentData().getCompound(TAG);
        player.getPersistentData().remove(TAG);
        PENDING.remove(player.getUUID());
        player.setInvisible(false);
        if (player.isPassenger()) {
            player.stopRiding();
        }
        ResourceLocation dim = ResourceLocation.tryParse(back.getString("dimension"));
        ServerLevel level = dim == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) {
            level = player.server.overworld();
        }
        move(player, level, new Vec3(back.getDouble("x"), back.getDouble("y"), back.getDouble("z")));
        player.setYRot(back.getFloat("yaw"));
        player.setXRot(back.getFloat("pitch"));
    }

    private static void move(ServerPlayer player, ServerLevel to, Vec3 pos) {
        if (player.level() == to) {
            player.teleportTo(pos.x, pos.y, pos.z);
        } else {
            PortalAPI.teleportEntity(player, to, pos);
        }
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (Iterator<Map.Entry<UUID, UUID>> it = PENDING.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, UUID> e = it.next();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null) {
                it.remove();
                continue;
            }
            Entity body = ((ServerLevel) player.level()).getEntity(e.getValue());
            if (body instanceof TardisBody b && !b.isVehicle() && player.startRiding(b, true)) {
                it.remove();
                continue;
            }
            int left = WAIT.merge(e.getKey(), -1, Integer::sum);
            if (left <= 0) {
                it.remove();
                RETURNING.add(e.getKey());
            }
        }
        if (!RETURNING.isEmpty()) {
            Set<UUID> now = new HashSet<>(RETURNING);
            RETURNING.clear();
            for (UUID id : now) {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) {
                    stop(player);
                }
            }
        }
    }

    /** Getting off the box (Shift, or the box going) ends piloting: the pilot goes back inside next tick. */
    private static void onMount(EntityMountEvent event) {
        if (!event.getLevel().isClientSide() && event.isDismounting()
                && event.getEntityBeingMounted() instanceof TardisBody
                && event.getEntityMounting() instanceof ServerPlayer player && isPilot(player)) {
            RETURNING.add(player.getUUID());
        }
    }

    private static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPilot(player)) {
            event.setCanceled(true);
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isPilot(player) && !player.isPassenger()) {
            RETURNING.add(player.getUUID());
        }
    }
}
