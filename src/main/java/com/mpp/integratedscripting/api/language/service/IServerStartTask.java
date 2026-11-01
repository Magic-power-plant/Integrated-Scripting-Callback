package com.mpp.integratedscripting.api.language.service;

/**
 * A task that runs once when the integrated server has started, contributed by a
 * language.
 *
 * <p>This is where side effects belong that used to live in
 * {@code ILanguageHandler#onServerStarted()} (the JavaScript module writes its
 * TypeScript typings from here). Because it is a contributor list, several
 * tasks can be attached to one language, and a third-party mod can add its own
 * without replacing the language's handler.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface IServerStartTask {

    /**
     * Run the task. Implementations must not throw; log and continue instead.
     */
    void onServerStarted();

}
