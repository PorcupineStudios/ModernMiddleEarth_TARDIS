package com.porcupinestudios.tardisrb.compat.create;

import com.porcupinestudios.tardisrb.block.ExteriorBlock;
import com.porcupinestudios.tardisrb.core.TardisManager;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Turns rotation into charge for the nearest TARDIS: |RPM| x {@code charge_per_rpm_second} per second (tuning file).
 * Fractions are kept between ticks; charge above the TARDIS's cap is lost.
 */
public final class WinderBlockEntity extends KineticBlockEntity {
    private double buffer;
    private UUID linked;

    public WinderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !(level instanceof ServerLevel server)) {
            return;
        }
        float speed = Math.abs(getSpeed());
        if (speed == 0) {
            return;
        }
        buffer += speed * TardisContent.tuning().winder().chargePerRpmSecond() / 20.0;
        if (buffer < 1) {
            return;
        }
        long whole = (long) buffer;
        buffer -= whole;
        tardis(server).ifPresent(t -> t.addCharge(whole));
    }

    /** The TARDIS this winder feeds: the interior it stands in, else a police box within link range. */
    public Optional<TardisRecord> tardis(ServerLevel server) {
        if (linked != null) {
            Optional<TardisRecord> known = TardisManager.get(server.getServer()).get(linked);
            if (known.isPresent() && near(server, known.get())) {
                return known;
            }
            linked = null;
        }
        Optional<TardisRecord> found = Interiors.at(server, worldPosition);
        if (found.isEmpty()) {
            int r = TardisContent.tuning().winder().linkRange();
            for (BlockPos p : BlockPos.betweenClosed(worldPosition.offset(-r, -r, -r), worldPosition.offset(r, r, r))) {
                found = ExteriorBlock.tardisAt(server, p);
                if (found.isPresent()) {
                    break;
                }
            }
        }
        found.ifPresent(t -> linked = t.id());
        return found;
    }

    private boolean near(ServerLevel server, TardisRecord t) {
        if (server.dimension().equals(Interiors.DIMENSION)) {
            return Interiors.plotAt(worldPosition) == t.plot();
        }
        int r = TardisContent.tuning().winder().linkRange() + 1;
        return t.location().map(g -> g.dimension().equals(server.dimension())
                && g.pos().closerThan(worldPosition, r + 1)).orElse(false);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putDouble("charge_buffer", buffer);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        buffer = tag.getDouble("charge_buffer");
    }
}
