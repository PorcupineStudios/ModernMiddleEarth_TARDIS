package com.porcupinestudios.lotrport.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import com.porcupinestudios.lotrport.LotrClientConfig;
import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.combat.AttackTiming;
import com.porcupinestudios.lotrport.net.HudNet;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.joml.Matrix4f;

/**
 * LOTR's in-game overlays (LOTRTickHandlerClient, LOTRAttackTiming, LOTRModelCompass), drawn with LOTR's textures and
 * at LOTR's places:
 * <ul>
 * <li>the alignment bar, top centre: the watched faction's bar tinted its colour, the ring marker between the rank
 * below and the rank above (the pledge ring when pledged; it flashes when alignment moves), the rank names at each
 * end, the faction name, and the rank, or for 10 s after a change the alignment number. It slides in from the top and
 * away while a screen is open or the player list is held, and moves below boss bars;</li>
 * <li>the compass, top right: LOTR's compass disc tilted back, inside the Ring's band and its inscription, turning with
 * the player's facing; the biome above it and the coordinates below at half size;</li>
 * <li>the attack meter, bottom right: while the melee wait runs (see {@link AttackTiming}), the bar empties from the
 * left with the weapon drawn on it.</li>
 * </ul>
 * The bar and compass show in Middle-earth (standing in a lotr_port biome) or always if {@code
 * hud.alwaysShowAlignment} is set. This class also swallows an attack click on a living thing while the wait runs,
 * as LOTR did, so the click does nothing at all. Client only.
 */
