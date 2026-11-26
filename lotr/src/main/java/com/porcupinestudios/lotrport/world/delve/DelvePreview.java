package com.porcupinestudios.lotrport.world.delve;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Dev tool ({@code /lotr_delve preview}): one picture per level of what a delve builds at foot height (floor + 1),
 * every {@code scale}th block, over the ground (dark under the mountain, green where the level would be in the open),
 * and {@code summary.txt}. Built from the plan alone with the map's ground (no world, no caves), so tuning a delve's
 * data can be checked in minutes without flying round it.
 */
public final class DelvePreview {
    private DelvePreview() {}

    public static List<Path> render(DelvePlan plan, Ground ground, Path out, int scale) throws IOException {
        Files.createDirectories(out);
        DelveSpec spec = plan.spec;
        int w = (plan.maxX - plan.minX) / scale + 1;
        int h = (plan.maxZ - plan.minZ) / scale + 1;
        int levels = spec.levels.size();
        BufferedImage[] img = new BufferedImage[levels];
        Map<Integer, Integer> feet = new HashMap<>();
        for (int k = 0; k < levels; k++) {
            feet.put(spec.levels.get(k).floor() + 1, k);
            img[k] = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        }
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                double g = ground.height(plan.minX + i * scale, plan.minZ + j * scale);
                for (int k = 0; k < levels; k++) {
                    img[k].setRGB(i, j, g > spec.levels.get(k).floor() + 2 ? 0x1E1A16 : 0x6E8E5E);
                }
            }
        }
        for (int cx = Math.floorDiv(plan.minX, 16); cx <= Math.floorDiv(plan.maxX, 16); cx++) {
            for (int cz = Math.floorDiv(plan.minZ, 16); cz <= Math.floorDiv(plan.maxZ, 16); cz++) {
                int x0 = cx * 16;
                int z0 = cz * 16;
                plan.build(new Canvas() {
                    public int minX() {
                        return x0;
                    }

                    public int maxX() {
                        return x0 + 15;
                    }

                    public int minZ() {
                        return z0;
                    }

                    public int maxZ() {
                        return z0 + 15;
                    }

                    public int minY() {
                        return -64;
                    }

                    public int maxY() {
                        return 639;
                    }

                    public void set(int x, int y, int z, Mat mat) {
                        Integer k = feet.get(y);
                        if (k == null || mat == Mat.KEEP || Math.floorMod(x - plan.minX, scale) != 0
                                || Math.floorMod(z - plan.minZ, scale) != 0) {
                            return;
                        }
                        img[k].setRGB((x - plan.minX) / scale, (z - plan.minZ) / scale, colour(mat));
                    }

                    public boolean solid(int x, int y, int z) {
                        return y < ground.height(x, z);
                    }

                    public void template(String id, int rotation, int x, int y, int z) {}
                });
            }
        }
        List<Path> files = new ArrayList<>();
        for (int k = 0; k < levels; k++) {
            Path f = out.resolve("level_" + String.format("%02d", k) + "_" + spec.levels.get(k).name() + ".png");
            ImageIO.write(img[k], "png", f.toFile());
            files.add(f);
        }
        Files.writeString(out.resolve("summary.txt"), plan.summary);
        return files;
    }

    /** A rough colour per block kind, for the pictures. */
    static int colour(Mat m) {
        String id = m.id;
        if (m.isAir()) {
            return 0xF4EEDC;
        }
        if (id.contains("lava") || id.contains("magma")) {
            return 0xFF6A00;
        }
        if (id.contains("water")) {
            return 0x3A6FD8;
        }
        if (id.contains("mithril")) {
            return 0x7FFFFF;
        }
        if (id.contains("_ore")) {
            return 0x3FAF3F;
        }
        if (id.contains("glowing") || id.contains("lantern") || id.contains("torch") || id.contains("chandelier")) {
            return 0xFFE040;
        }
        if (id.contains("door") || id.contains("gate")) {
            return 0xB040FF;
        }
        if (id.contains("stairs") || id.contains("slab")) {
            return 0xD8B860;
        }
        if (id.contains("pillar")) {
            return 0x5A6A90;
        }
        if (id.contains("chest") || id.contains("barrel") || id.contains("keg") || id.contains("forge")
                || id.contains("anvil") || id.contains("weapon_rack")) {
            return 0xC03030;
        }
        if (id.contains("cobweb") || id.contains("bone") || id.contains("skull") || id.contains("calcite")) {
            return 0xE8E0C0;
        }
        if (id.contains("cobble") || id.contains("gravel") || id.contains("andesite") || id.contains("coarse")) {
            return 0x8A7050;
        }
        if (id.contains("brick") || id.contains("tiles") || id.contains("polished")) {
            return 0x707078;
        }
        return 0x4A3A2A;
    }
}
