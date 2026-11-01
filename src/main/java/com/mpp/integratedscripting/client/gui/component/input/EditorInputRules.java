package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.LexicalContext;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure editing rules of the multi-line script editor.
 *
 * <p>The terminal editor used to be a plain text box: {@code Enter} inserted a
 * bare newline, {@code Tab} always inserted four spaces, brackets were never
 * completed and there was no undo. The rules here are modelled on CodeMirror's
 * editing commands and are deliberately independent of Minecraft:</p>
 *
 * <ul>
 *   <li>{@link #newline} is CodeMirror's {@code insertNewlineAndIndent}: it
 *   keeps the current indentation, adds one unit after an opening bracket and
 *   splits {@code {} into three lines.</li>
 *   <li>{@link #handleInsert} is {@code closeBrackets}: it completes bracket and
 *   quote pairs, wraps the selection, skips over a closing character that is
 *   already there and rejects completions inside strings and comments
 *   ({@link LexicalContext}).</li>
 *   <li>{@link #indentLines}, {@link #toggleLineComment},
 *   {@link #duplicateLines}, {@link #deleteLines} and {@link #moveLines} are the
 *   line-oriented commands bound to Tab, Shift-Tab, Ctrl-/ and friends.</li>
 *   <li>{@link #smartHome} and the word navigation helpers mirror CodeMirror's
 *   {@code cursorLineBoundaryBackward} and {@code findWordStart}/{@code End}.</li>
 * </ul>
 *
 * <p>Every method takes the current value and caret/selection and returns a new
 * {@link EditorEdit}, or {@code null} when the command does not apply, so the
 * behaviour is fully unit testable (see {@code EditorInputRulesTest}).</p>
 *
 * @author rubensworks
 */
public final class EditorInputRules {

    /** One indentation step: four spaces, matching the editor's soft tabs. */
    public static final String INDENT_UNIT = "    ";

    /** Visual width of a tab stop for the elastic-tab rule. */
    public static final int TAB_SIZE = 4;

    private static final String OPENERS = "([{";
    private static final String CLOSERS = ")]}";
    private static final String QUOTES = "\"'`";

    private EditorInputRules() {
    }

    // ---------------------------------------------------------------- positions

    /** Clamp a position into the value. */
    public static int clamp(String value, int pos) {
        return Math.max(0, Math.min(value.length(), pos));
    }

    /** Index of the first character of the line containing {@code pos}. */
    public static int lineStart(String value, int pos) {
        int clamped = clamp(value, pos);
        if (clamped == 0) {
            return 0;
        }
        int index = value.lastIndexOf('\n', clamped - 1);
        return index < 0 ? 0 : index + 1;
    }

    /**
     * Index of the line break ending the line containing {@code pos}, or the
     * value length for the last line. A position that points at a line break
     * belongs to the line before it.
     */
    public static int lineEnd(String value, int pos) {
        int clamped = clamp(value, pos);
        int index = value.indexOf('\n', clamped);
        return index < 0 ? value.length() : index;
    }

    /** The whitespace prefix of a single line. */
    public static String leadingWhitespace(String line) {
        int i = 0;
        while (i < line.length() && isIndentChar(line.charAt(i))) {
            i++;
        }
        return line.substring(0, i);
    }

    /** The first non-whitespace character of the line containing {@code pos}. */
    public static int firstNonWhitespace(String value, int pos) {
        int start = lineStart(value, pos);
        int end = lineEnd(value, pos);
        int i = start;
        while (i < end && isIndentChar(value.charAt(i))) {
            i++;
        }
        return i;
    }

    /**
     * Home behaviour of CodeMirror's smart home: go to the first non-whitespace
     * character, and to the very start of the line when already there.
     */
    public static int smartHome(String value, int pos) {
        int start = lineStart(value, pos);
        int indent = firstNonWhitespace(value, pos);
        return pos == indent ? start : indent;
    }

    /** Position of the start of the previous word, skipping whitespace first. */
    public static int findWordStart(String value, int pos) {
        int i = clamp(value, pos);
        while (i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
            i--;
        }
        if (i > 0) {
            boolean word = isWordChar(value.charAt(i - 1));
            while (i > 0 && !Character.isWhitespace(value.charAt(i - 1))
                    && isWordChar(value.charAt(i - 1)) == word) {
                i--;
            }
        }
        return i;
    }

    /** Position of the end of the next word, skipping whitespace first. */
    public static int findWordEnd(String value, int pos) {
        int i = clamp(value, pos);
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) {
            i++;
        }
        if (i < value.length()) {
            boolean word = isWordChar(value.charAt(i));
            while (i < value.length() && !Character.isWhitespace(value.charAt(i))
                    && isWordChar(value.charAt(i)) == word) {
                i++;
            }
        }
        return i;
    }

    /** How many spaces a tab press inserts, up to the next tab stop. */
    public static int tabSpaces(String value, int pos, int tabSize) {
        int column = clamp(value, pos) - lineStart(value, pos);
        int size = Math.max(1, tabSize);
        int spaces = size - (column % size);
        return spaces == 0 ? size : spaces;
    }

    /** Whether a character can be part of a JavaScript identifier. */
    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    // ---------------------------------------------------------------- enter / indent

    /**
     * Insert a newline with indentation, like CodeMirror's
     * {@code insertNewlineAndIndent}.
     * @param value The document.
     * @param cursor The caret.
     * @param anchor The other end of the selection.
     * @param unit One indentation unit.
     * @return The edit.
     */
    public static EditorEdit newline(String value, int cursor, int anchor, String unit) {
        return newline(value, cursor, anchor, unit, LexicalContext.CODE);
    }

    /**
     * Insert a newline with indentation, aware of the lexical context.
     * @param value The document.
     * @param cursor The caret.
     * @param anchor The other end of the selection.
     * @param unit One indentation unit.
     * @param context The lexical context at the caret ({@link LexicalContext}).
     * @return The edit.
     */
    public static EditorEdit newline(String value, int cursor, int anchor, String unit, int context) {
        int start = Math.min(clamp(value, cursor), clamp(value, anchor));
        int end = Math.max(clamp(value, cursor), clamp(value, anchor));
        String text = value.substring(0, start) + value.substring(end);
        int caret = start;
        int lineStart = lineStart(text, caret);
        int lineEnd = lineEnd(text, caret);
        String lineText = text.substring(lineStart, lineEnd);
        String before = text.substring(lineStart, caret);
        String after = text.substring(caret, lineEnd);
        String lineIndent = leadingWhitespace(lineText);
        String trimmedBefore = before.trim();
        String trimmedAfter = after.trim();
        String indent = lineIndent;

        if (context == LexicalContext.COMMENT) {
            // Continue a comment in the same style CodeMirror's comment support does.
            if (trimmedBefore.startsWith("//")) {
                indent = lineIndent + "// ";
            } else if (trimmedBefore.startsWith("/*") || trimmedBefore.startsWith("*")) {
                indent = lineIndent + " * ";
            }
            return insertNewline(text, caret, indent, null);
        }
        if (context == LexicalContext.STRING) {
            // Inside a string literal: keep the indentation, add nothing.
            return insertNewline(text, caret, lineIndent, null);
        }

        String closingIndent = null;
        if (!trimmedBefore.isEmpty() && endsWithOpener(trimmedBefore)) {
            indent = lineIndent + unit;
            if (!trimmedAfter.isEmpty()
                    && matches(trimmedBefore.charAt(trimmedBefore.length() - 1), trimmedAfter.charAt(0))) {
                closingIndent = lineIndent;
            }
        } else if (trimmedBefore.isEmpty()) {
            String previousLine = previousLine(text, lineStart);
            String trimmedPrevious = previousLine.trim();
            if (!trimmedPrevious.isEmpty() && endsWithOpener(trimmedPrevious)) {
                indent = leadingWhitespace(previousLine) + unit;
            }
            if (!trimmedAfter.isEmpty() && isClosingBracket(trimmedAfter.charAt(0))) {
                // Typing Enter in front of a closing bracket keeps the new line
                // aligned with that bracket.
                indent = lineIndent;
            }
        }
        return insertNewline(text, caret, indent, closingIndent);
    }

    private static EditorEdit insertNewline(String text, int caret, String indent,
                                            @Nullable String closingIndent) {
        String insertion = closingIndent == null
                ? "\n" + indent
                : "\n" + indent + "\n" + closingIndent;
        String newValue = text.substring(0, caret) + insertion + text.substring(caret);
        int newCaret = caret + 1 + indent.length();
        return new EditorEdit(newValue, newCaret, newCaret);
    }

    /**
     * Insert a blank, correctly indented line below the current one
     * (CodeMirror's {@code insertBlankLine}).
     */
    public static EditorEdit insertBlankLine(String value, int cursor, int anchor) {
        int caret = Math.max(clamp(value, cursor), clamp(value, anchor));
        int end = lineEnd(value, caret);
        String indent = leadingWhitespace(value.substring(lineStart(value, end), end));
        String insertion = "\n" + indent;
        String newValue = value.substring(0, end) + insertion + value.substring(end);
        int newCaret = end + insertion.length();
        return new EditorEdit(newValue, newCaret, newCaret);
    }

    // ---------------------------------------------------------------- brackets / quotes

    /**
     * Handle a typed character with CodeMirror-style bracket and quote
     * completion.
     * @param value The document.
     * @param cursor The caret.
     * @param anchor The other end of the selection.
     * @param typed The typed character.
     * @param context The lexical context at the caret.
     * @param unit One indentation unit.
     * @return The edit, or {@code null} when the character should just be inserted.
     */
    @Nullable
    public static EditorEdit handleInsert(String value, int cursor, int anchor, char typed,
                                          int context, String unit) {
        int start = Math.min(clamp(value, cursor), clamp(value, anchor));
        int end = Math.max(clamp(value, cursor), clamp(value, anchor));
        boolean opener = isOpeningBracket(typed);
        boolean closer = isClosingBracket(typed);
        boolean quote = isQuote(typed);

        // 1. Skip over an identical closing character (or quote) right after the
        //    caret. This is what makes typing ")" after an auto-inserted ")"
        //    move the caret instead of duplicating the bracket.
        if (start == end && (closer || quote)
                && start < value.length() && value.charAt(start) == typed) {
            return new EditorEdit(value, start + 1, start + 1);
        }

        // 2. Wrap the selection in a pair (only in code).
        if (start != end && context == LexicalContext.CODE && (opener || quote)) {
            char close = quote ? typed : closingFor(typed);
            String selected = value.substring(start, end);
            String newValue = value.substring(0, start) + typed + selected + close + value.substring(end);
            return new EditorEdit(newValue, end + 1, start + 1);
        }

        if (context != LexicalContext.CODE) {
            return null;
        }

        if (opener) {
            char next = start < value.length() ? value.charAt(start) : 0;
            if (next != 0 && (isWordChar(next) || isOpeningBracket(next)
                    || isClosingBracket(next) || isQuote(next))) {
                return null;
            }
            String newValue = value.substring(0, start) + typed + closingFor(typed)
                    + value.substring(start);
            return new EditorEdit(newValue, start + 1, start + 1);
        }

        if (quote) {
            char previous = start > 0 ? value.charAt(start - 1) : 0;
            char next = start < value.length() ? value.charAt(start) : 0;
            if (previous == '\\') {
                return null;
            }
            // Do not pair a quote inside a word such as don't.
            if (next != 0 && isWordChar(next)) {
                return null;
            }
            if (previous != 0 && isWordChar(previous)) {
                return null;
            }
            String newValue = value.substring(0, start) + typed + typed + value.substring(start);
            return new EditorEdit(newValue, start + 1, start + 1);
        }

        if (closer) {
            // Re-indent a line that only consists of whitespace in front of the
            // typed closing bracket: typing "}" dedents by one unit.
            int lineStart = lineStart(value, start);
            String prefix = value.substring(lineStart, start);
            if (!prefix.isEmpty() && prefix.trim().isEmpty()) {
                String indent = removeOneIndent(prefix, unit);
                String newValue = value.substring(0, lineStart) + indent + typed + value.substring(start);
                int caret = lineStart + indent.length() + 1;
                return new EditorEdit(newValue, caret, caret);
            }
            return null;
        }

        return null;
    }

    /**
     * The range Backspace deletes when the caret sits in the line's leading
     * spaces: CodeMirror deletes back to the previous indentation tab stop
     * instead of removing a single space.
     * @param value The document.
     * @param cursor The caret.
     * @return {@code {from, to}}, or {@code null} when the caret is not in
     *         leading spaces.
     */
    @Nullable
    public static int[] indentationBackspaceRange(String value, int cursor) {
        int pos = clamp(value, cursor);
        int lineStart = lineStart(value, pos);
        if (pos <= lineStart) {
            return null;
        }
        String prefix = value.substring(lineStart, pos);
        if (prefix.trim().length() != 0) {
            return null;
        }
        int spaces = 0;
        for (int i = prefix.length() - 1; i >= 0 && prefix.charAt(i) == ' '; i--) {
            spaces++;
        }
        if (spaces == 0) {
            // Only tabs before the caret: delete one character as usual.
            return null;
        }
        int remove = spaces % TAB_SIZE == 0 ? TAB_SIZE : spaces % TAB_SIZE;
        remove = Math.min(remove, spaces);
        return new int[]{pos - remove, pos};
    }

    /**
     * The range of an empty bracket or quote pair around the caret, which
     * Backspace deletes as one unit (CodeMirror's {@code deleteBracketPair}).
     * @param value The document.
     * @param cursor The caret.
     * @return {@code {start, end}} including both characters, or {@code null}.
     */
    @Nullable
    public static int[] emptyPair(String value, int cursor) {
        if (cursor <= 0 || cursor >= value.length()) {
            return null;
        }
        char before = value.charAt(cursor - 1);
        char after = value.charAt(cursor);
        if (isOpeningBracket(before) && closingFor(before) == after) {
            return new int[]{cursor - 1, cursor + 1};
        }
        if (isQuote(before) && before == after) {
            return new int[]{cursor - 1, cursor + 1};
        }
        return null;
    }

    /** Whether the character opens a bracket pair. */
    public static boolean isOpeningBracket(char c) {
        return OPENERS.indexOf(c) >= 0;
    }

    /** Whether the character closes a bracket pair. */
    public static boolean isClosingBracket(char c) {
        return CLOSERS.indexOf(c) >= 0;
    }

    /** Whether the character is a quote or backtick. */
    public static boolean isQuote(char c) {
        return QUOTES.indexOf(c) >= 0;
    }

    /** The closing character for an opening bracket. */
    public static char closingFor(char open) {
        switch (open) {
            case '(':
                return ')';
            case '[':
                return ']';
            case '{':
                return '}';
            default:
                return open;
        }
    }

    /** Whether two bracket characters are a matching open/close pair. */
    public static boolean matches(char open, char close) {
        return isOpeningBracket(open) && closingFor(open) == close;
    }

    // ---------------------------------------------------------------- line commands

    /**
     * Indent or dedent every line touched by the selection by one unit
     * (CodeMirror's {@code indentMore}/{@code indentLess}, Tab and Shift-Tab).
     * @param value The document.
     * @param cursor The caret.
     * @param anchor The other end of the selection.
     * @param unit One indentation unit.
     * @param direction {@code 1} to indent, {@code -1} to dedent.
     * @return The edit.
     */
    public static EditorEdit indentLines(String value, int cursor, int anchor, String unit, int direction) {
        int[] range = selectedLineRange(value, cursor, anchor);
        List<Integer> starts = lineStarts(value, range[0], range[1]);
        StringBuilder builder = new StringBuilder(value);
        int newCursor = clamp(value, cursor);
        int newAnchor = clamp(value, anchor);
        for (int i = starts.size() - 1; i >= 0; i--) {
            int at = starts.get(i);
            if (direction > 0) {
                builder.insert(at, unit);
                if (newCursor >= at) {
                    newCursor += unit.length();
                }
                if (newAnchor >= at) {
                    newAnchor += unit.length();
                }
            } else {
                int remove = removableIndent(builder, at, unit);
                if (remove > 0) {
                    builder.delete(at, at + remove);
                    newCursor = shiftAfterDelete(newCursor, at, remove);
                    newAnchor = shiftAfterDelete(newAnchor, at, remove);
                }
            }
        }
        return new EditorEdit(builder.toString(), newCursor, newAnchor);
    }

    /**
     * Toggle a line comment on every line touched by the selection
     * (CodeMirror's {@code toggleComment}, Ctrl+/).
     * @param value The document.
     * @param cursor The caret.
     * @param anchor The other end of the selection.
     * @param prefix The line comment prefix, {@code "//"} for JavaScript.
     * @return The edit.
     */
    public static EditorEdit toggleLineComment(String value, int cursor, int anchor, String prefix) {
        int[] range = selectedLineRange(value, cursor, anchor);
        List<Integer> starts = lineStarts(value, range[0], range[1]);
        boolean anyContent = false;
        boolean allCommented = true;
        for (int at : starts) {
            String line = value.substring(at, lineEnd(value, at));
            if (line.trim().isEmpty()) {
                continue;
            }
            anyContent = true;
            int indent = leadingWhitespace(line).length();
            if (!line.startsWith(prefix, indent)) {
                allCommented = false;
                break;
            }
        }
        boolean comment = !anyContent || !allCommented;

        StringBuilder builder = new StringBuilder(value);
        int newCursor = clamp(value, cursor);
        int newAnchor = clamp(value, anchor);
        for (int i = starts.size() - 1; i >= 0; i--) {
            int at = starts.get(i);
            String line = value.substring(at, lineEnd(value, at));
            if (line.trim().isEmpty()) {
                continue;
            }
            int indent = at + leadingWhitespace(line).length();
            if (comment) {
                String insert = prefix + " ";
                builder.insert(indent, insert);
                if (newCursor >= indent) {
                    newCursor += insert.length();
                }
                if (newAnchor >= indent) {
                    newAnchor += insert.length();
                }
            } else {
                int remove = prefix.length();
                if (indent + remove < builder.length() && builder.charAt(indent + remove) == ' ') {
                    remove++;
                }
                builder.delete(indent, indent + remove);
                newCursor = shiftAfterDelete(newCursor, indent, remove);
                newAnchor = shiftAfterDelete(newAnchor, indent, remove);
            }
        }
        return new EditorEdit(builder.toString(), newCursor, newAnchor);
    }

    /**
     * Duplicate the lines touched by the selection, placing the caret (and the
     * selection) in the copy (CodeMirror's {@code copyLineDown}, Ctrl+D).
     */
    public static EditorEdit duplicateLines(String value, int cursor, int anchor) {
        int[] range = selectedLineRange(value, cursor, anchor);
        String block = value.substring(range[0], range[1]);
        String insertion = "\n" + block;
        String newValue = value.substring(0, range[1]) + insertion + value.substring(range[1]);
        return new EditorEdit(newValue, clamp(value, cursor) + insertion.length(),
                clamp(value, anchor) + insertion.length());
    }

    /**
     * Delete the lines touched by the selection (CodeMirror's
     * {@code deleteLine}, Ctrl+Shift+K).
     */
    public static EditorEdit deleteLines(String value, int cursor, int anchor) {
        int[] range = selectedLineRange(value, cursor, anchor);
        int from = range[0];
        int to = range[1];
        if (to < value.length() && value.charAt(to) == '\n') {
            to++;
        } else if (from > 0) {
            from--;
        }
        String newValue = value.substring(0, from) + value.substring(to);
        int caret = Math.min(from, newValue.length());
        return new EditorEdit(newValue, caret, caret);
    }

    /**
     * Move the lines touched by the selection one line up or down
     * (CodeMirror's {@code moveLineUp}/{@code moveLineDown}, Alt+Up/Down).
     * @param direction {@code -1} for up, {@code 1} for down.
     */
    public static EditorEdit moveLines(String value, int cursor, int anchor, int direction) {
        int[] range = selectedLineRange(value, cursor, anchor);
        int from = range[0];
        int to = range[1];
        if (direction < 0) {
            if (from == 0) {
                return new EditorEdit(value, cursor, anchor);
            }
            int previousStart = lineStart(value, from - 1);
            String block = value.substring(from, to);
            String previous = value.substring(previousStart, from - 1);
            String newValue = value.substring(0, previousStart) + block + "\n" + previous
                    + value.substring(to);
            int delta = from - previousStart;
            return new EditorEdit(newValue, clamp(value, cursor) - delta, clamp(value, anchor) - delta);
        }
        if (to >= value.length()) {
            return new EditorEdit(value, cursor, anchor);
        }
        int nextStart = to + 1;
        int nextEnd = lineEnd(value, nextStart);
        String block = value.substring(from, to);
        String next = value.substring(nextStart, nextEnd);
        String newValue = value.substring(0, from) + next + "\n" + block + value.substring(nextEnd);
        int delta = next.length() + 1;
        return new EditorEdit(newValue, clamp(value, cursor) + delta, clamp(value, anchor) + delta);
    }

    // ---------------------------------------------------------------- internals

    /**
     * The character range of all lines touched by a selection. A selection that
     * ends exactly at a line start does not include that line, which is what
     * line-oriented commands should do.
     */
    private static int[] selectedLineRange(String value, int cursor, int anchor) {
        int start = Math.min(clamp(value, cursor), clamp(value, anchor));
        int end = Math.max(clamp(value, cursor), clamp(value, anchor));
        int rangeStart = lineStart(value, start);
        int last = end;
        if (end > start && end > 0 && value.charAt(end - 1) == '\n') {
            last = end - 1;
        }
        return new int[]{rangeStart, lineEnd(value, last)};
    }

    private static List<Integer> lineStarts(String value, int rangeStart, int rangeEnd) {
        List<Integer> starts = new ArrayList<>();
        int pos = rangeStart;
        while (true) {
            starts.add(pos);
            int newline = value.indexOf('\n', pos);
            if (newline < 0 || newline >= rangeEnd) {
                break;
            }
            pos = newline + 1;
        }
        return starts;
    }

    private static int removableIndent(CharSequence text, int at, String unit) {
        if (at >= text.length()) {
            return 0;
        }
        if (text.charAt(at) == '\t') {
            return 1;
        }
        int count = 0;
        while (count < unit.length() && at + count < text.length() && text.charAt(at + count) == ' ') {
            count++;
        }
        return count;
    }

    private static String removeOneIndent(String indent, String unit) {
        if (indent.endsWith("\t")) {
            return indent.substring(0, indent.length() - 1);
        }
        int remove = 0;
        while (remove < unit.length() && remove < indent.length()
                && indent.charAt(indent.length() - 1 - remove) == ' ') {
            remove++;
        }
        return indent.substring(0, indent.length() - remove);
    }

    private static int shiftAfterDelete(int pos, int at, int remove) {
        if (pos >= at + remove) {
            return pos - remove;
        }
        if (pos > at) {
            return at;
        }
        return pos;
    }

    private static String previousLine(String value, int lineStart) {
        if (lineStart <= 0) {
            return "";
        }
        int previousStart = lineStart(value, lineStart - 1);
        return value.substring(previousStart, lineStart - 1);
    }

    private static boolean endsWithOpener(String text) {
        if (text.isEmpty()) {
            return false;
        }
        return isOpeningBracket(text.charAt(text.length() - 1)) || text.endsWith("=>");
    }

    private static boolean isIndentChar(char c) {
        return c == ' ' || c == '\t';
    }

}
