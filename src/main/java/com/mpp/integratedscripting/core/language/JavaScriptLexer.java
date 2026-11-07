package com.mpp.integratedscripting.core.language;

import com.google.common.collect.Lists;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Streaming line lexer for JavaScript.
 *
 * <p>The 1.20 reference implementation splits every line on a fixed list of
 * tokens (docs/PORTING-1.12.2.md §7.9.3). That never colours comments, string
 * literals or numbers, while §11.2 item 13 asks for exactly those classes. This
 * lexer performs a single left-to-right scan, so the fixed tokens are only
 * matched outside of comments and string literals.</p>
 *
 * <p>1.12.2 difference: the lexer is stateful between lines, like CodeMirror's
 * stream tokenizers. {@link #tokenizeLine(String, HighlightState)} receives the
 * state at the start of a line and returns the state at its end, so a block
 * comment, a template literal or a regular expression is coloured correctly
 * across line breaks. The state is carried by {@link State}, an immutable
 * {@link HighlightState}.</p>
 *
 * <p>The lexer is deliberately forgiving: it never throws, it always accepts
 * arbitrary text, and its tokens cover the input exactly (the editor relies on
 * that to slice wrapped visual lines).</p>
 *
 * <p>{@link JavaScriptTokenizer} is kept as a compatibility facade over this
 * class for the {@code TextFormatting}-based API of 1.12.2; new code talks to
 * this lexer through {@code IHighlightService}.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptLexer implements ILineTokenizer {

    /** Reserved words that get the keyword token type. */
    public static final Set<String> KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "async", "await", "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "false", "finally", "for",
            "function", "if", "import", "in", "instanceof", "let", "new", "null", "of", "return",
            "static", "super", "switch", "this", "throw", "true", "try", "typeof", "undefined",
            "var", "void", "while", "with", "yield")));

    /**
     * Words that are only keywords in TypeScript: declaration keywords, member
     * modifiers, assertion and query operators, and the primitive type names.
     *
     * <p>They are kept apart from {@link #KEYWORDS} so that {@code .js}
     * highlighting does not colour {@code type}, {@code as} or {@code from},
     * which are ordinary identifiers there.</p>
     */
    public static final Set<String> TYPESCRIPT_ONLY_KEYWORDS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
                    "abstract", "any", "as", "asserts", "bigint", "boolean", "declare", "enum",
                    "from", "implements", "infer", "interface", "is", "keyof", "module",
                    "namespace", "never", "number", "object", "override", "private", "protected",
                    "public", "readonly", "satisfies", "string", "symbol", "type", "unique",
                    "unknown")));

    /** The keyword set of TypeScript: the JavaScript keywords plus the TS-only ones. */
    public static final Set<String> TYPESCRIPT_KEYWORDS = typescriptKeywords();

    /**
     * Keywords after which a slash starts a regular expression rather than a
     * division. Kept in sync with the expression-position heuristic below.
     */
    private static final Set<String> REGEX_KEYWORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "await", "case", "delete", "do", "else", "in", "instanceof", "new", "of", "return",
            "throw", "typeof", "void", "yield")));

    /** Characters that get the symbol token type. */
    public static final String SYMBOLS = "{}()[]=;.,:+-*/%<>!&|^~?";

    /** Characters that are operators rather than punctuation. */
    public static final String OPERATORS = "=+-*/%<>!&|^~?";

    /** Brackets, separators and the member-access dot. */
    public static final String PUNCTUATION = "{}()[],;.:";

    private static final JavaScriptLexer INSTANCE = new JavaScriptLexer();

    /**
     * Lexer state carried from the end of one line into the next.
     */
    public enum State implements HighlightState {
        /** Ordinary code. */
        CODE,
        /** Inside a block comment. */
        BLOCK_COMMENT,
        /** Inside a template literal. */
        TEMPLATE_STRING
    }

    /**
     * @return A shared stateless lexer instance; all state lives in the arguments.
     */
    public static JavaScriptLexer getInstance() {
        return INSTANCE;
    }

    @Override
    public HighlightState initialState() {
        return State.CODE;
    }

    @Override
    public LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState) {
        return tokenize(line, stateOf(startState));
    }

    /**
     * Tokenize one line, continuing from a lexer state.
     * @param line The line to tokenize, without newline characters.
     * @param start The state at the start of the line; {@code null} means {@link State#CODE}.
     * @return The line's tokens and the state at the end of the line.
     */
    public static LineTokens tokenize(@Nullable String line, @Nullable State start) {
        return tokenize(line, start, KEYWORDS);
    }

    /**
     * Tokenize one line with a language specific keyword set.
     * @param line The line to tokenize, without newline characters.
     * @param start The state at the start of the line; {@code null} means {@link State#CODE}.
     * @param keywords The words to tokenize as keywords, for example
     *                 {@link #KEYWORDS} or {@link #TYPESCRIPT_KEYWORDS}.
     * @return The line's tokens and the state at the end of the line.
     */
    public static LineTokens tokenize(@Nullable String line, @Nullable State start,
                                      @Nullable Set<String> keywords) {
        String text = line == null ? "" : line;
        Set<String> keywordSet = keywords == null ? KEYWORDS : keywords;
        State state = start == null ? State.CODE : start;
        List<Token> tokens = Lists.newArrayList();

        int i = 0;
        int length = text.length();
        // What was the last non-whitespace character? Used for `obj.member`
        // properties and for the regex-versus-division heuristic.
        char previous = 0;

        while (i < length) {
            char c = text.charAt(i);

            if (state == State.BLOCK_COMMENT) {
                int end = text.indexOf("*/", i);
                if (end < 0) {
                    add(tokens, i, length, TokenType.COMMENT);
                    i = length;
                } else {
                    add(tokens, i, end + 2, TokenType.COMMENT);
                    i = end + 2;
                    state = State.CODE;
                    previous = '/';
                }
                continue;
            }

            if (state == State.TEMPLATE_STRING) {
                int end = findTemplateEnd(text, i);
                if (end < 0) {
                    add(tokens, i, length, TokenType.STRING);
                    i = length;
                } else {
                    add(tokens, i, end, TokenType.STRING);
                    i = end;
                    state = State.CODE;
                    previous = '`';
                }
                continue;
            }

            // Line comment.
            if (c == '/' && i + 1 < length && text.charAt(i + 1) == '/') {
                add(tokens, i, length, TokenType.COMMENT);
                i = length;
                continue;
            }

            // Block comment, possibly continuing on the next line.
            if (c == '/' && i + 1 < length && text.charAt(i + 1) == '*') {
                int end = text.indexOf("*/", i + 2);
                if (end < 0) {
                    add(tokens, i, length, TokenType.COMMENT);
                    state = State.BLOCK_COMMENT;
                    i = length;
                } else {
                    add(tokens, i, end + 2, TokenType.COMMENT);
                    i = end + 2;
                    previous = '/';
                }
                continue;
            }

            // Regular expression literal, only where an expression may start.
            if (c == '/' && regexAllowed(previous)) {
                int end = findRegexEnd(text, i);
                if (end > i) {
                    add(tokens, i, end, TokenType.STRING);
                    i = end;
                    previous = '/';
                    continue;
                }
            }

            // String literals.
            if (c == '"' || c == '\'') {
                int end = findQuotedEnd(text, i);
                add(tokens, i, end, TokenType.STRING);
                i = end;
                previous = c;
                continue;
            }

            // Template literal, which may continue on the next line.
            if (c == '`') {
                int end = findTemplateEnd(text, i + 1);
                if (end < 0) {
                    add(tokens, i, length, TokenType.STRING);
                    state = State.TEMPLATE_STRING;
                    i = length;
                } else {
                    add(tokens, i, end, TokenType.STRING);
                    i = end;
                    previous = '`';
                }
                continue;
            }

            // Numeric literal (including 0x/0b/0o, exponents, separators and BigInt).
            if (Character.isDigit(c)
                    || (c == '.' && i + 1 < length && Character.isDigit(text.charAt(i + 1)))) {
                int end = findNumberEnd(text, i);
                add(tokens, i, end, TokenType.NUMBER);
                i = end;
                previous = '0';
                continue;
            }

            // Identifier, keyword or property name.
            if (isIdentifierStart(c)) {
                int end = i;
                while (end < length && isIdentifierPart(text.charAt(end))) {
                    end++;
                }
                String word = text.substring(i, end);
                if (keywordSet.contains(word)) {
                    add(tokens, i, end, TokenType.KEYWORD);
                    previous = REGEX_KEYWORDS.contains(word) ? 'R' : 'a';
                } else if (previous == '.') {
                    // obj.property, CodeMirror's `propertyName`.
                    add(tokens, i, end, TokenType.PROPERTY);
                    previous = 'a';
                } else {
                    add(tokens, i, end, TokenType.DEFAULT);
                    previous = 'a';
                }
                i = end;
                continue;
            }

            // Whitespace is kept verbatim so the tokens reconstruct the line.
            if (Character.isWhitespace(c)) {
                int end = i;
                while (end < length && Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                add(tokens, i, end, TokenType.DEFAULT);
                i = end;
                continue;
            }

            // Symbols and everything else.
            if (SYMBOLS.indexOf(c) >= 0) {
                add(tokens, i, i + 1, TokenType.SYMBOL);
                previous = c;
            } else {
                add(tokens, i, i + 1, TokenType.DEFAULT);
                previous = c;
            }
            i++;
        }

        return LineTokens.of(tokens, state);
    }

    /**
     * Highlight a complete document in one pass, carrying state across lines.
     * @param value The complete editor value.
     * @return One token list per logical line.
     */
    public static List<List<Token>> tokenizeDocument(@Nullable String value) {
        String text = value == null ? "" : value;
        List<List<Token>> lines = Lists.newArrayList();
        State state = State.CODE;
        int pos = 0;
        while (true) {
            int newline = text.indexOf('\n', pos);
            int end = newline < 0 ? text.length() : newline;
            LineTokens tokens = tokenize(text.substring(pos, end), state);
            lines.add(tokens.getTokens());
            state = stateOf(tokens.getEndState());
            if (newline < 0) {
                break;
            }
            pos = newline + 1;
        }
        return lines;
    }

    /**
     * The lexical context of a document position, used by the editor's input
     * rules.
     * @param value The complete document.
     * @param position A character index in {@code value}.
     * @return {@code LexicalContext#CODE}, {@code #STRING} or {@code #COMMENT}.
     */
    public static int lexicalContextAt(@Nullable String value, int position) {
        String text = value == null ? "" : value;
        int pos = Math.max(0, Math.min(text.length(), position));
        State state = State.CODE;
        int lineStart = 0;
        while (true) {
            int newline = text.indexOf('\n', lineStart);
            int lineEnd = newline < 0 ? text.length() : newline;
            String line = text.substring(lineStart, lineEnd);
            if (pos <= lineEnd) {
                LineTokens tokens = tokenize(line, state);
                return contextInLine(tokens, line, pos - lineStart);
            }
            state = stateOf(tokenize(line, state).getEndState());
            lineStart = lineEnd + 1;
        }
    }

    /**
     * @param state A highlight state.
     * @return The JavaScript lexer state; {@link State#CODE} for {@code null} or a
     *         foreign state.
     */
    public static State stateOf(@Nullable HighlightState state) {
        return state instanceof State ? (State) state : State.CODE;
    }

    /**
     * The lexical context of a position inside one already tokenized line.
     * @param tokens The line's tokens.
     * @param line The line text, used to inspect the last token.
     * @param index The zero based position inside the line.
     * @return One of the {@code LexicalContext} constants.
     */
    static int contextInLine(LineTokens tokens, String line, int index) {
        for (Token token : tokens.getTokens()) {
            if (index >= token.getStart() && index < token.getEnd()) {
                return contextOfToken(token);
            }
        }
        // The position is at (or past) the end of the line: the state at the end
        // of the line decides, plus an unclosed literal that runs to the margin.
        State endState = stateOf(tokens.getEndState());
        if (endState == State.BLOCK_COMMENT) {
            return LexicalContext.COMMENT;
        }
        if (endState == State.TEMPLATE_STRING) {
            return LexicalContext.STRING;
        }
        List<Token> all = tokens.getTokens();
        if (!all.isEmpty()) {
            Token last = all.get(all.size() - 1);
            String text = line.substring(Math.min(last.getStart(), line.length()),
                    Math.min(last.getEnd(), line.length()));
            if (last.getType() == TokenType.COMMENT) {
                // A block comment that closed on this line ends in code; a line
                // comment always reaches the end of the line.
                return text.endsWith("*/") && !text.startsWith("//")
                        ? LexicalContext.CODE : LexicalContext.COMMENT;
            }
            if (last.getType() == TokenType.STRING && isUnclosedLiteral(text)) {
                return LexicalContext.STRING;
            }
        }
        return LexicalContext.CODE;
    }

    private static int contextOfToken(Token token) {
        if (token.getType() == TokenType.COMMENT) {
            return LexicalContext.COMMENT;
        }
        if (token.getType() == TokenType.STRING) {
            return LexicalContext.STRING;
        }
        return LexicalContext.CODE;
    }

    /**
     * Whether a literal token never closes on this line, so a caret at the end of
     * the line is still inside it.
     */
    private static boolean isUnclosedLiteral(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        char first = token.charAt(0);
        if (!isLiteralStart(first)) {
            return false;
        }
        if (token.length() < 2) {
            // A lone opening quote that was just typed.
            return true;
        }
        if (first == '/') {
            // A regex token is only produced when it completed on this line.
            return false;
        }
        // Count unescaped occurrences of the quote: an odd count is unterminated.
        int count = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == first) {
                count++;
            }
        }
        return count % 2 != 0;
    }

    private static boolean isLiteralStart(char c) {
        return c == '"' || c == '\'' || c == '`' || c == '/';
    }

    private static boolean regexAllowed(char previous) {
        if (previous == 'R') {
            return true;
        }
        return previous == 0 || "=([{,;:!&|?+-*%<>~^".indexOf(previous) >= 0;
    }

    private static int findQuotedEnd(String text, int start) {
        char quote = text.charAt(start);
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == quote) {
                return i + 1;
            }
            i++;
        }
        return text.length();
    }

    /**
     * Find the end of a template literal.
     * @param text The text to scan.
     * @param start Index just after the opening backtick (or a later segment
     *              when continuing a multi-line literal).
     * @return The index after the closing backtick, or -1 when the literal
     *         continues on the next line.
     */
    private static int findTemplateEnd(String text, int start) {
        int i = start;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                return i + 1;
            }
            i++;
        }
        return -1;
    }

    private static int findRegexEnd(String text, int start) {
        boolean inClass = false;
        int i = start + 1;
        boolean nonEmpty = false;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
                nonEmpty = true;
                continue;
            }
            if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                if (!nonEmpty) {
                    return -1;
                }
                i++;
                while (i < text.length() && Character.isLetter(text.charAt(i))) {
                    i++;
                }
                return i;
            } else if (!Character.isWhitespace(c)) {
                nonEmpty = true;
            }
            i++;
        }
        return -1;
    }

    private static int findNumberEnd(String text, int start) {
        int i = start;
        int length = text.length();
        if (i + 1 < length && text.charAt(i) == '0' && "xXbBoO".indexOf(text.charAt(i + 1)) >= 0) {
            i += 2;
            while (i < length && (isHexDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
        } else {
            while (i < length && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                i++;
            }
            if (i < length && text.charAt(i) == '.') {
                i++;
                while (i < length && (Character.isDigit(text.charAt(i)) || text.charAt(i) == '_')) {
                    i++;
                }
            }
            if (i < length && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
                int exponent = i + 1;
                if (exponent < length && (text.charAt(exponent) == '+' || text.charAt(exponent) == '-')) {
                    exponent++;
                }
                if (exponent < length && Character.isDigit(text.charAt(exponent))) {
                    i = exponent;
                    while (i < length && Character.isDigit(text.charAt(i))) {
                        i++;
                    }
                }
            }
        }
        if (i < length && text.charAt(i) == 'n') {
            i++;
        }
        return i;
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    /**
     * @param c A character.
     * @return Whether it may start a JavaScript identifier.
     */
    public static boolean isIdentifierStart(char c) {
        return Character.isJavaIdentifierStart(c);
    }

    /**
     * @param c A character.
     * @return Whether it may continue a JavaScript identifier.
     */
    public static boolean isIdentifierPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    /**
     * @return The JavaScript keywords plus the TypeScript-only ones.
     */
    private static Set<String> typescriptKeywords() {
        Set<String> keywords = new HashSet<>(KEYWORDS);
        keywords.addAll(TYPESCRIPT_ONLY_KEYWORDS);
        return Collections.unmodifiableSet(keywords);
    }

    private static void add(List<Token> tokens, int start, int end, TokenType type) {
        if (end <= start) {
            return;
        }
        if (!tokens.isEmpty()) {
            Token last = tokens.get(tokens.size() - 1);
            if (last.getType() == type && last.getEnd() == start) {
                tokens.set(tokens.size() - 1, new Token(last.getStart(), end, type));
                return;
            }
        }
        tokens.add(new Token(start, end, type));
    }

}
