package com.mpp.integratedscripting.client.gui.component.input;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pure-logic tests for the completion popup geometry
 * (docs/LANGUAGE-API-REFACTOR.md Phase 4: positioning and hit testing are
 * tested without a client).
 */
public class CompletionPopupLayoutTest {

    @Test
    public void testOpensBelowTheCaretWhenThereIsRoom() {
        CompletionPopupLayout layout = new CompletionPopupLayout(100, 50, 320, 240, 60, 4);
        assertEquals(100, layout.getX());
        assertEquals(50, layout.getY());
        assertEquals(80, layout.getWidth());
        assertEquals(4 * CompletionPopupLayout.ROW_HEIGHT + CompletionPopupLayout.PADDING_Y * 2,
                layout.getHeight());
        assertEquals(4, layout.getRowCount());
    }

    @Test
    public void testFlipsAboveTheCaretWhenTheBottomIsFull() {
        CompletionPopupLayout layout = new CompletionPopupLayout(100, 220, 320, 240, 60, 4);
        int height = 4 * CompletionPopupLayout.ROW_HEIGHT + CompletionPopupLayout.PADDING_Y * 2;
        assertEquals(220 - CompletionPopupLayout.ROW_HEIGHT - height - 2, layout.getY());
        assertTrue(layout.getY() + layout.getHeight() < 240);
    }

    @Test
    public void testStaysInsideTheScreenHorizontally() {
        CompletionPopupLayout layout = new CompletionPopupLayout(300, 50, 320, 240, 60, 2);
        assertEquals(320 - 80 - 2, layout.getX());
        assertTrue(layout.getX() + layout.getWidth() <= 320);
    }

    @Test
    public void testWidthUsesTheContentBetweenTheBounds() {
        assertEquals(CompletionPopupLayout.MIN_WIDTH,
                new CompletionPopupLayout(0, 0, 320, 240, 10, 1).getWidth());
        assertEquals(CompletionPopupLayout.MAX_WIDTH,
                new CompletionPopupLayout(0, 0, 320, 240, 500, 1).getWidth());
        assertEquals(80, new CompletionPopupLayout(0, 0, 320, 240, 74, 1).getWidth());
    }

    @Test
    public void testRowHitTesting() {
        CompletionPopupLayout layout = new CompletionPopupLayout(100, 50, 320, 240, 60, 4);
        assertEquals(0, layout.getRowAt(110, 52));
        assertEquals(1, layout.getRowAt(110, 61));
        assertEquals(2, layout.getRowAt(110, 70));
        assertEquals(3, layout.getRowAt(110, 79));
        // Top padding, outside, and below the last row are not rows.
        assertEquals(-1, layout.getRowAt(110, 50));
        assertEquals(-1, layout.getRowAt(99, 55));
        assertEquals(-1, layout.getRowAt(110, 89));
        assertTrue(layout.contains(110, 55));
        assertFalse(layout.contains(99, 55));
    }

    @Test
    public void testATinyScreenStillShowsOneRow() {
        CompletionPopupLayout layout = new CompletionPopupLayout(10, 8, 100, 12, 60, 8);
        assertEquals(CompletionPopupLayout.ROW_HEIGHT + CompletionPopupLayout.PADDING_Y * 2,
                layout.getHeight());
        assertEquals(1, layout.getRowCount());
        assertEquals(1, layout.getY());
    }

}
