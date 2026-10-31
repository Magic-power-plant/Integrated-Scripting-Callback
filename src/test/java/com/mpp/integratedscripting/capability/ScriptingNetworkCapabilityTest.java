package com.mpp.integratedscripting.capability;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.cyclops.integrateddynamics.api.network.AttachCapabilitiesEventNetwork;
import com.mpp.Reference;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that the scripting network capability is attached to ID networks
 * under the documented name (decision A10, docs/PORTING-1.12.2.md §5.8.2).
 */
public class ScriptingNetworkCapabilityTest {

    @Test
    public void testScriptingNetworkCapabilityIsAttached() {
        AttachCapabilitiesEventNetwork event = new AttachCapabilitiesEventNetwork(null);

        new ScriptingNetworkCapabilityConstructors().onNetworkLoad(event);

        Map<ResourceLocation, ICapabilityProvider> capabilities = event.getCapabilities();
        assertEquals(1, capabilities.size());
        assertTrue(capabilities.containsKey(new ResourceLocation(Reference.MOD_ID, "scripting_network")));
    }

}
