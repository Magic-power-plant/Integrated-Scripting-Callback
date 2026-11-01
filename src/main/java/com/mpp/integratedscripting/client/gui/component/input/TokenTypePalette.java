package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.callback.TextStyle;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Maps semantic {@link TokenType}s to the RGB colours of the terminal editor.
 *
 * <p>This replaces the {@code TextFormatting}-based colour resolution of the old
 * highlight contract. The colours are the tuned {@link TextStyle} editor palette
 * (the same values {@code TextStyleContrastTest} guards against the light editor
 * panel), so the refactor does not change how any token is drawn.</p>
 *
 * <p>The common side never sees these colours: Token types are language data,
 * colours are a client presentation detail.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public final class TokenTypePalette {

    private TokenTypePalette() {
    }

    /**
     * @param type A token type; {@code null} means plain text.
     * @return The RGB colour to draw it with.
     */
    public static int colorOf(TokenType type) {
        if (type == null) {
            return TextStyle.DEFAULT_RGB;
        }
        switch (type) {
            case KEYWORD:
                return TextStyle.KEYWORD_RGB;
            case COMMENT:
                return TextStyle.COMMENT_RGB;
            case STRING:
            case NUMBER:
                return TextStyle.CONSTANT_RGB;
            case PROPERTY:
                return TextStyle.ATTRIBUTE_RGB;
            case SYMBOL:
            case OPERATOR:
            case PUNCTUATION:
                return TextStyle.SYMBOL_RGB;
            default:
                return TextStyle.DEFAULT_RGB;
        }
    }

}
