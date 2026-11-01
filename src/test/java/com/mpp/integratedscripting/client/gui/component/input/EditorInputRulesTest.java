package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.integratedscripting.api.language.LexicalContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the CodeMirror-inspired editing rules of the terminal editor.
 */
public class EditorInputRulesTest {

    private static final String UNIT = EditorInputRules.INDENT_UNIT;

    private static EditorEdit typed(String value, int cursor, char typed) {
        return EditorInputRules.handleInsert(value, cursor, cursor, typed, LexicalContext.CODE, UNIT);
    }

    // ---------------------------------------------------------------- enter / auto-indent

    @Test
    public void testNewlineAtLineStart() {
        EditorEdit edit = EditorInputRules.newline("", 0, 0, UNIT);
        assertEquals("\n", edit.getValue());
        assertEquals(1, edit.getCursor());
    }

    @Test
    public void testNewlineKeepsIndentation() {
        String value = "if (x) {\n    foo();";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT);
        assertEquals("if (x) {\n    foo();\n    ", edit.getValue());
        assertEquals(value.length() + 5, edit.getCursor());
    }

    @Test
    public void testNewlineIndentsAfterOpeningBrace() {
        String value = "function f() {";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT);
        assertEquals("function f() {\n    ", edit.getValue());
        assertEquals(value.length() + 5, edit.getCursor());
    }

    @Test
    public void testNewlineIndentsAfterArrow() {
        String value = "const f = (x) =>";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT);
        assertTrue(edit.getValue().endsWith("=>\n    "));
    }

    @Test
    public void testNewlineSplitsEmptyBraces() {
        EditorEdit edit = EditorInputRules.newline("{}", 1, 1, UNIT);
        assertEquals("{\n    \n}", edit.getValue());
        assertEquals(6, edit.getCursor());
    }

    @Test
    public void testNewlineSplitsInlineBraces() {
        EditorEdit edit = EditorInputRules.newline("if (x) {}", 8, 8, UNIT);
        assertEquals("if (x) {\n    \n}", edit.getValue());
        assertEquals(13, edit.getCursor());
    }

    @Test
    public void testNewlineOnBlankLineAfterOpeningBrace() {
        String value = "if (x) {\n";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT);
        assertEquals("if (x) {\n\n    ", edit.getValue());
        assertEquals(value.length() + 5, edit.getCursor());
    }

    @Test
    public void testNewlineBeforeClosingBracketKeepsAlignment() {
        EditorEdit edit = EditorInputRules.newline("{\n    }", 6, 6, UNIT);
        assertEquals("{\n    \n    }", edit.getValue());
        assertEquals(11, edit.getCursor());
    }

    @Test
    public void testNewlineReplacesSelection() {
        EditorEdit edit = EditorInputRules.newline("abc", 3, 0, UNIT);
        assertEquals("\n", edit.getValue());
        assertEquals(1, edit.getCursor());
    }

    @Test
    public void testNewlineContinuesLineComment() {
        String value = "    // note";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT,
                LexicalContext.COMMENT);
        assertEquals("    // note\n    // ", edit.getValue());
        assertEquals(value.length() + 8, edit.getCursor());
    }

    @Test
    public void testNewlineContinuesBlockComment() {
        String value = "/* a";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT,
                LexicalContext.COMMENT);
        assertEquals("/* a\n * ", edit.getValue());
    }

    @Test
    public void testNewlineInsideStringAddsNoIndent() {
        String value = "    const s = \"abc";
        EditorEdit edit = EditorInputRules.newline(value, value.length(), value.length(), UNIT,
                LexicalContext.STRING);
        assertEquals("    const s = \"abc\n    ", edit.getValue());
    }

    @Test
    public void testInsertBlankLineBelow() {
        String value = "if (x) {\n    foo();\n}";
        int cursor = value.indexOf("foo");
        EditorEdit edit = EditorInputRules.insertBlankLine(value, cursor, cursor);
        assertEquals("if (x) {\n    foo();\n    \n}", edit.getValue());
        assertEquals(value.indexOf('\n', cursor) + 5, edit.getCursor());
    }

    // ---------------------------------------------------------------- bracket / quote completion

    @Test
    public void testAutoCloseBracket() {
        EditorEdit edit = typed("foo", 3, '(');
        assertNotNull(edit);
        assertEquals("foo()", edit.getValue());
        assertEquals(4, edit.getCursor());
    }

    @Test
    public void testNoAutoCloseBeforeWord() {
        assertNull(typed("foobar", 3, '('));
    }

    @Test
    public void testNoAutoCloseBeforeClosingBracket() {
        assertNull(typed("foo)", 3, '('));
    }

    @Test
    public void testSkipOverClosingBracket() {
        EditorEdit edit = typed("foo()", 4, ')');
        assertNotNull(edit);
        assertEquals("foo()", edit.getValue());
        assertEquals(5, edit.getCursor());
    }

    @Test
    public void testWrapSelectionInBrackets() {
        EditorEdit edit = EditorInputRules.handleInsert("abc", 3, 0, '(', LexicalContext.CODE, UNIT);
        assertNotNull(edit);
        assertEquals("(abc)", edit.getValue());
        assertEquals(4, edit.getCursor());
        assertEquals(1, edit.getAnchor());
    }

    @Test
    public void testWrapSelectionInQuotes() {
        EditorEdit edit = EditorInputRules.handleInsert("abc", 3, 0, '"', LexicalContext.CODE, UNIT);
        assertNotNull(edit);
        assertEquals("\"abc\"", edit.getValue());
    }

    @Test
    public void testAutoCloseQuote() {
        EditorEdit edit = typed("x = ", 4, '\'');
        assertNotNull(edit);
        assertEquals("x = ''", edit.getValue());
        assertEquals(5, edit.getCursor());
    }

    @Test
    public void testSkipOverQuote() {
        EditorEdit edit = typed("''", 1, '\'');
        assertNotNull(edit);
        assertEquals(2, edit.getCursor());
    }

    @Test
    public void testNoAutoCloseQuoteInsideWord() {
        assertNull(typed("don", 3, '\''));
        assertNull(typed("ab", 2, '\''));
    }

    @Test
    public void testNoAutoCloseInString() {
        assertNull(EditorInputRules.handleInsert("x = \"a", 6, 6, '(', LexicalContext.STRING, UNIT));
        assertNull(EditorInputRules.handleInsert("x = \"a", 6, 6, '"', LexicalContext.STRING, UNIT));
    }

    @Test
    public void testBackspaceDedentsClosingBracket() {
        EditorEdit edit = typed("{\n    ", 6, '}');
        assertNotNull(edit);
        assertEquals("{\n}", edit.getValue());
        assertEquals(3, edit.getCursor());
    }

    @Test
    public void testClosingBracketIsNotDedentedMidLine() {
        assertNull(typed("x ", 2, '}'));
    }

    @Test
    public void testEmptyPairForBackspace() {
        int[] pair = EditorInputRules.emptyPair("foo()", 4);
        assertNotNull(pair);
        assertEquals(3, pair[0]);
        assertEquals(5, pair[1]);
        assertNotNull(EditorInputRules.emptyPair("''", 1));
        assertNull(EditorInputRules.emptyPair("foo(bar)", 4));
        assertNull(EditorInputRules.emptyPair("foo", 1));
    }

    @Test
    public void testIndentationBackspaceStopsAtTabStop() {
        int[] four = EditorInputRules.indentationBackspaceRange("    x", 4);
        assertNotNull(four);
        assertEquals(0, four[0]);
        assertEquals(4, four[1]);

        int[] two = EditorInputRules.indentationBackspaceRange("      x", 6);
        assertNotNull(two);
        assertEquals(4, two[0]);
        assertEquals(6, two[1]);

        assertNull(EditorInputRules.indentationBackspaceRange("      x", 7));
        assertNull(EditorInputRules.indentationBackspaceRange("a   b", 4));
        assertNull(EditorInputRules.indentationBackspaceRange("\tx", 1));
        assertNull(EditorInputRules.indentationBackspaceRange("    x", 0));
    }

    // ---------------------------------------------------------------- indentation / comments

    @Test
    public void testIndentSingleLine() {
        EditorEdit edit = EditorInputRules.indentLines("a\nb", 0, 0, UNIT, 1);
        assertEquals("    a\nb", edit.getValue());
        assertEquals(4, edit.getCursor());
    }

    @Test
    public void testIndentSelectedLines() {
        EditorEdit edit = EditorInputRules.indentLines("a\nb", 3, 0, UNIT, 1);
        assertEquals("    a\n    b", edit.getValue());
        assertEquals(11, edit.getCursor());
        assertEquals(4, edit.getAnchor());
    }

    @Test
    public void testIndentDoesNotIncludeLineStartingAtSelectionEnd() {
        // Selecting "a\n" must not indent the second line.
        EditorEdit edit = EditorInputRules.indentLines("a\nb", 0, 2, UNIT, 1);
        assertEquals("    a\nb", edit.getValue());
    }

    @Test
    public void testDedentLines() {
        EditorEdit edit = EditorInputRules.indentLines("    a\n    b", 11, 0, UNIT, -1);
        assertEquals("a\nb", edit.getValue());
        assertEquals(3, edit.getCursor());
        assertEquals(0, edit.getAnchor());
    }

    @Test
    public void testDedentTab() {
        EditorEdit edit = EditorInputRules.indentLines("\ta", 2, 2, UNIT, -1);
        assertEquals("a", edit.getValue());
    }

    @Test
    public void testToggleComment() {
        EditorEdit edit = EditorInputRules.toggleLineComment("const x = 1;", 5, 5, "//");
        assertEquals("// const x = 1;", edit.getValue());
        assertEquals(8, edit.getCursor());

        EditorEdit back = EditorInputRules.toggleLineComment(edit.getValue(), 8, 8, "//");
        assertEquals("const x = 1;", back.getValue());
    }

    @Test
    public void testToggleCommentRespectsIndentation() {
        EditorEdit edit = EditorInputRules.toggleLineComment("    x", 5, 5, "//");
        assertEquals("    // x", edit.getValue());
    }

    @Test
    public void testToggleCommentMultipleLines() {
        EditorEdit edit = EditorInputRules.toggleLineComment("a\nb", 3, 0, "//");
        assertEquals("// a\n// b", edit.getValue());
        EditorEdit back = EditorInputRules.toggleLineComment(edit.getValue(), 8, 0, "//");
        assertEquals("a\nb", back.getValue());
    }

    @Test
    public void testToggleCommentSkipsBlankLines() {
        EditorEdit edit = EditorInputRules.toggleLineComment("a\n\nb", 4, 0, "//");
        assertEquals("// a\n\n// b", edit.getValue());
    }

    @Test
    public void testDuplicateLine() {
        EditorEdit edit = EditorInputRules.duplicateLines("foo\nbar", 1, 1);
        assertEquals("foo\nfoo\nbar", edit.getValue());
        assertEquals(5, edit.getCursor());
    }

    @Test
    public void testDeleteLine() {
        EditorEdit edit = EditorInputRules.deleteLines("foo\nbar", 1, 1);
        assertEquals("bar", edit.getValue());
        assertEquals(0, edit.getCursor());
    }

    @Test
    public void testDeleteLastLine() {
        EditorEdit edit = EditorInputRules.deleteLines("foo\nbar", 5, 5);
        assertEquals("foo", edit.getValue());
        assertEquals(3, edit.getCursor());
    }

    @Test
    public void testDeleteOnlyLine() {
        EditorEdit edit = EditorInputRules.deleteLines("foo", 1, 1);
        assertEquals("", edit.getValue());
        assertEquals(0, edit.getCursor());
    }

    @Test
    public void testMoveLineUp() {
        EditorEdit edit = EditorInputRules.moveLines("foo\nbar", 5, 5, -1);
        assertEquals("bar\nfoo", edit.getValue());
        assertEquals(1, edit.getCursor());
    }

    @Test
    public void testMoveLineDown() {
        EditorEdit edit = EditorInputRules.moveLines("foo\nbar", 1, 1, 1);
        assertEquals("bar\nfoo", edit.getValue());
        assertEquals(5, edit.getCursor());
    }

    @Test
    public void testMoveLineAtBoundaryIsNoOp() {
        assertEquals("foo\nbar", EditorInputRules.moveLines("foo\nbar", 1, 1, -1).getValue());
        assertEquals("foo\nbar", EditorInputRules.moveLines("foo\nbar", 5, 5, 1).getValue());
    }

    // ---------------------------------------------------------------- navigation

    @Test
    public void testSmartHome() {
        assertEquals(4, EditorInputRules.smartHome("    foo", 7));
        assertEquals(0, EditorInputRules.smartHome("    foo", 4));
        assertEquals(0, EditorInputRules.smartHome("foo", 2));
    }

    @Test
    public void testTabSpaces() {
        assertEquals(4, EditorInputRules.tabSpaces("", 0, 4));
        assertEquals(1, EditorInputRules.tabSpaces("abc", 3, 4));
        assertEquals(4, EditorInputRules.tabSpaces("abcd", 4, 4));
        assertEquals(2, EditorInputRules.tabSpaces("    ab", 6, 4));
    }

    @Test
    public void testWordNavigation() {
        assertEquals(5, EditorInputRules.findWordEnd("hello world", 0));
        assertEquals(6, EditorInputRules.findWordStart("hello world", 11));
        assertEquals(3, EditorInputRules.findWordEnd("foo.bar", 0));
        assertEquals(4, EditorInputRules.findWordEnd("foo.bar", 3));
        assertEquals(3, EditorInputRules.findWordStart("foo.bar", 4));
    }

    @Test
    public void testBracketHelpers() {
        assertTrue(EditorInputRules.isOpeningBracket('{'));
        assertTrue(EditorInputRules.isClosingBracket(']'));
        assertFalse(EditorInputRules.isOpeningBracket('a'));
        assertEquals('}', EditorInputRules.closingFor('{'));
        assertTrue(EditorInputRules.matches('(', ')'));
        assertFalse(EditorInputRules.matches('(', ']'));
    }

}
