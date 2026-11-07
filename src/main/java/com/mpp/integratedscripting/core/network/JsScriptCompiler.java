package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The part of script instantiation that needs GraalVM but not a particular
 * source language.
 *
 * <p>It was the body of {@link GraalScriptFactory} until TypeScript arrived: a
 * {@code .ts} script differs only in how its text becomes JavaScript
 * ({@code TypeScriptCompiler}), while reading the file, building the sandbox,
 * evaluating, tracking errors and releasing the context are identical. Keeping
 * the shared half here means a second language cannot drift from the first in
 * its listener and lifetime handling (docs/AUDIT.md D1).</p>
 *
 * <p>1.12.2 differences preserved from the JavaScript factory
 * (docs/PORTING-1.12.2.md §6.17, §8.8): errors are tracked by
 * {@link ScriptErrorTracker}, and every compiled script owns exactly one disk
 * listener which fans out to all of its consumers through a
 * {@link ScriptInvalidationDispatcher}.</p>
 *
 * @author rubensworks
 */
public final class JsScriptCompiler {

    private JsScriptCompiler() {
    }

    /**
     * @return The server-side scripting data.
     * @throws EvaluationException When there is none, i.e. on the client side and
     *         very early in server start-up (decision C6).
     */
    public static IScriptingData requireScriptingData() throws EvaluationException {
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.no_scripting_data"));
        }
        return scriptingData;
    }

    /**
     * Read a script's text from the network.
     * @param scriptingData The scripting data.
     * @param disk The disk id.
     * @param path The script path relative to the disk.
     * @return The script content.
     * @throws EvaluationException When the disk does not contain the path: the
     *         facade can be validated against a disk that no longer has the file.
     */
    public static String requireScriptContent(IScriptingData scriptingData, int disk, Path path)
            throws EvaluationException {
        String scriptContent = scriptingData.getScripts(disk).get(path);
        if (scriptContent == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.path_not_in_network", disk, path.toString()));
        }
        return scriptContent;
    }

    /**
     * Evaluate JavaScript in a fresh sandbox and wrap it as a script.
     * @param scriptingData The scripting data that owns the stdout/stderr streams
     *                      and the change listeners.
     * @param disk The disk id.
     * @param path The script path relative to the disk.
     * @param languageId The GraalVM language to evaluate with, normally {@code js}.
     * @param javaScript The JavaScript to evaluate.
     * @param memories The addressed memories of the network this script belongs to.
     * @return The compiled script.
     * @throws EvaluationException When the JavaScript does not compile or throws
     *         while it runs.
     */
    public static IScript compile(IScriptingData scriptingData, int disk, Path path,
                                  String languageId, String javaScript,
                                  Map<String, DimPos> memories) throws EvaluationException {
        // Construct stdout and stderr output streams
        Pair<OutputStream, OutputStream> outputStreams = scriptingData.getOutputStreams(disk, path);

        // Construct the Graal context
        Context graalContext = ScriptHelpers.createPopulatedContext(contextBuilder -> contextBuilder
                .out(outputStreams.getLeft())
                .err(outputStreams.getRight()), memories);
        Value languageBinding = graalContext.getBindings("js");

        AtomicBoolean contextClosed = new AtomicBoolean(false);
        try {
            // Read the script
            Source source = Source.newBuilder(languageId, javaScript, path.toString()).build();
            try {
                graalContext.eval(source);
            } catch (PolyglotException e) {
                closeQuietly(graalContext, contextClosed);
                throw ScriptErrorTracker.track(new EvaluationException(L10NHelpers.localize(
                        "script.integratedscripting.error.script_read",
                        path.toString(), disk, e.getMessage())), disk, path);
            }

            // One disk listener per compiled script. It releases the context and
            // notifies every consumer exactly once when this path changes.
            final IScriptingData.IDiskScriptsChangeListener[] diskListener = new IScriptingData.IDiskScriptsChangeListener[1];
            ScriptInvalidationDispatcher invalidation = new ScriptInvalidationDispatcher(() -> {
                if (diskListener[0] != null) {
                    scriptingData.removeListener(disk, diskListener[0]);
                    diskListener[0] = null;
                }
                closeQuietly(graalContext, contextClosed);
            });
            diskListener[0] = scriptPathRelative -> {
                // Disk listeners see every change in the disk, so filter by path.
                if (scriptPathRelative.equals(path)) {
                    invalidation.releaseAndNotify();
                }
            };
            scriptingData.addListener(disk, diskListener[0]);

            return new GraalScript(graalContext, languageBinding, invalidation, disk, path, null);
        } catch (IOException e) {
            closeQuietly(graalContext, contextClosed);
            throw ScriptErrorTracker.track(new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.script_read",
                    path.toString(), disk, e.getMessage())), disk, path);
        } catch (RuntimeException e) {
            closeQuietly(graalContext, contextClosed);
            throw e;
        }
    }

    /**
     * Close a Graal context exactly once, ignoring the failure modes that are
     * expected when a script is released while it is still being evaluated.
     * @param context The context to close.
     * @param closed A flag guarding against a second close.
     */
    public static void closeQuietly(Context context, AtomicBoolean closed) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            context.close(true);
        } catch (RuntimeException e) {
            // The context may still be executing on another thread; nothing else to do.
        }
    }

}
