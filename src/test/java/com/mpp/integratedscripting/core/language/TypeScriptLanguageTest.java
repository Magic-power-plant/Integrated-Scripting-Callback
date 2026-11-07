package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.core.language.service.LanguageServiceRegistry;
import com.mpp.integratedscripting.core.language.typescript.TypeScriptRuntime;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Acceptance tests for the built-in TypeScript language
 * (docs/TYPESCRIPT-TRANSLANGUAGE.md §4).
 *
 * <p>They cover what registration buys: {@code .ts} resolves to a language, that
 * language has the editor services it needs, declaration files stay out of the
 * script pipeline, and a new file is still a {@code .js} file because JavaScript
 * stays the default language.</p>
 */
public class TypeScriptLanguageTest {

    private LanguageServiceRegistry services;
    private LanguageHandlerRegistry handlers;

    @Before
    public void setUp() {
        this.services = new LanguageServiceRegistry();
        this.handlers = new LanguageHandlerRegistry(this.services);
        this.services.attachHandlerRegistry(this.handlers);
    }

    @Test
    public void testHandlerDescribesTheLanguage() throws Exception {
        LanguageHandlerTypeScript handler = new LanguageHandlerTypeScript();

        assertEquals(LanguageId.TYPESCRIPT, handler.getLanguageId());
        assertEquals("TypeScript", handler.getName());
        assertEquals(1, handler.getExtensions().size());
        assertEquals("ts", handler.getExtensions().get(0));
        assertNotNull("The handler must offer a script factory", handler.getScriptFactory());
    }

    @Test
    public void testTypeScriptFilesResolveToTheTypeScriptLanguage() {
        LanguageHandlers.register(handlers, services);

        assertTrue(handlers.getProvider(Paths.get("main.ts")) instanceof LanguageHandlerTypeScript);
        assertTrue(handlers.getProvider(Paths.get("dir", "Main.TS")) instanceof LanguageHandlerTypeScript);
        // Unrelated extensions are untouched.
        assertTrue(handlers.getProvider(Paths.get("main.js")) instanceof LanguageHandlerJavaScript);
        assertNull(handlers.getProvider(Paths.get("main.tsx")));
    }

    @Test
    public void testDeclarationFilesAreNeverScripts() {
        LanguageHandlers.register(handlers, services);

        // `.d.ts` has the last extension `ts`, so the registry must refuse it before
        // it can hand the generated typings to the TypeScript language.
        assertNull(handlers.getProvider(Paths.get("integratedscriptingcallback.d.ts")));
        assertNull(handlers.getProvider(Paths.get("dir", "Types.D.TS")));
        assertTrue(LanguageHandlerRegistry.isTypeScriptDeclaration(
                Paths.get("a", "mod.d.ts")));
        assertFalse(LanguageHandlerRegistry.isTypeScriptDeclaration(Paths.get("a", "mod.ts")));
        assertFalse(LanguageHandlerRegistry.isTypeScriptDeclaration(null));
        assertFalse(LanguageHandlerRegistry.isTypeScriptDeclarationName("mod.d.ts.bak"));
    }

    @Test
    public void testJavaScriptStaysTheDefaultLanguage() {
        LanguageHandlers.register(handlers, services);

        LanguageHandlerJavaScript javaScript = (LanguageHandlerJavaScript) handlers.getDefaultHandler();
        ILanguageServices javaScriptServices = services.getServices(javaScript.getLanguageId());
        assertEquals("js", javaScriptServices.get(LanguageServiceKeys.EDIT).getDefaultFileExtension());
        // A new file is created from the default language, so it stays `.js`.
        assertEquals(LanguageId.JAVASCRIPT, javaScript.getLanguageId());
    }

    @Test
    public void testHighlighterKnowsTheTypeScriptKeywords() {
        LanguageHandlers.register(handlers, services);
        ILanguageServices services = this.services.getServices(LanguageId.TYPESCRIPT);

        List<Token> tokens = services.get(LanguageServiceKeys.HIGHLIGHT)
                .getTokenizer().tokenizeLine("interface Box extends Base", null).getTokens();
        assertEquals(new Token(0, 9, TokenType.KEYWORD), tokens.get(0));
        // The type name is an identifier; the trailing CSS of the line is irrelevant.
        List<String> firstLineKeywords = keywords(tokens, "interface Box extends Base");
        assertEquals(firstLineKeywords.toString(), 2, firstLineKeywords.size());
        assertTrue(firstLineKeywords.toString(), firstLineKeywords.contains("interface"));
        assertTrue(firstLineKeywords.toString(), firstLineKeywords.contains("extends"));

        List<Token> declaration = services.get(LanguageServiceKeys.HIGHLIGHT)
                .getTokenizer().tokenizeLine("const box: Box = x satisfies Shape", null).getTokens();
        List<String> keywords = keywords(declaration, "const box: Box = x satisfies Shape");
        assertTrue(keywords.toString(), keywords.contains("const"));
        assertTrue(keywords.toString(), keywords.contains("satisfies"));
        // `Box` and `Shape` are identifiers, not keywords.
        assertFalse(keywords.toString(), keywords.contains("Box"));

        // The document-wide entry point carries the same keyword set.
        List<List<Token>> lines = services.get(LanguageServiceKeys.HIGHLIGHT)
                .highlight(DocumentText.of("type Id = string;\nconst id: Id = 'a';"));
        assertEquals(2, lines.size());
        assertEquals(TokenType.KEYWORD, lines.get(0).get(0).getType());
    }

