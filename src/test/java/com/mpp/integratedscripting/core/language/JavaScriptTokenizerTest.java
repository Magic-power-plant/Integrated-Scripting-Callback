package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.callback.TextStyle;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the 1.12.2 JavaScript syntax highlighter (M5, §11.2 item 13).
 */
public class JavaScriptTokenizerTest {

    private static String joined(List<Pair<TextFormatting, String>> segments) {
        StringBuilder builder = new StringBuilder();
        for (Pair<TextFormatting, String> segment : segments) {
            builder.append(segment.getRight());
        }
        return builder.toString();
    }

    private static TextFormatting styleOfSegmentContaining(List<Pair<TextFormatting, String>> segments,
                                                           String text) {
        for (Pair<TextFormatting, String> segment : segments) {
            if (segment.getRight().contains(text)) {
                return segment.getLeft();
            }
        }
        return TextStyle.DEFAULT;
    }

    @Test
    public void testEmptyLine() {
        assertTrue(JavaScriptTokenizer.tokenize("").isEmpty());
        assertTrue(JavaScriptTokenizer.tokenize(null).isEmpty());
    }

    @Test
    public void testSegmentsReconstructTheLine() {
        String[] lines = new String[]{
                "const foo = 42;",
                "function f(item) { return item.stackable(); }",
                "// a comment",
                "let s = \"a { string }\";",
                "x = 'it\\'s';",
                "  /* block */  ",
                "a\tb",
        };
        for (String line : lines) {
            assertEquals(line, joined(JavaScriptTokenizer.tokenize(line)));
        }
    }

    @Test
    public void testKeyword() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("const x = 1;");
        assertEquals(TextStyle.KEYWORD, styleOfSegmentContaining(segments, "const"));
        assertEquals(TextStyle.SYMBOL, styleOfSegmentContaining(segments, "="));
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "1"));
    }

    @Test
    public void testIdentifierIsNotKeyword() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("constant = 1;");
        // "constant" starts with "const" but is not a keyword: the whole word is plain.
        assertEquals(TextStyle.DEFAULT, styleOfSegmentContaining(segments, "constant"));
    }

    @Test
    public void testLineComment() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("x = 1; // note");
        assertEquals(TextStyle.COMMENT, styleOfSegmentContaining(segments, "// note"));
    }

    @Test
    public void testBlockComment() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("/* a */ x");
        assertEquals(TextStyle.COMMENT, styleOfSegmentContaining(segments, "/* a */"));
        assertEquals(TextStyle.DEFAULT, styleOfSegmentContaining(segments, "x"));
    }

    @Test
    public void testUnterminatedBlockCommentRunsToEndOfLine() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("/* a");
        assertEquals("/* a", joined(segments));
        assertEquals(TextStyle.COMMENT, segments.get(0).getLeft());
    }

    @Test
    public void testStringLiteral() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("s = \"const // x\";");
        assertEquals("s = \"const // x\";", joined(segments));
        // Keywords and comment markers inside a string are not highlighted.
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "\"const // x\""));
        for (Pair<TextFormatting, String> segment : segments) {
            assertTrue(segment.getLeft() != TextStyle.COMMENT);
            assertTrue(segment.getLeft() != TextStyle.KEYWORD);
        }
    }

    @Test
    public void testEscapedQuoteInString() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("s = 'it\\'s';");
        assertEquals("s = 'it\\'s';", joined(segments));
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "'it\\'s'"));
    }

    @Test
    public void testUnterminatedStringRunsToEndOfLine() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("s = \"abc");
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "\"abc"));
    }

    @Test
    public void testNumbers() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("a = 12.5 + 0xFF;");
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "12.5"));
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "0xFF"));
    }

    /**
     * Regression guard for the style constants: the GUI resolves these through
     * {@code TextFormatting.toString()}, which must produce a colour code.
     */
    @Test
    public void testStyleColourCodes() {
        for (TextFormatting style : new TextFormatting[]{TextStyle.ATTRIBUTE, TextStyle.COMMENT,
                TextStyle.SYMBOL, TextStyle.CONSTANT, TextStyle.KEYWORD}) {
            String formatted = style.toString();
            assertEquals(2, formatted.length());
            assertEquals('\u00a7', formatted.charAt(0));
        }
    }

}
