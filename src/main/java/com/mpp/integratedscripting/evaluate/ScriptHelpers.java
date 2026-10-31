package com.mpp.integratedscripting.evaluate;

import com.mpp.GeneralConfig;
import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
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

        return context;
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
