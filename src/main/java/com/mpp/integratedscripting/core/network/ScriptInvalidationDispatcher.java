package com.mpp.integratedscripting.core.network;

import com.google.common.collect.Lists;
import com.mpp.integratedscripting.api.network.IScript;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Owns the invalidation listeners and the release action of one compiled script.
 *
 * <p>1.12.2 port fix (docs/AUDIT.md D1). One compiled script instance is shared
 * by the network's script cache and by every variable that resolves a member of
 * it, so several consumers register on the same instance. The reference
 * implementation stored the disk listener in a single field, which meant that</p>
 * <ul>
 *   <li>each additional consumer replaced the previous consumer's callback,</li>
 *   <li>each additional consumer registered an extra disk listener that
 *       {@link IScript#removeInvalidationListeners()} could not remove, and</li>
 *   <li>closing the Graal context on the first removal broke every other
 *       consumer of the same script, including scripts that had not changed at
 *       all.</li>
 * </ul>
 *
 * <p>This dispatcher keeps a listener list, notifies each listener exactly once
 * in reverse registration order (the last registered consumer is the most
 * specific one, so it is invalidated first), and releases the compiled script
 * exactly once, only when the script file itself changed.</p>
 *
 * @author rubensworks
 */
final class ScriptInvalidationDispatcher {

    private final List<IScript.IInvalidateListener> listeners = Lists.newArrayList();
    private final Runnable onReleased;
    private boolean released;

    /**
     * @param onReleased Releases the compiled script: unregisters the disk
     *                   listener and closes the Graal context. Invoked at most once.
     */
    ScriptInvalidationDispatcher(Runnable onReleased) {
        this.onReleased = onReleased;
    }

    /**
     * @param listener The listener to add. Ignored once the script was released.
     */
    synchronized void add(@Nullable IScript.IInvalidateListener listener) {
        if (listener != null && !this.released) {
            this.listeners.add(listener);
        }
    }

    /**
     * @param listener The listener to remove.
     * @return If the listener was registered.
     */
    synchronized boolean remove(@Nullable IScript.IInvalidateListener listener) {
        return listener != null && this.listeners.remove(listener);
    }

    /**
     * Remove every listener without releasing the script. A released script is
     * unusable, but a script without listeners is merely no longer observed.
     */
    synchronized void clear() {
        this.listeners.clear();
    }

    /**
     * @return If the compiled script was released (its Graal context is closed).
     */
    synchronized boolean isReleased() {
        return this.released;
    }

    /**
     * @return The number of registered listeners. Diagnostics and tests only.
     */
    synchronized int getListenerCount() {
        return this.listeners.size();
    }

    /**
     * Release the compiled script and notify every registered listener exactly
     * once, in reverse registration order.
     *
     * <p>Releasing before notifying guarantees that no listener can fetch the
     * closed value again: the network listener runs last and removes the script
     * from its cache, so the next evaluation compiles a fresh script.</p>
     */
    void releaseAndNotify() {
        List<IScript.IInvalidateListener> snapshot;
        synchronized (this) {
            if (this.released) {
                return;
            }
            this.released = true;
            snapshot = Lists.newArrayList(this.listeners);
            this.listeners.clear();
        }
        try {
            this.onReleased.run();
        } finally {
            for (int i = snapshot.size() - 1; i >= 0; i--) {
                snapshot.get(i).invalidate();
            }
        }
    }

}
