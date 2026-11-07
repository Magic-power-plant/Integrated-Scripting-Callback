package com.mpp.integratedscripting.core.language;

import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslator;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslatorRegistry;
import com.mpp.integratedscripting.evaluate.translation.translator.ValueTranslatorObjectAdapter;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which object value types get an interface in the generated
 * declarations, and under which name.
 *
 * <p>ID's own object types keep their familiar {@code ValueBlock},
 * {@code ValueItemstack}, ... names even if another mod registers a value type
 * with a colliding type name; the other type is suffixed instead. Value types
 * without a {@link ValueTranslatorObjectAdapter} get no interface at all, so the
 * generator never references a name that does not exist.</p>
 *
 * @author rubensworks
 */
final class TypingsDeclarations {

    /**
     * One declared object interface.
     */
    static final class Declaration {

        private final IValueType<?> valueType;
        private final String interfaceName;
        private final String memberKey;

        private Declaration(IValueType<?> valueType, String interfaceName, String memberKey) {
            this.valueType = valueType;
            this.interfaceName = interfaceName;
            this.memberKey = memberKey;
        }

        IValueType<?> getValueType() {
            return this.valueType;
        }

        String getInterfaceName() {
            return this.interfaceName;
        }

        String getMemberKey() {
            return this.memberKey;
        }

    }

    private TypingsDeclarations() {
    }

    /**
     * @param valueTypes Every registered value type.
     * @param translators The translator registry, used to find the identifying
     *                    member of an object type.
     * @param preferred Value types that may claim their name first, normally
     *                  ID's own object types.
     * @return The declarations, ordered by interface name for a deterministic
     *         file.
     */
    static List<Declaration> collect(Collection<IValueType<?>> valueTypes,
                                     IValueTranslatorRegistry translators,
                                     Collection<IValueType<?>> preferred) {
        Map<IValueType<?>, String> memberKeys = new IdentityHashMap<>();
        for (IValueType<?> valueType : valueTypes) {
            if (valueType == null || !valueType.isObject()) {
                continue;
            }
            String memberKey = memberKeyOf(valueType, translators);
            if (memberKey != null) {
                memberKeys.put(valueType, memberKey);
            }
        }

        List<IValueType<?>> candidates = new ArrayList<>(memberKeys.keySet());
        candidates.sort(Comparator
                .comparing((IValueType<?> type) -> TypingsTypes.interfaceNameFor(type.getTypeName()))
                .thenComparing(type -> String.valueOf(type.getTranslationKey())));

        Map<IValueType<?>, String> names = new IdentityHashMap<>();
        Set<String> used = new HashSet<>();
        for (IValueType<?> valueType : preferred) {
            if (memberKeys.containsKey(valueType) && !names.containsKey(valueType)) {
                names.put(valueType, uniqueName(TypingsTypes.interfaceNameFor(valueType.getTypeName()), used));
            }
        }
        for (IValueType<?> valueType : candidates) {
            if (!names.containsKey(valueType)) {
                names.put(valueType, uniqueName(TypingsTypes.interfaceNameFor(valueType.getTypeName()), used));
            }
        }

        List<Declaration> declarations = new ArrayList<>();
        for (Map.Entry<IValueType<?>, String> entry : names.entrySet()) {
            declarations.add(new Declaration(entry.getKey(), entry.getValue(), memberKeys.get(entry.getKey())));
        }
        declarations.sort(Comparator.comparing(Declaration::getInterfaceName));
        return declarations;
    }

    /**
     * @param declarations Declarations from {@link #collect}.
     * @return The interface names, keyed by value type identity.
     */
    static Map<IValueType<?>, String> names(List<Declaration> declarations) {
        Map<IValueType<?>, String> names = new IdentityHashMap<>();
        for (Declaration declaration : declarations) {
            names.put(declaration.getValueType(), declaration.getInterfaceName());
        }
        return names;
    }

    @Nullable
    private static String memberKeyOf(IValueType<?> valueType, IValueTranslatorRegistry translators) {
        IValueTranslator translator = translators.getValueTypeTranslator(valueType);
        if (translator instanceof ValueTranslatorObjectAdapter) {
            return ((ValueTranslatorObjectAdapter<?>) translator).getKey();
        }
        return null;
    }

    private static String uniqueName(String base, Set<String> used) {
        String name = base;
        int index = 2;
        while (!used.add(name)) {
            name = base + index++;
        }
        return name;
    }

}
