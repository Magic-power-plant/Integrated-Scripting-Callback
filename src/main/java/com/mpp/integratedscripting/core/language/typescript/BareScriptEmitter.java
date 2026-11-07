package com.mpp.integratedscripting.core.language.typescript;

import com.pingsu.transpiler.DefaultJavaScriptEmitter;
import com.pingsu.transpiler.JavaScriptEmitter;
import com.pingsu.transpiler.JavaScriptTarget;
import com.pingsu.transpiler.TranspileResult;

/**
 * Removes the one module-scope artefact Translanguage adds to a bare script.
 *
 * <p>When every {@code import}/{@code export} of a file is erased - a script
 * that only had type imports, for instance - Translanguage appends
 * {@code export {};} so the file stays a valid ES module instead of silently
 * becoming a classic script. That is the correct rule for a bundler and a
 * syntax error in this mod, where no module system exists. The marker is always
 * its own statement on its own line, so dropping the newline around it keeps the
 * user's line numbering intact.</p>
 *
 * <p>A user who writes {@code export {};} on purpose loses it too, which is
 * exactly the intended outcome: such a script has nothing to export anyway.</p>
 *
 * <p>Instances are immutable and shared across compilations, as
 * {@code TypeScriptTranspiler} requires.</p>
 *
 * @author rubensworks
 */
public final class BareScriptEmitter implements JavaScriptEmitter {

    /** The statement Translanguage appends to a file with no runtime exports. */
    public static final String MODULE_MARKER = "\nexport {};\n";

    private final JavaScriptEmitter delegate;

    /**
     * Create an emitter on top of the default one.
     */
    public BareScriptEmitter() {
        this(new DefaultJavaScriptEmitter());
    }

    /**
     * Create an emitter that post-processes another backend.
     * @param delegate The backend that produces the JavaScript.
     */
    public BareScriptEmitter(JavaScriptEmitter delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("A delegate emitter is required.");
        }
        this.delegate = delegate;
    }

    @Override
    public TranspileResult emit(String sourceName, String intermediateJs, JavaScriptTarget target) {
        TranspileResult result = this.delegate.emit(sourceName, intermediateJs, target);
        if (!result.successful()) {
            return result;
        }
        String javaScript = stripModuleMarker(result.javaScript());
        if (javaScript.equals(result.javaScript())) {
            return result;
        }
        return new TranspileResult(javaScript, result.diagnostics());
    }

    /**
     * @param javaScript The emitted JavaScript.
     * @return The same text without the module-scope marker statement.
     */
    public static String stripModuleMarker(String javaScript) {
        if (javaScript == null || javaScript.isEmpty()) {
            return javaScript == null ? "" : javaScript;
        }
        return javaScript.replace(MODULE_MARKER, "\n");
    }

    @Override
    public String toString() {
        return "BareScriptEmitter";
    }

}
