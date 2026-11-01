package com.mpp.integratedscripting.api.language.highlight;

import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.text.DocumentText;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Syntax highlighting and lexical-context queries for one language.
 *
 * <p>Both abilities live here because they are answered by the same lexer: the
 * editor asks {@link #lexicalContextAt(DocumentText, int)} before it completes a
 * bracket or quote, and {@link #highlight(DocumentText)} for the colours.</p>
 *
 * <p>The default {@link #highlight(DocumentText)} walks the document line by line
 * and carries the tokenizer state, which is correct for every streaming lexer.
 * An editor may instead call {@link #getTokenizer()} itself and cache the state
 * of the visible range; the terminal editor does exactly that through its
 * per-document highlight cache.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface IHighlightService {

    /**
     * @return The line tokenizer of this language.
     */
    ILineTokenizer getTokenizer();

    /**
     * The lexical context of a document position, used by the editor's input
     * rules to decide whether brackets and quotes may be auto-completed.
     * @param document The document.
     * @param offset A character offset into the document.
     * @return One of the {@link LexicalContext} constants.
     */
    default int lexicalContextAt(DocumentText document, int offset) {
        return LexicalContext.CODE;
    }

    /**
     * Highlight a complete document, carrying the tokenizer state across lines.
     * @param document The document.
     * @return One token list per logical line, in order; never {@code null}.
     */
    default List<List<Token>> highlight(@Nullable DocumentText document) {
        if (document == null) {
            return Collections.emptyList();
        }
        ILineTokenizer tokenizer = getTokenizer();
        List<List<Token>> lines = new ArrayList<>(document.getLineCount());
        HighlightState state = tokenizer.initialState();
        for (int line = 0; line < document.getLineCount(); line++) {
            LineTokens tokens = tokenizer.tokenizeLine(document.getLine(line), state);
            lines.add(tokens.getTokens());
            state = tokens.getEndState();
        }
        return lines;
    }

}
