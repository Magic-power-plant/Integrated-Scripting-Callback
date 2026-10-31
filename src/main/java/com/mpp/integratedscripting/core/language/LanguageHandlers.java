package com.mpp.integratedscripting.core.language;

import org.cyclops.cyclopscore.helper.MinecraftHelpers;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;

/**
 * Holder for the language handler registry.
 *
 * <p>Mirrors the 1.20 reference implementation: inside Minecraft the registry
 * comes from the mod's {@code RegistryManager}, outside of it the standalone
 * {@link LanguageHandlerRegistry} is used so unit tests do not need a running
 * game.</p>
 *
 * @author rubensworks
 */
public class LanguageHandlers {

    public static final ILanguageHandlerRegistry REGISTRY = constructRegistry();

    private static boolean loaded = false;

    private static ILanguageHandlerRegistry constructRegistry() {
        // This also allows this registry to be used outside of a minecraft environment.
        if (MinecraftHelpers.isModdedEnvironment()) {
            IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
            if (instance != null) {
                ILanguageHandlerRegistry registry = instance.getRegistryManager()
                        .getRegistry(ILanguageHandlerRegistry.class);
                if (registry != null) {
                    return registry;
                }
            }
        }
        return LanguageHandlerRegistry.getInstance();
    }

    /**
     * Register the built-in language handlers exactly once.
     */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        REGISTRY.register(new LanguageHandlerJavaScript());
    }

}
