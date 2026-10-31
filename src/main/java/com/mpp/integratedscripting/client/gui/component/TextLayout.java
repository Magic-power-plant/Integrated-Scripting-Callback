package com.mpp.integratedscripting.client.gui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wrapped-line model of a multi-line script, used by the 1.12 terminal text
 * editor.
 *
 * <p>1.12.2 has no {@code StringSplitter}/{@code FormattedCharSequence}, so the
 * editor needs its own line layout (docs/PORTING-1.12.2.md §7.9.3). This class
 * contains only the geometry, without any rendering or Minecraft state, so it
 * can be unit tested with a fake width provider.</p>
 *
 * <p>Coordinates: a {@link Line} stores the index of its first character in the
 * full value. Several visual lines may share one logical line number when a
 * long line is wrapped; only a real {@code '\n'} increases the line number.</p>
 *
 * @author rubensworks
 */
public final class TextLayout {

    /** Height of one visual line in pixels, matching {@code GuiScreenBook}. */
    public static final int ROW_HEIGHT = 9;

    /**
     * Measures the pixel width of a text fragment.
     */
    public interface IWidthProvider {
        int getWidth(String text);
    }

    /**
     * One visual line of the wrapped layout.
     */
    public static final class Line {
        private final int start;
        private final String text;
        private final int lineNumber;

        public Line(int start, String text, int lineNumber) {
            this.start = start;
            this.text = text;
            this.lineNumber = lineNumber;
        }

        /** Index of this line's first character in the full value. */
        public int getStart() {
            return start;
        }

        /** This line's text, without its trailing newline. */
        public String getText() {
            return text;
        }

        /** Index of the last character of this line in the full value. */
        public int getEnd() {
            return start + text.length();
        }

        /** Logical (1-based) line number, shared by wrapped lines. */
        public int getLineNumber() {
            return lineNumber;
        }

    }

    private final String value;
    private final List<Line> lines;
    private final IWidthProvider widthProvider;

    private TextLayout(String value, List<Line> lines, IWidthProvider widthProvider) {
        this.value = value;
        this.lines = lines;
        this.widthProvider = widthProvider;
    }

    /**
     * Wrap a value into visual lines.
     * @param value The full editor content.
     * @param maxWidth The available width in pixels; values below 1 are treated as 1.
     * @param widthProvider Measures text fragments.
     * @return The layout. An empty value still yields one (empty) line.
     */
    public static TextLayout build(String value, int maxWidth, IWidthProvider widthProvider) {
        String safeValue = value == null ? "" : value;
        int safeWidth = Math.max(1, maxWidth);
        List<Line> lines = new ArrayList<>();

        int pos = 0;
        int lineNumber = 1;
        while (true) {
            int newLine = safeValue.indexOf('\n', pos);
            int end = newLine < 0 ? safeValue.length() : newLine;
            String content = safeValue.substring(pos, end);

            if (content.isEmpty()) {
                lines.add(new Line(pos, "", lineNumber));
            } else {
                int offset = 0;
                while (offset < content.length()) {
                    int fit = lengthThatFits(content, offset, safeWidth, widthProvider);
                    lines.add(new Line(pos + offset, content.substring(offset, offset + fit), lineNumber));
                    offset += fit;
                }
            }

            if (newLine < 0) {
                break;
            }
            pos = newLine + 1;
            lineNumber++;
        }

        return new TextLayout(safeValue, Collections.unmodifiableList(lines), widthProvider);
    }

    /**
     * Find how many characters of {@code content} starting at {@code offset} fit
     * into the available width. Always returns at least one character so that a
     * single character wider than the editor cannot produce an empty line.
     */
    private static int lengthThatFits(String content, int offset, int maxWidth,
                                      IWidthProvider widthProvider) {
        int fit = 0;
        while (offset + fit < content.length()) {
            int candidate = fit + 1;
            if (widthProvider.getWidth(content.substring(offset, offset + candidate)) > maxWidth) {
                break;
            }
            fit = candidate;
        }
        return Math.max(1, fit);
    }

    public String getValue() {
        return value;
    }

    public List<Line> getLines() {
        return lines;
    }

    public int getLineCount() {
        return lines.size();
    }

    public Line getLine(int index) {
        return lines.get(Math.max(0, Math.min(lines.size() - 1, index)));
    }

    /**
     * Find the visual line that contains the given character index. A character
     * index at a line break belongs to the line before the break.
     * @param charPos Index in the full value.
     * @return The visual line index, never out of bounds.
     */
    public int getLineIndexAt(int charPos) {
        int low = 0;
        int high = lines.size() - 1;
        int result = 0;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).getStart() <= charPos) {
                result = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return result;
    }

    /**
     * The x offset of a character index inside a visual line.
     * @param lineIndex The visual line.
     * @param charPos Index in the full value.
     * @return The pixel offset from the line's left edge.
     */
    public int getX(int lineIndex, int charPos) {
        Line line = getLine(lineIndex);
        int column = Math.max(0, Math.min(line.getText().length(), charPos - line.getStart()));
        return widthProvider.getWidth(line.getText().substring(0, column));
    }

    /**
     * The character index inside a visual line that is closest to an x offset.
     * @param lineIndex The visual line.
     * @param x The pixel offset from the line's left edge.
     * @return Index in the full value.
     */
    public int getCharIndexAt(int lineIndex, int x) {
        Line line = getLine(lineIndex);
        if (x <= 0) {
            return line.getStart();
        }
        int column = line.getText().length();
        for (int i = 1; i <= line.getText().length(); i++) {
            if (widthProvider.getWidth(line.getText().substring(0, i)) > x) {
                column = i - 1;
                break;
            }
        }
        return line.getStart() + column;
    }

    /**
     * Move the cursor one visual line up or down, keeping the pixel column as
     * close as possible.
     * @param charPos The current character index.
     * @param direction -1 for up, 1 for down.
     * @return The new character index.
     */
    public int changeLine(int charPos, int direction) {
        int lineIndex = getLineIndexAt(charPos);
        int column = getX(lineIndex, charPos);
        int target = Math.max(0, Math.min(lines.size() - 1, lineIndex + direction));
        return getCharIndexAt(target, column);
    }

    /**
     * The index of the first character of the visual line containing the given index.
     */
    public int findLineStart(int charPos) {
        return getLine(getLineIndexAt(charPos)).getStart();
    }

    /**
     * The index of the last character of the visual line containing the given index.
     */
    public int findLineEnd(int charPos) {
        Line line = getLine(getLineIndexAt(charPos));
        return line.getEnd();
    }

}

