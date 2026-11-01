package com.mpp.integratedscripting.client.gui.component.input;

import net.minecraft.client.gui.GuiScreen;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Cursor and selection editing logic for a multi-line text value.
 *
 * <p>The 1.12.2 port cannot reuse {@code TextFieldHelper} (it only exists in
 * 1.20) and {@code GuiTextField} does not support line breaks, so the editing
 * operations are ported here from {@code GuiTextField}'s implementation
 * (docs/PORTING-1.12.2.md §7.9.3). The class operates on a flat string and
 * knows nothing about rendering, which keeps it unit testable.</p>
 *
 * <p>All mutations go through {@link #mutate(Runnable)}, which snapshots the
 * value, cursor and selected text and only fires the corresponding listener when
 * it actually changed. This mirrors the listener semantics of the 1.20
 * {@code TextFieldHelperExtended}.</p>
 *
 * @author rubensworks
 */
public class TextFieldHelperExtended {

    /**
     * Observer for completed value changes, used by the editor to build its undo
     * history (CodeMirror records transactions rather than individual keystrokes).
     */
    public interface IEditListener {
        /**
         * Called after a mutation that changed the value.
         * @param oldValue The value before the edit.
         * @param oldCursor The caret before the edit.
         * @param oldSelection The selection anchor before the edit.
         * @param newValue The value after the edit.
         * @param newCursor The caret after the edit.
         * @param newSelection The selection anchor after the edit.
         * @param changeStart Index of the first changed character.
         * @param changeEnd Index in the new value where the changed region ends.
         */
        void onEdited(String oldValue, int oldCursor, int oldSelection,
                      String newValue, int newCursor, int newSelection,
                      int changeStart, int changeEnd);
    }

    private final Consumer<String> onValueChange;
    private final Consumer<String> onSelectionChange;
    private final Consumer<Integer> onCursorChange;

    private String value = "";
    private int cursorPos;
    private int selectionPos;
    @Nullable
    private IEditListener editListener;

    /**
     * @param onValueChange Called when the text content changed (may be null).
     * @param onSelectionChange Called with the new selected text when it changed (may be null).
     * @param onCursorChange Called with the new cursor position when it changed (may be null).
     */
    public TextFieldHelperExtended(@Nullable Consumer<String> onValueChange,
                                   @Nullable Consumer<String> onSelectionChange,
                                   @Nullable Consumer<Integer> onCursorChange) {
        this.onValueChange = onValueChange;
        this.onSelectionChange = onSelectionChange;
        this.onCursorChange = onCursorChange;
    }

    // ---------------------------------------------------------------- value

    public void setEditListener(@Nullable IEditListener editListener) {
        this.editListener = editListener;
    }

    public String getValue() {
        return value;
    }

    /**
     * Replace the value without moving the cursor, e.g. when the server sends an
     * updated script for the file that is being edited.
     */
    public void setValuePassive(String valueNew) {
        mutate(() -> this.value = valueNew == null ? "" : valueNew);
    }

    /**
     * Replace the value and the whole selection in one step, used to apply an
     * {@link EditorEdit} without firing two separate notifications.
     */
    public void applyEdit(String newValue, int cursorNew, int selectionNew) {
        mutate(() -> {
            this.value = newValue == null ? "" : newValue;
            this.cursorPos = cursorNew;
            this.selectionPos = selectionNew;
        });
    }

    /**
     * Replace a character range, moving the caret to the end of the inserted
     * text.
     */
    public void replaceRange(int start, int end, @Nullable String text) {
        mutate(() -> {
            int from = clamp(Math.min(start, end), 0, this.value.length());
            int to = clamp(Math.max(start, end), 0, this.value.length());
            String insert = text == null ? "" : text;
            this.value = this.value.substring(0, from) + insert + this.value.substring(to);
            this.cursorPos = from + insert.length();
            this.selectionPos = this.cursorPos;
        });
    }

    /**
     * Replace the value and move the cursor to the start, like selecting another
     * script file in the terminal.
     */
    public void setValue(String valueNew) {
        mutate(() -> {
            this.value = valueNew == null ? "" : valueNew;
            this.cursorPos = 0;
            this.selectionPos = 0;
        });
    }

    // ---------------------------------------------------------------- cursor / selection

    public int getCursorPos() {
        return cursorPos;
    }

    public int getSelectionPos() {
        return selectionPos;
    }

    public String getSelected() {
        int start = Math.min(cursorPos, selectionPos);
        int end = Math.max(cursorPos, selectionPos);
        return value.substring(start, end);
    }

    public boolean hasSelection() {
        return cursorPos != selectionPos;
    }

    /**
     * Move the cursor, optionally dragging the selection anchor with it.
     */
    public void setCursorPos(int pos, boolean select) {
        mutate(() -> {
            this.cursorPos = clamp(pos, 0, value.length());
            if (!select) {
                this.selectionPos = this.cursorPos;
            }
        });
    }

    public void setSelectionPos(int pos) {
        mutate(() -> this.selectionPos = clamp(pos, 0, value.length()));
    }

    public void selectAll() {
        mutate(() -> {
            this.cursorPos = value.length();
            this.selectionPos = 0;
        });
    }

    public void setCursorToStart(boolean select) {
        setCursorPos(0, select);
    }

    public void setCursorToEnd(boolean select) {
        setCursorPos(value.length(), select);
    }

    /**
     * Move the cursor by one character, or by one word when {@code word} is true.
     * @param direction -1 for left/up, 1 for right/down.
     * @param select Whether the selection anchor moves with the cursor.
     * @param word Whether to move by whole words (Ctrl).
     */
    public void moveBy(int direction, boolean select, boolean word) {
        mutate(() -> {
            int from = select ? this.selectionPos : this.cursorPos;
            if (word) {
                this.cursorPos = direction < 0
                        ? EditorInputRules.findWordStart(this.value, from)
                        : EditorInputRules.findWordEnd(this.value, from);
            } else {
                this.cursorPos = clamp(from + direction, 0, value.length());
            }
            if (!select) {
                this.selectionPos = this.cursorPos;
            }
        });
    }

    // ---------------------------------------------------------------- editing

    /**
     * Insert text at the cursor position, replacing the selection.
     * @param text The text to insert; may contain newlines.
     */
    public void insertText(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        mutate(() -> {
            int start = Math.min(this.cursorPos, this.selectionPos);
            int end = Math.max(this.cursorPos, this.selectionPos);
            this.value = this.value.substring(0, start) + text + this.value.substring(end);
            this.cursorPos = start + text.length();
            this.selectionPos = this.cursorPos;
        });
    }

    /**
     * Delete characters next to the cursor: -1 is backspace, 1 is delete.
     */
    public void deleteFromCursor(int direction) {
        mutate(() -> {
            if (hasSelection()) {
                deleteSelection();
                return;
            }
            int start = direction < 0 ? this.cursorPos + direction : this.cursorPos;
            int end = direction < 0 ? this.cursorPos : this.cursorPos + direction;
            if (start < 0 || end > this.value.length() || start >= end) {
                return;
            }
            this.value = this.value.substring(0, start) + this.value.substring(end);
            this.cursorPos = start;
            this.selectionPos = start;
        });
    }

    /**
     * Delete one word in the given direction, or the selection if there is one.
     * Word boundaries follow JavaScript identifiers and punctuation runs, like
     * CodeMirror's {@code deleteByGroup} with a word group.
     */
    public void deleteWords(int direction) {
        mutate(() -> {
            if (hasSelection()) {
                deleteSelection();
                return;
            }
            int target = direction < 0
                    ? EditorInputRules.findWordStart(this.value, this.cursorPos)
                    : EditorInputRules.findWordEnd(this.value, this.cursorPos);
            if (target == this.cursorPos) {
                return;
            }
            int start = Math.min(this.cursorPos, target);
            int end = Math.max(this.cursorPos, target);
            this.value = this.value.substring(0, start) + this.value.substring(end);
            this.cursorPos = start;
            this.selectionPos = start;
        });
    }

    private void deleteSelection() {
        int start = Math.min(this.cursorPos, this.selectionPos);
        int end = Math.max(this.cursorPos, this.selectionPos);
        this.value = this.value.substring(0, start) + this.value.substring(end);
        this.cursorPos = start;
        this.selectionPos = start;
    }

    /**
     * Copy the selected text to the system clipboard.
     */
    public void copy() {
        if (hasSelection()) {
            GuiScreen.setClipboardString(getSelected());
        }
    }

    /**
     * Cut the selected text to the system clipboard.
     */
    public void cut() {
        if (hasSelection()) {
            GuiScreen.setClipboardString(getSelected());
            deleteFromCursor(1);
        }
    }

    /**
     * Paste the system clipboard content, replacing the selection.
     */
    public void paste() {
        String clipboard = GuiScreen.getClipboardString();
        if (clipboard != null && !clipboard.isEmpty()) {
            insertText(clipboard);
        }
    }

    // ---------------------------------------------------------------- word navigation

    /**
     * Port of {@code GuiTextField#getNthWordFromPosWS}: find the position of the
     * word {@code count} words away from {@code pos}.
     */
    public int getNthWordFromPos(int count, int pos) {
        int result = clamp(pos, 0, value.length());
        boolean backwards = count < 0;
        int words = Math.abs(count);
        for (int i = 0; i < words; i++) {
            if (!backwards) {
                int length = value.length();
                result = value.indexOf(' ', result);
                if (result == -1) {
                    result = length;
                } else {
                    while (result < length && value.charAt(result) == ' ') {
                        result++;
                    }
                }
            } else {
                while (result > 0 && value.charAt(result - 1) == ' ') {
                    result--;
                }
                while (result > 0 && value.charAt(result - 1) != ' ') {
                    result--;
                }
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- internals

    private void mutate(Runnable edit) {
        String oldValue = this.value;
        String oldSelected = getSelected();
        int oldCursor = this.cursorPos;
        int oldSelection = this.selectionPos;

        edit.run();
        this.cursorPos = clamp(this.cursorPos, 0, this.value.length());
        this.selectionPos = clamp(this.selectionPos, 0, this.value.length());

        if (!oldValue.equals(this.value)) {
            if (this.editListener != null) {
                int changeStart = commonPrefixLength(oldValue, this.value);
                int changeEnd = this.value.length() - commonSuffixLength(oldValue, this.value, changeStart);
                this.editListener.onEdited(oldValue, oldCursor, oldSelection,
                        this.value, this.cursorPos, this.selectionPos,
                        changeStart, Math.max(changeStart, changeEnd));
            }
            if (this.onValueChange != null) {
                this.onValueChange.accept(this.value);
            }
        }
        if (!oldSelected.equals(getSelected()) && this.onSelectionChange != null) {
            this.onSelectionChange.accept(getSelected());
        }
        if (oldCursor != this.cursorPos && this.onCursorChange != null) {
            this.onCursorChange.accept(this.cursorPos);
        }
    }

    private static int commonPrefixLength(String first, String second) {
        int max = Math.min(first.length(), second.length());
        int i = 0;
        while (i < max && first.charAt(i) == second.charAt(i)) {
            i++;
        }
        return i;
    }

    private static int commonSuffixLength(String first, String second, int fromStart) {
        int i = 0;
        int firstEnd = first.length();
        int secondEnd = second.length();
        while (firstEnd - i > fromStart && secondEnd - i > fromStart
                && first.charAt(firstEnd - 1 - i) == second.charAt(secondEnd - 1 - i)) {
            i++;
        }
        return i;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}
