package com.mpp.integratedscripting.core.language;

import com.google.common.collect.Lists;
import com.mpp.integratedscripting.callback.TextStyle;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Minimal line-based tokenizer for JavaScript syntax highlighting.
 *
 * <p>The 1.20 reference implementation splits every line on a fixed list of
 * tokens (docs/PORTING-1.12.2.md §7.9.3). That never colours comments, string
 * literals or numbers, while §11.2 item 13 asks for exactly those classes. This
 * tokenizer performs a single left-to-right scan, so the fixed tokens are only
 * matched outside of comments and string literals.</p>
 *
 * <p>It is deliberately line-based and stateless: a block comment that starts on
 * one line is coloured until the end of that line, and the rest of the block
 * comment is not remembered for the following lines. This is a display-only
 * limitation shared with the reference implementation (it has no lexer state
 * either), see the M5 notes in docs/MILESTONES.md.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptTokenizer {

    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(new java.util.HashSet<>(
            java.util.Arrays.asList(
                    "const", "delete", "let", "var", "function", "return", "true", "false",
                    "null", "undefined", "if", "else", "for", "while", "new", "typeof")));

    public static final String SYMBOLS = "{}()[]=;.";

    private JavaScriptTokenizer() {
    }

    /**
     * Split one line into styled segments. Adjacent segments with the same style
     * are merged, and segment contents always concatenate back to the input.
     * @param line The line to tokenize, without newline characters.
     * @return The styled segments; empty for an empty line.
     */
    public static List<Pair<TextFormatting, String>> tokenize(String line) {
        List<Pair<TextFormatting, String>> segments = Lists.newArrayList();
        if (line == null || line.isEmpty()) {
            return segments;
        }

        int i = 0;
        int length = line.length();
        while (i < length) {
            char c = line.charAt(i);

            // Line or block comment (a block comment is coloured until the end of this line).
            if (c == '/' && i + 1 < length) {
                char next = line.charAt(i + 1);
                if (next == '/') {
                    add(segments, TextStyle.COMMENT, line.substring(i));
                    break;
                }
                if (next == '*') {
                    int end = line.indexOf("*/", i + 2);
                    end = end < 0 ? length : end + 2;
                    add(segments, TextStyle.COMMENT, line.substring(i, end));
                    i = end;
                    continue;
                }
            }

            // String literal (single, double and template quotes).
            if (c == '"' || c == '\'' || c == '`') {
                int end = findStringEnd(line, i);
                add(segments, TextStyle.CONSTANT, line.substring(i, end));
                i = end;
                continue;
            }

            // Numeric literal.
            if (Character.isDigit(c)) {
                int end = i;
                while (end < length && isNumberChar(line.charAt(end))) {
                    end++;
                }
                add(segments, TextStyle.CONSTANT, line.substring(i, end));
                i = end;
                continue;
            }

            // Identifier or keyword.
            if (Character.isJavaIdentifierStart(c)) {
                int end = i;
                while (end < length && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String word = line.substring(i, end);
                add(segments, KEYWORDS.contains(word) ? TextStyle.KEYWORD : TextStyle.DEFAULT, word);
                i = end;
                continue;
            }

            // Symbols and everything else.
            add(segments, SYMBOLS.indexOf(c) >= 0 ? TextStyle.SYMBOL : TextStyle.DEFAULT,
                    String.valueOf(c));
            i++;
        }
        return segments;
    }

    private static int findStringEnd(String line, int start) {
        char quote = line.charAt(start);
        int i = start + 1;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return line.length();
    }

    private static boolean isNumberChar(char c) {
        return Character.isDigit(c) || c == '.' || c == 'x' || c == 'X' || c == 'e' || c == 'E'
                || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private static void add(List<Pair<TextFormatting, String>> segments, TextFormatting style, String text) {
        if (text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty()) {
            Pair<TextFormatting, String> last = segments.get(segments.size() - 1);
            if (last.getLeft() == style) {
                segments.set(segments.size() - 1, Pair.of(style, last.getRight() + text));
                return;
            }
        }
        segments.add(Pair.of(style, text));
    }

}
