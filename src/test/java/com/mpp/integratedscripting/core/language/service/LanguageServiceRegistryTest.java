package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.IServerStartTask;
import com.mpp.integratedscripting.api.language.service.LanguageContributorKeys;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.core.language.LanguageHandlerRegistry;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.junit.Before;
import org.junit.Test;

import javax.annotation.Nullable;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the typed service container
 * (docs/LANGUAGE-API-REFACTOR.md Phase 1).
 */
public class LanguageServiceRegistryTest {

    private LanguageServiceRegistry services;
    private LanguageHandlerRegistry handlers;

    @Before
    public void setUp() {
        this.services = new LanguageServiceRegistry();
        this.handlers = new LanguageHandlerRegistry(this.services);
        this.services.attachHandlerRegistry(this.handlers);
    }

    @Test
    public void testUnknownLanguageFallsBackToDefaults() {
        ILanguageServices bundle = services.getServices(LanguageId.of("mymod", "nothing"));
        assertSame(DefaultServices.PLAIN_HIGHLIGHT, bundle.get(LanguageServiceKeys.HIGHLIGHT));
        assertSame(DefaultServices.PLAIN_EDIT, bundle.get(LanguageServiceKeys.EDIT));
        assertSame(DefaultServices.EMPTY_DIAGNOSTICS, bundle.get(LanguageServiceKeys.DIAGNOSTICS));
        assertEquals("txt", bundle.get(LanguageServiceKeys.EDIT).getDefaultFileExtension());
        assertEquals(Collections.emptyList(),
                bundle.getContributors(LanguageContributorKeys.SERVER_START_TASKS));
        assertEquals(Collections.emptyList(), bundle.getContributors(LanguageContributorKeys.COMPLETION));
    }

