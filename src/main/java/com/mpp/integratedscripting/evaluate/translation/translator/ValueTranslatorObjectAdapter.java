package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.callback.IObjectValueBridge;
import com.mpp.integratedscripting.callback.ObjectValueBridges;
import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import net.minecraft.nbt.NBTBase;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.util.Collections;
import java.util.Set;

/**
 * Translates ID object values to and from Graal proxy objects.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §6.20): object values are
 * converted through {@link IObjectValueBridge}, because
 * {@code ValueObjectTypeBase#serialize} returns a String in 1.12.2.</p>
 *
 * @author rubensworks
 */
public class ValueTranslatorObjectAdapter<V extends IValue> implements IValueTranslator<V> {

    private final String key;
    private final Set<String> keys;
    private final ValueObjectTypeBase<V> valueType;

    public ValueTranslatorObjectAdapter(String key, ValueObjectTypeBase<V> valueType) {
        this.key = key;
        this.keys = Collections.singleton(key);
        this.valueType = valueType;
    }

    public String getKey() {
        return key;
    }

    @Override
    public IValueType<?> getValueType() {
        return this.valueType;
    }

    @Override
    public boolean canHandleGraalValue(Value value) {
        if (value.isProxyObject()) {
            try {
                ValueObjectProxyObject<?> proxyObject = value.asProxyObject();
                return proxyObject.getValue() != null && proxyObject.getValue().getType() == this.valueType;
            } catch (ClassCastException e) {
                // Ignore error and fall through to the member-key check.
            }
        }
        return value.getMemberKeys().equals(this.keys);
    }

    @Override
    public boolean canTranslateNbt() {
        return true;
    }

    @Override
    public Value translateToGraal(Context context, V value, IEvaluationExceptionFactory exceptionFactory) {
        return context.asValue(new ValueObjectProxyObject<>(
                context, exceptionFactory, this.key, OperatorBindings.getMethods(this.valueType),
                this.valueType, value));
    }

    @SuppressWarnings("unchecked")
    @Override
    public V translateFromGraal(Context context, Value value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        // Unwrap the value if it was translated in the opposite direction before.
        if (value.isProxyObject()) {
            try {
                ValueObjectProxyObject<?> proxyObject = value.asProxyObject();
                return (V) proxyObject.getValue();
            } catch (ClassCastException e) {
                // Fallback to the generic case below.
            }
        }

        Value member = value.getMember(this.key);
        IValue memberValue = ValueTranslators.REGISTRY.translateFromGraal(context, member, exceptionFactory);
        NBTBase tag = ValueTranslators.REGISTRY.translateToNbtTag(context, memberValue, exceptionFactory);
        IObjectValueBridge<V> bridge = ObjectValueBridges.get(this.valueType);
        if (bridge == null) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.unknown_from_graal",
                    this.valueType.getTranslationKey()));
        }
        return bridge.fromNbt(this.valueType, tag, exceptionFactory);
    }

    @Override
    public NBTBase translateToNbt(Context context, V value, IEvaluationExceptionFactory exceptionFactory)
            throws EvaluationException {
        IObjectValueBridge<V> bridge = ObjectValueBridges.get(this.valueType);
        if (bridge == null) {
            throw exceptionFactory.createError(L10NHelpers.localize(
                    "valuetype.integratedscripting.error.translation.unsupported_translateToNbt",
                    L10NHelpers.localize(value.getType().getTranslationKey()),
                    value.getType().toCompactString(value)));
        }
        return bridge.toNbt(this.valueType, value, exceptionFactory);
    }

}
