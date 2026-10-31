package com.mpp.integratedscripting.client.gui.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the 1.12.2 editor line layout (M5).
 */
public class TextLayoutTest {

    /** Every character is 5 pixels wide, so wrapping is easy to reason about. */
    private static final TextLayout.IWidthProvider FIVE_PX = text -> text.length() * 5;

    @Test
    public void testEmptyValueHasOneLine() {
        TextLayout layout = TextLayout.build("", 50, FIVE_PX);
        assertEquals(1, layout.getLineCount());
        assertEquals("", layout.getLine(0).getText());
        assertEquals(0, layout.getLine(0).getStart());
        assertEquals(1, layout.getLine(0).getLineNumber());
    }

    @Test
    public void testSingleLine() {
        TextLayout layout = TextLayout.build("const foo = 1;", 500, FIVE_PX);
        assertEquals(1, layout.getLineCount());
        assertEquals("const foo = 1;", layout.getLine(0).getText());
    }

    @Test
    public void testLogicalLines() {
        TextLayout layout = TextLayout.build("a\nbb\nccc", 500, FIVE_PX);
        assertEquals(3, layout.getLineCount());
        assertEquals("a", layout.getLine(0).getText());
        assertEquals("bb", layout.getLine(1).getText());
        assertEquals("ccc", layout.getLine(2).getText());
        assertEquals(0, layout.getLine(0).getStart());
        assertEquals(2, layout.getLine(1).getStart());
        assertEquals(5, layout.getLine(2).getStart());
        assertEquals(1, layout.getLine(0).getLineNumber());
        assertEquals(2, layout.getLine(1).getLineNumber());
        assertEquals(3, layout.getLine(2).getLineNumber());
    }

    @Test
    public void testTrailingNewlineAddsEmptyLine() {
        TextLayout layout = TextLayout.build("a\n", 500, FIVE_PX);
        assertEquals(2, layout.getLineCount());
        assertEquals("", layout.getLine(1).getText());
        assertEquals(2, layout.getLine(1).getStart());
    }

    @Test
    public void testWrapping() {
        // 50 pixels = 10 characters per line.
        TextLayout layout = TextLayout.build("0123456789abcdef", 50, FIVE_PX);
        assertEquals(2, layout.getLineCount());
        assertEquals("0123456789", layout.getLine(0).getText());
        assertEquals("abcdef", layout.getLine(1).getText());
        // Wrapped lines share the logical line number.
        assertEquals(1, layout.getLine(1).getLineNumber());
        assertEquals(10, layout.getLine(1).getStart());
    }

    @Test
    public void testWrappingDoesNotLoseCharacters() {
        String value = "aaa bbb ccc ddd eee fff ggg hhh iii jjj kkk lll";
        TextLayout layout = TextLayout.build(value, 40, FIVE_PX);
        StringBuilder joined = new StringBuilder();
        for (TextLayout.Line line : layout.getLines()) {
            joined.append(line.getText());
        }
        assertEquals(value, joined.toString());
    }

    @Test
    public void testVeryNarrowWidthKeepsOneCharacterPerLine() {
        TextLayout layout = TextLayout.build("abc", 1, FIVE_PX);
        assertEquals(3, layout.getLineCount());
        assertEquals("a", layout.getLine(0).getText());
        assertEquals("c", layout.getLine(2).getText());
    }

    @Test
    public void testLineIndexAtCharacter() {
        TextLayout layout = TextLayout.build("ab\ncd", 500, FIVE_PX);
        assertEquals(0, layout.getLineIndexAt(0));
        assertEquals(0, layout.getLineIndexAt(2));
        assertEquals(1, layout.getLineIndexAt(3));
        assertEquals(1, layout.getLineIndexAt(5));
    }

    @Test
    public void testGetX() {
        TextLayout layout = TextLayout.build("abcd", 500, FIVE_PX);
        assertEquals(0, layout.getX(0, 0));
        assertEquals(15, layout.getX(0, 3));
        assertEquals(20, layout.getX(0, 4));
        // Out of range positions are clamped.
        assertEquals(20, layout.getX(0, 99));
    }

    @Test
    public void testGetCharIndexAt() {
        TextLayout layout = TextLayout.build("abcd", 500, FIVE_PX);
        assertEquals(0, layout.getCharIndexAt(0, 0));
        assertEquals(1, layout.getCharIndexAt(0, 7));
        assertEquals(2, layout.getCharIndexAt(0, 12));
        assertEquals(4, layout.getCharIndexAt(0, 100));
    }

    @Test
    public void testChangeLineKeepsColumn() {
        TextLayout layout = TextLayout.build("abcd\nab\nabcdef", 500, FIVE_PX);
        // From column 4 of line 1 down to the shorter line: clamps to its end.
        assertEquals(7, layout.changeLine(3, 1));
        // Down again keeps the pixel column, which is the second character here.
        assertEquals(10, layout.changeLine(7, 1));
        // Up from the beginning stays at the beginning.
        assertEquals(0, layout.changeLine(0, -1));
    }

    @Test
    public void testFindLineStartAndEnd() {
        TextLayout layout = TextLayout.build("ab\ncd", 500, FIVE_PX);
        assertEquals(0, layout.findLineStart(1));
        assertEquals(2, layout.findLineEnd(1));
        assertEquals(3, layout.findLineStart(4));
        assertEquals(5, layout.findLineEnd(5));
    }

    @Test
    public void testAllLinesHaveValidStarts() {
        TextLayout layout = TextLayout.build("one\ntwo\nthree", 16, FIVE_PX);
        for (int i = 0; i < layout.getLineCount(); i++) {
            TextLayout.Line line = layout.getLine(i);
            assertTrue(line.getStart() >= 0);
            assertTrue(line.getStart() + line.getText().length() <= 13);
            assertTrue(line.getLineNumber() >= 1);
        }
    }

}
