package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHighlighter;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.callback.TextStyle;
import com.mpp.integratedscripting.core.language.service.LanguageServiceRegistry;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the extension/language registry and its legacy bridge
 * (docs/LANGUAGE-API-REFACTOR.md Phase 0 and Phase 2).
 */
public class LanguageHandlerRegistryTest {

    private LanguageServiceRegistry services;
    private LanguageHandlerRegistry handlers;

    @Before
    public void setUp() {
        this.services = new LanguageServiceRegistry();
        this.handlers = new LanguageHandlerRegistry(this.services);
        this.services.attachHandlerRegistry(this.handlers);
    }

    @Test
    public void testExtensionMatchingIsCaseInsensitiveAndAcceptsDots() {
        FakeHandler handler = new FakeHandler(LanguageId.of("mymod", "mylang"), "MyLang", ".ml");
        handlers.register(handler);

        assertSame(handler, handlers.getProvider(Paths.get("script.mylang")));
        assertSame(handler, handlers.getProvider(Paths.get("script.MYLANG")));
        assertSame(handler, handlers.getProvider(Paths.get("dir", "a.b.ml")));
        assertNull(handlers.getProvider(Paths.get("script.txt")));
        assertNull(handlers.getProvider(Paths.get("noextension")));
        assertNull(handlers.getProvider(Paths.get("trailing.")));
        assertEquals("mylang", LanguageHandlerRegistry.getPathExtension(Paths.get("a.MYLANG")));
        assertNull(LanguageHandlerRegistry.getPathExtension(Paths.get("noextension")));
    }

    @Test
    public void testHandlerLookupByIdAndSnapshots() {
        FakeHandler first = new FakeHandler(LanguageId.of("mymod", "first"), "one");
        FakeHandler second = new FakeHandler(LanguageId.of("mymod", "second"), "two");
        handlers.register(first);
        handlers.register(second);

        assertSame(first, handlers.getHandler(LanguageId.of("mymod", "first")));
        assertNull(handlers.getHandler(LanguageId.of("mymod", "unknown")));
        assertNull(handlers.getHandler(null));
        assertEquals(2, handlers.getHandlers().size());
        assertEquals(2, handlers.getLanguages().size());
        assertSame(first, handlers.getDefaultHandler());
        assertTrue(handlers.getConflicts().isEmpty());
    }

    @Test
    public void testDuplicateExtensionIsReportedAndTheLastRegistrationWins() {
        LanguageId firstId = LanguageId.of("mymod", "first");
        LanguageId secondId = LanguageId.of("mymod", "second");
        FakeHandler first = new FakeHandler(firstId, "js");
        FakeHandler second = new FakeHandler(secondId, "js");
        handlers.register(first);
        handlers.register(second);

        assertSame(second, handlers.getProvider(Paths.get("a.js")));
        assertEquals(1, handlers.getConflicts().size());
        assertTrue(handlers.getConflicts().iterator().next().contains(".js"));
    }

    @Test
    public void testReplacingALanguageDropsTheOldExtensions() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        FakeHandler old = new FakeHandler(language, "old", "shared");
        FakeHandler replacement = new FakeHandler(language, "new", "shared");
        handlers.register(old);
        handlers.register(replacement);

