package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Middle-earth map, framed as LOTR's: LOTR's map image under its parchment overlay, waypoints drawn with LOTR's
 * icons and named when zoomed in, the player's face where they stand, and a compass. Drag to pan, scroll to zoom
 * (toward the mouse); click a bright waypoint to fast travel. Dim gold waypoints can't be reached now (the tooltip
 * says why), grey ones are in regions not visited yet. M or Escape closes it.
 */
public final class MapScreen extends Screen {
    private static final ResourceLocation MAP = LotrPort.id("textures/gui/map.png");
    private static final ResourceLocation ICONS = LotrPort.id("textures/gui/map/map_screen.png");
    private static final ResourceLocation OVERLAY = LotrPort.id("textures/gui/map/map_overlay.png");
    private static final int MAP_W = 3200;
    private static final int MAP_H = 4000;
    /** Space around the map window: title above, hints below. */
    private static final int MARGIN = 24;
    private static final int FRAME = 0xFF3A2814;
    private static final int FRAME_LIGHT = 0xFF8A6A3A;
    private static final int TEXT = 0xE8D8B0;
    /** Waypoint icons in LOTR's map sheet, by state (locked, blocked, ready), and the hovered one. */
    private static final int[][] ICON_UV = { { 0, 204 }, { 0, 208 }, { 4, 208 } };
    private static final int[] HOVER_UV = { 4, 204 };
    private static final int ICON = 6;
    /** Waypoint names are drawn at this zoom or closer. */
    private static final double LABEL_ZOOM = 0.9;

    private final MapNet.MapData data;
    /** Map pixel at the window centre, and screen pixels per map pixel. */
    private double centerX;
    private double centerZ;
    private double zoom = 0.75;
    private int x0;
    private int y0;
    private int x1;
    private int y1;

    private MapScreen(MapNet.MapData data) {
        super(Component.translatableWithFallback("screen.lotr_port.map", "Middle-earth"));
        this.data = data;
        this.centerX = data.playerX();
        this.centerZ = data.playerZ();
    }

    public static void open(MapNet.MapData data) {
        Minecraft.getInstance().setScreen(new MapScreen(data));
    }

    @Override
    protected void init() {
        x0 = MARGIN / 2;
        y0 = MARGIN;
        x1 = width - MARGIN / 2;
        y1 = height - MARGIN;
        zoom = Math.max(minZoom(), zoom);
    }

    /** The smallest zoom: the whole map fits the window. */
    private double minZoom() {
        return Math.min((x1 - x0) / (double) MAP_W, (y1 - y0) / (double) MAP_H);
    }

    private double screenX(double mapX) {
        return (x0 + x1) / 2.0 + (mapX - centerX) * zoom;
    }

