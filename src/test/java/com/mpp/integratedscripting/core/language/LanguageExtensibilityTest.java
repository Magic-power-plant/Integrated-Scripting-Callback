package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.IServerStartTask;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKeys;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.core.language.service.DefaultServices;
import com.mpp.integratedscripting.core.language.service.LanguageServiceRegistry;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Extensibility acceptance tests (docs/LANGUAGE-API-REFACTOR.md Phase 5).
 *
 * <p>They prove the two properties the refactor exists for: a new language needs
 * only its identity and script factory and still works through the defaults, and
 * a third-party mod can add a completion contributor to an existing language
 * without touching that language or the editor.</p>
 */
public class LanguageExtensibilityTest {

    private LanguageServiceRegistry services;
    private LanguageHandlerRegistry handlers;

    @Before
    public void setUp() {
        this.services = new LanguageServiceRegistry();
        this.handlers = new LanguageHandlerRegistry(this.services);
        this.services.attachHandlerRegistry(this.handlers);
    }

    @Test
    public void testMinimalLanguageWorksWithTheDefaults() {
        MinimalHandler handler = new MinimalHandler(LanguageId.of("mymod", "plain"), "plain", "plain");
        handlers.register(handler);

        ILanguageServices bundle = services.getServices(handler.getLanguageId());
        assertSame(DefaultServices.PLAIN_HIGHLIGHT, bundle.get(LanguageServiceKeys.HIGHLIGHT));
        assertSame(DefaultServices.PLAIN_EDIT, bundle.get(LanguageServiceKeys.EDIT));
        assertSame(DefaultServices.EMPTY_DIAGNOSTICS, bundle.get(LanguageServiceKeys.DIAGNOSTICS));

        // Highlighting still covers the document, with plain tokens.
        List<List<Token>> lines = bundle.get(LanguageServiceKeys.HIGHLIGHT)
                .highlight(DocumentText.of("hello\n\nworld"));
        assertEquals(3, lines.size());
        assertEquals(new Token(0, 5, TokenType.DEFAULT), lines.get(0).get(0));
        assertTrue(lines.get(1).isEmpty());
        assertEquals(new Token(0, 5, TokenType.DEFAULT), lines.get(2).get(0));

        // The neutral editing rules work, and they are the documented defaults.
        assertEquals("    ", bundle.get(LanguageServiceKeys.EDIT).getIndentUnit());
        assertEquals("//", bundle.get(LanguageServiceKeys.EDIT).getCommentStyle().getLineComment());
        assertEquals("txt", bundle.get(LanguageServiceKeys.EDIT).getDefaultFileExtension());
        assertFalse(bundle.get(LanguageServiceKeys.EDIT).getIdentifierSyntax()
                .isValidMemberName("$x"));
        assertEquals(LexicalContext.CODE, bundle.get(LanguageServiceKeys.HIGHLIGHT)
                .lexicalContextAt(DocumentText.of("'x"), 1));

        // Completion has no candidates, but is never null.
        assertNotNull(bundle.get(LanguageServiceKeys.COMPLETION));
        assertTrue(bundle.get(LanguageServiceKeys.COMPLETION)
                .complete(context(handler.getLanguageId(), "")).isEmpty());
    }

    @Test
    public void testLanguageIdIsDerivedFromTheNameByDefault() {
        assertEquals(LanguageId.of(LanguageId.DEFAULT_NAMESPACE, "plain"),
                new MinimalHandler(null, "plain", "plain").getLanguageId());
        assertEquals(LanguageId.of(LanguageId.DEFAULT_NAMESPACE, "my-lang"),
                new MinimalHandler(null, "My Lang", "mylang").getLanguageId());
    }

    @Test
    public void testThirdPartyContributorAddsCandidatesToJavaScript() {
        LanguageHandlers.register(handlers, services);
        LanguageId javaScript = LanguageId.JAVASCRIPT;

        services.registerContributor(javaScript, LanguageContributorKeys.COMPLETION, 200,
                new ICompletionContributor() {
                    @Override
                    public void contribute(CompletionContext context, CompletionCollector out) {
                        out.add(CompletionItem.builder("myJsDocTag")
                                .kind(CompletionKind.MODULE)
                                .detail("from a third-party mod")
                                .build());
                    }
                });

        List<CompletionItem> items = services.getServices(javaScript)
                .get(LanguageServiceKeys.COMPLETION)
                .complete(context(javaScript, ""));
        List<String> labels = new ArrayList<>();
        for (CompletionItem item : items) {
            labels.add(item.getLabel());
        }
        assertTrue("expected the third-party candidate in " + labels,
                labels.contains("myJsDocTag"));
        // The language's own candidates are still there.
        assertTrue("expected the built-in keywords in " + labels, labels.contains("const"));
    }

