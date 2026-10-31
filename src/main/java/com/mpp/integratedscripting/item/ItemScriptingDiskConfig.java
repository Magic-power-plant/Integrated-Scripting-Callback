package com.mpp.integratedscripting.item;

import org.cyclops.cyclopscore.config.extendedconfig.ItemConfig;
import com.mpp.IntegratedScriptingCallback;

/**
 * Config for the scripting disk item.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §6.21): {@code ItemConfig} with
 * the 1.12 constructor signature; the item is instantiated reflectively by
 * Cyclops.</p>
 *
 * @author rubensworks
 */
public class ItemScriptingDiskConfig extends ItemConfig {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static ItemScriptingDiskConfig _instance;

    public ItemScriptingDiskConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "scripting_disk",
                "Scripting disk item.",
                ItemScriptingDisk.class
        );
    }

}
