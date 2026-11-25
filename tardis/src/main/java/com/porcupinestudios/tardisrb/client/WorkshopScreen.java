package com.porcupinestudios.tardisrb.client;

import com.porcupinestudios.tardisrb.net.WorkshopNet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The repair and upgrade tree at the Repair Station (Aaron, 2026-11-03; reworked 2026-11-19). Two tabs: Repairs (a
 * node per subsystem) and Upgrades (columns by depth, lines from each node's parents). Each node shows its level /
 * max in the bottom right; hover for the next level's cost and exactly what it changes; click a bright node to buy
 * it. Drag or scroll to pan. Placeholder look until Aaron's art.
 */
public final class WorkshopScreen extends Screen {
    private static final int NODE_W = 100;
    private static final int NODE_H = 26;
    private static final int GAP_X = 116;
    private static final int GAP_Y = 30;
    private static int tab = WorkshopNet.REPAIR;

    private WorkshopNet.WorkshopView view;
    private final Map<WorkshopNet.Entry, int[]> placed = new LinkedHashMap<>();
    private double panX;
    private double panY;
    private boolean dragging;

    private WorkshopScreen(WorkshopNet.WorkshopView view) {
        super(Component.literal(view.name()));
        this.view = view;
    }

    public static void show(WorkshopNet.WorkshopView view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof WorkshopScreen open) {
            open.view = view;
            open.rebuildWidgets();
        } else {
            mc.setScreen(new WorkshopScreen(view));
        }
    }

    private int top() {
        return 60;
    }

    private int bottom() {
        return height - 28;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        tabButton(WorkshopNet.REPAIR, "tardis_rb.workshop.tab.repairs", "Repairs", cx - 100);
        tabButton(WorkshopNet.RESEARCH, "tardis_rb.workshop.tab.upgrades", "Upgrades", cx + 4);
        layout();
    }

    private void tabButton(int kind, String key, String fallback, int x) {
        Button b = addRenderableWidget(Button.builder(Component.translatableWithFallback(key, fallback), btn -> {
            tab = kind;
            panX = 0;
            panY = 0;
            rebuildWidgets();
        }).bounds(x, 34, 96, 18).build());
        b.active = tab != kind;
    }

    /** Places this tab's nodes in tree space (before panning). */
    private void layout() {
        placed.clear();
        List<WorkshopNet.Entry> shown = view.entries().stream().filter(e -> e.kind() == tab).toList();
        if (tab == WorkshopNet.REPAIR) {
            for (int i = 0; i < shown.size(); i++) {
                placed.put(shown.get(i), new int[] {(i % 3) * GAP_X, (i / 3) * GAP_Y});
            }
        } else {
            Map<ResourceLocation, WorkshopNet.Entry> byId = new HashMap<>();
            shown.forEach(e -> byId.put(e.id(), e));
            Map<ResourceLocation, Integer> depth = new HashMap<>();
            Map<Integer, Integer> used = new HashMap<>();
            for (WorkshopNet.Entry e : shown) {
                int d = depth(e.id(), byId, depth, 0);
                int row = used.merge(d, 1, Integer::sum) - 1;
                placed.put(e, new int[] {d * GAP_X, row * GAP_Y});
            }
        }
    }

    private static int depth(ResourceLocation id, Map<ResourceLocation, WorkshopNet.Entry> byId,
            Map<ResourceLocation, Integer> memo, int guard) {
        Integer known = memo.get(id);
        if (known != null) {
            return known;
        }
        WorkshopNet.Entry e = byId.get(id);
        int d = 0;
        if (e != null && guard < 64) {
            for (ResourceLocation p : e.parents()) {
                if (byId.containsKey(p)) {
                    d = Math.max(d, depth(p, byId, memo, guard + 1) + 1);
                }
            }
        }
        memo.put(id, d);
        return d;
    }

    private int originX() {
        return width / 2 - 220 + (int) panX;
    }

    private int originY() {
        return top() + 6 + (int) panY;
    }

    private WorkshopNet.Entry at(double mouseX, double mouseY) {
        if (mouseY < top() || mouseY > bottom()) {
            return null;
        }
        for (Map.Entry<WorkshopNet.Entry, int[]> p : placed.entrySet()) {
            int x = originX() + p.getValue()[0];
            int y = originY() + p.getValue()[1];
            if (mouseX >= x && mouseX < x + NODE_W && mouseY >= y && mouseY < y + NODE_H) {
                return p.getKey();
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        WorkshopNet.Entry e = at(mouseX, mouseY);
        if (e != null && button == 0 && e.state() == WorkshopNet.AVAILABLE) {
            PacketDistributor.sendToServer(new WorkshopNet.Act(e.kind(), e.id()));
            return true;
        }
        dragging = button == 0 && mouseY >= top() && mouseY <= bottom();
        return dragging;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            panX += dx;
            panY += dy;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        panY += sy * GAP_Y;
        panX += sx * GAP_X / 2.0;
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatableWithFallback("tardis_rb.workshop.status",
                "Stage %s    Hop range %s    Charge %s", view.stage(), view.range(), view.charge()), width / 2, 20,
                0xC0C0C0);
        int left = width / 2 - 230;
        int right = width / 2 + 230;
        g.fill(left, top(), right, bottom(), 0x80101018);
        g.enableScissor(left, top(), right, bottom());
        drawLinks(g);
        for (Map.Entry<WorkshopNet.Entry, int[]> p : placed.entrySet()) {
            drawNode(g, p.getKey(), originX() + p.getValue()[0], originY() + p.getValue()[1]);
        }
        g.disableScissor();
        if (placed.isEmpty()) {
            g.drawCenteredString(font, Component.translatableWithFallback("tardis_rb.workshop.empty", "Nothing here yet"),
                    width / 2, top() + 20, 0x808080);
        }
        if (!view.message().isEmpty()) {
            g.drawCenteredString(font, Component.literal(view.message()), width / 2, height - 20, 0xFFD080);
        }
        WorkshopNet.Entry hover = at(mouseX, mouseY);
        if (hover != null) {
            g.renderComponentTooltip(font, tooltip(hover), mouseX, mouseY);
        }
    }

    /** Lines between levels of one subsystem, and from research parents to their children. */
    private void drawLinks(GuiGraphics g) {
        Map<String, int[]> byKey = new HashMap<>();
        placed.forEach((e, p) -> byKey.put(e.id().toString(), p));
        for (Map.Entry<WorkshopNet.Entry, int[]> p : placed.entrySet()) {
            WorkshopNet.Entry e = p.getKey();
            List<int[]> from = new ArrayList<>();
            if (tab == WorkshopNet.RESEARCH) {
                e.parents().forEach(par -> {
                    int[] q = byKey.get(par.toString());
                    if (q != null) {
                        from.add(q);
                    }
                });
            }
            int color = e.state() == WorkshopNet.DONE ? 0xFF70D070 : 0xFF606060;
            for (int[] q : from) {
                int x1 = originX() + q[0] + NODE_W;
                int y1 = originY() + q[1] + NODE_H / 2;
                int x2 = originX() + p.getValue()[0];
                int y2 = originY() + p.getValue()[1] + NODE_H / 2;
                int mid = (x1 + x2) / 2;
                g.hLine(x1, mid, y1, color);
                g.vLine(mid, Math.min(y1, y2) - 1, Math.max(y1, y2) + 1, color);
                g.hLine(mid, x2, y2, color);
            }
        }
    }

    private void drawNode(GuiGraphics g, WorkshopNet.Entry e, int x, int y) {
        int border = switch (e.state()) {
            case WorkshopNet.DONE -> 0xFF70D070;
            case WorkshopNet.RUNNING -> 0xFF70B0FF;
            case WorkshopNet.AVAILABLE -> 0xFFFFE080;
            default -> 0xFF505050;
        };
        g.fill(x, y, x + NODE_W, y + NODE_H, border);
        g.fill(x + 1, y + 1, x + NODE_W - 1, y + NODE_H - 1, e.state() == WorkshopNet.LOCKED ? 0xFF181818 : 0xFF202830);
        int text = e.state() == WorkshopNet.LOCKED ? 0x808080 : 0xFFFFFF;
        g.drawString(font, font.plainSubstrByWidth(name(e.title()), NODE_W - 8), x + 4, y + 4, text);
        String level = e.level() + "/" + e.max();
        g.drawString(font, level, x + NODE_W - 4 - font.width(level), y + NODE_H - 11,
                e.level() >= e.max() ? 0x70D070 : 0xC0C0C0);
    }

    /** "hop_calibration" -> "Hop calibration" (lang {@code tardis_rb.node.<id>} first). */
    private static String name(String id) {
        String plain = id.isEmpty() ? id : Character.toUpperCase(id.charAt(0)) + id.substring(1).replace('_', ' ');
        return Component.translatableWithFallback("tardis_rb.node." + id, plain).getString();
    }

    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v);
    }

    private static List<Component> tooltip(WorkshopNet.Entry e) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name(e.title()) + "  (" + e.level() + "/" + e.max() + ")"));
        String state = switch (e.state()) {
            case WorkshopNet.DONE -> e.kind() == WorkshopNet.REPAIR ? "Fully repaired" : "Fully upgraded";
            case WorkshopNet.RUNNING -> "Under way";
            case WorkshopNet.AVAILABLE -> e.kind() == WorkshopNet.REPAIR ? "Click to repair" : "Click to upgrade";
            default -> "Locked";
        };
        lines.add(Component.literal(state).withColor(0xC0C0C0));
        if (e.level() < e.max()) {
            lines.add(Component.literal("Cost: " + e.cost()).withColor(0xA0A0A0));
            if (e.outcome().isEmpty()) {
                lines.add(Component.literal("Changes no numbers (it unlocks what needs it)").withColor(0xA0A0A0));
            }
            for (WorkshopNet.Change c : e.outcome()) {
                String stat = Component.translatableWithFallback("tardis_rb.stat." + c.key(),
                        name(c.key())).getString();
                lines.add(Component.literal(stat + ": " + number(c.before()) + " \u2192 " + number(c.after()))
                        .withColor(0x80E080));
            }
        }
        if (e.stage()) {
            lines.add(Component.literal("Needed for this stage").withColor(0xFFD080));
        }
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
