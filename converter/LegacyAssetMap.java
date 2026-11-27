import com.porcupinestudios.lotrport.client.LegacyComposer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/**
 * Lists which of the port's generated textures and sounds are LOTR's, so the built mod leaves them out and
 * client/LegacyAssets serves them from the player's own LOTR jar:
 * <ul>
 * <li>lotr/src/main/resources/lotr_port_legacy_assets.tsv: unchanged copies of a jar file (pixels equal for PNGs,
 * bytes for the rest), "our path TAB jar path"</li>
 * <li>lotr/src/main/resources/lotr_port_legacy_composites.tsv: textures the converter built from several of LOTR's
 * (drinks in vessels, connected sheets, gate frames, dyed hats), "our path TAB op TAB args...", listed only when
 * client/LegacyComposer rebuilds the converter's file exactly</li>
 * </ul>
 * Everything else (our own textures, vanilla's recoloured, Minecraft 1.7.10's horses) stays in the mod.
 *
 *   ./gradlew :lotr:compileJava
 *   java -cp lotr/build/classes/java/main converter/LegacyAssetMap.java "converter/libs/LOTRMod v36.15.jar"
 *
 * Run from the repo root after the converter has written lotr/src/generated/resources. JDK 21.
 */
public class LegacyAssetMap {
    static final Path GENERATED = Path.of("lotr/src/generated/resources/assets");
    static final Path COPIES = Path.of("lotr/src/main/resources/lotr_port_legacy_assets.tsv");
    static final Path COMPOSITES = Path.of("lotr/src/main/resources/lotr_port_legacy_composites.tsv");
    static final String ITEMS = "assets/lotr/textures/items/", BLOCKS = "assets/lotr/textures/blocks/";
    /** generate.py ITEM_ARMOR + HAT_TINTS: LOTR dyes its hats; the undyed colours. */
    static final Map<String, List<String>> HATS = Map.of(
            "lotr_port/textures/models/armor/leather_hat_layer_1.png", List.of("tint", "assets/lotr/armor/hat.png", "684A36"),
            "lotr_port/textures/models/armor/party_hat_layer_1.png", List.of("tint", "assets/lotr/armor/partyhat.png", "C83A3A"));

