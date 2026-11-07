package com.mpp.integratedscripting.core.language;

import com.mpp.IntegratedScriptingCallback;
import org.apache.logging.log4j.Level;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Signatures for the ID operators whose generic declaration is less precise
 * than the operator itself.
 *
 * <p>ID 1.12.2 stores no element type for a list, so {@code list.get} can only
 * be declared as {@code (arg0: any[], arg1: number) => any} by inspecting the
 * registry. The operators below do have a usable contract, and TypeScript can
 * express it with generics or a concrete element type. Every entry also carries
 * the input and output shapes it was written for; when ID changes an operator,
 * the entry stops matching and the generator falls back to the generic
 * declaration instead of publishing a wrong signature.</p>
 *
 * <p>Keys are the script names the generator emits (see
 * {@code operator-bindings.log}); object types appear under the interface name
 * they are declared as, for example {@code ValueItemstack}.</p>
 *
 * @author rubensworks
 */
final class TypingsSignatures {

    private static final class Override {

        private final String inputs;
        private final String output;
        private final String signature;

        private Override(String inputs, String output, String signature) {
            this.inputs = inputs;
            this.output = output;
            this.signature = signature;
        }

    }

    private static final Map<String, Override> GLOBAL = new HashMap<>();
    private static final Map<String, Override> METHODS = new HashMap<>();

