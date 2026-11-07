package com.mpp.integratedscripting.core.evaluate.operator;

import com.mpp.Reference;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.logicprogrammer.IConfigRenderPattern;
import org.cyclops.integrateddynamics.core.evaluate.operator.GeneralOperator;
import org.cyclops.integrateddynamics.core.evaluate.operator.OperatorBase;

/**
 * An operator of the {@code addressed_memory} category.
 *
 * <p>Its translation key becomes
 * {@code operator.operators.integratedscriptingcallback.addressed_memory.<name>.name},
 * which {@code OperatorBindings} turns into the global script name
 * {@code addressedMemory<Name>} and, because the first input is the memory
 * value type, into the method name {@code <name>} on memory objects.</p>
 *
 * @author rubensworks
 */
public class AddressedMemoryOperator extends GeneralOperator {

    public static final String UNLOCALIZED_TYPE = "addressed_memory";

    public AddressedMemoryOperator(String symbol, String operatorName, IValueType[] inputTypes,
                                   IValueType outputType, OperatorBase.IFunction function,
                                   IConfigRenderPattern renderPattern) {
        super(symbol, operatorName, inputTypes, outputType, function, renderPattern);
    }

    @Override
    public String getUnlocalizedType() {
        return UNLOCALIZED_TYPE;
    }

    @Override
    protected String getModId() {
        return Reference.MOD_ID;
    }

}
