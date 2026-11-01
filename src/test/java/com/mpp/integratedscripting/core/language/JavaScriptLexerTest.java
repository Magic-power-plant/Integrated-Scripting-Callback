package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the streaming JavaScript lexer.
 *
 * <p>The token-type/text goldens pin the styling of the pre-refactor tokenizer:
 * {@code JavaScriptTokenizer#styleOf} maps STRING and NUMBER to the old
 * {@code CONSTANT} style, KEYWORD to KEYWORD, COMMENT to COMMENT, PROPERTY to
 * ATTRIBUTE and every symbol type to SYMBOL, so these assertions are exactly the
 * old output (docs/LANGUAGE-API-REFACTOR.md Phase 3: "逐 token 一致"). The
 * existing {@code JavaScriptTokenizerTest} keeps the style-level and
 * lexical-context regressions for the same reason.</p>
 */
public class JavaScriptLexerTest {

    private static final TokenType K = TokenType.KEYWORD;
    private static final TokenType C = TokenType.COMMENT;
    private static final TokenType S = TokenType.STRING;
    private static final TokenType N = TokenType.NUMBER;
    private static final TokenType P = TokenType.PROPERTY;
    private static final TokenType Y = TokenType.SYMBOL;
    private static final TokenType D = TokenType.DEFAULT;

    @Test
    public void testEmptyLine() {
        assertTrue(JavaScriptLexer.tokenize("", null).getTokens().isEmpty());
        assertTrue(JavaScriptLexer.tokenize(null, null).getTokens().isEmpty());
    }

    @Test
    public void testGoldenTokenization() {
        assertLine("const x = 42;", new TokenType[]{K, D, Y, D, N, Y},
                new String[]{"const", " x ", "=", " ", "42", ";"});
        assertLine("item.stackable()", new TokenType[]{D, Y, P, Y},
                new String[]{"item", ".", "stackable", "()"});
        assertLine("// note", new TokenType[]{C}, new String[]{"// note"});
        assertLine("/* a */ x", new TokenType[]{C, D}, new String[]{"/* a */", " x"});
        assertLine("`a ${b} c`", new TokenType[]{S}, new String[]{"`a ${b} c`"});
        assertLine("const r = /ab+c/gi;", new TokenType[]{K, D, Y, D, S, Y},
                new String[]{"const", " r ", "=", " ", "/ab+c/gi", ";"});
        assertLine("a = b / c;", new TokenType[]{D, Y, D, Y, D, Y},
                new String[]{"a ", "=", " b ", "/", " c", ";"});
        assertLine("s = 'it\\'s';", new TokenType[]{D, Y, D, S, Y},
                new String[]{"s ", "=", " ", "'it\\'s'", ";"});
        assertLine("x = 0xFF + 0b10n;", new TokenType[]{D, Y, D, N, D, Y, D, N, Y},
                new String[]{"x ", "=", " ", "0xFF", " ", "+", " ", "0b10n", ";"});
        assertLine("static async await", new TokenType[]{K, D, K, D, K},
                new String[]{"static", " ", "async", " ", "await"});
        assertLine("constant = 1;", new TokenType[]{D, Y, D, N, Y},
                new String[]{"constant ", "=", " ", "1", ";"});
        assertLine("obj.class", new TokenType[]{D, Y, K},
                new String[]{"obj", ".", "class"});
        assertLine("if (a && b) { return true; }",
                new TokenType[]{K, D, Y, D, Y, D, Y, D, Y, D, K, D, K, Y, D, Y},
                new String[]{"if", " ", "(", "a ", "&&", " b", ")", " ", "{", " ",
                        "return", " ", "true", ";", " ", "}"});
    }

    @Test
    public void testTokensCoverEveryLineExactly() {
        String[] lines = new String[]{
                "",
                "const foo = 42;",
                "function f(item) { return item.stackable(); }",
                "// a comment",
                "let s = \"a { string }\";",
                "  /* block */  ",
                "a\tb",
                "const r = /ab+c/gi;",
                "`template ${x}`",
                "s = \"unterminated",
                "/* opened"
        };
        for (String line : lines) {
            assertCovers(line, JavaScriptLexer.tokenize(line, null).getTokens());
        }
        String[] documents = new String[]{
                "/* a\nb */ x",
                "const s = `a\nb`;",
                "a\n\nb\n",
                "`` \n `x` \n"
        };
        for (String document : documents) {
            List<List<Token>> linesOfTokens = JavaScriptLexer.tokenizeDocument(document);
            String[] split = document.split("\n", -1);
            assertEquals(split.length, linesOfTokens.size());
            for (int i = 0; i < split.length; i++) {
                assertCovers(split[i], linesOfTokens.get(i));
            }
        }
    }

    @Test
    public void testBlockCommentStateAcrossLines() {
        LineTokens first = JavaScriptLexer.tokenize("/* a", null);
        assertEquals(JavaScriptLexer.State.BLOCK_COMMENT, first.getEndState());
        assertEquals(TokenType.COMMENT, first.getTokens().get(0).getType());

        LineTokens second = JavaScriptLexer.tokenize("b */ x", JavaScriptLexer.stateOf(first.getEndState()));
        assertEquals(JavaScriptLexer.State.CODE, second.getEndState());
        assertEquals(TokenType.COMMENT, second.getTokens().get(0).getType());
        assertEquals("b */", sub("b */ x", second.getTokens().get(0)));
        assertEquals(TokenType.DEFAULT, second.getTokens().get(1).getType());
    }

    @Test
    public void testTemplateLiteralStateAcrossLines() {
        LineTokens first = JavaScriptLexer.tokenize("const s = `a", null);
        assertEquals(JavaScriptLexer.State.TEMPLATE_STRING, first.getEndState());
        LineTokens second = JavaScriptLexer.tokenize("b`;", JavaScriptLexer.stateOf(first.getEndState()));
        assertEquals(JavaScriptLexer.State.CODE, second.getEndState());
        assertEquals(TokenType.STRING, second.getTokens().get(0).getType());
        assertEquals(TokenType.SYMBOL, second.getTokens().get(1).getType());
    }

    @Test
    public void testLexicalContextMatchesTheCompatibilityFacade() {
        String[] documents = new String[]{
                "const a = 'x';",
                "x; // note",
                "/* a\nb */ x",
                "`a\nb`",
                "s = 'abc",
                "s = 'abc'",
                "const x = 1;"
        };
        for (String document : documents) {
            assertEquals(document, JavaScriptTokenizer.tokenizeDocument(document),
                    convert(JavaScriptLexer.tokenizeDocument(document), document));
            for (int offset = 0; offset <= document.length(); offset++) {
                assertEquals("context at " + offset + " in " + document,
                        JavaScriptTokenizer.lexicalContextAt(document, offset),
                        JavaScriptLexer.lexicalContextAt(document, offset));
            }
        }
        assertEquals(LexicalContext.STRING, JavaScriptLexer.lexicalContextAt("`a\nb`", 3));
        assertEquals(LexicalContext.COMMENT, JavaScriptLexer.lexicalContextAt("/* a\nb */ x", 6));
        assertEquals(LexicalContext.CODE, JavaScriptLexer.lexicalContextAt("/* a\nb */ x", 10));
    }

    @Test
    public void testIdentifierSyntax() {
        JavaScriptIdentifierSyntax syntax = new JavaScriptIdentifierSyntax();
        assertTrue(Character.isJavaIdentifierStart('$'));
        assertTrue(syntax.isIdentifierStart('a'));
        assertTrue(syntax.isIdentifierPart('$'));
        assertTrue(syntax.isIdentifierPart('_'));
        assertTrue(syntax.isIdentifierPart('\u00e9'));
        assertTrue(syntax.isValidMemberName("$foo"));
        assertFalse(syntax.isValidMemberName("42"));
        assertFalse(syntax.isValidMemberName("a-b"));
        assertFalse(syntax.isValidMemberName(""));
        assertEquals("foo", syntax.memberAt(DocumentText.of("item.foo(1)"), 6));
        assertEquals("", syntax.memberAt(DocumentText.of("a + b"), 2));
        assertEquals("$x", syntax.memberAt(DocumentText.of("a.$x + 1"), 4));
    }

    // ---------------------------------------------------------------- helpers

    private static String sub(String line, Token token) {
        return line.substring(Math.min(token.getStart(), line.length()),
                Math.min(token.getEnd(), line.length()));
    }

    private static void assertLine(String line, TokenType[] types, String[] texts) {
        List<Token> tokens = JavaScriptLexer.tokenize(line, null).getTokens();
        assertEquals("token count of '" + line + "'", types.length, tokens.size());
        for (int i = 0; i < types.length; i++) {
            assertEquals("type of token " + i + " in '" + line + "'", types[i],
                    tokens.get(i).getType());
            assertEquals("text of token " + i + " in '" + line + "'", texts[i],
                    sub(line, tokens.get(i)));
        }
        assertCovers(line, tokens);
    }

    private static void assertCovers(String line, List<Token> tokens) {
        int offset = 0;
        StringBuilder rebuilt = new StringBuilder();
        for (Token token : tokens) {
            assertEquals("token starts where the previous ended in '" + line + "'",
                    offset, token.getStart());
            assertTrue("token has positive length in '" + line + "'",
                    token.getEnd() > token.getStart());
            rebuilt.append(sub(line, token));
            offset = token.getEnd();
        }
        assertEquals("tokens cover the line '" + line + "'", line.length(), offset);
        assertEquals("tokens reconstruct the line '" + line + "'", line, rebuilt.toString());
    }

    /**
     * Convert the lexer's tokens to the old styled segments, merging adjacent
     * equal styles exactly like the old tokenizer did.
     */
    private static List<List<Pair<TextFormatting, String>>> convert(List<List<Token>> lines,
                                                                    String document) {
        String[] split = document.split("\n", -1);
        List<List<Pair<TextFormatting, String>>> result = new ArrayList<>();
        for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
            String line = lineIndex < split.length ? split[lineIndex] : "";
            List<Pair<TextFormatting, String>> segments = new ArrayList<>();
            for (Token token : lines.get(lineIndex)) {
                TextFormatting style = JavaScriptTokenizer.styleOf(token.getType());
                String text = sub(line, token);
                if (!segments.isEmpty()
                        && segments.get(segments.size() - 1).getLeft() == style) {
                    Pair<TextFormatting, String> last = segments.remove(segments.size() - 1);
                    segments.add(Pair.of(style, last.getRight() + text));
                } else {
                    segments.add(Pair.of(style, text));
                }
            }
            result.add(segments);
        }
        return result;
    }

}