    @Test
    public void testPlainJavaScriptHighlightingIsUnchanged() {
        // `interface`, `type` and `as` are ordinary identifiers in JavaScript, so
        // sharing the lexer must not leak the TypeScript keyword set into `.js`.
        List<Token> tokens = JavaScriptLexer.tokenize("interface type as", null).getTokens();
        for (Token token : tokens) {
            assertEquals(token.toString(), TokenType.DEFAULT, token.getType());
        }
        assertEquals(TokenType.KEYWORD, JavaScriptLexer.tokenize("const a = 1", null)
                .getTokens().get(0).getType());
    }

    @Test
    public void testEditingRulesAreTheJavaScriptOnesWithATsExtension() {
        LanguageHandlers.register(handlers, services);
        ILanguageServices javaScript = this.services.getServices(LanguageId.JAVASCRIPT);
        ILanguageServices typeScript = this.services.getServices(LanguageId.TYPESCRIPT);

        IEditSupport edit = typeScript.get(LanguageServiceKeys.EDIT);
        assertEquals("ts", edit.getDefaultFileExtension());
        // Indentation, comments and identifiers are identical to JavaScript.
        assertEquals(javaScript.get(LanguageServiceKeys.EDIT).getIndentUnit(), edit.getIndentUnit());
        assertEquals(javaScript.get(LanguageServiceKeys.EDIT).getCommentStyle().getLineComment(),
                edit.getCommentStyle().getLineComment());
        assertEquals(javaScript.get(LanguageServiceKeys.EDIT).getCommentStyle().getBlockCommentOpen(),
                edit.getCommentStyle().getBlockCommentOpen());
    }

    @Test
    public void testCompletionOffersTypeScriptKeywords() {
        LanguageHandlers.register(handlers, services);

        CompletionContext context = new CompletionContext(DocumentText.of(""), 0,
                LanguageId.TYPESCRIPT, LexicalContext.CODE, (char) 0, "inter");
        List<String> labels = new ArrayList<>();
        for (CompletionItem item : services.getServices(LanguageId.TYPESCRIPT)
                .get(LanguageServiceKeys.COMPLETION).complete(context)) {
            labels.add(item.getLabel());
        }
        assertTrue(labels.toString(), labels.contains("interface"));

        // A JavaScript completion request does not get TypeScript-only keywords.
        CompletionContext javaScriptContext = new CompletionContext(DocumentText.of(""), 0,
                LanguageId.JAVASCRIPT, LexicalContext.CODE, (char) 0, "inter");
        List<String> javaScriptLabels = new ArrayList<>();
        for (CompletionItem item : services.getServices(LanguageId.JAVASCRIPT)
                .get(LanguageServiceKeys.COMPLETION).complete(javaScriptContext)) {
            javaScriptLabels.add(item.getLabel());
        }
        assertFalse(javaScriptLabels.toString(), javaScriptLabels.contains("interface"));
    }

    @Test
    public void testTheVendoredTranspilerIsOnTheClasspath() {
        // Guards the build: if the shade/`testImplementation` wiring regresses, the
        // language silently disappears and this says why.
        assertTrue(TypeScriptRuntime.MISSING_RUNTIME_MESSAGE,
                TypeScriptRuntime.isAvailable());
    }

    /**
     * @param tokens A tokenized line.
     * @param line The line the tokens describe.
     * @return The text of every keyword token, in order.
     */
    private static List<String> keywords(List<Token> tokens, String line) {
        List<String> keywords = new ArrayList<>();
        for (Token token : tokens) {
            if (token.getType() == TokenType.KEYWORD) {
                keywords.add(line.substring(token.getStart(), token.getEnd()));
            }
        }
        return keywords;
    }

}
