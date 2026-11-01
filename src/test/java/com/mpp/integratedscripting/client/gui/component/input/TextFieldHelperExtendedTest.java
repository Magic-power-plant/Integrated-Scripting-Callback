package com.mpp.integratedscripting.client.gui.component.input;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the 1.12.2 multi-line editing logic (M5, §11.2 item 10).
 */
public class TextFieldHelperExtendedTest {

    private final List<String> valueChanges = new ArrayList<>();
    private final List<String> selectionChanges = new ArrayList<>();
    private final List<Integer> cursorChanges = new ArrayList<>();
    private TextFieldHelperExtended helper;

    @Before
    public void setUp() {
        this.helper = new TextFieldHelperExtended(this.valueChanges::add, this.selectionChanges::add,
                this.cursorChanges::add);
        this.helper.setValue("hello world");
    }

    @Test
    public void testSetValueResetsCursor() {
        assertEquals("hello world", this.helper.getValue());
        assertEquals(0, this.helper.getCursorPos());
        assertEquals(0, this.helper.getSelectionPos());
    }

    @Test
    public void testInsertAtCursor() {
        this.helper.setCursorPos(5, false);
        this.helper.insertText(",");
        assertEquals("hello, world", this.helper.getValue());
        assertEquals(6, this.helper.getCursorPos());
    }

    @Test
    public void testInsertReplacesSelection() {
        this.helper.setCursorPos(0, false);
        this.helper.setSelectionPos(5);
        this.helper.insertText("bye");
        assertEquals("bye world", this.helper.getValue());
        assertEquals(3, this.helper.getCursorPos());
    }

    @Test
    public void testInsertNewline() {
        this.helper.setCursorPos(5, false);
        this.helper.insertText("\n");
        assertEquals("hello\n world", this.helper.getValue());
        assertEquals(6, this.helper.getCursorPos());
    }

    @Test
    public void testBackspaceAndDelete() {
        this.helper.setCursorPos(5, false);
        this.helper.deleteFromCursor(-1);
        assertEquals("hell world", this.helper.getValue());
        assertEquals(4, this.helper.getCursorPos());

        this.helper.setCursorPos(4, false);
        this.helper.deleteFromCursor(1);
        assertEquals("hellworld", this.helper.getValue());
        assertEquals(4, this.helper.getCursorPos());
    }

    @Test
    public void testBackspaceAtStartDoesNothing() {
        this.helper.setCursorPos(0, false);
        this.helper.deleteFromCursor(-1);
        assertEquals("hello world", this.helper.getValue());
    }

    @Test
    public void testDeleteWithSelection() {
        this.helper.setCursorPos(0, false);
        this.helper.setSelectionPos(6);
        this.helper.deleteFromCursor(-1);
        assertEquals("world", this.helper.getValue());
        assertEquals(0, this.helper.getCursorPos());
    }

    @Test
    public void testDeleteWords() {
        this.helper.setCursorPos(this.helper.getValue().length(), false);
        this.helper.deleteWords(-1);
        assertEquals("hello ", this.helper.getValue());
        assertEquals(6, this.helper.getCursorPos());
    }

    @Test
    public void testWordNavigation() {
        this.helper.setCursorPos(0, false);
        // Forward: "hello world" -> index of the first space is 5, skip spaces -> 6.
        assertEquals(6, this.helper.getNthWordFromPos(1, 0));
        // Backward from the end -> start of "world".
        assertEquals(6, this.helper.getNthWordFromPos(-1, 11));
        // Backward from the start clamps to 0.
        assertEquals(0, this.helper.getNthWordFromPos(-1, 0));
    }

    @Test
    public void testMoveBy() {
        this.helper.setCursorPos(5, false);
        this.helper.moveBy(1, false, false);
        assertEquals(6, this.helper.getCursorPos());
        assertEquals(6, this.helper.getSelectionPos());

        this.helper.setCursorPos(5, false);
        this.helper.moveBy(1, true, false);
        assertEquals(6, this.helper.getCursorPos());
        assertEquals(5, this.helper.getSelectionPos());
        assertEquals(" ", this.helper.getSelected());
    }

    @Test
    public void testSelectAll() {
        this.helper.selectAll();
        assertEquals("hello world", this.helper.getSelected());
        assertEquals(0, this.helper.getSelectionPos());
        assertEquals(11, this.helper.getCursorPos());
        assertTrue(this.selectionChanges.contains("hello world"));
    }

