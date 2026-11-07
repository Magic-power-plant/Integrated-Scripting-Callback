package com.mpp.integratedscripting.core.language;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * One value type as it appears in the generated TypeScript declarations.
 *
 * <p>{@link #ts} is the TypeScript type text, {@link #shape} a coarse name used
 * to recognise the operators the curated signatures in {@link TypingsSignatures}
 * were written for, and {@link #parameter} the default name of a parameter of
 * that type. Parameters used to be called {@code arg0}, {@code arg1}, ... which
 * told the IDE nothing; deriving them from the value type keeps the signature
 * readable without inventing per-operator knowledge.</p>
 *
 * @author rubensworks
 */
final class TypingsType {

    static final TypingsType ANY = new TypingsType("any", "any", "value");
    static final TypingsType NUMBER = new TypingsType("number", "number", "number");
    static final TypingsType BOOLEAN = new TypingsType("boolean", "boolean", "boolean");
    static final TypingsType STRING = new TypingsType("string", "string", "string");
    /** ID's list value is a plain JavaScript array; 1.12.2 carries no element type. */
    static final TypingsType LIST = new TypingsType("any[]", "list", "list");
    static final TypingsType NBT = new TypingsType("NbtCompound", "nbt", "nbt");
    static final TypingsType OPERATOR = new TypingsType("Operator", "operator", "operator");

    /**
     * JavaScript keywords plus the strict-mode reserved words; a parameter may
     * not use them. Type-only words ({@code type}, {@code interface}) are legal
     * identifiers but read like syntax in a signature, so they stay out as well.
     */
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "enum", "export", "extends",
            "false", "finally", "for", "function", "if", "import", "in",
            "instanceof", "new", "null", "return", "super", "switch", "this",
            "throw", "true", "try", "typeof", "var", "void", "while", "with",
            "yield", "await", "implements", "interface", "let", "package",
            "private", "protected", "public", "static", "type", "declare",
            "namespace", "abstract", "readonly", "keyof", "infer", "asserts"));

    /** The TypeScript type text, for example {@code number} or {@code ValueBlock}. */
    final String ts;

    /**
     * A coarse, unambiguous name for the type: scalars use their TypeScript
     * name, containers their ID name ({@code list}, {@code nbt}, {@code operator})
     * and object types the declared interface name. Curated signatures are only
     * applied when every shape still matches, so ID changing an operator's
     * types degrades the declaration instead of lying about it.
     */
    final String shape;

    /** The default parameter name for a value of this type. */
    final String parameter;

    private TypingsType(String ts, String shape, String parameter) {
        this.ts = ts;
        this.shape = shape;
        this.parameter = parameter;
    }

    /**
     * @param interfaceName The declared interface name of an object value type,
     *                      for example {@code ValueItemstack}.
     * @param typeName The value type's ID type name, for example {@code itemstack}.
     * @return A type that refers to that interface.
     */
    static TypingsType object(String interfaceName, String typeName) {
        return new TypingsType(interfaceName, interfaceName, identifier(typeName, "value"));
    }

    /**
     * @param raw A type name.
     * @param fallback The identifier to use when nothing usable remains.
     * @return {@code raw} lower-cased and reduced to a JavaScript identifier.
     */
    static String identifier(String raw, String fallback) {
        String name = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (Character.isJavaIdentifierPart(character)) {
                builder.append(character);
            }
        }
        String identifier = builder.toString();
        if (identifier.isEmpty() || !Character.isJavaIdentifierStart(identifier.charAt(0))) {
            return fallback;
        }
        return RESERVED.contains(identifier) ? fallback : identifier;
    }

    @Override
    public String toString() {
        return "TypingsType(" + ts + ", " + shape + ")";
    }

}
