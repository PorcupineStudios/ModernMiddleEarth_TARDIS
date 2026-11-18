package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import com.porcupinestudios.lotrport.ui.UiNet;
import com.porcupinestudios.lotrport.ui.View;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The one screen every UI-core view opens in: title, tabs, the framed page (a table, stats, a profile or a timetable) and
 * Back/Done. It draws whatever {@link View} the server last sent; a tab click or an action asks the server, and the
 * answer replaces the view in place, keeping the player's search, ticks, sort, scroll and selection
 * ({@link TableState}). An open screen asks for a fresh copy every {@link #REFRESH_TICKS}, so numbers stay live.
 */
public class UiScreen extends Screen {
    /** Ticks between refreshes while open. */
    public static final int REFRESH_TICKS = 100;
    private static final int MAX_WIDTH = 420;
    private static final int MAX_HEIGHT = 250;

    private View view;
    private final Imm ui;
    private TablePane table;
    private StatsPane stats;
    private ProfilePane profile;
    private TimetablePane timetable;
    private BlocksPane blocks;
    private EditBox search;
    /** The name field while renaming on a profile page. */
    private EditBox renameBox;
    private Popups.Popup popup;
    private Draggable dragging;
    private String pendingTab;
    private int ticks;
    // layout
    private int fx;
    private int fy;
    private int fw;
    private int fh;
    private int titleY;
    private int tabsY;

    public UiScreen(View view) {
        super(view.title());
        this.view = view;
        this.ui = new Imm(Minecraft.getInstance().font);
    }

    // ---------------------------------------------------------------- from the server

    /** A view came: refresh the open screen if it shows the same thing, else open one if asked to. */
    public static void show(UiNet.Show show) {
        Minecraft mc = Minecraft.getInstance();
        View v = show.view();
        if (mc.screen instanceof UiScreen ui && ui.view.id().equals(v.id()) && ui.view.context().equals(v.context())) {
            ui.update(v);
        } else if (show.open()) {
            mc.setScreen(new UiScreen(v));
        }
    }

    private static Component notice;
    private static long noticeUntil;

    /** An action's answer: shown under the frame for a few seconds, or over the hotbar without a screen. */
    public static void notice(UiNet.Notice n) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof UiScreen) {
            notice = n.text();
            noticeUntil = System.currentTimeMillis() + 6000;
        } else if (mc.player != null) {
            mc.player.displayClientMessage(n.text(), true);
        }
    }

    /** The server says a view may no longer be seen: close it if it is open. */
    public static void close(UiNet.Close close) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof UiScreen ui && ui.view.id().equals(close.view())
                && ui.view.context().equals(close.context())) {
            ui.onClose();
        }
    }

    /** Asks the server to open a view (from a key, another screen or an item). */
    public static void request(View.Target target) {
        PacketDistributor.sendToServer(new UiNet.Open(target, true));
    }

    private void update(View v) {
        boolean sameTab = view.tab().equals(v.tab());
        boolean sameKind = view.page().getClass() == v.page().getClass();
        view = v;
        pendingTab = null;
        if (sameTab && sameKind) {
            if (v.page() instanceof Page.Table t && table != null) {
                table.set(t);
            } else if (v.page() instanceof Page.Stats s && stats != null) {
                stats.set(s);
            } else if (v.page() instanceof Page.Profile p && profile != null) {
                profile.set(p);
            } else if (v.page() instanceof Page.Blocks b && blocks != null) {
                blocks.set(b);
            } else if (v.page() instanceof Page.Timetable t && timetable != null) {
                // keep the cells being painted until the drag ends
                if (!timetable.painting()) {
                    timetable.set(t);
                }
            }
        } else {
            popup = null;
            rebuildWidgets();
        }
    }

    // ---------------------------------------------------------------- to the server

    /** Runs an action: asks first if it says so. */
    void act(Action action, String row, int value) {
        if (action.confirm()) {
            popup(new Popups.Confirm(action, row, value, width, height));
        } else {
            send(action.id(), row, value);
        }
    }

    void send(String action, String row, int value) {
        PacketDistributor.sendToServer(new UiNet.Act(view.target(), action, row, value));
    }

    private void openTab(String tab) {
        if (!tab.equals(view.tab())) {
            pendingTab = tab;
            PacketDistributor.sendToServer(new UiNet.Open(new View.Target(view.id(), view.context(), tab), false));
        }
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void init() {
        fw = Math.min(width - 16, MAX_WIDTH);
        fx = (width - fw) / 2;
        // tabs wrap onto rows with their whole names (squeezing them cut every name to "Ov...")
        int rows = view.tabs().isEmpty() ? 0 : tabRows().size();
        int tabsH = rows == 0 ? 0 : rows * (UiTheme.TAB_HEIGHT + 1) - 3;
        fh = Math.min(height - 30 - tabsH, MAX_HEIGHT);
        titleY = Math.max(4, (height - fh - tabsH - 16) / 2);
        tabsY = titleY + 12;
        fy = tabsY + tabsH;
        table = null;
        stats = null;
        profile = null;
        timetable = null;
        blocks = null;
        search = null;
        renameBox = null;
        String key = view.id() + "|" + view.context() + "|" + view.tab();
        if (view.page() instanceof Page.Table t) {
            TableState state = TableState.of(key);
            table = new TablePane(this, state, t);
            int[] box = TablePane.searchBox(contentX(), contentY(), contentW());
            search = new EditBox(font, box[0] + 5, box[1] + 4, box[2] - 10, box[3] - 6,
                    Component.translatableWithFallback("ui.lotr_port.search", "Search..."));
            search.setBordered(false);
            search.setTextColor(UiTheme.DARK_TEXT);
            search.setHint(Component.translatableWithFallback("ui.lotr_port.search", "Search...")
                    .withColor(UiTheme.ON_LIGHT[1]));
            search.setMaxLength(64);
            search.setValue(state.search);
            search.setResponder(s -> {
                state.search = s;
                state.scroll = 0;
                table.refilter();
            });
            addRenderableWidget(search);
        } else if (view.page() instanceof Page.Stats s) {
            stats = new StatsPane(this, s);
        } else if (view.page() instanceof Page.Profile p) {
            profile = new ProfilePane(this, p);
        } else if (view.page() instanceof Page.Timetable t) {
            timetable = new TimetablePane(this, t);
        } else if (view.page() instanceof Page.Blocks b) {
            blocks = new BlocksPane(this, b);
        }
    }

    /** Puts a name field over the profile's name; Enter sends it, Escape or a click elsewhere drops it. */
    void startRename() {
        if (profile == null || profile.profile().rename().isEmpty()) {
            return;
        }
        int[] nb = ProfilePane.nameBox(contentX(), contentY(), contentW());
        renameBox = new EditBox(font, nb[0], nb[1] - 3, nb[2], 16,
                Component.translatableWithFallback("ui.lotr_port.name", "Name"));
        renameBox.setMaxLength(32);
        renameBox.setValue(profile.profile().name().getString());
        addRenderableWidget(renameBox);
        setFocused(renameBox);
        renameBox.setFocused(true);
    }

    private void endRename(boolean send) {
        if (renameBox == null) {
            return;
        }
        String name = renameBox.getValue().strip();
        removeWidget(renameBox);
        renameBox = null;
        if (send && profile != null && !name.isEmpty()) {
            profile.profile().rename().ifPresent(action -> send(action, name, 0));
        }
    }

    private int contentX() {
        return fx + UiTheme.PAD;
    }

    private int contentY() {
        return fy + UiTheme.PAD + 2;
    }

    private int contentW() {
        return fw - 2 * UiTheme.PAD;
    }

    private int contentH() {
        // leave room for Done, which sits across the frame's bottom edge
        return fh - 2 * UiTheme.PAD - 12;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (popup != null) {
            popup.fit(width, height);
        }
        ui.begin(g, mouseX, mouseY, popup != null && popup.contains(mouseX, mouseY) ? 1 : 0);
        super.render(g, mouseX, mouseY, partialTick);
        if (popup != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            ui.layer = 1;
            popup.draw(ui, this);
            ui.layer = 0;
            g.pose().popPose();
        }
        if (notice != null && System.currentTimeMillis() < noticeUntil) {
            int w = Math.min(width - 8, font.width(notice) + 12);
            int y = Math.min(height - font.lineHeight - 6, fy + fh + UiTheme.BUTTON_HEIGHT / 2 + 4);
            g.fill(width / 2 - w / 2, y - 3, width / 2 + w / 2, y + font.lineHeight + 2, 0xC0000000);
            g.drawCenteredString(font, notice, width / 2, y, 0xFFFFE8A0);
        }
        ui.tooltip().ifPresent(t -> g.renderTooltip(font, t, mouseX, mouseY));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, view.title(), width / 2, titleY, UiTheme.TITLE);
        int[][] tabRects = tabRects();
        int selected = selectedTab();
        for (int i = 0; i < view.tabs().size(); i++) {
            if (i != selected) {
                drawTab(i, tabRects, false);
            }
        }
        ui.sprite(UiTheme.PANEL, fx, fy, fw, fh);
        if (selected >= 0) {
            drawTab(selected, tabRects, true);
        }
        if (table != null) {
            table.draw(ui, contentX(), contentY(), contentW(), contentH());
        } else if (stats != null) {
            stats.draw(ui, contentX(), contentY(), contentW(), contentH());
        } else if (profile != null) {
            profile.draw(ui, contentX(), contentY(), contentW(), contentH(), renameBox != null);
        } else if (timetable != null) {
            timetable.draw(ui, contentX(), contentY(), contentW(), contentH());
        } else if (blocks != null) {
            blocks.draw(ui, contentX(), contentY(), contentW(), contentH());
        }
        int bw = 100;
        int by = fy + fh - UiTheme.BUTTON_HEIGHT / 2 - 2;
        ui.button(width / 2 - bw / 2, by, bw, UiTheme.BUTTON_HEIGHT,
                Component.translatableWithFallback("gui.done", "Done"), this::onClose);
        view.back().ifPresent(back -> ui.button(fx + UiTheme.PAD, by, 60, UiTheme.BUTTON_HEIGHT,
                Component.translatableWithFallback("gui.back", "Back"), () -> {
                    PacketDistributor.sendToServer(new UiNet.Open(back, true));
                }));
    }

    private int selectedTab() {
        String want = pendingTab != null ? pendingTab : view.tab();
        List<View.Tab> tabs = view.tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(want)) {
                return i;
            }
        }
        return tabs.isEmpty() ? -1 : 0;
    }

    /** The tabs packed into rows that fit over the frame, each tab as wide as its name. */
    private List<List<Integer>> tabRows() {
        List<View.Tab> tabs = view.tabs();
        List<List<Integer>> rows = new java.util.ArrayList<>();
        List<Integer> row = new java.util.ArrayList<>();
        int used = 0;
        int avail = fw - 12;
        for (int i = 0; i < tabs.size(); i++) {
            int w = tabWidth(tabs.get(i));
            if (!row.isEmpty() && used + w > avail) {
                rows.add(row);
                row = new java.util.ArrayList<>();
                used = 0;
            }
            row.add(i);
            used += w + 3;
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    private int tabWidth(View.Tab tab) {
        return Math.max(40, font.width(tab.label()) + 14);
    }

    /** Each tab's x, y and width: rows stretched to the frame's width, the last row lying on the frame. */
    private int[][] tabRects() {
        List<List<Integer>> rows = tabRows();
        int[][] out = new int[view.tabs().size()][];
        for (int r = 0; r < rows.size(); r++) {
            List<Integer> row = rows.get(r);
            int natural = 0;
            for (int i : row) {
                natural += tabWidth(view.tabs().get(i));
            }
            int avail = fw - 12 - (row.size() - 1) * 3;
            // full rows stretch to the frame's width; a short last row keeps its tabs' own widths
            double stretch = rows.size() > 1 && natural < avail ? avail / (double) natural : 1;
            int x = fx + 6;
            int y = tabsY + r * (UiTheme.TAB_HEIGHT + 1);
            for (int k = 0; k < row.size(); k++) {
                int i = row.get(k);
                int w = k == row.size() - 1 && stretch > 1 ? fx + fw - 6 - x
                        : (int) Math.round(tabWidth(view.tabs().get(i)) * stretch);
                out[i] = new int[] {x, y, w};
                x += w + 3;
            }
        }
        return out;
    }

    private void drawTab(int i, int[][] rects, boolean selected) {
        View.Tab tab = view.tabs().get(i);
        int x = rects[i][0];
        int w = rects[i][2];
        int y = selected ? rects[i][1] - 1 : rects[i][1];
        int h = UiTheme.TAB_HEIGHT + (selected ? 1 : 0);
        boolean hover = !selected && ui.over(x, y, w, h);
        ui.sprite(selected ? UiTheme.TAB_SELECTED : hover ? UiTheme.TAB_HOVER : UiTheme.TAB, x, y, w, h);
        ui.text(tab.label().getString(), x + 4, y + 1, w - 8, UiTheme.TAB_HEIGHT - 2, 0,
                selected ? UiTheme.DARK_TEXT : UiTheme.LIGHT_TEXT);
        if (!selected) {
            ui.hit(x, y, w, h, () -> openTab(tab.id()));
        }
    }

    // ---------------------------------------------------------------- input

    void popup(Popups.Popup p) {
        popup = p;
    }

    /** Something the mouse drags while the button is held (a scroll bar's handle). */
    interface Draggable {
        void dragTo(double mouseY);
    }

    void dragScroll(Draggable pane) {
        dragging = pane;
        pane.dragTo(ui.mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (popup != null) {
            if (popup.contains(mouseX, mouseY)) {
                ui.click(mouseX, mouseY);
            } else {
                popup = null;
            }
            return true;
        }
        // the text field first, so clicking into it focuses it; then everything drawn
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        endRename(false);
        if (search != null) {
            search.setFocused(false);
        }
        if (profile != null && profile.tree() != null && profile.tree().contains(mouseX, mouseY)) {
            profile.tree().press(mouseX, mouseY);
            return true;
        }
        if (blocks != null && blocks.press(mouseX, mouseY)) {
            return true;
        }
        return ui.click(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) {
            dragging.dragTo(mouseY);
            return true;
        }
        if (timetable != null && timetable.painting()) {
            timetable.paintAt(mouseX, mouseY);
            return true;
        }
        if (blocks != null && blocks.drag(mouseX, mouseY)) {
            return true;
        }
        if (profile != null && profile.tree() != null && profile.tree().pressed()) {
            profile.tree().drag(dx, dy);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        if (blocks != null && blocks.release(ui, mouseX, mouseY)) {
            return true;
        }
        if (timetable != null) {
            timetable.stopPainting();
        }
        if (profile != null && profile.tree() != null) {
            profile.tree().release(mouseX, mouseY);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup instanceof Popups.ChoiceMenu menu && menu.contains(mouseX, mouseY) && menu.scroll(scrollY)) {
            return true;
        }
        if (popup == null && table != null && table.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (popup == null && profile != null && profile.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (popup == null && stats != null && stats.scroll(mouseY, scrollY)) {
            return true;
        }
        if (popup == null && blocks != null && blocks.scroll(mouseX, mouseY, scrollY)) {
            return true;
        }
        if (popup == null && timetable != null && timetable.scroll(mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && popup != null) {
            popup = null;
            return true;
        }
        if (renameBox != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                endRename(true);
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                endRename(false);
                return true;
            }
            // typing goes to the field, not to the inventory key that would close the screen
            return renameBox.keyPressed(key, scan, modifiers) || renameBox.canConsumeInput() || super.keyPressed(key,
                    scan, modifiers);
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        if (++ticks % REFRESH_TICKS == 0 && pendingTab == null) {
            PacketDistributor.sendToServer(new UiNet.Open(view.target(), false));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The view on screen (for tests and other screens). */
    public View view() {
        return view;
    }

    Optional<TablePane> table() {
        return Optional.ofNullable(table);
    }
}
