package com.mpp.integratedscripting.core.language;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Renders a function type for one operator.
 *
 * @author rubensworks
 */
final class TypingsRenderer {

    private TypingsRenderer() {
    }

    /**
     * @param ownerInterface The interface a method is declared on, or null for a
     *                       global operator.
     * @param name The script name of the operator.
     * @param inputTypes The operator's inputs, without the receiver of a method.
     * @param outputType The operator's output.
     * @return A TypeScript function type, for example
     *         {@code (list: any[], index: number) => any}.
     */
    static String signature(@Nullable String ownerInterface, String name,
                            List<TypingsType> inputTypes, TypingsType outputType) {
        String override = ownerInterface == null
                ? TypingsSignatures.global(name, inputTypes, outputType)
                : TypingsSignatures.method(ownerInterface, name, inputTypes, outputType);
        if (override != null) {
            return override;
        }

        StringBuilder builder = new StringBuilder("(");
        Set<String> used = new HashSet<>();
        for (int i = 0; i < inputTypes.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            TypingsType inputType = inputTypes.get(i);
            builder.append(unique(inputType.parameter, used))
                    .append(": ")
                    .append(inputType.ts);
        }
        return builder.append(") => ").append(outputType.ts).toString();
    }

    private static String unique(String parameter, Set<String> used) {
        String name = parameter;
        int index = 2;
        while (!used.add(name)) {
            name = parameter + index++;
        }
        return name;
    }

}
