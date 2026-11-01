package com.mpp.integratedscripting.core.language.contributor;

import com.mpp.integratedscripting.api.language.completion.CompletionContext;

/**
 * Small shared helpers for the built-in completion contributors.
 *
 * @author rubensworks
 */
public final class ContributorSupport {

    private ContributorSupport() {
    }

    /**
     * Whether a candidate matches the typed prefix. Matching is a case
     * insensitive prefix test; an empty prefix matches everything.
     * @param candidate The candidate text.
     * @param prefix The identifier prefix before the caret.
     * @return Whether the candidate should be offered.
     */
    public static boolean matchesPrefix(String candidate, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return candidate != null;
        }
        return candidate != null && candidate.length() >= prefix.length()
                && candidate.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    /**
     * Whether the caret follows a member-access dot once the typed prefix is
     * removed, for example {@code item.sta|}.
     * @param context The completion request.
     * @return Whether the request happens after a dot.
     */
    public static boolean isMemberAccess(CompletionContext context) {
        if (context == null) {
            return false;
        }
        String text = context.getDocument().getText();
        int dot = context.getOffset() - context.getPrefix().length() - 1;
        return dot >= 0 && dot < text.length() && text.charAt(dot) == '.';
    }

}
