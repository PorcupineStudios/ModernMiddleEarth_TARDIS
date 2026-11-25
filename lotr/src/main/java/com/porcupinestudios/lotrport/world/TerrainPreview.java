package com.porcupinestudios.lotrport.world;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Dev tool (Aaron 2026-11-14, tuning the Shire): {@code -Dlotr_port.terrainpreview=<x>,<z>,<size>} on a server of the
 * Middle-earth world type generates the square of {@code size} blocks round x, z, writes {@code terrain_preview/}
 * in the run folder ({@code relief.png}: shaded ground, water blue, tree crowns dark; {@code variants.png}: the biome
 * variant patches over the relief; {@code blocks.png}: each column's top block in its map colour; {@code stats.txt}) and stops the JVM without saving ({@code
 * -Dlotr_port.terrainpreview.save=true}: stops normally and saves the world, to look round it with a client). Stats: water share, slope
 * percentiles, and overhangs (columns with air within 6 blocks under the ground's top, above sea level; trees are
 * looked through), with the first few spots.
 */
public final class TerrainPreview {
    private TerrainPreview() {}

    static void runIfAsked(MinecraftServer server) {
        String arg = System.getProperty("lotr_port.terrainpreview");
        if (arg == null) {
            return;
        }
        try {
            String[] p = arg.split(",");
            run(server.overworld(), Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()), server.getServerDirectory().resolve("terrain_preview"));
        } catch (Exception e) {
            LotrPort.LOG.error("[preview] failed", e);
        }
        if (Boolean.getBoolean("lotr_port.terrainpreview.save")) {
            server.halt(false); // stops normally and saves, for a client to look round afterwards
            return;
        }
        org.apache.logging.log4j.LogManager.shutdown();
        Runtime.getRuntime().halt(0);
    }

    static void run(ServerLevel level, int cx, int cz, int size, Path out) throws IOException {
        long start = System.nanoTime();
        int x0 = cx - size / 2;
        int z0 = cz - size / 2;
        int[] ground = new int[size * size];
        int[] kind = new int[size * size]; // 0 ground, 1 water, 2 tree crown
        int[] seen = new int[size * size]; // map colour of the top block seen from above (blocks.png)
        Map<String, Integer> grounds = new HashMap<>(); // what the ground is made of
        int webColumns = 0;
        boolean[] overhang = new boolean[size * size];
        int sea = level.getSeaLevel();
        int overhangs = 0;
        List<String> spots = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int chx = x0 >> 4; chx <= (x0 + size - 1) >> 4; chx++) {
            for (int chz = z0 >> 4; chz <= (z0 + size - 1) >> 4; chz++) {
                LevelChunk chunk = level.getChunk(chx, chz);
                for (int i = 0; i < 16; i++) {
                    for (int k = 0; k < 16; k++) {
                        int x = (chx << 4) + i;
                        int z = (chz << 4) + k;
                        if (x < x0 || z < z0 || x >= x0 + size || z >= z0 + size) {
                            continue;
                        }
                        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, i, k);
                        int y = top;
                        boolean crown = false;
                        int above = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, i, k) - 1;
                        BlockState first = level.getBlockState(m.set(x, above, z));
                        seen[(z - z0) * size + (x - x0)] = first.getMapColor(level, m).col;
                        BlockState s = level.getBlockState(m.set(x, y, z));
                        boolean web = false;
                        // look through trees, plants, webs and air down to the ground
                        while (y > level.getMinBuildHeight() && (s.isAir() || s.is(BlockTags.LEAVES)
                                || s.is(BlockTags.LOGS) || s.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                || s.is(net.minecraft.world.level.block.Blocks.MUSHROOM_STEM)
                                || s.canBeReplaced() && s.getFluidState().isEmpty())) {
                            crown |= s.is(BlockTags.LEAVES);
                            web |= s.is(net.minecraft.world.level.block.Blocks.COBWEB);
                            y--;
                            s = level.getBlockState(m.set(x, y, z));
                        }
                        webColumns += web ? 1 : 0;
                        grounds.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock())
                                .getPath(), 1, Integer::sum);
                        int idx = (z - z0) * size + (x - x0);
                        boolean water = !s.getFluidState().isEmpty();
                        if (water) {
                            while (y > level.getMinBuildHeight() && !level.getBlockState(m.set(x, y, z))
                                    .getFluidState().isEmpty()) {
                                y--;
                            }
                        }
                        ground[idx] = y;
                        kind[idx] = water ? 1 : crown ? 2 : 0;
                        if (!water && y > sea) {
                            for (int d = 1; d <= 6; d++) {
                                if (level.getBlockState(m.set(x, y - d, z)).isAir()) {
                                    overhangs++;
                                    overhang[idx] = true;
                                    if (spots.size() < 12) {
                                        spots.add(x + " " + y + " " + z);
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        Files.createDirectories(out);
        BufferedImage relief = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        BufferedImage variants = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        MiddleEarthMap map = MiddleEarthMap.get();
        Map<String, Integer> colours = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        int[] palette = {0xE8C547, 0x3A7D2C, 0x1F5A1A, 0xB86B3A, 0x6B8E23, 0xEEEEEE, 0xC9B26B, 0xD9534F, 0x8E44AD,
                0x4AA3DF};
        double[] slopes = new double[size * size];
        int n = 0;
        int water = 0;
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int idx = z * size + x;
                int g = ground[idx];
                double dx = x + 1 < size ? ground[idx + 1] - g : g - ground[idx - 1];
                double dz = z + 1 < size ? ground[idx + size] - g : g - ground[idx - size];
                if (kind[idx] != 1) {
                    slopes[n++] = Math.hypot(dx, dz);
                } else {
                    water++;
                }
                double shade = Math.max(0.35, Math.min(1.25, 0.85 - 0.18 * (dx + dz)));
                double t = Math.max(0, Math.min(1, (g - 60) / 50.0));
                int r, gr, b;
                if (kind[idx] == 1) {
                    r = 40; gr = 80; b = 170;
                } else if (kind[idx] == 2) {
                    r = (int) (25 + 20 * t); gr = (int) (70 + 30 * t); b = 25;
                } else {
                    r = (int) (80 + 90 * t); gr = (int) (150 + 50 * t); b = (int) (60 + 50 * t);
                }
                int rgb = rgb(r * shade, gr * shade, b * shade);
                relief.setRGB(x, z, rgb);
                String v = map.variantAt(x0 + x, z0 + z);
                String key = v == null ? "plain" : v;
                counts.merge(key, 1, Integer::sum);
                int c = v == null ? 0x808080 : colours.computeIfAbsent(v, k -> palette[colours.size() % palette.length]);
                double l = kind[idx] == 1 ? 0.35 : shade * 0.75;
                variants.setRGB(x, z, rgb(((c >> 16) & 255) * l, ((c >> 8) & 255) * l, (c & 255) * l));
            }
        }
        ImageIO.write(relief, "png", out.resolve("relief.png").toFile());
        for (int i = 0; i < size * size; i++) {
            if (overhang[i]) {
                relief.setRGB(i % size, i / size, 0xFF2020);
            }
        }
        ImageIO.write(relief, "png", out.resolve("overhangs.png").toFile()); // the relief with overhangs in red
        ImageIO.write(variants, "png", out.resolve("variants.png").toFile());
        // blocks.png (2026-11-25): what a bird sees, each column the map colour of its top block, shaded by the
        // ground's slope; enclosures' fences, crops, walls and webs show up here
        BufferedImage blocks = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int idx = z * size + x;
                int g = ground[idx];
                double dx = x + 1 < size ? ground[idx + 1] - g : g - ground[idx - 1];
                double dz = z + 1 < size ? ground[idx + size] - g : g - ground[idx - size];
                double shade = Math.max(0.5, Math.min(1.2, 0.9 - 0.12 * (dx + dz)));
                int c = seen[idx];
                blocks.setRGB(x, z, rgb(((c >> 16) & 255) * shade, ((c >> 8) & 255) * shade, (c & 255) * shade));
            }
        }
        ImageIO.write(blocks, "png", out.resolve("blocks.png").toFile());
        Files.writeString(out.resolve("enclosures.txt"), enclosures(level, map, x0, z0, size));
        double[] s = Arrays.copyOf(slopes, n);
        Arrays.sort(s);
        StringBuilder stats = new StringBuilder();
        stats.append("square ").append(size).append(" at ").append(cx).append(",").append(cz).append("\n");
        stats.append(String.format("water %.1f%%%n", 100.0 * water / (size * size)));
        stats.append(String.format("slope (blocks per block) median %.2f p90 %.2f p99 %.2f max %.1f%n",
                s[n / 2], s[n * 9 / 10], s[n * 99 / 100], s[n - 1]));
        stats.append(String.format("overhangs %d columns (%.3f%%), first: %s%n", overhangs,
                100.0 * overhangs / (size * size), spots));
        counts.forEach((k, c) -> stats.append(String.format("variant %s %.1f%% colour %06X%n", k,
                100.0 * c / (size * size), k.equals("plain") ? 0x808080 : colours.getOrDefault(k, 0))));
        stats.append(String.format("columns with webs %.2f%%%n", 100.0 * webColumns / (size * size)));
        grounds.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(12).forEach(g ->
                stats.append(String.format("ground %s %.1f%%%n", g.getKey(), 100.0 * g.getValue() / (size * size))));
        stats.append(String.format("took %d s%n", (System.nanoTime() - start) / 1_000_000_000L));
        Files.writeString(out.resolve("stats.txt"), stats.toString());
        LotrPort.LOG.info("[preview]\n{}", stats);
    }

    /**
     * Each enclosure inside the square drawn as its top blocks, a character a block: F fence, G gate, P post (log), H
     * hedge (leaves), C corn, f farmland, p path, T tree log, L leaves, w water, . grass or dirt, ? anything else.
     */
    private static String enclosures(ServerLevel level, MiddleEarthMap map, int x0, int z0, int size) {
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int cx = Math.floorDiv(x0, 128); cx <= Math.floorDiv(x0 + size - 1, 128); cx++) {
            for (int cz = Math.floorDiv(z0, 128); cz <= Math.floorDiv(z0 + size - 1, 128); cz++) {
                var e = map.enclosureInCellOf(cx * 128, cz * 128);
                if (e == null || e.minX() < x0 || e.minZ() < z0 || e.maxX() >= x0 + size || e.maxZ() >= z0 + size
                        || !seen.add(e.minX() + "," + e.minZ())) {
                    continue;
                }
                sb.append(e.kind().name()).append(" at ").append(e.minX()).append(',').append(e.minZ()).append(' ')
                        .append(e.sizeX()).append('x').append(e.sizeZ()).append(" level ").append(e.level())
                        .append('\n');
                BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                for (int z = e.minZ() - 1; z <= e.maxZ() + 1; z++) {
                    for (int x = e.minX() - 1; x <= e.maxX() + 1; x++) {
                        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                        BlockState b = level.getBlockState(m.set(x, y, z));
                        String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath();
                        char c = b.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock ? 'G'
                                : b.getBlock() instanceof net.minecraft.world.level.block.FenceBlock ? 'F'
                                : id.equals("corn_stalks") ? 'C' : id.equals("farmland") ? 'f'
                                : id.equals("dirt_path") ? 'p' : !b.getFluidState().isEmpty() ? 'w'
                                : b.is(BlockTags.LEAVES) ? (e.onFence(x, z) ? 'H' : 'L')
                                : b.is(BlockTags.LOGS) ? (e.onFence(x, z) ? 'P' : 'T')
                                : id.contains("grass") || id.contains("dirt") || b.canBeReplaced() ? '.' : '?';
                        sb.append(c);
                    }
                    sb.append('\n');
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
