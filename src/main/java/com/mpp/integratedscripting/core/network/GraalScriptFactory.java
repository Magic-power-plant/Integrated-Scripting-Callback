package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instantiates scripts using GraalVM.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §6.17, §8.8): errors are tracked
 * by {@link ScriptErrorTracker} instead of the weak-reference resolution helper,
 * and every compiled script owns exactly one disk listener that fans out to all
 * of its consumers through a {@link ScriptInvalidationDispatcher}. The Graal
 * {@link Context} is closed when that listener fires, i.e. when this compiled
 * version of the script is really dead, and never when a single consumer merely
 * detaches itself (docs/AUDIT.md D1).</p>
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
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            // Client-side (and very early server-side) there is no script data (C6).
            throw new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.no_scripting_data"));
        }

        // A missing file must produce a readable error instead of a NullPointerException
        // from Source.newBuilder (audit follow-up): the facade can be validated against
        // a disk that no longer contains the script.
        String scriptContent = scriptingData.getScripts(disk).get(path);
        if (scriptContent == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.path_not_in_network", disk, path.toString()));
        }

        // Construct stdout and stderr output streams
        Pair<OutputStream, OutputStream> outputStreams = scriptingData.getOutputStreams(disk, path);

        // Construct the Graal context
        Context graalContext = ScriptHelpers.createPopulatedContext(contextBuilder -> contextBuilder
                .out(outputStreams.getLeft())
                .err(outputStreams.getRight()));
        Value languageBinding = graalContext.getBindings("js");

        AtomicBoolean contextClosed = new AtomicBoolean(false);
        try {
            // Read the script
            Source source = Source.newBuilder(this.languageId, scriptContent, path.toString()).build();
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
    private static void closeQuietly(Context context, AtomicBoolean closed) {
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
