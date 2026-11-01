package com.mpp.integratedscripting.api.language.highlight;

/**
 * Opaque lexer state carried from the end of one line into the next, like the
 * {@code startState}/{@code endState} pair of a CodeMirror stream tokenizer.
 *
 * <p>Each language defines its own immutable implementation (an enum constant
 * is the usual choice). Consumers never inspect it; they only pass it back into
 * {@link ILineTokenizer#tokenizeLine(String, HighlightState)}.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface HighlightState {

}
