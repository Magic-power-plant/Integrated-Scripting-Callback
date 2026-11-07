package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.core.language.typescript.TypeScriptCompileException;
import com.mpp.integratedscripting.core.language.typescript.TypeScriptCompiler;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;

/**
 * Instantiates {@code .ts} scripts: compile with Translanguage, then run the
 * resulting JavaScript in exactly the sandbox a {@code .js} script gets.
 *
 * <p>Errors are reported where the user can act on them. A TypeScript problem
 * (syntax, or syntax this mod cannot host such as a value import) surfaces as an
 * {@link EvaluationException} that names the file, the disk, the line, the column
 * and Translanguage's diagnostic code; those positions are computed against the
 * original source, so they match the editor. A failure while the compiled code
 * runs surfaces through {@link JsScriptCompiler} like any JavaScript error, with
 * the position in the generated JavaScript - identical for a script without an
 * {@code enum}, one line offset per enum member beyond the first otherwise,
 * because lowering an enum expands it.</p>
 *
 * <p>No type checking happens here on purpose: this is a transpiler, not
 * {@code tsc}. Types are the editor's business, through the generated
 * {@code .d.ts} (docs/TYPESCRIPT-FEASIBILITY.md §1, §7.1).</p>
 *
 * @author rubensworks
 */
public class TypeScriptScriptFactory implements IScriptFactory {

    /** The GraalVM language the compiled output is evaluated with. */
    public static final String LANGUAGE_ID = "js";

    @Nullable
    @Override
    public IScript getScript(int disk, Path path) throws EvaluationException {
        return getScript(disk, path, IScriptFactory.noMemories());
    }

    @Nullable
    @Override
    public IScript getScript(int disk, Path path, Map<String, DimPos> memories) throws EvaluationException {
        IScriptingData scriptingData = JsScriptCompiler.requireScriptingData();
        String source = JsScriptCompiler.requireScriptContent(scriptingData, disk, path);

        String javaScript;
        try {
            javaScript = TypeScriptCompiler.toJavaScript(path.toString(), source);
        } catch (TypeScriptCompileException e) {
            throw ScriptErrorTracker.track(new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.typescript_compile",
                    path.toString(), disk, e.getLine(), e.getColumn(), e.getCode(),
                    String.valueOf(e.getMessage()))), disk, path);
        }

        return JsScriptCompiler.compile(scriptingData, disk, path, LANGUAGE_ID, javaScript, memories);
    }

}
