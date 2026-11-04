package com.porcupinestudios.lotrport.tree;

import com.porcupinestudios.lotrport.content.SaplingRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Whole trees, for whoever fells them: RingRealms colonists (woodcutters, orchards) later, and anything else.
 *
 * <ul>
 * <li>{@link #find}: the tree a log or leaf belongs to. Trees grown by {@code lotr_port:template_tree} (world
 * generation and saplings) are recorded ({@link TreeRecords}), so the answer is that exact tree; anything else
 * (vanilla stand-ins, player builds) falls back to a search from the block.</li>
 * <li>{@link #blocks}: every log and leaf of it: from the trunk through touching logs (also diagonally, for LOTR's
 * branches) and the leaves on them, inside the recorded box, never into another recorded tree's trunk.</li>
 * <li>{@link #harvest}: fells the whole tree, returns the drops (for a colonist's inventory, nothing is dropped in
 * the world) and the sapling that grows it again ({@link SaplingRules}, reversed).</li>
 * <li>Events on the NeoForge bus: {@link Grown} (saplings only: world generation runs off the main thread) and
 * {@link Harvested}.</li>
 * </ul>
 */
public final class Trees {
    /** Most blocks one tree may have (LOTR's giant mallorn has a few thousand). */
    public static final int MAX_BLOCKS = 12000;
    /** How far an unrecorded tree's logs are followed from the block. */
    private static final int LOOSE_RADIUS = 16;

    private Trees() {}

    /** A tree found from one of its blocks: its record if it has one, and its blocks. */
    public record Tree(Optional<TreeRecord> record, Set<BlockPos> logs, Set<BlockPos> leaves) {
        public int size() {
            return logs.size() + leaves.size();
        }
    }

    /** What felling a tree gave: the drops, the sapling to plant again (if any), and the tree that was felled. */
    public record Harvest(List<ItemStack> drops, Optional<Block> sapling, Tree tree) {}

    /** A tree grew from saplings (main thread). */
    public static final class Grown extends Event {
        private final ServerLevel level;
        private final TreeRecord tree;

        public Grown(ServerLevel level, TreeRecord tree) {
            this.level = level;
            this.tree = tree;
        }

        public ServerLevel level() {
            return level;
        }

        public TreeRecord tree() {
            return tree;
        }
    }

    /** A whole tree was felled through {@link #harvest}; {@code harvester} may be null. */
    public static final class Harvested extends Event {
        private final ServerLevel level;
        private final Harvest harvest;
        @Nullable
        private final Entity harvester;

        public Harvested(ServerLevel level, Harvest harvest, @Nullable Entity harvester) {
            this.level = level;
            this.harvest = harvest;
            this.harvester = harvester;
        }

        public ServerLevel level() {
            return level;
        }

        public Harvest harvest() {
            return harvest;
        }

        @Nullable
        public Entity harvester() {
            return harvester;
        }
    }

    /** Records a tree a template tree feature just grew (called from world generation and saplings). */
    public static void record(ServerLevel level, TreeRecord tree) {
        TreeRecords store = TreeRecords.of(level);
        if (store != null) {
            store.add(tree);
        }
        if (tree.planted() && level.getServer().isSameThread()) {
            NeoForge.EVENT_BUS.post(new Grown(level, tree));
        }
    }

    /** Recorded trees whose trunk is within the radius: what a woodcutter could fell next. */
    public static List<TreeRecord> near(ServerLevel level, BlockPos pos, int radius) {
        TreeRecords store = TreeRecords.of(level);
        return store == null ? List.of() : store.near(pos, radius);
    }

