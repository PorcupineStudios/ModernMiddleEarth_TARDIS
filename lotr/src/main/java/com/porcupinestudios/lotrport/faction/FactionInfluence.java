package com.porcupinestudios.lotrport.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.Comparator;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/**
 * Where a faction's word reaches (LOTR's control zones, plus its people): a player is in a faction's influence
 * inside one of its control zones on the Middle-earth map, or within {@code npc_radius} blocks of one of its NPCs in
 * any dimension (a Ranger visiting the TARDIS brings the Dúnedain's eyes with him). Kills only change alignment with
 * the factions whose influence the player is in ({@link FactionKills}); the alignment bar shows which applies.
 * Numbers: {@code data/lotr_port/alignment/settings.json}.
 */
public final class FactionInfluence {
    public record Settings(int npcRadius, boolean zoneKillsCount, boolean npcKillsCount, boolean showBarNearNpcs) {
        public static final Settings DEFAULT = new Settings(24, true, true, true);
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("npc_radius", 24).forGetter(Settings::npcRadius),
                Codec.BOOL.optionalFieldOf("zone_kills_count", true).forGetter(Settings::zoneKillsCount),
                Codec.BOOL.optionalFieldOf("npc_kills_count", true).forGetter(Settings::npcKillsCount),
                Codec.BOOL.optionalFieldOf("show_bar_near_npcs", true).forGetter(Settings::showBarNearNpcs))
                .apply(i, Settings::new));
    }

    /** How a faction's influence reaches the player, weakest first. */
    public enum Reach {
        /** Outside its zones and far from its people: kills don't count. */
        NONE,
        /** Near one of its NPCs, outside its zones. */
        NPC,
        /** Inside one of its control zones. */
        ZONE
    }

    private static final CodecJsonLoader<Settings> LOADER = new CodecJsonLoader<>("alignment", Settings.CODEC);

    private FactionInfluence() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
    }

    public static Settings settings() {
        return LOADER.entries().getOrDefault(ResourceLocation.fromNamespaceAndPath("lotr_port", "settings"),
                Settings.DEFAULT);
    }

    public static Reach reach(ServerPlayer player, ResourceLocation faction) {
        Settings s = settings();
        if (s.zoneKillsCount() && inZone(player, faction)) {
            return Reach.ZONE;
        }
        if (s.npcKillsCount() && nearestNpc(player, faction).isPresent()) {
            return Reach.NPC;
        }
        return Reach.NONE;
    }

    /** Inside one of the faction's control zones, in Middle-earth. Factions without zones (realms) have none. */
    public static boolean inZone(ServerPlayer player, ResourceLocation faction) {
        ServerLevel me = LotrDimensions.middleEarth(player.server);
        if (me == null || player.level() != me) {
            return false;
        }
        FactionDef def = Factions.definitions().get(faction);
        if (def == null || def.controlZones().isEmpty()) {
            return false;
        }
        MiddleEarthMap map = MiddleEarthMap.get();
        double px = map.pixelX(player.getX());
        double pz = map.pixelZ(player.getZ());
        for (FactionDef.MapArea z : def.controlZones()) {
            double dx = px - z.x();
            double dz = pz - z.z();
            if (dx * dx + dz * dz <= (double) z.radius() * z.radius()) {
                return true;
            }
        }
        return false;
    }

    /**
     * LOTR's control-zone fade (LOTRFaction.getControlZoneAlignmentMultiplier) for a player outside the faction's zones:
     * 1 at a zone's edge down to 0 at 50 map pixels from it (LOTR's getControlZoneReducedRange; 0 for isolationist
     * factions, so no fade), in Middle-earth only.
     */
    public static float zoneFade(ServerPlayer player, ResourceLocation faction) {
        ServerLevel me = LotrDimensions.middleEarth(player.server);
        if (me == null || player.level() != me) {
            return 0f;
        }
        FactionDef def = Factions.definitions().get(faction);
        if (def == null || def.controlZones().isEmpty() || def.isolationist()) {
            return 0f;
        }
        int range = 50;
        MiddleEarthMap map = MiddleEarthMap.get();
        double px = map.pixelX(player.getX());
        double pz = map.pixelZ(player.getZ());
        double nearest = Double.MAX_VALUE;
        for (FactionDef.MapArea z : def.controlZones()) {
            double d = Math.sqrt((px - z.x()) * (px - z.x()) + (pz - z.z()) * (pz - z.z())) - z.radius();
            nearest = Math.min(nearest, Math.max(0, d));
        }
        return nearest > range ? 0f : (float) Math.max(0, Math.min(1, 1 - nearest / range));
    }

    /** The nearest living NPC of the faction within npc_radius, in the player's own level. */
    public static Optional<LotrNpc> nearestNpc(ServerPlayer player, ResourceLocation faction) {
        int r = settings().npcRadius();
        return player.level().getEntitiesOfClass(LotrNpc.class, player.getBoundingBox().inflate(r),
                n -> n.isAlive() && FactionKills.factionOf(n).filter(faction::equals).isPresent()
                        && n.distanceTo(player) <= r)
                .stream().min(Comparator.comparingDouble(n -> n.distanceTo(player)));
    }

    /** The faction of the nearest faction NPC within npc_radius, if any. */
    public static Optional<ResourceLocation> nearestFaction(ServerPlayer player) {
        int r = settings().npcRadius();
        return player.level().getEntitiesOfClass(LotrNpc.class, player.getBoundingBox().inflate(r),
                n -> n.isAlive() && FactionKills.factionOf(n).isPresent() && n.distanceTo(player) <= r)
                .stream().min(Comparator.comparingDouble(n -> n.distanceTo(player)))
                .flatMap(FactionKills::factionOf);
    }
}
