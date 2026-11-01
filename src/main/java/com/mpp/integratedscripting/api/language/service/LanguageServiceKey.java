package com.mpp.integratedscripting.api.language.service;

/**
 * Typed key identifying one language service implementation.
 *
 * <p>The class token makes the cast inside the service container safe, so call
 * sites never need {@code @SuppressWarnings("unchecked")}:</p>
 *
 * <pre>
 * IEditSupport edit = services.get(LanguageServiceKeys.EDIT);
 * </pre>
 *
 * <p>Every key carries a default implementation (a null object), so
 * {@link ILanguageServices#get(LanguageServiceKey)} never returns {@code null}
 * and callers never check for a missing provider.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md). Define your
 * own key with {@link #of(String, Class, Object)} to add a capability without
 * changing any existing interface.</p>
 *
 * @param <T> The service type.
 * @author rubensworks
 */
public final class LanguageServiceKey<T> {

    private final String id;
    private final Class<T> type;
    private final T defaultValue;

    private LanguageServiceKey(String id, Class<T> type, T defaultValue) {
        this.id = id;
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /**
     * Create a service key.
     * @param id A unique, stable id, for example {@code "highlight"}.
     * @param type The service interface, used for the type-safe cast.
     * @param defaultValue The null-object returned when no provider is registered;
     *                     must not be {@code null}, otherwise callers would have to
     *                     check for absence.
     * @param <T> The service type.
     * @return The key.
     */
    public static <T> LanguageServiceKey<T> of(String id, Class<T> type, T defaultValue) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("A language service key needs an id.");
        }
        if (type == null) {
            throw new IllegalArgumentException("A language service key needs a type.");
        }
        if (defaultValue == null) {
            throw new IllegalArgumentException("The language service key '" + id
                    + "' needs a default implementation (null-object).");
        }
        return new LanguageServiceKey<>(id, type, defaultValue);
    }

    /**
     * @return The stable id of this key.
     */
    public String getId() {
        return id;
    }

    /**
     * @return The service interface.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * @return The default implementation used when no provider is registered.
     */
    public T getDefaultValue() {
        return defaultValue;
    }

    /**
     * Type-safe cast, so the container can store services untyped.
     * @param service A candidate service.
     * @return The service as {@code T}.
     * @throws ClassCastException When the service does not implement {@link #getType()}.
     */
    public T cast(Object service) {
        return type.cast(service);
    }

    @Override
    public String toString() {
        return "LanguageServiceKey(" + id + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LanguageServiceKey)) {
            return false;
        }
        LanguageServiceKey<?> that = (LanguageServiceKey<?>) other;
        return id.equals(that.id) && type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + type.hashCode();
    }

}
