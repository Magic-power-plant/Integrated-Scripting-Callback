package com.mpp.integratedscripting.api.language;

/**
 * Lexical contexts of a document position, used by the terminal editor's input
 * rules (auto-indent, bracket and quote completion).
 *
 * <p>An editor should only complete brackets and quotes, or re-indent a closing
 * bracket, when the caret is in {@link #CODE}. Inside a string literal
 * ({@link #STRING}) or a comment ({@link #COMMENT}) the typed characters are
 * plain text. This mirrors the way CodeMirror's close-bracket and indentation
 * extensions consult the language's tokenizer before deciding whether to
 * auto-insert.</p>
 *
 * <p>Language services return one of these constants from
 * {@code IHighlightService#lexicalContextAt(DocumentText, int)}; the legacy
 * {@link ILanguageHandler#getLexicalContext(String, int)} is bridged by the
 * registry. The default implementation always reports {@link #CODE}, so a
 * language without a lexer keeps the simple behaviour.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class LexicalContext {

    /** Ordinary code, where brackets and quotes may be completed. */
    public static final int CODE = 0;
    /** Inside a string literal (including template literals), where input is text. */
    public static final int STRING = 1;
    /** Inside a line or block comment, where input is text. */
    public static final int COMMENT = 2;

    private LexicalContext() {
    }

}