    /** The tree a block belongs to, or empty if it isn't a log or leaf of one. */
    public static Optional<Tree> find(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isLog(state) && !state.is(BlockTags.LEAVES)) {
            return Optional.empty();
        }
        TreeRecords store = TreeRecords.of(level);
        List<TreeRecord> holding = store == null ? List.of() : store.containing(pos);
        // the recorded tree whose trunk is nearest wins where crowns overlap
        Optional<TreeRecord> record = holding.stream().min((a, b) -> Double.compare(
                horizontal(a.trunk(), pos), horizontal(b.trunk(), pos)));
        if (record.isPresent()) {
            Tree tree = blocks(level, record.get(), holding);
            if (tree.logs().contains(pos) || tree.leaves().contains(pos)) {
                return Optional.of(tree);
            }
        }
        return isLog(state) ? Optional.of(loose(level, pos)) : Optional.empty();
    }

    /** Every log and leaf of a recorded tree that still stands. */
    public static Tree blocks(ServerLevel level, TreeRecord tree) {
        TreeRecords store = TreeRecords.of(level);
        return blocks(level, tree, store == null ? List.of() : store.containing(tree.trunk()));
    }

    private static Tree blocks(ServerLevel level, TreeRecord tree, List<TreeRecord> others) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        for (int dx = 0; dx < tree.trunkWidth(); dx++) {
            for (int dz = 0; dz < tree.trunkWidth(); dz++) {
                BlockPos p = tree.trunk().offset(dx, 0, dz);
                if (isLog(level.getBlockState(p))) {
                    logs.add(p);
                    todo.add(p);
                }
            }
        }
        while (!todo.isEmpty() && logs.size() < MAX_BLOCKS) {
            BlockPos p = todo.poll();
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (!tree.box().isInside(q) || logs.contains(q) || !isLog(level.getBlockState(q))) {
                    continue;
                }
                BlockPos at = q.immutable();
                if (others.stream().anyMatch(o -> o != tree && !o.equals(tree) && o.inTrunk(at))) {
                    continue; // another tree's trunk
                }
                logs.add(at);
                todo.add(at);
            }
        }
        // a recorded tree owns every leaf in its box near its logs, persistent ones too (far leaves are saved that way)
        return new Tree(Optional.of(tree), logs, leaves(level, logs, tree.box()::isInside, true));
    }

    /** An unrecorded tree: logs touching the block (diagonals too) within a radius, and their leaves. */
    private static Tree loose(ServerLevel level, BlockPos start) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        logs.add(start.immutable());
        todo.add(start.immutable());
        while (!todo.isEmpty() && logs.size() < MAX_BLOCKS) {
            BlockPos p = todo.poll();
            for (BlockPos q : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
                if (logs.contains(q) || Math.abs(q.getX() - start.getX()) > LOOSE_RADIUS
                        || Math.abs(q.getZ() - start.getZ()) > LOOSE_RADIUS || !isLog(level.getBlockState(q))) {
                    continue;
                }
                logs.add(q.immutable());
                todo.add(q.immutable());
            }
        }
        return new Tree(Optional.empty(), logs, leaves(level, logs, p -> true, false));
    }

    /**
     * Leaves reached from the logs within the 6-block leaf distance; persistent (player-placed) leaves only when
     * {@code persistentToo}.
     */
    private static Set<BlockPos> leaves(ServerLevel level, Set<BlockPos> logs,
            java.util.function.Predicate<BlockPos> inside, boolean persistentToo) {
        Set<BlockPos> out = new LinkedHashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>(logs);
        java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        logs.forEach(p -> dist.put(p, 0));
        while (!todo.isEmpty() && out.size() + logs.size() < MAX_BLOCKS) {
            BlockPos p = todo.poll();
            int d = dist.get(p);
            if (d >= 6) {
                continue;
            }
            for (BlockPos q : List.of(p.above(), p.below(), p.north(), p.south(), p.east(), p.west())) {
                if (dist.containsKey(q) || !inside.test(q)) {
                    continue;
                }
                BlockState s = level.getBlockState(q);
                boolean persistent = s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT);
                if (s.is(BlockTags.LEAVES) && (persistentToo || !persistent)) {
                    dist.put(q, d + 1);
                    out.add(q);
                    todo.add(q);
                }
            }
        }
        return out;
    }

    /**
     * Fells the whole tree a block belongs to: every log (and the leaves, if {@code leaves}) is removed without
     * dropping anything; their drops are returned. Forgets the tree's record. Empty if the block is no tree.
     */
    public static Optional<Harvest> harvest(ServerLevel level, BlockPos pos, @Nullable Entity harvester,
            boolean leaves) {
        Optional<Tree> found = find(level, pos);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Tree tree = found.get();
        List<ItemStack> drops = new ArrayList<>();
        List<BlockPos> cut = new ArrayList<>(tree.logs());
        if (leaves) {
            cut.addAll(tree.leaves());
        }
        for (BlockPos p : cut) {
            BlockState s = level.getBlockState(p);
            drops.addAll(Block.getDrops(s, level, p, level.getBlockEntity(p), harvester, ItemStack.EMPTY));
            level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        tree.record().ifPresent(r -> {
            TreeRecords store = TreeRecords.of(level);
            if (store != null) {
                store.remove(r);
            }
        });
        Harvest harvest = new Harvest(drops, tree.record().flatMap(r -> saplingFor(r.tree())), tree);
        NeoForge.EVENT_BUS.post(new Harvested(level, harvest, harvester));
        return Optional.of(harvest);
    }

    /** The sapling that grows a tree feature (the first sapling whose rules name it). */
    public static Optional<Block> saplingFor(ResourceLocation tree) {
        return SaplingRules.all().entrySet().stream()
                .filter(e -> e.getValue().rules().stream().anyMatch(r -> r.tree().equals(tree)))
                .map(e -> BuiltInRegistries.BLOCK.get(e.getKey())).findFirst();
    }

    public static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS) || state.getBlock().defaultBlockState().hasProperty(
                net.minecraft.world.level.block.RotatedPillarBlock.AXIS) && !state.is(BlockTags.LEAVES)
                && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_wood");
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
