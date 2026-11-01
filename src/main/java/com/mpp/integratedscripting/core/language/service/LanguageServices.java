package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKey;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable implementation of {@link ILanguageServices}: the resolved snapshot
 * of one language.
 *
 * <p>Instances are built by {@link LanguageServiceRegistry} and cached there;
 * they never change, so an editor may keep one for the whole lifetime of the
 * file it is editing even while another thread registers a contributor (the
 * editor picks the new bundle up the next time it resolves the file).</p>
 *
 * @author rubensworks
 */
public final class LanguageServices implements ILanguageServices {

    private final LanguageId languageId;
    private final Map<LanguageServiceKey<?>, Object> services;
    private final Map<LanguageContributorKey<?>, List<Object>> contributors;

    /**
     * @param languageId The language of this bundle.
     * @param services The resolved services, keyed by service key.
     * @param contributors The resolved contributor lists (already priority
     *                     ordered), keyed by contributor key.
     */
    public LanguageServices(LanguageId languageId,
                            Map<LanguageServiceKey<?>, Object> services,
                            Map<LanguageContributorKey<?>, List<Object>> contributors) {
        this.languageId = languageId;
        this.services = Collections.unmodifiableMap(services);
        this.contributors = Collections.unmodifiableMap(contributors);
    }

    @Override
    public LanguageId getLanguageId() {
        return languageId;
    }

    @Override
    public <T> T get(LanguageServiceKey<T> key) {
        Object service = this.services.get(key);
        return service == null ? key.getDefaultValue() : key.cast(service);
    }

    @Override
    public <T> List<T> getContributors(LanguageContributorKey<T> key) {
        List<Object> values = this.contributors.get(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> typed = new ArrayList<>(values.size());
        for (Object value : values) {
            typed.add(key.cast(value));
        }
        return Collections.unmodifiableList(typed);
    }

    @Override
    public String toString() {
        return "LanguageServices(" + languageId + ", " + services.size() + " services, "
                + contributors.size() + " contributor lists)";
    }

}
