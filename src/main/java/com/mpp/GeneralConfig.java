package com.mpp;

import org.cyclops.cyclopscore.config.ConfigurableProperty;
import org.cyclops.cyclopscore.config.ConfigurableTypeCategory;
import org.cyclops.cyclopscore.config.extendedconfig.DummyConfig;
import org.cyclops.cyclopscore.tracking.Versions;

/**
 * General options for this mod.
 *
 * <p>1.12.2 Forge uses a single configuration file, so the Graal sandbox switches
 * live in the common config (see docs/PORTING-1.12.2.md §8.2).</p>
 *
 * @author rubensworks
 */
public class GeneralConfig extends DummyConfig {

    @ConfigurableProperty(category = ConfigurableTypeCategory.CORE,
            comment = "If the version checker should be enabled. Disabled by default: "
                    + "this fork has no public update JSON.")
    public static boolean versionChecker = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "The base energy usage for the scripting drive.", minimalValue = 0)
    public static int scriptingDriveBaseConsumption = 2;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "The base energy usage for the scripting terminal.", minimalValue = 0)
    public static int terminalScriptingBaseConsumption = 1;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "The minimum number of ticks in between sending a script change packet from client to server.",
            minimalValue = 0)
    public static int terminalScriptingClientSyncTickInterval = 20;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If new processes can be created from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowCreateProcess = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If new threads can be created from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowCreateThread = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If IO is allowed from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowIo = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If host class loading is allowed from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowHostClassLoading = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If experimental options can be used in guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowExperimentalOptions = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If environment variables can be accessed from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowEnvironment = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If the native interface can be accessed from guest languages. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowNative = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "If all Java public constructors should be accessible. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowHostPublicAccess = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allow guest languages to implement any Java interface. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowHostAllImplementations = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allow guest languages to implement (extend) any Java class. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowHostAllClassImplementations = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access arrays as values with array elements.",
            requiresMcRestart = true)
    public static boolean graalAllowHostArrayAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access lists as values with array elements and iterators.",
            requiresMcRestart = true)
    public static boolean graalAllowHostListAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access java.nio.ByteBuffers as values with buffer elements.",
            requiresMcRestart = true)
    public static boolean graalAllowHostBufferAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access iterables as values with iterators.",
            requiresMcRestart = true)
    public static boolean graalAllowHostIterableAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access iterators as iterator values.",
            requiresMcRestart = true)
    public static boolean graalAllowHostIteratorAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to access map as hash values.",
            requiresMcRestart = true)
    public static boolean graalAllowHostMapAccess = true;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Allows the guest application to inherit access to allowed methods, i.e. implementations "
                    + "of allowed abstract and interface methods and overrides of allowed concrete methods. "
                    + "Only enable this on private servers and if you know what you are doing.",
            requiresMcRestart = true)
    public static boolean graalAllowHostAccessInheritance = false;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "The maximum number of statements that can be executed in one evaluation. "
                    + "This avoids infinite loops or very complex programs. Set to -1 to disable the limit "
                    + "(not recommended).",
            minimalValue = -1)
    public static int graalStatementLimit = 16384;

    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "The maximum number of lines in stdout and stderr script log files. "
                    + "Set to -1 to disable the limit.",
            minimalValue = -1)
    public static int maxLogLines = 2096;

    /** New in 1.12.2: scalar NBT wrapping, see docs/PORTING-1.12.2.md §1.4 #2. */
    @ConfigurableProperty(category = ConfigurableTypeCategory.GENERAL,
            comment = "Wrap non-compound NBT arguments as {\"value\": <tag>} instead of failing.")
    public static boolean nbtScalarWrapper = false;

    public GeneralConfig() {
        super(IntegratedScriptingCallback._instance, true, "general",
                "General config for Integrated Scripting Callback.", GeneralConfig.class);
    }

    @Override
    public void onRegistered() {
        if (versionChecker) {
            Versions.registerMod(getMod(), IntegratedScriptingCallback._instance, Reference.VERSION_URL);
        }
    }
}
