package com.mpp.integratedscripting.client.gui.component;

/**
 * Geometry of the scripting terminal at any GUI size.
 *
 * <p>The 1.20 reference screen has a fixed 256x240 background
 * ({@code textures/gui/terminal_scripting.png}). On large screens that wastes a
 * lot of space and the editor stays small, so the 1.12.2 port slices the
 * background instead of blitting it 1:1 (see {@link TerminalBackground}): the
 * frame edges, the header boxes and the player inventory cells keep their pixel
 * size while the editor, the file list and the surrounding grey panels grow with
 * the scaled screen size. The same texture at 256x240 therefore still produces
 * the original screen pixel for pixel.</p>
 *
 * <p>The texture has two side by side panels and is not a single symmetric
 * nine-slice, so the layout is expressed as the bands the renderer needs:</p>
 * <ul>
 *   <li>left panel: fixed width {@value #LEFT_PANEL_WIDTH}, a {@value #HEADER_HEIGHT}px
 *       header, a vertically stretchable file list and a fixed
 *       {@value #LEFT_BOTTOM_HEIGHT}px bottom band;</li>
 *   <li>main panel: fixed {@value #RIGHT_FRAME_WIDTH}px right frame, a
 *       {@value #HEADER_HEIGHT}px header, a stretchable editor and a fixed
 *       {@value #BOTTOM_HEIGHT}px bottom band with the slots, anchored to the
 *       bottom and centered horizontally.</li>
 * </ul>
 *
 * <p>Because the bottom band is anchored to the bottom, every container slot
 * moves by the same vertical offset. Horizontally the whole slot cluster
 * (player inventory, variable slot and error icon) shifts by half of the extra
 * width, which keeps it centered and keeps the variable slot above the right
 * edge of the player inventory as in the 256x240 design. The client GUI moves
 * the client side slots by {@link #getContentShiftX()} and
 * {@link #getExtraHeight()} in {@code GuiTerminalScripting#initGui()}; the
 * server side slot coordinates are irrelevant to click routing (clicks are
 * identified by slot index), so both sides stay compatible.</p>
 *
 * @author rubensworks
 */
public final class TerminalLayout {

    /** Size of the original background texture. */
    public static final int BASE_WIDTH = 256;
    public static final int BASE_HEIGHT = 240;

    /** The texture is a square GUI sheet; slices are expressed in its pixels. */
    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 256;

    /** The left panel keeps its designed width; only the main panel grows. */
    public static final int LEFT_PANEL_WIDTH = 80;
    /** Height of both header boxes (rows 0..17 of the texture). */
    public static final int HEADER_HEIGHT = 18;
    /** Bottom band of the left panel below the file list (rows 232..239). */
    public static final int LEFT_BOTTOM_HEIGHT = 8;
    /** Bottom band of the main panel with the variable and player slots. */
    public static final int BOTTOM_HEIGHT = 106;
    /** Fixed width of the main panel's right frame (texture x 249..255). */
    public static final int RIGHT_FRAME_WIDTH = 7;

    /** Space kept between the GUI and the scaled screen edges. */
    public static final int SCREEN_MARGIN = 8;
    /**
     * Upper bound of the expanded GUI. The screen itself limits the size in
     * practice; this only keeps a pathological GUI scale from producing an
     * unreadable full-screen sheet.
     */
    public static final int MAX_WIDTH = 1024;
    public static final int MAX_HEIGHT = 768;

    /** File list geometry, identical to the original screen. */
    public static final int LIST_X = 19;
    public static final int LIST_Y = 18;
    public static final int LIST_WIDTH = 56;
    public static final int LIST_ROW_HEIGHT = 8;
    public static final int SCROLL_BAR_X = 5;
    /** The original scroll bar starts at y 18 and is 162px high in a 214px list. */
    public static final int SCROLL_BAR_LIST_INSET = 52;

    /** Editor inset inside the main panel (the widget starts below the border). */
    public static final int EDITOR_X = 81;
    public static final int EDITOR_Y = 19;
    /** Distance of the error icon from the bottom frame. */
    public static final int ERROR_BOTTOM_INSET = 101;

    /** Offsets of the slot cluster from the bottom of the texture. */
    public static final int PLAYER_GRID_BASE_X = 87;
    public static final int PLAYER_GRID_FROM_BOTTOM = 83;
    public static final int HOTBAR_FROM_BOTTOM = 25;
    public static final int VARIABLE_SLOT_BASE_X = 231;
    public static final int VARIABLE_SLOT_FROM_BOTTOM = 104;
    /** Horizontal distance from the variable slot to the error icon. */
    public static final int ERROR_LEFT_OF_VARIABLE_SLOT = 19;
    /** The create-file button sits in the last 10px above the left bottom band. */
    public static final int CREATE_BUTTON_INSET = 18;

