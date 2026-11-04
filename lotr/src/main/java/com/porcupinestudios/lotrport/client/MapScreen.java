package com.porcupinestudios.lotrport.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import com.porcupinestudios.lotrport.world.map.MiddleEarthMap;
import com.porcupinestudios.lotrport.world.map.Roads;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * LOTR's Middle-earth map screen, rebuilt to work the same way: fullscreen by default (a widget switches to a framed
 * window with a 30 px border), zoom in powers of two from 1/8 to 16 map pixels per screen pixel, eased over 6 ticks,
 * around the centre; drag or WASD/arrows to pan; LOTR's region and sea labels, fading in and out over their zoom
 * range; the parchment overlay stretched over the window at 20%; the compass in the bottom left; widgets for
 * waypoints, zoom, fullscreen, sepia and labels. Hovering the map shows the biome and the block x, z under the
 * cursor; an operator or creative player in Middle-earth presses the Map Teleport key (T) to go there. Click a
 * waypoint to select it, then press Fast Travel (F) to travel.
 */
public final class MapScreen extends Screen {
    private static final ResourceLocation ICONS = LotrPort.id("textures/gui/map/map_screen.png");
    private static final ResourceLocation OVERLAY = LotrPort.id("textures/gui/map/map_overlay.png");
    private static final ResourceLocation LABELS = LotrPort.id("map/labels.json");
    private static final int BORDER = 30;
    private static final int BORDER_COLOR = 0xFFA21100;
    private static final int MIN_ZOOM = -3;
    private static final int MAX_ZOOM = 4;
    private static final int ZOOM_TICKS = 6;
    /** Waypoint icons in LOTR's map sheet, by state (locked, blocked, ready), and the selected or hovered one. */
    private static final int[][] ICON_UV = { { 0, 204 }, { 0, 208 }, { 4, 208 } };
    private static final int[] HOVER_UV = { 4, 204 };
    private static final int ICON = 6;
    private static final int SELECT_RANGE = 5;
    private static final int ROAD_COLOR = 0x000000;
    /** Waypoint names show from this zoom power in, at this text size. */
    private static final float NAME_ZOOM = 2;
    private static final float NAME_SCALE = 0.5f;

    /** Kept between openings, like LOTR's. */
    private static int zoomPower;
    private static boolean fullscreen;
    private static boolean sepia;
    private static boolean showLabels = true;
    private static boolean showWaypoints = true;
    private static List<Label> labels;

    private final MapNet.MapData data;
    private final MiddleEarthMap map = MiddleEarthMap.get();
    private final List<Widget> widgets = new ArrayList<>();
    private float posX;
    private float posZ;
    private float prevPosX;
    private float prevPosZ;
    private int prevZoomPower = zoomPower;
    private int zoomTicks;
    private float zoomExp;
    private float zoomScale;
    private int x0;
    private int y0;
    private int x1;
    private int y1;
    private boolean dragging;
    private MapNet.Mark selected;

    /** One of LOTR's map labels: lang key or biome, map pixel, size, angle and the zoom powers it shows between. */
    record Label(Component name, float x, float y, float scale, float angle, float minZoom, float maxZoom) {}

    /** A small button in the map's top corners, drawn from LOTR's map sheet (x < 0 counts from the right). */
    private record Widget(String name, int x, int y, int u, int v) {}

    private MapScreen(MapNet.MapData data) {
        super(Component.translatableWithFallback("screen.lotr_port.map.title", "Middle-earth Map"));
        this.data = data;
        this.posX = prevPosX = data.playerX();
        this.posZ = prevPosZ = data.playerZ();
    }

    public static void open(MapNet.MapData data) {
        Minecraft.getInstance().setScreen(new MapScreen(data));
    }

    @Override
    protected void init() {
        setupDimensions();
        widgets.clear();
        widgets.add(new Widget("waypoints", -58, 6, 80, 0));
        widgets.add(new Widget("labels", -72, 6, 70, 0));
        widgets.add(new Widget("zoom_in", 6, 6, 30, 0));
        widgets.add(new Widget("zoom_out", 6, 20, 40, 0));
        widgets.add(new Widget("fullscreen", 20, 6, 50, 0));
        widgets.add(new Widget("sepia", 34, 6, 60, 0));
        if (labels == null) {
            labels = loadLabels();
        }
    }

