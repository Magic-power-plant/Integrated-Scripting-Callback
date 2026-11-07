package com.mpp.integratedscripting.core.language.typescript;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link TypeScriptCompiler} and the two transformations the mod
 * adds on top of Translanguage ({@link BareScriptPass},
 * {@link BareScriptEmitter}).
 *
 * <p>They are written against the behaviour a script author observes - "my types
 * disappear, my line numbers stay, an import is an error" - rather than against
 * the transpiler's internals, so replacing the library does not require rewriting
 * them (docs/TYPESCRIPT-TRANSLANGUAGE.md §4).</p>
 */
public class TypeScriptCompilerTest {

    @Test
    public void testTypeAnnotationsAreErasedAndLinesStayPut() throws Exception {
        String source = String.join("\n",
                "interface Item { size: number; stackable: boolean }",
                "const items: Item[] = [];",
                "function pick<T extends Item>(input: T[], min: number): T[] {",
                "  return input.filter((item: T) => item.size >= min);",
                "}");
        String javaScript = TypeScriptCompiler.toJavaScript("pick.ts", source);

        assertFalse(javaScript, javaScript.contains("interface"));
        assertFalse(javaScript, javaScript.contains(": number"));
        assertFalse(javaScript, javaScript.contains("<T extends Item>"));
        // Line numbers are what an error report needs, and they are preserved:
        // erased text is removed, not re-wrapped, so no line is created or lost.
        assertEquals(lineCount(source), lineCount(javaScript));
        String[] lines = javaScript.split("\n", -1);
        assertEquals("", lines[0].trim());
        assertEquals("const items = [];", lines[1].trim());
        assertEquals("function pick(input, min) {", lines[2].trim());
        assertEquals("return input.filter((item) => item.size >= min);", lines[3].trim());
    }

    @Test
    public void testEnumIsLoweredToOneRuntimeObject() throws Exception {
        String source = String.join("\n",
                "enum Mode { A, B = 5, C }",
                "export const mode: Mode = Mode.C;");
        String javaScript = TypeScriptCompiler.toJavaScript("mode.ts", source);

        assertTrue(javaScript, javaScript.contains("(function (Mode)"));
        assertTrue(javaScript, javaScript.contains("Mode[\"B\"] = 5"));
        // The export keyword is gone but the declaration it introduced is not.
        assertFalse(javaScript, javaScript.contains("export"));
        assertTrue(javaScript, javaScript.contains("const mode = Mode.C;"));
    }

    @Test
    public void testExportKeywordIsBlankedWithoutMovingTheDeclaration() throws Exception {
        String source = "export const answer: number = 42;";
        String javaScript = TypeScriptCompiler.toJavaScript("answer.ts", source);

        assertFalse(javaScript, javaScript.contains("export"));
        // `const` keeps the column it had after `export `.
        assertEquals(source.indexOf("const"), javaScript.indexOf("const"));
    }

    @Test
    public void testExportListIsDroppedAndTheDeclarationsStay() throws Exception {
        String source = String.join("\n",
                "const a = 1;",
                "function b() { return 2; }",
                "export { a, b };");
        String javaScript = TypeScriptCompiler.toJavaScript("list.ts", source);

        assertFalse(javaScript, javaScript.contains("export"));
        assertTrue(javaScript, javaScript.contains("const a = 1;"));
        assertTrue(javaScript, javaScript.contains("function b()"));
        assertEquals(lineCount(source), lineCount(javaScript));
    }

    @Test
    public void testExportDefaultBecomesANamedBinding() throws Exception {
        String javaScript = TypeScriptCompiler.toJavaScript("default.ts",
                "export default function greet(name: string): string { return name; }");

        assertFalse(javaScript, javaScript.contains("export"));
        assertTrue(javaScript, javaScript.contains("const " + BareScriptPass.DEFAULT_EXPORT_BINDING + " ="));
        assertTrue(javaScript, javaScript.contains("function greet(name)"));
    }

