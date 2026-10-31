package com.mpp.integratedscripting.api.evaluate.translation;

import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * This event is emitted on the Forge event bus so that other mods can register
 * their own value translators.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §5.10): 1.12 has a single global
 * Forge event bus instead of the per-mod lifecycle bus, so this is a regular
 * {@link Event} posted on {@code MinecraftForge.EVENT_BUS}. This extension point
 * is therefore not source compatible with the 1.20 version.</p>
 *
 * @author rubensworks
 */
public class ValueTranslatorRegisterEvent extends Event {

    private final IValueTranslatorRegistry registry;

    public ValueTranslatorRegisterEvent(IValueTranslatorRegistry registry) {
        this.registry = registry;
    }

    public IValueTranslatorRegistry getRegistry() {
        return registry;
    }

}
