package com.porcupinestudios.lotrport.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The jukebox (key J): every playlist that can play where you stand (the land's, and the road's or underground's when
 * you're there; calm, suspense and fight), and their tracks. Click a track's box to take it out of the shuffle or put
 * it back (saved in config/lotr_port_jukebox.json with titles, albums and lists, as a list of tracks to change), its
 * ▶ to play it now. The list playing now and the track playing now are marked.
 */
public class JukeboxScreen extends Screen {
    private static final int ROW = 12;
    private static final int TOP = 40;
    private record Row(String header, ResourceLocation track) {}

    private final List<Row> rows = new ArrayList<>();
    private double scroll;

    public JukeboxScreen() {
        super(Component.literal("Jukebox"));
    }

    @Override
    protected void init() {
        rebuild();
        int y = height - 26;
        addRenderableWidget(Button.builder(Component.literal("Skip"), b -> LotrMusic.skip())
                .bounds(width / 2 - 154, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> rebuild())
                .bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(width / 2 + 54, y, 100, 20).build());
    }

    private void rebuild() {
        rows.clear();
        for (Map.Entry<String, List<ResourceLocation>> e : LotrMusic.eligible(Minecraft.getInstance()).entrySet()) {
            rows.add(new Row(e.getKey(), null));
            for (ResourceLocation id : e.getValue()) {
                rows.add(new Row(null, id));
            }
        }
    }

    private int listHeight() {
        return height - TOP - 34;
    }

    private int left() {
        return width / 2 - 170;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        String region = LotrMusic.region();
        String where = region == null ? "Not in Middle-earth"
                : region + (LotrMusic.place().isEmpty() ? "" : " · " + LotrMusic.place()) + " · "
                        + LotrMusic.mood();
        g.drawCenteredString(font, where, width / 2, 20, 0xAAAAAA);
        ResourceLocation now = LotrMusic.nowPlaying();
        g.drawCenteredString(font, now == null ? "Nothing playing" : "♫ " + LotrMusic.describe(now), width / 2,
                30, 0xFFD27F);
        if (rows.isEmpty()) {
            g.drawCenteredString(font, "No playlist can play here", width / 2, TOP + 20, 0x888888);
            return;
        }
        int x = left();
        int h = listHeight();
        g.enableScissor(x - 4, TOP, x + 344, TOP + h);
        String current = LotrMusic.currentList();
        for (int i = 0; i < rows.size(); i++) {
            int y = TOP + i * ROW - (int) scroll;
            if (y < TOP - ROW || y > TOP + h) {
                continue;
            }
            Row r = rows.get(i);
            if (r.header() != null) {
                boolean playingList = r.header().equals(current);
                g.drawString(font, (playingList ? "▶ " : "") + r.header(), x, y + 2,
                        playingList ? 0x7FFF7F : 0xFFFF55);
                continue;
            }
            boolean on = LotrMusic.enabled(r.track());
            boolean hover = mouseX >= x && mouseX < x + 340 && mouseY >= y && mouseY < y + ROW;
            if (hover) {
                g.fill(x - 2, y, x + 342, y + ROW, 0x30FFFFFF);
            }
            g.drawString(font, on ? "[x]" : "[ ]", x + 8, y + 2, on ? 0x55FF55 : 0xFF5555);
            String text = LotrMusic.title(r.track());
            String album = LotrMusic.album(r.track());
            int color = r.track().equals(now) ? 0xFFD27F : on ? 0xFFFFFF : 0x777777;
            g.drawString(font, font.plainSubstrByWidth(text, 170), x + 30, y + 2, color);
            g.drawString(font, font.plainSubstrByWidth(album, 120), x + 205, y + 2, 0x888888);
            g.drawString(font, "▶", x + 332, y + 2, 0xAAAAFF);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = left();
        if (mouseY >= TOP && mouseY < TOP + listHeight() && mouseX >= x && mouseX < x + 344) {
            int i = (int) ((mouseY - TOP + scroll) / ROW);
            if (i >= 0 && i < rows.size() && rows.get(i).track() != null) {
                ResourceLocation id = rows.get(i).track();
                if (mouseX >= x + 326) {
                    LotrMusic.playNow(id);
                } else {
                    LotrMusic.setEnabled(id, !LotrMusic.enabled(id));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double dx, double dy) {
        int max = Math.max(0, rows.size() * ROW - listHeight());
        scroll = Mth.clamp(scroll - dy * ROW * 3, 0, max);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
