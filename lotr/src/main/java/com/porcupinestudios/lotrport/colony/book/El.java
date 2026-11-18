package com.porcupinestudios.lotrport.colony.book;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One element of a book page. The client lays them out top to bottom:
 * <ul>
 *   <li>{@code head}: a section heading ({@code text}).</li>
 *   <li>{@code pair}: label left, value right, an optional item icon ({@code item}) before the label.</li>
 *   <li>{@code meter}: label and value over a bar ({@code fill} 0-1, {@code mark} a threshold, -1 none).</li>
 *   <li>{@code text}: a paragraph, wrapped.</li>
 *   <li>{@code card}: a framed row for a person, building or offer: a face ({@code face} = skin texture
 *       "tex|64" or an item id), title, subtitle ({@code value}), small meters and buttons as {@code children},
 *       clicking it runs {@code action}.</li>
 *   <li>{@code buttons}: a row of {@code button} children.</li>
 *   <li>{@code button}: {@code text}, runs {@code action} with {@code arg}; disabled with {@code why}.</li>
 *   <li>{@code stepper}: label, value shown, arrows step {@code num} by {@code step} in min..max.</li>
 *   <li>{@code items}: a grid of item icons with counts: children {@code pair}s (item, num).</li>
 *   <li>{@code chip}: a header chip: an item icon and a short value (coins, people...).</li>
 *   <li>{@code field}: a text box; Enter sends its text as the arg of {@code action}.</li>
 *   <li>{@code tag}: a small label chip.</li>
 *   <li>{@code gap}: a little space.</li>
 * </ul>
 * Tone: 0 normal, 1 muted, 2 good, 3 warning, 4 bad (as {@code ui.Tone}).
 */
