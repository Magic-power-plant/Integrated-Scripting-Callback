package com.mpp.integratedscripting.api.language.edit;

/**
 * Editing conventions of one language: indentation, comments, identifiers and
 * the extension a new file gets.
 *
 * <p>Everything the terminal editor and the container used to hard-code lives
 * here, so a language module can change it without touching the GUI.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface IEditSupport {

    /**
     * @return One indentation step, for example four spaces. Must not be empty.
     */
    default String getIndentUnit() {
        return "    ";
    }

    /**
     * @return The comment syntax of the language; never {@code null}.
     */
    default ICommentStyle getCommentStyle() {
        return DefaultCommentStyle.INSTANCE;
    }

    /**
     * @return The identifier syntax of the language; never {@code null}.
     */
    default IIdentifierSyntax getIdentifierSyntax() {
        return DefaultIdentifierSyntax.INSTANCE;
    }

    /**
     * @return The extension without dot that a new script file gets, for example
     *         {@code "js"}. Must not be empty.
     */
    default String getDefaultFileExtension() {
        return "txt";
    }

    /**
     * The neutral comment style used when a language does not provide one: line
     * comments starting with two slashes, and C-style block comments.
     */
    final class DefaultCommentStyle implements ICommentStyle {

        /** Shared instance. */
        public static final DefaultCommentStyle INSTANCE = new DefaultCommentStyle();

        private DefaultCommentStyle() {
        }

        @Override
        public String getLineComment() {
            return "//";
        }

        @Override
        public String getBlockCommentOpen() {
            return "/*";
        }

        @Override
        public String getBlockCommentClose() {
            return "*/";
        }

    }

    /**
     * The neutral identifier syntax used when a language does not provide one:
     * ASCII letters, digits and underscore, not starting with a digit.
     */
    final class DefaultIdentifierSyntax implements IIdentifierSyntax {

        /** Shared instance. */
        public static final DefaultIdentifierSyntax INSTANCE = new DefaultIdentifierSyntax();

        private DefaultIdentifierSyntax() {
        }

        @Override
        public boolean isIdentifierStart(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
        }

        @Override
        public boolean isIdentifierPart(char c) {
            return isIdentifierStart(c) || (c >= '0' && c <= '9');
        }

    }

}
