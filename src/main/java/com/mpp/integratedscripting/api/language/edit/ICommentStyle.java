package com.mpp.integratedscripting.api.language.edit;

import javax.annotation.Nullable;

/**
 * How one language writes comments.
 *
 * <p>Returning {@code null} for a form means the language does not support it;
 * the editor then skips the corresponding command instead of inserting a wrong
 * prefix.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ICommentStyle {

    /**
     * @return The prefix of a line comment, for example {@code "//"}, or {@code null}.
     */
    @Nullable
    String getLineComment();

    /**
     * @return The opening delimiter of a block comment, for example {@code "/*"},
     *         or {@code null}.
     */
    @Nullable
    String getBlockCommentOpen();

    /**
     * @return The closing delimiter of a block comment, for example the two
     *         character star-slash sequence, or {@code null}.
     */
    @Nullable
    String getBlockCommentClose();

}
