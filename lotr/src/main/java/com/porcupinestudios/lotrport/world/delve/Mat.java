package com.porcupinestudios.lotrport.world.delve;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A block a delve places, as data: a block id, block state properties and an optional chest loot table, written
 * {@code ns:block[prop=value,...]}. Interned, so the game side resolves each one to a block state once and caches it
 * in {@link #resolved}. Properties the block doesn't have are ignored there, so a palette can name any block for a
 * role that sets a facing. {@link #KEEP} places nothing (what was there stays).
 */
public final class Mat {
    private static final Map<String, Mat> ALL = new ConcurrentHashMap<>();
    public static final Mat AIR = of("minecraft:air");
    public static final Mat KEEP = of("keep");

    public final String id;
    public final Map<String, String> props;
    /** Loot table of a chest or barrel, null for none. */
    public final String loot;
    public final String key;
    /** The game's block state for this, set by the game side on first use. */
    public volatile Object resolved;

    private Mat(String id, Map<String, String> props, String loot, String key) {
        this.id = id;
        this.props = props;
        this.loot = loot;
        this.key = key;
    }

    public static Mat of(String spec) {
        return of(spec, null);
    }

    public static Mat of(String spec, String loot) {
        String id = spec.trim();
        Map<String, String> props = new TreeMap<>();
        int open = id.indexOf('[');
        if (open >= 0) {
            int close = id.lastIndexOf(']');
            if (close < open) {
                throw new IllegalArgumentException("bad block spec " + spec);
            }
            for (String kv : id.substring(open + 1, close).split(",")) {
                if (kv.isBlank()) {
                    continue;
                }
                int eq = kv.indexOf('=');
                if (eq < 0) {
                    throw new IllegalArgumentException("bad block property " + kv + " in " + spec);
                }
                props.put(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
            }
            id = id.substring(0, open).trim();
        }
        return intern(id, props, loot);
    }

    private static Mat intern(String id, Map<String, String> props, String loot) {
        StringBuilder key = new StringBuilder(id);
        if (!props.isEmpty()) {
            key.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : props.entrySet()) {
                if (!first) {
                    key.append(',');
                }
                key.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            key.append(']');
        }
        if (loot != null) {
            key.append("{loot=").append(loot).append('}');
        }
        String k = key.toString();
        Mat m = ALL.get(k);
        if (m != null) {
            return m;
        }
        return ALL.computeIfAbsent(k, s -> new Mat(id, Collections.unmodifiableMap(new TreeMap<>(props)), loot, s));
    }

    /** This block with one property set (added or replaced). */
    public Mat with(String prop, String value) {
        if (value.equals(props.get(prop)) || this == KEEP) {
            return this;
        }
        Map<String, String> p = new TreeMap<>(props);
        p.put(prop, value);
        return intern(id, p, loot);
    }

    public boolean isAir() {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air");
    }

    @Override
    public String toString() {
        return key;
    }
}
