package com.mpp.integratedscripting.item;

import com.mpp.IntegratedScriptingCallback;
import org.cyclops.cyclopscore.config.extendedconfig.ItemConfig;

/**
 * Config for the network refresher item.
 *
 * <p>See {@link ItemRefresher} and docs/NETWORK-REFRESHER.md. Cyclops creates
 * and registers the item reflectively from this config.</p>
 *
 * @author rubensworks
 */
public class ItemRefresherConfig extends ItemConfig {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static ItemRefresherConfig _instance;

    public ItemRefresherConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "refresher",
                "Network refresher item.",
                ItemRefresher.class
        );
    }

}
