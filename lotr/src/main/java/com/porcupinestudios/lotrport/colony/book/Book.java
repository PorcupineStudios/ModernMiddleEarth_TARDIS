package com.porcupinestudios.lotrport.colony.book;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * One colony book screen as the server builds it (plaques, the ledgers, the Customs Counter): a title, a few header
 * chips, the left page (the overview, always shown) and the right page (the open tab), each a list of {@link El}s.
 * The client screen ({@code client/book/BookScreen}) only draws and sends back clicks; every rule stays here.
 *
 * @param kind the provider ({@link Books})
 * @param context what it is about (a colony id, colony/building ids...)
 * @param tab the open tab
 * @param message a short note to show once (the answer to the last click), "" for none
 * @param accent the book's colour: 0 brown (plaques), 1 green (Hall), 2 gold (Treasury), 3 blue (Customs)
 */
public record Book(String kind, String context, String title, String subtitle, List<El> chips, List<Tab> tabs,
        String tab, List<El> left, List<El> right, String message, int accent) {
    public record Tab(String id, String label, String icon) {}

    public Book withMessage(String m) {
        return new Book(kind, context, title, subtitle, chips, tabs, tab, left, right, m, accent);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind);
        t.putString("context", context);
        t.putString("title", title);
        t.putString("subtitle", subtitle);
        t.put("chips", El.saveAll(chips));
        ListTag tl = new ListTag();
        for (Tab x : tabs) {
            CompoundTag c = new CompoundTag();
            c.putString("id", x.id());
            c.putString("label", x.label());
            c.putString("icon", x.icon());
            tl.add(c);
        }
        t.put("tabs", tl);
        t.putString("tab", tab);
        t.put("left", El.saveAll(left));
        t.put("right", El.saveAll(right));
        t.putString("message", message);
        t.putInt("accent", accent);
        return t;
    }

    public static Book load(CompoundTag t) {
        List<Tab> tabs = new ArrayList<>();
        for (Tag x : t.getList("tabs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            tabs.add(new Tab(c.getString("id"), c.getString("label"), c.getString("icon")));
        }
        return new Book(t.getString("kind"), t.getString("context"), t.getString("title"), t.getString("subtitle"),
                El.loadAll(t.getList("chips", Tag.TAG_COMPOUND)), tabs, t.getString("tab"),
                El.loadAll(t.getList("left", Tag.TAG_COMPOUND)), El.loadAll(t.getList("right", Tag.TAG_COMPOUND)),
                t.getString("message"), t.getInt("accent"));
    }
}
