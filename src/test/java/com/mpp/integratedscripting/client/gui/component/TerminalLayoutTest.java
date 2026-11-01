package com.mpp.integratedscripting.client.gui.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the resizable terminal geometry.
 *
 * <p>They pin two properties: at 256x240 the layout reproduces the original
 * texture exactly, and on a larger scaled screen every growing band grows while
 * the frame, header and slot blocks keep their texture size and stay bottom
 * anchored, with the slot cluster centered horizontally.</p>
 */
public class TerminalLayoutTest {

    @Test
    public void testBaseGeometryMatchesTheOriginalTexture() {
        TerminalLayout layout = new TerminalLayout(TerminalLayout.BASE_WIDTH, TerminalLayout.BASE_HEIGHT);

        assertEquals(256, layout.getWidth());
        assertEquals(240, layout.getHeight());
        assertFalse(layout.isExpanded());
        assertEquals(0, layout.getExtraWidth());
        assertEquals(0, layout.getExtraHeight());

        // Left panel: 18px header + 214px list + 8px bottom band.
        assertEquals(214, layout.getListHeight());
        assertEquals(26, layout.getVisibleRows());
        assertEquals(162, layout.getScrollBarHeight());
        assertEquals(222, layout.getCreateButtonY());
        assertEquals(232, layout.getLeftBottomTop());

        // Main panel: 106px bottom band and a 168x115 editor.
        assertEquals(134, layout.getBottomTop());
        assertEquals(168, layout.getEditorWidth());
        assertEquals(115, layout.getEditorHeight());

        // Original error icon and slot block positions.
        assertEquals(212, layout.getErrorX());
        assertEquals(139, layout.getErrorY());
        assertEquals(87, layout.getPlayerGridX());
        assertEquals(157, layout.getPlayerGridY());
        assertEquals(215, layout.getHotbarY());
        assertEquals(231, layout.getVariableSlotX());
        assertEquals(136, layout.getVariableSlotY());
    }

    @Test
    public void testSmallScreenKeepsTheOriginalSize() {
        TerminalLayout layout = TerminalLayout.forScreen(256, 240);
        assertEquals(256, layout.getWidth());
        assertEquals(240, layout.getHeight());

        // A window smaller than the texture (or a tiny scaled GUI) may not shrink
        // the screen below its designed minimum.
        layout = TerminalLayout.forScreen(100, 100);
        assertEquals(256, layout.getWidth());
        assertEquals(240, layout.getHeight());
    }

    @Test
    public void testScreenMarginAndExpansion() {
        // A 427x240 scaled screen (854x480 at GUI scale 2) keeps the height and
        // gains 427 - 2*8 - 256 = 155px of editor width.
        TerminalLayout layout = TerminalLayout.forScreen(427, 240);
        assertEquals(411, layout.getWidth());
        assertEquals(240, layout.getHeight());
        assertEquals(155, layout.getExtraWidth());
        assertEquals(323, layout.getEditorWidth());

        // The file list keeps its designed height if the GUI height does not grow.
        assertEquals(214, layout.getListHeight());
        assertEquals(26, layout.getVisibleRows());
        assertEquals(162, layout.getScrollBarHeight());

        // The slot cluster moves by half of the extra width (155 / 2 = 77).
        assertEquals(77, layout.getContentShiftX());
        assertEquals(87 + 77, layout.getPlayerGridX());
        assertEquals(231 + 77, layout.getVariableSlotX());
        assertEquals(212 + 77, layout.getErrorX());
    }

    @Test
    public void testHeightExpandsTheEditorAndKeepsTheBottomBand() {
        TerminalLayout layout = TerminalLayout.forScreen(800, 400);
        assertEquals(784, layout.getWidth());
        assertEquals(384, layout.getHeight());
        assertEquals(144, layout.getExtraHeight());

        assertEquals(384 - 106, layout.getBottomTop());
        assertEquals(384 - 106 - 19, layout.getEditorHeight());
        assertEquals(384 - 26, layout.getListHeight());
        assertEquals(384 - 18, layout.getCreateButtonY());
        assertEquals(384 - 101, layout.getErrorY());
    }

    @Test
    public void testSlotClusterStaysCenteredAndBottomAnchored() {
        for (int width : new int[]{256, 411, 640, TerminalLayout.MAX_WIDTH}) {
            for (int height : new int[]{240, 254, 480, TerminalLayout.MAX_HEIGHT}) {
                TerminalLayout layout = new TerminalLayout(width, height);
                int shift = layout.getContentShiftX();
                // The cluster moves by half of the extra width, so it stays centered
                // under the editor, and the variable slot stays above the right edge
                // of the player inventory.
                assertEquals(87 + shift, layout.getPlayerGridX());
                assertEquals(231 + shift, layout.getVariableSlotX());
                assertEquals(layout.getVariableSlotX() - 19, layout.getErrorX());
                // The three inventory rows and the hotbar stay above the bevel.
                assertEquals(layout.getHeight() - 7, layout.getPlayerGridY() + 76);
                assertEquals(layout.getHeight() - 7, layout.getHotbarY() + 18);
                // The cluster always fits inside the main panel's right frame.
                assertTrue(layout.getPlayerGridX() + 162
                        <= layout.getWidth() - TerminalLayout.RIGHT_FRAME_WIDTH);
                assertTrue(layout.getVariableSlotX() + 18
                        <= layout.getWidth() - TerminalLayout.RIGHT_FRAME_WIDTH);
                // Only the 3px asymmetry of the original texture remains.
                int leftGap = layout.getPlayerGridX() - 84;
                int rightGap = layout.getWidth() - TerminalLayout.RIGHT_FRAME_WIDTH
                        - (layout.getPlayerGridX() + 162);
                assertTrue(Math.abs(leftGap - rightGap) <= 3);
            }
        }
    }

    @Test
    public void testScrollBarAlwaysFitsInsideTheList() {
        for (int height = TerminalLayout.BASE_HEIGHT; height <= TerminalLayout.MAX_HEIGHT; height += 16) {
            TerminalLayout layout = new TerminalLayout(TerminalLayout.BASE_WIDTH, height);
            assertTrue("scroll bar must not be shorter than its handle",
                    layout.getScrollBarHeight() >= 17);
            assertTrue("scroll bar must stay inside the list band",
                    layout.getScrollBarHeight() <= layout.getListHeight());
        }
    }

    @Test
    public void testPathologicalScreenIsCapped() {
        TerminalLayout layout = TerminalLayout.forScreen(10000, 10000);
        assertEquals(TerminalLayout.MAX_WIDTH, layout.getWidth());
        assertEquals(TerminalLayout.MAX_HEIGHT, layout.getHeight());
    }

}
