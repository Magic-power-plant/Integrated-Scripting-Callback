package com.mpp.integratedscripting.callback;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBlock.ValueBlock;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeEntity.ValueEntity;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the 1.12.2 object value bridges (§5.5.1).
 *
 * <p>The reference implementation round-trips object values through SNBT. In
 * 1.12.2 only item stacks, fluid stacks, ingredients and recipes serialize to
 * SNBT; blocks serialize as {@code registryName$meta} and entities as a UUID
 * string, so those two need a dedicated bridge. This is the
 * {@code ObjectValueBridgesTest} listed in §11.1.</p>
 *
 * <p>Only the bridge contract is tested here; constructing real ID object values
 * needs the game registries and is covered in-game by §11.2 items 21 and 23.</p>
 */
public class ObjectValueBridgesTest {

    private final IEvaluationExceptionFactory exceptionFactory = EvaluationException::new;

    @Test
    public void testEveryObjectValueTypeHasABridge() {
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_ITEMSTACK));
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_FLUIDSTACK));
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_INGREDIENTS));
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_RECIPE));
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_BLOCK));
        assertNotNull(ObjectValueBridges.get(ValueTypes.OBJECT_ENTITY));
        assertEquals(6, ObjectValueBridges.getBridges().size());
        assertTrue(ObjectValueBridges.get(ValueTypes.OBJECT_BLOCK) instanceof BlockObjectValueBridge);
        assertTrue(ObjectValueBridges.get(ValueTypes.OBJECT_ENTITY) instanceof EntityObjectValueBridge);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockSerializesToRegistryNameAndMeta() {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);
        ValueBlock value = mock(ValueBlock.class);
        when(valueType.serialize(value)).thenReturn("minecraft:stone$5");

        NBTBase tag = new BlockObjectValueBridge().toNbt(valueType, value, this.exceptionFactory);

        assertTrue(tag instanceof NBTTagCompound);
        NBTTagCompound compound = (NBTTagCompound) tag;
        assertEquals("minecraft:stone", compound.getString(BlockObjectValueBridge.KEY_NAME));
        assertEquals(5, compound.getInteger(BlockObjectValueBridge.KEY_META));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockWithoutSerializationBecomesAnEmptyObject() {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);
        ValueBlock value = mock(ValueBlock.class);
        when(valueType.serialize(value)).thenReturn("");

        NBTTagCompound compound = (NBTTagCompound) new BlockObjectValueBridge()
                .toNbt(valueType, value, this.exceptionFactory);

        assertTrue(compound.getKeySet().isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockDeserializesNameAndMeta() throws EvaluationException {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);

        NBTTagCompound compound = new NBTTagCompound();
        compound.setString(BlockObjectValueBridge.KEY_NAME, "minecraft:stone");
        compound.setInteger(BlockObjectValueBridge.KEY_META, 3);

        new BlockObjectValueBridge().fromNbt(valueType, compound, this.exceptionFactory);

        verify(valueType).deserialize(eq("minecraft:stone$3"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockAcceptsTheCapitalisedNameKey() throws EvaluationException {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);

        NBTTagCompound compound = new NBTTagCompound();
        compound.setString(BlockObjectValueBridge.KEY_NAME_LEGACY, "minecraft:dirt");

        new BlockObjectValueBridge().fromNbt(valueType, compound, this.exceptionFactory);

        verify(valueType).deserialize(eq("minecraft:dirt$0"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockWithoutNameDeserializesEmpty() throws EvaluationException {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);

        new BlockObjectValueBridge().fromNbt(valueType, new NBTTagCompound(), this.exceptionFactory);

        verify(valueType).deserialize(eq(""));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testBlockRejectsNonCompoundTags() {
        ValueObjectTypeBase<ValueBlock> valueType = mock(ValueObjectTypeBase.class);
        try {
            new BlockObjectValueBridge().fromNbt(valueType, new NBTTagString("x"), this.exceptionFactory);
            fail("A non-compound tag must be rejected");
        } catch (EvaluationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("object_not_compound")
                    || e.getMessage().contains("compound"));
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEntitySerializesToUuidString() {
        ValueObjectTypeBase<ValueEntity> valueType = mock(ValueObjectTypeBase.class);
        ValueEntity value = mock(ValueEntity.class);
        when(valueType.serialize(value)).thenReturn("0f5e1c3a-0000-0000-0000-000000000000");

        NBTTagCompound compound = (NBTTagCompound) new EntityObjectValueBridge()
                .toNbt(valueType, value, this.exceptionFactory);

        assertEquals("0f5e1c3a-0000-0000-0000-000000000000",
                compound.getString(EntityObjectValueBridge.KEY_UUID));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testEntityDeserializesUuid() throws EvaluationException {
        ValueObjectTypeBase<ValueEntity> valueType = mock(ValueObjectTypeBase.class);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString(EntityObjectValueBridge.KEY_UUID, "id-1");

        new EntityObjectValueBridge().fromNbt(valueType, compound, this.exceptionFactory);

        verify(valueType).deserialize(eq("id-1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSnbtBridgeParsesAndProducesSnbt() throws EvaluationException {
        ValueObjectTypeBase<org.cyclops.integrateddynamics.api.evaluate.variable.IValue> valueType =
                mock(ValueObjectTypeBase.class);
        when(valueType.serialize(any())).thenReturn("{id:\"minecraft:stone\",Count:1b}");

        NBTBase tag = new SnbtObjectValueBridge<>().toNbt(valueType,
                mock(org.cyclops.integrateddynamics.api.evaluate.variable.IValue.class),
                this.exceptionFactory);

        assertTrue(tag instanceof NBTTagCompound);
        assertEquals("minecraft:stone", ((NBTTagCompound) tag).getString("id"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSnbtBridgeRoundTripsThroughDeserialize() throws EvaluationException {
        ValueObjectTypeBase<org.cyclops.integrateddynamics.api.evaluate.variable.IValue> valueType =
                mock(ValueObjectTypeBase.class);
        NBTTagCompound compound = new NBTTagCompound();
        compound.setString("id", "minecraft:stone");

        new SnbtObjectValueBridge<>().fromNbt(valueType, compound, this.exceptionFactory);

        verify(valueType).deserialize(eq(compound.toString()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSnbtBridgeReportsInvalidSnbt() {
        ValueObjectTypeBase<org.cyclops.integrateddynamics.api.evaluate.variable.IValue> valueType =
                mock(ValueObjectTypeBase.class);
        when(valueType.serialize(any())).thenReturn("this is not snbt");

        try {
            new SnbtObjectValueBridge<>().toNbt(valueType,
                    mock(org.cyclops.integrateddynamics.api.evaluate.variable.IValue.class),
                    this.exceptionFactory);
            fail("Invalid SNBT must be reported instead of crashing");
        } catch (EvaluationException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("object_serialize")
                    || e.getMessage().contains("this is not snbt"));
        }
    }

    @Test
    public void testUnknownValueTypeHasNoBridge() {
        // A non-object value type is not registered and must not resolve a bridge.
        assertSame(null, ObjectValueBridges.get(null));
    }

}
