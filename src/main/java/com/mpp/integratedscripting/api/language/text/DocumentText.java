package com.mpp.integratedscripting.api.language.text;

import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Immutable view of an editor document: the complete text plus the line index
 * every language service needs.
 *
 * <p>Before this existed, offset&rarr;line/column conversion was reimplemented in
 * the editor (linearly), in the tokenizer and in the terminal GUI. A service now
 * receives one {@code DocumentText} instead of a raw {@code String}, so those
 * conversions (and the member extraction, the completion prefix and the
 * diagnostic positions) share the same implementation.</p>
 *
 * <p>The line index is built lazily on the first line-based query and cached, so
 * {@link #of(String)} stays cheap for services that only need the text. Line and
 * column indices are zero based; {@link #slice(int, int)} is boundary safe.
 * Lines are split on {@code '\n'}; a trailing newline yields an empty last
 * line, matching the editor's model.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class DocumentText {

    private final String text;
    @Nullable
    private volatile int[] lineStarts;

    private DocumentText(String text) {
        this.text = text;
    }

    /**
     * @param text The document text; {@code null} is treated as the empty document.
     * @return The document view.
     */
    public static DocumentText of(@Nullable String text) {
        return new DocumentText(text == null ? "" : text);
    }

    /**
     * @return The complete text.
     */
    public String getText() {
        return text;
    }

    /**
     * @return The number of characters.
     */
    public int getLength() {
        return text.length();
    }

    /**
     * @return The number of logical lines; always at least one.
     */
    public int getLineCount() {
        return lineStarts().length;
    }

    /**
     * @param lineIndex A zero based line index.
     * @return The offset of the first character of that line.
     */
    public int getLineStart(int lineIndex) {
        int[] starts = lineStarts();
        return starts[clampLine(lineIndex, starts)];
    }

    /**
     * @param lineIndex A zero based line index.
     * @return The offset just after the last character of that line, excluding
     *         the newline.
     */
    public int getLineEnd(int lineIndex) {
        int[] starts = lineStarts();
        int line = clampLine(lineIndex, starts);
        if (line + 1 < starts.length) {
            // The newline of this line is just before the next line's start.
            return hasCarriageReturn(starts[line + 1]) ? starts[line + 1] - 2 : starts[line + 1] - 1;
        }
        return text.length();
    }

    /**
     * @param lineIndex A zero based line index.
     * @return The line without its newline; empty for an empty line.
     */
    public String getLine(int lineIndex) {
        return slice(getLineStart(lineIndex), getLineEnd(lineIndex));
    }

    /**
     * @param offset A character offset; clamped into the document.
     * @return The zero based line that contains the offset.
     */
    public int getLineOfOffset(int offset) {
        int[] starts = lineStarts();
        int position = Math.max(0, Math.min(text.length(), offset));
        int index = Arrays.binarySearch(starts, position);
        if (index >= 0) {
            return index;
        }
        // binarySearch returns -(insertion point) - 1.
        return Math.max(0, -index - 2);
    }

    /**
     * @param offset A character offset; clamped into the document.
     * @return The zero based column of the offset inside its line.
     */
    public int getColumnOfOffset(int offset) {
        int position = Math.max(0, Math.min(text.length(), offset));
        return position - getLineStart(getLineOfOffset(position));
    }

    /**
     * Extract a range of the document, with both ends clamped into the document
     * and swapped when they are given in the wrong order.
     * @param from The inclusive start offset.
     * @param to The exclusive end offset.
     * @return The requested substring.
     */
    public String slice(int from, int to) {
        int start = Math.max(0, Math.min(text.length(), Math.min(from, to)));
        int end = Math.max(0, Math.min(text.length(), Math.max(from, to)));
        return start == end ? "" : text.substring(start, end);
    }

    @Override
    public String toString() {
        return "DocumentText[" + text.length() + " chars, " + getLineCount() + " lines]";
    }

    private int clampLine(int lineIndex, int[] starts) {
        return Math.max(0, Math.min(starts.length - 1, lineIndex));
    }

    private boolean hasCarriageReturn(int nextLineStart) {
        return nextLineStart >= 2 && text.charAt(nextLineStart - 2) == '\r';
    }

    private int[] lineStarts() {
        int[] starts = this.lineStarts;
        if (starts != null) {
            return starts;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        starts = new int[count];
        int line = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                starts[line++] = i + 1;
            }
        }
        this.lineStarts = starts;
        return starts;
    }

}
