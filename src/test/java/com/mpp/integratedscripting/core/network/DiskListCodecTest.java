package com.mpp.integratedscripting.core.network;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the 1.12.2 terminal disk-list payload (M5).
 */
public class DiskListCodecTest {

    @Test
    public void testEmptyList() {
        assertEquals("", DiskListCodec.encode(new IntArrayList()));
        assertTrue(DiskListCodec.decode("").isEmpty());
    }

    @Test
    public void testRoundTrip() {
        IntList disks = new IntArrayList(new int[]{0, 3, 17});
        assertEquals("0,3,17", DiskListCodec.encode(disks));
        assertEquals(disks, DiskListCodec.decode("0,3,17"));
    }

    @Test
    public void testDecodeNull() {
        assertTrue(DiskListCodec.decode(null).isEmpty());
    }

    @Test
    public void testDecodeWhitespaceAndMalformedEntries() {
        IntList disks = DiskListCodec.decode(" 1 , x ,2,, 3 ");
        assertEquals(3, disks.size());
        assertEquals(1, disks.getInt(0));
        assertEquals(2, disks.getInt(1));
        assertEquals(3, disks.getInt(2));
    }

    @Test
    public void testDecodeTrailingComma() {
        IntList disks = DiskListCodec.decode("5,");
        assertEquals(1, disks.size());
        assertEquals(5, disks.getInt(0));
    }

}
