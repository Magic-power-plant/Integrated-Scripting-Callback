package com.mpp.integratedscripting.capability;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.cyclops.cyclopscore.modcompat.capabilities.DefaultCapabilityProvider;
import org.cyclops.integrateddynamics.api.network.AttachCapabilitiesEventNetwork;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.capability.network.PartNetworkConfig;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.callback.PartNetworkBridge;
import com.mpp.integratedscripting.capability.network.ScriptingNetworkConfig;
import com.mpp.integratedscripting.core.network.ScriptingNetwork;

/**
 * Attaches a {@link ScriptingNetwork} to every Integrated Dynamics network.
 *
 * <p>1.12.2 keeps a single Forge event bus; ID's {@code Network} posts
 * {@link AttachCapabilitiesEventNetwork} on it while gathering capabilities
 * (verified in the 1.12.2 ID jar, see docs/MILESTONES.md M2).</p>
 *
 * <p>The listener runs at {@link EventPriority#LOWEST} so that ID's own
 * {@code part_network} capability has already been added to the event when it
 * runs. The part network / network pairing is then registered in
 * {@link PartNetworkBridge}: ID 1.12.2-1.1.11 never fills in
 * {@code PartNetwork#network} itself, and the scripting variable facade only
 * receives the part network (see the M4 notes in docs/MILESTONES.md).</p>
 *
 * @author rubensworks
 */
public class ScriptingNetworkCapabilityConstructors {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNetworkLoad(AttachCapabilitiesEventNetwork event) {
        // The owning network is passed along so that a changed script can notify
        // the network's parts (VariableContentsUpdatedEvent), see M7 notes.
        ScriptingNetwork scriptingNetwork = new ScriptingNetwork(event.getNetwork());
        event.addCapability(
                new ResourceLocation(Reference.MOD_ID, "scripting_network"),
                new DefaultCapabilityProvider<IScriptingNetwork>(
                        () -> ScriptingNetworkConfig.CAPABILITY, scriptingNetwork)
        );

        // Register the owner of ID's part network, so that
        // IVariableFacade#getVariable(IPartNetwork) can reach this network's
        // scripting capability. Runs last, after ID added its part_network capability.
        if (PartNetworkConfig.CAPABILITY != null) {
            INetwork network = event.getNetwork();
            for (ICapabilityProvider provider : event.getCapabilities().values()) {
                IPartNetwork partNetwork = provider.getCapability(PartNetworkConfig.CAPABILITY, null);
                if (partNetwork != null) {
                    PartNetworkBridge.register(partNetwork, network);
                }
            }
        }
    }

}
