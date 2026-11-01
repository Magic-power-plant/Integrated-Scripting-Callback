package com.mpp.integratedscripting.client.gui.component.input;

/**
 * The screen geometry of the completion dropdown, without any client class so it
 * can be unit tested.
 *
 * <p>The popup is anchored at the caret, grows to the widest row, is kept inside
 * the screen and flips above the caret line when there is not enough room below
 * it. {@link #getRowAt(int, int)} maps a mouse position to a visible row, which
 * is what makes the list clickable.</p>
 *
 * @author rubensworks
 */
public final class CompletionPopupLayout {

    /** Height of one row; matches the editor's row height. */
    public static final int ROW_HEIGHT = 9;
    /** Horizontal padding inside the popup. */
    public static final int PADDING_X = 3;
    /** Vertical padding inside the popup. */
    public static final int PADDING_Y = 2;
    /** The popup is at least this wide. */
    public static final int MIN_WIDTH = 80;
    /** The popup is at most this wide; longer labels are truncated. */
    public static final int MAX_WIDTH = 220;

    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int rowCount;

    /**
     * @param caretX The caret's screen x, used as the popup's preferred left edge.
     * @param caretBottom The screen y just below the caret line.
     * @param screenWidth The GUI width, to keep the popup on screen.
     * @param screenHeight The GUI height, to keep the popup on screen.
     * @param contentWidth The widest row's content width, without padding.
     * @param rowCount The number of rows to show.
     */
    public CompletionPopupLayout(int caretX, int caretBottom, int screenWidth, int screenHeight,
                                 int contentWidth, int rowCount) {
        int requestedRows = Math.max(0, rowCount);
        this.width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, contentWidth + PADDING_X * 2));
        int rows = Math.max(1, requestedRows);
        int desiredHeight = rows * ROW_HEIGHT + PADDING_Y * 2;
        // A tiny GUI must still show at least one row.
        this.height = Math.min(desiredHeight,
                Math.max(ROW_HEIGHT + PADDING_Y * 2, screenHeight - 2));
        // Never claim more rows than the clamped height can show.
        int fittingRows = Math.max(1, (this.height - PADDING_Y * 2) / ROW_HEIGHT);
        this.rowCount = Math.min(requestedRows, fittingRows);

        int left = Math.max(1, Math.min(caretX, screenWidth - this.width - 2));
        int top = caretBottom;
        if (top + this.height > screenHeight - 2) {
            // No room below the caret: flip the popup above the caret line.
            top = caretBottom - ROW_HEIGHT - this.height - 2;
        }
        this.x = left;
        this.y = Math.max(1, Math.min(top, Math.max(1, screenHeight - this.height - 2)));
    }

    /**
     * @return The left edge, in screen coordinates.
     */
    public int getX() {
        return x;
    }

    /**
     * @return The top edge, in screen coordinates.
     */
    public int getY() {
        return y;
    }

    /**
     * @return The popup width.
     */
    public int getWidth() {
        return width;
    }

    /**
     * @return The popup height.
     */
    public int getHeight() {
        return height;
    }

    /**
     * @return The number of rows the layout was built for.
     */
    public int getRowCount() {
        return rowCount;
    }

    /**
     * @param mouseX The mouse x, in screen coordinates.
     * @param mouseY The mouse y, in screen coordinates.
     * @return Whether the point is inside the popup.
     */
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /**
     * Map a screen point to a visible row of the popup.
     * @param mouseX The mouse x, in screen coordinates.
     * @param mouseY The mouse y, in screen coordinates.
     * @return The zero-based row, or {@code -1} when the point is not on a row.
     */
    public int getRowAt(int mouseX, int mouseY) {
        if (!contains(mouseX, mouseY)) {
            return -1;
        }
        int offset = mouseY - this.y - PADDING_Y;
        if (offset < 0) {
            return -1;
        }
        int row = offset / ROW_HEIGHT;
        if (row >= this.rowCount) {
            return -1;
        }
        return row;
    }

}
