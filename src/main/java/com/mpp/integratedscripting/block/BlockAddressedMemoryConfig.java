package com.mpp.integratedscripting.block;

import com.mpp.IntegratedScriptingCallback;
import org.cyclops.cyclopscore.config.extendedconfig.BlockContainerConfig;

/**
 * Config for {@link BlockAddressedMemory}.
 *
 * <p>The tile entity, the container and the GUI are derived by Cyclops'
 * {@code BlockAction} for {@code BlockContainerConfig} instances.</p>
 *
 * @author rubensworks
 */
public class BlockAddressedMemoryConfig extends BlockContainerConfig {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockAddressedMemoryConfig _instance;

    public BlockAddressedMemoryConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "addressed_memory",
                "Addressed memory block.",
                BlockAddressedMemory.class
        );
    }

}
