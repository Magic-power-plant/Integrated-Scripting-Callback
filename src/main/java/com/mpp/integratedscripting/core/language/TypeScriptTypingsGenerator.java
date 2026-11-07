package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.callback.TypingsI18n;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates TypeScript definitions for all available operators and value types.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §5.4.5, §6.16): operators are
 * read from {@link OperatorBindings} instead of ID's interact operator
 * registries, and localization goes through {@link TypingsI18n} so that it is
 * safe on a dedicated server.</p>
 *
 * <p>Compared to the 1.20 reference output, the declarations are global (a
 * script is a bare script and can not import types), NBT is a nested
 * {@code NbtCompound}/{@code NbtValue} pair instead of {@code Record&lt;string,
 * any&gt;}, an operator value is the callable {@code Operator} instead of
 * {@code Function}, the identifying member of the six built-in ID object types
 * has a documented shape, {@link TypingsSignatures} supplies generics and
 * element types for what ID 1.12.2 can only describe as {@code list}/{@code any},
 * and object types registered by other mods are included when they have a
 * translator.</p>
 *
 * @author rubensworks
 */
public class TypeScriptTypingsGenerator {

    private static final String INDENT = "  ";

    /**
     * Generate the typings from the live registries.
     * @return The complete {@code .d.ts} content.
     */
    public String generate() {
        List<TypingsDeclarations.Declaration> declarations = TypingsDeclarations.collect(
                registeredValueTypes(),
                ValueTranslators.REGISTRY,
                Arrays.asList(ValueTypes.OBJECT_BLOCK, ValueTypes.OBJECT_ITEMSTACK,
                        ValueTypes.OBJECT_ENTITY, ValueTypes.OBJECT_FLUIDSTACK,
                        ValueTypes.OBJECT_INGREDIENTS, ValueTypes.OBJECT_RECIPE));
        return render(declarations, OperatorBindings.getGlobalOperators(), OperatorBindings::getMethods);
    }

    /**
     * 1.12.2's {@code IValueTypeRegistry#getValueTypes()} returns a raw
     * collection, so the elements are retyped once here instead of at every use.
     * @return Every registered value type.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Collection<IValueType<?>> registeredValueTypes() {
        return (Collection) ValueTypes.REGISTRY.getValueTypes();
    }

    /**
     * Render a complete file from already collected inputs. Separated from
     * {@link #generate()} so the file contract can be tested without the game's
     * operator and value type registries.
     * @param declarations The object value types that get an interface.
     * @param globalOperators The global operators, keyed by script name.
     * @param methods The scoped methods of a value type, keyed by method name.
     * @return The complete {@code .d.ts} content.
     */
    String render(List<TypingsDeclarations.Declaration> declarations,
                  Map<String, IOperator> globalOperators,
                  Function<IValueType<?>, Map<String, IOperator>> methods) {
        TypingsTypes types = new TypingsTypes(TypingsDeclarations.names(declarations));

        StringBuilder file = new StringBuilder();
        appendHeader(file, memoryInterfaceName(declarations));
        appendGlobalOperators(file, globalOperators, types);
        for (TypingsDeclarations.Declaration declaration : declarations) {
            appendObjectType(file, declaration, methods, types);
        }
        return file.toString();
    }

    /**
     * @param declarations The declared object types.
     * @return The interface name of the addressed memory type, or {@code any} when
     *         it is not available (tests, or a mod setup without the block).
     */
    private static String memoryInterfaceName(List<TypingsDeclarations.Declaration> declarations) {
        IValueType<?> memoryType = AddressedMemoryValues.getTypeOrNull();
        if (memoryType != null) {
            for (TypingsDeclarations.Declaration declaration : declarations) {
                if (declaration.getValueType() == memoryType) {
                    return declaration.getInterfaceName();
                }
            }
        }
        return "any";
    }

    private void appendHeader(StringBuilder file, String memoryType) {
        file.append("/**\n")
                .append(" * Typings for Integrated Scripting.\n")
                .append(" * This file is auto-generated on server start, so do not modify this file!\n")
                .append(" *\n")
                .append(" * A script is a bare script and can not import types, so every\n")
                .append(" * declaration in this file is global.\n")
                .append(" */\n\n")
                .append("declare var idContext: Context;\n\n")
                .append("/**\n")
                .append(" * The memory card of the only addressed memory in the network.\n")
                .append(" * Only defined when the network contains exactly one memory.\n")
                .append(" */\n")
                .append("declare var ").append(ScriptHelpers.MEMORY_CARD_GLOBAL)
                .append(": ").append(memoryType).append(" | undefined;\n\n")
                .append("interface Context {\n")
                .append(INDENT).append("ops: Operations;\n")
                .append(INDENT).append("/** The addressed memories of the network, by name and by index. */\n")
                .append(INDENT).append("memories: { [name: string]: ").append(memoryType).append(" };\n")
                .append(INDENT).append("/** The only addressed memory, when there is exactly one. */\n")
                .append(INDENT).append("memory?: ").append(memoryType).append(";\n")
                .append("}\n\n")
                .append("/**\n")
                .append(" * A callable operator value. Operators reach scripts as plain\n")
                .append(" * functions, so any function is accepted where one is expected.\n")
                .append(" */\n")
                .append("type Operator = (...args: any[]) => any;\n\n")
                .append("/**\n")
                .append(" * One value inside an NBT compound: a primitive, a list, a nested\n")
                .append(" * compound, or null when the key is absent.\n")
                .append(" */\n")
                .append("type NbtValue = string | number | boolean | NbtValue[] | NbtCompound | null;\n\n")
                .append("/**\n")
                .append(" * An NBT compound, exposed to scripts as a plain object.\n")
                .append(" */\n")
                .append("interface NbtCompound {\n")
                .append(INDENT).append("[key: string]: NbtValue;\n")
                .append("}\n\n");
        appendIdentityTypes(file);
    }

