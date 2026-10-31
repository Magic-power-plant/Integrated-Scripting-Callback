package com.mpp.integratedscripting.proxy;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.acceptance.HarnessLoader;
import com.mpp.integratedscripting.core.client.model.ScriptingVariableModelProviders;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.cyclopscore.proxy.ClientProxyComponent;

/**
 * Proxy for the client side.
 *
 * <p>The acceptance harnesses are registered through {@link HarnessLoader} so
 * that this class never references them directly: they are excluded from the
 * release jar (docs/AUDIT.md D7, docs/MILESTONES.md M8), and a direct reference
 * would make the shipped jar fail to load this proxy.</p>
 *
 * @author rubensworks
 */
public class ClientProxy extends ClientProxyComponent {

    public ClientProxy() {
        super(new CommonProxy());
    }

    @Override
    public ModBase getMod() {
        return IntegratedScriptingCallback._instance;
    }

    @Override
    public void registerEventHooks() {
        super.registerEventHooks();
        // M6: the script overlay must be registered before the model loader bakes
        // the variable card models (docs/PORTING-1.12.2.md §6.13).
        ScriptingVariableModelProviders.load();
        // Development-only acceptance harnesses, each behind its own system property.
        HarnessLoader.registerIfEnabled(HarnessLoader.PROPERTY_GUI_SMOKE,
                HarnessLoader.GUI_SMOKE_HARNESS);
        HarnessLoader.registerIfEnabled(HarnessLoader.PROPERTY_RESOURCE_SMOKE,
                HarnessLoader.RESOURCE_SMOKE_HARNESS);
        HarnessLoader.registerIfEnabled(HarnessLoader.PROPERTY_CLIENT,
                HarnessLoader.CLIENT_ACCEPTANCE_HARNESS);
    }

}
