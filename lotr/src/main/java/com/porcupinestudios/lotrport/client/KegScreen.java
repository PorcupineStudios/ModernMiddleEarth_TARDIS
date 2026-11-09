package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.content.LotrComponents;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.drink.Drinks;
import com.porcupinestudios.lotrport.drink.KegBlockEntity;
import com.porcupinestudios.lotrport.drink.KegMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * LOTR's barrel screen (LOTRGuiBarrel's layout and textures, {@code converter/drinks.py} copies them): the drink fills
 * the gauge on the right in its colour, bubbling while it brews.
 */
public class KegScreen extends AbstractContainerScreen<KegMenu> {
    private static final ResourceLocation BACKGROUND = LotrPort.id("textures/gui/keg.png");
    private static final ResourceLocation GAUGE = LotrPort.id("textures/gui/keg_brewing.png");
    private static final int TANK_X = 148;
    private static final int TANK_BOTTOM = 130;
    private static final int TANK_W = 48;
    private static final int TANK_H = 96;
    private Button button;

    public KegScreen(KegMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 210;
        imageHeight = 221;
        inventoryLabelX = 25;
        inventoryLabelY = 127;
    }

    @Override
    protected void init() {
        super.init();
        button = addRenderableWidget(Button.builder(Component.empty(), b -> minecraft.gameMode
                .handleInventoryButtonClick(menu.containerId, menu.mode() == KegBlockEntity.BREWING ? 1 : 0))
                .bounds(leftPos + 25, topPos + 97, 100, 20).build());
        updateButton();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateButton();
    }

    private void updateButton() {
        boolean brewing = menu.mode() == KegBlockEntity.BREWING;
        button.setMessage(Component.translatable(brewing ? "container.lotr_port.keg.stop_brewing"
                : "container.lotr_port.keg.start_brewing"));
        button.active = menu.mode() == KegBlockEntity.EMPTY || brewing && Drinks.strength(menu.drink()) > 0;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        int h = menu.capacity() > 0 ? Math.min(TANK_H, TANK_H * menu.amount() / menu.capacity()) : 0;
        if (h <= 0) {
            return;
        }
        Drinks.DrinkFluidEntry fluid = Drinks.BY_ITEM.get(BuiltInRegistries.ITEM.getKey(menu.drink().getItem()));
        int x = leftPos + TANK_X;
        int y = topPos + TANK_BOTTOM - h;
        g.fill(x, y, x + TANK_W, y + h, 0xFF000000 | (fluid == null ? 0xC08040 : fluid.color()));
        g.blit(GAUGE, x, y, 1, TANK_H - h, TANK_W, h); // the gauge's marks over the drink
        if (menu.mode() == KegBlockEntity.BREWING) {
            g.blit(GAUGE, x, y, 51, TANK_H - h, TANK_W, h); // bubbles
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, imageWidth / 2 - font.width(title) / 2, 6, 0x404040, false);
        Component status = status();
        g.drawString(font, status, imageWidth / 2 - font.width(status) / 2, 17, 0x404040, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    /** LOTR's line under the title: Empty, Brewing Ale (Light), Ale (Strong) x 12. */
    private Component status() {
        ItemStack drink = menu.drink();
        if (menu.mode() == KegBlockEntity.EMPTY || drink.isEmpty()) {
            return Component.translatable("container.lotr_port.keg.empty");
        }
        Component strength = drink.has(LotrComponents.DRINK_STRENGTH.get())
                ? Component.translatable("item.lotr_port.drink.strength." + Drinks.strength(drink)) : Component.empty();
        return menu.mode() == KegBlockEntity.BREWING
                ? Component.translatable("container.lotr_port.keg.brewing", drink.getHoverName(), strength)
                : Component.translatable("container.lotr_port.keg.full", drink.getHoverName(), strength,
                        drink.getCount());
    }
}
