package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.core.network.GraalScriptFactory;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import java.util.Arrays;
import java.util.List;

/**
 * The built-in JavaScript language.
 *
 * <p>The handler only describes the language; everything else lives in services
 * and contributors registered by {@link LanguageHandlers#load()}:</p>
 * <ul>
 *   <li>{@link JavaScriptHighlightService} - highlighting and lexical context,</li>
 *   <li>{@link JavaScriptEditSupport} - indentation, comments, identifiers and
 *       the new-file extension,</li>
 *   <li>{@link JavaScriptCompletionService} plus the contributors in
 *       {@code core.language.contributor} - completion,</li>
 *   <li>{@link TypeScriptTypingsTask} - the typings written on server start.</li>
 * </ul>
 *
 * @author rubensworks
 */
public class LanguageHandlerJavaScript implements ILanguageHandler {

    private final IScriptFactory scriptFactory;

    public LanguageHandlerJavaScript() {
        this.scriptFactory = new GraalScriptFactory("js");
    }

    @Override
    public LanguageId getLanguageId() {
        return LanguageId.JAVASCRIPT;
    }

    @Override
    public String getName() {
        return "JavaScript";
    }

    @Override
    public List<String> getExtensions() {
        return Arrays.asList("js", "cjs", "mjs");
    }

    @Override
    public IScriptFactory getScriptFactory() throws EvaluationException {
        return this.scriptFactory;
    }

}
