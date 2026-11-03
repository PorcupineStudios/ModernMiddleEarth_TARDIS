package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.MapNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Middle-earth map: LOTR's map image with the waypoints on it. Drag to pan, scroll to zoom; click a green
 * waypoint to fast travel there. Yellow waypoints are unlocked but not usable now (cooldown, enemies, hostile
 * faction); grey ones are in regions not yet visited.
 */
public final class MapScreen extends Screen {
    private static final ResourceLocation MAP = LotrPort.id("textures/gui/map.png");
    private static final int MAP_W = 3200;
    private static final int MAP_H = 4000;
    private static final int[] COLORS = { 0xFF808080, 0xFFE0C040, 0xFF40E040 };

    private final MapNet.MapData data;
    /** Map pixel at the screen centre, and screen pixels per map pixel. */
    private double centerX;
    private double centerZ;
    private double zoom = 0.5;

    private MapScreen(MapNet.MapData data) {
        super(Component.translatableWithFallback("screen.lotr_port.map", "Middle-earth"));
        this.data = data;
        this.centerX = data.playerX();
        this.centerZ = data.playerZ();
    }

    public static void open(MapNet.MapData data) {
        Minecraft.getInstance().setScreen(new MapScreen(data));
    }

    private double screenX(double mapX) {
        return width / 2.0 + (mapX - centerX) * zoom;
    }

    private double screenY(double mapZ) {
        return height / 2.0 + (mapZ - centerZ) * zoom;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xFF1A1408);
        // the part of the map on screen
        double left = centerX - width / 2.0 / zoom;
        double top = centerZ - height / 2.0 / zoom;
        int u = (int) Math.max(0, Math.floor(left));
        int v = (int) Math.max(0, Math.floor(top));
        int u2 = (int) Math.min(MAP_W, Math.ceil(centerX + width / 2.0 / zoom));
        int v2 = (int) Math.min(MAP_H, Math.ceil(centerZ + height / 2.0 / zoom));
        if (u2 > u && v2 > v) {
            int x = (int) Math.round(screenX(u));
            int y = (int) Math.round(screenY(v));
            int w = (int) Math.round(screenX(u2)) - x;
            int h = (int) Math.round(screenY(v2)) - y;
            g.blit(MAP, x, y, w, h, u, v, u2 - u, v2 - v, MAP_W, MAP_H);
        }
        MapNet.Mark hovered = null;
        for (MapNet.Mark m : data.marks()) {
            int x = (int) Math.round(screenX(m.mapX()));
            int y = (int) Math.round(screenY(m.mapZ()));
            g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF000000);
            g.fill(x - 1, y - 1, x + 1, y + 1, COLORS[Math.max(0, Math.min(2, m.state()))]);
            if (Math.abs(mouseX - x) <= 3 && Math.abs(mouseY - y) <= 3) {
                hovered = m;
            }
        }
        int px = (int) Math.round(screenX(data.playerX()));
        int py = (int) Math.round(screenY(data.playerZ()));
        g.fill(px - 3, py - 3, px + 3, py + 3, 0xFFFFFFFF);
        g.fill(px - 2, py - 2, px + 2, py + 2, 0xFFD02020);
        g.drawCenteredString(font, title, width / 2, 6, 0xFFFFFF);
        if (data.waitTicks() > 0) {
            g.drawCenteredString(font, Component.translatableWithFallback("screen.lotr_port.map.wait",
                    "Fast travel in %s s", (data.waitTicks() + 19) / 20), width / 2, height - 14, 0xE0C040);
        }
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            ResourceLocation id = hovered.id();
            lines.add(Component.translatableWithFallback("waypoint." + id.getNamespace() + "." + id.getPath(),
                    id.getPath()));
            if (hovered.state() == MapNet.Mark.READY) {
                lines.add(Component.translatableWithFallback("screen.lotr_port.map.travel", "Click to travel")
                        .withStyle(s -> s.withColor(0x40E040)));
            }
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // the map fills the screen
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (MapNet.Mark m : data.marks()) {
                if (m.state() == MapNet.Mark.READY && Math.abs(mouseX - screenX(m.mapX())) <= 3
                        && Math.abs(mouseY - screenY(m.mapZ())) <= 3) {
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
        zoom = Math.max(0.05, Math.min(8, zoom * (scrollY > 0 ? 1.25 : 0.8)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
