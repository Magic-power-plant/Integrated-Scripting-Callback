package com.mpp.integratedscripting.core.network;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptMember;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

import javax.annotation.Nullable;
import java.nio.file.Path;

/**
 * A script referencing a Graal value.
 *
 * <p>1.12.2 port differences (docs/AUDIT.md D1): the invalidation listeners and
 * the release of the compiled script live in a
 * {@link ScriptInvalidationDispatcher} shared by the root script and all of its
 * members, and a released script produces a localized
 * {@link EvaluationException} instead of letting GraalVM's
 * {@link IllegalStateException} ("The Context is already closed") escape into
 * the evaluation path.</p>
 *
 * @author rubensworks
 */
public class GraalScript implements IScript, IScriptMember {

    private final Context graalContext;
    private final Value graalValue;
    private final ScriptInvalidationDispatcher invalidation;
    private final int disk;
    private final Path path;
    @Nullable
    private final String member;

    GraalScript(Context graalContext, Value graalValue,
                ScriptInvalidationDispatcher invalidation,
                int disk, Path path, @Nullable String member) {
        this.graalContext = graalContext;
        this.graalValue = graalValue;
        this.invalidation = invalidation;
        this.disk = disk;
        this.path = path;
        this.member = member;
    }

    @Nullable
    @Override
    public IScriptMember getMember(String memberName) throws EvaluationException {
        IEvaluationExceptionFactory exceptionFactory =
                ScriptHelpers.getEvaluationExceptionFactory(this.disk, this.path, memberName);
        assertUsable(exceptionFactory);
        Value memberValue;
        try {
            memberValue = this.graalValue.getMember(memberName);
        } catch (PolyglotException e) {
            throw exceptionFactory.createError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        return memberValue == null ? null : new GraalScript(
                this.graalContext, memberValue, this.invalidation, this.disk, this.path, memberName);
    }

    /**
     * Fail readably when the underlying Graal context was already released,
     * instead of throwing {@link IllegalStateException} from inside GraalVM.
     * @param exceptionFactory The exception factory to use.
     * @throws EvaluationException When this script version was invalidated.
     */
    private void assertUsable(IEvaluationExceptionFactory exceptionFactory) throws EvaluationException {
        if (this.invalidation.isReleased()) {
            throw exceptionFactory.createError(
                    "script.integratedscripting.error.script_invalidated",
                    this.path == null ? "" : this.path.toString(), this.disk);
        }
    }

    @Override
    public void addInvalidationListener(IInvalidateListener listener) {
        this.invalidation.add(listener);
    }

    @Override
    public void removeInvalidationListener(IInvalidateListener listener) {
        this.invalidation.remove(listener);
    }

    @Override
    public void removeInvalidationListeners() {
        this.invalidation.clear();
    }

    @Override
    public void release() {
        this.invalidation.releaseAndNotify();
    }

    @Override
    public IValue getValue() throws EvaluationException {
        IEvaluationExceptionFactory exceptionFactory = ScriptHelpers.getEvaluationExceptionFactory(
                this.disk, this.path, this.member);
        assertUsable(exceptionFactory);
        try {
            // Without this the statement counter would accumulate across evaluations (§8.3).
            this.graalContext.resetLimits();
            return ValueTranslators.REGISTRY.translateFromGraal(
                    this.graalContext, this.graalValue, exceptionFactory);
        } catch (PolyglotException e) {
            throw exceptionFactory.createError(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

}
