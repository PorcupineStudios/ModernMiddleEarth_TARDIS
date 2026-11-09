package com.porcupinestudios.lotrport.animal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.porcupinestudios.lotrport.npc.LotrNpc;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * LOTR's own spawn checks, as the converter read them from LOTR's code (creatures.json): each rule is one shape LOTR
 * uses. {@code near_blocks}: LOTR's ambient check (LOTRAmbientSpawnChecks: enough light, then of {@code attempts}
 * random blocks within {@code xz}/{@code y}, more than {@code required} plants, vines, leaves or water);
 * {@code no_farmhands}: no farmhand NPC within {@code range}; {@code below_y}/{@code above_y}: below (above) the
 * height, else 1 in {@code else_one_in}; {@code near_water}: water within {@code range} and at most
 * {@code max_nearby} of its kind near. {@code dark} (orcs, wargs, spiders) is the NPC spawner's
 * ({@link com.porcupinestudios.lotrport.npc.NpcSpawner}); {@code trees} the converter's (biome lists).
 */
public final class SpawnRules {
    private SpawnRules() {}

    public static boolean test(List<JsonObject> rules, EntityType<?> type, ServerLevelAccessor level, BlockPos pos,
            RandomSource random) {
        for (JsonObject r : rules) {
            if (!test(r, type, level, pos, random)) {
                return false;
            }
        }
        return true;
    }

    private static boolean test(JsonObject r, EntityType<?> type, ServerLevelAccessor level, BlockPos pos,
            RandomSource random) {
        return switch (r.get("type").getAsString()) {
            case "near_blocks" -> nearBlocks(r, level, pos, random);
            case "no_farmhands" -> level.getEntitiesOfClass(LotrNpc.class, new AABB(pos).inflate(num(r, "range", 16)),
                    n -> n.typeId().getPath().contains("farmhand")).isEmpty();
            case "below_y" -> pos.getY() < num(r, "y", 60) || oneIn(r, random);
            case "above_y" -> pos.getY() > num(r, "y", 60) || oneIn(r, random);
            case "near_water" -> {
                int range = num(r, "range", 8);
                if (level.getEntities(type, new AABB(pos).inflate(24, 12, 24), e -> true).size()
                        > num(r, "max_nearby", 3)) {
                    yield false;
                }
                yield BlockPos.betweenClosedStream(pos.offset(-range, -range, -range), pos.offset(range, range, range))
                        .anyMatch(p -> level.getFluidState(p).is(FluidTags.WATER));
            }
            default -> true;
        };
    }

    private static boolean oneIn(JsonObject r, RandomSource random) {
        int n = num(r, "else_one_in", 0);
        return n > 0 && random.nextInt(n) == 0;
    }

    private static boolean nearBlocks(JsonObject r, ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        int light = level.getMaxLocalRawBrightness(pos);
        if (!(pos.getY() >= 62 && light >= random.nextInt(8) || light >= 8)) {
            return false;
        }
        int xz = Math.max(1, num(r, "xz", 8));
        int y = Math.max(1, num(r, "y", 4));
        int required = num(r, "required", 4);
        int counted = 0;
        for (int i = 0; i < num(r, "attempts", 32); i++) {
            BlockPos p = pos.offset(random.nextInt(xz) - random.nextInt(xz), random.nextInt(y) - random.nextInt(y),
                    random.nextInt(xz) - random.nextInt(xz));
            if (level.hasChunkAt(p) && matches(r, level.getBlockState(p)) && ++counted > required) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(JsonObject r, BlockState state) {
        for (JsonElement e : r.getAsJsonArray("blocks")) {
            boolean hit = switch (e.getAsString()) {
                case "plants" -> state.getBlock() instanceof BushBlock; // flowers, grass, ferns, saplings, crops
                case "vine" -> state.getBlock() instanceof VineBlock;
                case "leaves" -> state.is(BlockTags.LEAVES);
                case "water" -> state.getFluidState().is(FluidTags.WATER);
                default -> false;
            };
            if (hit) {
                return true;
            }
        }
        return false;
    }

    private static int num(JsonObject r, String key, int fallback) {
        return r.has(key) ? r.get(key).getAsInt() : fallback;
    }
}
