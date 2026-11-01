package com.mpp.integratedscripting.client.gui.component.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the CodeMirror-style undo history of the terminal editor.
 */
public class EditorHistoryTest {

    private static EditorHistory.Snapshot state(String value, int cursor) {
        return new EditorHistory.Snapshot(value, cursor, cursor);
    }

    @Test
    public void testTypingIsGrouped() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.record(state("a", 1), state("ab", 2), 1100L, true);
        history.record(state("ab", 2), state("abc", 3), 1200L, true);
        assertEquals(1, history.getUndoSize());

        EditorHistory.Snapshot undo = history.undo(state("abc", 3));
        assertEquals("", undo.getValue());
    }

    @Test
    public void testPauseStartsNewGroup() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.record(state("a", 1), state("ab", 2), 2000L, true);
        assertEquals(2, history.getUndoSize());
    }

    @Test
    public void testNoteCaretKeepsTypingGroupOpen() {
        EditorHistory history = new EditorHistory();
        // Typing "(" inserted the pair "()" and left the caret between them.
        history.record(state("a", 1), state("a()", 2), 1000L, true);
        // Typing ")" skipped over the auto-inserted bracket without changing text.
        history.noteCaret(3, 3);
        history.record(state("a()", 3), state("a()x", 4), 1100L, true);
        assertEquals(1, history.getUndoSize());
    }

    @Test
    public void testMovingCaretStartsNewGroup() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.record(state("a", 1), state("ab", 2), 1100L, true);
        // The player moved the caret before typing again.
        history.record(state("ab", 0), state("xab", 1), 1200L, true);
        assertEquals(2, history.getUndoSize());
    }

    @Test
    public void testCommandStartsNewGroup() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.record(state("a", 1), state("a\n", 2), 1000L, false);
        assertEquals(2, history.getUndoSize());
    }

    @Test
    public void testUndoRedoRoundTrip() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.record(state("a", 1), state("ab", 2), 6000L, true);
        assertEquals(2, history.getUndoSize());

        EditorHistory.Snapshot first = history.undo(state("ab", 2));
        assertEquals("a", first.getValue());
        assertEquals(1, history.getRedoSize());

        EditorHistory.Snapshot second = history.undo(state("a", 1));
        assertEquals("", second.getValue());

        EditorHistory.Snapshot redo = history.redo(state("", 0));
        assertEquals("a", redo.getValue());

        EditorHistory.Snapshot redoAgain = history.redo(state("a", 1));
        assertEquals("ab", redoAgain.getValue());
        assertEquals(0, history.getRedoSize());
    }

    @Test
    public void testNewEditClearsRedo() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.undo(state("a", 1));
        assertTrue(history.canRedo());

        history.record(state("", 0), state("b", 1), 7000L, true);
        assertFalse(history.canRedo());
    }

    @Test
    public void testNoChangeIsNotRecorded() {
        EditorHistory history = new EditorHistory();
        history.record(state("a", 1), state("a", 1), 1000L, true);
        assertEquals(0, history.getUndoSize());
        assertFalse(history.canUndo());
        assertNull(history.undo(state("a", 1)));
    }

    @Test
    public void testHistoryIsBounded() {
        EditorHistory history = new EditorHistory();
        for (int i = 0; i < EditorHistory.MAX_ENTRIES + 20; i++) {
            history.record(state("a", 0), state("b", 1), 1000L * (i + 1), false);
        }
        assertEquals(EditorHistory.MAX_ENTRIES, history.getUndoSize());
    }

    @Test
    public void testHistoryIsBoundedByCharacters() {
        EditorHistory history = new EditorHistory();
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            big.append('x');
        }
        for (int i = 0; i < 100; i++) {
            String before = big.toString() + i;
            String after = before + "y";
            history.record(state(before, 0), state(after, 1), 1000L * (i + 1), false);
        }
        assertTrue(history.getUndoChars() <= EditorHistory.MAX_TOTAL_CHARS);
        assertTrue(history.getUndoSize() < 100);
    }

    @Test
    public void testResetClearsEverything() {
        EditorHistory history = new EditorHistory();
        history.record(state("", 0), state("a", 1), 1000L, true);
        history.undo(state("a", 1));
        history.reset();
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertEquals(0, history.getUndoSize());
        assertEquals(0, history.getRedoSize());
    }

}