    private void setupDimensions() {
        int b = fullscreen ? 0 : BORDER;
        x0 = b;
        y0 = b;
        x1 = width - b;
        y1 = height - b;
    }

    // ------------------------------------------------------------------ coordinates

    private void updateZoom(float partialTick) {
        zoomExp = zoomPower;
        if (zoomTicks > 0) {
            float t = (zoomTicks - partialTick) / ZOOM_TICKS;
            zoomExp = prevZoomPower + (zoomPower - prevZoomPower) * (1 - t);
        }
        zoomScale = (float) Math.pow(2, zoomExp);
    }

    private float viewX(float partialTick) {
        return Mth.lerp(partialTick, prevPosX, posX);
    }

    private float viewZ(float partialTick) {
        return Mth.lerp(partialTick, prevPosZ, posZ);
    }

    private float cx() {
        return (x0 + x1) / 2f;
    }

    private float cy() {
        return (y0 + y1) / 2f;
    }

    private boolean inMap(double mx, double my) {
        return mx >= x0 && mx < x1 && my >= y0 && my < y1;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        updateZoom(partialTick);
        float vx = viewX(partialTick);
        float vz = viewZ(partialTick);
        g.fill(0, 0, width, height, 0xFF000000);
        if (!fullscreen) {
            // LOTR's framed map: the parchment overlay over the whole screen, tinted brown, and a black-red-black frame
            RenderSystem.enableBlend();
            g.setColor(0.65f, 0.5f, 0.35f, 1f);
            quad(g, OVERLAY, 0, 0, width, height, 0, 0, 1, 1, 1f);
            g.setColor(1f, 1f, 1f, 1f);
            g.fill(x0 - 4, y0 - 4, x1 + 4, y1 + 4, 0xFF000000);
            g.fill(x0 - 3, y0 - 3, x1 + 3, y1 + 3, BORDER_COLOR);
            g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
        }
        g.enableScissor(x0, y0, x1, y1);
        drawMap(g, vx, vz);
        drawRoads(g, vx, vz);
        if (showLabels) {
            drawLabels(g, vx, vz);
        }
        MapNet.Mark hovered = showWaypoints ? drawWaypoints(g, vx, vz, mouseX, mouseY) : null;
        drawPlayer(g, vx, vz);
        // compass, bottom left
        RenderSystem.enableBlend();
        g.blit(ICONS, x0 + 12, y1 - 44, 224, 200, 32, 32, 256, 256);
        g.disableScissor();
        Widget overWidget = drawWidgets(g, mouseX, mouseY);
        if (!fullscreen) {
            g.drawCenteredString(font, title, width / 2, (y0 - font.lineHeight) / 2, 0xFFFFFF);
        }
        List<Component> lines = new ArrayList<>();
        Component tp = null;
        if (overWidget == null && hovered == null && inMap(mouseX, mouseY) && !dragging) {
            tp = hoverLines(lines, vx, vz, mouseX, mouseY);
        }
        selectedLines(lines);
        drawSubtitles(g, lines);
        if (tp != null) {
            // LOTR puts the teleport hint at the bottom right, under the map
            int ty = fullscreen ? y1 - 6 - font.lineHeight : y1 + (BORDER - font.lineHeight) / 2 + 1;
            g.drawString(font, tp, x1 - font.width(tp) - (fullscreen ? 6 : 0), ty, 0xFFFFFF);
        }
        if (overWidget != null) {
            g.renderTooltip(font, Component.translatableWithFallback("screen.lotr_port.map.widget." + overWidget.name,
                    overWidget.name.replace('_', ' ')), mouseX, mouseY);
        } else if (hovered != null) {
            tooltip(g, hovered, mouseX, mouseY);
        }
    }

