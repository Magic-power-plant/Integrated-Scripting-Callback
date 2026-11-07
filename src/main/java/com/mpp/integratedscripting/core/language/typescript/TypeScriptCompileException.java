package com.mpp.integratedscripting.core.language.typescript;

/**
 * One reason a TypeScript script could not be turned into JavaScript, in the
 * mod's own vocabulary.
 *
 * <p>Translanguage's {@link com.pingsu.transpiler.Diagnostic} would do the job,
 * but exposing it here would drag the library into the evaluation path: the
 * network layer must stay loadable (and testable) with the transpiler absent, and
 * the messages it shows are localized through the mod's language files. So the
 * compiler translates the first diagnostic into this exception and the script
 * factory localizes it.</p>
 *
 * <p>Line and column are 1-based and refer to the {@code .ts} file the user
 * edited: the transpiler reports them against the original source, before any
 * edit, so they match the editor exactly. (A <em>runtime</em> error after a
 * successful compilation is a different story: its position is in the generated
 * JavaScript, which only drifts for a script containing an {@code enum}.)</p>
 *
 * @author rubensworks
 */
public final class TypeScriptCompileException extends Exception {

    private static final long serialVersionUID = 1L;

    /** Pseudo-code used when the transpiler threw instead of reporting a diagnostic. */
    public static final String CODE_INTERNAL = "TS_INTERNAL";

    /** Pseudo-code used when the source is larger than the configured limit. */
    public static final String CODE_TOO_LARGE = "TS_TOO_LARGE";

    private final String code;
    private final int line;
    private final int column;
    private final int diagnosticCount;

    /**
     * @param code The diagnostic code, for example {@code TS_SYNTAX}.
     * @param line The 1-based line of the first problem.
     * @param column The 1-based column of the first problem.
     * @param message The human readable reason, in English (as the transpiler
     *                reports it).
     * @param diagnosticCount How many problems were reported in total.
     */
    public TypeScriptCompileException(String code, int line, int column, String message,
                                      int diagnosticCount) {
        super(message);
        this.code = code == null ? CODE_INTERNAL : code;
        this.line = Math.max(1, line);
        this.column = Math.max(1, column);
        this.diagnosticCount = Math.max(1, diagnosticCount);
    }

    /**
     * @return The diagnostic code, for example {@code TS_SYNTAX}.
     */
    public String getCode() {
        return this.code;
    }

    /**
     * @return The 1-based line of the first problem.
     */
    public int getLine() {
        return this.line;
    }

    /**
     * @return The 1-based column of the first problem.
     */
    public int getColumn() {
        return this.column;
    }

    /**
     * @return How many problems the transpiler reported in total.
     */
    public int getDiagnosticCount() {
        return this.diagnosticCount;
    }

    @Override
    public String toString() {
        return this.code + " at " + this.line + ":" + this.column + ": " + getMessage();
    }

}
