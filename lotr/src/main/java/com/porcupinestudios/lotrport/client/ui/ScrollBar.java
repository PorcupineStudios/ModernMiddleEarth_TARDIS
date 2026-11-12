package com.porcupinestudios.lotrport.client.ui;

/**
 * A scroll bar for anything taller than its box (stats, profiles, timetables, long choice lists): the track and
 * handle sprites, dragging the handle or clicking the track, and the mouse wheel. Keeps its own offset in GUI units.
 */
final class ScrollBar implements UiScreen.Draggable {
    static final int WIDTH = 8;

    private final UiScreen screen;
    /** How far the content is scrolled. */
    int offset;
    private int content;
    private int view;
    private int trackY;
    private int trackH;

    ScrollBar(UiScreen screen) {
        this.screen = screen;
    }

    /** Sets the sizes for this frame and keeps the offset in range; true if the content needs a bar. */
    boolean measure(int contentHeight, int viewHeight) {
        content = contentHeight;
        view = viewHeight;
        offset = Math.max(0, Math.min(offset, max()));
        return content > view;
    }

    private int max() {
        return Math.max(0, content - view);
    }

    /** Draws the bar at x, y down h, if the content needs one. */
    void draw(Imm ui, int x, int y, int h) {
        trackY = y;
        trackH = h;
        if (content <= view) {
            return;
        }
        ui.sprite(UiTheme.SCROLL_TRACK, x, y, WIDTH, h);
        int handle = handle();
        int hy = y + (int) ((h - handle) * (offset / (double) Math.max(1, max())));
        ui.sprite(UiTheme.SCROLL_HANDLE, x, hy, WIDTH, handle);
        ui.hit(x, y, WIDTH, h, () -> screen.dragScroll(this));
    }

    private int handle() {
        return Math.max(16, Math.min(trackH, trackH * view / Math.max(1, content)));
    }

    /** Wheel: a few lines per notch. True if it moved. */
    boolean wheel(double amount, int step) {
        int before = offset;
        offset = Math.max(0, Math.min(max(), offset - (int) Math.signum(amount) * step));
        return offset != before;
    }

    @Override
    public void dragTo(double mouseY) {
        int handle = handle();
        double f = (mouseY - trackY - handle / 2.0) / Math.max(1, trackH - handle);
        offset = (int) Math.round(Math.max(0, Math.min(1, f)) * max());
    }
}
