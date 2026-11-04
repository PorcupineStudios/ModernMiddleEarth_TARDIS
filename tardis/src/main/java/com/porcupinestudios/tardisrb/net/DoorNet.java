package com.porcupinestudios.tardisrb.net;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.block.InteriorDoorBlock;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Using an open doorway (Aaron, 2026-11-04): the portal fills the doorway, so a right-click there never reaches the
 * door block; the client finds the doorway along the player's view ({@code client/DoorInput}) and sends {@link Use},
 * and the server uses that door block as if it had been clicked.
 */
public final class DoorNet {
    /** How far from the doorway a player may be (blocks, squared). */
    private static final double REACH_SQR = 8 * 8;

    private DoorNet() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(DoorNet::payloads);
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(Use.TYPE, Use.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                use(sp, p.pos());
            }
        }));
    }

    static void use(ServerPlayer player, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        BlockState state = player.serverLevel().getBlockState(pos);
        boolean door = state.getBlock() instanceof ExteriorBlock || state.getBlock() instanceof InteriorDoorBlock;
        if (!door || !state.getValue(BlockStateProperties.OPEN) || player.isSpectator()
                || player.position().distanceToSqr(center) > REACH_SQR) {
            return;
        }
        state.useWithoutItem(player.serverLevel(), player, new BlockHitResult(center, Direction.UP, pos, false));
    }

    /** The player used the open doorway of the door block at {@code pos}. */
    public record Use(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(TardisRB.id("door_use"));
        public static final StreamCodec<ByteBuf, Use> CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Use::pos,
                Use::new);

        @Override
        public Type<Use> type() {
            return TYPE;
        }
    }
}
