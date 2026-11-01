package com.mpp.integratedscripting.core.language.contributor;

import com.mpp.integratedscripting.api.language.completion.CompletionCollector;
import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.CompletionKind;
import com.mpp.integratedscripting.api.language.completion.ICompletionContributor;
import com.mpp.integratedscripting.callback.OperatorBindings;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Offers Integrated Dynamics' global script operators (the operator functions the
 * scripting runtime exposes).
 *
 * <p>The data comes from {@link OperatorBindings}, which needs the Integrated
 * Dynamics operator registry. A client that has not initialised it (or a unit
 * test without the game) simply gets no candidates: a completion source must
 * never break the editor. The name source is injectable so the contributor can
 * be tested without the game.</p>
 *
 * @author rubensworks
 */
public final class OperatorCompletionContributor implements ICompletionContributor {

    private final Supplier<? extends Collection<String>> names;

    /**
     * Use the game's global operator table.
     */
    public OperatorCompletionContributor() {
        this(new Supplier<Collection<String>>() {
            @Override
            public Collection<String> get() {
                return OperatorBindings.getGlobalOperators().keySet();
            }
        });
    }

    /**
     * @param names Supplies the global operator names.
     */
    public OperatorCompletionContributor(Supplier<? extends Collection<String>> names) {
        this.names = names;
    }

    @Override
    public void contribute(CompletionContext context, CompletionCollector out) {
        if (!context.isInCode()) {
            return;
        }
        Collection<String> operators;
        try {
            operators = this.names.get();
        } catch (RuntimeException e) {
            return;
        } catch (LinkageError e) {
            return;
        }
        if (operators == null) {
            return;
        }
        for (String name : operators) {
            if (ContributorSupport.matchesPrefix(name, context.getPrefix())) {
                out.add(CompletionItem.builder(name)
                        .kind(CompletionKind.OPERATOR)
                        .detail("global operator")
                        .build());
            }
        }
    }

    @Override
    public String toString() {
        return "OperatorCompletionContributor";
    }

}