    public static void main(String[] args) throws Exception {
        try (ZipFile zip = new ZipFile(args[0])) {
            Map<String, byte[]> cache = new HashMap<>();
            java.util.function.Function<String, byte[]> jar = p -> cache.computeIfAbsent(p, k -> {
                try {
                    var e = zip.getEntry(k);
                    return e == null ? null : zip.getInputStream(e).readAllBytes();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
            List<String> names = Collections.list(zip.entries()).stream().map(e -> e.getName()).toList();
            Map<String, String> jarByKey = new HashMap<>();
            for (String name : names) {
                if (name.startsWith("assets/") && wanted(name)) {
                    jarByKey.putIfAbsent(key(name, jar.apply(name)), name);
                }
            }
            List<Path> files;
            try (var walk = Files.walk(GENERATED)) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            List<String> copies = new ArrayList<>(), left = new ArrayList<>();
            Map<String, String> copyOf = new HashMap<>();
            for (Path p : files) {
                String rel = GENERATED.relativize(p).toString().replace('\\', '/');
                if (!wanted(rel)) continue;
                String hit = jarByKey.get(key(rel, Files.readAllBytes(p)));
                if (hit != null) {
                    copies.add(rel + "\t" + hit);
                    copyOf.put(rel, hit);
                } else {
                    left.add(rel);
                }
            }

            // candidates for the composites
            Map<String, List<String>> vessels = new HashMap<>(), stems = new HashMap<>();
            List<String> liquids = new ArrayList<>();
            for (String n : names) {
                if (n.startsWith(ITEMS + "drink_") && n.endsWith(".png") && n.indexOf('/', ITEMS.length()) < 0) {
                    vessels.computeIfAbsent(snake(n.substring(ITEMS.length() + 6, n.length() - 4)), k -> new ArrayList<>()).add(n);
                } else if (n.startsWith(ITEMS) && n.endsWith("_liquid.png")) {
                    liquids.add(n);
                } else if (n.startsWith(BLOCKS) && n.endsWith("_base.png")) {
                    String stem = n.substring(0, n.length() - "_base.png".length());
                    stems.computeIfAbsent(snake(stem.substring(BLOCKS.length())), k -> new ArrayList<>()).add(stem);
                }
            }
            Map<String, String> liquidOf = new HashMap<>();  // drink -> the liquid that worked
            List<String> composites = new ArrayList<>(), ours = new ArrayList<>();
            for (String rel : left) {
                byte[] want = Files.readAllBytes(GENERATED.resolve(rel));
                List<String> found = null;
                String[] parts = rel.split("/");
                if (HATS.containsKey(rel)) {
                    found = check(HATS.get(rel), jar, want) ? HATS.get(rel) : null;
                } else if (rel.startsWith("lotr_port/textures/item/drink/") && parts.length == 6) {
                    String drink = parts[4], vessel = parts[5].replace(".png", "");
                    for (String v : vessels.getOrDefault(vessel, List.of())) {
                        List<String> tries = new ArrayList<>();
                        if (liquidOf.containsKey(drink)) tries.add(liquidOf.get(drink));
                        tries.addAll(liquids);
                        for (String l : tries) {
                            List<String> r = List.of("drink", v, l);
                            if (check(r, jar, want)) {
                                found = r;
                                liquidOf.put(drink, l);
                                break;
                            }
                        }
                        if (found != null) break;
                    }
                } else if (rel.startsWith("lotr_port/textures/block/connected/")) {
                    String name = parts[4].replace(".png", "");
                    for (String stem : stems.getOrDefault(name, List.of())) {
                        if (check(List.of("sheet", stem), jar, want)) found = List.of("sheet", stem);
                    }
                    String src = copyOf.get("lotr_port/textures/block/" + name + ".png");
                    if (found == null && src != null && check(List.of("gate_sheet", src), jar, want)) {
                        found = List.of("gate_sheet", src);
                    }
                } else if (rel.startsWith("lotr_port/textures/block/") && parts.length == 4) {
                    for (String stem : stems.getOrDefault(parts[3].replace(".png", ""), List.of())) {
                        if (check(List.of("connected", stem), jar, want)) found = List.of("connected", stem);
                    }
                }
                if (found != null) composites.add(rel + "\t" + String.join("\t", found));
                else ours.add(rel);
            }
            Files.write(COPIES, copies);
            Files.write(COMPOSITES, composites);
            System.out.println(copies.size() + " copies and " + composites.size()
                    + " composites read from LOTR's jar at runtime; " + ours.size() + " stay in the mod:");
            Map<String, Integer> byDir = new TreeMap<>();
            for (String o : ours) byDir.merge(o.substring(0, o.lastIndexOf('/')), 1, Integer::sum);
            byDir.forEach((k, v) -> System.out.println("  " + k + ": " + v));
        }
    }

    /** True if the recipe rebuilds the converter's file pixel for pixel. */
    static boolean check(List<String> recipe, java.util.function.Function<String, byte[]> jar, byte[] want)
            throws Exception {
        LegacyComposer.Img got = LegacyComposer.compose(recipe, jar);
        LegacyComposer.Img exp = LegacyComposer.read(p -> want, "");
        return got != null && exp != null && got.w() == exp.w() && got.h() == exp.h() && same(got.px(), exp.px());
    }

    static boolean same(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i] && !((a[i] >>> 24) == 0 && (b[i] >>> 24) == 0)) return false;
        }
        return true;
    }

    /** converter/assets.py snake: camelCase / odd LOTR names to a resource path segment. */
    static String snake(String name) {
        name = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        name = name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2");
        name = name.toLowerCase(Locale.ROOT);
        name = name.replaceAll("[^a-z0-9_./-]+", "_");
        name = name.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return name;
    }

    static boolean wanted(String name) {
        return name.endsWith(".png") || name.endsWith(".ogg");
    }

    /** PNGs by their pixels (Pillow re-encodes them), fully transparent pixels counted alike; other files by bytes. */
    static String key(String name, byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        if (name.endsWith(".png")) {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) return "unreadable:" + name;
            int w = img.getWidth(), h = img.getHeight();
            int[] px = img.getRGB(0, 0, w, h, null, 0, w);
            ByteBuffer buf = ByteBuffer.allocate(8 + px.length * 4).putInt(w).putInt(h);
            for (int c : px) buf.putInt((c >>> 24) == 0 ? 0 : c);
            md.update(buf.array());
        } else {
            md.update(bytes);
        }
        return HexFormat.of().formatHex(md.digest());
    }
}