    /** The map image, the ocean around it, and the overlay. Float positions, so panning is smooth. */
    private void drawMap(GuiGraphics g, float vx, float vz) {
        g.fill(x0, y0, x1, y1, MapTextures.ocean(sepia));
        int w = MapTextures.width();
        int h = MapTextures.height();
        float halfW = (x1 - x0) / 2f / zoomScale;
        float halfH = (y1 - y0) / 2f / zoomScale;
        float u0 = Math.max(0, vx - halfW);
        float v0 = Math.max(0, vz - halfH);
        float u1 = Math.min(w, vx + halfW);
        float v1 = Math.min(h, vz + halfH);
        if (u1 > u0 && v1 > v0) {
            double screenPerTexel = zoomScale * Minecraft.getInstance().getWindow().getGuiScale();
            int level = screenPerTexel >= 1 ? 0 : Math.min(MapTextures.LEVELS - 1,
                    (int) Math.floor(Math.log(1 / screenPerTexel) / Math.log(2)));
            ResourceLocation tex = MapTextures.get(level, sepia);
            Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(screenPerTexel < 1, false);
            quad(g, tex, cx() + (u0 - vx) * zoomScale, cy() + (v0 - vz) * zoomScale, cx() + (u1 - vx) * zoomScale,
                    cy() + (v1 - vz) * zoomScale, u0 / w, v0 / h, u1 / w, v1 / h, 1f);
        }
        // LOTR's parchment overlay, once over the whole window at 20%
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        quad(g, OVERLAY, x0, y0, x1, y1, 0, 0, 1, 1, 0.2f);
        RenderSystem.disableBlend();
    }

