package com.mpp.integratedscripting.evaluate.translation;

import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslatorRegistry;
import com.mpp.integratedscripting.api.evaluate.translation.ValueTranslatorRegisterEvent;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorBoolean;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorDouble;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorInteger;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorList;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorLong;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorNbt;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorObjectAdapter;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorOperator;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorString;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.evaluate.value.ValueTypeAddressedMemory;
import com.mpp.IntegratedScriptingCallback;
import net.minecraftforge.common.MinecraftForge;
import org.cyclops.cyclopscore.helper.MinecraftHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

/**
 * Holder for the value translator registry and its registrations.
 *
 * <p>Registration order is important (docs/PORTING-1.12.2.md §8.4): scalars,
 * list and operator first, then the object adapters, then NBT last so that NBT
 * does not swallow proxy objects.</p>
 *
 * @author rubensworks
 */
public class ValueTranslators {

    public static final IValueTranslatorRegistry REGISTRY = constructRegistry();
    public static ValueTranslatorNbt TRANSLATOR_NBT;

    private static boolean loaded = false;

    private static IValueTranslatorRegistry constructRegistry() {
        // This also allows this registry to be used outside of a minecraft environment.
        if (MinecraftHelpers.isModdedEnvironment()) {
            IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
            if (instance != null) {
                IValueTranslatorRegistry registry = instance.getRegistryManager()
                        .getRegistry(IValueTranslatorRegistry.class);
                if (registry != null) {
                    return registry;
                }
            }
        }
        return ValueTranslatorRegistry.getInstance();
    }

    /**
     * Register the built-in translators exactly once.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        // Raw value types
        REGISTRY.register(new ValueTranslatorBoolean());
        REGISTRY.register(new ValueTranslatorInteger());
        REGISTRY.register(new ValueTranslatorLong());
        REGISTRY.register(new ValueTranslatorDouble());
        REGISTRY.register(new ValueTranslatorString());
        REGISTRY.register(new ValueTranslatorList());
        REGISTRY.register(new ValueTranslatorOperator());

        // Object types
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_block", ValueTypes.OBJECT_BLOCK));
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_item", ValueTypes.OBJECT_ITEMSTACK));
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_entity", ValueTypes.OBJECT_ENTITY));
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_fluid", ValueTypes.OBJECT_FLUIDSTACK));
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_ingredients", ValueTypes.OBJECT_INGREDIENTS));
        REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_recipe", ValueTypes.OBJECT_RECIPE));
        // The addressed memory object type of this mod (preInit), so scripts can hold
        // a memory and call its get/set methods.
        ValueTypeAddressedMemory addressedMemory = AddressedMemoryValues.getTypeOrNull();
        if (addressedMemory != null) {
            REGISTRY.register(new ValueTranslatorObjectAdapter<>("id_memory", addressedMemory));
        }

        // Allow other mods to register translators (§5.10).
        if (MinecraftHelpers.isModdedEnvironment()) {
            MinecraftForge.EVENT_BUS.post(new ValueTranslatorRegisterEvent(REGISTRY));
        }

        // NBT has last priority
        REGISTRY.register(TRANSLATOR_NBT = new ValueTranslatorNbt());

        IntegratedScriptingCallback.clog("Registered " + ValueTranslatorRegistry.getInstance().getTranslators().size()
                + " value translators and " + com.mpp.integratedscripting.callback.OperatorBindings
                .getGlobalOperators().size() + " global operators.");
    }

}
