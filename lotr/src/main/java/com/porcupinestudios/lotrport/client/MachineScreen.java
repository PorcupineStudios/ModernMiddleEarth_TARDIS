package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.machine.MachineMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * One screen for every LOTR machine (forges, hobbit oven, millstone, unsmeltery, armour stand), drawn on LOTR's own
 * screen textures from the menu's {@link MachineMenu.Layout}: the flame burns down as the fuel does and the arrow
 * fills with progress, as in a furnace.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = menu.layout.height;
        inventoryLabelY = menu.layout.inventoryY - 12; // LOTR: 8, ySize - 94 + 2
    }

    @Override
    protected void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2; // LOTR centres the name
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        MachineMenu.Layout layout = menu.layout;
        g.blit(layout.texture, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        // LOTR's numbers (LOTRGui*.drawGuiContainerBackgroundLayer): forge and oven flames scale to 12 and draw h + 2
        // high, the unsmeltery's to 13, h + 1; arrows n + 1 long; the millstone's wheel n high
        if (layout.flame != null && menu.burn() > 0) {
            boolean unsmeltery = layout == MachineMenu.Layout.UNSMELTERY;
            int scale = unsmeltery ? 13 : 12;
            int h = menu.burnMax() > 0 ? menu.burn() * scale / menu.burnMax() : scale;
            g.blit(layout.texture, leftPos + layout.flame[0], topPos + layout.flame[1] + 12 - h, 176, 12 - h, 14,
                    h + (unsmeltery ? 1 : 2));
        }
        int[] a = layout.arrow;
        if (a != null && menu.progress() > 0) {
            int n = menu.progress() * a[2] / menu.runTicks();
            switch (a[3]) {
                case 0 -> g.blit(layout.texture, leftPos + a[0], topPos + a[1], 176, 14, n + 1, 16);
                case 1 -> g.blit(layout.texture, leftPos + a[0], topPos + a[1], 176, 14, 16, n + 1);
                default -> g.blit(layout.texture, leftPos + a[0], topPos + a[1], 176, 0, 14, n);
            }
        }
    }
}
