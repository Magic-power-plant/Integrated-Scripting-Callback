package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The completion policy of JavaScript.
 *
 * <p>The candidates themselves come from the language's completion
 * <em>contributors</em> (keywords, document symbols, operators, object methods),
 * which the service registry merges into a composite. Keeping the policy here
 * means the trigger characters and the item limit are language data, and a
 * third-party mod can add candidates for JavaScript without replacing this
 * class.</p>
 *
 * @author rubensworks
 */
public final class JavaScriptCompletionService implements ICompletionService {

    /** JavaScript also offers members after a dot. */
    public static final Set<Character> TRIGGER_CHARACTERS = Collections.singleton('.');

    /** The popup never needs more than this many candidates for one request. */
    public static final int MAX_ITEMS = 100;

    @Override
    public Set<Character> getTriggerCharacters() {
        return TRIGGER_CHARACTERS;
    }

    @Override
    public int getMaxItems() {
        return MAX_ITEMS;
    }

    @Override
    public List<CompletionItem> complete(CompletionContext context) {
        // JavaScript's own candidate sources are registered contributors; this
        // service deliberately adds none of its own so that replacing a data
        // source (or adding one) never requires touching the language service.
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "JavaScriptCompletionService";
    }

}
