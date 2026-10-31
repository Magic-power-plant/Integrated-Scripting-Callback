package com.mpp.integratedscripting.evaluate.translation.translator;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import lombok.SneakyThrows;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.operator.CurriedOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.Variable;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyObject;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * A Graal proxy object for an {@link NBTTagCompound}.
 *
 * @author rubensworks
 */
public class NbtCompoundTagProxyObject implements ProxyObject {

    private final Context context;
    private final IEvaluationExceptionFactory exceptionFactory;
    private final NBTTagCompound tag;

    @Nullable
    private final Map<String, IOperator> methods;
    @Nullable
    private final IValue value;

    public NbtCompoundTagProxyObject(Context context, IEvaluationExceptionFactory exceptionFactory,
                                     NBTTagCompound tag, @Nullable Map<String, IOperator> methods,
                                     @Nullable IValue value) {
        this.context = context;
        this.exceptionFactory = exceptionFactory;
        this.tag = tag;
        this.methods = methods;
        this.value = value;
    }

    public NBTTagCompound getTag() {
        return tag;
    }

    @Nullable
    public IValue getValue() {
        return value;
    }

    @SneakyThrows
    @Override
    public Object getMember(String key) {
        if (methods != null) {
            IOperator operator = methods.get(key);
            if (operator != null) {
                CurriedOperator curriedOperator = new CurriedOperator(operator, new Variable<>(value));
                return ValueTranslators.REGISTRY.translateToGraal(context,
                        ValueTypeOperator.ValueOperator.of(curriedOperator), exceptionFactory);
            }
        }
        return ValueTranslators.TRANSLATOR_NBT.translateTag(context, tag.getTag(key), exceptionFactory);
    }

    @Override
    public Object getMemberKeys() {
        return tag.getKeySet().toArray();
    }

    @Override
    public boolean hasMember(String key) {
        return tag.hasKey(key) || (this.methods != null && this.methods.containsKey(key));
    }

    @SneakyThrows
    @Override
    public void putMember(String key, Value value) {
        IValue idValue = ValueTranslators.REGISTRY.translateFromGraal(context, value, exceptionFactory);
        NBTBase nbt = ValueTranslators.REGISTRY.translateToNbtTag(context, idValue, exceptionFactory);
        if (nbt == null) {
            tag.removeTag(key);
        } else {
            tag.setTag(key, nbt);
        }
    }

}
