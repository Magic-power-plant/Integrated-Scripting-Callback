package com.mpp.integratedscripting.callback;

import com.google.common.collect.Maps;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import java.util.Map;

/**
 * Registry of {@link IObjectValueBridge}'s, keyed by identity of the object
 * value type (docs/PORTING-1.12.2.md §5.5.1).
 *
 * @author rubensworks
 */
public final class ObjectValueBridges {

    private static final Map<IValueType<?>, IObjectValueBridge<?>> BRIDGES = Maps.newIdentityHashMap();

    static {
        IObjectValueBridge<?> snbt = new SnbtObjectValueBridge<>();
        BRIDGES.put(ValueTypes.OBJECT_ITEMSTACK, snbt);
        BRIDGES.put(ValueTypes.OBJECT_FLUIDSTACK, snbt);
        BRIDGES.put(ValueTypes.OBJECT_INGREDIENTS, snbt);
        BRIDGES.put(ValueTypes.OBJECT_RECIPE, snbt);
        BRIDGES.put(ValueTypes.OBJECT_BLOCK, new BlockObjectValueBridge());
        BRIDGES.put(ValueTypes.OBJECT_ENTITY, new EntityObjectValueBridge());
    }

    private ObjectValueBridges() {
    }

    @SuppressWarnings("unchecked")
    public static <V extends IValue> IObjectValueBridge<V> get(ValueObjectTypeBase<V> valueType) {
        return (IObjectValueBridge<V>) BRIDGES.get(valueType);
    }

    /**
     * Register a bridge for an object value type that is added after this class
     * was initialised, such as the addressed memory type of this mod.
     * @param valueType The object value type.
     * @param bridge Its bridge.
     * @param <V> The value type.
     */
    public static <V extends IValue> void register(ValueObjectTypeBase<V> valueType, IObjectValueBridge<V> bridge) {
        BRIDGES.put(valueType, bridge);
    }

    /**
     * @return An unmodifiable view of the registered bridges, for diagnostics.
     */
    public static Map<IValueType<?>, IObjectValueBridge<?>> getBridges() {
        return java.util.Collections.unmodifiableMap(BRIDGES);
    }

}
