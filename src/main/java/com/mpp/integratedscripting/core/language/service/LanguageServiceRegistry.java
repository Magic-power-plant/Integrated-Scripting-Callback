package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;
import com.mpp.integratedscripting.api.language.service.ILanguageServiceRegistry;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKey;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKeys;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKey;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.core.language.LanguageHandlerRegistry;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Default {@link ILanguageServiceRegistry}: an in-memory store of service
 * providers and contributor lists, with a cache of resolved immutable bundles.
 *
 * <p>Registration invalidates that language's cached bundle, so a registration
 * that happens late (or a test that registers and then asserts) is always
 * visible on the next {@link #getServices(LanguageId)}. Reads are synchronized
 * because the client render thread resolves file languages while the game
 * start-up still registers.</p>
 *
 * @author rubensworks
 */
public class LanguageServiceRegistry implements ILanguageServiceRegistry {

    private static final LanguageServiceRegistry INSTANCE = new LanguageServiceRegistry();

    private final Map<LanguageId, Map<LanguageServiceKey<?>, Object>> providers = new HashMap<>();
    private final Map<LanguageId, Map<LanguageContributorKey<?>, List<Contribution>>> contributions
            = new HashMap<>();
    private final Map<LanguageId, ILanguageServices> cache = new HashMap<>();

    @Nullable
    private ILanguageHandlerRegistry handlerRegistry;

    private long sequence;

    /**
     * @return The unique game-wide instance; unit tests may construct their own.
     */
    public static LanguageServiceRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Wire the handler registry used by {@link #getServices(Path)}.
     * @param handlerRegistry The handler registry.
     */
    public synchronized void attachHandlerRegistry(@Nullable ILanguageHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
        this.cache.clear();
    }

    @Override
    public synchronized <T> void registerService(LanguageId language, LanguageServiceKey<T> key,
                                                 T service) {
        if (language == null || key == null) {
            throw new IllegalArgumentException("A language and a service key are required.");
        }
        Map<LanguageServiceKey<?>, Object> services = this.providers.get(language);
        if (services == null) {
            services = new HashMap<>();
            this.providers.put(language, services);
        }
        services.put(key, key.cast(service));
        invalidate(language);
    }

    @Override
    public synchronized <T> void registerContributor(LanguageId language,
                                                     LanguageContributorKey<T> key,
                                                     int priority, T contributor) {
        if (language == null || key == null) {
            throw new IllegalArgumentException("A language and a contributor key are required.");
        }
        Map<LanguageContributorKey<?>, List<Contribution>> byKey = this.contributions.get(language);
        if (byKey == null) {
            byKey = new HashMap<>();
            this.contributions.put(language, byKey);
        }
        List<Contribution> list = byKey.get(key);
        if (list == null) {
            list = new ArrayList<>();
            byKey.put(key, list);
        }
        list.add(new Contribution(priority, this.sequence++, key.cast(contributor)));
        invalidate(language);
    }

    @Override
    public synchronized ILanguageServices getServices(LanguageId language) {
        ILanguageServices services = this.cache.get(language);
        if (services == null) {
            services = build(language);
            this.cache.put(language, services);
        }
        return services;
    }

    @Nullable
    @Override
    public ILanguageServices getServices(Path path) {
        if (path == null) {
            return null;
        }
        ILanguageHandlerRegistry handlers;
        synchronized (this) {
            handlers = this.handlerRegistry;
        }
        if (handlers == null) {
            handlers = LanguageHandlerRegistry.getInstance();
        }
        ILanguageHandler handler = handlers.getProvider(path);
        return handler == null ? null : getServices(handler.getLanguageId());
    }

    @Override
    public synchronized Collection<LanguageId> getLanguages() {
        TreeSet<LanguageId> languages = new TreeSet<>();
        languages.addAll(this.providers.keySet());
        languages.addAll(this.contributions.keySet());
        return Collections.unmodifiableCollection(languages);
    }

    /**
     * Test/acceptance hook: forget every registration and every cached bundle.
     */
    public synchronized void clear() {
        this.providers.clear();
        this.contributions.clear();
        this.cache.clear();
        this.sequence = 0;
    }

    private void invalidate(LanguageId language) {
        this.cache.remove(language);
    }

    private ILanguageServices build(LanguageId language) {
        Map<LanguageServiceKey<?>, Object> services = new HashMap<>();
        Map<LanguageServiceKey<?>, Object> registered = this.providers.get(language);
        if (registered != null) {
            services.putAll(registered);
        }

        Map<LanguageContributorKey<?>, List<Object>> contributorValues = new HashMap<>();
        Map<LanguageContributorKey<?>, List<Contribution>> byKey = this.contributions.get(language);
        if (byKey != null) {
            for (Map.Entry<LanguageContributorKey<?>, List<Contribution>> entry : byKey.entrySet()) {
                List<Contribution> ordered = new ArrayList<>(entry.getValue());
                Collections.sort(ordered);
                List<Object> values = new ArrayList<>(ordered.size());
                for (Contribution contribution : ordered) {
                    values.add(contribution.value);
                }
                contributorValues.put(entry.getKey(), Collections.unmodifiableList(values));
            }
        }

        // Completion is the only aggregated service: the language's own service
        // (if any) and every contributor are merged into one composite, so adding
        // a contributor never requires replacing the language's completion service.
        Object explicitCompletion = services.get(LanguageServiceKeys.COMPLETION);
        List<ICompletionService> delegates = new ArrayList<>();
        int maxItems = 100;
        if (explicitCompletion instanceof ICompletionService) {
            ICompletionService service = (ICompletionService) explicitCompletion;
            delegates.add(service);
            maxItems = service.getMaxItems();
        }
        List<ICompletionContributor> completionContributors = new ArrayList<>();
        List<Object> rawContributors = contributorValues.get(LanguageContributorKeys.COMPLETION);
        if (rawContributors != null) {
            for (Object raw : rawContributors) {
                completionContributors.add(LanguageContributorKeys.COMPLETION.cast(raw));
            }
        }
        services.put(LanguageServiceKeys.COMPLETION,
                new CompositeCompletionService(delegates, completionContributors, maxItems));

        return new LanguageServices(language, services, contributorValues);
    }

    /**
     * One contributed value with its ordering weight and registration order.
     */
    private static final class Contribution implements Comparable<Contribution> {

        private final int priority;
        private final long sequence;
        private final Object value;

        private Contribution(int priority, long sequence, Object value) {
            this.priority = priority;
            this.sequence = sequence;
            this.value = value;
        }

        @Override
        public int compareTo(Contribution other) {
            if (priority != other.priority) {
                // Higher priority first.
                return priority > other.priority ? -1 : 1;
            }
            return Long.compare(sequence, other.sequence);
        }

    }

}