    @Test
    public void testRegisteredServiceReplacesTheDefault() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        IEditSupport custom = new IEditSupport() {
            @Override
            public String getDefaultFileExtension() {
                return "ml";
            }
        };
        services.registerService(language, LanguageServiceKeys.EDIT, custom);
        assertSame(custom, services.getServices(language).get(LanguageServiceKeys.EDIT));
        assertEquals("ml", services.getServices(language).get(LanguageServiceKeys.EDIT)
                .getDefaultFileExtension());
    }

    @Test
    public void testRegisteringInvalidatesTheCachedBundle() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        ILanguageServices before = services.getServices(language);
        assertSame(DefaultServices.PLAIN_HIGHLIGHT, before.get(LanguageServiceKeys.HIGHLIGHT));

        IHighlightService custom = new IHighlightService() {
            @Override
            public ILineTokenizer getTokenizer() {
                return null;
            }
        };
        services.registerService(language, LanguageServiceKeys.HIGHLIGHT, custom);
        ILanguageServices after = services.getServices(language);
        assertSame(custom, after.get(LanguageServiceKeys.HIGHLIGHT));
        // The old snapshot is immutable and keeps its old value.
        assertSame(DefaultServices.PLAIN_HIGHLIGHT, before.get(LanguageServiceKeys.HIGHLIGHT));
    }

    @Test
    public void testContributorsAreOrderedByPriorityThenRegistration() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        ICompletionContributor low = contributor("low");
        ICompletionContributor high = contributor("high");
        ICompletionContributor highSecond = contributor("high-second");
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 5, low);
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 100, high);
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 100, highSecond);

        List<ICompletionContributor> contributors = services.getServices(language)
                .getContributors(LanguageContributorKeys.COMPLETION);
        assertEquals(3, contributors.size());
        assertSame(high, contributors.get(0));
        assertSame(highSecond, contributors.get(1));
        assertSame(low, contributors.get(2));
    }

    @Test
    public void testContributorKeysAreIndependent() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        services.registerContributor(language, LanguageContributorKeys.SERVER_START_TASKS, 0,
                new IServerStartTask() {
                    @Override
                    public void onServerStarted() {
                    }
                });
        ILanguageServices bundle = services.getServices(language);
        assertEquals(1, bundle.getContributors(LanguageContributorKeys.SERVER_START_TASKS).size());
        assertTrue(bundle.getContributors(LanguageContributorKeys.COMPLETION).isEmpty());
    }

    @Test
    public void testCompletionCompositeDeduplicatesSortsAndTruncates() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 10,
                permissiveContributor(CompletionItem.builder("beta").kind(CompletionKind.KEYWORD)
                        .priority(0).build()));
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 100,
                permissiveContributor(CompletionItem.builder("beta").kind(CompletionKind.VARIABLE)
                        .priority(5).build()));
        services.registerContributor(language, LanguageContributorKeys.COMPLETION, 50,
                permissiveContributor(CompletionItem.builder("alpha").priority(0).build()));

        List<CompletionItem> items = services.getServices(language)
                .get(LanguageServiceKeys.COMPLETION).complete(context(language, ""));
        assertEquals(2, items.size());
        // "beta" was offered twice; the higher-priority contributor's candidate won,
        // and the item's own priority (5) sorts it before alpha (0).
        assertEquals("beta", items.get(0).getLabel());
        assertEquals(CompletionKind.VARIABLE, items.get(0).getKind());
        assertEquals("alpha", items.get(1).getLabel());
        assertEquals(CompletionKind.VARIABLE, items.get(1).getKind());
    }

    @Test
    public void testCompletionServiceLimitIsRespected() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        final ICompletionService limited = new ICompletionService() {
            @Override
            public int getMaxItems() {
                return 2;
            }

            @Override
            public List<CompletionItem> complete(CompletionContext context) {
                return Arrays.asList(CompletionItem.builder("a").build(),
                        CompletionItem.builder("b").build(),
                        CompletionItem.builder("c").build());
            }
        };
        services.registerService(language, LanguageServiceKeys.COMPLETION, limited);
        List<CompletionItem> items = services.getServices(language)
                .get(LanguageServiceKeys.COMPLETION).complete(context(language, ""));
        assertEquals(2, items.size());
    }

    @Test
    public void testPathResolution() {
        LanguageId language = LanguageId.of("mymod", "mylang");
        handlers.register(new FakeHandler(language, "fake", "fake2"));
        assertSame(services.getServices(language),
                services.getServices(Paths.get("dir", "script5.fake")));
        assertSame(services.getServices(language),
                services.getServices(Paths.get("script.FAKE")));
        assertNull(services.getServices(Paths.get("script.txt")));
        assertNull(services.getServices(Paths.get("noextension")));
    }

    @Test
    public void testGetLanguages() {
        LanguageId first = LanguageId.of("mymod", "a");
        LanguageId second = LanguageId.of("mymod", "b");
        services.registerService(first, LanguageServiceKeys.EDIT, DefaultServices.PLAIN_EDIT);
        services.registerContributor(second, LanguageContributorKeys.COMPLETION, 0,
                contributor("x"));
        assertEquals(2, services.getLanguages().size());
        assertTrue(services.getLanguages().contains(first));
        assertTrue(services.getLanguages().contains(second));
    }

    @Test
    public void testNestedTokenizerIsNotNullForPlainHighlighting() {
        ILanguageServices bundle = services.getServices(LanguageId.PLAIN_TEXT);
        IHighlightService highlight = bundle.get(LanguageServiceKeys.HIGHLIGHT);
        assertNotNull(highlight.getTokenizer());
        List<List<Token>> lines = highlight.highlight(DocumentText.of("a\nbb"));
        assertEquals(2, lines.size());
        assertEquals(new Token(0, 1, TokenType.DEFAULT), lines.get(0).get(0));
        assertEquals(new Token(0, 2, TokenType.DEFAULT), lines.get(1).get(0));
        assertEquals(LexicalContext.CODE, highlight.lexicalContextAt(DocumentText.of("x"), 0));
    }

    private static CompletionContext context(LanguageId language, String prefix) {
        return new CompletionContext(DocumentText.of(""), 0, language, LexicalContext.CODE, (char) 0,
                prefix);
    }

    private static ICompletionContributor contributor(final String label) {
        return permissiveContributor(CompletionItem.builder(label).build());
    }

    private static ICompletionContributor permissiveContributor(final CompletionItem item) {
        return new ICompletionContributor() {
            @Override
            public void contribute(CompletionContext context, CompletionCollector out) {
                out.add(item);
            }
        };
    }

    private static final class FakeHandler implements ILanguageHandler {

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

}
