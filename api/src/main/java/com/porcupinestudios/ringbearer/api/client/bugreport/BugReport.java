package com.porcupinestudios.ringbearer.api.client.bugreport;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.GlUtil;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.neoforged.fml.ModList;

/** Gathers a bug report (description, sanitised logs, game facts, screenshot) and posts it to the relay. */
public final class BugReport {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final int TAIL_BYTES = 4 << 20;

    private BugReport() {}

    /**
     * Collects game facts on the render thread, then reads logs and sends off-thread.
     * @return the new ticket's URL
     */
    public static CompletableFuture<String> send(String title, String description, byte[] screenshotPng,
            String openScreen) {
        String relay = BugReportConfig.RELAY_URL.get().strip().replaceAll("/+$", "");
        if (relay.isEmpty()) {
            return CompletableFuture.failedFuture(new IOException("no relayUrl set in the ringbearer_api client config"));
        }
        Minecraft mc = Minecraft.getInstance();
        JsonObject meta = meta(mc);
        if (openScreen != null) {
            meta.addProperty("screen", openScreen);
        }
        Path gameDir = mc.gameDirectory.toPath();
        int lines = BugReportConfig.LOG_LINES.get();
        long maxAge = BugReportConfig.CRASH_REPORT_MAX_AGE_HOURS.get();
        LogSanitizer clean = new LogSanitizer(mc.getUser().getName(), mc.getUser().getProfileId().toString(),
                System.getProperty("user.name"));
        return CompletableFuture.supplyAsync(() -> {
            JsonObject body = new JsonObject();
            body.addProperty("title", title);
            body.addProperty("description", description);
            body.add("meta", meta);
            body.addProperty("log", clean.clean(tail(gameDir.resolve("logs/latest.log"), lines)));
            Path crash = newestCrash(gameDir.resolve("crash-reports"), maxAge);
            if (crash != null) {
                body.addProperty("crashName", crash.getFileName().toString());
                body.addProperty("crash", clean.clean(tail(crash, 3000)));
            }
            if (screenshotPng != null) {
                body.addProperty("screenshot", Base64.getEncoder().encodeToString(screenshotPng));
            }
            return GSON.toJson(body);
        }, Util.ioPool()).thenCompose(json -> HTTP.sendAsync(
                withAdminKey(HttpRequest.newBuilder(URI.create(relay + "/report")).timeout(Duration.ofSeconds(90))
                        .header("Content-Type", "application/json"))
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString())).thenApply(resp -> {
                    JsonObject r;
                    try {
                        r = GSON.fromJson(resp.body(), JsonObject.class);
                    } catch (RuntimeException e) {
                        r = null;
                    }
                    if (resp.statusCode() != 200 || r == null || !r.has("url")) {
                        String why = r != null && r.has("error") ? r.get("error").getAsString() : "HTTP " + resp.statusCode();
                        throw new IllegalStateException(why);
                    }
                    return r.get("url").getAsString();
                });
    }

    private static JsonObject meta(Minecraft mc) {
        JsonObject m = new JsonObject();
        m.addProperty("minecraft", SharedConstants.getCurrentVersion().getName());
        ModList.get().getModContainerById("neoforge")
                .ifPresent(c -> m.addProperty("neoforge", c.getModInfo().getVersion().toString()));
        m.addProperty("java", System.getProperty("java.version"));
        m.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        m.addProperty("gpu", GlUtil.getRenderer() + " / " + GlUtil.getOpenGLVersion());
        m.addProperty("memory", Runtime.getRuntime().maxMemory() / (1 << 20) + " MB max");
        m.addProperty("world", mc.level == null ? "none (menu)" : mc.hasSingleplayerServer() ? "singleplayer" : "multiplayer");
        if (mc.level != null && mc.player != null) {
            m.addProperty("dimension", mc.level.dimension().location().toString());
            m.addProperty("position", mc.player.blockPosition().toShortString());
        }
        JsonArray mods = new JsonArray();
        ModList.get().getMods().stream().sorted(Comparator.comparing(i -> i.getModId()))
                .forEach(i -> mods.add(i.getModId() + " " + i.getVersion()));
        m.add("mods", mods);
        return m;
    }

    /** The last {@code lines} lines of a file, reading at most its last few MB. */
    /** The admin key, if one is set, as a header: the relay then skips its daily limit. */
    private static HttpRequest.Builder withAdminKey(HttpRequest.Builder b) {
        String key = BugReportConfig.ADMIN_KEY.get().strip();
        return key.isEmpty() ? b : b.header("X-Admin-Key", key);
    }

    static String tail(Path file, int lines) {
        try (RandomAccessFile f = new RandomAccessFile(file.toFile(), "r")) {
            long start = Math.max(0, f.length() - TAIL_BYTES);
            byte[] buf = new byte[(int) (f.length() - start)];
            f.seek(start);
            f.readFully(buf);
            String[] all = new String(buf, StandardCharsets.UTF_8).split("\r?\n");
            return String.join("\n", Arrays.copyOfRange(all, Math.max(0, all.length - lines), all.length));
        } catch (IOException e) {
            return "(could not read " + file.getFileName() + ": " + e.getMessage() + ")";
        }
    }

    static Path newestCrash(Path dir, long maxAgeHours) {
        if (maxAgeHours <= 0 || !Files.isDirectory(dir)) {
            return null;
        }
        Instant cutoff = Instant.now().minus(Duration.ofHours(maxAgeHours));
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".txt"))
                    .filter(p -> modified(p).isAfter(cutoff))
                    .max(Comparator.comparing(BugReport::modified)).orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static Instant modified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
