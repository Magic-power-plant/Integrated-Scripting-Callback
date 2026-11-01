package com.mpp.integratedscripting.api.language.service;

import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;

/**
 * The built-in language contributor keys.
 *
 * <p>Contributors are lists, not single services, which is what lets a
 * third-party mod add candidates or start-up tasks to an existing language.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class LanguageContributorKeys {

    /** Extra completion sources; see {@link ICompletionContributor}. */
    public static final LanguageContributorKey<ICompletionContributor> COMPLETION =
            LanguageContributorKey.of("completion", ICompletionContributor.class);

    /** Tasks run once when the server has started, per language. */
    public static final LanguageContributorKey<IServerStartTask> SERVER_START_TASKS =
            LanguageContributorKey.of("server_start_tasks", IServerStartTask.class);

    private LanguageContributorKeys() {
    }

}
