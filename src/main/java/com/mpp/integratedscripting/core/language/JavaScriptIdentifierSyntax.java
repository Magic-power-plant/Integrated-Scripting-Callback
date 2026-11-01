package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.edit.IIdentifierSyntax;

/**
 * JavaScript identifier rules.
 *
 * <p>The terminal used to decide member names with the regular expression
 * {@code [^0-9a-zA-Z_]}, which is stricter than JavaScript: it rejected
 * {@code $} and Unicode identifiers. This implementation follows the language
 * (and the lexer) instead.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptIdentifierSyntax implements IIdentifierSyntax {

    @Override
    public boolean isIdentifierStart(char c) {
        return c == '$' || JavaScriptLexer.isIdentifierStart(c);
    }

    @Override
    public boolean isIdentifierPart(char c) {
        return c == '$' || JavaScriptLexer.isIdentifierPart(c);
    }

    @Override
    public String toString() {
        return "JavaScriptIdentifierSyntax";
    }

}