    @Test
    public void testThirdPartyContributorRespectsTheItemLimit() {
        LanguageHandlers.register(handlers, services);
        LanguageId javaScript = LanguageId.JAVASCRIPT;
        services.registerContributor(javaScript, LanguageContributorKeys.COMPLETION, 0,
                new ICompletionContributor() {
                    @Override
                    public void contribute(CompletionContext context, CompletionCollector out) {
                        for (int i = 0; i < 500; i++) {
                            out.add(CompletionItem.builder("zz" + i).build());
                        }
                    }
                });
        List<CompletionItem> items = services.getServices(javaScript)
                .get(LanguageServiceKeys.COMPLETION)
                .complete(context(javaScript, ""));
        assertTrue(items.size() <= 100);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testServerStartTasksAndLegacyHooksRunOnce() {
        LanguageHandlers.register(handlers, services);
        final AtomicInteger taskCalls = new AtomicInteger();
        services.registerContributor(LanguageId.JAVASCRIPT, LanguageContributorKeys.SERVER_START_TASKS,
                10, new IServerStartTask() {
                    @Override
                    public void onServerStarted() {
                        taskCalls.incrementAndGet();
                    }
                });

        final AtomicInteger legacyCalls = new AtomicInteger();
        handlers.register(new MinimalHandler(LanguageId.of("mymod", "legacy"), "legacy", "legacy") {
            @Override
            public void onServerStarted() {
                legacyCalls.incrementAndGet();
            }
        });

        handlers.onServerStarted();
        assertEquals(1, taskCalls.get());
        assertEquals(1, legacyCalls.get());
    }

    @Test
    public void testEditingRulesAreIndependentOfTheLanguageHandler() {
        // The editor only needs an ILanguageServices bundle; a language that
        // registers one service gets it without any handler-side method.
        LanguageId language = LanguageId.of("mymod", "custom");
        handlers.register(new MinimalHandler(language, "custom", "custom"));
        services.registerService(language, LanguageServiceKeys.EDIT, new IEditSupport() {
            @Override
            public String getIndentUnit() {
                return "\t";
            }

            @Override
            public String getDefaultFileExtension() {
                return "custom";
            }
        });
        ILanguageServices bundle = services.getServices(language);
        assertEquals("\t", bundle.get(LanguageServiceKeys.EDIT).getIndentUnit());
        assertEquals("custom", bundle.get(LanguageServiceKeys.EDIT).getDefaultFileExtension());
        // The language still uses the default highlighter.
        assertSame(DefaultServices.PLAIN_HIGHLIGHT, bundle.get(LanguageServiceKeys.HIGHLIGHT));
    }

    private static CompletionContext context(LanguageId language, String prefix) {
        return new CompletionContext(DocumentText.of(""), 0, language, LexicalContext.CODE,
                (char) 0, prefix);
    }

    /**
     * A language that only implements identity and the script factory, exactly as
     * a third-party mod would.
     */
    private static class MinimalHandler implements ILanguageHandler {

        @Nullable
        private final LanguageId language;
        private final String displayName;
        private final List<String> extensions;

        private MinimalHandler(@Nullable LanguageId language, String displayName, String extension) {
            this.language = language;
            this.displayName = displayName;
            this.extensions = Collections.singletonList(extension);
        }

        @Override
        public LanguageId getLanguageId() {
            return this.language != null ? this.language : ILanguageHandler.super.getLanguageId();
        }

        @Override
        public String getName() {
            return this.language != null ? this.language.getName() : this.displayName;
        }

        @Override
        public List<String> getExtensions() {
            return extensions;
        }

        @Nullable
        @Override
        public IScriptFactory getScriptFactory() throws EvaluationException {
            return null;
        }

    }

}
