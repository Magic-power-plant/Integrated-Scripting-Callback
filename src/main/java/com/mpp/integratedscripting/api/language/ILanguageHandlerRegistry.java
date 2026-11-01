package com.mpp.integratedscripting.api.language;

import org.cyclops.cyclopscore.init.IRegistry;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Registry of {@link ILanguageHandler}s, indexed both by file extension and by
 * stable {@link LanguageId}.
 *
 * <p>The registry is also the composition root of the legacy bridge: when a
 * handler is registered, the legacy capabilities it overrides are converted into
 * services of the {@code ILanguageServiceRegistry}, so the editor only ever
 * talks to services.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ILanguageHandlerRegistry extends IRegistry {

    /**
     * Register one language handler.
     * @param provider The handler.
     */
    void register(ILanguageHandler provider);

    /**
     * @param filePath A script file path.
     * @return The handler for its extension, or {@code null}.
     */
    @Nullable
    ILanguageHandler getProvider(Path filePath);

    /**
     * @param language A language id.
     * @return The handler registered for that id, or {@code null}.
     */
    @Nullable
    ILanguageHandler getHandler(LanguageId language);

    /**
     * @return A snapshot of every registered handler, in registration order.
     */
    default Collection<ILanguageHandler> getHandlers() {
        List<ILanguageHandler> handlers = new ArrayList<>();
        for (LanguageId language : getLanguages()) {
            ILanguageHandler handler = getHandler(language);
            if (handler != null) {
                handlers.add(handler);
            }
        }
        return Collections.unmodifiableList(handlers);
    }

    /**
     * @return A snapshot of every registered language id.
     */
    Collection<LanguageId> getLanguages();

    /**
     * @return The first registered handler, used as "the default language" when no
     *         language was chosen explicitly (for example when creating a new
     *         script file); {@code null} when nothing is registered.
     */
    @Nullable
    default ILanguageHandler getDefaultHandler() {
        Collection<ILanguageHandler> handlers = getHandlers();
        return handlers.isEmpty() ? null : handlers.iterator().next();
    }

    /**
     * @return Human readable descriptions of every extension that was registered
     *         twice, for self-diagnostics; empty when there was no conflict.
     */
    default Collection<String> getConflicts() {
        return Collections.emptyList();
    }

}