    private final int width;
    private final int height;

    /**
     * @param width Requested GUI width; clamped to at least {@link #BASE_WIDTH}.
     * @param height Requested GUI height; clamped to at least {@link #BASE_HEIGHT}.
     */
    public TerminalLayout(int width, int height) {
        this.width = Math.max(BASE_WIDTH, Math.min(width, MAX_WIDTH));
        this.height = Math.max(BASE_HEIGHT, Math.min(height, MAX_HEIGHT));
    }

    /**
     * Compute the GUI size that fills the player's scaled screen, with a small
     * margin and never below the original texture size.
     *
     * @param screenWidth The scaled screen width ({@code GuiScreen#width}).
     * @param screenHeight The scaled screen height ({@code GuiScreen#height}).
     * @return The layout for this screen.
     */
    public static TerminalLayout forScreen(int screenWidth, int screenHeight) {
        int availableWidth = Math.max(BASE_WIDTH, screenWidth - 2 * SCREEN_MARGIN);
        int availableHeight = Math.max(BASE_HEIGHT, screenHeight - 2 * SCREEN_MARGIN);
        return new TerminalLayout(Math.min(availableWidth, MAX_WIDTH),
                Math.min(availableHeight, MAX_HEIGHT));
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    public int getExtraWidth() {
        return this.width - BASE_WIDTH;
    }

    public int getExtraHeight() {
        return this.height - BASE_HEIGHT;
    }

    public boolean isExpanded() {
        return getExtraWidth() > 0 || getExtraHeight() > 0;
    }

    // ---------------------------------------------------------------- left panel

    /** Height of the vertically stretchable file list band. */
    public int getListHeight() {
        return this.height - HEADER_HEIGHT - LEFT_BOTTOM_HEIGHT;
    }

    /** Number of file rows that fit in the list band. */
    public int getVisibleRows() {
        return Math.max(1, getListHeight() / LIST_ROW_HEIGHT);
    }

    /** Scroll bar height, proportional to the list band. */
    public int getScrollBarHeight() {
        return Math.max(17, getListHeight() - SCROLL_BAR_LIST_INSET);
    }

    /** Top of the create-file button, relative to the GUI. */
    public int getCreateButtonY() {
        return this.height - CREATE_BUTTON_INSET;
    }

    /** Top of the left panel's fixed bottom band. */
    public int getLeftBottomTop() {
        return this.height - LEFT_BOTTOM_HEIGHT;
    }

    // ---------------------------------------------------------------- main panel

    /** Top of the fixed bottom band of the main panel. */
    public int getBottomTop() {
        return this.height - BOTTOM_HEIGHT;
    }

    /** Left edge of the editor widget, relative to the GUI. */
    public int getEditorX() {
        return EDITOR_X;
    }

    /** Top edge of the editor widget, relative to the GUI. */
    public int getEditorY() {
        return EDITOR_Y;
    }

    /** Height of the editor widget (inside the panel border). */
    public int getEditorHeight() {
        return getBottomTop() - EDITOR_Y;
    }

    /** Width of the editor widget (inside the panel border). */
    public int getEditorWidth() {
        return this.width - EDITOR_X - RIGHT_FRAME_WIDTH;
    }

    /** Left edge of the error icon, just left of the variable slot. */
    public int getErrorX() {
        return getVariableSlotX() - ERROR_LEFT_OF_VARIABLE_SLOT;
    }

    /** Top edge of the error icon. */
    public int getErrorY() {
        return this.height - ERROR_BOTTOM_INSET;
    }

    /**
     * Horizontal shift of the whole bottom slot cluster (player inventory,
     * variable slot and error icon).
     *
     * <p>Half of the extra width keeps the cluster centered under the editor
     * while the variable slot stays above the right edge of the player inventory
     * exactly as in the 256x240 design.</p>
     */
    public int getContentShiftX() {
        return getExtraWidth() / 2;
    }

    /** Left edge of the player inventory slot cell block, relative to the GUI. */
    public int getPlayerGridX() {
        return PLAYER_GRID_BASE_X + getContentShiftX();
    }

    /** Top edge of the player inventory slot cell block, relative to the GUI. */
    public int getPlayerGridY() {
        return this.height - PLAYER_GRID_FROM_BOTTOM;
    }

    /** Top edge of the hotbar slot cell row, relative to the GUI. */
    public int getHotbarY() {
        return this.height - HOTBAR_FROM_BOTTOM;
    }

    /** Left edge of the variable slot cell, relative to the GUI. */
    public int getVariableSlotX() {
        return VARIABLE_SLOT_BASE_X + getContentShiftX();
    }

    /** Top edge of the variable slot cell, relative to the GUI. */
    public int getVariableSlotY() {
        return this.height - VARIABLE_SLOT_FROM_BOTTOM;
    }

}
