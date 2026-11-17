package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.npc.Traders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** LOTR's LOTRGuiTradeInteract: the trader's name and title over Talk, Trade and Exchange Coins. */
public class TraderInteractScreen extends Screen {
    private final int npc;

    public TraderInteractScreen(Traders.Interact data) {
        super(data.title());
        this.npc = data.npc();
    }

    public static void open(Traders.Interact data) {
        Minecraft.getInstance().setScreen(new TraderInteractScreen(data));
    }

    @Override
    protected void init() {
        int x = width / 2;
        int y = height / 5 * 3;
        addRenderableWidget(Button.builder(Component.translatableWithFallback("lotr_port.gui.npc.talk", "Talk"),
                b -> choose(Traders.TALK)).bounds(x - 65, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("lotr_port.gui.npc.trade", "Trade"),
                b -> choose(Traders.TRADE)).bounds(x + 5, y, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("lotr_port.gui.npc.exchange",
                "Exchange Coins"), b -> choose(Traders.EXCHANGE)).bounds(x - 65, y + 25, 130, 20).build());
    }

    private void choose(int action) {
        PacketDistributor.sendToServer(new Traders.Choose(npc, action));
        if (action == Traders.TALK) {
            onClose();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, title, width / 2, height / 5 * 3 - 20, 0xFFFFFF);
    }

    @Override
    public void tick() {
        if (minecraft != null && minecraft.level != null && !(minecraft.level.getEntity(npc) instanceof
                com.porcupinestudios.lotrport.npc.LotrNpc n && n.isAlive()
                && minecraft.player.distanceTo(n) <= com.porcupinestudios.lotrport.npc.LotrNpc.DEAL_DISTANCE)) {
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
