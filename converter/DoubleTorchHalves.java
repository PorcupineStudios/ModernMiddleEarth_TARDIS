import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Gives LOTR's two-block torches in the exported structures their halves: LOTR stored the lower half as metadata 0 and
 * the upper as 1, which the id table maps to one state, so a template has two lower halves stacked. The upper one
 * becomes {@code half=upper}. structures.py does this itself from now on (props, kind double_torch); this patches
 * the .nbt files written before that, without a converter run.
 *
 *   java converter/DoubleTorchHalves.java lotr/src/generated/resources/data/lotr_port/structure
 *
 * Plain NBT reader and writer, no Minecraft classes. JDK 21. Prints the files it changed.
 */
public class DoubleTorchHalves {
    static final Set<String> TORCHES = Set.of("lotr_port:orc_torch_block", "lotr_port:tauredain_double_torch");

    public static void main(String[] args) throws IOException {
        List<Path> files;
        try (var s = Files.walk(Path.of(args[0]))) {
            files = s.filter(p -> p.toString().endsWith(".nbt")).toList();
        }
        int changed = 0;
        for (Path f : files) {
            Object[] root;
            try (var in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(f))))) {
                root = readNamed(in);
            }
            if (fix(cast(root[2]))) {
                try (var out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(f))))) {
                    out.writeByte(10);
                    out.writeUTF((String) root[1]);
                    writePayload(out, 10, root[2]);
                }
                System.out.println(f);
                changed++;
            }
        }
        System.out.println(changed + " of " + files.size() + " templates changed");
    }

    @SuppressWarnings("unchecked")
    static <T> T cast(Object o) {
        return (T) o;
    }

    static boolean fix(Map<String, Object> root) {
        List<Object> palettes = new ArrayList<>();
        if (root.containsKey("palette")) {
            palettes.add(((Object[]) root.get("palette"))[1]);
        }
        if (root.containsKey("palettes")) {
            for (Object p : (List<Object>) ((Object[]) root.get("palettes"))[1]) {
                palettes.add(((Object[]) p)[1]);
            }
        }
        boolean any = false;
        for (Object po : palettes) {
            List<Object> palette = cast(po);
            for (int i = 0, n = palette.size(); i < n; i++) {
                Map<String, Object> st = cast(palette.get(i));
                if (!TORCHES.contains(st.get("Name")) || st.containsKey("Properties")) {
                    continue;
                }
                st.put("Properties", props("lower"));
                Map<String, Object> upper = new LinkedHashMap<>(st);
                upper.put("Properties", props("upper"));
                int up = palette.size();
                palette.add(upper);
                any |= relabel(root, i, up);
            }
        }
        return any;
    }

    static Object[] props(String half) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("half", half);
        return new Object[] {(byte) 10, m};
    }

    /** Blocks of state {@code lower} with the same state under them become {@code upper}. */
    static boolean relabel(Map<String, Object> root, int lower, int upper) {
        List<Object> blocks = cast(((Object[]) root.get("blocks"))[1]);
        Set<List<Integer>> at = new HashSet<>();
        for (Object b : blocks) {
            Map<String, Object> m = cast(b);
            if ((int) m.get("state") == lower) {
                at.add(pos(m));
            }
        }
        boolean any = false;
        for (Object b : blocks) {
            Map<String, Object> m = cast(b);
            List<Integer> p = pos(m);
            if ((int) m.get("state") == lower && at.contains(List.of(p.get(0), p.get(1) - 1, p.get(2)))) {
                m.put("state", upper);
                any = true;
            }
        }
        return any;
    }

    static List<Integer> pos(Map<String, Object> block) {
        Object[] l = cast(block.get("pos"));
        List<Object> v = cast(l[1]);
        return List.of((Integer) v.get(0), (Integer) v.get(1), (Integer) v.get(2));
    }

    // NBT: compounds are LinkedHashMap<String,Object> whose values keep their tag type in a wrapper only where the
    // type isn't implied (lists: Object[]{elemType, List}, compounds inside lists: Map). Scalars keep their Java type.

    static Object[] readNamed(DataInputStream in) throws IOException {
        byte t = in.readByte();
        String name = in.readUTF();
        return new Object[] {t, name, read(in, t)};
    }

    static Object read(DataInputStream in, int t) throws IOException {
        return switch (t) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> { byte[] a = new byte[in.readInt()]; in.readFully(a); yield a; }
            case 8 -> in.readUTF();
            case 9 -> {
                byte et = in.readByte();
                int n = in.readInt();
                List<Object> l = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    l.add(read(in, et));
                }
                yield new Object[] {et, l};
            }
            case 10 -> {
                Map<String, Object> m = new LinkedHashMap<>();
                for (byte ct; (ct = in.readByte()) != 0; ) {
                    String k = in.readUTF();
                    Object v = read(in, ct);
                    m.put(k, ct == 10 || ct == 9 ? (ct == 10 ? new Object[] {(byte) 10, v} : v) : v);
                }
                yield m;
            }
            case 11 -> { int[] a = new int[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readInt(); yield a; }
            case 12 -> { long[] a = new long[in.readInt()]; for (int i = 0; i < a.length; i++) a[i] = in.readLong(); yield a; }
            default -> throw new IOException("tag " + t);
        };
    }

    static int type(Object v) {
        if (v instanceof Byte) return 1;
        if (v instanceof Short) return 2;
        if (v instanceof Integer) return 3;
        if (v instanceof Long) return 4;
        if (v instanceof Float) return 5;
        if (v instanceof Double) return 6;
        if (v instanceof byte[]) return 7;
        if (v instanceof String) return 8;
        if (v instanceof Object[] w) return ((byte) w[0]) == 10 && !(w[1] instanceof List) ? 10 : 9;
        if (v instanceof Map) return 10;
        if (v instanceof int[]) return 11;
        if (v instanceof long[]) return 12;
        throw new IllegalArgumentException(String.valueOf(v));
    }

    static void writePayload(DataOutputStream out, int t, Object v) throws IOException {
        switch (t) {
            case 1 -> out.writeByte((Byte) v);
            case 2 -> out.writeShort((Short) v);
            case 3 -> out.writeInt((Integer) v);
            case 4 -> out.writeLong((Long) v);
            case 5 -> out.writeFloat((Float) v);
            case 6 -> out.writeDouble((Double) v);
            case 7 -> { byte[] a = (byte[]) v; out.writeInt(a.length); out.write(a); }
            case 8 -> out.writeUTF((String) v);
            case 9 -> {
                Object[] w = (Object[]) v;
                byte et = (byte) w[0];
                List<Object> l = cast(w[1]);
                out.writeByte(l.isEmpty() && et == 0 ? 0 : et);
                out.writeInt(l.size());
                for (Object e : l) {
                    writePayload(out, et, e);
                }
            }
            case 10 -> {
                Map<String, Object> m = v instanceof Object[] w ? cast(w[1]) : cast(v);
                for (var e : m.entrySet()) {
                    int ct = type(e.getValue());
                    out.writeByte(ct);
                    out.writeUTF(e.getKey());
                    writePayload(out, ct, e.getValue());
                }
                out.writeByte(0);
            }
            case 11 -> { int[] a = (int[]) v; out.writeInt(a.length); for (int x : a) out.writeInt(x); }
            case 12 -> { long[] a = (long[]) v; out.writeInt(a.length); for (long x : a) out.writeLong(x); }
            default -> throw new IOException("tag " + t);
        }
    }
}
