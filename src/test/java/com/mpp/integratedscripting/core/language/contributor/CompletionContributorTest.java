package com.mpp.integratedscripting.core.language.contributor;

import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.LexicalContext;
import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the built-in completion contributors
 * (docs/LANGUAGE-API-REFACTOR.md Phase 3).
 */
public class CompletionContributorTest {

    private static CompletionContext context(String document, int offset, char trigger,
                                             String prefix, int lexicalContext) {
        return new CompletionContext(DocumentText.of(document), offset, LanguageId.JAVASCRIPT,
                lexicalContext, trigger, prefix);
    }

    private static CompletionContext codeContext(String document, int offset, String prefix) {
        return context(document, offset, (char) 0, prefix, LexicalContext.CODE);
    }

    private static List<CompletionItem> collect(ICompletionContributor contributor,
                                                CompletionContext context) {
        final List<CompletionItem> items = new ArrayList<>();
        contributor.contribute(context, new CompletionCollector() {
            @Override
            public void add(CompletionItem item) {
                items.add(item);
            }
        });
        return items;
    }

    @Test
    public void testKeywordContributorFiltersByPrefix() {
        ICompletionContributor contributor = new KeywordCompletionContributor(
                Arrays.asList("const", "return", "while"));
        assertEquals(0, collect(contributor, codeContext("", 0, "zz")).size());
    }

    @Test
    public void testKeywordContributorWithPrefix() {
        ICompletionContributor contributor = new KeywordCompletionContributor(
                Arrays.asList("const", "return", "while"));
        List<CompletionItem> items = collect(contributor, codeContext("re", 2, "re"));
        assertEquals(1, items.size());
        assertEquals("return", items.get(0).getLabel());
        assertEquals(CompletionKind.KEYWORD, items.get(0).getKind());
    }

    @Test
    public void testKeywordContributorOffersEverythingForExplicitRequest() {
        ICompletionContributor contributor = new KeywordCompletionContributor(
                Arrays.asList("const", "return", "while"));
        List<CompletionItem> items = collect(contributor, codeContext("", 0, ""));
        assertEquals(3, items.size());
    }

    @Test
    public void testContributorsAreSilentOutsideCode() {
        ICompletionContributor contributor = new KeywordCompletionContributor(
                Arrays.asList("const", "return"));
        CompletionContext inString = context("'con", 4, (char) 0, "con", LexicalContext.STRING);
        assertTrue(collect(contributor, inString).isEmpty());
        CompletionContext inComment = context("// con", 6, (char) 0, "con",
                LexicalContext.COMMENT);
        assertTrue(collect(contributor, inComment).isEmpty());
    }

    @Test
    public void testDocumentSymbolContributor() {
        String document = "const foo = 1;\nfunction bar() {}\nlet baz;\nclass Qux {}\n";
        ICompletionContributor contributor = new DocumentSymbolCompletionContributor();
        List<CompletionItem> all = collect(contributor, codeContext(document, 0, ""));
        assertEquals(4, all.size());
        assertEquals("foo", all.get(0).getLabel());
        assertEquals(CompletionKind.VARIABLE, all.get(0).getKind());
        assertEquals("bar", all.get(1).getLabel());
        assertEquals(CompletionKind.FUNCTION, all.get(1).getKind());

        List<CompletionItem> prefixed = collect(contributor, codeContext(document, 0, "ba"));
        assertEquals(2, prefixed.size());
        assertEquals("bar", prefixed.get(0).getLabel());
        assertEquals("baz", prefixed.get(1).getLabel());
    }

    @Test
    public void testDocumentSymbolContributorIgnoresPlainIdentifiers() {
        String document = "foo = bar + 1;\nconsole.log(baz);\n";
        assertTrue(collect(new DocumentSymbolCompletionContributor(),
                codeContext(document, 0, "")).isEmpty());
    }

    @Test
    public void testMethodContributorOnlyAfterADot() {
        ICompletionContributor contributor = new MethodCompletionContributor(
                names("stackable", "strength", "length"));

        // No dot: nothing.
        assertTrue(collect(contributor, codeContext("sta", 3, "sta")).isEmpty());

        // After a dot: methods matching the prefix.
        List<CompletionItem> items = collect(contributor,
                codeContext("item.sta", 8, "sta"));
        assertEquals(1, items.size());
        assertEquals("stackable", items.get(0).getLabel());
        assertEquals(CompletionKind.FUNCTION, items.get(0).getKind());

        // Directly after the dot the prefix is empty: every method is offered.
        assertEquals(3, collect(contributor, codeContext("item.", 5, "")).size());
    }

    @Test
    public void testOperatorContributor() {
        ICompletionContributor contributor = new OperatorCompletionContributor(
                names("arithmeticAddition", "itemstackStackable", "listLength"));
        List<CompletionItem> items = collect(contributor, codeContext("item", 4, "item"));
        assertEquals(1, items.size());
        assertEquals("itemstackStackable", items.get(0).getLabel());
        assertEquals(CompletionKind.OPERATOR, items.get(0).getKind());
    }

    @Test
    public void testContributorPriorityDefaultsToZero() {
        assertEquals(0, new KeywordCompletionContributor(Arrays.asList("a")).getPriority());
        assertEquals(0, new DocumentSymbolCompletionContributor().getPriority());
        assertEquals(0, new OperatorCompletionContributor(names("a")).getPriority());
        assertEquals(0, new MethodCompletionContributor(names("a")).getPriority());
    }

    private static java.util.function.Supplier<Collection<String>> names(String... names) {
        final List<String> values = Arrays.asList(names);
        return new java.util.function.Supplier<Collection<String>>() {
            @Override
            public Collection<String> get() {
                return values;
            }
        };
    }

}