    private double screenY(double mapZ) {
        return (y0 + y1) / 2.0 + (mapZ - centerZ) * zoom;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF1A1208);
        // frame
        g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, FRAME);
        g.fill(x0 - 2, y0 - 2, x1 + 2, y1 + 2, FRAME_LIGHT);
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, FRAME);
        g.fill(x0, y0, x1, y1, 0xFF5A7A9A); // the sea beyond the map's edge
        g.enableScissor(x0, y0, x1, y1);
        drawMap(g);
        MapNet.Mark hovered = drawWaypoints(g, mouseX, mouseY);
        drawPlayer(g);
        g.disableScissor();
        // compass, bottom right inside the frame
        g.blit(ICONS, x1 - 36, y1 - 36, 224, 200, 32, 32, 256, 256);
        g.drawCenteredString(font, title, width / 2, (y0 - font.lineHeight) / 2, TEXT);
        Component hint = data.waitTicks() > 0
                ? Component.translatableWithFallback("screen.lotr_port.map.wait", "Fast travel in %s s",
                        (data.waitTicks() + 19) / 20).withStyle(s -> s.withColor(0xE0C040))
                : Component.translatableWithFallback("screen.lotr_port.map.hint",
                        "Drag to move, scroll to zoom, click a bright waypoint to travel");
        g.drawCenteredString(font, hint, width / 2, y1 + (MARGIN - font.lineHeight) / 2 + 1, TEXT);
        if (hovered != null) {
            tooltip(g, hovered, mouseX, mouseY);
        }
    }

    private void drawMap(GuiGraphics g) {
        double left = centerX - (x1 - x0) / 2.0 / zoom;
        double top = centerZ - (y1 - y0) / 2.0 / zoom;
        int u = (int) Math.max(0, Math.floor(left));
        int v = (int) Math.max(0, Math.floor(top));
        int u2 = (int) Math.min(MAP_W, Math.ceil(centerX + (x1 - x0) / 2.0 / zoom));
        int v2 = (int) Math.min(MAP_H, Math.ceil(centerZ + (y1 - y0) / 2.0 / zoom));
        if (u2 <= u || v2 <= v) {
            return;
        }
        int x = (int) Math.round(screenX(u));
        int y = (int) Math.round(screenY(v));
        int w = (int) Math.round(screenX(u2)) - x;
        int h = (int) Math.round(screenY(v2)) - y;
        g.blit(MAP, x, y, w, h, u, v, u2 - u, v2 - v, MAP_W, MAP_H);
        // LOTR's parchment overlay, tiled over the visible part and faint, so the map looks drawn on old paper
        RenderSystem.enableBlend();
        g.setColor(1f, 1f, 1f, 0.25f);
        for (int ty = Math.max(y, y0); ty < Math.min(y + h, y1); ty += 256) {
            for (int tx = Math.max(x, x0); tx < Math.min(x + w, x1); tx += 256) {
                int tw = Math.min(256, Math.min(x + w, x1) - tx);
                int th = Math.min(256, Math.min(y + h, y1) - ty);
                g.blit(OVERLAY, tx, ty, 0, 0, tw, th, 256, 256);
            }
        }
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private MapNet.Mark drawWaypoints(GuiGraphics g, int mouseX, int mouseY) {
        MapNet.Mark hovered = null;
        boolean inWindow = mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1;
        for (MapNet.Mark m : data.marks()) {
            int x = (int) Math.round(screenX(m.mapX()));
            int y = (int) Math.round(screenY(m.mapZ()));
            if (x < x0 - 50 || x > x1 + 50 || y < y0 - 10 || y > y1 + 10) {
                continue;
            }
            boolean over = inWindow && Math.abs(mouseX - x) <= ICON / 2 + 1 && Math.abs(mouseY - y) <= ICON / 2 + 1;
            if (over) {
                hovered = m;
            }
            int[] uv = over ? HOVER_UV : ICON_UV[Math.max(0, Math.min(2, m.state()))];
            g.blit(ICONS, x - ICON / 2, y - ICON / 2, ICON, ICON, uv[0], uv[1], 4, 4, 256, 256);
            if (zoom >= LABEL_ZOOM && m.state() != MapNet.Mark.LOCKED) {
                Component name = name(m);
                int w = font.width(name);
                g.fill(x - w / 2 - 2, y + ICON / 2 + 1, x + w / 2 + 2, y + ICON / 2 + 2 + font.lineHeight, 0x90000000);
                g.drawString(font, name, x - w / 2, y + ICON / 2 + 2, m.state() == MapNet.Mark.READY ? 0xFFF0C0
                        : 0xC0A060, false);
            }
        }
        return hovered;
    }

    private void drawPlayer(GuiGraphics g) {
        int px = (int) Math.round(screenX(data.playerX()));
        int py = (int) Math.round(screenY(data.playerZ()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.fill(px - 5, py - 5, px + 5, py + 5, 0xFF000000);
            PlayerFaceRenderer.draw(g, mc.player.getSkin(), px - 4, py - 4, 8);
        } else {
            g.fill(px - 3, py - 3, px + 3, py + 3, 0xFFD02020);
        }
    }

    private void tooltip(GuiGraphics g, MapNet.Mark m, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(m));
        lines.add(Component.translatableWithFallback("screen.lotr_port.map.distance", "%s blocks away",
                m.distance()).withStyle(s -> s.withColor(0xA0A0A0)));
        switch (m.state()) {
            case MapNet.Mark.READY -> lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel",
                    "Click to travel").withStyle(s -> s.withColor(0x40E040)));
            case MapNet.Mark.BLOCKED -> lines.add(m.problem().copy().withStyle(s -> s.withColor(0xE0C040)));
            default -> lines.add(Component.translatableWithFallback("screen.lotr_port.map.locked",
                    "Visit this region to unlock it").withStyle(s -> s.withColor(0x909090)));
        }
        g.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    private static Component name(MapNet.Mark m) {
        ResourceLocation id = m.id();
        return Component.translatableWithFallback("waypoint." + id.getNamespace() + "." + id.getPath(),
                id.getPath());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the map and its frame fill the screen
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1) {
            for (MapNet.Mark m : data.marks()) {
                if (m.state() == MapNet.Mark.READY && Math.abs(mouseX - screenX(m.mapX())) <= ICON / 2 + 1
                        && Math.abs(mouseY - screenY(m.mapZ())) <= ICON / 2 + 1) {
                    PacketDistributor.sendToServer(new MapNet.Travel(m.id()));
                    onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        centerX = Math.max(0, Math.min(MAP_W, centerX - dragX / zoom));
        centerZ = Math.max(0, Math.min(MAP_H, centerZ - dragY / zoom));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // keep the map pixel under the mouse where it is
        double mapX = centerX + (mouseX - (x0 + x1) / 2.0) / zoom;
        double mapZ = centerZ + (mouseY - (y0 + y1) / 2.0) / zoom;
        zoom = Math.max(minZoom(), Math.min(8, zoom * (scrollY > 0 ? 1.25 : 0.8)));
        centerX = Math.max(0, Math.min(MAP_W, mapX - (mouseX - (x0 + x1) / 2.0) / zoom));
        centerZ = Math.max(0, Math.min(MAP_H, mapZ - (mouseY - (y0 + y1) / 2.0) / zoom));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (LotrClient.MAP_KEY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
