package com.mpp.integratedscripting.core.evaluate.value;

import com.mpp.integratedscripting.callback.AddressedMemoryObjectValueBridge;
import com.mpp.integratedscripting.callback.ObjectValueBridges;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import javax.annotation.Nullable;

/**
 * Holder for the single {@code addressed_memory} value type instance.
 *
 * <p>The type is registered into Integrated Dynamics' value type registry during
 * pre-initialisation, i.e. before the operator registry and the value
 * translators are built, so operators and script translations can refer to it.
 * The registration is idempotent because the mod can be reloaded in a
 * development environment.</p>
 *
 * @author rubensworks
 */
public final class AddressedMemoryValues {

    @Nullable
    private static ValueTypeAddressedMemory type;

    private AddressedMemoryValues() {
    }

    /**
     * Register the value type and its object bridge exactly once.
     */
    public static synchronized void load() {
        if (type != null) {
            return;
        }
        type = ValueTypes.REGISTRY.register(new ValueTypeAddressedMemory());
        ObjectValueBridges.register(type, new AddressedMemoryObjectValueBridge());
    }

    /**
     * @return The registered value type.
     * @throws IllegalStateException When {@link #load()} has not run yet.
     */
    public static ValueTypeAddressedMemory getType() {
        ValueTypeAddressedMemory valueType = type;
        if (valueType == null) {
            throw new IllegalStateException("The addressed memory value type was not registered yet.");
        }
        return valueType;
    }

    /**
     * @param position The memory block position, or null for the default value.
     * @return A value of this type pointing at that block.
     */
    public static ValueAddressedMemory of(@Nullable DimPos position) {
        return ValueAddressedMemory.of(getType(), position);
    }

    /**
     * @return The registered instance, or null when it was not registered yet.
     */
    @Nullable
    public static ValueTypeAddressedMemory getTypeOrNull() {
        return type;
    }

}
