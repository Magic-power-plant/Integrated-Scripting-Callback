package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;
import com.mpp.integratedscripting.api.language.edit.ICommentStyle;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.edit.IIdentifierSyntax;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.callback.TextStyle;
import com.mpp.integratedscripting.client.gui.component.TextLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.component.GuiScrollBar;
import org.cyclops.cyclopscore.client.gui.component.input.IInputListener;
import org.cyclops.cyclopscore.helper.RenderHelpers;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

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
 * <p>The editing experience follows CodeMirror's commands and extensions:</p>
 *
 * <ul>
 *   <li>Enter auto-indents, opens an indented block after {@code {}/{@code (}/{@code [}
 *   and splits an empty {@code {} into three lines ({@link EditorInputRules#newline}).</li>
 *   <li>Brackets and quotes auto-close, skip over an existing closer, wrap the
 *   selection and are suppressed in strings/comments ({@link EditorInputRules#handleInsert}).
 *   Backspace deletes an empty pair as one unit.</li>
 *   <li>Tab indents the selected lines or inserts elastic spaces to the next tab
 *   stop; Shift-Tab dedents. Ctrl-/ toggles line comments, Ctrl-D duplicates a
 *   line, Ctrl-Shift-K deletes it, Alt-Up/Down move lines.</li>
 *   <li>Typing an identifier opens the completion popup after two characters
 *   (CodeMirror's {@code activateOnTyping}); Ctrl-Space opens it explicitly and
 *   {@code .} opens member candidates. Typing narrows the list, the arrow/page
 *   keys move, Enter/Tab or a mouse click accepts and Escape closes it.</li>
 *   <li>Ctrl-Z / Ctrl-Y / Ctrl-Shift-Z undo and redo grouped typing
 *   ({@link EditorHistory}).</li>
 *   <li>Home is a smart home, Up/Down keep the goal column, Ctrl-Home/End and
 *   PageUp/PageDown jump like CodeMirror, and the active line and the matching
 *   bracket are highlighted.</li>
 * </ul>
 *
 * <p>Everything language specific comes from an {@link ILanguageServices} bundle
 * that the owning screen resolves <em>once</em> when the edited file changes
 * (docs/LANGUAGE-API-REFACTOR.md §4.5): highlighting and lexical context from
 * {@link IHighlightService}, indentation/comments/identifiers from
 * {@link IEditSupport}, candidates from {@link ICompletionService}. This widget
 * never looks a language up while drawing or handling keys.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiWidgetTextArea extends Gui {

    public static final int ROW_HEIGHT = 9;
    private static final int LINE_NUMBER_WIDTH = 14;
    private static final int SCROLL_BAR_WIDTH = 14;
    // The editor is drawn on the light panel of the terminal texture, so the code
    // colours are dark; see TextStyle for the contrast ratios.
    private static final int TEXT_COLOR = TextStyle.DEFAULT_RGB;
    private static final int LINE_NUMBER_COLOR = 0x6E6E6E;
    private static final int LINE_NUMBER_ACTIVE_COLOR = 0x2E2E2E;
    private static final int SELECTION_COLOR = 0x80B0B0FF;
    private static final int CURSOR_COLOR = 0xFF000000;
    /** CodeMirror-style active line tint, subtle on the light panel. */
    private static final int ACTIVE_LINE_COLOR = 0x146E82F0;
    /** Matching-bracket highlight, also used behind the bracket under the caret. */
    private static final int BRACKET_MATCH_COLOR = 0x556E82F0;
    private static final long DOUBLE_CLICK_MILLIS = 250L;
    /**
     * Minimum identifier length before completion opens by itself while typing,
     * so a single character does not flash a huge keyword list.
     */
    private static final int AUTO_TRIGGER_MIN_PREFIX = 2;

    private final FontRenderer fontRenderer;
    private int x;
    private int y;
    private int width;
    private int height;
    private final boolean showLineNumbers;
    private final TextFieldHelperExtended helper;
    private ScrollBar scrollBar;
    private final EditorHistory history = new EditorHistory();
    private final TextLayout.IWidthProvider widthProvider;
    private final GuiWidgetCompletionPopup completionPopup = new GuiWidgetCompletionPopup();

    private boolean focused;
    private boolean canLoseFocus = true;
    private int frameTick;
    private int firstRow;
    private boolean syncingScrollBar;
    private long lastClickTime;
    private int lastClickIndex = -1;
    private int clickCount;
    /** Pixel column that Up/Down try to keep; -1 means "recompute from the caret". */
    private int goalColumn = -1;
    /** True while the current mutation is typing, so history can group it. */
    private boolean typingInput;
    /** True while undo/redo applies a snapshot, so it is not recorded again. */
    private boolean historyApplying;

    /** One indentation step of the current language. */
    private String indentUnit = EditorInputRules.INDENT_UNIT;
    /** Elastic-tab width of the current language, in spaces. */
    private int tabSize = EditorInputRules.TAB_SIZE;
    /** Line comment prefix of the current language, or null when it has none. */
    @Nullable
    private String lineCommentPrefix = "//";

    @Nullable
    private ILanguageServices languageServices;
    @Nullable
    private IHighlightService highlightService;
    @Nullable
    private IEditSupport editSupport;
    @Nullable
    private ICompletionService completionService;

    @Nullable
    private TextLayout layout;
    private boolean layoutDirty = true;
    @Nullable
    private IInputListener listener;
    @Nullable
    private IInputListener listenerSelection;
    @Nullable
    private IInputListener listenerCursor;

    @Nullable
    private List<List<Token>> highlightedLines;
    @Nullable
    private String highlightedValue;

    @Nullable
    private DocumentText document;
    @Nullable
    private String documentValue;

    @Nullable
    private CompletionListModel completionModel;
    private String completionPrefix = "";
    /** Geometry of the last drawn popup, reused for mouse hit testing. */
    @Nullable
    private CompletionPopupLayout completionPopupLayout;
    /**
     * Word start at which the user dismissed the popup with Escape; auto-open
     * stays quiet while the caret is still inside that word (-1 means "none").
     */
    private int completionSuppressedWordStart = -1;

    @Nullable
    private int[] bracketMatch;
    private int bracketMatchCursor = -1;
    @Nullable
    private TextLayout bracketMatchLayout;

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
        this.widthProvider = new TextLayout.ICharWidthProvider() {
            @Override
            public int getCharWidth(char c) {
                return fontRenderer.getCharWidth(c);
            }

            @Override
            public int getWidth(String text) {
                return fontRenderer.getStringWidth(text);
            }
        };
        this.helper = new TextFieldHelperExtended(this::onValueChanged, this::onSelectionChanged,
                this::onCursorChanged);
        this.helper.setEditListener(this::onEdited);
        this.scrollBar = scrollBar
                ? new ScrollBar(x + width - SCROLL_BAR_WIDTH, y, height, this::onScrollBarMoved,
                height / ROW_HEIGHT)
                : null;
        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(1);
        }
    }

    /**
     * Move and/or resize the editor in place.
     *
     * <p>Used when the terminal is resized with the game window: the document,
     * caret, selection, undo history and scroll row are kept, only the wrapped
     * layout and the internal scroll bar are rebuilt for the new bounds. Recreating
     * the widget instead would drop all of that state.</p>
     *
     * @param x Left edge, in screen coordinates.
     * @param y Top edge, in screen coordinates.
     * @param width New width, including the line number gutter.
     * @param height New height.
     */
    public void setBounds(int x, int y, int width, int height) {
        if (this.x == x && this.y == y && this.width == width && this.height == height) {
            return;
        }
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (this.scrollBar != null) {
            this.scrollBar = new ScrollBar(x + width - SCROLL_BAR_WIDTH, y, height,
                    this::onScrollBarMoved, height / ROW_HEIGHT);
        }
        // The wrap width changed, so the cached layout and everything derived from
        // it (bracket match, completion popup) is stale.
        this.layout = null;
        this.layoutDirty = true;
        this.goalColumn = -1;
        this.bracketMatch = null;
        this.bracketMatchLayout = null;
        closeCompletion();
        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(getLayout().getLineCount());
            setFirstRow(this.firstRow);
        }
        // If the editor shrank, keep the caret in view instead of leaving it below
        // the new last visible row.
        ensureCursorVisible();
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

    /**
     * Attach the language services of the file being edited.
     *
     * <p>The owning screen resolves these once per selected file, which is why the
     * drawing and key-handling paths below contain no registry lookup: they read
     * this bundle (and its always-present default implementations).</p>
     * @param languageServices The resolved services, or {@code null} for plain text.
     */
    public void setLanguageServices(@Nullable ILanguageServices languageServices) {
        this.languageServices = languageServices;
        this.highlightService = languageServices == null
                ? null : languageServices.get(LanguageServiceKeys.HIGHLIGHT);
        this.editSupport = languageServices == null
                ? null : languageServices.get(LanguageServiceKeys.EDIT);
        this.completionService = languageServices == null
                ? null : languageServices.get(LanguageServiceKeys.COMPLETION);

        if (this.editSupport == null) {
            this.indentUnit = EditorInputRules.INDENT_UNIT;
            this.tabSize = EditorInputRules.TAB_SIZE;
            this.lineCommentPrefix = "//";
        } else {
            this.indentUnit = this.editSupport.getIndentUnit();
            this.tabSize = tabSizeOf(this.indentUnit);
            ICommentStyle commentStyle = this.editSupport.getCommentStyle();
            this.lineCommentPrefix = commentStyle == null ? null : commentStyle.getLineComment();
        }
        closeCompletion();
        this.completionSuppressedWordStart = -1;
        invalidateHighlight();
        invalidateDocument();
    }

    private static int tabSizeOf(String indentUnit) {
        if (indentUnit == null || indentUnit.isEmpty()) {
            return EditorInputRules.TAB_SIZE;
        }
        for (int i = 0; i < indentUnit.length(); i++) {
            if (indentUnit.charAt(i) != ' ') {
                return EditorInputRules.TAB_SIZE;
            }
        }
        return indentUnit.length();
    }

    public String getValue() {
        return this.helper.getValue();
    }

    /**
     * Replace the content and reset the cursor to the start, like selecting another
     * script file. Clears the undo history, because the old document is gone.
     */
    public void setValue(String value) {
        this.historyApplying = true;
        try {
            this.helper.setValue(value);
        } finally {
            this.historyApplying = false;
        }
        this.history.reset();
        this.firstRow = 0;
        this.layout = null;
        this.layoutDirty = true;
        this.goalColumn = -1;
        this.bracketMatch = null;
        closeCompletion();
        this.completionSuppressedWordStart = -1;
        invalidateHighlight();
        invalidateDocument();
    }

    /**
     * Replace the content of the currently edited file (e.g. the server pushed an
     * update) while keeping the caret inside the new value.
     *
     * <p>Replacing the text with {@link #setValue} would jump the caret to the
     * start of the file; docs/AUDIT.md R3 records that regression.</p>
     */
    public void setValuePassive(String value) {
        boolean changed = value != null && !value.equals(this.helper.getValue());
        this.historyApplying = true;
        try {
            this.helper.setValuePassive(value);
        } finally {
            this.historyApplying = false;
        }
        if (changed) {
            // The document was replaced from outside the editor, so old undo
            // states no longer describe it. An echo of the player's own edit
            // has the same value and leaves the history alone.
            this.history.reset();
            this.layout = null;
            this.layoutDirty = true;
            this.goalColumn = -1;
            this.bracketMatch = null;
            closeCompletion();
            this.completionSuppressedWordStart = -1;
            invalidateHighlight();
            invalidateDocument();
        }
    }

    public String getSelected() {
        return this.helper.getSelected();
    }

    public int getCursorPos() {
        return this.helper.getCursorPos();
    }

    public void setCursorPos(int pos) {
        this.helper.setCursorPos(pos, false);
        this.goalColumn = -1;
        ensureCursorVisible();
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
        if (!focused) {
            this.goalColumn = -1;
            closeCompletion();
        }
    }

    public void setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
    }

    /**
     * @return The cached immutable view of the current value, used by the owning
     *         screen for the caret position display.
     */
    public DocumentText getDocument() {
        String value = this.helper.getValue();
        if (this.document == null || this.documentValue == null
                || !this.documentValue.equals(value)) {
            this.document = DocumentText.of(value);
            this.documentValue = value;
        }
        return this.document;
    }

    public void tick() {
        this.frameTick++;
    }

    /**
     * Dismiss the completion popup, used when another widget consumes a click or a
     * key that should close it.
     */
    public void dismissCompletion() {
        closeCompletion();
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
        // A left click on the popup picks that row instead of moving the caret.
        if (mouseButton == 0 && acceptCompletionAt(mouseX, mouseY)) {
            return;
        }
        closeCompletion();
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
        this.goalColumn = -1;
        int index = getIndexAtPosition(mouseX, mouseY);
        long now = System.currentTimeMillis();
        if (index == this.lastClickIndex && now - this.lastClickTime < DOUBLE_CLICK_MILLIS) {
            this.clickCount++;
        } else {
            this.clickCount = 1;
        }
        this.lastClickIndex = index;
        this.lastClickTime = now;

        if (this.clickCount == 2) {
            selectWord(index);
        } else if (this.clickCount >= 3) {
            selectLine(index);
        } else {
            this.helper.setCursorPos(index, GuiScreen.isShiftKeyDown());
        }
    }

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (!this.focused || clickedMouseButton != 0) {
            return;
        }
        // CodeMirror scrolls the view when the drag leaves the editor.
        if (mouseY < this.y && this.firstRow > 0) {
            setFirstRow(this.firstRow - 1);
        } else if (mouseY >= this.y + this.height) {
            setFirstRow(this.firstRow + 1);
        }
        this.helper.setCursorPos(getIndexAtPosition(mouseX, mouseY), true);
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
        this.typingInput = false;
        boolean shift = GuiScreen.isShiftKeyDown();
        boolean ctrl = GuiScreen.isCtrlKeyDown();
        boolean alt = GuiScreen.isAltKeyDown();
        String value = this.helper.getValue();

        // The completion popup owns these keys while it is open.
        if (this.completionModel != null && !ctrl && !alt) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                closeCompletion();
                suppressCompletion();
                return true;
            }
            if (keyCode == Keyboard.KEY_UP) {
                this.completionModel.moveSelection(-1);
                return true;
            }
            if (keyCode == Keyboard.KEY_DOWN) {
                this.completionModel.moveSelection(1);
                return true;
            }
            if (keyCode == Keyboard.KEY_PRIOR) {
                this.completionModel.movePage(-1);
                return true;
            }
            if (keyCode == Keyboard.KEY_NEXT) {
                this.completionModel.movePage(1);
                return true;
            }
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER
                    || keyCode == Keyboard.KEY_TAB) {
                if (commitCompletion()) {
                    return true;
                }
            }
        }
        if (this.completionModel != null && !keepsCompletionOpen(typedChar, keyCode)) {
            closeCompletion();
        }

        // Ctrl-Space is the explicit completion trigger of CodeMirror. It also
        // re-arms auto-completion after Escape dismissed the current word.
        if (ctrl && !alt && keyCode == Keyboard.KEY_SPACE) {
            this.completionSuppressedWordStart = -1;
            requestCompletion((char) 0);
            return true;
        }

        // Any other shortcut (undo, line commands, ...) invalidates the list.
        if (this.completionModel != null && (ctrl || alt)) {
            closeCompletion();
        }

        // History first, so Ctrl-Z also works while a selection exists.
        if (ctrl && !alt && keyCode == Keyboard.KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            afterKey();
            return true;
        }
        if (ctrl && !alt && keyCode == Keyboard.KEY_Y) {
            redo();
            afterKey();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlA(keyCode)) {
            this.helper.selectAll();
            afterKey();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            this.helper.copy();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            this.helper.paste();
            afterKey();
            return true;
        }
        if (GuiScreen.isKeyComboCtrlX(keyCode)) {
            this.helper.cut();
            afterKey();
            return true;
        }

        // Line commands from CodeMirror's default keymap.
        if (ctrl && !alt && (keyCode == Keyboard.KEY_SLASH || keyCode == Keyboard.KEY_DIVIDE)) {
            String prefix = this.lineCommentPrefix;
            if (prefix != null && !prefix.isEmpty()) {
                apply(EditorInputRules.toggleLineComment(value, this.helper.getCursorPos(),
                        this.helper.getSelectionPos(), prefix));
            }
            afterKey();
            return true;
        }
        if (ctrl && !alt && keyCode == Keyboard.KEY_D) {
            apply(EditorInputRules.duplicateLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos()));
            afterKey();
            return true;
        }
        if (ctrl && shift && keyCode == Keyboard.KEY_K) {
            apply(EditorInputRules.deleteLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos()));
            afterKey();
            return true;
        }
        if (alt && !ctrl && keyCode == Keyboard.KEY_UP) {
            apply(EditorInputRules.moveLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos(), -1));
            afterKey();
            return true;
        }
        if (alt && !ctrl && keyCode == Keyboard.KEY_DOWN) {
            apply(EditorInputRules.moveLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos(), 1));
            afterKey();
            return true;
        }
        if (ctrl && !alt && keyCode == Keyboard.KEY_RBRACKET) {
            apply(EditorInputRules.indentLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos(), this.indentUnit, 1));
            afterKey();
            return true;
        }
        if (ctrl && !alt && keyCode == Keyboard.KEY_LBRACKET) {
            apply(EditorInputRules.indentLines(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos(), this.indentUnit, -1));
            afterKey();
            return true;
        }

        switch (keyCode) {
            case Keyboard.KEY_TAB:
                if (shift) {
                    apply(EditorInputRules.indentLines(value, this.helper.getCursorPos(),
                            this.helper.getSelectionPos(), this.indentUnit, -1));
                } else {
                    handleTab(value);
                }
                break;
            case Keyboard.KEY_RETURN:
            case Keyboard.KEY_NUMPADENTER:
                this.typingInput = true;
                if (ctrl) {
                    apply(EditorInputRules.insertBlankLine(value, this.helper.getCursorPos(),
                            this.helper.getSelectionPos()));
                } else {
                    apply(EditorInputRules.newline(value, this.helper.getCursorPos(),
                            this.helper.getSelectionPos(), this.indentUnit,
                            getContextAt(Math.min(this.helper.getCursorPos(), this.helper.getSelectionPos()))));
                }
                break;
            case Keyboard.KEY_BACK:
                this.typingInput = true;
                if (ctrl) {
                    this.helper.deleteWords(-1);
                } else {
                    int[] pair = this.helper.hasSelection() ? null
                            : EditorInputRules.emptyPair(value, this.helper.getCursorPos());
                    int[] indent = pair == null && !this.helper.hasSelection()
                            ? EditorInputRules.indentationBackspaceRange(value, this.helper.getCursorPos())
                            : null;
                    if (pair != null) {
                        this.helper.replaceRange(pair[0], pair[1], "");
                    } else if (indent != null) {
                        this.helper.replaceRange(indent[0], indent[1], "");
                    } else {
                        this.helper.deleteFromCursor(-1);
                    }
                }
                break;
            case Keyboard.KEY_DELETE:
                this.typingInput = true;
                if (ctrl) {
                    this.helper.deleteWords(1);
                } else {
                    int[] pair = this.helper.hasSelection() ? null
                            : EditorInputRules.emptyPair(value, this.helper.getCursorPos());
                    if (pair != null) {
                        this.helper.replaceRange(pair[0], pair[1], "");
                    } else {
                        this.helper.deleteFromCursor(1);
                    }
                }
                break;
            case Keyboard.KEY_LEFT:
                this.helper.moveBy(-1, shift, ctrl);
                this.goalColumn = -1;
                break;
            case Keyboard.KEY_RIGHT:
                this.helper.moveBy(1, shift, ctrl);
                this.goalColumn = -1;
                break;
            case Keyboard.KEY_UP:
                if (ctrl) {
                    this.helper.setCursorPos(0, shift);
                } else {
                    moveVertical(-1, shift);
                }
                break;
            case Keyboard.KEY_DOWN:
                if (ctrl) {
                    this.helper.setCursorPos(value.length(), shift);
                } else {
                    moveVertical(1, shift);
                }
                break;
            case Keyboard.KEY_HOME:
                if (ctrl) {
                    this.helper.setCursorToStart(shift);
                } else {
                    this.helper.setCursorPos(EditorInputRules.smartHome(value, this.helper.getCursorPos()), shift);
                }
                this.goalColumn = -1;
                break;
            case Keyboard.KEY_END:
                if (ctrl) {
                    this.helper.setCursorToEnd(shift);
                } else {
                    this.helper.setCursorPos(EditorInputRules.lineEnd(value, this.helper.getCursorPos()), shift);
                }
                this.goalColumn = -1;
                break;
            case Keyboard.KEY_PRIOR:
                movePage(-1, shift);
                break;
            case Keyboard.KEY_NEXT:
                movePage(1, shift);
                break;
            default:
                if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                    this.typingInput = true;
                    handleTypedCharacter(typedChar);
                } else {
                    return false;
                }
                break;
        }
        if (this.completionModel != null
                && (keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE)) {
            // Deleting characters widens the prefix again; the fetched candidates
            // are still complete, so only the local filter changes.
            refreshCompletionPrefix();
        }
        afterKey();
        return true;
    }

    /**
     * Whether a key keeps the completion popup open: typing characters and
     * deleting narrow the list, everything else (arrows, shortcuts, ...) closes it.
     */
    private static boolean keepsCompletionOpen(char typedChar, int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
                || keyCode == 0 || ChatAllowedCharacters.isAllowedCharacter(typedChar);
    }

    private void afterKey() {
        ensureCursorVisible();
    }

    private void handleTab(String value) {
        int cursor = this.helper.getCursorPos();
        int anchor = this.helper.getSelectionPos();
        if (EditorInputRules.lineStart(value, Math.min(cursor, anchor))
                != EditorInputRules.lineStart(value, Math.max(cursor, anchor))) {
            apply(EditorInputRules.indentLines(value, cursor, anchor, this.indentUnit, 1));
            return;
        }
        this.typingInput = true;
        int spaces = EditorInputRules.tabSpaces(value, cursor, this.tabSize);
        this.helper.insertText(spaces(spaces));
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    private void handleTypedCharacter(char typedChar) {
        String value = this.helper.getValue();
        int cursor = this.helper.getCursorPos();
        int anchor = this.helper.getSelectionPos();
        // Querying the lexical context scans from the start of the document, so
        // only bracket and quote keys pay for it.
        int context = EditorInputRules.isOpeningBracket(typedChar)
                || EditorInputRules.isClosingBracket(typedChar)
                || EditorInputRules.isQuote(typedChar)
                ? getContextAt(Math.min(cursor, anchor)) : LexicalContext.CODE;
        EditorEdit edit = EditorInputRules.handleInsert(value, cursor, anchor, typedChar, context,
                this.indentUnit);
        if (edit != null) {
            apply(edit);
        } else {
            this.helper.insertText(Character.toString(typedChar));
        }

        IIdentifierSyntax syntax = getIdentifierSyntax();
        boolean identifierChar = syntax != null && syntax.isIdentifierPart(typedChar);
        if (!identifierChar) {
            // A new word (or a dot) ends an Escape suppression.
            this.completionSuppressedWordStart = -1;
        }
        if (this.completionModel != null) {
            if (identifierChar) {
                // Narrow the already fetched candidates instead of asking again.
                this.completionPrefix = this.completionPrefix + typedChar;
                this.completionModel.setFilter(this.completionPrefix);
                if (this.completionModel.isEmpty()) {
                    closeCompletion();
                }
            } else {
                closeCompletion();
                maybeTriggerCompletion(typedChar);
            }
        } else {
            maybeTriggerCompletion(typedChar);
            maybeAutoTriggerCompletion(typedChar, identifierChar);
        }
    }

    private void maybeTriggerCompletion(char typedChar) {
        ICompletionService service = this.completionService;
        if (service == null) {
            return;
        }
        Set<Character> triggers = service.getTriggerCharacters();
        if (triggers != null && triggers.contains(typedChar)) {
            requestCompletion(typedChar);
        }
    }

    /**
     * CodeMirror's {@code activateOnTyping}: open the popup while the player
     * types an identifier, once the word is long enough to be meaningful. The
     * candidates are fetched once per request; later characters only narrow the
     * list (see {@link #handleTypedCharacter(char)}).
     */
    private void maybeAutoTriggerCompletion(char typedChar, boolean identifierChar) {
        if (!identifierChar || this.completionService == null || this.helper.hasSelection()) {
            return;
        }
        int cursor = this.helper.getCursorPos();
        String value = this.helper.getValue();
        int wordStart = wordStartAt(value, cursor);
        if (wordStart == this.completionSuppressedWordStart) {
            // Escape dismissed completion for this word.
            return;
        }
        if (cursor - wordStart < AUTO_TRIGGER_MIN_PREFIX) {
            return;
        }
        IIdentifierSyntax syntax = getIdentifierSyntax();
        if (syntax == null || !syntax.isIdentifierStart(value.charAt(wordStart))) {
            // Numbers and other non-identifier tokens never open the popup.
            return;
        }
        requestCompletion(typedChar);
    }

    private int getContextAt(int pos) {
        if (this.highlightService == null) {
            return LexicalContext.CODE;
        }
        return this.highlightService.lexicalContextAt(getDocument(), pos);
    }

    /**
     * Apply a pure rule result, moving the caret and selection together.
     */
    private void apply(@Nullable EditorEdit edit) {
        if (edit == null) {
            return;
        }
        String before = this.helper.getValue();
        this.helper.applyEdit(edit.getValue(), edit.getCursor(), edit.getAnchor());
        if (this.typingInput && before.equals(this.helper.getValue())) {
            // A typing action that only moved the caret (skipping over an
            // auto-inserted bracket) must not split the undo group.
            this.history.noteCaret(this.helper.getCursorPos(), this.helper.getSelectionPos());
        }
        this.goalColumn = -1;
    }

    private void moveVertical(int direction, boolean select) {
        TextLayout layoutCurrent = getLayout();
        int cursor = this.helper.getCursorPos();
        int lineIndex = layoutCurrent.getLineIndexAt(cursor);
        int targetLine = Math.max(0, Math.min(layoutCurrent.getLineCount() - 1, lineIndex + direction));
        if (this.goalColumn < 0) {
            this.goalColumn = layoutCurrent.getX(lineIndex, cursor);
        }
        int index;
        if (targetLine == lineIndex) {
            index = direction < 0 ? 0 : this.helper.getValue().length();
        } else {
            index = layoutCurrent.getCharIndexAt(targetLine, this.goalColumn);
        }
        this.helper.setCursorPos(index, select);
    }

    private void movePage(int direction, boolean select) {
        TextLayout layoutCurrent = getLayout();
        int cursor = this.helper.getCursorPos();
        int lineIndex = layoutCurrent.getLineIndexAt(cursor);
        int column = layoutCurrent.getX(lineIndex, cursor);
        int rows = Math.max(1, getVisibleRows() - 1);
        int targetLine = Math.max(0, Math.min(layoutCurrent.getLineCount() - 1,
                lineIndex + direction * rows));
        this.helper.setCursorPos(layoutCurrent.getCharIndexAt(targetLine, column), select);
        setFirstRow(this.firstRow + direction * rows);
        this.goalColumn = column;
    }

    private void undo() {
        EditorHistory.Snapshot previous = this.history.undo(snapshot());
        if (previous != null) {
            restore(previous);
        }
    }

    private void redo() {
        EditorHistory.Snapshot next = this.history.redo(snapshot());
        if (next != null) {
            restore(next);
        }
    }

    private EditorHistory.Snapshot snapshot() {
        return new EditorHistory.Snapshot(this.helper.getValue(), this.helper.getCursorPos(),
                this.helper.getSelectionPos());
    }

    private void restore(EditorHistory.Snapshot snapshot) {
        this.historyApplying = true;
        try {
            this.helper.applyEdit(snapshot.getValue(), snapshot.getCursor(), snapshot.getAnchor());
        } finally {
            this.historyApplying = false;
        }
        this.goalColumn = -1;
    }

    private void onEdited(String oldValue, int oldCursor, int oldSelection,
                          String newValue, int newCursor, int newSelection,
                          int changeStart, int changeEnd) {
        if (this.historyApplying) {
            return;
        }
        this.history.record(new EditorHistory.Snapshot(oldValue, oldCursor, oldSelection),
                new EditorHistory.Snapshot(newValue, newCursor, newSelection),
                System.currentTimeMillis(), this.typingInput);
    }

    private void selectWord(int index) {
        String value = this.helper.getValue();
        int start = index;
        int end = index;
        IIdentifierSyntax syntax = getIdentifierSyntax();
        if (syntax == null) {
            while (start > 0 && isWordChar(value.charAt(start - 1))) {
                start--;
            }
            while (end < value.length() && isWordChar(value.charAt(end))) {
                end++;
            }
        } else {
            while (start > 0 && syntax.isIdentifierPart(value.charAt(start - 1))) {
                start--;
            }
            while (end < value.length() && syntax.isIdentifierPart(value.charAt(end))) {
                end++;
            }
        }
        this.helper.setCursorPos(start, false);
        this.helper.setSelectionPos(end);
    }

    private void selectLine(int index) {
        String value = this.helper.getValue();
        int start = EditorInputRules.lineStart(value, index);
        int end = EditorInputRules.lineEnd(value, index);
        this.helper.applyEdit(value, end, start);
    }

    private static boolean isWordChar(char c) {
        return EditorInputRules.isWordChar(c);
    }

    @Nullable
    private IIdentifierSyntax getIdentifierSyntax() {
        return this.editSupport == null ? null : this.editSupport.getIdentifierSyntax();
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
            row = 0;
        }
        if (row >= layoutCurrent.getLineCount()) {
            row = layoutCurrent.getLineCount() - 1;
        }
        return layoutCurrent.getCharIndexAt(row, mouseX - this.x - getTextOffset());
    }

    // ---------------------------------------------------------------- completion

    /**
     * Ask the language's completion service for candidates at the caret.
     * @param trigger The character that triggered the request, or {@code 0} for an
     *                explicit request (Ctrl-Space).
     */
    private void requestCompletion(char trigger) {
        ICompletionService service = this.completionService;
        if (service == null || this.languageServices == null || this.helper.hasSelection()) {
            closeCompletion();
            return;
        }
        int offset = this.helper.getCursorPos();
        String prefix = prefixAt(this.helper.getValue(), offset);
        CompletionContext context = new CompletionContext(getDocument(), offset,
                this.languageServices.getLanguageId(), getContextAt(offset), trigger, prefix);
        List<CompletionItem> items = service.complete(context);
        if (items == null || items.isEmpty()) {
            closeCompletion();
            return;
        }
        CompletionListModel model = new CompletionListModel(items);
        model.setFilter(prefix);
        if (model.isEmpty()) {
            closeCompletion();
            return;
        }
        this.completionModel = model;
        this.completionPrefix = prefix;
    }

    private String prefixAt(String value, int offset) {
        if (getIdentifierSyntax() == null) {
            return "";
        }
        int end = Math.max(0, Math.min(value.length(), offset));
        return value.substring(wordStartAt(value, end), end);
    }

    /**
     * @param value The document text.
     * @param offset The caret offset.
     * @return The offset where the identifier around the caret starts.
     */
    private int wordStartAt(String value, int offset) {
        int start = Math.max(0, Math.min(value.length(), offset));
        IIdentifierSyntax syntax = getIdentifierSyntax();
        if (syntax == null) {
            return start;
        }
        while (start > 0 && syntax.isIdentifierPart(value.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    /**
     * Recompute the completion prefix from the document (after a deletion), and
     * narrow or close the popup accordingly.
     */
    private void refreshCompletionPrefix() {
        if (this.completionModel == null) {
            return;
        }
        if (this.helper.hasSelection()) {
            closeCompletion();
            return;
        }
        this.completionPrefix = prefixAt(this.helper.getValue(), this.helper.getCursorPos());
        this.completionModel.setFilter(this.completionPrefix);
        if (this.completionModel.isEmpty()) {
            closeCompletion();
        }
    }

    /**
     * Insert the selected candidate and move the caret.
     * @return Whether a candidate was inserted.
     */
    private boolean commitCompletion() {
        CompletionListModel model = this.completionModel;
        if (model == null) {
            return false;
        }
        CompletionItem item = model.getSelected();
        if (item == null) {
            closeCompletion();
            return false;
        }
        // Capture the prefix length before closeCompletion() clears it; the
        // accepted text replaces exactly the characters that were typed.
        int cursor = this.helper.getCursorPos();
        int prefixLength = this.completionPrefix.length();
        closeCompletion();
        int start = Math.max(0, cursor - prefixLength);
        this.typingInput = false;
        this.helper.replaceRange(start, cursor, item.getInsertText());
        int target = Math.min(this.helper.getValue().length(),
                start + Math.max(0, item.getCursorOffset()));
        this.helper.setCursorPos(target, false);
        this.goalColumn = -1;
        return true;
    }

    /**
     * Accept the row under the mouse, when the click landed on the popup.
     * @param mouseX The mouse x, in screen coordinates.
     * @param mouseY The mouse y, in screen coordinates.
     * @return Whether a candidate was accepted.
     */
    private boolean acceptCompletionAt(int mouseX, int mouseY) {
        CompletionListModel model = this.completionModel;
        CompletionPopupLayout layout = this.completionPopupLayout;
        if (model == null || layout == null) {
            return false;
        }
        int row = layout.getRowAt(mouseX, mouseY);
        if (row < 0) {
            return false;
        }
        int index = model.getFirstVisibleRow() + row;
        if (index >= model.getFiltered().size()) {
            return false;
        }
        model.select(index);
        return commitCompletion();
    }

    /**
     * @return Whether the completion dropdown is open with at least one candidate.
     */
    public boolean isCompletionOpen() {
        return this.completionModel != null && !this.completionModel.isEmpty();
    }

    /**
     * @return The number of candidates the open dropdown currently shows.
     */
    public int getCompletionCandidateCount() {
        return this.completionModel == null ? 0 : this.completionModel.getFiltered().size();
    }

    /**
     * @return The label of the selected candidate, or {@code null} when closed.
     */
    @Nullable
    public String getCompletionSelectedLabel() {
        CompletionItem selected = this.completionModel == null
                ? null : this.completionModel.getSelected();
        return selected == null ? null : selected.getLabel();
    }

    /**
     * @return The geometry of the last drawn dropdown, or {@code null} when closed.
     */
    @Nullable
    public CompletionPopupLayout getCompletionPopupLayout() {
        return this.completionPopupLayout;
    }

    /**
     * Whether a screen position is on the open dropdown. The owning screen uses
     * this to route a click to the popup instead of dismissing it.
     * @param mouseX The mouse x, in screen coordinates.
     * @param mouseY The mouse y, in screen coordinates.
     * @return Whether the point is on a drawn dropdown row.
     */
    public boolean isCompletionPopupAt(int mouseX, int mouseY) {
        CompletionPopupLayout layout = this.completionPopupLayout;
        return this.completionModel != null && layout != null
                && layout.getRowAt(mouseX, mouseY) >= 0;
    }

    private void closeCompletion() {
        this.completionModel = null;
        this.completionPrefix = "";
        this.completionPopupLayout = null;
    }

    /**
     * Remember that the user dismissed completion inside the current word, so
     * typing more of that word does not reopen it. A new word or an explicit
     * Ctrl+Space clears this.
     */
    private void suppressCompletion() {
        this.completionSuppressedWordStart = wordStartAt(this.helper.getValue(),
                this.helper.getCursorPos());
    }

    // ---------------------------------------------------------------- rendering

    public void draw(int mouseX, int mouseY) {
        TextLayout layoutCurrent = getLayout();
        int textOffset = getTextOffset();
        int visibleRows = getVisibleRows();
        int lastLine = Math.min(this.firstRow + visibleRows, layoutCurrent.getLineCount());
        int cursorLine = layoutCurrent.getLineIndexAt(this.helper.getCursorPos());
        int cursorLogicalLine = layoutCurrent.getLine(Math.max(0, Math.min(layoutCurrent.getLineCount() - 1,
                cursorLine))).getLineNumber();
        List<List<Token>> highlight = getHighlight();
        int lastLineNumber = -1;

        // CodeMirror's highlightActiveLine: a subtle band behind the caret line.
        if (this.focused && cursorLine >= this.firstRow && cursorLine < lastLine) {
            int lineY = this.y + (cursorLine - this.firstRow) * ROW_HEIGHT;
            drawRect(this.x + textOffset, lineY,
                    this.x + this.width - (this.scrollBar != null ? SCROLL_BAR_WIDTH : 0),
                    lineY + ROW_HEIGHT, ACTIVE_LINE_COLOR);
        }

        // Selection is painted under the text so the code stays readable.
        drawSelection(layoutCurrent, textOffset);

        for (int i = this.firstRow; i < lastLine; i++) {
            TextLayout.Line line = layoutCurrent.getLine(i);
            int lineY = this.y + (i - this.firstRow) * ROW_HEIGHT;

            if (this.showLineNumbers && line.getLineNumber() != lastLineNumber) {
                boolean active = line.getLineNumber() == cursorLogicalLine;
                RenderHelpers.drawScaledString(this.fontRenderer,
                        String.valueOf(line.getLineNumber()), this.x, lineY + 2, 0.5F,
                        active ? LINE_NUMBER_ACTIVE_COLOR : LINE_NUMBER_COLOR, false);
                lastLineNumber = line.getLineNumber();
            }

            drawLine(line, this.x + textOffset, lineY, highlight);
        }

        drawBracketMatch(layoutCurrent, textOffset);
        drawCursor(layoutCurrent, textOffset);

        if (this.scrollBar != null) {
            this.scrollBar.setTotalRows(layoutCurrent.getLineCount());
            if (this.scrollBar.needsScrollBars()) {
                this.scrollBar.drawGuiContainerBackgroundLayer(0, mouseX, mouseY);
            }
        }

        // The dropdown floats above the editor and its scroll bar.
        drawCompletionPopup(layoutCurrent, textOffset);
    }

    private void drawLine(TextLayout.Line line, int lineX, int lineY,
                          @Nullable List<List<Token>> highlight) {
        String text = line.getText();
        if (text.isEmpty()) {
            return;
        }
        if (highlight != null) {
            int index = line.getLineNumber() - 1;
            if (index >= 0 && index < highlight.size()) {
                List<Token> logical = highlight.get(index);
                if (logical != null && !logical.isEmpty()) {
                    int offset = line.getStart() - line.getLogicalLineStart();
                    drawTokens(logical, text, offset, lineX, lineY);
                    return;
                }
                if (logical != null) {
                    return;
                }
            }
        }
        this.fontRenderer.drawString(text, lineX, lineY, TEXT_COLOR, false);
    }

    /**
     * Draw the tokens of one logical line clipped to one wrapped visual line, so
     * wrapping does not draw the start of the line twice.
     */
    private void drawTokens(List<Token> tokens, String visualText, int offset, int lineX, int lineY) {
        int end = offset + visualText.length();
        int xCurrent = lineX;
        for (Token token : tokens) {
            if (token.getEnd() <= offset) {
                continue;
            }
            if (token.getStart() >= end) {
                break;
            }
            int from = Math.max(offset, token.getStart()) - offset;
            int to = Math.min(end, token.getEnd()) - offset;
            if (to <= from) {
                continue;
            }
            String text = visualText.substring(from, to);
            this.fontRenderer.drawString(text, xCurrent, lineY,
                    TokenTypePalette.colorOf(token.getType()), false);
            xCurrent += this.fontRenderer.getStringWidth(text);
        }
    }

    private void drawCompletionPopup(TextLayout layoutCurrent, int textOffset) {
        CompletionListModel model = this.completionModel;
        this.completionPopupLayout = null;
        if (model == null || model.isEmpty() || !this.focused) {
            return;
        }
        int cursor = this.helper.getCursorPos();
        int cursorLine = layoutCurrent.getLineIndexAt(cursor);
        if (cursorLine < this.firstRow || cursorLine >= this.firstRow + getVisibleRows()) {
            return;
        }
        int lineY = this.y + (cursorLine - this.firstRow) * ROW_HEIGHT;
        // CodeMirror anchors the tooltip at the start of the completed range,
        // not at the caret, so the list lines up with the word being typed.
        int prefixStart = Math.max(0, cursor - this.completionPrefix.length());
        int cursorX = this.x + textOffset + layoutCurrent.getX(cursorLine, prefixStart);
        int screenWidth = 320;
        int screenHeight = 240;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.currentScreen != null) {
            screenWidth = minecraft.currentScreen.width;
            screenHeight = minecraft.currentScreen.height;
        }
        CompletionPopupLayout layout = this.completionPopup.layout(this.fontRenderer, model,
                cursorX, lineY + ROW_HEIGHT, screenWidth, screenHeight);
        if (layout == null) {
            return;
        }
        this.completionPopupLayout = layout;
        this.completionPopup.draw(this.fontRenderer, model, layout);
    }

    private void drawSelection(TextLayout layoutCurrent, int textOffset) {
        int selStart = Math.min(this.helper.getCursorPos(), this.helper.getSelectionPos());
        int selEnd = Math.max(this.helper.getCursorPos(), this.helper.getSelectionPos());
        if (selStart == selEnd) {
            return;
        }

        int lastLine = Math.min(this.firstRow + getVisibleRows(), layoutCurrent.getLineCount());
        for (int i = this.firstRow; i < lastLine; i++) {
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

    /**
     * Highlight the bracket next to the caret and its match, like CodeMirror's
     * {@code matchBrackets}.
     */
    private void drawBracketMatch(TextLayout layoutCurrent, int textOffset) {
        if (!this.focused) {
            return;
        }
        int[] match = getBracketMatch(layoutCurrent);
        if (match == null) {
            return;
        }
        String value = this.helper.getValue();
        int lastLine = Math.min(this.firstRow + getVisibleRows(), layoutCurrent.getLineCount());
        for (int position : match) {
            if (position < 0 || position >= value.length()) {
                continue;
            }
            int lineIndex = layoutCurrent.getLineIndexAt(position);
            if (lineIndex < this.firstRow || lineIndex >= lastLine) {
                continue;
            }
            int lineY = this.y + (lineIndex - this.firstRow) * ROW_HEIGHT;
            int x1 = this.x + textOffset + layoutCurrent.getX(lineIndex, position);
            int x2 = x1 + Math.max(2, this.fontRenderer.getCharWidth(value.charAt(position)));
            drawRect(x1, lineY, x2, lineY + ROW_HEIGHT, BRACKET_MATCH_COLOR);
        }
    }

    @Nullable
    private int[] getBracketMatch(TextLayout layoutCurrent) {
        int cursor = this.helper.getCursorPos();
        if (this.bracketMatchLayout == layoutCurrent && this.bracketMatchCursor == cursor) {
            return this.bracketMatch;
        }
        this.bracketMatchLayout = layoutCurrent;
        this.bracketMatchCursor = cursor;
        this.bracketMatch = computeBracketMatch();
        return this.bracketMatch;
    }

    @Nullable
    private int[] computeBracketMatch() {
        String value = this.helper.getValue();
        int cursor = this.helper.getCursorPos();
        int position = -1;
        if (cursor < value.length() && isBracketCharacter(value.charAt(cursor))) {
            position = cursor;
        } else if (cursor > 0 && isBracketCharacter(value.charAt(cursor - 1))) {
            position = cursor - 1;
        }
        if (position < 0) {
            return null;
        }
        if (getContextAt(position) != LexicalContext.CODE) {
            return null;
        }
        int match = findMatchingBracket(value, position);
        return match < 0 ? null : new int[]{position, match};
    }

    private static boolean isBracketCharacter(char c) {
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private static int findMatchingBracket(String value, int position) {
        char bracket = value.charAt(position);
        char open;
        char close;
        int direction;
        if (EditorInputRules.isOpeningBracket(bracket)) {
            open = bracket;
            close = EditorInputRules.closingFor(bracket);
            direction = 1;
        } else if (EditorInputRules.isClosingBracket(bracket)) {
            open = (char) 0;
            close = bracket;
            direction = -1;
        } else {
            return -1;
        }
        int depth = 1;
        for (int i = position + direction; i >= 0 && i < value.length(); i += direction) {
            char c = value.charAt(i);
            if (open == 0) {
                // Looking backwards for the opening bracket of a closer.
                if (c == close) {
                    depth++;
                } else if (EditorInputRules.isOpeningBracket(c)
                        && EditorInputRules.closingFor(c) == close) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            } else {
                if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    @Nullable
    private List<List<Token>> getHighlight() {
        String value = this.helper.getValue();
        if (this.highlightedValue != null && this.highlightedValue.equals(value)) {
            return this.highlightedLines;
        }
        this.highlightedValue = value;
        IHighlightService service = this.highlightService;
        this.highlightedLines = service == null ? null : service.highlight(getDocument());
        return this.highlightedLines;
    }

    private void invalidateHighlight() {
        this.highlightedLines = null;
        this.highlightedValue = null;
    }

    private void invalidateDocument() {
        this.document = null;
        this.documentValue = null;
    }

    private int getTextOffset() {
        return this.showLineNumbers ? LINE_NUMBER_WIDTH : 0;
    }

    private int getTextWidth() {
        int reserved = getTextOffset() + (this.scrollBar != null ? SCROLL_BAR_WIDTH : 0);
        return Math.max(1, this.width - reserved);
    }

    private TextLayout getLayout() {
        if (this.layout == null || this.layoutDirty) {
            // rebuild() keeps the measured wrap lengths of unchanged lines, so
            // editing one line does not re-measure the whole script.
            this.layout = TextLayout.rebuild(this.layout, this.helper.getValue(),
                    getTextWidth(), this.widthProvider);
            this.layoutDirty = false;
        }
        return this.layout;
    }

    // ---------------------------------------------------------------- listener plumbing

    private void onValueChanged(String value) {
        this.layoutDirty = true;
        this.bracketMatch = null;
        invalidateHighlight();
        invalidateDocument();
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
