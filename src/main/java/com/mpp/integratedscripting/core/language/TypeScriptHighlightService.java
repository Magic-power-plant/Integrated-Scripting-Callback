package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.text.DocumentText;

import javax.annotation.Nullable;

/**
 * TypeScript syntax highlighting and lexical-context queries.
 *
 * <p>TypeScript is a superset of JavaScript, so this service reuses
 * {@link JavaScriptLexer} - the same streaming tokenizer, the same block comment
 * and template literal state - with {@link JavaScriptLexer#TYPESCRIPT_KEYWORDS}
 * instead of the plain JavaScript set. Nothing else about the lexer changes:
 * {@code .ts} and {@code .js} share strings, numbers, comments and regular
 * expressions, and the editor still gets a token state it can cache per line.</p>
 *
 * <p>The lexical-context query deliberately keeps the JavaScript behaviour: the
 * bracket and quote auto-completion rules depend on whether the caret is in code,
 * a string or a comment, and TypeScript's type positions are code.</p>
 *
 * @author rubensworks
 */
public final class TypeScriptHighlightService implements IHighlightService {

    /** The lexer, configured with the TypeScript keyword set. */
    private static final ILineTokenizer TOKENIZER = new ILineTokenizer() {
        @Override
        public HighlightState initialState() {
            return JavaScriptLexer.State.CODE;
        }

        @Override
        public LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState) {
            return JavaScriptLexer.tokenize(line, JavaScriptLexer.stateOf(startState),
                    JavaScriptLexer.TYPESCRIPT_KEYWORDS);
        }

        @Override
        public String toString() {
            return "TypeScriptTokenizer";
        }
    };

    @Override
    public ILineTokenizer getTokenizer() {
        return TOKENIZER;
    }

    @Override
    public int lexicalContextAt(DocumentText document, int offset) {
        return JavaScriptLexer.lexicalContextAt(document == null ? "" : document.getText(), offset);
    }

    @Override
    public String toString() {
        return "TypeScriptHighlightService";
    }

}
