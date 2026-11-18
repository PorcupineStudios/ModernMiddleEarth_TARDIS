package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.npc.NpcTrading.Deal;
import com.porcupinestudios.lotrport.npc.TraderMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * LOTR's trade screen (LOTRGuiTrade, its 512x512 texture): the trader's name, Buy and Sell rows with each price in
 * the circle under the item, Sell offers with the coin button and its price, then the inventory.
 */
public class TradeScreen extends AbstractContainerScreen<TraderMenu> {
    private static final ResourceLocation TEXTURE = LotrPort.id("textures/gui/trade.png");
    private static final int TEXT = 0x404040;
    private static final int BUTTON_X = 79;
    private static final int BUTTON_Y = 164;

    public TradeScreen(TraderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 270;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 512, 512);
        int state = menu.sellPrice() <= 0 ? 0 : over(mouseX, mouseY) ? 2 : 1;
        g.blit(TEXTURE, leftPos + BUTTON_X, topPos + BUTTON_Y, 176, state * 18, 18, 18, 512, 512);
    }

    private boolean over(double mouseX, double mouseY) {
        double x = mouseX - leftPos - BUTTON_X;
        double y = mouseY - topPos - BUTTON_Y;
        return x >= 0 && y >= 0 && x < 18 && y < 18;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, menu.title, 89 - font.width(menu.title) / 2, 11, TEXT, false);
        g.drawString(font, label("buy", "Buy"), 8, 28, TEXT, false);
        g.drawString(font, label("sell", "Sell"), 8, 79, TEXT, false);
        g.drawString(font, label("sell_offer", "Sell offers"), 8, 129, TEXT, false);
        g.drawString(font, playerInventoryTitle, 8, 176, TEXT, false);
        costs(g, menu.deals.buy(), TraderMenu.BUY, true);
        costs(g, menu.deals.sell(), TraderMenu.SELL, false);
        int price = menu.sellPrice();
        if (price > 0) {
            g.drawString(font, Component.translatableWithFallback("container.lotr_port.trade.sell_price",
                    "Sell price: %s", price), 100, 169, TEXT, false);
        }
    }

    private void costs(GuiGraphics g, List<Deal> deals, int first, boolean buy) {
        for (int i = 0; i < deals.size(); i++) {
            Slot slot = menu.getSlot(first + i);
            boolean open = buy ? menu.buyOpen(i) : menu.sellOpen(i);
            if (!open) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA0000000);
                g.pose().popPose();
            }
            String cost = open ? Integer.toString(deals.get(i).cost)
                    : label("locked", "Locked").getString();
            g.pose().pushPose();
            int w = font.width(cost);
            float scale = w > 15 ? 0.5f : 1f;
            g.pose().translate(slot.x + 8, slot.y + 22 - font.lineHeight * scale / 2f, 300);
            g.pose().scale(scale, scale, 1f);
            g.drawString(font, cost, -w / 2, 0, TEXT, false);
            g.pose().popPose();
        }
    }

    private static Component label(String key, String fallback) {
        return Component.translatableWithFallback("container.lotr_port.trade." + key, fallback);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && over(mouseX, mouseY) && menu.sellPrice() > 0 && minecraft != null
                && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TraderMenu.SELL_BUTTON);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
    }
}
