package com.mpp.integratedscripting.core.network;

import org.cyclops.integrateddynamics.api.network.INetwork;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.capability.network.ScriptingNetworkConfig;

import javax.annotation.Nullable;

/**
 * Helpers to access the scripting data and the scripting network capability.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §5.8.3): capabilities are
 * nullable instead of {@code LazyOptional}, so every call site does a
 * {@code null} check.</p>
 *
 * @author rubensworks
 */
public final class ScriptingNetworkHelpers {

    private ScriptingNetworkHelpers() {
    }

    /**
     * Get the scripting data.
     * @return The scripting data, or null if no world is loaded (client side).
     */
    @Nullable
    public static IScriptingData getScriptingData() {
        IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
        return instance == null ? null : instance.scriptingData;
    }

    /**
     * Get the scripting network capability of a network.
     * @param network The network, may be null.
     * @return The scripting network, or null if it is not available.
     */
    @Nullable
    public static IScriptingNetwork getScriptingNetwork(@Nullable INetwork network) {
        if (network == null || ScriptingNetworkConfig.CAPABILITY == null) {
            return null;
        }
        if (!network.hasCapability(ScriptingNetworkConfig.CAPABILITY)) {
            return null;
        }
        return network.getCapability(ScriptingNetworkConfig.CAPABILITY);
    }

}
