package com.porcupinestudios.tardisrb.block;

import com.porcupinestudios.tardisrb.console.ConsoleControls;
import com.porcupinestudios.tardisrb.core.TardisRecord;
import com.porcupinestudios.tardisrb.interior.Interiors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The console's state: which controls are on it (those whose requirements hold, refreshed every second on the server
 * and synced to clients, so the renderer and click test need no data files), the course being set (offset from where
 * the TARDIS stands, step size, picked destination), when each control was last used (for the animations), what the
 * monitor shows and which way it faces round the time rotor.
 */
public final class ConsoleBlockEntity extends BlockEntity {
    /** A control as the client sees it. */
    public record Shown(String id, String kind, String action, String part) {}

    private static final Set<ConsoleBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    private List<Shown> shown = new ArrayList<>();
    private int dx;
    private int dy;
    private int dz;
    private int step;
    private int destination;
    private final Map<String, Long> usedAt = new HashMap<>();
    private final Map<String, Integer> uses = new HashMap<>();
    private List<String> readout = new ArrayList<>();
    /** Which way the monitor faces from the rotor axis, degrees ({@code atan2(z, x)}); NaN = where the model has it. */
    private float monitorAngle = Float.NaN;
    /** Client only: the angle the monitor is drawn at, easing toward {@link #monitorAngle}. */
    public float drawnAngle = Float.NaN;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(TardisBlocks.CONSOLE_BE.get(), pos, state);
    }

    /** Consoles loaded on this client, for the click test. */
    public static List<ConsoleBlockEntity> clientLoaded() {
        synchronized (CLIENT) {
            return new ArrayList<>(CLIENT);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConsoleBlockEntity be) {
        if (level.getGameTime() % 20 == 0 && level instanceof ServerLevel sl) {
            List<Shown> now = ConsoleControls.shown(sl, pos);
            Optional<TardisRecord> tardis = Interiors.at(sl, pos);
            List<String> lines = tardis.isEmpty() ? List.of()
                    : ConsoleControls.readout(sl.getServer(), tardis.get(), be);
            if (!now.equals(be.shown) || !lines.equals(be.readout)) {
                be.shown = now;
                be.readout = new ArrayList<>(lines);
                be.changed();
            }
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            synchronized (CLIENT) {
                CLIENT.add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (CLIENT) {
            CLIENT.remove(this);
        }
    }

    public List<Shown> shown() {
        return List.copyOf(shown);
    }

    public void setShown(List<Shown> controls) {
        shown = new ArrayList<>(controls);
        changed();
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public int dz() {
        return dz;
    }

    public void setOffset(int x, int y, int z) {
        dx = x;
        dy = y;
        dz = z;
        changed();
    }

    public int step() {
        return step;
    }

    public void setStep(int index) {
        step = index;
        changed();
    }

    public int destination() {
        return destination;
    }

    public void setDestination(int index) {
        destination = index;
        changed();
    }

    /** Marks a control as just used (animations) and counts its uses (lever position, dial turns). */
    public void used(String control) {
        usedAt.put(control, level == null ? 0 : level.getGameTime());
        uses.merge(control, 1, Integer::sum);
        changed();
    }

    public List<String> readout() {
        return List.copyOf(readout);
    }

    public float monitorAngle() {
        return monitorAngle;
    }

    public void setMonitorAngle(float degrees) {
        monitorAngle = degrees;
        changed();
    }

    public long usedAt(String control) {
        return usedAt.getOrDefault(control, Long.MIN_VALUE / 2);
    }

    public int uses(String control) {
        return uses.getOrDefault(control, 0);
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("dataVersion", 1);
        ListTag list = new ListTag();
        for (Shown s : shown) {
            CompoundTag c = new CompoundTag();
            c.putString("id", s.id());
            c.putString("kind", s.kind());
            c.putString("action", s.action());
            c.putString("part", s.part());
            list.add(c);
        }
        tag.put("shown", list);
        tag.putIntArray("offset", new int[] {dx, dy, dz});
        tag.putInt("step", step);
        tag.putInt("destination", destination);
        CompoundTag used = new CompoundTag();
        usedAt.forEach(used::putLong);
        tag.put("used_at", used);
        CompoundTag count = new CompoundTag();
        uses.forEach(count::putInt);
        tag.put("uses", count);
        ListTag lines = new ListTag();
        readout.forEach(l -> lines.add(StringTag.valueOf(l)));
        tag.put("readout", lines);
        if (!Float.isNaN(monitorAngle)) {
            tag.putFloat("monitor_angle", monitorAngle);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        shown = new ArrayList<>();
        for (Tag t : tag.getList("shown", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            shown.add(new Shown(c.getString("id"), c.getString("kind"), c.getString("action"),
                    c.getString("part")));
        }
        int[] off = tag.getIntArray("offset");
        dx = off.length == 3 ? off[0] : 0;
        dy = off.length == 3 ? off[1] : 0;
        dz = off.length == 3 ? off[2] : 0;
        step = tag.getInt("step");
        destination = tag.getInt("destination");
        usedAt.clear();
        CompoundTag used = tag.getCompound("used_at");
        used.getAllKeys().forEach(k -> usedAt.put(k, used.getLong(k)));
        uses.clear();
        CompoundTag count = tag.getCompound("uses");
        count.getAllKeys().forEach(k -> uses.put(k, count.getInt(k)));
        readout = new ArrayList<>();
        for (Tag t : tag.getList("readout", Tag.TAG_STRING)) {
            readout.add(t.getAsString());
        }
        monitorAngle = tag.contains("monitor_angle") ? tag.getFloat("monitor_angle") : Float.NaN;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
