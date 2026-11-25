package com.porcupinestudios.ringbearer.harness.content;

import com.porcupinestudios.ringbearer.harness.HarnessMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A driver's keys, sent every tick while they hold the reins ({@link Reins}): forward (1 go, -1 back, 0 neither), turn
 * (1 left, -1 right), and sprint (canter).
 */
public record DriveInput(float forward, float turn, boolean sprint) implements CustomPacketPayload {
    public static final Type<DriveInput> TYPE = new Type<>(HarnessMod.id("drive"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DriveInput> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DriveInput::forward, ByteBufCodecs.FLOAT, DriveInput::turn, ByteBufCodecs.BOOL,
            DriveInput::sprint, DriveInput::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
