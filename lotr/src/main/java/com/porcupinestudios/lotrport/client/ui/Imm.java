package com.porcupinestudios.lotrport.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Immediate-mode drawing for the UI screens: each frame the panes draw buttons, boxes and rows through this, and each
 * clickable thing they draw leaves a {@link Hit}. A click is matched against this frame's hits, newest layer first
 * (popups are drawn after the page, so they win). Hover state is the mouse position this frame. No widget objects to
 * keep in step with the view: a refresh just draws the new view.
 */
public final class Imm {
    /** A clickable box and what a click does. {@code layer} 0 = page, 1 = popup. */
    public record Hit(int x, int y, int w, int h, int layer, Runnable click) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public final Font font;
    public GuiGraphics g;
    public int mouseX;
    public int mouseY;
    /** Layer being drawn: hits made now go to it, and hover only counts on the top layer under the mouse. */
    public int layer;
    /** The topmost layer the mouse is over (set by the screen before drawing: popups block hover below them). */
    public int hoverLayer;
    private final List<Hit> hits = new ArrayList<>();
    private Component tooltip;

    public Imm(Font font) {
        this.font = font;
    }

    /** Starts a frame. */
    public void begin(GuiGraphics graphics, int mx, int my, int topLayer) {
        g = graphics;
        mouseX = mx;
        mouseY = my;
        hoverLayer = topLayer;
        layer = 0;
        hits.clear();
        tooltip = null;
    }

    /** The tooltip asked for this frame, to draw last. */
    public Optional<Component> tooltip() {
        return Optional.ofNullable(tooltip);
    }

    public void tooltip(Component text) {
        tooltip = text;
    }

    /** Runs the click of the topmost hit under the mouse. False if nothing was there. */
    public boolean click(double mx, double my) {
        for (int l = 1; l >= 0; l--) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.layer == l && h.contains(mx, my)) {
                    h.click.run();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean over(int x, int y, int w, int h) {
        return layer >= hoverLayer && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void hit(int x, int y, int w, int h, Runnable click) {
        hits.add(new Hit(x, y, w, h, layer, click));
    }

    // ---------------------------------------------------------------- drawing

    public void sprite(ResourceLocation sprite, int x, int y, int w, int h) {
        g.blitSprite(sprite, x, y, w, h);
    }

    /** Text cut to {@code width} with "..." if it doesn't fit. */
    public String fit(String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String dots = "...";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(dots))) + dots;
    }

    public void text(String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public void text(Component text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    /** Text in a box: cut to fit, aligned (-1 left, 0 centre, 1 right), centred vertically. */
    public void text(String text, int x, int y, int w, int h, int align, int color) {
        String s = fit(text, w);
        int tw = font.width(s);
        int tx = align < 0 ? x : align > 0 ? x + w - tw : x + (w - tw) / 2;
        text(s, tx, y + (h - font.lineHeight) / 2 + 1, color);
    }

    /** A beige button. Disabled buttons draw greyed and show {@code why} on hover. */
    public void button(int x, int y, int w, int h, Component label, boolean enabled, Optional<Component> why,
            Runnable click) {
        boolean hover = over(x, y, w, h);
        ResourceLocation s = !enabled ? UiTheme.BUTTON_DISABLED : hover ? UiTheme.BUTTON_HOVER : UiTheme.BUTTON;
        sprite(s, x, y, w, h);
        // the sprite's lip is 2 units at the bottom: centre the label on the face
        text(label.getString(), x + 4, y, w - 8, h - 2, 0, enabled ? UiTheme.DARK_TEXT : UiTheme.DISABLED_TEXT);
        if (hover && !enabled) {
            why.ifPresent(this::tooltip);
        }
        if (enabled) {
            hit(x, y, w, h, click);
        }
    }

    public void button(int x, int y, int w, int h, Component label, Runnable click) {
        button(x, y, w, h, label, true, Optional.empty(), click);
    }

    /** A button with a drop-down arrow at its right. */
    public void dropdown(int x, int y, int w, int h, String label, Runnable click) {
        boolean hover = over(x, y, w, h);
        sprite(hover ? UiTheme.BUTTON_HOVER : UiTheme.BUTTON, x, y, w, h);
        int arrow = h - 8;
        text(label, x + 6, y, w - 14 - arrow, h - 2, -1, UiTheme.DARK_TEXT);
        sprite(UiTheme.ARROW_DOWN, x + w - arrow - 6, y + 3, arrow, arrow);
        hit(x, y, w, h, click);
    }

    /** A tick box with its label to the right; the whole line is clickable. */
    public void checkbox(int x, int y, int w, boolean checked, String label, Runnable click) {
        int s = UiTheme.CHECK_SIZE;
        sprite(checked ? UiTheme.CHECKED : UiTheme.CHECK, x, y, s, s);
        text(fit(label, w - s - 5), x + s + 5, y + 2, UiTheme.DARK_TEXT);
        hit(x, y, w, s, click);
    }

    /** A bar: {@code fill} 0-1, reddish below a third. */
    public void bar(int x, int y, int w, int h, double fill) {
        g.fill(x, y, x + w, y + h, UiTheme.TROUGH);
        int f = (int) Math.round(w * Math.max(0, Math.min(1, fill)));
        g.fill(x, y, x + f, y + h, fill < 1 / 3.0 ? UiTheme.BAR_LOW : UiTheme.BAR);
    }

    public void clip(int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    public void unclip() {
        g.disableScissor();
    }
}
