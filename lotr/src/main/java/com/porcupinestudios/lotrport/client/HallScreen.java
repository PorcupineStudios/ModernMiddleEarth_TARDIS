package com.porcupinestudios.lotrport.client;

import com.porcupinestudios.lotrport.colony.members.HallNet;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Town Hall: the colony at a glance and who may manage it. Members are listed with their roles; anyone with
 * the {@code members} permission can pick a member and give a role below their own, remove them, or add a player by
 * name. The roles and what each may do are listed on the right. The server checks every change.
 */
public class HallScreen extends Screen {
    private static final int ROW = 12;
    private static final int LIST_W = 150;

    private HallNet.Hall hall;
    private UUID selected;
    private EditBox addBox;

    private HallScreen(HallNet.Hall hall) {
        super(Component.translatableWithFallback("screen.lotr_port.hall", "Town Hall"));
        this.hall = hall;
    }

    public static void show(HallNet.Hall hall) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HallScreen open && open.hall.colony().equals(hall.colony())) {
            open.hall = hall;
            open.rebuildWidgets();
        } else if (hall.open()) {
            mc.setScreen(new HallScreen(hall));
        }
    }

    private int left() {
        return width / 2 - 200;
    }

    private int top() {
        return 44;
    }

    private Optional<HallNet.Member> current() {
        return hall.members().stream().filter(m -> m.id().equals(selected)).findFirst();
    }

    @Override
    protected void init() {
        int x = left() + LIST_W + 12;
        int y = top() + 14 + hall.roles().size() * 10;
        if (hall.canEdit()) {
            Optional<HallNet.Member> cur = current();
            if (cur.isPresent() && !cur.get().role().equals("lotr_port:ruler")) {
                int i = 0;
                for (HallNet.Role r : hall.roles()) {
                    if (r.id().equals("lotr_port:ruler")) {
                        continue;
                    }
                    String id = r.id();
                    addRenderableWidget(Button.builder(Component.literal(r.name()), b -> set(cur.get().id()
                            .toString(), id)).bounds(x + (i % 2) * 84, y + 16 + (i / 2) * 22, 80, 20).build());
                    i++;
                }
                addRenderableWidget(Button.builder(Component.translatableWithFallback(
                        "screen.lotr_port.hall.remove", "Remove"), b -> set(cur.get().id().toString(), ""))
                        .bounds(x, y + 16 + ((i + 1) / 2) * 22, 80, 20).build());
            }
            addBox = new EditBox(font, left(), height - 52, 110, 18, Component.literal("Player"));
            addBox.setHint(Component.translatableWithFallback("screen.lotr_port.hall.player", "Player name"));
            addRenderableWidget(addBox);
            addRenderableWidget(Button.builder(Component.translatableWithFallback("screen.lotr_port.hall.add",
                    "Add as citizen"), b -> {
                        if (!addBox.getValue().isBlank()) {
                            set(addBox.getValue().strip(), "lotr_port:citizen");
                        }
                    }).bounds(left() + 114, height - 53, 100, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatableWithFallback("gui.done", "Done"), b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    private void set(String player, String role) {
        PacketDistributor.sendToServer(new HallNet.SetRole(hall.colony(), player, role));
    }

    private String roleName(String id) {
        return hall.roles().stream().filter(r -> r.id().equals(id)).map(HallNet.Role::name).findFirst().orElse(id);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        g.drawCenteredString(font, Component.literal(hall.name()), width / 2, 8, 0xFFD37F);
        g.drawCenteredString(font, Component.translatableWithFallback("screen.lotr_port.hall.summary",
                "%s citizens · %s soldiers · %s coins · you: %s", hall.citizens(), hall.soldiers(),
                hall.coins(), hall.yourRole().isEmpty() ? "visitor" : roleName(hall.yourRole())), width / 2, 22,
                0xAAAAAA);
        int x = left();
        int y = top();
        int listH = height - y - 62;
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.hall.members", "Members"), x, y - 11,
                0xFFFFFF);
        g.fill(x - 2, y - 2, x + LIST_W + 2, y + listH, 0x80000000);
        int row = 0;
        for (HallNet.Member m : hall.members()) {
            int ry = y + row * ROW;
            row++;
            if (ry + ROW > y + listH) {
                break;
            }
            if (m.id().equals(selected)) {
                g.fill(x, ry, x + LIST_W, ry + ROW, 0x60FFFFFF);
            }
            g.drawString(font, m.name(), x + 2, ry + 2, 0xFFFFFF);
            String rn = roleName(m.role());
            g.drawString(font, rn, x + LIST_W - 2 - font.width(rn), ry + 2, 0xFFD37F);
        }
        int dx = x + LIST_W + 12;
        // the roles and what each may do, one line each (hover a line for all of it)
        g.drawString(font, Component.translatableWithFallback("screen.lotr_port.hall.roles", "Roles"), dx, y - 11,
                0xFFFFFF);
        int ry = y;
        for (HallNet.Role r : hall.roles()) {
            String text = r.name() + ": " + (r.permissions().equals("*") ? "everything" : r.permissions());
            int w = width - dx - 8;
            g.drawString(font, font.plainSubstrByWidth(text, w), dx, ry, 0xAAAAAA);
            if (mouseX >= dx && mouseX < dx + w && mouseY >= ry && mouseY < ry + 10 && font.width(text) > w) {
                g.renderTooltip(font, font.split(Component.literal(text), 200), mouseX, mouseY);
            }
            ry += 10;
        }
        Optional<HallNet.Member> cur = current();
        int by = y + 4 + hall.roles().size() * 10;
        if (hall.canEdit() && cur.isPresent()) {
            g.drawString(font, Component.translatableWithFallback("screen.lotr_port.hall.give", "Role for %s:",
                    cur.get().name()), dx, by, 0xFFFFFF);
        } else if (hall.canEdit()) {
            g.drawString(font, Component.translatableWithFallback("screen.lotr_port.hall.pick",
                    "Pick a member to change their role"), dx, by, 0xAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = left();
        int y = top();
        if (mx >= x && mx < x + LIST_W && my >= y) {
            int i = (int) ((my - y) / ROW);
            if (i >= 0 && i < hall.members().size()) {
                selected = hall.members().get(i).id();
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
