package com.porcupinestudios.lotrport.client.ui;

import com.porcupinestudios.lotrport.LotrPort;
import com.porcupinestudios.lotrport.ui.Tone;
import net.minecraft.resources.ResourceLocation;

/**
 * The look: sprite ids (Kenney's RPG pack under {@code textures/gui/sprites/ui/}, nine-sliced by their .mcmeta) and
 * text colours. Change the look here, not in the panes. Colours depend on what the text sits on: {@link #ON_DARK}
 * for brown (table rows), {@link #ON_LIGHT} for beige (panels, popups).
 */
public final class UiTheme {
    private UiTheme() {}

    public static ResourceLocation sprite(String name) {
        return LotrPort.id("ui/" + name);
    }

    public static final ResourceLocation PANEL = sprite("panel_beige");
    public static final ResourceLocation POPUP = sprite("panel_beigelight");
    public static final ResourceLocation INSET_DARK = sprite("inset_brown");
    public static final ResourceLocation INSET_LIGHT = sprite("inset_beigelight");
    public static final ResourceLocation INSET = sprite("inset_beige");
    public static final ResourceLocation BUTTON = sprite("button");
    public static final ResourceLocation BUTTON_HOVER = sprite("button_hover");
    public static final ResourceLocation BUTTON_PRESSED = sprite("button_pressed");
    public static final ResourceLocation BUTTON_DISABLED = sprite("button_disabled");
    public static final ResourceLocation TAB = sprite("tab");
    public static final ResourceLocation TAB_HOVER = sprite("tab_hover");
    public static final ResourceLocation TAB_SELECTED = sprite("tab_selected");
    public static final ResourceLocation ROW = sprite("row");
    public static final ResourceLocation ROW_HOVER = sprite("row_hover");
    public static final ResourceLocation CHECK = sprite("checkbox");
    public static final ResourceLocation CHECKED = sprite("checkbox_checked");
    public static final ResourceLocation ARROW_DOWN = sprite("arrow_down");
    public static final ResourceLocation ARROW_LEFT = sprite("arrowbrown_left");
    public static final ResourceLocation ARROW_RIGHT = sprite("arrowbrown_right");
    public static final ResourceLocation SCROLL_TRACK = sprite("scroll_track");
    public static final ResourceLocation SCROLL_HANDLE = sprite("scroll_handle");

    /** The title above the tabs. */
    public static final int TITLE = 0xFFF5D98B;
    /** Text on beige: tabs, buttons, popups, stats. */
    public static final int DARK_TEXT = 0xFF4A2E17;
    /** Text on brown: table rows. */
    public static final int LIGHT_TEXT = 0xFFF3E7CF;
    /** Table column headings. */
    public static final int HEADING = 0xFFFBF1DC;
    public static final int DISABLED_TEXT = 0xFF8C8C8C;
    /** Bar fill and its trough. */
    public static final int BAR = 0xFF8FB85A;
    public static final int BAR_LOW = 0xFFD07A4E;
    public static final int TROUGH = 0x40000000;

    public static final int[] ON_DARK = {LIGHT_TEXT, 0xFFC9B394, 0xFFBFE48F, 0xFFF2A488, 0xFFFF8A7A};
    public static final int[] ON_LIGHT = {DARK_TEXT, 0xFF8A6F52, 0xFF3F7A2A, 0xFFB0452A, 0xFFA02A1E};

    /** The colour of text in this tone on brown ({@code dark} true) or on beige. */
    public static int tone(Tone tone, boolean dark) {
        return (dark ? ON_DARK : ON_LIGHT)[tone.ordinal()];
    }

    // sizes in GUI units
    public static final int TAB_HEIGHT = 16;
    public static final int BUTTON_HEIGHT = 18;
    public static final int FIELD_HEIGHT = 16;
    public static final int ROW_HEIGHT = 20;
    public static final int ROW_GAP = 3;
    public static final int HEADER_HEIGHT = 14;
    public static final int CHECK_SIZE = 11;
    public static final int PAD = 8;
}
