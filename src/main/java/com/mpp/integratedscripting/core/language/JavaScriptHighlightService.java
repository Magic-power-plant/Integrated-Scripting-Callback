package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.text.DocumentText;

/**
 * JavaScript syntax highlighting and lexical-context queries.
 *
 * <p>This is the service the terminal editor resolves for a {@code .js} file. The
 * tokenizer is the streaming {@link JavaScriptLexer}, so the default
 * {@link IHighlightService#highlight(DocumentText)} implementation carries block
 * comment and template literal state across line breaks.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptHighlightService implements IHighlightService {

    private final JavaScriptLexer lexer = JavaScriptLexer.getInstance();

    @Override
    public ILineTokenizer getTokenizer() {
        return lexer;
    }

    @Override
    public int lexicalContextAt(DocumentText document, int offset) {
        return JavaScriptLexer.lexicalContextAt(document == null ? "" : document.getText(), offset);
    }

    @Override
    public String toString() {
        return "JavaScriptHighlightService";
    }

}
