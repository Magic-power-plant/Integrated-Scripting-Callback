package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.api.network.IScriptingData;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;

/**
 * Instantiates scripts using GraalVM.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §6.17, §8.8): errors are tracked
 * by {@code ScriptErrorTracker} instead of the weak-reference resolution helper,
 * and every compiled script owns exactly one disk listener that fans out to all
 * of its consumers through a {@code ScriptInvalidationDispatcher}. The Graal
 * {@code Context} is closed when that listener fires, i.e. when this compiled
 * version of the script is really dead, and never when a single consumer merely
 * detaches itself (docs/AUDIT.md D1).</p>
 *
 * <p>The sandbox, the evaluation and the lifetime handling live in
 * {@link JsScriptCompiler}, which the TypeScript factory shares: the two
 * languages differ only in how the file's text becomes JavaScript.</p>
 *
 * @author rubensworks
 */
public class GraalScriptFactory implements IScriptFactory {

    private final String languageId;

    public GraalScriptFactory(String languageId) {
        this.languageId = languageId;
    }

    @Nullable
    @Override
    public IScript getScript(int disk, Path path) throws EvaluationException {
        return getScript(disk, path, IScriptFactory.noMemories());
    }

    @Nullable
    @Override
    public IScript getScript(int disk, Path path, Map<String, DimPos> memories) throws EvaluationException {
        IScriptingData scriptingData = JsScriptCompiler.requireScriptingData();
        String scriptContent = JsScriptCompiler.requireScriptContent(scriptingData, disk, path);
        return JsScriptCompiler.compile(scriptingData, disk, path, this.languageId, scriptContent, memories);
    }

}
