package com.mpp.integratedscripting.capability.network;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import org.cyclops.cyclopscore.config.extendedconfig.CapabilityConfig;
import org.cyclops.cyclopscore.modcompat.capabilities.DefaultCapabilityStorage;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.core.network.ScriptingNetwork;

/**
 * Config for the scripting network capability.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §5.8.1): the capability is
 * registered through Cyclops' {@code ConfigurableType.CAPABILITY} and injected
 * into {@link #CAPABILITY} by Forge.</p>
 *
 * @author rubensworks
 */
public class ScriptingNetworkConfig extends CapabilityConfig<IScriptingNetwork> {

    public static ScriptingNetworkConfig _instance;

    @CapabilityInject(IScriptingNetwork.class)
    public static Capability<IScriptingNetwork> CAPABILITY = null;

    public ScriptingNetworkConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "scripting_network",
                "Scripting network capability.",
                IScriptingNetwork.class,
                new DefaultCapabilityStorage<IScriptingNetwork>(),
                ScriptingNetwork.class
        );
    }

}
