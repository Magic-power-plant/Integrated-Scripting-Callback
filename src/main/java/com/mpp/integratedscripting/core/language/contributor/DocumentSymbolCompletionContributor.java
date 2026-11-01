package com.mpp.integratedscripting.core.language.contributor;

import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offers the symbols declared in the document.
 *
 * <p>This is a deliberately small document scan, not a symbol index: it finds
 * {@code const}/{@code let}/{@code var}/{@code function}/{@code class}
 * declarations. A future symbol index can replace this contributor without
 * changing the completion service or the editor.</p>
 *
 * @author rubensworks
 */
public final class DocumentSymbolCompletionContributor implements ICompletionContributor {

    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(const|let|var|function|class)\\s+([\\p{L}_$][\\p{L}\\p{N}_$]*)");

    @Override
    public void contribute(CompletionContext context, CompletionCollector out) {
        if (!context.isInCode()) {
            return;
        }
        String text = context.getDocument().getText();
        Matcher matcher = DECLARATION.matcher(text);
        Set<String> seen = new HashSet<>();
        while (matcher.find()) {
            String name = matcher.group(2);
            if (name == null || !seen.add(name)) {
                continue;
            }
            if (!ContributorSupport.matchesPrefix(name, context.getPrefix())) {
                continue;
            }
            CompletionKind kind = "function".equals(matcher.group(1))
                    ? CompletionKind.FUNCTION : CompletionKind.VARIABLE;
            out.add(CompletionItem.builder(name)
                    .kind(kind)
                    .detail("declared in this script")
                    .build());
        }
    }

    @Override
    public String toString() {
        return "DocumentSymbolCompletionContributor";
    }

}
