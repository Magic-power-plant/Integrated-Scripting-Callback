package com.mpp.integratedscripting.api.language.completion;

import javax.annotation.Nullable;

/**
 * One completion candidate.
 *
 * <p>Items are immutable values produced by completion contributors; the
 * completion service deduplicates, sorts and truncates them. The popup only
 * renders and commits what the service returned, which keeps the data source
 * layer testable without a client.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class CompletionItem implements Comparable<CompletionItem> {

    private final String label;
    private final String insertText;
    private final int cursorOffset;
    private final CompletionKind kind;
    @Nullable
    private final String detail;
    private final String sortText;
    private final int priority;

    private CompletionItem(Builder builder) {
        this.label = builder.label;
        this.insertText = builder.insertText == null ? builder.label : builder.insertText;
        this.cursorOffset = builder.cursorOffset < 0 ? this.insertText.length() : builder.cursorOffset;
        this.kind = builder.kind == null ? CompletionKind.VARIABLE : builder.kind;
        this.detail = builder.detail;
        this.sortText = builder.sortText == null ? builder.label : builder.sortText;
        this.priority = builder.priority;
    }

    /**
     * @param label The text shown in the popup; must not be empty.
     * @return A builder for one candidate.
     */
    public static Builder builder(String label) {
        return new Builder(label);
    }

    /**
     * Shorthand for a keyword candidate.
     * @param label The keyword.
     * @return The candidate.
     */
    public static CompletionItem keyword(String label) {
        return builder(label).kind(CompletionKind.KEYWORD).build();
    }

    /**
     * @return The text shown in the popup.
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return The text inserted when the candidate is accepted.
     */
    public String getInsertText() {
        return insertText;
    }

    /**
     * @return The caret position after insertion, relative to the start of the
     *         inserted text.
     */
    public int getCursorOffset() {
        return cursorOffset;
    }

    /**
     * @return The semantic kind of the candidate; never {@code null}.
     */
    public CompletionKind getKind() {
        return kind;
    }

    /**
     * @return A short elaboration shown next to the label, or {@code null}.
     */
    @Nullable
    public String getDetail() {
        return detail;
    }

    /**
     * @return The text the service sorts by, lower case by default.
     */
    public String getSortText() {
        return sortText;
    }

    /**
     * @return The contributor's priority; higher candidates survive deduplication.
     */
    public int getPriority() {
        return priority;
    }

    /**
     * Stable ordering used by the completion service: higher priority first, then
     * {@link #getSortText()} (case insensitive), then the label.
     */
    @Override
    public int compareTo(CompletionItem other) {
        if (priority != other.priority) {
            return priority > other.priority ? -1 : 1;
        }
        int bySortText = sortText.compareToIgnoreCase(other.sortText);
        if (bySortText != 0) {
            return bySortText;
        }
        return label.compareTo(other.label);
    }

    /**
     * Identity used for deduplication: two candidates that insert the same text
     * are the same completion, even when they come from different contributors.
     * @param other Another item.
     * @return Whether both insert the same text.
     */
    public boolean insertsSameText(CompletionItem other) {
        return insertText.equals(other.insertText);
    }

    @Override
    public String toString() {
        return label + "(" + kind + ")";
    }

    /**
     * Fluent builder for {@link CompletionItem}.
     */
    public static final class Builder {

        private final String label;
        private String insertText;
        private int cursorOffset = -1;
        private CompletionKind kind;
        private String detail;
        private String sortText;
        private int priority;

        private Builder(String label) {
            if (label == null || label.isEmpty()) {
                throw new IllegalArgumentException("A completion label must not be empty.");
            }
            this.label = label;
        }

        /**
         * @param insertText The text to insert; defaults to the label.
         * @return This builder.
         */
        public Builder insertText(String insertText) {
            this.insertText = insertText;
            return this;
        }

        /**
         * @param cursorOffset The caret offset after insertion, relative to the
         *                     inserted text; defaults to its end.
         * @return This builder.
         */
        public Builder cursorOffset(int cursorOffset) {
            this.cursorOffset = cursorOffset;
            return this;
        }

        /**
         * @param kind The semantic kind.
         * @return This builder.
         */
        public Builder kind(CompletionKind kind) {
            this.kind = kind;
            return this;
        }

        /**
         * @param detail A short elaboration.
         * @return This builder.
         */
        public Builder detail(String detail) {
            this.detail = detail;
            return this;
        }

        /**
         * @param sortText The text to sort by; defaults to the label.
         * @return This builder.
         */
        public Builder sortText(String sortText) {
            this.sortText = sortText;
            return this;
        }

        /**
         * @param priority The contributor priority; higher is better.
         * @return This builder.
         */
        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        /**
         * @return The immutable candidate.
         */
        public CompletionItem build() {
            return new CompletionItem(this);
        }

    }

}