public record El(String kind, String text, String value, int tone, float fill, float mark, String item, String face,
        String action, String arg, int num, int min, int max, int step, boolean enabled, String why,
        List<El> children) {
    public static final int NORMAL = 0;
    public static final int MUTED = 1;
    public static final int GOOD = 2;
    public static final int WARN = 3;
    public static final int BAD = 4;

    private static El of(String kind, String text, String value, int tone) {
        return new El(kind, text, value, tone, 0, -1, "", "", "", "", 0, 0, 0, 1, true, "", List.of());
    }

    public static El head(String text) {
        return of("head", text, "", NORMAL);
    }

    public static El pair(String label, String value) {
        return of("pair", label, value, NORMAL);
    }

    public static El pair(String label, String value, int tone) {
        return of("pair", label, value, tone);
    }

    public static El text(String text) {
        return of("text", text, "", MUTED);
    }

    public static El text(String text, int tone) {
        return of("text", text, "", tone);
    }

    public static El gap() {
        return of("gap", "", "", NORMAL);
    }

    public static El meter(String label, String value, double fill) {
        return meter(label, value, fill, -1);
    }

    public static El meter(String label, String value, double fill, double mark) {
        int tone = fill < 0.34 ? BAD : fill < 0.6 ? WARN : GOOD;
        return new El("meter", label, value, tone, (float) Math.max(0, Math.min(1, fill)), (float) mark, "", "", "", "",
                0, 0, 0, 1, true, "", List.of());
    }

    public static El chip(String item, String value, String tooltip) {
        return new El("chip", tooltip, value, NORMAL, 0, -1, item, "", "", "", 0, 0, 0, 1, true, "", List.of());
    }

    public static El button(String label, String action, String arg) {
        return new El("button", label, "", NORMAL, 0, -1, "", "", action, arg, 0, 0, 0, 1, true, "", List.of());
    }

    public static El button(String label, String action, String arg, boolean enabled, String why) {
        return new El("button", label, "", NORMAL, 0, -1, "", "", action, arg, 0, 0, 0, 1, enabled, why, List.of());
    }

    public static El buttons(List<El> buttons) {
        return new El("buttons", "", "", NORMAL, 0, -1, "", "", "", "", 0, 0, 0, 1, true, "", List.copyOf(buttons));
    }

    public static El stepper(String label, String shown, String action, String arg, int value, int min, int max,
            int step, boolean enabled, String why) {
        return new El("stepper", label, shown, NORMAL, 0, -1, "", "", action, arg, value, min, max, step, enabled, why,
                List.of());
    }

    /** A card: face (skin "tex|64" or item id), title, subtitle, outline tone, click action, meters and buttons. */
    public static El card(String face, String title, String subtitle, int tone, String action, String arg,
            List<El> children) {
        return new El("card", title, subtitle, tone, 0, -1, "", face, action, arg, 0, 0, 0, 1, true, "",
                List.copyOf(children));
    }

    /** A text box (one per page): {@code text} its hint, {@code value} what is in it; Enter sends it as the arg. */
    public static El field(String hint, String value, String action, boolean enabled, String why) {
        return new El("field", hint, value, NORMAL, 0, -1, "", "", action, "", 0, 0, 0, 1, enabled, why, List.of());
    }

    /** A small label chip (traits...), laid out in a {@link #buttons} row. */
    public static El tag(String text, int tone) {
        return new El("tag", text, "", tone, 0, -1, "", "", "", "", 0, 0, 0, 1, true, "", List.of());
    }

    public static El items(List<El> stacks) {
        return new El("items", "", "", NORMAL, 0, -1, "", "", "", "", 0, 0, 0, 1, true, "", List.copyOf(stacks));
    }

    /** One stack of an {@link #items} grid. */
    public static El stack(String item, int count) {
        return new El("pair", "", "", NORMAL, 0, -1, item, "", "", "", count, 0, 0, 1, true, "", List.of());
    }

    public El withItem(String id) {
        return new El(kind, text, value, tone, fill, mark, id, face, action, arg, num, min, max, step, enabled, why,
                children);
    }

    public El withTone(int t) {
        return new El(kind, text, value, t, fill, mark, item, face, action, arg, num, min, max, step, enabled, why,
                children);
    }

    /** A tooltip (kept in {@code why}) for pairs, meters and chips. */
    public El withTip(String tip) {
        return new El(kind, text, value, tone, fill, mark, item, face, action, arg, num, min, max, step, enabled, tip,
                children);
    }

    public El withAction(String a, String r) {
        return new El(kind, text, value, tone, fill, mark, item, face, a, r, num, min, max, step, enabled, why,
                children);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("k", kind);
        put(t, "t", text);
        put(t, "v", value);
        if (tone != 0) {
            t.putByte("o", (byte) tone);
        }
        if (fill != 0) {
            t.putFloat("f", fill);
        }
        if (mark != -1) {
            t.putFloat("m", mark);
        }
        put(t, "i", item);
        put(t, "fa", face);
        put(t, "a", action);
        put(t, "r", arg);
        if (num != 0) {
            t.putInt("n", num);
        }
        if (min != 0) {
            t.putInt("lo", min);
        }
        if (max != 0) {
            t.putInt("hi", max);
        }
        if (step != 1) {
            t.putInt("s", step);
        }
        if (!enabled) {
            t.putBoolean("off", true);
        }
        put(t, "w", why);
        if (!children.isEmpty()) {
            t.put("c", saveAll(children));
        }
        return t;
    }

    private static void put(CompoundTag t, String k, String v) {
        if (!v.isEmpty()) {
            t.putString(k, v);
        }
    }

    public static El load(CompoundTag t) {
        return new El(t.getString("k"), t.getString("t"), t.getString("v"), t.getByte("o"), t.getFloat("f"),
                t.contains("m") ? t.getFloat("m") : -1, t.getString("i"), t.getString("fa"), t.getString("a"),
                t.getString("r"), t.getInt("n"), t.getInt("lo"), t.getInt("hi"), t.contains("s") ? t.getInt("s") : 1,
                !t.getBoolean("off"), t.getString("w"), loadAll(t.getList("c", Tag.TAG_COMPOUND)));
    }

    public static ListTag saveAll(List<El> els) {
        ListTag l = new ListTag();
        for (El e : els) {
            l.add(e.save());
        }
        return l;
    }

    public static List<El> loadAll(ListTag l) {
        List<El> out = new ArrayList<>();
        for (Tag x : l) {
            out.add(load((CompoundTag) x));
        }
        return out;
    }
}
