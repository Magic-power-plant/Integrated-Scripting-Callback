package com.mpp.integratedscripting.core.evaluate.operator;

import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.logicprogrammer.IConfigRenderPattern;
import org.cyclops.integrateddynamics.core.evaluate.operator.OperatorBase;
import org.cyclops.integrateddynamics.core.evaluate.operator.Operators;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeString.ValueString;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

/**
 * The operators of the addressed memory object type.
 *
 * <ul>
 *   <li>{@code get(memory, address)} returns the value stored at an address.</li>
 *   <li>{@code set(memory, address, value)} stores a value at an address and
 *       returns it.</li>
 * </ul>
 *
 * <p>Both are scoped operators: their first input is the addressed memory value
 * type, which is what makes {@code OperatorBindings} expose them as methods
 * named {@code get} and {@code set} on memory objects, so scripts can write
 * {@code memory.get("a")} and {@code memory.set("a", 42)}.</p>
 *
 * <p>Reading and writing always target the memory's own storage: a variable card
 * in an address slot is the memory's output (it receives the stored value), not
 * the source of the value. {@code set} also updates the card of that slot.</p>
 *
 * @author rubensworks
 */
public final class AddressedMemoryOperators {

    public static IOperator GET;
    public static IOperator SET;

    private static boolean loaded = false;

    private AddressedMemoryOperators() {
    }

    /**
     * Register the operators exactly once. Must run after
     * {@link AddressedMemoryValues#load()}.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        final IValueType memoryType = AddressedMemoryValues.getType();

        GET = Operators.REGISTRY.register(new AddressedMemoryOperator("get", "get",
                new IValueType[]{memoryType, ValueTypes.STRING}, ValueTypes.CATEGORY_ANY,
                (OperatorBase.IFunction) variables -> {
                    TileAddressedMemory memory = requireMemory(variables);
                    return memory.readValue(requireAddress(addressOf(variables)));
                },
                IConfigRenderPattern.PREFIX_2));

        SET = Operators.REGISTRY.register(new AddressedMemoryOperator("set", "set",
                new IValueType[]{memoryType, ValueTypes.STRING, ValueTypes.CATEGORY_ANY},
                ValueTypes.CATEGORY_ANY,
                (OperatorBase.IFunction) variables -> {
                    TileAddressedMemory memory = requireMemory(variables);
                    IValue value = variables.getValue(2, ValueTypes.CATEGORY_ANY);
                    return memory.writeValue(requireAddress(addressOf(variables)), value);
                },
                IConfigRenderPattern.PREFIX_3));
    }

    private static TileAddressedMemory requireMemory(OperatorBase.SafeVariablesGetter variables)
            throws EvaluationException {
        ValueAddressedMemory value = variables.getValue(0, AddressedMemoryValues.getType());
        TileAddressedMemory memory = value.getMemory();
        if (memory == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.memory_missing"));
        }
        return memory;
    }

    private static String addressOf(OperatorBase.SafeVariablesGetter variables) throws EvaluationException {
        ValueString address = variables.getValue(1, ValueTypes.STRING);
        return address.getRawValue();
    }

    /**
     * @param address A script-provided address.
     * @return The zero-based address index.
     * @throws EvaluationException When the address is not one of a to p.
     */
    public static int requireAddress(String address) throws EvaluationException {
        int index = TileAddressedMemory.getAddressIndex(address);
        if (index < 0) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.address_invalid", String.valueOf(address)));
        }
        return index;
    }

}

