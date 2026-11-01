package com.mpp.integratedscripting.api.language.diagnostics;

import javax.annotation.Nullable;

/**
 * One diagnostic reported for a document range.
 *
 * <p>The diagnostic service is not consumed by the terminal editor yet; it
 * exists so that syntax checking can be added later by registering a service,
 * without changing {@code ILanguageHandler} or the editor.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class Diagnostic {

    /**
     * Severity of a diagnostic, ordered from most to least urgent.
     */
    public enum Severity {
        /** The document cannot run. */
        ERROR,
        /** The document runs, but something is suspicious. */
        WARNING,
        /** Neutral information about the document. */
        INFO,
        /** A suggestion. */
        HINT
    }

    private final int start;
    private final int end;
    private final Severity severity;
    private final String message;
    @Nullable
    private final String source;

    /**
     * @param start The inclusive start offset.
     * @param end The exclusive end offset; must not be before {@code start}.
     * @param severity The severity.
     * @param message The human readable message.
     * @param source The producer, for example a language id or a rule name.
     */
    public Diagnostic(int start, int end, Severity severity, String message, @Nullable String source) {
        if (end < start) {
            throw new IllegalArgumentException("Invalid diagnostic range [" + start + ", " + end + ").");
        }
        this.start = start;
        this.end = end;
        this.severity = severity == null ? Severity.INFO : severity;
        this.message = message == null ? "" : message;
        this.source = source;
    }

    /**
     * @return The inclusive start offset.
     */
    public int getStart() {
        return start;
    }

    /**
     * @return The exclusive end offset.
     */
    public int getEnd() {
        return end;
    }

    /**
     * @return The severity; never {@code null}.
     */
    public Severity getSeverity() {
        return severity;
    }

    /**
     * @return The human readable message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * @return The producer of this diagnostic, or {@code null}.
     */
    @Nullable
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return severity + "[" + start + ", " + end + ") " + message;
    }

}
