package com.mpp.integratedscripting.core.language;

import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Map;

/**
 * Maps ID value types to the TypeScript types of the generated declarations.
 *
 * <p>Only the five scalar types, the three containers and the object types that
 * actually got an interface are named; everything else becomes {@code any}
 * instead of a {@code ValueXxx} reference to an interface that was never
 * declared. That fallback is what makes the file valid for value types
 * registered by other mods: a type without a translator keeps working in
 * scripts, it simply does not get a precise declaration.</p>
 *
 * @author rubensworks
 */
final class TypingsTypes {

    public static final String IDENTITY_BLOCK = "BlockIdentity";
    public static final String IDENTITY_ITEMSTACK = "ItemStackIdentity";
    public static final String IDENTITY_FLUIDSTACK = "FluidStackIdentity";
    public static final String IDENTITY_ENTITY = "EntityIdentity";
    public static final String IDENTITY_INGREDIENTS = "IngredientsIdentity";
    public static final String IDENTITY_RECIPE = "RecipeIdentity";

    private final Map<IValueType<?>, String> interfaceNames;

    /**
     * @param interfaceNames The object value types that get an interface,
     *                       keyed by identity.
     */
    TypingsTypes(Map<IValueType<?>, String> interfaceNames) {
        this.interfaceNames = interfaceNames;
    }

    /**
     * @param valueType A value type, or null for an unspecified one.
     * @return The TypeScript type to use in a signature.
     */
    TypingsType of(@Nullable IValueType<?> valueType) {
        if (valueType == null) {
            return TypingsType.ANY;
        }
        // The number category covers integer, long and double, which all cross
        // the JavaScript boundary as `number`.
        if (ValueTypes.CATEGORY_NUMBER.correspondsTo(valueType)) {
            return TypingsType.NUMBER;
        }
        if (valueType.isCategory()) {
            return TypingsType.ANY;
        }
        if (valueType == ValueTypes.BOOLEAN) {
            return TypingsType.BOOLEAN;
        }
        if (valueType == ValueTypes.STRING) {
            return TypingsType.STRING;
        }
        if (valueType == ValueTypes.LIST) {
            return TypingsType.LIST;
        }
        if (valueType == ValueTypes.NBT) {
            return TypingsType.NBT;
        }
        if (valueType == ValueTypes.OPERATOR) {
            return TypingsType.OPERATOR;
        }
        String interfaceName = this.interfaceNames.get(valueType);
        if (interfaceName != null) {
            return TypingsType.object(interfaceName, valueType.getTypeName());
        }
        return TypingsType.ANY;
    }

    /**
     * @param memberKey The identifying member of an object value, for example
     *                  {@code id_item}.
     * @return The TypeScript type of that member. The six built-in ID object
     *         types get a documented shape; every other object type publishes
     *         its serialized NBT form, which is a compound.
     */
    static String identityType(String memberKey) {
        if (memberKey == null) {
            return "NbtCompound";
        }
        switch (memberKey) {
            case "id_block":
                return IDENTITY_BLOCK;
            case "id_item":
                return IDENTITY_ITEMSTACK;
            case "id_fluid":
                return IDENTITY_FLUIDSTACK;
            case "id_entity":
                return IDENTITY_ENTITY;
            case "id_ingredients":
                return IDENTITY_INGREDIENTS;
            case "id_recipe":
                return IDENTITY_RECIPE;
            default:
                return "NbtCompound";
        }
    }

    /**
     * @param typeName A value type's ID type name, for example {@code itemstack}.
     * @return The interface name for it, for example {@code ValueItemstack}.
     */
    static String interfaceNameFor(String typeName) {
        String cleaned = typeName == null ? "" : typeName;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char character = cleaned.charAt(i);
            if (Character.isLetterOrDigit(character) || character == '_' || character == '$') {
                builder.append(character);
            }
        }
        String name = builder.toString();
        if (name.isEmpty()) {
            return "ValueUnknown";
        }
        return "Value" + name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    @Override
    public String toString() {
        return "TypingsTypes(" + this.interfaceNames.size() + " interfaces)";
    }

}
