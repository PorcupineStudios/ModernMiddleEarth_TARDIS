package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.npc.LotrNpc;
import com.porcupinestudios.lotrport.unit.UnitInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * A hired unit's gear (LOTR's LOTRGuiHiredWarriorInventory): vanilla's inventory screen with the unit in the picture,
 * its armour on the left and its two hands on the right where the crafting grid would be.
 */
public class UnitInventoryScreen extends AbstractContainerScreen<UnitInventoryMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    public UnitInventoryScreen(UnitInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 166;
        titleLabelX = 80;
        titleLabelY = 14;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - titleLabelX - 6), titleLabelX,
                titleLabelY, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // cover vanilla's crafting grid and its off-hand slot, then frame the unit's two hand slots
        g.fill(leftPos + 76, topPos + 6, leftPos + 170, topPos + 81, 0xFFC6C6C6);
        for (int i = 0; i < 2; i++) {
            slotFrame(g, leftPos + UnitInventoryMenu.HAND_X - 1, topPos + 43 + i * 18);
        }
        LotrNpc unit = menu.unit(minecraft.player);
        if (unit != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, leftPos + 26, topPos + 8, leftPos + 75,
                    topPos + 78, 30, 0.0625f, mouseX, mouseY, unit);
        }
    }

    /** Vanilla's slot look: dark top-left edge, white bottom-right, grey inside. */
    private static void slotFrame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
