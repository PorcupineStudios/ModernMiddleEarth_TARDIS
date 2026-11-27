package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * LOTR's own textures and sounds are not shipped in this mod: they are read from the player's copy of The Lord of the
 * Rings Mod: Legacy (the 1.7.10 jar, which a modpack downloads from its own page into mods/; NeoForge skips it as an
 * old Forge mod and never runs it). {@code lotr_port_legacy_assets.tsv} lists each of our asset paths and the jar
 * entry it is an unchanged copy of (written by converter/LegacyAssetMap.java); the build leaves those files out of the
 * mod jar. A required, always-on resource pack serves them from the jar, so Minecraft loads them into its atlases and
 * sound list with everything else at resource reload: no cost while playing.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LegacyAssets {
    private static final String MAP = "/lotr_port_legacy_assets.tsv";
    /** Textures the converter built from several of LOTR's: our path, then the recipe (op and arguments). */
    private static final String COMPOSITES = "/lotr_port_legacy_composites.tsv";
    /** Classes only The Lord of the Rings Mod 1.7.10 has, to tell its jar from any other old jar in mods/. */
    private static final String MARKER = "lotr/common/LOTRMod.class";
    /** Resource pack format of Minecraft 1.21.1. */
    private static final int PACK_FORMAT = 34;

    private LegacyAssets() {}

    @SubscribeEvent
    public static void addPack(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Map<String, String> map = readMap();
        Map<String, List<String>> composites = readComposites();
        Optional<Path> jar = findJar(FMLPaths.MODSDIR.get());
        if (jar.isEmpty()) {
            LotrPort.LOG.error("The Lord of the Rings Mod: Legacy (1.7.10 jar) is not in {}: {} of LOTR's textures "
                    + "and sounds will be missing", FMLPaths.MODSDIR.get(), map.size() + composites.size());
            NeoForge.EVENT_BUS.addListener(LegacyAssets::warnMissing);
            return;
        }
        LotrPort.LOG.info("Reading {} of LOTR's textures and sounds from {} and building {} more from them",
                map.size(), jar.get().getFileName(), composites.size());
        PackLocationInfo info = new PackLocationInfo(LotrPort.MOD_ID + ":lotr_legacy",
                Component.literal("LOTR Legacy assets"), PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new JarPack(location, jar.get(), map, composites);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return openPrimary(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (pack != null) {
            event.addRepositorySource(packs -> packs.accept(pack));
        }
    }

    private static boolean warned;

    /** Once, on the title screen: without the jar, LOTR's blocks and items draw as the missing texture. */
    private static void warnMissing(ScreenEvent.Init.Post event) {
        if (warned || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        warned = true;
        SystemToast.add(Minecraft.getInstance().getToasts(), SystemToast.SystemToastId.PACK_LOAD_FAILURE,
                Component.literal("LOTR Legacy mod not found"),
                Component.literal("Put The Lord of the Rings Mod: Legacy jar in your mods folder for its textures"));
    }

    /** Our path -> recipe for {@link LegacyComposer#compose}, from {@link #COMPOSITES}. */
    static Map<String, List<String>> readComposites() {
        Map<String, List<String>> out = new HashMap<>();
        for (Map.Entry<String, String> e : readTsv(COMPOSITES).entrySet()) {
            out.put(e.getKey(), List.of(e.getValue().split("\t")));
        }
        return out;
    }

    static Map<String, String> readMap() {
        return readTsv(MAP);
    }

    /** "assets/" + first column -> the rest of the line. */
    private static Map<String, String> readTsv(String resource) {
        Map<String, String> map = new HashMap<>();
        try (InputStream in = LegacyAssets.class.getResourceAsStream(resource)) {
            if (in == null) {
                return map;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    map.put("assets/" + line.substring(0, tab), line.substring(tab + 1));
                }
            }
        } catch (IOException e) {
            LotrPort.LOG.error("Couldn't read {}", resource, e);
        }
        return map;
    }

    /** The first jar in mods/ that holds LOTR's main class. */
    static Optional<Path> findJar(Path mods) {
        if (!Files.isDirectory(mods)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(mods)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".jar")).sorted()
                    .filter(LegacyAssets::isLotrJar).findFirst();
        } catch (IOException e) {
            LotrPort.LOG.error("Couldn't list {}", mods, e);
            return Optional.empty();
        }
    }

    private static boolean isLotrJar(Path p) {
        try (ZipFile zip = new ZipFile(p.toFile())) {
            return zip.getEntry(MARKER) != null;
        } catch (IOException e) {
            return false;
        }
    }

    /** Our paths answered from LOTR's jar; a texture's animation (.mcmeta) comes from beside it in the jar too. */
    static final class JarPack extends AbstractPackResources {
        private final Map<String, String> map;
        private final Map<String, List<String>> composites;
        /** Composites built this reload (PNG bytes; empty = failed), so each is built once. */
        private final Map<String, byte[]> built = new ConcurrentHashMap<>();
        private final Set<String> namespaces = new HashSet<>();
        private final ZipFile zip;

        JarPack(PackLocationInfo location, Path jar, Map<String, String> map, Map<String, List<String>> composites) {
            super(location);
            this.map = map;
            this.composites = composites;
            for (String key : map.keySet()) {
                namespaces.add(key.split("/", 3)[1]);
            }
            for (String key : composites.keySet()) {
                namespaces.add(key.split("/", 3)[1]);
            }
            ZipFile opened = null;
            try {
                opened = new ZipFile(jar.toFile());
            } catch (IOException e) {
                LotrPort.LOG.error("Couldn't open {}", jar, e);
            }
            this.zip = opened;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            if (path.length == 1 && path[0].equals(PACK_META)) {
                byte[] meta = ("{\"pack\":{\"description\":\"LOTR Legacy assets, read from your LOTR jar\","
                        + "\"pack_format\":" + PACK_FORMAT + "}}").getBytes(StandardCharsets.UTF_8);
                return () -> new java.io.ByteArrayInputStream(meta);
            }
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            return entry("assets/" + id.getNamespace() + "/" + id.getPath());
        }

        private IoSupplier<InputStream> entry(String ours) {
            if (zip == null) {
                return null;
            }
            List<String> recipe = composites.get(ours);
            if (recipe != null) {
                byte[] png = built.computeIfAbsent(ours, k -> build(k, recipe));
                return png.length == 0 ? null : () -> new java.io.ByteArrayInputStream(png);
            }
            String inJar = map.get(ours);
            if (inJar == null && ours.endsWith(METADATA_EXTENSION)) {
                String base = map.get(ours.substring(0, ours.length() - METADATA_EXTENSION.length()));
                inJar = base == null ? null : base + METADATA_EXTENSION;
            }
            ZipEntry e = inJar == null ? null : zip.getEntry(inJar);
            return e == null ? null : () -> zip.getInputStream(e);
        }

        private byte[] build(String ours, List<String> recipe) {
            try {
                LegacyComposer.Img img = LegacyComposer.compose(recipe, this::read);
                if (img != null) {
                    return LegacyComposer.png(img);
                }
                LotrPort.LOG.warn("Couldn't build {} from LOTR's jar ({})", ours, recipe);
            } catch (IOException | RuntimeException e) {
                LotrPort.LOG.warn("Couldn't build {} from LOTR's jar ({})", ours, recipe, e);
            }
            return new byte[0];
        }

        private byte[] read(String inJar) {
            ZipEntry e = zip.getEntry(inJar);
            if (e == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(e)) {
                return in.readAllBytes();
            } catch (IOException ex) {
                return null;
            }
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            String prefix = "assets/" + namespace + "/" + path + "/";
            for (String key : Stream.concat(map.keySet().stream(), composites.keySet().stream()).toList()) {
                if (key.startsWith(prefix)) {
                    IoSupplier<InputStream> s = entry(key);
                    if (s != null) {
                        output.accept(ResourceLocation.fromNamespaceAndPath(namespace,
                                key.substring(("assets/" + namespace + "/").length())), s);
                    }
                }
            }
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES ? namespaces : Set.of();
        }

        @Override
        public void close() {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException ignored) {
                    // closing a read-only zip
                }
            }
        }
    }
}
