package com.mpp.integratedscripting.callback;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mpp.IntegratedScriptingCallback;
import org.apache.logging.log4j.Level;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.operator.Operators;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 1.12.2-only: synthesizes the script-visible global operator table and the
 * per-object-type method table from {@link Operators#REGISTRY}.
 *
 * <p>The 1.20 reference implementation reads ID's interact operator registries
 * ({@code getGlobalInteractOperators} / {@code getScopedInteractOperators}).
 * Those do not exist in ID 1.12.2 (docs/PORTING-1.12.2.md §5.4), so names are
 * derived from each operator's translation key:</p>
 *
 * <pre>
 * operator.operators.&lt;modid&gt;.&lt;category&gt;.&lt;part1&gt;.&lt;partN&gt;.name
 * global name = lowerFirst(camel(category)) + camel(parts...)
 * method name = lowerFirst(camel(parts...))
 * </pre>
 *
 * <p>Conflicts are resolved deterministically by
 * {@code (input type count, unique name)}; every resolved alias can be
 * overridden through
 * {@code assets/integratedscriptingcallback/operator-aliases.json}.</p>
 *
 * @author rubensworks
 */
public final class OperatorBindings {

    public static final String ALIASES_RESOURCE = "/assets/integratedscriptingcallback/operator-aliases.json";

    private static final Map<String, IOperator> GLOBAL = Maps.newLinkedHashMap();
    private static final Map<IValueType<?>, Map<String, IOperator>> SCOPED = Maps.newIdentityHashMap();
    private static final Set<String> RESERVED = Sets.newHashSet(
            "__proto__", "constructor", "prototype", "hasOwnProperty", "isPrototypeOf",
            "propertyIsEnumerable", "toLocaleString", "toString", "valueOf");

    private static boolean loaded = false;

    private OperatorBindings() {
    }

    /**
     * Build the binding tables once. Safe to call from any thread.
     */
    public static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;

        List<IOperator> operators = Lists.newArrayList(Operators.REGISTRY.getOperators());
        operators.sort(Comparator.comparing(IOperator::getUniqueName));

        // Group by derived global name
        Map<String, List<IOperator>> byGlobalName = Maps.newLinkedHashMap();
        for (IOperator operator : operators) {
            String globalName = sanitize(globalNameFor(operator.getTranslationKey()));
            byGlobalName.computeIfAbsent(globalName, k -> Lists.newArrayList()).add(operator);
        }
        for (Map.Entry<String, List<IOperator>> entry : byGlobalName.entrySet()) {
            List<IOperator> candidates = entry.getValue();
            candidates.sort(operatorComparator());
            if (candidates.size() > 1) {
                IntegratedScriptingCallback.clog(Level.INFO, String.format(
                        "Operator name conflict for '%s': %s (using %s)",
                        entry.getKey(), describe(candidates), candidates.get(0).getUniqueName()));
            }
            GLOBAL.put(entry.getKey(), candidates.get(0));
        }

        applyAliases();

        // Build object method tables from the (possibly aliased) global table.
        Set<IOperator> boundOperators = new LinkedHashSet<>(GLOBAL.values());
        Map<IValueType<?>, Map<String, List<IOperator>>> scopedCandidates = Maps.newIdentityHashMap();
        for (IOperator operator : boundOperators) {
            IValueType[] inputTypes = operator.getInputTypes();
            if (inputTypes.length < 1 || inputTypes[0] == null || inputTypes[0].isCategory()) {
                continue;
            }
            String methodName = sanitize(methodNameFor(operator.getTranslationKey()));
            for (IValueType<?> valueType : ValueTypes.REGISTRY.getValueTypes()) {
                if (valueType.isCategory()) {
                    continue;
                }
                if (ValueHelpers.correspondsTo(valueType, inputTypes[0])) {
                    scopedCandidates.computeIfAbsent(valueType, k -> Maps.newLinkedHashMap())
                            .computeIfAbsent(methodName, k -> Lists.newArrayList()).add(operator);
                }
            }
        }
        for (Map.Entry<IValueType<?>, Map<String, List<IOperator>>> typeEntry : scopedCandidates.entrySet()) {
            Map<String, IOperator> methods = Maps.newLinkedHashMap();
            for (Map.Entry<String, List<IOperator>> methodEntry : typeEntry.getValue().entrySet()) {
                List<IOperator> candidates = methodEntry.getValue();
                candidates.sort(operatorComparator());
                if (candidates.size() > 1) {
                    IntegratedScriptingCallback.clog(Level.DEBUG, String.format(
                            "Method name conflict for '%s' on %s: %s (using %s)",
                            methodEntry.getKey(), typeEntry.getKey().getTranslationKey(),
                            describe(candidates), candidates.get(0).getUniqueName()));
                }
                methods.put(methodEntry.getKey(), candidates.get(0));
            }
            SCOPED.put(typeEntry.getKey(), methods);
        }

        IntegratedScriptingCallback.clog("Registered " + GLOBAL.size() + " global operators and "
                + SCOPED.size() + " scoped method sets.");
    }

    /**
     * @return The global operator table, keyed by script name.
     */
    public static Map<String, IOperator> getGlobalOperators() {
        load();
        return Collections.unmodifiableMap(GLOBAL);
    }

    /**
     * @param valueType A concrete value type, or null.
     * @return The methods available on values of the given type.
     */
    public static Map<String, IOperator> getMethods(@Nullable IValueType<?> valueType) {
        load();
        Map<String, IOperator> methods = valueType == null ? null : SCOPED.get(valueType);
        return methods == null ? Collections.<String, IOperator>emptyMap() : Collections.unmodifiableMap(methods);
    }

    /**
     * @param translationKey An operator translation key.
     * @return The derived global script name, before sanitizing.
     */
    public static String globalNameFor(String translationKey) {
        String[] parts = split(translationKey);
        String category = parts.length > 2 ? parts[1] : "general";
        String rest = parts.length > 2 ? join(parts, 2)
                : (parts.length > 0 ? parts[parts.length - 1] : "op");
        return lowerFirst(camel(category)) + camel(rest);
    }

    /**
     * @param translationKey An operator translation key.
     * @return The derived object method name, before sanitizing.
     */
    public static String methodNameFor(String translationKey) {
        String[] parts = split(translationKey);
        String rest = parts.length > 2 ? join(parts, 2) : (parts.length > 1 ? parts[1] : "op");
        return lowerFirst(camel(rest));
    }

    /**
     * @param name A candidate script identifier.
     * @return The name made safe for JavaScript and ID's operator proxies.
     */
    public static String sanitize(String name) {
        String result = name == null ? "" : name;
        if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) {
            result = "op_" + result;
        }
        if (RESERVED.contains(result)) {
            result = "op_" + result;
        }
        return result;
    }

    private static Comparator<IOperator> operatorComparator() {
        return Comparator
                .comparingInt((IOperator operator) -> operator.getInputTypes().length)
                .thenComparing(IOperator::getUniqueName);
    }

    private static String describe(Collection<IOperator> operators) {
        StringBuilder builder = new StringBuilder();
        for (IOperator operator : operators) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(operator.getUniqueName());
        }
        return builder.toString();
    }

    private static String[] split(String translationKey) {
        String key = translationKey == null ? "" : translationKey;
        if (key.startsWith("operator.operators.")) {
            key = key.substring("operator.operators.".length());
        }
        if (key.endsWith(".name")) {
            key = key.substring(0, key.length() - ".name".length());
        }
        return key.split("\\.");
    }

    private static String join(String[] parts, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (sb.length() > 0) {
                sb.append('_');
            }
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String camel(String raw) {
        StringBuilder sb = new StringBuilder();
        for (String part : raw.toLowerCase(Locale.ROOT).split("[._\\-]+")) {
            if (part.isEmpty()) {
                continue;
            }
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static String lowerFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Apply the bundled operator aliases, if the resource is present.
     * Invalid or unresolvable aliases are logged and skipped, never fatal.
     */
    private static void applyAliases() {
        InputStream stream = OperatorBindings.class.getResourceAsStream(ALIASES_RESOURCE);
        if (stream == null) {
            IntegratedScriptingCallback.clog(Level.DEBUG,
                    "No operator aliases found at " + ALIASES_RESOURCE + ".");
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            if (!root.isJsonObject()) {
                IntegratedScriptingCallback.clog(Level.WARN,
                        "Ignoring operator aliases: the root must be a JSON object.");
                return;
            }
            JsonObject aliases = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : aliases.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    // Documentation/meta keys are not aliases.
                    continue;
                }
                String alias = sanitize(entry.getKey());
                String target = entry.getValue().isJsonPrimitive()
                        ? entry.getValue().getAsString() : null;
                IOperator operator = findOperator(target);
                if (operator == null) {
                    IntegratedScriptingCallback.clog(Level.WARN, String.format(
                            "Ignoring operator alias '%s': unknown operator '%s'.", alias, target));
                    continue;
                }
                GLOBAL.put(alias, operator);
            }
        } catch (Exception e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "Failed to read operator aliases, continuing without them: " + e.getMessage());
        }
    }

    @Nullable
    private static IOperator findOperator(@Nullable String uniqueName) {
        if (uniqueName == null) {
            return null;
        }
        // Aliases may use the ID unique name, which for 1.12 base operators is the
        // translation key, or the script-visible global name.
        for (IOperator operator : Operators.REGISTRY.getOperators()) {
            if (uniqueName.equals(operator.getUniqueName())
                    || uniqueName.equals(operator.getTranslationKey())) {
                return operator;
            }
        }
        return GLOBAL.get(sanitize(uniqueName));
    }

    /**
     * Test/diagnostic hook: forget all bindings so the next call rebuilds them.
     */
    public static synchronized void reset() {
        GLOBAL.clear();
        SCOPED.clear();
        loaded = false;
    }

    /**
     * Write the resolved global and scoped bindings to
     * {@code <root>/operator-bindings.log}, so conflicts and the actual 1.12.2
     * names can be inspected without launching a debugger (§5.4.2).
     * @param root The mod data root directory.
     */
    public static void dumpBindings(java.nio.file.Path root) {
        load();
        java.nio.file.Path path = root.resolve("operator-bindings.log");
        try {
            java.nio.file.Files.createDirectories(root);
            StringBuilder builder = new StringBuilder();
            builder.append("# Global operators: script name\tID unique name\tinput types\n");
            for (Map.Entry<String, IOperator> entry : GLOBAL.entrySet()) {
                builder.append(entry.getKey()).append('\t')
                        .append(entry.getValue().getUniqueName()).append('\t')
                        .append(describeInputs(entry.getValue())).append('\n');
            }
            builder.append("\n# Scoped methods: value type\tscript name\tID unique name\n");
            for (Map.Entry<IValueType<?>, Map<String, IOperator>> typeEntry : SCOPED.entrySet()) {
                for (Map.Entry<String, IOperator> methodEntry : typeEntry.getValue().entrySet()) {
                    builder.append(typeEntry.getKey().getTranslationKey()).append('\t')
                            .append(methodEntry.getKey()).append('\t')
                            .append(methodEntry.getValue().getUniqueName()).append('\n');
                }
            }
            java.nio.file.Files.write(path,
                    builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            IntegratedScriptingCallback.clog("Wrote operator bindings to " + path + ".");
        } catch (java.io.IOException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "Failed to write operator bindings to " + path + ": " + e.getMessage());
        }
    }

    private static String describeInputs(IOperator operator) {
        StringBuilder builder = new StringBuilder();
        for (IValueType inputType : operator.getInputTypes()) {
            if (builder.length() > 0) {
                builder.append(",");
            }
            builder.append(inputType == null ? "null" : inputType.getTranslationKey());
        }
        return builder.toString();
    }

}
