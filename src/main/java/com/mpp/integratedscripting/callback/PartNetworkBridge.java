package com.mpp.integratedscripting.callback;

import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.core.network.PartNetwork;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 1.12.2 specific bridge from an {@link IPartNetwork} back to its
 * {@link INetwork}.
 *
 * <p>The 1.20 reference {@code IVariableFacade} receives both the network and
 * the part network; 1.12.2 only receives the part network, while the scripting
 * network capability is attached to the {@code INetwork}. {@code PartNetwork}
 * declares a {@code getNetwork()}, but Integrated Dynamics 1.12.2-1.1.11 never
 * assigns that field: scanning the jar shows that only {@code PartNetwork}
 * itself references its {@code setNetwork} method. Relying on it alone would
 * therefore make every script variable card unreadable in-game (this was found
 * during the M4 acceptance run).</p>
 *
 * <p>The port solves this without a Mixin (decision D6): when a network
 * gathers its capabilities, {@link com.mpp.integratedscripting.capability.ScriptingNetworkCapabilityConstructors}
 * registers the part network / network pair here. The bridge still prefers the
 * field when a future Integrated Dynamics version populates it, and falls back
 * to the explicit registration otherwise.</p>
 *
 * @author rubensworks
 */
public final class PartNetworkBridge {

    /**
     * Weak keys: part networks are recreated with their network, and a stale
     * entry must never keep a dead network alive.
     */
    private static final Map<IPartNetwork, INetwork> NETWORKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private PartNetworkBridge() {
    }

    /**
     * Remember which network a part network belongs to.
     * @param partNetwork The part network.
     * @param network The owning network.
     */
    public static void register(IPartNetwork partNetwork, INetwork network) {
        NETWORKS.put(partNetwork, network);
    }

    /**
     * @param partNetwork The part network, may be null.
     * @return The network owning the part network, or null if it is not known.
     */
    @Nullable
    public static INetwork getNetwork(@Nullable IPartNetwork partNetwork) {
        if (partNetwork == null) {
            return null;
        }
        if (partNetwork instanceof PartNetwork) {
            INetwork network = ((PartNetwork) partNetwork).getNetwork();
            if (network != null) {
                return network;
            }
        }
        return NETWORKS.get(partNetwork);
    }

}
