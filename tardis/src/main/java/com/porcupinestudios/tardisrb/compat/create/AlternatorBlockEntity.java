package com.porcupinestudios.tardisrb.compat.create;

import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Artron Alternator's working part ({@link AlternatorBlock}): finds the police box under it, turns rotation into
 * charge for it (|RPM| x {@code alternator.charge_per_rpm_second} per second, fractions kept; charge over the cap is
 * lost), and tells clients how far down the box is ({@code drop}, for the arms) and whether it is charging (the
 * lightning).
 */
public final class AlternatorBlockEntity extends KineticBlockEntity {
    private double buffer;
    /** Blocks from the alternator down to the box's top half; 0 = no box under it. */
    private int drop;
    private boolean charging;

    public AlternatorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int drop() {
        return drop;
    }

    public boolean charging() {
        return charging;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        Optional<TardisRecord> tardis = box(server);
        boolean now = false;
        float speed = Math.abs(getSpeed());
        if (tardis.isPresent() && speed > 0) {
            buffer += speed * TardisContent.tuning().alternator().chargePerRpmSecond() / 20.0;
            long whole = (long) buffer;
            buffer -= whole;
            now = whole == 0 ? charging : tardis.get().addCharge(whole) > 0;
        }
        if (now != charging) {
            charging = now;
            sendData();
        }
    }

    /** The TARDIS whose police box stands under this, within reach; sets {@link #drop}. */
    private Optional<TardisRecord> box(ServerLevel server) {
        int reach = TardisContent.tuning().alternator().reach();
        for (int d = 1; d <= reach; d++) {
            BlockPos p = worldPosition.below(d);
            if (server.getBlockState(p).getBlock() instanceof ExteriorBlock) {
                Optional<TardisRecord> t = ExteriorBlock.tardisAt(server, p);
                setDrop(t.isPresent() ? d : 0);
                return t;
            }
            if (!server.getBlockState(p).isAir()) {
                break;
            }
        }
        setDrop(0);
        return Optional.empty();
    }

    private void setDrop(int d) {
        if (d != drop) {
            drop = d;
            sendData();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("charge_buffer", buffer);
        tag.putInt("drop", drop);
        tag.putBoolean("charging", charging);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        buffer = tag.getDouble("charge_buffer");
        drop = tag.getInt("drop");
        charging = tag.getBoolean("charging");
    }
}
