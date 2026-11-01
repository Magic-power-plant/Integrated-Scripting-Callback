package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.core.language.JavaScriptTokenizer;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Headless typing simulation for the script editor.
 *
 * <p>Drives {@link EditorInputRules} through {@link TextFieldHelperExtended}
 * exactly like {@code GuiWidgetTextArea} does, without a Minecraft client, so the
 * composed behaviour (completion followed by skipping, auto-indent, grouped
 * undo) is covered end to end. The scripts below are also what the in-game
 * acceptance harnesses type character by character.</p>
 */
public class EditorTypingSimulationTest {

    private static final String UNIT = EditorInputRules.INDENT_UNIT;

    private TextFieldHelperExtended helper;
    private EditorHistory history;
    private boolean typing;
    private boolean restoring;
    private long clock = 1000L;

    @Before
    public void setUp() {
        this.helper = new TextFieldHelperExtended(null, null, null);
        this.history = new EditorHistory();
        this.helper.setEditListener((oldValue, oldCursor, oldSelection, newValue, newCursor,
                                     newSelection, changeStart, changeEnd) -> {
            if (this.restoring) {
                return;
            }
            this.clock += 100L;
            this.history.record(new EditorHistory.Snapshot(oldValue, oldCursor, oldSelection),
                    new EditorHistory.Snapshot(newValue, newCursor, newSelection), this.clock, this.typing);
        });
    }

    private void type(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                enter();
            } else {
                typeCharacter(c);
            }
        }
    }

    private void typeCharacter(char c) {
        this.typing = true;
        try {
            String value = this.helper.getValue();
            int cursor = this.helper.getCursorPos();
            int anchor = this.helper.getSelectionPos();
            int context = EditorInputRules.isOpeningBracket(c)
                    || EditorInputRules.isClosingBracket(c) || EditorInputRules.isQuote(c)
                    ? JavaScriptTokenizer.lexicalContextAt(value, Math.min(cursor, anchor))
                    : LexicalContext.CODE;
            EditorEdit edit = EditorInputRules.handleInsert(value, cursor, anchor, c, context, UNIT);
            if (edit != null) {
                apply(edit);
            } else {
                this.helper.insertText(Character.toString(c));
            }
        } finally {
            this.typing = false;
        }
    }

    private void enter() {
        this.typing = true;
        try {
            String value = this.helper.getValue();
            EditorEdit edit = EditorInputRules.newline(value, this.helper.getCursorPos(),
                    this.helper.getSelectionPos(), UNIT,
                    JavaScriptTokenizer.lexicalContextAt(value, this.helper.getCursorPos()));
            apply(edit);
        } finally {
            this.typing = false;
        }
    }

    private void apply(EditorEdit edit) {
        String before = this.helper.getValue();
        this.helper.applyEdit(edit.getValue(), edit.getCursor(), edit.getAnchor());
        if (this.typing && before.equals(this.helper.getValue())) {
            this.history.noteCaret(this.helper.getCursorPos(), this.helper.getSelectionPos());
        }
    }

    private void undo() {
        EditorHistory.Snapshot previous = this.history.undo(new EditorHistory.Snapshot(
                this.helper.getValue(), this.helper.getCursorPos(), this.helper.getSelectionPos()));
        if (previous != null) {
            this.restoring = true;
            try {
                this.helper.applyEdit(previous.getValue(), previous.getCursor(), previous.getAnchor());
            } finally {
                this.restoring = false;
            }
        }
    }

    @Test
    public void testTypingHarnessScriptLeavesTextUnchanged() {
        type("const foo = 42;\nfunction f(item) { return item.stackable(); }");
        assertEquals("const foo = 42;\nfunction f(item) { return item.stackable(); }",
                this.helper.getValue());
    }

    @Test
    public void testTypingStringLiteralLeavesTextUnchanged() {
        type("const s = \"hello\";");
        assertEquals("const s = \"hello\";", this.helper.getValue());
    }

    @Test
    public void testEnterInsideBlockKeepsClosingBraceOnItsOwnLine() {
        type("if (x) {\n");
        // "{" auto-completed to "{}", so Enter splits the pair into three lines.
        assertEquals("if (x) {\n    \n}", this.helper.getValue());
        assertEquals("if (x) {\n    ".length(), this.helper.getCursorPos());
    }

    @Test
    public void testEnterBetweenBracesSplitsIntoThreeLines() {
        type("{\n");
        assertEquals("{\n    \n}", this.helper.getValue());
        assertEquals(6, this.helper.getCursorPos());
    }

    @Test
    public void testUndoRestoresWholeTypedWord() {
        type("abc");
        assertEquals("abc", this.helper.getValue());
        undo();
        assertEquals("", this.helper.getValue());
    }

    @Test
    public void testUndoAfterAutoCloseKeepsGroup() {
        type("a()x");
        assertEquals("a()x", this.helper.getValue());
        undo();
        assertEquals("", this.helper.getValue());
    }

}
