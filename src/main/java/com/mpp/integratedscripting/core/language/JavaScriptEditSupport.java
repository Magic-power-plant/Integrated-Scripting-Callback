package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.edit.ICommentStyle;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.edit.IIdentifierSyntax;

/**
 * The editing conventions of JavaScript: four-space indentation, C-style
 * comments, identifier rules that include {@code $} and Unicode, and {@code js}
 * as the extension of a new file.
 *
 * <p>Every one of those conventions is also TypeScript's, so
 * {@link TypeScriptEditSupport} extends this class and changes only the default
 * file extension.</p>
 *
 * @author rubensworks
 */
public class JavaScriptEditSupport implements IEditSupport {

    /** One indentation step. */
    public static final String INDENT_UNIT = "    ";

    private static final ICommentStyle COMMENT_STYLE = new ICommentStyle() {
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
    };

    private final IIdentifierSyntax identifierSyntax = new JavaScriptIdentifierSyntax();

    @Override
    public String getIndentUnit() {
        return INDENT_UNIT;
    }

    @Override
    public ICommentStyle getCommentStyle() {
        return COMMENT_STYLE;
    }

    @Override
    public IIdentifierSyntax getIdentifierSyntax() {
        return identifierSyntax;
    }

    @Override
    public String getDefaultFileExtension() {
        return "js";
    }

    @Override
    public String toString() {
        return "JavaScriptEditSupport";
    }

}
