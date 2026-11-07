package com.mpp.integratedscripting.evaluate;

import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.ResourceLimits;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Helpers for creating the sandboxed GraalJS context used by scripts.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §8.1): {@code allowInnerContextOptions} does not exist
 * in GraalVM 21.3.0, and neither does
 * {@code HostAccess.Builder#allowAccessInheritance}; unresolved errors are
 * localized immediately and rate-limited through {@link ScriptErrorTracker}
 * instead of {@code EvaluationExceptionResolutionHelpers}.</p>
 *
 * @author rubensworks
 */
public class ScriptHelpers {

    /**
     * The bare global that holds the only memory of the network, if there is
     * exactly one. It matches the way players refer to the memory's card.
     */
    public static final String MEMORY_CARD_GLOBAL = "内存器变量卡";

    private static final Engine ENGINE = Engine
            .newBuilder()
            .option("engine.WarnInterpreterOnly", "false")
            .build();

    /**
     * @return The shared Graal engine.
     */
    public static Engine getEngine() {
        return ENGINE;
    }

    public static Context createBaseContext(
            @Nullable Function<Context.Builder, Context.Builder> contextBuilderModifier) {
        Context.Builder contextBuilder = Context
                .newBuilder()
                .engine(ENGINE)
                .allowCreateProcess(GeneralConfig.graalAllowCreateProcess)
                .allowCreateThread(GeneralConfig.graalAllowCreateThread)
                .allowIO(GeneralConfig.graalAllowIo)
                .allowHostClassLoading(GeneralConfig.graalAllowHostClassLoading)
                .allowExperimentalOptions(GeneralConfig.graalAllowExperimentalOptions)
                .allowEnvironmentAccess(GeneralConfig.graalAllowEnvironment
                        ? EnvironmentAccess.INHERIT : EnvironmentAccess.NONE)
                .allowNativeAccess(GeneralConfig.graalAllowNative)
                .allowHostAccess(HostAccess.newBuilder()
                        .allowPublicAccess(GeneralConfig.graalAllowHostPublicAccess)
                        .allowAllImplementations(GeneralConfig.graalAllowHostAllImplementations)
                        .allowAllClassImplementations(GeneralConfig.graalAllowHostAllClassImplementations)
                        .allowArrayAccess(GeneralConfig.graalAllowHostArrayAccess)
                        .allowListAccess(GeneralConfig.graalAllowHostListAccess)
                        .allowBufferAccess(GeneralConfig.graalAllowHostBufferAccess)
                        .allowIterableAccess(GeneralConfig.graalAllowHostIterableAccess)
                        .allowIteratorAccess(GeneralConfig.graalAllowHostIteratorAccess)
                        .allowMapAccess(GeneralConfig.graalAllowHostMapAccess)
                        .build());
        // 1.12.2 / GraalVM 21.3.0: no allowInnerContextOptions, no allowAccessInheritance.

        if (GeneralConfig.graalStatementLimit > 0) {
            contextBuilder = contextBuilder.resourceLimits(ResourceLimits.newBuilder()
                    .statementLimit(GeneralConfig.graalStatementLimit, null)
                    .build());
        }
        if (contextBuilderModifier != null) {
            contextBuilder = contextBuilderModifier.apply(contextBuilder);
        }
        return contextBuilder.build();
    }

    public static Context createPopulatedContext(
            @Nullable Function<Context.Builder, Context.Builder> contextBuilderModifier)
            throws EvaluationException {
        return createPopulatedContext(contextBuilderModifier, Collections.<String, DimPos>emptyMap());
    }

    /**
     * Create the script sandbox, including the addressed memories of the network
     * the script is compiled for.
     *
     * <p>A memory is exposed as the value of its memory card: the
     * {@code addressed_memory} object value, on which {@code get} and
     * {@code set} are available as methods. The object is reachable as</p>
     * <ul>
     *   <li>{@code idContext.memories.<name>} for every memory of the network,</li>
     *   <li>{@code idContext.memory} when the network has exactly one memory, and</li>
     *   <li>{@code 内存器变量卡} in that same single-memory case, which is the
     *       name the memory's own variable card is usually referred to by.</li>
     * </ul>
     *
     * @param contextBuilderModifier An optional context builder modifier.
     * @param memories The addressed memories of the network, keyed by stable name.
     * @return A populated Graal context.
     * @throws EvaluationException When the context could not be created.
     */
    public static Context createPopulatedContext(
            @Nullable Function<Context.Builder, Context.Builder> contextBuilderModifier,
            @Nullable Map<String, DimPos> memories) throws EvaluationException {
        Context context = createBaseContext(contextBuilderModifier);

        // Create the idContext field with the synthesized global operators (§5.4).
        Value jsBindings = context.getBindings("js");
        Value jsObjectClass = jsBindings.getMember("Object");
        Value idContext = jsObjectClass.newInstance();
        Value ops = jsObjectClass.newInstance();
        for (Map.Entry<String, IOperator> entry : OperatorBindings.getGlobalOperators().entrySet()) {
            ops.putMember(entry.getKey(), ValueTranslators.REGISTRY.translateToGraal(
                    context,
                    ValueTypeOperator.ValueOperator.of(entry.getValue()),
                    getDummyEvaluationExceptionFactory()));
        }
        idContext.putMember("ops", ops);
        jsBindings.putMember("idContext", idContext);

        bindMemories(context, jsBindings, idContext, jsObjectClass, memories);

        return context;
    }

    /**
     * Add the memory bindings to an already created sandbox.
     */
    private static void bindMemories(Context context, Value jsBindings, Value idContext, Value jsObjectClass,
                                     @Nullable Map<String, DimPos> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        List<String> names = new ArrayList<>(memories.keySet());
        Collections.sort(names);

        Value memoryObject = jsObjectClass.newInstance();
        Value single = null;
        int index = 0;
        for (String name : names) {
            Value memoryValue;
            try {
                memoryValue = ValueTranslators.REGISTRY.translateToGraal(context,
                        AddressedMemoryValues.of(memories.get(name)),
                        getDummyEvaluationExceptionFactory());
            } catch (EvaluationException e) {
                // A missing translator must never prevent a script from compiling.
                IntegratedScriptingCallback.clog(Level.WARN,
                        "Could not expose addressed memory " + name + " to scripts: " + e.getMessage());
                continue;
            }
            memoryObject.putMember(name, memoryValue);
            memoryObject.putMember(String.valueOf(index++), memoryValue);
            if (single == null) {
                single = memoryValue;
            }
        }
        idContext.putMember("memories", memoryObject);

        if (names.size() == 1 && single != null) {
            idContext.putMember("memory", single);
            // The memory's own variable card is what players call the memory; make
            // that exact name usable as a bare global in scripts.
            jsBindings.putMember(MEMORY_CARD_GLOBAL, single);
        }
    }

    @Nullable
    public static String getPathExtension(Path path) {
        if (path == null || path.getFileName() == null) {
            return null;
        }
        String fileName = path.getFileName().toString();
        int dotPos = fileName.lastIndexOf('.');
        if (dotPos >= 0 && dotPos + 1 < fileName.length()) {
            return fileName.substring(dotPos + 1).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    public static IEvaluationExceptionFactory getDummyEvaluationExceptionFactory() {
        return EvaluationException::new;
    }

    /**
     * @param disk The disk id.
     * @param path The script path relative to the disk.
     * @param member The script member, or null for the root script.
     * @return An exception factory that localizes and rate-limit logs script errors.
     */
    public static IEvaluationExceptionFactory getEvaluationExceptionFactory(int disk, Path path,
                                                                           @Nullable String member) {
        String memberName = member == null ? "" : member;
        String pathString = path == null ? "" : path.toString();
        return message -> ScriptErrorTracker.track(new EvaluationException(L10NHelpers.localize(
                "script.integratedscripting.error.script_exec",
                memberName, pathString, disk, message)), disk, path);
    }

    /**
     * @return A short description of the runtime, for diagnostics and logs.
     */
    public static String getRuntimeDescription() {
        return "GraalVM 21.3.0 (sandbox: statements=" + GeneralConfig.graalStatementLimit
                + ", nbtScalarWrapper=" + GeneralConfig.nbtScalarWrapper + ")";
    }

}
