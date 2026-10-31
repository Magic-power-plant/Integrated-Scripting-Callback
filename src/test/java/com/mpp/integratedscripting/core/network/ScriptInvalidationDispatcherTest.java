package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the invalidation lifecycle of one compiled script
 * (docs/AUDIT.md D1).
 *
 * <p>The 1.20 reference implementation kept the disk listener in a single field
 * and closed the Graal context on the first {@code removeInvalidationListeners()}
 * call. With several consumers per script (network cache plus every variable),
 * that closed the context of scripts that had not changed and left listeners
 * behind. These tests pin the replacement contract down:</p>
 * <ul>
 *   <li>every listener is notified exactly once, in reverse registration order;</li>
 *   <li>the script is released exactly once, and only on a real release;</li>
 *   <li>detaching one listener leaves the others registered.</li>
 * </ul>
 */
public class ScriptInvalidationDispatcherTest {

    private static class RecordingListener implements IScript.IInvalidateListener {

        private final String name;
        private final List<String> log;
        private int invalidations;

        private RecordingListener(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }

        @Override
        public void invalidate() {
            this.invalidations++;
            this.log.add(this.name);
        }

    }

    private static ScriptInvalidationDispatcher newDispatcher(List<String> releaseLog) {
        return new ScriptInvalidationDispatcher(() -> releaseLog.add("release"));
    }

    @Test
    public void testEveryListenerIsNotifiedOnceInReverseOrder() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = new ScriptInvalidationDispatcher(() -> log.add("release"));
        RecordingListener first = new RecordingListener("first", log);
        RecordingListener second = new RecordingListener("second", log);
        dispatcher.add(first);
        dispatcher.add(second);

        dispatcher.releaseAndNotify();

        assertEquals("release must happen exactly once, before the listeners",
                java.util.Arrays.asList("release", "second", "first"), log);
        assertEquals(1, first.invalidations);
        assertEquals(1, second.invalidations);
        assertTrue(dispatcher.isReleased());
    }

    @Test
    public void testReleaseAndNotifyIsIdempotent() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = newDispatcher(log);
        RecordingListener listener = new RecordingListener("listener", log);
        dispatcher.add(listener);

        dispatcher.releaseAndNotify();
        dispatcher.releaseAndNotify();

        assertEquals(1, listener.invalidations);
        assertEquals(java.util.Arrays.asList("release", "listener"), log);
    }

    @Test
    public void testDetachingOneListenerKeepsTheOthers() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = newDispatcher(log);
        RecordingListener variable = new RecordingListener("variable", log);
        RecordingListener network = new RecordingListener("network", log);
        dispatcher.add(network);
        dispatcher.add(variable);

        // This is what ScriptVariable#invalidate does: detach only itself.
        assertTrue(dispatcher.remove(variable));
        assertFalse("Removing the same listener twice must not detach the others",
                dispatcher.remove(variable));
        assertEquals(1, dispatcher.getListenerCount());

        dispatcher.releaseAndNotify();

        assertEquals(0, variable.invalidations);
        assertEquals(1, network.invalidations);
    }

    @Test
    public void testClearDoesNotReleaseTheScript() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = newDispatcher(log);
        RecordingListener listener = new RecordingListener("listener", log);
        dispatcher.add(listener);

        dispatcher.clear();

        assertEquals(0, dispatcher.getListenerCount());
        assertFalse("A script without listeners is still usable", dispatcher.isReleased());

        // A new consumer can register again, and the release still happens once.
        RecordingListener late = new RecordingListener("late", log);
        dispatcher.add(late);
        dispatcher.releaseAndNotify();

        assertEquals(0, listener.invalidations);
        assertEquals(1, late.invalidations);
        assertEquals(java.util.Arrays.asList("release", "late"), log);
    }

    @Test
    public void testListenerCanDetachItselfDuringNotification() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = newDispatcher(log);
        RecordingListener[] selfRemoving = new RecordingListener[1];
        selfRemoving[0] = new RecordingListener("self", log);
        IScript.IInvalidateListener listener = () -> {
            selfRemoving[0].invalidate();
            dispatcher.remove(selfRemoving[0]);
        };
        RecordingListener other = new RecordingListener("other", log);
        dispatcher.add(listener);
        dispatcher.add(other);

        dispatcher.releaseAndNotify();

        // Reverse order: the plain listener runs first, then the self-removing one.
        assertEquals(java.util.Arrays.asList("release", "other", "self"), log);
        assertEquals(0, dispatcher.getListenerCount());
    }

    @Test
    public void testAddAfterReleaseIsIgnored() {
        List<String> log = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcher = newDispatcher(log);
        dispatcher.releaseAndNotify();

        RecordingListener listener = new RecordingListener("late", log);
        dispatcher.add(listener);
        assertEquals(0, dispatcher.getListenerCount());
        assertEquals(0, listener.invalidations);
    }

    @Test
    public void testReleasingOneScriptDoesNotAffectAnother() {
        // This is the regression that matters most: editing script A must never
        // release the context of script B (each script owns its own dispatcher).
        List<String> logA = new ArrayList<>();
        List<String> logB = new ArrayList<>();
        ScriptInvalidationDispatcher dispatcherA = newDispatcher(logA);
        ScriptInvalidationDispatcher dispatcherB = newDispatcher(logB);
        RecordingListener listenerB = new RecordingListener("B", logB);
        dispatcherB.add(listenerB);

        dispatcherA.releaseAndNotify();

        assertTrue(dispatcherA.isReleased());
        assertFalse(dispatcherB.isReleased());
        assertEquals(0, listenerB.invalidations);
        assertTrue(logB.isEmpty());
    }

}
