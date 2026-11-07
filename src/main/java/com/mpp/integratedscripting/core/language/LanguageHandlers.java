package com.mpp.integratedscripting.core.language;

import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.service.ILanguageServiceRegistry;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.IServerStartTask;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKeys;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.core.language.contributor.DocumentSymbolCompletionContributor;
import com.mpp.integratedscripting.core.language.contributor.KeywordCompletionContributor;
import com.mpp.integratedscripting.core.language.contributor.MethodCompletionContributor;
import com.mpp.integratedscripting.core.language.contributor.OperatorCompletionContributor;
import com.mpp.integratedscripting.core.language.service.LanguageServiceRegistry;
import com.mpp.integratedscripting.core.language.typescript.TypeScriptRuntime;
import org.cyclops.cyclopscore.helper.MinecraftHelpers;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * Composition root and convenience access to the language registries.
 *
 * <p>Inside Minecraft the registries come from the mod's
 * {@code RegistryManager}; outside of it (unit tests) the standalone
 * implementations are used, so tests need no running game. The two registries
 * are wired together here so that a path resolves to a language and that
 * language's service bundle.</p>
 *
 * <p>{@link #load()} is the single place that registers the built-in language
 * and its services/contributors. Third-party mods register through
 * {@link #REGISTRY} and {@link #SERVICES} during {@code preInit}/{@code init}.</p>
 *
 * @author rubensworks
 */
public class LanguageHandlers {

    /** The service registry every editor and third-party mod reads. */
    public static final ILanguageServiceRegistry SERVICES = constructServiceRegistry();

    /** The handler registry that maps files to languages. */
    public static final ILanguageHandlerRegistry REGISTRY = constructHandlerRegistry();

    private static boolean loaded = false;

    private static ILanguageServiceRegistry constructServiceRegistry() {
        ILanguageServiceRegistry registry = fetchFromRegistryManager(ILanguageServiceRegistry.class);
        return registry != null ? registry : LanguageServiceRegistry.getInstance();
    }

    private static ILanguageHandlerRegistry constructHandlerRegistry() {
        ILanguageHandlerRegistry registry = fetchFromRegistryManager(ILanguageHandlerRegistry.class);
        if (registry == null) {
            registry = LanguageHandlerRegistry.getInstance();
        }
        // Both directions: the service registry resolves paths through the
        // handler registry, and the handler registry bridges legacy handlers into
        // the service registry.
        if (SERVICES instanceof LanguageServiceRegistry) {
            ((LanguageServiceRegistry) SERVICES).attachHandlerRegistry(registry);
        }
        if (registry instanceof LanguageHandlerRegistry) {
            ((LanguageHandlerRegistry) registry).setServiceRegistry(SERVICES);
        }
        return registry;
    }

    @Nullable
    private static <T extends org.cyclops.cyclopscore.init.IRegistry> T fetchFromRegistryManager(
            Class<T> type) {
        try {
            if (!MinecraftHelpers.isModdedEnvironment()) {
                return null;
            }
            IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
            if (instance == null) {
                return null;
            }
            return instance.getRegistryManager().getRegistry(type);
        } catch (Throwable e) {
            // No Minecraft/FML on the classpath (plain unit tests): use the
            // standalone registries.
            return null;
        }
    }

    /**
     * Register the built-in language handlers and their services exactly once.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        register(REGISTRY, SERVICES);
    }

    /**
     * Register the built-in languages, services and contributors into the given
     * registries. Tests and embedders use this to build an isolated pair of
     * registries; {@link #load()} calls it for the game-wide ones.
     * @param handlers The handler registry to register the built-in languages in.
     * @param services The service registry to register the built-in services in.
     */
    public static void register(ILanguageHandlerRegistry handlers, ILanguageServiceRegistry services) {
        LanguageHandlerJavaScript handler = new LanguageHandlerJavaScript();
        handlers.register(handler);

        LanguageId language = handler.getLanguageId();
        services.registerService(language, LanguageServiceKeys.HIGHLIGHT,
                new JavaScriptHighlightService());
        services.registerService(language, LanguageServiceKeys.EDIT, new JavaScriptEditSupport());
        services.registerService(language, LanguageServiceKeys.COMPLETION,
                new JavaScriptCompletionService());

        // Built-in completion contributors, highest priority first. A language's
        // own service does the object-method case; the contributors add the
        // generic sources that any mod can extend or replace.
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 100,
                new KeywordCompletionContributor(JavaScriptLexer.KEYWORDS,
                        CompletionKind.KEYWORD));
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 80,
                new DocumentSymbolCompletionContributor());
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 60,
                new OperatorCompletionContributor());
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 40,
                new MethodCompletionContributor());

        services.registerContributor(language, LanguageContributorKeys.SERVER_START_TASKS, 0,
                new TypeScriptTypingsTask());

        registerTypeScript(handlers, services);
    }

    /**
     * Register the built-in TypeScript language, if the config allows it and the
     * Translanguage compiler is on the classpath.
     *
     * <p>Deliberately a separate method: it is the only place that references
     * {@link LanguageHandlerTypeScript} and its compiler, so the JVM resolves
     * those classes only after the probe passed. A slim jar without the
     * transpiler therefore loses TypeScript and keeps JavaScript, instead of
     * failing to load this class (compare {@code ScriptRuntime}).</p>
     *
     * <p>TypeScript reuses the JavaScript services where the two languages agree
     * (completion policy, generic contributors, editing rules) and adds only what
     * differs: the language id, the {@code ts} extension, the TypeScript keyword
     * set and the compiling script factory.</p>
     *
     * @param handlers The handler registry to register the language in.
     * @param services The service registry to register its services in.
     */
    private static void registerTypeScript(ILanguageHandlerRegistry handlers,
                                           ILanguageServiceRegistry services) {
        if (!GeneralConfig.typescriptEnabled) {
            IntegratedScriptingCallback.clog("TypeScript support is disabled in the config.");
            return;
        }
        if (!TypeScriptRuntime.isAvailable()) {
            TypeScriptRuntime.logUnavailable();
            return;
        }

        LanguageHandlerTypeScript handler = new LanguageHandlerTypeScript();
        handlers.register(handler);

        LanguageId language = handler.getLanguageId();
        services.registerService(language, LanguageServiceKeys.HIGHLIGHT,
                new TypeScriptHighlightService());
        services.registerService(language, LanguageServiceKeys.EDIT, new TypeScriptEditSupport());
        services.registerService(language, LanguageServiceKeys.COMPLETION,
                new JavaScriptCompletionService());

        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 100,
                new KeywordCompletionContributor(JavaScriptLexer.TYPESCRIPT_KEYWORDS,
                        CompletionKind.KEYWORD));
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 80,
                new DocumentSymbolCompletionContributor());
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 60,
                new OperatorCompletionContributor());
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 40,
                new MethodCompletionContributor());
    }

    /**
     * Run the server-start work of every language: the transition-period legacy
     * hook and every contributed {@link IServerStartTask}.
     */
    @SuppressWarnings("deprecation")
    public static void onServerStarted() {
        if (REGISTRY instanceof LanguageHandlerRegistry) {
            ((LanguageHandlerRegistry) REGISTRY).onServerStarted();
            return;
        }
        for (ILanguageHandler handler : REGISTRY.getHandlers()) {
            handler.onServerStarted();
            ILanguageServices services = SERVICES.getServices(handler.getLanguageId());
            for (IServerStartTask task : services.getContributors(
                    LanguageContributorKeys.SERVER_START_TASKS)) {
                task.onServerStarted();
            }
        }
    }

    /**
     * Resolve the services of the language that handles a file, falling back to
     * the plain-text defaults when no language claims the extension. Callers never
     * have to check for {@code null}: the result always offers working defaults.
     * @param path The script file path, or {@code null}.
     * @return The language services; never {@code null}.
     */
    public static ILanguageServices getServices(@Nullable Path path) {
        if (path != null) {
            ILanguageServices services = SERVICES.getServices(path);
            if (services != null) {
                return services;
            }
        }
        return SERVICES.getServices(LanguageId.PLAIN_TEXT);
    }

    /**
     * @return The extension a new script file gets, taken from the default (first
     *         registered) language's edit support.
     */
    public static String getDefaultFileExtension() {
        ILanguageHandler handler = REGISTRY.getDefaultHandler();
        if (handler == null) {
            return "js";
        }
        String extension = SERVICES.getServices(handler.getLanguageId())
                .get(LanguageServiceKeys.EDIT).getDefaultFileExtension();
        return extension == null || extension.isEmpty() ? "js" : extension;
    }

}
