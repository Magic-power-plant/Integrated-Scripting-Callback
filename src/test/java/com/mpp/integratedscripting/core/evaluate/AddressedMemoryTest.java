package com.mpp.integratedscripting.core.evaluate;

import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory;
import com.mpp.integratedscripting.core.evaluate.value.ValueTypeAddressedMemory;
import com.mpp.integratedscripting.inventory.container.ContainerAddressedMemory;
import net.minecraft.util.math.BlockPos;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Pure-logic tests for the addressed memory: address mapping, value
 * serialization, tooltip safety and the GUI geometry.
 *
 * <p>These run without a world, so they cover the parts that a GUI or a script
 * failure could silently break. The block, its network element, its operators and
 * its GUI are covered by {@code AddressedMemorySmokeHarness}
 * ({@code ./gradlew runClient -PmemorySmoke}).</p>
 *
 * @author rubensworks
 */
public class AddressedMemoryTest {

    @Test
    public void testAddressLettersAtoP() {
        assertEquals(16, TileAddressedMemory.ADDRESS_COUNT);
        assertEquals(0, TileAddressedMemory.getAddressIndex("a"));
        assertEquals(1, TileAddressedMemory.getAddressIndex("b"));
        assertEquals(15, TileAddressedMemory.getAddressIndex("p"));
        // Uppercase is accepted, because script authors should not have to care.
        assertEquals(1, TileAddressedMemory.getAddressIndex("B"));

        // Everything outside a to p is invalid, including letters the block has no slot for.
        assertEquals(-1, TileAddressedMemory.getAddressIndex("q"));
        assertEquals(-1, TileAddressedMemory.getAddressIndex("z"));
        assertEquals(-1, TileAddressedMemory.getAddressIndex("0"));
        assertEquals(-1, TileAddressedMemory.getAddressIndex(""));
        assertEquals(-1, TileAddressedMemory.getAddressIndex("ab"));
        assertEquals(-1, TileAddressedMemory.getAddressIndex(null));

        assertEquals("a", TileAddressedMemory.getAddressLetter(0));
        assertEquals("p", TileAddressedMemory.getAddressLetter(15));
    }

    @Test
    public void testAddressSlotsStartAfterTheMemoryCard() {
        assertEquals(0, TileAddressedMemory.SLOT_MEMORY_CARD);
        assertEquals(1, TileAddressedMemory.getSlotForAddress(0));
        assertEquals(16, TileAddressedMemory.getSlotForAddress(15));
        assertEquals(17, TileAddressedMemory.INVENTORY_SIZE);
    }

    @Test
    public void testValueSerializationRoundTrip() {
        ValueTypeAddressedMemory type = new ValueTypeAddressedMemory();
        DimPos position = DimPos.of(3, new BlockPos(10, 64, -5));
        ValueAddressedMemory value = ValueAddressedMemory.of(type, position);

        assertTrue(type.isObject());
        assertEquals("3:10:64:-5", type.serialize(value));
        assertEquals("10, 64, -5 (3)", type.toCompactString(value));

        ValueAddressedMemory parsed = type.deserialize(type.serialize(value));
        assertEquals(3, parsed.getPosition().getDimensionId());
        assertEquals(new BlockPos(10, 64, -5), parsed.getPosition().getBlockPos());
        assertEquals("3:10:64:-5", type.serialize(parsed));
    }

    @Test
    public void testDefaultValueIsEmpty() {
        ValueTypeAddressedMemory type = new ValueTypeAddressedMemory();
        ValueAddressedMemory empty = type.getDefault();

        assertNull(empty.getPosition());
        assertEquals("", type.serialize(empty));
        assertEquals("null", type.toCompactString(empty));
        assertNull(empty.getMemory());
        assertNull(type.deserialize("").getPosition());
    }

    @Test
    public void testTooltipAcceptsMissingValue() {
        ValueTypeAddressedMemory type = new ValueTypeAddressedMemory();

        // The Logic Programmer lists this value type as a string-input element, whose
        // tooltip carries no value at all:
        //   GuiLogicProgrammerBase#drawGuiContainerForegroundLayer
        //     -> ValueTypeLPElementBase#loadTooltip
        //     -> GuiElementValueTypeString#loadTooltip(list)
        //     -> valueType.loadTooltip(list, true, null)
        // Dereferencing that null threw an NPE and crashed the client while rendering
        // the screen (crash-2026-11-06_21.39.19-client.txt). The GUI chain itself is
        // not unit-testable here (its advanced tooltip calls L10NHelpers, which needs
        // a live game), so this covers the null argument at the same entry point.
        List<String> missing = new ArrayList<>();
        type.loadTooltip(missing, false, null);
        assertEquals(1, missing.size());

        // The empty (default) value is real but has no position: no position line.
        List<String> empty = new ArrayList<>();
        type.loadTooltip(empty, false, type.getDefault());
        assertEquals(1, empty.size());

        // A placed memory gets the extra position line on top of the base line.
        List<String> placed = new ArrayList<>();
        type.loadTooltip(placed, false,
                ValueAddressedMemory.of(type, DimPos.of(3, new BlockPos(10, 64, -5))));
        assertEquals(2, placed.size());
    }

    @Test
    public void testGuiGeometryMatchesTheTexture() {
        // Row one starts at x = 45 and steps 36 pixels (a type box sits between slots).
        assertEquals(45, ContainerAddressedMemory.getAddressSlotX(0));
        assertEquals(81, ContainerAddressedMemory.getAddressSlotX(1));
        assertEquals(297, ContainerAddressedMemory.getAddressSlotX(7));
        // Row two is offset half a cell to the left.
        assertEquals(27, ContainerAddressedMemory.getAddressSlotX(8));
        assertEquals(279, ContainerAddressedMemory.getAddressSlotX(15));
        assertEquals(34, ContainerAddressedMemory.getAddressSlotY(0));
        assertEquals(91, ContainerAddressedMemory.getAddressSlotY(15));
        // Type boxes are 32x7, 4 pixels below the address text.
        assertEquals(37, ContainerAddressedMemory.getTextBoxX(0));
        assertEquals(19, ContainerAddressedMemory.getTextBoxX(8));
        assertEquals(22, ContainerAddressedMemory.getTextBoxY(0));
        assertEquals(79, ContainerAddressedMemory.getTextBoxY(15));
        assertEquals(32, ContainerAddressedMemory.TEXT_BOX_WIDTH);
        assertEquals(7, ContainerAddressedMemory.TEXT_BOX_HEIGHT);
        assertEquals(4, ContainerAddressedMemory.ADDRESS_LABEL_GAP);
    }

}


