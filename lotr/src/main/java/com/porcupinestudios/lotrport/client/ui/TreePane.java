package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Page;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A family tree in a box you navigate like a map: the wheel zooms round the mouse, dragging pans, so any number of
 * generations and branches fit without scroll bars. Generations are rows, each centred on the same middle (parents
 * above, children below), laid out once per tree from the person in focus. Partners sit side by side (married: a red
 * line; were married: dashed), someone with an old and a new spouse between the two; a couple's children hang from
 * the middle of the line between them, down to a bar that splits to each child. Clicking someone who can be opened sends the tree's {@code open} action with their id.
 */
final class TreePane {
    private static final int NODE_W = 78;
    private static final int NODE_H = 22;
    private static final int GAP_X = 12;
    private static final int GAP_Y = 30;
    /** Between people who aren't a couple (siblings, cousins): wider than between partners. */
    private static final int FAMILY_GAP = 22;
    private static final float MIN_ZOOM = 0.3f;
    private static final float MAX_ZOOM = 2.5f;
    /** A press that moves less than this is a click, not a drag. */
    private static final double CLICK_SLOP = 3;
    /** The hint line at the box's foot. */
    private static final int HINT_H = 12;

    private final UiScreen screen;
    private Page.Tree tree;
    /** Node positions in tree units (top-left corners), focus at 0, 0. */
    private final Map<String, int[]> at = new LinkedHashMap<>();
    /** Each person's parents and partners (spouse and former), from the links. */
    private final Map<String, List<String>> parentsOf = new HashMap<>();
    private final Map<String, List<String>> partners = new HashMap<>();
    private float zoom = 1;
    /** Where the tree's 0, 0 is drawn, relative to the box's centre. */
    private double panX;
    private double panY;
    // the box of the last frame
    private int bx;
    private int by;
    private int bw;
    private int bh;
    private boolean pressed;
    /**
     * Whether the player has moved the view. Until then it is fitted and centred every frame, so it stays centred
     * whatever size the box turns out (window resizes, the first frames).
     */
    private boolean touched;
    private double pressX;
    private double pressY;
    private double moved;

    TreePane(UiScreen screen, Page.Tree tree) {
        this.screen = screen;
        set(tree);
    }

    void set(Page.Tree t) {
        boolean same = tree != null && tree.focus().equals(t.focus()) && tree.nodes().size() == t.nodes().size()
                && tree.links().size() == t.links().size();
        tree = t;
        if (!same) {
            layout();
            touched = false;
        }
    }

    // ---------------------------------------------------------------- layout

