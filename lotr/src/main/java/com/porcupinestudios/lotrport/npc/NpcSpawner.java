package com.porcupinestudios.lotrport.npc;

import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Natural NPC spawning, as LOTR's NPC spawner: around each player, now and then, a group from the biome's
 * {@link NpcSpawns} table appears 24 to 48 blocks away on dry ground, unless enough NPCs are already near. These NPCs
 * are ordinary (no person record) and despawn when no player is near.
 */
public final class NpcSpawner {
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 48;

    private NpcSpawner() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NpcSpawner::onTick);
    }

    private static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.getGameRules().getBoolean(
                net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            Optional<ResourceLocation> biome = level.getBiome(player.blockPosition()).unwrapKey()
                    .map(k -> k.location());
            attempt(level, player, biome.flatMap(NpcSpawns::forBiome), NpcSpawns::forBiome);
            attempt(level, player, biome.flatMap(NpcSpawns::animalsForBiome), NpcSpawns::animalsForBiome);
        }
    }

    private static void attempt(ServerLevel level, ServerPlayer player, Optional<NpcSpawns.Table> table,
            Function<ResourceLocation, Optional<NpcSpawns.Table>> lookup) {
        if (table.isEmpty() || table.get().entries().isEmpty()
                || (level.getGameTime() + player.getId()) % Math.max(1, table.get().interval()) != 0) {
            return;
        }
        trySpawn(level, player, table.get(), lookup);
    }

    public static int trySpawn(ServerLevel level, ServerPlayer player, NpcSpawns.Table table) {
        return trySpawn(level, player, table, NpcSpawns::forBiome);
    }

    /** One spawn attempt near the player; returns how many NPCs appeared. */
    public static int trySpawn(ServerLevel level, ServerPlayer player, NpcSpawns.Table table,
            Function<ResourceLocation, Optional<NpcSpawns.Table>> lookup) {
        RandomSource random = level.random;
        if (random.nextFloat() >= table.chance()) {
            return 0;
        }
        AABB near = player.getBoundingBox().inflate(table.radius());
        if (level.getEntitiesOfClass(LotrNpc.class, near,
                n -> !table.countOnlyAnimals() || NpcSpawns.isAnimal(n.typeId())).size() >= table.cap()) {
            return 0;
        }
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = MIN_DISTANCE + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
        int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
            return 0;
        }
        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!level.getFluidState(ground.below()).isEmpty() || !level.getFluidState(ground).isEmpty()) {
            return 0;
        }
        // the table of the spot itself, so groups don't spill across biome borders
        Optional<NpcSpawns.Table> here = level.getBiome(ground).unwrapKey().flatMap(k -> lookup.apply(k.location()));
        if (here.isEmpty() || here.get().entries().isEmpty()) {
            return 0;
        }
        NpcSpawns.Table spot = here.get();
        if (!spot.countOnlyAnimals()) { // people, not LOTR's animal tables
            // borders: land that changed hands brings its holder's people (border/TerritorySpawns)
            spot = com.porcupinestudios.lotrport.border.TerritorySpawns.adjust(level, ground, spot, random);
            if (spot.entries().isEmpty()) {
                return 0;
            }
        }
        NpcSpawns.Entry entry = pick(spot, random);
        if (entry.dark() && !spot.hostilesInDay() && !Monster.isDarkEnoughToSpawn(level, ground, random)) {
            return 0; // LOTR: orcs, wargs and spiders only in the dark, unless the biome allows hostiles by day
        }
        int count = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    ground.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2));
            Optional<LotrNpc> npc = LotrNpc.spawn(level, entry.npc(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            if (npc.isPresent()) {
                npc.get().maybeRide();
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * World generation (mixin AnimalGenerationMixin, after vanilla's own creatures): a newly generated chunk gets a
     * group from its biome's animal table with the table's generation_chance, as LOTR's animals came with the land
     * (1.7.10 spawned a biome's creature list at chunk generation).
     */
    public static void populate(net.minecraft.server.level.WorldGenRegion region) {
        net.minecraft.world.level.ChunkPos chunk = region.getCenter();
        BlockPos middle = new BlockPos(chunk.getMiddleBlockX(), 0, chunk.getMiddleBlockZ());
        Optional<NpcSpawns.Table> table = region.getBiome(middle).unwrapKey()
                .flatMap(k -> NpcSpawns.animalsForBiome(k.location()));
        if (table.isEmpty() || table.get().entries().isEmpty()) {
            return;
        }
        RandomSource random = region.getRandom();
        if (random.nextFloat() >= table.get().generationChance()) {
            return;
        }
        NpcSpawns.Entry entry = pick(table.get(), random);
        int count = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
        for (int i = 0; i < count; i++) {
            int x = chunk.getMinBlockX() + random.nextInt(16);
            int z = chunk.getMinBlockZ() + random.nextInt(16);
            int y = region.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (!region.getFluidState(at.below()).isEmpty() || !region.getFluidState(at).isEmpty()) {
                continue;
            }
            LotrNpc.create(region.getLevel(), entry.npc(), x + 0.5, y, z + 0.5, random)
                    .ifPresent(region::addFreshEntity);
        }
    }

    private static NpcSpawns.Entry pick(NpcSpawns.Table table, RandomSource random) {
        int total = 0;
        for (NpcSpawns.Entry e : table.entries()) {
            total += Math.max(0, e.weight());
        }
        int roll = total <= 0 ? 0 : random.nextInt(total);
        for (NpcSpawns.Entry e : table.entries()) {
            roll -= Math.max(0, e.weight());
            if (roll < 0) {
                return e;
            }
        }
        return table.entries().get(0);
    }
}
