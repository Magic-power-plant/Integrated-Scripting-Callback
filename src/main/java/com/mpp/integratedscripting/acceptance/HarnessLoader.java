package com.mpp.integratedscripting.acceptance;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import org.apache.logging.log4j.Level;
import com.mpp.IntegratedScriptingCallback;

/**
 * Registers the development-only acceptance harnesses.
 *
 * <p>1.12.2 port addition (docs/AUDIT.md D7 / docs/MILESTONES.md M8). The
 * harnesses drive a real singleplayer or dedicated server, so they are useful
 * during development but must not ship: they are excluded from the release jar
 * by {@code build.gradle}. That exclusion only works if nothing in the shipped
 * classes references them, which is why activation goes through reflection and
 * the class names appear here as plain strings.</p>
 *
 * <p>The system properties are the ones the Gradle {@code -P} hooks set
 * ({@code -Disc.guiSmoke}, {@code -Disc.resourceSmoke}, {@code -Disc.m7Smoke},
 * {@code -Disc.m7Gui}).</p>
 *
 * @author rubensworks
 */
public final class HarnessLoader {

    public static final String SERVER_ACCEPTANCE = "com.mpp.integratedscripting.acceptance.ServerAcceptance";
    public static final String GUI_SMOKE_HARNESS =
            "com.mpp.integratedscripting.client.gui.container.GuiSmokeHarness";
    public static final String RESOURCE_SMOKE_HARNESS =
            "com.mpp.integratedscripting.client.gui.container.ResourceSmokeHarness";
    public static final String CLIENT_ACCEPTANCE_HARNESS =
            "com.mpp.integratedscripting.client.gui.container.ClientAcceptanceHarness";

    public static final String PROPERTY_SERVER = "isc.m7Smoke";
    public static final String PROPERTY_GUI_SMOKE = "isc.guiSmoke";
    public static final String PROPERTY_RESOURCE_SMOKE = "isc.resourceSmoke";
    public static final String PROPERTY_CLIENT = "isc.m7Gui";

    private HarnessLoader() {
    }

    /**
     * Register one harness class on the Forge event bus when its property is set.
     * @param systemProperty The property that enables the harness.
     * @param className The fully qualified harness class name.
     */
    public static void registerIfEnabled(String systemProperty, String className) {
        registerIfEnabled(MinecraftForge.EVENT_BUS, systemProperty, className);
    }

    /**
     * Register one harness class on the given event bus when its property is set.
     * @param eventBus The bus to register on.
     * @param systemProperty The property that enables the harness.
     * @param className The fully qualified harness class name.
     */
    public static void registerIfEnabled(EventBus eventBus, String systemProperty, String className) {
        if (!Boolean.getBoolean(systemProperty)) {
            return;
        }
        try {
            Class<?> harnessClass = Class.forName(className);
            eventBus.register(harnessClass.getDeclaredConstructor().newInstance());
            IntegratedScriptingCallback.clog("Registered acceptance harness " + className + ".");
        } catch (ClassNotFoundException e) {
            // Expected for a release jar: the harnesses are excluded on purpose.
            IntegratedScriptingCallback.clog(Level.WARN, "Acceptance harness " + className
                    + " is enabled through -D" + systemProperty
                    + " but is not packaged in this build. Ignoring.");
        } catch (ReflectiveOperationException | RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.ERROR,
                    "Failed to register acceptance harness " + className + ": " + e);
        }
    }

}
