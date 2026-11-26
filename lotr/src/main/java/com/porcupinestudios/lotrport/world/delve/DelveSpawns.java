package com.porcupinestudios.lotrport.world.delve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.world.LotrDimensions;
import com.porcupinestudios.ringbearer.api.data.CodecJsonLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Who lives in a delve, by depth (Aaron 2026-11-25: orcs and trolls by depth, the Balrog a very rare spawn deep in the
 * mines with a cap): {@code data/<ns>/delve_spawns/<name>.json}, reloaded with /reload. Every {@code interval} ticks
 * for each player inside the delve (in one of its pieces), with chance {@code chance}, a spot {@code min_distance} to
 * {@code max_distance} blocks away and up to {@code height_range} up or down is tried: it must be inside the delve,
 * on solid ground with a clear box of the entry's {@code space} [width, height] over it. The entry is picked by
 * weight among those whose {@code min_y}..{@code max_y} holds the spot and whose {@code zone} (if any) is the
 * piece's there ({@code town}, {@code throne}, {@code gollum_lake}...), then rolls its own {@code chance}, and spawns
 * only in the dark unless {@code dark} is false (lit goblin towns); an entry
 * with {@code max_alive} never has more than that many of its NPC loaded in the world (the Balrog: one). No more
 * spawn while {@code cap} delve NPCs are within {@code radius} of the player. Biome spawns stay on the surface.
 */
