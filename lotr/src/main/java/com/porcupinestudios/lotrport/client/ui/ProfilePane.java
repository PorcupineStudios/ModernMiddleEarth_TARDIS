package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.ui.Action;
import com.porcupinestudios.lotrport.ui.Page;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Draws a {@link Page.Profile}: the portrait (head and shoulders of the body, when it is loaded here), the name with
 * a Rename button, the line under it, then the left and right sections, scrolling with the wheel when they are taller
 * than the page. Buttons sit at the bottom right.
 */
final class ProfilePane {
    static final int PORTRAIT = 58;
    /** How much of the body the portrait shows, from the top (head and shoulders). */
    private static final float PORTRAIT_PART = 0.62f;
    private static final int GAP = 14;

    private final UiScreen screen;
    private Page.Profile profile;
    private int scroll;
    private int bodyTop;
    private int bodyBottom;
    private int bodyHeight;

    ProfilePane(UiScreen screen, Page.Profile profile) {
        this.screen = screen;
        this.profile = profile;
    }

    void set(Page.Profile p) {
        profile = p;
    }

    Page.Profile profile() {
        return profile;
    }

    /** Where the name is drawn (the rename box goes over it): x, y, width. */
    static int[] nameBox(int x, int y, int w) {
        return new int[] {x + PORTRAIT + 10, y + 6, Math.min(170, w - PORTRAIT - 90)};
    }

    void draw(Imm ui, int x, int y, int w, int h, boolean renaming) {
        int buttonsH = profile.buttons().isEmpty() ? 0 : UiTheme.BUTTON_HEIGHT + 6;
        ui.sprite(UiTheme.INSET_LIGHT, x, y, w, h - buttonsH);
        // header: portrait, name, rename, subtitle
        int px = x + 6;
        int py = y + 6;
        ui.sprite(UiTheme.INSET_DARK, px, py, PORTRAIT, PORTRAIT);
        drawPortrait(ui, px + 3, py + 3, PORTRAIT - 6, PORTRAIT - 6);
        int[] nb = nameBox(x, y, w);
        if (!renaming) {
            ui.g.pose().pushPose();
            ui.g.pose().translate(nb[0], nb[1], 0);
            ui.g.pose().scale(1.5f, 1.5f, 1);
            ui.text(profile.name().copy().withStyle(ChatFormatting.BOLD), 0, 0, UiTheme.DARK_TEXT);
            ui.g.pose().popPose();
        }
        if (profile.rename().isPresent() && !renaming) {
            int bx = nb[0] + (int) (ui.font.width(profile.name().copy().withStyle(ChatFormatting.BOLD)) * 1.5f) + 8;
            ui.button(bx, nb[1] - 2, 56, 16, Component.translatableWithFallback("ui.lotr_port.rename", "Rename"),
                    screen::startRename);
        }
        ui.text(ui.fit(profile.subtitle().getString(), w - PORTRAIT - 20), nb[0], nb[1] + 20, UiTheme.ON_LIGHT[1]);

        // body: two columns of sections
        bodyTop = py + PORTRAIT + 6;
        bodyBottom = y + h - buttonsH - 4;
        int colW = (w - 16 - GAP) / 2;
        // the right column starts higher, under the subtitle beside the portrait
        int rightTop = nb[1] + 36;
        int lift = bodyTop - rightTop;
        int leftH = columnHeight(profile.left());
        int rightH = columnHeight(profile.right()) - lift;
        bodyHeight = Math.max(leftH, rightH);
        int room = bodyBottom - bodyTop;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, bodyHeight - room)));
        int ly = bodyTop - scroll;
        ui.clip(x, bodyTop - 2, colW + 12, room + 2);
        for (Page.Section s : profile.left()) {
            ly = Sections.draw(ui, screen, s, x + 8, ly, colW) + 8;
        }
        ui.unclip();
        ui.clip(x + 8 + colW, rightTop - 2, w - colW - 8, bodyBottom - rightTop + 2);
        int ry = rightTop - scroll;
        for (Page.Section s : profile.right()) {
            ry = Sections.draw(ui, screen, s, x + 8 + colW + GAP, ry, colW) + 8;
        }
        ui.unclip();
        if (bodyHeight > room) {
            // a thin bar to say there is more
            int track = room;
            int handle = Math.max(12, track * room / bodyHeight);
            int hy = bodyTop + (int) ((track - handle) * (scroll / (double) Math.max(1, bodyHeight - room)));
            ui.g.fill(x + w - 4, bodyTop, x + w - 2, bodyTop + track, UiTheme.TROUGH);
            ui.g.fill(x + w - 4, hy, x + w - 2, hy + handle, 0xFF8A6F52);
        }

        int bx = x + w;
        List<Action> buttons = profile.buttons();
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Action a = buttons.get(i);
            int bw = ui.font.width(a.label()) + 16;
            bx -= bw;
            ui.button(bx, y + h - UiTheme.BUTTON_HEIGHT, bw, UiTheme.BUTTON_HEIGHT, a.label(), a.enabled(), a.why(),
                    () -> screen.act(a, "", 0));
            bx -= 4;
        }
    }

    private static int columnHeight(List<Page.Section> sections) {
        int h = 0;
        for (Page.Section s : sections) {
            h += Sections.height(s) + 8;
        }
        return h;
    }

    /** Head and shoulders of the body, looking at the mouse; "Far away" when its body isn't loaded here. */
    private void drawPortrait(Imm ui, int x, int y, int w, int h) {
        Optional<LivingEntity> body = profile.portrait().flatMap(ProfilePane::find);
        if (body.isEmpty()) {
            ui.text(Component.translatableWithFallback("ui.lotr_port.far_away", "Far away").getString(), x, y, w, h,
                    0, UiTheme.LIGHT_TEXT);
            return;
        }
        LivingEntity e = body.get();
        float height = Math.max(0.5f, e.getBbHeight());
        int scale = Math.max(8, Math.round(h / (height * PORTRAIT_PART)));
        // centre the box on the middle of the shown part: height/2 + yOffset = height * (1 - part / 2)
        float yOffset = height * (0.5f - PORTRAIT_PART / 2);
        InventoryScreen.renderEntityInInventoryFollowsMouse(ui.g, x, y, x + w, y + h, scale, yOffset, ui.mouseX,
                ui.mouseY, e);
    }

    private static Optional<LivingEntity> find(UUID id) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        for (Entity e : level.entitiesForRendering()) {
            if (e.getUUID().equals(id) && e instanceof LivingEntity living) {
                return Optional.of(living);
            }
        }
        return Optional.empty();
    }

    /** Scrolls the sections; true if the mouse was over them. */
    boolean scroll(double mouseY, double amount) {
        if (mouseY < bodyTop - PORTRAIT / 2 || mouseY > bodyBottom) {
            return false;
        }
        scroll -= (int) Math.signum(amount) * 12;
        return true;
    }
}
