package com.mpp.integratedscripting.api.language.completion;

import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.text.DocumentText;

/**
 * Everything a completion contributor needs to know about one request.
 *
 * <p>The context is immutable and already carries the derived values a
 * contributor would otherwise recompute: the language, the lexical context and
 * the identifier prefix before the caret.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class CompletionContext {

    private final DocumentText document;
    private final int offset;
    private final LanguageId language;
    private final int lexicalContext;
    private final char triggerCharacter;
    private final String prefix;

    /**
     * @param document The document.
     * @param offset The caret offset inside the document.
     * @param language The language of the document.
     * @param lexicalContext The lexical context at the caret, one of the
     *                       {@link LexicalContext} constants.
     * @param triggerCharacter The character that triggered the request, or
     *                         {@code 0} for an explicit request (Ctrl+Space).
     * @param prefix The identifier prefix before the caret; never {@code null}.
     */
    public CompletionContext(DocumentText document, int offset, LanguageId language,
                             int lexicalContext, char triggerCharacter, String prefix) {
        this.document = document;
        this.offset = Math.max(0, Math.min(document.getLength(), offset));
        this.language = language;
        this.lexicalContext = lexicalContext;
        this.triggerCharacter = triggerCharacter;
        this.prefix = prefix == null ? "" : prefix;
    }

    /**
     * @return The document being completed.
     */
    public DocumentText getDocument() {
        return document;
    }

    /**
     * @return The caret offset, clamped into the document.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * @return The language of the document.
     */
    public LanguageId getLanguage() {
        return language;
    }

    /**
     * @return The lexical context at the caret, one of the {@link LexicalContext} constants.
     */
    public int getLexicalContext() {
        return lexicalContext;
    }

    /**
     * @return The character that triggered the request, or {@code 0} for an
     *         explicit request.
     */
    public char getTriggerCharacter() {
        return triggerCharacter;
    }

    /**
     * @return The identifier prefix before the caret; empty when the caret does
     *         not follow an identifier.
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @return Whether the caret is in ordinary code, where completion makes sense.
     */
    public boolean isInCode() {
        return lexicalContext == LexicalContext.CODE;
    }

}
