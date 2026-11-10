package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.content.pouch.PouchMenu;
import com.porcupinestudios.lotrport.content.pouch.Pouches;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * LOTR's pouch screen (LOTRGuiPouch): its texture 176 x 180, one slot row strip (from v 180) per row of the pouch,
 * and a name field at the top that renames the pouch as you type.
 */
public class PouchScreen extends AbstractContainerScreen<PouchMenu> {
    private static final ResourceLocation TEXTURE = LotrPort.id("textures/gui/pouch.png");
    private EditBox name;

    public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 180;
        inventoryLabelY = imageHeight - 96 + 2;
    }

    @Override
    protected void init() {
        super.init();
        name = new EditBox(font, leftPos + imageWidth / 2 - 80, topPos + 7, 160, 20, Component.empty());
        name.setMaxLength(64);
        name.setValue(title.getString());
        name.setResponder(text -> PacketDistributor.sendToServer(new Pouches.Rename(text)));
        addRenderableWidget(name);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (name.isFocused() && key != 256) { // typing goes to the name, Escape still closes
            return name.keyPressed(key, scan, modifiers) || true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        for (int row = 0; row < menu.rows; row++) {
            g.blit(TEXTURE, leftPos + 7, topPos + 29 + row * 18, 0, 180, 162, 18);
        }
    }
}
