package com.mpp.integratedscripting.api.language.completion;

/**
 * One composable source of completion candidates.
 *
 * <p>Contributors are the extension point that satisfies the open/closed
 * requirement of the language service layer: a third-party mod can add
 * candidates to <em>any</em> language (including the built-in JavaScript one) by
 * registering a contributor for that language, without replacing its
 * {@link ICompletionService} and without touching the editor.</p>
 *
 * <p>Implementations must be stateless or thread safe: they are called from the
 * client render thread.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ICompletionContributor {

    /**
     * Add candidates for one request.
     * @param context The request.
     * @param out The collector to write candidates to.
     */
    void contribute(CompletionContext context, CompletionCollector out);

    /**
     * @return The ordering weight of this contributor; higher contributors are
     *         consulted first, and their duplicate candidates win.
     */
    default int getPriority() {
        return 0;
    }

}
