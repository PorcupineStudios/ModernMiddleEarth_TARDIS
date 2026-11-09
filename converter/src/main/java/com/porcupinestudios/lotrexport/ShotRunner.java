package com.porcupinestudios.lotrexport;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

/**
 * Screenshots of real LOTR terrain, to compare with the port ({@code -Dlotrexport.shots=<script>}). Script lines:
 * {@code goto X Z YAW PITCH UP [floor]} (into Middle-earth, flying UP blocks over the highest block, or with
 * {@code floor} over the ground under trees; with night vision), {@code wait TICKS},
 * {@code shot NAME} (saved in the game's screenshots folder), {@code #} comments. Quits at the end.
 */
public class ShotRunner {
    private static final String FOLDER = "lotrexport-shots";
    private final List<String> lines = new ArrayList<>();
    private int next;
    private int waiting;
    private boolean started;
    private volatile String[] pending;

    public ShotRunner(File script) throws Exception {
        for (String line : Files.readAllLines(script.toPath(), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty() && !line.startsWith("#")) {
                lines.add(line.trim());
            }
        }
    }

    public void start(Minecraft mc) {
        mc.gameSettings.pauseOnLostFocus = false;
        mc.getSaveLoader().flushCache();
        mc.getSaveLoader().deleteWorldDirectory(FOLDER);
        FMLCommonHandler.instance().bus().register(this);
        mc.launchIntegratedServer(FOLDER, FOLDER,
                new WorldSettings(1L, WorldSettings.GameType.CREATIVE, true, false, WorldType.DEFAULT));
        started = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || !started || mc.thePlayer == null) {
            return;
        }
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        mc.gameSettings.hideGUI = true;
        if (pending != null) {
            return;
        }
        if (waiting > 0) {
            waiting--;
            return;
        }
        if (next >= lines.size()) {
            started = false;
            System.out.println("[lotrexport] shots finished");
            mc.shutdown();
            return;
        }
        String[] p = lines.get(next++).split("\\s+");
        try {
            switch (p[0]) {
                case "wait" -> waiting = Integer.parseInt(p[1]);
                case "shot" -> System.out.println("[lotrexport] " + ScreenShotHelper.saveScreenshot(mc.mcDataDir,
                        p[1] + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer()).getUnformattedText());
                case "goto", "survey", "rough" -> pending = p; // runs on the server thread: LOTR's generator isn't thread-safe
                default -> System.out.println("[lotrexport] unknown shot step " + p[0]);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        String[] p = pending;
        if (event.phase != TickEvent.Phase.END || p == null) {
            return;
        }
        try {
            if (p[0].equals("rough")) {
                rough(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                return;
            }
            if (p[0].equals("survey")) {
                survey(Integer.parseInt(p[1]), new File(p[2]));
                return;
            }
            go(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Float.parseFloat(p[3]), Float.parseFloat(p[4]),
                    Integer.parseInt(p[5]), p.length > 6 && p[6].equals("floor"));
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            pending = null; // only now may the client go on (and quit)
        }
    }

    /**
     * Writes terrain_survey.json: LOTR's ground height (highest non-liquid block, no decoration) every {@code step}
     * chunks over the whole map, per biome as count/mean/std, and each LOTRMountains peak's ground at its centre.
     */
    private void survey(int step, File out) throws Exception {
        WorldServer world = MinecraftServer.getServer().worldServerForDimension(dimension());
        net.minecraft.world.chunk.IChunkProvider gen = world.theChunkProviderServer.currentChunkProvider;
        Class<?> layer = Class.forName("lotr.common.world.genlayer.LOTRGenLayerWorld");
        int w = layer.getField("imageWidth").getInt(null);
        int h = layer.getField("imageHeight").getInt(null);
        java.lang.reflect.Method toX = Class.forName("lotr.common.world.map.LOTRWaypoint").getMethod("mapToWorldX", double.class);
        java.lang.reflect.Method toZ = Class.forName("lotr.common.world.map.LOTRWaypoint").getMethod("mapToWorldZ", double.class);
        int x0 = (Integer) toX.invoke(null, 0.0) >> 4, x1 = (Integer) toX.invoke(null, (double) w) >> 4;
        int z0 = (Integer) toZ.invoke(null, 0.0) >> 4, z1 = (Integer) toZ.invoke(null, (double) h) >> 4;
        Class<?> dimClass = Class.forName("lotr.common.LOTRDimension");
        Object[] lotrBiomes = (Object[]) dimClass.getField("biomeList").get(dimClass.getField("MIDDLE_EARTH").get(null));
        java.util.Map<String, double[]> stats = new java.util.TreeMap<>(); // n, sum, sum of squares
        int done = 0;
        for (int cx = x0; cx < x1; cx += step) {
            for (int cz = z0; cz < z1; cz += step) {
                net.minecraft.world.chunk.Chunk c = gen.provideChunk(cx, cz);
                for (int i = 2; i < 16; i += 4) {
                    for (int k = 2; k < 16; k += 4) {
                        int y = ground(c, i, k);
                        // the chunk's ids index LOTR's own biome list, not vanilla's
                        Object biome = lotrBiomes[c.getBiomeArray()[k << 4 | i] & 255];
                        String b = biome == null ? "?" : ((net.minecraft.world.biome.BiomeGenBase) biome).biomeName;
                        double[] st = stats.computeIfAbsent(b, key -> new double[3]);
                        st[0]++;
                        st[1] += y;
                        st[2] += (double) y * y;
                    }
                }
                if (++done % 2000 == 0) {
                    System.out.println("[lotrexport] survey " + done + " chunks");
                }
            }
        }
        com.google.gson.JsonObject biomes = new com.google.gson.JsonObject();
        for (java.util.Map.Entry<String, double[]> e : stats.entrySet()) {
            double n = e.getValue()[0], mean = e.getValue()[1] / n;
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("n", (int) n);
            o.addProperty("mean", Math.round(mean * 10) / 10.0);
            o.addProperty("std", Math.round(Math.sqrt(Math.max(0, e.getValue()[2] / n - mean * mean)) * 10) / 10.0);
            biomes.add(e.getKey(), o);
        }
        com.google.gson.JsonArray peaks = new com.google.gson.JsonArray();
        for (Object m : (Object[]) Class.forName("lotr.common.world.map.LOTRMountains").getMethod("values").invoke(null)) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("name", m.toString().toLowerCase());
            for (String f : new String[] { "xCoord", "zCoord", "range", "lavaRange" }) {
                java.lang.reflect.Field fl = m.getClass().getDeclaredField(f);
                fl.setAccessible(true);
                o.addProperty(f, fl.getInt(m));
            }
            java.lang.reflect.Field hf = m.getClass().getDeclaredField("height");
            hf.setAccessible(true);
            o.addProperty("height", hf.getFloat(m));
            int px = o.get("xCoord").getAsInt(), pz = o.get("zCoord").getAsInt();
            o.addProperty("ground", ground(gen.provideChunk(px >> 4, pz >> 4), px & 15, pz & 15));
            peaks.add(o);
        }
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("step_chunks", step);
        root.add("biomes", biomes);
        root.add("mountains", peaks);
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root)
                .getBytes(StandardCharsets.UTF_8));
        System.out.println("[lotrexport] survey written: " + done + " chunks");
    }

    /**
     * {@code rough X Z R}: LOTR's ground over the square of radius R round X, Z on a 4-block grid, logged as mean,
     * spread, mean step between neighbours and share of steps of 4+ (the port's GameTest terrainRoughness does the same).
     */
    private void rough(int x0, int z0, int r) throws Exception {
        WorldServer world = MinecraftServer.getServer().worldServerForDimension(dimension());
        net.minecraft.world.chunk.IChunkProvider gen = world.theChunkProviderServer.currentChunkProvider;
        int n = r * 2 / 4;
        int[][] h = new int[n][n];
        java.util.Map<Long, net.minecraft.world.chunk.Chunk> chunks = new java.util.HashMap<>();
        double sum = 0, sq = 0, step = 0;
        int steps = 0, steep = 0;
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < n; k++) {
                int x = x0 - r + i * 4, z = z0 - r + k * 4;
                net.minecraft.world.chunk.Chunk c = chunks.computeIfAbsent(((long) (x >> 4) << 32) ^ ((z >> 4) & 0xffffffffL),
                        key -> gen.provideChunk(x >> 4, z >> 4));
                h[i][k] = groundNoTrees(c, x & 15, z & 15);
                sum += h[i][k];
                sq += (double) h[i][k] * h[i][k];
                for (int[] d : new int[][] { { i - 1, k }, { i, k - 1 } }) {
                    if (d[0] >= 0 && d[1] >= 0) {
                        int dh = Math.abs(h[i][k] - h[d[0]][d[1]]);
                        step += dh;
                        steps++;
                        steep += dh >= 4 ? 1 : 0;
                    }
                }
            }
        }
        double mean = sum / (n * n);
        System.out.println(String.format("[lotrexport] rough %d %d mean %.1f std %.1f step %.2f steep %.3f", x0, z0, mean,
                Math.sqrt(Math.max(0, sq / (n * n) - mean * mean)), step / steps, (double) steep / steps));
    }

    /** Highest solid block + 1, skipping leaves and logs (provideChunk has no trees anyway: it isn't populated). */
    private static int groundNoTrees(net.minecraft.world.chunk.Chunk c, int x, int z) {
        for (int y = 255; y > 0; y--) {
            net.minecraft.block.material.Material m = c.getBlock(x, y, z).getMaterial();
            if (m.isSolid() && m != net.minecraft.block.material.Material.leaves
                    && m != net.minecraft.block.material.Material.wood) {
                return y + 1;
            }
        }
        return 0;
    }

    private static int ground(net.minecraft.world.chunk.Chunk c, int x, int z) {
        for (int y = 255; y > 0; y--) {
            net.minecraft.block.Block b = c.getBlock(x, y, z);
            if (b.getMaterial().isSolid()) {
                return y + 1;
            }
        }
        return 0;
    }

    private static int dimension() throws Exception {
        return Class.forName("lotr.common.LOTRDimension").getField("dimensionID")
                .getInt(Class.forName("lotr.common.LOTRDimension").getField("MIDDLE_EARTH").get(null));
    }

    private void go(int x, int z, float yaw, float pitch, int up, boolean floor) throws Exception {
        MinecraftServer server = MinecraftServer.getServer();
        int dim = Class.forName("lotr.common.LOTRDimension").getField("dimensionID")
                .getInt(Class.forName("lotr.common.LOTRDimension").getField("MIDDLE_EARTH").get(null));
        EntityPlayerMP player = (EntityPlayerMP) server.getConfigurationManager().playerEntityList.get(0);
        WorldServer world = server.worldServerForDimension(dim);
        if (player.dimension != dim) {
            server.getConfigurationManager().transferPlayerToDimension(player, dim, new Teleporter(world) {
                @Override
                public void placeInPortal(Entity entity, double px, double py, double pz, float f) {}

                @Override
                public boolean placeInExistingPortal(Entity entity, double px, double py, double pz, float f) {
                    return true;
                }
            });
        }
        world.getChunkFromBlockCoords(x, z); // loads (and generates) it so the height is real
        int y = world.getHeightValue(x, z);
        if (floor) { // down through leaves, logs and plants to the ground
            while (y > 1) {
                net.minecraft.block.material.Material m = world.getBlock(x, y - 1, z).getMaterial();
                if (m.isSolid() && m != net.minecraft.block.material.Material.leaves
                        && m != net.minecraft.block.material.Material.wood
                        && m != net.minecraft.block.material.Material.plants) {
                    break;
                }
                y--;
            }
        }
        player.addPotionEffect(new net.minecraft.potion.PotionEffect(net.minecraft.potion.Potion.nightVision.id,
                1000000, 0, true));
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(x + 0.5, y + up, z + 0.5, yaw, pitch);
        world.setWorldTime(6000);
        System.out.println("[lotrexport] goto " + x + " " + z + " ground " + y + " biome "
                + world.getBiomeGenForCoords(x, z).biomeName);
    }
}
