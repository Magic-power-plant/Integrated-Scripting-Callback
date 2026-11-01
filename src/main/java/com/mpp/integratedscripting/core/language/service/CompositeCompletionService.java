package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates completion contributors (and an optional language-specific
 * completion service) into one deduplicated, sorted and truncated list.
 *
 * <p>Deduplication is by inserted text: two candidates that would type the same
 * characters are the same completion, and the one with the higher priority (and,
 * on a tie, the one contributed first) wins. The remaining items are sorted by
 * {@link CompletionItem#compareTo(CompletionItem)} and cut to
 * {@link #getMaxItems()}.</p>
 *
 * @author rubensworks
 */
public final class CompositeCompletionService implements ICompletionService {

    private final List<ICompletionService> delegates;
    private final List<ICompletionContributor> contributors;
    private final int maxItems;

    /**
     * @param delegates Language-specific completion services, consulted after the
     *                  contributors; may be empty.
     * @param contributors The contributors, already ordered by priority.
     * @param maxItems The maximum number of candidates to return.
     */
    public CompositeCompletionService(@Nullable List<ICompletionService> delegates,
                                      @Nullable List<ICompletionContributor> contributors,
                                      int maxItems) {
        this.delegates = delegates == null ? Collections.<ICompletionService>emptyList() : delegates;
        this.contributors = contributors == null
                ? Collections.<ICompletionContributor>emptyList() : contributors;
        this.maxItems = Math.max(1, maxItems);
    }

    @Override
    public Set<Character> getTriggerCharacters() {
        Set<Character> triggers = new LinkedHashSet<>();
        for (ICompletionService delegate : this.delegates) {
            triggers.addAll(delegate.getTriggerCharacters());
        }
        if (triggers.isEmpty()) {
            triggers.add('.');
        }
        return Collections.unmodifiableSet(triggers);
    }

    @Override
    public int getMaxItems() {
        return maxItems;
    }

    @Override
    public List<CompletionItem> complete(CompletionContext context) {
        if (context == null) {
            return Collections.emptyList();
        }
        // Insertion text -> best candidate so far.
        Map<String, CompletionItem> best = new LinkedHashMap<>();

        for (ICompletionContributor contributor : this.contributors) {
            addAll(best, collect(contributor, context));
        }
        for (ICompletionService delegate : this.delegates) {
            addAll(best, delegate.complete(context));
        }

        List<CompletionItem> result = new ArrayList<>(best.values());
        Collections.sort(result);
        if (result.size() > this.maxItems) {
            result = new ArrayList<>(result.subList(0, this.maxItems));
        }
        return Collections.unmodifiableList(result);
    }

    private static List<CompletionItem> collect(ICompletionContributor contributor,
                                                CompletionContext context) {
        final List<CompletionItem> items = new ArrayList<>();
        contributor.contribute(context, new CompletionCollector() {
            @Override
            public void add(CompletionItem item) {
                if (item != null) {
                    items.add(item);
                }
            }
        });
        return items;
    }

    private static void addAll(Map<String, CompletionItem> best, @Nullable List<CompletionItem> items) {
        if (items == null) {
            return;
        }
        for (CompletionItem item : items) {
            if (item == null) {
                continue;
            }
            String key = item.getInsertText();
            CompletionItem previous = best.get(key);
            if (previous == null || item.getPriority() > previous.getPriority()) {
                best.put(key, item);
            }
        }
    }

}