    private void appendIdentityTypes(StringBuilder file) {
        file.append("/**\n")
                .append(" * The `id_block` payload of a block value: the registry name and the\n")
                .append(" * metadata that identifies the block state.\n")
                .append(" */\n")
                .append("interface BlockIdentity {\n")
                .append(INDENT).append("/** Registry name, for example `minecraft:stone`. */\n")
                .append(INDENT).append("name: string;\n")
                .append(INDENT).append("/** Metadata, defaults to `0` when omitted. */\n")
                .append(INDENT).append("meta?: number;\n")
                .append("}\n\n")
                .append("/**\n")
                .append(" * The `id_item` payload of an item stack value, in its NBT form.\n")
                .append(" */\n")
                .append("interface ItemStackIdentity {\n")
                .append(INDENT).append("/** Registry name, for example `minecraft:stone`. */\n")
                .append(INDENT).append("id: string;\n")
                .append(INDENT).append("/** Stack size, defaults to `1` when omitted. */\n")
                .append(INDENT).append("Count?: number;\n")
                .append(INDENT).append("Damage?: number;\n")
                .append(INDENT).append("tag?: NbtCompound;\n")
                .append("}\n\n")
                .append("/**\n")
                .append(" * The `id_fluid` payload of a fluid stack value, in its NBT form.\n")
                .append(" */\n")
                .append("interface FluidStackIdentity {\n")
                .append(INDENT).append("/** Registry name, for example `minecraft:water`. */\n")
                .append(INDENT).append("FluidName: string;\n")
                .append(INDENT).append("/** Amount in millibuckets. */\n")
                .append(INDENT).append("Amount: number;\n")
                .append(INDENT).append("tag?: NbtCompound;\n")
                .append("}\n\n")
                .append("/**\n")
                .append(" * The `id_entity` payload of an entity value.\n")
                .append(" */\n")
                .append("interface EntityIdentity {\n")
                .append(INDENT).append("/** The entity UUID; absent for an entity value without one. */\n")
                .append(INDENT).append("uuid?: string;\n")
                .append("}\n\n")
                .append("/**\n")
                .append(" * The `id_ingredients` payload: CommonCapabilities' mixed-ingredients\n")
                .append(" * NBT form.\n")
                .append(" */\n")
                .append("type IngredientsIdentity = NbtCompound;\n\n")
                .append("/**\n")
                .append(" * The `id_recipe` payload: CommonCapabilities' recipe-definition NBT\n")
                .append(" * form.\n")
                .append(" */\n")
                .append("type RecipeIdentity = NbtCompound;\n\n");
    }

    private void appendGlobalOperators(StringBuilder file, Map<String, IOperator> operators,
                                       TypingsTypes types) {
        file.append("/**\n")
                .append(" * Every global operator, keyed by the name scripts call it with.\n")
                .append(" */\n")
                .append("interface Operations {\n");
        for (Map.Entry<String, IOperator> entry : operators.entrySet()) {
            appendOperator(file, entry.getKey(), entry.getValue(), null, types, INDENT);
        }
        file.append("}\n\n");
    }

    private void appendObjectType(StringBuilder file, TypingsDeclarations.Declaration declaration,
                                  Function<IValueType<?>, Map<String, IOperator>> methods,
                                  TypingsTypes types) {
        IValueType<?> valueType = declaration.getValueType();
        appendDescription(file, valueType.getTranslationKey() + ".info", "");
        file.append("interface ").append(declaration.getInterfaceName()).append(" {\n")
                .append(INDENT).append(declaration.getMemberKey()).append(": ")
                .append(TypingsTypes.identityType(declaration.getMemberKey())).append(";\n\n");
        for (Map.Entry<String, IOperator> entry : methods.apply(valueType).entrySet()) {
            appendOperator(file, entry.getKey(), entry.getValue(), declaration.getInterfaceName(),
                    types, INDENT);
        }
        file.append("}\n\n");
    }

    private void appendOperator(StringBuilder file, String name, IOperator operator,
                                @Nullable String ownerInterface, TypingsTypes types, String indent) {
        appendDescription(file, operator.getTranslationKey() + ".info", indent);

        IValueType[] inputTypes = operator.getInputTypes();
        List<TypingsType> inputs = new ArrayList<>(inputTypes.length);
        for (int i = 0; i < inputTypes.length; i++) {
            if (ownerInterface != null && i == 0) {
                // The receiver of a method is the value itself.
                continue;
            }
            inputs.add(types.of(inputTypes[i]));
        }

        file.append(indent).append(name).append(": ")
                .append(TypingsRenderer.signature(ownerInterface, name, inputs,
                        types.of(operator.getOutputType())))
                .append(";\n");
    }

    private void appendDescription(StringBuilder file, String unlocalizedKey, String indent) {
        String description;
        try {
            if (!TypingsI18n.canTranslate(unlocalizedKey)) {
                return;
            }
            description = TypingsI18n.translate(unlocalizedKey);
        } catch (RuntimeException e) {
            // A comment is cosmetic: never fail the typings (or the server start)
            // because a language lookup did, for example in a unit test.
            return;
        }
        file.append(indent).append("/**\n")
                .append(indent).append(" * ").append(comment(description)).append("\n")
                .append(indent).append(" */\n");
    }

    /**
     * @param text A localized line.
     * @return The text made safe for a single-line JSDoc comment.
     */
    private static String comment(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", " ").replace("\n", " ").replace("*/", "*\\/");
    }

}
