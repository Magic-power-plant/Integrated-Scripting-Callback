package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslatorRegistry;import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorObjectAdapter;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the TypeScript declarations that the server writes on start.
 *
 * <p>They run without a game because {@link TypeScriptTypingsGenerator#render}
 * takes the declarations and operators as arguments; only ID's value type
 * singletons are used, exactly like {@code ObjectValueBridgesTest} already
 * does.</p>
 */
public class TypeScriptTypingsGeneratorTest {

    @Test
    public void testGenericSignatureNamesItsParametersAfterTheirTypes() {
        String signature = TypingsRenderer.signature(null, "blockIsplantable",
                Collections.singletonList(TypingsType.object("ValueBlock", "block")),
                TypingsType.BOOLEAN);

        assertEquals("(block: ValueBlock) => boolean", signature);
    }

    @Test
    public void testRepeatedParameterTypesAreNumbered() {
        String signature = TypingsRenderer.signature(null, "arithmeticAddition",
                Arrays.asList(TypingsType.NUMBER, TypingsType.NUMBER), TypingsType.NUMBER);

        assertEquals("(number: number, number2: number) => number", signature);
    }

    @Test
    public void testOperatorWithoutInputs() {
        assertEquals("() => number",
                TypingsRenderer.signature(null, "operatorWithoutInputs",
                        Collections.emptyList(), TypingsType.NUMBER));
    }

    @Test
    public void testCuratedListSignatureUsesAGenericElementType() {
        String signature = TypingsRenderer.signature(null, "listGet",
                Arrays.asList(TypingsType.LIST, TypingsType.NUMBER), TypingsType.ANY);

        assertEquals("<T>(list: T[], index: number) => T", signature);
    }

    @Test
    public void testCuratedSignatureIsSkippedWhenTheOperatorChanged() {
        // listGet never takes a string; a registry that says so must not get the
        // curated signature, because that would hide the ID change from the IDE.
        String signature = TypingsRenderer.signature(null, "listGet",
                Arrays.asList(TypingsType.STRING, TypingsType.NUMBER), TypingsType.ANY);

        assertEquals("(string: string, number: number) => any", signature);
    }

    @Test
    public void testCuratedObjectMethodKeepsItsElementType() {
        assertEquals("() => ValueItemstack[]",
                TypingsRenderer.signature("ValueItemstack", "inventory",
                        Collections.emptyList(), TypingsType.LIST));
        assertEquals("() => ValueItemstack[]",
                TypingsRenderer.signature("ValueEntity", "armorinventory",
                        Collections.emptyList(), TypingsType.LIST));
        // The global variants access the same lists.
        assertEquals("(entity: ValueEntity) => ValueItemstack[]",
                TypingsRenderer.signature(null, "entityInventory",
                        Collections.singletonList(TypingsType.object("ValueEntity", "entity")),
                        TypingsType.LIST));
    }

    @Test
    public void testUnknownSignatureFallsBackToTheGenericDeclaration() {
        assertNull(TypingsSignatures.global("notAnOperator",
                Collections.singletonList(TypingsType.LIST), TypingsType.ANY));
        assertEquals("(list: any[]) => any",
                TypingsRenderer.signature(null, "notAnOperator",
                        Collections.singletonList(TypingsType.LIST), TypingsType.ANY));
    }

    @Test
    public void testIdentityMemberTypes() {
        assertEquals("BlockIdentity", TypingsTypes.identityType("id_block"));
        assertEquals("ItemStackIdentity", TypingsTypes.identityType("id_item"));
        assertEquals("FluidStackIdentity", TypingsTypes.identityType("id_fluid"));
        assertEquals("EntityIdentity", TypingsTypes.identityType("id_entity"));
        assertEquals("IngredientsIdentity", TypingsTypes.identityType("id_ingredients"));
        assertEquals("RecipeIdentity", TypingsTypes.identityType("id_recipe"));
        // A modded object type publishes its serialized NBT form.
        assertEquals("NbtCompound", TypingsTypes.identityType("id_energy"));
        assertEquals("NbtCompound", TypingsTypes.identityType(null));
    }

    @Test
    public void testInterfaceNamesAreStableAndSanitized() {
        assertEquals("ValueBlock", TypingsTypes.interfaceNameFor("block"));
        assertEquals("ValueItemstack", TypingsTypes.interfaceNameFor("itemstack"));
        assertEquals("ValueEnergy", TypingsTypes.interfaceNameFor("energy"));
        assertEquals("ValueUnknown", TypingsTypes.interfaceNameFor(""));
        assertFalse(TypingsTypes.interfaceNameFor("my type").contains(" "));
    }

    @Test
    public void testValueTypesWithoutATranslatorGetNoInterface() {
        IValueType<?> mystery = mockValueType("mystery");
        IValueTranslatorRegistry registry = mock(IValueTranslatorRegistry.class);
        when(registry.getValueTypeTranslator(raw(mystery))).thenReturn(null);

        List<TypingsDeclarations.Declaration> declarations =
                TypingsDeclarations.collect(Collections.singletonList(mystery), registry,
                        Collections.emptyList());

        assertTrue(declarations.isEmpty());
        assertEquals(TypingsType.ANY, new TypingsTypes(Collections.emptyMap()).of(mystery));
    }

    @Test
    public void testTranslatedObjectTypesAreDeclaredWithTheirMember() {
        IValueType<?> itemstack = mockValueType("itemstack");
        IValueTranslatorRegistry registry = mock(IValueTranslatorRegistry.class);
        IValueTranslator translator = adapter("id_item");
        when(registry.getValueTypeTranslator(raw(itemstack))).thenReturn(translator);

        List<TypingsDeclarations.Declaration> declarations =
                TypingsDeclarations.collect(Collections.singletonList(itemstack), registry,
                        Collections.emptyList());

        assertEquals(1, declarations.size());
        TypingsDeclarations.Declaration declaration = declarations.get(0);
        assertEquals("ValueItemstack", declaration.getInterfaceName());
        assertEquals("id_item", declaration.getMemberKey());

        TypingsTypes types = new TypingsTypes(TypingsDeclarations.names(declarations));
        TypingsType type = types.of(itemstack);
        assertEquals("ValueItemstack", type.ts);
        assertEquals("itemstack", type.parameter);
    }

    @Test
    public void testCollidingTypeNamesAreSuffixedDeterministically() {
        IValueType<?> first = mockValueType("block");
        IValueType<?> second = mockValueType("block");
        IValueTranslatorRegistry registry = mock(IValueTranslatorRegistry.class);
        IValueTranslator firstTranslator = adapter("id_first");
        IValueTranslator secondTranslator = adapter("id_second");
        when(registry.getValueTypeTranslator(raw(first))).thenReturn(firstTranslator);
        when(registry.getValueTypeTranslator(raw(second))).thenReturn(secondTranslator);

        List<TypingsDeclarations.Declaration> declarations =
                TypingsDeclarations.collect(Arrays.asList(first, second), registry,
                        Collections.emptyList());

        assertEquals(2, declarations.size());
        assertFalse(declarations.get(0).getInterfaceName()
                .equals(declarations.get(1).getInterfaceName()));
    }

    @Test
    public void testRenderedFileIsGlobalAndUsesTheCuratedSignatures() {
        IValueType<?> itemstack = mockValueType("itemstack");
        IValueTranslatorRegistry registry = mock(IValueTranslatorRegistry.class);
        IValueTranslator translator = adapter("id_item");
        when(registry.getValueTypeTranslator(raw(itemstack))).thenReturn(translator);
        List<TypingsDeclarations.Declaration> declarations =
                TypingsDeclarations.collect(Collections.singletonList(itemstack), registry,
                        Collections.emptyList());

        Map<String, IOperator> globals = new LinkedHashMap<>();
        globals.put("listGet", operator("list.get",
                new IValueType[]{ValueTypes.LIST, ValueTypes.INTEGER}, ValueTypes.CATEGORY_ANY));
        globals.put("itemstackSize", operator("itemstack.size",
                new IValueType[]{raw(itemstack)}, ValueTypes.INTEGER));

        Map<String, IOperator> methods = new LinkedHashMap<>();
        methods.put("inventory", operator("itemstack.inventory",
                new IValueType[]{raw(itemstack)}, ValueTypes.LIST));

        String file = new TypeScriptTypingsGenerator().render(declarations, globals,
                valueType -> methods);

        assertTrue(file, file.contains("declare var idContext: Context;"));
        assertTrue(file, file.contains("interface Operations {"));
        assertFalse("A generated declaration file must not be a module: scripts can not import",
                file.contains("export "));
        assertTrue(file, file.contains("type Operator = (...args: any[]) => any;"));
        assertTrue(file, file.contains("interface NbtCompound {"));
        assertTrue(file, file.contains("interface ItemStackIdentity {"));

        // The curated signature replaces the registry's `(any[], number) => any`.
        assertTrue(file, file.contains("listGet: <T>(list: T[], index: number) => T;"));
        // Parameter names come from the value types, not arg0/arg1.
        assertTrue(file, file.contains("itemstackSize: (itemstack: ValueItemstack) => number;"));

        // The object method lost its receiver and kept the element type.
        assertTrue(file, file.contains("interface ValueItemstack {"));
        assertTrue(file, file.contains("id_item: ItemStackIdentity;"));
        assertTrue(file, file.contains("inventory: () => ValueItemstack[];"));
    }

    @Test
    public void testGeneratedFileNeverReferencesAnUndeclaredInterface() {
        // An object type without a translator must not leak a `ValueMystery`
        // reference into a signature: that would make the whole file invalid.
        IValueType<?> mystery = mockValueType("mystery");
        IValueTranslatorRegistry registry = mock(IValueTranslatorRegistry.class);
        when(registry.getValueTypeTranslator(raw(mystery))).thenReturn(null);
        List<TypingsDeclarations.Declaration> declarations =
                TypingsDeclarations.collect(Collections.singletonList(mystery), registry,
                        Collections.emptyList());

        Map<String, IOperator> globals = new LinkedHashMap<>();
        globals.put("mysteryOp", operator("mystery.op",
                new IValueType[]{mystery}, ValueTypes.BOOLEAN));

        String file = new TypeScriptTypingsGenerator().render(declarations, globals,
                valueType -> Collections.emptyMap());

        assertTrue(file, file.contains("mysteryOp: (value: any) => boolean;"));
        assertFalse(file, file.contains("ValueMystery"));
    }

    private static IValueType<?> mockValueType(String typeName) {
        IValueType<?> valueType = mock(IValueType.class);
        when(valueType.isObject()).thenReturn(true);
        when(valueType.isCategory()).thenReturn(false);
        when(valueType.getTypeName()).thenReturn(typeName);
        when(valueType.getTranslationKey()).thenReturn("valuetype.test." + typeName);
        return valueType;
    }

    private static IValueTranslator adapter(String memberKey) {
        ValueTranslatorObjectAdapter<?> adapter = mock(ValueTranslatorObjectAdapter.class);
        when(adapter.getKey()).thenReturn(memberKey);
        return adapter;
    }

    private static IOperator operator(String name, IValueType[] inputs, IValueType output) {
        IOperator operator = mock(IOperator.class);
        when(operator.getTranslationKey()).thenReturn("operator.operators.test." + name + ".name");
        when(operator.getInputTypes()).thenReturn(inputs);
        when(operator.getOutputType()).thenReturn(output);
        return operator;
    }

    @SuppressWarnings("unchecked")
    private static IValueType raw(IValueType<?> valueType) {
        return valueType;
    }

}
