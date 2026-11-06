package com.porcupinestudios.lotrport.travel;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.sound.LotrSounds;
import com.porcupinestudios.lotrport.faction.Alignment;
import com.porcupinestudios.lotrport.registry.LotrRegistries;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import com.porcupinestudios.ringbearer.api.history.HistoryEntry;
import com.porcupinestudios.ringbearer.api.history.HistoryLog;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Waypoints and fast travel, as LOTR's: entering a biome unlocks its waypoint region (biome tag
 * {@code lotr_port:waypoint_region/<region>}); a player may then travel to that region's waypoints, unless the
 * waypoint's faction distrusts them, a mob is attacking them, or the wait after their last trip isn't over.
 */
public final class FastTravel {
    public static final ResourceLocation HISTORY_TYPE = LotrPort.id("fast_travel");
    public static final String REGION_TAG_PREFIX = "waypoint_region/";
    private static final ResourceLocation SETTINGS = LotrPort.id("settings");

    private static final CodecJsonLoader<Waypoint> WAYPOINTS = new CodecJsonLoader<>("waypoint", Waypoint.CODEC);
    private static final CodecJsonLoader<TravelSettings> SETTINGS_LOADER =
            new CodecJsonLoader<>("fast_travel", TravelSettings.CODEC);

    private FastTravel() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(WAYPOINTS);
            event.addListener(SETTINGS_LOADER);
        });
        NeoForge.EVENT_BUS.addListener(FastTravel::onPlayerTick);
    }

    public static Map<ResourceLocation, Waypoint> waypoints() {
        return WAYPOINTS.entries();
    }

    public static Optional<Waypoint> waypoint(ResourceLocation id) {
        return Optional.ofNullable(WAYPOINTS.entries().get(id));
    }

    public static TravelSettings settings() {
        return SETTINGS_LOADER.entries().getOrDefault(SETTINGS, TravelSettings.DEFAULT);
    }

    public static List<String> loadProblems() {
        List<String> all = new java.util.ArrayList<>(WAYPOINTS.lastErrors());
        all.addAll(SETTINGS_LOADER.lastErrors());
        return all;
    }

    public static TravelData data(ServerPlayer player) {
        return player.getData(LotrRegistries.TRAVEL);
    }

    /** Unlocks a waypoint region for the player; true if it was new. */
    public static boolean unlock(ServerPlayer player, String region) {
        TravelData data = data(player);
        if (data.regions().contains(region)) {
            return false;
        }
        player.setData(LotrRegistries.TRAVEL, data.withRegion(region));
        player.displayClientMessage(Component.translatableWithFallback("waypoint.lotr_port.unlocked",
                "You can now fast travel to waypoints in %s", regionName(region)), false);
        return true;
    }

    public static Component regionName(String region) {
        return Component.translatableWithFallback("waypoint_region." + LotrPort.MOD_ID + "." + region, region);
    }

    public static Component name(ResourceLocation waypoint) {
        return Component.translatableWithFallback("waypoint." + waypoint.getNamespace() + "." + waypoint.getPath(),
                waypoint.getPath());
    }

    /** Ticks until the player may travel again (0 when ready). */
    public static long waitTicks(ServerPlayer player) {
        return Math.max(0, data(player).readyAt() - player.server.overworld().getGameTime());
    }

    /** Why the player can't travel to the waypoint now, or empty if they can. */
    public static Optional<Component> problem(ServerPlayer player, ResourceLocation id) {
        Optional<Waypoint> wp = waypoint(id);
        if (wp.isEmpty()) {
            return Optional.of(Component.translatableWithFallback("waypoint.lotr_port.unknown",
                    "No waypoint called %s", id.toString()));
        }
        if (!data(player).regions().contains(wp.get().region())) {
            return Optional.of(Component.translatableWithFallback("waypoint.lotr_port.locked",
                    "You haven't been to %s yet", regionName(wp.get().region())));
        }
        if (wp.get().faction().isPresent() && Alignment.get(player, wp.get().faction().get()) < 0) {
            ResourceLocation f = wp.get().faction().get();
            return Optional.of(Component.translatableWithFallback("waypoint.lotr_port.enemy",
                    "%s is hostile to you", Component.translatable("faction." + f.getNamespace() + "."
                            + f.getPath())));
        }
        long wait = waitTicks(player);
        if (wait > 0) {
            return Optional.of(Component.translatableWithFallback("waypoint.lotr_port.wait",
                    "You must wait %s seconds before travelling again", (wait + 19) / 20));
        }
        double range = settings().hostileRange();
        if (!player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                m -> m.isAlive() && m.getTarget() == player).isEmpty()) {
            return Optional.of(Component.translatableWithFallback("waypoint.lotr_port.under_attack",
                    "You can't travel while enemies are near"));
        }
        return Optional.empty();
    }

    /** Travels to the waypoint if allowed; tells the player why not otherwise. Returns whether they travelled. */
    public static boolean travel(ServerPlayer player, ResourceLocation id) {
        Optional<Component> problem = problem(player, id);
        if (problem.isPresent()) {
            player.displayClientMessage(problem.get(), false);
            return false;
        }
        Waypoint wp = waypoint(id).orElseThrow();
        ServerLevel level = player.serverLevel();
        level.getChunk(wp.x() >> 4, wp.z() >> 4); // generate it, so the height is real
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wp.x(), wp.z());
        BlockPos from = player.blockPosition();
        double distance = Math.sqrt(from.distToLowCornerSqr(wp.x(), from.getY(), wp.z()));
        player.stopRiding();
        player.teleportTo(level, wp.x() + 0.5, y, wp.z() + 0.5, Set.of(), player.getYRot(), player.getXRot());
        LotrSounds.playTo(player, "event.fast_travel");
        long now = player.server.overworld().getGameTime();
        player.setData(LotrRegistries.TRAVEL, data(player).travelled(now + settings().cooldownTicks(distance)));
        player.displayClientMessage(Component.translatableWithFallback("waypoint.lotr_port.arrived",
                "You travelled to %s", name(id)), false);
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", player.getUUID());
        tag.putString("waypoint", id.toString());
        tag.putLong("from", from.asLong());
        HistoryLog.get(player.server).append(new HistoryEntry(now, HISTORY_TYPE, level.dimension(),
                Optional.of(new BlockPos(wp.x(), y, wp.z())), tag));
        return true;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        player.serverLevel().getBiome(player.blockPosition()).tags()
                .filter(t -> t.location().getNamespace().equals(LotrPort.MOD_ID)
                        && t.location().getPath().startsWith(REGION_TAG_PREFIX))
                .forEach(t -> unlock(player, t.location().getPath().substring(REGION_TAG_PREFIX.length())));
    }
}