public final class DelveSpawns {
    public record Entry(ResourceLocation npc, int weight, int min, int max, int minY, int maxY, float chance,
                        int maxAlive, List<Integer> space, String zone, boolean dark) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("npc").forGetter(Entry::npc),
                Codec.INT.fieldOf("weight").forGetter(Entry::weight),
                Codec.INT.fieldOf("min").forGetter(Entry::min),
                Codec.INT.fieldOf("max").forGetter(Entry::max),
                Codec.INT.fieldOf("min_y").forGetter(Entry::minY),
                Codec.INT.fieldOf("max_y").forGetter(Entry::maxY),
                Codec.FLOAT.fieldOf("chance").forGetter(Entry::chance),
                Codec.INT.fieldOf("max_alive").forGetter(Entry::maxAlive),
                Codec.INT.listOf().fieldOf("space").forGetter(Entry::space),
                Codec.STRING.optionalFieldOf("zone", "").forGetter(Entry::zone),
                Codec.BOOL.optionalFieldOf("dark", true).forGetter(Entry::dark)).apply(i, Entry::new));
    }

    public record Table(ResourceLocation delve, int interval, float chance, int cap, int radius, int minDistance,
                        int maxDistance, int heightRange, int tries, List<Entry> entries) {
        public static final Codec<Table> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("delve").forGetter(Table::delve),
                Codec.INT.fieldOf("interval").forGetter(Table::interval),
                Codec.FLOAT.fieldOf("chance").forGetter(Table::chance),
                Codec.INT.fieldOf("cap").forGetter(Table::cap),
                Codec.INT.fieldOf("radius").forGetter(Table::radius),
                Codec.INT.fieldOf("min_distance").forGetter(Table::minDistance),
                Codec.INT.fieldOf("max_distance").forGetter(Table::maxDistance),
                Codec.INT.fieldOf("height_range").forGetter(Table::heightRange),
                Codec.INT.fieldOf("tries").forGetter(Table::tries),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Table::entries)).apply(i, Table::new));
    }

    private static final CodecJsonLoader<Table> LOADER = new CodecJsonLoader<>("delve_spawns", Table.CODEC);

    private DelveSpawns() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener(LOADER));
        NeoForge.EVENT_BUS.addListener(DelveSpawns::onTick);
    }

    public static Map<ResourceLocation, Table> tables() {
        return LOADER.entries();
    }

    private static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.players().isEmpty()
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || level != LotrDimensions.middleEarth(level.getServer())) {
            return;
        }
        for (Table table : LOADER.entries().values()) {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()
                        && (level.getGameTime() + player.getId()) % Math.max(1, table.interval()) == 0) {
                    trySpawn(level, player, table, false);
                }
            }
        }
    }

    /** One attempt near the player; returns how many NPCs appeared. {@code force} skips the table's chance. */
    public static int trySpawn(ServerLevel level, ServerPlayer player, Table table, boolean force) {
        RandomSource random = level.random;
        if (!force && random.nextFloat() >= table.chance()) {
            return 0;
        }
        Optional<DelveFeature.Config> config = DelveFeature.config(level.getServer(), table.delve());
        if (config.isEmpty()) {
            return 0;
        }
        BlockPos at = player.blockPosition();
        List<DelvePlan> plans = DelveFeature.plansNear(level.getServer(), config.get(), level.getSeed(),
                at.getX() - table.maxDistance(), at.getZ() - table.maxDistance(), at.getX() + table.maxDistance(),
                at.getZ() + table.maxDistance());
        if (plans.stream().noneMatch(p -> p.inside(at.getX(), at.getY(), at.getZ()))) {
            return 0;
        }
        AABB near = player.getBoundingBox().inflate(table.radius());
        if (level.getEntitiesOfClass(LotrNpc.class, near, n -> isDelveNpc(table, n)).size() >= table.cap()) {
            return 0;
        }
        for (int t = 0; t < table.tries(); t++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double d = table.minDistance() + random.nextDouble() * (table.maxDistance() - table.minDistance());
            int x = (int) Math.floor(player.getX() + Math.cos(angle) * d);
            int z = (int) Math.floor(player.getZ() + Math.sin(angle) * d);
            int y = at.getY() + random.nextInt(2 * table.heightRange() + 1) - table.heightRange();
            String zone = null;
            for (DelvePlan p : plans) {
                if (zone == null || zone.isEmpty()) {
                    String z2 = p.zoneAt(x, y, z);
                    zone = z2 == null ? zone : z2;
                }
            }
            if (!level.hasChunkAt(new BlockPos(x, y, z)) || zone == null) {
                continue;
            }
            // down to the floor
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
            int floor = Integer.MIN_VALUE;
            for (int k = 0; k < 12; k++) {
                p.setY(y - k);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isFaceSturdy(level,
                        p.below(), net.minecraft.core.Direction.UP)) {
                    floor = y - k;
                    break;
                }
            }
            if (floor == Integer.MIN_VALUE) {
                continue;
            }
            BlockPos ground = new BlockPos(x, floor, z);
            Entry entry = pick(table, floor, zone, random);
            if (entry == null || random.nextFloat() >= entry.chance() || !clear(level, ground, entry.space())) {
                continue;
            }
            if (entry.dark() && !Monster.isDarkEnoughToSpawn(level, ground, random)) {
                continue;
            }
            if (entry.maxAlive() > 0 && alive(level, entry.npc()) >= entry.maxAlive()) {
                continue;
            }
            int count = entry.min() + (entry.max() > entry.min() ? random.nextInt(entry.max() - entry.min() + 1) : 0);
            if (entry.maxAlive() > 0) {
                count = Math.min(count, entry.maxAlive() - alive(level, entry.npc()));
            }
            int spawned = 0;
            for (int i = 0; i < count; i++) {
                BlockPos s = i == 0 ? ground : ground.offset(random.nextInt(3) - 1, 0, random.nextInt(3) - 1);
                if (i > 0 && (!level.getBlockState(s).isAir() || !clear(level, s, entry.space()))) {
                    s = ground;
                }
                if (LotrNpc.spawn(level, entry.npc(), s.getX() + 0.5, s.getY(), s.getZ() + 0.5).isPresent()) {
                    spawned++;
                }
            }
            return spawned;
        }
        return 0;
    }

    private static boolean isDelveNpc(Table table, LotrNpc n) {
        for (Entry e : table.entries()) {
            if (e.npc().equals(n.typeId())) {
                return true;
            }
        }
        return false;
    }

    /** How many NPCs of a type are loaded in the level. */
    static int alive(ServerLevel level, ResourceLocation type) {
        int n = 0;
        for (var e : level.getAllEntities()) {
            if (e instanceof LotrNpc npc && npc.isAlive() && type.equals(npc.typeId())) {
                n++;
            }
        }
        return n;
    }

    /** A clear box {@code space} [width, height] blocks over the floor, centred on the spot. */
    static boolean clear(ServerLevel level, BlockPos ground, List<Integer> space) {
        int w = space.get(0);
        int h = space.get(1);
        int lo = -(w - 1) / 2;
        for (int dx = lo; dx < lo + w; dx++) {
            for (int dz = lo; dz < lo + w; dz++) {
                for (int dy = 0; dy < h; dy++) {
                    BlockPos q = ground.offset(dx, dy, dz);
                    if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()
                            || !level.getFluidState(q).isEmpty()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** An entry for a floor height, by weight among those whose depth band holds it. */
    public static Entry pick(Table table, int y, RandomSource random) {
        return pick(table, y, "", random);
    }

    /** An entry for a floor height and zone ("" = no zone): by weight among those whose band and zone fit. */
    public static Entry pick(Table table, int y, String zone, RandomSource random) {
        List<Entry> ok = new ArrayList<>();
        int total = 0;
        for (Entry e : table.entries()) {
            if (y >= e.minY() && y <= e.maxY() && e.weight() > 0 && (e.zone().isEmpty() || e.zone().equals(zone))) {
                ok.add(e);
                total += e.weight();
            }
        }
        if (total == 0) {
            return null;
        }
        int r = random.nextInt(total);
        for (Entry e : ok) {
            r -= e.weight();
            if (r < 0) {
                return e;
            }
        }
        return null;
    }
}
