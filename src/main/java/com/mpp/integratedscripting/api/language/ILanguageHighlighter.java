package com.mpp.integratedscripting.api.language;

import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 * One-pass syntax highlighter for a whole document.
 *
 * <p><strong>Compatibility type.</strong> New code uses {@code IHighlightService} and
 * {@code ILineTokenizer}, which are document, line and token-type aware. This
 * interface and {@code ILanguageHandler#getHighlighter()} are kept for one minor
 * version so existing language handlers keep working; the language registry
 * bridges them into an {@code IHighlightService}
 * (see docs/LANGUAGE-API-REFACTOR.md §5). It is removed in 2.0.</p>
 *
 * <p>1.12.2's {@link ILanguageHandler#markupLine(String)} tokenizes each line in
 * isolation, so a block comment or a template literal that spans several lines
 * cannot be highlighted. A highlighter receives the complete value and returns
 * the styled segments of every logical line (split on {@code '\n'}), which lets
 * the implementation carry lexer state from one line to the next, the same way a
 * CodeMirror stream tokenizer carries its {@code startState} through the
 * document.</p>
 *
 * <p>The returned list must have one entry per logical line, and each entry must
 * concatenate back to that line. An empty document still yields one (empty)
 * entry.</p>
 *
 * @author rubensworks
 */
public interface ILanguageHighlighter {

    /**
     * Highlight every logical line of a document.
     * @param value The complete editor value.
     * @return One segment list per logical line.
     */
    List<List<Pair<TextFormatting, String>>> highlightDocument(String value);

}
