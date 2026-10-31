package com.mpp.integratedscripting.client.gui.component.input;

import com.google.common.collect.Lists;
import com.mpp.integratedscripting.callback.TextStyle;
import com.mpp.integratedscripting.client.gui.component.TextLayout;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.component.GuiScrollBar;
import org.cyclops.cyclopscore.client.gui.component.input.IInputListener;
import org.cyclops.cyclopscore.helper.RenderHelpers;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Multi-line script editor widget for the 1.12.2 scripting terminal.
 *
 * <p>1.12.2 has no {@code TextFieldHelper} and {@code GuiTextField} is
 * single-line, so the widget is reimplemented on top of {@link TextLayout} and
 * {@link TextFieldHelperExtended}, following the rewrite plan in
 * docs/PORTING-1.12.2.md §7.9.3. The owning screen must call {@link #tick()},
 * {@link #mouseClicked(int, int, int)}, {@link #mouseClickMove(int, int, int, long)},
 * {@link #handleMouseInput()}, {@link #keyTyped(char, int)} and
 * {@link #draw(int, int)}.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiWidgetTextArea extends Gui {

    public static final int ROW_HEIGHT = 9;
    private static final int LINE_NUMBER_WIDTH = 14;
    // The editor is drawn on the light panel of the terminal texture, so the code
    // colours are dark; see TextStyle for the contrast ratios.
    private static final int TEXT_COLOR = TextStyle.DEFAULT_RGB;
    private static final int LINE_NUMBER_COLOR = 0x6E6E6E;
    private static final int SELECTION_COLOR = 0x80B0B0FF;
    private static final int CURSOR_COLOR = 0xFF000000;
    private static final long DOUBLE_CLICK_MILLIS = 250L;

    private final FontRenderer fontRenderer;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final boolean showLineNumbers;
    private final TextFieldHelperExtended helper;
    private final ScrollBar scrollBar;

    private boolean focused;
    private boolean canLoseFocus = true;
    private int frameTick;
    private int firstRow;
    private boolean syncingScrollBar;
    private long lastClickTime;
    private int lastClickIndex = -1;

    @Nullable
    private TextLayout layout;
    @Nullable
    private IInputListener listener;
    @Nullable
    private IInputListener listenerSelection;
    @Nullable
    private IInputListener listenerCursor;
    @Nullable
    private IMarkupProvider markupProvider;

    /**
     * @param fontRenderer The font renderer to measure and draw with.
     * @param x Left edge of the editor, in screen coordinates.
     * @param y Top edge of the editor, in screen coordinates.
     * @param width Width of the editor, including the line number gutter.
     * @param height Height of the editor.
     * @param scrollBar Whether an internal scroll bar should be shown and driven.
     * @param showLineNumbers Whether the line number gutter is drawn.
     */
    public GuiWidgetTextArea(FontRenderer fontRenderer, int x, int y, int width, int height,
                             boolean scrollBar, boolean showLineNumbers) {
        this.fontRenderer = fontRenderer;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.showLineNumbers = showLineNumbers;
        this.helper = new TextFieldHelperExtended(this::onValueChanged, this::onSelectionChanged,
                this::onCursorChanged);
        this.scrollBar = scrollBar
                ? new ScrollBar(x + width - 14, y, height, this::onScrollBarMoved, height / ROW_HEIGHT)
                : null;
        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(1);
        }
    }

    // ---------------------------------------------------------------- value / listeners

    public void setListener(@Nullable IInputListener listener) {
        this.listener = listener;
    }

    public void setListenerSelection(@Nullable IInputListener listenerSelection) {
        this.listenerSelection = listenerSelection;
    }

    public void setListenerCursor(@Nullable IInputListener listenerCursor) {
        this.listenerCursor = listenerCursor;
    }

    public void setMarkupProvider(@Nullable IMarkupProvider markupProvider) {
        this.markupProvider = markupProvider;
        this.layout = null;
    }

    public String getValue() {
        return this.helper.getValue();
    }

    /**
     * Replace the content and reset the cursor to the start, like selecting another
     * script file.
     */
    public void setValue(String value) {
        this.helper.setValue(value);
        this.firstRow = 0;
        this.layout = null;
    }

    public String getSelected() {
        return this.helper.getSelected();
    }

    public int getCursorPos() {
        return this.helper.getCursorPos();
    }

    public void setCursorPos(int pos) {
        this.helper.setCursorPos(pos, false);
    }

    public int getSelectionPos() {
        return this.helper.getSelectionPos();
    }

    public void setSelectionPos(int pos) {
        this.helper.setSelectionPos(pos);
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
    }

    public void tick() {
        this.frameTick++;
    }

    // ---------------------------------------------------------------- scrolling

    public int getFirstRow() {
        return firstRow;
    }

    /**
     * Scroll the editor so that {@code firstRowNew} is the topmost visible line.
     * The scroll bar handle is kept in sync, unless this call came from the scroll
     * bar itself.
     */
    public void setFirstRow(int firstRowNew) {
        TextLayout layoutCurrent = getLayout();
        int linesTotal = layoutCurrent.getLineCount();
        int clamped = Math.max(0, Math.min(firstRowNew, Math.max(0, linesTotal - getVisibleRows())));
        this.firstRow = clamped;

        if (this.scrollBar != null && !this.syncingScrollBar) {
            this.syncingScrollBar = true;
            try {
                this.scrollBar.setTotalRows(linesTotal);
                this.scrollBar.scrollTo(this.scrollBar.getScrollForRow(clamped));
            } finally {
                this.syncingScrollBar = false;
            }
        }
    }

    private void onScrollBarMoved(int firstRowNew) {
        this.syncingScrollBar = true;
        try {
            setFirstRow(firstRowNew);
        } finally {
            this.syncingScrollBar = false;
        }
    }

    public int getVisibleRows() {
        return Math.max(1, this.height / ROW_HEIGHT);
    }

    private void ensureCursorVisible() {
        TextLayout layoutCurrent = getLayout();
        int cursorLine = layoutCurrent.getLineIndexAt(this.helper.getCursorPos());
        if (cursorLine < this.firstRow) {
            setFirstRow(cursorLine);
        } else if (cursorLine >= this.firstRow + getVisibleRows()) {
            setFirstRow(cursorLine - getVisibleRows() + 1);
        }
    }

    // ---------------------------------------------------------------- input

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        boolean inside = isInside(mouseX, mouseY);
        if (this.canLoseFocus) {
            this.focused = inside;
        }
        if (!inside) {
            return;
        }
        if (mouseButton == 1) {
            // Right click selects everything, like the reference editor.
            this.focused = true;
            this.helper.selectAll();
            return;
        }
        if (mouseButton != 0) {
            return;
        }

        this.focused = true;
        int index = getIndexAtPosition(mouseX, mouseY);
        long now = System.currentTimeMillis();
        if (index == this.lastClickIndex && now - this.lastClickTime < DOUBLE_CLICK_MILLIS) {
            selectWord(index);
        } else {
            this.helper.setCursorPos(index, GuiScreen.isShiftKeyDown());
        }
        this.lastClickIndex = index;
        this.lastClickTime = now;
    }

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.focused && clickedMouseButton == 0) {
            this.helper.setCursorPos(getIndexAtPosition(mouseX, mouseY), true);
        }
    }

    public void handleMouseInput() {
        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(getLayout().getLineCount());
            this.scrollBar.handleMouseInput();
        }
    }

    /**
     * Process a key press.
     * @param typedChar The typed character.
     * @param keyCode The LWJGL key code.
     * @return Whether the key was consumed by the editor.
     */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!this.focused) {
            return false;
        }
        boolean shift = GuiScreen.isShiftKeyDown();
        boolean ctrl = GuiScreen.isCtrlKeyDown();

        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.helper.selectAll();
        } else if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            this.helper.copy();
        } else if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            this.helper.paste();
        } else if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            this.helper.cut();
        } else {
            switch (keyCode) {
                case 14: // backspace
                    if (ctrl) {
                        this.helper.deleteWords(-1);
                    } else {
                        this.helper.deleteFromCursor(-1);
                    }
                    break;
                case 211: // delete
                    if (ctrl) {
                        this.helper.deleteWords(1);
                    } else {
                        this.helper.deleteFromCursor(1);
                    }
                    break;
                case 203: // left
                    this.helper.moveBy(-1, shift, ctrl);
                    break;
                case 205: // right
                    this.helper.moveBy(1, shift, ctrl);
                    break;
                case 200: // up
                    this.helper.setCursorPos(getLayout().changeLine(this.helper.getCursorPos(), -1), shift);
                    break;
                case 208: // down
                    this.helper.setCursorPos(getLayout().changeLine(this.helper.getCursorPos(), 1), shift);
                    break;
                case 199: // home
                    this.helper.setCursorPos(ctrl ? 0
                            : getLayout().findLineStart(this.helper.getCursorPos()), shift);
                    break;
                case 207: // end
                    this.helper.setCursorPos(ctrl ? this.helper.getValue().length()
                            : getLayout().findLineEnd(this.helper.getCursorPos()), shift);
                    break;
                case 28: // enter
                case 156: // numpad enter
                    this.helper.insertText("\n");
                    break;
                case 15: // tab inserts four spaces, matching common editor behaviour
                    this.helper.insertText("    ");
                    break;
                default:
                    if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                        this.helper.insertText(Character.toString(typedChar));
                    } else {
                        return false;
                    }
                    break;
            }
        }
        ensureCursorVisible();
        return true;
    }

    private void selectWord(int index) {
        String value = this.helper.getValue();
        int start = index;
        int end = index;
        while (start > 0 && isWordChar(value.charAt(start - 1))) {
            start--;
        }
        while (end < value.length() && isWordChar(value.charAt(end))) {
            end++;
        }
        this.helper.setCursorPos(start, false);
        this.helper.setSelectionPos(end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * Whether a screen position is inside the editor, used by the terminal to
     * decide which scroll bar consumes the mouse wheel.
     */
    public boolean isInside(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + this.width
                && mouseY >= this.y && mouseY < this.y + this.height;
    }

    private int getIndexAtPosition(int mouseX, int mouseY) {
        TextLayout layoutCurrent = getLayout();
        int row = this.firstRow + (mouseY - this.y) / ROW_HEIGHT;
        if (row < 0) {
            return 0;
        }
        if (row >= layoutCurrent.getLineCount()) {
            return this.helper.getValue().length();
        }
        return layoutCurrent.getCharIndexAt(row, mouseX - this.x - getTextOffset());
    }

    // ---------------------------------------------------------------- rendering

    public void draw(int mouseX, int mouseY) {
        TextLayout layoutCurrent = getLayout();
        int textOffset = getTextOffset();
        int lastLineNumber = -1;

        for (int i = this.firstRow; i < Math.min(this.firstRow + getVisibleRows(),
                layoutCurrent.getLineCount()); i++) {
            TextLayout.Line line = layoutCurrent.getLine(i);
            int lineY = this.y + (i - this.firstRow) * ROW_HEIGHT;

            if (this.showLineNumbers && line.getLineNumber() != lastLineNumber) {
                RenderHelpers.drawScaledString(this.fontRenderer,
                        String.valueOf(line.getLineNumber()), this.x, lineY + 2, 0.5F,
                        LINE_NUMBER_COLOR, false);
            }
            lastLineNumber = line.getLineNumber();

            drawLine(line, this.x + textOffset, lineY);
        }

        drawSelection(layoutCurrent, textOffset);
        drawCursor(layoutCurrent, textOffset);

        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(layoutCurrent.getLineCount());
            if (this.scrollBar.needsScrollBars()) {
                this.scrollBar.drawGuiContainerBackgroundLayer(0, mouseX, mouseY);
            }
        }
    }

    private void drawLine(TextLayout.Line line, int lineX, int lineY) {
        List<Pair<TextFormatting, String>> segments = getMarkup(line.getText());
        int xCurrent = lineX;
        for (Pair<TextFormatting, String> segment : segments) {
            String text = segment.getRight();
            if (text.isEmpty()) {
                continue;
            }
            this.fontRenderer.drawString(text, xCurrent, lineY, colorOf(segment.getLeft()), false);
            xCurrent += this.fontRenderer.getStringWidth(text);
        }
    }

    private void drawSelection(TextLayout layoutCurrent, int textOffset) {
        int selStart = Math.min(this.helper.getCursorPos(), this.helper.getSelectionPos());
        int selEnd = Math.max(this.helper.getCursorPos(), this.helper.getSelectionPos());
        if (selStart == selEnd) {
            return;
        }

        for (int i = this.firstRow; i < Math.min(this.firstRow + getVisibleRows(),
                layoutCurrent.getLineCount()); i++) {
            TextLayout.Line line = layoutCurrent.getLine(i);
            int from = Math.max(selStart, line.getStart());
            int to = Math.min(selEnd, line.getEnd());
            if (to < from || to == from && selEnd <= line.getEnd()) {
                continue;
            }
            int lineY = this.y + (i - this.firstRow) * ROW_HEIGHT;
            int x1 = this.x + textOffset + layoutCurrent.getX(i, from);
            int x2 = this.x + textOffset + layoutCurrent.getX(i, to);
            drawRect(x1, lineY, x2, lineY + ROW_HEIGHT, SELECTION_COLOR);
            if (selEnd > line.getEnd()) {
                // Make a selected line break visible.
                drawRect(x2, lineY, x2 + 2, lineY + ROW_HEIGHT, SELECTION_COLOR);
            }
        }
    }

    private void drawCursor(TextLayout layoutCurrent, int textOffset) {
        if (!this.focused || this.frameTick / 6 % 2 != 0) {
            return;
        }
        int cursorLine = layoutCurrent.getLineIndexAt(this.helper.getCursorPos());
        if (cursorLine < this.firstRow || cursorLine >= this.firstRow + getVisibleRows()) {
            return;
        }
        int lineY = this.y + (cursorLine - this.firstRow) * ROW_HEIGHT;
        int cursorX = this.x + textOffset + layoutCurrent.getX(cursorLine, this.helper.getCursorPos());
        drawRect(cursorX, lineY - 1, cursorX + 1, lineY + ROW_HEIGHT, CURSOR_COLOR);
    }

    private List<Pair<TextFormatting, String>> getMarkup(String line) {
        if (this.markupProvider != null) {
            List<Pair<TextFormatting, String>> segments = this.markupProvider.markupLine(line);
            if (segments != null && !segments.isEmpty()) {
                return segments;
            }
        }
        List<Pair<TextFormatting, String>> segments = Lists.newArrayList();
        segments.add(Pair.of((TextFormatting) null, line));
        return segments;
    }

    private int colorOf(@Nullable TextFormatting style) {
        if (style == null) {
            return TEXT_COLOR;
        }
        // Semantic token styles carry a tuned RGB for this light editor panel;
        // anything else falls back to the regular TextFormatting colour code.
        return TextStyle.rgbOf(style,
                this.fontRenderer.getColorCode(style.toString().charAt(1)));
    }

    private int getTextOffset() {
        return this.showLineNumbers ? LINE_NUMBER_WIDTH : 0;
    }

    private int getTextWidth() {
        return Math.max(1, this.width - getTextOffset());
    }

    private TextLayout getLayout() {
        if (this.layout == null) {
            this.layout = TextLayout.build(this.helper.getValue(), getTextWidth(),
                    this.fontRenderer::getStringWidth);
        }
        return this.layout;
    }

    // ---------------------------------------------------------------- listener plumbing

    private void onValueChanged(String value) {
        this.layout = null;
        if (this.listener != null) {
            this.listener.onChanged();
        }
    }

    private void onSelectionChanged(String selected) {
        if (this.listenerSelection != null) {
            this.listenerSelection.onChanged();
        }
    }

    private void onCursorChanged(Integer cursorPos) {
        // A cursor move does not change the wrapped layout, so the layout cache is
        // deliberately kept: rebuilding it on every keystroke was O(value length x
        // line length) per cursor movement (docs/AUDIT.md D3).
        if (this.listenerCursor != null) {
            this.listenerCursor.onChanged();
        }
    }

    /**
     * Provides syntax highlighting for one line.
     */
    public interface IMarkupProvider {
        List<Pair<TextFormatting, String>> markupLine(String line);
    }

    /**
     * Scroll bar for the editor, which only draws itself when the content
     * overflows the visible rows.
     */
    private class ScrollBar extends GuiScrollBar {

        ScrollBar(int x, int y, int height, IScrollCallback scrollCallback, int visibleRows) {
            super(x, y, height, scrollCallback, visibleRows);
        }

        @Override
        public int getTotalRows() {
            return getLayout().getLineCount();
        }

        @Override
        protected boolean needsScrollBars() {
            return getTotalRows() > getVisibleRows();
        }

        float getScrollForRow(int row) {
            int steps = getTotalRows() - getVisibleRows();
            return steps <= 0 ? 0 : Math.min(1F, (float) row / (float) steps);
        }

    }

}
