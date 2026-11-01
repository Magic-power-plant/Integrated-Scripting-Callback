package com.mpp.integratedscripting.api.language.service;

import com.mpp.integratedscripting.api.language.LanguageId;
import org.cyclops.cyclopscore.init.IRegistry;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collection;

/**
 * Registry of language services and contributors.
 *
 * <p>This is the extensibility core of the language layer: a new language
 * registers its services here, and a mod can add a contributor to a language it
 * does not own. Registration happens during {@code preInit}/{@code init}; reads
 * use immutable snapshots and may happen from any thread.</p>
 *
 * <p>Registering a service replaces the previous provider of that key for that
 * language; registering a contributor appends it to the list.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ILanguageServiceRegistry extends IRegistry {

    /**
     * Register (or replace) one service of a language.
     * @param language The language.
     * @param key The service key.
     * @param service The implementation; must implement {@link LanguageServiceKey#getType()}.
     * @param <T> The service type.
     */
    <T> void registerService(LanguageId language, LanguageServiceKey<T> key, T service);

    /**
     * Append one contributor to a language's contributor list.
     * @param language The language.
     * @param key The contributor key.
     * @param priority The ordering weight; higher runs first.
     * @param contributor The implementation; must implement
     *                    {@link LanguageContributorKey#getType()}.
     * @param <T> The contributor type.
     */
    <T> void registerContributor(LanguageId language, LanguageContributorKey<T> key,
                                 int priority, T contributor);

    /**
     * Resolve the services of a language.
     * @param language The language.
     * @return The immutable service bundle; never {@code null}, even for a
     *         language that registered nothing (all keys fall back to their
     *         defaults).
     */
    ILanguageServices getServices(LanguageId language);

    /**
     * Resolve the services of the language that handles a file.
     * @param path A script file path.
     * @return The service bundle, or {@code null} when no language handles the
     *         file's extension.
     */
    @Nullable
    ILanguageServices getServices(Path path);

    /**
     * @return A snapshot of every language that has services or contributors.
     */
    Collection<LanguageId> getLanguages();

}
