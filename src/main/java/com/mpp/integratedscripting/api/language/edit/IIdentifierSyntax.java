package com.mpp.integratedscripting.api.language.edit;

import com.mpp.integratedscripting.api.language.text.DocumentText;

/**
 * What the language considers an identifier.
 *
 * <p>This replaced two hard-coded rules in the terminal: the GUI's reversed
 * regular expression for the member under the caret, and
 * {@code ContainerTerminalScripting}'s {@code [^0-9a-zA-Z_]} pattern, which was
 * stricter than JavaScript (it rejected {@code $} and Unicode identifiers).</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface IIdentifierSyntax {

    /**
     * @param c A character.
     * @return Whether the character may start an identifier.
     */
    boolean isIdentifierStart(char c);

    /**
     * @param c A character.
     * @return Whether the character may continue an identifier.
     */
    boolean isIdentifierPart(char c);

    /**
     * @param name A candidate member name, as selected in the editor.
     * @return Whether the name may be bound to a variable card.
     */
    default boolean isValidMemberName(String name) {
        if (name == null || name.isEmpty() || !isIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            if (!isIdentifierPart(name.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The identifier at a document position, used to bind a variable card to the
     * script member under the caret.
     * @param document The document.
     * @param offset A character offset, usually the caret position.
     * @return The identifier the offset touches; an empty string when there is none.
     */
    default String memberAt(DocumentText document, int offset) {
        if (document == null) {
            return "";
        }
        String text = document.getText();
        int position = Math.max(0, Math.min(text.length(), offset));
        int start = position;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        int end = position;
        while (end < text.length() && isIdentifierPart(text.charAt(end))) {
            end++;
        }
        return text.substring(start, end);
    }

}
