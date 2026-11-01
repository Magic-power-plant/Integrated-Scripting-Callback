package com.mpp.integratedscripting.client.gui.component.input;

/**
 * The outcome of an editor command: a new value plus the caret and selection
 * anchor inside it.
 *
 * <p>Following the editor's existing convention (and {@code GuiTextField}), the
 * caret is {@link #getCursor()} and the other end of the selection is
 * {@link #getAnchor()}. Both are character indices in {@link #getValue()}; when
 * they are equal there is no selection.</p>
 *
 * <p>The editor rules in {@link EditorInputRules} are pure functions that return
 * one of these, so every editing behaviour (auto-indent, bracket completion,
 * indentation, comment toggling) can be tested without a Minecraft client.</p>
 *
 * @author rubensworks
 */
public final class EditorEdit {

    private final String value;
    private final int cursor;
    private final int anchor;

    public EditorEdit(String value, int cursor, int anchor) {
        this.value = value;
        this.cursor = Math.max(0, Math.min(value.length(), cursor));
        this.anchor = Math.max(0, Math.min(value.length(), anchor));
    }

    /** The new document value. */
    public String getValue() {
        return value;
    }

    /** The caret position. */
    public int getCursor() {
        return cursor;
    }

    /** The other end of the selection; equal to the caret when nothing is selected. */
    public int getAnchor() {
        return anchor;
    }

    /** Whether this edit leaves the caret at a fixed position without a selection. */
    public boolean isCaretOnly() {
        return cursor == anchor;
    }

    /** The smaller of caret and anchor. */
    public int getSelectionStart() {
        return Math.min(cursor, anchor);
    }

    /** The larger of caret and anchor. */
    public int getSelectionEnd() {
        return Math.max(cursor, anchor);
    }

    @Override
    public String toString() {
        return "EditorEdit{cursor=" + cursor + ", anchor=" + anchor + ", value='"
                + value.replace("\n", "\\n") + "'}";
    }

}
