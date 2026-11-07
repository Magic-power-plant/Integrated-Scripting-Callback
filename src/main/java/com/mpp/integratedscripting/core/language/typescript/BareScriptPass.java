package com.mpp.integratedscripting.core.language.typescript;

import com.pingsu.transpiler.TransformationContext;
import com.pingsu.transpiler.TransformationPass;
import com.pingsu.transpiler.syntax.SourceRange;
import com.pingsu.transpiler.syntax.Token;
import com.pingsu.transpiler.syntax.TokenKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns TypeScript module syntax into something a bare classic script can run.
 *
 * <p>This mod evaluates every script as one file in a shared global scope
 * (docs/TYPESCRIPT-FEASIBILITY.md §4.2): there is no loader, so {@code import},
 * {@code export} and {@code export =} have nowhere to go. Translanguage's
 * {@code PRESERVE} target keeps them verbatim, which is exactly what a bundler
 * wants and exactly what a script host cannot run.</p>
 *
 * <p>The pass therefore does the smallest thing that works, and it does it
 * without moving anything:</p>
 * <ul>
 *   <li>{@code export const/let/var/function/class/abstract/async/enum …} - the
 *       {@code export} keyword is replaced by spaces, so every following
 *       character keeps its line and column;</li>
 *   <li>{@code export { a, b };} and {@code export {};} - the statement is
 *       blanked out, newlines included, because the declarations it lists are
 *       already in scope;</li>
 *   <li>{@code export default expr;} - becomes {@code const __default = expr;}
 *       (the only rewrite that changes a column, on one line);</li>
 *   <li>{@code export interface/type/declare/namespace} - left to
 *       {@code TypeErasurePass}, which removes the whole declaration. Editing
 *       them here would overlap that pass's edit and Translanguage rejects
 *       overlapping edits.</li>
 * </ul>
 *
 * <p>Anything that cannot keep its meaning in a bare script is reported as a
 * {@code TS_UNSUPPORTED} diagnostic with the position of the offending keyword:
 * value imports, re-exports ({@code export * from}, {@code export { a } from})
 * and {@code export =}. Failing closed is deliberate - silently dropping code
 * would be much worse than an error at the import line.</p>
 *
 * <p>The pass is stateless and therefore safe to share across calls, as
 * {@code TypeScriptTranspiler} requires.</p>
 *
 * @author rubensworks
 */
public final class BareScriptPass implements TransformationPass {

    /** Diagnostic code for syntax this mod's bare-script model cannot host. */
    public static final String CODE = "TS_UNSUPPORTED";

    /** Name of the variable an {@code export default} expression is bound to. */
    public static final String DEFAULT_EXPORT_BINDING = "__default";

    @Override
    public void transform(TransformationContext context) {
        List<Token> tokens = significant(context.tokens());
        lowerExports(context, tokens);
        rejectImports(context, tokens);
    }

    /**
     * @param tokens Every token of the compilation, trivia included.
     * @return The non-trivia tokens, in source order (the trailing {@code EOF}
     *         included: its text is empty, so it matches nothing).
     */
    static List<Token> significant(List<Token> tokens) {
        List<Token> significant = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            if (!token.trivia()) {
                significant.add(token);
            }
        }
        return significant;
    }

    private static void lowerExports(TransformationContext context, List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.kind() != TokenKind.KEYWORD || !"export".equals(token.text())) {
                continue;
            }
            String previous = textAt(tokens, i - 1);
            String next = textAt(tokens, i + 1);
            // `obj.export` and `{ export: 1 }` are ordinary identifiers, not the keyword.
            if (".".equals(previous) || "?".equals(previous) || ":".equals(next)) {
                continue;
            }
            if ("interface".equals(next) || "type".equals(next)
                    || "declare".equals(next) || "namespace".equals(next)) {
                // Type-only declaration: TypeErasurePass removes it as a whole.
                continue;
            }
            if ("{".equals(next)) {
                lowerExportList(context, tokens, i);
            } else if ("*".equals(next)) {
                context.error(token, CODE, "re-exports (export * from '...') are not available: "
                        + "this mod evaluates every script as a bare classic script");
            } else if ("default".equals(next)) {
                context.replace(token, tokens.get(i + 1),
                        "const " + DEFAULT_EXPORT_BINDING + " =");
            } else if ("import".equals(next)) {
                context.error(token, CODE, "export = is not available: "
                        + "this mod evaluates every script as a bare classic script");
            } else {
                // `export <declaration>`: blank the keyword, keep every other offset.
                context.replace(token, spaces(token.text().length()));
            }
        }
    }

    /**
     * Drop an {@code export { … };} statement, which only matters to a module
     * system the mod does not have. Re-exports are rejected instead, because
     * dropping them would silently remove code.
     */
    private static void lowerExportList(TransformationContext context, List<Token> tokens, int exportIndex) {
        Token exportToken = tokens.get(exportIndex);
        int closing = -1;
        int depth = 0;
        for (int j = exportIndex + 1; j < tokens.size(); j++) {
            String text = tokens.get(j).text();
            if ("{".equals(text)) {
                depth++;
            } else if ("}".equals(text)) {
                depth--;
                if (depth == 0) {
                    closing = j;
                    break;
                }
            }
        }
        if (closing < 0) {
            // Unbalanced braces are a syntax error Translanguage reports itself.
            return;
        }
        if ("from".equals(textAt(tokens, closing + 1))) {
            context.error(exportToken, CODE, "re-exports (export { ... } from '...') are not "
                    + "available: this mod evaluates every script as a bare classic script");
            return;
        }
        int end = tokens.get(closing).range().end();
        if (";".equals(textAt(tokens, closing + 1))) {
            end = tokens.get(closing + 1).range().end();
        }
        SourceRange range = new SourceRange(exportToken.range().start(), end);
        try {
            context.replace(range, blankOut(context.slice(range)));
        } catch (IllegalStateException e) {
            // TypeErasurePass already edited inside this list (e.g. `export { type Foo }`);
            // the two edits overlap and Translanguage refuses to merge them.
            context.error(exportToken, CODE, "this export list mixes types and values, which cannot "
                    + "be lowered to a bare script; write 'export type' on its own line");
        }
    }

    private static void rejectImports(TransformationContext context, List<Token> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.kind() != TokenKind.KEYWORD || !"import".equals(token.text())) {
                continue;
            }
            String previous = textAt(tokens, i - 1);
            String next = textAt(tokens, i + 1);
            if (".".equals(previous) || "?".equals(previous) || ":".equals(next)) {
                continue;
            }
            if ("(".equals(next) || "type".equals(next)) {
                // Dynamic `import()` is JavaScript, and `import type` is erased.
                continue;
            }
            context.error(token, CODE, "imports are not available: this mod evaluates every "
                    + "script as a bare classic script");
        }
    }

    private static String textAt(List<Token> tokens, int index) {
        return index < 0 || index >= tokens.size() ? "" : tokens.get(index).text();
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }

    /**
     * Replace every non-line-terminator character with a space, so removing a
     * statement keeps the line count and the columns of everything after it.
     * @param text The text to blank out.
     * @return The same text with only spaces, {@code \n} and {@code \r} left.
     */
    static String blankOut(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c == '\n' || c == '\r' ? c : ' ');
        }
        return builder.toString();
    }

}