    static {
        // ------------------------------------------------------------------
        // Lists: ID's list carries its element type only at runtime, so the
        // relationships are expressed with a generic parameter.
        // ------------------------------------------------------------------
        global("listAppend", "list,any", "list", "<T>(list: T[], element: T) => T[]");
        global("listConcat", "list,list", "list", "<T>(left: T[], right: T[]) => T[]");
        global("listContains", "list,any", "boolean", "<T>(list: T[], element: T) => boolean");
        global("listContainsP", "list,operator", "boolean",
                "<T>(list: T[], predicate: (element: T) => boolean) => boolean");
        global("listCount", "list,any", "number", "<T>(list: T[], element: T) => number");
        global("listCountP", "list,operator", "number",
                "<T>(list: T[], predicate: (element: T) => boolean) => number");
        global("listGet", "list,number", "any", "<T>(list: T[], index: number) => T");
        global("listGetOrDefault", "list,number,any", "any",
                "<T>(list: T[], index: number, fallback: T) => T");
        global("listHead", "list", "any", "<T>(list: T[]) => T");
        global("listLazybuilt", "any,operator", "list",
                "<T>(start: T, next: (previous: T) => T) => T[]");
        global("listSlice", "list,number,number", "list",
                "<T>(list: T[], from: number, to: number) => T[]");
        global("listTail", "list", "list", "<T>(list: T[]) => T[]");
        global("listUniq", "list", "list", "<T>(list: T[]) => T[]");
        global("listUniqP", "list,operator", "list",
                "<T>(list: T[], key: (element: T) => any) => T[]");

        // ------------------------------------------------------------------
        // Operator values: `Operator` is callable, `Function` is not typed.
        // ------------------------------------------------------------------
        global("operatorApply", "operator,any", "any",
                "<T, R>(operator: (argument: T) => R, argument: T) => R");
        global("operatorApply2", "operator,any,any", "any",
                "<A, B, R>(operator: (first: A, second: B) => R, first: A, second: B) => R");
        global("operatorApply3", "operator,any,any,any", "any",
                "<A, B, C, R>(operator: (first: A, second: B, third: C) => R,"
                        + " first: A, second: B, third: C) => R");
        global("operatorFilter", "operator,list", "list",
                "<T>(predicate: (element: T) => boolean, list: T[]) => T[]");
        global("operatorMap", "operator,list", "list",
                "<T, R>(mapper: (element: T) => R, list: T[]) => R[]");
        global("operatorReduce", "operator,list,any", "any",
                "<T, R>(reducer: (accumulator: R, element: T) => R, list: T[], initial: R) => R");
        global("operatorReduce1", "operator,list", "any",
                "<T>(reducer: (accumulator: T, element: T) => T, list: T[]) => T");

        // ------------------------------------------------------------------
        // NBT: a nested compound instead of an untyped object, and the typed
        // list accessors.
        // ------------------------------------------------------------------
        global("nbtKeys", "nbt", "list", "(nbt: NbtCompound) => string[]");
        global("nbtValuelistbyte", "nbt,string", "list",
                "(nbt: NbtCompound, key: string) => number[]");
        global("nbtValuelistint", "nbt,string", "list",
                "(nbt: NbtCompound, key: string) => number[]");
        global("nbtValuelisttag", "nbt,string", "list",
                "(nbt: NbtCompound, key: string) => NbtCompound[]");
        global("nbtWithlistbyte", "nbt,string,list", "nbt",
                "(nbt: NbtCompound, key: string, values: number[]) => NbtCompound");
        global("nbtWithlistint", "nbt,string,list", "nbt",
                "(nbt: NbtCompound, key: string, values: number[]) => NbtCompound");
        global("nbtWithlistlong", "nbt,string,list", "nbt",
                "(nbt: NbtCompound, key: string, values: number[]) => NbtCompound");
        global("nbtWithlisttag", "nbt,string,list", "nbt",
                "(nbt: NbtCompound, key: string, values: NbtCompound[]) => NbtCompound");

        // ------------------------------------------------------------------
        // Strings that are known to produce string lists.
        // ------------------------------------------------------------------
        global("stringJoin", "string,list", "string", "(separator: string, list: string[]) => string");
        global("stringOredict", "string", "list", "(name: string) => ValueItemstack[]");
        global("stringSplitOn", "string,string", "list",
                "(text: string, separator: string) => string[]");
        global("stringSplitOnRegex", "string,string", "list",
                "(text: string, pattern: string) => string[]");
        global("stringRegexGroups", "string,string", "list",
                "(text: string, pattern: string) => string[]");
        global("stringRegexScan", "string,number,string", "list",
                "(text: string, start: number, pattern: string) => string[]");
        global("itemstackItemlistcount", "list,ValueItemstack", "number",
                "(list: ValueItemstack[], item: ValueItemstack) => number");

        // ------------------------------------------------------------------
        // The global variants of the object accessors return the same
        // homogeneous lists as the methods below.
        // ------------------------------------------------------------------
        global("entityArmorinventory", "ValueEntity", "list",
                "(entity: ValueEntity) => ValueItemstack[]");
        global("entityEntityfluids", "ValueEntity", "list",
                "(entity: ValueEntity) => ValueFluidstack[]");
        global("entityEntityitems", "ValueEntity", "list",
                "(entity: ValueEntity) => ValueItemstack[]");
        global("entityInventory", "ValueEntity", "list",
                "(entity: ValueEntity) => ValueItemstack[]");
        global("entityMounted", "ValueEntity", "list", "(entity: ValueEntity) => ValueEntity[]");
        global("ingredientsEnergies", "ValueIngredients", "list",
                "(ingredients: ValueIngredients) => number[]");
        global("ingredientsFluids", "ValueIngredients", "list",
                "(ingredients: ValueIngredients) => ValueFluidstack[]");
        global("ingredientsItems", "ValueIngredients", "list",
                "(ingredients: ValueIngredients) => ValueItemstack[]");
        global("itemstackInventory", "ValueItemstack", "list",
                "(itemstack: ValueItemstack) => ValueItemstack[]");
        global("itemstackOredict", "ValueItemstack", "list",
                "(itemstack: ValueItemstack) => ValueItemstack[]");

        // ------------------------------------------------------------------
        // Object methods whose list really is homogeneous.
        // ------------------------------------------------------------------
        method("ValueItemstack", "inventory", "", "list", "() => ValueItemstack[]");
        method("ValueItemstack", "oredict", "", "list", "() => ValueItemstack[]");

        method("ValueEntity", "inventory", "", "list", "() => ValueItemstack[]");
        method("ValueEntity", "armorinventory", "", "list", "() => ValueItemstack[]");
        method("ValueEntity", "entityitems", "", "list", "() => ValueItemstack[]");
        method("ValueEntity", "entityfluids", "", "list", "() => ValueFluidstack[]");
        method("ValueEntity", "mounted", "", "list", "() => ValueEntity[]");

        method("ValueIngredients", "items", "", "list", "() => ValueItemstack[]");
        method("ValueIngredients", "fluids", "", "list", "() => ValueFluidstack[]");
        method("ValueIngredients", "energies", "", "list", "() => number[]");
        method("ValueIngredients", "withitem", "number,ValueItemstack", "ValueIngredients",
                "(index: number, item: ValueItemstack) => ValueIngredients");
        method("ValueIngredients", "withitems", "list", "ValueIngredients",
                "(items: ValueItemstack[]) => ValueIngredients");
        method("ValueIngredients", "withfluid", "number,ValueFluidstack", "ValueIngredients",
                "(index: number, fluid: ValueFluidstack) => ValueIngredients");
        method("ValueIngredients", "withfluids", "list", "ValueIngredients",
                "(fluids: ValueFluidstack[]) => ValueIngredients");
        method("ValueIngredients", "withenergy", "number,number", "ValueIngredients",
                "(index: number, energy: number) => ValueIngredients");
        method("ValueIngredients", "withenergies", "list", "ValueIngredients",
                "(energies: number[]) => ValueIngredients");
    }

