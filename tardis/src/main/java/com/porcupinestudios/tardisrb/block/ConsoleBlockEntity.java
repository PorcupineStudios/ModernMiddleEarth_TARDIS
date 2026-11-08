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
    /**
     * A control as the client sees it: its model, where it stands on which panel, what each clickable part does and
     * the values its gauges show (see {@code progression/Control}).
     */
    public record Shown(String id, String model, int panel, float u, float v, float turn, float scale,
            Map<String, String> actions, List<String> shows) {
        /** What clicking this part does ({@code none} if nothing). */
        public String action(String click) {
            return actions.getOrDefault(click, "none");
        }

        CompoundTag save() {
            CompoundTag c = new CompoundTag();
            c.putString("id", id);
            c.putString("model", model);
            c.putInt("panel", panel);
            c.putFloat("u", u);
            c.putFloat("v", v);
            c.putFloat("turn", turn);
            c.putFloat("scale", scale);
            CompoundTag a = new CompoundTag();
            actions.forEach(a::putString);
            c.put("actions", a);
            ListTag l = new ListTag();
            shows.forEach(x -> l.add(StringTag.valueOf(x)));
            c.put("shows", l);
            return c;
        }

        static Shown load(CompoundTag c) {
            Map<String, String> a = new HashMap<>();
            CompoundTag at = c.getCompound("actions");
            at.getAllKeys().forEach(k -> a.put(k, at.getString(k)));
            List<String> sh = new ArrayList<>();
            c.getList("shows", Tag.TAG_STRING).forEach(t -> sh.add(t.getAsString()));
            return new Shown(c.getString("id"), c.getString("model"), c.getInt("panel"), c.getFloat("u"),
                    c.getFloat("v"), c.getFloat("turn"), c.contains("scale") ? c.getFloat("scale") : 1F, Map.copyOf(a),
                    List.copyOf(sh));
        }
    }

    private static final Set<ConsoleBlockEntity> CLIENT = Collections.newSetFromMap(new WeakHashMap<>());

    private List<Shown> shown = new ArrayList<>();
    private int dx;
    private int dy;
    private int dz;
    private int step;
    private final Map<String, Long> usedAt = new HashMap<>();
    private final Map<String, Integer> uses = new HashMap<>();
    private List<String> readout = new ArrayList<>();
    /**
     * The settings the controls hold, by role (the action they are bound to): the coordinate slots {@code x0}..,
     * {@code z0}.., {@code y0}.., and {@code relative}, {@code land}, {@code save}, {@code handbrake}, {@code facing}
     * (0-3 = south, west, north, east), {@code destination}. A control bound to a role is drawn at its value.
     */
    private final Map<String, Integer> state = new HashMap<>();
    /** Each role's value before its last change, for the animation between the two. */
    private final Map<String, Integer> before = new HashMap<>();
    /** Memory slots: saved coordinate settings (roles to values), by slot. */
    private final Map<Integer, CompoundTag> memory = new HashMap<>();
    /** Values gauges show, 0..1 of full ({@code charge}, {@code rooms}), refreshed every second on the server. */
    private Map<String, Float> values = new HashMap<>();
    /** Which way the monitor faces from the rotor axis, degrees ({@code atan2(z, x)}); NaN = where the model has it. */
    private float monitorAngle = Float.NaN;
    /** Client only: the angle the monitor is drawn at, easing toward {@link #monitorAngle}. */
    public float drawnAngle = Float.NaN;

    public ConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(TardisBlocks.CONSOLE_BE.get(), pos, state);
        defaults();
    }

    /** Relative coordinates and land-on-ground start on (as the 1.7.10 console mostly had them). */
    private void defaults() {
        state.put("relative", 1);
        state.put("land", 1);
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
            Map<String, Float> vals = tardis.isEmpty() ? Map.of() : ConsoleControls.values(tardis.get());
            if (!now.equals(be.shown) || !lines.equals(be.readout) || !vals.equals(be.values)) {
                be.shown = now;
                be.readout = new ArrayList<>(lines);
                be.values = new HashMap<>(vals);
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
        return state("destination");
    }

    public void setDestination(int index) {
        setState("destination", index);
    }

    /**
     * Marks a control's clickable part ({@link com.porcupinestudios.tardisrb.console.ConsoleLayout#key}) as just used
     * (animations) and counts its uses (lever position, dial notch).
     */
    public void used(String control) {
        usedAt.put(control, level == null ? 0 : level.getGameTime());
        uses.merge(control, 1, Integer::sum);
        changed();
    }

    public boolean handbrake() {
        return state("handbrake") != 0;
    }

    public void setHandbrake(boolean on) {
        setState("handbrake", on ? 1 : 0);
    }

    /** A role's value (0 if never set). */
    public int state(String role) {
        return state.getOrDefault(role, 0);
    }

    public boolean hasState(String role) {
        return state.containsKey(role);
    }

    /** A role's value before its last change. */
    public int stateBefore(String role) {
        return before.getOrDefault(role, state(role));
    }

    public void setState(String role, int value) {
        before.put(role, state(role));
        state.put(role, value);
        changed();
    }

    /** The settings saved in a memory slot, if any (roles to values). */
    public Optional<CompoundTag> memory(int slot) {
        return Optional.ofNullable(memory.get(slot)).map(CompoundTag::copy);
    }

    public void setMemory(int slot, CompoundTag saved) {
        memory.put(slot, saved.copy());
        changed();
    }

    public int memoryUsed() {
        return memory.size();
    }

    /** The roles and values held now, as a tag (for a memory slot). */
    public CompoundTag stateTag() {
        CompoundTag t = new CompoundTag();
        state.forEach(t::putInt);
        return t;
    }

    /** A gauge's value, 0..1 of full. */
    public float value(String name) {
        return values.getOrDefault(name, 0F);
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
        tag.putInt("dataVersion", 2);
        ListTag list = new ListTag();
        for (Shown s : shown) {
            list.add(s.save());
        }
        tag.put("shown", list);
        CompoundTag vals = new CompoundTag();
        values.forEach(vals::putFloat);
        tag.put("values", vals);
        tag.putIntArray("offset", new int[] {dx, dy, dz});
        tag.putInt("step", step);
        tag.put("state", stateTag());
        CompoundTag was = new CompoundTag();
        before.forEach(was::putInt);
        tag.put("state_before", was);
        CompoundTag mem = new CompoundTag();
        memory.forEach((k, v) -> mem.put(String.valueOf(k), v));
        tag.put("memory", mem);
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
        // version 1 listed the old console's parts: dropped, the server lists the controls again within a second
        shown = new ArrayList<>();
        if (tag.getInt("dataVersion") >= 2) {
            for (Tag t : tag.getList("shown", Tag.TAG_COMPOUND)) {
                shown.add(Shown.load((CompoundTag) t));
            }
        }
        values = new HashMap<>();
        CompoundTag vals = tag.getCompound("values");
        vals.getAllKeys().forEach(k -> values.put(k, vals.getFloat(k)));
        int[] off = tag.getIntArray("offset");
        dx = off.length == 3 ? off[0] : 0;
        dy = off.length == 3 ? off[1] : 0;
        dz = off.length == 3 ? off[2] : 0;
        step = tag.getInt("step");
        state.clear();
        before.clear();
        defaults();
        CompoundTag st = tag.getCompound("state");
        st.getAllKeys().forEach(k -> state.put(k, st.getInt(k)));
        CompoundTag was = tag.getCompound("state_before");
        was.getAllKeys().forEach(k -> before.put(k, was.getInt(k)));
        memory.clear();
        CompoundTag mem = tag.getCompound("memory");
        for (String k : mem.getAllKeys()) {
            try {
                memory.put(Integer.parseInt(k), mem.getCompound(k));
            } catch (NumberFormatException ignored) {
                // not a slot
            }
        }
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
