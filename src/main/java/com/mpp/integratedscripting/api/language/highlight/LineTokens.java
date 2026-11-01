package com.mpp.integratedscripting.api.language.highlight;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Result of tokenizing one line: its tokens and the state the next line starts
 * in.
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class LineTokens {

    private final List<Token> tokens;
    @Nullable
    private final HighlightState endState;

    /**
     * @param tokens The line's tokens, in order; {@code null} is treated as empty.
     * @param endState The state at the end of the line; {@code null} means "no state".
     */
    public LineTokens(@Nullable List<Token> tokens, @Nullable HighlightState endState) {
        this.tokens = tokens == null
                ? Collections.<Token>emptyList()
                : Collections.unmodifiableList(tokens);
        this.endState = endState;
    }

    /**
     * @param tokens The line's tokens.
     * @param endState The state at the end of the line.
     * @return The result.
     */
    public static LineTokens of(@Nullable List<Token> tokens, @Nullable HighlightState endState) {
        return new LineTokens(tokens, endState);
    }

    /**
     * @return The line's tokens, in order; never {@code null}.
     */
    public List<Token> getTokens() {
        return tokens;
    }

    /**
     * @return The state at the end of the line, or {@code null}.
     */
    @Nullable
    public HighlightState getEndState() {
        return endState;
    }

    @Override
    public String toString() {
        return "LineTokens" + tokens + " -> " + endState;
    }

}
