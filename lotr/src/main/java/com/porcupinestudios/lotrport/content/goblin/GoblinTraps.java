package com.porcupinestudios.lotrport.content.goblin;

import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The Front Porch's trap (2026-11-26, Aaron: the floor should literally swing open). A porch floor is armed when
 * there are at least {@code min_drop} open blocks under it (the real porch's chute; decoys lie on rock, and the block
 * is the same, so nothing on screen or in F3 tells them apart). Stand on an armed floor for {@code trigger_ticks}
 * and grit trickles from the roof for the last {@code warn_ticks}, then the floor springs:
 * it splits along its long axis into two leaves that swing down on their outer edges (block display entities, the
 * vanilla client animating each of {@code swing_steps} steps), the goblins of {@code ambush} waiting at the bottom
 * of the chute. After {@code reset_ticks}, with nobody near, the leaves swing back up and the floor is whole again.
 * An armed floor also springs on a redstone signal (so a Create contraption or a lever can work one). Numbers in
 * {@code data/<ns>/goblin_traps/settings.json}; open traps are saved ({@code lotr_port_goblin_traps}).
 */
public final class GoblinTraps {
    public record Ambush(ResourceLocation npc, int min, int max) {
        public static final Codec<Ambush> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Ambush::npc),
                Codec.INT.fieldOf("min").forGetter(Ambush::min),
                Codec.INT.fieldOf("max").forGetter(Ambush::max)).apply(i, Ambush::new));
    }

    public record Settings(List<Integer> triggerTicks, int warnTicks, int swingSteps, int stepTicks, int resetTicks,
                           int maxBlocks, int minDrop, List<Ambush> ambush, int ambushRadius, int dust) {
        public static final Codec<Settings> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.listOf().fieldOf("trigger_ticks").forGetter(Settings::triggerTicks),
                Codec.INT.fieldOf("warn_ticks").forGetter(Settings::warnTicks),
                Codec.INT.fieldOf("swing_steps").forGetter(Settings::swingSteps),
                Codec.INT.fieldOf("step_ticks").forGetter(Settings::stepTicks),
                Codec.INT.fieldOf("reset_ticks").forGetter(Settings::resetTicks),
                Codec.INT.fieldOf("max_blocks").forGetter(Settings::maxBlocks),
                Codec.INT.fieldOf("min_drop").forGetter(Settings::minDrop),
                Ambush.CODEC.listOf().fieldOf("ambush").forGetter(Settings::ambush),
                Codec.INT.fieldOf("ambush_radius").forGetter(Settings::ambushRadius),
                Codec.INT.fieldOf("dust").forGetter(Settings::dust)).apply(i, Settings::new));
    }

    private static final CodecJsonLoader<Settings> LOADER = new CodecJsonLoader<>("goblin_traps", Settings.CODEC);
    private static final String FILE = "lotr_port_goblin_traps";

    /** A player standing on an armed floor: since when, where, and the tick it springs. */
    private record Standing(BlockPos pos, long since, long springs) {}

    private static final Map<UUID, Standing> STANDING = new HashMap<>();

    private GoblinTraps() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(GoblinTraps::onTick);
    }

    public static Optional<Settings> settings() {
        return Optional.ofNullable(LOADER.entries().get(LotrPort.id("settings")));
    }

    // ---- one sprung trap ----

    /** One leaf block of an open trap: where it was, what it was, and its display entity. */
    record Leaf(BlockPos pos, BlockState state, UUID display, Vector3f offset, float sign) {}

    /** An open (or opening, or closing) trap. */
    static final class Trap {
        final List<Leaf> leaves = new ArrayList<>();
        Direction.Axis along;
        long closeAt;
        /** Swing step now (0 shut .. steps open) and which way it is moving (1 opening, -1 closing, 0 still). */
        int step;
        int moving;
        long nextStep;
        AABB area;
    }

    private static final class Data extends SavedData {
        final List<Trap> traps = new ArrayList<>();

        CompoundTag saveTrap(Trap t) {
            CompoundTag tag = new CompoundTag();
            tag.putString("along", t.along.getName());
            tag.putLong("close_at", t.closeAt);
            tag.putInt("step", t.step);
            tag.putInt("moving", t.moving);
            ListTag leaves = new ListTag();
            for (Leaf l : t.leaves) {
                CompoundTag lt = new CompoundTag();
                lt.put("pos", NbtUtils.writeBlockPos(l.pos));
                lt.put("state", NbtUtils.writeBlockState(l.state));
                lt.putUUID("display", l.display);
                lt.putFloat("ox", l.offset.x);
                lt.putFloat("oy", l.offset.y);
                lt.putFloat("oz", l.offset.z);
                lt.putFloat("sign", l.sign);
                leaves.add(lt);
            }
            tag.put("leaves", leaves);
            return tag;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Trap t : traps) {
                list.add(saveTrap(t));
            }
            tag.put("traps", list);
            return tag;
        }

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            var blocks = registries.lookupOrThrow(Registries.BLOCK);
            for (Tag e : tag.getList("traps", Tag.TAG_COMPOUND)) {
                CompoundTag tt = (CompoundTag) e;
                Trap t = new Trap();
                t.along = Direction.Axis.byName(tt.getString("along"));
                t.closeAt = tt.getLong("close_at");
                // a swing cut short by a restart carries on from where it was
                t.step = tt.getInt("step");
                t.moving = tt.getInt("moving");
                for (Tag le : tt.getList("leaves", Tag.TAG_COMPOUND)) {
                    CompoundTag lt = (CompoundTag) le;
                    BlockPos pos = NbtUtils.readBlockPos(lt, "pos").orElse(BlockPos.ZERO);
                    t.leaves.add(new Leaf(pos, NbtUtils.readBlockState(blocks, lt.getCompound("state")),
                            lt.getUUID("display"), new Vector3f(lt.getFloat("ox"), lt.getFloat("oy"), lt.getFloat("oz")),
                            lt.getFloat("sign")));
                }
                t.area = area(t.leaves.stream().map(Leaf::pos).toList());
                d.traps.add(t);
            }
            return d;
        }
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), FILE);
    }

    private static AABB area(List<BlockPos> blocks) {
        int x0 = Integer.MAX_VALUE;
        int y0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int y1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
        }
        return new AABB(x0, y0, z0, x1 + 1, y1 + 1, z1 + 1);
    }

    /** Whether a porch floor block lies over a drop deep enough to give way. */
    public static boolean armed(ServerLevel level, BlockPos pos, int minDrop) {
        if (!(level.getBlockState(pos).getBlock() instanceof GoblinBlocks.PorchFloorBlock)) {
            return false;
        }
        BlockPos.MutableBlockPos p = pos.mutable();
        for (int i = 0; i < minDrop; i++) {
            p.move(Direction.DOWN);
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty() || !level.getFluidState(p).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block is part of a trap that is open now. */
    public static boolean isOpen(ServerLevel level, BlockPos pos) {
        for (Trap t : data(level).traps) {
            for (Leaf l : t.leaves) {
                if (l.pos.equals(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many traps are open in the level. */
    public static int openCount(ServerLevel level) {
        return data(level).traps.size();
    }

    /**
     * Springs the porch floor that holds {@code pos} (every porch floor block joined to it on its level): its blocks
     * become swinging leaves and the ambush waits below. Returns false if there is none or it is already open.
     */
    public static boolean spring(ServerLevel level, BlockPos pos, ServerPlayer by) {
        Optional<Settings> s = settings();
        if (s.isEmpty() || !armed(level, pos, s.get().minDrop()) || isOpen(level, pos)) {
            return false;
        }
        List<BlockPos> blocks = connected(level, pos, s.get().maxBlocks());
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos p : blocks) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
        }
        Trap t = new Trap();
        t.along = maxX - minX >= maxZ - minZ ? Direction.Axis.X : Direction.Axis.Z;
        boolean alongX = t.along == Direction.Axis.X;
        int latMin = alongX ? minZ : minX;
        int latMax = alongX ? maxZ : maxX;
        double centre = (latMin + latMax + 1) / 2.0;
        float top = pos.getY() + 1;
        for (BlockPos p : blocks) {
            BlockState state = level.getBlockState(p);
            int lat = alongX ? p.getZ() : p.getX();
            boolean left = lat + 0.5 < centre;
            float hingeLat = left ? latMin : latMax + 1;
            // the display stands on the hinge line; the block sits at an offset from it
            double ex = alongX ? p.getX() : hingeLat;
            double ez = alongX ? hingeLat : p.getZ();
            Vector3f offset = alongX ? new Vector3f(0, p.getY() - top, p.getZ() - hingeLat)
                    : new Vector3f(p.getX() - hingeLat, p.getY() - top, 0);
            // leaves swing down: about +x a point at +z drops for a positive angle; about +z a point at +x for negative
            float sign = alongX ? (left ? 1 : -1) : (left ? -1 : 1);
            Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            d.setPos(ex, top, ez);
            CompoundTag tag = d.saveWithoutId(new CompoundTag());
            tag.put("block_state", NbtUtils.writeBlockState(state));
            tag.put("transformation", transformation(t.along, offset, 0));
            // lit as the floor was (the hinge line can be inside the wall, which would draw it black)
            CompoundTag light = new CompoundTag();
            light.putInt("block", level.getBrightness(LightLayer.BLOCK, p.above()));
            light.putInt("sky", level.getBrightness(LightLayer.SKY, p.above()));
            tag.put("brightness", light);
            d.load(tag);
            level.addFreshEntity(d);
            t.leaves.add(new Leaf(p.immutable(), state, d.getUUID(), offset, sign));
        }
        for (BlockPos p : blocks) {
            level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        t.area = area(blocks);
        t.moving = 1;
        t.nextStep = level.getGameTime() + 1;
        t.closeAt = level.getGameTime() + s.get().resetTicks();
        data(level).traps.add(t);
        data(level).setDirty();
        double cx = (minX + maxX + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        level.playSound(null, cx, top, cz, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.playSound(null, cx, top, cz, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.5f, 0.8f);
        ambush(level, BlockPos.containing(cx, pos.getY() - 2, cz), s.get());
        return true;
    }

    private static List<BlockPos> connected(ServerLevel level, BlockPos start, int max) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(start.immutable());
        seen.add(start.immutable());
        while (!q.isEmpty() && out.size() < max) {
            BlockPos p = q.poll();
            if (!(level.getBlockState(p).getBlock() instanceof GoblinBlocks.PorchFloorBlock)) {
                continue;
            }
            out.add(p);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.add(n)) {
                    q.add(n);
                }
            }
        }
        return out;
    }

    /** The goblins waiting where the chute comes down (the first floor straight below the trap). */
    private static void ambush(ServerLevel level, BlockPos from, Settings s) {
        BlockPos.MutableBlockPos p = from.mutable();
        int bottom = Math.max(level.getMinBuildHeight(), from.getY() - 400);
        while (p.getY() > bottom && level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
            p.move(Direction.DOWN);
        }
        if (p.getY() <= bottom) {
            return;
        }
        RandomSource r = level.random;
        for (Ambush a : s.ambush()) {
            int n = a.min() + (a.max() > a.min() ? r.nextInt(a.max() - a.min() + 1) : 0);
            for (int i = 0; i < n; i++) {
                double x = p.getX() + 0.5 + (r.nextDouble() - 0.5) * 2 * s.ambushRadius();
                double z = p.getZ() + 0.5 + (r.nextDouble() - 0.5) * 2 * s.ambushRadius();
                LotrNpc.spawn(level, a.npc(), x, p.getY() + 1, z);
            }
        }
    }

    private static Tag transformation(Direction.Axis along, Vector3f offset, float angle) {
        Quaternionf q = along == Direction.Axis.X ? new Quaternionf().rotateX(angle) : new Quaternionf().rotateZ(angle);
        Vector3f translation = new Vector3f(offset).rotate(q);
        Transformation t = new Transformation(translation, q, new Vector3f(1, 1, 1), new Quaternionf());
        return Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE, t).getOrThrow();
    }

    /** Turns every leaf of a trap to its angle for a swing step, animated over the step's ticks. */
    private static void pose(ServerLevel level, Trap t, int step, Settings s) {
        float angle = (float) (Math.PI / 2 * step / s.swingSteps());
        for (Leaf l : t.leaves) {
            Entity e = level.getEntity(l.display);
            if (e instanceof Display.BlockDisplay d) {
                CompoundTag tag = d.saveWithoutId(new CompoundTag());
                tag.put("transformation", transformation(t.along, l.offset, angle * l.sign));
                tag.putInt("interpolation_duration", s.stepTicks());
                tag.putInt("start_interpolation", 0);
                d.load(tag);
            }
        }
    }

    /** Shuts a trap at once: displays gone, floor back (for tests and admins). */
    public static void closeNow(ServerLevel level, BlockPos anyLeaf) {
        Data data = data(level);
        data.traps.removeIf(t -> {
            boolean hit = t.leaves.stream().anyMatch(l -> l.pos.equals(anyLeaf));
            if (hit) {
                restore(level, t);
            }
            return hit;
        });
        data.setDirty();
    }

    private static void restore(ServerLevel level, Trap t) {
        for (Leaf l : t.leaves) {
            Entity e = level.getEntity(l.display);
            if (e != null) {
                e.discard();
            }
            level.setBlock(l.pos, l.state, Block.UPDATE_CLIENTS);
        }
    }

    // ---- ticking ----

    private static void onTick(ServerTickEvent.Post event) {
        Optional<Settings> s = settings();
        if (s.isEmpty()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Data data = data(level);
            long now = level.getGameTime();
            for (var it = data.traps.iterator(); it.hasNext(); ) {
                Trap t = it.next();
                if (!loaded(level, t)) {
                    // its leaves are saved with their chunks: wait until they are back
                    continue;
                }
                if (t.moving != 0 && now >= t.nextStep) {
                    t.step += t.moving;
                    if (t.step >= s.get().swingSteps()) {
                        t.step = s.get().swingSteps();
                        t.moving = 0;
                    }
                    if (t.step <= 0) {
                        restore(level, t);
                        it.remove();
                        data.setDirty();
                        continue;
                    }
                    pose(level, t, t.step, s.get());
                    t.nextStep = now + s.get().stepTicks();
                    data.setDirty();
                } else if (t.moving == 0 && now >= t.closeAt
                        && level.getEntitiesOfClass(ServerPlayer.class, t.area.inflate(3, 4, 3)).isEmpty()) {
                    t.moving = -1;
                    t.nextStep = now;
                    level.playSound(null, t.area.getCenter().x, t.area.maxY, t.area.getCenter().z,
                            SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.6f);
                }
            }
            if (now % 2 == 0) {
                for (ServerPlayer player : level.players()) {
                    stand(level, player, now, s.get());
                }
            }
        }
    }

    private static boolean loaded(ServerLevel level, Trap t) {
        long last = Long.MIN_VALUE;
        for (Leaf l : t.leaves) {
            long chunk = net.minecraft.world.level.ChunkPos.asLong(l.pos);
            if (chunk != last && !level.areEntitiesLoaded(chunk)) {
                return false;
            }
            last = chunk;
        }
        return true;
    }

    private static void stand(ServerLevel level, ServerPlayer player, long now, Settings s) {
        BlockPos below = player.getOnPos();
        BlockState state = level.getBlockState(below);
        boolean armed = !player.isSpectator() && armed(level, below, s.minDrop());
        Standing st = STANDING.get(player.getUUID());
        if (!armed) {
            if (st != null) {
                STANDING.remove(player.getUUID());
            }
            return;
        }
        if (st == null) {
            int lo = s.triggerTicks().get(0);
            int hi = s.triggerTicks().get(s.triggerTicks().size() - 1);
            st = new Standing(below.immutable(), now, now + lo + level.random.nextInt(Math.max(1, hi - lo + 1)));
            STANDING.put(player.getUUID(), st);
        }
        if (now >= st.springs) {
            STANDING.remove(player.getUUID());
            spring(level, below, player);
        } else if (now >= st.springs - s.warnTicks() && now % 6 == 0) {
            // grit trickles from the roof and the floor creaks
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.FALLING_DUST, state);
            level.sendParticles(dust, player.getX(), player.getY() + 3, player.getZ(), s.dust(), 2.0, 0.3, 2.0, 0);
            level.playSound(null, below, SoundEvents.GRAVEL_STEP, SoundSource.BLOCKS, 0.6f, 0.7f);
        }
    }
}
