package com.mpp.integratedscripting.client.gui.component;

/**
 * Geometry of the scripting terminal's header row.
 *
 * <p>The terminal background texture ({@code textures/gui/terminal_scripting.png})
 * has two grey boxes in its top band ({@code y} 3..16): a narrow one at
 * {@code x} 3..76 and a wide one at {@code x} 82..252. The 1.20 reference screen
 * puts the disk selector in the narrow box and the part name in the wide one
 * ({@code titleLabelX = 88}, {@code titleLabelY = 6}), which is what this class
 * mirrors.</p>
 *
 * <p>The 1.12 {@code GuiMultipart} base class draws the part name at (8, 6), the
 * very column where the disk label and the arrowed disk field start, so the
 * terminal has to place its title itself (see
 * {@code GuiTerminalScripting#drawGuiContainerForegroundLayer}). The disk label
 * is right-aligned against the field so it stays clear of it for locales of
 * different lengths (both shipped locales fit: {@code "Disk:"} is about 22 px,
 * {@code "磁盘:"} about 20 px, and the label area is 24 px wide).</p>
 *
 * @author rubensworks
 */
public final class TerminalHeaderLayout {

    /** Part name: inside the wide header box, matching the 1.20 reference. */
    public static final int TITLE_X = 88;
    public static final int TITLE_Y = 6;

    /** Colour of the header texts; the header band is a light grey box. */
    public static final int TEXT_COLOR = 0xFF404040;

    /** Narrow header box of the background texture. */
    public static final int HEADER_BOX_LEFT = 3;
    public static final int HEADER_BOX_RIGHT = 77;

    /** Arrowed disk field, as specified by the 1.20 reference. */
    public static final int DISK_FIELD_X = 30;
    public static final int DISK_FIELD_Y = 4;
    public static final int DISK_FIELD_WIDTH = 42;
    public static final int DISK_FIELD_HEIGHT = 15;

    /** Disk label, drawn to the left of the disk field. */
    public static final int DISK_LABEL_Y = 6;
    public static final int DISK_LABEL_GAP = 3;
    /** Widest label the header box can hold in front of the disk field. */
    public static final int DISK_LABEL_MAX_WIDTH = DISK_FIELD_X - DISK_LABEL_GAP - HEADER_BOX_LEFT;

    /**
     * Left edge for the disk label: right-aligned so that it ends
     * {@link #DISK_LABEL_GAP} pixels before the field, but never left of the
     * header box.
     *
     * @param labelWidth Width of the localised label in pixels.
     * @return The label's left edge, relative to the GUI.
     */
    public static int diskLabelX(int labelWidth) {
        return Math.max(HEADER_BOX_LEFT, DISK_FIELD_X - DISK_LABEL_GAP - labelWidth);
    }

    private TerminalHeaderLayout() {
    }

}
