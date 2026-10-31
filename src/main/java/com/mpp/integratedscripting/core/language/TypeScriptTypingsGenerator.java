package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.callback.TypingsI18n;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorObjectAdapter;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;

import java.util.Arrays;
import java.util.Map;

/**
 * Generates TypeScript definitions for all available operators and value types.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §5.4.5, §6.16): operators are
 * read from {@link OperatorBindings} instead of ID's interact operator
 * registries, and localization goes through {@link TypingsI18n} so that it is
 * safe on a dedicated server.</p>
 *
 * @author rubensworks
 */
public class TypeScriptTypingsGenerator {

    public String generate() {
        StringBuilder fileBuilder = new StringBuilder();

        // Static header
        fileBuilder.append("/**\n")
                .append(" * Typings for Integrated Scripting.\n")
                .append(" * This file is auto-generated on server start, so do not modify this file!\n")
                .append(" */\n\n")
                .append("declare global {\n")
                .append("  var idContext: Context;\n")
                .append("}\n\n")
                .append("export interface Context {\n")
                .append("  ops: Operations;\n")
                .append("}\n\n");

        // Global operators
        fileBuilder.append("export interface Operations {\n");
        for (Map.Entry<String, IOperator> entry : OperatorBindings.getGlobalOperators().entrySet()) {
            handleOperator(fileBuilder, entry.getKey(), entry.getValue(), false);
        }
        fileBuilder.append("}\n\n");

        // Value object types
        for (IValueType<?> valueType : ValueTypes.REGISTRY.getValueTypes()) {
            IValueTranslator translator = ValueTranslators.REGISTRY.getValueTypeTranslator(valueType);
            if (valueType.isObject() && translator instanceof ValueTranslatorObjectAdapter) {
                ValueTranslatorObjectAdapter<?> translatorObjectAdapter =
                        (ValueTranslatorObjectAdapter<?>) translator;

                // Value type description
                handleDescriptionComment(fileBuilder, valueType.getTranslationKey() + ".info");

                // Interface declaration
                fileBuilder.append("export interface ")
                        .append(getObjectValueTypeInterfaceName(valueType))
                        .append(" {\n");

                // Identifying member
                fileBuilder.append("  ")
                        .append(translatorObjectAdapter.getKey())
                        .append(": Record<string, any>;\n\n");

                // Scoped operators
                for (Map.Entry<String, IOperator> entry
                        : OperatorBindings.getMethods(valueType).entrySet()) {
                    handleOperator(fileBuilder, entry.getKey(), entry.getValue(), true);
                }

                fileBuilder.append("}\n\n");
            }
        }

        return fileBuilder.toString();
    }

    private void handleDescriptionComment(StringBuilder sb, String unlocalizedKey) {
        if (TypingsI18n.canTranslate(unlocalizedKey)) {
            sb.append("  /**\n");
            sb.append("   * ").append(TypingsI18n.translate(unlocalizedKey)).append("\n");
            sb.append("   */\n");
        }
    }

    private void handleOperator(StringBuilder sb, String name, IOperator operator, boolean scoped) {
        // Operator description
        handleDescriptionComment(sb, operator.getTranslationKey() + ".info");

        // Operator signature
        sb.append(String.format("  %s: (%s) => %s;\n", name,
                this.generateArguments(operator, scoped), this.generateType(operator.getOutputType())));
    }

    private String generateArguments(IOperator operator, boolean scoped) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        IValueType[] inputTypes = operator.getInputTypes();
        if (scoped) {
            inputTypes = Arrays.copyOfRange(inputTypes, 1, inputTypes.length);
        }
        for (IValueType inputType : inputTypes) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("arg%s: %s", i++, this.generateType(inputType)));
        }
        return sb.toString();
    }

    private String generateType(IValueType<?> valueType) {
        if (ValueTypes.CATEGORY_NUMBER.correspondsTo(valueType)) {
            return "number";
        }
        if (valueType.isCategory()) {
            return "any";
        }
        if (ValueTypes.BOOLEAN == valueType) {
            return "boolean";
        }
        if (ValueTypes.STRING == valueType) {
            return "string";
        }
        if (ValueTypes.LIST == valueType) {
            return "any[]";
        }
        if (ValueTypes.NBT == valueType) {
            return "Record<string, any>";
        }
        if (ValueTypes.OPERATOR == valueType) {
            return "Function";
        }
        return getObjectValueTypeInterfaceName(valueType);
    }

    private String getObjectValueTypeInterfaceName(IValueType<?> valueType) {
        String name = valueType.getTypeName();
        return "Value" + Character.toTitleCase(name.charAt(0)) + name.substring(1);
    }

}
