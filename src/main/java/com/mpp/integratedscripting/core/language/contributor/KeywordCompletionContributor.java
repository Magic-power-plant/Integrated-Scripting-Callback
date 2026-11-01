package com.mpp.integratedscripting.core.language.contributor;

import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Offers the language's keywords as completion candidates.
 *
 * <p>The keyword set comes from the language module (JavaScript passes
 * {@code JavaScriptLexer.KEYWORDS}), so the contributor stays language
 * independent and can be reused by another language with a different set.</p>
 *
 * @author rubensworks
 */
public final class KeywordCompletionContributor implements ICompletionContributor {

    private final Collection<String> keywords;
    private final CompletionKind kind;

    /**
     * @param keywords The keywords of the language.
     */
    public KeywordCompletionContributor(Collection<String> keywords) {
        this(keywords, CompletionKind.KEYWORD);
    }

    /**
     * @param keywords The keywords of the language.
     * @param kind The kind of the offered candidates.
     */
    public KeywordCompletionContributor(Collection<String> keywords, CompletionKind kind) {
        this.keywords = keywords == null ? Collections.<String>emptyList() : keywords;
        this.kind = kind == null ? CompletionKind.KEYWORD : kind;
    }

    @Override
    public void contribute(CompletionContext context, CompletionCollector out) {
        if (!context.isInCode()) {
            return;
        }
        for (String keyword : this.keywords) {
            if (ContributorSupport.matchesPrefix(keyword, context.getPrefix())) {
                out.add(CompletionItem.builder(keyword)
                        .kind(this.kind)
                        .sortText(keyword.toLowerCase(Locale.ROOT))
                        .build());
            }
        }
    }

    @Override
    public String toString() {
        return "KeywordCompletionContributor(" + keywords.size() + " keywords)";
    }

}
