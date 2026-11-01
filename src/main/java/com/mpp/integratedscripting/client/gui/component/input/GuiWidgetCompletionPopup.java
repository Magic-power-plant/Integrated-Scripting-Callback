package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

/**
 * The completion dropdown of the terminal editor.
 *
 * <p>This widget only draws what {@link CompletionListModel} selected: the model
 * owns filtering, selection and scrolling (and is unit tested without a client),
 * and {@link CompletionPopupLayout} owns the screen geometry (also unit tested),
 * so the drawing code stays a thin presentation layer. Each row shows a coloured
 * kind marker, the label with the typed prefix highlighted, and the item's
 * detail on the right; a thin bar on the right edge shows the scroll position
 * when the list is longer than the popup.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public final class GuiWidgetCompletionPopup {

    private static final int ROW_HEIGHT = CompletionPopupLayout.ROW_HEIGHT;
    private static final int PADDING_X = CompletionPopupLayout.PADDING_X;
    private static final int PADDING_Y = CompletionPopupLayout.PADDING_Y;
    /** Width reserved for the kind marker at the left of every row. */
    private static final int KIND_WIDTH = 6;
    /** Gap between the label and the right-aligned detail. */
    private static final int DETAIL_GAP = 8;
    /** Width reserved at the right edge when the list can scroll. */
    private static final int SCROLL_WIDTH = 4;

    private static final int BACKGROUND = 0xF01E1E1E;
    private static final int BORDER = 0xFF8A8A8A;
    private static final int SELECTED_BACKGROUND = 0xFF4A5FA8;
    private static final int TEXT_COLOR = 0xFFEDEDED;
    private static final int SELECTED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DETAIL_COLOR = 0xFFA8A8A8;
    private static final int MATCH_COLOR = 0xFFFFD75E;
    private static final int SCROLL_TRACK = 0xFF3A3A3A;
    private static final int SCROLL_THUMB = 0xFF8A8A8A;

    /**
     * Measure a list and compute where its popup goes.
     * @param fontRenderer The font renderer, for the row widths.
     * @param model The list model to lay out.
     * @param caretX The caret's screen x, used as the popup's left edge.
     * @param caretBottom The screen y just below the caret line.
     * @param screenWidth The GUI width, to keep the popup on screen.
     * @param screenHeight The GUI height, to keep the popup on screen.
     * @return The geometry, or {@code null} when there is nothing to show.
     */
    public CompletionPopupLayout layout(FontRenderer fontRenderer, CompletionListModel model,
                                        int caretX, int caretBottom, int screenWidth, int screenHeight) {
        if (model == null || model.isEmpty()) {
            return null;
        }
        int contentWidth = 0;
        for (CompletionItem item : model.getFiltered()) {
            int rowWidth = KIND_WIDTH + fontRenderer.getStringWidth(item.getLabel());
            String detail = item.getDetail();
            if (detail != null && !detail.isEmpty()) {
                rowWidth += DETAIL_GAP + fontRenderer.getStringWidth(detail);
            }
            contentWidth = Math.max(contentWidth, rowWidth);
        }
        return new CompletionPopupLayout(caretX, caretBottom, screenWidth, screenHeight,
                contentWidth, Math.max(1, model.getDrawnRows()));
    }

    /**
     * Draw the popup.
     * @param fontRenderer The font renderer.
     * @param model The list model to render.
     * @param layout The geometry from {@link #layout(FontRenderer, CompletionListModel, int, int, int, int)}.
     */
    public void draw(FontRenderer fontRenderer, CompletionListModel model, CompletionPopupLayout layout) {
        if (model == null || layout == null || model.isEmpty()) {
            return;
        }
        List<CompletionItem> items = model.getFiltered();
        int x = layout.getX();
        int y = layout.getY();
        int width = layout.getWidth();
        int height = layout.getHeight();

        Gui.drawRect(x, y, x + width, y + height, BACKGROUND);
        Gui.drawRect(x, y, x + width, y + 1, BORDER);
        Gui.drawRect(x, y + height - 1, x + width, y + height, BORDER);
        Gui.drawRect(x, y, x + 1, y + height, BORDER);
        Gui.drawRect(x + width - 1, y, x + width, y + height, BORDER);

        int first = model.getFirstVisibleRow();
        int rows = layout.getRowCount();
        boolean scrollable = items.size() > rows;
        int scrollSpace = scrollable ? SCROLL_WIDTH : 0;

        for (int row = 0; row < rows; row++) {
            int index = first + row;
            if (index >= items.size()) {
                break;
            }
            CompletionItem item = items.get(index);
            int rowY = y + PADDING_Y + row * ROW_HEIGHT;
            boolean selected = index == model.getSelectedIndex();
            if (selected) {
                Gui.drawRect(x + 1, rowY, x + width - 1 - scrollSpace, rowY + ROW_HEIGHT,
                        SELECTED_BACKGROUND);
            }

            int kindX = x + PADDING_X;
            Gui.drawRect(kindX, rowY + 3, kindX + 3, rowY + 6, colorOf(item.getKind()));

            int labelX = kindX + KIND_WIDTH;
            String detail = item.getDetail();
            int detailWidth = detail == null || detail.isEmpty()
                    ? 0 : fontRenderer.getStringWidth(detail) + DETAIL_GAP;
            int labelWidth = Math.max(1, width - PADDING_X * 2 - KIND_WIDTH - scrollSpace
                    - detailWidth - 2);
            String label = truncate(fontRenderer, item.getLabel(), labelWidth);
            drawLabel(fontRenderer, label, model.getFilterPrefix(), labelX, rowY + 1,
                    selected ? SELECTED_TEXT_COLOR : TEXT_COLOR);

            if (detailWidth > 0
                    && detailWidth + KIND_WIDTH + PADDING_X * 2 + 8 <= width) {
                int detailX = x + width - PADDING_X - scrollSpace
                        - fontRenderer.getStringWidth(detail);
                fontRenderer.drawString(detail, detailX, rowY + 1, DETAIL_COLOR, false);
            }
        }

        if (scrollable) {
            drawScrollBar(x + width - SCROLL_WIDTH, y, height, items.size(), rows, first);
        }
    }

    /**
     * Draw a label with the part that matches the typed prefix highlighted, like
     * CodeMirror's completion list.
     */
    private void drawLabel(FontRenderer fontRenderer, String label, String prefix,
                           int x, int y, int color) {
        int match = commonPrefixLength(label, prefix);
        if (match <= 0) {
            fontRenderer.drawString(label, x, y, color, false);
            return;
        }
        String matched = label.substring(0, match);
        fontRenderer.drawString(matched, x, y, MATCH_COLOR, false);
        fontRenderer.drawString(label.substring(match), x + fontRenderer.getStringWidth(matched),
                y, color, false);
    }

    private static int commonPrefixLength(String label, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return 0;
        }
        int length = Math.min(label.length(), prefix.length());
        int i = 0;
        while (i < length && Character.toLowerCase(label.charAt(i))
                == Character.toLowerCase(prefix.charAt(i))) {
            i++;
        }
        return i;
    }

    private void drawScrollBar(int x, int popupY, int popupHeight, int total, int rows, int first) {
        int top = popupY + PADDING_Y;
        int trackHeight = popupHeight - PADDING_Y * 2;
        if (trackHeight <= 0) {
            return;
        }
        Gui.drawRect(x, top, x + 2, top + trackHeight, SCROLL_TRACK);
        int thumbHeight = Math.max(4, trackHeight * rows / total);
        int maxFirst = Math.max(1, total - rows);
        int thumbTop = top + (trackHeight - thumbHeight) * first / maxFirst;
        Gui.drawRect(x, thumbTop, x + 2, thumbTop + thumbHeight, SCROLL_THUMB);
    }

    /**
     * @param kind A completion kind; {@code null} is treated as a variable.
     * @return The colour of its marker in the dropdown.
     */
    private static int colorOf(CompletionKind kind) {
        if (kind == null) {
            return 0xFF82AAFF;
        }
        switch (kind) {
            case KEYWORD:
                return 0xFFC792EA;
            case FUNCTION:
                return 0xFF7FDBCA;
            case PROPERTY:
                return 0xFFFFCB6B;
            case OPERATOR:
                return 0xFFFF9CAC;
            case MODULE:
                return 0xFFA8A8A8;
            case VARIABLE:
            default:
                return 0xFF82AAFF;
        }
    }

    private static String truncate(FontRenderer fontRenderer, String text, int maxWidth) {
        if (fontRenderer.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String result = text;
        while (result.length() > 1 && fontRenderer.getStringWidth(result + "...") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result + "...";
    }

}
