package com.mpp.integratedscripting.core.language;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.service.ILanguageServiceRegistry;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.IServerStartTask;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKeys;
import com.mpp.integratedscripting.core.language.service.LanguageServiceRegistry;
import com.mpp.integratedscripting.core.language.service.LegacyLanguageServices;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Registry of language handlers, keyed by file extension and by
 * {@link LanguageId}.
 *
 * <p>Registering a handler also bridges the legacy capabilities it overrides into
 * the {@link ILanguageServiceRegistry} (see {@link LegacyLanguageServices}), which
 * is what lets the terminal editor depend on services only.</p>
 *
 * <p>The 1.20 reference implementation listens to the per-mod
 * {@code ServerStartedEvent}. 1.12.2 has a single global Forge event bus, so
 * {@link LanguageHandlers#onServerStarted()} runs the server-start tasks
 * explicitly instead. This also keeps the registry usable in plain unit tests.</p>
 *
 * @author rubensworks
 */
public class LanguageHandlerRegistry implements ILanguageHandlerRegistry {

    private static final LanguageHandlerRegistry INSTANCE = new LanguageHandlerRegistry();

    private final Map<String, ILanguageHandler> extensionToHandlerMap = new LinkedHashMap<>();
    private final Map<LanguageId, ILanguageHandler> languageToHandlerMap = new LinkedHashMap<>();
    private final List<String> conflicts = new ArrayList<>();

    private ILanguageServiceRegistry services;

    /**
     * Create a registry that bridges into the game-wide service registry.
     */
    public LanguageHandlerRegistry() {
        this(LanguageServiceRegistry.getInstance());
    }

    /**
     * Create a registry that bridges into a specific service registry, for tests
     * and for the composition root.
     * @param services The service registry to register bridged services into.
     */
    public LanguageHandlerRegistry(ILanguageServiceRegistry services) {
        this.services = services;
    }

    /**
     * @return The unique game-wide instance; unit tests may construct their own.
     */
    public static LanguageHandlerRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Point the legacy bridge at a service registry.
     * @param services The service registry.
     */
    public synchronized void setServiceRegistry(ILanguageServiceRegistry services) {
        this.services = services;
        if (services instanceof LanguageServiceRegistry) {
            ((LanguageServiceRegistry) services).attachHandlerRegistry(this);
        }
    }

    @Override
    public synchronized void register(ILanguageHandler provider) {
        if (provider == null) {
            throw new IllegalArgumentException("A language handler must not be null.");
        }
        LanguageId language = provider.getLanguageId();

        ILanguageHandler previous = this.languageToHandlerMap.put(language, provider);
        if (previous != null && previous != provider) {
            // The replacement handler owns the language now; drop the old
            // handler's extensions so it cannot keep resolving files.
            Iterator<Map.Entry<String, ILanguageHandler>> iterator =
                    this.extensionToHandlerMap.entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue() == previous) {
                    iterator.remove();
                }
            }
            addConflict("The language id '" + language + "' is registered by both '"
                    + previous.getName() + "' and '" + provider.getName()
                    + "'; the last registration wins.");
        }

        List<String> extensions = provider.getExtensions();
        if (extensions != null) {
            for (String rawExtension : extensions) {
                String extension = normalizeExtension(rawExtension);
                if (extension == null) {
                    continue;
                }
                ILanguageHandler existing = this.extensionToHandlerMap.put(extension, provider);
                if (existing != null && existing != provider) {
                    addConflict("The extension '." + extension + "' is registered by both '"
                            + existing.getName() + "' and '" + provider.getName()
                            + "'; the last registration wins.");
                }
            }
        }

        LegacyLanguageServices.bridge(this.services, language, provider);
    }

    @Nullable
    @Override
    public synchronized ILanguageHandler getProvider(Path filePath) {
        // A .d.ts declaration file only describes types: it is generated for the
        // IDE, it is never evaluated, and the registry would otherwise hand it to
        // the TypeScript language because its last extension is "ts".
        if (isTypeScriptDeclaration(filePath)) {
            return null;
        }
        String extension = getPathExtension(filePath);
        if (extension != null) {
            return this.extensionToHandlerMap.get(extension);
        }
        return null;
    }

    @Nullable
    @Override
    public synchronized ILanguageHandler getHandler(LanguageId language) {
        return language == null ? null : this.languageToHandlerMap.get(language);
    }

    @Override
    public synchronized Collection<ILanguageHandler> getHandlers() {
        return Collections.unmodifiableCollection(
                new LinkedHashSet<>(this.languageToHandlerMap.values()));
    }

    @Override
    public synchronized Collection<LanguageId> getLanguages() {
        return Collections.unmodifiableCollection(
                new LinkedHashSet<>(this.languageToHandlerMap.keySet()));
    }

    @Nullable
    @Override
    public synchronized ILanguageHandler getDefaultHandler() {
        Collection<ILanguageHandler> handlers = this.languageToHandlerMap.values();
        return handlers.isEmpty() ? null : handlers.iterator().next();
    }

    @Override
    public synchronized Collection<String> getConflicts() {
        return Collections.unmodifiableList(new ArrayList<>(this.conflicts));
    }

    /**
     * Run the server-start work of every registered language exactly once: the
     * legacy {@code onServerStarted} hook (transition period only) and then every
     * contributed {@link IServerStartTask}.
     *
     * <p>1.12.2 keeps this explicit instead of using a Forge event, so plain unit
     * tests can call it.</p>
     */
    @SuppressWarnings("deprecation")
    public synchronized void onServerStarted() {
        Set<ILanguageHandler> handlers = new LinkedHashSet<>(this.languageToHandlerMap.values());
        for (ILanguageHandler handler : handlers) {
            handler.onServerStarted();
            if (this.services == null) {
                continue;
            }
            ILanguageServices languageServices = this.services.getServices(handler.getLanguageId());
            for (IServerStartTask task : languageServices.getContributors(
                    LanguageContributorKeys.SERVER_START_TASKS)) {
                task.onServerStarted();
            }
        }
    }

    private void addConflict(String message) {
        this.conflicts.add(message);
        IntegratedScriptingCallback.clog(Level.WARN, "Language registration conflict: " + message);
    }

    /**
     * @param extension An extension, with or without a leading dot, in any case.
     * @return The normalised lower-case extension without dot, or {@code null}
     *         when it is empty.
     */
    @Nullable
    public static String normalizeExtension(@Nullable String extension) {
        if (extension == null) {
            return null;
        }
        String value = extension.trim();
        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        return value.isEmpty() ? null : value.toLowerCase(Locale.ROOT);
    }

    /**
     * @param filePath A file path.
     * @return The lower-case extension without dot, or null.
     */
    @Nullable
    public static String getPathExtension(Path filePath) {
        if (filePath == null) {
            return null;
        }
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return normalizeExtension(name.substring(dot + 1));
    }

    /**
     * @param filePath A file path, or {@code null}.
     * @return Whether the file is a TypeScript declaration file ({@code *.d.ts}).
     */
    public static boolean isTypeScriptDeclaration(@Nullable Path filePath) {
        if (filePath == null) {
            return false;
        }
        Path fileName = filePath.getFileName();
        return fileName != null && isTypeScriptDeclarationName(fileName.toString());
    }

    /**
     * @param fileName A file name, or {@code null}.
     * @return Whether the name ends in {@code .d.ts}, in any case.
     */
    public static boolean isTypeScriptDeclarationName(@Nullable String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".d.ts");
    }

}
