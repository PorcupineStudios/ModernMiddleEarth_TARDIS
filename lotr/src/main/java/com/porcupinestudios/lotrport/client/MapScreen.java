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
 * waypoints, zoom, fullscreen, sepia and labels. Drawn the way LOTR 1.7.10's LOTRGuiMap draws it: the full map image
 * with nearest filtering, the red-to-black frame, 1 px dotted roads, 4 px waypoint dots with their names above them
 * from zoom power -1 in, labels brightest mid-range, the player's head clamped inside the window, and LOTR's
 * dark red-edged waypoint boxes and subtitle layout. Hovering the map shows the biome and the block x, z under the
 * cursor; an operator or creative player in Middle-earth presses the Map Teleport key (T) to go there. Click a
 * waypoint to select it, then press Fast Travel (F) to travel.
 */
public final class MapScreen extends Screen {
    private static final ResourceLocation ICONS = LotrPort.id("textures/gui/map/map_screen.png");
    private static final ResourceLocation OVERLAY = LotrPort.id("textures/gui/map/map_overlay.png");
    private static final ResourceLocation LABELS = LotrPort.id("map/labels.json");
    private static final int BORDER = 30;
    private static final int BORDER_COLOR = 0xFFA21100;
    /** Width of LOTR's red-to-black frame round the map. */
    private static final int FRAME = 4;
    private static final int MIN_ZOOM = -3;
    private static final int MAX_ZOOM = 4;
    /** A zoom step takes LOTR's 6 ticks, timed by the clock so it is smooth at any frame rate. */
    private static final long ZOOM_NANOS = 300_000_000L;
    /** Waypoint icons in LOTR's map sheet, by state (locked, blocked, ready); 4 px, drawn at 4 px like LOTR's. */
    private static final int[][] ICON_UV = { { 0, 204 }, { 0, 208 }, { 4, 208 } };
    private static final int ICON = 4;
    /** How near (screen px) the cursor must be to a waypoint dot: LOTR's 5 from zoom power 0 in, less zoomed out. */
    private static final float SELECT_RANGE = 5;
    private static final float SELECT_RANGE_MIN = 2;
    private static final int ROAD_COLOR = 0x000000;

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
    /** The zoom power an animation started from (can be between powers when zooming again mid-way). */
    private float zoomFrom = zoomPower;
    private long zoomStart;
    private float zoomExp = zoomPower;
    private float zoomScale;
    private int x0;
    private int y0;
    private int x1;
    private int y1;
    private boolean dragging;
    private MapNet.Mark selected;
    /** Other players and colonies this player may see; the server filters them, refreshed every 2 s. */
    private static com.porcupinestudios.lotrport.social.SocialNet.Icons icons =
            new com.porcupinestudios.lotrport.social.SocialNet.Icons(List.of(), List.of());
    private int iconTicks;
    /** Colony and player names show from this zoom power, at this text scale. */
    private static final float NAME_ZOOM = 2;
    private static final float NAME_SCALE = 0.5f;
    /** The waypoint under the cursor in the last frame drawn: what a click selects. */
    private MapNet.Mark hovered;

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
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.porcupinestudios.lotrport.social.SocialNet.IconsRequest());
    }

    /** From the server: the icons this player may see. */
    public static void icons(com.porcupinestudios.lotrport.social.SocialNet.Icons received) {
        icons = received;
    }

    private Component visibilityText() {
        return Component.translatableWithFallback("screen.lotr_port.map.visibility", "[Who sees me]");
    }

    private int[] visibilityBox() {
        Component t = visibilityText();
        int w = font.width(t);
        int x = x1 - w - 6;
        int y = y1 - font.lineHeight - 18;
        return new int[] {x, y, w, font.lineHeight};
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

    /**
     * The zoom now: eased (fast start, soft landing) from where the last zoom started to the target power, by the
     * clock. Each zoom starts from wherever the previous one had got to, so quick scrolling never jumps.
     */
    private void updateZoom() {
        float t = zoomAnimating() ? (System.nanoTime() - zoomStart) / (float) ZOOM_NANOS : 1;
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        zoomExp = zoomFrom + (zoomPower - zoomFrom) * eased;
        zoomScale = (float) Math.pow(2, zoomExp);
    }

    private boolean zoomAnimating() {
        return zoomFrom != zoomPower && System.nanoTime() - zoomStart < ZOOM_NANOS;
    }

    /**
     * The fraction of the current game tick, for panning between tick positions. A screen's render gets the frame's
     * real-time delta in 1.21, not this, which made panning and zooming jitter.
     */
    private static float partialTick() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    private float viewX(float partialTick) {
        return Mth.lerp(partialTick, prevPosX, posX);
    }

    private float viewZ(float partialTick) {
        return Mth.lerp(partialTick, prevPosZ, posZ);
    }

    /** The cursor's reach to a waypoint dot: 5 px from zoom power 0 in, down to 2 px at -3, where dots crowd. */
    private float selectRange() {
        return Mth.clamp(SELECT_RANGE + zoomExp, SELECT_RANGE_MIN, SELECT_RANGE);
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float frameDelta) {
        updateZoom();
        float partialTick = partialTick();
        float vx = viewX(partialTick);
        float vz = viewZ(partialTick);
        g.fill(0, 0, width, height, 0xFF000000);
        if (!fullscreen) {
            // LOTR's framed map: the parchment overlay over the whole screen, tinted brown, and a 4 px frame that
            // fades from red (next to the map) to black (outside), LOTR's renderGraduatedRects
            RenderSystem.enableBlend();
            g.setColor(0.65f, 0.5f, 0.35f, 1f);
            quad(g, OVERLAY, 0, 0, width, height, 0, 0, 1, 1, 1f);
            g.setColor(1f, 1f, 1f, 1f);
            for (int l = FRAME - 1; l >= 0; l--) {
                int c = lerpColor(BORDER_COLOR, 0xFF000000, (float) l / (FRAME - 1));
                g.fill(x0 - 1 - l, y0 - 1 - l, x1 + 1 + l, y1 + 1 + l, c);
            }
        }
        g.enableScissor(x0, y0, x1, y1);
        drawMap(g, vx, vz);
        // LOTR's order: compass, roads, player, waypoints, labels (labels go over the dots)
        RenderSystem.enableBlend();
        g.blit(ICONS, x0 + 12, y1 - 44, 224, 200, 32, 32, 256, 256);
        if (showWaypoints) {
            drawRoads(g, vx, vz);
        }
        drawPlayer(g, vx, vz);
        hovered = showWaypoints ? drawWaypoints(g, vx, vz, mouseX, mouseY) : null;
        if (dragging) {
            hovered = null;
        }
        if (showLabels) {
            drawLabels(g, vx, vz);
        }
        drawColonies(g, vx, vz);
        drawOthers(g, vx, vz);
        int[] vb = visibilityBox();
        boolean overVb = mouseX >= vb[0] && mouseX < vb[0] + vb[2] && mouseY >= vb[1] && mouseY < vb[1] + vb[3];
        g.drawString(font, visibilityText(), vb[0], vb[1], overVb ? 0xFFFFA0 : 0xFFFFFF, true);
        g.disableScissor();
        Widget overWidget = drawWidgets(g, mouseX, mouseY);
        if (!fullscreen) {
            g.drawString(font, title, width / 2 - font.width(title) / 2, 10, 0xFFFFFF, false);
        }
        if (!showWaypoints) {
            selected = null; // LOTR drops the selection when its waypoint is hidden
        }
        List<Component> lines = new ArrayList<>();
        Component tp = null;
        if (selected != null) {
            selectedLines(lines);
        } else if (overWidget == null && inMap(mouseX, mouseY)) {
            tp = hoverLines(lines, vx, vz, mouseX, mouseY);
        }
        drawSubtitles(g, lines);
        if (tp != null) {
            // LOTR puts the teleport hint at the bottom right, under the map, level between the two lines
            int ty = fullscreen ? y1 - 6 - font.lineHeight : y1 + 7 + (font.lineHeight + 3) / 2;
            g.drawString(font, tp, x1 - font.width(tp) - (fullscreen ? 6 : 0), ty, 0xFFFFFF, false);
        }
        if (selected != null) {
            waypointTooltip(g, selected, true, vx, vz);
        }
        if (overWidget != null) {
            g.renderTooltip(font, Component.translatableWithFallback("screen.lotr_port.map.widget." + overWidget.name,
                    overWidget.name.replace('_', ' ')), mouseX, mouseY);
        } else if (hovered != null && hovered != selected) {
            waypointTooltip(g, hovered, false, vx, vz);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gr = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return 0xFF000000 | r << 16 | gr << 8 | bl;
    }

    /**
     * The map image, the ocean around it, and the overlay. Like LOTR, the full-size image with nearest filtering at
     * every zoom: crisp painted pixels zoomed out, big square map pixels zoomed in. Float positions, so panning is
     * smooth.
     */
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
            ResourceLocation tex = MapTextures.get(sepia);
            Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(false, false);
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
     * LOTR's roads as dotted lines: 1 px black dots along each road, fading out when zoomed far out. LOTR draws every
     * (400 / stable zoom)th block-spaced road point, so a dot every 400/128 = 3.125 screen pixels; while zooming the
     * dots keep the spacing of the smaller zoom, as LOTR's.
     */
    private void drawRoads(GuiGraphics g, float vx, float vz) {
        float alpha = Math.min(1, (zoomExp + 3.3f) / 2.2f);
        if (alpha <= 0) {
            return;
        }
        int color = Math.round(alpha * 255) << 24 | ROAD_COLOR;
        float bpp = map.blocksPerPixel();
        float ox = (float) map.pixelX(0);
        float oz = (float) map.pixelZ(0);
        // the smaller of the two powers while zooming: halving or doubling the spacing keeps every other dot in place
        float stable = (float) Math.pow(2, zoomAnimating() ? Math.min(zoomPower, (int) Math.floor(zoomFrom))
                : zoomPower);
        float spacing = Math.max(1f / bpp, 400f / bpp / stable); // map pixels between dots (400 blocks / stable zoom)
        float left = vx - (x1 - x0) / 2f / zoomScale;
        float right = vx + (x1 - x0) / 2f / zoomScale;
        float top = vz - (y1 - y0) / 2f / zoomScale;
        float bottom = vz + (y1 - y0) / 2f / zoomScale;
        // walk each road as one line, so dots stay evenly spaced across its many short segments; every dot is a
        // 1 px square centred on its exact point (not snapped to the pixel grid), all in one batch
        float r = ((color >> 16) & 0xFF) / 255f;
        float gr = ((color >> 8) & 0xFF) / 255f;
        float bl = (color & 0xFF) / 255f;
        float a = alpha;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder buf = null;
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
                float px = cx() + (ax + (bx - ax) * t - vx) * zoomScale;
                float py = cy() + (az + (bz - az) * t - vz) * zoomScale;
                if (px < x0 - 1 || px > x1 + 1 || py < y0 - 1 || py > y1 + 1) {
                    continue;
                }
                if (buf == null) {
                    buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                }
                buf.addVertex(m, px - 0.5f, py - 0.5f, 0).setColor(r, gr, bl, a);
                buf.addVertex(m, px - 0.5f, py + 0.5f, 0).setColor(r, gr, bl, a);
                buf.addVertex(m, px + 0.5f, py + 0.5f, 0).setColor(r, gr, bl, a);
                buf.addVertex(m, px + 0.5f, py - 0.5f, 0).setColor(r, gr, bl, a);
            }
            carry = d - len;
        }
        if (buf != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }
    }

    /**
     * LOTR's labels: scaled with the zoom, turned by their angle, brightest (70%) in the middle of their zoom range
     * and fading to nothing at both ends.
     */
    private void drawLabels(GuiGraphics g, float vx, float vz) {
        RenderSystem.enableBlend();
        for (Label l : labels) {
            float t = (zoomExp - l.minZoom()) / (l.maxZoom() - l.minZoom());
            if (t <= 0 || t >= 1) {
                continue;
            }
            int alpha = Mth.clamp((int) ((0.5f - Math.abs(t - 0.5f)) / 0.5f * 0.7f * 255), 4, 255);
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

    /**
     * LOTR's waypoints: a 4 px dot (fading out when zoomed far out) and, from zoom power -1 in, the name above it,
     * growing to full size at zoom power 3, at 80% with a 1 px drop shadow. Returns the dot nearest the cursor
     * (within 5 px).
     */
    private MapNet.Mark drawWaypoints(GuiGraphics g, float vx, float vz, int mouseX, int mouseY) {
        float dotAlpha = Math.min(1, (zoomExp + 3.3f) / 2.2f);
        if (dotAlpha <= 0) {
            return null;
        }
        MapNet.Mark hovered = null;
        double best = Double.MAX_VALUE;
        boolean inWindow = inMap(mouseX, mouseY);
        float range = selectRange();
        float nameScale = Math.min(1, (zoomExp + 1) / 4);
        int nameAlpha = Mth.clamp((int) (nameScale * 0.8f * 255), 4, 255);
        RenderSystem.enableBlend();
        for (MapNet.Mark m : data.marks()) {
            float x = cx() + (m.mapX() - vx) * zoomScale;
            float y = cy() + (m.mapZ() - vz) * zoomScale;
            if (x < x0 - 200 || x > x1 + 200 || y < y0 - 200 || y > y1 + 200) {
                continue;
            }
            double dist = Math.hypot(x - mouseX, y - mouseY);
            if (inWindow && dist <= range && dist <= best) {
                hovered = m;
                best = dist;
            }
            int[] uv = ICON_UV[Math.max(0, Math.min(2, m.state()))];
            g.setColor(1f, 1f, 1f, dotAlpha);
            g.pose().pushPose();
            g.pose().translate(x - ICON / 2f, y - ICON / 2f, 0);
            g.blit(ICONS, 0, 0, uv[0], uv[1], ICON, ICON, 256, 256);
            g.pose().popPose();
            g.setColor(1f, 1f, 1f, 1f);
            if (showLabels && nameScale > 0) {
                Component name = name(m);
                g.pose().pushPose();
                g.pose().translate(x, y, 0);
                g.pose().scale(nameScale, nameScale, 1);
                int sx = -font.width(name) / 2;
                g.drawString(font, name, sx + 1, -15 + 1, nameAlpha << 24, false);
                g.drawString(font, name, sx, -15, nameAlpha << 24 | 0xFFFFFF, false);
                g.pose().popPose();
            }
        }
        return hovered;
    }

    /** Colonies: a banner-coloured square (yours gold, AI rulers' red, others white), names when zoomed in. */
    private void drawColonies(GuiGraphics g, float vx, float vz) {
        for (var c : icons.colonies()) {
            int x = Math.round(cx() + (c.x() - vx) * zoomScale);
            int y = Math.round(cy() + (c.z() - vz) * zoomScale);
            int colour = c.member() ? 0xFFE8B83A : c.ai() ? 0xFFB03030 : 0xFFE0E0E0;
            if (c.member() && c.limited()) {
                colour = (colour & 0x00FFFFFF) | 0x90000000;
            }
            g.fill(x - 4, y - 4, x + 4, y + 4, 0xFF000000);
            g.fill(x - 3, y - 3, x + 3, y + 3, colour);
            if (zoomExp >= NAME_ZOOM - 1) {
                Component name = Component.literal(c.name());
                g.pose().pushPose();
                g.pose().translate(x, y + 5, 0);
                g.pose().scale(NAME_SCALE * 1.5f, NAME_SCALE * 1.5f, 1);
                g.drawString(font, name, -font.width(name) / 2, 0, 0xFFFFFF, true);
                g.pose().popPose();
            }
        }
    }

    /** Other players who let this one see them: their face and name. */
    private void drawOthers(GuiGraphics g, float vx, float vz) {
        Minecraft mc = Minecraft.getInstance();
        for (var p : icons.players()) {
            int x = Math.round(cx() + (p.x() - vx) * zoomScale);
            int y = Math.round(cy() + (p.z() - vz) * zoomScale);
            g.fill(x - 5, y - 5, x + 5, y + 5, 0xFF303030);
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.id());
            if (info != null) {
                PlayerFaceRenderer.draw(g, info.getSkin(), x - 4, y - 4, 8);
            }
            g.pose().pushPose();
            g.pose().translate(x, y + 6, 0);
            g.pose().scale(NAME_SCALE * 1.5f, NAME_SCALE * 1.5f, 1);
            g.drawString(font, p.name(), -font.width(p.name()) / 2, 0, 0xFFFFFF, true);
            g.pose().popPose();
        }
    }

    /** The player's face (8 px) and hat layer (9 px), kept inside the map window like LOTR's. */
    private void drawPlayer(GuiGraphics g, float vx, float vz) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float px = Mth.clamp(cx() + (data.playerX() - vx) * zoomScale, x0 + 5, x1 - 6) + 0.5f;
        float py = Mth.clamp(cy() + (data.playerZ() - vz) * zoomScale, y0 + 5, y1 - 6) + 0.5f;
        ResourceLocation skin = mc.player.getSkin().texture();
        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(px - 4, py - 4, 0);
        g.blit(skin, 0, 0, 8, 8, 8, 8, 8, 8, 64, 64);
        g.pose().translate(-0.5f, -0.5f, 0);
        g.pose().scale(9 / 8f, 9 / 8f, 1);
        g.blit(skin, 0, 0, 8, 8, 40, 8, 8, 8, 64, 64);
        g.pose().popPose();
    }

    private Widget drawWidgets(GuiGraphics g, int mouseX, int mouseY) {
        Widget over = null;
        RenderSystem.enableBlend();
        for (Widget w : widgets) {
            int x = widgetX(w);
            int y = y0 + w.y();
            // like LOTR's: only the toggles and the zoom limits change icon; fullscreen and sepia stay as they are
            boolean off = switch (w.name()) {
                case "waypoints" -> !showWaypoints;
                case "zoom_in" -> zoomPower >= MAX_ZOOM;
                case "zoom_out" -> zoomPower <= MIN_ZOOM;
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

    /** LOTR's lines for the selected waypoint: why it is locked, how to travel, or how long to wait. */
    private void selectedLines(List<Component> lines) {
        if (selected.state() == MapNet.Mark.LOCKED) {
            lines.add(Component.translatableWithFallback("screen.lotr_port.map.locked_region",
                    "Unlock this waypoint by visiting its region"));
        } else if (selected.state() == MapNet.Mark.BLOCKED) {
            lines.add(selected.problem());
        } else if (data.waitTicks() > 0) {
            lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel_wait",
                    "You can fast travel to the selected waypoint in: %s", (data.waitTicks() + 19) / 20 + "s"));
        } else {
            lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel_prompt",
                    "Press '%s' to fast travel to the selected waypoint",
                    LotrClient.FAST_TRAVEL_KEY.getTranslatedKeyMessage()));
        }
    }

    /**
     * Text lines at the bottom: inside the window when fullscreen (on a dark band); otherwise in the border under the
     * map like LOTR's, from 7 px below it, 3 px apart, a single line moved down half a line.
     */
    private void drawSubtitles(GuiGraphics g, List<Component> lines) {
        if (lines.isEmpty()) {
            return;
        }
        int lh = font.lineHeight + (fullscreen ? 1 : 3);
        int top = fullscreen ? y1 - 6 - lines.size() * lh : y1 + 7 + (lines.size() == 1 ? lh / 2 : 0);
        if (fullscreen) {
            int w = 0;
            for (Component c : lines) {
                w = Math.max(w, font.width(c));
            }
            g.fill(width / 2 - w / 2 - 4, top - 3, width / 2 + w / 2 + 4, top + lines.size() * lh + 2, 0xA0000000);
        }
        int mid = (x0 + x1) / 2;
        for (int i = 0; i < lines.size(); i++) {
            Component c = lines.get(i);
            g.drawString(font, c, mid - font.width(c) / 2, top + i * lh, 0xFFFFFF, false);
        }
    }

    /**
     * LOTR's waypoint box: dark with a red edge, under the dot and kept inside the map; the name, and for the
     * selected waypoint its block coordinates too.
     */
    private void waypointTooltip(GuiGraphics g, MapNet.Mark m, boolean isSelected, float vx, float vz) {
        Component name = name(m);
        int bx = (int) Math.round((m.mapX() - map.pixelX(0)) * map.blocksPerPixel());
        int bz = (int) Math.round((m.mapZ() - map.pixelZ(0)) * map.blocksPerPixel());
        Component coords = Component.translatableWithFallback("screen.lotr_port.map.coords", "x: %s, z: %s", bx, bz);
        int border = 3;
        int lh = font.lineHeight;
        int inner = font.width(name);
        int h = border * 2 + lh;
        if (isSelected) {
            inner = Math.max(inner, font.width(coords));
            h += border + lh;
        }
        int w = inner + border * 2;
        int rx = Math.round(cx() + (m.mapX() - vx) * zoomScale) - w / 2;
        int ry = Math.round(cy() + (m.mapZ() - vz) * zoomScale) + 5;
        rx = Math.min(Math.max(rx, x0 + 2), x1 - 2 - w);
        ry = Math.min(Math.max(ry, y0 + 2), y1 - 2 - h);
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(rx, ry, rx + w, ry + h, 0xC0000000);
        g.fill(rx - 1, ry - 1, rx + w + 1, ry, BORDER_COLOR);
        g.fill(rx - 1, ry + h, rx + w + 1, ry + h + 1, BORDER_COLOR);
        g.fill(rx - 1, ry, rx, ry + h, BORDER_COLOR);
        g.fill(rx + w, ry, rx + w + 1, ry + h, BORDER_COLOR);
        int mid = rx + w / 2;
        g.drawString(font, name, mid - font.width(name) / 2, ry + border, 0xFFFFFF, false);
        if (isSelected) {
            g.drawString(font, coords, mid - font.width(coords) / 2, ry + border * 2 + lh, 0xFFFFFF, false);
        }
        g.pose().popPose();
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
        if (++iconTicks % 40 == 0) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.porcupinestudios.lotrport.social.SocialNet.IconsRequest());
        }
        prevPosX = posX;
        prevPosZ = posZ;
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
            updateZoom();
            zoomFrom = zoomExp;
            zoomStart = System.nanoTime();
            zoomPower = next;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] vb = visibilityBox();
        if (button == 0 && mouseX >= vb[0] && mouseX < vb[0] + vb[2] && mouseY >= vb[1] && mouseY < vb[1] + vb[3]) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                    new com.porcupinestudios.lotrport.social.SocialNet.OpenView());
            return true;
        }
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
                // the dot drawn under the cursor last frame: what you see is what you click
                MapNet.Mark hit = showWaypoints ? hovered : null;
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
            updateZoom();
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
            updateZoom();
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
