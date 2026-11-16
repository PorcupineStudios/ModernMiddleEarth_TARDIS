package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.colony.work.LedgerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A colonist's page (RingRealms' colonist screen): race, work, home, needs and purse. */
public class CitizenScreen extends Screen {
    private final LedgerNet.CitizenView c;

    private CitizenScreen(LedgerNet.CitizenView c) {
        super(Component.literal(c.name()));
        this.c = c;
    }

    public static void show(LedgerNet.CitizenView c) {
        Minecraft.getInstance().setScreen(new CitizenScreen(c));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height / 2 + 70, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int x = width / 2 - 120;
        int y = height / 2 - 70;
        g.fill(x - 8, y - 8, x + 248, y + 128, 0xA0000000);
        g.drawCenteredString(font, Component.literal(c.name()), width / 2, y, 0xFFD37F);
        g.drawCenteredString(font, Component.literal(c.race() + " of " + c.colony()), width / 2, y + 12, 0xAAAAAA);
        row(g, x, y + 32, "Work", c.work() + (c.unpaid() > 0 ? " (striking: wages owed)" : ""));
        row(g, x, y + 46, "Home", c.home());
        row(g, x, y + 60, "Food", c.food() + " / 20" + (c.food() <= 0 ? " (starving)" : ""));
        row(g, x, y + 74, "Happiness", c.happiness() + " / 100");
        row(g, x, y + 88, "Skill", c.skill() + " / 100");
        row(g, x, y + 102, "Purse", c.purse() + " coins");
    }

    private void row(GuiGraphics g, int x, int y, String label, String value) {
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.citizen." + label.toLowerCase(),
                label), x, y, 0xAAAAAA);
        g.drawString(font, font.plainSubstrByWidth(value, 170), x + 70, y, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