    private static void quad(GuiGraphics g, ResourceLocation tex, float qx0, float qy0, float qx1, float qy1,
            float u0, float v0, float u1, float v1, float alpha) {
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        b.addVertex(m, qx0, qy0, 0).setUv(u0, v0).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, qx0, qy1, 0).setUv(u0, v1).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, qx1, qy1, 0).setUv(u1, v1).setColor(1f, 1f, 1f, alpha);
        b.addVertex(m, qx1, qy0, 0).setUv(u1, v0).setColor(1f, 1f, 1f, alpha);
        BufferUploader.drawWithShader(b.buildOrThrow());
    }

    /**
     * LOTR's roads as dotted lines: small dark squares along each road, a dot every few screen pixels at any zoom,
     * fading out when zoomed far out.
     */
    private void drawRoads(GuiGraphics g, float vx, float vz) {
        float alpha = Math.min(1, Math.max(0, (zoomExp + 3.3f) / 2.2f));
        if (alpha <= 0.05f) {
            return;
        }
        int color = Math.round(alpha * 255) << 24 | ROAD_COLOR;
        float bpp = map.blocksPerPixel();
        float ox = (float) map.pixelX(0);
        float oz = (float) map.pixelZ(0);
        float spacing = 5f / zoomScale; // map pixels between dots (5 screen pixels, as LOTR's dotted roads)
                float left = vx - (x1 - x0) / 2f / zoomScale;
        float right = vx + (x1 - x0) / 2f / zoomScale;
        float top = vz - (y1 - y0) / 2f / zoomScale;
        float bottom = vz + (y1 - y0) / 2f / zoomScale;
        // walk each road as one line, so dots stay evenly spaced across its many short segments
        float carry = 0;
        float lastX = Float.NaN;
        float lastZ = Float.NaN;
        for (Roads.Segment seg : Roads.get().segments()) {
            float ax = (float) seg.x1() / bpp + ox;
            float az = (float) seg.z1() / bpp + oz;
            float bx = (float) seg.x2() / bpp + ox;
            float bz = (float) seg.z2() / bpp + oz;
            if (ax != lastX || az != lastZ) {
                carry = 0; // a new road
            }
            lastX = bx;
            lastZ = bz;
            float len = (float) Math.hypot(bx - ax, bz - az);
            boolean visible = !(Math.max(ax, bx) < left || Math.min(ax, bx) > right || Math.max(az, bz) < top
                    || Math.min(az, bz) > bottom);
            float d = carry;
            for (; d < len; d += spacing) {
                if (!visible) {
                    continue;
                }
                float t = d / len;
                int px = (int) Math.floor(cx() + (ax + (bx - ax) * t - vx) * zoomScale);
                int py = (int) Math.floor(cy() + (az + (bz - az) * t - vz) * zoomScale);
                g.fill(px, py, px + 1, py + 1, color);
            }
            carry = d - len;
        }
    }

    /** LOTR's labels: scaled with the zoom, turned by their angle, faded at the ends of their zoom range. */
    private void drawLabels(GuiGraphics g, float vx, float vz) {
        for (Label l : labels) {
            if (zoomExp < l.minZoom() || zoomExp > l.maxZoom()) {
                continue;
            }
            float fade = Math.min(1, Math.min(zoomExp - l.minZoom(), l.maxZoom() - zoomExp) / 0.5f);
            int alpha = Math.round(255 * 0.7f * Math.min(1, Math.max(0, fade)));
            if (alpha < 8) {
                continue;
            }
            float sx = cx() + (l.x() - vx) * zoomScale;
            float sy = cy() + (l.y() - vz) * zoomScale;
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().scale(zoomScale * l.scale(), zoomScale * l.scale(), 1);
            g.pose().mulPose(Axis.ZP.rotationDegrees(l.angle()));
            int w = font.width(l.name());
            g.drawString(font, l.name(), -w / 2, -font.lineHeight / 2, alpha << 24 | 0xFFFFFF, false);
            g.pose().popPose();
        }
    }

    private MapNet.Mark drawWaypoints(GuiGraphics g, float vx, float vz, int mouseX, int mouseY) {
        MapNet.Mark hovered = null;
        boolean inWindow = inMap(mouseX, mouseY);
        for (MapNet.Mark m : data.marks()) {
            int x = Math.round(cx() + (m.mapX() - vx) * zoomScale);
            int y = Math.round(cy() + (m.mapZ() - vz) * zoomScale);
            if (x < x0 - 50 || x > x1 + 50 || y < y0 - 10 || y > y1 + 10) {
                continue;
            }
            boolean over = inWindow && Math.abs(mouseX - x) <= SELECT_RANGE && Math.abs(mouseY - y) <= SELECT_RANGE;
            if (over && hovered == null) {
                hovered = m;
            }
            boolean lit = over || selected != null && selected.id().equals(m.id());
            int[] uv = lit ? HOVER_UV : ICON_UV[Math.max(0, Math.min(2, m.state()))];
            RenderSystem.enableBlend();
            g.blit(ICONS, x - ICON / 2, y - ICON / 2, ICON, ICON, uv[0], uv[1], 4, 4, 256, 256);
            // names only when zoomed in close, in small text under the dot
            if (zoomExp >= NAME_ZOOM) {
                Component name = name(m);
                g.pose().pushPose();
                g.pose().translate(x, y + ICON / 2f + 1, 0);
                g.pose().scale(NAME_SCALE, NAME_SCALE, 1);
                g.drawString(font, name, -font.width(name) / 2, 0, 0xFFFFFF, true);
                g.pose().popPose();
            }
        }
        return hovered;
    }

    private void drawPlayer(GuiGraphics g, float vx, float vz) {
        int px = Math.round(cx() + (data.playerX() - vx) * zoomScale);
        int py = Math.round(cy() + (data.playerZ() - vz) * zoomScale);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            g.fill(px - 5, py - 5, px + 5, py + 5, 0xFF000000);
            PlayerFaceRenderer.draw(g, mc.player.getSkin(), px - 4, py - 4, 8);
        }
    }

    private Widget drawWidgets(GuiGraphics g, int mouseX, int mouseY) {
        Widget over = null;
        RenderSystem.enableBlend();
        for (Widget w : widgets) {
            int x = widgetX(w);
            int y = y0 + w.y();
            boolean off = switch (w.name()) {
                case "waypoints" -> !showWaypoints;
                case "labels" -> !showLabels;
                case "zoom_in" -> zoomPower >= MAX_ZOOM;
                case "zoom_out" -> zoomPower <= MIN_ZOOM;
                case "fullscreen" -> !fullscreen;
                case "sepia" -> !sepia;
                default -> false;
            };
            g.blit(ICONS, x, y, w.u(), w.v() + (off ? 10 : 0), 10, 10, 256, 256);
            if (mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10) {
                over = w;
            }
        }
        return over;
    }

    private int widgetX(Widget w) {
        return w.x() < 0 ? x1 + w.x() : x0 + w.x();
    }

    /** Biome, block coordinates and (for those allowed) the teleport hint, for the map pixel under the cursor. */
    private Component hoverLines(List<Component> lines, float vx, float vz, int mouseX, int mouseY) {
        float mapX = vx + (mouseX - cx()) / zoomScale;
        float mapZ = vz + (mouseY - cy()) / zoomScale;
        int bx = (int) Math.round((mapX - map.pixelX(0)) * map.blocksPerPixel());
        int bz = (int) Math.round((mapZ - map.pixelZ(0)) * map.blocksPerPixel());
        int px = (int) Math.floor(mapX);
        int pz = (int) Math.floor(mapZ);
        ResourceLocation id = ResourceLocation.parse(map.biomes().get(map.pixelIndex(px, pz)).id());
        lines.add(Component.translatable("biome." + id.getNamespace() + "." + id.getPath()));
        lines.add(Component.translatableWithFallback("screen.lotr_port.map.coords", "x: %s, z: %s", bx, bz));
        return data.canTeleport() ? Component.translatableWithFallback("screen.lotr_port.map.tp", "Press '%s' to /tp",
                LotrClient.MAP_TELEPORT_KEY.getTranslatedKeyMessage()) : null;
    }

    private void selectedLines(List<Component> lines) {
        if (selected == null || selected.state() == MapNet.Mark.LOCKED) {
            return;
        }
        if (data.waitTicks() > 0) {
            lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel_wait",
                    "You can fast travel to the selected waypoint in: %s", (data.waitTicks() + 19) / 20 + "s"));
        } else if (selected.state() == MapNet.Mark.READY) {
            lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel_prompt",
                    "Press '%s' to fast travel to the selected waypoint",
                    LotrClient.FAST_TRAVEL_KEY.getTranslatedKeyMessage()));
        } else {
            lines.add(selected.problem());
        }
    }

    /** Text lines at the bottom: inside the window when fullscreen (on a dark band), in the border otherwise. */
    private void drawSubtitles(GuiGraphics g, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        int lh = font.lineHeight + 1;
        int top = fullscreen ? y1 - 6 - lines.size() * lh : y1 + 5;
        if (fullscreen) {
            int w = 0;
            for (Component c : lines) {
                w = Math.max(w, font.width(c));
            }
            g.fill(width / 2 - w / 2 - 4, top - 3, width / 2 + w / 2 + 4, top + lines.size() * lh + 2, 0xA0000000);
        }
        for (int i = 0; i < lines.size(); i++) {
            g.drawCenteredString(font, lines.get(i), width / 2, top + i * lh, 0xFFFFFF);
        }
    }

    private void tooltip(GuiGraphics g, MapNet.Mark m, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        lines.add(name(m));
        lines.add(Component.translatableWithFallback("screen.lotr_port.map.distance", "%s blocks away",
                m.distance()).withStyle(s -> s.withColor(0xA0A0A0)));
        switch (m.state()) {
            case MapNet.Mark.READY -> lines.add(Component.translatableWithFallback("screen.lotr_port.map.select",
                    "Click to select").withStyle(s -> s.withColor(0x40E040)));
            case MapNet.Mark.BLOCKED -> lines.add(m.problem().copy().withStyle(s -> s.withColor(0xE0C040)));
            default -> lines.add(Component.translatableWithFallback("screen.lotr_port.map.locked_region",
                    "Unlock this waypoint by visiting its region").withStyle(s -> s.withColor(0x909090)));
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
        // the map fills the screen
    }

    // ------------------------------------------------------------------ input

    @Override
    public void tick() {
        prevPosX = posX;
        prevPosZ = posZ;
        if (zoomTicks > 0) {
            zoomTicks--;
        }
        long win = Minecraft.getInstance().getWindow().getWindow();
        float step = 12f / (float) Math.pow(2, zoomPower);
        float dx = 0;
        float dz = 0;
        if (key(win, GLFW.GLFW_KEY_A) || key(win, GLFW.GLFW_KEY_LEFT)) {
            dx -= step;
        }
        if (key(win, GLFW.GLFW_KEY_D) || key(win, GLFW.GLFW_KEY_RIGHT)) {
            dx += step;
        }
        if (key(win, GLFW.GLFW_KEY_W) || key(win, GLFW.GLFW_KEY_UP)) {
            dz -= step;
        }
        if (key(win, GLFW.GLFW_KEY_S) || key(win, GLFW.GLFW_KEY_DOWN)) {
            dz += step;
        }
        move(dx, dz);
        prevPosX = Mth.clamp(prevPosX, 0, MapTextures.width());
        prevPosZ = Mth.clamp(prevPosZ, 0, MapTextures.height());
    }

    private static boolean key(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private void move(float dx, float dz) {
        posX = Mth.clamp(posX + dx, 0, MapTextures.width());
        posZ = Mth.clamp(posZ + dz, 0, MapTextures.height());
    }

    private void zoom(int by) {
        int next = Mth.clamp(zoomPower + by, MIN_ZOOM, MAX_ZOOM);
        if (next != zoomPower) {
            updateZoom(0);
            prevZoomPower = Math.round(zoomExp);
            zoomPower = next;
            zoomTicks = ZOOM_TICKS;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Widget w : widgets) {
                int x = widgetX(w);
                int y = y0 + w.y();
                if (mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10) {
                    widget(w);
                    return true;
                }
            }
            if (inMap(mouseX, mouseY)) {
                MapNet.Mark hit = null;
                if (showWaypoints) {
                    updateZoom(0);
                    for (MapNet.Mark m : data.marks()) {
                        if (Math.abs(mouseX - (cx() + (m.mapX() - posX) * zoomScale)) <= SELECT_RANGE
                                && Math.abs(mouseY - (cy() + (m.mapZ() - posZ) * zoomScale)) <= SELECT_RANGE) {
                            hit = m;
                            break;
                        }
                    }
                }
                selected = hit;
                dragging = hit == null;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void widget(Widget w) {
        switch (w.name()) {
            case "waypoints" -> showWaypoints = !showWaypoints;
            case "labels" -> showLabels = !showLabels;
            case "zoom_in" -> zoom(1);
            case "zoom_out" -> zoom(-1);
            case "fullscreen" -> {
                fullscreen = !fullscreen;
                setupDimensions();
            }
            case "sepia" -> sepia = !sepia;
            default -> { }
        }
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance
                .forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            updateZoom(0);
            move((float) (-dragX / zoomScale), (float) (-dragY / zoomScale));
            prevPosX = posX;
            prevPosZ = posZ;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            zoom(scrollY > 0 ? 1 : -1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * width / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * height / mc.getWindow().getScreenHeight();
        if (LotrClient.MAP_TELEPORT_KEY.matches(keyCode, scanCode) && data.canTeleport() && inMap(mx, my)) {
            updateZoom(0);
            float mapX = posX + (float) (mx - cx()) / zoomScale;
            float mapZ = posZ + (float) (my - cy()) / zoomScale;
            int bx = (int) Math.round((mapX - map.pixelX(0)) * map.blocksPerPixel());
            int bz = (int) Math.round((mapZ - map.pixelZ(0)) * map.blocksPerPixel());
            PacketDistributor.sendToServer(new MapNet.Teleport(bx, bz));
            onClose();
            return true;
        }
        if (LotrClient.FAST_TRAVEL_KEY.matches(keyCode, scanCode) && selected != null
                && selected.state() == MapNet.Mark.READY && data.waitTicks() <= 0) {
            PacketDistributor.sendToServer(new MapNet.Travel(selected.id()));
            onClose();
            return true;
        }
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

    // ------------------------------------------------------------------ labels

    private static List<Label> loadLabels() {
        List<Label> out = new ArrayList<>();
        try (Reader r = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(LABELS),
                StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("labels")) {
                JsonObject o = e.getAsJsonObject();
                Component name;
                if (o.has("biome")) {
                    ResourceLocation b = ResourceLocation.parse(o.get("biome").getAsString());
                    name = Component.translatable("biome." + b.getNamespace() + "." + b.getPath());
                } else {
                    String id = o.get("id").getAsString();
                    name = Component.translatableWithFallback("map_label.lotr_port." + id, id);
                }
                out.add(new Label(name, o.get("x").getAsFloat(), o.get("y").getAsFloat(),
                        o.get("scale").getAsFloat(), o.get("angle").getAsFloat(), o.get("min_zoom").getAsFloat(),
                        o.get("max_zoom").getAsFloat()));
            }
        } catch (Exception e) {
            LotrPort.LOG.warn("no map labels: {}", e.toString());
        }
        return out;
    }
}
