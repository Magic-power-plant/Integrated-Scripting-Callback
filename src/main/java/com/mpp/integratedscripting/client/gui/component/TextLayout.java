package com.mpp.integratedscripting.client.gui.component;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wrapped-line model of a multi-line script, used by the 1.12 terminal text
 * editor.
 *
 * <p>1.12.2 has no {@code StringSplitter}/{@code FormattedCharSequence}, so the
 * editor needs its own line layout (docs/PORTING-1.12.2.md §7.9.3). This class
 * contains only the geometry, without any rendering or Minecraft state, so it
 * can be unit tested with a fake width provider.</p>
 *
 * <p>Like CodeMirror's view of a document, the layout is <em>incremental</em>:
 * {@link #rebuild(TextLayout, String, int, IWidthProvider)} keeps the measured
 * wrap lengths of every line whose text did not change, and only re-measures the
 * lines that did. Measuring is also O(characters) instead of O(characters²)
 * when the provider can report one glyph at a time ({@link ICharWidthProvider}),
 * because a Minecraft {@code FontRenderer#getStringWidth} is exactly the sum of
 * its {@code getCharWidth} values. The old code measured every prefix of every
 * line, which was the hot path in docs/AUDIT.md D3.</p>
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
     * A width provider that can also measure a single glyph, enabling the fast
     * additive layout path. A Minecraft {@code FontRenderer} qualifies because
     * its string width is the sum of its character widths.
     */
    public interface ICharWidthProvider extends IWidthProvider {
        int getCharWidth(char c);
    }

    /**
     * One visual line of the wrapped layout.
     */
    public static final class Line {
        private final int start;
        private final int logicalStart;
        private final String text;
        private final int lineNumber;

        public Line(int start, String text, int lineNumber) {
            this(start, start, text, lineNumber);
        }

        Line(int start, int logicalStart, String text, int lineNumber) {
            this.start = start;
            this.logicalStart = logicalStart;
            this.text = text;
            this.lineNumber = lineNumber;
        }

        /** Index of this line's first character in the full value. */
        public int getStart() {
            return start;
        }

        /** Index of the first character of the logical line this visual line belongs to. */
        public int getLogicalLineStart() {
            return logicalStart;
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
    private final int maxWidth;
    private final List<Line> lines;
    private final List<LogicalLine> logicalLines;
    private final IWidthProvider widthProvider;
    @Nullable
    private final CharWidthCache charWidths;

    private TextLayout(String value, int maxWidth, List<Line> lines, List<LogicalLine> logicalLines,
                       IWidthProvider widthProvider, @Nullable CharWidthCache charWidths) {
        this.value = value;
        this.maxWidth = maxWidth;
        this.lines = Collections.unmodifiableList(lines);
        this.logicalLines = Collections.unmodifiableList(logicalLines);
        this.widthProvider = widthProvider;
        this.charWidths = charWidths;
    }

    /**
     * Wrap a value into visual lines.
     * @param value The full editor content.
     * @param maxWidth The available width in pixels; values below 1 are treated as 1.
     * @param widthProvider Measures text fragments.
     * @return The layout. An empty value still yields one (empty) line.
     */
    public static TextLayout build(String value, int maxWidth, IWidthProvider widthProvider) {
        return rebuild(null, value, maxWidth, widthProvider);
    }

    /**
     * Rebuild a layout, reusing the measured lines of the previous one.
     * @param previous The previous layout of this editor, or {@code null}.
     * @param value The new full editor content.
     * @param maxWidth The available width in pixels.
     * @param widthProvider Measures text fragments; reuse only happens for the
     *                      same provider instance.
     * @return The layout, which may be {@code previous} itself when nothing changed.
     */
    public static TextLayout rebuild(@Nullable TextLayout previous, String value, int maxWidth,
                                     IWidthProvider widthProvider) {
        String safeValue = value == null ? "" : value;
        int safeWidth = Math.max(1, maxWidth);
        if (previous != null && previous.widthProvider == widthProvider
                && previous.maxWidth == safeWidth && previous.value.equals(safeValue)) {
            return previous;
        }

        CharWidthCache charWidths = widthProvider instanceof ICharWidthProvider
                ? new CharWidthCache((ICharWidthProvider) widthProvider) : null;
        List<LogicalLine> logicalLines = new ArrayList<>();
        List<Line> lines = new ArrayList<>();

        int pos = 0;
        int lineNumber = 1;
        while (true) {
            int newline = safeValue.indexOf('\n', pos);
            int end = newline < 0 ? safeValue.length() : newline;
            String content = safeValue.substring(pos, end);

            LogicalLine logical = new LogicalLine(content, pos);
            if (previous != null && logicalLines.size() < previous.logicalLines.size()) {
                logical.reuse(previous.logicalLines.get(logicalLines.size()), safeWidth, charWidths);
            }
            int[] wraps = logical.wrapLengths(safeWidth, charWidths, widthProvider);
            int offset = 0;
            for (int wrapLength : wraps) {
                lines.add(new Line(pos + offset, pos,
                        content.substring(offset, offset + wrapLength), lineNumber));
                offset += wrapLength;
            }
            logicalLines.add(logical);

            if (newline < 0) {
                break;
            }
            pos = newline + 1;
            lineNumber++;
        }

        return new TextLayout(safeValue, safeWidth, lines, logicalLines, widthProvider, charWidths);
    }

    /**
     * The wrap lengths of a logical line, cached across rebuilds. A length of
     * zero only occurs for an empty line.
     */
    private static final class LogicalLine {
        private final String text;
        private final int start;
        @Nullable
        private int[] wraps;
        private int wrapsWidth = -1;
        private boolean wrapsByCharWidth;

        LogicalLine(String text, int start) {
            this.text = text;
            this.start = start;
        }

        void reuse(LogicalLine previous, int maxWidth, @Nullable CharWidthCache charWidths) {
            if (previous.wraps != null && previous.wrapsWidth == maxWidth
                    && previous.wrapsByCharWidth == (charWidths != null)
                    && previous.text.equals(this.text)) {
                this.wraps = previous.wraps;
                this.wrapsWidth = maxWidth;
                this.wrapsByCharWidth = charWidths != null;
            }
        }

        int[] wrapLengths(int maxWidth, @Nullable CharWidthCache charWidths,
                          IWidthProvider widthProvider) {
            if (this.wraps != null && this.wrapsWidth == maxWidth
                    && this.wrapsByCharWidth == (charWidths != null)) {
                return this.wraps;
            }
            this.wraps = computeWrapLengths(this.text, maxWidth, charWidths, widthProvider);
            this.wrapsWidth = maxWidth;
            this.wrapsByCharWidth = charWidths != null;
            return this.wraps;
        }

    }

    private static int[] computeWrapLengths(String content, int maxWidth,
                                            @Nullable CharWidthCache charWidths,
                                            IWidthProvider widthProvider) {
        if (content.isEmpty()) {
            return new int[]{0};
        }
        List<Integer> lengths = new ArrayList<>();
        int offset = 0;
        while (offset < content.length()) {
            int fit = charWidths != null
                    ? lengthThatFitsFast(content, offset, maxWidth, charWidths)
                    : lengthThatFitsSlow(content, offset, maxWidth, widthProvider);
            lengths.add(fit);
            offset += fit;
        }
        int[] result = new int[lengths.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = lengths.get(i);
        }
        return result;
    }

    /**
     * O(characters) wrap measurement: accumulate glyph widths until the next one
     * would exceed the available width. Always returns at least one character so
     * a single character wider than the editor cannot produce an empty line.
     */
    private static int lengthThatFitsFast(String content, int offset, int maxWidth,
                                          CharWidthCache charWidths) {
        int fit = 0;
        int width = 0;
        while (offset + fit < content.length()) {
            int charWidth = charWidths.widthOf(content.charAt(offset + fit));
            if (width + charWidth > maxWidth) {
                break;
            }
            width += charWidth;
            fit++;
        }
        return Math.max(1, fit);
    }

    /**
     * Fallback for providers that cannot measure a single glyph: measure growing
     * prefixes with the generic provider. Preserved for compatibility with the
     * original implementation and its tests.
     */
    private static int lengthThatFitsSlow(String content, int offset, int maxWidth,
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

    /** The width the layout was built for. */
    public int getMaxWidth() {
        return maxWidth;
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
        if (this.charWidths != null) {
            return this.charWidths.widthOf(line.getText(), 0, column);
        }
        return this.widthProvider.getWidth(line.getText().substring(0, column));
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
        String text = line.getText();
        if (this.charWidths != null) {
            int width = 0;
            for (int i = 0; i < text.length(); i++) {
                width += this.charWidths.widthOf(text.charAt(i));
                if (width > x) {
                    return line.getStart() + i;
                }
            }
            return line.getEnd();
        }
        int column = text.length();
        for (int i = 1; i <= text.length(); i++) {
            if (this.widthProvider.getWidth(text.substring(0, i)) > x) {
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

    /**
     * Cached glyph widths. Only used when the provider implements
     * {@link ICharWidthProvider}, where measuring one glyph is much cheaper than
     * measuring a growing prefix.
     */
    private static final class CharWidthCache {
        private final ICharWidthProvider provider;
        private final int[] ascii = new int[128];
        private final Map<Character, Integer> wide = new HashMap<>();

        CharWidthCache(ICharWidthProvider provider) {
            this.provider = provider;
            java.util.Arrays.fill(this.ascii, -1);
        }

        int widthOf(char c) {
            if (c < 128) {
                int cached = this.ascii[c];
                if (cached >= 0) {
                    return cached;
                }
                int width = Math.max(0, this.provider.getCharWidth(c));
                this.ascii[c] = width;
                return width;
            }
            Integer cached = this.wide.get(c);
            if (cached != null) {
                return cached;
            }
            int width = Math.max(0, this.provider.getCharWidth(c));
            this.wide.put(c, width);
            return width;
        }

        int widthOf(String text, int from, int to) {
            int width = 0;
            for (int i = from; i < to; i++) {
                width += widthOf(text.charAt(i));
            }
            return width;
        }
    }

}
