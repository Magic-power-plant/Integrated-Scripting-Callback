package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.callback.TextStyle;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 1.12.2 compatibility facade over {@link JavaScriptLexer}.
 *
 * <p>The 1.12.2 terminal colours code with the 16 {@link TextFormatting}
 * constants instead of RGB styles (docs/PORTING-1.12.2.md §7.9.3), so the
 * original tokenizer returned {@code Pair<TextFormatting, String>} segments. The
 * language service layer now uses semantic {@link TokenType}s, and this class
 * keeps the old signatures working by converting the lexer's tokens back to
 * styled segments.</p>
 *
 * <p>New code should use {@link JavaScriptHighlightService} (or any
 * {@code IHighlightService}) instead of calling this class.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptTokenizer {

    /** Reserved words that get the keyword style. */
    public static final Set<String> KEYWORDS = JavaScriptLexer.KEYWORDS;

    /** Characters that get the symbol style. */
    public static final String SYMBOLS = JavaScriptLexer.SYMBOLS;

    /**
     * Lexer state carried from the end of one line into the next; mirrors
     * {@link JavaScriptLexer.State}.
     */
    public enum State {
        /** Ordinary code. */
        CODE,
        /** Inside a block comment. */
        BLOCK_COMMENT,
        /** Inside a template literal. */
        TEMPLATE_STRING
    }

    /**
     * The result of tokenizing one line: its styled segments and the state the
     * next line starts in.
     */
    public static final class LineTokens {
        private final List<Pair<TextFormatting, String>> segments;
        private final State endState;

        public LineTokens(List<Pair<TextFormatting, String>> segments, State endState) {
            this.segments = Collections.unmodifiableList(segments);
            this.endState = endState;
        }

        public List<Pair<TextFormatting, String>> getSegments() {
            return segments;
        }

        public State getEndState() {
            return endState;
        }

    }

    private JavaScriptTokenizer() {
    }

    /**
     * Stateless single-line tokenization, for callers that only have one line.
     * @param line The line to tokenize, without newline characters.
     * @return The styled segments; empty for an empty line.
     */
    public static List<Pair<TextFormatting, String>> tokenize(String line) {
        return tokenizeLine(line, State.CODE).getSegments();
    }

    /**
     * Tokenize one line, continuing from a lexer state.
     * @param line The line to tokenize, without newline characters.
     * @param start The state at the start of the line; {@code null} means {@link State#CODE}.
     * @return The styled segments and the state at the end of the line.
     */
    public static LineTokens tokenizeLine(@Nullable String line, @Nullable State start) {
        String text = line == null ? "" : line;
        com.mpp.integratedscripting.api.language.highlight.LineTokens tokens =
                JavaScriptLexer.tokenize(text, toLexerState(start));
        return new LineTokens(toSegments(tokens.getTokens(), text), toFacadeState(tokens.getEndState()));
    }

    /**
     * Highlight a complete document in one pass, carrying state across lines.
     * @param value The complete editor value.
     * @return One segment list per logical line.
     */
    public static List<List<Pair<TextFormatting, String>>> tokenizeDocument(@Nullable String value) {
        String text = value == null ? "" : value;
        List<List<Pair<TextFormatting, String>>> lines = new ArrayList<>();
        State state = State.CODE;
        int pos = 0;
        while (true) {
            int newline = text.indexOf('\n', pos);
            int end = newline < 0 ? text.length() : newline;
            LineTokens tokens = tokenizeLine(text.substring(pos, end), state);
            lines.add(tokens.getSegments());
            state = tokens.getEndState();
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
        return JavaScriptLexer.lexicalContextAt(value, position);
    }

    /**
     * @param type A semantic token type.
     * @return The 1.12.2 formatting style used to draw it.
     */
    public static TextFormatting styleOf(TokenType type) {
        if (type == null) {
            return TextStyle.DEFAULT;
        }
        switch (type) {
            case KEYWORD:
                return TextStyle.KEYWORD;
            case COMMENT:
                return TextStyle.COMMENT;
            case STRING:
            case NUMBER:
                return TextStyle.CONSTANT;
            case PROPERTY:
                return TextStyle.ATTRIBUTE;
            case SYMBOL:
            case OPERATOR:
            case PUNCTUATION:
                return TextStyle.SYMBOL;
            default:
                return TextStyle.DEFAULT;
        }
    }

    private static List<Pair<TextFormatting, String>> toSegments(List<Token> tokens, String line) {
        List<Pair<TextFormatting, String>> segments = new ArrayList<>();
        for (Token token : tokens) {
            TextFormatting style = styleOf(token.getType());
            int start = Math.min(token.getStart(), line.length());
            int end = Math.min(token.getEnd(), line.length());
            if (end <= start) {
                continue;
            }
            String text = line.substring(start, end);
            if (!segments.isEmpty()) {
                Pair<TextFormatting, String> last = segments.get(segments.size() - 1);
                if (last.getLeft() == style) {
                    segments.set(segments.size() - 1, Pair.of(style, last.getRight() + text));
                    continue;
                }
            }
            segments.add(Pair.of(style, text));
        }
        return segments;
    }

    private static JavaScriptLexer.State toLexerState(@Nullable State state) {
        if (state == null) {
            return JavaScriptLexer.State.CODE;
        }
        switch (state) {
            case BLOCK_COMMENT:
                return JavaScriptLexer.State.BLOCK_COMMENT;
            case TEMPLATE_STRING:
                return JavaScriptLexer.State.TEMPLATE_STRING;
            default:
                return JavaScriptLexer.State.CODE;
        }
    }

    private static State toFacadeState(@Nullable HighlightState state) {
        JavaScriptLexer.State lexerState = JavaScriptLexer.stateOf(state);
        switch (lexerState) {
            case BLOCK_COMMENT:
                return State.BLOCK_COMMENT;
            case TEMPLATE_STRING:
                return State.TEMPLATE_STRING;
            default:
                return State.CODE;
        }
    }

}
