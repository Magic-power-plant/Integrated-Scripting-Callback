package com.mpp.integratedscripting.api.language.highlight;

import javax.annotation.Nullable;

/**
 * Tokenizes one line at a time, carrying state across line breaks.
 *
 * <p>This is the Java 8 / 1.12.2 equivalent of CodeMirror's stream tokenizer
 * interface. A language implements it once; the editor calls it for the lines
 * it draws (and caches the per-line state), so block comments, template
 * literals and regular expressions that span several lines are highlighted
 * correctly.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ILineTokenizer {

    /**
     * @return The state at the start of a document.
     */
    HighlightState initialState();

    /**
     * Tokenize one logical line.
     * @param line The line, without its newline; {@code null} is treated as empty.
     * @param startState The state at the start of the line, as returned by
     *                   {@link #initialState()} or a previous line's
     *                   {@link LineTokens#getEndState()}; {@code null} means the
     *                   initial state.
     * @return The tokens covering the line and the state at its end.
     */
    LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState);

}
