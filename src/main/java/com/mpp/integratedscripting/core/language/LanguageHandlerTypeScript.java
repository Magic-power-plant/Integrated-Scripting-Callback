package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.core.network.TypeScriptScriptFactory;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import java.util.Collections;
import java.util.List;

/**
 * The built-in TypeScript language.
 *
 * <p>TypeScript is not a second runtime: it is the same GraalJS sandbox as
 * JavaScript, entered through a compiler. {@link TypeScriptScriptFactory}
 * transpiles the file with Translanguage and evaluates the result, so everything
 * after the factory - the sandbox, the value translators, the network cache,
 * invalidation, the editor - is shared with {@code .js} and cannot drift.</p>
 *
 * <p>The handler only describes the language; the services are registered by
 * {@link LanguageHandlers#load()}:</p>
 * <ul>
 *   <li>{@link TypeScriptHighlightService} - JavaScript highlighting plus the
 *       TypeScript type keywords,</li>
 *   <li>{@link TypeScriptEditSupport} - the JavaScript editing rules, with
 *       {@code ts} as the extension of a new file,</li>
 *   <li>{@link JavaScriptCompletionService} plus the keyword/document/operator/
 *       method contributors - completion.</li>
 * </ul>
 *
 * <p>Declaration files ({@code *.d.ts}) are not scripts: they are generated next
 * to the world for the IDE. {@link LanguageHandlerRegistry#getProvider(java.nio.file.Path)}
 * refuses them for every language, and
 * {@code ScriptingData} keeps them out of the script list.</p>
 *
 * @author rubensworks
 */
public class LanguageHandlerTypeScript implements ILanguageHandler {

    private final IScriptFactory scriptFactory;

    public LanguageHandlerTypeScript() {
        this.scriptFactory = new TypeScriptScriptFactory();
    }

    @Override
    public LanguageId getLanguageId() {
        return LanguageId.TYPESCRIPT;
    }

    @Override
    public String getName() {
        return "TypeScript";
    }

    @Override
    public List<String> getExtensions() {
        return Collections.singletonList("ts");
    }

    @Override
    public IScriptFactory getScriptFactory() throws EvaluationException {
        return this.scriptFactory;
    }

}