        assertNull(handlers.getProvider(Paths.get("a.old")));
        assertSame(replacement, handlers.getProvider(Paths.get("a.new")));
        assertSame(replacement, handlers.getProvider(Paths.get("a.shared")));
        assertEquals(1, handlers.getHandlers().size());
        assertEquals(1, handlers.getLanguages().size());
        assertTrue(handlers.getConflicts().iterator().next().contains(language.toString()));
    }

    @Test
    public void testLegacyHandlerGetsHighlightAndEditServices() {
        LegacyHandler handler = new LegacyHandler(LanguageId.of("mymod", "legacy"), "legacy");
        handlers.register(handler);

        ILanguageServices bundle = services.getServices(handler.getLanguageId());
        IHighlightService highlight = bundle.get(LanguageServiceKeys.HIGHLIGHT);
        assertTrue(highlight instanceof com.mpp.integratedscripting.core.language.service
                .LegacyLanguageServices.LegacyHighlightService);

        // The document-wide legacy highlighter wins over per-line markup.
        List<List<Token>> lines = highlight.highlight(DocumentText.of("abc\ndef"));
        assertEquals(2, lines.size());
        assertEquals(TokenType.COMMENT, lines.get(0).get(0).getType());
        assertEquals("abc", DocumentText.of("abc\ndef").slice(lines.get(0).get(0).getStart(),
                lines.get(0).get(0).getEnd()));

        // The per-line tokenizer uses markupLine and covers the line exactly.
        LineTokens tokens = highlight.getTokenizer().tokenizeLine("hello", null);
        assertEquals(1, tokens.getTokens().size());
        assertEquals(new Token(0, 5, TokenType.KEYWORD), tokens.getTokens().get(0));

        // Lexical context and edit rules are delegated too.
        assertEquals(com.mpp.integratedscripting.api.language.LexicalContext.STRING,
                highlight.lexicalContextAt(DocumentText.of("abc"), 0));
        IEditSupport edit = bundle.get(LanguageServiceKeys.EDIT);
        assertEquals("#", edit.getCommentStyle().getLineComment());
        assertNull(edit.getCommentStyle().getBlockCommentOpen());
        assertEquals("legacy", edit.getDefaultFileExtension());
        assertEquals("    ", edit.getIndentUnit());
    }

    @Test
    public void testHandlerWithoutLegacyOverridesUsesDefaults() {
        FakeHandler handler = new FakeHandler(LanguageId.of("mymod", "modern"), "modern");
        handlers.register(handler);

        ILanguageServices bundle = services.getServices(handler.getLanguageId());
        assertSame(com.mpp.integratedscripting.core.language.service.DefaultServices.PLAIN_HIGHLIGHT,
                bundle.get(LanguageServiceKeys.HIGHLIGHT));
        assertSame(com.mpp.integratedscripting.core.language.service.DefaultServices.PLAIN_EDIT,
                bundle.get(LanguageServiceKeys.EDIT));
        assertTrue(handlers.getConflicts().isEmpty());
    }

    @Test
    public void testHandlerImplementingTheNewServiceIsRegisteredAsIs() {
        ModernHandler handler = new ModernHandler();
        handlers.register(handler);
        assertSame(handler, services.getServices(handler.getLanguageId())
                .get(LanguageServiceKeys.HIGHLIGHT));
    }

    @Test
    public void testNormalizeExtension() {
        assertEquals("js", LanguageHandlerRegistry.normalizeExtension("JS"));
        assertEquals("js", LanguageHandlerRegistry.normalizeExtension(".js"));
        assertEquals("js", LanguageHandlerRegistry.normalizeExtension("  .JS  "));
        assertNull(LanguageHandlerRegistry.normalizeExtension(""));
        assertNull(LanguageHandlerRegistry.normalizeExtension("."));
        assertNull(LanguageHandlerRegistry.normalizeExtension(null));
    }

    private static class FakeHandler implements ILanguageHandler {

        private final LanguageId language;
        private final List<String> extensions;

        private FakeHandler(LanguageId language, String... extensions) {
            this.language = language;
            this.extensions = Arrays.asList(extensions);
        }

        @Override
        public LanguageId getLanguageId() {
            return this.language;
        }

        @Override
        public String getName() {
            return this.language.getName();
        }

        @Override
        public List<String> getExtensions() {
            return this.extensions;
        }

        @Nullable
        @Override
        public IScriptFactory getScriptFactory() throws EvaluationException {
            return null;
        }

    }

    /**
     * A handler written against the old 9-method API: it overrides the legacy
     * highlighting/comment hooks and must get the corresponding services.
     */
    @SuppressWarnings("deprecation")
    private static final class LegacyHandler extends FakeHandler {

        private LegacyHandler(LanguageId language, String... extensions) {
            super(language, extensions);
        }

        @Override
        public List<Pair<TextFormatting, String>> markupLine(String line) {
            return Collections.singletonList(Pair.of(TextStyle.KEYWORD, line));
        }

        @Override
        public ILanguageHighlighter getHighlighter() {
            return value -> {
                List<List<Pair<TextFormatting, String>>> lines = new ArrayList<>();
                for (String line : value.split("\n", -1)) {
                    lines.add(Collections.singletonList(Pair.of(TextStyle.COMMENT, line)));
                }
                return lines;
            };
        }

        @Override
        public int getLexicalContext(String value, int position) {
            return com.mpp.integratedscripting.api.language.LexicalContext.STRING;
        }

        @Override
        public String getLineCommentPrefix() {
            return "#";
        }

    }

    /**
     * A new-style handler that implements the service interface directly.
     */
    private static final class ModernHandler extends FakeHandler implements IHighlightService {

        private ModernHandler() {
            super(LanguageId.of("mymod", "modern-service"), "modern-service");
        }

        @Override
        public ILineTokenizer getTokenizer() {
            return new ILineTokenizer() {
                @Override
                public HighlightState initialState() {
                    return null;
                }

                @Override
                public LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState) {
                    String text = line == null ? "" : line;
                    return LineTokens.of(text.isEmpty() ? Collections.<Token>emptyList()
                            : Collections.singletonList(new Token(0, text.length(), TokenType.DEFAULT)),
                            null);
                }
            };
        }

    }

}
