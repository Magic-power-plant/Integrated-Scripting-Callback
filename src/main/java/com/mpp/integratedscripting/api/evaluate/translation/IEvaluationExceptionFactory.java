package com.mpp.integratedscripting.api.evaluate.translation;

import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

/**
 * Construct evaluation exceptions with a given message.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §5.6): {@link EvaluationException}
 * only accepts a plain {@link String}, so messages are localized eagerly at the
 * point where the error is created.</p>
 *
 * @author rubensworks
 */
public interface IEvaluationExceptionFactory {

    /**
     * @param localizedMessage An already localized message.
     * @return The evaluation exception.
     */
    public EvaluationException createError(String localizedMessage);

    /**
     * Localize the given translation key and create an evaluation exception from it.
     * @param translationKey A translation key.
     * @param args Format arguments for the localized message.
     * @return The evaluation exception.
     */
    public default EvaluationException createError(String translationKey, Object... args) {
        return createError(L10NHelpers.localize(translationKey, args));
    }

}
