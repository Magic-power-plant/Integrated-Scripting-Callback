package com.mpp.integratedscripting.core.language;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Registry of language handlers, keyed by file extension.
 *
 * <p>The 1.20 reference implementation listens to the per-mod
 * {@code ServerStartedEvent}. 1.12.2 has a single global Forge event bus, so
 * {@code IntegratedScriptingCallback#onServerStarted} calls
 * {@link #onServerStarted()} explicitly instead of relying on a constructor
 * side effect (this also keeps the registry usable in plain unit tests).</p>
 *
 * @author rubensworks
 */
public class LanguageHandlerRegistry implements ILanguageHandlerRegistry {

    private static final LanguageHandlerRegistry INSTANCE = new LanguageHandlerRegistry();

    private final Map<String, ILanguageHandler> extensionToHandlerMap = Maps.newHashMap();

    private LanguageHandlerRegistry() {
    }

    /**
     * @return The unique instance.
     */
    public static LanguageHandlerRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public void register(ILanguageHandler translator) {
        for (String extension : translator.getExtensions()) {
            extensionToHandlerMap.put(extension, translator);
        }
    }

    @Nullable
    @Override
    public ILanguageHandler getProvider(Path filePath) {
        String extension = getPathExtension(filePath);
        if (extension != null) {
            return extensionToHandlerMap.get(extension);
        }
        return null;
    }

    /**
     * Notify all registered language handlers that the server has started.
     * Handlers are notified once, even if they are registered under several extensions.
     */
    public void onServerStarted() {
        Set<ILanguageHandler> handlers = Sets.newLinkedHashSet(extensionToHandlerMap.values());
        for (ILanguageHandler languageHandler : handlers) {
            languageHandler.onServerStarted();
        }
    }

    /**
     * @param filePath A file path.
     * @return The lower-case extension without dot, or null.
     */
    @Nullable
    public static String getPathExtension(Path filePath) {
        Path fileName = filePath.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return null;
        }
        return name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * @return A snapshot of all registered handlers, for diagnostics.
     */
    public java.util.Collection<ILanguageHandler> getHandlers() {
        return Lists.newArrayList(Sets.newLinkedHashSet(extensionToHandlerMap.values()));
    }

}
