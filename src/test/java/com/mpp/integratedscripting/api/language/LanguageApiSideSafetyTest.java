package com.mpp.integratedscripting.api.language;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Side-safety guard for the language API (docs/LANGUAGE-API-REFACTOR.md Phase 0
 * and §7.3).
 *
 * <p>A dedicated server strips client-only classes, so a non-deprecated public
 * API method or field must never mention a client type. The deprecated legacy
 * members ({@code getIcon}) are explicitly whitelisted: they only exist for binary
 * compatibility and are removed in 2.0.</p>
 */
public class LanguageApiSideSafetyTest {

    private static final String[] CLIENT_PACKAGE_PREFIXES = {
            "org.cyclops.cyclopscore.client.",
            "net.minecraft.client."
    };

    @Test
    public void testNoNonDeprecatedApiMemberMentionsAClientType() throws Exception {
        List<Class<?>> apiClasses = findApiClasses();
        assertTrue("The API scan found too few classes: " + apiClasses.size(),
                apiClasses.size() >= 15);

        List<String> offenders = new ArrayList<>();
        for (Class<?> type : apiClasses) {
            for (Field field : type.getDeclaredFields()) {
                if (isDeprecated(field)) {
                    continue;
                }
                check(type, "field " + field.getName(), field.getType(), offenders);
            }
            for (Method method : type.getDeclaredMethods()) {
                if (isDeprecated(method)) {
                    continue;
                }
                check(type, "method " + method.getName(), method.getReturnType(), offenders);
                for (Class<?> parameter : method.getParameterTypes()) {
                    check(type, "method " + method.getName() + " parameter",
                            parameter, offenders);
                }
            }
        }
        assertTrue("Non-deprecated language API members reference client types: " + offenders,
                offenders.isEmpty());
    }

    @Test
    public void testDeprecatedIconMethodIsWhitelisted() throws Exception {
        // Sanity check of the guard itself: the only client-typed member of the
        // handler API must be the deprecated getIcon bridge.
        Method getIcon = ILanguageHandler.class.getMethod("getIcon");
        assertTrue(isDeprecated(getIcon));
        assertTrue(isClientType(getIcon.getReturnType()));
    }

    private static boolean isDeprecated(java.lang.reflect.AnnotatedElement element) {
        return element.isAnnotationPresent(Deprecated.class);
    }

    private static void check(Class<?> owner, String member, Class<?> type, List<String> offenders) {
        if (type == null) {
            return;
        }
        if (type.isArray()) {
            check(owner, member, type.getComponentType(), offenders);
            return;
        }
        if (isClientType(type)) {
            offenders.add(owner.getName() + " " + member + " -> " + type.getName());
        }
    }

    private static boolean isClientType(Class<?> type) {
        String name = type.getName();
        for (String prefix : CLIENT_PACKAGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static List<Class<?>> findApiClasses() throws Exception {
        String packagePath = "com/mpp/integratedscripting/api/language";
        ClassLoader classLoader = LanguageApiSideSafetyTest.class.getClassLoader();
        List<Path> classFiles = new ArrayList<>();
        Enumeration<URL> roots = classLoader.getResources(packagePath);
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            if (!"file".equals(url.getProtocol())) {
                continue;
            }
            Path root = Paths.get(url.toURI());
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(path -> path.getFileName().toString().endsWith(".class"))
                        .forEach(classFiles::add);
            }
        }
        assertFalse("No compiled language API classes found", classFiles.isEmpty());

        List<Class<?>> classes = new ArrayList<>();
        for (Path classFile : classFiles) {
            String name = toClassName(classFile);
            if (name == null) {
                continue;
            }
            try {
                classes.add(Class.forName(name, false, classLoader));
            } catch (ClassNotFoundException | LinkageError e) {
                // A class that cannot be inspected is reported by the count assert.
            }
        }
        return classes;
    }

    private static String toClassName(Path classFile) {
        String path = classFile.toAbsolutePath().toString().replace('\\', '/');
        int index = path.indexOf("com/mpp/integratedscripting/");
        if (index < 0) {
            return null;
        }
        return path.substring(index, path.length() - ".class".length()).replace('/', '.');
    }

}
