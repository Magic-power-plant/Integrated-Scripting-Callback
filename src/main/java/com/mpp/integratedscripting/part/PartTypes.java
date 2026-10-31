package com.mpp.integratedscripting.part;

import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.part.IPartTypeRegistry;

/**
 * The part types of this mod.
 *
 * <p>Loaded from {@code IntegratedScriptingCallback#preInit} before Cyclops
 * handles the configs, mirroring how Integrated Dynamics initialises its own
 * {@code PartTypes} class: the part constructor creates and registers the part
 * item, the part block and the part GUI (docs/PORTING-1.12.2.md §6.24).</p>
 *
 * @author rubensworks
 */
public class PartTypes {

    public static final IPartTypeRegistry REGISTRY = IntegratedDynamics._instance
            .getRegistryManager().getRegistry(IPartTypeRegistry.class);

    public static final PartTypeTerminalScripting TERMINAL_SCRIPTING =
            REGISTRY.register(new PartTypeTerminalScripting("terminal_scripting"));

    /**
     * Triggers static initialisation; the parts register themselves in the
     * static initialiser above.
     */
    public static void load() {
    }

}