    @Test
    public void testValueImportIsRejectedAtItsOwnLine() throws Exception {
        String source = String.join("\n",
                "import type { Foo } from './types';",
                "import { helper } from './helper';",
                "const value: Foo = helper(1);");
        try {
            TypeScriptCompiler.toJavaScript("imports.ts", source);
            fail("Expected the value import to be refused");
        } catch (TypeScriptCompileException e) {
            assertEquals(BareScriptPass.CODE, e.getCode());
            assertEquals(2, e.getLine());
            assertEquals(1, e.getColumn());
            assertTrue(e.getMessage(), e.getMessage().contains("import"));
        }
    }

    @Test
    public void testReExportIsRejectedInsteadOfSilentlyDropped() throws Exception {
        try {
            TypeScriptCompiler.toJavaScript("reexport.ts", "export * from './other';");
            fail("Expected the re-export to be refused");
        } catch (TypeScriptCompileException e) {
            assertEquals(BareScriptPass.CODE, e.getCode());
            assertEquals(1, e.getLine());
        }

        try {
            TypeScriptCompiler.toJavaScript("reexport.ts", "export { a } from './other';");
            fail("Expected the named re-export to be refused");
        } catch (TypeScriptCompileException e) {
            assertEquals(BareScriptPass.CODE, e.getCode());
            assertEquals(1, e.getLine());
        }
    }

    @Test
    public void testTypeOnlyScriptKeepsNoModuleMarker() throws Exception {
        String source = String.join("\n",
                "import type { Foo } from './types';",
                "export interface Local { id: number }",
                "const value = 1 as unknown as Foo;");
        String javaScript = TypeScriptCompiler.toJavaScript("types.ts", source);

        assertFalse("Translanguage's `export {};` marker must not reach GraalJS: " + javaScript,
                javaScript.contains("export"));
        assertFalse(javaScript, javaScript.contains("import"));
        assertTrue(javaScript, javaScript.contains("const value = 1"));
    }

    @Test
    public void testSyntaxErrorCarriesItsPositionAndCode() {
        try {
            TypeScriptCompiler.toJavaScript("broken.ts", "const x: = 1;");
            fail("Expected a syntax error");
        } catch (TypeScriptCompileException e) {
            assertEquals("TS_SYNTAX", e.getCode());
            assertEquals(1, e.getLine());
            assertEquals(10, e.getColumn());
            assertFalse(e.getMessage().isEmpty());
        }
    }

    @Test
    public void testUnsupportedSyntaxIsReportedNotGuessed() {
        for (String source : new String[]{"namespace N { export const x = 1; }", "@sealed class A {}"}) {
            try {
                TypeScriptCompiler.toJavaScript("unsupported.ts", source);
                fail("Expected '" + source + "' to be refused");
            } catch (TypeScriptCompileException e) {
                assertEquals("TS_UNSUPPORTED", e.getCode());
            }
        }
    }

    @Test
    public void testSourceLengthLimitIsEnforced() {
        try {
            TypeScriptCompiler.toJavaScript("big.ts", "const a = 1;", 4);
            fail("Expected the source limit to apply");
        } catch (TypeScriptCompileException e) {
            assertEquals(TypeScriptCompileException.CODE_TOO_LARGE, e.getCode());
        }
    }

    @Test
    public void testByteOrderMarkIsIgnored() throws Exception {
        String javaScript = TypeScriptCompiler.toJavaScript("bom.ts", "\uFEFFconst a: number = 1;");

        assertFalse(javaScript, javaScript.startsWith("\uFEFF"));
        assertEquals(0, javaScript.indexOf("const"));
    }

    @Test
    public void testEmptySourceCompilesToEmptyJavaScript() throws Exception {
        assertEquals("", TypeScriptCompiler.toJavaScript("empty.ts", ""));
        assertEquals("", TypeScriptCompiler.toJavaScript("empty.ts", null));
    }

    @Test
    public void testModuleMarkerStripperOnlyRemovesTheMarkerStatement() {
        assertEquals("\nconst a = 1;\n", BareScriptEmitter.stripModuleMarker(
                "\nconst a = 1;\nexport {};\n"));
        // Anything that is not the exact marker statement is left alone.
        assertEquals("export const a = 1;\n",
                BareScriptEmitter.stripModuleMarker("export const a = 1;\n"));
        assertEquals("", BareScriptEmitter.stripModuleMarker(""));
    }

    private static int lineCount(String text) {
        return text.split("\n", -1).length;
    }

}
