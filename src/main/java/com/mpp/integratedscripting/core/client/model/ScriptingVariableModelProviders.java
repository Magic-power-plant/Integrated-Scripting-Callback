package com.mpp.integratedscripting.core.client.model;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.client.model.IVariableModelProviderRegistry;
import org.cyclops.integrateddynamics.core.client.model.SingleVariableModelProvider;
import com.mpp.Reference;

/**
 * Client-side variable card overlay models.
 *
 * <p>1.12.2 port of the 1.20 reference class (docs/PORTING-1.12.2.md §6.13).
 * The provider must be registered before model baking, which is why
 * {@link com.mpp.integratedscripting.proxy.ClientProxy} calls {@link #load()}
 * from {@code registerEventHooks()} (end of pre-initialisation).</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class ScriptingVariableModelProviders {

    public static final IVariableModelProviderRegistry REGISTRY =
            IntegratedDynamics._instance.getRegistryManager().getRegistry(IVariableModelProviderRegistry.class);

    public static final SingleVariableModelProvider SCRIPT = REGISTRY.addProvider(
            new SingleVariableModelProvider(new ResourceLocation(Reference.MOD_ID, "customoverlay/script")));

    public static void load() {
        // Static initialiser registers the provider; this method only makes the
        // call site explicit and mirrors the Integrated Dynamics convention.
    }

}
