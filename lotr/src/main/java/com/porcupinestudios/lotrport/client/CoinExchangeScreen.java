package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.CoinExchangeMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** LOTR's coin exchange screen (LOTRGuiCoinExchange): coins in the middle, an arrow to each side. */
public class CoinExchangeScreen extends AbstractContainerScreen<CoinExchangeMenu> {
    private static final ResourceLocation TEXTURE = LotrPort.id("textures/gui/coin_exchange.png");
    private static final int TEXT = 0x404040;
    private static final int ARROW_Y = 45;

    public CoinExchangeScreen(CoinExchangeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 188;
    }

    private int arrowX(int id) {
        return imageWidth / 2 + (id == 0 ? -28 : 28) - 16;
    }

    private boolean over(int id, double mouseX, double mouseY) {
        double x = mouseX - leftPos - arrowX(id);
        double y = mouseY - topPos - ARROW_Y;
        return x >= 0 && y >= 0 && x < 32 && y < 17;
    }

    private boolean enabled(int id) {
        return id == 0 ? menu.canBreak() : menu.canJoin();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        Slot in = menu.getSlot(CoinExchangeMenu.INPUT);
        if (in.hasItem()) {
            g.blit(TEXTURE, leftPos + in.x - 5, topPos + in.y - 5, 176, 51, 26, 26);
        }
        for (int id = 0; id < 2; id++) {
            int state = !enabled(id) ? 0 : over(id, mouseX, mouseY) ? 2 : 1;
            g.blit(TEXTURE, leftPos + arrowX(id), topPos + ARROW_Y, 176 + id * 32, state * 17, 32, 17);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Component t = Component.translatableWithFallback("container.lotr_port.coin_exchange", "Coin Exchange");
        g.drawString(font, t, 89 - font.width(t) / 2, 11, TEXT, false);
        g.drawString(font, playerInventoryTitle, 8, 94, TEXT, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int id = 0; id < 2; id++) {
            if (button == 0 && over(id, mouseX, mouseY) && enabled(id) && minecraft != null
                    && minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
