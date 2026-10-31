package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.callback.IObjectValueBridge;
import com.mpp.integratedscripting.callback.ObjectValueBridges;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import lombok.SneakyThrows;
import net.minecraft.nbt.NBTBase;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.operator.CurriedOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.Variable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A Graal proxy object for ID object values. It exposes the identifying
 * {@code id_xxx} member, which is translated through the matching
 * {@link IObjectValueBridge}, plus every scoped operator as a method.
 *
 * @author rubensworks
 */
public class ValueObjectProxyObject<V extends IValue> implements ProxyObject {

    private final Context context;
    private final IEvaluationExceptionFactory exceptionFactory;
    private final ValueObjectTypeBase<V> valueType;
    private final V value;
    private final Map<String, IOperator> methods;
    private final String memberNbtKey;

    @Nullable
    private Value memberNbtValue;

    public ValueObjectProxyObject(Context context, IEvaluationExceptionFactory exceptionFactory,
                                  String memberNbtKey, Map<String, IOperator> methods,
                                  ValueObjectTypeBase<V> valueType, V value) {
        this.context = context;
        this.exceptionFactory = exceptionFactory;
        this.valueType = valueType;
        this.methods = methods;
        this.value = value;
        this.memberNbtKey = memberNbtKey;
    }

    public ValueObjectTypeBase<V> getValueType() {
        return valueType;
    }

    @Nullable
    public IValue getValue() {
        return value;
    }

    @SneakyThrows
    @Override
    public Object getMember(String key) {
        IOperator operator = methods.get(key);
        if (operator != null) {
            CurriedOperator curriedOperator = new CurriedOperator(operator, new Variable<>(value));
            return ValueTranslators.REGISTRY.translateToGraal(context,
                    ValueTypeOperator.ValueOperator.of(curriedOperator), exceptionFactory);
        }
        if (key.equals(this.memberNbtKey)) {
            if (this.memberNbtValue == null) {
                IObjectValueBridge<V> bridge = ObjectValueBridges.get(this.valueType);
                NBTBase tag = bridge == null
                        ? new net.minecraft.nbt.NBTTagCompound()
                        : bridge.toNbt(this.valueType, this.value, exceptionFactory);
                this.memberNbtValue = ValueTranslators.TRANSLATOR_NBT.translateTag(
                        context, tag, exceptionFactory);
            }
            return this.memberNbtValue;
        }
        return null;
    }

    @Override
    public Object getMemberKeys() {
        return new String[]{ this.memberNbtKey };
    }

    @Override
    public boolean hasMember(String key) {
        return key.equals(this.memberNbtKey) || (this.methods != null && this.methods.containsKey(key));
    }

    @SneakyThrows
    @Override
    public void putMember(String key, Value value) {
        throw exceptionFactory.createError("valuetype.integratedscripting.error.translation.proxyobject_putMember",
                key, this.valueType.getTypeName());
    }

}
