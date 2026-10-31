package com.mpp.integratedscripting.core.network;

import com.google.common.collect.Sets;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the network layer that does not need a running game: disk bookkeeping
 * and the variable cache (docs/PORTING-1.12.2.md §12, M2).
 */
public class ScriptingNetworkTest {

    @Test
    public void testDiskBookkeeping() {
        ScriptingNetwork network = new ScriptingNetwork();
        assertTrue(network.getDisks().isEmpty());

        network.addDisk(1);
        network.addDisk(2);
        network.addDisk(2);
        assertEquals(Sets.newHashSet(1, 2), network.getDisks());

        network.removeDisk(1);
        assertEquals(Sets.newHashSet(2), network.getDisks());
    }

    @Test
    public void testVariablesAreCachedPerDiskPathAndMember() {
        ScriptingNetwork network = new ScriptingNetwork();
        IVariable<?> variable = network.getVariable(1, Paths.get("main.js"), "foo");

        assertSame(variable, network.getVariable(1, Paths.get("main.js"), "foo"));
        assertNotSame(variable, network.getVariable(1, Paths.get("main.js"), "bar"));
        assertNotSame(variable, network.getVariable(2, Paths.get("main.js"), "foo"));
        assertNotSame(variable, network.getVariable(1, Paths.get("other.js"), "foo"));
    }

}