    @Test
    public void testCursorToStartAndEnd() {
        this.helper.setCursorToEnd(false);
        assertEquals(11, this.helper.getCursorPos());
        assertFalse(this.helper.hasSelection());
        this.helper.setCursorToStart(true);
        assertEquals(0, this.helper.getCursorPos());
        assertEquals(11, this.helper.getSelectionPos());
        assertEquals("hello world", this.helper.getSelected());
    }

    @Test
    public void testCursorClampedToValueBounds() {
        this.helper.setCursorPos(999, false);
        assertEquals(11, this.helper.getCursorPos());
        this.helper.setCursorPos(-5, false);
        assertEquals(0, this.helper.getCursorPos());
    }

    @Test
    public void testValueChangeListener() {
        this.valueChanges.clear();
        this.helper.setCursorPos(0, false);
        this.helper.insertText("X");
        assertEquals(1, this.valueChanges.size());
        assertEquals("Xhello world", this.valueChanges.get(0));
    }

    @Test
    public void testCursorChangeListener() {
        this.cursorChanges.clear();
        this.helper.setCursorPos(3, false);
        assertTrue(this.cursorChanges.contains(3));
    }

    @Test
    public void testSetValuePassiveKeepsCursorInsideValue() {
        this.helper.setCursorPos(8, false);
        this.helper.setValuePassive("ab");
        assertEquals("ab", this.helper.getValue());
        assertEquals(2, this.helper.getCursorPos());
        assertEquals(2, this.helper.getSelectionPos());
    }

    @Test
    public void testInsertEmptyDoesNothing() {
        int changes = this.valueChanges.size();
        this.helper.insertText("");
        assertEquals(changes, this.valueChanges.size());
        assertEquals("hello world", this.helper.getValue());
    }

    @Test
    public void testDeleteSelectedAcrossLines() {
        this.helper.setValue("abc\ndef\nghi");
        this.helper.setCursorPos(2, false);
        this.helper.setSelectionPos(8);
        this.helper.deleteFromCursor(1);
        assertEquals("abghi", this.helper.getValue());
        assertEquals(2, this.helper.getCursorPos());
    }

    // ---------------------------------------------------------------- editor support

    @Test
    public void testApplyEditSetsValueCursorAndSelection() {
        this.helper.setValue("abc");
        this.helper.applyEdit("abcd", 4, 1);
        assertEquals("abcd", this.helper.getValue());
        assertEquals(4, this.helper.getCursorPos());
        assertEquals(1, this.helper.getSelectionPos());
        assertEquals("bcd", this.helper.getSelected());
    }

    @Test
    public void testReplaceRangeMovesCaretToEnd() {
        this.helper.setValue("abc");
        this.helper.replaceRange(1, 2, "XY");
        assertEquals("aXYc", this.helper.getValue());
        assertEquals(3, this.helper.getCursorPos());
        assertEquals(3, this.helper.getSelectionPos());
    }

    @Test
    public void testEditListenerReportsChangedRange() {
        final String[] reported = new String[1];
        final int[] range = new int[2];
        this.helper.setValue("abc");
        this.helper.setEditListener((oldValue, oldCursor, oldSelection, newValue, newCursor,
                                     newSelection, changeStart, changeEnd) -> {
            reported[0] = oldValue + "->" + newValue;
            range[0] = changeStart;
            range[1] = changeEnd;
        });

        this.helper.setCursorPos(1, false);
        this.helper.insertText("XY");
        assertEquals("abc->aXYbc", reported[0]);
        assertEquals(1, range[0]);
        assertEquals(3, range[1]);
    }

    @Test
    public void testEditListenerNotFiredForCursorMoves() {
        final int[] calls = new int[1];
        this.helper.setEditListener((oldValue, oldCursor, oldSelection, newValue, newCursor,
                                     newSelection, changeStart, changeEnd) -> calls[0]++);
        this.helper.setCursorPos(3, false);
        this.helper.selectAll();
        assertEquals(0, calls[0]);
    }

    @Test
    public void testWordDeletionUsesCodeBoundaries() {
        this.helper.setValue("foo.bar baz");
        this.helper.setCursorPos(3, false);
        this.helper.deleteWords(1);
        assertEquals("foobar baz", this.helper.getValue());
        assertEquals(3, this.helper.getCursorPos());
    }

    @Test
    public void testWordMovementUsesCodeBoundaries() {
        this.helper.setValue("foo.bar");
        this.helper.setCursorPos(0, false);
        this.helper.moveBy(1, false, true);
        assertEquals(3, this.helper.getCursorPos());
        this.helper.moveBy(1, false, true);
        assertEquals(4, this.helper.getCursorPos());
        this.helper.moveBy(1, false, true);
        assertEquals(7, this.helper.getCursorPos());
    }

}
