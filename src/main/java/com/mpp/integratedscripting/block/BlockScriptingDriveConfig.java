package com.mpp.integratedscripting.block;

import org.cyclops.cyclopscore.config.extendedconfig.BlockContainerConfig;
import com.mpp.IntegratedScriptingCallback;

/**
 * Config for {@link BlockScriptingDrive}.
 *
 * <p>The tile entity ({@code integratedscriptingcallback:scripting_drive},
 * decision A10) and the GUI registration are handled automatically by Cyclops'
 * {@code BlockAction} for {@code BlockContainerConfig} instances.</p>
 *
 * @author rubensworks
 */
public class BlockScriptingDriveConfig extends BlockContainerConfig {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockScriptingDriveConfig _instance;

    public BlockScriptingDriveConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "scripting_drive",
                "Scripting drive block.",
                BlockScriptingDrive.class
        );
    }

}
