package com.mpp.integratedscripting.core.language.typescript;

import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * End-to-end check of the TypeScript pipeline on the runtime that ships with the
 * mod: compile with Translanguage, then evaluate the output in GraalJS 21.3.0,
 * which is the same engine a script gets in game.
 *
 * <p>This is the contract that matters - "a {@code .ts} script is a {@code .js}
 * script after compilation" - and it catches a transpiler that emits syntax the
 * bundled engine cannot parse (docs/TYPESCRIPT-TRANSLANGUAGE.md §2.2).</p>
 */
public class TypeScriptExecutionTest {

    @Test
    public void testCompiledScriptRunsInTheModsEngine() throws Exception {
        String source = String.join("\n",
                "interface Item { size: number; stackable: boolean }",
                "enum Mode { A, B = 5, C }",
                "",
                "class Box {",
                "  value: number = 1;",
                "  constructor(public label: string, private count: number) {}",
                "  describe(): string { return this.label + ':' + this.count; }",
                "}",
                "",
                "const items: Item[] = [{ size: 16, stackable: true }, { size: 1, stackable: false }];",
                "function read(item?: Item): number { return item?.size ?? 0; }",
                "const box = new Box('x', 2);",
                "const answer: number = 42;");

        String javaScript = TypeScriptCompiler.toJavaScript("script.ts", source);

        try (Context context = ScriptHelpers.createBaseContext(null)) {
            context.eval("js", javaScript);
            Value bindings = context.getBindings("js");

            // Enums are real runtime objects with reverse mappings.
            assertEquals(5, bindings.getMember("Mode").getMember("B").asInt());
            assertEquals("C", bindings.getMember("Mode").getMember(String.valueOf(6)).asString());
            // Class fields and constructor parameter properties survive.
            assertEquals(1, bindings.getMember("box").getMember("value").asInt());
            assertEquals("x:2", bindings.getMember("box").invokeMember("describe").asString());
            // Optional chaining and the nullish coalescing operator survive.
            assertEquals(0, bindings.getMember("read").execute().asInt());
            assertEquals(16, bindings.getMember("read").execute(
                    bindings.getMember("items").getArrayElement(0)).asInt());
            // Plain values are plain values.
            assertEquals(42, bindings.getMember("answer").asInt());
        }
    }

    @Test
    public void testExportedDeclarationsRemainVisibleAsGlobals() throws Exception {
        String source = String.join("\n",
                "export const answer: number = 42;",
                "export function greet(name: string): string { return 'hi ' + name; }",
                "export class Thing { constructor(public id: number) {} }");

        String javaScript = TypeScriptCompiler.toJavaScript("globals.ts", source);

        try (Context context = ScriptHelpers.createBaseContext(null)) {
            context.eval("js", javaScript);
            Value bindings = context.getBindings("js");
            assertEquals(42, bindings.getMember("answer").asInt());
            assertEquals("hi bob", bindings.getMember("greet").execute("bob").asString());
            assertEquals(7, bindings.getMember("Thing").newInstance(7)
                    .getMember("id").asInt());
        }
    }

    @Test
    public void testTypeOnlyScriptEvaluatesWithoutDoingAnything() throws Exception {
        String source = String.join("\n",
                "import type { Unused } from './types';",
                "export interface Shape { id: number }",
                "export type Alias = string | number;");

        String javaScript = TypeScriptCompiler.toJavaScript("empty.ts", source);
        assertTrue("A declaration-only file must not carry module syntax: " + javaScript,
                !javaScript.contains("export") && !javaScript.contains("import"));

        try (Context context = ScriptHelpers.createBaseContext(null)) {
            // Must not throw: an empty classic script is a legal program.
            context.eval("js", javaScript);
        }
    }

}
