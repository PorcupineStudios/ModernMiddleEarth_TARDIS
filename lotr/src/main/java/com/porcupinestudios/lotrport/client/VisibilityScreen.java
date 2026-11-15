package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.social.SocialNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Who sees you on the map (from the map's "Who sees me", or {@code /lotr_visibility}): everyone, your colonies'
 * members, your groups, and chosen players; your colonies' icons (public, members only, hidden) where your role
 * allows; and your player groups (create, add, remove, leave). The server applies and checks every change.
 */
public class VisibilityScreen extends Screen {
    private SocialNet.View view;
    private EditBox playerBox;
    private EditBox groupBox;
    private EditBox memberBox;

    private VisibilityScreen(SocialNet.View view) {
        super(Component.translatableWithFallback("screen.lotr_port.visibility", "Who sees you on the map"));
        this.view = view;
    }

    public static void show(SocialNet.View v) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof VisibilityScreen open) {
            open.view = v;
            open.rebuildWidgets();
        } else if (v.open()) {
            mc.setScreen(new VisibilityScreen(v));
        }
    }

    private void send(SocialNet.Kind kind, String a, String b) {
        PacketDistributor.sendToServer(new SocialNet.Change(kind.ordinal(), a, b));
    }

    private static Component onOff(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "Yes" : "No"));
    }

    @Override
    protected void init() {
        int l = width / 2 - 200;
        int y = 30;
        addRenderableWidget(Button.builder(onOff("Everyone", view.everyone()), b -> send(SocialNet.Kind.EVERYONE,
                "", "")).bounds(l, y, 130, 20).build());
        addRenderableWidget(Button.builder(onOff("My colonies", view.colonies()), b -> send(SocialNet.Kind.COLONIES,
                "", "")).bounds(l + 134, y, 130, 20).build());
        addRenderableWidget(Button.builder(onOff("My groups", view.groups()), b -> send(SocialNet.Kind.GROUPS,
                "", "")).bounds(l + 268, y, 130, 20).build());

        y += 40;
        playerBox = new EditBox(font, l, y, 120, 18, Component.literal("Player"));
        playerBox.setHint(Component.literal("Player name"));
        addRenderableWidget(playerBox);
        addRenderableWidget(Button.builder(Component.literal("Allow"), b -> send(SocialNet.Kind.ADD_PLAYER,
                playerBox.getValue(), "")).bounds(l + 124, y - 1, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Remove"), b -> send(SocialNet.Kind.REMOVE_PLAYER,
                playerBox.getValue(), "")).bounds(l + 176, y - 1, 56, 20).build());

        int cy = 70;
        for (SocialNet.ColonyView c : view.colonyList()) {
            if (c.canEdit()) {
                String id = c.id().toString();
                addRenderableWidget(Button.builder(Component.literal(presence(c.presence())),
                        b -> send(SocialNet.Kind.COLONY_PRESENCE, id, "")).bounds(width / 2 + 120, cy + 10, 80, 18)
                        .build());
            }
            cy += 22;
        }

        int gy = height - 100;
        groupBox = new EditBox(font, l, gy, 100, 18, Component.literal("Group"));
        groupBox.setHint(Component.literal("Group name"));
        addRenderableWidget(groupBox);
        memberBox = new EditBox(font, l + 104, gy, 100, 18, Component.literal("Player"));
        memberBox.setHint(Component.literal("Player name"));
        addRenderableWidget(memberBox);
        addRenderableWidget(Button.builder(Component.literal("Create"), b -> send(SocialNet.Kind.GROUP_CREATE,
                groupBox.getValue(), "")).bounds(l + 208, gy - 1, 52, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Add"), b -> send(SocialNet.Kind.GROUP_ADD,
                groupBox.getValue(), memberBox.getValue())).bounds(l + 262, gy - 1, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Kick"), b -> send(SocialNet.Kind.GROUP_REMOVE,
                groupBox.getValue(), memberBox.getValue())).bounds(l + 304, gy - 1, 40, 20).build());
        int ly = gy + 24;
        for (SocialNet.GroupView g : view.groupList()) {
            String id = g.id().toString();
            addRenderableWidget(Button.builder(Component.literal(g.leader() ? "Disband" : "Leave"),
                    b -> send(SocialNet.Kind.GROUP_LEAVE, id, "")).bounds(l + 300, ly - 4, 54, 16).build());
            ly += 16;
            if (ly > height - 30) {
                break;
            }
        }
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 24, 100, 20).build());
    }

    private static String presence(int i) {
        return switch (i) {
            case 0 -> "Public";
            case 1 -> "Members only";
            default -> "Hidden";
        };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        int l = width / 2 - 200;
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);
        g.drawString(font, "Chosen players always see you:", l, 59, 0xAAAAAA);
        String players = view.players().isEmpty() ? "none" : String.join(", ", view.players());
        int y = 94;
        for (var line : font.split(Component.literal(players), 230)) {
            g.drawString(font, line, l, y, 0xFFFFFF);
            y += 10;
        }
        int cx = width / 2 + 40;
        g.drawString(font, "Your colonies on the map:", cx, 59, 0xAAAAAA);
        int cy = 70;
        for (SocialNet.ColonyView c : view.colonyList()) {
            g.drawString(font, c.name(), cx, cy + 15, 0xFFD37F);
            if (!c.canEdit()) {
                g.drawString(font, presence(c.presence()), width / 2 + 120, cy + 15, 0xAAAAAA);
            }
            cy += 22;
        }
        int gy = height - 100;
        g.drawString(font, "Groups (members see each other if \"My groups\" is on):", l, gy - 12, 0xAAAAAA);
        int ly = gy + 24;
        for (SocialNet.GroupView gr : view.groupList()) {
            g.drawString(font, font.plainSubstrByWidth(gr.name() + ": " + gr.members(), 290), l, ly, 0xFFFFFF);
            ly += 16;
            if (ly > height - 30) {
                break;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
