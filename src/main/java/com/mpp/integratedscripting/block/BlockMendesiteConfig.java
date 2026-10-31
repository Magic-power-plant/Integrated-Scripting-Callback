package com.mpp.integratedscripting.block;

import org.cyclops.cyclopscore.config.extendedconfig.BlockConfig;
import com.mpp.IntegratedScriptingCallback;

/**
 * Config for {@link BlockMendesite}.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §9.4): {@code BlockConfig} takes
 * the 1.12 constructor signature and creates the block plus its {@code ItemBlock}
 * reflectively. The item is automatically added to the mod's creative tab.</p>
 *
 * @author rubensworks
 */
public class BlockMendesiteConfig extends BlockConfig {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockMendesiteConfig _instance;

    public BlockMendesiteConfig() {
        super(
                IntegratedScriptingCallback._instance,
                true,
                "mendesite",
                "Mendesite block, the basic crafting material for scripting items.",
                BlockMendesite.class
        );
    }

}
