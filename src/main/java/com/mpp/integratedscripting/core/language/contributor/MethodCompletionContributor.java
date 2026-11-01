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
 * Offers object methods after a member-access dot, for example
 * {@code item.sta|}.
 *
 * <p>When the value type of the object is not known (the normal case for a text
 * editor without a type checker) the union of every scoped method name is
 * offered; Integrated Dynamics resolves the actual method at run time. The name
 * source is injectable so the contributor can be tested without the game.</p>
 *
 * @author rubensworks
 */
public final class MethodCompletionContributor implements ICompletionContributor {

    private final Supplier<? extends Collection<String>> names;

    /**
     * Use the game's scoped method tables.
     */
    public MethodCompletionContributor() {
        this(new Supplier<Collection<String>>() {
            @Override
            public Collection<String> get() {
                return OperatorBindings.getAllMethodNames();
            }
        });
    }

    /**
     * @param names Supplies the object method names.
     */
    public MethodCompletionContributor(Supplier<? extends Collection<String>> names) {
        this.names = names;
    }

    @Override
    public void contribute(CompletionContext context, CompletionCollector out) {
        if (!context.isInCode() || !ContributorSupport.isMemberAccess(context)) {
            return;
        }
        Collection<String> methods;
        try {
            methods = this.names.get();
        } catch (RuntimeException e) {
            return;
        } catch (LinkageError e) {
            return;
        }
        if (methods == null) {
            return;
        }
        for (String name : methods) {
            if (ContributorSupport.matchesPrefix(name, context.getPrefix())) {
                out.add(CompletionItem.builder(name)
                        .kind(CompletionKind.FUNCTION)
                        .detail("object method")
                        .build());
            }
        }
    }

    @Override
    public String toString() {
        return "MethodCompletionContributor";
    }

}
