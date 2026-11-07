package com.mpp.integratedscripting.core.language.typescript;

import com.mpp.GeneralConfig;
import com.pingsu.transpiler.Diagnostic;
import com.pingsu.transpiler.EnumLoweringPass;
import com.pingsu.transpiler.JavaScriptEmitter;
import com.pingsu.transpiler.JavaScriptTarget;
import com.pingsu.transpiler.TransformationPass;
import com.pingsu.transpiler.TranspileResult;
import com.pingsu.transpiler.Transpiler;
import com.pingsu.transpiler.TypeErasurePass;
import com.pingsu.transpiler.TypeScriptTranspiler;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Turns a {@code .ts} script into the bare JavaScript this mod evaluates.
 *
 * <p>The pipeline is Translanguage's, configured for this mod's script model:</p>
 * <ol>
 *   <li>{@code TypeErasurePass} - erases type annotations and pure type
 *       declarations, and performs import elision. Erasing removes text instead
 *       of re-wrapping it, so the <em>line count</em> survives (measured: 17
 *       lines in, 17 lines out, docs/TYPESCRIPT-TRANSLANGUAGE.md §2.2) and a
 *       diagnostic's line is the line the user wrote. Text after an erased
 *       annotation shifts left within its line, which only affects runtime stack
 *       positions, never compile diagnostics: those are reported against the
 *       original source.</li>
 *   <li>{@code EnumLoweringPass} - lowers {@code enum} to a runtime object.
 *       This is the one transform that adds lines.</li>
 *   <li>{@link BareScriptPass} - removes {@code export} and rejects
 *       {@code import}, which have no meaning without a module loader. It must
 *       run after the two passes above: they file their own edits, and
 *       Translanguage rejects overlapping transformations.</li>
 * </ol>
 *
 * <p>The target is {@link JavaScriptTarget#PRESERVE}: GraalJS 21.3.0 already
 * understands modern JavaScript, and re-printing to {@code ES6} would move every
 * character, taking the error positions with it.</p>
 *
 * <p>The class is stateless apart from the shared transpiler instance, which
 * Translanguage documents as immutable and thread-safe, so script compilation may
 * happen on several server threads without synchronisation.</p>
 *
 * @author rubensworks
 */
public final class TypeScriptCompiler {

    /**
     * Fallback source limit: 256 KiB of characters, well above a hand written
     * script and far below anything that could stall a server tick
     * (100 KB takes about 90 ms, docs/TYPESCRIPT-TRANSLANGUAGE.md §2.3).
     */
    public static final int DEFAULT_MAX_SOURCE_LENGTH = 256 * 1024;

    private static final List<TransformationPass> PASSES = Collections.unmodifiableList(Arrays.asList(
            (TransformationPass) new TypeErasurePass(),
            new EnumLoweringPass(),
            new BareScriptPass()));

    private static final JavaScriptEmitter EMITTER = new BareScriptEmitter();

    private static final Transpiler TRANSPILER = new TypeScriptTranspiler(
            JavaScriptTarget.PRESERVE, PASSES, EMITTER);

    private TypeScriptCompiler() {
    }

    /**
     * Compile TypeScript with the configured source limit.
     * @param sourceName The name shown in diagnostics, normally the script path.
     * @param source The TypeScript source.
     * @return The JavaScript to evaluate.
     * @throws TypeScriptCompileException When the source is too large, uses
     *         syntax this mod cannot host, or is not valid TypeScript.
     */
    public static String toJavaScript(String sourceName, @Nullable String source)
            throws TypeScriptCompileException {
        return toJavaScript(sourceName, source, GeneralConfig.typescriptMaxSourceLength);
    }

    /**
     * Compile TypeScript with an explicit source limit.
     * @param sourceName The name shown in diagnostics, normally the script path.
     * @param source The TypeScript source.
     * @param maxSourceLength The maximum number of characters, or a
     *                        non-positive value for {@link #DEFAULT_MAX_SOURCE_LENGTH}.
     * @return The JavaScript to evaluate.
     * @throws TypeScriptCompileException When the source is too large, uses
     *         syntax this mod cannot host, or is not valid TypeScript.
     */
    public static String toJavaScript(String sourceName, @Nullable String source, int maxSourceLength)
            throws TypeScriptCompileException {
        String text = source == null ? "" : source;
        // A UTF-8 BOM is invisible in the editor but would shift every column by one.
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        int limit = maxSourceLength > 0 ? maxSourceLength : DEFAULT_MAX_SOURCE_LENGTH;
        if (text.length() > limit) {
            throw new TypeScriptCompileException(TypeScriptCompileException.CODE_TOO_LARGE, 1, 1,
                    "the script has " + text.length() + " characters, above the limit of " + limit,
                    1);
        }
        if (text.isEmpty()) {
            return "";
        }

        TranspileResult result;
        try {
            result = TRANSPILER.transpile(sourceName == null ? "script.ts" : sourceName, text);
        } catch (RuntimeException e) {
            // A transformation pass can trip over input it does not model; never let
            // that escape as an anonymous RuntimeException into the evaluation path.
            throw new TypeScriptCompileException(TypeScriptCompileException.CODE_INTERNAL, 1, 1,
                    String.valueOf(e), 1);
        }
        if (result.successful()) {
            return result.javaScript();
        }
        List<Diagnostic> diagnostics = result.diagnostics();
        if (diagnostics == null || diagnostics.isEmpty()) {
            throw new TypeScriptCompileException(TypeScriptCompileException.CODE_INTERNAL, 1, 1,
                    "the transpiler refused the script without a diagnostic", 1);
        }
        Diagnostic first = diagnostics.get(0);
        throw new TypeScriptCompileException(first.code(), first.line(), first.column(),
                first.message(), diagnostics.size());
    }

    /**
     * @return The JavaScript target this mod compiles to ({@code PRESERVE}).
     */
    public static JavaScriptTarget getTarget() {
        return JavaScriptTarget.PRESERVE;
    }

}
