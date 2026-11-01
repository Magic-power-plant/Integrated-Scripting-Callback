package com.mpp.integratedscripting.api.language.service;

import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;
import com.mpp.integratedscripting.api.language.diagnostics.IDiagnosticService;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.core.language.service.DefaultServices;

/**
 * The built-in language service keys.
 *
 * <p>Each key carries a working default, so a language only has to register the
 * services it actually improves on. Adding a new capability means defining a
 * new key and a default; no existing interface changes.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class LanguageServiceKeys {

    /** Syntax highlighting and lexical context. Default: plain text. */
    public static final LanguageServiceKey<IHighlightService> HIGHLIGHT =
            LanguageServiceKey.of("highlight", IHighlightService.class, DefaultServices.PLAIN_HIGHLIGHT);

    /** Indentation, comments, identifier syntax and the new-file extension. */
    public static final LanguageServiceKey<IEditSupport> EDIT =
            LanguageServiceKey.of("edit", IEditSupport.class, DefaultServices.PLAIN_EDIT);

    /** Completion candidates. Default: none. */
    public static final LanguageServiceKey<ICompletionService> COMPLETION =
            LanguageServiceKey.of("completion", ICompletionService.class, DefaultServices.EMPTY_COMPLETION);

    /** Document diagnostics. Default: none. */
    public static final LanguageServiceKey<IDiagnosticService> DIAGNOSTICS =
            LanguageServiceKey.of("diagnostics", IDiagnosticService.class, DefaultServices.EMPTY_DIAGNOSTICS);

    private LanguageServiceKeys() {
    }

}
