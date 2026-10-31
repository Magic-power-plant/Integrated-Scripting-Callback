package com.mpp.integratedscripting.callback;

import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.core.network.PartNetwork;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the 1.12.2 {@link IPartNetwork} to {@code INetwork} bridge
 * (docs/PORTING-1.12.2.md §5.3, decision C12).
 */
public class PartNetworkBridgeTest {

    @Test
    public void testMissingPartNetwork() {
        assertNull(PartNetworkBridge.getNetwork(null));
    }

    @Test
    public void testForeignPartNetwork() {
        assertNull(PartNetworkBridge.getNetwork(mock(IPartNetwork.class)));
    }

    @Test
    public void testPartNetworkExposesItsNetwork() {
        PartNetwork partNetwork = mock(PartNetwork.class);
        INetwork network = mock(INetwork.class);
        when(partNetwork.getNetwork()).thenReturn(network);

        assertSame(network, PartNetworkBridge.getNetwork(partNetwork));
    }

    @Test
    public void testExplicitRegistrationIsUsedWhenTheFieldIsUnset() {
        // Integrated Dynamics 1.12.2-1.1.11 never sets PartNetwork#network, so the
        // capability listener registers the pairing explicitly (see M4 notes).
        PartNetwork partNetwork = mock(PartNetwork.class);
        when(partNetwork.getNetwork()).thenReturn(null);
        INetwork network = mock(INetwork.class);

        PartNetworkBridge.register(partNetwork, network);

        assertSame(network, PartNetworkBridge.getNetwork(partNetwork));
    }

    @Test
    public void testExplicitRegistrationWorksForForeignPartNetworks() {
        IPartNetwork partNetwork = mock(IPartNetwork.class);
        INetwork network = mock(INetwork.class);

        PartNetworkBridge.register(partNetwork, network);

        assertSame(network, PartNetworkBridge.getNetwork(partNetwork));
    }

}
