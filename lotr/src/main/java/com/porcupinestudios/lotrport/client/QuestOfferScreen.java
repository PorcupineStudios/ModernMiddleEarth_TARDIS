package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.net.QuestNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * LOTR's quest offer page: the giver in a frame on the left, what they ask and the reward on the right, and buttons to
 * take the quest (it goes in the Red Book) or turn it down. Closing the page turns it down.
 */
public final class QuestOfferScreen extends Screen {
    private static final ResourceLocation PAGE = LotrPort.id("textures/gui/quest/miniquest.png");
    private static final int W = 256;
    private static final int H = 200;
    private static final int INK = 0x3F2A14;

    private final QuestNet.Offer offer;
    private boolean answered;
    private int left;
    private int top;

    private QuestOfferScreen(QuestNet.Offer offer) {
        super(Component.literal(offer.giver()));
        this.offer = offer;
    }

    public static void open(QuestNet.Offer offer) {
        Minecraft.getInstance().setScreen(new QuestOfferScreen(offer));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.lotr_port.quest.accept",
                "Accept"), b -> answer(true)).bounds(left + 84, top + H - 32, 76, 20).build());
        addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.lotr_port.quest.decline",
                "Decline"), b -> answer(false)).bounds(left + 166, top + H - 32, 76, 20).build());
    }

    private void answer(boolean accept) {
        if (!answered) {
            answered = true;
            PacketDistributor.sendToServer(new QuestNet.Answer(accept));
        }
        onClose();
    }

    @Override
    public void removed() {
        // closing with Escape turns the quest down, so the server forgets the offer
        if (!answered) {
            answered = true;
            PacketDistributor.sendToServer(new QuestNet.Answer(false));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, left + W / 2 - font.width(title) / 2, top + 8, INK, false);
        g.drawString(font, offer.faction(), left + W / 2 - font.width(offer.faction()) / 2, top + 24, INK, false);
        Entity giver = minecraft.level == null ? null : minecraft.level.getEntity(offer.npc());
        if (giver instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, left + 16, top + 40, left + 76, top + 120, 30,
                    0.0625f, mouseX, mouseY, living);
        }
        int y = top + 42;
        Component ask = Component.translatableWithFallback("screen.lotr_port.quest.ask", "\"Could you %s?\"",
                offer.task());
        for (FormattedCharSequence line : font.split(ask, 156)) {
            g.drawString(font, line, left + 84, y, INK, false);
            y += font.lineHeight + 1;
        }
        y += 8;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.reward", "Reward:"),
                left + 84, y, INK, false);
        y += font.lineHeight + 2;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.coins", "%s coins",
                offer.coins()), left + 90, y, 0x7A5A10, false);
        y += font.lineHeight + 1;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.alignment",
                "+%s alignment", offer.alignment()), left + 90, y, 0x2F6A1F, false);
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.quest.book",
                "Taken quests are kept in your Red Book"), left + 16, top + H - 48, 0x6A5030, false);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.blit(PAGE, left, top, 0, 0, W, H, 256, 256);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
