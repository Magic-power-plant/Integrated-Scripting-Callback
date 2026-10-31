package com.mpp.integratedscripting.api.network;

import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import javax.annotation.Nullable;

/**
 * A script that contains executable members.
 *
 * <p>Ported from the 1.20 reference implementation; API unchanged (level A in
 * docs/PORTING-1.12.2.md §6.5).</p>
 *
 * @author rubensworks
 */
public interface IScript {

    /**
     * Get the given script member.
     * @param memberName A member name.
     * @return The member, or null if it does not exist.
     * @throws EvaluationException If the underlying script could not be evaluated.
     */
    @Nullable
    public IScriptMember getMember(String memberName) throws EvaluationException;

    /**
     * Register a listener that will be invoked when this script gets invalidated.
     * @param listener A listener for invalidations.
     */
    public void addInvalidationListener(IInvalidateListener listener);

    /**
     * Remove one previously registered listener.
     *
     * <p>1.12.2 port addition (docs/AUDIT.md D1): one compiled script is shared by
     * the network's script cache and by every variable that resolves a member of
     * it, so a consumer must be able to detach itself without detaching the
     * others. The reference implementation only had "remove all", which made the
     * first invalidated variable unregister the network's own listener.</p>
     *
     * @param listener The listener to remove; ignored when it is not registered.
     */
    public void removeInvalidationListener(IInvalidateListener listener);

    /**
     * Remove all registered invalidation listeners.
     */
    public void removeInvalidationListeners();

    public interface IInvalidateListener {

        /**
         * Called when a script was invalidated.
         */
        public void invalidate();

    }

}