    private void layout() {
        at.clear();
        parentsOf.clear();
        Map<String, List<String>> children = new HashMap<>();
        partners.clear();
        for (Page.TreeLink l : tree.links()) {
            if (l.partners()) {
                partners.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l.to());
                partners.computeIfAbsent(l.to(), k -> new ArrayList<>()).add(l.from());
            } else {
                children.computeIfAbsent(l.from(), k -> new ArrayList<>()).add(l.to());
                parentsOf.computeIfAbsent(l.to(), k -> new ArrayList<>()).add(l.from());
            }
        }
        // generations by walking out from the focus: parents a row up, children a row down, partners the same row
        Map<String, Integer> gen = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        gen.put(tree.focus(), 0);
        queue.add(tree.focus());
        while (!queue.isEmpty()) {
            String id = queue.poll();
            int g = gen.get(id);
            for (String p : parentsOf.getOrDefault(id, List.of())) {
                if (gen.putIfAbsent(p, g - 1) == null) {
                    queue.add(p);
                }
            }
            for (String c : children.getOrDefault(id, List.of())) {
                if (gen.putIfAbsent(c, g + 1) == null) {
                    queue.add(c);
                }
            }
            for (String s : partners.getOrDefault(id, List.of())) {
                if (gen.putIfAbsent(s, g) == null) {
                    queue.add(s);
                }
            }
        }
        int lowest = gen.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (Page.TreeNode n : tree.nodes()) {
            gen.putIfAbsent(n.id(), lowest + 2);
        }
        Map<Integer, List<String>> rows = new java.util.TreeMap<>();
        gen.forEach((id, g) -> rows.computeIfAbsent(g, k -> new ArrayList<>()).add(id));
        Map<String, Double> order = new HashMap<>();
        for (Map.Entry<Integer, List<String>> row : rows.entrySet()) {
            List<String> ids = row.getValue();
            // order: by the place of their parents in the row above (a family's children together, in their parents'
            // order), else as found
            Map<String, Double> key = new HashMap<>();
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                key.put(id, parentsOf.getOrDefault(id, List.of()).stream().filter(order::containsKey)
                        .mapToDouble(order::get).average().orElse(1000 + i) + i * 1e-4);
            }
            // someone who married in (no parents here) sits beside their partner: the first partner's right, a
            // second partner's left, so a person between an old and a new spouse has a family on each side
            for (String id : ids) {
                if (parentsOf.getOrDefault(id, List.of()).stream().anyMatch(order::containsKey)) {
                    continue;
                }
                for (String partner : partners.getOrDefault(id, List.of())) {
                    if (!key.containsKey(partner) || key.get(partner) >= 1000
                            || partner.equals(id)) {
                        continue;
                    }
                    List<String> theirs = partners.getOrDefault(partner, List.of());
                    int k = theirs.indexOf(id);
                    key.put(id, key.get(partner) + (k % 2 == 0 ? 1e-5 : -1e-5) * (1 + k / 2));
                    break;
                }
            }
            List<String> sorted = new ArrayList<>(ids);
            sorted.sort((x, y) -> Double.compare(key.get(x), key.get(y)));
            // partners close together, families further apart
            int[] xs = new int[sorted.size()];
            for (int i = 1; i < sorted.size(); i++) {
                boolean couple = partners.getOrDefault(sorted.get(i - 1), List.of()).contains(sorted.get(i));
                xs[i] = xs[i - 1] + NODE_W + (couple ? GAP_X : FAMILY_GAP);
            }
            // every generation centred on the same middle: the small ones over and under the big ones
            int shift = -(xs[sorted.size() - 1] + NODE_W) / 2;
            for (int i = 0; i < sorted.size(); i++) {
                at.put(sorted.get(i), new int[] {xs[i] + shift, row.getKey() * (NODE_H + GAP_Y)});
                order.put(sorted.get(i), (double) i);
            }
        }
    }

    // ---------------------------------------------------------------- drawing

    void draw(Imm ui, int x, int y, int w, int h) {
        bx = x;
        by = y;
        bw = w;
        bh = h;
        if (!touched) {
            fit();
        }
        ui.sprite(UiTheme.INSET, x, y, w, h);
        ui.clip(x + 2, y + 2, w - 4, h - 4);
        GuiGraphics g = ui.g;
        g.pose().pushPose();
        g.pose().translate(x + w / 2.0 + panX * zoom, y + h / 2.0 + panY * zoom, 0);
        g.pose().scale(zoom, zoom, 1);
        // couples: a line between the two boxes, solid red when married, dashed grey when they were
        for (Page.TreeLink l : tree.links()) {
            int[] a = at.get(l.from());
            int[] b = at.get(l.to());
            if (a == null || b == null || !l.partners()) {
                continue;
            }
            int ly = a[1] + NODE_H / 2;
            int x1 = Math.min(a[0], b[0]) + NODE_W;
            int x2 = Math.max(a[0], b[0]);
            if (Page.TreeLink.SPOUSE.equals(l.kind())) {
                g.fill(x1, ly - 1, x2, ly + 1, 0xFFB0452A);
            } else {
                for (int dx = x1; dx < x2; dx += 4) {
                    g.fill(dx, ly, Math.min(dx + 2, x2), ly + 1, 0xFF8A6F52);
                }
            }
        }
        // families: the parents' line meets in the middle, drops to a bar over the children and splits to each
        Map<String, List<String>> families = new LinkedHashMap<>();
        for (Page.TreeNode n : tree.nodes()) {
            List<String> ps = parentsOf.getOrDefault(n.id(), List.of()).stream().filter(at::containsKey).sorted()
                    .toList();
            if (!ps.isEmpty() && at.containsKey(n.id())) {
                families.computeIfAbsent(String.join("+", ps), k -> new ArrayList<>()).add(n.id());
            }
        }
        Map<Integer, Integer> barsInRow = new HashMap<>();
        for (Map.Entry<String, List<String>> fam : families.entrySet()) {
            List<int[]> ps = new ArrayList<>();
            for (String p : fam.getKey().split("\\+")) {
                ps.add(at.get(p));
            }
            int ax = (int) Math.round(ps.stream().mapToDouble(p -> p[0] + NODE_W / 2.0).average().orElse(0));
            // two parents: from the middle of the line between them; one: from under their box
            int ay = ps.size() > 1 ? ps.get(0)[1] + NODE_H / 2 + 1 : ps.get(0)[1] + NODE_H;
            int childTop = at.get(fam.getValue().get(0))[1];
            // families with children in one row get their bars a little apart, so the lines don't merge
            int k = barsInRow.merge(childTop, 1, Integer::sum) - 1;
            int bar = childTop - GAP_Y / 2 + (k % 3) * 3 - 3;
            int minX = ax;
            int maxX = ax;
            for (String c : fam.getValue()) {
                int cx = at.get(c)[0] + NODE_W / 2;
                minX = Math.min(minX, cx);
                maxX = Math.max(maxX, cx);
                g.fill(cx, bar, cx + 1, at.get(c)[1], UiTheme.DARK_TEXT);
            }
            g.fill(ax, ay, ax + 1, bar + 1, UiTheme.DARK_TEXT);
            g.fill(minX, bar, maxX + 1, bar + 1, UiTheme.DARK_TEXT);
        }
        Page.TreeNode hover = null;
        double[] m = toTree(ui.mouseX, ui.mouseY);
        boolean inside = ui.over(x, y, w, h);
        for (Page.TreeNode n : tree.nodes()) {
            int[] p = at.get(n.id());
            if (p == null) {
                continue;
            }
            boolean focus = n.id().equals(tree.focus());
            boolean over = inside && m[0] >= p[0] && m[0] < p[0] + NODE_W && m[1] >= p[1] && m[1] < p[1] + NODE_H;
            if (over) {
                hover = n;
            }
            boolean left = Page.TreeNode.LEFT.equals(n.mark());
            int fill = focus ? 0xFFE6CF7E : !n.alive() ? 0xFFC9BFAF : left ? 0xFFC8D3DA : 0xFFF3E7CF;
            g.fill(p[0], p[1], p[0] + NODE_W, p[1] + NODE_H, over && n.openable() ? 0xFF4A2E17 : 0xFF8A6F52);
            g.fill(p[0] + 1, p[1] + 1, p[0] + NODE_W - 1, p[1] + NODE_H - 1, fill);
            String name = ui.fit(n.name().getString(), NODE_W - 6);
            String detail = ui.fit(n.detail().getString(), NODE_W - 6);
            g.drawString(ui.font, name, p[0] + (NODE_W - ui.font.width(name)) / 2, p[1] + 3, UiTheme.DARK_TEXT,
                    false);
            g.drawString(ui.font, detail, p[0] + (NODE_W - ui.font.width(detail)) / 2, p[1] + 12,
                    UiTheme.ON_LIGHT[1], false);
            // the badge: a cross for the dead, an arrow out for those who left the colony
            String badge = Page.TreeNode.DEAD.equals(n.mark()) ? "\u2020" : left ? "\u2192" : "";
            if (!badge.isEmpty()) {
                int bwd = ui.font.width(badge) + 3;
                g.fill(p[0] + NODE_W - bwd - 1, p[1] + 1, p[0] + NODE_W - 1, p[1] + 10, left ? 0xFF5B7A8C : 0xFF6B5B4B);
                g.drawString(ui.font, badge, p[0] + NODE_W - bwd + 1, p[1] + 2, 0xFFFFFFFF, false);
            }
        }
        g.pose().popPose();
        ui.unclip();
        // the corner hint, and whoever is under the mouse
        ui.text(Component.translatableWithFallback("ui.lotr_port.tree_hint", "Wheel: zoom   Drag: move")
                .getString(), x + 5, y + h - 11, UiTheme.ON_LIGHT[1]);
        if (hover != null) {
            ui.tooltip(Component.literal(hover.name().getString() + " - " + hover.detail().getString()));
        }
    }

    /** Zooms out (never in past 1) so the whole family fits, centred. */
    private void fit() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] p : at.values()) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            maxX = Math.max(maxX, p[0] + NODE_W);
            maxY = Math.max(maxY, p[1] + NODE_H);
        }
        if (minX > maxX) {
            return;
        }
        float fit = Math.min((bw - 16f) / (maxX - minX), (bh - 24f) / (maxY - minY));
        zoom = Math.max(MIN_ZOOM, Math.min(1, fit));
        // the middle of the family on the middle of the box, above the hint line at its foot
        panX = -(minX + maxX) / 2.0;
        panY = -(minY + maxY) / 2.0 - HINT_H / 2.0 / zoom;
    }

    /** Screen point to tree units. */
    private double[] toTree(double mx, double my) {
        return new double[] {(mx - bx - bw / 2.0) / zoom - panX, (my - by - bh / 2.0) / zoom - panY};
    }

    boolean contains(double mx, double my) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    // ---------------------------------------------------------------- input

    /** The mouse went down in the box: a drag or, if it barely moves, a click. */
    void press(double mx, double my) {
        pressed = true;
        pressX = mx;
        pressY = my;
        moved = 0;
    }

    void drag(double dx, double dy) {
        if (!pressed) {
            return;
        }
        moved += Math.abs(dx) + Math.abs(dy);
        touched = true;
        panX += dx / zoom;
        panY += dy / zoom;
    }

    boolean pressed() {
        return pressed;
    }

    /** The mouse came up: a click on someone opens their page. */
    void release(double mx, double my) {
        if (!pressed) {
            return;
        }
        pressed = false;
        if (moved > CLICK_SLOP || tree.open().isEmpty()) {
            return;
        }
        double[] m = toTree(mx, my);
        for (Page.TreeNode n : tree.nodes()) {
            int[] p = at.get(n.id());
            if (p != null && n.openable() && m[0] >= p[0] && m[0] < p[0] + NODE_W && m[1] >= p[1]
                    && m[1] < p[1] + NODE_H && !n.id().equals(tree.focus())) {
                screen.send(tree.open().get(), n.id(), 0);
                return;
            }
        }
    }

    /** Zooms round the mouse, so the point under it stays put. */
    boolean zoom(double mx, double my, double amount) {
        if (!contains(mx, my)) {
            return false;
        }
        touched = true;
        double[] before = toTree(mx, my);
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * (float) Math.pow(1.15, Math.signum(amount))));
        double[] after = toTree(mx, my);
        panX += after[0] - before[0];
        panY += after[1] - before[1];
        return true;
    }
}
