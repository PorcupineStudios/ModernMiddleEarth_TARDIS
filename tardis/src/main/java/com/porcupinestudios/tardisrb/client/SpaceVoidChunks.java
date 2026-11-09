package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.TardisRB;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * In space this client makes the empty chunks it needs itself: the chunk the player is in and the ones it reaches in
 * the next ticks, as empty void, loaded exactly as if the server had sent them. Minecraft stops ticking a player whose
 * chunk is missing, so without them a player flying faster than the server sends chunks froze, and a player the
 * server leaves out of chunk loading ({@code space/SpaceChunks}) couldn't move at all. A chunk the server sends
 * replaces the void one; void chunks left far behind are dropped again.
 */
@EventBusSubscriber(modid = TardisRB.MOD_ID, value = Dist.CLIENT)
public final class SpaceVoidChunks {
    /** Ticks ahead of the player made ready, and chunks round each spot. */
    private static final int AHEAD = 2;
    private static final int AROUND = 1;

    private static final LongSet MADE = new LongOpenHashSet();
    private static ClientLevel madeIn;

    private SpaceVoidChunks() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        ClientPacketListener net = mc.getConnection();
        if (level != madeIn) {
            MADE.clear(); // the old level and its chunks are gone
            madeIn = level;
        }
        if (player == null || level == null || net == null) {
            return;
        }
        boolean space = GlobeRenderer.systemNear(level, player.position()) != null
                || GlobeRenderer.planetBelow(level, player.getX(), player.getZ()) != null;
        dropBehind(level, net, player.chunkPosition(), space ? mc.options.getEffectiveRenderDistance() + 2 : -1);
        if (!space) {
            return;
        }
        Vec3 step = player.getDeltaMovement();
        for (int k = 0; k <= AHEAD; k++) {
            ChunkPos at = new ChunkPos(BlockPos.containing(player.position().add(step.scale(k))));
            for (int dx = -AROUND; dx <= AROUND; dx++) {
                for (int dz = -AROUND; dz <= AROUND; dz++) {
                    make(level, net, at.x + dx, at.z + dz);
                }
            }
        }
    }

    private static void make(ClientLevel level, ClientPacketListener net, int x, int z) {
        if (level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null) {
            return;
        }
        LevelChunk empty = new LevelChunk(level, new ChunkPos(x, z));
        net.handleLevelChunkWithLight(new ClientboundLevelChunkWithLightPacket(empty, level.getLightEngine(), null,
                null));
        MADE.add(ChunkPos.asLong(x, z));
    }

    /**
     * Drops void chunks further than {@code keep} chunks from the player (all of them for -1). A chunk the server has
     * since filled with blocks is the server's now and stays.
     */
    private static void dropBehind(ClientLevel level, ClientPacketListener net, ChunkPos at, int keep) {
        for (LongIterator it = MADE.iterator(); it.hasNext();) {
            long key = it.nextLong();
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            if (keep >= 0 && Math.max(Math.abs(x - at.x), Math.abs(z - at.z)) <= keep) {
                continue;
            }
            it.remove();
            LevelChunk chunk = level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null && isVoid(chunk)) {
                net.handleForgetLevelChunk(new ClientboundForgetLevelChunkPacket(new ChunkPos(x, z)));
            }
        }
    }

    private static boolean isVoid(LevelChunk chunk) {
        if (!chunk.getBlockEntities().isEmpty()) {
            return false;
        }
        for (LevelChunkSection section : chunk.getSections()) {
            if (!section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }
}
