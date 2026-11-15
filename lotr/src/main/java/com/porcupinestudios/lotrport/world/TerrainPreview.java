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
 * variant patches over the relief; {@code stats.txt}) and stops the JVM without saving ({@code
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
                        BlockState s = level.getBlockState(m.set(x, y, z));
                        // look through trees, plants and air down to the ground
                        while (y > level.getMinBuildHeight() && (s.isAir() || s.is(BlockTags.LEAVES)
                                || s.is(BlockTags.LOGS) || s.canBeReplaced() && s.getFluidState().isEmpty())) {
                            crown |= s.is(BlockTags.LEAVES);
                            y--;
                            s = level.getBlockState(m.set(x, y, z));
                        }
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
        ImageIO.write(variants, "png", out.resolve("variants.png").toFile());
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
        stats.append(String.format("took %d s%n", (System.nanoTime() - start) / 1_000_000_000L));
        Files.writeString(out.resolve("stats.txt"), stats.toString());
        LotrPort.LOG.info("[preview]\n{}", stats);
    }

    private static int rgb(double r, double g, double b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }
}
