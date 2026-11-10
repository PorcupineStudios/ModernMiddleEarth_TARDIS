package com.porcupinestudios.lotrport.npc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * LOTR's invasions (LOTRInvasions, LOTRBiomeInvasionSpawns): once a second each biome rolls its invasion chances
 * ({@code invasion_spawns/biomes.json}, LOTR's chance per second); on a hit, one of that row's invasions
 * ({@code invasion/<id>.json}: faction, mobs) sends {@code size_min}-{@code size_max} of its mobs at the player from
 * 32 blocks away. LOTR's invasion banner, waves and conquest are not ported.
 */
public final class InvasionSpawner {
    public record Invasion(Optional<ResourceLocation> faction, List<ResourceLocation> mobs, int sizeMin, int sizeMax,
            String lang) {
        public static final Codec<Invasion> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.optionalFieldOf("faction").forGetter(Invasion::faction),
                ResourceLocation.CODEC.listOf().fieldOf("mobs").forGetter(Invasion::mobs),
                Codec.INT.optionalFieldOf("size_min", 6).forGetter(Invasion::sizeMin),
                Codec.INT.optionalFieldOf("size_max", 12).forGetter(Invasion::sizeMax),
                Codec.STRING.optionalFieldOf("lang", "").forGetter(Invasion::lang))
                .apply(i, Invasion::new));
    }

    private static final CodecJsonLoader<Invasion> INVASIONS = new CodecJsonLoader<>("invasion", Invasion.CODEC);
    private static final CodecJsonLoader<JsonElement> BIOMES = new CodecJsonLoader<>("invasion_spawns",
            net.minecraft.util.ExtraCodecs.JSON);

    private InvasionSpawner() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> {
            e.addListener(INVASIONS);
            e.addListener(BIOMES);
        });
        NeoForge.EVENT_BUS.addListener(InvasionSpawner::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || player.isSpectator()
                || !(player.level() instanceof ServerLevel level)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        JsonElement table = BIOMES.entries().get(ResourceLocation.fromNamespaceAndPath("lotr_port", "biomes"));
        if (table == null || !table.isJsonObject()) {
            return;
        }
        Optional<ResourceLocation> biome = level.getBiome(player.blockPosition()).unwrapKey().map(k -> k.location());
        if (biome.isEmpty() || !biome.get().getNamespace().equals("lotr_port")
                || !table.getAsJsonObject().has(biome.get().getPath())) {
            return;
        }
        RandomSource r = level.random;
        for (JsonElement row : table.getAsJsonObject().getAsJsonArray(biome.get().getPath())) {
            JsonObject o = row.getAsJsonObject();
            if (r.nextDouble() < o.get("chance_per_second").getAsDouble()) {
                JsonArray ids = o.getAsJsonArray("invasions");
                if (!ids.isEmpty()) {
                    invade(level, player, ResourceLocation.parse(ids.get(r.nextInt(ids.size())).getAsString()));
                }
                return;
            }
        }
    }

    /** Sends an invasion at the player; returns how many mobs came. */
    public static int invade(ServerLevel level, ServerPlayer player, ResourceLocation id) {
        Invasion inv = INVASIONS.entries().get(id);
        if (inv == null || inv.mobs().isEmpty()) {
            return 0;
        }
        RandomSource r = level.random;
        double a = r.nextDouble() * Math.PI * 2;
        BlockPos base = BlockPos.containing(player.getX() + Math.cos(a) * 32, 0, player.getZ() + Math.sin(a) * 32);
        if (!level.hasChunkAt(base)) {
            return 0;
        }
        int n = inv.sizeMin() + r.nextInt(Math.max(1, inv.sizeMax() - inv.sizeMin() + 1));
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    base.offset(r.nextInt(7) - 3, 0, r.nextInt(7) - 3));
            Optional<LotrNpc> npc = LotrNpc.spawn(level, inv.mobs().get(r.nextInt(inv.mobs().size())),
                    p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            if (npc.isPresent()) {
                npc.get().maybeRide();
                if (npc.get().isEnemy(player)) {
                    npc.get().setTarget(player);
                }
                spawned++;
            }
        }
        if (spawned > 0 && !inv.lang().isEmpty()) {
            player.sendSystemMessage(Component.literal("Invasion: ").append(Component.translatable(inv.lang())));
        }
        return spawned;
    }

    public static Map<ResourceLocation, Invasion> invasions() {
        return INVASIONS.entries();
    }
}
