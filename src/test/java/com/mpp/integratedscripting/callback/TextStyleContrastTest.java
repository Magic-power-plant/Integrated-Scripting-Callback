package com.mpp.integratedscripting.callback;

import net.minecraft.util.text.TextFormatting;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression guard for the terminal editor palette (docs/AUDIT.md D10): the code
 * is drawn on the light editor panel of the terminal texture, so every syntax
 * colour must stay readable there.
 */
public class TextStyleContrastTest {

    /** WCAG AA contrast for normal-size body text. */
    private static final double MIN_CONTRAST = 4.5;

    @Test
    public void testEveryEditorColourIsReadableOnTheEditorPanel() {
        int background = TextStyle.EDITOR_BACKGROUND_RGB;
        assertReadable("default", TextStyle.DEFAULT_RGB, background);
        assertReadable("attribute", TextStyle.ATTRIBUTE_RGB, background);
        assertReadable("comment", TextStyle.COMMENT_RGB, background);
        assertReadable("symbol", TextStyle.SYMBOL_RGB, background);
        assertReadable("constant", TextStyle.CONSTANT_RGB, background);
        assertReadable("keyword", TextStyle.KEYWORD_RGB, background);
    }

    @Test
    public void testSemanticStylesResolveToTheTunedRgb() {
        assertEquals(TextStyle.ATTRIBUTE_RGB, TextStyle.rgbOf(TextStyle.ATTRIBUTE, -1));
        assertEquals(TextStyle.COMMENT_RGB, TextStyle.rgbOf(TextStyle.COMMENT, -1));
        assertEquals(TextStyle.SYMBOL_RGB, TextStyle.rgbOf(TextStyle.SYMBOL, -1));
        assertEquals(TextStyle.CONSTANT_RGB, TextStyle.rgbOf(TextStyle.CONSTANT, -1));
        assertEquals(TextStyle.KEYWORD_RGB, TextStyle.rgbOf(TextStyle.KEYWORD, -1));
        assertEquals(TextStyle.DEFAULT_RGB, TextStyle.rgbOf(TextStyle.DEFAULT, -1));
    }

    @Test
    public void testOtherStylesFallBackToTheirColourCode() {
        assertEquals(0x123456, TextStyle.rgbOf(TextFormatting.RED, 0x123456));
        assertEquals(0x123456, TextStyle.rgbOf(TextFormatting.WHITE, 0x123456));
    }

    private static void assertReadable(String name, int rgb, int background) {
        double ratio = contrastRatio(rgb, background);
        assertTrue(name + " has only " + ratio + ":1 contrast on the editor panel",
                ratio >= MIN_CONTRAST);
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
