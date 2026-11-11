package com.porcupinestudios.lotrport.land;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.society.Owner;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Claimed land: a square of whole chunks around {@code center}, {@code radius} chunks each way, owned by a colony or
 * a player. {@code id} is the banner entity's UUID, or the colony's id for the land around its Hall.
 */
public record Claim(UUID id, Owner owner, ResourceKey<Level> dimension, int centerX, int centerZ, int radius,
        Optional<BlockPos> banner) {
    public static final Codec<Claim> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Claim::id),
            Owner.CODEC.fieldOf("owner").forGetter(Claim::owner),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Claim::dimension),
            Codec.INT.fieldOf("x").forGetter(Claim::centerX),
            Codec.INT.fieldOf("z").forGetter(Claim::centerZ),
            Codec.INT.optionalFieldOf("radius", 0).forGetter(Claim::radius),
            BlockPos.CODEC.optionalFieldOf("banner").forGetter(Claim::banner))
            .apply(i, Claim::new));

    public static Claim around(UUID id, Owner owner, ResourceKey<Level> dimension, BlockPos at, int radius,
            Optional<BlockPos> banner) {
        return new Claim(id, owner, dimension, at.getX() >> 4, at.getZ() >> 4, Math.max(0, radius), banner);
    }

    public boolean covers(int cx, int cz) {
        return Math.abs(cx - centerX) <= radius && Math.abs(cz - centerZ) <= radius;
    }

    public ChunkPos center() {
        return new ChunkPos(centerX, centerZ);
    }
}
