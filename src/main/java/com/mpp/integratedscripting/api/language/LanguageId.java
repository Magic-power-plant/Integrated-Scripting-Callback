package com.mpp.integratedscripting.api.language;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Stable identity of a scripting language, in the same {@code namespace:name}
 * form as Minecraft resource locations.
 *
 * <p>A language handler's display name may change, but its id must stay stable:
 * third-party mods attach services and completion contributors to an id, and
 * the terminal editor caches the resolved service bundle per file by id.
 * Language ids are normalised to lower case.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class LanguageId implements Comparable<LanguageId> {

    /**
     * Namespace used by this mod's own languages; third-party mods pass their
     * own mod id.
     */
    public static final String DEFAULT_NAMESPACE = "integratedscriptingcallback";

    /** The built-in JavaScript language. */
    public static final LanguageId JAVASCRIPT = LanguageId.of(DEFAULT_NAMESPACE, "javascript");

    /**
     * The built-in TypeScript language: a {@code .ts} script is compiled to
     * JavaScript before it is evaluated.
     */
    public static final LanguageId TYPESCRIPT = LanguageId.of(DEFAULT_NAMESPACE, "typescript");

    /** Fallback language used when a file has no registered handler. */
    public static final LanguageId PLAIN_TEXT = LanguageId.of(DEFAULT_NAMESPACE, "plaintext");

    private final String namespace;
    private final String name;

    private LanguageId(String namespace, String name) {
        this.namespace = namespace;
        this.name = name;
    }

    /**
     * Create a language id from a namespace and a name.
     * @param namespace The owning mod id, for example {@code mymod}.
     * @param name The language name inside that namespace, for example {@code mylang}.
     * @return The language id.
     * @throws IllegalArgumentException When either part is empty or contains characters
     *         that are not allowed in a resource location.
     */
    public static LanguageId of(String namespace, String name) {
        return new LanguageId(validate("namespace", namespace), validate("name", name));
    }

    /**
     * Parse a language id in {@code namespace:name} form.
     * @param value The textual id.
     * @return The language id.
     * @throws IllegalArgumentException When the value is not a valid id.
     */
    public static LanguageId parse(@Nullable String value) {
        if (value == null) {
            throw new IllegalArgumentException("A language id must not be null.");
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1 || value.indexOf(':', separator + 1) >= 0) {
            throw new IllegalArgumentException(
                    "A language id must have the form 'namespace:name', but was '" + value + "'.");
        }
        return of(value.substring(0, separator), value.substring(separator + 1));
    }

    /**
     * Turn a display name into a stable id part: lower case, with every run of
     * characters that is not allowed in an id replaced by a single {@code '-'}.
     * @param displayName A human readable language name.
     * @return A slug that {@link #of(String, String)} accepts, or {@code "language"}
     *         when the input contains no usable character.
     */
    public static String slug(@Nullable String displayName) {
        String value = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(value.length());
        boolean separator = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-') {
                builder.append(c);
                separator = false;
            } else if (!separator && builder.length() > 0) {
                builder.append('-');
                separator = true;
            }
        }
        // Trailing separators are not part of a valid id.
        while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '-') {
            builder.deleteCharAt(builder.length() - 1);
        }
        String slug = builder.toString();
        return slug.isEmpty() ? "language" : slug;
    }

    private static String validate(String part, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("The language id " + part + " must not be empty.");
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = Character.toLowerCase(c);
            } else if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-')) {
                throw new IllegalArgumentException("The language id " + part + " '" + value
                        + "' contains an invalid character '" + c
                        + "'; only lower-case letters, digits, '_', '.' and '-' are allowed.");
            }
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * @return The mod id that owns this language.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * @return The language name inside its namespace.
     */
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanguageId)) {
            return false;
        }
        LanguageId that = (LanguageId) other;
        return namespace.equals(that.namespace) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return 31 * namespace.hashCode() + name.hashCode();
    }

    @Override
    public int compareTo(LanguageId other) {
        int byNamespace = namespace.compareTo(other.namespace);
        return byNamespace != 0 ? byNamespace : name.compareTo(other.name);
    }

}
