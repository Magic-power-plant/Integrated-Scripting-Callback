package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.LexicalContext;
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

    private static void assertReconstructs(String expected, List<Pair<TextFormatting, String>> segments) {
        assertEquals(expected, joined(segments));
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
                "const r = /ab+c/gi;",
                "a = b / c;",
                "`template ${x}`",
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
    public void testModernKeywords() {
        for (String keyword : new String[]{"class", "async", "await", "switch", "try", "catch",
                "finally", "throw", "export", "import", "extends", "yield", "this", "super"}) {
            List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize(keyword + " x");
            assertEquals(keyword, TextStyle.KEYWORD, styleOfSegmentContaining(segments, keyword));
        }
    }

    @Test
    public void testIdentifierIsNotKeyword() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("constant = 1;");
        // "constant" starts with "const" but is not a keyword: the whole word is plain.
        assertEquals(TextStyle.DEFAULT, styleOfSegmentContaining(segments, "constant"));
    }

    @Test
    public void testPropertyIsAttribute() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("item.stackable()");
        assertEquals(TextStyle.ATTRIBUTE, styleOfSegmentContaining(segments, "stackable"));
        assertEquals(TextStyle.DEFAULT, styleOfSegmentContaining(segments, "item"));
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
        JavaScriptTokenizer.LineTokens tokens = JavaScriptTokenizer.tokenizeLine("/* a",
                JavaScriptTokenizer.State.CODE);
        assertEquals("/* a", joined(tokens.getSegments()));
        assertEquals(TextStyle.COMMENT, tokens.getSegments().get(0).getLeft());
        assertEquals(JavaScriptTokenizer.State.BLOCK_COMMENT, tokens.getEndState());
    }

    @Test
    public void testBlockCommentContinuesOnNextLine() {
        List<List<Pair<TextFormatting, String>>> lines = JavaScriptTokenizer.tokenizeDocument("/* a\nb */ x");
        assertEquals(2, lines.size());
        assertEquals(TextStyle.COMMENT, styleOfSegmentContaining(lines.get(0), "/* a"));
        assertEquals(TextStyle.COMMENT, styleOfSegmentContaining(lines.get(1), "b */"));
        assertEquals(TextStyle.DEFAULT, styleOfSegmentContaining(lines.get(1), "x"));
        assertEquals("b */ x", joined(lines.get(1)));
    }

    @Test
    public void testTemplateLiteralSpansLines() {
        List<List<Pair<TextFormatting, String>>> lines =
                JavaScriptTokenizer.tokenizeDocument("const s = `a\nb`;");
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(lines.get(0), "`a"));
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(lines.get(1), "b`"));
        assertEquals(TextStyle.SYMBOL, styleOfSegmentContaining(lines.get(1), ";"));
    }

    @Test
    public void testTemplateLiteralWithinOneLine() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("`a ${b} c`");
        assertReconstructs("`a ${b} c`", segments);
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "`a ${b} c`"));
    }

    @Test
    public void testRegexLiteral() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("const r = /ab+c/gi;");
        assertEquals(TextStyle.CONSTANT, styleOfSegmentContaining(segments, "/ab+c/gi"));
    }

    @Test
    public void testDivisionIsNotRegex() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("a = b / c;");
        assertEquals(TextStyle.SYMBOL, styleOfSegmentContaining(segments, "/"));
    }

    @Test
    public void testStringLiteral() {
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("s = \"const // x\";");
        assertReconstructs("s = \"const // x\";", segments);
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
        assertReconstructs("s = 'it\\'s';", segments);
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

    @Test
    public void testNumberForms() {
        for (String number : new String[]{"0b1010", "0o777", "1_000", "1.5e-3", "42n"}) {
            List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize("x = " + number + ";");
            assertEquals(number, TextStyle.CONSTANT, styleOfSegmentContaining(segments, number));
        }
    }

    @Test
    public void testDocumentHasOneEntryPerLine() {
        List<List<Pair<TextFormatting, String>>> lines = JavaScriptTokenizer.tokenizeDocument("a\n\nb\n");
        assertEquals(4, lines.size());
        assertEquals("", joined(lines.get(1)));
        assertEquals("", joined(lines.get(3)));
    }

    // ---------------------------------------------------------------- lexical context

    @Test
    public void testContextInString() {
        String value = "const a = 'x';";
        assertEquals(LexicalContext.STRING, JavaScriptTokenizer.lexicalContextAt(value, 11));
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt(value, 13));
    }

    @Test
    public void testContextInLineComment() {
        String value = "x; // note";
        assertEquals(LexicalContext.COMMENT, JavaScriptTokenizer.lexicalContextAt(value, 8));
        assertEquals(LexicalContext.COMMENT, JavaScriptTokenizer.lexicalContextAt(value, value.length()));
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt(value, 1));
    }

    @Test
    public void testContextInBlockCommentAcrossLines() {
        String value = "/* a\nb */ x";
        assertEquals(LexicalContext.COMMENT, JavaScriptTokenizer.lexicalContextAt(value, 2));
        assertEquals(LexicalContext.COMMENT, JavaScriptTokenizer.lexicalContextAt(value, 6));
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt(value, 10));
    }

    @Test
    public void testContextInTemplateLiteral() {
        String value = "`a\nb`";
        assertEquals(LexicalContext.STRING, JavaScriptTokenizer.lexicalContextAt(value, 1));
        assertEquals(LexicalContext.STRING, JavaScriptTokenizer.lexicalContextAt(value, 3));
        // Right after the closing backtick the caret is in code again.
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt(value, 5));
    }

    @Test
    public void testContextAtEndOfUnterminatedString() {
        assertEquals(LexicalContext.STRING, JavaScriptTokenizer.lexicalContextAt("s = 'abc", 9));
        // A closed literal ends in code, so a quote typed after it opens a new one.
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt("s = 'abc'", 10));
    }

    @Test
    public void testContextAtCodePosition() {
        assertEquals(LexicalContext.CODE, JavaScriptTokenizer.lexicalContextAt("const x = 1;", 6));
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
