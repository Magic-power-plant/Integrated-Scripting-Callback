package com.mpp.integratedscripting.api.language.completion;

/**
 * Sink a completion contributor writes its candidates to.
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface CompletionCollector {

    /**
     * Add one candidate. Implementations ignore {@code null} and may deduplicate
     * later; a contributor does not have to sort.
     * @param item The candidate.
     */
    void add(CompletionItem item);

}
