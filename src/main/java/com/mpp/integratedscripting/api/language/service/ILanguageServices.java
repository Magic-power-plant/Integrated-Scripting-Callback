package com.mpp.integratedscripting.api.language.service;

import com.mpp.integratedscripting.api.language.LanguageId;

import java.util.Collections;
import java.util.List;

/**
 * The resolved, immutable services of one language: what an editor holds after
 * it resolved a file's language once.
 *
 * <p>Access is null safe on purpose: {@link #get(LanguageServiceKey)} falls back
 * to the key's default implementation and {@link #getContributors} to an empty
 * list, so the drawing and key-handling paths contain no lookups and no null
 * checks.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ILanguageServices {

    /**
     * @return The language these services belong to.
     */
    LanguageId getLanguageId();

    /**
     * @param key The requested service.
     * @param <T> The service type.
     * @return The registered service, or the key's default implementation when
     *         no provider was registered; never {@code null}.
     */
    <T> T get(LanguageServiceKey<T> key);

    /**
     * @param key The requested contributor list.
     * @param <T> The contributor type.
     * @return The contributors, highest priority first, registration order within
     *         one priority; never {@code null}.
     */
    default <T> List<T> getContributors(LanguageContributorKey<T> key) {
        return Collections.emptyList();
    }

}
