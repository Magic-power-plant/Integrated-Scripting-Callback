package com.mpp.integratedscripting.api.language.diagnostics;

import com.mpp.integratedscripting.api.language.text.DocumentText;

import java.util.Collections;
import java.util.List;

/**
 * Reports problems in a document.
 *
 * <p>The default implementation reports nothing, so a language that does not
 * implement diagnostics simply never shows any. This is the pattern the whole
 * service layer follows: adding a capability means registering a service, not
 * extending an interface.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface IDiagnosticService {

    /**
     * Diagnose a complete document.
     * @param document The document.
     * @return The diagnostics, in document order; never {@code null}.
     */
    default List<Diagnostic> diagnose(DocumentText document) {
        return Collections.emptyList();
    }

}
