package com.porcupinestudios.tardisrb.space;

import com.porcupinestudios.tardisrb.TardisRB;
import com.porcupinestudios.tardisrb.net.SkyNet;
import com.porcupinestudios.tardisrb.progression.TardisContent;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Globe maps for planets without a picture: the planet's biomes sampled on a grid ({@code map_size} wide, half as
 * tall) and coloured ({@link BiomeColor}). Sampling only asks the biome source (no chunks are made), a little each
 * tick ({@code map_budget_ms}), once per world: the finished map is saved in the world folder and sent compressed to
 * clients, which draw it as the globe's texture.
 */
public final class PlanetMaps {
    private static final int VERSION = 2;
    private static final Map<ResourceLocation, Job> JOBS = new HashMap<>();

    /** A map being sampled (or finished: {@code packed} is set). */
    private static final class Job {
        final Planet planet;
        final int width;
        final int height;
        final byte[] rgb;
        int next;
        byte[] packed;

        Job(Planet planet) {
            this.planet = planet;
            this.width = planet.link().mapSize();
            this.height = Math.max(1, (int) Math.round(width * planet.height() / planet.width()));
            this.rgb = new byte[width * height * 3];
        }

        boolean same(Planet p) {
            return p.link().surface().equals(planet.link().surface()) && p.link().mapSize() == width
                    && p.link().dimension().equals(planet.link().dimension());
        }
    }

    private PlanetMaps() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(PlanetMaps::onTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> JOBS.clear());
    }

    /** The finished, compressed map of a planet, if it has one. */
    public static Optional<SkyNet.PlanetMap> map(ResourceLocation planet) {
        Job job = JOBS.get(planet);
        return job == null || job.packed == null ? Optional.empty()
                : Optional.of(new SkyNet.PlanetMap(planet, job.width, job.height, job.packed));
    }

    private static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long budget = (long) (TardisContent.tuning().space().mapBudgetMs() * 1_000_000);
        for (Planet p : Planets.all(server)) {
            if (p.link().picture().isPresent()) {
                continue;
            }
            Job job = JOBS.get(p.id());
            if (job == null || !job.same(p)) {
                job = new Job(p);
                JOBS.put(p.id(), job);
                if (load(server, job)) {
                    continue;
                }
            }
            if (job.packed != null) {
                continue;
            }
            if (budget <= 0) {
                return;
            }
            ServerLevel level = server.getLevel(p.link().dimension());
            if (level == null) {
                continue;
            }
            long until = System.nanoTime() + budget;
            sample(level, job, until);
            if (job.packed != null) {
                save(server, job);
                TardisRB.LOG.info("Globe map of {} done ({}x{})", p.id(), job.width, job.height);
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    SkyNet.sendMap(player, map(p.id()).orElseThrow());
                }
            }
            return; // one planet a tick
        }
    }

    private static void sample(ServerLevel level, Job job, long until) {
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Planet p = job.planet;
        int qy = QuartPos.fromBlock(p.link().ground());
        int total = job.width * job.height;
        while (job.next < total) {
            int px = job.next % job.width;
            int pz = job.next / job.width;
            double x = p.minX() + (px + 0.5) / job.width * p.width();
            double z = p.minZ() + (pz + 0.5) / job.height * p.height();
            int c = BiomeColor.of(source.getNoiseBiome(QuartPos.fromBlock((int) Math.floor(x)), qy,
                    QuartPos.fromBlock((int) Math.floor(z)), sampler));
            int i = job.next * 3;
            job.rgb[i] = (byte) (c >> 16);
            job.rgb[i + 1] = (byte) (c >> 8);
            job.rgb[i + 2] = (byte) c;
            job.next++;
            if ((job.next & 63) == 0 && System.nanoTime() > until) {
                return;
            }
        }
        job.packed = pack(job.rgb);
    }

    static byte[] pack(byte[] rgb) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(rgb.length / 4);
        try (DeflaterOutputStream z = new DeflaterOutputStream(out, new Deflater(Deflater.BEST_COMPRESSION))) {
            z.write(rgb);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    private static Path file(MinecraftServer server, ResourceLocation id) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("tardis_rb_planets")
                .resolve(id.getNamespace() + "__" + id.getPath().replace('/', '_') + ".bin");
    }

    private static void save(MinecraftServer server, Job job) {
        Path f = file(server, job.planet.id());
        try {
            Files.createDirectories(f.getParent());
            try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(f))) {
                out.writeInt(VERSION);
                var b = job.planet.link().surface();
                out.writeUTF(job.planet.link().dimension().location().toString());
                out.writeInt(b.minX());
                out.writeInt(b.maxX());
                out.writeInt(b.minZ());
                out.writeInt(b.maxZ());
                out.writeInt(job.width);
                out.writeInt(job.packed.length);
                out.write(job.packed);
            }
        } catch (IOException e) {
            TardisRB.LOG.warn("Couldn't save the globe map of {}: {}", job.planet.id(), e.toString());
        }
    }

    /** Loads a saved map made for the same surface; true if it did. */
    private static boolean load(MinecraftServer server, Job job) {
        Path f = file(server, job.planet.id());
        if (!Files.isRegularFile(f)) {
            return false;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(f))) {
            var b = job.planet.link().surface();
            if (in.readInt() != VERSION || !in.readUTF().equals(job.planet.link().dimension().location().toString())
                    || in.readInt() != b.minX() || in.readInt() != b.maxX() || in.readInt() != b.minZ()
                    || in.readInt() != b.maxZ() || in.readInt() != job.width) {
                return false;
            }
            byte[] packed = new byte[in.readInt()];
            in.readFully(packed);
            job.packed = packed;
            job.next = job.width * job.height;
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
