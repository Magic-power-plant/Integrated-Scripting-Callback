package com.mpp.integratedscripting.api.language.service;

/**
 * Typed key identifying one kind of language contributor (a composable list
 * rather than a single service).
 *
 * <p>Contributors are the aggregation extension point: several mods can add to
 * the same list, ordered by priority, without any of them replacing another.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @param <T> The contributor type.
 * @author rubensworks
 */
public final class LanguageContributorKey<T> {

    private final String id;
    private final Class<T> type;

    private LanguageContributorKey(String id, Class<T> type) {
        this.id = id;
        this.type = type;
    }

    /**
     * Create a contributor key.
     * @param id A unique, stable id, for example {@code "completion"}.
     * @param type The contributor interface, used for the type-safe cast.
     * @param <T> The contributor type.
     * @return The key.
     */
    public static <T> LanguageContributorKey<T> of(String id, Class<T> type) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A language contributor key needs an id.");
        }
        if (type == null) {
            throw new IllegalArgumentException("A language contributor key needs a type.");
        }
        return new LanguageContributorKey<>(id, type);
    }

    /**
     * @return The stable id of this key.
     */
    public String getId() {
        return id;
    }

    /**
     * @return The contributor interface.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Type-safe cast, so the container can store contributions untyped.
     * @param contributor A candidate contributor.
     * @return The contributor as {@code T}.
     * @throws ClassCastException When the contributor does not implement {@link #getType()}.
     */
    public T cast(Object contributor) {
        return type.cast(contributor);
    }

    @Override
    public String toString() {
        return "LanguageContributorKey(" + id + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanguageContributorKey)) {
            return false;
        }
        LanguageContributorKey<?> that = (LanguageContributorKey<?>) other;
        return id.equals(that.id) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + type.hashCode();
    }

}
