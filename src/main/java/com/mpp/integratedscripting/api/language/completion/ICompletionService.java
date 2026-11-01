package com.mpp.integratedscripting.api.language.completion;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Turns a {@link CompletionContext} into the final candidate list.
 *
 * <p>The service is responsible for aggregating contributors, deduplicating,
 * sorting and truncating; contributors only supply data. The default
 * implementation ({@code CompositeCompletionService}) merges an optional
 * language-provided service with every registered contributor.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ICompletionService {

    /**
     * @return The characters that trigger completion while typing, in addition to
     *         an explicit request (Ctrl+Space).
     */
    default Set<Character> getTriggerCharacters() {
        return Collections.singleton('.');
    }

    /**
     * @return The maximum number of candidates returned by {@link #complete}.
     */
    default int getMaxItems() {
        return 100;
    }

    /**
     * Compute the candidates for one request.
     * @param context The request.
     * @return The final, sorted list; empty when there is nothing to offer.
     */
    List<CompletionItem> complete(CompletionContext context);

}