    private TypingsSignatures() {
    }

    /**
     * @param name The script name of a global operator.
     * @param inputs The operator's inputs.
     * @param output The operator's output.
     * @return The curated signature, or null when there is none or it no longer
     *         matches ID's operator.
     */
    @Nullable
    static String global(String name, List<TypingsType> inputs, TypingsType output) {
        return apply(GLOBAL.get(name), "operator " + name, inputs, output);
    }

    /**
     * @param ownerInterface The interface the method is declared on.
     * @param name The script name of the method.
     * @param inputs The operator's inputs without the receiver.
     * @param output The operator's output.
     * @return The curated signature, or null when there is none or it no longer
     *         matches ID's operator.
     */
    @Nullable
    static String method(String ownerInterface, String name, List<TypingsType> inputs, TypingsType output) {
        return apply(METHODS.get(ownerInterface + "." + name),
                "method " + ownerInterface + "." + name, inputs, output);
    }

    @Nullable
    private static String apply(@Nullable Override override, String id,
                                List<TypingsType> inputs, TypingsType output) {
        if (override == null) {
            return null;
        }
        String actual = shapes(inputs);
        if (!override.inputs.equals(actual) || !override.output.equals(output.shape)) {
            IntegratedScriptingCallback.clog(Level.DEBUG, "Ignoring the typings override for " + id
                    + ": ID now declares (" + actual + ") => " + output.shape
                    + ", the override was written for (" + override.inputs + ") => "
                    + override.output + ". Falling back to the generic signature.");
            return null;
        }
        return override.signature;
    }

    private static String shapes(List<TypingsType> types) {
        StringBuilder builder = new StringBuilder();
        for (TypingsType type : types) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(type.shape);
        }
        return builder.toString();
    }

    private static void global(String name, String inputs, String output, String signature) {
        put(GLOBAL, name, new Override(inputs, output, signature));
    }

    private static void method(String owner, String name, String inputs, String output, String signature) {
        put(METHODS, owner + "." + name, new Override(inputs, output, signature));
    }

    private static void put(Map<String, Override> target, String key, Override override) {
        if (target.put(key, override) != null) {
            throw new IllegalStateException("Duplicate typings override for " + key);
        }
    }

}
