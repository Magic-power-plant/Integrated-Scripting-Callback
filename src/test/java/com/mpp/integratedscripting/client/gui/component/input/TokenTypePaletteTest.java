package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.callback.TextStyle;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression guard for the token-type palette: the terminal editor draws code on
 * the light editor panel, so every token colour must stay readable there
 * (docs/LANGUAGE-API-REFACTOR.md §4.2: the RGB mapping moved from
 * {@code TextFormatting} to {@link TokenType}, the contrast rule stays).
 */
public class TokenTypePaletteTest {

    private static final double MIN_CONTRAST = 4.5;

    @Test
    public void testEveryTokenTypeIsReadableOnTheEditorPanel() {
        for (TokenType type : TokenType.values()) {
            int rgb = TokenTypePalette.colorOf(type);
            double ratio = contrastRatio(rgb, TextStyle.EDITOR_BACKGROUND_RGB);
            assertTrue(type + " has only " + ratio + ":1 contrast on the editor panel",
                    ratio >= MIN_CONTRAST);
        }
    }

    @Test
    public void testTokenTypesKeepTheOldTextFormattingColours() {
        // STRING and NUMBER used to be the single CONSTANT style; PROPERTY used to
        // be ATTRIBUTE; the symbol types used to be SYMBOL.
        assertEquals(TextStyle.CONSTANT_RGB, TokenTypePalette.colorOf(TokenType.STRING));
        assertEquals(TextStyle.CONSTANT_RGB, TokenTypePalette.colorOf(TokenType.NUMBER));
        assertEquals(TextStyle.ATTRIBUTE_RGB, TokenTypePalette.colorOf(TokenType.PROPERTY));
        assertEquals(TextStyle.SYMBOL_RGB, TokenTypePalette.colorOf(TokenType.SYMBOL));
        assertEquals(TextStyle.SYMBOL_RGB, TokenTypePalette.colorOf(TokenType.OPERATOR));
        assertEquals(TextStyle.SYMBOL_RGB, TokenTypePalette.colorOf(TokenType.PUNCTUATION));
        assertEquals(TextStyle.KEYWORD_RGB, TokenTypePalette.colorOf(TokenType.KEYWORD));
        assertEquals(TextStyle.COMMENT_RGB, TokenTypePalette.colorOf(TokenType.COMMENT));
        assertEquals(TextStyle.DEFAULT_RGB, TokenTypePalette.colorOf(TokenType.DEFAULT));
        assertEquals(TextStyle.DEFAULT_RGB, TokenTypePalette.colorOf(null));
    }

    private static double contrastRatio(int first, int second) {
        double luminanceFirst = relativeLuminance(first);
        double luminanceSecond = relativeLuminance(second);
        return (Math.max(luminanceFirst, luminanceSecond) + 0.05)
                / (Math.min(luminanceFirst, luminanceSecond) + 0.05);
    }

    private static double relativeLuminance(int rgb) {
        return 0.2126 * linearize(rgb >> 16 & 0xFF)
                + 0.7152 * linearize(rgb >> 8 & 0xFF)
                + 0.0722 * linearize(rgb & 0xFF);
    }

    private static double linearize(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.04045
                ? normalized / 12.92
                : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }

}
