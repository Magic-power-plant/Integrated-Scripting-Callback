package com.mpp.integratedscripting.callback;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for the 1.12.2 operator name derivation (docs/PORTING-1.12.2.md §5.4).
 */
public class OperatorBindingsTest {

    @Test
    public void testGlobalAndMethodNames() {
        assertNames(
                "operator.operators.integrateddynamics.itemstack.stackable.name",
                "itemstackStackable", "stackable");
        assertNames(
                "operator.operators.integrateddynamics.itemstack.strength.name",
                "itemstackStrength", "strength");
        assertNames(
                "operator.operators.integrateddynamics.block.is_plantable.name",
                "blockIsPlantable", "isPlantable");
        assertNames(
                "operator.operators.integrateddynamics.arithmetic.addition.name",
                "arithmeticAddition", "addition");
        assertNames(
                "operator.operators.integrateddynamics.nbt.get_tag.name",
                "nbtGetTag", "getTag");
        assertNames(
                "operator.operators.integrateddynamics.list.length.name",
                "listLength", "length");
    }

    @Test
    public void testMultiPartNames() {
        assertNames(
                "operator.operators.integrateddynamics.any.b.c.name",
                "anyBC", "bC");
    }

    @Test
    public void testSanitize() {
        assertEquals("abc", OperatorBindings.sanitize("abc"));
        assertEquals("op_", OperatorBindings.sanitize(""));
        assertEquals("op_1abc", OperatorBindings.sanitize("1abc"));
        assertEquals("op_toString", OperatorBindings.sanitize("toString"));
        assertEquals("op___proto__", OperatorBindings.sanitize("__proto__"));
        assertEquals("op_hasOwnProperty", OperatorBindings.sanitize("hasOwnProperty"));
    }

    @Test
    public void testNonStandardTranslationKeyHasStableFallback() {
        // Non-ID operators may not follow the operator.operators.* convention.
        assertEquals("generalBar", OperatorBindings.globalNameFor("foo.bar"));
        assertEquals("bar", OperatorBindings.methodNameFor("foo.bar"));
    }

    private static void assertNames(String translationKey, String globalName, String methodName) {
        assertEquals("global name of " + translationKey,
                globalName, OperatorBindings.globalNameFor(translationKey));
        assertEquals("method name of " + translationKey,
                methodName, OperatorBindings.methodNameFor(translationKey));
    }

}
