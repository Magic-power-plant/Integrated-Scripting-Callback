package com.mpp.integratedscripting.client.gui.component.input;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo/redo history of the script editor.
 *
 * <p>Modelled on CodeMirror's history extension: each entry is the complete
 * editor state <em>before</em> an edit, and consecutive typing is folded into one
 * entry so that a whole word is undone at once. A new entry is started when the
 * previous edit was more than {@link #GROUP_DELAY_MILLIS} ago, when the caret is
 * not where the previous typing ended (the player moved it), or when the edit
 * was a command (paste, indent, comment toggle, ...).</p>
 *
 * <p>The class stores snapshots rather than character diffs, like CodeMirror's
 * early history implementation: scripts edited in the terminal are small
 * (a few kilobytes), and snapshots make undo/redo trivially correct even when
 * the server replaces the document.</p>
 *
 * @author rubensworks
 */
public final class EditorHistory {

    /** Upper bound on retained undo states, to keep memory bounded. */
    public static final int MAX_ENTRIES = 200;

    /**
     * Upper bound on the characters retained by the undo stack. Snapshots hold
     * whole documents, so a few very large scripts would otherwise pin megabytes
     * of memory; the oldest states are dropped first, keeping at least one.
     */
    public static final int MAX_TOTAL_CHARS = 1_000_000;

    /** Typing that pauses for longer than this starts a new undo group. */
    public static final long GROUP_DELAY_MILLIS = 500L;

    /** One complete editor state. */
    public static final class Snapshot {
        private final String value;
        private final int cursor;
        private final int anchor;

        public Snapshot(String value, int cursor, int anchor) {
            this.value = value == null ? "" : value;
            this.cursor = clamp(this.value, cursor);
            this.anchor = clamp(this.value, anchor);
        }

        public String getValue() {
            return value;
        }

        public int getCursor() {
            return cursor;
        }

        public int getAnchor() {
            return anchor;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Snapshot)) {
                return false;
            }
            Snapshot snapshot = (Snapshot) other;
            return cursor == snapshot.cursor && anchor == snapshot.anchor
                    && value.equals(snapshot.value);
        }

        @Override
        public int hashCode() {
            int result = value.hashCode();
            result = 31 * result + cursor;
            result = 31 * result + anchor;
            return result;
        }

        @Override
        public String toString() {
            return "Snapshot{cursor=" + cursor + ", anchor=" + anchor + ", length=" + value.length() + "}";
        }

        private static int clamp(String value, int pos) {
            return Math.max(0, Math.min(value.length(), pos));
        }
    }

    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private final Deque<Snapshot> redo = new ArrayDeque<>();
    private int undoChars;
    private int redoChars;

    private long lastEditTime = Long.MIN_VALUE;
    private boolean lastWasTyping;
    private int lastCaret = -1;
    private int lastAnchor = -1;

    /**
     * Record an edit.
     * @param before The state before the edit.
     * @param after The state after the edit.
     * @param now The current time in milliseconds (injectable for tests).
     * @param typing Whether the edit came from typing a character, Enter,
     *               Backspace, Delete or Tab; commands start a new undo group.
     */
    public void record(Snapshot before, Snapshot after, long now, boolean typing) {
        if (before.equals(after)) {
            return;
        }
        boolean continueGroup = typing && this.lastWasTyping
                && now - this.lastEditTime <= GROUP_DELAY_MILLIS
                && this.lastCaret == before.getCursor()
                && this.lastAnchor == before.getAnchor();
        if (!continueGroup) {
            this.undo.addLast(before);
            this.undoChars += before.getValue().length();
            trimUndo();
        }
        this.redo.clear();
        this.redoChars = 0;
        this.lastWasTyping = typing;
        this.lastEditTime = now;
        this.lastCaret = after.getCursor();
        this.lastAnchor = after.getAnchor();
    }

    /**
     * Keep the current typing group open when a typing action only moved the
     * caret, e.g. when typing ")" skipped over an auto-inserted bracket. Without
     * this, the skip would look like the player moving the caret and start a new
     * undo group.
     */
    public void noteCaret(int cursor, int anchor) {
        if (this.lastWasTyping) {
            this.lastCaret = cursor;
            this.lastAnchor = anchor;
        }
    }

    /**
     * Move one step back in history.
     * @param current The current state, which becomes the redo target.
     * @return The previous state, or {@code null} when there is nothing to undo.
     */
    @Nullable
    public Snapshot undo(Snapshot current) {
        if (this.undo.isEmpty()) {
            return null;
        }
        Snapshot previous = this.undo.removeLast();
        this.undoChars -= previous.getValue().length();
        this.redo.addLast(current);
        this.redoChars += current.getValue().length();
        while (this.redo.size() > MAX_ENTRIES) {
            this.redoChars -= this.redo.removeFirst().getValue().length();
        }
        this.lastWasTyping = false;
        return previous;
    }

    /**
     * Move one step forward in history.
     * @param current The current state, which becomes the undo target.
     * @return The next state, or {@code null} when there is nothing to redo.
     */
    @Nullable
    public Snapshot redo(Snapshot current) {
        if (this.redo.isEmpty()) {
            return null;
        }
        Snapshot next = this.redo.removeLast();
        this.redoChars -= next.getValue().length();
        this.undo.addLast(current);
        this.undoChars += current.getValue().length();
        trimUndo();
        this.lastWasTyping = false;
        return next;
    }

    private void trimUndo() {
        while (this.undo.size() > 1
                && (this.undo.size() > MAX_ENTRIES || this.undoChars > MAX_TOTAL_CHARS)) {
            this.undoChars -= this.undo.removeFirst().getValue().length();
        }
    }

    /** Forget every undo and redo state, e.g. after loading another script. */
    public void reset() {
        this.undo.clear();
        this.redo.clear();
        this.undoChars = 0;
        this.redoChars = 0;
        this.lastWasTyping = false;
        this.lastEditTime = Long.MIN_VALUE;
        this.lastCaret = -1;
        this.lastAnchor = -1;
    }

    public boolean canUndo() {
        return !this.undo.isEmpty();
    }

    public boolean canRedo() {
        return !this.redo.isEmpty();
    }

    /** Number of undo states, for tests. */
    public int getUndoSize() {
        return this.undo.size();
    }

    /** Number of redo states, for tests. */
    public int getRedoSize() {
        return this.redo.size();
    }

    /** Number of characters retained by the undo stack, for tests. */
    public int getUndoChars() {
        return this.undoChars;
    }

}
