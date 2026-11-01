package com.mpp.integratedscripting.api.language.text;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link DocumentText} (docs/LANGUAGE-API-REFACTOR.md Phase 1).
 */
public class DocumentTextTest {

    @Test
    public void testEmptyDocument() {
        DocumentText document = DocumentText.of("");
        assertEquals(0, document.getLength());
        assertEquals(1, document.getLineCount());
        assertEquals(0, document.getLineStart(0));
        assertEquals(0, document.getLineEnd(0));
        assertEquals("", document.getLine(0));
        assertEquals(0, document.getLineOfOffset(0));
        assertEquals(0, document.getColumnOfOffset(0));
    }

    @Test
    public void testNullIsEmpty() {
        assertEquals("", DocumentText.of(null).getText());
        assertEquals(1, DocumentText.of(null).getLineCount());
    }

    @Test
    public void testLineIndex() {
        DocumentText document = DocumentText.of("abc\ndef\n");
        assertEquals(3, document.getLineCount());
        assertEquals(0, document.getLineStart(0));
        assertEquals(3, document.getLineEnd(0));
        assertEquals("abc", document.getLine(0));
        assertEquals(4, document.getLineStart(1));
        assertEquals(7, document.getLineEnd(1));
        assertEquals("def", document.getLine(1));
        assertEquals(8, document.getLineStart(2));
        assertEquals(8, document.getLineEnd(2));
        assertEquals("", document.getLine(2));
    }

    @Test
    public void testCrLfLineEndExcludesBothCharacters() {
        DocumentText document = DocumentText.of("abc\r\ndef");
        assertEquals("abc", document.getLine(0));
        assertEquals(3, document.getLineEnd(0));
        assertEquals(1, document.getLineOfOffset(5));
    }

    @Test
    public void testLineAndColumnOfOffset() {
        DocumentText document = DocumentText.of("abc\ndef\nghi");
        int[] offsets = new int[]{0, 2, 3, 4, 7, 8, 10};
        int[] lines = new int[]{0, 0, 0, 1, 1, 2, 2};
        int[] columns = new int[]{0, 2, 3, 0, 3, 0, 2};
        for (int i = 0; i < offsets.length; i++) {
            assertEquals("line of " + offsets[i], lines[i], document.getLineOfOffset(offsets[i]));
            assertEquals("column of " + offsets[i], columns[i], document.getColumnOfOffset(offsets[i]));
        }
        // Out of range offsets are clamped into the document.
        assertEquals(0, document.getLineOfOffset(-5));
        assertEquals(11, document.getLength());
        assertEquals(2, document.getLineOfOffset(999));
        assertEquals(3, document.getColumnOfOffset(999));
    }

    @Test
    public void testSliceIsBoundarySafe() {
        DocumentText document = DocumentText.of("abcdef");
        assertEquals("bcd", document.slice(1, 4));
        assertEquals("abc", document.slice(-3, 3));
        assertEquals("def", document.slice(3, 999));
        assertEquals("", document.slice(2, 2));
        // Reversed bounds are swapped.
        assertEquals("cd", document.slice(4, 2));
    }

    @Test
    public void testLineIndicesAreClamped() {
        DocumentText document = DocumentText.of("a\nb");
        assertEquals(0, document.getLineStart(-1));
        assertEquals("a", document.getLine(-7));
        assertEquals("b", document.getLine(99));
        assertEquals(3, document.getLineEnd(99));
    }

    @Test
    public void testRepeatedLineQueriesUseTheSameIndex() {
        DocumentText document = DocumentText.of("a\nb\nc\nd");
        assertEquals(4, document.getLineCount());
        // Offsets 0..6 of "a\nb\nc\nd" live on lines 0,0,1,1,2,2,3.
        int[] expectedLine = new int[]{0, 0, 1, 1, 2, 2, 3};
        for (int i = 0; i < 100; i++) {
            int offset = i % document.getLength();
            assertEquals("line of offset " + offset, expectedLine[offset],
                    document.getLineOfOffset(offset));
        }
    }

}