@EventBusSubscriber(modid = LotrPort.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class LotrHud {
    private static final ResourceLocation ALIGNMENT = LotrPort.id("textures/gui/alignment.png");
    private static final ResourceLocation METER = LotrPort.id("textures/gui/attack_meter.png");
    private static final ResourceLocation COMPASS = LotrPort.id("textures/gui/compass.png");
    private static final ResourceLocation RING = LotrPort.id("textures/gui/compass_ring.png");
    private static final ResourceLocation WRITING = LotrPort.id("textures/gui/compass_writing.png");

    private static final Component ENEMY =
            Component.translatableWithFallback("faction.lotr_port.rank.enemy", "Enemy");
    private static final Component NEUTRAL =
            Component.translatableWithFallback("faction.lotr_port.rank.neutral", "Stranger");
    private static final String LIMITS_KEY = "faction.lotr_port.alignment.limits";

    private static final int BAR_WIDTH = 232;
    private static final int BAR_HEIGHT = 14;
    /** Travel of the ring marker across the bar. */
    private static final int MARKER_RANGE = 220;
    private static final int TEXT_COLOUR = 0xFFEE0C;
    /** Where the bar rests off screen. */
    private static final int HIDDEN_Y = -20;

    /** LOTRAlignmentTicker: ticks to glide to a new value, flash the marker, and show the number. */
    private static final int MOVE_TICKS = 20;
    private static final int FLASH_TICKS = 30;
    private static final int NUMBER_TICKS = 200;

    private static HudNet.AlignmentBar bar = HudNet.AlignmentBar.NONE;
    private static float oldAlign;
    private static float newAlign;
    private static int moveTick;
    private static int prevMoveTick;
    private static int flashTick;
    private static int numberTick;

    private static float slideY = HIDDEN_Y;
    private static float prevSlideY = HIDDEN_Y;
    /** Where the bar rests on screen: below the boss bars of the last frame. */
    private static int restY = 4;
    /** Bottom of the lowest boss bar drawn this frame (0 = none). */
    private static int bossBottom;
    private static boolean firstBar = true;

    private LotrHud() {}

    /** Called from {@link LotrClient} on the mod bus. */
    static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.EFFECTS, LotrPort.id("compass"), LotrHud::renderCompass);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, LotrPort.id("alignment"), LotrHud::renderAlignment);
        event.registerAbove(VanillaGuiLayers.HOTBAR, LotrPort.id("attack_meter"), LotrHud::renderAttackMeter);
    }

    /** A bar from the server (HudNet). A new faction starts still; a new value glides there and flashes. */
    static void receive(HudNet.AlignmentBar next) {
        if (!next.faction().equals(bar.faction()) || firstBar) {
            oldAlign = newAlign = next.alignment();
            moveTick = prevMoveTick = flashTick = numberTick = 0;
            firstBar = false;
        } else if (next.alignment() != newAlign) {
            oldAlign = newAlign;
            newAlign = next.alignment();
            moveTick = prevMoveTick = MOVE_TICKS;
            flashTick = FLASH_TICKS;
            numberTick = NUMBER_TICKS;
        }
        bar = next;
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) {
        bar = HudNet.AlignmentBar.NONE;
        firstBar = true;
        slideY = prevSlideY = HIDDEN_Y;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        prevMoveTick = moveTick;
        if (moveTick > 0 && --moveTick == 0) {
            oldAlign = newAlign;
        }
        if (flashTick > 0) {
            flashTick--;
        }
        if (numberTick > 0) {
            numberTick--;
        }
        // LOTR slides the bar in when nothing covers the game and away under screens and the player list
        prevSlideY = slideY;
        boolean visible = mc.player != null && mc.screen == null && !mc.options.keyPlayerList.isDown()
                && !mc.options.hideGui;
        float target = visible ? restY : HIDDEN_Y;
        float step = (restY - HIDDEN_Y) / 4f;
        slideY = slideY < target ? Math.min(target, slideY + step) : Math.max(target, slideY - step);
    }

    /** LOTR's attack timing on the client: a click at a living thing during the wait does nothing, not even a swing. */
    @SubscribeEvent
    public static void attackClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.isAttack() && mc.player != null && AttackTiming.enabled()
                && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity
                && AttackTiming.remaining(mc.player) > 0) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void frameStart(RenderGuiEvent.Pre event) {
        bossBottom = 0;
    }

    @SubscribeEvent
    public static void bossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        bossBottom = Math.max(bossBottom, event.getY() + event.getIncrement());
    }

    private static boolean inMiddleEarth(Minecraft mc) {
        return LotrClientConfig.ALWAYS_SHOW_ALIGNMENT.get()
                || mc.level != null && mc.player != null && LotrAmbience.inMiddleEarth(mc.level, mc.player.blockPosition());
    }

    // ------------------------------------------------------------------ alignment bar

    private static void renderAlignment(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        // outside Middle-earth (the TARDIS, the overworld) the bar shows while a faction NPC is near
        if (mc.options.hideGui || mc.player == null || !inMiddleEarth(mc) && !bar.nearNpc()) {
            return;
        }
        // one vanilla boss bar ends at 31; LOTR moved the bar down 20 for it
        restY = 4 + LotrClientConfig.ALIGNMENT_Y_OFFSET.get() + (bossBottom > 0 ? bossBottom - 11 : 0);
        if (!bar.present()) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        float x = g.guiWidth() / 2f + LotrClientConfig.ALIGNMENT_X_OFFSET.get();
        float y = Mth.lerp(partial, prevSlideY, slideY);
        boolean full = slideY == restY && prevSlideY == restY;
        float align = moveTick > 0
                ? newAlign + (oldAlign - newAlign) * Mth.lerp(partial, prevMoveTick, moveTick) / MOVE_TICKS
                : newAlign;
        Bounds b = bounds(align);
        float progress = Mth.clamp((align - b.lower()) / (b.upper() - b.lower()), 0f, 1f);

        RenderSystem.enableBlend();
        int rgb = bar.color();
        g.setColor((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, 1f);
        blitAt(g, ALIGNMENT, x - BAR_WIDTH / 2f, y, 0, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);
        g.setColor(1f, 1f, 1f, 1f);
        blitAt(g, ALIGNMENT, x - BAR_WIDTH / 2f, y, 0, 0, BAR_WIDTH, BAR_HEIGHT);
        // the ring marker: frames of a flash along the row, the pledge ring row when pledged
        float markerX = x - 8 + (progress - 0.5f) * 2f * MARKER_RANGE / 2f;
        blitAt(g, ALIGNMENT, markerX, y + BAR_HEIGHT / 2f - 8, 16 * (flashTick / 3), bar.pledged() ? 212 : 36,
                16, 16);
        renderReach(g, x, y);

        if (!full) {
            return;
        }
        Font font = mc.font;
        int textX = Math.round(x);
        int textY = Math.round(y + BAR_HEIGHT + 4);
        // the ranks at each end, half size
        int end = BAR_WIDTH / 2 - 6;
        g.pose().pushPose();
        g.pose().scale(0.5f, 0.5f, 1f);
        Component low = Component.translatableWithFallback(LIMITS_KEY, "%s (%s)", b.lowerName(), format(b.lower()));
        Component high = Component.translatableWithFallback(LIMITS_KEY, "%s (%s)", b.upperName(), format(b.upper()));
        borderedText(g, font, low, (textX - end) * 2 - font.width(low) / 2, textY * 2, 1f);
        borderedText(g, font, high, (textX + end) * 2 - font.width(high) / 2, textY * 2, 1f);
        g.pose().popPose();
        borderedText(g, font, bar.name(), textX - font.width(bar.name()) / 2, textY, 1f);
        // the rank, or for a while after a change the alignment itself, pulsing and then fading
        Component value;
        float alpha;
        if (numberTick > 0) {
            value = Component.literal(format(align));
            alpha = triangleWave(numberTick, 0.7f, 1f, 30f);
            if (numberTick < 15) {
                alpha *= numberTick / 15f;
            }
        } else {
            value = rankName(align);
            alpha = 1f;
        }
        borderedText(g, font, value, textX - font.width(value) / 2, textY + font.lineHeight + 3, alpha);
    }

    /**
     * LOTR's control-zone arrows either side of the bar: gold inside one of the faction's control zones, pale near one
     * of its people outside them (kills count in both), none when out of its reach (kills don't change alignment).
     */
    private static void renderReach(GuiGraphics g, float x, float y) {
        int reach = bar.reach();
        if (reach == 0) {
            return;
        }
        int v = reach == 2 ? 60 : 76;
        float ay = y + BAR_HEIGHT / 2f - 6;
        blitAt(g, ALIGNMENT, x - BAR_WIDTH / 2f - 14, ay, 0, v, 14, 12);
        blitAt(g, ALIGNMENT, x + BAR_WIDTH / 2f, ay, 14, v, 14, 12);
    }

    /** The span the bar shows (LOTR's renderAlignmentBar): between this rank and the next, or tenfold bands. */
    record Bounds(float lower, float upper, Component lowerName, Component upperName) {}

    static Bounds bounds(float align) {
        List<HudNet.Rank> ranks = bar.ranks();
        int at = -1;
        for (int i = 0; i < ranks.size(); i++) {
            if (align >= ranks.get(i).alignment()) {
                at = i;
            }
        }
        if (at >= 0) {
            HudNet.Rank rank = ranks.get(at);
            if (at + 1 < ranks.size()) {
                HudNet.Rank next = ranks.get(at + 1);
                return new Bounds(rank.alignment(), next.alignment(), rank.name(), next.name());
            }
            // past the top rank: tenfold bands
            float lower = rank.alignment();
            float upper = lower > 0 ? lower * 10 : 10;
            while (align >= upper) {
                lower = upper;
                upper *= 10;
            }
            return new Bounds(lower, upper, rank.name(), rank.name());
        }
        float first = ranks.isEmpty() || ranks.get(0).alignment() <= 0 ? 10f : ranks.get(0).alignment();
        if (Math.abs(align) < first) {
            return new Bounds(-first, first, ENEMY, ranks.isEmpty() ? NEUTRAL : ranks.get(0).name());
        }
        if (align < 0) {
            float upper = -first;
            float lower = upper * 10;
            while (align <= lower) {
                upper *= 10;
                lower *= 10;
            }
            return new Bounds(lower, upper, ENEMY, ENEMY);
        }
        float lower = first;
        float upper = first * 10;
        while (align >= upper) {
            lower = upper;
            upper *= 10;
        }
        return new Bounds(lower, upper, NEUTRAL, NEUTRAL);
    }

    private static Component rankName(float align) {
        HudNet.Rank best = null;
        for (HudNet.Rank r : bar.ranks()) {
            if (align >= r.alignment()) {
                best = r;
            }
        }
        return best != null ? best.name() : align < 0 ? ENEMY : NEUTRAL;
    }

    /** LOTRAlignmentValues.formatAlignForDisplay: one decimal, grouped thousands, a + on anything not negative. */
    static String format(float align) {
        DecimalFormat f = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));
        String s = f.format(align);
        return s.startsWith("-") ? s : "+" + s;
    }

    /** LOTRFunctions.triangleWave: between min and max and back over each period. */
    private static float triangleWave(float t, float min, float max, float period) {
        return min + (max - min) * Math.abs(t % period / period - 0.5f) * 2f;
    }

    /** LOTR's drawBorderedText: a black outline one pixel round, then the gold text. */
    private static void borderedText(GuiGraphics g, Font font, Component text, int x, int y, float alpha) {
        int a = Mth.clamp((int) (alpha * 255f), 4, 255) << 24;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.drawString(font, text.getString(), x + dx, y + dy, a, false);
                }
            }
        }
        g.drawString(font, text, x, y, a | TEXT_COLOUR, false);
    }

    private static void blitAt(GuiGraphics g, ResourceLocation tex, float x, float y, int u, int v, int w, int h) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.blit(tex, 0, 0, u, v, w, h, 256, 256);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ attack meter

    private static void renderAttackMeter(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.options.hideGui || player == null || !AttackTiming.enabled()
                || !LotrClientConfig.ATTACK_METER.get()) {
            return;
        }
        AttackTiming.Wait wait = AttackTiming.wait(player);
        if (wait.length() <= 0) {
            return;
        }
        float now = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        float left = (wait.ready() - now) / wait.length();
        if (left <= 0f) {
            return;
        }
        float done = Mth.clamp(1f - left, 0f, 1f);
        int x0 = g.guiWidth() / 2 + 120;
        int x1 = g.guiWidth() - 20;
        int y1 = g.guiHeight() - 10;
        int y0 = y1 - 10;
        if (x1 - x0 < 20) {
            return;
        }
        RenderSystem.enableBlend();
        // LOTR's meter texture: background, the part still to wait, the frame; each a 1/16 strip of the sheet
        g.blit(METER, x0, y0, x1 - x0, 10, 0, 0, 128, 8, 128, 128);
        int split = Math.round(x0 + (x1 - x0) * done);
        if (split < x1) {
            g.blit(METER, split, y0, x1 - split, 10, done * 128f, 8, Math.max(1, Math.round((1f - done) * 128f)), 8,
                    128, 128);
        }
        g.blit(METER, x0, y0, x1 - x0, 10, 0, 16, 128, 8, 128, 128);
        if (!wait.item().isEmpty()) {
            g.renderItem(wait.item(), (x0 + x1) / 2 - 8, (y0 + y1) / 2 - 8);
        }
    }

    // ------------------------------------------------------------------ compass

    private static void renderCompass(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.options.hideGui || player == null || mc.screen != null || mc.getDebugOverlay().showDebugScreen()
                || !LotrClientConfig.ONSCREEN_COMPASS.get() || !inMiddleEarth(mc)) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        int cx = g.guiWidth() - 60;
        int cy = 40;
        float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
        g.flush();
        PoseStack pose = g.pose();
        pose.pushPose();
        // behind the rest of the HUD: everything drawn later sits in front of it
        pose.translate(cx, cy, -100);
        pose.scale(1f, 1f, -1f);
        pose.mulPose(Axis.XP.rotationDegrees(40f));
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw));
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // the disc: 64 across, the whole compass texture, north away from the player
        RenderSystem.setShaderTexture(0, COMPASS);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        Matrix4f m = pose.last().pose();
        buf.addVertex(m, -32, 0, 32).setUv(0, 0);
        buf.addVertex(m, 32, 0, 32).setUv(1, 0);
        buf.addVertex(m, 32, 0, -32).setUv(1, 1);
        buf.addVertex(m, -32, 0, -32).setUv(0, 1);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        // the Ring's band (LOTRModelPortal): 60 blocks 4 x 7 x 3, 38 out, turned evenly round
        RenderSystem.setShaderTexture(0, RING);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        for (int i = 0; i < 60; i++) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotation(i / 60f * Mth.TWO_PI));
            box(buf, pose.last().pose(), -2, -3.5f, -38, 4, 7, 3, 64, 32);
            pose.popPose();
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());

        // its inscription, just outside and just inside the band
        RenderSystem.setShaderTexture(0, WRITING);
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        writing(buf, pose.last().pose(), 1.05f);
        writing(buf, pose.last().pose(), 0.85f);
        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableCull();
        pose.popPose();

        if (LotrClientConfig.COMPASS_EXTRA_INFO.get()) {
            Font font = mc.font;
            pose.pushPose();
            pose.scale(0.5f, 0.5f, 1f);
            String coords = Mth.floor(player.getX()) + ", " + Mth.floor(player.getBoundingBox().minY) + ", "
                    + Mth.floor(player.getZ());
            g.drawString(font, coords, cx * 2 - font.width(coords) / 2, cy * 2 + 70, 0xFFFFFF, false);
            BlockPos at = player.blockPosition();
            ResourceLocation biome = mc.level.getBiome(at).unwrapKey().map(k -> k.location()).orElse(null);
            if (biome != null && biome.getNamespace().equals(LotrPort.MOD_ID)) {
                Component name = Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath());
                g.drawString(font, name, cx * 2 - font.width(name) / 2, cy * 2 - 70, 0xFFFFFF, false);
            }
            pose.popPose();
        }
    }

    /** A Minecraft 1.7.10 model box (ModelBox) with its texture layout, texture offset 0, 0. */
    private static void box(BufferBuilder buf, Matrix4f m, float x0, float y0, float z0, int w, int h, int d,
            float texW, float texH) {
        float x1 = x0 + w;
        float y1 = y0 + h;
        float z1 = z0 + d;
        float[] p7 = {x0, y0, z0};
        float[] p0 = {x1, y0, z0};
        float[] p1 = {x1, y1, z0};
        float[] p2 = {x0, y1, z0};
        float[] p3 = {x0, y0, z1};
        float[] p4 = {x1, y0, z1};
        float[] p5 = {x1, y1, z1};
        float[] p6 = {x0, y1, z1};
        quad(buf, m, p4, p0, p1, p5, d + w, d, d + w + d, d + h, texW, texH);
        quad(buf, m, p7, p3, p6, p2, 0, d, d, d + h, texW, texH);
        quad(buf, m, p4, p3, p7, p0, d, 0, d + w, d, texW, texH);
        quad(buf, m, p1, p2, p6, p5, d + w, d, d + w + w, 0, texW, texH);
        quad(buf, m, p0, p7, p2, p1, d, d, d + w, d + h, texW, texH);
        quad(buf, m, p3, p4, p5, p6, d + w + d, d, d + w + d + w, d + h, texW, texH);
    }

    /** 1.7.10's TexturedQuad: corners get (u2, v1), (u1, v1), (u1, v2), (u2, v2). */
    private static void quad(BufferBuilder buf, Matrix4f m, float[] a, float[] b, float[] c, float[] e, float u1,
            float v1, float u2, float v2, float texW, float texH) {
        buf.addVertex(m, a[0], a[1], a[2]).setUv(u2 / texW, v1 / texH);
        buf.addVertex(m, b[0], b[1], b[2]).setUv(u1 / texW, v1 / texH);
        buf.addVertex(m, c[0], c[1], c[2]).setUv(u1 / texW, v2 / texH);
        buf.addVertex(m, e[0], e[1], e[2]).setUv(u2 / texW, v2 / texH);
    }

    /** The inscription band: 60 panels 4 x 5 at radius 38 (times scale), mirrored so it reads round the ring. */
    private static void writing(BufferBuilder buf, Matrix4f m, float scale) {
        float[][] base = {{2, -2.5f, -38}, {-2, -2.5f, -38}, {-2, 2.5f, -38}, {2, 2.5f, -38}};
        for (int i = 0; i < 60; i++) {
            float a = -(i / 60f * Mth.TWO_PI);
            float cos = Mth.cos(a);
            float sin = Mth.sin(a);
            float u1 = i / 60f;
            float u2 = (i + 1) / 60f;
            float[] us = {u2, u1, u1, u2};
            float[] vs = {0, 0, 1, 1};
            for (int k = 0; k < 4; k++) {
                float x = base[k][0] * cos + base[k][2] * sin;
                float z = base[k][2] * cos - base[k][0] * sin;
                buf.addVertex(m, -x * scale, base[k][1] * scale, z * scale).setUv(us[k], vs[k]);
            }
        }
    }
}
