package com.mpp.integratedscripting.client.gui.component;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the terminal header geometry (disk label/field/title columns).
 *
 * <p>These guard the regression where the part name, the disk label and the
 * arrowed disk field all started at x = 8..30 in the same header box.</p>
 */
public class TerminalHeaderLayoutTest {

    /** Width of {@code "Disk:"} in the vanilla font. */
    private static final int ENGLISH_LABEL_WIDTH = 22;
    /** Width of {@code "磁盘:"} (two full-width glyphs plus the colon). */
    private static final int CHINESE_LABEL_WIDTH = 20;

    @Test
    public void testDiskLabelEndsBeforeField() {
        for (int width : new int[]{ENGLISH_LABEL_WIDTH, CHINESE_LABEL_WIDTH}) {
            int labelX = TerminalHeaderLayout.diskLabelX(width);
            assertTrue("label must start inside the header box", labelX >= TerminalHeaderLayout.HEADER_BOX_LEFT);
            assertTrue("label must not reach the disk field",
                    labelX + width <= TerminalHeaderLayout.DISK_FIELD_X);
        }
    }

    @Test
    public void testShippedLocalesFitTheLabelArea() {
        assertTrue(ENGLISH_LABEL_WIDTH <= TerminalHeaderLayout.DISK_LABEL_MAX_WIDTH);
        assertTrue(CHINESE_LABEL_WIDTH <= TerminalHeaderLayout.DISK_LABEL_MAX_WIDTH);
    }

    @Test
    public void testDiskLabelIsRightAlignedAgainstTheField() {
        assertEquals(TerminalHeaderLayout.DISK_FIELD_X - TerminalHeaderLayout.DISK_LABEL_GAP
                        - ENGLISH_LABEL_WIDTH,
                TerminalHeaderLayout.diskLabelX(ENGLISH_LABEL_WIDTH));
    }

    @Test
    public void testOverlongLabelIsClampedToTheHeaderBox() {
        // A translation wider than the label area starts at the box edge instead of
        // running off the background texture.
        assertEquals(TerminalHeaderLayout.HEADER_BOX_LEFT,
                TerminalHeaderLayout.diskLabelX(TerminalHeaderLayout.DISK_LABEL_MAX_WIDTH + 10));
    }

    @Test
    public void testDiskFieldStaysInsideHeaderBox() {
        int fieldX = TerminalHeaderLayout.DISK_FIELD_X;
        int fieldRight = fieldX + TerminalHeaderLayout.DISK_FIELD_WIDTH;
        assertTrue(fieldX > TerminalHeaderLayout.diskLabelX(CHINESE_LABEL_WIDTH));
        assertTrue(fieldRight <= TerminalHeaderLayout.HEADER_BOX_RIGHT);
    }

    @Test
    public void testTitleIsInTheWideHeaderBox() {
        // The part name used to be drawn at (8, 6); it must now start right of the
        // narrow header box that holds the disk selector.
        assertTrue(TerminalHeaderLayout.TITLE_X >= TerminalHeaderLayout.HEADER_BOX_RIGHT + 8);
        assertTrue(TerminalHeaderLayout.TITLE_X + TerminalHeaderLayout.DISK_FIELD_WIDTH
                <= 256);
    }

}
